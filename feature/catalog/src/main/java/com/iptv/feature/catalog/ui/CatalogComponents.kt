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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.storage.entity.ChannelEntity
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
) {
    val haptics = LocalHapticFeedback.current
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Graphite),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        modifier = modifier.aspectRatio(2f / 3f),
    ) {
        Box(Modifier.fillMaxSize().background(GraphiteLight)) {
            IptvAsyncImage(
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
        item(span = { GridItemSpan(maxLineSpan) }) {
            PagingAppendState(content.loadState.append) { content.retry() }
        }
    }
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
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(GraphiteLight),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNullOrBlank()) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Carmine, modifier = Modifier.size(32.dp))
        } else {
            IptvAsyncImage(
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
