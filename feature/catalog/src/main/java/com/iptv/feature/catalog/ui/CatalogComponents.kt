package com.iptv.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.iptv.core.common.text.formatBytes
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.DownloadEntity
import com.iptv.core.storage.entity.DownloadStatus
import com.iptv.feature.catalog.R

// Alias semánticos sobre el esquema del tema: una sola paleta en toda la app.
internal val CinemaBlack: Color @Composable get() = MaterialTheme.colorScheme.background
internal val Graphite: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
internal val GraphiteLight: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
internal val Carmine: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val MutedText: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Cabecera con título, subtítulo y campo de búsqueda, común a las secciones. */
@Composable
internal fun CatalogHeader(
    title: String,
    subtitle: String,
    query: String,
    searchHint: Int,
    onQueryChange: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(CinemaBlack)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 14.dp),
    ) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = subtitle,
            color = MutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        val keyboardController = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(searchHint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.catalog_clear_search),
                            tint = MutedText,
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Graphite,
                unfocusedContainerColor = Graphite,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedLeadingIconColor = Color.White,
                unfocusedLeadingIconColor = MutedText,
                focusedPlaceholderColor = MutedText,
                unfocusedPlaceholderColor = MutedText,
                cursorColor = Carmine,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        )
    }
}

@Composable
internal fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Graphite,
            labelColor = MutedText,
            selectedContainerColor = Carmine,
            selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = GraphiteLight,
            selectedBorderColor = Carmine,
        ),
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** Póster 2:3 usado en parrillas y carruseles de películas/series. */
@Composable
internal fun PosterCard(
    item: ChannelEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    download: DownloadEntity? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Graphite),
        // Sin sombra: 5dp de elevación por tarjeta es coste de GPU por frame
        // durante el fling y sobre fondo grafito apenas se aprecia.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.aspectRatio(2f / 3f).combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick?.let { longClick ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    longClick()
                }
            },
        ),
    ) {
        Box(Modifier.fillMaxSize().background(GraphiteLight)) {
            IptvAsyncImage(
                model = item.logoUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                fadeIn = false,
            )
            // Scrim sólo bajo el texto: el gradiente a altura completa era
            // overdraw doble por tarjeta en cada frame de scroll.
            Box(
                Modifier.fillMaxWidth().fillMaxHeight(0.42f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        ),
                    ),
            )
            IconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleFavorite()
                },
                modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.catalog_cd_favorite),
                    tint = if (item.isFavorite) Carmine else Color.White.copy(alpha = 0.82f),
                )
            }
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(
                    text = item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (download != null) {
                DownloadBadge(
                    download = download,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Carmine,
                    trackColor = GraphiteLight.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
        }
    }
}

/** Insignia de estado de descarga sobre el póster (esquina superior izquierda). */
@Composable
internal fun DownloadBadge(download: DownloadEntity, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(28.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center,
    ) {
        when (download.status) {
            DownloadStatus.DONE -> Icon(
                imageVector = Icons.Filled.DownloadDone,
                contentDescription = null,
                tint = Carmine,
                modifier = Modifier.size(16.dp),
            )
            DownloadStatus.FAILED -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            else -> {
                val pct = if (download.totalBytes > 0) {
                    download.downloadedBytes.toFloat() / download.totalBytes
                } else {
                    -1f
                }
                if (pct >= 0f) {
                    CircularProgressIndicator(
                        progress = { pct },
                        modifier = Modifier.size(20.dp),
                        color = Carmine,
                        strokeWidth = 2.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Carmine,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

/**
 * Póster con menú contextual en pulsación larga: reproducir, descargar /
 * gestionar la descarga existente y favorito. [onDownload] null = item no
 * descargable (p. ej. una serie: se descargan sus episodios).
 */
@Composable
internal fun PosterCardWithMenu(
    item: ChannelEntity,
    download: DownloadEntity?,
    progress: Float?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onCancelDownload: () -> Unit = {},
    onDeleteDownload: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        PosterCard(
            item = item,
            onClick = onClick,
            onToggleFavorite = onToggleFavorite,
            modifier = modifier,
            progress = progress,
            download = download,
            onLongClick = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            fun dismissAnd(action: () -> Unit): () -> Unit = { menuOpen = false; action() }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.catalog_menu_play)) },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = dismissAnd(onPlay),
            )
            when (download?.status) {
                DownloadStatus.DONE -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.catalog_menu_download_delete)) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = dismissAnd(onDeleteDownload),
                )
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> DropdownMenuItem(
                    text = { Text(stringResource(R.string.catalog_menu_download_cancel)) },
                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                    onClick = dismissAnd(onCancelDownload),
                )
                DownloadStatus.FAILED -> {
                    if (onDownload != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.catalog_menu_download_retry)) },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = dismissAnd(onDownload),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.catalog_menu_download_delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = dismissAnd(onDeleteDownload),
                    )
                }
                else -> if (onDownload != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.catalog_menu_download)) },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = dismissAnd(onDownload),
                    )
                }
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (item.isFavorite) R.string.catalog_favorite_remove
                            else R.string.catalog_favorite_add,
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (item.isFavorite) Carmine else Color.Unspecified,
                    )
                },
                onClick = dismissAnd(onToggleFavorite),
            )
        }
    }
}

/**
 * Esqueleto de la vista de descubrimiento (héroe + filas de pósters) durante
 * la primera carga: sustituye al estado vacío, que hacía parecer la sección
 * sin contenido mientras llegaban las consultas a Room.
 */
@Composable
internal fun BrowseSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Graphite),
        )
        repeat(3) {
            Column {
                Box(
                    Modifier.padding(horizontal = 16.dp)
                        .width(140.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Graphite),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    repeat(4) {
                        Box(
                            Modifier.width(128.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Graphite),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parrilla paginada de pósters para resultados de búsqueda y "ver todo".
 * Con [onPlay] no nulo cada tarjeta ofrece el menú contextual (pulsación
 * larga) con reproducción y gestión de descargas.
 */
@Composable
internal fun PosterGrid(
    content: LazyPagingItems<ChannelEntity>,
    onClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
    favOverride: (Long) -> Boolean? = { null },
    downloads: Map<Long, DownloadEntity> = emptyMap(),
    onPlay: ((ChannelEntity) -> Unit)? = null,
    onDownload: ((ChannelEntity) -> Unit)? = null,
    onCancelDownload: (ChannelEntity) -> Unit = {},
    onDeleteDownload: (DownloadEntity) -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = content.itemCount, key = content.itemKey { it.id }) { index ->
            content[index]?.let { raw ->
                // La estrella usa el estado optimista: el refresh de Paging
                // puede tardar segundos en catálogos grandes.
                val item = favOverride(raw.id)
                    ?.let { raw.copy(isFavorite = it) } ?: raw
                val download = downloads[item.id]
                if (onPlay != null) {
                    PosterCardWithMenu(
                        item = item,
                        download = download,
                        progress = null,
                        onClick = { onClick(item.id) },
                        onPlay = { onPlay(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                        onDownload = onDownload?.let { dl -> { dl(item) } },
                        onCancelDownload = { onCancelDownload(item) },
                        onDeleteDownload = { download?.let(onDeleteDownload) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PosterCard(
                        item = item,
                        onClick = { onClick(item.id) },
                        onToggleFavorite = { onToggleFavorite(item) },
                        modifier = Modifier.fillMaxWidth(),
                        download = download,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            PagingAppendState(content.loadState.append) { content.retry() }
        }
    }
}

/**
 * Lista de descargas con el progreso claramente visible: miniatura, estado
 * con porcentaje y tamaño, barra de progreso y la acción que corresponde al
 * estado (reproducir, cancelar, reintentar o eliminar).
 */
@Composable
internal fun DownloadsList(
    content: LazyPagingItems<ChannelEntity>,
    downloads: Map<Long, DownloadEntity>,
    onClick: (Long) -> Unit,
    onDownload: (ChannelEntity) -> Unit,
    onCancelDownload: (ChannelEntity) -> Unit,
    onDeleteDownload: (DownloadEntity) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = content.itemCount, key = content.itemKey { it.id }) { index ->
            content[index]?.let { item ->
                DownloadRow(
                    item = item,
                    download = downloads[item.id],
                    onClick = { onClick(item.id) },
                    onRetry = { onDownload(item) },
                    onCancel = { onCancelDownload(item) },
                    onDelete = { downloads[item.id]?.let(onDeleteDownload) },
                )
            }
        }
        item { PagingAppendState(content.loadState.append) { content.retry() } }
        if (content.loadState.refresh !is LoadState.Loading && content.itemCount == 0) {
            item {
                Box(
                    Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.catalog_downloads_empty),
                        color = MutedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: ChannelEntity,
    download: DownloadEntity?,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val done = download?.status == DownloadStatus.DONE
    val failed = download == null || download.status == DownloadStatus.FAILED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GraphiteLight)
            .clickable(enabled = done || failed) { if (done) onClick() else onRetry() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(52.dp).aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(Graphite),
            contentAlignment = Alignment.Center,
        ) {
            if (item.logoUrl.isNullOrBlank()) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Carmine,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                IptvAsyncImage(
                    model = item.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    animated = true,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = downloadStatusText(download),
                color = if (failed) MaterialTheme.colorScheme.error else MutedText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (download?.status == DownloadStatus.QUEUED ||
                download?.status == DownloadStatus.DOWNLOADING
            ) {
                Spacer(Modifier.height(6.dp))
                if (download.status == DownloadStatus.DOWNLOADING && download.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = {
                            download.downloadedBytes / download.totalBytes.toFloat()
                        },
                        color = Carmine,
                        trackColor = Graphite,
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                } else {
                    LinearProgressIndicator(
                        color = Carmine,
                        trackColor = Graphite,
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        when {
            done -> IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.catalog_menu_download_delete),
                    tint = MutedText,
                )
            }
            failed -> IconButton(onClick = onRetry) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.catalog_menu_download_retry),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            else -> IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.catalog_menu_download_cancel),
                    tint = MutedText,
                )
            }
        }
    }
}

/** Línea de estado legible de una descarga: porcentaje y tamaño si se conocen. */
@Composable
private fun downloadStatusText(download: DownloadEntity?): String = when {
    download == null || download.status == DownloadStatus.FAILED ->
        stringResource(R.string.download_state_failed)
    download.status == DownloadStatus.QUEUED ->
        stringResource(R.string.download_state_queued)
    download.status == DownloadStatus.DOWNLOADING && download.totalBytes > 0 ->
        stringResource(
            R.string.download_state_progress,
            (download.downloadedBytes * 100 / download.totalBytes).toInt(),
            formatBytes(download.downloadedBytes),
            formatBytes(download.totalBytes),
        )
    download.status == DownloadStatus.DOWNLOADING ->
        stringResource(
            R.string.download_state_progress_unknown,
            formatBytes(download.downloadedBytes),
        )
    else -> stringResource(
        R.string.download_state_done,
        formatBytes(download.totalBytes.takeIf { it > 0 } ?: download.downloadedBytes),
    )
}

/**
 * Pie de lista para la página siguiente: progreso discreto al cargar y
 * mensaje con reintento si falla (antes una página fallida quedaba muda).
 */
@Composable
internal fun PagingAppendState(state: LoadState, onRetry: () -> Unit) {
    when (state) {
        is LoadState.Loading -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(26.dp), color = Carmine, strokeWidth = 2.dp)
        }
        is LoadState.Error -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.catalog_paging_error),
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(com.iptv.core.designsystem.R.string.ds_retry), color = Carmine)
            }
        }
        else -> Unit
    }
}

/** Logo cuadrado de canal con marcador cuando no hay imagen. */
@Composable
internal fun ChannelLogo(
    logoUrl: String?,
    size: Dp = 60.dp,
) {
    // Si la carga falla (URL rota, formato no soportado) se muestra el icono
    // de play en lugar de la caja vacía del placeholder.
    var failed by remember(logoUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(GraphiteLight),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNullOrBlank() || failed) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Carmine, modifier = Modifier.size(32.dp))
        } else {
            IptvAsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(5.dp),
                onError = { failed = true },
                animated = true,
            )
        }
    }
}

internal fun searchHintFor(kind: ContentKind): Int = when (kind) {
    ContentKind.TV -> R.string.catalog_search_tv
    ContentKind.MOVIES -> R.string.catalog_search_movies
    ContentKind.SERIES -> R.string.catalog_search_series
}

internal fun emptyMessageFor(kind: ContentKind): Int = when (kind) {
    ContentKind.TV -> R.string.catalog_empty_tv
    ContentKind.MOVIES -> R.string.catalog_empty_movies
    ContentKind.SERIES -> R.string.catalog_empty_series
}

/** Chip que abre el selector de idioma. */
@Composable
internal fun LanguageChip(
    selected: String?,
    onClick: () -> Unit,
) {
    CategoryChip(
        label = selected?.let { languageLabel(it) } ?: stringResource(R.string.catalog_language),
        selected = selected != null,
        onClick = onClick,
        leadingIcon = {
            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(16.dp))
        },
    )
}

/** Nombre legible de la familia de idioma; el resto usa Locale como país. */
@Composable
internal fun languageLabel(code: String): String {
    val manual = when (code) {
        "EN" -> R.string.lang_en
        "ES" -> R.string.lang_es
        "AR" -> R.string.lang_ar
        "DE" -> R.string.lang_de
        "FR" -> R.string.lang_fr
        "PT" -> R.string.lang_pt
        "IT" -> R.string.lang_it
        "TR" -> R.string.lang_tr
        "NL" -> R.string.lang_nl
        "SCANDI" -> R.string.lang_scandi
        "EXYU" -> R.string.lang_exyu
        "RU" -> R.string.lang_ru
        "PL" -> R.string.lang_pl
        "RO" -> R.string.lang_ro
        "GR" -> R.string.lang_gr
        "IL" -> R.string.lang_il
        "IN" -> R.string.lang_in
        "IR" -> R.string.lang_ir
        "KU" -> R.string.lang_ku
        "CN" -> R.string.lang_cn
        "JP" -> R.string.lang_jp
        "KR" -> R.string.lang_kr
        "SEA" -> R.string.lang_sea
        "AFR" -> R.string.lang_afr
        "ASIA" -> R.string.lang_asia
        "EU" -> R.string.lang_eu
        "INT" -> R.string.lang_int
        "AL" -> R.string.lang_al
        "MV" -> R.string.lang_mv
        "MC" -> R.string.lang_mc
        "RX" -> R.string.lang_rx
        else -> null
    }
    if (manual != null) return stringResource(manual)
    val country = java.util.Locale("", code).getDisplayCountry(java.util.Locale.getDefault())
    return if (country.isBlank() || country.equals(code, ignoreCase = true)) code else "$code · $country"
}

/** Diálogo con los idiomas detectados en el origen activo. */
@Composable
internal fun LanguagePickerDialog(
    languages: List<Pair<String, Int>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.catalog_language_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item(key = "all") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.catalog_language_all)) },
                        trailingContent = {
                            if (selected == null) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Carmine)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            onSelect(null)
                            onDismiss()
                        },
                    )
                }
                items(languages, key = { it.first }) { (code, count) ->
                    ListItem(
                        headlineContent = { Text(languageLabel(code)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$count",
                                    color = MutedText,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (selected == code) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Carmine)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            onSelect(code)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.catalog_close))
            }
        },
    )
}
