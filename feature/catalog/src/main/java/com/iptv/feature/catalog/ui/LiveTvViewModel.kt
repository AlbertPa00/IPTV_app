package com.iptv.feature.catalog.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.common.text.TextNormalizer
import com.iptv.core.common.tvlink.TvLinkClient
import com.iptv.core.common.tvlink.TvLinkConnection
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.GuideRow
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.ProgrammeEntity
import com.iptv.core.storage.entity.SourceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
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
    private val tvLinkClient: TvLinkClient,
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

    // Recarga local (pull-to-refresh): el tick re-suscribe las consultas de
    // categorías y guía; la lista de canales la revalida la pantalla con
    // LazyPagingItems.refresh(). El indicador tiene un suelo mínimo para que
    // el gesto se perciba aunque Room responda al instante.
    private val refreshTick = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        refreshTick.update { it + 1 }
        viewModelScope.launch {
            delay(MIN_REFRESH_DWELL_MS)
            _isRefreshing.value = false
        }
    }

    /** Pista única de primer uso ("toca un canal"): visible hasta que se marca vista. */
    val showQuickHint: StateFlow<Boolean> = appPreferences.liveHintSeen
        .map { seen -> !seen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markQuickHintSeen() {
        appPreferences.setLiveHintSeen()
    }

    // -- Enviar a la TV vinculada -----------------------------------------------

    /** Nombre de la TV conectada vía enlace local; null si no hay mando activo. */
    val remoteTvName: StateFlow<String?> = tvLinkClient.connection
        .map { conn -> conn.deviceName.takeIf { conn.state == TvLinkConnection.CONNECTED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Reproduce el canal en la TV vinculada (no toca la sesión local). */
    fun playOnTv(channelId: Long) {
        tvLinkClient.remote.value?.play(channelId)
    }

    // replay = MAX: al volver a la pestaña se re-emite el último valor sin
    // parpadeo a vacío; el upstream igualmente descansa a los 5 s sin UI.
    private val activeSource: StateFlow<SourceEntity?> = sourceDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), null)

    val categories: StateFlow<List<CategoryEntity>> = combine(activeSource, refreshTick) { s, _ -> s }
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
    val guideByChannel: StateFlow<Map<Long, GuideRow>> = combine(activeSource, refreshTick) { s, _ -> s }
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
    }.flowOn(Dispatchers.Default)

    // El texto de búsqueda se debilita para no reconstruir el Pager a cada
    // pulsación; el resto de filtros (favoritos, grupo) aplican al instante.
    private val debouncedQuery = _filters.map { it.query }
        .distinctUntilChanged()
        .debounce { if (it.isBlank()) 0L else 300L }

    /**
     * Emisión futura o en curso encontrada en la guía. `channelCount` cuenta
     * las variantes del canal que la emiten (HD/4K/regionales); se ofrece la
     * primera, que es la de mejor orden en la lista.
     */
    data class ProgrammeHit(
        val programmeId: Long,
        val title: String,
        val startUtc: Long,
        val endUtc: Long,
        val channelId: Long?,
        val channelName: String?,
        val channelLogoUrl: String?,
        val channelCount: Int,
    )

    /**
     * Resultados "cuándo lo echan": FTS sobre títulos de la EPG, sólo mientras
     * hay búsqueda activa. Las variantes del mismo programa (varios canales)
     * se colapsan en una fila.
     */
    val programmeResults: StateFlow<List<ProgrammeHit>> = combine(
        activeSource,
        _filters,
        debouncedQuery,
    ) { s, f, q -> Triple(s, f, q) }
        .flatMapLatest { (source, filters, query) ->
            val match = query.toFtsMatch()
            if (source == null || match.isBlank()) {
                flowOf(emptyList())
            } else {
                flowOf(searchProgrammes(source.id, match, filters))
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * FTS de programas + resolución de canales en dos consultas indexadas
     * (un JOIN con OR no usaría índice). El dedup y el emparejamiento con
     * el canal se hacen en memoria — mismo patrón que `guideSnapshot`.
     */
    private suspend fun searchProgrammes(
        sourceId: Long,
        match: String,
        filters: Filters,
    ): List<ProgrammeHit> {
        val programmes = programmeDao.searchProgrammeRows(
            sourceId,
            match,
            System.currentTimeMillis(),
            PROGRAMME_SEARCH_ROWS,
        )
        if (programmes.isEmpty()) return emptyList()
        val keys = programmes.mapTo(HashSet()) { it.channelKey }.apply { remove("") }.toList()
        val names = programmes.mapTo(HashSet()) { it.channelNameNorm }.apply { remove("") }.toList()
        var channels =
            if (keys.isEmpty() && names.isEmpty()) emptyList()
            else programmeDao.liveChannelsForKeys(sourceId, keys, names)
        val scoped = filters.categoryId != null || filters.favoritesOnly || filters.language != null
        if (scoped) {
            channels = channels.filter { ch ->
                (filters.categoryId == null || ch.categoryId == filters.categoryId) &&
                    (!filters.favoritesOnly || ch.isFavorite) &&
                    (filters.language == null || ch.language == filters.language)
            }
        }
        return toProgrammeHits(programmes, channels)
            .let { hits -> if (scoped) hits.filter { it.channelCount > 0 } else hits }
            .take(PROGRAMME_SEARCH_LIMIT)
    }

    /**
     * Un Pager por (origen, filtros): cachedIn conserva las páginas cargadas
     * y las re-emite a un nuevo coleccionista — volver del reproductor o de
     * una ficha repite el snapshot al instante en lugar de paginar de nuevo.
     */
    private data class PagingKey(val sourceId: Long, val filters: Filters)

    private val pagingFlows = LinkedHashMap<PagingKey, Flow<PagingData<ChannelEntity>>>()

    private fun pagingFlow(key: PagingKey): Flow<PagingData<ChannelEntity>> {
        while (pagingFlows.size >= MAX_PAGING_CACHE) {
            pagingFlows.remove(pagingFlows.keys.first())
        }
        return pagingFlows.getOrPut(key) {
            val filters = key.filters
            Pager(PagingConfig(pageSize = 60, initialLoadSize = 120)) {
                when {
                    filters.query.isNotBlank() -> {
                        val match = filters.query.toFtsMatch()
                        if (match.isBlank()) {
                            channelDao.pagingBySearchScoped(
                                key.sourceId,
                                ContentKind.TV.storageValue,
                                filters.query.toLikePattern(),
                                filters.categoryId,
                                filters.favoritesOnly,
                                filters.language,
                            )
                        } else {
                            channelDao.pagingByFtsScoped(
                                key.sourceId,
                                ContentKind.TV.storageValue,
                                match,
                                filters.categoryId,
                                filters.favoritesOnly,
                                filters.language,
                            )
                        }
                    }
                    filters.favoritesOnly -> channelDao.pagingFavorites(
                        key.sourceId,
                        ContentKind.TV.storageValue,
                    )
                    filters.categoryId != null -> channelDao.pagingByCategory(
                        key.sourceId,
                        filters.categoryId,
                    )
                    filters.language != null -> channelDao.pagingByLanguage(
                        key.sourceId,
                        ContentKind.TV.storageValue,
                        filters.language,
                    )
                    else -> channelDao.pagingBySource(key.sourceId, ContentKind.TV.storageValue)
                }
            }.flow.cachedIn(viewModelScope)
        }
    }

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        activeSource,
        _filters,
        debouncedQuery,
    ) { source, filters, query -> source to filters.copy(query = query) }
        .distinctUntilChanged()
        .flatMapLatest { (source, filters) ->
            // El null inicial del origen es "aún cargando": no emitir nada
            // mantiene loadState.refresh=Loading y la pantalla muestra el
            // indicador en lugar de un vacío transitorio.
            if (source == null) {
                emptyFlow()
            } else {
                pagingFlow(PagingKey(source.id, filters))
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
        _filters.update { it.copy(query = query.trimStart()) }
    }

    // Estado optimista de favoritos, granular por canal: sólo se recompone
    // la fila pulsada — un mapa global re-emitía toda la lista por toggle.
    private val favStates: SnapshotStateMap<Long, Boolean> = mutableStateMapOf()

    /** Override optimista del favorito, o null si no se ha tocado. */
    fun favOverride(id: Long): Boolean? = favStates[id]

    fun toggleFavorite(channel: ChannelEntity) {
        val target = !(favStates[channel.id] ?: channel.isFavorite)
        favStates[channel.id] = target
        viewModelScope.launch { channelDao.setFavorite(channel.id, target) }
    }

    // -- Personalización de canales -----------------------------------------

    /** Oculta el canal en catálogo, búsqueda y guía (reversible en Ajustes). */
    fun hideChannel(channel: ChannelEntity) {
        viewModelScope.launch { channelDao.setHidden(channel.id, true) }
    }

    /** Renombra la copia del usuario; la EPG sigue casando por el proveedor. */
    fun renameChannel(channel: ChannelEntity, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty() || clean == channel.name) return
        viewModelScope.launch {
            channelDao.rename(channel.id, clean, TextNormalizer.searchKey(clean))
        }
    }

    fun restoreChannelName(channel: ChannelEntity) {
        viewModelScope.launch { channelDao.restoreName(channel.id) }
    }

    /**
     * Ámbito reordenable visible (favoritos o una categoría concreta), como
     * lista completa no paginada para el drag & drop. `null` en "Todos" y
     * con búsqueda activa: ahí el orden siempre es el del proveedor.
     */
    val reorderScope: StateFlow<List<ChannelEntity>?> = combine(activeSource, _filters, ::Pair)
        .flatMapLatest { (source, filters) ->
            when {
                source == null || filters.query.isNotBlank() -> flowOf(null)
                filters.favoritesOnly ->
                    channelDao.observeFavoritesForReorder(source.id, ContentKind.TV.storageValue)
                filters.categoryId != null ->
                    channelDao.observeCategoryForReorder(source.id, filters.categoryId)
                else -> flowOf(null)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Persiste el orden tras un drag & drop: materializa `customOrder` en el
     * ámbito entero leyendo la entidad fresca de la DB (los items de la UI
     * pueden estar stale si algo cambió a mitad del gesto). Los canales que
     * lleguen después por resincronización se insertan en su posición de
     * proveedor.
     */
    fun persistReorder(orderedIds: List<Long>) {
        val scope = reorderScope.value ?: return
        val byId = scope.associateBy { it.id }
        viewModelScope.launch {
            channelDao.updateAll(
                orderedIds.mapIndexedNotNull { i, id -> byId[id]?.copy(customOrder = i) },
            )
        }
    }

    private companion object {
        /** Sueño mínimo entre recálculos y techo cuando no hay borde próximo. */
        const val MIN_TICK_MS = 1_000L
        const val MAX_TICK_MS = 10 * 60_000L
        const val MIN_REFRESH_DWELL_MS = 600L
        const val MAX_PAGING_CACHE = 12
        const val PROGRAMME_SEARCH_LIMIT = 80
        /** Filas FTS brutas antes de colapsar variantes del mismo programa. */
        const val PROGRAMME_SEARCH_ROWS = 200
    }
}

/**
 * Colapsa programas repetidos (el mismo título a la misma hora en variantes
 * HD/4K/regionales) en una fila por emisión y resuelve su canal por tvgId o
 * por el nombre del proveedor — así un canal renombrado no pierde su EPG.
 * Función pura a nivel de archivo para poder probarla sin Room.
 */
internal fun toProgrammeHits(
    programmes: List<ProgrammeEntity>,
    channels: List<ChannelEntity>,
): List<LiveTvViewModel.ProgrammeHit> {
    val byTvgId = channels.filter { !it.tvgId.isNullOrBlank() }.associateBy { it.tvgId }
    val byName = channels.associateBy { it.providerNameNorm }
    return programmes.groupBy { it.title to it.startUtc }
        .map { (key, group) ->
            val emitting = group.mapNotNull { p ->
                byTvgId[p.channelKey] ?: byName[p.channelNameNorm]
            }.distinctBy(ChannelEntity::id)
            val best = emitting.firstOrNull()
            val first = group.first()
            LiveTvViewModel.ProgrammeHit(
                programmeId = first.id,
                title = key.first,
                startUtc = key.second,
                endUtc = first.endUtc,
                channelId = best?.id,
                channelName = best?.name,
                channelLogoUrl = best?.logoUrl,
                channelCount = emitting.size,
            )
        }
}
