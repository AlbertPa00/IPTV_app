package com.iptv.feature.source.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.core.storage.entity.CategoryEntity
import com.iptv.core.storage.entity.SourceTypes
import com.iptv.feature.source.R
import java.text.DateFormat
import java.util.Date

/**
 * Pestaña Ajustes: fuentes, opciones (refresco automático y control
 * parental con PIN) y Acerca de (versión, licencias, privacidad).
 */
@Composable
fun SettingsScreen(
    appVersion: String,
    onAddSource: () -> Unit,
    sourcesViewModel: SourcesViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val sourcesState by sourcesViewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var showLicenses by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.sources_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (sourcesState.rows.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.sources_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(sourcesState.rows, key = { it.source.id }) { row ->
                SourceCard(
                    row = row,
                    refreshing = row.source.id in sourcesState.refreshingIds,
                    onActivate = { sourcesViewModel.setActive(row.source.id) },
                    onRefresh = { sourcesViewModel.refresh(row.source) },
                    onDelete = { sourcesViewModel.requestDelete(row.source) },
                )
            }
        }

        item {
            Button(onClick = onAddSource, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.sources_add))
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        item {
            Text(
                stringResource(R.string.settings_options_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.settings_auto_refresh),
                subtitle = stringResource(R.string.settings_auto_refresh_sub),
                checked = settings.autoRefresh,
                onCheckedChange = settingsViewModel::setAutoRefresh,
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.settings_wifi_only),
                subtitle = stringResource(R.string.settings_wifi_only_sub),
                checked = settings.wifiOnly,
                enabled = settings.autoRefresh,
                onCheckedChange = settingsViewModel::setWifiOnly,
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        item {
            Text(
                stringResource(R.string.settings_parental_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            SwitchRow(
                title = stringResource(R.string.settings_parental),
                subtitle = stringResource(
                    if (settings.parentalEnabled) {
                        R.string.settings_parental_on_sub
                    } else {
                        R.string.settings_parental_off_sub
                    },
                ),
                checked = settings.parentalEnabled,
                onCheckedChange = { settingsViewModel.onParentalToggleClick() },
            )
        }

        if (settings.parentalEnabled) {
            item {
                TextButton(
                    onClick = settingsViewModel::onManageCategoriesClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.settings_parental_manage))
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

        item {
            Text(
                stringResource(R.string.settings_about_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        item {
            AboutRow(
                label = stringResource(R.string.settings_version),
                value = appVersion,
            )
        }
        item {
            AboutRow(
                label = stringResource(R.string.settings_licenses),
                onClick = { showLicenses = true },
            )
        }
        item {
            AboutRow(
                label = stringResource(R.string.settings_privacy),
                onClick = { showPrivacy = true },
            )
        }
    }

    // -- Diálogos ---------------------------------------------------------

    sourcesState.pendingDelete?.let { source ->
        AlertDialog(
            onDismissRequest = sourcesViewModel::dismissDelete,
            title = { Text(stringResource(R.string.sources_delete_title)) },
            text = { Text(stringResource(R.string.sources_delete_confirm, source.name)) },
            confirmButton = {
                TextButton(onClick = sourcesViewModel::confirmDelete) {
                    Text(stringResource(R.string.sources_delete_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = sourcesViewModel::dismissDelete) {
                    Text(stringResource(R.string.sources_delete_no))
                }
            },
        )
    }

    settings.pinPrompt?.let { prompt ->
        PinDialog(
            prompt = prompt,
            error = settings.pinError,
            onSubmit = settingsViewModel::submitPin,
            onDismiss = settingsViewModel::dismissPin,
        )
    }

    if (settings.managingCategories) {
        LockedCategoriesDialog(
            categories = settings.categories,
            onToggle = settingsViewModel::setCategoryLocked,
            onDismiss = settingsViewModel::closeCategories,
        )
    }

    if (showLicenses) {
        TextDialog(
            title = stringResource(R.string.settings_licenses),
            body = stringResource(R.string.settings_licenses_body),
            onDismiss = { showLicenses = false },
        )
    }

    if (showPrivacy) {
        TextDialog(
            title = stringResource(R.string.settings_privacy),
            body = stringResource(R.string.settings_privacy_body),
            onDismiss = { showPrivacy = false },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun AboutRow(label: String, value: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PinDialog(
    prompt: SettingsViewModel.PinPrompt,
    error: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember(prompt) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (prompt) {
                        SettingsViewModel.PinPrompt.CREATE -> R.string.pin_create_title
                        SettingsViewModel.PinPrompt.CONFIRM_CREATE -> R.string.pin_confirm_title
                        SettingsViewModel.PinPrompt.DISABLE -> R.string.pin_unlock_disable_title
                        SettingsViewModel.PinPrompt.MANAGE -> R.string.pin_unlock_manage_title
                    },
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) pin = it },
                    label = { Text(stringResource(R.string.pin_field)) },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.pin_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(pin) }, enabled = pin.length == 4) {
                Text(stringResource(R.string.pin_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_cancel)) }
        },
    )
}

@Composable
private fun LockedCategoriesDialog(
    categories: List<CategoryEntity>,
    onToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_locked_categories)) },
        text = {
            LazyColumn {
                items(categories, key = { it.id }) { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                kindLabel(category.kind),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = category.isLocked,
                            onCheckedChange = { onToggle(category.id, it) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_accept)) }
        },
    )
}

@Composable
private fun kindLabel(kind: String): String = when (kind) {
    "VOD" -> stringResource(R.string.nav_movies_kind)
    "SERIES" -> stringResource(R.string.nav_series_kind)
    else -> stringResource(R.string.nav_live_kind)
}

@Composable
private fun TextDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn { item { Text(body, style = MaterialTheme.typography.bodySmall) } }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.pin_accept)) }
        },
    )
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
