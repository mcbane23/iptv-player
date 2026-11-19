package com.bane23.iptvplayer.data.storage

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicFolderTest {
    private val dir: File = Files.createTempDirectory("glowbox-test").toFile()

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `safe name drops characters a file system refuses`() {
        assertEquals("Film 2 Nastavak", PublicFolder.safeName("Film 2: Nastavak?", "x"))
        assertEquals("a b", PublicFolder.safeName("  a \t b. ", "x"))
        assertEquals("rezerva", PublicFolder.safeName("/\\:*", "rezerva"))
        assertEquals(80, PublicFolder.safeName("a".repeat(200), "x").length)
    }

    @Test
    fun `free file is created and numbered when the name is taken`() {
        val first = PublicFolder.freeFile(dir, "Film", "mkv")
        val second = PublicFolder.freeFile(dir, "Film", "mkv")
        assertEquals("Film.mkv", first.name)
        assertEquals("Film (2).mkv", second.name)
        assertTrue(first.exists() && second.exists())
        assertEquals("Film (3).mkv", PublicFolder.freeFile(dir, "Film", "mkv", claim = false).name)
        assertFalse(File(dir, "Film (3).mkv").exists())
    }

    @Test
    fun `transfer moves the content and keeps an existing file`() {
        val source = File(dir, "stari").apply { mkdirs() }
        val target = File(dir, "novi").apply { mkdirs() }
        File(target, "Snimak.ts").writeText("tu od ranije")
        val from = File(source, "movie_1_2.ts").apply { writeText("sadrzaj") }

        val moved = PublicFolder.transfer(from, target, "Snimak", "ts")

        assertEquals("Snimak (2).ts", moved?.name)
        assertEquals("sadrzaj", moved?.readText())
        assertEquals("tu od ranije", File(target, "Snimak.ts").readText())
        assertFalse(from.exists())
    }

    @Test
    fun `transfer of a missing file gives nothing and leaves no leftovers`() {
        val target = File(dir, "novi").apply { mkdirs() }
        assertNull(PublicFolder.transfer(File(dir, "nema.ts"), target, "Snimak", "ts"))
        assertEquals(0, target.listFiles()?.size)
    }

    @Test
    fun `unfinished copies are cleared`() {
        File(dir, "Film.mkv.part").writeText("pola")
        File(dir, "Film.mkv").writeText("ceo")
        PublicFolder.clearUnfinished(dir)
        assertEquals(listOf("Film.mkv"), dir.listFiles()?.map { it.name })
    }
}
