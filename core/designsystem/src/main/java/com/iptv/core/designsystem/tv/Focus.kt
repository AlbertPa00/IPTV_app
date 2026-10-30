package com.iptv.core.designsystem.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Elemento interactivo con affordance de foco para D-pad: escala y borde
 * primario al recibir el foco. En pantallas táctiles el borde/escala
 * nunca se activan (el foco no se mueve por clicks), así que es un no-op
 * barato que permite reutilizar el mismo componente en ambos factores.
 *
 * Con [onClick] el elemento es clickable (y por tanto enfocable); sin él
 * sólo toma el foco.
 */
fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1.08f,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        label = "tvFocusScale",
    )
    val borderColor = MaterialTheme.colorScheme.primary
    this
        .scale(scale)
        .onFocusChanged { focused = it.isFocused }
        .border(
            width = 2.dp,
            color = if (focused) borderColor else Color.Transparent,
            shape = shape,
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier.focusable()
            },
        )
}

/**
 * Affordance de foco puramente visual para elementos que YA son
 * interactivos (clickable/combinedClickable, chips, botones): dibuja
 * escala y borde cuando el nodo o su subárbol tiene el foco, sin crear un
 * segundo objetivo de foco (un `focusable()` extra robaría el CENTER al
 * clickable real). No-op en táctil.
 */
fun Modifier.tvFocusAffordance(
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = 1.05f,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        label = "tvAffordanceScale",
    )
    val borderColor = MaterialTheme.colorScheme.primary
    this
        .scale(scale)
        .onFocusChanged { focused = it.hasFocus }
        .border(
            width = 2.dp,
            color = if (focused) borderColor else Color.Transparent,
            shape = shape,
        )
}

/**
 * Margen de seguridad de overscan: los televisores recortan los bordes de
 * la señal (~5 %), así que el contenido interactivo de TV no debe tocar
 * el perímetro. Aplicar en la raíz de overlays y pantallas de TV.
 */
fun Modifier.tvSafeArea(): Modifier = padding(horizontal = 48.dp, vertical = 24.dp)
