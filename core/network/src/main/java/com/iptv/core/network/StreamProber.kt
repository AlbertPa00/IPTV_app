package com.iptv.core.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Probes the first bytes of a stream to learn what the server actually
 * serves. IPTV endpoints routinely lie: `.m3u8` URLs that return raw
 * MPEG-TS when the panel has HLS output disabled, extensionless URLs that
 * serve TS, `application/octet-stream` Content-Types, etc. The URL's
 * extension and the Content-Type header are only hints; the payload
 * prefix is the truth.
 */
object StreamProber {

    /** MIME declarable a un player; null cuando el payload no identifica nada.
     *  (strings espejo de androidx.media3.common.MimeTypes; network no
     *  depende de media3). */
    enum class Kind(val mimeType: String?) {
        HLS("application/x-mpegURL"),
        TS("video/mp2t"),
        MP4("video/mp4"),
        DASH("application/dash+xml"),
        UNKNOWN(null),
    }

    /**
     * Resultado completo del sondeo. [hlsFmp4] indica que el playlist HLS
     * usa segmentos fMP4/CMAF (#EXT-X-MAP, .m4s…): el receptor Cast debe
     * declararlo así o demultiplexa esperando MPEG-TS.
     */
    data class ProbeResult(val kind: Kind, val hlsFmp4: Boolean = false)

    private const val SNIFF_BYTES = 512
    private const val PLAYLIST_SNIFF_BYTES = 16 * 1024
    private const val TS_SYNC: Byte = 0x47

    /**
     * Resultados por (url, UA, referer): re-sondear la misma URL repetiría la
     * petición en vano (zapping Cast, reintentos locales). Vive en el objeto,
     * es decir en el proceso; cada entrada pesa ~100 bytes por URL.
     */
    private val cache = java.util.concurrent.ConcurrentHashMap<Triple<String, String, String>, ProbeResult>()

    /**
     * Fetches the first bytes of [url] and sniffs the payload.
     * Never throws; returns [Kind.UNKNOWN] on any failure.
     */
    fun probe(client: OkHttpClient, url: String, userAgent: String, referrer: String?): Kind =
        probeDetailed(client, url, userAgent, referrer).kind

    /**
     * Igual que [probe] pero devuelve el [ProbeResult] completo (p. ej. el
     * flag fMP4 que el receptor Cast necesita para demultiplexar el HLS).
     */
    fun probeDetailed(
        client: OkHttpClient,
        url: String,
        userAgent: String,
        referrer: String?,
    ): ProbeResult {
        val key = Triple(url, userAgent, referrer.orEmpty())
        return cache[key] ?: doProbe(client, url, userAgent, referrer).also { result ->
            // UNKNOWN suele ser fallo transitorio (timeout, red caída): no se
            // cachea para que el próximo intento sondee de verdad. Techo: una
            // lista de 50k canales no puede inflar el mapa sin fin.
            if (result.kind != Kind.UNKNOWN) {
                if (cache.size >= MAX_CACHE) cache.clear()
                cache[key] = result
            }
        }
    }

    private fun doProbe(
        client: OkHttpClient,
        url: String,
        userAgent: String,
        referrer: String?,
    ): ProbeResult = runCatching {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-4095")
            .apply { referrer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .build()
        // Timeouts cortos propios del sondeo (fail fast ante paneles
        // caídos) compartiendo el pool del cliente de la app.
        client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
            .newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Probe HTTP ${response.code} for ${redactUrl(url)}")
                return@use ProbeResult(Kind.UNKNOWN)
            }
            val stream = response.body!!.byteStream()
            val head = ByteArray(SNIFF_BYTES)
            val n = readUpTo(stream, head)
            var sniffed = sniffPayload(head, n)
            if (sniffed == Kind.UNKNOWN) {
                sniffed = kindFromContentType(response.header("Content-Type"))
            }
            // Las playlists son texto pequeño: se leen unos KB más para
            // detectar segmentos fMP4 — el receptor Cast los necesita
            // declarados o los interpreta como MPEG-TS y la carga falla.
            val fmp4 = sniffed == Kind.HLS && detectFmp4Playlist(stream, head, n)
            ProbeResult(sniffed, fmp4)
        }
    }.onFailure { Log.w(TAG, "Probe failed for ${redactUrl(url)}", it) }
        .getOrDefault(ProbeResult(Kind.UNKNOWN))

    /**
     * Sniffs an already-read payload prefix — same logic the proxy uses to
     * decide whether a response is really a playlist.
     */
    fun sniffPayload(head: ByteArray, n: Int): Kind = when {
        n >= 1 && head[0] == TS_SYNC -> Kind.TS
        n >= 8 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte() -> Kind.MP4
        else -> {
            val text = String(head, 0, n, Charsets.UTF_8)
                .trimStart { it == '\uFEFF' || it.isWhitespace() }
            when {
                text.startsWith("#EXTM3U") -> Kind.HLS
                text.startsWith("<MPD") || text.startsWith("<?xml") && text.contains("<MPD") -> Kind.DASH
                else -> Kind.UNKNOWN
            }
        }
    }

    /**
     * Continúa leyendo el playlist (acotado a [PLAYLIST_SNIFF_BYTES]) y
     * busca marcas de segmentos fMP4. Acumular el texto completo cubre las
     * marcas que queden a caballo entre lecturas.
     */
    private fun detectFmp4Playlist(stream: InputStream, head: ByteArray, headLen: Int): Boolean {
        val text = StringBuilder().append(String(head, 0, headLen, Charsets.UTF_8))
        // Content-Type puede mentir y declarar playlist sobre binario: sin
        // la marca EXTM3U no hay playlist que inspeccionar.
        if (!text.toString().trimStart { it == '\uFEFF' || it.isWhitespace() }.startsWith("#EXTM3U")) {
            return false
        }
        if (looksLikeFmp4Hls(text)) return true
        val buf = ByteArray(2048)
        var total = headLen
        return try {
            while (total < PLAYLIST_SNIFF_BYTES) {
                val n = stream.read(buf, 0, minOf(buf.size, PLAYLIST_SNIFF_BYTES - total))
                if (n < 0) break
                text.append(String(buf, 0, n, Charsets.UTF_8))
                if (looksLikeFmp4Hls(text)) return true
                total += n
            }
            false
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Marcas de un media playlist con segmentos fMP4/CMAF: `#EXT-X-MAP`
     * (init map, siempre fMP4) o líneas de segmento .m4s/.cmfv/.cmfa. Las
     * master playlists no declaran formato — devuelven false (se asume TS
     * como hasta ahora; la alternativa exigiría sondear una variante).
     */
    fun looksLikeFmp4Hls(manifest: CharSequence): Boolean =
        manifest.contains("#EXT-X-MAP") || FMP4_SEGMENT_LINE.containsMatchIn(manifest)

    private val FMP4_SEGMENT_LINE =
        Regex("""^[^#\s].*\.(m4s|cmfv|cmfa)(\?[^\s]*)?$""", RegexOption.MULTILINE)

    private fun kindFromContentType(contentType: String?): Kind = when {
        contentType == null -> Kind.UNKNOWN
        contentType.contains("mpegurl") -> Kind.HLS
        contentType.contains("mp2t") || contentType.contains("mpeg") -> Kind.TS
        contentType.contains("dash") -> Kind.DASH
        contentType.contains("mp4") -> Kind.MP4
        else -> Kind.UNKNOWN
    }

    private fun readUpTo(stream: InputStream, buf: ByteArray): Int {
        var total = 0
        return try {
            while (total < buf.size) {
                val n = stream.read(buf, total, buf.size - total)
                if (n < 0) break
                total += n
                // 188 bytes = one full TS packet; enough for every signature.
                if (total >= 188) break
            }
            total
        } catch (_: SocketTimeoutException) {
            total
        } catch (_: IOException) {
            total
        }
    }

    private const val TAG = "StreamProber"
    private const val MAX_CACHE = 512
}
