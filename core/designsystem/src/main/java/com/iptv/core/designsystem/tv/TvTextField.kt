package com.iptv.core.designsystem.tv

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.iptv.core.designsystem.R

/**
 * Campo de texto consciente del factor: en táctil es un [OutlinedTextField]
 * normal; en TV es una fila enfocable que sólo abre un diálogo de edición al
 * pulsar OK — evita que el teclado en pantalla salte al navegar con el mando
 * (un TextField enfocado en Android TV invoca el IME por sí mismo). En el
 * diálogo el IME al enfocar sí es el comportamiento esperado.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    if (!LocalIsTv.current) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    var editing by remember { mutableStateOf(false) }
    val masked = visualTransformation != VisualTransformation.None
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f)
            .tvFocusAffordance(shape = RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { editing = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    value.isEmpty() -> label
                    masked -> "\u2022".repeat(value.length)
                    else -> value
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (value.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        trailingIcon?.let {
            Spacer(Modifier.size(8.dp))
            it()
        }
    }

    if (editing) {
        var draft by remember { mutableStateOf(value) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    trailingIcon = trailingIcon,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onValueChange(draft); editing = false },
                    modifier = Modifier.tvFocusAffordance(),
                ) {
                    Text(stringResource(R.string.ds_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editing = false },
                    modifier = Modifier.tvFocusAffordance(),
                ) {
                    Text(stringResource(R.string.ds_cancel))
                }
            },
        )
    }
}
