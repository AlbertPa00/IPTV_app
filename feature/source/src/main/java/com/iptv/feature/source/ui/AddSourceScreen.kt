package com.iptv.feature.source.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.feature.source.R
import com.iptv.feature.source.domain.SyncStep

/**
 * Pantalla de alta de fuente con dos vías: lista M3U por URL o cuenta Xtream
 * (usuario/contraseña). Un punto de entrada por fuente, sin jerga técnica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddSourceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addM3uFile(it.toString()) }
    }

    LaunchedEffect(state.completedSourceId) {
        if (state.completedSourceId != null) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.source_add_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.source_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            TabRow(selectedTabIndex = if (state.tab == AddSourceViewModel.Tab.M3U) 0 else 1) {
                Tab(
                    selected = state.tab == AddSourceViewModel.Tab.M3U,
                    onClick = { viewModel.onTabSelect(AddSourceViewModel.Tab.M3U) },
                    text = { Text(stringResource(R.string.source_tab_m3u)) },
                )
                Tab(
                    selected = state.tab == AddSourceViewModel.Tab.XTREAM,
                    onClick = { viewModel.onTabSelect(AddSourceViewModel.Tab.XTREAM) },
                    text = { Text(stringResource(R.string.source_tab_xtream)) },
                )
            }

            Spacer(Modifier.height(20.dp))

            if (state.tab == AddSourceViewModel.Tab.M3U) {
                OutlinedTextField(
                    value = state.listName,
                    onValueChange = viewModel::onListNameChange,
                    label = { Text(stringResource(R.string.source_field_name)) },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.listUrl,
                    onValueChange = viewModel::onListUrlChange,
                    label = { Text(stringResource(R.string.source_field_url)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = viewModel::addM3uList,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.source_action_add_m3u)) }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.source_or_file),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf("application/vnd.apple.mpegurl", "audio/x-mpegurl", "text/plain", "*/*")
                        )
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null)
                    Text(stringResource(R.string.source_action_select_file))
                }
            } else {
                OutlinedTextField(
                    value = state.server,
                    onValueChange = viewModel::onServerChange,
                    label = { Text(stringResource(R.string.source_field_server)) },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text(stringResource(R.string.source_field_username)) },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.source_field_password)) },
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = if (state.showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = viewModel::onTogglePassword) {
                            Icon(
                                imageVector = if (state.showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.source_cd_toggle_password),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.accountName,
                    onValueChange = viewModel::onAccountNameChange,
                    label = { Text(stringResource(R.string.source_field_name)) },
                    singleLine = true,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = viewModel::loginXtream,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.source_action_login)) }
            }

            if (state.busy) {
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    text = progressLabel(state.step, state.count),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            state.errorRes?.let { res ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(res),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (state.partialDoneSourceId != null) {
                val sections = mutableListOf<String>()
                for (res in state.partialSections) sections += stringResource(res)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.source_sync_partial, sections.joinToString(", ")),
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::confirmDone,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.source_continue)) }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun progressLabel(step: SyncStep?, count: Int): String = when (step) {
    SyncStep.CONNECTING -> stringResource(R.string.source_progress_connecting)
    SyncStep.AUTHENTICATING -> stringResource(R.string.source_progress_authenticating)
    SyncStep.FETCHING -> stringResource(R.string.source_progress_fetching)
    SyncStep.IMPORTING -> stringResource(R.string.source_progress_importing, count)
    null -> stringResource(R.string.source_progress_connecting)
}
