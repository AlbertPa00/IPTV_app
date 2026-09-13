package com.iptv.feature.player.cast

import android.net.Uri

/**
 * Las URLs de streams llevan credenciales embebidas (Xtream:
 * `/live/user/pass/…`; M3U: tokens en la query). Para trazas w/e sólo se
 * conserva esquema + host + puerto.
 */
internal fun redactUrl(url: String): String {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return "<url>"
    val scheme = uri.scheme ?: return "<url>"
    return buildString {
        append(scheme).append("://")
        append(uri.host ?: "<host>")
        if (uri.port > 0) append(':').append(uri.port)
        append("/…")
    }
}
