package com.iptv.core.common.tvlink

import com.iptv.core.common.playback.NowPlaying
import com.iptv.core.common.playback.PlaybackInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Vinculación móvil ↔ TV en la LAN (patrón [com.iptv.core.common.cast.CastController]):
 * contratos neutros aquí, implementación en `:feature:tvlink`. La misma APK
 * ejerce de host (dispositivo controlado, típicamente la TV) y de cliente
 * (mando, típicamente el móvil) — el transporte es un socket TCP propio con
 * mensajes NDJSON, sin dependencia de Play services (funciona en Fire TV).
 */

/** TV/host descubierto por NSD. [id] es estable (va en el nombre del servicio). */
data class DiscoveredTv(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
)

/** TV vinculada persistida por el cliente (aunque ahora mismo esté apagada). */
data class PairedTv(
    val id: String,
    val name: String,
    val host: String?,
    val port: Int,
)

/** Cliente vinculado visto por el host. */
data class LinkedClient(
    val id: String,
    val name: String,
    val connected: Boolean,
)

data class TvLinkHostStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    /** Puerto real del socket (se anuncia por NSD; visible para alta manual). */
    val port: Int = 0,
    /** PIN efímero visible para vincular; null cuando no hay pairing abierto. */
    val pairingPin: String? = null,
    val clients: List<LinkedClient> = emptyList(),
)

/** Lado "dispositivo controlado". Un singleton de proceso. */
interface TvLinkHost {
    val status: StateFlow<TvLinkHostStatus>

    /** Activa/desactiva el servidor de control (persistido). */
    fun setEnabled(enabled: Boolean)

    /** Genera y expone un PIN efímero para vincular un nuevo dispositivo. */
    fun showPairingPin()
    fun hidePairingPin()

    /** Desvincula un cliente; si está conectado se desconecta. */
    fun revokeClient(clientId: String)
}

enum class TvLinkConnection { DISCONNECTED, CONNECTING, PIN_REQUIRED, CONNECTED, FAILED }
enum class LinkFailure { UNREACHABLE, PIN_WRONG, VERSION, DROPPED }

// -- Modelos del espejo remoto (catálogo + fuentes del host) -------------------

/** Canal del catálogo del host identificado por su clave estable (externalId). */
data class RemoteChannel(
    val key: String,
    val name: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val isFavorite: Boolean = false,
    val kind: String = "LIVE",
    val language: String = "",
)

/** Grupo/categoría del host con su recuento para los chips del catálogo. */
data class RemoteGroup(val key: String?, val name: String, val count: Int)

/**
 * Vista actual del catálogo del host: filtro vigente, páginas acumuladas
 * (offset/limit) y lista de grupos para los filtros.
 */
data class RemoteCatalogState(
    val kind: String = "LIVE",
    val query: String? = null,
    val group: String? = null,
    val favoritesOnly: Boolean = false,
    val items: List<RemoteChannel> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val groups: List<RemoteGroup> = emptyList(),
)

/** Fuente configurada en el host. */
data class RemoteSource(
    val key: String,
    val type: String,
    val name: String,
    val active: Boolean,
    val channels: Int,
    val lastSyncAt: Long? = null,
    val detail: String? = null,
)

/** Progreso en vivo de una operación de fuente en el host. */
data class SourceOpState(
    val opId: String,
    val running: Boolean,
    val step: String? = null,
    val count: Int = 0,
    val error: String? = null,
)

data class TvLinkConnectionState(
    val state: TvLinkConnection = TvLinkConnection.DISCONNECTED,
    val deviceName: String? = null,
    val failure: LinkFailure? = null,
)

/**
 * Espejo remoto de [com.iptv.core.common.playback.PlaybackSession]: mismo
 * modelo mental, contrato propio (no puede exponer un Player local — el
 * vídeo decodifica en el otro dispositivo).
 */
interface RemoteSession {
    val nowPlaying: StateFlow<NowPlaying?>
    val info: StateFlow<PlaybackInfo>
    /** Posición extrapolada en el cliente a partir de los ticks del host. */
    val positionMs: StateFlow<Long>
    /** Duración del contenido (0 = desconocida o directo). */
    val durationMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>

    /** [positionMs] != null → handoff: reanudar en el host donde iba el otro. */
    fun play(channelId: Long, positionMs: Long? = null)
    fun zap(delta: Int)
    fun zapToPrevious()
    fun pause()
    fun resume()
    fun seekBy(deltaMs: Long)
    /** Seek absoluto: arrastrar la línea temporal a un punto del contenido. */
    fun seekTo(positionMs: Long)
    fun setSleepTimer(minutes: Int?)
    /** Subir/bajar el volumen de reproducción del host (±1 paso de sistema). */
    fun volumeBy(delta: Int)

    /** Pide al host canal + posición para reanudar la reproducción aquí. */
    fun takeToThisDevice()

    // -- Catálogo y fuentes del host -------------------------------------------

    /** Espejo del catálogo del host: se rellena pidiendo páginas/grupos. */
    val catalog: StateFlow<RemoteCatalogState>
    /** Fuentes configuradas en el host. */
    val remoteSources: StateFlow<List<RemoteSource>>
    /** Progreso de la operación de fuente en curso (alta/refresco). */
    val sourceOp: StateFlow<SourceOpState?>

    /** Pide una página del catálogo del host con el filtro dado. */
    fun browseCatalog(
        kind: String,
        query: String? = null,
        group: String? = null,
        favoritesOnly: Boolean = false,
        offset: Int = 0,
    )

    /** Pide la lista de grupos/categorías del host para un tipo. */
    fun browseGroups(kind: String)

    /** Reproduce en el host el canal referido por su clave estable. */
    fun playKey(channelKey: String, channelName: String? = null)

    /** Marca/quita favorito en el host. */
    fun setFavoriteKey(channelKey: String, favorite: Boolean)

    /** Pide la lista de fuentes del host. */
    fun requestSources()

    /** Alta de fuente M3U por URL en el host; devuelve el opId para seguirla. */
    fun addM3uSource(name: String?, url: String): String

    /** Alta de cuenta Xtream en el host; devuelve el opId para seguirla. */
    fun addXtreamSource(name: String?, server: String, username: String, password: String): String

    /** Resincroniza una fuente existente del host. */
    fun refreshSource(sourceKey: String)

    /** Activa una fuente del host (su catálogo pasa a ser el vigente). */
    fun setActiveSource(sourceKey: String)
}

/** Lado "mando". Un singleton de proceso. */
interface TvLinkClient {
    val discovered: StateFlow<List<DiscoveredTv>>
    val pairedTvs: StateFlow<List<PairedTv>>
    val connection: StateFlow<TvLinkConnectionState>
    val remote: StateFlow<RemoteSession?>

    /**
     * Emite el channelId cuando un handoff remoto (takeToThisDevice) ya fue
     * adoptado por la sesión local — la UI navega al reproductor propio.
     */
    val handoffApplied: Flow<Long>

    fun startDiscovery()
    fun stopDiscovery()

    /** Conecta a un host por IP/puerto (NSD o manual). */
    fun connect(host: String, port: Int, deviceId: String? = null, deviceName: String? = null)
    fun submitPin(pin: String)
    fun disconnect()
    fun unpair(deviceId: String)
}
