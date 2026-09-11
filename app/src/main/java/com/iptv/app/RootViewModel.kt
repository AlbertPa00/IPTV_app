package com.iptv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    epgRepository: EpgRepository,
) : ViewModel() {

    val hasSources: StateFlow<Boolean?> = sourceDao.observeCount()
        .map<Int, Boolean?> { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // La EPG se refresca sola cada vez que el catálogo de la fuente activa
        // cambia (alta, refresco manual o cambio de fuente): antes solo se
        // actualizaba desde el botón de la guía.
        viewModelScope.launch {
            sourceDao.observeActive()
                .map { it?.id to it?.lastSyncAt }
                .distinctUntilChanged()
                .collectLatest { (id, lastSyncAt) ->
                    if (id != null && lastSyncAt != null) {
                        epgRepository.syncActive().collect { /* progreso no visible */ }
                    }
                }
        }
    }
}
