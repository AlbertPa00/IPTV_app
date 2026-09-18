package com.iptv.feature.epg.data

import com.iptv.core.common.text.TextNormalizer
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

internal data class XmlTvProgramme(
    val channelKey: String,
    val channelNameNorm: String,
    val title: String,
    val description: String?,
    val startUtc: Long,
    val endUtc: Long,
    val iconUrl: String?,
)

/** Pull parser: only the current XML element is retained in memory. */
internal class XmlTvParser {
    suspend fun parse(input: InputStream, onProgramme: suspend (XmlTvProgramme) -> Unit) {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(input, null)
        }
        val names = hashMapOf<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> parseChannel(parser)?.let { (id, name) -> names[id] = name }
                    "programme" -> parseProgramme(parser, names)?.let { onProgramme(it) }
                }
            }
            event = parser.next()
        }
    }

    private fun parseChannel(parser: XmlPullParser): Pair<String, String>? {
        val id = parser.getAttributeValue(null, "id")?.trim().orEmpty()
        var displayName: String? = null
        val depth = parser.depth
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.next() == XmlPullParser.START_TAG && parser.name == "display-name" && displayName == null) {
                displayName = parser.nextText().trim()
            }
        }
        return if (id.isNotBlank()) id to TextNormalizer.searchKey(displayName ?: id) else null
    }

    private fun parseProgramme(parser: XmlPullParser, names: Map<String, String>): XmlTvProgramme? {
        val channel = parser.getAttributeValue(null, "channel")?.trim().orEmpty()
        val start = parser.getAttributeValue(null, "start")?.let(XmlTvDateParser::parseUtcMillis)
        val end = parser.getAttributeValue(null, "stop")?.let(XmlTvDateParser::parseUtcMillis)
        var title: String? = null
        var description: String? = null
        var icon: String? = null
        val depth = parser.depth
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.next() == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> if (title == null) title = parser.nextText().trim()
                    "desc" -> if (description == null) description = parser.nextText().trim().takeIf(String::isNotBlank)
                    "icon" -> if (icon == null) icon = parser.getAttributeValue(null, "src")?.trim()?.takeIf(String::isNotBlank)
                }
            }
        }
        return if (channel.isNotBlank() && start != null && end != null && end > start && !title.isNullOrBlank()) {
            XmlTvProgramme(channel, names[channel] ?: TextNormalizer.searchKey(channel), title, description, start, end, icon)
        } else null
    }
}

internal object XmlTvDateParser {
    /**
     * "yyyyMMddHHmmss ±HHMM" parseado a mano: DateTimeFormatter + un Regex
     * instanciado por llamada eran el mayor coste de CPU de la importación
     * XMLTV (cientos de miles de programas por documento).
     */
    fun parseUtcMillis(raw: String): Long? {
        val value = raw.trim()
        if (value.length < 14) return null
        fun d2(i: Int): Int {
            val a = value[i] - '0'
            val b = value[i + 1] - '0'
            if (a !in 0..9 || b !in 0..9) return -1
            return a * 10 + b
        }
        val fields = IntArray(7) { d2(it * 2) }
        if (fields.any { it < 0 }) return null
        val suffix = value.substring(14).trim()
        val offset = when {
            suffix.isEmpty() || suffix == "Z" -> ZoneOffset.UTC
            suffix.length == 5 && (suffix[0] == '+' || suffix[0] == '-') &&
                suffix[1].isDigit() && suffix[2].isDigit() && suffix[3].isDigit() && suffix[4].isDigit() -> {
                val minutes = (suffix[1] - '0') * 600 + (suffix[2] - '0') * 60 +
                    (suffix[3] - '0') * 10 + (suffix[4] - '0')
                ZoneOffset.ofTotalSeconds(if (suffix[0] == '-') -minutes * 60 else minutes * 60)
            }
            else -> runCatching { ZoneOffset.of(suffix) }.getOrNull() ?: return null
        }
        val local = runCatching {
            LocalDateTime.of(
                fields[0] * 100 + fields[1], fields[2], fields[3],
                fields[4], fields[5], fields[6],
            )
        }.getOrNull() ?: return null
        return local.toInstant(offset).toEpochMilli()
    }
}
