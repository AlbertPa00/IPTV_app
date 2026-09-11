package com.iptv.feature.catalog.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parsea la respuesta de `player_api.php?action=get_vod_info`. Los paneles
 * son inconsistentes: `duration` llega como "HH:MM:SS", "120 min" o solo
 * `duration_secs`; `backdrop_path` es un array de URLs TMDB.
 */
class VodInfoParser(private val json: Json = Json { ignoreUnknownKeys = true }) {

    fun parse(payload: String, fallbackTitle: String, fallbackImage: String?): VodInfo {
        val root = json.parseToJsonElement(payload).asObject()
        val info = root["info"].asObject()
        val movieData = root["movie_data"].asObject()

        return VodInfo(
            title = info["name"].asText() ?: movieData["name"].asText() ?: fallbackTitle,
            plot = info["plot"].asText() ?: info["description"].asText(),
            cast = info["cast"].asText(),
            director = info["director"].asText(),
            genre = info["genre"].asText(),
            rating = info["rating"].asText(),
            releaseDate = info["releasedate"].asText() ?: info["releaseDate"].asText(),
            duration = info["duration"].asText()
                ?: info["duration_secs"].asLong()?.let { formatSeconds(it) },
            backdropUrl = info["backdrop_path"].asArray().firstOrNull().asText()
                ?: info["movie_image"].asText()
                ?: info["cover_big"].asText()
                ?: fallbackImage,
            country = info["country"].asText(),
            ageRating = info["age"].asText(),
        )
    }

    private fun formatSeconds(seconds: Long): String {
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        return if (hours > 0) "${hours}h ${minutes}min" else "${minutes}min"
    }

    private fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonElement?.asArray(): List<JsonElement> =
        (this as? kotlinx.serialization.json.JsonArray)?.toList().orEmpty()

    private fun JsonElement?.asText(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return primitive.content.trim().takeIf { it.isNotEmpty() }
        return primitive.content.trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JsonElement?.asLong(): Long? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.content.toDoubleOrNull()?.toLong()
    }
}
