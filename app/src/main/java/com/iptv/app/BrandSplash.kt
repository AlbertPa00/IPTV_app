package com.iptv.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.iptv.core.designsystem.theme.Carmine
import com.iptv.core.designsystem.theme.CinemaBlack
import com.iptv.core.designsystem.theme.IptvTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Variantes de la intro de marca. */
enum class SplashVariant {
    /** Icono: scale+fade con leve rebote y wordmark que sube escalonado. */
    MarkReveal,

    /** Tipográfica: marca pequeña, nombre y línea carmín que se dibuja. */
    Wordmark,

    /** Señal: icono + doble anillo expansivo tipo broadcast, sin texto. */
    Pulse,
}

/**
 * Intro de marca mientras el NavHost decide el destino inicial.
 *
 * DEMO: un toque sobre la pantalla cicla entre las tres [SplashVariant]
 * para compararlas sin recompilar. Al elegir, fijar la variante y quitar
 * el contador/clickable.
 */
@Composable
fun BrandSplash(modifier: Modifier = Modifier) {
    var demoIndex by remember { mutableIntStateOf(0) }
    val variants = SplashVariant.entries
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { demoIndex = (demoIndex + 1) % variants.size },
        contentAlignment = Alignment.Center,
    ) {
        key(demoIndex) {
            when (variants[demoIndex]) {
                SplashVariant.MarkReveal -> SplashMarkReveal()
                SplashVariant.Wordmark -> SplashWordmark()
                SplashVariant.Pulse -> SplashPulse()
            }
        }
    }
}

// -- Variante A: MarkReveal ---------------------------------------------------

@Composable
private fun SplashMarkReveal() {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.72f) }
    val wordAlpha = remember { Animatable(0f) }
    val wordShift = remember { Animatable(16f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch {
            logoScale.animateTo(
                1f,
                spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        delay(160)
        launch { wordAlpha.animateTo(1f, tween(280)) }
        wordShift.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier
                .size(112.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value),
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .offset(y = wordShift.value.dp)
                .alpha(wordAlpha.value),
        ) {
            Wordmark()
        }
    }
}

// -- Variante B: Wordmark -----------------------------------------------------

@Composable
private fun SplashWordmark() {
    val markAlpha = remember { Animatable(0f) }
    val markScale = remember { Animatable(0.8f) }
    val lineProgress = remember { Animatable(0f) }
    val wordAlpha = remember { Animatable(0f) }
    val wordShift = remember { Animatable(10f) }

    LaunchedEffect(Unit) {
        launch { markAlpha.animateTo(1f, tween(240)) }
        launch {
            markScale.animateTo(
                1f,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        delay(120)
        launch { wordAlpha.animateTo(1f, tween(280)) }
        launch { wordShift.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        lineProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .scale(markScale.value)
                .alpha(markAlpha.value),
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .offset(y = wordShift.value.dp)
                .alpha(wordAlpha.value),
        ) {
            Wordmark()
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(120.dp * lineProgress.value)
                .background(Carmine),
        )
    }
}

// -- Variante C: Pulse --------------------------------------------------------

@Composable
private fun SplashPulse() {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.9f) }
    val pulse1 = remember { Animatable(0f) }
    val pulse2 = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoAlpha.animateTo(1f, tween(260)) }
        launch { logoScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        delay(160)
        launch { pulse1.animateTo(1f, tween(680, easing = FastOutSlowInEasing)) }
        delay(150)
        pulse2.animateTo(1f, tween(680, easing = FastOutSlowInEasing))
    }

    Box(contentAlignment = Alignment.Center) {
        PulseRing(pulse1.value)
        PulseRing(pulse2.value)
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier
                .size(104.dp)
                .scale(logoScale.value)
                .alpha(logoAlpha.value),
        )
    }
}

@Composable
private fun PulseRing(progress: Float) {
    if (progress <= 0f) return
    Box(
        Modifier
            .size(104.dp)
            .scale(1f + 1.35f * progress)
            .alpha((1f - progress) * 0.4f)
            .border(2.dp, Carmine, CircleShape),
    )
}

// -- Piezas comunes -------------------------------------------------------------

@Composable
private fun Wordmark() {
    val style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
    Row {
        Text("Cero", color = Color.White, style = style)
        Text("Cast", color = Carmine, style = style)
    }
}

// -- Previews -------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF08090B, widthDp = 360, heightDp = 720)
@Composable
private fun SplashMarkRevealPreview() {
    IptvTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SplashMarkReveal()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090B, widthDp = 360, heightDp = 720)
@Composable
private fun SplashWordmarkPreview() {
    IptvTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SplashWordmark()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090B, widthDp = 360, heightDp = 720)
@Composable
private fun SplashPulsePreview() {
    IptvTheme {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SplashPulse()
        }
    }
}
