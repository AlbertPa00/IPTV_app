package com.iptv.feature.epg.data

import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.SourceEntity
import com.iptv.core.storage.entity.SourceTypes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class XmlTvParserTest {
    @Test
    fun `parses XMLTV offsets and Z as UTC`() {
        assertEquals(Instant.parse("2025-01-02T01:04:05Z").toEpochMilli(), XmlTvDateParser.parseUtcMillis("20250102030405 +0200"))
        assertEquals(Instant.parse("2025-01-02T03:04:05Z").toEpochMilli(), XmlTvDateParser.parseUtcMillis("20250102030405Z"))
    }

    @Test
    fun `streams minimum channel and programme`() = runBlocking {
        val xml = """<tv><channel id="one"><display-name>Canal Úno</display-name></channel><programme start="20250102030405 +0000" stop="20250102040405 +0000" channel="one"><title>News</title><desc>Headlines</desc><icon src="https://img/icon.png"/></programme></tv>"""
        val result = mutableListOf<XmlTvProgramme>()
        XmlTvParser().parse(xml.byteInputStream()) { result += it }
        assertEquals(1, result.size)
        assertEquals("canal uno", result.single().channelNameNorm)
        assertEquals("News", result.single().title)
        assertEquals("Headlines", result.single().description)
    }

    @Test
    fun `resolves explicit and encoded Xtream URLs`() {
        val explicit = SourceEntity(type = SourceTypes.M3U_URL, name = "M3U", epgUrl = "https://guide/epg.xml")
        assertEquals("https://guide/epg.xml", EpgUrlResolver.resolve(explicit) { null })
        val xtream = SourceEntity(type = SourceTypes.XTREAM, name = "X", url = "https://host:8443", username = "user name", passwordEnc = "cipher")
        assertEquals("https://host:8443/xmltv.php?username=user%20name&password=p%26ss", EpgUrlResolver.resolve(xtream) { "p&ss" })
    }

    @Test
    fun `detects gzip by header or extension`() {
        assertTrue(EpgCompression.isGzip("https://guide/epg.xml", "gzip"))
        assertTrue(EpgCompression.isGzip("https://guide/epg.xml.gz?key=1", null))
        assertFalse(EpgCompression.isGzip("https://guide/epg.xml", null))
    }

    @Test
    fun `associates tvg id before normalized name fallback`() {
        val channels = listOf(ChannelEntity(sourceId = 1, externalId = "1", name = "Canal Uno", nameNorm = "canal uno", streamUrl = "url", tvgId = "one"))
        val association = EpgAssociation(channels)
        assertTrue(association.matches(programme("one", "other")))
        assertTrue(association.matches(programme("different", "canal uno")))
        assertFalse(association.matches(programme("different", "other")))
    }

    private fun programme(key: String, name: String) = XmlTvProgramme(key, name, "Title", null, 1, 2, null)
}
