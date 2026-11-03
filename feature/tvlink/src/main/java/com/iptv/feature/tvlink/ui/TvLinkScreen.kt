package com.iptv.feature.tvlink.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SendToMobile
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.core.common.tvlink.LinkFailure
import com.iptv.core.common.tvlink.PairedTv
import com.iptv.core.common.tvlink.RemoteChannel
import com.iptv.core.common.tvlink.RemoteSession
import com.iptv.core.common.tvlink.RemoteSource
import com.iptv.core.common.tvlink.TvLinkConnection
import com.iptv.core.storage.entity.SourceEntity
import kotlinx.coroutines.delay
import com.iptv.core.designsystem.components.IptvAsyncImage
import com.iptv.core.designsystem.tv.LocalIsTv
import com.iptv.core.designsystem.tv.TvTextField
import com.iptv.core.designsystem.tv.tvFocusAffordance
import com.iptv.core.designsystem.tv.tvSafeArea
import com.iptv.feature.tvlink.R
import com.iptv.feature.tvlink.wire.LINK_PORT_FALLBACK

/**
 * Pantalla única de vinculación: el mismo composable sirve de host (la TV
 * enseña el PIN y sus clientes) y de mando (el móvil descubre, empareja y
 * controla). Las secciones se reordenan según el dispositivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvLinkScreen(
    onBack: () -> Unit,
    onPlayLocal: (Long) -> Unit,
    viewModel: TvLinkViewModel = hiltViewModel(),
) {
    val host by viewModel.hostStatus.collectAsStateWithLifecycle()
    val discovered by viewModel.discovered.collectAsStateWithLifecycle()
    val paired by viewModel.pairedTvs.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val remote by viewModel.remote.collectAsStateWithLifecycle()
    val localNow by viewModel.localNowPlaying.collectAsStateWithLifecycle()

    var showManual by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showInstallOnTv by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // "Instalar en más dispositivos" sólo existe si la ficha de Play
    // está publicada: en builds sideload el acceso no se muestra.
    val installedFromPlay = remember { isInstalledFromPlay(context) }
    // Subpaneles del mando: catálogo y fuentes del host ocupan toda la vista.
    var panel by remember { mutableStateOf(LinkPanel.NONE) }
    LaunchedEffect(connection.state) {
        if (connection.state != TvLinkConnection.CONNECTED) panel = LinkPanel.NONE
    }

    // Tras un handoff TV→aquí la sesión local ya reproduce: a la pantalla.
    LaunchedEffect(Unit) {
        viewModel.handoffApplied.collect(onPlayLocal)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tvlink_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.tvFocusAffordance(shape = CircleShape),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        val isTv = LocalIsTv.current

        // En la TV el caso de uso habitual es "sacar el PIN de vinculación";
        // en el móvil es "encontrar y controlar la TV". La sección útil va
        // primero en cada factor.
        fun LazyListScope.remoteSection() {
            item {
                Text(
                    stringResource(R.string.tvlink_remote_section),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            when (connection.state) {
                TvLinkConnection.CONNECTED -> item {
                    RemotePanel(
                        viewModel = viewModel,
                        deviceName = connection.deviceName.orEmpty(),
                        hasLocalContent = localNow != null,
                        onDisconnect = viewModel::disconnect,
                        onSleepTimer = { showSleep = true },
                        onOpenPanel = { panel = it },
                    )
                }
                else -> {
                    item { ConnectionStateCard(connection) }
                    if (connection.state == TvLinkConnection.PIN_REQUIRED) {
                        item { PinEntry(onSubmit = viewModel::submitPin) }
                    }
                    items(discovered, key = { it.id }) { tv ->
                        DeviceRow(
                            name = tv.name,
                            subtitle = "${tv.host}:${tv.port}",
                            onClick = { viewModel.connect(tv) },
                        )
                    }
                    items(
                        paired.filter { known -> discovered.none { it.id == known.id } },
                        key = { "paired-${it.id}" },
                    ) { tv ->
                        PairedRow(
                            tv = tv,
                            onClick = { viewModel.connect(tv) },
                            onUnpair = { viewModel.unpair(tv.id) },
                        )
                    }
                    item {
                        TextButton(
                            onClick = { showManual = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .tvFocusAffordance(),
                        ) {
                            Text(stringResource(R.string.tvlink_manual))
                        }
                    }
                    if (!isTv && installedFromPlay) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvFocusAffordance()
                                    .clickable { showInstallOnTv = true }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.SendToMobile, contentDescription = null)
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.tvlink_install_on_tv),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        stringResource(R.string.tvlink_install_on_tv_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        fun LazyListScope.hostSection() {
            item {
                Text(
                    stringResource(R.string.tvlink_host_section),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusAffordance(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.tvlink_host_enable),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(
                                when {
                                    !host.enabled -> R.string.tvlink_host_off_sub
                                    host.running -> R.string.tvlink_host_on_sub
                                    else -> R.string.tvlink_host_error_sub
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = host.enabled, onCheckedChange = viewModel::setHostEnabled)
                }
            }
            if (host.enabled && host.running) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.tvlink_port_sub, host.port),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            onClick = viewModel::showPin,
                            modifier = Modifier.tvFocusAffordance(),
                        ) {
                            Text(stringResource(R.string.tvlink_show_pin))
                        }
                    }
                }
                host.clients.forEach { client ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (client.connected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(client.name, Modifier.weight(1f))
                            TextButton(
                                onClick = { viewModel.revoke(client.id) },
                                modifier = Modifier.tvFocusAffordance(),
                            ) {
                                Text(stringResource(R.string.tvlink_revoke))
                            }
                        }
                    }
                }
            }
        }

        val remoteNow = remote
        when {
            panel == LinkPanel.CATALOG && remoteNow != null -> RemoteCatalogPanel(
                remote = remoteNow,
                onBack = { panel = LinkPanel.NONE },
                modifier = Modifier.padding(padding),
            )
            panel == LinkPanel.SOURCES && remoteNow != null -> RemoteSourcesPanel(
                remote = remoteNow,
                viewModel = viewModel,
                onBack = { panel = LinkPanel.NONE },
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .then(if (isTv) Modifier.tvSafeArea() else Modifier),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isTv) {
                    hostSection()
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    remoteSection()
                } else {
                    remoteSection()
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    hostSection()
                }
            }
        }
    }

    if (showManual) {
        ManualDialog(
            onConnect = { host_, port -> viewModel.connectManual(host_, port); showManual = false },
            onDismiss = { showManual = false },
        )
    }

    if (showInstallOnTv) {
        AlertDialog(
            onDismissRequest = { showInstallOnTv = false },
            title = { Text(stringResource(R.string.tvlink_install_title)) },
            text = { Text(stringResource(R.string.tvlink_install_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showInstallOnTv = false
                        openPlayListing(context)
                    },
                    modifier = Modifier.tvFocusAffordance(),
                ) {
                    Text(stringResource(R.string.tvlink_install_open_play))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showInstallOnTv = false },
                    modifier = Modifier.tvFocusAffordance(),
                ) {
                    Text(stringResource(R.string.tvlink_cancel))
                }
            },
        )
    }

    // PIN de vinculación en modal opaco: imposible de pasar por alto en la
    // TV y con fondo oscurecido detrás (antes era texto suelto en la lista).
    host.pairingPin?.let { pin ->
        AlertDialog(
            onDismissRequest = viewModel::hidePin,
            title = { Text(stringResource(R.string.tvlink_show_pin)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.tvlink_pin_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        pin,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::hidePin,
                    modifier = Modifier.tvFocusAffordance(),
                ) {
                    Text(stringResource(R.string.tvlink_pin_hide))
                }
            },
        )
    }

    val remoteSession = remote
    if (showSleep && remoteSession != null) {
        SleepTimerDialog(
            onSelect = { remoteSession.setSleepTimer(it); showSleep = false },
            onDismiss = { showSleep = false },
        )
    }
}

// -- Panel de control remoto -----------------------------------------------------

/** Subvista del mando cuando hay conexión: portada, catálogo o fuentes. */
private enum class LinkPanel { NONE, CATALOG, SOURCES }

@Composable
private fun RemotePanel(
    viewModel: TvLinkViewModel,
    deviceName: String,
    hasLocalContent: Boolean,
    onDisconnect: () -> Unit,
    onSleepTimer: () -> Unit,
    onOpenPanel: (LinkPanel) -> Unit,
) {
    val remote = viewModel.remote.collectAsStateWithLifecycle().value ?: return
    val now by remote.nowPlaying.collectAsStateWithLifecycle()
    val info by remote.info.collectAsStateWithLifecycle()
    val position by remote.positionMs.collectAsStateWithLifecycle()
    val duration by remote.durationMs.collectAsStateWithLifecycle()
    val playing by remote.isPlaying.collectAsStateWithLifecycle()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.tvlink_connected_to, deviceName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IptvAsyncImage(
                    model = now?.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        ),
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        now?.name ?: stringResource(R.string.tvlink_nothing_playing),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            now == null -> ""
                            now!!.isLive -> stringResource(R.string.tvlink_live)
                            else -> formatPosition(position)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            info.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Línea temporal arrastrable: sólo bajo demanda (el directo no
            // tiene duración). Al soltar va un seek absoluto al host.
            if (now != null && !now!!.isLive && duration > 0) {
                var dragPos by remember { mutableStateOf<Float?>(null) }
                val shownPos = (dragPos ?: position.toFloat())
                    .coerceIn(0f, duration.toFloat())
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = shownPos,
                    onValueChange = { dragPos = it },
                    onValueChangeFinished = {
                        dragPos?.let { remote.seekTo(it.toLong()) }
                        dragPos = null
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth().tvFocusAffordance(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatPosition(shownPos.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatPosition(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteButton(Icons.Filled.KeyboardArrowDown, "CH−") { remote.zap(-1) }
                RemoteButton(Icons.Filled.Replay10, "−10 s") { remote.seekBy(-10_000) }
                IconButton(
                    onClick = { if (playing) remote.pause() else remote.resume() },
                    modifier = Modifier.tvFocusAffordance(shape = CircleShape),
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                RemoteButton(Icons.Filled.Forward10, "+10 s") {
                    remote.seekBy(10_000)
                }
                RemoteButton(Icons.Filled.KeyboardArrowUp, "CH+") { remote.zap(+1) }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val btn = Modifier.tvFocusAffordance(shape = CircleShape)
                IconButton(onClick = { remote.volumeBy(-1) }, modifier = btn) {
                    Icon(Icons.Filled.VolumeDown, contentDescription = null)
                }
                IconButton(onClick = { remote.volumeBy(+1) }, modifier = btn) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null)
                }
                IconButton(onClick = { remote.zapToPrevious() }, modifier = btn) {
                    Icon(Icons.Filled.History, contentDescription = null)
                }
                IconButton(onClick = onSleepTimer, modifier = btn) {
                    Icon(Icons.Filled.Timer, contentDescription = null)
                }
                IconButton(onClick = viewModel::takeHere, modifier = btn) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                }
                IconButton(
                    onClick = viewModel::sendLocalToTv,
                    enabled = hasLocalContent,
                    modifier = btn,
                ) {
                    Icon(Icons.Filled.SendToMobile, contentDescription = null)
                }
                IconButton(onClick = onDisconnect, modifier = btn) {
                    Icon(
                        Icons.Filled.LinkOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { onOpenPanel(LinkPanel.CATALOG) },
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusAffordance(),
                ) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.tvlink_catalog))
                }
                FilledTonalButton(
                    onClick = { onOpenPanel(LinkPanel.SOURCES) },
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusAffordance(),
                ) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.tvlink_sources))
                }
            }
        }
    }
}

@Composable
private fun RemoteButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.tvFocusAffordance(shape = CircleShape),
        ) {
            Icon(icon, contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// -- Secciones auxiliares ---------------------------------------------------------

@Composable
private fun ConnectionStateCard(connection: com.iptv.core.common.tvlink.TvLinkConnectionState) {
    val text = when (connection.state) {
        TvLinkConnection.DISCONNECTED -> stringResource(R.string.tvlink_scanning)
        TvLinkConnection.CONNECTING -> stringResource(R.string.tvlink_connecting)
        TvLinkConnection.PIN_REQUIRED -> stringResource(
            if (connection.failure == LinkFailure.PIN_WRONG) {
                R.string.tvlink_wrong_pin
            } else {
                R.string.tvlink_enter_pin_hint
            },
        )
        TvLinkConnection.CONNECTED -> return
        TvLinkConnection.FAILED -> stringResource(
            when (connection.failure) {
                LinkFailure.VERSION -> R.string.tvlink_fail_version
                LinkFailure.PIN_WRONG -> R.string.tvlink_fail_rejected
                else -> R.string.tvlink_fail_unreachable
            },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (connection.state == TvLinkConnection.CONNECTING ||
            connection.state == TvLinkConnection.DISCONNECTED
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PinEntry(onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TvTextField(
            value = pin,
            onValueChange = { if (it.length <= 6) pin = it.filter(Char::isDigit) },
            label = stringResource(R.string.tvlink_pin_field),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(10.dp))
        Button(
            onClick = { onSubmit(pin) },
            enabled = pin.length == 6,
            modifier = Modifier.tvFocusAffordance(),
        ) {
            Text(stringResource(R.string.tvlink_pair))
        }
    }
}

@Composable
private fun DeviceRow(name: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusAffordance()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Tv, contentDescription = null)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PairedRow(tv: PairedTv, onClick: () -> Unit, onUnpair: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusAffordance()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tv.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.tvlink_paired_offline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onUnpair,
            modifier = Modifier.tvFocusAffordance(),
        ) {
            Text(stringResource(R.string.tvlink_revoke))
        }
    }
}

// -- Diálogos ---------------------------------------------------------------------

@Composable
private fun ManualDialog(onConnect: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(LINK_PORT_FALLBACK.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tvlink_manual)) },
        text = {
            Column {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text(stringResource(R.string.tvlink_manual_host)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.tvlink_manual_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(host, port.toIntOrNull() ?: 0) },
                enabled = host.isNotBlank() && (port.toIntOrNull() ?: 0) > 0,
                modifier = Modifier.tvFocusAffordance(),
            ) {
                Text(stringResource(R.string.tvlink_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocusAffordance()) {
                Text(stringResource(R.string.tvlink_cancel))
            }
        },
    )
}

@Composable
private fun SleepTimerDialog(onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tvlink_sleep)) },
        text = {
            Column {
                options.forEach { minutes ->
                    Text(
                        stringResource(R.string.tvlink_sleep_minutes, minutes),
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvFocusAffordance()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.tvlink_sleep_off),
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusAffordance()
                        .clickable { onSelect(null) }
                        .padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocusAffordance()) {
                Text(stringResource(R.string.tvlink_cancel))
            }
        },
    )
}

private fun formatPosition(ms: Long): String {
    val totalSec = (ms / 1_000).coerceAtLeast(0)
    val h = totalSec / 3_600
    val m = (totalSec % 3_600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// -- Catálogo remoto del host ---------------------------------------------------

/**
 * Catálogo del host a pantalla completa: pestañas por tipo, chips de grupo,
 * favoritos, búsqueda con debounce y paginación al llegar al final.
 * Tocar un canal envía PLAY con su clave estable — la TV resuelve el canal
 * en su propia base de datos.
 */
@Composable
private fun RemoteCatalogPanel(
    remote: RemoteSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catalog by remote.catalog.collectAsStateWithLifecycle()
    val now by remote.nowPlaying.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf("LIVE") }
    var group by remember { mutableStateOf<String?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Un solo origen de peticiones: cualquier cambio de filtro o el texto
    // (con debounce corto) recarga la primera página en el host.
    LaunchedEffect(kind, group, favOnly, query) {
        delay(300)
        remote.browseCatalog(kind, query.ifBlank { null }, group, favOnly, offset = 0)
    }

    // Carga la página siguiente al acercarse al final de la lista.
    val lastVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
    }
    LaunchedEffect(lastVisible, catalog.items.size) {
        if (!catalog.loading && catalog.items.isNotEmpty() &&
            catalog.items.size < catalog.total &&
            lastVisible >= catalog.items.size - 20
        ) {
            remote.browseCatalog(
                catalog.kind, catalog.query, catalog.group,
                catalog.favoritesOnly, catalog.items.size,
            )
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.tvFocusAffordance()) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.tvlink_back))
            }
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.tvlink_catalog),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val kinds = listOf(
                "LIVE" to R.string.tvlink_catalog_live,
                "VOD" to R.string.tvlink_catalog_vod,
                "SERIES" to R.string.tvlink_catalog_series,
            )
            kinds.forEach { (k, label) ->
                FilterChip(
                    selected = kind == k,
                    onClick = { kind = k; group = null },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.tvFocusAffordance(),
                )
            }
            FilterChip(
                selected = favOnly,
                onClick = { favOnly = !favOnly },
                label = { Text(stringResource(R.string.tvlink_catalog_favs)) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.tvFocusAffordance(),
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.tvlink_catalog_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        )

        if (catalog.groups.size > 1) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = group == null,
                        onClick = { group = null },
                        label = { Text(stringResource(R.string.tvlink_catalog_all)) },
                        modifier = Modifier.tvFocusAffordance(),
                    )
                }
                items(catalog.groups) { g ->
                    FilterChip(
                        selected = group == g.key,
                        onClick = { group = g.key },
                        label = {
                            Text(
                                g.name.ifEmpty {
                                    stringResource(R.string.tvlink_catalog_uncat)
                                } + " · " + g.count,
                            )
                        },
                        modifier = Modifier.tvFocusAffordance(),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(catalog.items, key = { it.kind + "|" + it.key }) { ch ->
                RemoteChannelRow(
                    channel = ch,
                    playing = now?.channelKey == ch.key,
                    onPlay = { remote.playKey(ch.key, ch.name) },
                    onToggleFav = { remote.setFavoriteKey(ch.key, !ch.isFavorite) },
                )
            }
            when {
                catalog.loading -> item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                    }
                }
                catalog.items.isEmpty() -> item {
                    Text(
                        stringResource(R.string.tvlink_catalog_empty),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> item {
                    Text(
                        stringResource(R.string.tvlink_catalog_total, catalog.total),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteChannelRow(
    channel: RemoteChannel,
    playing: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusAffordance()
            .clickable(onClick = onPlay)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IptvAsyncImage(
            model = channel.logoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(6.dp),
                ),
        )
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            channel.group?.takeIf { it.isNotEmpty() }?.let { g ->
                Text(
                    g,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (playing) {
            Text(
                stringResource(R.string.tvlink_now_playing_tag),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(6.dp))
        }
        IconButton(
            onClick = onToggleFav,
            modifier = Modifier.tvFocusAffordance(shape = CircleShape),
        ) {
            Icon(
                if (channel.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (channel.isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

// -- Fuentes del host -------------------------------------------------------------

/**
 * Gestor de fuentes del host: lista con activa/recuento, alta M3U/Xtream con
 * progreso en vivo, resincronización, activar — y "sincronizar mi lista",
 * que empuja una a una las fuentes locales del móvil a la TV.
 */
@Composable
private fun RemoteSourcesPanel(
    remote: RemoteSession,
    viewModel: TvLinkViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sources by remote.remoteSources.collectAsStateWithLifecycle()
    val op by remote.sourceOp.collectAsStateWithLifecycle()
    val localSources by viewModel.localSources.collectAsStateWithLifecycle()
    val syncing by viewModel.syncingSources.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showSyncPick by remember { mutableStateOf(false) }

    LaunchedEffect(remote) { remote.requestSources() }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.tvFocusAffordance()) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.tvlink_back))
            }
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.tvlink_sources),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val syncable = localSources.any { it.type != "M3U_FILE" }
            FilledTonalButton(
                onClick = { showSyncPick = true },
                enabled = !syncing && syncable,
                modifier = Modifier.tvFocusAffordance(),
            ) {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                } else {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(
                        if (syncing) R.string.tvlink_sources_syncing
                        else R.string.tvlink_sources_sync_mine,
                    ),
                )
            }
            FilledTonalButton(
                onClick = { showAdd = true },
                modifier = Modifier.tvFocusAffordance(),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.tvlink_sources_add))
            }
        }

        op?.let { o ->
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (o.running) {
                        CircularProgressIndicator(Modifier.size(18.dp))
                        Spacer(Modifier.size(10.dp))
                    }
                    Text(
                        when {
                            o.error != null -> stringResource(
                                R.string.tvlink_op_failed, sourceErrorText(o.error),
                            )
                            !o.running -> stringResource(R.string.tvlink_op_done)
                            o.count > 0 -> stringResource(R.string.tvlink_op_working, o.count)
                            else -> stringResource(R.string.tvlink_catalog_loading)
                        },
                        color = if (o.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sources, key = { it.key }) { s ->
                RemoteSourceRow(
                    source = s,
                    onRefresh = { remote.refreshSource(s.key) },
                    onActivate = { remote.setActiveSource(s.key) },
                )
            }
            if (sources.isEmpty() && !syncing) {
                item {
                    Text(
                        stringResource(R.string.tvlink_sources_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddSourceDialog(
            onAddM3u = { name, url -> remote.addM3uSource(name, url); showAdd = false },
            onAddXtream = { name, server, user, pass ->
                remote.addXtreamSource(name, server, user, pass)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }

    if (showSyncPick) {
        SyncSourcesDialog(
            sources = localSources,
            onSync = { picked ->
                viewModel.syncMySources(picked)
                showSyncPick = false
            },
            onDismiss = { showSyncPick = false },
        )
    }
}

/**
 * Selector de fuentes locales para "sincronizar mi lista": checkboxes con
 * todo marcado por defecto; los ficheros M3U del móvil se muestran
 * deshabilitados (su archivo no existe en la TV).
 */
@Composable
private fun SyncSourcesDialog(
    sources: List<SourceEntity>,
    onSync: (List<SourceEntity>) -> Unit,
    onDismiss: () -> Unit,
) {
    val syncable = sources.filter { it.type != "M3U_FILE" }
    var picked by remember { mutableStateOf(syncable.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tvlink_sync_pick_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.tvlink_sync_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (sources.isEmpty()) {
                    Text(
                        stringResource(R.string.tvlink_sync_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sources.forEach { s ->
                    val enabled = s.type != "M3U_FILE"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (enabled) {
                                    Modifier
                                        .tvFocusAffordance()
                                        .clickable {
                                            picked = if (s.id in picked) {
                                                picked - s.id
                                            } else {
                                                picked + s.id
                                            }
                                        }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = s.id in picked,
                            onCheckedChange = { on ->
                                picked = if (on) picked + s.id else picked - s.id
                            },
                            enabled = enabled,
                        )
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                if (enabled) {
                                    s.type
                                } else {
                                    stringResource(R.string.tvlink_sync_local_file)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSync(sources.filter { it.id in picked }) },
                enabled = picked.isNotEmpty(),
                modifier = Modifier.tvFocusAffordance(),
            ) {
                Text(stringResource(R.string.tvlink_sync_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocusAffordance()) {
                Text(stringResource(R.string.tvlink_cancel))
            }
        },
    )
}

@Composable
private fun RemoteSourceRow(
    source: RemoteSource,
    onRefresh: () -> Unit,
    onActivate: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(source.name, style = MaterialTheme.typography.bodyLarge)
                    if (source.active) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.tvlink_sources_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    listOfNotNull(
                        source.type,
                        source.detail,
                        stringResource(R.string.tvlink_sources_channels, source.channels),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.tvFocusAffordance(shape = CircleShape),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
            if (!source.active) {
                TextButton(
                    onClick = onActivate,
                    modifier = Modifier.tvFocusAffordance(),
                ) {
                    Text(stringResource(R.string.tvlink_sources_set_active))
                }
            }
        }
    }
}

@Composable
private fun AddSourceDialog(
    onAddM3u: (String?, String) -> Unit,
    onAddXtream: (String?, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var xtream by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    val valid = if (xtream) {
        server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    } else {
        url.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tvlink_sources_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !xtream,
                        onClick = { xtream = false },
                        label = { Text(stringResource(R.string.tvlink_add_m3u)) },
                        modifier = Modifier.tvFocusAffordance(),
                    )
                    FilterChip(
                        selected = xtream,
                        onClick = { xtream = true },
                        label = { Text(stringResource(R.string.tvlink_add_xtream)) },
                        modifier = Modifier.tvFocusAffordance(),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tvlink_add_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (xtream) {
                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text(stringResource(R.string.tvlink_add_server)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text(stringResource(R.string.tvlink_add_user)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text(stringResource(R.string.tvlink_add_pass)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.tvlink_add_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (xtream) {
                        onAddXtream(
                            name.ifBlank { null },
                            server.trim(), user.trim(), pass,
                        )
                    } else {
                        onAddM3u(name.ifBlank { null }, url.trim())
                    }
                },
                enabled = valid,
                modifier = Modifier.tvFocusAffordance(),
            ) {
                Text(stringResource(R.string.tvlink_add_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.tvFocusAffordance()) {
                Text(stringResource(R.string.tvlink_cancel))
            }
        },
    )
}

/** Clave de error del host ("invalid_url", "account_inactive:Paused"…) →
 *  recurso localizado del mando. */
@Composable
private fun sourceErrorText(key: String?): String = when (key?.substringBefore(':')) {
    "invalid_url" -> stringResource(R.string.tvlink_error_invalid_url)
    "network" -> stringResource(R.string.tvlink_error_network)
    "invalid_credentials" -> stringResource(R.string.tvlink_error_invalid_credentials)
    "account_inactive" -> stringResource(R.string.tvlink_error_account_inactive)
    "empty_playlist" -> stringResource(R.string.tvlink_error_empty_playlist)
    "not_found" -> stringResource(R.string.tvlink_error_not_found)
    else -> stringResource(R.string.tvlink_error_unknown)
}

/** ¿Llegó la app por Play? Es la señal de que la ficha existe y el
 *  flujo "Instalar en más dispositivos" puede funcionar. */
private fun isInstalledFromPlay(context: Context): Boolean {
    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            context.packageManager
                .getInstallSourceInfo(context.packageName)
                .installingPackageName
        }.getOrNull()
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getInstallerPackageName(context.packageName)
    }
    return installer == "com.android.vending"
}

/** Abre la ficha de Play; sin la app de Play se cae al navegador. */
private fun openPlayListing(context: Context) {
    val details = "details?id=${context.packageName}"
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://$details")),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/$details"),
                ),
            )
        }
    }
}
