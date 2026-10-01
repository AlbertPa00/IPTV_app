package com.iptv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.feature.epg.data.EpgRepository
import com.iptv.feature.source.data.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RootViewModel @Inject constructor(
    sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    private val appPreferences: AppPreferences,
    private val sourceRepository: SourceRepository,
    epgRepository: EpgRepository,
) : ViewModel() {

    val hasSources: StateFlow<Boolean?> = sourceDao.observeCount()
        .map<Int, Boolean?> { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null hasta que las preferencias emiten: evita decidir el arranque a ciegas. */
    val onboardingCompleted: StateFlow<Boolean?> = appPreferences.onboardingCompleted
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeOnboarding() {
        appPreferences.setOnboardingCompleted(true)
    }

    // Catálogo: una sola sincronización automática por fuente y sesión —
    // evita bucles si la fuente devuelve legítimamente cero canales.
    private val catalogSyncAttempted = mutableSetOf<Long>()

    init {
        // Catálogo + EPG en segundo plano al abrir la app.
        //
        // Catálogo: si la fuente activa nunca se sincronizó o su catálogo
        // quedó vacío (sync interrumpida, BD limpiada), el refresco arranca
        // solo — antes dependía del botón manual o del worker diario.
        //
        // EPG: se refresca cuando el catálogo de la fuente activa cambia
        // (alta, refresco manual o cambio de fuente) o al abrir la app con
        // la guía obsoleta: el XMLTV cubre ~5 días, así que sin el chequeo
        // de antigüedad la recarga dependía por completo del worker diario —
        // que MIUI puede diferir horas o matar con el ahorro de batería.
        viewModelScope.launch {
            sourceDao.observeActive()
                .distinctUntilChanged { a, b ->
                    a?.id == b?.id && a?.lastSyncAt == b?.lastSyncAt
                }
                .collectLatest { source ->
                    if (source == null) return@collectLatest
                    val id = source.id
                    val catalogEmpty = channelDao.countBySource(id) == 0
                    if ((source.lastSyncAt == null || catalogEmpty) &&
                        catalogSyncAttempted.add(id)
                    ) {
                        // Al terminar, lastSyncAt cambia → la nueva emisión
                        // reevalúa la EPG sobre el catálogo ya poblado.
                        sourceRepository.refresh(source).collect { /* progreso no visible */ }
                    }
                    if (source.lastSyncAt == null) return@collectLatest
                    val now = System.currentTimeMillis()
                    val empty = !programmeDao.hasFutureProgrammes(id, now)
                    val stale = now - appPreferences.epgLastSyncAt() > EPG_STALE_MILLIS
                    if (empty || stale) {
                        epgRepository.syncActive().collect { /* progreso no visible */ }
                    }
                }
        }
    }

    private companion object {
        /** Antigüedad máxima de la EPG antes de resincronizar al abrir. */
        const val EPG_STALE_MILLIS = 12L * 60 * 60 * 1000
    }
}
