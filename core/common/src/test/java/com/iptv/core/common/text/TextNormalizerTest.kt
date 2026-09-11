package com.iptv.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `normalizes accents case and whitespace`() {
        assertEquals("canal accion espana", TextNormalizer.searchKey("  CANAL Acción España  "))
    }

    @Test
    fun `preserves numbers and symbols`() {
        assertEquals("formula 1 + motor", TextNormalizer.searchKey("Fórmula 1 + Motor"))
    }
}
