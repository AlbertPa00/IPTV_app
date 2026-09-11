package com.iptv.feature.catalog.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.ContinueWatchingItem
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.SourceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Secciones bajo demanda (Películas y Series): descubrimiento por carruseles —
 * destacado, favoritos y una fila por categoría — más una parrilla paginada
 * para "ver todo" y para los resultados de búsqueda.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VodCatalogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    categoryDao: CategoryDao,
    sourceDao: SourceDao,
) : ViewModel() {

    val kind: ContentKind = ContentKind.valueOf(
        checkNotNull(savedStateHandle.get<String>("kind")) { "kind es obligatorio en la ruta" },
    )

    /** Parrilla a pantalla completa que abre "Ver todo" en cada carrusel. */
    sealed interface Grid {
        data object All : Grid
        data object Favorites : Grid
        data object ContinueWatching : Grid
        data class Category(val id: Long, val title: String) : Grid
        data object Uncategorized : Grid
    }

    /** Elemento de carrusel: el canal más su progreso de visionado si existe. */
    data class BrowseItem(val channel: ChannelEntity, val progress: Float? = null)

    /** Carrusel de la vista de descubrimiento; [grid] es el destino de "Ver todo". */
    sealed interface BrowseRow {
        val items: List<BrowseItem>
        val grid: Grid

        data class ContinueWatching(override val items: List<BrowseItem>) : BrowseRow {
            override val grid get() = Grid.ContinueWatching

            companion object {
                fun of(entries: List<ContinueWatchingItem>) = ContinueWatching(
                    entries.map { BrowseItem(it.channel, it.progress) },
                )
            }
        }

        data class Favorites(override val items: List<BrowseItem>) : BrowseRow {
            override val grid get() = Grid.Favorites

            companion object {
                fun of(channels: List<ChannelEntity>) = Favorites(channels.map(::BrowseItem))
            }
        }

        data class Category(
            val id: Long,
            val name: String,
            override val items: List<BrowseItem>,
        ) : BrowseRow {
            override val grid get() = Grid.Category(id, name)

            companion object {
                fun of(id: Long, name: String, channels: List<ChannelEntity>) =
                    Category(id, name, channels.map(::BrowseItem))
            }
        }

        data class Uncategorized(override val items: List<BrowseItem>) : BrowseRow {
            override val grid get() = Grid.Uncategorized

            companion object {
                fun of(channels: List<ChannelEntity>) = Uncategorized(channels.map(::BrowseItem))
            }
        }

        data class All(override val items: List<BrowseItem>) : BrowseRow {
            override val grid get() = Grid.All

            companion object {
                fun of(channels: List<ChannelEntity>) = All(channels.map(::BrowseItem))
            }
        }
    }

    data class UiState(
        val query: String = "",
        val grid: Grid? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val activeSource: StateFlow<SourceEntity?> = sourceDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val categories: StateFlow<List<CategoryEntity>> = activeSource
        .flatMapLatest { source ->
            if (source == null) flowOf(emptyList())
            else categoryDao.observeBySource(source.id, kind.storageValue)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rows: StateFlow<List<BrowseRow>> = combine(activeSource, categories) { s, c -> s to c }
        .distinctUntilChanged()
        .flatMapLatest { (source, cats) -> browseRows(source, cats) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun browseRows(
        source: SourceEntity?,
        cats: List<CategoryEntity>,
    ): Flow<List<BrowseRow>> {
        if (source == null) return flowOf(emptyList())
        val kind = kind.storageValue
        val continueWatching =
            playbackHistoryDao.observeContinueWatching(source.id, kind, CONTINUE_ROW_LIMIT)
        val favorites = channelDao.observeTopFavorites(source.id, kind, FAVORITES_ROW_LIMIT)
        val rest: Flow<List<BrowseRow>> = if (cats.isEmpty()) {
            channelDao.observeTopByKind(source.id, kind, ROW_LIMIT).map { items ->
                if (items.isEmpty()) emptyList() else listOf(BrowseRow.All.of(items))
            }
        } else {
            val perCategory = cats.map { category ->
                channelDao.observeTopByCategory(category.id, ROW_LIMIT).map { category to it }
            }
            combine(
                combine(perCategory) { it.toList() },
                channelDao.observeUncategorized(source.id, kind, ROW_LIMIT),
            ) { pairs, uncategorized ->
                buildList {
                    pairs.forEach { (category, items) ->
                        if (items.isNotEmpty()) {
                            add(BrowseRow.Category.of(category.id, category.name, items))
                        }
                    }
                    if (uncategorized.isNotEmpty()) add(BrowseRow.Uncategorized.of(uncategorized))
                }
            }
        }
        return combine(continueWatching, favorites, rest) { watching, favs, others ->
            buildList {
                if (watching.isNotEmpty()) add(BrowseRow.ContinueWatching.of(watching))
                if (favs.isNotEmpty()) add(BrowseRow.Favorites.of(favs))
                addAll(others)
            }
        }
    }

    val paging: Flow<PagingData<ChannelEntity>> = combine(activeSource, _uiState) { s, st -> s to st }
        .distinctUntilChanged()
        .flatMapLatest { (source, state) ->
            if (source == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(PagingConfig(pageSize = 60, initialLoadSize = 120)) {
                    val kind = kind.storageValue
                    when {
                        state.query.isNotBlank() ->
                            channelDao.pagingBySearch(source.id, kind, state.query.toLikePattern())
                        else -> when (val grid = state.grid) {
                            is Grid.Category -> channelDao.pagingByCategory(source.id, grid.id)
                            is Grid.Favorites -> channelDao.pagingFavorites(source.id, kind)
                            is Grid.ContinueWatching ->
                                playbackHistoryDao.pagingContinueWatching(source.id, kind)
                            is Grid.Uncategorized -> channelDao.pagingUncategorized(source.id, kind)
                            else -> channelDao.pagingBySource(source.id, kind)
                        }
                    }
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    fun onQueryChange(query: String) {
        _uiState.update { UiState(query = query.trimStart()) }
    }

    fun openGrid(row: BrowseRow) {
        _uiState.update { it.copy(query = "", grid = row.grid) }
    }

    fun closeGrid() {
        _uiState.update { it.copy(grid = null) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            channelDao.setFavorite(channel.id, !channel.isFavorite)
        }
    }

    private companion object {
        const val ROW_LIMIT = 14
        const val FAVORITES_ROW_LIMIT = 20
        const val CONTINUE_ROW_LIMIT = 12
    }
}
