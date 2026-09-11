package com.iptv.feature.catalog.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VodInfoParserTest {

    private val parser = VodInfoParser()

    @Test
    fun `parses a typical xtream vod info payload`() {
        val payload = """
        {
          "info": {
            "movie_image": "http://img/poster.jpg",
            "backdrop_path": ["http://img/backdrop1.jpg", "http://img/backdrop2.jpg"],
            "genre": "Action, Thriller",
            "plot": "A hero saves the day.",
            "cast": "Actor One, Actor Two",
            "rating": "7.8",
            "director": "Director Name",
            "releasedate": "2021-05-14",
            "duration_secs": 6120,
            "country": "USA",
            "age": "16"
          },
          "movie_data": {"name": "Movie Name", "stream_id": 42}
        }
        """.trimIndent()

        val info = parser.parse(payload, "Fallback", null)

        assertEquals("Movie Name", info.title)
        assertEquals("A hero saves the day.", info.plot)
        assertEquals("Actor One, Actor Two", info.cast)
        assertEquals("Director Name", info.director)
        assertEquals("Action, Thriller", info.genre)
        assertEquals("7.8", info.rating)
        assertEquals("2021-05-14", info.releaseDate)
        assertEquals("2021", info.year)
        assertEquals("1h 42min", info.duration)
        assertEquals("http://img/backdrop1.jpg", info.backdropUrl)
        assertEquals("USA", info.country)
        assertEquals("16", info.ageRating)
    }

    @Test
    fun `string duration is used as-is when present`() {
        val payload = """{"info": {"duration": "02:05:00", "name": "X"}}"""
        assertEquals("02:05:00", parser.parse(payload, "F", null).duration)
    }

    @Test
    fun `falls back to catalog name and poster when info is sparse`() {
        val info = parser.parse("""{"info": {}}""", "Catalog Title", "http://img/logo.png")
        assertEquals("Catalog Title", info.title)
        assertEquals("http://img/logo.png", info.backdropUrl)
        assertNull(info.plot)
        assertNull(info.rating)
    }
}
