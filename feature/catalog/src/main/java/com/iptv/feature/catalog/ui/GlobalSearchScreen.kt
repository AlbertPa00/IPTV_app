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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.core.designsystem.components.EmptyState
import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.feature.catalog.R

/**
 * Búsqueda global: una consulta cruza canales en directo, películas y series
 * de la fuente activa, agrupadas por sección.
 */
@Composable
fun GlobalSearchScreen(
    onChannelClick: (Long) -> Unit,
    onMovieClick: (Long) -> Unit,
    onSeriesClick: (Long) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(CinemaBlack)) {
        CatalogHeader(
            title = stringResource(R.string.search_title),
            subtitle = stringResource(R.string.search_subtitle),
            query = state.query,
            searchHint = R.string.search_hint,
            onQueryChange = viewModel::onQueryChange,
        )

        when {
            state.query.isBlank() -> EmptyState(stringResource(R.string.search_prompt))
            sections.isEmpty -> EmptyState(stringResource(R.string.catalog_empty_search))
            else -> SearchSections(
                sections = sections,
                onChannelClick = onChannelClick,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }
}

@Composable
private fun SearchSections(
    sections: GlobalSearchViewModel.Sections,
    onChannelClick: (Long) -> Unit,
    onMovieClick: (Long) -> Unit,
    onSeriesClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (sections.channels.isNotEmpty()) {
            item(key = "channels-header") { SectionTitle(stringResource(R.string.catalog_title_tv)) }
            items(sections.channels, key = { "ch-${it.id}" }) { channel ->
                ChannelResultRow(channel, onClick = { onChannelClick(channel.id) })
            }
        }
        if (sections.movies.isNotEmpty()) {
            item(key = "movies-header") { SectionTitle(stringResource(R.string.catalog_title_movies)) }
            item(key = "movies-row") {
                PosterRow(sections.movies, onMovieClick, onToggleFavorite)
            }
        }
        if (sections.series.isNotEmpty()) {
            item(key = "series-header") { SectionTitle(stringResource(R.string.catalog_title_series)) }
            item(key = "series-row") {
                PosterRow(sections.series, onSeriesClick, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun PosterRow(
    items: List<ChannelEntity>,
    onItemClick: (Long) -> Unit,
    onToggleFavorite: (ChannelEntity) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(items, key = { it.id }) { item ->
            PosterCard(
                item = item,
                onClick = { onItemClick(item.id) },
                onToggleFavorite = { onToggleFavorite(item) },
                modifier = Modifier.width(128.dp),
            )
        }
    }
}

@Composable
private fun ChannelResultRow(channel: ChannelEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLogo(channel.logoUrl, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = channel.name,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(4.dp, 20.dp).background(Carmine))
    }
}
