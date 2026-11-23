package com.bane23.iptvplayer.player

/** Koliko sadržaja plejer učita unapred. Manji bafer brže kreće, veći bolje podnosi slabu vezu. */
enum class BufferSize { SMALL, NORMAL, LARGE }

/** Koji dekoder plejer bira prvi. */
enum class DecoderMode {
    /** Hardverski dekoder uređaja, a softverski samo ako hardverski ne može. */
    AUTO,

    /** Prvo softverski dekoder; pomaže kad hardverski daje crnu ili iskidanu sliku. */
    SOFTWARE,

    /**
     * Hardverski dekoder u tunel režimu: uređaj sam usklađuje sliku sa zvukom, umesto plejera.
     * Pomaže na TV uređajima gde slika zaostaje za zvukom; ako uređaj to ne podržava, radi kao [AUTO].
     */
    TUNNEL,
}

/** Veličina slova titlova, kao deo visine slike. */
enum class SubtitleSize(val fraction: Float) { SMALL(0.04f), NORMAL(0.0533f), LARGE(0.07f), XLARGE(0.09f) }

/** Položaj titlova: koliko su podignuti od dna slike, kao deo njene visine. */
enum class SubtitlePosition(val bottomFraction: Float) { LOW(0.08f), MIDDLE(0.18f), HIGH(0.3f) }

/** Podešavanja plejera. Važe za kanale, filmove i serije, na telefonu i TV-u. */
data class PlayerSettings(
    val buffer: BufferSize = BufferSize.NORMAL,
    val decoder: DecoderMode = DecoderMode.AUTO,
    /**
     * Jezici zvuka po redu važnosti, kako stoje u podešavanjima (prvi, drugi, treći; npr. "sr").
     * Prazno mesto znači da jezik nije izabran. Bez ijednog jezika ostaje zvuk koji strim sam ponudi.
     */
    val audioLanguageSlots: List<String> = emptyList(),
    /**
     * Jezici titlova po redu važnosti, na isti način. Uključuje se prvi koji sadržaj ima; bez
     * ijednog poklapanja titlovi ostaju isključeni dok ih korisnik ne uključi u plejeru.
     */
    val subtitleLanguageSlots: List<String> = emptyList(),
    /** Za koliko sekundi dugmad napred i nazad premotaju film ili epizodu. */
    val seekStepSeconds: Int = DEFAULT_SEEK_STEP_SECONDS,
    /** Crna pozadina iza titlova; bez nje slova imaju samo crnu ivicu. */
    val subtitleBackground: Boolean = true,
    val subtitleSize: SubtitleSize = SubtitleSize.NORMAL,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.LOW,
    /** Izjednačavanje jačine zvuka između kanala, filmova i serija. */
    val loudness: LoudnessLevel = LoudnessLevel.LIGHT,
) {
    /** Izabrani jezici zvuka po redu važnosti, bez praznih mesta i ponavljanja. */
    val audioLanguages: List<String> get() = languagePriority(audioLanguageSlots)

    /** Izabrani jezici titlova po redu važnosti, bez praznih mesta i ponavljanja. */
    val subtitleLanguages: List<String> get() = languagePriority(subtitleLanguageSlots)

    companion object {
        const val DEFAULT_SEEK_STEP_SECONDS = 10

        /** Koliko jezika po redu važnosti može da se izabere za titlove, odnosno zvuk. */
        const val LANGUAGE_SLOTS = 3

        /** Mesta iz podešavanja kao spisak jezika po redu važnosti. */
        fun languagePriority(slots: List<String>): List<String> =
            slots.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        /**
         * Čita mesta iz zapisa u podešavanjima (jezici razdvojeni zarezom, prazno mesto ostaje prazno).
         * Stariji zapis sa jednim jezikom postaje prvo mesto.
         */
        fun parseLanguageSlots(raw: String?): List<String> =
            raw.orEmpty().split(',').map { it.trim() }.take(LANGUAGE_SLOTS).dropLastWhile { it.isEmpty() }

        /** Zapis mesta za podešavanja; obrnuto od [parseLanguageSlots]. */
        fun formatLanguageSlots(slots: List<String>): String =
            slots.map { it.trim().replace(",", "") }.take(LANGUAGE_SLOTS).dropLastWhile { it.isEmpty() }.joinToString(",")

        /** Mesta sa jezikom [language] na mestu [index] (od nule); prazan jezik prazni to mesto. */
        fun withLanguageSlot(slots: List<String>, index: Int, language: String): List<String> {
            if (index !in 0 until LANGUAGE_SLOTS) return slots
            val padded = (slots + List(LANGUAGE_SLOTS) { "" }).take(LANGUAGE_SLOTS).toMutableList()
            padded[index] = language.trim()
            return padded.dropLastWhile { it.isEmpty() }
        }

        /** Vrednosti koje se nude u podešavanjima. */
        val SEEK_STEP_OPTIONS = listOf(5, 10, 15, 30, 60)
    }
}
