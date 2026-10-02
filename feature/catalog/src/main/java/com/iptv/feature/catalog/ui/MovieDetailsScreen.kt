package com.iptv.feature.catalog.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.core.designsystem.components.ConfirmDeleteDialog
import com.iptv.core.designsystem.components.EmptyState
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.designsystem.components.LoadingState
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadStatus
import com.iptv.feature.catalog.R

/** Ficha de película: fondo, metadatos Xtream y acciones reproducir/reanudar. */
@Composable
fun MovieDetailsScreen(
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    viewModel: MovieDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val channel = state.channel
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    Box(Modifier.fillMaxSize().background(CinemaBlack)) {
        when {
            state.loading -> LoadingState()
            channel == null -> EmptyState(stringResource(R.string.details_not_found))
            else -> DetailsContent(
                state = state,
                download = download,
                onBack = onBack,
                onPlay = { onPlay(channel.id) },
                onRestart = { viewModel.playFromStart { onPlay(channel.id) } },
                onToggleFavorite = viewModel::toggleFavorite,
                onToggleDownload = {
                    // Con la descarga completada el botón borra el fichero:
                    // se pide confirmación antes de tocar el ViewModel.
                    val current = download
                    if (current?.status == DownloadStatus.DONE) {
                        pendingDelete = current
                    } else {
                        viewModel.toggleDownload()
                    }
                },
            )
        }
    }
    pendingDelete?.let { toDelete ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.catalog_download_delete_title),
            text = stringResource(R.string.catalog_download_delete_confirm, toDelete.title),
            onConfirm = {
                viewModel.deleteDownload()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun DetailsContent(
    state: MovieDetailsViewModel.UiState,
    download: com.iptv.core.storage.entity.DownloadEntity?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
) {
    val channel = state.channel ?: return
    val info = state.info

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // En horizontal el 16:9 sobre el ancho supera la altura de pantalla:
        // altura explícita del 55 % (sin aspectRatio, que mediría alto=ancho·9/16
        // antes de poder acotarlo).
        val bannerModifier = if (
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {
            Modifier
                .fillMaxWidth()
                .height(LocalConfiguration.current.screenHeightDp.dp * 0.55f)
        } else {
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        }
        Box(bannerModifier) {
            val backdrop = info?.backdropUrl ?: channel.logoUrl
            if (backdrop != null) {
                IptvAsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, CinemaBlack),
                    ),
                ),
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                    .size(48.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.details_back),
                    tint = Color.White,
                )
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = info?.title ?: channel.name,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            MetaRow(state)
            Spacer(Modifier.height(18.dp))
            ActionRow(
                state = state,
                download = download,
                onPlay = onPlay,
                onRestart = onRestart,
                onToggleFavorite = onToggleFavorite,
                onToggleDownload = onToggleDownload,
            )
            info?.plot?.takeIf { it.isNotBlank() }?.let { plot ->
                Spacer(Modifier.height(18.dp))
                Text(
                    text = plot,
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            InfoSection(state)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MetaRow(state: MovieDetailsViewModel.UiState) {
    val info = state.info ?: return
    val parts = listOfNotNull(
        info.year,
        info.duration,
        info.rating?.let { "★ $it" },
        info.ageRating,
    ).filter { it.isNotBlank() }
    if (parts.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text(
        text = parts.joinToString("  ·  "),
        color = MutedText,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ActionRow(
    state: MovieDetailsViewModel.UiState,
    download: com.iptv.core.storage.entity.DownloadEntity?,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleDownload: () -> Unit,
) {
    val channel = state.channel ?: return
    Column {
        if (state.hasProgress) {
            val progress = if (state.resumeDurationMs > 0) {
                (state.resumePositionMs.toFloat() / state.resumeDurationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                color = Carmine,
                trackColor = GraphiteLight.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.details_progress,
                    formatPosition(state.resumePositionMs),
                    formatPosition(state.resumeDurationMs),
                ),
                color = MutedText,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onPlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Carmine,
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (state.hasProgress) R.string.details_resume else R.string.details_play,
                    ),
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Graphite),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(
                        if (channel.isFavorite) R.string.catalog_favorite_remove
                        else R.string.catalog_favorite_add,
                    ),
                    tint = if (channel.isFavorite) Carmine else MutedText,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = onToggleDownload,
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Graphite),
            ) {
                DownloadIconState(download)
            }
        }
        if (state.hasProgress) {
            TextButton(onClick = onRestart) {
                Icon(
                    imageVector = Icons.Filled.RestartAlt,
                    contentDescription = null,
                    tint = MutedText,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.details_restart), color = MutedText)
            }
        }
    }
}

@Composable
private fun InfoSection(state: MovieDetailsViewModel.UiState) {
    val info = state.info ?: return
    val rows = listOfNotNull(
        info.director?.let { R.string.details_director to it },
        info.cast?.let { R.string.details_cast to it },
        info.genre?.let { R.string.details_genre to it },
        info.country?.let { R.string.details_country to it },
    ).filter { it.second.isNotBlank() }
    if (rows.isEmpty()) return

    Spacer(Modifier.height(22.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (labelRes, value) ->
            Row {
                Text(
                    text = stringResource(labelRes),
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(96.dp),
                )
                Text(
                    text = value,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Icono del botón de descarga de la ficha según el estado actual. */
@Composable
private fun DownloadIconState(
    download: com.iptv.core.storage.entity.DownloadEntity?,
) {
    when (download?.status) {
        DownloadStatus.DONE -> Icon(
            Icons.Filled.DownloadDone,
            contentDescription = stringResource(R.string.details_download_remove),
            tint = Carmine,
            modifier = Modifier.size(22.dp),
        )
        DownloadStatus.FAILED -> Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = stringResource(R.string.details_download_retry),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        DownloadStatus.QUEUED -> CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = Carmine,
        )
        DownloadStatus.DOWNLOADING -> {
            if (download.totalBytes > 0) {
                CircularProgressIndicator(
                    progress = { download.downloadedBytes / download.totalBytes.toFloat() },
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Carmine,
                    trackColor = GraphiteLight,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Carmine,
                )
            }
        }
        else -> Icon(
            Icons.Filled.Download,
            contentDescription = stringResource(R.string.details_download),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun formatPosition(ms: Long): String {
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
