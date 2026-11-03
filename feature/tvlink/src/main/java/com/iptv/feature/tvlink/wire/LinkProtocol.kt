package com.iptv.feature.tvlink.wire

import com.iptv.core.common.playback.NowPlaying
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Protocolo del enlace móvil ↔ TV: NDJSON sobre TCP (un mensaje por línea).
 *
 * - El cliente abre con [LinkMsg.Hello]; el host responde [LinkMsg.AuthOk]
 *   (token nuevo o el ya conocido) o [LinkMsg.AuthFail]. Tras AuthOk el host
 *   empuja [LinkMsg.State] en cada cambio y [LinkMsg.Tick] ~cada 2 s.
 * - `ignoreUnknownKeys` + `encodeDefaults=false` toleran versiones
 *   distintas de la misma APK en ambos extremos: campos nuevos se ignoran y
 *   mensajes desconocidos se descartan en el read loop.
 */
internal const val LINK_PROTOCOL_VERSION = 1
internal const val LINK_SERVICE_TYPE = "_iptvlink._tcp."
internal const val LINK_SERVICE_PREFIX = "IPTV-"
internal const val LINK_PORT_FALLBACK = 45230

internal val linkJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

@Serializable
internal data class NowPlayingDto(
    val channelId: Long,
    val sourceId: Long,
    val name: String,
    val logoUrl: String? = null,
    val isLive: Boolean,
    val isFavorite: Boolean = false,
    /** externalId del canal: el id local no cruza dispositivos; la clave
     *  permite al mando marcar "sonando ahora" en su espejo de catálogo. */
    val channelKey: String? = null,
)

internal fun NowPlaying.toDto(channelKey: String? = null) = NowPlayingDto(
    channelId, sourceId, name, logoUrl, isLive, isFavorite, channelKey,
)

internal fun NowPlayingDto.toDomain() = NowPlaying(
    channelId = channelId,
    sourceId = sourceId,
    name = name,
    logoUrl = logoUrl,
    isLive = isLive,
    isFavorite = isFavorite,
    channelKey = channelKey,
)

@Serializable
internal enum class LinkAction {
    PLAY, ZAP, ZAP_PREVIOUS, PAUSE, RESUME, SEEK_BY, SLEEP_TIMER, TAKE_HERE,
    /** Seek absoluto (positionMs): el slider del mando arrastra a un punto. */
    SEEK_TO,
    /** delta = dirección (−1/0/+1 pasos de volumen del sistema del host). */
    VOLUME_DELTA,
    /** Catálogo remoto: página de items y lista de grupos/categorías. */
    CATALOG_PAGE, CATALOG_GROUPS,
    /** Marca/quita favorito en el host (favorite + channelKey). */
    FAVORITE_SET,
    /** Fuentes del host: listado, alta M3U/Xtream, resincronización, activar. */
    SOURCES_LIST, SOURCE_ADD_M3U, SOURCE_ADD_XTREAM, SOURCE_REFRESH,
    SOURCE_SET_ACTIVE,
}

/** Canal del catálogo remoto: identidad estable, sin streamUrl (no hace
 *  falta — el play viaja por [key] y no filtra URLs al mando). */
@Serializable
internal data class RemoteChannelDto(
    val key: String,
    val name: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val fav: Boolean = false,
    val kind: String,
    val lang: String = "",
)

@Serializable
internal data class RemoteGroupDto(
    val key: String,
    val name: String,
    val count: Int,
)

internal fun RemoteChannelDto.toDomain() = com.iptv.core.common.tvlink.RemoteChannel(
    key = key, name = name, logoUrl = logoUrl, group = group,
    isFavorite = fav, kind = kind, language = lang,
)

/** Fuente del host: la clave es opaca (type|host-o-url|usuario) — nunca el
 *  id de Room ni las credenciales. */
@Serializable
internal data class RemoteSourceDto(
    val key: String,
    val type: String,
    val name: String,
    val active: Boolean,
    val channels: Int,
    val lastSyncAt: Long? = null,
    val detail: String? = null,
)

@Serializable
internal enum class AuthFailReason { PIN_REQUIRED, PIN_WRONG, VERSION, REJECTED }

@Serializable
internal sealed interface LinkMsg {

    @Serializable
    @SerialName("hello")
    data class Hello(
        val v: Int,
        val deviceId: String,
        val deviceName: String,
        val pin: String? = null,
        val token: String? = null,
    ) : LinkMsg

    @Serializable
    @SerialName("auth_ok")
    data class AuthOk(
        val token: String,
        val deviceId: String,
        val deviceName: String,
    ) : LinkMsg

    @Serializable
    @SerialName("auth_fail")
    data class AuthFail(val reason: AuthFailReason) : LinkMsg

    /** Instantánea de sesión: empujada al autenticar y en cada cambio. */
    @Serializable
    @SerialName("state")
    data class State(
        val nowPlaying: NowPlayingDto? = null,
        val error: String? = null,
        val reconnectAttempt: Int? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        /** Duración del contenido (0 = desconocida/en directo): el mando la
         *  necesita para dibujar la línea temporal arrastrable. */
        val durationMs: Long = 0,
        val sleepTimerEndAtMs: Long? = null,
    ) : LinkMsg

    /** Latido de posición; el cliente extrapola entre ticks. */
    @Serializable
    @SerialName("tick")
    data class Tick(
        val positionMs: Long,
        val playing: Boolean,
        val durationMs: Long = 0,
    ) : LinkMsg

    @Serializable
    @SerialName("cmd")
    data class Cmd(
        val action: LinkAction,
        val channelId: Long = 0,
        /** Identidad estable del canal (externalId): los channelId de Room son
         *  locales a cada base de datos — entre dispositivos NO coinciden. */
        val channelKey: String? = null,
        val channelName: String? = null,
        val deltaMs: Long = 0,
        val delta: Int = 0,
        val minutes: Int? = null,
        val positionMs: Long = 0,
        // -- Catálogo remoto (CATALOG_PAGE / CATALOG_GROUPS / FAVORITE_SET)
        val reqId: Int = 0,
        val kind: String? = null,
        val query: String? = null,
        val group: String? = null,
        val offset: Int = 0,
        val limit: Int = 0,
        val favoritesOnly: Boolean = false,
        /** FAVORITE_SET: destino (true = marcar favorito). */
        val favorite: Boolean = false,
        // -- Fuentes remotas (SOURCE_*)
        val opId: String? = null,
        /** Referencia opaca a la fuente: "type|host-o-url|usuario". */
        val sourceKey: String? = null,
        val sourceName: String? = null,
        val url: String? = null,
        val server: String? = null,
        val username: String? = null,
        val password: String? = null,
    ) : LinkMsg

    /** Respuesta a [LinkAction.TAKE_HERE]: canal + posición para reanudar. */
    @Serializable
    @SerialName("handoff")
    data class Handoff(
        val channelId: Long,
        val positionMs: Long,
        val channelKey: String? = null,
        val channelName: String? = null,
    ) : LinkMsg

    /** Página del catálogo del host (respuesta a CATALOG_PAGE). */
    @Serializable
    @SerialName("catalog")
    data class CatalogPage(
        val reqId: Int,
        val kind: String,
        val items: List<RemoteChannelDto>,
        val total: Int,
        val offset: Int,
        val done: Boolean,
    ) : LinkMsg

    /** Grupos/categorías del host (respuesta a CATALOG_GROUPS). */
    @Serializable
    @SerialName("groups")
    data class CatalogGroups(
        val reqId: Int,
        val kind: String,
        val groups: List<RemoteGroupDto>,
    ) : LinkMsg

    /** Fuentes configuradas en el host (respuesta a SOURCES_LIST). */
    @Serializable
    @SerialName("sources")
    data class Sources(val items: List<RemoteSourceDto>) : LinkMsg

    /** Progreso de una operación de fuente (alta/refresco) en el host. */
    @Serializable
    @SerialName("source_op")
    data class SourceOp(
        val opId: String,
        /** progress | done | failed */
        val phase: String,
        val step: String? = null,
        val count: Int = 0,
        val error: String? = null,
    ) : LinkMsg

    @Serializable
    @SerialName("ping")
    data object Ping : LinkMsg

    @Serializable
    @SerialName("pong")
    data object Pong : LinkMsg
}
