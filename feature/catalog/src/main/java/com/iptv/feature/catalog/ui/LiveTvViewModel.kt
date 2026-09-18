package com.iptv.feature.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.iptv.core.common.prefs.AppPreferences
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sección TV en directo: lista de canales paginada con favoritos, grupos,
 * búsqueda y la información "ahora / a continuación" de la EPG en cada fila.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    private val appPreferences: AppPreferences,
    categoryDao: CategoryDao,
    sourceDao: SourceDao,
) : ViewModel() {

    data class Filters(
        val query: String = "",
        val favoritesOnly: Boolean = false,
        val categoryId: Long? = null,
        val language: String? = null,
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    /** Pista única de primer uso ("toca un canal"): visible hasta que se marca vista. */
    val showQuickHint: StateFlow<Boolean> = appPreferences.liveHintSeen
        .map { seen -> !seen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markQuickHintSeen() {
        appPreferences.setLiveHintSeen()
    }

    // replay = MAX: al volver a la pestaña se re-emite el último valor sin
    // parpadeo a vacío; el upstream igualmente descansa a los 5 s sin UI.
    private val activeSource: StateFlow<SourceEntity?> = sourceDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), null)

    val categories: StateFlow<List<CategoryEntity>> = activeSource
        .flatMapLatest { source ->
            if (source == null) flowOf(emptyList())
            else categoryDao.observeBySource(source.id, ContentKind.TV.storageValue)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), emptyList())

    /** Idiomas detectados en las categorías del origen activo (orden por nº de grupos). */
    val languages: StateFlow<List<Pair<String, Int>>> = categories
        .map { cats ->
            cats.mapNotNull { it.language.takeIf(String::isNotBlank) }
                .groupingBy { it }
                .eachCount()
                .entries.sortedByDescending { it.value }
                .map { it.key to it.value }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), emptyList())

    /** EPG por canal para pintar "ahora / a continuación" sin consultas por fila. */
    val guideByChannel: StateFlow<Map<Long, GuideRow>> = activeSource
        .flatMapLatest { source ->
            if (source == null) {
                flowOf(emptyMap())
            } else {
                // La guía se compone con consultas agregadas + join en memoria
                // (guideSnapshot); el EXISTS evita lanzarla en orígenes sin EPG.
                programmeDao.observeHasProgrammes(source.id)
                    .distinctUntilChanged()
                    .flatMapLatest { has ->
                        if (!has) flowOf(emptyMap()) else guideMap(source.id)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), emptyMap())

    /**
     * Recalcula sólo cuando la guía puede cambiar: al llegar el borde de un
     * programa (fin del actual / inicio del siguiente) o cuando la tabla se
     * reescribe (importación EPG, detectada por la marca de agua). Sin sondeo
     * fijo: la pantalla anima el progreso con su propio reloj.
     */
    private fun guideMap(sourceId: Long): Flow<Map<Long, GuideRow>> = flow {
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val rows = programmeDao.guideSnapshot(sourceId, now)
            emit(rows.associateBy(GuideRow::channelId))
            val wakeIn = rows.asSequence()
                .flatMap { sequenceOf(it.currentEndUtc, it.nextStartUtc) }
                .filterNotNull()
                .map { it - now }
                .filter { it > 0 }
                .minOrNull() ?: MAX_TICK_MS
            withTimeoutOrNull(wakeIn.coerceIn(MIN_TICK_MS, MAX_TICK_MS)) {
                programmeDao.observeWatermark(sourceId).drop(1).debounce(5_000).first()
            }
        }
    }

    // El texto de búsqueda se debilita para no reconstruir el Pager a cada
    // pulsación; el resto de filtros (favoritos, grupo) aplican al instante.
    private val debouncedQuery = _filters.map { it.query }
        .distinctUntilChanged()
        .debounce { if (it.isBlank()) 0L else 300L }

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        activeSource,
        _filters,
        debouncedQuery,
    ) { source, filters, query -> source to filters.copy(query = query) }
        .distinctUntilChanged()
        .flatMapLatest { (source, filters) ->
            if (source == null) {
                flowOf(PagingData.empty())
            } else {
                Pager(PagingConfig(pageSize = 60, initialLoadSize = 120)) {
                    when {
                        filters.query.isNotBlank() -> {
                            val match = filters.query.toFtsMatch()
                            if (match.isBlank()) {
                                channelDao.pagingBySearch(
                                    source.id,
                                    ContentKind.TV.storageValue,
                                    filters.query.toLikePattern(),
                                )
                            } else {
                                channelDao.pagingByFts(
                                    source.id,
                                    ContentKind.TV.storageValue,
                                    match,
                                )
                            }
                        }
                        filters.favoritesOnly -> channelDao.pagingFavorites(
                            source.id,
                            ContentKind.TV.storageValue,
                        )
                        filters.categoryId != null -> channelDao.pagingByCategory(
                            source.id,
                            filters.categoryId,
                        )
                        filters.language != null -> channelDao.pagingByLanguage(
                            source.id,
                            ContentKind.TV.storageValue,
                            filters.language,
                        )
                        else -> channelDao.pagingBySource(source.id, ContentKind.TV.storageValue)
                    }
                }.flow.cachedIn(viewModelScope)
            }
        }

    fun selectAll() {
        _filters.update { it.copy(favoritesOnly = false, categoryId = null, language = null, query = "") }
    }

    fun selectFavorites() {
        _filters.update { it.copy(favoritesOnly = true, categoryId = null, language = null, query = "") }
    }

    fun selectCategory(categoryId: Long) {
        _filters.update { it.copy(favoritesOnly = false, categoryId = categoryId, query = "") }
    }

    fun selectLanguage(language: String?) {
        _filters.update { it.copy(language = language, favoritesOnly = false, categoryId = null, query = "") }
    }

    fun onQueryChange(query: String) {
        _filters.update { Filters(query = query.trimStart()) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            channelDao.setFavorite(channel.id, !channel.isFavorite)
        }
    }

    private companion object {
        /** Sueño mínimo entre recálculos y techo cuando no hay borde próximo. */
        const val MIN_TICK_MS = 1_000L
        const val MAX_TICK_MS = 10 * 60_000L
    }
}
