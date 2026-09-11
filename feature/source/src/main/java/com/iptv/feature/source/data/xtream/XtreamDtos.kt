package com.iptv.feature.source.data.xtream

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class PlayerApiResponse(
    val user_info: UserInfo = UserInfo(),
    val server_info: ServerInfo = ServerInfo(),
)

@Serializable
data class UserInfo(
    val username: String = "",
    val password: String = "",
    val auth: Int = 0,
    val status: String = "",
    val exp_date: String? = null,
    val is_trial: String? = null,
    val active_cons: String? = null,
    val max_connections: String? = null,
    val message: String? = null,
)

@Serializable
data class ServerInfo(
    val url: String = "",
    val port: Int = 0,
    val https_port: Int? = null,
    val server_protocol: String? = null,
)

@Serializable
data class XtCategory(
    val category_id: String = "",
    val category_name: String = "",
)

/**
 * Los paneles Xtream no son consistentes: `category_id` llega como cadena
 * ("5"), número (5) o array (["5"]), y algunos exponen `category_ids`.
 * Estos serializadores normalizan todo a un id en texto.
 */
object CategoryIdSerializer : JsonTransformingSerializer<String>(String.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val first = when (element) {
            is JsonArray -> element.firstOrNull()
            else -> element
        }
        return JsonPrimitive((first as? JsonPrimitive)?.content.orEmpty())
    }
}

object CategoryIdsSerializer : JsonTransformingSerializer<List<String>>(
    ListSerializer(String.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        JsonArray((element as? JsonArray).orEmpty().map { JsonPrimitive((it as? JsonPrimitive)?.content.orEmpty()) })
}

@Serializable
data class XtStream(
    val num: Int = 0,
    val name: String = "",
    val stream_type: String = "",
    val stream_id: Long = 0,
    val stream_icon: String? = null,
    val epg_channel_id: String? = null,
    val added: String? = null,
    @Serializable(with = CategoryIdSerializer::class)
    val category_id: String = "",
    @Serializable(with = CategoryIdsSerializer::class)
    val category_ids: List<String> = emptyList(),
    val tv_archive: Int? = null,
    val direct_source: String? = null,
    val container_extension: String? = null,
    val rating: String? = null,
) {
    val effectiveCategoryId: String
        get() = category_id.ifBlank { category_ids.firstOrNull().orEmpty() }
}

@Serializable
data class XtSeries(
    val num: Int = 0,
    val name: String = "",
    val series_id: Long = 0,
    val cover: String? = null,
    @Serializable(with = CategoryIdSerializer::class)
    val category_id: String = "",
    @Serializable(with = CategoryIdsSerializer::class)
    val category_ids: List<String> = emptyList(),
    val plot: String? = null,
    val rating: String? = null,
    val releaseDate: String? = null,
) {
    val effectiveCategoryId: String
        get() = category_id.ifBlank { category_ids.firstOrNull().orEmpty() }
}
