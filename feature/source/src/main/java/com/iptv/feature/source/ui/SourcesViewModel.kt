package com.iptv.feature.source.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.entity.SourceEntity
import com.iptv.feature.source.data.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SourcesViewModel @Inject constructor(
    private val repository: SourceRepository,
    private val channelDao: ChannelDao,
) : ViewModel() {

    data class SourceRow(
        val source: SourceEntity,
        val channelCount: Int,
    )

    data class UiState(
        val rows: List<SourceRow> = emptyList(),
        val refreshingIds: Set<Long> = emptySet(),
        val pendingDelete: SourceEntity? = null,
    )

    private val refreshingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingDelete = MutableStateFlow<SourceEntity?>(null)

    val uiState: StateFlow<UiState> = combine(
        repository.observeSources().flatMapLatest { sources ->
            if (sources.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(sources.map { source ->
                    channelDao.observeCountBySource(source.id).flatMapLatest { count ->
                        flowOf(SourceRow(source, count))
                    }
                }) { rows -> rows.toList() }
            }
        },
        refreshingIds,
        pendingDelete,
    ) { rows, refreshing, pending ->
        UiState(rows = rows, refreshingIds = refreshing, pendingDelete = pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun setActive(id: Long) {
        viewModelScope.launch { repository.setActiveSource(id) }
    }

    fun refresh(source: SourceEntity) {
        if (source.id in refreshingIds.value) return
        refreshingIds.update { it + source.id }
        viewModelScope.launch {
            repository.refresh(source).collect { /* el progreso se refleja al terminar vía Room */ }
            refreshingIds.update { it - source.id }
        }
    }

    fun requestDelete(source: SourceEntity) {
        pendingDelete.value = source
    }

    fun dismissDelete() {
        pendingDelete.value = null
    }

    fun confirmDelete() {
        val target = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { repository.deleteSource(target.id) }
    }
}
