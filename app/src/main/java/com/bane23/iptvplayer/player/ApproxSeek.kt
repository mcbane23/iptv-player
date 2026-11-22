package com.bane23.iptvplayer.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.SniffFailure
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mkv.MatroskaExtractor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Šta se zna o premotavanju sadržaja koji se sada pušta; ispisuje se u informacijama o strimu. */
enum class SeekKind {
    /** Fajl ima indeks (ili nije matroska): premotava se obično. */
    NORMAL,

    /** Fajl nema indeks; mesto se procenjuje. */
    APPROXIMATE,

    /** Fajl nema ni indeks ni trajanje; trajanje se još meri, pa premotavanje stiže za koji trenutak. */
    MEASURING,

    /** Server ne javlja veličinu fajla, pa mesto ne može da se proceni. */
    NO_SIZE,

    /** Server ne dozvoljava čitanje fajla od sredine. */
    NO_RANGE,
}

/**
 * Zajedničko stanje približnog premotavanja jednog plejera: deli ga čitač fajla, provera odgovora
 * servera i sam plejer. Čita se i piše sa više niti.
 */
class ApproxSeekState {
    /** Stanje premotavanja sadržaja koji se sada pušta. */
    @Volatile
    var kind = SeekKind.NORMAL

    /** Adrese kod kojih server nije dozvolio čitanje od sredine; kod njih se više ne pokušava. */
    val refused: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Poznate tačke po adresi, da i posle ponovne pripreme istog sadržaja procena ostane tačna. */
    val anchors = ConcurrentHashMap<String, SeekAnchors>()
}

/** Server je na zahtev „od ovog bajta” poslao ceo fajl od početka: čitanje od sredine ne radi. */
class RangeIgnoredException : RuntimeException("Server ignored the Range request")

/**
 * Proverava odgovor servera kad se fajl bez indeksa traži od sredine. Plejer bi inače sam pročitao
 * i odbacio sve do traženog mesta, što kod sporog servera traje minutima.
 */
@OptIn(UnstableApi::class)
class RangeGuard(private val state: ApproxSeekState) : TransferListener {
    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (state.kind != SeekKind.APPROXIMATE || dataSpec.position <= 0) return
        if (source is HttpDataSource && source.responseCode == 200) throw RangeIgnoredException()
    }

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) = Unit

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
}

/** Čitači fajlova kao i obično, s tim što matroska čitač ume približno da premota fajl bez indeksa. */
@OptIn(UnstableApi::class)
class ApproxSeekExtractorsFactory(
    private val delegate: ExtractorsFactory,
    private val state: ApproxSeekState,
) : ExtractorsFactory {
    override fun createExtractors(): Array<Extractor> = delegate.createExtractors()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        val key = uri.toString()
        val noRanges = responseHeaders.any { (name, values) ->
            name.equals("Accept-Ranges", ignoreCase = true) && values.any { it.trim().equals("none", ignoreCase = true) }
        }
        val allowed = !noRanges && key !in state.refused
        state.kind = SeekKind.NORMAL
        return delegate.createExtractors(uri, responseHeaders)
            .map { if (it is MatroskaExtractor) ApproxSeekExtractor(it, key, state, allowed) else it }
            .toTypedArray()
    }
}

/**
 * Omotač oko matroska čitača. Fajl sa indeksom prolazi netaknut. Za fajl bez indeksa plejeru se
 * umesto „ne može da se premotava” daje procena mesta ([SeekAnchors]); posle skoka se u podacima
 * nađe početak prvog bloka, pročita mu se vreme i, ako je daleko od traženog, skoči se još jednom,
 * bliže. Tek od bloka koji je dovoljno blizu podaci idu pravom čitaču.
 */
@OptIn(UnstableApi::class)
internal class ApproxSeekExtractor(
    private val delegate: Extractor,
    private val key: String,
    private val state: ApproxSeekState,
    /** Server nije unapred odbio čitanje od sredine. */
    private val allowed: Boolean,
) : Extractor {

    private var output: ExtractorOutput? = null
    private var length = C.LENGTH_UNSET.toLong()
    private var timecodeScaleNs = MkvClusters.DEFAULT_TIMECODE_SCALE_NS
    private var headerRead = false

    /** Pravi čitač je javio da fajl nema indeks. */
    private var indexMissing = false
    private var anchors: SeekAnchors? = null

    /** Trajanje javljeno plejeru uz procenu mesta, ili [C.TIME_UNSET] dok procena nije javljena. */
    private var announcedDurationUs = C.TIME_UNSET

    /** Posle skoka: trenutak koji se traži, dok se ne nađe blok od kog se nastavlja. */
    private var syncTargetUs = C.TIME_UNSET
    private var corrections = 0
    private var lastSampleTimeUs = C.TIME_UNSET
    private var lastAnchorTimeUs = 0L
    private val scan = ByteArray(SCAN_BYTES)

    override fun sniff(input: ExtractorInput): Boolean = delegate.sniff(input)

    override fun getSniffFailureDetails(): List<SniffFailure> = delegate.sniffFailureDetails

    override fun getUnderlyingImplementation(): Extractor = delegate.underlyingImplementation

    override fun init(output: ExtractorOutput) {
        this.output = output
        delegate.init(Output(output))
    }

    override fun release() = delegate.release()

    override fun seek(position: Long, timeUs: Long) {
        delegate.seek(position, timeUs)
        lastSampleTimeUs = C.TIME_UNSET
        lastAnchorTimeUs = timeUs
        corrections = 0
        // Skok na sam početak čita fajl redom od zaglavlja; svaki drugi sleće usred podataka.
        syncTargetUs = if (announcedDurationUs != C.TIME_UNSET && position > 0) timeUs else C.TIME_UNSET
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        length = input.length
        if (!headerRead && input.position == 0L) readHeader(input)
        if (syncTargetUs != C.TIME_UNSET) {
            when (sync(input, seekPosition)) {
                Sync.END -> return Extractor.RESULT_END_OF_INPUT
                Sync.SEEK -> return Extractor.RESULT_SEEK
                Sync.READY -> syncTargetUs = C.TIME_UNSET
            }
        }
        val result = delegate.read(input, seekPosition)
        if (indexMissing && result == Extractor.RESULT_CONTINUE) learn(input.position)
        return result
    }

    /** Jedinica vremena fajla stoji u zaglavlju; treba za čitanje vremena blokova posle skoka. */
    private fun readHeader(input: ExtractorInput) {
        headerRead = true
        val header = ByteArray(HEADER_SCAN_BYTES)
        val filled = peek(input, header)
        input.resetPeekPosition()
        timecodeScaleNs = MkvClusters.timecodeScaleNs(header, filled)
    }

    private enum class Sync { READY, SEEK, END }

    /**
     * Traži početak bloka od trenutnog mesta. Kad je vreme bloka daleko od traženog, traži bliže
     * mesto: malo unapred samo preskoči podatke, inače traži novi zahtev serveru ([Sync.SEEK]).
     */
    private fun sync(input: ExtractorInput, seekPosition: PositionHolder): Sync {
        val known = anchors
        while (true) {
            input.resetPeekPosition()
            val filled = peek(input, scan)
            input.resetPeekPosition()
            val at = MkvClusters.find(scan, filled)
            if (at < 0) {
                if (filled < scan.size) return Sync.END
                input.skipFully(filled - MkvClusters.HEADER_BYTES)
                continue
            }
            input.skipFully(at)
            val position = input.position
            val timecode = MkvClusters.timecode(scan, at)
            if (timecode < 0 || known == null) return Sync.READY
            val timeUs = timecode * timecodeScaleNs / 1_000
            known.add(position, timeUs)
            announceIfChanged()
            if (SeekLanding.isCloseEnough(timeUs, syncTargetUs) || corrections >= MAX_CORRECTIONS) return Sync.READY
            corrections++
            val better = known.positionFor(syncTargetUs)
            when {
                // Procena pada u isti blok: bliže od ovoga se ne može.
                abs(better - position) < MIN_CORRECTION_BYTES -> return Sync.READY
                better > position && better - position <= SKIP_AHEAD_BYTES -> input.skipFully((better - position).toInt())
                else -> {
                    seekPosition.position = better
                    return Sync.SEEK
                }
            }
        }
    }

    /** Puni [buffer] podacima koji slede, bez pomeranja mesta čitanja; vraća koliko ih je stiglo. */
    private fun peek(input: ExtractorInput, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = input.peek(buffer, filled, buffer.size - filled)
            if (read == C.RESULT_END_OF_INPUT) break
            filled += read
        }
        return filled
    }

    /** Tokom običnog gledanja povremeno pamti dokle se u fajlu stiglo za koje vreme. */
    private fun learn(position: Long) {
        val timeUs = lastSampleTimeUs
        if (timeUs == C.TIME_UNSET || timeUs - lastAnchorTimeUs < ANCHOR_EVERY_US) return
        lastAnchorTimeUs = timeUs
        anchors?.add(position, timeUs)
        announceIfChanged()
    }

    /** Fajl nema indeks: od sada se mesto procenjuje, ako server to dozvoljava i javlja veličinu fajla. */
    private fun startApproximate(fileDurationUs: Long) {
        if (anchors != null) return
        if (!allowed) {
            state.kind = SeekKind.NO_RANGE
            return
        }
        if (length <= 0) {
            state.kind = SeekKind.NO_SIZE
            return
        }
        // Tačke iz ranijeg puštanja istog sadržaja važe samo ako je fajl i dalje iste veličine.
        val key = "$key#$length#$fileDurationUs"
        anchors = state.anchors.getOrPut(key) { SeekAnchors(length, fileDurationUs) }
        state.kind = SeekKind.MEASURING
    }

    /** Javlja plejeru procenu kad se prvi put zna trajanje i kad se procenjeno trajanje primetno promeni. */
    private fun announceIfChanged() {
        val known = anchors ?: return
        val durationUs = known.durationUs()
        if (durationUs == SeekAnchors.UNKNOWN) return
        val announced = announcedDurationUs
        if (announced != C.TIME_UNSET && abs(durationUs - announced) <= announced / DURATION_CHANGE_SHARE) return
        announcedDurationUs = durationUs
        state.kind = SeekKind.APPROXIMATE
        output?.seekMap(ApproxSeekMap(known, durationUs))
    }

    private inner class Output(private val real: ExtractorOutput) : ExtractorOutput {
        override fun track(id: Int, type: Int): TrackOutput {
            val track = real.track(id, type)
            return if (type == C.TRACK_TYPE_VIDEO || type == C.TRACK_TYPE_AUDIO) Track(track) else track
        }

        override fun endTracks() = real.endTracks()

        override fun seekMap(seekMap: SeekMap) {
            if (seekMap.isSeekable) {
                real.seekMap(seekMap)
                return
            }
            indexMissing = true
            val fileDurationUs = seekMap.durationUs.takeIf { it != C.TIME_UNSET && it > 0 } ?: SeekAnchors.UNKNOWN
            startApproximate(fileDurationUs)
            // Dok se trajanje ne zna (ili procena nije moguća) važi ono što je javio pravi čitač.
            if (anchors?.durationUs().let { it == null || it == SeekAnchors.UNKNOWN }) real.seekMap(seekMap)
            announceIfChanged()
        }
    }

    /** Prosleđuje sve pravom izlazu i usput pamti vreme poslednjeg uzorka slike ili zvuka. */
    private inner class Track(private val real: TrackOutput) : TrackOutput {
        override fun durationUs(durationUs: Long) = real.durationUs(durationUs)

        override fun format(format: Format) = real.format(format)

        override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int =
            real.sampleData(input, length, allowEndOfInput, sampleDataPart)

        override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) =
            real.sampleData(data, length, sampleDataPart)

        override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
            lastSampleTimeUs = timeUs
            real.sampleMetadata(timeUs, flags, size, offset, cryptoData)
        }
    }

    private companion object {
        const val SCAN_BYTES = 64 * 1024
        const val HEADER_SCAN_BYTES = 8 * 1024

        /** Najviše ovoliko puta se posle skoka traži bliže mesto; svaki put je novi zahtev serveru. */
        const val MAX_CORRECTIONS = 4
        const val MIN_CORRECTION_BYTES = 128 * 1024

        /** Do ovoliko unapred je brže pročitati i odbaciti podatke nego slati novi zahtev. */
        const val SKIP_AHEAD_BYTES = 2 * 1024 * 1024
        const val ANCHOR_EVERY_US = 20_000_000L

        /** Procenjeno trajanje se javlja ponovo kad se promeni za više od jednog ovolikog dela. */
        const val DURATION_CHANGE_SHARE = 50
    }
}

/** Procena mesta u fajlu bez indeksa, za plejer. */
@OptIn(UnstableApi::class)
internal class ApproxSeekMap(private val anchors: SeekAnchors, private val durationUs: Long) : SeekMap {
    override fun isSeekable(): Boolean = true

    override fun getDurationUs(): Long = durationUs

    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val time = timeUs.coerceIn(0, durationUs)
        return SeekMap.SeekPoints(SeekPoint(time, anchors.positionFor(time)))
    }
}
