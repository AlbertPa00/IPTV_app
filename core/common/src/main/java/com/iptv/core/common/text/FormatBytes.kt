package com.iptv.core.common.text

import java.util.Locale

/** Tamaño legible para ficheros y descargas: "512 KB", "128 MB", "1.4 GB". */
fun formatBytes(bytes: Long): String {
    val locale = Locale.getDefault()
    return when {
        bytes >= 1L shl 30 -> String.format(locale, "%.1f GB", bytes / 1073741824.0)
        bytes >= 1L shl 20 -> String.format(locale, "%.0f MB", bytes / 1048576.0)
        else -> String.format(locale, "%.0f KB", bytes / 1024.0)
    }
}
