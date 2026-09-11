package com.iptv.feature.series.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesInfoParserTest {
    private val parser = SeriesInfoParser()

    @Test
    fun `parses mixed string and number provider fields`() {
        val result = parser.parse(
            """
            {
              "info": {"name": "Dark", "cover": "https://img/cover.jpg", "plot": "A plot", "rating": 8.7},
              "seasons": [{"season_number": "1", "name": "Season One"}],
              "episodes": {
                "1": [
                  {"id": 101, "episode_num": "2", "title": "Secrets", "container_extension": "mkv",
                   "info": {"rating": "8.1", "duration": 52, "movie_image": "https://img/e2.jpg"}}
                ]
              }
            }
            """.trimIndent(),
            fallbackTitle = "Fallback",
            fallbackCover = null,
        )

        assertEquals("Dark", result.title)
        assertEquals("8.7", result.rating)
        assertEquals("Season One", result.seasons.single().title)
        with(result.seasons.single().episodes.single()) {
            assertEquals("101", id)
            assertEquals(2, number)
            assertEquals("52", duration)
            assertEquals("mkv", extension)
        }
    }

    @Test
    fun `uses fallbacks and ignores malformed episodes`() {
        val result = parser.parse(
            """{"info":{},"episodes":{"3":[{"title":"Missing id"}]}}""",
            fallbackTitle = "Local title",
            fallbackCover = "https://local/cover.jpg",
        )

        assertEquals("Local title", result.title)
        assertEquals("https://local/cover.jpg", result.coverUrl)
        assertNull(result.plot)
        assertEquals(emptyList<SeriesSeason>(), result.seasons)
    }
}
