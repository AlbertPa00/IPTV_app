package com.iptv.core.common.text

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageTagTest {

    @Test
    fun `prefijos de pais agrupan en familia de idioma`() {
        assertEquals("AR", LanguageTag.detect("AR - SHAHID VIP"))
        assertEquals("EN", LanguageTag.detect("US| ENTERTAINMENT"))
        assertEquals("EN", LanguageTag.detect("UK: SPORTS"))
        assertEquals("ES", LanguageTag.detect("ES - TDT"))
        assertEquals("DE", LanguageTag.detect("DE  CINE"))
        assertEquals("FR", LanguageTag.detect("FR/INFOS"))
    }

    @Test
    fun `alias de paneles se normalizan`() {
        assertEquals("EN", LanguageTag.detect("USA| MOVIES"))
        assertEquals("EN", LanguageTag.detect("GB - NEWS"))
        assertEquals("ES", LanguageTag.detect("LATAM - NOVELAS"))
        assertEquals("ES", LanguageTag.detect("LATINO 24/7"))
    }

    @Test
    fun `paises se agrupan por familia linguistica`() {
        assertEquals("ES", LanguageTag.detect("MX| NOVELAS"))
        assertEquals("ES", LanguageTag.detect("CO| NOTICIAS"))
        assertEquals("AR", LanguageTag.detect("SA| ROTANA"))
        assertEquals("AR", LanguageTag.detect("EG| NIL SAT"))
        assertEquals("EN", LanguageTag.detect("CA| CBC"))
        assertEquals("EN", LanguageTag.detect("AU| SPORTS"))
        assertEquals("PT", LanguageTag.detect("BR| GLOBO"))
        assertEquals("SCANDI", LanguageTag.detect("SE| SVT"))
        assertEquals("SCANDI", LanguageTag.detect("DK| DR1"))
        assertEquals("EXYU", LanguageTag.detect("SR| SERBIA"))
        assertEquals("RU", LanguageTag.detect("KZ| KAZACHSTAN"))
        assertEquals("IN", LanguageTag.detect("PK| URDU"))
        assertEquals("SEA", LanguageTag.detect("PH| PINOY"))
        assertEquals("EU", LanguageTag.detect("CZ| NOVA"))
        assertEquals("GR", LanguageTag.detect("CY| RIK"))
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
    fun `nombre de pais o idioma completo en cualquier posicion`() {
        assertEquals("DE", LanguageTag.detect("NETFLIX GERMANY"))
        assertEquals("TR", LanguageTag.detect("TURKISH SERIES"))
        assertEquals("ES", LanguageTag.detect("VOD ESPAÑA"))
        assertEquals("SCANDI", LanguageTag.detect("FILM SVENSKA"))
        assertEquals("SCANDI", LanguageTag.detect("SUOMI TV"))
        assertEquals("FR", LanguageTag.detect("QC| TVA"))
        assertEquals("IN", LanguageTag.detect("HINDI MOVIES"))
        assertEquals("KU", LanguageTag.detect("KURDISH SHOWS"))
    }

    @Test
    fun `prefijo de panel con espacio simple`() {
        assertEquals("ASIA", LanguageTag.detect("ASIA MOVIES (MULTI-SUBS)"))
    }

    @Test
    fun `codigo iso como sufijo`() {
        assertEquals("ES", LanguageTag.detect("Series | Series ES"))
        assertEquals("ES", LanguageTag.detect("Peliculas | Reina Roja ES"))
        assertEquals("EN", LanguageTag.detect("Documental Naturaleza (UK)"))
    }

    @Test
    fun `sufijos de calidad no son idiomas`() {
        assertEquals("", LanguageTag.detect("TRECE TV"))
        assertEquals("", LanguageTag.detect("CANAL COCINA HD"))
    }
}
