package com.iptv.feature.source.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.iptv.core.common.dispatchers.AppDispatchers
import com.iptv.core.common.text.TextNormalizer
import com.iptv.core.network.DEFAULT_USER_AGENT
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.db.AppDatabase
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.Kinds
import android.util.Log
import com.iptv.core.storage.entity.SourceEntity
import com.iptv.core.storage.entity.SourceTypes
import com.iptv.core.storage.security.CredentialCrypto
import com.iptv.feature.source.data.m3u.M3uContentClassifier
import com.iptv.feature.source.data.m3u.M3uParser
import com.iptv.feature.source.data.m3u.ParsedChannel
import com.iptv.feature.source.data.xtream.HttpCodeException
import com.iptv.feature.source.data.xtream.XtCategory
import com.iptv.feature.source.data.xtream.XtSeries
import com.iptv.feature.source.data.xtream.XtStream
import com.iptv.feature.source.data.xtream.XtreamClient
import com.iptv.feature.source.data.xtream.XtreamStreamUrlBuilder
import com.iptv.feature.source.domain.SourceError
import com.iptv.feature.source.domain.SourceSyncPhase
import com.iptv.feature.source.domain.SyncStep
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Slice "source": alta, validación y sincronización de fuentes (M3U por URL y
 * Xtream Codes). Toda la lógica de este caso de uso vive aquí, dentro del slice.
 */
@Singleton
class SourceRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: AppDatabase,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val httpClient: OkHttpClient,
    private val xtreamClient: XtreamClient,
    private val credentialCrypto: CredentialCrypto,
    private val dispatchers: AppDispatchers,
) {

    fun observeSources(): Flow<List<SourceEntity>> = sourceDao.observeAll()

    fun observeActiveSource(): Flow<SourceEntity?> = sourceDao.observeActive()

    fun addM3uUrl(displayName: String?, rawUrl: String): Flow<SourceSyncPhase> = flow {
        val url = rawUrl.trim()
        if (url.toHttpUrlOrNull() == null) {
            emit(SourceSyncPhase.Failed(SourceError.InvalidUrl))
            return@flow
        }
        emit(SourceSyncPhase.Progress(SyncStep.CONNECTING, 0))
        var createdSourceId: Long? = null
        try {
            val request = Request.Builder().url(url).header("User-Agent", DEFAULT_USER_AGENT).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = if (response.code == 404 || response.code == 410) SourceError.NotFound else SourceError.Network
                    emit(SourceSyncPhase.Failed(error))
                    return@flow
                }
                val body = response.body ?: run {
                    emit(SourceSyncPhase.Failed(SourceError.EmptyPlaylist))
                    return@flow
                }
                val existingSource = sourceDao.findByUrl(url)
                val sourceId = existingSource?.id ?: ensureM3uSource(url, displayName)
                if (existingSource == null) createdSourceId = sourceId
                val charset = body.contentType()?.charset() ?: Charsets.UTF_8
                BufferedReader(InputStreamReader(body.byteStream(), charset)).use { reader ->
                    importPlaylist(sourceId, reader) { count ->
                        emit(SourceSyncPhase.Progress(SyncStep.IMPORTING, count))
                    }
                }
                emit(SourceSyncPhase.Done(sourceId))
            }
        } catch (e: EmptyPlaylistException) {
            createdSourceId?.let { sourceDao.deleteById(it) }
            emit(SourceSyncPhase.Failed(SourceError.EmptyPlaylist))
        } catch (e: IOException) {
            createdSourceId?.let { sourceDao.deleteById(it) }
            emit(SourceSyncPhase.Failed(SourceError.Network))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            createdSourceId?.let { sourceDao.deleteById(it) }
            emit(SourceSyncPhase.Failed(SourceError.Unknown))
        }
    }.flowOn(dispatchers.io)

    fun addM3uFile(displayName: String?, rawUri: String): Flow<SourceSyncPhase> = flow {
        val uri = Uri.parse(rawUri)
        if (uri.scheme != "content") {
            emit(SourceSyncPhase.Failed(SourceError.FileAccess))
            return@flow
        }
        emit(SourceSyncPhase.Progress(SyncStep.CONNECTING, 0))
        var copiedFile: File? = null
        var sourceId: Long? = null
        try {
            val stored = copyPlaylistToInternalStorage(uri)
            val file = stored.file
            if (stored.created) copiedFile = file
            val sourceName = displayName ?: queryDisplayName(uri)
            val existingSource = sourceDao.findByUrl(file.absolutePath)
            val id = existingSource?.id ?: ensureM3uFile(file, sourceName)
            if (existingSource == null) sourceId = id
            file.bufferedReader(detectCharset(file)).use { reader ->
                importPlaylist(id, reader) { count ->
                    emit(SourceSyncPhase.Progress(SyncStep.IMPORTING, count))
                }
            }
            emit(SourceSyncPhase.Done(id))
        } catch (e: EmptyPlaylistException) {
            cleanupFailedFileImport(sourceId, copiedFile)
            emit(SourceSyncPhase.Failed(SourceError.EmptyPlaylist))
        } catch (e: IOException) {
            cleanupFailedFileImport(sourceId, copiedFile)
            emit(SourceSyncPhase.Failed(SourceError.FileAccess))
        } catch (e: SecurityException) {
            cleanupFailedFileImport(sourceId, copiedFile)
            emit(SourceSyncPhase.Failed(SourceError.FileAccess))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            cleanupFailedFileImport(sourceId, copiedFile)
            emit(SourceSyncPhase.Failed(SourceError.Unknown))
        }
    }.flowOn(dispatchers.io)

    /** Login Xtream y sincronización de TV, películas y series. */
    fun loginAndSyncXtream(displayName: String?, rawServer: String, username: String, password: String): Flow<SourceSyncPhase> = flow {
        if (rawServer.isBlank() || username.isBlank() || password.isBlank()) {
            emit(SourceSyncPhase.Failed(SourceError.InvalidUrl))
            return@flow
        }
        val server = parseServer(rawServer) ?: run {
            emit(SourceSyncPhase.Failed(SourceError.InvalidUrl))
            return@flow
        }
        emit(SourceSyncPhase.Progress(SyncStep.AUTHENTICATING, 0))
        try {
            val response = xtreamClient.authenticate(server.scheme, server.host, server.port, username, password)
            val user = response.user_info
            if (user.auth != 1) {
                emit(SourceSyncPhase.Failed(SourceError.InvalidCredentials))
                return@flow
            }
            if (!user.status.equals("Active", ignoreCase = true)) {
                emit(SourceSyncPhase.Failed(SourceError.AccountInactive(user.status)))
                return@flow
            }
            val sourceId = ensureXtreamSource(server, username, password, displayName)
            emitAll(syncXtreamCatalog(sourceId, server, username, password))
        } catch (e: HttpCodeException) {
            val error = if (e.code == 401 || e.code == 403) SourceError.InvalidCredentials else SourceError.Network
            emit(SourceSyncPhase.Failed(error))
        } catch (e: IOException) {
            emit(SourceSyncPhase.Failed(SourceError.Network))
        } catch (e: SerializationException) {
            emit(SourceSyncPhase.Failed(SourceError.InvalidUrl))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(SourceSyncPhase.Failed(SourceError.Unknown))
        }
    }.flowOn(dispatchers.io)

    /** Resincroniza una fuente existente (M3U por URL o Xtream). */
    fun refresh(source: SourceEntity): Flow<SourceSyncPhase> = flow {
        when (source.type) {
            SourceTypes.M3U_URL -> {
                val url = source.url
                if (url.isNullOrBlank()) {
                    emit(SourceSyncPhase.Failed(SourceError.InvalidUrl))
                } else {
                    emitAll(addM3uUrl(source.name, url))
                }
            }

            SourceTypes.M3U_FILE -> {
                val path = source.url
                if (path.isNullOrBlank()) {
                    emit(SourceSyncPhase.Failed(SourceError.FileAccess))
                } else {
                    try {
                        val file = File(path)
                        file.bufferedReader(detectCharset(file)).use { reader ->
                            importPlaylist(source.id, reader) { count ->
                                emit(SourceSyncPhase.Progress(SyncStep.IMPORTING, count))
                            }
                        }
                        emit(SourceSyncPhase.Done(source.id))
                    } catch (e: EmptyPlaylistException) {
                        emit(SourceSyncPhase.Failed(SourceError.EmptyPlaylist))
                    } catch (e: IOException) {
                        emit(SourceSyncPhase.Failed(SourceError.FileAccess))
                    }
                }
            }

            SourceTypes.XTREAM -> {
                val server = source.url?.toHttpUrlOrNull()
                val password = source.passwordEnc?.let { credentialCrypto.decrypt(it) }
                val username = source.username
                if (server == null || password == null || username.isNullOrBlank()) {
                    emit(SourceSyncPhase.Failed(SourceError.Unknown))
                } else {
                    val parsed = ParsedServer(server.scheme, server.host, server.port)
                    try {
                        emitAll(syncXtreamCatalog(source.id, parsed, username, password))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        emit(SourceSyncPhase.Failed(SourceError.Unknown))
                    }
                }
            }

            else -> emit(SourceSyncPhase.Failed(SourceError.Unknown))
        }
    }.flowOn(dispatchers.io)

    suspend fun setActiveSource(id: Long) {
        sourceDao.setActive(id)
    }

    suspend fun deleteSource(id: Long) {
        val source = sourceDao.findById(id)
        database.withTransaction {
            channelDao.deleteBySource(id)
            categoryDao.deleteBySource(id)
            programmeDao.deleteBySource(id)
            playbackHistoryDao.deleteBySource(id)
            sourceDao.deleteById(id)
        }
        if (source?.type == SourceTypes.M3U_FILE) source.url?.let(::File)?.let(::deleteStoredPlaylist)
        if (sourceDao.observeActive().first() == null) {
            sourceDao.observeAll().first().firstOrNull()?.let { sourceDao.setActive(it.id) }
        }
    }

    private fun syncXtreamCatalog(sourceId: Long, server: ParsedServer, username: String, password: String): Flow<SourceSyncPhase> = flow {
        emit(SourceSyncPhase.Progress(SyncStep.FETCHING, 0))
        // Las 6 peticiones son independientes: en paralelo el alta de una cuenta
        // grande tarda lo que tarda la respuesta más lenta, no la suma de todas.
        // Los catálogos opcionales (VOD/series) devuelven null si fallan tras
        // reintentar, para informar al usuario en lugar de ocultarlos.
        val catalog = coroutineScope {
            val liveCategories = async {
                xtreamClient.liveCategories(server.scheme, server.host, server.port, username, password)
            }
            val liveStreams = async {
                xtreamClient.liveStreams(server.scheme, server.host, server.port, username, password)
            }
            val vodCategories = async {
                optionalCatalog("vod_categories") { xtreamClient.vodCategories(server.scheme, server.host, server.port, username, password) }
            }
            val vodStreams = async {
                optionalCatalog("vod_streams") { xtreamClient.vodStreams(server.scheme, server.host, server.port, username, password) }
            }
            val seriesCategories = async {
                optionalCatalog("series_categories") { xtreamClient.seriesCategories(server.scheme, server.host, server.port, username, password) }
            }
            val series = async {
                optionalCatalog("series") { xtreamClient.series(server.scheme, server.host, server.port, username, password) }
            }
            XtreamCatalog(
                liveCategories = liveCategories.await(),
                liveStreams = liveStreams.await(),
                vodCategories = vodCategories.await(),
                vodStreams = vodStreams.await(),
                seriesCategories = seriesCategories.await(),
                series = series.await(),
            )
        }
        val missing = buildSet {
            if (catalog.vodCategories == null || catalog.vodStreams == null) add(SECTION_VOD)
            if (catalog.seriesCategories == null || catalog.series == null) add(SECTION_SERIES)
        }
        emit(SourceSyncPhase.Progress(
            SyncStep.FETCHING,
            catalog.liveStreams.size + catalog.vodStreams.orEmpty().size + catalog.series.orEmpty().size,
        ))

        val categories = buildList {
            addAll(catalog.liveCategories.toEntities(sourceId, Kinds.LIVE))
            addAll(catalog.vodCategories.orEmpty().toEntities(sourceId, Kinds.VOD))
            addAll(catalog.seriesCategories.orEmpty().toEntities(sourceId, Kinds.SERIES))
        }
        val channels = buildList {
            addAll(catalog.liveStreams.map { it.toLiveEntity(sourceId, server, username, password) })
            addAll(catalog.vodStreams.orEmpty().map { it.toVodEntity(sourceId, server, username, password) })
            addAll(catalog.series.orEmpty().map { it.toSeriesEntity(sourceId) })
        }
        // Las secciones que el servidor no devolvió conservan sus filas
        // anteriores: un fallo transitorio no debe borrar contenido ya importado.
        val keptKinds = missing.map { if (it == SECTION_VOD) Kinds.VOD else Kinds.SERIES }.toSet()
        replaceCatalog(sourceId, categories, channels, userAgent = null, keepKinds = keptKinds)
        ensureActiveSource(sourceId)
        emit(SourceSyncPhase.Done(sourceId, missing))
    }.flowOn(dispatchers.io)

    private data class XtreamCatalog(
        val liveCategories: List<XtCategory>,
        val liveStreams: List<XtStream>,
        val vodCategories: List<XtCategory>?,
        val vodStreams: List<XtStream>?,
        val seriesCategories: List<XtCategory>?,
        val series: List<XtSeries>?,
    )

    /**
     * VOD/series son opcionales en Xtream: un fallo devuelve null (tras un
     * reintento) para distinguir "el panel no tiene esta sección" de "el
     * servidor no respondió".
     */
    private suspend fun <T> optionalCatalog(name: String, block: suspend () -> List<T>): List<T>? {
        repeat(2) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0) {
                    Log.w(TAG, "Catálogo $name falló, reintentando", e)
                    delay(1_500)
                } else {
                    Log.w(TAG, "Catálogo opcional Xtream no disponible: $name", e)
                }
            }
        }
        return null
    }

    private fun List<XtCategory>.toEntities(
        sourceId: Long,
        kind: String,
    ): List<CategoryEntity> = distinctBy { it.category_id }.mapIndexed { index, category ->
        CategoryEntity(
            sourceId = sourceId,
            externalId = categoryKey(kind, category.category_id),
            kind = kind,
            name = category.category_name,
            sortOrder = index,
        )
    }

    private fun XtStream.toLiveEntity(
        sourceId: Long,
        server: ParsedServer,
        username: String,
        password: String,
    ) = ChannelEntity(
        sourceId = sourceId,
        externalId = "live:$stream_id",
        name = name,
        nameNorm = TextNormalizer.searchKey(name),
        streamUrl = direct_source?.takeIf { it.isNotBlank() }
            ?: XtreamStreamUrlBuilder.live(server.baseUrl, username, password, stream_id, container_extension),
        logoUrl = stream_icon,
        tvgId = epg_channel_id,
        groupTitle = effectiveCategoryId.takeIf { it.isNotBlank() }
            ?.let { categoryKey(Kinds.LIVE, it) },
        kind = Kinds.LIVE,
        containerExt = container_extension,
        sortOrder = num,
    )

    private fun XtStream.toVodEntity(
        sourceId: Long,
        server: ParsedServer,
        username: String,
        password: String,
    ) = ChannelEntity(
        sourceId = sourceId,
        externalId = "vod:$stream_id",
        name = name,
        nameNorm = TextNormalizer.searchKey(name),
        streamUrl = direct_source?.takeIf { it.isNotBlank() }
            ?: XtreamStreamUrlBuilder.movie(server.baseUrl, username, password, stream_id, container_extension),
        logoUrl = stream_icon,
        groupTitle = effectiveCategoryId.takeIf { it.isNotBlank() }
            ?.let { categoryKey(Kinds.VOD, it) },
        kind = Kinds.VOD,
        containerExt = container_extension,
        sortOrder = num,
    )

    private fun XtSeries.toSeriesEntity(sourceId: Long) = ChannelEntity(
        sourceId = sourceId,
        externalId = "series:$series_id",
        name = name,
        nameNorm = TextNormalizer.searchKey(name),
        streamUrl = "",
        logoUrl = cover,
        groupTitle = effectiveCategoryId.takeIf { it.isNotBlank() }
            ?.let { categoryKey(Kinds.SERIES, it) },
        kind = Kinds.SERIES,
        sortOrder = num,
    )

    private fun categoryKey(kind: String, categoryId: String) = "$kind:$categoryId"

    private suspend fun importPlaylist(
        sourceId: Long,
        reader: BufferedReader,
        onProgress: suspend (Int) -> Unit,
    ) {
        val categories = linkedMapOf<String, CategoryEntity>()
        val channels = mutableListOf<ChannelEntity>()
        var userAgent: String? = null
        var epgUrl: String? = null
        val iterator = M3uParser().parse(reader) { epgUrl = it }.iterator()

        while (iterator.hasNext()) {
            val parsed = iterator.next()
            val kind = M3uContentClassifier.classify(parsed)
            userAgent = userAgent ?: parsed.userAgent
            addCategory(categories, sourceId, parsed.groupTitle, kind)
            channels += parsed.toEntity(sourceId, kind)
            if (channels.size % BATCH_SIZE == 0) onProgress(channels.size)
        }

        if (channels.isEmpty()) throw EmptyPlaylistException()
        if (channels.size % BATCH_SIZE != 0) onProgress(channels.size)
        replaceCatalog(sourceId, categories.values.toList(), channels, userAgent, epgUrl)
        ensureActiveSource(sourceId)
    }

    private fun addCategory(
        categories: MutableMap<String, CategoryEntity>,
        sourceId: Long,
        groupTitle: String?,
        kind: String,
    ) {
        val name = groupTitle?.takeIf { it.isNotBlank() } ?: return
        val key = categoryKey(kind, name)
        categories.getOrPut(key) {
            CategoryEntity(
                sourceId = sourceId,
                externalId = key,
                kind = kind,
                name = name,
                sortOrder = categories.size,
            )
        }
    }

    private fun ParsedChannel.toEntity(sourceId: Long, kind: String) = ChannelEntity(
        sourceId = sourceId,
        externalId = url,
        name = name,
        nameNorm = TextNormalizer.searchKey(name),
        streamUrl = url,
        logoUrl = tvgLogo,
        tvgId = tvgId,
        groupTitle = groupTitle?.let { categoryKey(kind, it) },
        kind = kind,
        containerExt = containerExt,
        sortOrder = sortOrder,
    )

    private fun copyPlaylistToInternalStorage(uri: Uri): StoredPlaylist {
        val directory = playlistDirectory()
        val temporary = File(directory, "${UUID.randomUUID()}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir el archivo")

        DigestInputStream(input, digest).use { source ->
            temporary.outputStream().use { output -> source.copyTo(output) }
        }

        val target = File(directory, "${digest.digest().toHex()}.m3u")
        if (target.exists()) {
            temporary.delete()
            return StoredPlaylist(target, created = false)
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("No se pudo guardar el archivo")
        }
        return StoredPlaylist(target, created = true)
    }

    private fun detectCharset(file: File): Charset {
        val sample = ByteArray(CHARSET_SAMPLE_SIZE)
        val count = file.inputStream().use { it.read(sample) }.coerceAtLeast(0)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val result = decoder.decode(ByteBuffer.wrap(sample, 0, count), CharBuffer.allocate(count * 2 + 1), false)
        return if (result.isError) StandardCharsets.ISO_8859_1 else StandardCharsets.UTF_8
    }

    private suspend fun cleanupFailedFileImport(sourceId: Long?, file: File?) {
        if (sourceId != null) sourceDao.deleteById(sourceId)
        file?.let(::deleteStoredPlaylist)
    }

    private fun deleteStoredPlaylist(file: File) {
        if (file.canonicalFile.parentFile == playlistDirectory().canonicalFile) file.delete()
    }

    private fun playlistDirectory(): File =
        File(appContext.filesDir, PLAYLIST_DIRECTORY).apply { mkdirs() }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun queryDisplayName(uri: Uri): String? =
        appContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                .substringBeforeLast('.')
                .takeIf { it.isNotBlank() }
        }

    /**
     * Reemplaza el catálogo de una fuente en una transacción, conservando los
     * favoritos (identificados por externalId: URL en M3U, stream_id en Xtream).
     */
    private suspend fun replaceCatalog(
        sourceId: Long,
        categories: List<CategoryEntity>,
        channels: List<ChannelEntity>,
        userAgent: String?,
        epgUrl: String? = null,
        keepKinds: Set<String> = emptySet(),
    ) {
        database.withTransaction {
            val favorites = channelDao.favoriteExternalIds(sourceId).toHashSet()
            val lockedCategories = categoryDao.lockedExternalIds(sourceId).toHashSet()
            val previousCategories = categoryDao.allBySource(sourceId)
                .associateBy { it.externalId }
            if (keepKinds.isEmpty()) {
                channelDao.deleteBySource(sourceId)
                categoryDao.deleteBySource(sourceId)
            } else {
                channelDao.deleteBySourceExceptKinds(sourceId, keepKinds)
                categoryDao.deleteBySourceExceptKinds(sourceId, keepKinds)
            }
            val insertedCategoryIds = categoryDao.insertAll(
                categories.map { category ->
                    val previous = previousCategories[category.externalId]
                    category.copy(
                        isLocked = category.externalId in lockedCategories,
                        hidden = previous?.hidden ?: false,
                        // Conserva el orden personalizado si el usuario lo cambió.
                        sortOrder = previous?.sortOrder ?: category.sortOrder,
                    )
                },
            )
            val externalToRowId = categories.map { it.externalId }.zip(insertedCategoryIds).toMap()
            channels.chunked(BATCH_SIZE).forEach { batch ->
                channelDao.insertAll(
                    batch.map { channel ->
                        channel.copy(
                            categoryId = channel.groupTitle?.let { externalToRowId[it] },
                            isFavorite = channel.externalId in favorites,
                        )
                    }
                )
            }
            if (userAgent != null || epgUrl != null) {
                sourceDao.findById(sourceId)?.let {
                    sourceDao.update(
                        it.copy(
                            userAgent = userAgent ?: it.userAgent,
                            epgUrl = epgUrl ?: it.epgUrl,
                        )
                    )
                }
            }
            sourceDao.markSynced(sourceId, System.currentTimeMillis())
        }
    }

    private suspend fun ensureM3uSource(url: String, displayName: String?): Long {
        sourceDao.findByUrl(url)?.let { return it.id }
        val host = url.toHttpUrlOrNull()?.host ?: url
        return sourceDao.insert(
            SourceEntity(
                type = SourceTypes.M3U_URL,
                name = displayName?.takeIf { it.isNotBlank() } ?: host,
                url = url,
                isActive = sourceDao.count() == 0,
            )
        )
    }

    private suspend fun ensureM3uFile(file: File, displayName: String?): Long =
        sourceDao.insert(
            SourceEntity(
                type = SourceTypes.M3U_FILE,
                name = displayName?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
                url = file.absolutePath,
                isActive = sourceDao.count() == 0,
            )
        )

    private suspend fun ensureXtreamSource(server: ParsedServer, username: String, password: String, displayName: String?): Long {
        val existing = sourceDao.findAccount(SourceTypes.XTREAM, server.host, username)
        val isFirst = sourceDao.count() == 0
        if (existing != null) {
            sourceDao.update(
                existing.copy(
                    url = server.baseUrl,
                    passwordEnc = credentialCrypto.encrypt(password),
                    isActive = existing.isActive || isFirst,
                )
            )
            return existing.id
        }
        return sourceDao.insert(
            SourceEntity(
                type = SourceTypes.XTREAM,
                name = displayName?.takeIf { it.isNotBlank() } ?: username,
                url = server.baseUrl,
                host = server.host,
                port = server.port,
                username = username,
                passwordEnc = credentialCrypto.encrypt(password),
                isActive = isFirst,
            )
        )
    }

    private suspend fun ensureActiveSource(sourceId: Long) {
        if (sourceDao.observeActive().first() == null) {
            sourceDao.setActive(sourceId)
        }
    }

    private fun parseServer(input: String): ParsedServer? {
        val raw = input.trim()
        if (raw.isBlank()) return null
        val withScheme = when {
            raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true) -> raw
            else -> "http://$raw"
        }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        return ParsedServer(url.scheme, url.host, url.port)
    }

    private data class ParsedServer(val scheme: String, val host: String, val port: Int) {
        val baseUrl: String = "$scheme://$host:$port"
    }

    private data class StoredPlaylist(val file: File, val created: Boolean)

    private class EmptyPlaylistException : IllegalArgumentException()

    private companion object {
        const val TAG = "SourceRepository"
        const val BATCH_SIZE = 500
        const val SECTION_VOD = "vod"
        const val SECTION_SERIES = "series"
        const val CHARSET_SAMPLE_SIZE = 64 * 1024
        const val PLAYLIST_DIRECTORY = "playlists"
    }
}
