package com.iptv.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.iptv.feature.player.R

internal val PlayerCarmine: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val PlayerGraphite: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant
internal val PlayerMuted: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Diálogos del reproductor compartidos entre la UI táctil (PlayerScreen)
 * y los overlays de TV: son neutrales de input — las filas clickable ya
 * son enfocables por D-pad y BACK llega como onDismissRequest.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun TracksDialog(player: Player?, onDismiss: () -> Unit) {
    // Tracks no es observable: sin este listener el check no se movía al
    // elegir pista y parecía que la selección no se aplicaba.
    var tracks by remember(player) { mutableStateOf(player?.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    val groups = tracks?.groups.orEmpty()
    // La misma pista puede aparecer en varios Tracks.Group (variantes HLS,
    // DVB): se fusionan por identidad para no repetir filas ni cabeceras.
    val audioOptions = remember(tracks) { mergeTrackOptions(groups, C.TRACK_TYPE_AUDIO) }
    val textOptions = remember(tracks) { mergeTrackOptions(groups, C.TRACK_TYPE_TEXT) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_tracks), color = Color.White) },
        text = {
            if (audioOptions.isEmpty() && textOptions.isEmpty()) {
                Text(
                    stringResource(R.string.player_tracks_empty),
                    color = PlayerMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    if (audioOptions.isNotEmpty()) {
                        item { TrackSectionHeader(R.string.player_audio) }
                        items(audioOptions.size) { i ->
                            val option = audioOptions[i]
                            TrackRow(
                                label = option.label,
                                selected = option.selected,
                                enabled = option.enabled,
                                onClick = { selectTrackOption(player, C.TRACK_TYPE_AUDIO, option) },
                            )
                        }
                    }
                    if (textOptions.isNotEmpty()) {
                        item { TrackSectionHeader(R.string.player_subtitles) }
                        item {
                            val noneSelected = groups
                                .filter { it.type == C.TRACK_TYPE_TEXT }
                                .all { g -> (0 until g.length).none { g.isTrackSelected(it) } }
                            TrackRow(
                                label = stringResource(R.string.player_track_off),
                                selected = noneSelected,
                                onClick = {
                                    player?.trackSelectionParameters =
                                        player.trackSelectionParameters.buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                            .build()
                                },
                            )
                        }
                        items(textOptions.size) { i ->
                            val option = textOptions[i]
                            TrackRow(
                                label = option.label,
                                selected = option.selected,
                                enabled = option.enabled,
                                onClick = { selectTrackOption(player, C.TRACK_TYPE_TEXT, option) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.player_close), color = PlayerCarmine)
            }
        },
        containerColor = PlayerGraphite,
    )
}

/** Opción de pista ya fusionada: puede apuntar a varios grupos duplicados. */
private data class TrackOption(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean,
    val targets: List<Pair<TrackGroup, Int>>,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun mergeTrackOptions(
    groups: List<Tracks.Group>,
    type: Int,
): List<TrackOption> {
    val byKey = linkedMapOf<String, TrackOption>()
    var order = 0
    groups.filter { it.type == type }.forEach { group ->
        for (i in 0 until group.length) {
            val f = group.getTrackFormat(i)
            // El id suele variar entre grupos del mismo stream lógico (HLS/DVB);
            // la clave es la identidad visible para el usuario.
            val key = "${f.language}|${f.label}|${f.sampleMimeType}|${f.codecs}|${f.channelCount}|${f.roleFlags}|${f.selectionFlags}"
            val existing = byKey[key]
            if (existing == null) {
                order++
                byKey[key] = TrackOption(
                    label = trackLabel(f, order),
                    selected = group.isTrackSelected(i),
                    enabled = group.isTrackSupported(i),
                    targets = listOf(group.mediaTrackGroup to i),
                )
            } else {
                byKey[key] = existing.copy(
                    selected = existing.selected || group.isTrackSelected(i),
                    enabled = existing.enabled || group.isTrackSupported(i),
                    targets = existing.targets + (group.mediaTrackGroup to i),
                )
            }
        }
    }
    return byKey.values.toList()
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun selectTrackOption(player: Player?, type: Int, option: TrackOption) {
    val builder = player?.trackSelectionParameters?.buildUpon()
        ?.setTrackTypeDisabled(type, false)
        ?.clearOverridesOfType(type)
        ?: return
    option.targets.forEach { (group, index) ->
        builder.setOverrideForType(TrackSelectionOverride(group, index))
    }
    player.trackSelectionParameters = builder.build()
}

/** Etiqueta legible: evita IDs tipo "1/8219" (DVB) cuando hay idioma. */
private fun trackLabel(format: Format, fallbackIndex: Int): String =
    format.label?.takeIf { it.any(Char::isLetter) }
        ?: format.language?.let {
            runCatching { java.util.Locale(it).displayLanguage }.getOrNull()
        }
        ?: format.label
        ?: format.id
        ?: "#$fallbackIndex"

@Composable
private fun TrackSectionHeader(titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        color = PlayerCarmine,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Diálogo del temporizador de apagado: pausa la reproducción al cumplirse. */
@Composable
internal fun SleepTimerDialog(
    endAtMs: Long?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(15, 30, 45, 60, 90)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_sleep_timer), color = Color.White) },
        text = {
            Column {
                if (endAtMs != null) {
                    val remaining = ((endAtMs - System.currentTimeMillis()) / 60_000L)
                        .coerceAtLeast(1L)
                    Text(
                        stringResource(R.string.player_sleep_active_in, remaining),
                        color = PlayerMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                options.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_sleep_minutes, minutes),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (endAtMs != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(null) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.player_sleep_off),
                            color = PlayerCarmine,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.player_close), color = PlayerCarmine)
            }
        },
        containerColor = PlayerGraphite,
    )
}

@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else PlayerMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = PlayerCarmine,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
