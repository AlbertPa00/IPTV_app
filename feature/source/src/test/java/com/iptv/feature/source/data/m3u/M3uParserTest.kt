package com.iptv.feature.source.data.m3u

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class M3uParserTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val parser = M3uParser()

    @Test
    fun `parses standard attributes and stream url`() {
        val playlist = """
            #EXTM3U url-tvg="https://guide.test/epg.xml"
            #EXTINF:-1 tvg-id="la1.es" tvg-name="La 1" tvg-logo="https://img.test/la1.png" group-title="General" tvg-type="live",La 1 HD
            https://stream.test/live/la1.m3u8?token=secret
        """.trimIndent()

        val channel = parser.parse(playlist.reader().buffered()).single()

        assertEquals("La 1 HD", channel.name)
        assertEquals("la1.es", channel.tvgId)
        assertEquals("La 1", channel.tvgName)
        assertEquals("https://img.test/la1.png", channel.tvgLogo)
        assertEquals("General", channel.groupTitle)
        assertEquals("live", channel.contentType)
        assertEquals("https://stream.test/live/la1.m3u8?token=secret", channel.url)
        assertEquals("m3u8", channel.containerExt)
    }

    @Test
    fun `captures url-tvg and x-tvg-url headers while streaming`() {
        var epgUrl: String? = null
        val playlist = "#EXTM3U x-tvg-url=\"https://guide.test/epg.xml.gz\"\n#EXTINF:-1,Canal\nhttps://stream.test/live"

        val channels = parser.parse(playlist.reader().buffered()) { epgUrl = it }.toList()

        assertEquals(1, channels.size)
        assertEquals("https://guide.test/epg.xml.gz", epgUrl)
    }

    @Test
    fun `applies extgrp and vlc options to pending channel`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,Canal sin atributos
            #EXTGRP:Noticias
            #EXTVLCOPT:http-user-agent=Provider Player
            #EXTVLCOPT:http-referrer=https://provider.test/
            http://stream.test/channel.ts
        """.trimIndent()

        val channel = parser.parse(playlist.reader().buffered()).single()

        assertEquals("Noticias", channel.groupTitle)
        assertEquals("Provider Player", channel.userAgent)
        assertEquals("https://provider.test/", channel.referrer)
        assertEquals("ts", channel.containerExt)
    }

    @Test
    fun `ignores incomplete entries and directives`() {
        val playlist = """
            #EXTM3U
            #UNKNOWN:value
            http://orphan.test/stream.ts
            #EXTINF:-1,
            http://unnamed.test/stream.ts
            #EXTINF:-1,Valid
            https://valid.test/live
        """.trimIndent()

        val channels = parser.parse(playlist.reader().buffered()).toList()

        assertEquals(1, channels.size)
        assertEquals("Valid", channels.single().name)
        assertNull(channels.single().containerExt)
    }

    @Test
    fun `removes utf8 bom from first line`() {
        val playlist = "\uFEFF#EXTM3U\n#EXTINF:-1,Canal\nhttps://stream.test/live"

        assertEquals("Canal", parser.parse(playlist.reader().buffered()).single().name)
    }

    @Test
    fun `parses playlist from local file reader`() {
        val file = temporaryFolder.newFile("channels.m3u8")
        file.writeText("#EXTM3U\n#EXTINF:-1 group-title=\"Local\",Canal local\nfile:///video/channel.mp4")

        val channel = file.bufferedReader().use { parser.parse(it).single() }

        assertEquals("Canal local", channel.name)
        assertEquals("Local", channel.groupTitle)
        assertEquals("mp4", channel.containerExt)
    }
}
