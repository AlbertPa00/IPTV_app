package com.iptv.feature.tvlink.server

import android.content.Context
import android.media.AudioManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.iptv.core.common.platform.isTelevision
import com.iptv.core.common.playback.PlaybackSession
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.common.text.TextNormalizer
import com.iptv.core.common.tvlink.LinkedClient
import com.iptv.core.common.tvlink.RemoteSourceController
import com.iptv.core.common.tvlink.RemoteSourceSync
import com.iptv.core.common.tvlink.TvLinkHost
import com.iptv.core.common.tvlink.TvLinkHostStatus
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.Kinds
import com.iptv.core.storage.entity.SourceEntity
import com.iptv.feature.tvlink.wire.AuthFailReason
import com.iptv.feature.tvlink.wire.LINK_PORT_FALLBACK
import com.iptv.feature.tvlink.wire.LINK_PROTOCOL_VERSION
import com.iptv.feature.tvlink.wire.LINK_SERVICE_PREFIX
import com.iptv.feature.tvlink.wire.LINK_SERVICE_TYPE
import com.iptv.feature.tvlink.wire.LinkAction
import com.iptv.feature.tvlink.wire.LinkMsg
import com.iptv.feature.tvlink.wire.LinkSocket
import com.iptv.feature.tvlink.wire.RemoteChannelDto
import com.iptv.feature.tvlink.wire.RemoteGroupDto
import com.iptv.feature.tvlink.wire.RemoteSourceDto
import com.iptv.feature.tvlink.ChannelResolver
import com.iptv.feature.tvlink.keepalive.TvLinkService
import com.iptv.feature.tvlink.wire.seekWhenReady
import com.iptv.feature.tvlink.wire.toDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Servidor de control (lado TV): ServerSocket + anuncio NSD + puente sobre
 * [PlaybackSession]. Arranca solo si el ajuste lo permite (auto en TV,
 * apagado en móviles salvo opt-in). Mientras escucha levanta [TvLinkService]
 * (FGS `specialUse`) para que el enlace sobreviva al enviar la app a
 * segundo plano; si el proceso muere, el enlace muere con él.
 */
@Singleton
internal class TvLinkServerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playback: PlaybackSession,
    private val prefs: AppPreferences,
    private val resolver: ChannelResolver,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val sourceDao: SourceDao,
    private val remoteSources: RemoteSourceController,
) : TvLinkHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var stateJob: Job? = null
    private var tickJob: Job? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    /** Conexiones autenticadas; CopyOnWrite para difundir sin locks. */
    private val conns = CopyOnWriteArrayList<ClientConn>()

    private var pendingPin: String? = null
    private var pinExpiresAtMs = 0L
    private var pinExpiryJob: Job? = null

    private val _status = MutableStateFlow(TvLinkHostStatus())
    override val status = _status.asStateFlow()

    private val deviceId by lazy { prefs.tvLinkDeviceId() }
    private val deviceName by lazy { Build.MODEL ?: "IPTV" }

    init {
        scope.launch {
            prefs.tvLinkHostEnabled.collect { override ->
                val enabled = override ?: context.isTelevision()
                _status.update { it.copy(enabled = enabled) }
                if (enabled) start() else stop()
            }
        }
    }

    // -- TvLinkHost -----------------------------------------------------------

    override fun setEnabled(enabled: Boolean) = prefs.setTvLinkHostEnabled(enabled)

    override fun showPairingPin() {
        pendingPin = "%06d".format(SecureRandom().nextInt(1_000_000))
        pinExpiresAtMs = System.currentTimeMillis() + PIN_TTL_MS
        _status.update { it.copy(pairingPin = pendingPin) }
        pinExpiryJob?.cancel()
        pinExpiryJob = scope.launch {
            delay(PIN_TTL_MS)
            hidePairingPin()
        }
    }

    override fun hidePairingPin() {
        pendingPin = null
        pinExpiryJob?.cancel()
        _status.update { it.copy(pairingPin = null) }
    }

    override fun revokeClient(clientId: String) {
        prefs.setTvLinkPairedClients(
            prefs.tvLinkPairedClients().filterNot { it.startsWith("$clientId|") }.toSet(),
        )
        conns.filter { it.deviceId == clientId }.forEach { it.socket.close() }
        refreshClients()
    }

    // -- Ciclo de vida del servidor --------------------------------------------

    private fun start() {
        if (serverSocket != null) return
        // Puerto preferente (el alta manual lo conoce de antemano); si está
        // ocupado se cae a uno efímero, que siempre anuncia NSD.
        val socket = runCatching { ServerSocket(LINK_PORT_FALLBACK) }
            .recoverCatching { ServerSocket(0) }
            .getOrElse {
                Log.w(TAG, "No se pudo abrir el socket de control", it)
                _status.update { s -> s.copy(running = false) }
                return
            }
        serverSocket = socket
        registerNsd(socket.localPort)
        _status.update { it.copy(running = true, port = socket.localPort) }
        refreshClients()
        // Proceso protegido en segundo plano mientras el enlace escuche.
        TvLinkService.start(context)

        acceptJob = scope.launch {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { serve(LinkSocket(client)) }
            }
        }
        // Difunde la sesión en cada cambio (NowPlaying / errores / timer).
        stateJob = scope.launch {
            combine(playback.nowPlaying, playback.info, ::Pair).collect {
                broadcast(snapshot())
            }
        }
        // Latido de posición; el cliente extrapola entre ticks. El player
        // sólo admite lectura en el hilo principal.
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (conns.isNotEmpty()) {
                    val (pos, playing, dur) = withContext(Dispatchers.Main) {
                        val p = playback.player.value
                        Triple(
                            p?.currentPosition ?: 0,
                            p?.isPlaying == true,
                            p?.duration?.takeIf { it > 0 } ?: 0,
                        )
                    }
                    broadcast(
                        LinkMsg.Tick(positionMs = pos, playing = playing, durationMs = dur),
                    )
                }
            }
        }
    }

    private fun stop() {
        acceptJob?.cancel(); acceptJob = null
        stateJob?.cancel(); stateJob = null
        tickJob?.cancel(); tickJob = null
        registrationListener?.let { runCatching { nsd?.unregisterService(it) } }
        registrationListener = null
        conns.forEach { it.socket.close() }
        conns.clear()
        runCatching { serverSocket?.close() }
        serverSocket = null
        _status.update { it.copy(running = false, port = 0, clients = emptyList()) }
        TvLinkService.stop(context)
    }

    // -- Registro NSD -----------------------------------------------------------

    private fun registerNsd(port: Int) {
        val manager = nsd ?: return
        val info = NsdServiceInfo().apply {
            serviceName = LINK_SERVICE_PREFIX + deviceId
            serviceType = LINK_SERVICE_TYPE
            setPort(port)
            runCatching { setAttribute("n", deviceName) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD register falló: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { Log.w(TAG, "NSD no disponible", it) }
    }

    // -- Cliente conectado --------------------------------------------------------

    private suspend fun serve(link: LinkSocket) {
        val conn = ClientConn(link)
        try {
            if (!authenticate(conn)) return
            conns += conn
            refreshClients()
            link.send(snapshot())
            while (true) {
                when (val msg = link.read() ?: break) {
                    is LinkMsg.Cmd -> dispatch(conn, msg)
                    LinkMsg.Ping -> link.send(LinkMsg.Pong)
                    else -> {}
                }
            }
        } finally {
            conns -= conn
            link.close()
            refreshClients()
        }
    }

    /** Fase de auth: acepta Hellos hasta validar PIN o token conocido. */
    private suspend fun authenticate(conn: ClientConn): Boolean {
        while (true) {
            val hello = conn.socket.read() as? LinkMsg.Hello ?: return false
            when {
                hello.v > LINK_PROTOCOL_VERSION ->
                    conn.socket.send(LinkMsg.AuthFail(AuthFailReason.VERSION))

                hello.token != null &&
                    pairedClients().any {
                        it.id == hello.deviceId && it.token == hello.token
                    } -> {
                    conn.deviceId = hello.deviceId
                    conn.socket.send(LinkMsg.AuthOk(hello.token, deviceId, deviceName))
                    return true
                }

                hello.pin != null &&
                    hello.pin == pendingPin &&
                    System.currentTimeMillis() < pinExpiresAtMs -> {
                    val token = newToken()
                    val record = ClientRecord(hello.deviceId, hello.deviceName, token)
                    savePairedClient(record)
                    hidePairingPin()
                    conn.deviceId = record.id
                    conn.socket.send(LinkMsg.AuthOk(token, deviceId, deviceName))
                    return true
                }

                else -> conn.socket.send(
                    LinkMsg.AuthFail(
                        if (hello.pin != null) AuthFailReason.PIN_WRONG
                        else AuthFailReason.PIN_REQUIRED,
                    ),
                )
            }
        }
    }

    private suspend fun dispatch(conn: ClientConn, cmd: LinkMsg.Cmd) {
        when (cmd.action) {
            LinkAction.PLAY -> {
                // El channelId llega del catálogo del OTRO dispositivo: hay
                // que resolverlo en esta base de datos por identidad estable.
                val local = resolver.resolve(
                    cmd.channelId, cmd.channelKey, cmd.channelName,
                )
                if (local == null) {
                    Log.w(TAG, "PLAY sin canal local para id=${cmd.channelId}")
                } else {
                    playback.play(local.id)
                    if (cmd.positionMs > 0) {
                        scope.launch { seekWhenReady(playback, cmd.positionMs) }
                    }
                }
            }
            LinkAction.ZAP -> playback.playAdjacent(cmd.delta)
            LinkAction.ZAP_PREVIOUS -> playback.zapToPrevious()
            LinkAction.PAUSE -> withContext(Dispatchers.Main) {
                playback.player.value?.pause()
            }
            LinkAction.RESUME -> withContext(Dispatchers.Main) {
                playback.player.value?.play()
            }
            LinkAction.SEEK_BY -> withContext(Dispatchers.Main) {
                playback.seekBy(cmd.deltaMs)
            }
            LinkAction.SEEK_TO -> withContext(Dispatchers.Main) {
                playback.seekTo(cmd.positionMs)
            }
            LinkAction.SLEEP_TIMER -> playback.setSleepTimer(cmd.minutes)
            LinkAction.VOLUME_DELTA ->
                (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                    ?.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        cmd.delta.coerceIn(-1, 1),
                        AudioManager.FLAG_SHOW_UI,
                    )
            LinkAction.TAKE_HERE -> {
                val channelId = playback.nowPlaying.value?.channelId ?: -1L
                val (key, name) = resolver.describe(channelId)
                val pos = withContext(Dispatchers.Main) {
                    playback.player.value?.currentPosition ?: 0
                }
                conn.socket.send(
                    LinkMsg.Handoff(
                        channelId = channelId,
                        positionMs = pos,
                        channelKey = key,
                        channelName = name,
                    ),
                )
            }

            // -- Catálogo remoto ---------------------------------------------------
            LinkAction.CATALOG_PAGE -> serveCatalogPage(conn, cmd)
            LinkAction.CATALOG_GROUPS -> serveCatalogGroups(conn, cmd)
            LinkAction.FAVORITE_SET ->
                resolver.resolve(cmd.channelId, cmd.channelKey, cmd.channelName)?.let {
                    channelDao.setFavorite(it.id, cmd.favorite)
                }

            // -- Fuentes remotas ---------------------------------------------------
            LinkAction.SOURCES_LIST ->
                conn.socket.send(LinkMsg.Sources(sourcesSnapshot()))
            LinkAction.SOURCE_ADD_M3U ->
                cmd.url?.takeIf { it.isNotBlank() }?.let { url ->
                    runSourceOp(conn, cmd.opId) {
                        remoteSources.addM3u(cmd.sourceName, url)
                    }
                }
            LinkAction.SOURCE_ADD_XTREAM -> {
                val server = cmd.server?.takeIf { it.isNotBlank() }
                val user = cmd.username?.takeIf { it.isNotBlank() }
                if (server != null && user != null) runSourceOp(conn, cmd.opId) {
                    remoteSources.addXtream(
                        cmd.sourceName, server, user, cmd.password.orEmpty(),
                    )
                }
            }
            LinkAction.SOURCE_REFRESH ->
                findSourceByKey(cmd.sourceKey.orEmpty())?.let { s ->
                    runSourceOp(conn, cmd.opId) { remoteSources.refresh(s.id) }
                }
            LinkAction.SOURCE_SET_ACTIVE ->
                findSourceByKey(cmd.sourceKey.orEmpty())?.let { s ->
                    sourceDao.setActive(s.id)
                    conn.socket.send(LinkMsg.Sources(sourcesSnapshot()))
                }
        }
        // Confirmación inmediata para que el mando refleje el cambio ya.
        broadcast(snapshot())
    }

    // -- Catálogo remoto ----------------------------------------------------------

    private suspend fun serveCatalogPage(conn: ClientConn, cmd: LinkMsg.Cmd) {
        val kind = cmd.kind ?: Kinds.LIVE
        val (group, uncat) = groupFilter(cmd.group)
        val query = cmd.query?.takeIf { it.isNotBlank() }?.let(TextNormalizer::searchKey)
        val limit = cmd.limit.coerceIn(1, MAX_REMOTE_PAGE)
        val sid = sourceDao.findActive()?.id
        val items = sid?.let {
            channelDao.listForRemote(
                it, kind, group, uncat, null, cmd.favoritesOnly, query, limit, cmd.offset,
            )
        }.orEmpty()
        val total = sid?.let {
            channelDao.countForRemote(it, kind, group, uncat, null, cmd.favoritesOnly, query)
        } ?: 0
        conn.socket.send(
            LinkMsg.CatalogPage(
                reqId = cmd.reqId, kind = kind,
                items = items.map(::toRemoteChannelDto), total = total,
                offset = cmd.offset, done = cmd.offset + items.size >= total,
            ),
        )
    }

    private suspend fun serveCatalogGroups(conn: ClientConn, cmd: LinkMsg.Cmd) {
        val kind = cmd.kind ?: Kinds.LIVE
        val groups = sourceDao.findActive()?.id?.let { sid ->
            val names = categoryDao.visibleBySource(sid, kind)
                .associate { it.externalId to it.name }
            channelDao.remoteGroupCounts(sid, kind).map { c ->
                // Clave vacía = "sin categoría" (el nombre lo localiza el mando).
                RemoteGroupDto(
                    key = c.groupKey ?: "",
                    name = c.groupKey?.let { names[it] } ?: "",
                    count = c.count,
                )
            }
        }.orEmpty()
        conn.socket.send(LinkMsg.CatalogGroups(cmd.reqId, kind, groups))
    }

    /** Filtro de grupo por el cable: null=todo el tipo, ""=sin categoría,
     *  otro valor=clave de grupo (groupTitle/externalId de la categoría). */
    private fun groupFilter(g: String?): Pair<String?, Boolean> = when {
        g == null -> null to false
        g.isEmpty() -> null to true
        else -> g to false
    }

    private fun toRemoteChannelDto(c: ChannelEntity) = RemoteChannelDto(
        key = c.externalId,
        name = c.name,
        logoUrl = c.logoUrl,
        group = c.groupTitle,
        fav = c.isFavorite,
        kind = c.kind,
        lang = c.language,
    )

    // -- Fuentes remotas -----------------------------------------------------------

    private suspend fun sourcesSnapshot(): List<RemoteSourceDto> =
        sourceDao.observeAll().first().map { s ->
            RemoteSourceDto(
                key = sourceKeyOf(s),
                type = s.type,
                name = s.name,
                active = s.isActive,
                channels = channelDao.countBySource(s.id),
                lastSyncAt = s.lastSyncAt,
                detail = s.host ?: s.url,
            )
        }

    /** Clave opaca de fuente para el cable: tipo|host-o-url|usuario. Sirve
     *  para identificar la fuente sin exponer credenciales; el servidor la
     *  resuelve contra su propia tabla. */
    private fun sourceKeyOf(s: SourceEntity) =
        "${s.type}|${s.host ?: s.url ?: ""}|${s.username ?: ""}"

    private suspend fun findSourceByKey(key: String): SourceEntity? =
        sourceDao.observeAll().first().firstOrNull { sourceKeyOf(it) == key }

    /** Lanza la operación de fuente en segundo plano e informa por `SourceOp`.
     *  La importación sigue aunque el mando se desconecte a medias — los
     *  envíos fallidos no tumban el flujo. */
    private fun runSourceOp(
        conn: ClientConn,
        opId: String?,
        op: () -> Flow<RemoteSourceSync>,
    ) {
        val id = opId ?: "op"
        scope.launch {
            runCatching {
                op().collect { s ->
                    conn.socket.send(
                        LinkMsg.SourceOp(
                            opId = id,
                            phase = when {
                                s.failed != null -> "failed"
                                s.done -> "done"
                                else -> "progress"
                            },
                            step = s.step, count = s.count, error = s.failed,
                        ),
                    )
                }
                conn.socket.send(LinkMsg.Sources(sourcesSnapshot()))
            }.onFailure {
                conn.socket.send(
                    LinkMsg.SourceOp(opId = id, phase = "failed", error = it.message),
                )
            }
        }
    }

    // -- Estado y difusión --------------------------------------------------------

    private suspend fun snapshot(): LinkMsg.State {
        // ExoPlayer exige el hilo principal; la lectura de estado es puntual.
        val (pos, playing, dur) = withContext(Dispatchers.Main) {
            val p = playback.player.value
            Triple(
                p?.currentPosition ?: 0,
                p?.isPlaying == true,
                p?.duration?.takeIf { it > 0 } ?: 0,
            )
        }
        val now = playback.nowPlaying.value
        val key = now?.let { resolver.describe(it.channelId).first }
        return LinkMsg.State(
            nowPlaying = now?.toDto(channelKey = key),
            error = playback.info.value.error?.name,
            reconnectAttempt = playback.info.value.reconnectAttempt,
            isPlaying = playing,
            positionMs = pos,
            durationMs = dur,
            sleepTimerEndAtMs = playback.info.value.sleepTimerEndAtMs,
        )
    }

    private fun broadcast(msg: LinkMsg) = conns.forEach { it.socket.send(msg) }

    // -- Registro de vinculados -----------------------------------------------------

    private data class ClientRecord(val id: String, val name: String, val token: String)

    private class ClientConn(val socket: LinkSocket) {
        @Volatile var deviceId: String = ""
    }

    private fun pairedClients(): List<ClientRecord> =
        prefs.tvLinkPairedClients().mapNotNull { rec ->
            val parts = rec.split("|")
            if (parts.size == 3) ClientRecord(parts[0], parts[1], parts[2]) else null
        }

    private fun savePairedClient(record: ClientRecord) {
        val safeName = record.name.replace("|", " ")
        prefs.setTvLinkPairedClients(
            prefs.tvLinkPairedClients()
                .filterNot { it.startsWith("${record.id}|") }
                .toSet() + "${record.id}|$safeName|${record.token}",
        )
    }

    private fun refreshClients() {
        val paired = pairedClients()
        _status.update { s ->
            s.copy(
                clients = paired.map { p ->
                    LinkedClient(
                        id = p.id,
                        name = p.name,
                        connected = conns.any { it.deviceId == p.id },
                    )
                },
            )
        }
    }

    private fun newToken(): String =
        ByteArray(16).also(SecureRandom()::nextBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val TAG = "TvLinkServer"
        const val TICK_MS = 2_000L
        const val PIN_TTL_MS = 3 * 60_000L
        const val MAX_REMOTE_PAGE = 300
    }
}
