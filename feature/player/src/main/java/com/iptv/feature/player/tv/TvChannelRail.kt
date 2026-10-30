package com.iptv.feature.player.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.feature.player.R
import com.iptv.feature.player.ui.PlayerViewModel

private val RAIL_WIDTH = 380.dp

/**
 * Panel lateral de canales para zapear con el mando. Sobre la lista hay
 * una fila de filtros (Todos / Favoritos / categorías): seleccionar uno
 * recarga la lista mostrada. El foco inicial cae en el canal en curso;
 * DPAD_UP/DOWN navegan, CENTER reproduce, LEFT/BACK cierran.
 */
@Composable
fun TvChannelRail(
    channels: List<ChannelEntity>,
    categories: List<CategoryEntity>,
    filter: PlayerViewModel.RailFilter,
    currentChannelId: Long?,
    onFilterPick: (PlayerViewModel.RailFilter) -> Unit,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = channels.indexOfFirst { it.id == currentChannelId }
    val currentFocusRequester = remember { FocusRequester() }

    // Al abrir: el foco cae en el canal en curso y la lista se posiciona
    // en él (los canales pueden ser miles — scroll directo al índice).
    // Sólo la primera resolución: recolocar el foco en cada cambio de
    // canal robaría el foco al usuario mientras navega la lista.
    var initialFocused by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex) {
        if (initialFocused || currentIndex < 0) return@LaunchedEffect
        listState.scrollToItem(currentIndex)
        // El item recién compuesto puede tardar un frame en adjuntar su
        // focusRequester: se reintenta un par de frames antes de rendirse.
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
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        modifier = modifier.fillMaxHeight().width(RAIL_WIDTH),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.player_channel_list),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            if (categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    item(key = "all") {
                        RailChip(
                            label = stringResource(R.string.player_all),
                            selected = filter == PlayerViewModel.RailFilter.All,
                            onClick = { onFilterPick(PlayerViewModel.RailFilter.All) },
                        )
                    }
                    item(key = "favorites") {
                        RailChip(
                            label = stringResource(R.string.player_favorites),
                            icon = Icons.Filled.Star,
                            selected = filter == PlayerViewModel.RailFilter.Favorites,
                            onClick = { onFilterPick(PlayerViewModel.RailFilter.Favorites) },
                        )
                    }
                    items(
                        items = categories,
                        key = { it.id },
                    ) { category ->
                        RailChip(
                            label = category.name,
                            selected = (filter as? PlayerViewModel.RailFilter.Category)
                                ?.id == category.id,
                            onClick = {
                                onFilterPick(PlayerViewModel.RailFilter.Category(category.id))
                            },
                        )
                    }
                }
            }
            if (channels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(
                        items = channels,
                        key = { _, channel -> channel.id },
                    ) { index, channel ->
                        RailItem(
                            number = index + 1,
                            channel = channel,
                            focused = channel.id == currentChannelId,
                            focusRequester = if (channel.id == currentChannelId) {
                                currentFocusRequester
                            } else {
                                null
                            },
                            onClick = { onPick(channel.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Chip de filtro del rail: Todos / Favoritos / una categoría. */
@Composable
private fun RailChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.12f)
                },
            )
            .tvFocusable(shape = shape, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun RailItem(
    number: Int,
    channel: ChannelEntity,
    focused: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
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
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(
            text = number.toString(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(34.dp),
        )
        IptvAsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = channel.name,
            color = if (focused) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.White
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (channel.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
