package com.iptv.feature.catalog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.SourceDao
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.Kinds
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Búsqueda global sobre la fuente activa: canales, películas y series. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    sourceDao: SourceDao,
) : ViewModel() {

    data class Sections(
        val channels: List<ChannelEntity> = emptyList(),
        val movies: List<ChannelEntity> = emptyList(),
        val series: List<ChannelEntity> = emptyList(),
    ) {
        val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
    }

    data class UiState(val query: String = "")

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val sections: StateFlow<Sections> = combine(
        sourceDao.observeActive(),
        _uiState.map { it.query }.distinctUntilChanged().debounce(250),
    ) { source, query -> source to query }
        .flatMapLatest { (source, query) ->
            if (source == null || query.isBlank()) {
                flowOf(Sections())
            } else {
                val pattern = query.toLikePattern()
                combine(
                    channelDao.searchTop(source.id, Kinds.LIVE, pattern, SECTION_LIMIT),
                    channelDao.searchTop(source.id, Kinds.VOD, pattern, SECTION_LIMIT),
                    channelDao.searchTop(source.id, Kinds.SERIES, pattern, SECTION_LIMIT),
                ) { live, vod, series -> Sections(live, vod, series) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sections())

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query.trimStart()) }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch { channelDao.setFavorite(channel.id, !channel.isFavorite) }
    }

    private companion object {
        const val SECTION_LIMIT = 12
    }
}
