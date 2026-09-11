package com.iptv.feature.catalog.data

import android.util.Log
import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.SourceTypes
import com.iptv.core.storage.security.CredentialCrypto
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Metadatos de película vía `get_vod_info`. Devuelve `null` ante cualquier
 * fallo o fuente sin API (M3U): la pantalla de detalle siempre muestra la
 * información básica del catálogo.
 */
@Singleton
class VodInfoRepository @Inject constructor(
    private val sourceDao: SourceDao,
    private val credentialCrypto: CredentialCrypto,
    private val client: OkHttpClient,
    json: Json,
    private val dispatchers: AppDispatchers,
) {
    private val parser = VodInfoParser(json)

    suspend fun load(channel: ChannelEntity): VodInfo? = withContext(dispatchers.io) {
        val source = sourceDao.findById(channel.sourceId) ?: return@withContext null
        if (source.type != SourceTypes.XTREAM) return@withContext null
        val vodId = channel.externalId.removePrefix("vod:")
            .takeIf { it.isNotBlank() && it != channel.externalId }
            ?: return@withContext null
        val username = source.username?.takeIf { it.isNotBlank() } ?: return@withContext null
        val password = source.passwordEnc?.let(credentialCrypto::decrypt)
            ?: return@withContext null
        val baseUrl = source.url?.toHttpUrlOrNull() ?: return@withContext null

        val apiUrl = baseUrl.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .addQueryParameter("action", "get_vod_info")
            .addQueryParameter("vod_id", vodId)
            .build()
        val request = Request.Builder().url(apiUrl)
            .header("User-Agent", source.userAgent ?: DEFAULT_USER_AGENT)
            .build()

        runCatching {
            val payload = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.string()?.takeIf { it.isNotBlank() }
            } ?: return@runCatching null
            parser.parse(payload, channel.name, channel.logoUrl)
        }.onFailure { Log.w(TAG, "get_vod_info falló para ${channel.id}", it) }
            .getOrNull()
    }

    private companion object {
        const val TAG = "VodInfoRepository"
    }
}
