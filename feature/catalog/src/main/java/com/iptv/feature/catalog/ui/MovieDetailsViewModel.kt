package com.iptv.feature.catalog.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.core.storage.dao.ChannelDao
import com.iptv.core.storage.dao.PlaybackHistoryDao
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.feature.catalog.data.VodInfo
import com.iptv.feature.catalog.data.VodInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MovieDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val vodInfoRepository: VodInfoRepository,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val channel: ChannelEntity? = null,
        val info: VodInfo? = null,
        val resumePositionMs: Long = 0L,
        val resumeDurationMs: Long = 0L,
        val error: String? = null,
    ) {
        val hasProgress: Boolean get() = resumePositionMs > 0L
    }

    private val channelId = checkNotNull(savedStateHandle.get<Long>("channelId"))
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val channel = channelDao.findById(channelId)
            if (channel == null) {
                _uiState.update { it.copy(loading = false, error = "not-found") }
                return@launch
            }
            val history = playbackHistoryDao.find(channelId)
            _uiState.update {
                it.copy(
                    loading = false,
                    channel = channel,
                    resumePositionMs = history?.positionMs ?: 0L,
                    resumeDurationMs = history?.durationMs ?: 0L,
                )
            }
            // Los metadatos llegan después: la ficha se muestra sin esperar.
            val info = vodInfoRepository.load(channel)
            if (info != null) _uiState.update { it.copy(info = info) }
        }
    }

    fun toggleFavorite() {
        val channel = _uiState.value.channel ?: return
        viewModelScope.launch {
            channelDao.setFavorite(channel.id, !channel.isFavorite)
            _uiState.update {
                it.copy(channel = it.channel?.copy(isFavorite = !channel.isFavorite))
            }
        }
    }

    /**
     * "Ver desde el principio": borra el progreso antes de navegar al
     * reproductor para que no reanude desde el punto guardado.
     */
    fun playFromStart(onReady: () -> Unit) {
        viewModelScope.launch {
            playbackHistoryDao.delete(channelId)
            _uiState.update { it.copy(resumePositionMs = 0L, resumeDurationMs = 0L) }
            onReady()
        }
    }
}
