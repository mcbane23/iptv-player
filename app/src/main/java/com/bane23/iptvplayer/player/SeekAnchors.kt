package com.bane23.iptvplayer.player

/**
 * Poznate tačke fajla bez indeksa: „na ovom bajtu je ovaj trenutak snimka”. Iz njih se procenjuje
 * gde u fajlu treba tražiti zadati trenutak. Na početku se zna samo početak (i kraj, kad fajl nosi
 * trajanje); svako premotavanje i svako gledanje dodaju nove tačke, pa procena postaje sve tačnija.
 *
 * Ne zna ništa o plejeru; koristi se sa više niti, pa je sve zaključano.
 */
class SeekAnchors(
    /** Veličina fajla u bajtovima. */
    private val length: Long,
    /** Trajanje zapisano u fajlu, ili [UNKNOWN] kad ga fajl ne nosi. */
    private val fileDurationUs: Long = UNKNOWN,
) {
    private val positions = ArrayList<Long>()
    private val times = ArrayList<Long>()

    init {
        positions.add(0)
        times.add(0)
    }

    /** Dodaje tačku. Tačka koja se ne slaže sa redosledom već poznatih se odbacuje. */
    @Synchronized
    fun add(position: Long, timeUs: Long) {
        if (position <= 0 || position >= length || timeUs <= 0) return
        if (fileDurationUs != UNKNOWN && timeUs >= fileDurationUs) return
        var index = times.binarySearch(timeUs)
        if (index >= 0) return
        index = -index - 1
        // Vreme i mesto u fajlu moraju da rastu zajedno.
        if (positions[index - 1] >= position) return
        if (index < positions.size && positions[index] <= position) return
        if (positions.size >= MAX_ANCHORS) return
        positions.add(index, position)
        times.add(index, timeUs)
    }

    /**
     * Trajanje snimka: ono iz fajla, ili procena iz najdalje poznate tačke (isti prosečan protok do
     * kraja fajla). [UNKNOWN] dok nema nijedne tačke osim početka.
     */
    @Synchronized
    fun durationUs(): Long {
        if (fileDurationUs != UNKNOWN) return fileDurationUs
        val position = positions.last()
        if (position <= 0) return UNKNOWN
        return (times.last().toDouble() * length / position).toLong()
    }

    /** Procena mesta u fajlu za trenutak [timeUs], između dve najbliže poznate tačke. */
    @Synchronized
    fun positionFor(timeUs: Long): Long {
        if (timeUs <= 0) return 0
        val duration = durationUs()
        if (duration == UNKNOWN) return 0
        var index = times.binarySearch(timeUs)
        if (index >= 0) return positions[index]
        index = -index - 1
        val fromPosition = positions[index - 1]
        val fromTime = times[index - 1]
        val toPosition = if (index < positions.size) positions[index] else length
        val toTime = if (index < times.size) times[index] else duration
        if (toTime <= fromTime) return fromPosition
        val share = ((timeUs - fromTime).toDouble() / (toTime - fromTime)).coerceIn(0.0, 1.0)
        return (fromPosition + (toPosition - fromPosition) * share).toLong().coerceIn(0, length - 1)
    }

    companion object {
        const val UNKNOWN = Long.MIN_VALUE + 1
        private const val MAX_ANCHORS = 4_000
    }
}

/** Traženje početka sledećeg bloka (Cluster) u matroska fajlu i čitanje njegovog vremena. */
object MkvClusters {
    /** Koliko bajtova od početka bloka treba da bi se blok prepoznao i pročitalo mu se vreme. */
    const val HEADER_BYTES = 4 + 8 + 1 + 1 + 8

    /** Podrazumevana jedinica vremena u matroska fajlu: milisekunda, u nanosekundama. */
    const val DEFAULT_TIMECODE_SCALE_NS = 1_000_000L

    private const val ID_TIMECODE = 0xE7

    /** Elementi kojima blok sme da počne: vreme, kontrolni zbir, praznina, mesto, veličina prethodnog. */
    private val FIRST_CHILD = intArrayOf(ID_TIMECODE, 0xBF, 0xEC, 0xA7, 0xAB)

    /**
     * Mesto prvog bloka u [buffer] (gleda se prvih [length] bajtova), ili -1 kad ga nema. Blok čiji
     * početak nije ceo u baferu se ne računa: pozivalac zadržava poslednjih [HEADER_BYTES] bajtova
     * i traži dalje.
     */
    fun find(buffer: ByteArray, length: Int): Int {
        var i = 0
        val last = length - HEADER_BYTES
        while (i <= last) {
            if (buffer[i] == 0x1F.toByte() && buffer[i + 1] == 0x43.toByte() &&
                buffer[i + 2] == 0xB6.toByte() && buffer[i + 3] == 0x75.toByte()
            ) {
                val sizeLength = varintLength(buffer[i + 4])
                if (sizeLength > 0 && (buffer[i + 4 + sizeLength].toInt() and 0xFF) in FIRST_CHILD) return i
            }
            i++
        }
        return -1
    }

    /**
     * Vreme bloka koji počinje na [offset], u jedinicama fajla, ili -1 kad blok ne počinje vremenom.
     * Od [offset] mora biti bar [HEADER_BYTES] bajtova.
     */
    fun timecode(buffer: ByteArray, offset: Int): Long {
        val sizeLength = varintLength(buffer[offset + 4])
        if (sizeLength <= 0) return -1
        var at = offset + 4 + sizeLength
        if ((buffer[at].toInt() and 0xFF) != ID_TIMECODE) return -1
        at++
        val bytes = (buffer[at].toInt() and 0xFF) - 0x80
        if (bytes !in 1..8) return -1
        at++
        var value = 0L
        repeat(bytes) { value = (value shl 8) or (buffer[at + it].toLong() and 0xFF) }
        return if (value < 0) -1 else value
    }

    /**
     * Jedinica vremena fajla (TimecodeScale, u nanosekundama) iz zaglavlja, ili podrazumevana kad
     * zaglavlje je ne navodi.
     */
    fun timecodeScaleNs(header: ByteArray, length: Int): Long {
        var i = 0
        while (i + 4 < length) {
            if (header[i] == 0x2A.toByte() && header[i + 1] == 0xD7.toByte() && header[i + 2] == 0xB1.toByte()) {
                val bytes = (header[i + 3].toInt() and 0xFF) - 0x80
                if (bytes in 1..8 && i + 4 + bytes <= length) {
                    var value = 0L
                    repeat(bytes) { value = (value shl 8) or (header[i + 4 + it].toLong() and 0xFF) }
                    if (value in 1..1_000_000_000L) return value
                }
            }
            i++
        }
        return DEFAULT_TIMECODE_SCALE_NS
    }

    /** Dužina zapisa veličine elementa (1 do 8 bajtova) prema prvom bajtu, ili 0 kad nije ispravan. */
    private fun varintLength(first: Byte): Int {
        val value = first.toInt() and 0xFF
        if (value == 0) return 0
        return Integer.numberOfLeadingZeros(value) - 24 + 1
    }
}

/**
 * Odluka posle sletanja na blok tokom približnog premotavanja: da li se nastavlja odatle ili se
 * traži bliže mesto.
 */
object SeekLanding {
    /** Sletanje do ovoliko pre traženog trenutka je dovoljno dobro (plejer taj deo samo preleti). */
    const val EARLY_US = 8_000_000L

    /** Sletanje do ovoliko posle traženog trenutka je dovoljno dobro. */
    const val LATE_US = 3_000_000L

    fun isCloseEnough(landedUs: Long, targetUs: Long): Boolean =
        landedUs >= targetUs - EARLY_US && landedUs <= targetUs + LATE_US
}
