package com.iptv.feature.tvlink.client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.iptv.core.common.playback.NowPlaying
import com.iptv.core.common.playback.PlaybackErrorKind
import com.iptv.core.common.playback.PlaybackInfo
import com.iptv.core.common.playback.PlaybackSession
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.common.tvlink.DiscoveredTv
import com.iptv.core.common.tvlink.LinkFailure
import com.iptv.core.common.tvlink.PairedTv
import com.iptv.core.common.tvlink.RemoteCatalogState
import com.iptv.core.common.tvlink.RemoteGroup
import com.iptv.core.common.tvlink.RemoteSession
import com.iptv.core.common.tvlink.RemoteSource
import com.iptv.core.common.tvlink.SourceOpState
import com.iptv.core.common.tvlink.TvLinkClient
import com.iptv.core.common.tvlink.TvLinkConnection
import com.iptv.core.common.tvlink.TvLinkConnectionState
import com.iptv.feature.tvlink.ChannelResolver
import com.iptv.feature.tvlink.wire.AuthFailReason
import com.iptv.feature.tvlink.wire.LINK_PROTOCOL_VERSION
import com.iptv.feature.tvlink.wire.LINK_SERVICE_PREFIX
import com.iptv.feature.tvlink.wire.LINK_SERVICE_TYPE
import com.iptv.feature.tvlink.wire.LinkAction
import com.iptv.feature.tvlink.wire.LinkMsg
import com.iptv.feature.tvlink.wire.LinkSocket
import com.iptv.feature.tvlink.wire.seekWhenReady
import com.iptv.feature.tvlink.wire.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lado mando: descubre TVs por NSD, empareja con PIN y mantiene una
 * [RemoteSession] espejo sobre el socket. Reconexión con backoff mientras
 * el usuario quiera estar conectado.
 */
@Singleton
internal class TvLinkClientImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences,
    private val playback: PlaybackSession,
    private val resolver: ChannelResolver,
) : TvLinkClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val myDeviceId by lazy { prefs.tvLinkDeviceId() }
    private val myDeviceName by lazy { Build.MODEL ?: "IPTV" }

    private val _discovered = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    override val discovered = _discovered.asStateFlow()

    private val _pairedTvs = MutableStateFlow(
        loadKnownTvs().filter { it.id != myDeviceId }.map { it.toPublic() },
    )
    override val pairedTvs = _pairedTvs.asStateFlow()

    private val _connection = MutableStateFlow(TvLinkConnectionState())
    override val connection = _connection.asStateFlow()

    private val _remote = MutableStateFlow<RemoteSession?>(null)
    override val remote = _remote.asStateFlow()

    private val _handoffApplied = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    override val handoffApplied: Flow<Long> = _handoffApplied.asSharedFlow()

    private var discoveryJob: Job? = null
    private var connJob: Job? = null

    // Se escriben desde el hilo de UI (submitPin/disconnect) y se leen en
    // el bucle de conexión (Dispatchers.IO): todos volátiles.
    @Volatile private var link: LinkSocket? = null
    @Volatile private var connectedDeviceId: String? = null
    @Volatile private var wantConnected = false

    /**
     * PIN que el usuario ya envió. Si el socket cae entre el PIN_REQUIRED y
     * el envío, la TV lo vuelve a pedir al reconectar y lo reenviamos
     * automáticamente — antes se perdía en silencio y "vincular" parecía
     * no hacer nada.
     */
    @Volatile private var pendingPin: String? = null

    // -- Descubrimiento NSD -------------------------------------------------------

    override fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        val manager = nsd ?: return
        discoveryJob = scope.launch {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock = wifi?.createMulticastLock("tvlink")
                ?.apply { setReferenceCounted(true) }
            runCatching { multicastLock?.acquire() }
            try {
                callbackFlow { collectDiscovery(manager) }.collect(::applyDiscoveryEvent)
            } finally {
                runCatching { multicastLock?.release() }
            }
        }
    }

    override fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _discovered.value = emptyList()
    }

    private sealed interface DiscoveryEvent {
        data class Found(val tv: DiscoveredTv) : DiscoveryEvent
        data class Lost(val serviceName: String) : DiscoveryEvent
    }

    /** Registra el listener NSD; la resolución va en cola (una a la vez). */
    private suspend fun ProducerScope<DiscoveryEvent>.collectDiscovery(manager: NsdManager) {
        val lock = Any()
        val resolveQueue = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        var resolveListener: NsdManager.ResolveListener? = null

        fun pumpResolve() {
            val next = synchronized(lock) {
                if (resolving || resolveQueue.isEmpty()) return
                resolving = true
                resolveQueue.removeFirst()
            }
            resolveListener?.let { manager.resolveService(next, it) }
        }

        resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                synchronized(lock) { resolving = false }
                pumpResolve()
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                // En algunos dispositivos el nombre resuelto llega con el
                // dominio colgado ("IPTV-x._iptvlink._tcp.local."): extraer
                // el id sólo de la primera etiqueta o la TV nunca se lista.
                val shortName = info.serviceName.substringBefore('.')
                if (shortName.startsWith(LINK_SERVICE_PREFIX)) {
                    val host = info.host?.hostAddress
                    if (host != null) {
                        val display = info.attributes["n"]
                            ?.let { String(it, Charsets.UTF_8) }
                            ?: shortName
                        trySend(
                            DiscoveryEvent.Found(
                                DiscoveredTv(
                                    id = shortName.removePrefix(LINK_SERVICE_PREFIX),
                                    name = display,
                                    host = host,
                                    port = info.port,
                                ),
                            ),
                        )
                    }
                }
                synchronized(lock) { resolving = false }
                pumpResolve()
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD discovery falló: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.startsWith(LINK_SERVICE_TYPE.trimEnd('.'))) return
                synchronized(lock) { resolveQueue.addLast(info) }
                pumpResolve()
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                trySend(DiscoveryEvent.Lost(info.serviceName))
            }
        }

        runCatching {
            manager.discoverServices(LINK_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
        awaitClose { runCatching { manager.stopServiceDiscovery(listener) } }
    }

    private fun applyDiscoveryEvent(event: DiscoveryEvent) {
        when (event) {
            // NSD ve también el propio anuncio del dispositivo: nunca se
            // lista — conectarse a uno mismo sólo confunde (pide su propio PIN).
            is DiscoveryEvent.Found -> _discovered.update { list ->
                if (event.tv.id == myDeviceId) {
                    list
                } else {
                    (list.filterNot { it.id == event.tv.id } + event.tv)
                        .sortedBy { it.name }
                }
            }
            is DiscoveryEvent.Lost -> _discovered.update { list ->
                list.filterNot {
                    LINK_SERVICE_PREFIX + it.id == event.serviceName.substringBefore('.')
                }
            }
        }
    }

    // -- Conexión ----------------------------------------------------------------

    override fun connect(host: String, port: Int, deviceId: String?, deviceName: String?) {
        disconnect()
        wantConnected = true
        connJob = scope.launch { connectionLoop(host, port, deviceId, deviceName) }
    }

    override fun submitPin(pin: String) {
        val clean = pin.trim()
        pendingPin = clean
        // send() escribe el socket de forma síncrona en el hilo llamante y
        // el botón "vincular" llega desde main: Android lo tumbaba con
        // NetworkOnMainThreadException, el runCatching de send lo tragaba y
        // el PIN nunca salía del móvil — "vincular" no hacía literalmente
        // nada. Todo envío va por IO.
        scope.launch { link?.send(hello(pin = clean)) }
    }

    override fun disconnect() {
        wantConnected = false
        connJob?.cancel()
        connJob = null
        link?.close()
        link = null
        connectedDeviceId = null
        pendingPin = null
        (_remote.value as? RemoteSessionImpl)?.dispose()
        _remote.value = null
        _connection.value = TvLinkConnectionState()
    }

    override fun unpair(deviceId: String) {
        persistKnownTvs(loadKnownTvs().filterNot { it.id == deviceId })
        if (connectedDeviceId == deviceId) disconnect()
    }

    private suspend fun connectionLoop(
        host: String,
        port: Int,
        deviceId: String?,
        deviceName: String?,
    ) {
        var knownId = deviceId
        var name = deviceName
        var backoff = 1_000L
        var failures = 0
        var everAuthed = false

        while (currentCoroutineContext().isActive && wantConnected) {
            _connection.value = TvLinkConnectionState(TvLinkConnection.CONNECTING, name)
            val socket = runCatching {
                Socket().apply {
                    tcpNoDelay = true
                    keepAlive = true
                    connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                }
            }.getOrNull()
            if (socket == null) {
                failures++
                if (!everAuthed && failures >= MAX_FIRST_FAILURES) {
                    _connection.value = TvLinkConnectionState(
                        TvLinkConnection.FAILED, name, LinkFailure.UNREACHABLE,
                    )
                    break
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                continue
            }

            val sock = LinkSocket(socket)
            link = sock
            sock.send(
                hello(
                    token = knownId?.let { id -> loadKnownTvs().find { it.id == id }?.token },
                ),
            )

            while (wantConnected) {
                when (val msg = sock.read() ?: break) {
                    is LinkMsg.AuthOk -> {
                        knownId = msg.deviceId
                        name = msg.deviceName
                        connectedDeviceId = msg.deviceId
                        pendingPin = null
                        everAuthed = true
                        failures = 0
                        backoff = 1_000L
                        persistTv(msg.deviceId, msg.deviceName, msg.token, host, port)
                        _connection.value = TvLinkConnectionState(
                            TvLinkConnection.CONNECTED, msg.deviceName,
                        )
                        _remote.value = RemoteSessionImpl(sock, scope, resolver)
                    }
                    is LinkMsg.AuthFail -> when (msg.reason) {
                        AuthFailReason.PIN_REQUIRED -> {
                            _connection.value = TvLinkConnectionState(
                                TvLinkConnection.PIN_REQUIRED, name,
                            )
                            // Si el usuario ya había enviado el PIN (p.ej. el
                            // socket cayó justo después), reenviarlo en cuanto
                            // la TV lo vuelve a pedir.
                            pendingPin?.let { sock.send(hello(pin = it)) }
                        }
                        AuthFailReason.PIN_WRONG -> {
                            // Caducó o era erróneo: no insistir con el mismo.
                            pendingPin = null
                            _connection.value = TvLinkConnectionState(
                                TvLinkConnection.PIN_REQUIRED, name,
                                failure = LinkFailure.PIN_WRONG,
                            )
                        }
                        AuthFailReason.VERSION -> {
                            _connection.value = TvLinkConnectionState(
                                TvLinkConnection.FAILED, name, LinkFailure.VERSION,
                            )
                            wantConnected = false
                        }
                        AuthFailReason.REJECTED -> {
                            _connection.value = TvLinkConnectionState(
                                TvLinkConnection.FAILED, name, LinkFailure.PIN_WRONG,
                            )
                            wantConnected = false
                        }
                    }
                    is LinkMsg.State -> remoteImpl?.onState(msg)
                    is LinkMsg.Tick -> remoteImpl?.onTick(msg)
                    is LinkMsg.CatalogPage -> remoteImpl?.onCatalogPage(msg)
                    is LinkMsg.CatalogGroups -> remoteImpl?.onCatalogGroups(msg)
                    is LinkMsg.Sources -> remoteImpl?.onSources(msg)
                    is LinkMsg.SourceOp -> remoteImpl?.onSourceOp(msg)
                    is LinkMsg.Handoff -> applyHandoff(msg)
                    LinkMsg.Ping -> sock.send(LinkMsg.Pong)
                    else -> {}
                }
            }

            sock.close()
            link = null
            connectedDeviceId = null
            (_remote.value as? RemoteSessionImpl)?.dispose()
            _remote.value = null
            if (!wantConnected) break
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
        if (_connection.value.state != TvLinkConnection.FAILED) {
            _connection.value = TvLinkConnectionState(
                if (wantConnected) TvLinkConnection.CONNECTING else TvLinkConnection.DISCONNECTED,
                name,
            )
        }
    }

    private fun hello(pin: String? = null, token: String? = null) = LinkMsg.Hello(
        v = LINK_PROTOCOL_VERSION,
        deviceId = myDeviceId,
        deviceName = myDeviceName,
        pin = pin,
        token = token,
    )

    private val remoteImpl get() = _remote.value as? RemoteSessionImpl

    // -- Handoff remoto → local -----------------------------------------------------

    private fun applyHandoff(msg: LinkMsg.Handoff) {
        if (msg.channelId <= 0) return
        scope.launch {
            // El channelId es de la base de datos del HOST: resolver el
            // equivalente local por externalId/nombre antes de reproducir.
            val local = resolver.resolve(
                msg.channelId, msg.channelKey, msg.channelName,
            )
            if (local == null) {
                Log.w(TAG, "Handoff sin canal local para id=${msg.channelId}")
                return@launch
            }
            playback.play(local.id)
            if (msg.positionMs > 0) seekWhenReady(playback, msg.positionMs)
            _handoffApplied.tryEmit(local.id)
        }
    }

    // -- Persistencia de TVs conocidas ------------------------------------------------

    private data class KnownTv(
        val id: String,
        val name: String,
        val token: String,
        val host: String?,
        val port: Int,
    ) {
        fun toPublic() = PairedTv(id, name, host, port)
        fun toRecord() = "$id|${name.replace("|", " ")}|$token|${host.orEmpty()}|$port"
    }

    /** Registro "id|name|token|host|port" — el host/puerto es último conocido. */
    private fun loadKnownTvs(): List<KnownTv> =
        prefs.tvLinkKnownTvs().mapNotNull { rec ->
            val p = rec.split("|")
            if (p.size == 5) {
                KnownTv(
                    id = p[0], name = p[1], token = p[2],
                    host = p[3].ifEmpty { null }, port = p[4].toIntOrNull() ?: 0,
                )
            } else {
                null
            }
        }

    private fun persistTv(id: String, name: String, token: String, host: String, port: Int) {
        persistKnownTvs(
            loadKnownTvs().filterNot { it.id == id } + KnownTv(id, name, token, host, port),
        )
    }

    private fun persistKnownTvs(list: List<KnownTv>) {
        prefs.setTvLinkKnownTvs(list.map { it.toRecord() }.toSet())
        _pairedTvs.value = list.filter { it.id != myDeviceId }.map { it.toPublic() }
    }

    private companion object {
        const val TAG = "TvLinkClient"
        const val CONNECT_TIMEOUT_MS = 4_000
        const val MAX_FIRST_FAILURES = 4
        const val MAX_BACKOFF_MS = 15_000L
    }
}

/**
 * Espejo mutable de la sesión remota: la posición interpola el último tick
 * (los relojes de ambos dispositivos pueden diferir — se usa el reloj local
 * en la recepción, no el timestamp del host).
 */
private class RemoteSessionImpl(
    private val link: LinkSocket,
    private val scope: CoroutineScope,
    private val resolver: ChannelResolver,
) : RemoteSession {

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying = _nowPlaying.asStateFlow()

    private val _info = MutableStateFlow(PlaybackInfo())
    override val info = _info.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying = _isPlaying.asStateFlow()

    // -- Espejo de catálogo y fuentes del host -----------------------------------

    private val _catalog = MutableStateFlow(RemoteCatalogState())
    override val catalog = _catalog.asStateFlow()

    private val _remoteSources = MutableStateFlow<List<RemoteSource>>(emptyList())
    override val remoteSources = _remoteSources.asStateFlow()

    private val _sourceOp = MutableStateFlow<SourceOpState?>(null)
    override val sourceOp = _sourceOp.asStateFlow()

    /** Correlación reqId → filtro pedido: respuestas tardías de un filtro
     *  ya reemplazado (cambio de pestaña/búsqueda) se descartan. */
    private var reqCounter = 0
    private val pendingCatalog = mutableMapOf<Int, CatalogFilter>()
    private val pendingGroups = mutableSetOf<Int>()

    private data class CatalogFilter(
        val kind: String,
        val query: String?,
        val group: String?,
        val favoritesOnly: Boolean,
    )

    private var tickBaseMs = 0L
    private var tickAtMs = 0L

    private val interpolator = scope.launch {
        while (isActive) {
            delay(1_000)
            if (_isPlaying.value) {
                _positionMs.value = tickBaseMs + (System.currentTimeMillis() - tickAtMs)
            }
        }
    }

    fun onState(s: LinkMsg.State) {
        _nowPlaying.value = s.nowPlaying?.toDomain()
        _info.value = PlaybackInfo(
            error = s.error?.let {
                runCatching { PlaybackErrorKind.valueOf(it) }
                    .getOrDefault(PlaybackErrorKind.GENERIC)
            },
            reconnectAttempt = s.reconnectAttempt,
            sleepTimerEndAtMs = s.sleepTimerEndAtMs,
        )
        _isPlaying.value = s.isPlaying
        _durationMs.value = s.durationMs
        tickBaseMs = s.positionMs
        tickAtMs = System.currentTimeMillis()
        _positionMs.value = s.positionMs
    }

    fun onTick(t: LinkMsg.Tick) {
        tickBaseMs = t.positionMs
        tickAtMs = System.currentTimeMillis()
        _isPlaying.value = t.playing
        _positionMs.value = t.positionMs
        if (t.durationMs > 0) _durationMs.value = t.durationMs
    }

    fun dispose() = interpolator.cancel()

    override fun play(channelId: Long, positionMs: Long?) {
        // El host recibe id + identidad estable: sus channelId son otra base
        // de datos y coincidir por accidente reproduciría un canal erróneo.
        scope.launch {
            val (key, name) = resolver.describe(channelId)
            send(
                LinkMsg.Cmd(
                    action = LinkAction.PLAY,
                    channelId = channelId,
                    channelKey = key,
                    channelName = name,
                    positionMs = positionMs ?: 0,
                ),
            )
        }
    }

    override fun zap(delta: Int) = send(LinkMsg.Cmd(LinkAction.ZAP, delta = delta))
    override fun zapToPrevious() = send(LinkMsg.Cmd(LinkAction.ZAP_PREVIOUS))
    override fun pause() = send(LinkMsg.Cmd(LinkAction.PAUSE))
    override fun resume() = send(LinkMsg.Cmd(LinkAction.RESUME))
    override fun seekBy(deltaMs: Long) = send(LinkMsg.Cmd(LinkAction.SEEK_BY, deltaMs = deltaMs))
    override fun seekTo(positionMs: Long) {
        // Optimista: el slider se mueve ya; el próximo tick del host confirma.
        _positionMs.value = positionMs
        tickBaseMs = positionMs
        tickAtMs = System.currentTimeMillis()
        send(LinkMsg.Cmd(LinkAction.SEEK_TO, positionMs = positionMs))
    }
    override fun setSleepTimer(minutes: Int?) =
        send(LinkMsg.Cmd(LinkAction.SLEEP_TIMER, minutes = minutes))
    override fun volumeBy(delta: Int) =
        send(LinkMsg.Cmd(LinkAction.VOLUME_DELTA, delta = delta))
    override fun takeToThisDevice() = send(LinkMsg.Cmd(LinkAction.TAKE_HERE))

    // -- Catálogo remoto -----------------------------------------------------------

    override fun browseCatalog(
        kind: String,
        query: String?,
        group: String?,
        favoritesOnly: Boolean,
        offset: Int,
    ) {
        val filter = CatalogFilter(kind, query, group, favoritesOnly)
        _catalog.update { cur ->
            if (offset == 0) {
                RemoteCatalogState(
                    kind = kind, query = query, group = group,
                    favoritesOnly = favoritesOnly, loading = true,
                    groups = cur.groups,
                )
            } else {
                cur.copy(loading = true)
            }
        }
        if (offset == 0) browseGroups(kind)
        val reqId = ++reqCounter
        pendingCatalog[reqId] = filter
        send(
            LinkMsg.Cmd(
                action = LinkAction.CATALOG_PAGE, reqId = reqId, kind = kind,
                query = query, group = group, favoritesOnly = favoritesOnly,
                offset = offset, limit = REMOTE_PAGE_SIZE,
            ),
        )
    }

    override fun browseGroups(kind: String) {
        val reqId = ++reqCounter
        pendingGroups += reqId
        send(LinkMsg.Cmd(LinkAction.CATALOG_GROUPS, reqId = reqId, kind = kind))
    }

    fun onCatalogPage(p: LinkMsg.CatalogPage) {
        val filter = pendingCatalog.remove(p.reqId) ?: return
        _catalog.update { cur ->
            val expected = CatalogFilter(cur.kind, cur.query, cur.group, cur.favoritesOnly)
            if (filter != expected) return@update cur.copy(loading = false)
            val merged = if (p.offset == 0) {
                p.items.map { it.toDomain() }
            } else {
                cur.items + p.items.map { it.toDomain() }
            }
            cur.copy(items = merged, total = p.total, loading = !p.done)
        }
    }

    fun onCatalogGroups(g: LinkMsg.CatalogGroups) {
        pendingGroups.remove(g.reqId)
        _catalog.update { cur ->
            if (cur.kind != g.kind) {
                cur
            } else {
                cur.copy(
                    groups = g.groups.map { RemoteGroup(it.key, it.name, it.count) },
                )
            }
        }
    }

    override fun playKey(channelKey: String, channelName: String?) =
        send(
            LinkMsg.Cmd(
                LinkAction.PLAY,
                channelKey = channelKey,
                channelName = channelName,
            ),
        )

    override fun setFavoriteKey(channelKey: String, favorite: Boolean) {
        // Optimista: la fila cambia ya; el host confirma en el próximo State.
        _catalog.update { c ->
            c.copy(
                items = c.items.map {
                    if (it.key == channelKey) it.copy(isFavorite = favorite) else it
                },
            )
        }
        send(
            LinkMsg.Cmd(
                LinkAction.FAVORITE_SET,
                channelKey = channelKey,
                favorite = favorite,
            ),
        )
    }

    // -- Fuentes remotas ------------------------------------------------------------

    override fun requestSources() = send(LinkMsg.Cmd(LinkAction.SOURCES_LIST))

    override fun addM3uSource(name: String?, url: String): String =
        launchSourceOp("m3u") { opId ->
            LinkMsg.Cmd(
                LinkAction.SOURCE_ADD_M3U,
                opId = opId, sourceName = name, url = url,
            )
        }

    override fun addXtreamSource(
        name: String?,
        server: String,
        username: String,
        password: String,
    ): String = launchSourceOp("xc") { opId ->
        LinkMsg.Cmd(
            LinkAction.SOURCE_ADD_XTREAM,
            opId = opId, sourceName = name, server = server,
            username = username, password = password,
        )
    }

    override fun refreshSource(sourceKey: String) {
        launchSourceOp("ref") { opId ->
            LinkMsg.Cmd(LinkAction.SOURCE_REFRESH, opId = opId, sourceKey = sourceKey)
        }
    }

    override fun setActiveSource(sourceKey: String) =
        send(LinkMsg.Cmd(LinkAction.SOURCE_SET_ACTIVE, sourceKey = sourceKey))

    /** opId correlativo + estado inicial; devuelve el opId para la UI. */
    private inline fun launchSourceOp(prefix: String, build: (String) -> LinkMsg.Cmd): String {
        val opId = "$prefix-${++reqCounter}"
        _sourceOp.value = SourceOpState(opId = opId, running = true)
        send(build(opId))
        return opId
    }

    fun onSources(s: LinkMsg.Sources) {
        _remoteSources.value = s.items.map {
            RemoteSource(
                key = it.key, type = it.type, name = it.name,
                active = it.active, channels = it.channels,
                lastSyncAt = it.lastSyncAt, detail = it.detail,
            )
        }
    }

    fun onSourceOp(o: LinkMsg.SourceOp) {
        _sourceOp.value = SourceOpState(
            opId = o.opId,
            running = o.phase == "progress",
            step = o.step, count = o.count, error = o.error,
        )
    }

    // Los botones del mando llaman desde el hilo de UI y el socket escribe
    // de forma síncrona: saltar a IO o morían con NetworkOnMainThreadException
    // igual que submitPin.
    private fun send(msg: LinkMsg) {
        scope.launch { link.send(msg) }
    }

    private companion object {
        const val REMOTE_PAGE_SIZE = 150
    }
}
