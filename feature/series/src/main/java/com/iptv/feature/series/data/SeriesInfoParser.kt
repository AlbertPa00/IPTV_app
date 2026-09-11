package com.iptv.feature.series.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

class SeriesInfoParser(private val json: Json = Json { ignoreUnknownKeys = true }) {

    fun parse(payload: String, fallbackTitle: String, fallbackCover: String?): SeriesDetails {
        val root = json.parseToJsonElement(payload).asObject()
        val info = root["info"].asObject()
        val seasonNames = root["seasons"].asArray().associate { season ->
            val item = season.asObject()
            item["season_number"].asInt() to (item["name"].asText() ?: "")
        }
        val seasons = root["episodes"].asObject().mapNotNull { (key, value) ->
            val number = key.toIntOrNull() ?: value.asArray().firstOrNull()
                ?.asObject()?.get("season")?.asInt() ?: return@mapNotNull null
            val episodes = value.asArray().mapNotNull { parseEpisode(it) }
                .sortedBy { it.number }
            if (episodes.isEmpty()) null else SeriesSeason(
                number = number,
                title = seasonNames[number].takeUnless { it.isNullOrBlank() } ?: "Temporada $number",
                episodes = episodes,
            )
        }.sortedBy { it.number }

        return SeriesDetails(
            title = info["name"].asText() ?: root["name"].asText() ?: fallbackTitle,
            coverUrl = info["cover"].asText() ?: info["movie_image"].asText() ?: fallbackCover,
            plot = info["plot"].asText(),
            rating = info["rating"].asText(),
            seasons = seasons,
        )
    }

    private fun parseEpisode(element: JsonElement): SeriesEpisode? {
        val episode = element.asObject()
        val id = episode["id"].asText() ?: return null
        val info = episode["info"].asObject()
        return SeriesEpisode(
            id = id,
            number = episode["episode_num"].asInt() ?: episode["episode_number"].asInt() ?: 0,
            title = episode["title"].asText() ?: info["name"].asText() ?: "Episodio",
            plot = info["plot"].asText(),
            imageUrl = info["movie_image"].asText() ?: info["cover_big"].asText(),
            rating = info["rating"].asText(),
            duration = info["duration"].asText(),
            extension = episode["container_extension"].asText() ?: "mp4",
        )
    }

    private fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonElement?.asArray(): List<JsonElement> =
        (this as? kotlinx.serialization.json.JsonArray)?.toList().orEmpty()

    private fun JsonElement?.asText(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive.isString) return primitive.content.trim().takeIf { it.isNotEmpty() }
        return primitive.content.trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JsonElement?.asInt(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.content.toDoubleOrNull()?.toInt()
    }
}
