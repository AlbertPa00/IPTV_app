package com.iptv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.common.prefs.AppPreferences
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.feature.epg.data.EpgRepository
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
    private val programmeDao: ProgrammeDao,
    private val appPreferences: AppPreferences,
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

    init {
        // La EPG se refresca sola cuando el catálogo de la fuente activa
        // cambia (alta, refresco manual o cambio de fuente) o al abrir la app
        // con la guía obsoleta: el XMLTV cubre ~5 días, así que sin el chequeo
        // de antigüedad la recarga dependía por completo del worker diario —
        // que MIUI puede diferir horas o matar con el ahorro de batería.
        // Sin estas comprobaciones cada arranque en frío re-descargaba el
        // XMLTV completo y cada refresco de catálogo abortaba una descarga
        // en curso.
        viewModelScope.launch {
            sourceDao.observeActive()
                .map { it?.id to it?.lastSyncAt }
                .distinctUntilChanged()
                .collectLatest { (id, lastSyncAt) ->
                    if (id == null || lastSyncAt == null) return@collectLatest
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
