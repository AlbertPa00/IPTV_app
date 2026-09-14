package com.iptv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    epgRepository: EpgRepository,
) : ViewModel() {

    val hasSources: StateFlow<Boolean?> = sourceDao.observeCount()
        .map<Int, Boolean?> { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // La EPG se refresca sola cuando el catálogo de la fuente activa
        // cambia (alta, refresco manual o cambio de fuente), pero sólo si la
        // guía no tiene programas vigentes: sin esa comprobación cada arranque
        // en frío re-descargaba el XMLTV completo y cada refresco de catálogo
        // abortaba y reiniciaba una descarga en curso. El refresco diario del
        // worker sigue sincronizando la EPG siempre.
        viewModelScope.launch {
            sourceDao.observeActive()
                .map { it?.id to it?.lastSyncAt }
                .distinctUntilChanged()
                .collectLatest { (id, lastSyncAt) ->
                    if (id != null && lastSyncAt != null &&
                        !programmeDao.hasFutureProgrammes(id, System.currentTimeMillis())
                    ) {
                        epgRepository.syncActive().collect { /* progreso no visible */ }
                    }
                }
        }
    }
}
