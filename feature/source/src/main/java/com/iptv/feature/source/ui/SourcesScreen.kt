package com.iptv.feature.source.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.core.designsystem.components.EmptyState
import com.iptv.core.storage.entity.SourceTypes
import com.iptv.feature.source.R
import java.text.DateFormat
import java.util.Date

/**
 * Gestión de fuentes: activar, actualizar y eliminar. Se muestra dentro de la
 * pestaña de Ajustes; añadir fuentes navega a la pantalla de alta.
 */
@Composable
fun SourcesScreen(
    onAddSource: () -> Unit,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.sources_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.rows.isEmpty()) {
                EmptyState(stringResource(R.string.sources_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.rows, key = { it.source.id }) { row ->
                        SourceCard(
                            row = row,
                            refreshing = row.source.id in state.refreshingIds,
                            onActivate = { viewModel.setActive(row.source.id) },
                            onRefresh = { viewModel.refresh(row.source) },
                            onDelete = { viewModel.requestDelete(row.source) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddSource, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.sources_add))
        }
    }

    state.pendingDelete?.let { source ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.sources_delete_title)) },
            text = { Text(stringResource(R.string.sources_delete_confirm, source.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.sources_delete_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(R.string.sources_delete_no))
                }
            },
        )
    }
}

@Composable
private fun SourceCard(
    row: SourcesViewModel.SourceRow,
    refreshing: Boolean,
    onActivate: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val source = row.source
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onActivate)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(
                    if (source.isActive) R.string.sources_active else R.string.sources_inactive
                ),
                tint = if (source.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(
                        typeLabel(source.type),
                        stringResource(R.string.sources_channels, row.channelCount),
                        lastSyncLabel(source.lastSyncAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (refreshing) {
                CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = "" })
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.sources_refresh))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sources_delete))
            }
        }
    }
}

@Composable
private fun typeLabel(type: String): String = when (type) {
    SourceTypes.M3U_URL, SourceTypes.M3U_FILE -> stringResource(R.string.sources_type_m3u)
    SourceTypes.XTREAM -> stringResource(R.string.sources_type_xtream)
    else -> type
}

@Composable
private fun lastSyncLabel(timestamp: Long?): String =
    if (timestamp == null) {
        stringResource(R.string.sources_last_sync_never)
    } else {
        stringResource(
            R.string.sources_last_sync,
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp)),
        )
    }
