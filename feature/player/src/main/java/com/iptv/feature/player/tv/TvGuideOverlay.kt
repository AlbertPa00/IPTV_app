package com.iptv.feature.player.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.designsystem.tv.tvFocusable
import com.iptv.core.storage.dao.GuideRow
import com.iptv.feature.player.R
import java.text.DateFormat
import java.util.Date

private val GUIDE_WIDTH = 560.dp

/** HH:mm en la zona horaria del dispositivo (compartido por los overlays de TV). */
internal fun formatTvTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

/**
 * Panel EPG "ahora y a continuación": todos los canales de la fuente con
 * su programa en curso (y el siguiente). Las flechas navegan el foco por
 * las filas, CENTER sintoniza el canal y GUIDE/BACK cierran. La consulta
 * ya viene resuelta de Room ([GuideRow] de ProgrammeDao.guideSnapshot).
 */
@Composable
fun TvGuideOverlay(
    rows: List<GuideRow>,
    currentChannelId: Long?,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = rows.indexOfFirst { it.channelId == currentChannelId }
    val currentFocusRequester = remember { FocusRequester() }

    // Mismo patrón que el rail: el foco inicial cae en el canal en curso.
    var initialFocused by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex) {
        if (initialFocused || currentIndex < 0) return@LaunchedEffect
        listState.scrollToItem(currentIndex)
        repeat(3) {
            if (!initialFocused) {
                withFrameNanos { }
                initialFocused = runCatching {
                    currentFocusRequester.requestFocus()
                    true
                }.getOrDefault(false)
            }
        }
    }

    Surface(
        color = Color.Black.copy(alpha = 0.92f),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = modifier.fillMaxHeight().width(GUIDE_WIDTH),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.player_guide_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            if (rows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.player_guide_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.channelId },
                    ) { _, row ->
                        GuideRowItem(
                            row = row,
                            focusRequester = if (row.channelId == currentChannelId) {
                                currentFocusRequester
                            } else {
                                null
                            },
                            onClick = { onPick(row.channelId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRowItem(
    row: GuideRow,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
            .tvFocusable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.channelLogoUrl != null) {
                IptvAsyncImage(
                    model = row.channelLogoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = row.channelName,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (row.currentTitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    row.currentStartUtc?.let {
                        append(formatTvTime(it))
                        row.currentEndUtc?.let { end -> append(" – ").append(formatTvTime(end)) }
                        append(" · ")
                    }
                    append(row.currentTitle)
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val start = row.currentStartUtc
            val end = row.currentEndUtc
            if (start != null && end != null && end > start) {
                val progress = (
                    (System.currentTimeMillis() - start).toFloat() / (end - start).toFloat()
                    ).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        if (row.nextTitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = buildString {
                    append("› ")
                    row.nextStartUtc?.let { append(formatTvTime(it)).append(" · ") }
                    append(row.nextTitle)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
