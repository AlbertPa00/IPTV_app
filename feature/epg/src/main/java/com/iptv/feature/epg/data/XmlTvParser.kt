package com.iptv.feature.epg.data

import com.iptv.core.common.text.TextNormalizer
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
    private val compact = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parseUtcMillis(raw: String): Long? = runCatching {
        val value = raw.trim()
        require(value.length >= 14)
        val local = LocalDateTime.parse(value.substring(0, 14), compact)
        val suffix = value.substring(14).trim()
        val offset = when {
            suffix.isEmpty() || suffix == "Z" -> ZoneOffset.UTC
            suffix.matches(Regex("[+-]\\d{4}")) -> ZoneOffset.of(suffix.substring(0, 3) + ":" + suffix.substring(3))
            else -> ZoneOffset.of(suffix)
        }
        local.toInstant(offset).toEpochMilli()
    }.getOrNull()
}
