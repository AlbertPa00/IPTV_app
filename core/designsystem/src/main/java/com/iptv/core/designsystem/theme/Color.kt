package com.iptv.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// Marca: azul profundo + acento turquesa.
val Blue10 = Color(0xFF001B44)
val Blue20 = Color(0xFF00306F)
val Blue40 = Color(0xFF325CA8)
val Blue80 = Color(0xFFAEC6FF)
val Blue90 = Color(0xFFD8E2FF)

val Teal30 = Color(0xFF00696E)
val Teal40 = Color(0xFF00A1A7)
val Teal80 = Color(0xFF4FD8DE)
val Teal90 = Color(0xFFB0F0F2)

val Neutral10 = Color(0xFF1A1C1F)
val Neutral20 = Color(0xFF2F3135)
val Neutral90 = Color(0xFFE2E2E6)
val Neutral95 = Color(0xFFF1F0F7)

val ErrorDark = Color(0xFFFFB4AB)
val ErrorLight = Color(0xFFBA1A1A)

val LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal30,
    background = Neutral95,
    onBackground = Neutral10,
    surface = Color.White,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral20,
    error = ErrorLight,
)

val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Blue80,
    onPrimary = Blue10,
    primaryContainer = Blue20,
    onPrimaryContainer = Blue90,
    secondary = Teal80,
    onSecondary = Color(0xFF003739),
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral20,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral90,
    error = ErrorDark,
)
