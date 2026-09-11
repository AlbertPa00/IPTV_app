package com.iptv.feature.catalog.ui

import com.iptv.core.storage.entity.Kinds
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentKindTest {

    @Test
    fun `maps visual sections to persisted kinds`() {
        assertEquals(Kinds.LIVE, ContentKind.TV.storageValue)
        assertEquals(Kinds.VOD, ContentKind.MOVIES.storageValue)
        assertEquals(Kinds.SERIES, ContentKind.SERIES.storageValue)
    }
}
