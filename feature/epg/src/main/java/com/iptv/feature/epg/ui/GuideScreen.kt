package com.iptv.feature.epg.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.storage.dao.GuideRow
import com.iptv.core.storage.dao.GuideSort
import com.iptv.feature.epg.R
import com.iptv.feature.epg.data.EpgSyncState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private val GuideBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val GuideCard: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val GuideAccent: Color @Composable get() = MaterialTheme.colorScheme.primary
private val GuideMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val GuideTrack: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer

@Composable
fun GuideScreen(
    onChannelClick: (Long) -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val rows by viewModel.guide.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val syncing = sync is EpgSyncState.Progress
    // Tic cada 30 s para que las barras de "ahora" avancen entre recomposiciones.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    Column(Modifier.fillMaxSize().background(GuideBackground)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.epg_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.epg_subtitle), color = GuideMuted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = viewModel::sync, enabled = !syncing) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (sync is EpgSyncState.Failed) R.string.epg_retry else R.string.epg_sync))
            }
        }
        GuideFilterRail(
            filter = filter,
            onToggleFavorites = viewModel::toggleFavorites,
            onSelectSort = viewModel::selectSort,
        )
        when {
            syncing -> SyncProgress((sync as EpgSyncState.Progress).imported)
            sync is EpgSyncState.Failed -> StatusMessage(errorText((sync as EpgSyncState.Failed).reason), viewModel::sync)
            rows.isEmpty() -> StatusMessage(
                stringResource(if (filter.favoritesOnly) R.string.epg_empty_favorites else R.string.epg_empty),
                viewModel::sync,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.channelId }) { row -> GuideChannel(row, onChannelClick, now) }
            }
        }
    }
}

@Composable
private fun GuideFilterRail(
    filter: GuideViewModel.GuideFilter,
    onToggleFavorites: () -> Unit,
    onSelectSort: (GuideSort) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter.favoritesOnly,
            onClick = onToggleFavorites,
            label = { Text(stringResource(R.string.epg_favorites)) },
            leadingIcon = {
                Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = GuideCard,
                labelColor = GuideMuted,
                selectedContainerColor = GuideAccent,
                selectedLabelColor = Color.White,
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = filter.favoritesOnly,
                borderColor = GuideTrack,
                selectedBorderColor = GuideAccent,
            ),
        )
        Box {
            FilterChip(
                selected = filter.sort != GuideSort.PROVIDER,
                onClick = { sortMenuOpen = true },
                label = { Text(stringResource(sortLabel(filter.sort))) },
                trailingIcon = {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = GuideCard,
                    labelColor = GuideMuted,
                    selectedContainerColor = GuideAccent,
                    selectedLabelColor = Color.White,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = filter.sort != GuideSort.PROVIDER,
                    borderColor = GuideTrack,
                    selectedBorderColor = GuideAccent,
                ),
            )
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false },
                containerColor = GuideCard,
            ) {
                GuideSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sortLabel(option)), color = Color.White) },
                        onClick = {
                            onSelectSort(option)
                            sortMenuOpen = false
                        },
                        trailingIcon = {
                            if (option == filter.sort) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = GuideAccent)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncProgress(count: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = GuideAccent)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.epg_syncing, count), color = Color.White)
        }
    }
}

@Composable
private fun StatusMessage(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Tv, contentDescription = null, tint = GuideAccent, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(message, color = GuideMuted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = retry) { Text(stringResource(R.string.epg_sync)) }
        }
    }
}

@Composable
private fun GuideChannel(row: GuideRow, onClick: (Long) -> Unit, now: Long) {
    val currentStart = row.currentStartUtc
    val currentEnd = row.currentEndUtc
    val nextTitle = row.nextTitle
    val progress = if (currentStart != null && currentEnd != null) {
        ((now - currentStart).toFloat() / (currentEnd - currentStart).toFloat()).coerceIn(0f, 1f)
    } else 0f
    Card(
        colors = CardDefaults.cardColors(containerColor = GuideCard),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick(row.channelId) },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.channelLogoUrl != null) {
                IptvAsyncImage(row.channelLogoUrl, contentDescription = null, modifier = Modifier.size(52.dp))
            } else {
                Icon(Icons.Filled.Tv, contentDescription = null, tint = GuideMuted, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.channelName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.currentTitle ?: stringResource(R.string.epg_no_current), color = GuideAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (currentStart != null && currentEnd != null) {
                    Text("${formatTime(currentStart)} – ${formatTime(currentEnd)}", color = GuideMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(progress = { progress }, color = GuideAccent, trackColor = GuideTrack, modifier = Modifier.fillMaxWidth().height(3.dp))
                }
                if (nextTitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.epg_next, row.nextStartUtc?.let(::formatTime).orEmpty(), nextTitle), color = GuideMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun sortLabel(sort: GuideSort): Int = when (sort) {
    GuideSort.PROVIDER -> R.string.epg_sort_provider
    GuideSort.NAME -> R.string.epg_sort_name
    GuideSort.FAVORITES -> R.string.epg_sort_favorites
}

@Composable
private fun errorText(reason: EpgSyncState.Reason): String = stringResource(
    when (reason) {
        EpgSyncState.Reason.NO_SOURCE -> R.string.epg_error_no_source
        EpgSyncState.Reason.NO_URL -> R.string.epg_error_no_url
        EpgSyncState.Reason.NETWORK -> R.string.epg_error_network
        EpgSyncState.Reason.INVALID_XML -> R.string.epg_error_xml
        EpgSyncState.Reason.EMPTY -> R.string.epg_error_empty
    }
)

private fun formatTime(timestamp: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
