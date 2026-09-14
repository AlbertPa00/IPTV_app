package com.iptv.feature.epg.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.storage.dao.GuideRow
import com.iptv.core.storage.dao.ProgrammeDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.feature.epg.data.EpgRepository
import com.iptv.feature.epg.data.EpgSyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class GuideViewModel @Inject constructor(
    sourceDao: SourceDao,
    programmeDao: ProgrammeDao,
    private val repository: EpgRepository,
) : ViewModel() {
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    // replay = MAX: al volver a la pestaña se re-emite la guía previa sin
    // parpadeo a vacío mientras guideSnapshot vuelve a consultar.
    val guide: StateFlow<List<GuideRow>> = sourceDao.observeActive()
        .flatMapLatest { source ->
            if (source == null) {
                flowOf(emptyList())
            } else {
                programmeDao.observeHasProgrammes(source.id)
                    .distinctUntilChanged()
                    .flatMapLatest { has ->
                        if (!has) flowOf(emptyList())
                        else clock.map { now -> programmeDao.guideSnapshot(source.id, now) }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, Long.MAX_VALUE), emptyList())

    val syncState = MutableStateFlow<EpgSyncState?>(null)

    fun sync() {
        if (syncState.value is EpgSyncState.Progress) return
        viewModelScope.launch {
            repository.syncActive().collect { syncState.value = it }
        }
    }
}
