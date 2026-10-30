package com.iptv.feature.player.tv

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.state.PlayerStateObserver
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.designsystem.tv.tvSafeArea
import com.iptv.feature.player.R
import com.iptv.feature.player.ui.PlayerViewModel
import kotlinx.coroutines.delay

private const val CONTROLS_HIDE_DELAY_MS = 8_000L
private const val INFO_HIDE_DELAY_MS = 4_500L
private const val OSD_CONFIRM_DELAY_MS = 2_500L

/**
 * Shell de TV player-first: el vídeo es permanente y toda la navegación
 * son overlays sobre él (rail de canales, info, controles, OSD numérico).
 * Reutiliza [PlayerViewModel] — el motor es el mismo que en la pantalla
 * táctil; lo que cambia es el modelo de interacción (teclas, no gestos).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TvPlayerScreen(
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val channelList by viewModel.channelList.collectAsStateWithLifecycle()
    val hasChannelList by viewModel.hasChannelList.collectAsStateWithLifecycle()
    val playPause = player?.let { rememberPlayPauseButtonState(it) }

    // Buffering no tiene holder oficial en media3-ui-compose: se observa
    // con PlayerStateObserver, la primitiva sobre la que se construyen
    // todos los remember*State de la librería.
    var isBuffering by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        val p = player
        if (p == null) {
            isBuffering = false
            return@LaunchedEffect
        }
        PlayerStateObserver(p, Player.EVENT_PLAYBACK_STATE_CHANGED) {
            isBuffering = it.playbackState == Player.STATE_BUFFERING
        }.observe()
    }

    var overlay by remember { mutableStateOf(TvOverlay.NONE) }
    var osdDigits by remember { mutableStateOf("") }
    var lastInteractionAt by remember { mutableStateOf(System.currentTimeMillis()) }

    val activity = LocalContext.current.findActivity()

    // En TV no hay "salir del fullscreen": la pantalla no se apaga mientras
    // haya reproducción en curso.
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    fun confirmDigits() {
        val n = osdDigits.toIntOrNull() ?: 0
        osdDigits = ""
        val target = channelList.getOrNull(n - 1) ?: return
        viewModel.playChannel(target.id)
        overlay = TvOverlay.INFO
    }

    fun handleAction(action: TvAction) {
        when (action) {
            TvAction.OpenControls -> overlay = TvOverlay.CONTROLS
            TvAction.CloseOverlay -> { overlay = TvOverlay.NONE; osdDigits = "" }
            TvAction.OpenRail -> {
                viewModel.requestChannelList()
                overlay = TvOverlay.RAIL
            }
            TvAction.OpenInfo -> overlay = TvOverlay.INFO
            TvAction.TogglePlayPause -> playPause?.onClick()
            is TvAction.SeekBy -> player?.let {
                // TIME_UNSET en duration haría coerceIn lanzar (min > max).
                if (it.duration > 0) {
                    it.seekTo(
                        (it.currentPosition + action.deltaMs)
                            .coerceIn(0L, it.duration),
                    )
                }
            }
            is TvAction.ZapBy -> {
                viewModel.playAdjacent(action.delta)
                overlay = TvOverlay.INFO
            }
            is TvAction.Digit -> {
                osdDigits = (osdDigits + action.n).take(4)
                overlay = TvOverlay.NUMBER_OSD
            }
            TvAction.ConfirmDigits -> if (osdDigits.isNotEmpty()) confirmDigits()
            TvAction.Back -> onExit()
        }
    }

    val ctx = TvInputContext(
        overlay = overlay,
        isLive = state.isLive,
        hasChannelList = hasChannelList,
    )

    // Auto-ocultado: los controles se repliegan pasado el delay desde la
    // última tecla; la tarjeta info desaparece sola; el OSD confirma solo.
    LaunchedEffect(overlay, lastInteractionAt) {
        when (overlay) {
            TvOverlay.CONTROLS -> {
                delay(CONTROLS_HIDE_DELAY_MS)
                overlay = TvOverlay.NONE
            }
            TvOverlay.INFO -> {
                delay(INFO_HIDE_DELAY_MS)
                if (overlay == TvOverlay.INFO) overlay = TvOverlay.NONE
            }
            TvOverlay.NUMBER_OSD -> {
                delay(OSD_CONFIRM_DELAY_MS)
                if (overlay == TvOverlay.NUMBER_OSD) confirmDigits()
            }
            else -> Unit
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                val tvKey = TvKey(
                    code = event.nativeKeyEvent.keyCode,
                    action = if (event.type == KeyEventType.KeyDown) {
                        TvKeyAction.DOWN
                    } else {
                        TvKeyAction.UP
                    },
                    repeat = event.nativeKeyEvent.repeatCount,
                )
                when (val result = routeTvKey(tvKey, ctx)) {
                    is TvKeyResult.Perform -> {
                        lastInteractionAt = System.currentTimeMillis()
                        handleAction(result.action)
                        true
                    }
                    TvKeyResult.Consume -> true
                    TvKeyResult.Ignore -> false
                }
            },
    ) {
        PlayerSurface(
            player = player,
            modifier = Modifier.fillMaxSize(),
        )

        // Carga inicial / reconexión / pérdida de red.
        val reconnectAttempt = state.reconnectAttempt
        if ((isBuffering || reconnectAttempt != null) &&
            !state.isCasting && !state.isCastConnecting && state.errorRes == null
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(28.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when {
                            state.networkLost -> stringResource(R.string.player_no_connection)
                            reconnectAttempt != null ->
                                stringResource(R.string.player_reconnecting, reconnectAttempt)
                            else -> stringResource(R.string.player_loading)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Emitiendo: la superficie queda negra, se informa del destino.
        if (state.isCasting) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().tvSafeArea(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(
                            R.string.player_casting_to,
                            state.castDeviceName
                                ?: stringResource(R.string.player_cast_device),
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }

        // Error de reproducción: reintentar o salir.
        state.errorRes?.let { res ->
            TvErrorCard(
                message = stringResource(res),
                actionText = stringResource(R.string.player_retry),
                onAction = viewModel::retry,
                onDismiss = onExit,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // Tarjeta de canal: post-zap e INFO.
        AnimatedVisibility(
            visible = overlay == TvOverlay.INFO && !state.isCasting,
            enter = fadeIn() + slideInHorizontally { -it / 4 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).tvSafeArea(),
        ) {
            TvInfoCard(
                number = channelList.indexOfFirst { it.id == state.channel?.id }
                    .takeIf { it >= 0 }?.plus(1),
                name = state.channel?.name.orEmpty(),
                logoUrl = state.channel?.logoUrl,
                isLive = state.isLive,
            )
        }

        // OSD numérico.
        if (overlay == TvOverlay.NUMBER_OSD) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.TopEnd).tvSafeArea(),
            ) {
                Text(
                    text = osdDigits.ifEmpty { "_" },
                    color = Color.White,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
        }

        // Rail lateral de canales.
        AnimatedVisibility(
            visible = overlay == TvOverlay.RAIL,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            TvChannelRail(
                channels = channelList,
                currentChannelId = state.channel?.id,
                onPick = { id ->
                    viewModel.playChannel(id)
                    overlay = TvOverlay.INFO
                },
            )
        }

        // Barra de transporte.
        AnimatedVisibility(
            visible = overlay == TvOverlay.CONTROLS && !state.isCasting,
            enter = fadeIn() + slideInHorizontally { it / 6 },
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TvControlsOverlay(
                channel = state.channel,
                isLive = state.isLive,
                isPlaying = playPause?.showPlay == false,
                onTogglePlayPause = { handleAction(TvAction.TogglePlayPause) },
                onSeek = { delta -> handleAction(TvAction.SeekBy(delta)) },
                onOpenRail = { handleAction(TvAction.OpenRail) },
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Tarjeta de error a pantalla completa: acción principal + salida. */
@Composable
private fun TvErrorCard(
    message: String,
    actionText: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.player_playback_interrupted),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(actionText, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(
                        stringResource(R.string.player_back),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Tarjeta informativa del canal en curso (post-zap / tecla INFO). */
@Composable
private fun TvInfoCard(
    number: Int?,
    name: String,
    logoUrl: String?,
    isLive: Boolean,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            if (logoUrl != null) {
                IptvAsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(14.dp))
            }
            if (number != null) {
                Text(
                    text = number.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(14.dp))
            }
            Column {
                Text(
                    text = name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (isLive) R.string.player_live else R.string.player_on_demand,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
