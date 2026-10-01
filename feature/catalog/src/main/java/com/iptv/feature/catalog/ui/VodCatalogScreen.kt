package com.iptv.feature.catalog.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.iptv.core.designsystem.components.ConfirmDeleteDialog
import com.iptv.core.designsystem.components.EmptyState
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.designsystem.components.LoadingState
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.feature.catalog.R

/**
 * Películas y Series: descubrimiento por carruseles con destacado, fila de
 * favoritos y una fila por categoría. "Ver todo" abre la parrilla paginada;
 * la búsqueda muestra resultados en parrilla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodCatalogScreen(
    kind: ContentKind,
    onItemClick: (Long) -> Unit,
    onResumeItem: ((Long) -> Unit)? = null,
    active: Boolean = true,
    viewModel: VodCatalogViewModel = hiltViewModel(key = kind.name),
) {
    // La VM de cada sección es una instancia keyed dentro del destino "home":
    // attach() fija la sección antes de que los flows se coleccionen.
    viewModel.attach(kind)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val browseLoaded by viewModel.browseLoaded.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val content = viewModel.paging.collectAsLazyPagingItems()
    var showLanguagePicker by remember { mutableStateOf(false) }
    // Borrar una descarga es destructivo: todos los puntos (menú del póster,
    // papelera de la lista, parrilla) piden confirmación antes de llamar al VM.
    var pendingDelete by remember { mutableStateOf<DownloadEntity?>(null) }

    // Android 13+: sin POST_NOTIFICATIONS la descarga funciona igual pero el
    // progreso no es visible. Se pide una vez, cuando hay algo descargándose.
    val context = LocalContext.current
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(downloads.isNotEmpty()) {
        if (downloads.isNotEmpty() &&
            Build.VERSION.SDK_INT >= 33 &&
            !viewModel.wasMediaPermsAsked() &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.markMediaPermsAsked()
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // La parrilla/búsqueda recargan vía LazyPagingItems: el indicador se
    // sostiene mientras dure ese refresh además del suelo del ViewModel.
    var pulledRefresh by remember { mutableStateOf(false) }
    LaunchedEffect(content.loadState.refresh) {
        if (content.loadState.refresh !is LoadState.Loading) pulledRefresh = false
    }

    // "Ver todo" es un modo interno de la pantalla: Atrás vuelve a los
    // carruseles en lugar de salir de la sección. Una pestaña oculta (keep
    // alive) no debe capturar el gesto.
    BackHandler(enabled = active && state.grid != null) { viewModel.closeGrid() }

    Column(Modifier.fillMaxSize().background(CinemaBlack)) {
        CatalogHeader(
            title = stringResource(titleFor(kind)),
            subtitle = stringResource(R.string.catalog_browse_subtitle),
            query = state.query,
            searchHint = searchHintFor(kind),
            onQueryChange = viewModel::onQueryChange,
        )
        PullToRefreshBox(
            isRefreshing = isRefreshing ||
                (pulledRefresh && content.loadState.refresh is LoadState.Loading),
            onRefresh = {
                pulledRefresh = true
                viewModel.refresh()
                content.refresh()
            },
            modifier = Modifier.weight(1f),
        ) {
            when {
                state.query.isNotBlank() -> SearchResults(
                    content = content,
                    kind = kind,
                    onItemClick = onItemClick,
                    onToggleFavorite = viewModel::toggleFavorite,
                    downloads = downloads,
                    onPlay = onResumeItem,
                    onDownload = viewModel::enqueueDownload,
                    onCancelDownload = { viewModel.cancelDownload(it.id) },
                    onDeleteDownload = { pendingDelete = it },
                )
                state.grid != null -> GridMode(
                    grid = state.grid!!,
                    content = content,
                    kind = kind,
                    onBack = viewModel::closeGrid,
                    onItemClick = onItemClick,
                    onResumeItem = onResumeItem,
                    onToggleFavorite = viewModel::toggleFavorite,
                    downloads = downloads,
                    onDownload = viewModel::enqueueDownload,
                    onCancelDownload = { viewModel.cancelDownload(it.id) },
                    onDeleteDownload = { pendingDelete = it },
                )
                else -> BrowseMode(
                    rows = rows,
                    loaded = browseLoaded,
                    kind = kind,
                    languages = languages,
                    selectedLanguage = state.language,
                    onLanguageClick = { showLanguagePicker = true },
                    onRowClick = viewModel::openGrid,
                    onItemClick = onItemClick,
                    onResumeItem = onResumeItem,
                    onToggleFavorite = viewModel::toggleFavorite,
                    downloads = downloads,
                    onDownload = viewModel::enqueueDownload,
                    onCancelDownload = { viewModel.cancelDownload(it.id) },
                    onDeleteDownload = { pendingDelete = it },
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
        pendingDelete?.let { download ->
            ConfirmDeleteDialog(
                title = stringResource(R.string.catalog_download_delete_title),
                text = stringResource(
                    R.string.catalog_download_delete_confirm, download.title,
                ),
                onConfirm = {
                    viewModel.deleteDownload(download.id)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

@Composable
private fun BrowseMode(
    rows: List<VodCatalogViewModel.BrowseRow>,
    loaded: Boolean,
    kind: ContentKind,
    languages: List<Pair<String, Int>>,
    selectedLanguage: String?,
    onLanguageClick: () -> Unit,
    onRowClick: (VodCatalogViewModel.BrowseRow) -> Unit,
    onItemClick: (Long) -> Unit,
    onResumeItem: ((Long) -> Unit)?,
    onToggleFavorite: (ChannelEntity) -> Unit,
    downloads: Map<Long, DownloadEntity>,
    onDownload: (ChannelEntity) -> Unit,
    onCancelDownload: (ChannelEntity) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
) {
    // Todo dentro del LazyColumn: es el hijo scrollable que necesita el
    // gesto de pull-to-refresh, también en los estados vacío y de carga.
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
        when {
            !loaded -> item(key = "skeleton") { BrowseSkeleton() }
            // Aunque haya chip de idiomas, sin filas se muestra el estado
            // vacío: ocupa toda la lista y mantiene vivo el pull-to-refresh.
            rows.isEmpty() -> item(key = "empty") {
                EmptyState(
                    text = stringResource(emptyMessageFor(kind)),
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
            else -> {
                val featured = rows.firstNotNullOfOrNull { it.items.firstOrNull()?.channel }
                if (featured != null) {
                    item(key = "featured") {
                        FeaturedHero(featured, onItemClick, onToggleFavorite)
                    }
                }
                items(rows, key = { rowKey(it) }) { row ->
                    // "Seguir viendo" y "Descargados" reproducen directo (los
                    // episodios descargados no tienen ficha); el resto la abre.
                    val itemClick = when (row) {
                        is VodCatalogViewModel.BrowseRow.ContinueWatching,
                        is VodCatalogViewModel.BrowseRow.Downloads,
                        -> onResumeItem ?: onItemClick
                        else -> onItemClick
                    }
                    // El menú contextual aplica a películas y a items ya
                    // descargados; un póster de serie no es reproducible ni
                    // descargable (sus episodios se gestionan en su ficha).
                    val menuEnabled = kind == ContentKind.MOVIES ||
                        row is VodCatalogViewModel.BrowseRow.Downloads
                    CatalogRow(
                        row = row,
                        onRowClick = onRowClick,
                        onItemClick = itemClick,
                        onToggleFavorite = onToggleFavorite,
                        downloads = downloads,
                        onPlay = if (menuEnabled) onResumeItem else null,
                        onDownload = if (menuEnabled) onDownload else null,
                        onCancelDownload = onCancelDownload,
                        onDeleteDownload = onDeleteDownload,
                    )
                }
            }
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
    downloads: Map<Long, DownloadEntity>,
    onPlay: ((Long) -> Unit)?,
    onDownload: ((ChannelEntity) -> Unit)?,
    onCancelDownload: (ChannelEntity) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
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
                val download = downloads[item.channel.id]
                if (onPlay != null) {
                    PosterCardWithMenu(
                        item = item.channel,
                        download = download,
                        progress = item.progress,
                        onClick = { onItemClick(item.channel.id) },
                        onPlay = { onPlay(item.channel.id) },
                        onToggleFavorite = { onToggleFavorite(item.channel) },
                        onDownload = onDownload?.let { dl -> { dl(item.channel) } },
                        onCancelDownload = { onCancelDownload(item.channel) },
                        onDeleteDownload = { download?.let(onDeleteDownload) },
                        modifier = Modifier.width(128.dp),
                    )
                } else {
                    PosterCard(
                        item = item.channel,
                        progress = item.progress,
                        download = download,
                        onClick = { onItemClick(item.channel.id) },
                        onToggleFavorite = { onToggleFavorite(item.channel) },
                        modifier = Modifier.width(128.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GridMode(
    grid: VodCatalogViewModel.Grid,
    content: LazyPagingItems<ChannelEntity>,
    kind: ContentKind,
    onBack: () -> Unit,
    onItemClick: (Long) -> Unit,
    onResumeItem: ((Long) -> Unit)?,
    onToggleFavorite: (ChannelEntity) -> Unit,
    downloads: Map<Long, DownloadEntity>,
    onDownload: (ChannelEntity) -> Unit,
    onCancelDownload: (ChannelEntity) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
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
                // Los descargados se listan con su progreso y reproducen
                // directo (un episodio no tiene ficha); el menú de descarga
                // sólo aplica a películas en el resto de parrillas.
                val isDownloads = grid is VodCatalogViewModel.Grid.Downloads
                if (isDownloads) {
                    DownloadsList(
                        content = content,
                        downloads = downloads,
                        onClick = onResumeItem ?: onItemClick,
                        onDownload = onDownload,
                        onCancelDownload = onCancelDownload,
                        onDeleteDownload = onDeleteDownload,
                    )
                } else {
                    val withMenu = kind == ContentKind.MOVIES
                    PosterGrid(
                        content = content,
                        onClick = onItemClick,
                        onToggleFavorite = onToggleFavorite,
                        downloads = downloads,
                        onPlay = if (withMenu) onResumeItem?.let { fn -> { fn(it.id) } } else null,
                        onDownload = if (withMenu) onDownload else null,
                        onCancelDownload = onCancelDownload,
                        onDeleteDownload = onDeleteDownload,
                    )
                }
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
    downloads: Map<Long, DownloadEntity>,
    onPlay: ((Long) -> Unit)?,
    onDownload: (ChannelEntity) -> Unit,
    onCancelDownload: (ChannelEntity) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
) {
    when {
        content.loadState.refresh is LoadState.Loading && content.itemCount == 0 -> LoadingState()
        content.itemCount == 0 -> LazyColumn(Modifier.fillMaxSize()) {
            // Scrollable para que el pull-to-refresh funcione también aquí.
            item {
                EmptyState(
                    text = stringResource(R.string.catalog_empty_search),
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        }
        else -> PosterGrid(
            content = content,
            onClick = onItemClick,
            onToggleFavorite = onToggleFavorite,
            downloads = downloads,
            onPlay = if (kind == ContentKind.MOVIES) {
                onPlay?.let { fn -> { fn(it.id) } }
            } else {
                null
            },
            onDownload = if (kind == ContentKind.MOVIES) onDownload else null,
            onCancelDownload = onCancelDownload,
            onDeleteDownload = onDeleteDownload,
        )
    }
}

@Composable
private fun rowTitle(row: VodCatalogViewModel.BrowseRow): String = when (row) {
    is VodCatalogViewModel.BrowseRow.ContinueWatching -> stringResource(R.string.catalog_continue_watching)
    is VodCatalogViewModel.BrowseRow.Favorites -> stringResource(R.string.catalog_favorites)
    is VodCatalogViewModel.BrowseRow.Downloads -> stringResource(R.string.catalog_downloads_row)
    is VodCatalogViewModel.BrowseRow.Category -> row.name
    is VodCatalogViewModel.BrowseRow.Uncategorized -> stringResource(R.string.catalog_more)
    is VodCatalogViewModel.BrowseRow.All -> stringResource(R.string.catalog_all)
}

@Composable
private fun gridTitle(grid: VodCatalogViewModel.Grid): String = when (grid) {
    is VodCatalogViewModel.Grid.Category -> grid.title
    is VodCatalogViewModel.Grid.Favorites -> stringResource(R.string.catalog_favorites)
    is VodCatalogViewModel.Grid.Downloads -> stringResource(R.string.catalog_downloads_row)
    is VodCatalogViewModel.Grid.ContinueWatching -> stringResource(R.string.catalog_continue_watching)
    is VodCatalogViewModel.Grid.Uncategorized -> stringResource(R.string.catalog_more)
    is VodCatalogViewModel.Grid.All -> stringResource(R.string.catalog_all)
}

private fun rowKey(row: VodCatalogViewModel.BrowseRow): String = when (row) {
    is VodCatalogViewModel.BrowseRow.ContinueWatching -> "continue-watching"
    is VodCatalogViewModel.BrowseRow.Downloads -> "downloads"
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
