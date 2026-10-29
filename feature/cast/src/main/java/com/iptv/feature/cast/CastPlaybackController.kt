package com.iptv.feature.cast

import android.content.Context
import android.util.Log
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.iptv.core.common.cast.CastController
import com.iptv.core.common.cast.CastEvent
import com.iptv.core.common.cast.CastStreamSpec
import com.iptv.core.common.cast.CastUiState
import com.iptv.core.network.StreamProber
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Orquestación de la sesión Cast: transferencia local↔remoto, sondeo del
 * stream, proxy local y escalado remux→transcode. La máquina de estados vive
 * aquí y no en el ViewModel: el player es de proceso ([CastPlayerRuntime])
 * y los listeners del framework Cast quedan encapsulados.
 *
 * No libera nada al detacherse: la sesión Cast sobrevive a la pantalla.
 * Sin scope: una instancia por consumidor (pantalla del player).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CastPlaybackController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val okHttpClient: OkHttpClient,
) : CastController {

    private val _uiState = MutableStateFlow(CastUiState())
    override val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CastEvent>(extraBufferCapacity = 8)
    override val events = _events.asSharedFlow()

    private var castPlayer: RemoteCastPlayer? = null
    private var castContext: CastContext? = null
    private var localPlayer: Player? = null
    private var observedMediaClient: RemoteMediaClient? = null
    private var observedCastSession: CastSession? = null
    private var onZap: ((Int) -> Unit)? = null

    private var lastItem: MediaItem? = null
    private var lastSpec: CastStreamSpec? = null

    private var castTransferPending = false
    private var castTransferPlayWhenReady = false
    private var activeCastHlsMode: String? = null
    private var transcodeEscalated = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _volume = MutableStateFlow(1f)
    override val volume = _volume.asStateFlow()
    private val _muted = MutableStateFlow(false)
    override val muted = _muted.asStateFlow()

    override val remotePlayer: Player? get() = castPlayer
    override val isSessionAvailable: Boolean get() = castPlayer?.isCastSessionAvailable == true

    // -- Ciclo de vida ------------------------------------------------------

    /** Crea el RemoteCastPlayer de proceso si hace falta y engancha listeners. */
    override fun attach(local: Player) {
        localPlayer = local
        val created = CastPlayerRuntime.castPlayer(appContext)
        if (created == null) {
            _uiState.update { it.copy(available = false) }
            return
        }
        castPlayer = created
        castContext = CastPlayerRuntime.castContext()
        created.addListener(castListener)
        created.setSessionAvailabilityListener(sessionAvailabilityListener)
        castContext?.sessionManager?.addSessionManagerListener(
            castSessionListener, CastSession::class.java,
        )
        _uiState.update { it.copy(available = true) }
    }

    /**
     * Desengancha listeners de la pantalla. Ni el player ni el proxy se
     * liberan: la sesión Cast sigue viva tras salir (la gestiona el servicio).
     */
    override fun detach() {
        localPlayer = null
        onZap = null
        CastProxyRuntime.onZap = null
        observedMediaClient?.unregisterCallback(receiverErrorCallback)
        observedMediaClient = null
        observedCastSession?.removeCastListener(castDeviceListener)
        observedCastSession = null
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.removeListener(castListener)
        castContext?.sessionManager?.removeSessionManagerListener(
            castSessionListener, CastSession::class.java,
        )
        scope.cancel()
    }

    override fun setMediaSessionToken(token: android.media.session.MediaSession.Token?) {
        CastProxyRuntime.sessionToken = token
    }

    override fun setZapHandler(handler: ((Int) -> Unit)?) {
        onZap = handler
        CastProxyRuntime.onZap = handler
    }

    // -- Transferencia ------------------------------------------------------

    /**
     * Lleva la reproducción al receptor: adopta la sesión si ya está en este
     * stream, o carga el item vía proxy local. Emite RemoteActive al
     * completar o LocalResume si falla.
     */
    override suspend fun transferToCast(
        item: MediaItem,
        positionMs: Long,
        playWhenReady: Boolean,
        spec: CastStreamSpec,
    ) {
        val remote = castPlayer ?: return
        if (_uiState.value.casting || castTransferPending) return
        lastItem = item
        lastSpec = spec
        castTransferPlayWhenReady = playWhenReady
        castTransferPending = true
        _uiState.update { it.copy(connecting = true, transferPending = true, receiverError = null) }

        observeReceiverErrors()

        // Re-entering while a cast runs: adopt the session instead of
        // reloading the TV — but only when the receiver is on THIS stream.
        val remoteState = runCatching {
            castContext?.sessionManager?.currentCastSession
                ?.remoteMediaClient?.mediaStatus?.playerState
        }.getOrNull()
        if (CastMediaDecisions.shouldAdoptRemotePlayback(
                remoteState,
                CastPlayerRuntime.loadedStreamUri,
                item.localConfiguration?.uri?.toString(),
            )
        ) {
            castTransferPending = false
            emitRemoteActive()
            return
        }
        applyCastItem(item, positionMs, playWhenReady, spec)
    }

    /** Recarga el item en el receptor (zapping con sesión activa). */
    override suspend fun loadOnRemote(item: MediaItem, spec: CastStreamSpec) {
        lastItem = item
        lastSpec = spec
        transcodeEscalated = false
        activeCastHlsMode = null
        applyCastItem(item, positionMs = 0L, playWhenReady = true, spec)
    }

    /**
     * Carga [item] en el receptor: sondea el payload real para decidir la URL
     * y el MIME (los paneles IPTV mienten por extensión y Content-Type) y lo
     * sirve a través del proxy local de sesión.
     */
    private suspend fun applyCastItem(
        item: MediaItem,
        positionMs: Long,
        playWhenReady: Boolean,
        spec: CastStreamSpec,
        hlsModeOverride: String? = null,
    ) {
        val remote = castPlayer ?: return
        // El título alimenta la notificación del proxy en todos los caminos
        // (transferencia nueva y zapping con sesión activa).
        CastProxyRuntime.channelTitle = spec.channelTitle
        val isLive = item.liveConfiguration != MediaItem.LiveConfiguration.UNSET
        val target = withContext(Dispatchers.IO) {
            resolveCastTarget(spec.remoteUrl, isLive, spec, hlsModeOverride)
        }
        if (castPlayer?.isCastSessionAvailable != true) return

        val remoteAddress = runCatching {
            castContext?.sessionManager?.currentCastSession?.castDevice?.inetAddress
        }.getOrNull()
        val proxy = CastProxyRuntime.ensureStarted(
            appContext, okHttpClient, spec.userAgent, spec.referrer, remoteAddress,
        )
        val proxiedUrl = when {
            proxy == null -> target.url
            target.hlsMode != null -> proxy.hlsUrl(target.url, target.hlsMode)
            else -> proxy.proxyUrl(target.url)
        }
        activeCastHlsMode = target.hlsMode
        Log.d(TAG, "Cast URL: $proxiedUrl (${target.mimeType}, mode=${target.hlsMode})")

        val castItem = item.buildUpon()
            .setUri(proxiedUrl)
            .setMimeType(target.mimeType)
            .build()

        runCatching {
            remote.playWhenReady = playWhenReady
            remote.setMediaItem(castItem, positionMs)
            remote.prepare()
        }.onSuccess {
            CastPlayerRuntime.markLoaded(spec.remoteUrl)
        }.onFailure { e ->
            Log.e(TAG, "applyCastItem failed", e)
            castTransferPending = false
            _uiState.update { it.copy(transferPending = false) }
            CastProxyRuntime.stop(appContext)
            _events.emit(CastEvent.LocalResume(
                transferFromRemote = false, playWhenReady = null, showError = false,
            ))
        }
    }

    /** [hlsMode]: null = direct proxy; "r" = ffmpeg remux to HLS;
     *  "t" = ffmpeg remux with audio→AAC transcode; "n" = in-memory cutter.
     *  The receiver can't play progressive video/mp2t — live TS needs HLS. */
    private data class CastTarget(val url: String, val mimeType: String, val hlsMode: String?)

    /**
     * Resolves the URL and MIME type to hand to the receiver. For Xtream live
     * streams the `.m3u8` variant is tried first (real HLS), falling back to
     * the original URL when the panel serves TS at that URL or doesn't
     * support HLS output at all. Raw TS streams are wrapped in synthetic HLS
     * because the Default Media Receiver rejects progressive video/mp2t.
     */
    private fun resolveCastTarget(
        originalUrl: String,
        isLive: Boolean,
        spec: CastStreamSpec,
        hlsModeOverride: String? = null,
    ): CastTarget {
        val hlsVariant = CastMediaDecisions.preferHlsForLive(originalUrl, isLive)
        val candidates = if (hlsVariant == originalUrl) {
            listOf(originalUrl)
        } else {
            listOf(hlsVariant, originalUrl)
        }
        for (url in candidates) {
            val kind = StreamProber.probe(okHttpClient, url, spec.userAgent, spec.referrer)
            Log.d(TAG, "Probe $url -> $kind")
            val target = when (kind) {
                StreamProber.Kind.HLS -> CastTarget(url, MimeTypes.APPLICATION_M3U8, null)
                // Raw TS live → remux to HLS through ffmpeg (keyframe-aligned
                // segments, far more reliable than naive packet cuts).
                StreamProber.Kind.TS -> CastTarget(url, MimeTypes.APPLICATION_M3U8, hlsModeOverride ?: defaultHlsMode())
                StreamProber.Kind.MP4 -> CastTarget(url, MimeTypes.VIDEO_MP4, null)
                StreamProber.Kind.DASH -> CastTarget(url, MimeTypes.APPLICATION_MPD, null)
                StreamProber.Kind.UNKNOWN -> null
            }
            if (target != null) return target
        }
        val url = candidates.first()
        val mime = CastMediaDecisions.mimeTypeForCast(url, isLive)
        // A live TS fallback still needs the HLS wrapper to play at all.
        val needsWrap = mime == MimeTypes.VIDEO_MP2T && isLive
        return CastTarget(
            url,
            if (needsWrap) MimeTypes.APPLICATION_M3U8 else mime,
            if (needsWrap) hlsModeOverride ?: defaultHlsMode() else null,
        )
    }

    /** "r" (ffmpeg remux) si la nativa existe; "n" (cortador en memoria) en
     *  dispositivos 32-bit, donde ffmpeg-kit no se instala. */
    private fun defaultHlsMode(): String {
        val ok = FfmpegHlsSession.isNativeAvailable(appContext)
        if (!ok) Log.w(TAG, "ffmpeg native missing (32-bit device) — using in-memory TS cutter")
        return if (ok) "r" else "n"
    }

    /** Termina la sesión y devuelve la reproducción al local. */
    override fun endSession() {
        val wasCasting = _uiState.value.casting
        castTransferPending = false
        CastPlayerRuntime.clearLoaded()
        CastProxyRuntime.stop(appContext)
        _uiState.update {
            it.copy(casting = false, connecting = false, transferPending = false, deviceName = null)
        }
        _events.tryEmit(CastEvent.LocalResume(
            transferFromRemote = wasCasting, playWhenReady = null, showError = false,
        ))
        runCatching { castContext?.sessionManager?.endCurrentSession(true) }
    }

    override fun clearReceiverError() {
        _uiState.update { it.copy(receiverError = null) }
    }

    // -- Volumen del receptor -----------------------------------------------

    override fun refreshCastVolume() {
        val session = runCatching {
            castContext?.sessionManager?.currentCastSession
        }.getOrNull() ?: return
        _volume.value = session.volume.toFloat().coerceIn(0f, 1f)
        _muted.value = session.isMute
    }

    override fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        _volume.value = v
        runCatching {
            castContext?.sessionManager?.currentCastSession?.setVolume(v.toDouble())
        }
    }

    override fun setMuted(muted: Boolean) {
        _muted.value = muted
        runCatching {
            castContext?.sessionManager?.currentCastSession?.setMute(muted)
        }
    }

    // -- Listeners ----------------------------------------------------------

    private val castDeviceListener = object : Cast.Listener() {
        override fun onVolumeChanged() = refreshCastVolume()
    }

    private val castListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d(TAG, "Cast playback state: $playbackState (pending=$castTransferPending)")
            if (castTransferPending && playbackState == Player.STATE_READY) {
                completeCastTransfer()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Cast player error: ${error.errorCode} ${error.message}", error)
            // A remuxed TS stream failing usually means the receiver can't
            // decode the audio (MP2/AC3) — retry once with audio→AAC
            // transcode before giving up.
            if (activeCastHlsMode == "r" && !transcodeEscalated) {
                transcodeEscalated = true
                castTransferPending = true
                Log.w(TAG, "Retrying cast with audio transcode")
                scope.launch {
                    lastItem?.let { item ->
                        lastSpec?.let { applyCastItem(item, 0L, playWhenReady = true, it, "t") }
                    }
                }
                return
            }
            val wasCasting = _uiState.value.casting
            val resumePlayback = if (wasCasting) {
                castPlayer?.playWhenReady == true
            } else {
                castTransferPlayWhenReady
            }
            castTransferPending = false
            CastProxyRuntime.stop(appContext)
            _uiState.update { it.copy(casting = false, transferPending = false) }
            scope.launch {
                _events.emit(CastEvent.LocalResume(
                    transferFromRemote = wasCasting,
                    playWhenReady = resumePlayback,
                    showError = true,
                ))
            }
        }
    }

    private val sessionAvailabilityListener = object : SessionAvailabilityListener {
        override fun onCastSessionAvailable() = autoTransfer()
        override fun onCastSessionUnavailable() = onSessionGone()
    }

    private fun autoTransfer() {
        val local = localPlayer ?: return
        val item = lastItem ?: return
        val spec = lastSpec ?: return
        scope.launch {
            transferToCast(item, local.currentPosition.coerceAtLeast(0L), local.playWhenReady, spec)
        }
    }

    private fun onSessionGone() {
        val wasCasting = _uiState.value.casting
        val wasPending = castTransferPending
        castTransferPending = false
        CastPlayerRuntime.clearLoaded()
        CastProxyRuntime.stop(appContext)
        _uiState.update {
            it.copy(casting = false, connecting = false, transferPending = false, deviceName = null)
        }
        if (wasCasting || wasPending) {
            _events.tryEmit(CastEvent.LocalResume(
                transferFromRemote = wasCasting, playWhenReady = null, showError = false,
            ))
        }
    }

    private fun completeCastTransfer() {
        if (!castTransferPending || castPlayer?.isCastSessionAvailable != true) return
        castTransferPending = false
        emitRemoteActive()
    }

    private fun emitRemoteActive() {
        _uiState.update {
            it.copy(
                casting = true,
                connecting = false,
                transferPending = false,
                deviceName = castContext?.sessionManager
                    ?.currentCastSession?.castDevice?.friendlyName,
                receiverError = null,
            )
        }
        _events.tryEmit(CastEvent.RemoteActive)
    }

    private val castSessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = markCastConnecting(session)
        override fun onSessionResuming(session: CastSession, sessionId: String) =
            markCastConnecting(session)
        override fun onSessionStarted(session: CastSession, sessionId: String) {}
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionEnded(session: CastSession, error: Int) {
            _uiState.update { it.copy(connecting = false) }
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _uiState.update { it.copy(connecting = false) }
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _uiState.update { it.copy(connecting = false, deviceName = null) }
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _uiState.update { it.copy(connecting = false, deviceName = null) }
        }
    }

    private fun markCastConnecting(session: CastSession) {
        _uiState.update {
            it.copy(
                connecting = true,
                deviceName = session.castDevice?.friendlyName ?: it.deviceName,
            )
        }
    }

    private val receiverErrorCallback = object : RemoteMediaClient.Callback() {
        override fun onMediaError(mediaError: MediaError) {
            val detail = buildString {
                append(mediaError.type ?: "LOAD_FAILED")
                mediaError.detailedErrorCode?.let { append(" · code ").append(it) }
                mediaError.reason?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            }
            Log.e(TAG, "Receiver media error: $detail")
            _uiState.update { it.copy(receiverError = detail) }
        }
    }

    private fun observeReceiverErrors() {
        val session = runCatching {
            castContext?.sessionManager?.currentCastSession
        }.getOrNull() ?: return
        if (observedCastSession !== session) {
            observedCastSession?.removeCastListener(castDeviceListener)
            session.addCastListener(castDeviceListener)
            observedCastSession = session
        }
        refreshCastVolume()
        val client = session.remoteMediaClient ?: return
        if (observedMediaClient !== client) {
            observedMediaClient?.unregisterCallback(receiverErrorCallback)
            client.registerCallback(receiverErrorCallback)
            observedMediaClient = client
        }
    }

    private companion object {
        const val TAG = "IptvCastCtl"
    }
}
