package com.iptv.feature.series.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistEpisodeParserTest {

    @Test
    fun `parses SxxExx pattern`() {
        val parsed = PlaylistEpisodeParser.parse("ES - Breaking Bad S02E05")
        assertEquals(2, parsed?.season)
        assertEquals(5, parsed?.episode)
        assertEquals("ES - Breaking Bad", parsed?.baseTitle)
    }

    @Test
    fun `parses TxxExx pattern`() {
        val parsed = PlaylistEpisodeParser.parse("La casa de papel T03E07")
        assertEquals(3, parsed?.season)
        assertEquals(7, parsed?.episode)
        assertEquals("La casa de papel", parsed?.baseTitle)
    }

    @Test
    fun `parses NxM pattern`() {
        val parsed = PlaylistEpisodeParser.parse("Friends 4x12")
        assertEquals(4, parsed?.season)
        assertEquals(12, parsed?.episode)
        assertEquals("Friends", parsed?.baseTitle)
    }

    @Test
    fun `returns null for titles without episode info`() {
        assertNull(PlaylistEpisodeParser.parse("ES - Historia de dos ciudades (2026) (GB)"))
    }

    @Test
    fun `same series episodes share base key`() {
        val e1 = PlaylistEpisodeParser.baseKey("Show Name S01E01")
        val e2 = PlaylistEpisodeParser.baseKey("Show Name S01E02")
        val other = PlaylistEpisodeParser.baseKey("Another Show S01E01")
        assertEquals(e1, e2)
        assert(e1 != other)
    }
}
