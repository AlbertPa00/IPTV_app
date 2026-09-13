package com.iptv.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTagTest {

    @Test
    fun `prefijos de pais con distintos separadores`() {
        assertEquals("AR", LanguageTag.detect("AR - SHAHID VIP"))
        assertEquals("US", LanguageTag.detect("US| ENTERTAINMENT"))
        assertEquals("UK", LanguageTag.detect("UK: SPORTS"))
        assertEquals("ES", LanguageTag.detect("ES - TDT"))
        assertEquals("DE", LanguageTag.detect("DE  CINE"))
        assertEquals("FR", LanguageTag.detect("FR/INFOS"))
    }

    @Test
    fun `alias de paneles se normalizan`() {
        assertEquals("US", LanguageTag.detect("USA| MOVIES"))
        assertEquals("UK", LanguageTag.detect("GB - NEWS"))
        assertEquals("LA", LanguageTag.detect("LATAM - NOVELAS"))
    }

    @Test
    fun `regiones multi-pais`() {
        assertEquals("EXYU", LanguageTag.detect("EXYU - PINK"))
        assertEquals("ASIA", LanguageTag.detect("ASIA - DESI"))
        assertEquals("AFR", LanguageTag.detect("AFR - CANAL"))
    }

    @Test
    fun `etiquetas de calidad no son idiomas`() {
        assertEquals("", LanguageTag.detect("4K| UHD 3840P"))
        assertEquals("", LanguageTag.detect("VIP - SPORTS"))
    }

    @Test
    fun `sin prefijo detecta el alfabeto`() {
        assertEquals("AR", LanguageTag.detect("مسلسلات عربية فائقة"))
        assertEquals("RU", LanguageTag.detect("РОССИЯ КАНАЛЫ"))
        assertEquals("GR", LanguageTag.detect("ΕΛΛΗΝΙΚΑ"))
        assertEquals("CN", LanguageTag.detect("中文频道"))
        assertEquals("IN", LanguageTag.detect("हिंदी चैनल"))
    }

    @Test
    fun `nombre latin sin prefijo devuelve vacio`() {
        assertEquals("", LanguageTag.detect("NETFLIX MOVIES"))
        assertEquals("", LanguageTag.detect("Series Antiguas"))
        assertEquals("", LanguageTag.detect("MOVISTAR DEPORTES 4 SD (SOLO EVENTOS)"))
        assertEquals("", LanguageTag.detect("ANTENA 3 HD"))
    }

    @Test
    fun `codigos iso menos frecuentes`() {
        assertEquals("GE", LanguageTag.detect("GE| GEORGIA"))
        assertEquals("KZ", LanguageTag.detect("KZ| KAZACHSTAN"))
        assertEquals("AZ", LanguageTag.detect("AZ| AZERBAIJAN"))
        assertEquals("EN", LanguageTag.detect("EN - DRAMA"))
    }

    @Test
    fun `prefijo de panel con espacio simple`() {
        assertEquals("ASIA", LanguageTag.detect("ASIA MOVIES (MULTI-SUBS)"))
        assertEquals("LA", LanguageTag.detect("LATINO 24/7"))
    }

    @Test
    fun `codigo iso como sufijo`() {
        assertEquals("ES", LanguageTag.detect("Series | Series ES"))
        assertEquals("ES", LanguageTag.detect("Peliculas | Reina Roja ES"))
        assertEquals("UK", LanguageTag.detect("Documental Naturaleza (UK)"))
    }

    @Test
    fun `sufijos de calidad no son idiomas`() {
        assertEquals("", LanguageTag.detect("TRECE TV"))
        assertEquals("", LanguageTag.detect("CANAL COCINA HD"))
    }
}
