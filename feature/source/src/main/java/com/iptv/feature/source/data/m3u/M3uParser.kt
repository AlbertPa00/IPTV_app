package com.iptv.feature.source.data.m3u

/**
 * Canal resultante de parsear una lista M3U/M3U8.
 */
data class ParsedChannel(
    val name: String,
    val url: String = "",
    val tvgId: String? = null,
    val tvgName: String? = null,
    val tvgLogo: String? = null,
    val groupTitle: String? = null,
    val tvgCountry: String? = null,
    val tvgLanguage: String? = null,
    val contentType: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val containerExt: String? = null,
    val sortOrder: Int = 0,
)

/**
 * Parser M3U en streaming: procesa línea a línea sin cargar la lista en memoria
 * y expone los canales como [Sequence] para poder insertarlos por lotes.
 *
 * Soporta: atributos estándar (tvg-id, tvg-name, tvg-logo, group-title),
 * #EXTGRP, #EXTVLCOPT (http-user-agent, http-referrer), BOM UTF-8 y
 * nombres con comas o comillas.
 */
class M3uParser {

    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(
        reader: java.io.BufferedReader,
        onEpgUrl: (String) -> Unit = {},
    ): Sequence<ParsedChannel> = sequence {
        var pending: ParsedChannel? = null
        var order = 0
        var firstLine = true
        while (true) {
            val raw = reader.readLine() ?: break
            var line = raw.trim()
            if (firstLine) {
                firstLine = false
                if (line.startsWith(BOM)) line = line.removePrefix(BOM).trim()
            }
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    (attrs["url-tvg"] ?: attrs["x-tvg-url"])
                        ?.split(',')
                        ?.firstOrNull { it.isNotBlank() }
                        ?.trim()
                        ?.let(onEpgUrl)
                }

                line.startsWith("#EXTINF", ignoreCase = true) ->
                    pending = parseExtInf(line).copy(sortOrder = order++)

                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    pending = pending?.copy(groupTitle = line.substringAfter(':').trim())

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    when {
                        opt.startsWith("http-user-agent=", ignoreCase = true) ->
                            pending = pending?.copy(userAgent = opt.substringAfter('=').trim())

                        opt.startsWith("http-referrer=", ignoreCase = true) ->
                            pending = pending?.copy(referrer = opt.substringAfter('=').trim())
                    }
                }

                line.startsWith("#") -> Unit // directiva desconocida: se ignora

                else -> {
                    val entry = pending?.copy(url = line, containerExt = extOf(line))
                    if (entry != null && entry.url.isNotBlank() && entry.name.isNotBlank()) {
                        yield(entry)
                    }
                    pending = null
                }
            }
        }
    }

    private fun parseExtInf(line: String): ParsedChannel {
        val attrs = attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return ParsedChannel(
            name = extractName(line),
            tvgId = attrs["tvg-id"],
            tvgName = attrs["tvg-name"],
            tvgLogo = attrs["tvg-logo"],
            groupTitle = attrs["group-title"],
            tvgCountry = attrs["tvg-country"],
            tvgLanguage = attrs["tvg-language"],
            contentType = attrs["tvg-type"] ?: attrs["media-type"] ?: attrs["type"],
        )
    }

    /**
     * El nombre va tras la última coma que queda fuera de los atributos entrecomillados.
     * Si no hay atributos, es todo lo que sigue a la primera coma.
     */
    private fun extractName(line: String): String {
        val lastQuote = line.lastIndexOf('"')
        val commaIdx = if (lastQuote >= 0) line.indexOf(',', lastQuote) else line.indexOf(',')
        return if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else ""
    }

    /** Extensión del contenedor según el último segmento de la ruta (ej. .ts, .m3u8, .mp4). */
    private fun extOf(url: String): String? {
        val file = url.substringBefore('?').substringAfterLast('/')
        val ext = file.substringAfterLast('.', "").lowercase()
        return ext.takeIf { it.length in 1..4 && it.all { c -> c.isLetterOrDigit() } }
    }

    private companion object {
        const val BOM = "\uFEFF"
    }
}
