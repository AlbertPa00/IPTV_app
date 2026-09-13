package com.iptv.feature.player.ui

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import coil.compose.AsyncImage
import com.iptv.core.designsystem.components.LoadingState
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.feature.player.R
import com.iptv.feature.player.cast.CastRouteButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PlayerCarmine = Color(0xFFE5093D)
private val PlayerGraphite = Color(0xFF17191E)
private val PlayerMuted = Color(0xFFB8BAC0)

/** Tipo de ajuste por deslizamiento vertical: brillo (izquierda) o volumen. */
private enum class GestureKind { BRIGHTNESS, VOLUME }
private data class GestureHud(val kind: GestureKind, val fraction: Float)

/** Pantalla completa cuyos controles operan sobre el Player local o remoto activo. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var channelListVisible by remember { mutableStateOf(false) }
    val channelList by viewModel.channelList.collectAsStateWithLifecycle()
    val hasChannelList by viewModel.hasChannelList.collectAsStateWithLifecycle()

    LaunchedEffect(channelListVisible) {
        if (channelListVisible) viewModel.requestChannelList()
    }
    val isInPipMode by viewModel.isInPipMode.collectAsStateWithLifecycle()
    val castVolume by viewModel.castVolume.collectAsStateWithLifecycle()
    val castMuted by viewModel.castMuted.collectAsStateWithLifecycle()
    var tracksDialogVisible by remember { mutableStateOf(false) }
    var tracksTick by remember { mutableStateOf(0) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var askedMediaPerms by rememberSaveable { mutableStateOf(false) }
    var sleepDialogVisible by remember { mutableStateOf(false) }
    var gestureHud by remember { mutableStateOf<GestureHud?>(null) }

    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val hudScope = rememberCoroutineScope()

    fun readBrightness(): Float =
        activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f

    fun applyBrightness(fraction: Float) {
        activity?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = fraction }
        }
    }

    fun readVolume(): Float = audioManager?.let {
        val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        it.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    } ?: 0f

    fun applyVolume(fraction: Float) {
        audioManager?.let {
            val max = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            it.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (fraction * max).roundToInt().coerceIn(0, max),
                0,
            )
        }
    }

    // La pantalla vuelve al brillo del sistema al salir del reproductor.
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onTracksChanged(tracks: Tracks) { tracksTick++ }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    // Android 13+: el servicio de Cast publica notificación de control y la
    // búsqueda de dispositivos usa NEARBY_WIFI_DEVICES. Se piden en contexto,
    // la primera vez que el botón de Cast está disponible.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    LaunchedEffect(state.castButtonAvailable) {
        if (state.castButtonAvailable && !askedMediaPerms && Build.VERSION.SDK_INT >= 33) {
            askedMediaPerms = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.NEARBY_WIFI_DEVICES,
                ),
            )
        }
    }

    DisposableEffect(fullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (fullscreen) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Posición y duración para la barra de progreso en contenido bajo demanda.
    LaunchedEffect(player, state.isLive) {
        while (true) {
            if (!state.isLive) {
                positionMs = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
                val d = player?.duration ?: 0L
                durationMs = if (d > 0) d else 0L
            }
            delay(500)
        }
    }

    // PiP: al salir con Home se minimiza si hay reproducción local en curso.
    // Durante Cast no tiene sentido: el PiP mostraría una ventana negra.
    LaunchedEffect(isPlaying, state.isCasting) {
        viewModel.setPipAutoEnter(isPlaying && !state.isCasting)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                activity?.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setAutoEnterEnabled(isPlaying && !state.isCasting)
                        .build(),
                )
            }
        }
    }

    // Sincroniza el volumen del receptor al entrar en modo Cast.
    LaunchedEffect(state.isCasting) {
        if (state.isCasting) viewModel.refreshCastVolume()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.setPipAutoEnter(false) }
    }

    // Auto-ocultado de controles mientras se reproduce (como en cualquier
    // reproductor moderno); pausado, al arrastrar la barra o con la lista de
    // canales abierta permanecen visibles.
    LaunchedEffect(controlsVisible, isPlaying, fullscreen, scrubFraction == null, channelListVisible) {
        if (controlsVisible && isPlaying && scrubFraction == null && !channelListVisible) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
        }
    }

    BackHandler {
        when {
            channelListVisible -> channelListVisible = false
            fullscreen -> fullscreen = false
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Toque simple: mostrar/ocultar controles al instante (la espera del
        // detector de doble toque hacía lenta la respuesta). Doble toque en
        // VOD: ±10 s según la mitad de la pantalla. En PiP no hay gestos.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(state.isLive, isInPipMode) {
                    if (isInPipMode) return@pointerInput
                    var lastTapAt = 0L
                    detectTapGestures { offset ->
                        val now = SystemClock.uptimeMillis()
                        val isDoubleTap = now - lastTapAt <= ViewConfiguration.getDoubleTapTimeout()
                        lastTapAt = now
                        if (isDoubleTap && !state.isLive) {
                            controlsVisible = true
                            val delta = if (offset.x < size.width / 2f) -SEEK_STEP_MS else SEEK_STEP_MS
                            player?.let {
                                it.seekTo((it.currentPosition + delta).coerceIn(0L, durationMs))
                            }
                        } else {
                            controlsVisible = !controlsVisible
                        }
                    }
                }
                .pointerInput(state.isCasting, isInPipMode) {
                    if (isInPipMode) return@pointerInput
                    var kind = GestureKind.VOLUME
                    var fraction = 0f
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            kind = if (offset.x < size.width / 2f) {
                                GestureKind.BRIGHTNESS
                            } else {
                                GestureKind.VOLUME
                            }
                            fraction = if (kind == GestureKind.BRIGHTNESS) {
                                readBrightness()
                            } else if (state.isCasting) {
                                castVolume
                            } else {
                                readVolume()
                            }
                            gestureHud = GestureHud(kind, fraction)
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            fraction = (fraction - amount / (size.height * 0.6f)).coerceIn(0f, 1f)
                            if (kind == GestureKind.BRIGHTNESS) {
                                applyBrightness(fraction)
                            } else if (state.isCasting) {
                                viewModel.setCastVolume(fraction)
                            } else {
                                applyVolume(fraction)
                            }
                            gestureHud = GestureHud(kind, fraction)
                        },
                        onDragEnd = {
                            hudScope.launch {
                                delay(GESTURE_HUD_MS)
                                gestureHud = null
                            }
                        },
                        onDragCancel = { gestureHud = null },
                    )
                },
        )

        // Indicador temporal de brillo/volumen tras el gesto.
        gestureHud?.let { hud ->
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(if (hud.kind == GestureKind.BRIGHTNESS) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (hud.kind == GestureKind.BRIGHTNESS) {
                            Icons.Filled.BrightnessMedium
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = stringResource(
                            if (hud.kind == GestureKind.BRIGHTNESS) {
                                R.string.player_brightness
                            } else {
                                R.string.player_volume
                            },
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(hud.fraction * 100).roundToInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        // Emitiendo: la superficie de vídeo queda negra en el teléfono porque
        // el contenido va a la TV. Se muestra una pantalla informativa con el
        // canal, el dispositivo y el control de volumen siempre accesible.
        if (state.isCasting && !isInPipMode) {
            CastBackdrop(
                state = state,
                volume = castVolume,
                muted = castMuted,
                onVolumeChange = viewModel::setCastVolume,
                onMuteToggle = { viewModel.setCastMuted(!castMuted) },
                onStopCasting = viewModel::returnToMobile,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter).background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.92f), Color.Transparent)),
                    ),
                )
                Box(
                    Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter).background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))),
                    ),
                )

                Column(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.player_back),
                                tint = Color.White,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.size(6.dp, 36.dp).clip(CircleShape).background(PlayerCarmine))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = state.channel?.name.orEmpty(),
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            PlayerStatus(state)
                        }
                        IconButton(
                            onClick = {
                                controlsVisible = true
                                viewModel.toggleFavorite()
                            },
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = stringResource(
                                    if (state.channel?.isFavorite == true) {
                                        R.string.player_favorite_remove
                                    } else {
                                        R.string.player_favorite_add
                                    },
                                ),
                                tint = if (state.channel?.isFavorite == true) PlayerCarmine else Color.White,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                controlsVisible = true
                                sleepDialogVisible = true
                            },
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bedtime,
                                contentDescription = stringResource(R.string.player_sleep_timer),
                                tint = if (state.sleepTimerEndAtMs != null) PlayerCarmine else Color.White,
                            )
                        }
                    }
                    // Segunda fila: acciones secundarias — deja espacio real al
                    // título en pantallas estrechas (antes se aplastaba).
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isLive && hasChannelList) {
                            IconButton(
                                onClick = {
                                    controlsVisible = true
                                    channelListVisible = !channelListVisible
                                },
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = stringResource(R.string.player_channel_list),
                                    tint = Color.White,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        if (!state.isCasting) {
                            IconButton(
                                onClick = {
                                    controlsVisible = true
                                    tracksDialogVisible = true
                                },
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Subtitles,
                                    contentDescription = stringResource(R.string.player_tracks),
                                    tint = Color.White,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    controlsVisible = true
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT ->
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL ->
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                },
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AspectRatio,
                                    contentDescription = stringResource(R.string.player_aspect_ratio),
                                    tint = Color.White,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { activity?.enterPip() },
                                modifier = Modifier.size(48.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureInPictureAlt,
                                    contentDescription = stringResource(R.string.player_pip),
                                    tint = Color.White,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        CastRouteButton(
                            contentDescription = stringResource(R.string.player_cast),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = { fullscreen = !fullscreen },
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                imageVector = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = stringResource(
                                    if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen,
                                ),
                                tint = Color.White,
                            )
                        }
                    }
                }

                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isLive) {
                            IconButton(
                                onClick = {
                                    controlsVisible = true
                                    viewModel.playAdjacent(-1)
                                },
                                modifier = Modifier.size(56.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.player_previous_channel),
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Spacer(Modifier.width(28.dp))
                        } else {
                            IconButton(
                                onClick = {
                                    controlsVisible = true
                                    player?.let {
                                        it.seekTo((it.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
                                    }
                                },
                                modifier = Modifier.size(56.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Replay10,
                                    contentDescription = stringResource(R.string.player_seek_back),
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                            Spacer(Modifier.width(28.dp))
                        }
                        IconButton(
                            onClick = {
                                player?.let { if (it.isPlaying) it.pause() else it.play() }
                            },
                            modifier = Modifier.size(72.dp).clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.player_pause else R.string.player_play,
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        Spacer(Modifier.width(28.dp))
                        if (state.isLive) {
                            IconButton(
                                onClick = {
                                    controlsVisible = true
                                    viewModel.playAdjacent(1)
                                },
                                modifier = Modifier.size(56.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = stringResource(R.string.player_next_channel),
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    controlsVisible = true
                                    player?.let {
                                        it.seekTo((it.currentPosition + SEEK_STEP_MS).coerceIn(0L, durationMs))
                                    }
                                },
                                modifier = Modifier.size(56.dp).clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f)),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forward10,
                                    contentDescription = stringResource(R.string.player_seek_forward),
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                    }
                }

                if (!state.isLive && durationMs > 0) {
                    SeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        scrubFraction = scrubFraction,
                        onScrub = { scrubFraction = it },
                        onScrubFinished = { fraction ->
                            player?.seekTo((fraction * durationMs).toLong())
                            scrubFraction = null
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 18.dp),
                    )
                }
            }
        }

        // Lista lateral de canales: zapping sin salir del reproductor.
        AnimatedVisibility(
            visible = channelListVisible && state.isLive && !isInPipMode,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            ChannelListPanel(
                channels = channelList,
                currentId = state.channel?.id,
                onSelect = viewModel::playChannel,
                onClose = { channelListVisible = false },
            )
        }

        if (tracksDialogVisible) {
            // tracksTick fuerza recomposición cuando cambian las pistas.
            @Suppress("UNUSED_EXPRESSION") tracksTick
            TracksDialog(player = player, onDismiss = { tracksDialogVisible = false })
        }

        if (sleepDialogVisible) {
            SleepTimerDialog(
                endAtMs = state.sleepTimerEndAtMs,
                onSelect = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    sleepDialogVisible = false
                },
                onDismiss = { sleepDialogVisible = false },
            )
        }

        when {
            state.channel == null && state.errorRes == null -> LoadingState()
            state.errorRes != null -> ErrorOverlay(
                message = stringResource(state.errorRes!!),
                detail = state.castErrorDetail,
                actionText = if (state.showReturnToMobile) {
                    stringResource(R.string.player_return_to_mobile)
                } else {
                    stringResource(R.string.player_retry)
                },
                onAction = if (state.showReturnToMobile) viewModel::returnToMobile else viewModel::retry,
            )
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    scrubFraction: Float?,
    onScrub: (Float) -> Unit,
    onScrubFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shownFraction = scrubFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownPosition = (shownFraction * durationMs).toLong()
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = formatDuration(shownPosition),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = shownFraction,
            onValueChange = onScrub,
            onValueChangeFinished = { onScrubFinished(shownFraction) },
            colors = SliderDefaults.colors(
                thumbColor = PlayerCarmine,
                activeTrackColor = PlayerCarmine,
                inactiveTrackColor = PlayerGraphite,
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        Text(
            text = formatDuration(durationMs),
            color = PlayerMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Panel lateral con la lista de canales en directo; resalta el actual. */
@Composable
private fun ChannelListPanel(
    channels: List<ChannelEntity>,
    currentId: Long?,
    onSelect: (Long) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = Color(0xF20F1014),
        modifier = Modifier.fillMaxHeight().width(340.dp)
            .clickable(enabled = true, onClick = onClose),
    ) {
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
            items(channels, key = { it.id }) { channel ->
                val isCurrent = channel.id == currentId
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(if (isCurrent) PlayerCarmine.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { onSelect(channel.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(52.dp, 36.dp).clip(RoundedCornerShape(6.dp))
                            .background(PlayerGraphite),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(3.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = channel.name,
                        color = if (isCurrent) PlayerCarmine else Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Pantalla mostrada mientras se emite: sustituye al vídeo (que va a la TV y
 * en el teléfono se ve negro) por el canal, el dispositivo de destino, el
 * volumen del receptor y la opción de devolver la reproducción al móvil.
 */
@Composable
private fun CastBackdrop(
    state: PlayerViewModel.UiState,
    volume: Float,
    muted: Boolean,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onStopCasting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF14161C), Color(0xFF0A0B0F))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp).widthIn(max = 460.dp),
        ) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(16.dp))
                    .background(PlayerGraphite),
                contentAlignment = Alignment.Center,
            ) {
                if (state.channel?.logoUrl.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Filled.Cast,
                        contentDescription = null,
                        tint = PlayerCarmine,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    AsyncImage(
                        model = state.channel?.logoUrl,
                        contentDescription = state.channel?.name,
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.channel?.name.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Cast,
                    contentDescription = null,
                    tint = PlayerCarmine,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        R.string.player_casting_to,
                        state.castDeviceName ?: stringResource(R.string.player_cast_device),
                    ),
                    color = PlayerMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onMuteToggle) {
                    Icon(
                        imageVector = if (muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = stringResource(
                            if (muted) R.string.player_cast_unmute else R.string.player_cast_mute,
                        ),
                        tint = Color.White,
                    )
                }
                Slider(
                    value = if (muted) 0f else volume,
                    onValueChange = onVolumeChange,
                    colors = SliderDefaults.colors(
                        thumbColor = PlayerCarmine,
                        activeTrackColor = PlayerCarmine,
                        inactiveTrackColor = PlayerGraphite,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.player_cast_volume),
                color = PlayerMuted,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.player_cast_hint),
                color = PlayerMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = onStopCasting,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.player_return_to_mobile))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1_000).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private const val CONTROLS_HIDE_DELAY_MS = 4_000L
private const val SEEK_STEP_MS = 10_000L
private const val GESTURE_HUD_MS = 900L

private fun Activity.enterPip() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
    runCatching {
        enterPictureInPictureMode(
            android.app.PictureInPictureParams.Builder()
                .setAspectRatio(android.util.Rational(16, 9))
                .build(),
        )
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Selector de pistas de audio y subtítulos del contenido en reproducción.
 * Sólo local: en modo Cast la selección de pistas la gestiona el receptor.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun TracksDialog(player: Player?, onDismiss: () -> Unit) {
    // Tracks no es observable: sin este listener el check no se movía al
    // elegir pista y parecía que la selección no se aplicaba.
    var tracks by remember(player) { mutableStateOf(player?.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    val groups = tracks?.groups.orEmpty()
    // La misma pista puede aparecer en varios Tracks.Group (variantes HLS,
    // DVB): se fusionan por identidad para no repetir filas ni cabeceras.
    val audioOptions = remember(tracks) { mergeTrackOptions(groups, C.TRACK_TYPE_AUDIO) }
    val textOptions = remember(tracks) { mergeTrackOptions(groups, C.TRACK_TYPE_TEXT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_tracks), color = Color.White) },
        text = {
            if (audioOptions.isEmpty() && textOptions.isEmpty()) {
                Text(
                    stringResource(R.string.player_tracks_empty),
                    color = PlayerMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    if (audioOptions.isNotEmpty()) {
                        item { TrackSectionHeader(R.string.player_audio) }
                        items(audioOptions.size) { i ->
                            val option = audioOptions[i]
                            TrackRow(
                                label = option.label,
                                selected = option.selected,
                                enabled = option.enabled,
                                onClick = { selectTrackOption(player, C.TRACK_TYPE_AUDIO, option) },
                            )
                        }
                    }
                    if (textOptions.isNotEmpty()) {
                        item { TrackSectionHeader(R.string.player_subtitles) }
                        item {
                            val noneSelected = groups
                                .filter { it.type == C.TRACK_TYPE_TEXT }
                                .all { g -> (0 until g.length).none { g.isTrackSelected(it) } }
                            TrackRow(
                                label = stringResource(R.string.player_track_off),
                                selected = noneSelected,
                                onClick = {
                                    player?.trackSelectionParameters =
                                        player.trackSelectionParameters.buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                            .build()
                                },
                            )
                        }
                        items(textOptions.size) { i ->
                            val option = textOptions[i]
                            TrackRow(
                                label = option.label,
                                selected = option.selected,
                                enabled = option.enabled,
                                onClick = { selectTrackOption(player, C.TRACK_TYPE_TEXT, option) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.player_close), color = PlayerCarmine)
            }
        },
        containerColor = PlayerGraphite,
    )
}

/** Opción de pista ya fusionada: puede apuntar a varios grupos duplicados. */
private data class TrackOption(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean,
    val targets: List<Pair<TrackGroup, Int>>,
)

private fun mergeTrackOptions(
    groups: List<Tracks.Group>,
    type: Int,
): List<TrackOption> {
    val byKey = linkedMapOf<String, TrackOption>()
    var order = 0
    groups.filter { it.type == type }.forEach { group ->
        for (i in 0 until group.length) {
            val f = group.getTrackFormat(i)
            // El id suele variar entre grupos del mismo stream lógico (HLS/DVB);
            // la clave es la identidad visible para el usuario.
            val key = "${f.language}|${f.label}|${f.sampleMimeType}|${f.codecs}|${f.channelCount}|${f.roleFlags}|${f.selectionFlags}"
            val existing = byKey[key]
            if (existing == null) {
                order++
                byKey[key] = TrackOption(
                    label = trackLabel(f, order),
                    selected = group.isTrackSelected(i),
                    enabled = group.isTrackSupported(i),
                    targets = listOf(group.mediaTrackGroup to i),
                )
            } else {
                byKey[key] = existing.copy(
                    selected = existing.selected || group.isTrackSelected(i),
                    enabled = existing.enabled || group.isTrackSupported(i),
                    targets = existing.targets + (group.mediaTrackGroup to i),
                )
            }
        }
    }
    return byKey.values.toList()
}

private fun selectTrackOption(player: Player?, type: Int, option: TrackOption) {
    val builder = player?.trackSelectionParameters?.buildUpon()
        ?.setTrackTypeDisabled(type, false)
        ?.clearOverridesOfType(type)
        ?: return
    option.targets.forEach { (group, index) ->
        builder.setOverrideForType(TrackSelectionOverride(group, index))
    }
    player.trackSelectionParameters = builder.build()
}

/** Etiqueta legible: evita IDs tipo "1/8219" (DVB) cuando hay idioma. */
private fun trackLabel(format: Format, fallbackIndex: Int): String =
    format.label?.takeIf { it.any(Char::isLetter) }
        ?: format.language?.let {
            runCatching { java.util.Locale(it).displayLanguage }.getOrNull()
        }
        ?: format.label
        ?: format.id
        ?: "#$fallbackIndex"

@Composable
private fun TrackSectionHeader(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = PlayerCarmine,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Diálogo del temporizador de apagado: pausa la reproducción al cumplirse. */
@Composable
private fun SleepTimerDialog(
    endAtMs: Long?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_sleep_timer), color = Color.White) },
        text = {
            Column {
                if (endAtMs != null) {
                    val remaining = ((endAtMs - System.currentTimeMillis()) / 60_000L)
                        .coerceAtLeast(1L)
                    Text(
                        stringResource(R.string.player_sleep_active_in, remaining),
                        color = PlayerMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_sleep_minutes, minutes),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (endAtMs != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(null) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.player_sleep_off),
                            color = PlayerCarmine,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.player_close), color = PlayerCarmine)
            }
        },
        containerColor = PlayerGraphite,
    )
}

@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else PlayerMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PlayerCarmine,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PlayerStatus(state: PlayerViewModel.UiState) {
    when {
        state.isCasting -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(PlayerCarmine))
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(
                    R.string.player_casting_to,
                    state.castDeviceName ?: stringResource(R.string.player_cast_device),
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        state.isLive -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(PlayerCarmine))
            Spacer(Modifier.width(7.dp))
            Text(
                text = stringResource(R.string.player_live),
                color = PlayerCarmine,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        else -> Text(
            text = stringResource(R.string.player_on_demand),
            color = PlayerMuted,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun ErrorOverlay(
    message: String,
    actionText: String,
    onAction: () -> Unit,
    detail: String? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = PlayerGraphite,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(10.dp, 42.dp).clip(CircleShape).background(PlayerCarmine))
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.player_playback_interrupted),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    color = PlayerMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
                detail?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        color = PlayerMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = PlayerCarmine, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text(actionText, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
