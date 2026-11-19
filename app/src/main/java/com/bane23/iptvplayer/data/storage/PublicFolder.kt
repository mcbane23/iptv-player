package com.bane23.iptvplayer.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException

/**
 * Javni folder aplikacije, Downloads/Glowbox: fajlovi u njemu se vide u svakom upravniku fajlova i
 * ostaju posle deinstalacije. Od Androida 11 aplikacija tu piše svoje fajlove bez dozvole; na
 * starijim uređajima treba dozvola za memoriju, a bez nje se koristi folder aplikacije kao ranije.
 */
object PublicFolder {
    const val NAME = "Glowbox"

    /** Na ovom uređaju pisanje u Downloads traži dozvolu koju korisnik mora da odobri. */
    val needsPermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    fun hasPermission(context: Context): Boolean = !needsPermission ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /**
     * Downloads/Glowbox, odnosno njegov podfolder [sub]; pravi ga ako ne postoji. Vraća null kad
     * memorija nije dostupna, nema dozvole ili folder ne može da se napravi.
     */
    fun directory(context: Context, sub: String? = null): File? {
        if (!hasPermission(context)) return null
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return null
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val root = File(downloads, NAME)
        val dir = if (sub.isNullOrEmpty()) root else File(root, sub)
        return try {
            dir.takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * Fajl u [dir] koji još ne postoji. Fajl istog imena može da ostane od ranije instalacije, a
     * njega ova instalacija ne sme ni da menja ni da briše, pa se tada imenu dodaje redni broj.
     * Uz [claim] se fajl odmah i pravi (prazan), da se vidi da li sistem dozvoljava to ime.
     */
    fun freeFile(dir: File, baseName: String, extension: String, claim: Boolean = true): File {
        var file = File(dir, "$baseName.$extension")
        var number = 2
        while (number < MAX_COPIES) {
            val free = try {
                if (claim) file.createNewFile() else !file.exists()
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
            if (free) return file
            file = File(dir, "$baseName ($number).$extension")
            number++
        }
        return file
    }

    /**
     * Prenosi [from] u [dir] pod imenom [baseName].[extension] (ili sa rednim brojem ako je zauzeto).
     * Kad običan premeštaj ne uspe, fajl se kopira, a [from] ostaje: briše ga onaj ko je zvao, tek
     * pošto zapamti novo mesto. Vraća novi fajl, ili null kad nema mesta ili prenos ne uspe.
     */
    fun transfer(from: File, dir: File, baseName: String, extension: String): File? {
        val target = freeFile(dir, baseName, extension, claim = false)
        if (target.exists()) return null
        try {
            if (from.renameTo(target)) return target
        } catch (_: SecurityException) {
            // Ide se na kopiranje.
        }
        if (dir.usableSpace < from.length() + COPY_MARGIN_BYTES) return null
        val part = File(dir, target.name + PART_SUFFIX)
        return try {
            from.inputStream().use { input -> part.outputStream().use { output -> input.copyTo(output, COPY_BUFFER) } }
            if (part.length() == from.length() && part.renameTo(target)) target else null.also { part.delete() }
        } catch (_: IOException) {
            part.delete()
            null
        } catch (_: SecurityException) {
            part.delete()
            null
        }
    }

    /** Briše nedovršene kopije koje su ostale kad je prenos prekinut (ugašena aplikacija). */
    fun clearUnfinished(dir: File) {
        dir.listFiles { file -> file.name.endsWith(PART_SUFFIX) }?.forEach { it.delete() }
    }

    /** Naziv fajla od naslova: bez znakova koje sistem fajlova ne podnosi, razumne dužine. */
    fun safeName(title: String, fallback: String): String =
        title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), " ").replace(Regex("\\s+"), " ").trim().trim('.').take(MAX_NAME).trim()
            .ifEmpty { fallback }

    private const val MAX_COPIES = 1000
    private const val PART_SUFFIX = ".part"
    private const val COPY_BUFFER = 1 shl 20
    private const val COPY_MARGIN_BYTES = 100L * 1024 * 1024
    private const val MAX_NAME = 80
}
