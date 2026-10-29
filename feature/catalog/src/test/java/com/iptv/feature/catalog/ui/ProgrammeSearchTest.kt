package com.iptv.feature.catalog.ui

import com.iptv.core.storage.entity.ChannelEntity
import com.iptv.core.storage.entity.ProgrammeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cobertura del emparejamiento programa↔canal y del reorden acotado:
 * funciones puras del LiveTvViewModel, sin Room.
 */
class ProgrammeSearchTest {

    private fun channel(
        id: Long,
        tvgId: String? = null,
        name: String = "Canal $id",
        nameNorm: String = "canal $id",
        providerNameNorm: String = nameNorm,
    ) = ChannelEntity(
        id = id,
        sourceId = 1,
        externalId = "http://x/$id",
        name = name,
        nameNorm = nameNorm,
        streamUrl = "http://x/$id",
        tvgId = tvgId,
        providerNameNorm = providerNameNorm,
    )

    private fun programme(
        id: Long,
        title: String,
        start: Long,
        channelKey: String = "",
        channelNameNorm: String = "",
    ) = ProgrammeEntity(
        id = id,
        sourceId = 1,
        channelKey = channelKey,
        channelNameNorm = channelNameNorm,
        title = title,
        startUtc = start,
        endUtc = start + 3_600_000,
    )

    @Test
    fun deduplicatesSameTitleSameStartAcrossChannelVariants() {
        val programmes = listOf(
            programme(1, "El partido", 100, channelKey = "laliga"),
            programme(2, "El partido", 100, channelKey = "laliga4k"),
            programme(3, "Otro", 200, channelKey = "tdp"),
        )
        val channels = listOf(
            channel(10, tvgId = "laliga"),
            channel(11, tvgId = "laliga4k"),
            channel(12, tvgId = "tdp"),
        )

        val hits = toProgrammeHits(programmes, channels)

        assertEquals(2, hits.size)
        val partido = hits.first()
        assertEquals("El partido", partido.title)
        assertEquals(2, partido.channelCount)
        assertEquals(10L, partido.channelId)
    }

    @Test
    fun resolvesRenamedChannelByProviderNameNorm() {
        // El usuario renombró el canal: name/nameNorm divergen del proveedor,
        // pero la EPG sigue casando por providerNameNorm.
        val renamed = channel(
            10,
            name = "Mi canal",
            nameNorm = "mi canal",
            providerNameNorm = "la 1",
        )
        val programmes = listOf(
            programme(1, "Telediario", 100, channelNameNorm = "la 1"),
        )

        val hits = toProgrammeHits(programmes, listOf(renamed))

        assertEquals(10L, hits.single().channelId)
        assertEquals("Mi canal", hits.single().channelName)
    }

    @Test
    fun programmeWithoutChannelKeepsNullChannel() {
        val hits = toProgrammeHits(
            listOf(programme(1, "Perdido", 100, channelKey = "inexistente")),
            emptyList(),
        )

        assertNull(hits.single().channelId)
        assertEquals(0, hits.single().channelCount)
    }

    @Test
    fun tvgIdMatchWinsOverNameMatch() {
        // Un canal con el mismo nombre normalizado y otro con el tvgId:
        // el programa casa por tvgId, no por nombre.
        val programmes = listOf(
            programme(1, "X", 100, channelKey = "key", channelNameNorm = "mismo"),
        )
        val byName = channel(20, providerNameNorm = "mismo")
        val byKey = channel(30, tvgId = "key")

        val hit = toProgrammeHits(programmes, listOf(byName, byKey)).single()

        assertEquals(30L, hit.channelId)
    }

    @Test
    fun movedInScopeSwapsAndRespectsBounds() {
        val scope = listOf(channel(1), channel(2), channel(3))

        val moved = movedInScope(scope, channelId = 2, up = true)
        assertEquals(listOf(2L, 1L, 3L), moved!!.map { it.id })

        assertNull(movedInScope(scope, channelId = 1, up = true))
        assertNull(movedInScope(scope, channelId = 3, up = false))
        assertNull(movedInScope(scope, channelId = 99, up = false))
    }
}
