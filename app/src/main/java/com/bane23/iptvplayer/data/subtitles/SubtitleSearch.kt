package com.bane23.iptvplayer.data.subtitles

/** Šta se traži: film ili epizoda. [tmdbId] je ID filma, odnosno serije kad su zadati [season] i [episode]. */
data class SubtitleQuery(
    val title: String,
    val tmdbId: Long? = null,
    val season: Int? = null,
    val episode: Int? = null,
) {
    val isEpisode: Boolean get() = season != null && episode != null
}

/** Jedan titl iz pretrage. [fileId] ide u zahtev za preuzimanje. */
data class SubtitleResult(
    val fileId: Long,
    /** Jezik kako ga šalje OpenSubtitles (npr. "sr", "pt-br"). */
    val language: String,
    /** Naziv izdanja za koje je titl pravljen, ili naziv fajla. */
    val name: String,
    val downloads: Long,
    val hearingImpaired: Boolean,
    val machineTranslated: Boolean,
)

/** Čista logika pretrage titlova: sređivanje naziva, izbor jezika i redosled rezultata. */
object SubtitleSearch {

    private val BRACKETS = Regex("""\[[^\]]*\]|\{[^}]*\}""")
    private val YEAR = Regex("""\(?\b((?:19|20)\d{2})\b\)?""")

    /** Oznaka na početku naziva kakvu liste dodaju: "EN - ", "EX-YU | ", "4K: ". */
    private val PREFIX = Regex("""^\s*[A-Z0-9]{2,6}(?:-[A-Z0-9]{2,4})?\s*[-|:]\s+""")
    private val QUALITY = Regex("""\b(?:4K|UHD|FHD|HD|SD|HEVC|H265|H264|X265|X264|1080P|720P|2160P|MULTI|MULTISUB|DUAL)\b""", RegexOption.IGNORE_CASE)
    private val EPISODE_MARK = Regex("""\bS\d{1,2}\s*E\d{1,3}\b.*$""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    /** Naziv bez oznaka liste i godina iz naziva, ako je ima. */
    data class CleanTitle(val title: String, val year: Int?)

    /**
     * Nazivi u listama često imaju dodatke ("EN - Naziv (2020) [4K]"). Za pretragu ostaje samo
     * naziv, a godina se izdvaja posebno. Ako posle čišćenja ne ostane ništa, vraća se izvorni naziv.
     */
    fun cleanTitle(raw: String): CleanTitle {
        var text = BRACKETS.replace(raw, " ")
        text = PREFIX.replace(text, "")
        text = EPISODE_MARK.replace(text, "")
        // Godina je poslednja koja se pominje; naziv može i sam da bude godina ("1917 (2019)").
        val yearMatch = YEAR.findAll(text).lastOrNull()?.takeIf { it.range.first > 0 }
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        if (yearMatch != null) text = text.removeRange(yearMatch.range)
        text = QUALITY.replace(text, " ")
        text = text.replace("()", " ").replace(SPACES, " ").trim().trim('-', '|', ':', '.').trim()
        return if (text.isEmpty()) CleanTitle(raw.trim(), null) else CleanTitle(text, year)
    }

    /**
     * Jezici koji se traže, po redu važnosti: [preferred] (iz podešavanja titlova), jezik uređaja
     * [device], pa engleski. Uz srpski, hrvatski ili bosanski idu i druga dva, jer su titlovi
     * međusobno razumljivi, a često postoji samo jedan od njih.
     */
    fun languages(preferred: String, device: String): List<String> {
        val result = LinkedHashSet<String>()
        for (code in listOf(preferred, device)) {
            val language = code.trim().lowercase().substringBefore('-').substringBefore('_')
            if (language.isEmpty()) continue
            if (language in SOUTH_SLAVIC) {
                result.add(language)
                result.addAll(SOUTH_SLAVIC)
            } else {
                result.add(language)
            }
        }
        result.add("en")
        return result.toList()
    }

    /** Rezultati pretrage: po redu jezika iz [languages], pa po broju preuzimanja; mašinski prevodi na kraju. */
    fun results(response: OsSearchResponse, languages: List<String>): List<SubtitleResult> {
        val seen = HashSet<Long>()
        return response.data.mapNotNull { item ->
            val attributes = item.attributes ?: return@mapNotNull null
            val file = attributes.files.firstOrNull { it.fileId != null } ?: return@mapNotNull null
            val fileId = file.fileId ?: return@mapNotNull null
            if (!seen.add(fileId)) return@mapNotNull null
            SubtitleResult(
                fileId = fileId,
                language = attributes.language?.lowercase().orEmpty(),
                name = attributes.release?.trim()?.takeIf { it.isNotEmpty() }
                    ?: file.fileName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: fileId.toString(),
                downloads = attributes.downloadCount ?: 0,
                hearingImpaired = attributes.hearingImpaired == true,
                machineTranslated = attributes.aiTranslated == true || attributes.machineTranslated == true,
            )
        }.sortedWith(
            compareBy<SubtitleResult> { result ->
                languages.indexOf(result.language.substringBefore('-')).let { if (it < 0) languages.size else it }
            }.thenBy { it.machineTranslated }.thenByDescending { it.downloads },
        )
    }

    private val SOUTH_SLAVIC = listOf("sr", "hr", "bs")
}
