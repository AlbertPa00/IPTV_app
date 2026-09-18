package com.iptv.feature.catalog.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.iptv.core.designsystem.components.EmptyState
import com.iptv.core.designsystem.components.LoadingState
import com.iptv.core.storage.dao.GuideRow
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.feature.catalog.R
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

/**
 * Sección TV en directo: lista de zapping con el programa en emisión y el
 * siguiente por canal. La EPG se resuelve una vez por minuto en el ViewModel
 * y se consulta por `channelId` al componer cada fila.
 */
@Composable
fun LiveTvScreen(
    onChannelClick: (Long) -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val guideByChannel by viewModel.guideByChannel.collectAsStateWithLifecycle()
    val showQuickHint by viewModel.showQuickHint.collectAsStateWithLifecycle()
    val content = viewModel.channels.collectAsLazyPagingItems()
    var showLanguagePicker by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    Column(Modifier.fillMaxSize().background(CinemaBlack)) {
        CatalogHeader(
            title = stringResource(R.string.catalog_title_tv),
            subtitle = stringResource(R.string.catalog_live_subtitle),
            query = filters.query,
            searchHint = R.string.catalog_search_tv,
            onQueryChange = viewModel::onQueryChange,
        )
        if (filters.query.isBlank()) {
            LiveFilterRail(
                categories = categories.filter {
                    filters.language == null || it.language == filters.language
                },
                languages = languages,
                selectedLanguage = filters.language,
                favoritesOnly = filters.favoritesOnly,
                selectedCategoryId = filters.categoryId,
                onSelectFavorites = viewModel::selectFavorites,
                onSelectAll = viewModel::selectAll,
                onSelectCategory = viewModel::selectCategory,
                onLanguageClick = { showLanguagePicker = true },
            )
        }
        if (showLanguagePicker) {
            LanguagePickerDialog(
                languages = languages,
                selected = filters.language,
                onSelect = viewModel::selectLanguage,
                onDismiss = { showLanguagePicker = false },
            )
        }
        Box(Modifier.weight(1f)) {
            when {
                content.loadState.refresh is LoadState.Loading && content.itemCount == 0 ->
                    LoadingState()
                content.itemCount == 0 -> EmptyState(liveEmptyMessage(filters))
                else -> ChannelList(content, guideByChannel, now, onChannelClick, viewModel::toggleFavorite)
            }
            if (showQuickHint && content.itemCount > 0) {
                QuickHintBanner(
                    text = stringResource(R.string.catalog_quick_hint),
                    onClose = viewModel::markQuickHintSeen,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
                LaunchedEffect(Unit) {
                    delay(8_000)
                    viewModel.markQuickHintSeen()
                }
            }
        }
    }
}

@Composable
private fun LiveFilterRail(
    categories: List<CategoryEntity>,
    languages: List<Pair<String, Int>>,
    selectedLanguage: String?,
    favoritesOnly: Boolean,
    selectedCategoryId: Long?,
    onSelectFavorites: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectCategory: (Long) -> Unit,
    onLanguageClick: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (languages.isNotEmpty()) {
            item(key = "language") {
                LanguageChip(selected = selectedLanguage, onClick = onLanguageClick)
            }
        }
        item(key = "favorites") {
            CategoryChip(
                label = stringResource(R.string.catalog_favorites),
                selected = favoritesOnly,
                onClick = onSelectFavorites,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        item(key = "all") {
            CategoryChip(
                label = stringResource(R.string.catalog_all),
                selected = !favoritesOnly && selectedCategoryId == null && selectedLanguage == null,
                onClick = onSelectAll,
            )
        }
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                label = category.name,
                selected = selectedCategoryId == category.id,
                onClick = { onSelectCategory(category.id) },
            )
        }
    }
}

@Composable
private fun ChannelList(
    content: LazyPagingItems<ChannelEntity>,
    guideByChannel: Map<Long, GuideRow>,
    now: Long,
    onClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = content.itemCount, key = content.itemKey { it.id }) { index ->
            content[index]?.let { channel ->
                LiveChannelRow(
                    channel = channel,
                    guide = guideByChannel[channel.id],
                    now = now,
                    onClick = { onClick(channel.id) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                )
            }
        }
        item { PagingAppendState(content.loadState.append) { content.retry() } }
    }
}

@Composable
private fun LiveChannelRow(
    channel: ChannelEntity,
    guide: GuideRow?,
    now: Long,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Graphite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel.logoUrl)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Carmine))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = channel.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                NowNext(guide, channel.groupTitle, now)
            }
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite()
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = stringResource(
                        if (channel.isFavorite) {
                            R.string.catalog_favorite_remove
                        } else {
                            R.string.catalog_favorite_add
                        },
                    ),
                    tint = if (channel.isFavorite) Carmine else MutedText,
                )
            }
        }
    }
}

@Composable
private fun NowNext(guide: GuideRow?, groupTitle: String?, now: Long) {
    val start = guide?.currentStartUtc
    val end = guide?.currentEndUtc
    val currentTitle = guide?.currentTitle
    when {
        currentTitle != null -> {
            Column {
                Text(
                    text = currentTitle,
                    color = Carmine,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (start != null && end != null && end > start) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = {
                            ((now - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
                        },
                        color = Carmine,
                        trackColor = GraphiteLight,
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = "${formatTime(start)} – ${formatTime(end)}",
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                guide.nextTitle?.let {
                    Text(
                        text = stringResource(
                            R.string.catalog_next_programme,
                            guide.nextStartUtc?.let(::formatTime).orEmpty(),
                            it,
                        ),
                        color = MutedText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        else -> Text(
            text = groupTitle?.substringAfter(':')
                ?.takeUnless { it.isBlank() || it.all(Char::isDigit) }
                ?: stringResource(R.string.catalog_live_now),
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun liveEmptyMessage(filters: LiveTvViewModel.Filters): String = stringResource(
    when {
        filters.query.isNotBlank() -> R.string.catalog_empty_search
        filters.favoritesOnly -> R.string.catalog_empty_favorites
        else -> R.string.catalog_empty_tv
    },
)

private fun formatTime(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

/**
 * Pista de primer uso: banner inferior auto-ocultable (también por la X y por
 * el temporizador del llamante). Se muestra una única vez por instalación.
 */
@Composable
private fun QuickHintBanner(
    text: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.catalog_close),
                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}
