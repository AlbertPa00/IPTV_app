package com.iptv.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.feature.catalog.R

internal val CinemaBlack = Color(0xFF08090B)
internal val Graphite = Color(0xFF15171B)
internal val GraphiteLight = Color(0xFF22252B)
internal val Carmine = Color(0xFFE50914)
internal val MutedText = Color(0xFFB6B8BE)
internal val StarGold = Color(0xFFF5B50A)

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
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(searchHint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
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
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Graphite),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = modifier.aspectRatio(2f / 3f),
    ) {
        Box(Modifier.fillMaxSize().background(GraphiteLight)) {
            AsyncImage(
                model = item.logoUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                    ),
                ),
            )
            IconButton(
                onClick = onToggleFavorite,
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

/** Parrilla paginada de pósters para resultados de búsqueda y "ver todo". */
@Composable
internal fun PosterGrid(
    content: LazyPagingItems<ChannelEntity>,
    onClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = content.itemCount, key = content.itemKey { it.id }) { index ->
            content[index]?.let { item ->
                PosterCard(
                    item = item,
                    onClick = { onClick(item.id) },
                    onToggleFavorite = { onToggleFavorite(item) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Logo cuadrado de canal con marcador cuando no hay imagen. */
@Composable
internal fun ChannelLogo(
    logoUrl: String?,
    size: Dp = 60.dp,
) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(GraphiteLight),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNullOrBlank()) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Carmine, modifier = Modifier.size(32.dp))
        } else {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(5.dp),
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
