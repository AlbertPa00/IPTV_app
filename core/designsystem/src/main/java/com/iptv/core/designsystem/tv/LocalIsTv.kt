package com.iptv.core.designsystem.tv

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Form factor del dispositivo en ejecución. `true` en televisores: los
 * componentes compartidos lo consultan para decidir si muestran
 * affordances táctiles (hints de gesto, pull-to-refresh) o de mando.
 * Se provee en la raíz (`IptvApp`); por defecto `false` en previews.
 */
val LocalIsTv = staticCompositionLocalOf { false }
