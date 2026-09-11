package com.iptv.core.common.pip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado compartido de Picture-in-Picture entre la Activity (que recibe los
 * callbacks del sistema) y la pantalla del reproductor (que decide cuándo
 * entrar automáticamente y cómo renderizar).
 */
@Singleton
class PipController @Inject constructor() {

    /** El reproductor lo activa mientras haya reproducción en curso. */
    var autoEnterOnUserLeave: Boolean = false

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    fun onPipModeChanged(inPip: Boolean) {
        _isInPipMode.value = inPip
    }
}
