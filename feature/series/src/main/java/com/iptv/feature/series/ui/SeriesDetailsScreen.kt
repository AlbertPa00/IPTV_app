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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.iptv.core.designsystem.components.EmptyState
import com.iptv.core.designsystem.components.ErrorState
import com.iptv.core.designsystem.components.LoadingState
import com.iptv.feature.series.R
import com.iptv.feature.series.data.SeriesDetails
import com.iptv.feature.series.data.SeriesEpisode

private val NetflixRed = Color(0xFFE50914)
private val PageBackground = Color(0xFF0B0B0B)
private val CardBackground = Color(0xFF1B1B1B)

@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onPlay: (Long) -> Unit,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openPlayer.collect(onPlay) }

    Box(Modifier.fillMaxSize().background(PageBackground)) {
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load)
            state.details != null -> SeriesContent(
                details = state.details!!,
                selectedSeason = state.selectedSeason,
                preparingEpisodeId = state.preparingEpisodeId,
                onSelectSeason = viewModel::selectSeason,
                onPlayEpisode = viewModel::play,
            )
            else -> ErrorState("Error al cargar la serie", onRetry = viewModel::load)
        }
        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).background(Color.Black.copy(alpha = .65f))) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.series_back),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun SeriesContent(
    details: SeriesDetails,
    selectedSeason: Int?,
    preparingEpisodeId: String?,
    onSelectSeason: (Int) -> Unit,
    onPlayEpisode: (SeriesEpisode) -> Unit,
) {
    val selected = details.seasons.firstOrNull { it.number == selectedSeason }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            AsyncImage(
                model = details.coverUrl,
                contentDescription = stringResource(R.string.series_cover_description, details.title),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(details.title, color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                details.rating?.let {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.series_rating, it)) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                details.plot?.let {
                    Text(it, color = Color(0xFFD0D0D0), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
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
                EpisodeRow(episode, preparingEpisodeId == episode.id, onPlayEpisode)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EpisodeRow(episode: SeriesEpisode, preparing: Boolean, onPlay: (SeriesEpisode) -> Unit) {
    val playDescription = stringResource(R.string.series_play_episode, episode.title)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .background(CardBackground)
            .clickable(enabled = !preparing, role = Role.Button) { onPlay(episode) }
            .semantics { contentDescription = playDescription }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = episode.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 128.dp, height = 72.dp).background(Color.DarkGray),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                "${stringResource(R.string.series_episode_number, episode.number)} · ${episode.title}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            episode.duration?.let { Text(stringResource(R.string.series_duration, it), color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
            episode.plot?.let { Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        if (preparing) CircularProgressIndicator(Modifier.size(30.dp), color = NetflixRed)
        else Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = NetflixRed, modifier = Modifier.size(36.dp))
    }
}
