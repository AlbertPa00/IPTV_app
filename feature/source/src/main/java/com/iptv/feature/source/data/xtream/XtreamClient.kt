package com.iptv.feature.source.data.xtream

import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.network.DEFAULT_USER_AGENT
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
        json.decodeFromString(execute(apiUrl(scheme, host, port, username, password)))

    suspend fun liveCategories(scheme: String, host: String, port: Int, username: String, password: String): List<XtCategory> =
        json.decodeFromString(execute(apiUrl(scheme, host, port, username, password).action("get_live_categories")))

    suspend fun liveStreams(scheme: String, host: String, port: Int, username: String, password: String): List<XtStream> =
        json.decodeFromString(execute(apiUrl(scheme, host, port, username, password).action("get_live_streams")))

    suspend fun vodCategories(scheme: String, host: String, port: Int, username: String, password: String): List<XtCategory> =
        json.decodeFromString(execute(apiUrl(scheme, host, port, username, password).action("get_vod_categories")))

    suspend fun vodStreams(scheme: String, host: String, port: Int, username: String, password: String): List<XtStream> =
        json.decodeFromString(execute(apiUrl(scheme, host, port, username, password).action("get_vod_streams")))

    suspend fun seriesCategories(scheme: String, host: String, port: Int, username: String, password: String): List<XtCategory> =
        json.decodeFromString(execute(apiUrl(scheme, host, port, username, password).action("get_series_categories")))

    suspend fun series(scheme: String, host: String, port: Int, username: String, password: String): List<XtSeries> =
        json.decodeFromString(execute(apiUrl(scheme, host, port, username, password).action("get_series")))

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

    private suspend fun execute(url: HttpUrl): String = withContext(dispatchers.io) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Respuesta vacía")
            if (!response.isSuccessful) throw HttpCodeException(response.code)
            body.string()
        }
    }
}
