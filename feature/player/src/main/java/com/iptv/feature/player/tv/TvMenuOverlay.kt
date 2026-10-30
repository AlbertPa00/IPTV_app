package com.iptv.feature.player.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iptv.core.designsystem.tv.tvFocusable
import com.iptv.feature.player.R

private val MENU_WIDTH = 340.dp

/**
 * Menú contextual de la tecla MENU: acciones sobre la reproducción en
 * curso que no merecen botón propio en la barra de transporte. Las
 * flechas navegan las opciones, CENTER ejecuta y MENU/BACK cierran.
 */
@Composable
fun TvMenuOverlay(
    channelName: String,
    isFavorite: Boolean,
    onOpenRail: () -> Unit,
    onOpenGuide: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Surface(
        color = Color.Black.copy(alpha = 0.92f),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        modifier = modifier.fillMaxHeight().width(MENU_WIDTH),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                text = channelName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            TvMenuItem(
                icon = Icons.AutoMirrored.Filled.List,
                label = stringResource(R.string.player_channel_list),
                onClick = onOpenRail,
                focusRequester = firstFocus,
            )
            TvMenuItem(
                icon = Icons.Filled.DateRange,
                label = stringResource(R.string.player_guide_title),
                onClick = onOpenGuide,
            )
            TvMenuItem(
                icon = Icons.Filled.Star,
                label = stringResource(
                    if (isFavorite) {
                        R.string.player_favorite_remove
                    } else {
                        R.string.player_favorite_add
                    },
                ),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                onClick = onToggleFavorite,
            )
            TvMenuItem(
                icon = Icons.Filled.Subtitles,
                label = stringResource(R.string.player_tracks),
                onClick = onOpenTracks,
            )
            TvMenuItem(
                icon = Icons.Filled.Bedtime,
                label = stringResource(R.string.player_sleep_timer),
                onClick = onOpenSleepTimer,
            )
            TvMenuItem(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                label = stringResource(R.string.player_exit),
                onClick = onExit,
            )
        }
    }
}

@Composable
private fun TvMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    tint: Color = Color.White,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
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
            .padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(4.dp))
    }
}
