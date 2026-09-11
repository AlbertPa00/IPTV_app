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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.iptv.core.storage.dao.GuideRow
import com.iptv.feature.epg.R
import com.iptv.feature.epg.data.EpgSyncState
import java.text.DateFormat
import java.util.Date

private val GuideBackground = Color(0xFF080B12)
private val GuideCard = Color(0xFF151A25)
private val GuideAccent = Color(0xFF62D6FF)

@Composable
fun GuideScreen(
    onChannelClick: (Long) -> Unit,
    viewModel: GuideViewModel = hiltViewModel(),
) {
    val rows by viewModel.guide.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val syncing = sync is EpgSyncState.Progress

    Column(Modifier.fillMaxSize().background(GuideBackground)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.epg_title), color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.epg_subtitle), color = Color(0xFF9EA8BA), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = viewModel::sync, enabled = !syncing) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (sync is EpgSyncState.Failed) R.string.epg_retry else R.string.epg_sync))
            }
        }
        when {
            syncing -> SyncProgress((sync as EpgSyncState.Progress).imported)
            sync is EpgSyncState.Failed -> StatusMessage(errorText((sync as EpgSyncState.Failed).reason), viewModel::sync)
            rows.isEmpty() -> StatusMessage(stringResource(R.string.epg_empty), viewModel::sync)
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.channelId }) { row -> GuideChannel(row, onChannelClick) }
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
            Text(message, color = Color(0xFFB9C2D2), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = retry) { Text(stringResource(R.string.epg_sync)) }
        }
    }
}

@Composable
private fun GuideChannel(row: GuideRow, onClick: (Long) -> Unit) {
    val now = System.currentTimeMillis()
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
                AsyncImage(row.channelLogoUrl, contentDescription = null, modifier = Modifier.size(52.dp))
            } else {
                Icon(Icons.Filled.Tv, contentDescription = null, tint = Color(0xFF8A94A8), modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(row.channelName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.currentTitle ?: stringResource(R.string.epg_no_current), color = GuideAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (currentStart != null && currentEnd != null) {
                    Text("${formatTime(currentStart)} – ${formatTime(currentEnd)}", color = Color(0xFF9EA8BA), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(5.dp))
                    LinearProgressIndicator(progress = { progress }, color = GuideAccent, trackColor = Color(0xFF303747), modifier = Modifier.fillMaxWidth().height(3.dp))
                }
                if (nextTitle != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.epg_next, row.nextStartUtc?.let(::formatTime).orEmpty(), nextTitle), color = Color(0xFFB9C2D2), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
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
