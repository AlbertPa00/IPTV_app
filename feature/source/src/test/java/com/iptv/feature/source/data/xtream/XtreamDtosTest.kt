package com.iptv.feature.source.data.xtream

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Los paneles Xtream devuelven `category_id` como cadena, número o array, y a
 * veces `category_ids` en su lugar. Estas variantes deben resolver el id.
 */
class XtreamDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `stream category_id as string`() {
        val stream = json.decodeFromString<XtStream>(
            """{"stream_id": 1, "name": "M", "category_id": "7"}""",
        )
        assertEquals("7", stream.effectiveCategoryId)
    }

    @Test
    fun `stream category_id as array`() {
        val stream = json.decodeFromString<XtStream>(
            """{"stream_id": 1, "name": "M", "category_id": ["7", "8"]}""",
        )
        assertEquals("7", stream.effectiveCategoryId)
    }

    @Test
    fun `stream category_id as number`() {
        val stream = json.decodeFromString<XtStream>(
            """{"stream_id": 1, "name": "M", "category_id": 7}""",
        )
        assertEquals("7", stream.effectiveCategoryId)
    }

    @Test
    fun `series falls back to category_ids array`() {
        val series = json.decodeFromString<XtSeries>(
            """{"series_id": 1, "name": "S", "category_ids": [12, 15]}""",
        )
        assertEquals("12", series.effectiveCategoryId)
    }

    @Test
    fun `missing category resolves to blank`() {
        val stream = json.decodeFromString<XtStream>("""{"stream_id": 1, "name": "M"}""")
        assertEquals("", stream.effectiveCategoryId)
    }
}
