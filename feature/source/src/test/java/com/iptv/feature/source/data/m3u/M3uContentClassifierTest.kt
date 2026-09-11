package com.iptv.feature.source.data.m3u

import com.iptv.core.storage.entity.Kinds
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uContentClassifierTest {

    @Test
    fun `classifies explicit movie metadata as vod`() {
        assertEquals(Kinds.VOD, M3uContentClassifier.classify(ParsedChannel("Film", contentType = "movie")))
    }

    @Test
    fun `classifies xtream movie and series paths`() {
        assertEquals(Kinds.VOD, M3uContentClassifier.classify(ParsedChannel("Film", url = "http://host/movie/u/p/1.mp4")))
        assertEquals(Kinds.SERIES, M3uContentClassifier.classify(ParsedChannel("Episode", url = "http://host/series/u/p/2.mkv")))
    }

    @Test
    fun `classifies common movie groups as vod`() {
        assertEquals(Kinds.VOD, M3uContentClassifier.classify(ParsedChannel("Film", groupTitle = "ES | PELÍCULAS 4K")))
    }

    @Test
    fun `classifies series groups and episode name patterns`() {
        assertEquals(Kinds.SERIES, M3uContentClassifier.classify(ParsedChannel("Item", groupTitle = "ES | SERIES 24H")))
        assertEquals(Kinds.SERIES, M3uContentClassifier.classify(ParsedChannel("Breaking Bad S01E03")))
        assertEquals(Kinds.SERIES, M3uContentClassifier.classify(ParsedChannel("Lupin 2x01", groupTitle = "General")))
        assertEquals(Kinds.SERIES, M3uContentClassifier.classify(ParsedChannel("Item", contentType = "series")))
    }

    @Test
    fun `keeps regular channels as live tv`() {
        assertEquals(Kinds.LIVE, M3uContentClassifier.classify(ParsedChannel("La 1", groupTitle = "España Generalistas")))
        // "Serie A" es fútbol en directo, no una serie.
        assertEquals(Kinds.LIVE, M3uContentClassifier.classify(ParsedChannel("DAZN 1", groupTitle = "DEPORTES | SERIE A")))
    }
}
