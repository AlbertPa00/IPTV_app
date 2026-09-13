package com.iptv.core.common.text

import java.util.Locale

/**
 * Detecta el idioma/país de un canal o categoría de IPTV y lo normaliza a
 * una FAMILIA de idioma ("EN", "ES", "AR", "SCANDI"…): lo que un espectador
 * filtra realmente es qué entiende, no el país del canal. Así "US| PRIME",
 * "UK: SPORTS" y "EN - MOVIES" agrupan en "EN", y "LA| COLOMBIA" o
 * "MX| NOVELAS" en "ES".
 *
 * Señales, en orden: prefijo de código ("AR - ", "US|"), prefijo de panel
 * multi-letra ("ASIA MOVIES"), sufijo ISO ("Series ES"), nombre de país o
 * idioma completo en cualquier posición ("NETFLIX GERMANY", "TURKISH
 * SERIES") y, por último, el alfabeto (árabe, cirílico, CJK…).
 */
object LanguageTag {

    /** Versión del detector: al subirla el backfill re-etiqueta el catálogo. */
    const val DETECTOR_VERSION = 2

    /** Token inicial de 2-6 letras seguido de separador típico de panel. */
    private val PREFIX = Regex("""^([A-Za-z]{2,6})\s*(?:[-|•·:/\\]|\s{2,})""")

    /** Código de panel multi-letra seguido de espacio simple ("ASIA MOVIES"). */
    private val PANEL_PREFIX = Regex("""^([A-Za-z]{3,6})\s""")

    /** Código ISO al final del nombre: "Series ES", "… (UK)". */
    private val SUFFIX = Regex("""[\s\-|•·:/\\(]\s*([A-Za-z]{2,3})\s*\)?\s*$""")

    /** Palabras completas de letras (para buscar nombres de país/idioma). */
    private val WORD = Regex("""[A-Za-zÀ-ÿ]{3,}""")

    /** Etiquetas de calidad/marketing que no son idiomas. */
    private val NON_LANGUAGE = setOf(
        "4K", "8K", "UHD", "FHD", "HD", "SD", "HEVC", "VIP", "XXX",
        "RAW", "VOD", "TV", "PPV", "NBA", "NFL", "NHL", "MLB", "UFC",
        "DARK", "CINEMA", "SPORTS", "TOP", "WWE",
    )

    /**
     * Códigos de panel que no son países ISO pero agrupan contenido:
     * regiones (EXYU, SCANDI…), idiomas como prefijo (EN), marcas que el
     * panel usa como prefijo (NF=Netflix, MV/MC=música, RX=relax…).
     */
    private val PANEL = setOf(
        "LATAM", "LATINO", "CARIBE", "EXYU", "SCANDI", "NORDIC", "ASIA",
        "AFR", "KURD", "INT", "WORLD", "EU", "EN", "SEA",
        // UK no es ISO (ISO usa GB) pero es el código estándar en paneles.
        "UK", "QC",
        // Códigos de panel que agrupan por marca/contenido, no por país.
        "NF", "MV", "MC", "RX", "TS", "WT", "SU", "AS",
    )

    /** Países ISO-3166 reales (cubre AF, GE, AZ, UZ, KZ, SR…). */
    private val ISO: Set<String> = Locale.getISOCountries().toSet()

    private val KNOWN: Set<String> = ISO + PANEL

    /**
     * Código detectado → familia de idioma mostrada en el filtro. Lo que no
     * aparece aquí se queda como su propio código (país o panel).
     */
    private val FAMILY = mapOf(
        // Inglés
        "US" to "EN", "UK" to "EN", "GB" to "EN", "IE" to "EN", "CA" to "EN",
        "AU" to "EN", "NZ" to "EN", "ZA" to "EN", "NF" to "EN", "SC" to "EN",
        "SU" to "EN", "TS" to "EN",
        // Español
        "LA" to "ES", "LATAM" to "ES", "LATINO" to "ES", "CARIBE" to "ES",
        "MX" to "ES", "CO" to "ES", "CL" to "ES", "PE" to "ES", "VE" to "ES",
        "EC" to "ES", "UY" to "ES", "PY" to "ES", "BO" to "ES", "CR" to "ES",
        "PA" to "ES", "DO" to "ES", "PR" to "ES", "GT" to "ES", "SV" to "ES",
        "HN" to "ES", "NI" to "ES", "CU" to "ES", "GQ" to "ES",
        // Árabe: en paneles IPTV "AR" suele ser árabe/MENA, no Argentina.
        "SA" to "AR", "AE" to "AR", "QA" to "AR", "KW" to "AR", "BH" to "AR",
        "OM" to "AR", "YE" to "AR", "IQ" to "AR", "SY" to "AR", "LB" to "AR",
        "JO" to "AR", "PS" to "AR", "EG" to "AR", "DZ" to "AR", "MA" to "AR",
        "TN" to "AR", "LY" to "AR", "SD" to "AR", "TD" to "AR", "MR" to "AR",
        // Francés
        "QC" to "FR", "FR" to "FR",
        // Alemán
        "AT" to "DE", "CH" to "DE", "LI" to "DE", "DE" to "DE",
        // Portugués
        "BR" to "PT", "AO" to "PT", "MZ" to "PT", "CV" to "PT", "GW" to "PT",
        "ST" to "PT", "PT" to "PT",
        // Neerlandés (Flandes domina el contenido belga en paneles)
        "BE" to "NL", "NL" to "NL",
        // Ex-Yugoslavia / Balcanes (SR en paneles suele ser Serbia, no Surinam)
        "SR" to "EXYU", "RS" to "EXYU", "HR" to "EXYU", "BA" to "EXYU",
        "ME" to "EXYU", "CG" to "EXYU", "MK" to "EXYU", "SI" to "EXYU",
        "XK" to "EXYU", "EXYU" to "EXYU",
        // Escandinavia
        "SE" to "SCANDI", "NO" to "SCANDI", "DK" to "SCANDI", "FI" to "SCANDI",
        "IS" to "SCANDI", "FO" to "SCANDI", "GL" to "SCANDI", "AX" to "SCANDI",
        "SJ" to "SCANDI", "NORDIC" to "SCANDI", "SCANDI" to "SCANDI",
        // Ruso / ex-URSS
        "UA" to "RU", "BY" to "RU", "KZ" to "RU", "KG" to "RU", "UZ" to "RU",
        "TJ" to "RU", "TM" to "RU", "MD" to "RU", "GE" to "RU", "AM" to "RU",
        "AZ" to "RU", "MN" to "RU", "RU" to "RU",
        // Subcontinente indio (hindi, urdu, bengalí, dari…)
        "PK" to "IN", "BD" to "IN", "LK" to "IN", "NP" to "IN", "BN" to "IN",
        "AF" to "IN", "AG" to "IN", "IN" to "IN",
        // Griego
        "CY" to "GR", "GR" to "GR",
        // Hebreo
        "HE" to "IL", "IL" to "IL",
        // Sudeste asiático
        "TH" to "SEA", "VN" to "SEA", "VI" to "SEA", "PH" to "SEA", "ID" to "SEA",
        "MY" to "SEA", "SG" to "SEA", "MM" to "SEA", "KH" to "SEA", "SEA" to "SEA",
        // Chino
        "HK" to "CN", "TW" to "CN", "MO" to "CN", "CN" to "CN",
        // África subsahariana
        "NG" to "AFR", "KE" to "AFR", "GH" to "AFR", "ET" to "AFR",
        "CM" to "AFR", "CI" to "AFR", "SO" to "AFR", "SN" to "AFR",
        "CD" to "AFR", "UG" to "AFR", "TZ" to "AFR", "ZW" to "AFR",
        "ZM" to "AFR", "RW" to "AFR", "MW" to "AFR", "ML" to "AFR",
        "AFR" to "AFR",
        // Europa del Este / resto europeo menor
        "BG" to "EU", "CZ" to "EU", "SK" to "EU", "HU" to "EU", "EE" to "EU",
        "LV" to "EU", "LT" to "EU", "MT" to "EU", "AD" to "EU", "LU" to "EU",
        "EU" to "EU",
        // Internacional / multi
        "INT" to "INT", "WORLD" to "INT", "WT" to "INT",
        // Paneles asiáticos genéricos
        "AS" to "ASIA", "ASIA" to "ASIA",
        // Música / relax (etiquetas propias)
        "MV" to "MV", "MC" to "MC", "RX" to "RX",
        // Sin cambio de familia
        "EN" to "EN", "ES" to "ES", "AR" to "AR", "IT" to "IT", "TR" to "TR",
        "PL" to "PL", "RO" to "RO", "AL" to "AL", "IR" to "IR", "KU" to "KU",
        "JP" to "JP", "KR" to "KR",
    )

    /**
     * Nombre completo de país/idioma en cualquier posición → familia.
     * Cubre "NETFLIX GERMANY", "TURKISH SERIES", "ESPAÑA", "SVENSKA"…
     */
    private val WORDS = mapOf(
        // Inglés
        "ENGLISH" to "EN", "BRITISH" to "EN", "AMERICAN" to "EN",
        "CANADIAN" to "EN", "AUSTRALIAN" to "EN", "IRISH" to "EN",
        "SCOTTISH" to "EN", "WELSH" to "EN",
        // Español
        "SPAIN" to "ES", "ESPANA" to "ES", "ESPANOL" to "ES", "SPANISH" to "ES",
        "LATINO" to "ES", "LATINA" to "ES", "LATAM" to "ES", "HISPANIC" to "ES",
        "MEXICO" to "ES", "MEXICAN" to "ES", "ARGENTINA" to "ES",
        "COLOMBIA" to "ES", "CHILE" to "ES", "PERU" to "ES", "VENEZUELA" to "ES",
        "ECUADOR" to "ES", "URUGUAY" to "ES", "PARAGUAY" to "ES",
        "BOLIVIA" to "ES", "PANAMA" to "ES", "CUBA" to "ES", "DOMINICAN" to "ES",
        "GUATEMALA" to "ES", "HONDURAS" to "ES", "NICARAGUA" to "ES",
        "CATALAN" to "ES", "GALICIAN" to "ES", "BASQUE" to "ES",
        // Árabe
        "ARABIC" to "AR", "ARABE" to "AR", "ARABIA" to "AR", "MAGHREB" to "AR",
        "MOROCCO" to "AR", "ALGERIA" to "AR", "TUNISIA" to "AR", "EGYPT" to "AR",
        "LEBANON" to "AR", "SYRIA" to "AR", "IRAQ" to "AR", "JORDAN" to "AR",
        "PALESTINE" to "AR", "SAUDI" to "AR", "EMIRATI" to "AR", "KUWAIT" to "AR",
        "BAHRAIN" to "AR", "OMAN" to "AR", "YEMEN" to "AR", "LIBYA" to "AR",
        "SUDAN" to "AR", "KHALIJI" to "AR",
        // Francés
        "FRANCE" to "FR", "FRENCH" to "FR", "FRANCAIS" to "FR",
        "FRANCOPHONE" to "FR", "QUEBEC" to "FR",
        // Alemán
        "GERMANY" to "DE", "GERMAN" to "DE", "DEUTSCH" to "DE",
        "DEUTSCHLAND" to "DE", "OESTERREICH" to "DE", "AUSTRIA" to "DE",
        // Portugués
        "PORTUGAL" to "PT", "PORTUGUESE" to "PT", "LUSO" to "PT",
        "BRAZIL" to "PT", "BRASIL" to "PT", "ANGOLA" to "PT", "MOZAMBIQUE" to "PT",
        // Italiano
        "ITALY" to "IT", "ITALIA" to "IT", "ITALIAN" to "IT", "ITALIANO" to "IT",
        // Turco
        "TURKISH" to "TR", "TURKEY" to "TR", "TURKIYE" to "TR", "TURKCE" to "TR",
        // Neerlandés
        "NETHERLANDS" to "NL", "DUTCH" to "NL", "HOLLAND" to "NL",
        "NEDERLANDS" to "NL", "VLAAMS" to "NL", "BELGIE" to "NL",
        // Escandinavia
        "SVENSKA" to "SCANDI", "SWEDEN" to "SCANDI", "SWEDISH" to "SCANDI",
        "SUOMI" to "SCANDI", "SUOMEN" to "SCANDI", "FINLAND" to "SCANDI",
        "FINNISH" to "SCANDI", "DANSKE" to "SCANDI", "DANISH" to "SCANDI",
        "DENMARK" to "SCANDI", "NORGE" to "SCANDI", "NORSK" to "SCANDI",
        "NORWAY" to "SCANDI", "NORWEGIAN" to "SCANDI", "ICELAND" to "SCANDI",
        "SCANDINAVIAN" to "SCANDI", "NORDIC" to "SCANDI",
        // Ruso / ex-URSS
        "RUSSIA" to "RU", "RUSSIAN" to "RU", "UKRAINE" to "RU",
        "UKRAINIAN" to "RU", "GEORGIAN" to "RU", "ARMENIAN" to "RU",
        "AZERI" to "RU", "KAZAKH" to "RU", "UZBEK" to "RU", "MONGOLIA" to "RU",
        // Subcontinente indio
        "INDIA" to "IN", "INDIAN" to "IN", "HINDI" to "IN", "URDU" to "IN",
        "BANGLA" to "IN", "BENGALI" to "IN", "TAMIL" to "IN", "TELUGU" to "IN",
        "PUNJABI" to "IN", "GUJARATI" to "IN", "MARATHI" to "IN",
        "MALAYALAM" to "IN", "KANNADA" to "IN", "PASHTO" to "IN", "DARI" to "IN",
        "NEPALI" to "IN", "NEPAL" to "IN", "SINHALA" to "IN", "LANKA" to "IN",
        "PAKISTAN" to "IN", "PAKISTANI" to "IN", "BANGLADESH" to "IN",
        "AFGHANISTAN" to "IN", "AFGHAN" to "IN",
        // Griego
        "GREECE" to "GR", "GREEK" to "GR", "ELLINIKA" to "GR", "CYPRUS" to "GR",
        // Hebreo
        "HEBREW" to "IL", "ISRAEL" to "IL", "ISRAELI" to "IL",
        // Sudeste asiático
        "VIETNAM" to "SEA", "VIETNAMESE" to "SEA", "THAILAND" to "SEA",
        "THAI" to "SEA", "INDONESIA" to "SEA", "MALAYSIA" to "SEA",
        "MALAY" to "SEA", "PHILIPPINES" to "SEA", "FILIPINO" to "SEA",
        "TAGALOG" to "SEA", "MYANMAR" to "SEA", "CAMBODIA" to "SEA",
        "KHMER" to "SEA", "LAOS" to "SEA", "BAHASA" to "SEA",
        // Este de Asia
        "JAPAN" to "JP", "JAPANESE" to "JP", "KOREA" to "KR", "KOREAN" to "KR",
        "CHINA" to "CN", "CHINESE" to "CN", "TAIWAN" to "CN",
        "MANDARIN" to "CN", "CANTONESE" to "CN",
        // África subsahariana
        "AFRICA" to "AFR", "AFRICAN" to "AFR", "NIGERIA" to "AFR",
        "KENYA" to "AFR", "GHANA" to "AFR", "ETHIOPIA" to "AFR",
        "SOMALIA" to "AFR", "SOMALI" to "AFR", "CAMEROON" to "AFR",
        "SWAHILI" to "AFR", "AMHARIC" to "AFR", "ZULU" to "AFR",
        "YORUBA" to "AFR", "IGBO" to "AFR", "HAUSA" to "AFR",
        // Europa del Este / Balcanes
        "POLAND" to "PL", "POLISH" to "PL", "POLSKA" to "PL",
        "ROMANIA" to "RO", "ROMANIAN" to "RO", "ROMANA" to "RO",
        "ALBANIA" to "AL", "ALBANIAN" to "AL", "SHQIP" to "AL",
        "SERBIA" to "EXYU", "SRPSKI" to "EXYU", "CROATIA" to "EXYU",
        "HRVATSKI" to "EXYU", "BOSNIA" to "EXYU", "BOSANSKI" to "EXYU",
        "MONTENEGRO" to "EXYU", "MACEDONIA" to "EXYU", "BALKAN" to "EXYU",
        "BULGARIA" to "EU", "BULGARIAN" to "EU", "CZECH" to "EU",
        "SLOVAK" to "EU", "HUNGARY" to "EU", "HUNGARIAN" to "EU",
        "MAGYAR" to "EU", "ESTONIA" to "EU", "LATVIA" to "EU",
        "LITHUANIA" to "EU", "MALTA" to "EU", "BALTIC" to "EU",
        // Otros
        "KURDISH" to "KU", "KURD" to "KU",
        "PERSIAN" to "IR", "IRAN" to "IR", "FARSI" to "IR",
    )

    fun detect(name: String): String {
        val trimmed = name.trimStart()
        PREFIX.find(trimmed)?.groupValues?.get(1)?.uppercase()?.let { raw ->
            if (raw !in NON_LANGUAGE) {
                val code = alias(raw)
                if (code in KNOWN) return family(code)
            }
        }
        // Prefijos de panel sin separador duro: "ASIA MOVIES", "LATINO 24/7".
        PANEL_PREFIX.find(trimmed)?.groupValues?.get(1)?.uppercase()?.let { raw ->
            if (raw in PANEL) return family(alias(raw))
        }
        // Sufijo: "Series ES", "Peliculas ES", "Documental (UK)".
        SUFFIX.find(name.trimEnd())?.groupValues?.get(1)?.uppercase()?.let { raw ->
            if (raw !in NON_LANGUAGE && raw in KNOWN) return family(raw)
        }
        // Nombre completo en cualquier posición: "NETFLIX GERMANY", "TURKISH".
        WORD.findAll(name).forEach { match ->
            WORDS[match.value.uppercase().foldAccent()]?.let { return it }
        }
        return family(scriptOf(trimmed))
    }

    /** Alias históricos de panel antes de buscar la familia. */
    private fun alias(raw: String): String = when (raw) {
        "USA" -> "US"
        "KURD" -> "KU"
        else -> raw
    }

    private fun family(code: String): String = FAMILY[code] ?: code

    /** Quita la tilde para comparar ("ESPAÑA" → "ESPANA"). */
    private fun String.foldAccent(): String = buildString(length) {
        for (c in this@foldAccent) {
            append(
                when (c) {
                    'Á', 'À', 'Â', 'Ä' -> 'A'
                    'É', 'È', 'Ê', 'Ë' -> 'E'
                    'Í', 'Ì', 'Î', 'Ï' -> 'I'
                    'Ó', 'Ò', 'Ô', 'Ö' -> 'O'
                    'Ú', 'Ù', 'Û', 'Ü' -> 'U'
                    'Ñ' -> 'N'
                    else -> c
                },
            )
        }
    }

    /** Alfabeto dominante como respaldo cuando no hay prefijo de país. */
    private fun scriptOf(text: String): String {
        for (ch in text) {
            when (ch.code) {
                in 0x0600..0x06FF, in 0x0750..0x077F, in 0xFB50..0xFDFF -> return "AR"
                in 0x0400..0x04FF -> return "RU"
                in 0x0590..0x05FF -> return "HE"
                in 0x0370..0x03FF -> return "GR"
                in 0x4E00..0x9FFF -> return "CN"
                in 0x3040..0x30FF -> return "JP"
                in 0xAC00..0xD7AF -> return "KR"
                in 0x0900..0x097F -> return "IN"
                in 0x0E00..0x0E7F -> return "TH"
            }
        }
        return ""
    }
}
