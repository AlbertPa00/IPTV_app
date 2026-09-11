package com.iptv.feature.player.cast

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

/**
 * Local HTTP proxy that forwards IPTV streams to Chromecast with proper
 * headers and CORS. This mirrors the approach VLC uses: instead of sending
 * the IPTV URL directly to the Chromecast (which fails due to CORS, auth
 * headers, and format issues), we run a local HTTP server that the
 * Chromecast connects to.
 *
 * The proxy:
 *  - Forwards User-Agent and Referer headers from the source
 *  - Adds CORS headers so the Cast receiver can access the content
 *  - Rewrites HLS manifest segment URLs to route through the proxy using
 *    the LAN address the Chromecast actually reaches
 *  - Streams the response back without buffering the entire content
 *
 * Target URLs travel inside the proxy path as URL-safe Base64. Earlier
 * versions used form-encoding, which was decoded twice (once by NanoHTTPD,
 * once by us) and corrupted signed URLs containing '%' or '+'.
 */
class CastProxyServer(
    private val userAgent: String,
    private val referrer: String?,
    private val advertisedHost: String,
    private val workDir: java.io.File,
) {
    private var server: ProxyServer? = null
    private var port: Int = 0

    /**
     * Random per-session path prefix. The proxy listens on all interfaces so
     * the Chromecast can reach it; without a secret anyone on the LAN could
     * use the phone as an open HTTP proxy while a cast session is alive.
     */
    private val secret: String = java.security.SecureRandom().let { random ->
        ByteArray(12).also(random::nextBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun start(): Int {
        val s = ProxyServer(0, userAgent, referrer, advertisedHost, workDir, secret)
        s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server = s
        port = s.listeningPort
        Log.d(TAG, "Proxy started on $advertisedHost:$port")
        return port
    }

    fun stop() {
        server?.shutdownSessions()
        server?.stop()
        server = null
        Log.d(TAG, "Proxy stopped")
    }

    /** Whether this running instance already serves the given credentials. */
    fun handles(userAgent: String, referrer: String?): Boolean =
        this.userAgent == userAgent && this.referrer == referrer

    /**
     * Returns the proxy URL for the given original URL.
     * The Chromecast will connect to this URL instead of the original.
     */
    fun proxyUrl(originalUrl: String): String {
        if (advertisedHost.isBlank() || port == 0) return originalUrl
        val token = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(originalUrl.toByteArray(Charsets.UTF_8))
        return "http://$advertisedHost:$port/$secret/proxy/$token"
    }

    /**
     * Synthetic live-HLS endpoint for raw MPEG-TS streams. The Default Media
     * Receiver cannot play progressive video/mp2t, so the proxy re-segments
     * the upstream into a live playlist instead.
     *
     * [mode]: "n" = in-memory packet cutter, "r" = ffmpeg remux (copy),
     * "t" = ffmpeg remux with audio transcoded to AAC.
     */
    fun hlsUrl(originalUrl: String, mode: String): String {
        if (advertisedHost.isBlank() || port == 0) return originalUrl
        val token = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$mode|$originalUrl".toByteArray(Charsets.UTF_8))
        return "http://$advertisedHost:$port/$secret/hls/$token"
    }

    companion object {
        private const val TAG = "CastProxy"

        /**
         * Picks the local IPv4 that routes to [target] (the Cast device), so the
         * advertised address is always on a subnet the Chromecast can reach.
         * Falls back to the first usable interface when no route is known.
         */
        fun selectLocalAddress(target: InetAddress?): String? {
            if (target != null) {
                val routed = runCatching {
                    DatagramSocket().use { socket ->
                        socket.connect(target, 9)
                        socket.localAddress
                    }
                }.getOrNull()
                if (routed != null && !routed.isAnyLocalAddress && !routed.isLoopbackAddress) {
                    return routed.hostAddress
                }
            }
            return firstAvailableIpv4()
        }

        private fun firstAvailableIpv4(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces()
                    .toList()
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLinkLocalAddress }
                    ?.hostAddress
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get local IP", e)
                null
            }
        }
    }
}

private class ProxyServer(
    port: Int,
    private val userAgent: String,
    private val referrer: String?,
    private val advertisedHost: String,
    private val workDir: java.io.File,
    private val secret: String,
) : NanoHTTPD(port) {

    // Streaming client: infinite read timeout so live segments and VOD
    // downloads are never cut off mid-stream.
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Manifest client: bounded read timeout so a live HLS playlist that
    // keeps the connection open without closing it doesn't hang the proxy
    // forever. Manifests are small (< 256 KB), so 10 s is plenty.
    private val manifestClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val hlsSessions = java.util.concurrent.ConcurrentHashMap<String, LiveHlsSource>()

    fun shutdownSessions() {
        hlsSessions.values.forEach { it.close() }
        hlsSessions.clear()
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
        }
        val uri = session.uri
        Log.d(TAG, "${session.method} $uri")
        // Everything except CORS preflights lives under the random
        // per-session secret; unknown paths get a flat 404.
        val path = uri.removePrefix("/$secret")
        if (path == uri || !path.startsWith("/")) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
        val routed = path
        return try {
            when {
                routed.startsWith(HLS_PREFIX) -> serveHlsPlaylist(routed.removePrefix(HLS_PREFIX))
                routed.startsWith(SEG_PREFIX) -> serveHlsSegment(routed.removePrefix(SEG_PREFIX))
                routed.startsWith(PROXY_PREFIX) -> {
                    val targetUrl = decodeTarget(routed.removePrefix(PROXY_PREFIX))
                    if (targetUrl == null) {
                        newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad proxy token")
                    } else {
                        proxyRequest(targetUrl, session)
                    }
                }
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proxy error for $uri", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Proxy error: ${e.message}")
        }
    }

    // -- Synthetic HLS for raw TS live streams ----------------------------

    private fun serveHlsPlaylist(token: String): Response {
        val payload = decodeTarget(token)
            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad token")
        // Token payload is "mode|url"; no mode prefix means the legacy
        // in-memory segmenter.
        val mode = if (payload.length > 1 && payload[1] == '|') payload[0] else 'n'
        val targetUrl = if (payload.length > 1 && payload[1] == '|') payload.substring(2) else payload
        // One channel per cast session: a new playlist means the receiver
        // zapped — drop the other sessions and their upstream connections.
        hlsSessions.entries.removeIf { (key, s) -> key != token && s.close().let { true } }
        val hls = hlsSessions.getOrPut(token) {
            createHlsSource(mode, targetUrl, token).also { it.start() }
        }
        hls.lastAccessMs = System.currentTimeMillis()
        // Give the segmenter a moment to produce the first segment so the
        // receiver's initial playlist isn't empty.
        hls.awaitFirstSegment(10_000)
        hls.failure?.let {
            return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Upstream failed: $it")
        }
        val segmentBase = "http://$advertisedHost:$listeningPort/$secret$SEG_PREFIX$token"
        val playlist = hls.playlist(segmentBase)
        return withCors(
            newFixedLengthResponse(Response.Status.OK, HLS_CONTENT_TYPE, playlist).apply {
                addHeader("Cache-Control", "no-store")
            },
        )
    }

    private fun createHlsSource(mode: Char, targetUrl: String, token: String): LiveHlsSource =
        when (mode) {
            'r', 't' -> FfmpegHlsSession(
                targetUrl, userAgent, referrer,
                workDir = java.io.File(workDir, "hls_" + token.hashCode().toString(16)),
                transcodeAudio = mode == 't',
            )
            else -> LiveTsHlsSession(targetUrl, userAgent, referrer)
        }

    private fun serveHlsSegment(path: String): Response {
        val slash = path.lastIndexOf('/')
        if (slash < 0) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Bad segment path")
        }
        val token = path.substring(0, slash)
        val name = path.substring(slash + 1)
        val hls = hlsSessions[token]
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Unknown session")
        hls.lastAccessMs = System.currentTimeMillis()
        val payload = hls.openSegment(name)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Segment gone")
        return withCors(
            newFixedLengthResponse(Response.Status.OK, "video/mp2t", payload.first, payload.second),
        )
    }

    private fun decodeTarget(token: String): String? = runCatching {
        val padding = (4 - token.length % 4) % 4
        String(
            java.util.Base64.getUrlDecoder().decode(token + "=".repeat(padding)),
            Charsets.UTF_8,
        )
    }.getOrNull()

    private fun proxyRequest(targetUrl: String, session: IHTTPSession): Response {
        val path = targetUrl.substringBefore('?').substringBefore('#').lowercase()
        val looksLikeManifest = path.endsWith(".m3u8") || path.endsWith(".m3u")
        // Manifests use the bounded-timeout client so a live playlist that
        // keeps the connection open doesn't hang the proxy. Segments and
        // progressive downloads use the streaming client with infinite
        // read timeout.
        val http = if (looksLikeManifest) manifestClient else client

        val builder = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", userAgent)
            .header("Connection", "keep-alive")

        if (session.method == Method.HEAD) builder.head()
        referrer?.takeIf { it.isNotBlank() }?.let { builder.header("Referer", it) }
        session.headers["range"]?.let { builder.header("Range", it) }

        val response = http.newCall(builder.build()).execute()
        val body = response.body ?: return newFixedLengthResponse(
            Response.Status.INTERNAL_ERROR, "text/plain", "Empty response from server",
        )

        val upstreamType = response.header("Content-Type")
        val contentType = upstreamType ?: guessContentType(targetUrl)
        val status = Response.Status.lookup(response.code) ?: Response.Status.INTERNAL_ERROR

        // Only rewrite the body when it is genuinely an HLS playlist — Xtream
        // panels without HLS output serve raw MPEG-TS at .m3u8 URLs, and
        // mangling binary bytes as text corrupts the stream.
        val possibleManifest = looksLikeManifest || upstreamType?.contains("mpegurl") == true

        val proxiedResponse = if (response.isSuccessful && possibleManifest) {
            val buffered = java.io.BufferedInputStream(body.byteStream(), PEEK_BUFFER_BYTES)
            buffered.mark(PEEK_BUFFER_BYTES)
            val head = ByteArray(PEEK_BUFFER_BYTES)
            val headLen = runCatching { buffered.read(head) }.getOrDefault(-1)
            buffered.reset()

            when (CastStreamProber.sniffPayload(head, maxOf(headLen, 0))) {
                CastStreamProber.Kind.HLS -> {
                    val manifestContent = readBoundedString(buffered, MAX_MANIFEST_BYTES)
                    if (manifestContent == null) {
                        response.close()
                        newFixedLengthResponse(
                            Response.Status.SERVICE_UNAVAILABLE,
                            "text/plain",
                            "Manifest read timed out",
                        )
                    } else {
                        val rewritten = rewriteHlsManifest(
                            manifestContent,
                            response.request.url.toString(),
                        )
                        response.close()
                        newFixedLengthResponse(Response.Status.OK, HLS_CONTENT_TYPE, rewritten)
                    }
                }

                CastStreamProber.Kind.TS ->
                    newChunkedResponse(status, "video/mp2t", buffered)

                CastStreamProber.Kind.MP4 ->
                    newChunkedResponse(status, "video/mp4", buffered)

                CastStreamProber.Kind.DASH ->
                    newChunkedResponse(status, "application/dash+xml", buffered)

                CastStreamProber.Kind.UNKNOWN -> {
                    // Mislabeled or empty manifest: pass bytes through as-is.
                    newChunkedResponse(status, contentType, buffered)
                }
            }
        } else {
            val stream: InputStream = body.byteStream()
            val contentLength = body.contentLength()
            if (contentLength > 0) {
                newFixedLengthResponse(status, contentType, stream, contentLength)
            } else {
                newChunkedResponse(status, contentType, stream)
            }
        }

        response.header("Content-Range")?.let { proxiedResponse.addHeader("Content-Range", it) }
        response.header("Accept-Ranges")?.let { proxiedResponse.addHeader("Accept-Ranges", it) }

        return withCors(proxiedResponse)
    }

    /**
     * Reads up to [maxBytes] from [stream] as UTF-8 text. Returns null if the
     * stream blocks beyond the manifest client's read timeout (the OkHttp
     * call will have already thrown, but this is a secondary guard).
     */
    private fun readBoundedString(stream: InputStream, maxBytes: Int): String? {
        val baos = java.io.ByteArrayOutputStream(maxBytes)
        val buf = ByteArray(8192)
        var total = 0
        return try {
            while (total < maxBytes) {
                val n = stream.read(buf, 0, minOf(buf.size, maxBytes - total))
                if (n < 0) break
                baos.write(buf, 0, n)
                total += n
            }
            baos.toString("UTF-8")
        } catch (e: java.net.SocketTimeoutException) {
            // Server sent the playlist but keeps the socket open without EOF:
            // serve what we already have if it parses as a playlist.
            val partial = baos.toString("UTF-8")
            if (partial.trimStart { it == '﻿' || it.isWhitespace() }.startsWith("#EXTM3U")) {
                Log.w(TAG, "Manifest read timed out; serving ${partial.length} bytes anyway")
                partial
            } else {
                Log.w(TAG, "Manifest read timed out with no playlist content", e)
                null
            }
        }
    }

    private fun withCors(response: Response): Response = response.apply {
        addHeader("Access-Control-Allow-Origin", "*")
        addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
        addHeader("Access-Control-Expose-Headers", "Content-Range, Accept-Ranges, Content-Length")
    }

    /**
     * Rewrites HLS manifest URLs to route through the local proxy. Handles
     * absolute, host-relative and relative URIs, plus URI= attributes in
     * #EXT-X-KEY / #EXT-X-MAP / #EXT-X-MEDIA tags.
     *
     * IMPORTANT: the advertised host must be the phone's LAN address — the
     * manifest is parsed by the Chromecast, so "localhost" would point at
     * the TV itself and every segment fetch would fail.
     */
    private fun rewriteHlsManifest(manifest: String, manifestUrl: String): String {
        val baseUrl = manifestUrl.substringBeforeLast('/')
        val host = "http://$advertisedHost:${this.listeningPort}/$secret"
        val lines = manifest.lines().map { line ->
            val trimmed = line.trim()
            when {
                // The Cast receiver requires BANDWIDTH on variant streams; many
                // IPTV playlists omit it, so we inject a sane default.
                trimmed.startsWith("#EXT-X-STREAM-INF:") && !trimmed.contains("BANDWIDTH=") ->
                    "$trimmed,BANDWIDTH=1500000"

                trimmed.isEmpty() || trimmed.startsWith("#") ->
                    if (trimmed.contains("URI=\"")) rewriteUriAttribute(trimmed, baseUrl, host) else line

                trimmed.startsWith("http://") || trimmed.startsWith("https://") ->
                    "$host/proxy/${encodeTarget(trimmed)}"

                trimmed.startsWith("/") -> {
                    val absoluteUrl = baseUrl.substringBefore("://") + "://" +
                        baseUrl.substringAfter("://").substringBefore('/') + trimmed
                    "$host/proxy/${encodeTarget(absoluteUrl)}"
                }

                else -> {
                    val absoluteUrl = "$baseUrl/$trimmed"
                    "$host/proxy/${encodeTarget(absoluteUrl)}"
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun rewriteUriAttribute(line: String, baseUrl: String, host: String): String {
        val regex = Regex("URI=\"([^\"]+)\"")
        return regex.replace(line) { match ->
            val uri = match.groupValues[1]
            val absoluteUri = when {
                uri.startsWith("http://") || uri.startsWith("https://") -> uri
                uri.startsWith("/") -> baseUrl.substringBefore("://") + "://" +
                    baseUrl.substringAfter("://").substringBefore('/') + uri
                else -> "$baseUrl/$uri"
            }
            "URI=\"$host/proxy/${encodeTarget(absoluteUri)}\""
        }
    }

    private fun encodeTarget(url: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(url.toByteArray(Charsets.UTF_8))

    private fun guessContentType(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> "application/vnd.apple.mpegurl"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mpd") -> "application/dash+xml"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        const val TAG = "CastProxy"
        const val PROXY_PREFIX = "/proxy/"
        const val HLS_PREFIX = "/hls/"
        const val SEG_PREFIX = "/seg/"
        const val HLS_CONTENT_TYPE = "application/x-mpegURL"
        const val MAX_MANIFEST_BYTES = 1_048_576 // 1 MB — manifests are tiny
        const val PEEK_BUFFER_BYTES = 512
    }
}
