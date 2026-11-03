package com.iptv.feature.player.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.designsystem.tv.tvFocusable
import com.iptv.core.designsystem.tv.tvSafeArea
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.feature.player.R

private const val SEEK_STEP_MS = 10_000L

/**
 * Barra de transporte de TV: botones enfocables navegables con las
 * flechas del mando. Al abrirse el foco cae en play/pausa. Los gestos de
 * la versión táctil (swipe de volumen, doble-tap) no existen aquí — el
 * volumen lo gestiona el propio televisor vía CEC.
 */
@Composable
fun TvControlsOverlay(
    channel: ChannelEntity?,
    isLive: Boolean,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenRail: () -> Unit,
    onOpenTracks: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPauseFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { playPauseFocus.requestFocus() }

    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.tvSafeArea().padding(bottom = 10.dp),
        ) {
            // Identidad del canal en reproducción.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel?.logoUrl != null) {
                    IptvAsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channel?.name.orEmpty(),
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

            Spacer(Modifier.size(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvControlButton(
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.player_pause else R.string.player_play,
                    ),
                    onClick = onTogglePlayPause,
                    modifier = Modifier.focusRequester(playPauseFocus),
                )
                if (!isLive) {
                    TvControlButton(
                        icon = Icons.Filled.Replay10,
                        contentDescription = stringResource(R.string.player_seek_back),
                        onClick = { onSeek(-SEEK_STEP_MS) },
                    )
                    TvControlButton(
                        icon = Icons.Filled.Forward10,
                        contentDescription = stringResource(R.string.player_seek_forward),
                        onClick = { onSeek(SEEK_STEP_MS) },
                    )
                }
                TvControlButton(
                    icon = Icons.Filled.Subtitles,
                    contentDescription = stringResource(R.string.player_tracks),
                    onClick = onOpenTracks,
                )
                TvControlButton(
                    icon = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.player_channel_list),
                    onClick = onOpenRail,
                )
                TvControlButton(
                    icon = Icons.Filled.Star,
                    contentDescription = stringResource(
                        if (channel?.isFavorite == true) {
                            R.string.player_favorite_remove
                        } else {
                            R.string.player_favorite_add
                        },
                    ),
                    tint = if (channel?.isFavorite == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White
                    },
                    onClick = onToggleFavorite,
                )
            }
        }
    }
}

@Composable
private fun TvControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .tvFocusable(shape = CircleShape, onClick = onClick)
            .padding(14.dp),
    )
}
