package com.iptv.feature.epg.data

import androidx.room.withTransaction
import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.db.AppDatabase
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.ProgrammeEntity
import com.iptv.core.storage.entity.SourceEntity
import com.iptv.core.storage.entity.SourceTypes
import com.iptv.core.storage.security.CredentialCrypto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EpgSyncState {
    data class Progress(val imported: Int) : EpgSyncState
    data class Done(val imported: Int) : EpgSyncState
    data class Failed(val reason: Reason) : EpgSyncState
    enum class Reason { NO_SOURCE, NO_URL, NETWORK, INVALID_XML, EMPTY }
}

@Singleton
class EpgRepository @Inject constructor(
    private val database: AppDatabase,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    private val client: OkHttpClient,
    private val crypto: CredentialCrypto,
    private val dispatchers: AppDispatchers,
) {
    fun syncActive(): Flow<EpgSyncState> = channelFlow {
        val source = sourceDao.observeActive().firstOrNull() ?: run {
            send(EpgSyncState.Failed(EpgSyncState.Reason.NO_SOURCE)); return@channelFlow
        }
        val url = EpgUrlResolver.resolve(source) { encrypted -> crypto.decrypt(encrypted) } ?: run {
            send(EpgSyncState.Failed(EpgSyncState.Reason.NO_URL)); return@channelFlow
        }
        send(EpgSyncState.Progress(0))
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", source.userAgent ?: DEFAULT_USER_AGENT)
                .header("Accept-Encoding", "gzip")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val gzip = EpgCompression.isGzip(url, response.header("Content-Encoding"))
                val stream = if (gzip) GZIPInputStream(body.byteStream()) else body.byteStream()
                stream.use { imported ->
                    val association = EpgAssociation(channelDao.liveBySource(source.id))
                    var count = 0
                    database.withTransaction {
                        programmeDao.deleteBySource(source.id)
                        val batch = ArrayList<ProgrammeEntity>(BATCH_SIZE)
                        XmlTvParser().parse(imported) { raw ->
                            if (association.matches(raw)) {
                                batch += raw.toEntity(source.id)
                                count++
                                if (batch.size == BATCH_SIZE) {
                                    programmeDao.insertAll(batch)
                                    batch.clear()
                                    trySend(EpgSyncState.Progress(count))
                                }
                            }
                        }
                        if (batch.isNotEmpty()) programmeDao.insertAll(batch)
                    }
                    if (count == 0) send(EpgSyncState.Failed(EpgSyncState.Reason.EMPTY))
                    else {
                        programmeDao.purgeEndedBefore(System.currentTimeMillis() - RETENTION_MILLIS)
                        send(EpgSyncState.Done(count))
                    }
                }
            }
        } catch (e: IOException) {
            send(EpgSyncState.Failed(EpgSyncState.Reason.NETWORK))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            send(EpgSyncState.Failed(EpgSyncState.Reason.INVALID_XML))
        }
        awaitClose { }
    }.flowOn(dispatchers.io)

    private fun XmlTvProgramme.toEntity(sourceId: Long) = ProgrammeEntity(
        sourceId = sourceId,
        channelKey = channelKey,
        channelNameNorm = channelNameNorm,
        title = title,
        description = description,
        startUtc = startUtc,
        endUtc = endUtc,
        iconUrl = iconUrl,
    )

    private companion object {
        const val BATCH_SIZE = 500
        const val RETENTION_MILLIS = 24L * 60 * 60 * 1000
    }
}

internal object EpgUrlResolver {
    fun resolve(source: SourceEntity, decrypt: (String) -> String?): String? {
        source.epgUrl?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        if (source.type != SourceTypes.XTREAM) return null
        val base = source.url?.toHttpUrlOrNull() ?: return null
        val username = source.username?.takeIf(String::isNotBlank) ?: return null
        val password = source.passwordEnc?.let(decrypt)?.takeIf(String::isNotBlank) ?: return null
        return base.newBuilder()
            .addPathSegment("xmltv.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .build().toString()
    }
}

internal object EpgCompression {
    fun isGzip(url: String, contentEncoding: String?): Boolean =
        contentEncoding?.contains("gzip", ignoreCase = true) == true ||
            url.substringBefore('?').endsWith(".gz", ignoreCase = true)
}

internal class EpgAssociation(channels: List<ChannelEntity>) {
    private val tvgIds = channels.mapNotNull { it.tvgId?.lowercase() }.toHashSet()
    private val normalizedNames = channels.map { it.nameNorm }.toHashSet()

    /** Exact tvg-id has priority; normalized display name is the provider fallback. */
    fun matches(programme: XmlTvProgramme): Boolean =
        programme.channelKey.lowercase() in tvgIds || programme.channelNameNorm in normalizedNames
}
