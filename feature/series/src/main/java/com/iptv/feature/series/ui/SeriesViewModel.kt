package com.iptv.feature.series.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.feature.series.data.SeriesDetails
import com.iptv.feature.series.data.SeriesEpisode
import com.iptv.feature.series.data.SeriesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SeriesRepository,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val details: SeriesDetails? = null,
        val selectedSeason: Int? = null,
        val error: String? = null,
        val preparingEpisodeId: String? = null,
    )

    private val seriesId = checkNotNull(savedStateHandle.get<Long>("seriesId"))
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val _openPlayer = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openPlayer: SharedFlow<Long> = _openPlayer.asSharedFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.load(seriesId) }
                .onSuccess { details ->
                    _uiState.value = UiState(
                        loading = false,
                        details = details,
                        selectedSeason = details.seasons.firstOrNull()?.number,
                    )
                }
                .onFailure { error ->
                    _uiState.update { it.copy(loading = false, error = error.message ?: "Error al cargar la serie") }
                }
        }
    }

    fun selectSeason(number: Int) = _uiState.update { it.copy(selectedSeason = number) }

    fun play(episode: SeriesEpisode) {
        if (_uiState.value.preparingEpisodeId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(preparingEpisodeId = episode.id, error = null) }
            runCatching { repository.prepareEpisode(seriesId, episode) }
                .onSuccess { _openPlayer.emit(it) }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "No se pudo abrir el episodio") }
                }
            _uiState.update { it.copy(preparingEpisodeId = null) }
        }
    }
}
