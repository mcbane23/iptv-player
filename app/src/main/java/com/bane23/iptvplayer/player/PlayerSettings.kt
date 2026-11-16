package com.bane23.iptvplayer.player

/** Koliko sadržaja plejer učita unapred. Manji bafer brže kreće, veći bolje podnosi slabu vezu. */
enum class BufferSize { SMALL, NORMAL, LARGE }

/** Koji dekoder plejer bira prvi. */
enum class DecoderMode {
    /** Hardverski dekoder uređaja, a softverski samo ako hardverski ne može. */
    AUTO,

    /** Prvo softverski dekoder; pomaže kad hardverski daje crnu ili iskidanu sliku. */
    SOFTWARE,
}

/** Veličina slova titlova, kao deo visine slike. */
enum class SubtitleSize(val fraction: Float) { SMALL(0.04f), NORMAL(0.0533f), LARGE(0.07f), XLARGE(0.09f) }

/** Položaj titlova: koliko su podignuti od dna slike, kao deo njene visine. */
enum class SubtitlePosition(val bottomFraction: Float) { LOW(0.08f), MIDDLE(0.18f), HIGH(0.3f) }

/** Podešavanja plejera. Važe za kanale, filmove i serije, na telefonu i TV-u. */
data class PlayerSettings(
    val buffer: BufferSize = BufferSize.NORMAL,
    val decoder: DecoderMode = DecoderMode.AUTO,
    /** Jezik zvuka koji se bira kad ih ima više (npr. "sr"); prazno = onaj koji strim sam ponudi. */
    val audioLanguage: String = "",
    /** Jezik titlova koji se sam uključuje; prazno = titlovi su isključeni dok ih korisnik ne uključi. */
    val subtitleLanguage: String = "",
    /** Za koliko sekundi dugmad napred i nazad premotaju film ili epizodu. */
    val seekStepSeconds: Int = DEFAULT_SEEK_STEP_SECONDS,
    /** Crna pozadina iza titlova; bez nje slova imaju samo crnu ivicu. */
    val subtitleBackground: Boolean = true,
    val subtitleSize: SubtitleSize = SubtitleSize.NORMAL,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.LOW,
) {
    companion object {
        const val DEFAULT_SEEK_STEP_SECONDS = 10

        /** Vrednosti koje se nude u podešavanjima. */
        val SEEK_STEP_OPTIONS = listOf(5, 10, 15, 30, 60)
    }
}
