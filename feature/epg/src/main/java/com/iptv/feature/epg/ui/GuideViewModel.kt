package com.iptv.feature.epg.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.storage.dao.GuideRow
import com.iptv.core.storage.dao.GuideSort
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.feature.epg.data.EpgRepository
import com.iptv.feature.epg.data.EpgSyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    sourceDao: SourceDao,
    private val programmeDao: ProgrammeDao,
    private val repository: EpgRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    /** Filtro de la guía, persistido en ajustes para recordar la elección. */
    data class GuideFilter(
        val favoritesOnly: Boolean = false,
        val sort: GuideSort = GuideSort.PROVIDER,
    )

    val filter: StateFlow<GuideFilter> = combine(
        prefs.guideFavoritesOnly,
        prefs.guideSort,
    ) { favoritesOnly, sort ->
        GuideFilter(
            favoritesOnly = favoritesOnly,
            sort = sort?.let { runCatching { GuideSort.valueOf(it) }.getOrNull() }
                ?: GuideSort.PROVIDER,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideFilter())

    val guide: StateFlow<List<GuideRow>> = combine(
        sourceDao.observeActive(),
        filter,
    ) { source, f -> source to f }
        .distinctUntilChanged()
        .flatMapLatest { (source, f) ->
            if (source == null) {
                flowOf(emptyList())
            } else {
                programmeDao.observeHasProgrammes(source.id)
                    .distinctUntilChanged()
                    .flatMapLatest { has ->
                        if (!has) flowOf(emptyList()) else guideRows(source.id, f)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Recalcula sólo cuando la guía puede cambiar: al llegar el borde de un
     * programa (fin del actual / inicio del siguiente) o cuando la tabla se
     * reescribe (importación EPG, detectada por la marca de agua). Sin sondeo
     * fijo: la pantalla anima el progreso con su propio reloj.
     */
    private fun guideRows(sourceId: Long, f: GuideFilter): Flow<List<GuideRow>> = flow {
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val rows = programmeDao.guideSnapshot(sourceId, now, f.favoritesOnly, f.sort)
            emit(rows)
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

    val syncState = MutableStateFlow<EpgSyncState?>(null)

    fun sync() {
        if (syncState.value is EpgSyncState.Progress) return
        viewModelScope.launch {
            repository.syncActive().collect { syncState.value = it }
        }
    }

    fun toggleFavorites() {
        prefs.setGuideFavoritesOnly(!filter.value.favoritesOnly)
    }

    fun selectSort(sort: GuideSort) {
        prefs.setGuideSort(sort.name)
    }

    private companion object {
        /** Sueño mínimo entre recálculos y techo cuando no hay borde próximo. */
        const val MIN_TICK_MS = 1_000L
        const val MAX_TICK_MS = 10 * 60_000L
    }
}
