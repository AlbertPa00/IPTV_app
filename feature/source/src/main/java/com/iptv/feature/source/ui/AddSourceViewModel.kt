package com.iptv.feature.source.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.feature.source.R
import com.iptv.feature.source.data.SourceRepository
import com.iptv.feature.source.domain.SourceError
import com.iptv.feature.source.domain.SourceSyncPhase
import com.iptv.feature.source.domain.SyncStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    private val repository: SourceRepository,
) : ViewModel() {

    enum class Tab { M3U, XTREAM }

    data class UiState(
        val tab: Tab = Tab.M3U,
        val listName: String = "",
        val listUrl: String = "",
        val server: String = "",
        val username: String = "",
        val password: String = "",
        val accountName: String = "",
        val showPassword: Boolean = false,
        val busy: Boolean = false,
        val step: SyncStep? = null,
        val count: Int = 0,
        @StringRes val errorRes: Int? = null,
        val completedSourceId: Long? = null,
        val partialDoneSourceId: Long? = null,
        val partialSections: List<Int> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun onTabSelect(tab: Tab) = _uiState.update { it.copy(tab = tab, errorRes = null) }
    fun onListNameChange(value: String) = _uiState.update { it.copy(listName = value) }
    fun onListUrlChange(value: String) = _uiState.update { it.copy(listUrl = value) }
    fun onServerChange(value: String) = _uiState.update { it.copy(server = value) }
    fun onUsernameChange(value: String) = _uiState.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value) }
    fun onAccountNameChange(value: String) = _uiState.update { it.copy(accountName = value) }
    fun onTogglePassword() = _uiState.update { it.copy(showPassword = !it.showPassword) }

    fun addM3uList() {
        val state = _uiState.value
        if (state.busy) return
        if (state.listUrl.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.source_error_url_required) }
            return
        }
        launchImport {
            repository.addM3uUrl(state.listName.takeIf { it.isNotBlank() }, state.listUrl)
        }
    }

    fun addM3uFile(uri: String) {
        val state = _uiState.value
        if (state.busy) return
        launchImport {
            repository.addM3uFile(state.listName.takeIf { it.isNotBlank() }, uri)
        }
    }

    fun addDemoList(name: String) {
        if (_uiState.value.busy) return
        launchImport {
            repository.addM3uUrl(name, DEMO_URL)
        }
    }

    fun loginXtream() {
        val state = _uiState.value
        if (state.busy) return
        if (state.server.isBlank() || state.username.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.source_error_fields_required) }
            return
        }
        launchImport {
            repository.loginAndSyncXtream(
                state.accountName.takeIf { it.isNotBlank() },
                state.server,
                state.username,
                state.password,
            )
        }
    }

    private fun launchImport(block: () -> kotlinx.coroutines.flow.Flow<SourceSyncPhase>) {
        job?.cancel()
        _uiState.update {
            it.copy(
                busy = true,
                errorRes = null,
                step = SyncStep.CONNECTING,
                count = 0,
                completedSourceId = null,
            )
        }
        job = viewModelScope.launch {
            block().collect { phase ->
                when (phase) {
                    is SourceSyncPhase.Progress ->
                        _uiState.update { it.copy(step = phase.step, count = phase.count) }

                    is SourceSyncPhase.Done ->
                        if (phase.missingSections.isEmpty()) {
                            _uiState.update { it.copy(busy = false, step = null, completedSourceId = phase.sourceId) }
                        } else {
                            _uiState.update {
                                it.copy(
                                    busy = false,
                                    step = null,
                                    partialDoneSourceId = phase.sourceId,
                                    partialSections = phase.missingSections.map(::sectionLabelRes),
                                )
                            }
                        }

                    is SourceSyncPhase.Failed ->
                        _uiState.update { it.copy(busy = false, step = null, errorRes = phase.error.toRes()) }
                }
            }
        }
    }

    fun confirmDone() {
        val id = _uiState.value.partialDoneSourceId ?: return
        _uiState.update { it.copy(partialDoneSourceId = null, completedSourceId = id) }
    }

    @StringRes
    private fun SourceError.toRes(): Int = when (this) {
        SourceError.InvalidUrl -> R.string.source_error_invalid_url
        SourceError.Network -> R.string.source_error_network
        SourceError.InvalidCredentials -> R.string.source_error_credentials
        is SourceError.AccountInactive -> R.string.source_error_inactive
        SourceError.EmptyPlaylist -> R.string.source_error_empty
        SourceError.FileAccess -> R.string.source_error_file_access
        SourceError.NotFound -> R.string.source_error_not_found
        SourceError.Unknown -> R.string.source_error_unknown
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private companion object {
        const val DEMO_URL = "https://iptv-org.github.io/iptv/countries/us.m3u"
    }
}
