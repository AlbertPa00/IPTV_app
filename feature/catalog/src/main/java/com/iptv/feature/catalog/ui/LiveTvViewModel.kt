package com.iptv.feature.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.GuideRow
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.SourceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sección TV en directo: lista de canales paginada con favoritos, grupos,
 * búsqueda y la información "ahora / a continuación" de la EPG en cada fila.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    categoryDao: CategoryDao,
    sourceDao: SourceDao,
    programmeDao: ProgrammeDao,
) : ViewModel() {

    data class Filters(
        val query: String = "",
        val favoritesOnly: Boolean = false,
        val categoryId: Long? = null,
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    private val activeSource: StateFlow<SourceEntity?> = sourceDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> = activeSource
        .flatMapLatest { source ->
            if (source == null) flowOf(emptyList())
            else categoryDao.observeBySource(source.id, ContentKind.TV.storageValue)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    /** EPG por canal para pintar "ahora / a continuación" sin consultas por fila. */
    val guideByChannel: StateFlow<Map<Long, GuideRow>> = activeSource
        .flatMapLatest { source ->
            if (source == null) {
                flowOf(emptyMap())
            } else {
                clock.flatMapLatest { now -> programmeDao.observeGuide(source.id, now) }
                    .map { rows -> rows.associateBy(GuideRow::channelId) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val channels: Flow<PagingData<ChannelEntity>> = combine(activeSource, _filters) { s, f -> s to f }
        .distinctUntilChanged()
        .flatMapLatest { (source, filters) ->
            if (source == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(PagingConfig(pageSize = 60, initialLoadSize = 120)) {
                    when {
                        filters.query.isNotBlank() -> channelDao.pagingBySearch(
                            source.id,
                            ContentKind.TV.storageValue,
                            filters.query.toLikePattern(),
                        )
                        filters.favoritesOnly -> channelDao.pagingFavorites(
                            source.id,
                            ContentKind.TV.storageValue,
                        )
                        filters.categoryId != null -> channelDao.pagingByCategory(
                            source.id,
                            filters.categoryId,
                        )
                        else -> channelDao.pagingBySource(source.id, ContentKind.TV.storageValue)
                    }
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    fun selectAll() {
        _filters.update { it.copy(favoritesOnly = false, categoryId = null, query = "") }
    }

    fun selectFavorites() {
        _filters.update { it.copy(favoritesOnly = true, categoryId = null, query = "") }
    }

    fun selectCategory(categoryId: Long) {
        _filters.update { it.copy(favoritesOnly = false, categoryId = categoryId, query = "") }
    }

    fun onQueryChange(query: String) {
        _filters.update { Filters(query = query.trimStart()) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            channelDao.setFavorite(channel.id, !channel.isFavorite)
        }
    }
}
