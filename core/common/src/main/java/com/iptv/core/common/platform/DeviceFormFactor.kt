package com.iptv.core.common.platform

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

/**
 * True cuando la app corre en un dispositivo de salón (Android TV,
 * Google TV, Fire TV, proyectores en modo televisor). Se resuelve una vez
 * por arranque en la Activity; el modo no cambia en caliente.
 */
fun Context.isTelevision(): Boolean =
    (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
        ?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
