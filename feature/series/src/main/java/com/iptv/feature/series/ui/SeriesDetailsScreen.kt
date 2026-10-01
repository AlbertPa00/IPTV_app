package com.iptv.feature.series.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.core.designsystem.components.ConfirmDeleteDialog
import com.iptv.core.designsystem.components.EmptyState
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.designsystem.components.ErrorState
import com.iptv.core.designsystem.components.LoadingState
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadStatus
import com.iptv.feature.series.R
import com.iptv.feature.series.data.SeriesDetails
import com.iptv.feature.series.data.SeriesEpisode

private val AccentRed: Color @Composable get() = MaterialTheme.colorScheme.primary
private val PageBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val CardBackground: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val MutedText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openPlayer.collect(onPlay) }
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    Box(Modifier.fillMaxSize().background(PageBackground)) {
        when {
            state.loading -> LoadingState()
            state.errorRes != null && state.details == null ->
                ErrorState(stringResource(state.errorRes!!), onRetry = viewModel::load)
            state.details != null -> SeriesContent(
                details = state.details!!,
                selectedSeason = state.selectedSeason,
                preparingEpisodeId = state.preparingEpisodeId,
                preparingDownloadId = state.preparingDownloadId,
                downloads = downloads,
                onSelectSeason = viewModel::selectSeason,
                onPlayEpisode = viewModel::play,
                onToggleDownload = { episode ->
                    // Episodio ya descargado: el toque borra el fichero, así
                    // que primero se confirma en el diálogo de abajo.
                    val existing = downloads[episode.id]
                    if (existing?.status == DownloadStatus.DONE) {
                        pendingDelete = existing
                    } else {
                        viewModel.toggleDownload(episode)
                    }
                },
            )
            else -> ErrorState(stringResource(R.string.series_error_load), onRetry = viewModel::load)
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).background(Color.Black.copy(alpha = .65f))) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.series_back),
                tint = Color.White,
            )
        }
    }
    pendingDelete?.let { download ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.series_download_delete_title),
            text = stringResource(R.string.series_download_delete_confirm, download.title),
            onConfirm = {
                pendingDelete = null
                viewModel.deleteDownloadById(download.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SeriesContent(
    details: SeriesDetails,
    selectedSeason: Int?,
    preparingEpisodeId: String?,
    preparingDownloadId: String?,
    downloads: Map<String, DownloadEntity>,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (SeriesEpisode) -> Unit,
    onToggleDownload: (SeriesEpisode) -> Unit,
) {
    val selected = details.seasons.firstOrNull { it.number == selectedSeason }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            IptvAsyncImage(
                model = details.coverUrl,
                contentDescription = stringResource(R.string.series_cover_description, details.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(details.title, color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                details.rating?.let {
                    Text(
                        text = stringResource(R.string.series_rating, it),
                        color = AccentRed,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                details.plot?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    stringResource(R.string.series_seasons),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                items(details.seasons, key = { it.number }) { season ->
                    FilterChip(
                        selected = season.number == selectedSeason,
                        onClick = { onSelectSeason(season.number) },
                        label = { Text(season.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CardBackground,
                            labelColor = MutedText,
                            selectedContainerColor = AccentRed,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            selected?.let {
                Text(
                    stringResource(R.string.series_episodes, it.number),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        if (selected == null) {
            item { EmptyState(stringResource(R.string.series_no_episodes), Modifier.height(220.dp)) }
        } else {
            items(selected.episodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    preparing = preparingEpisodeId == episode.id,
                    preparingDownload = preparingDownloadId == episode.id,
                    download = downloads[episode.id],
                    onPlay = onPlayEpisode,
                    onToggleDownload = onToggleDownload,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EpisodeRow(
    episode: SeriesEpisode,
    preparing: Boolean,
    preparingDownload: Boolean,
    download: DownloadEntity?,
    onPlay: (SeriesEpisode) -> Unit,
    onToggleDownload: (SeriesEpisode) -> Unit,
) {
    val playDescription = stringResource(R.string.series_play_episode, episode.title)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .background(CardBackground)
            .clickable(enabled = !preparing, role = Role.Button) { onPlay(episode) }
            .semantics { contentDescription = playDescription }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IptvAsyncImage(
            model = episode.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 128.dp, height = 72.dp).background(CardBackground),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                "${stringResource(R.string.series_episode_number, episode.number)} · ${episode.title}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.duration?.let { Text(stringResource(R.string.series_duration, it), color = MutedText, style = MaterialTheme.typography.bodySmall) }
            episode.plot?.let { Text(it, color = MutedText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        DownloadIcon(download, preparingDownload, Modifier.size(40.dp)) {
            onToggleDownload(episode)
        }
        if (preparing) CircularProgressIndicator(Modifier.size(30.dp), color = AccentRed)
        else Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AccentRed, modifier = Modifier.size(36.dp))
    }
}

/** Icono de descarga del episodio según el estado de la fila `downloads`. */
@Composable
private fun DownloadIcon(
    download: DownloadEntity?,
    preparingDownload: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val description = stringResource(
        when (download?.status) {
            DownloadStatus.DONE -> R.string.series_episode_downloaded
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING ->
                R.string.series_episode_download_cancel
            else -> R.string.series_episode_download
        },
    )
    IconButton(onClick = onClick, modifier = modifier) {
        when {
            preparingDownload -> CircularProgressIndicator(
                Modifier.size(22.dp),
                color = AccentRed,
                strokeWidth = 2.dp,
            )
            download?.status == DownloadStatus.DONE -> Icon(
                Icons.Filled.DownloadDone,
                contentDescription = description,
                tint = AccentRed,
            )
            download?.status == DownloadStatus.FAILED -> Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.error,
            )
            download?.status == DownloadStatus.QUEUED ||
                download?.status == DownloadStatus.DOWNLOADING -> {
                val pct = if (download.totalBytes > 0) {
                    download.downloadedBytes.toFloat() / download.totalBytes
                } else {
                    -1f
                }
                if (pct >= 0f) {
                    CircularProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.size(22.dp),
                        color = AccentRed,
                        strokeWidth = 2.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = AccentRed,
                        strokeWidth = 2.dp,
                    )
                }
            }
            else -> Icon(
                Icons.Filled.Download,
                contentDescription = description,
                tint = MutedText,
            )
        }
    }
}
