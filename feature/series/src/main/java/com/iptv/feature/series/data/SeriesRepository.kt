package com.iptv.feature.series.data

import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.common.text.TextNormalizer
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.Kinds
import com.iptv.core.storage.entity.SourceTypes
import com.iptv.core.storage.security.CredentialCrypto
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeriesRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val credentialCrypto: CredentialCrypto,
    private val client: OkHttpClient,
    json: Json,
    private val dispatchers: AppDispatchers,
) {
    private val parser = SeriesInfoParser(json)

    suspend fun load(seriesId: Long): SeriesDetails = withContext(dispatchers.io) {
        val series = channelDao.findById(seriesId) ?: throw SeriesLoadException("Serie no encontrada")
        if (series.kind != Kinds.SERIES) throw SeriesLoadException("El contenido no es una serie")
        val source = sourceDao.findById(series.sourceId) ?: throw SeriesLoadException("Fuente no encontrada")
        if (source.type != SourceTypes.XTREAM) return@withContext loadFromPlaylist(series)
        val username = source.username?.takeIf { it.isNotBlank() }
            ?: throw SeriesLoadException("Usuario no disponible")
        val password = source.passwordEnc?.let(credentialCrypto::decrypt)
            ?: throw SeriesLoadException("No se pudieron leer las credenciales")
        val baseUrl = source.url?.toHttpUrlOrNull()
            ?: throw SeriesLoadException("Servidor no válido")
        val externalId = series.externalId.removePrefix("series:").takeIf { it.isNotBlank() }
            ?: throw SeriesLoadException("Identificador de serie no válido")
        val apiUrl = baseUrl.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .addQueryParameter("action", "get_series_info")
            .addQueryParameter("series_id", externalId)
            .build()
        val request = Request.Builder().url(apiUrl)
            .header("User-Agent", source.userAgent ?: DEFAULT_USER_AGENT)
            .build()
        val payload = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw SeriesLoadException("Error del proveedor (${response.code})")
                response.body?.string()?.takeIf { it.isNotBlank() }
                    ?: throw SeriesLoadException("El proveedor devolvió una respuesta vacía")
            }
        } catch (error: IOException) {
            throw SeriesLoadException("No se pudo conectar con el proveedor", error)
        }
        try {
            parser.parse(payload, series.name, series.logoUrl)
        } catch (error: Exception) {
            throw SeriesLoadException("La respuesta del proveedor no es válida", error)
        }
    }

    /**
     * Series de listas M3U: cada entrada ya es un stream reproducible, así
     * que los episodios se reconstruyen agrupando por título base. Las
     * entradas sin patrón SxxExx forman una temporada con un solo episodio.
     * El id del episodio es el id del canal ya guardado.
     */
    private suspend fun loadFromPlaylist(series: ChannelEntity): SeriesDetails {
        val baseKey = PlaylistEpisodeParser.baseKey(series.name)
        val episodes = channelDao.seriesBySource(series.sourceId)
            .filter { PlaylistEpisodeParser.baseKey(it.name) == baseKey }
            .map { it to (PlaylistEpisodeParser.parse(it.name)) }
            .sortedWith(
                compareBy(
                    { it.second?.season ?: 1 },
                    { it.second?.episode ?: Int.MAX_VALUE },
                    { it.first.name },
                ),
            )
        val seasons = episodes
            .groupBy { it.second?.season ?: 1 }
            .toSortedMap()
            .map { (seasonNumber, entries) ->
                SeriesSeason(
                    number = seasonNumber,
                    title = "Temporada $seasonNumber",
                    episodes = entries.mapIndexed { index, (channel, parsed) ->
                        SeriesEpisode(
                            id = channel.id.toString(),
                            number = parsed?.episode ?: (index + 1),
                            title = channel.name,
                            plot = null,
                            imageUrl = channel.logoUrl ?: series.logoUrl,
                            rating = null,
                            duration = null,
                            extension = channel.containerExt ?: "mp4",
                        )
                    },
                )
            }
        return SeriesDetails(
            title = PlaylistEpisodeParser.parse(series.name)?.baseTitle ?: series.name,
            coverUrl = series.logoUrl,
            plot = null,
            rating = null,
            seasons = seasons,
        )
    }

    suspend fun prepareEpisode(seriesId: Long, episode: SeriesEpisode): Long = withContext(dispatchers.io) {
        val series = channelDao.findById(seriesId) ?: throw SeriesLoadException("Serie no encontrada")
        val source = sourceDao.findById(series.sourceId) ?: throw SeriesLoadException("Fuente no encontrada")
        // M3U: el episodio es un canal ya existente; su id viaja en episode.id.
        if (source.type != SourceTypes.XTREAM) {
            return@withContext episode.id.toLongOrNull()
                ?: throw SeriesLoadException("Episodio no disponible")
        }
        val username = source.username ?: throw SeriesLoadException("Usuario no disponible")
        val password = source.passwordEnc?.let(credentialCrypto::decrypt)
            ?: throw SeriesLoadException("No se pudieron leer las credenciales")
        val baseUrl = source.url ?: throw SeriesLoadException("Servidor no válido")
        val externalId = "episode:${episode.id}"
        channelDao.findByExternalId(source.id, externalId)?.id ?: run {
            val entity = ChannelEntity(
                sourceId = source.id,
                externalId = externalId,
                name = episode.title,
                nameNorm = TextNormalizer.searchKey(episode.title),
                streamUrl = EpisodeUrlBuilder.build(baseUrl, username, password, episode.id, episode.extension),
                logoUrl = episode.imageUrl ?: series.logoUrl,
                kind = Kinds.VOD,
                containerExt = episode.extension,
            )
            val insertedId = channelDao.insert(entity)
            if (insertedId != -1L) insertedId
            else channelDao.findByExternalId(source.id, externalId)?.id
                ?: throw SeriesLoadException("No se pudo guardar el episodio")
        }
    }
}

class SeriesLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
