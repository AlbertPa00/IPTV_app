package com.iptv.feature.source.data

import com.iptv.core.common.tvlink.RemoteSourceController
import com.iptv.core.common.tvlink.RemoteSourceSync
import com.iptv.core.storage.dao.SourceDao
import com.iptv.feature.source.domain.SourceError
import com.iptv.feature.source.domain.SourceSyncPhase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Adaptador del contrato neutro [RemoteSourceController] (`:core:common`)
 * sobre [SourceRepository]: el enlace móvil ↔ TV da de alta y resincroniza
 * fuentes en el host sin que `:feature:tvlink` conozca este slice.
 */
@Singleton
internal class RemoteSourceControllerImpl @Inject constructor(
    private val repo: SourceRepository,
    private val sourceDao: SourceDao,
) : RemoteSourceController {

    override fun addM3u(name: String?, url: String): Flow<RemoteSourceSync> =
        repo.addM3uUrl(name, url).map(::toRemote)

    override fun addXtream(
        name: String?,
        server: String,
        username: String,
        password: String,
    ): Flow<RemoteSourceSync> =
        repo.loginAndSyncXtream(name, server, username, password).map(::toRemote)

    override fun refresh(sourceId: Long): Flow<RemoteSourceSync> = flow {
        val source = sourceDao.findById(sourceId)
        if (source == null) {
            emit(RemoteSourceSync(failed = "not_found"))
        } else {
            emitAll(repo.refresh(source).map(::toRemote))
        }
    }

    private fun toRemote(p: SourceSyncPhase) = when (p) {
        is SourceSyncPhase.Progress ->
            RemoteSourceSync(step = p.step.name, count = p.count)
        is SourceSyncPhase.Done -> RemoteSourceSync(done = true)
        is SourceSyncPhase.Failed -> RemoteSourceSync(failed = errorKey(p.error))
    }

    /** Clave estable de error para el cable: el mando la traduce a su idioma. */
    private fun errorKey(e: SourceError) = when (e) {
        SourceError.InvalidUrl -> "invalid_url"
        SourceError.Network -> "network"
        SourceError.InvalidCredentials -> "invalid_credentials"
        is SourceError.AccountInactive -> "account_inactive:${e.status}"
        SourceError.EmptyPlaylist -> "empty_playlist"
        SourceError.FileAccess -> "file_access"
        SourceError.NotFound -> "not_found"
        SourceError.Unknown -> "unknown"
    }
}
