package com.iptv.feature.catalog.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.iptv.core.designsystem.components.EmptyState
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.designsystem.components.LoadingState
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.feature.catalog.R

/**
 * Películas y Series: descubrimiento por carruseles con destacado, fila de
 * favoritos y una fila por categoría. "Ver todo" abre la parrilla paginada;
 * la búsqueda muestra resultados en parrilla.
 */
@Composable
fun VodCatalogScreen(
    onItemClick: (Long) -> Unit,
    onResumeItem: ((Long) -> Unit)? = null,
    viewModel: VodCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val content = viewModel.paging.collectAsLazyPagingItems()
    val kind = viewModel.kind
    var showLanguagePicker by remember { mutableStateOf(false) }

    // "Ver todo" es un modo interno de la pantalla: Atrás vuelve a los
    // carruseles en lugar de salir de la sección.
    BackHandler(enabled = state.grid != null) { viewModel.closeGrid() }

    Column(Modifier.fillMaxSize().background(CinemaBlack)) {
        CatalogHeader(
            title = stringResource(titleFor(kind)),
            subtitle = stringResource(R.string.catalog_browse_subtitle),
            query = state.query,
            searchHint = searchHintFor(kind),
            onQueryChange = viewModel::onQueryChange,
        )
        Box(Modifier.weight(1f)) {
            when {
                state.query.isNotBlank() -> SearchResults(content, kind, onItemClick, viewModel::toggleFavorite)
                state.grid != null -> GridMode(
                    grid = state.grid!!,
                    content = content,
                    onBack = viewModel::closeGrid,
                    onItemClick = onItemClick,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
                else -> BrowseMode(
                    rows = rows,
                    kind = kind,
                    languages = languages,
                    selectedLanguage = state.language,
                    onLanguageClick = { showLanguagePicker = true },
                    onRowClick = viewModel::openGrid,
                    onItemClick = onItemClick,
                    onResumeItem = onResumeItem,
                    onToggleFavorite = viewModel::toggleFavorite,
                )
            }
        }
        if (showLanguagePicker) {
            LanguagePickerDialog(
                languages = languages,
                selected = state.language,
                onSelect = viewModel::selectLanguage,
                onDismiss = { showLanguagePicker = false },
            )
        }
    }
}

@Composable
private fun BrowseMode(
    rows: List<VodCatalogViewModel.BrowseRow>,
    kind: ContentKind,
    languages: List<Pair<String, Int>>,
    selectedLanguage: String?,
    onLanguageClick: () -> Unit,
    onRowClick: (VodCatalogViewModel.BrowseRow) -> Unit,
    onItemClick: (Long) -> Unit,
    onResumeItem: ((Long) -> Unit)?,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    if (rows.isEmpty() && languages.isEmpty()) {
        EmptyState(stringResource(emptyMessageFor(kind)))
        return
    }
    val featured = rows.firstNotNullOfOrNull { it.items.firstOrNull()?.channel }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (languages.isNotEmpty()) {
            item(key = "language") {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    LanguageChip(selected = selectedLanguage, onClick = onLanguageClick)
                }
            }
        }
        if (featured != null) {
            item(key = "featured") {
                FeaturedHero(featured, onItemClick, onToggleFavorite)
            }
        }
        items(rows, key = { rowKey(it) }) { row ->
            // "Seguir viendo" reanuda directo; el resto abre la ficha.
            val itemClick = if (row is VodCatalogViewModel.BrowseRow.ContinueWatching) {
                onResumeItem ?: onItemClick
            } else {
                onItemClick
            }
            CatalogRow(row, onRowClick, itemClick, onToggleFavorite)
        }
    }
}

@Composable
private fun FeaturedHero(
    item: ChannelEntity,
    onClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(GraphiteLight)
            .clickable { onClick(item.id) },
    ) {
        IptvAsyncImage(
            model = item.logoUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.9f)),
                ),
            ),
        )
        IconButton(
            onClick = { onToggleFavorite(item) },
            modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.catalog_cd_favorite),
                tint = if (item.isFavorite) Carmine else Color.White.copy(alpha = 0.82f),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(
                text = stringResource(R.string.catalog_featured),
                color = Carmine,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.name,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onClick(item.id) },
                colors = ButtonDefaults.buttonColors(containerColor = Carmine, contentColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.catalog_play), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CatalogRow(
    row: VodCatalogViewModel.BrowseRow,
    onRowClick: (VodCatalogViewModel.BrowseRow) -> Unit,
    onItemClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = rowTitle(row),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onRowClick(row) }) {
                Text(stringResource(R.string.catalog_see_all), color = Carmine)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Carmine,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(row.items, key = { it.channel.id }) { item ->
                PosterCard(
                    item = item.channel,
                    progress = item.progress,
                    onClick = { onItemClick(item.channel.id) },
                    onToggleFavorite = { onToggleFavorite(item.channel) },
                    modifier = Modifier.width(128.dp),
                )
            }
        }
    }
}

@Composable
private fun GridMode(
    grid: VodCatalogViewModel.Grid,
    content: LazyPagingItems<ChannelEntity>,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.catalog_all_categories),
                    tint = Color.White,
                )
            }
            Text(
                text = gridTitle(grid),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f)) {
            if (content.loadState.refresh is LoadState.Loading && content.itemCount == 0) {
                LoadingState()
            } else {
                PosterGrid(content, onItemClick, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun SearchResults(
    content: LazyPagingItems<ChannelEntity>,
    kind: ContentKind,
    onItemClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    when {
        content.loadState.refresh is LoadState.Loading && content.itemCount == 0 -> LoadingState()
        content.itemCount == 0 -> EmptyState(stringResource(R.string.catalog_empty_search))
        else -> PosterGrid(content, onItemClick, onToggleFavorite)
    }
}

@Composable
private fun rowTitle(row: VodCatalogViewModel.BrowseRow): String = when (row) {
    is VodCatalogViewModel.BrowseRow.ContinueWatching -> stringResource(R.string.catalog_continue_watching)
    is VodCatalogViewModel.BrowseRow.Favorites -> stringResource(R.string.catalog_favorites)
    is VodCatalogViewModel.BrowseRow.Category -> row.name
    is VodCatalogViewModel.BrowseRow.Uncategorized -> stringResource(R.string.catalog_more)
    is VodCatalogViewModel.BrowseRow.All -> stringResource(R.string.catalog_all)
}

@Composable
private fun gridTitle(grid: VodCatalogViewModel.Grid): String = when (grid) {
    is VodCatalogViewModel.Grid.Category -> grid.title
    is VodCatalogViewModel.Grid.Favorites -> stringResource(R.string.catalog_favorites)
    is VodCatalogViewModel.Grid.ContinueWatching -> stringResource(R.string.catalog_continue_watching)
    is VodCatalogViewModel.Grid.Uncategorized -> stringResource(R.string.catalog_more)
    is VodCatalogViewModel.Grid.All -> stringResource(R.string.catalog_all)
}

private fun rowKey(row: VodCatalogViewModel.BrowseRow): String = when (row) {
    is VodCatalogViewModel.BrowseRow.ContinueWatching -> "continue-watching"
    is VodCatalogViewModel.BrowseRow.Favorites -> "favorites"
    is VodCatalogViewModel.BrowseRow.Category -> "category-${row.id}"
    is VodCatalogViewModel.BrowseRow.Uncategorized -> "uncategorized"
    is VodCatalogViewModel.BrowseRow.All -> "all"
}

private fun titleFor(kind: ContentKind): Int = when (kind) {
    ContentKind.TV -> R.string.catalog_title_tv
    ContentKind.MOVIES -> R.string.catalog_title_movies
    ContentKind.SERIES -> R.string.catalog_title_series
}
