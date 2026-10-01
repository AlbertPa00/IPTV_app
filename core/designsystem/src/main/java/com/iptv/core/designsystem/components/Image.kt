package com.iptv.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Imagen de red con el tratamiento común de la app: fundido de entrada y
 * marcador grafito mientras carga o si la URL falla. Sin él los pósters y
 * logos aparecían de golpe o dejaban huecos vacíos.
 *
 * [onError] permite al llamador sustituir el marcador por un fallback con
 * más significado (p. ej. el icono de play en los logos de canal).
 */
@Composable
fun IptvAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
) {
    val placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(model)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        onError = onError,
        modifier = modifier,
    )
}
