package com.iptv.feature.source.data.xtream

import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.network.DEFAULT_USER_AGENT
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

class HttpCodeException(val code: Int) : IOException("HTTP $code")

/**
 * Cliente del protocolo Xtream Codes (player_api.php).
 * Se construye por petición porque el host/puerto dependen de cada cuenta.
 */
@Singleton
class XtreamClient @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val dispatchers: AppDispatchers,
) {

    suspend fun authenticate(scheme: String, host: String, port: Int, username: String, password: String): PlayerApiResponse =
        execute(apiUrl(scheme, host, port, username, password), PlayerApiResponse.serializer())

    suspend fun liveCategories(scheme: String, host: String, port: Int, username: String, password: String): List<XtCategory> =
        execute(apiUrl(scheme, host, port, username, password).action("get_live_categories"), ListSerializer(XtCategory.serializer()))

    /**
     * Las colecciones de streams superan los 100 000 elementos en paneles
     * grandes: se parsean en streaming y se entregan por lotes vía [onBatch]
     * en lugar de materializar la lista completa, lo que provocaba OOM.
     */
    suspend fun liveStreams(
        scheme: String, host: String, port: Int, username: String, password: String,
        onBatch: suspend (List<XtStream>) -> Unit,
    ) = streamCatalog(
        apiUrl(scheme, host, port, username, password).action("get_live_streams"),
        XtStream.serializer(), onBatch,
    )

    suspend fun vodCategories(scheme: String, host: String, port: Int, username: String, password: String): List<XtCategory> =
        execute(apiUrl(scheme, host, port, username, password).action("get_vod_categories"), ListSerializer(XtCategory.serializer()))

    suspend fun vodStreams(
        scheme: String, host: String, port: Int, username: String, password: String,
        onBatch: suspend (List<XtStream>) -> Unit,
    ) = streamCatalog(
        apiUrl(scheme, host, port, username, password).action("get_vod_streams"),
        XtStream.serializer(), onBatch,
    )

    suspend fun seriesCategories(scheme: String, host: String, port: Int, username: String, password: String): List<XtCategory> =
        execute(apiUrl(scheme, host, port, username, password).action("get_series_categories"), ListSerializer(XtCategory.serializer()))

    suspend fun series(
        scheme: String, host: String, port: Int, username: String, password: String,
        onBatch: suspend (List<XtSeries>) -> Unit,
    ) = streamCatalog(
        apiUrl(scheme, host, port, username, password).action("get_series"),
        XtSeries.serializer(), onBatch,
    )

    private fun apiUrl(scheme: String, host: String, port: Int, username: String, password: String): HttpUrl =
        HttpUrl.Builder()
            .scheme(scheme)
            .host(host)
            .port(port)
            .addPathSegments("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .build()

    private fun HttpUrl.action(name: String): HttpUrl =
        newBuilder().addQueryParameter("action", name).build()

    /**
     * Parsea el JSON directamente desde el stream: los catálogos Xtream pueden
     * pesar decenas de MB y materializar el String completo provocaba churn de
     * GC continuo durante la sincronización.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> execute(url: HttpUrl, deserializer: DeserializationStrategy<T>): T =
        withContext(dispatchers.io) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body ?: throw IOException("Respuesta vacía")
                if (!response.isSuccessful) throw HttpCodeException(response.code)
                json.decodeFromStream(deserializer, body.byteStream())
            }
        }

    /**
     * Variante por lotes para catálogos enormes: decodeToSequence lee el array
     * elemento a elemento y [onBatch] recibe tandas de [CATALOG_BATCH_SIZE]
     * sin que la respuesta completa llegue a la memoria. El sequence es
     * perezoso: se consume bajo el `use` del response, que se mantiene abierto
     * (con backpressure TCP) mientras el consumidor inserta cada lote.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> streamCatalog(
        url: HttpUrl,
        elementSerializer: DeserializationStrategy<T>,
        onBatch: suspend (List<T>) -> Unit,
    ) = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Respuesta vacía")
            if (!response.isSuccessful) throw HttpCodeException(response.code)
            val batch = ArrayList<T>(CATALOG_BATCH_SIZE)
            for (item in json.decodeToSequence(body.byteStream(), elementSerializer, DecodeSequenceMode.ARRAY_WRAPPED)) {
                batch += item
                if (batch.size == CATALOG_BATCH_SIZE) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) onBatch(batch.toList())
        }
    }

    private companion object {
        const val CATALOG_BATCH_SIZE = 500
    }
}
