package com.iptv.feature.catalog.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.iptv.core.common.download.DownloadController
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.storage.dao.CategoryDao
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.ContinueWatchingItem
import com.iptv.core.storage.dao.DownloadDao
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadSections
import com.iptv.core.storage.entity.SourceEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Secciones bajo demanda (Películas y Series): descubrimiento por carruseles —
 * destacado, favoritos y una fila por categoría — más una parrilla paginada
 * para "ver todo" y para los resultados de búsqueda.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VodCatalogViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val downloadDao: DownloadDao,
    private val downloadController: DownloadController,
    private val prefs: AppPreferences,
    categoryDao: CategoryDao,
    sourceDao: SourceDao,
) : ViewModel() {

    // Las instancias de películas y series viven en la misma entrada de
    // navegación "home": no hay argumento de ruta, cada pantalla fija su
    // sección una vez con attach() (sólo la primera llamada hace efecto).
    private var attachedKind: ContentKind? = null
    val kind: ContentKind get() = checkNotNull(attachedKind) { "attach() antes de usar el VM" }

    fun attach(kind: ContentKind) {
        if (attachedKind == null) attachedKind = kind
    }

    /** Parrilla a pantalla completa que abre "Ver todo" en cada carrusel. */
    sealed interface Grid {
        data object All : Grid
        data object Favorites : Grid
        data object ContinueWatching : Grid
        data object Downloads : Grid
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

        /** Items con descarga local: películas en Películas, episodios en Series. */
        data class Downloads(override val items: List<BrowseItem>) : BrowseRow {
            override val grid get() = Grid.Downloads

            companion object {
                fun of(channels: List<ChannelEntity>) = Downloads(channels.map(::BrowseItem))
            }
        }
    }

    data class UiState(
        val query: String = "",
        val grid: Grid? = null,
        val language: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // replay = MAX: al volver a la pestaña se re-emite el último valor sin
    // parpadeo a vacío; el upstream igualmente descansa a los 5 s sin UI.
    private val activeSource: StateFlow<SourceEntity?> = sourceDao.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), null)

    private val categories: StateFlow<List<CategoryEntity>> = activeSource
        .flatMapLatest { source ->
            if (source == null) flowOf(emptyList())
            else categoryDao.observeBySource(source.id, kind.storageValue)
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

    private val language: StateFlow<String?> = _uiState
        .map { it.language }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), null)

    // Recarga local (pull-to-refresh): el tick re-suscribe browseRows y las
    // consultas a Room vuelven a ejecutarse, sin tocar la red.
    private val refreshTick = MutableStateFlow(0)

    /** Emisiones reales del upstream de [rows]: refresh() espera a la primera
     *  emisión posterior al gesto para apagar el indicador. */
    private val browseEmissions = MutableStateFlow(0)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** true tras la primera emisión real de [rows]: distingue "cargando" de "vacío". */
    private val _browseLoaded = MutableStateFlow(false)
    val browseLoaded: StateFlow<Boolean> = _browseLoaded.asStateFlow()

    // -- Descargas -------------------------------------------------------------
    // La sección de descargas depende del catálogo: Películas muestra
    // películas, Series muestra episodios descargados.

    private val downloadSection: String?
        get() = when (kind) {
            ContentKind.MOVIES -> DownloadSections.MOVIE
            ContentKind.SERIES -> DownloadSections.EPISODE
            ContentKind.TV -> null
        }

    /** Estado de descarga por channelId: lo consultan las tarjetas/menús. */
    val downloads: StateFlow<Map<Long, DownloadEntity>> = downloadDao.observeAll()
        .map { list -> list.associateBy(DownloadEntity::channelId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun enqueueDownload(channel: ChannelEntity) {
        val section = downloadSection ?: return
        viewModelScope.launch { downloadController.enqueue(channel.id, section) }
    }

    fun cancelDownload(channelId: Long) {
        viewModelScope.launch { downloadController.cancel(channelId) }
    }

    fun deleteDownload(downloadId: Long) {
        viewModelScope.launch { downloadController.delete(downloadId) }
    }

    /** Permisos de notificación/compartidos con Cast: se piden una sola vez. */
    fun wasMediaPermsAsked(): Boolean = prefs.wasCastPermissionsAsked()

    fun markMediaPermsAsked() = prefs.setCastPermissionsAsked()

    /**
     * Anticipa la visita a la pestaña: mantiene suscritos los StateFlow que
     * pintan la vista de exploración (carruseles, idiomas y badges de
     * descarga), de modo que las consultas a Room corran en segundo plano
     * antes de que el usuario abra Películas/Series.
     */
    private var prefetched = false

    fun prefetch() {
        if (prefetched) return
        prefetched = true
        viewModelScope.launch { rows.collect() }
        viewModelScope.launch { languages.collect() }
        viewModelScope.launch { downloads.collect() }
    }

    val rows: StateFlow<List<BrowseRow>> = combine(
        activeSource,
        categories,
        language,
        refreshTick,
    ) { source, cats, lang, tick -> Triple(source, cats, lang) to tick }
        .distinctUntilChanged()
        .flatMapLatest { (key, _) ->
            val source = key.first
            // El null inicial de stateIn es "aún cargando", no "sin origen":
            // marcar browseLoaded aquí pintaría el estado vacío antes de
            // tiempo (la app recién abierta parecía rota). Sólo cuenta una
            // emisión con origen real.
            browseRows(source, key.second, key.third)
                .onEach { if (source != null) _browseLoaded.value = true }
        }
        .onEach { browseEmissions.update { it + 1 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), emptyList())

    private fun browseRows(
        source: SourceEntity?,
        cats: List<CategoryEntity>,
        lang: String?,
    ): Flow<List<BrowseRow>> {
        if (source == null) return flowOf(emptyList())
        val kind = kind.storageValue
        // Con idioma seleccionado los carruseles se limitan a ese idioma.
        val visibleCats = if (lang == null) cats else cats.filter { it.language == lang }
        val continueWatching =
            playbackHistoryDao.observeContinueWatching(source.id, kind, CONTINUE_ROW_LIMIT)
        val favorites = channelDao.observeTopFavorites(source.id, kind, FAVORITES_ROW_LIMIT)
        val rest: Flow<List<BrowseRow>> = if (visibleCats.isEmpty()) {
            val top = if (lang == null) {
                channelDao.observeTopByKind(source.id, kind, ROW_LIMIT)
            } else {
                channelDao.observeTopByLanguage(source.id, kind, lang, ROW_LIMIT)
            }
            top.map { items ->
                if (items.isEmpty()) emptyList() else listOf(BrowseRow.All.of(items))
            }
        } else {
            // Se limitan los carruseles: un Flow por categoría con cientos de
            // grupos re-dispara cientos de consultas ante cada escritura en
            // `channels` (favorito, sync…). Con el índice (sourceId, kind,
            // sortOrder, name) cada consulta es una lectura de índice rápida y
            // nadie necesita 400 carruseles en pantalla.
            val perCategory = visibleCats.take(MAX_BROWSE_CATEGORIES).map { category ->
                channelDao.observeTopInCategory(source.id, kind, category.id, ROW_LIMIT)
                    .map { category to it }
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
                    if (lang == null && uncategorized.isNotEmpty()) {
                        add(BrowseRow.Uncategorized.of(uncategorized))
                    }
                }
            }
        }
        // La fila "Descargados" encabeza la vista: es el acceso al contenido
        // disponible sin conexión. Los canales llegan vía JOIN downloads→channels.
        val section = downloadSection
        val downloaded = if (section != null) {
            channelDao.observeDownloadedChannels(section, ROW_LIMIT)
        } else {
            flowOf(emptyList())
        }
        return combine(continueWatching, favorites, downloaded, rest) {
                watching, favs, dl, others ->
            buildList {
                if (dl.isNotEmpty()) add(BrowseRow.Downloads.of(dl))
                if (watching.isNotEmpty()) add(BrowseRow.ContinueWatching.of(watching))
                if (favs.isNotEmpty()) add(BrowseRow.Favorites.of(favs))
                addAll(others)
            }
        }
    }

    // Igual que en TV: no reconstruir el Pager a cada pulsación de búsqueda.
    private val debouncedQuery = _uiState.map { it.query }
        .distinctUntilChanged()
        .debounce { if (it.isBlank()) 0L else 300L }

    /**
     * Un Pager por (origen, parrilla, búsqueda): cachedIn conserva las
     * páginas ya cargadas y las re-emite a un nuevo coleccionista, así que
     * volver de una ficha o reabrir "Ver todo"/una búsqueda repite el
     * snapshot al instante en lugar de paginar desde cero.
     */
    private data class PagingKey(
        val sourceId: Long,
        val grid: Grid?,
        val query: String,
        val language: String?,
    )

    private val pagingFlows = LinkedHashMap<PagingKey, Flow<PagingData<ChannelEntity>>>()

    private fun pagingFlow(key: PagingKey): Flow<PagingData<ChannelEntity>> {
        while (pagingFlows.size >= MAX_PAGING_CACHE) {
            pagingFlows.remove(pagingFlows.keys.first())
        }
        return pagingFlows.getOrPut(key) {
            Pager(PagingConfig(pageSize = 60, initialLoadSize = 120)) {
                val kind = kind.storageValue
                when {
                    key.query.isNotBlank() -> {
                        val match = key.query.toFtsMatch()
                        if (match.isBlank()) {
                            channelDao.pagingBySearch(key.sourceId, kind, key.query.toLikePattern())
                        } else {
                            channelDao.pagingByFts(key.sourceId, kind, match)
                        }
                    }
                    else -> when (val grid = key.grid) {
                        is Grid.Category -> channelDao.pagingByCategory(key.sourceId, grid.id)
                        is Grid.Favorites -> channelDao.pagingFavorites(key.sourceId, kind)
                        is Grid.Downloads -> channelDao.pagingDownloadedChannels(
                            downloadSection ?: DownloadSections.MOVIE,
                        )
                        is Grid.ContinueWatching ->
                            playbackHistoryDao.pagingContinueWatching(key.sourceId, kind)
                        is Grid.Uncategorized -> channelDao.pagingUncategorized(key.sourceId, kind)
                        else ->
                            if (key.language != null) {
                                channelDao.pagingByLanguage(key.sourceId, kind, key.language)
                            } else {
                                channelDao.pagingBySource(key.sourceId, kind)
                            }
                    }
                }
            }.flow.cachedIn(viewModelScope)
        }
    }

    val paging: Flow<PagingData<ChannelEntity>> = combine(
        activeSource,
        _uiState,
        debouncedQuery,
    ) { source, state, query -> source to state.copy(query = query) }
        .distinctUntilChanged()
        .flatMapLatest { (source, state) ->
            // Sin emisión mientras el origen aún no llegó: LazyPagingItems
            // queda en refresh=Loading y la pantalla muestra su estado de
            // carga en lugar de un "vacío" transitorio.
            if (source == null) {
                emptyFlow()
            } else {
                pagingFlow(PagingKey(source.id, state.grid, state.query, state.language))
            }
        }

    /**
     * Recarga local de los carruseles (gesto "deslizar para refrescar").
     * El indicador espera a la primera emisión posterior al tick y dura al
     * menos [MIN_REFRESH_DWELL_MS]: sin ese suelo el gesto no se percibe.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        val mark = browseEmissions.value
        refreshTick.update { it + 1 }
        viewModelScope.launch {
            val dwell = launch { delay(MIN_REFRESH_DWELL_MS) }
            withTimeoutOrNull(MAX_REFRESH_WAIT_MS) {
                browseEmissions.first { it > mark }
            }
            dwell.join()
            _isRefreshing.value = false
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { UiState(query = query.trimStart(), language = it.language) }
    }

    fun selectLanguage(language: String?) {
        _uiState.update { it.copy(language = language) }
    }

    fun openGrid(row: BrowseRow) {
        _uiState.update { it.copy(query = "", grid = row.grid) }
    }

    fun closeGrid() {
        _uiState.update { it.copy(grid = null) }
    }

    // Estado optimista de favoritos: la estrella responde al instante y el
    // refresh de Paging confirma detrás. SnapshotStateMap trackea los get()
    // en composición: un put invalida solo a las tarjetas visibles que leyeron
    // el mapa — no a la pantalla entera como hacía el StateFlow en la raíz.
    private val favStates: SnapshotStateMap<Long, Boolean> = mutableStateMapOf()

    /** Override optimista del favorito, o null si no se ha tocado. */
    fun favOverride(id: Long): Boolean? = favStates[id]

    fun toggleFavorite(channel: ChannelEntity) {
        val target = !(favStates[channel.id] ?: channel.isFavorite)
        favStates[channel.id] = target
        viewModelScope.launch { channelDao.setFavorite(channel.id, target) }
    }

    private companion object {
        const val ROW_LIMIT = 14
        const val FAVORITES_ROW_LIMIT = 20
        const val CONTINUE_ROW_LIMIT = 12
        const val MAX_BROWSE_CATEGORIES = 60
        const val MAX_PAGING_CACHE = 12
        const val MIN_REFRESH_DWELL_MS = 600L
        const val MAX_REFRESH_WAIT_MS = 8_000L
    }
}
