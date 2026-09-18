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

// Identidad "cine" del tema oscuro: fondo casi negro, superficies grafito y
// acento carmín. Es la paleta única de la app (catálogo, reproductor, EPG).
// El carmín se apaga un punto respecto al rojo Netflix puro (#E50914): sobre
// negro OLED el rojo saturado vibraba; #DC2626 sigue leyéndose rojo.
val Carmine = Color(0xFFDC2626)
val CinemaBlack = Color(0xFF08090B)
val Graphite = Color(0xFF15171B)
val GraphiteLight = Color(0xFF22252B)
val MutedText = Color(0xFFB6B8BE)

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
    primary = Carmine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A0A10),
    onPrimaryContainer = Color.White,
    secondary = MutedText,
    onSecondary = CinemaBlack,
    secondaryContainer = GraphiteLight,
    onSecondaryContainer = Color.White,
    tertiary = Carmine,
    tertiaryContainer = Color(0xFF3D1115),
    onTertiaryContainer = Color.White,
    background = CinemaBlack,
    onBackground = Color.White,
    surface = CinemaBlack,
    onSurface = Color.White,
    surfaceVariant = Graphite,
    onSurfaceVariant = MutedText,
    surfaceContainer = Graphite,
    surfaceContainerHigh = GraphiteLight,
    outline = Color(0xFF6E7278),
    error = ErrorDark,
)
