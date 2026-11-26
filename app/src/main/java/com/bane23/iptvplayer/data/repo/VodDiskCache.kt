package com.bane23.iptvplayer.data.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.encodeToStream

/** Sačuvan spisak i vreme osvežavanja liste kada je preuzet sa servera. */
class VodCached<T>(val stamp: Long, val value: T)

/**
 * Spiskovi kategorija, filmova i serija sačuvani na uređaju, da se Filmovi i Serije posle pokretanja
 * aplikacije otvaraju odmah, bez čekanja na server. Uz svaki spisak se pamti vreme osvežavanja liste;
 * kad se lista osveži (dnevno ili ručno), sačuvani spisak se i dalje prikazuje dok se novi ne preuzme.
 * Fajlovi su u privatnom delu aplikacije (ne brišu se brisanjem keša), sažeti.
 */
@Singleton
class VodDiskCache internal constructor(private val root: File) {

    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, DIR))

    fun readCategories(playlistId: Long, kind: VodKind): VodCached<List<VodCategory>>? =
        read(file(playlistId, kind, CATEGORIES)) { input -> decodeCategories(input) }

    fun writeCategories(playlistId: Long, kind: VodKind, stamp: Long, categories: List<VodCategory>) =
        write(file(playlistId, kind, CATEGORIES), stamp) { output -> encodeCategories(categories, output) }

    fun readItems(playlistId: Long, kind: VodKind, categoryId: String): VodCached<List<VodItem>>? =
        read(file(playlistId, kind, itemsName(categoryId))) { input -> decodeItems(input) }

    fun writeItems(playlistId: Long, kind: VodKind, categoryId: String, stamp: Long, items: List<VodItem>) =
        write(file(playlistId, kind, itemsName(categoryId)), stamp) { output -> encodeItems(items, output) }

    /**
     * Red „nedavno dodato” sa „Početne”: dvadesetak stavki, pa se čita trenutno, dok ceo spisak liste
     * ima desetine hiljada. [signature] opisuje sakrivene i zaključane kategorije u času upisa.
     */
    fun readAdded(playlistId: Long, kind: VodKind): VodCached<List<VodItem>>? =
        read(file(playlistId, kind, ADDED)) { input -> decodeItems(input) }

    fun writeAdded(playlistId: Long, kind: VodKind, signature: Long, items: List<VodItem>) =
        write(file(playlistId, kind, ADDED), signature) { output -> encodeItems(items, output) }

    /**
     * Filmovi, odnosno serije, iz reda „Nastavi gledanje” sa TMDB ID-jem i kategorijom, da se jezičke
     * verzije posle pokretanja spoje bez čitanja celog spiska liste.
     */
    fun readVersions(playlistId: Long, kind: VodKind): VodCached<List<VodItem>>? =
        read(file(playlistId, kind, VERSIONS)) { input -> decodeItems(input) }

    fun writeVersions(playlistId: Long, kind: VodKind, items: List<VodItem>) =
        write(file(playlistId, kind, VERSIONS), 0L) { output -> encodeItems(items, output) }

    /** Briše sačuvane spiskove jedne liste (lista obrisana ili promenjena). */
    fun clear(playlistId: Long) {
        File(root, playlistId.toString()).deleteRecursively()
    }

    /** Briše sve sačuvane spiskove (opcija isključena). */
    fun clearAll() {
        root.deleteRecursively()
    }

    private fun file(playlistId: Long, kind: VodKind, name: String) =
        File(File(root, playlistId.toString()), "${kind.name.lowercase()}-$name")

    private fun itemsName(categoryId: String) =
        if (categoryId.isEmpty()) "items-all" else "items-c" + URLEncoder.encode(categoryId, "UTF-8")

    private fun <T> read(file: File, decode: (InputStream) -> T): VodCached<T>? {
        if (!file.isFile) return null
        return try {
            DataInputStream(BufferedInputStream(file.inputStream())).use { data ->
                // Drugi zapis (starija verzija aplikacije) ili nešto što nije ovaj fajl.
                if (data.readInt() != VERSION) throw java.io.IOException("unknown format")
                val stamp = data.readLong()
                VodCached(stamp, decode(GZIPInputStream(data)))
            }
        } catch (_: Exception) {
            // Oštećen, nedovršen ili zastareo fajl: briše se, a spisak se preuzima sa servera.
            file.delete()
            null
        }
    }

    private fun write(file: File, stamp: Long, encode: (OutputStream) -> Unit) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { data ->
                data.writeInt(VERSION)
                data.writeLong(stamp)
                val zip = GZIPOutputStream(data)
                encode(zip)
                zip.finish()
            }
            if (!temp.renameTo(file)) {
                file.delete()
                if (!temp.renameTo(file)) temp.delete()
            }
        } catch (_: Exception) {
            // Pun disk i slično: spisak radi i bez čuvanja, samo se sledeći put preuzima iznova.
        }
    }

    internal companion object {
        const val DIR = "vod-cache"
        const val CATEGORIES = "categories"
        const val ADDED = "added"
        const val VERSIONS = "continue-versions"

        /** Menja se kad se promeni zapis, pa se stari fajlovi samo zanemare. */
        const val VERSION = 1

        private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        @OptIn(ExperimentalSerializationApi::class)
        fun encodeItems(items: List<VodItem>, output: OutputStream) =
            json.encodeToStream(ListSerializer(StoredItem.serializer()), items.map { it.stored() }, output)

        @OptIn(ExperimentalSerializationApi::class)
        fun decodeItems(input: InputStream): List<VodItem> {
            val items = ArrayList<VodItem>()
            val categoryIds = HashMap<String, String>()
            for (s in json.decodeToSequence(input, StoredItem.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)) {
                items.add(
                    VodItem(
                        id = s.i,
                        name = s.n,
                        poster = s.p,
                        rating = s.r,
                        extension = s.e,
                        tmdbId = s.t,
                        added = s.a,
                        year = s.y,
                        categoryId = s.c?.let { categoryIds.getOrPut(it) { it } },
                    ),
                )
            }
            items.trimToSize()
            return items
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun encodeCategories(categories: List<VodCategory>, output: OutputStream) =
            json.encodeToStream(ListSerializer(StoredCategory.serializer()), categories.map { StoredCategory(it.id, it.name) }, output)

        @OptIn(ExperimentalSerializationApi::class)
        fun decodeCategories(input: InputStream): List<VodCategory> =
            json.decodeToSequence(input, StoredCategory.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
                .map { VodCategory(it.i, it.n) }
                .toList()

        private fun VodItem.stored() = StoredItem(id, name, poster, rating, extension, tmdbId, added, year, categoryId)
    }
}

/** Zapis filma ili serije na disku; kratki nazivi polja jer spisak može imati desetine hiljada stavki. */
@Serializable
internal class StoredItem(
    val i: Long,
    val n: String,
    val p: String? = null,
    val r: String? = null,
    val e: String? = null,
    val t: Long? = null,
    val a: Long = 0,
    val y: Int = 0,
    val c: String? = null,
)

@Serializable
internal class StoredCategory(val i: String, val n: String)
