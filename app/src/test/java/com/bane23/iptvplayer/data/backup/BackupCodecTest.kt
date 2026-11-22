package com.bane23.iptvplayer.data.backup

import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferHistory
import com.bane23.iptvplayer.data.lan.TransferLockedItem
import com.bane23.iptvplayer.data.lan.TransferPlaylist
import com.bane23.iptvplayer.data.lan.TransferProgress
import com.bane23.iptvplayer.data.lan.TransferReminder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val backup = Backup(
        createdAt = 1_700_000_000_000,
        device = Backup.DEVICE_TV,
        appVersion = "1.2.3",
        bundle = TransferBundle(
            playlists = listOf(
                TransferPlaylist(
                    type = TransferPlaylist.TYPE_XTREAM,
                    name = "Kućna lista",
                    source = "http://server.invalid:8080/",
                    username = "izmisljen",
                    password = "tajna \"sa\" znacima",
                    favorites = listOf("x:101"),
                    lockedItems = listOf(TransferLockedItem("channel", "x:5", "Izmišljen kanal")),
                    history = listOf(TransferHistory("x:101", 1_700_000_000_500)),
                    progress = listOf(TransferProgress(kind = "movie", itemId = 7, positionMs = 60_000, durationMs = 120_000)),
                    reminders = listOf(TransferReminder("x:101", 1_700_000_100_000, "Kanal", "Emisija")),
                ),
                TransferPlaylist(
                    type = TransferPlaylist.TYPE_FILE,
                    name = "Iz fajla",
                    source = "",
                    fileContent = BackupCodec.pack("#EXTM3U\n#EXTINF:-1,Izmišljen\nhttp://example.invalid/1\n".toByteArray()),
                ),
            ),
            pinRecord = "ab:" + "0".repeat(64),
        ),
        prefs = mapOf("pause_info" to "false", "player_seek_step" to "30"),
        devicePrefs = mapOf("ui_scale" to "SMALL"),
        favoriteLists = listOf("Sport", "Deca"),
        language = "SERBIAN",
    )

    @Test
    fun `plain file is readable text and reads back the same`() {
        val text = BackupCodec.encode(backup)
        assertTrue(text.contains("\"glowbox-backup\""))
        assertTrue(text.contains("Kućna lista"))
        assertEquals(BackupRead.Ok(backup), BackupCodec.decode(text))
    }

    @Test
    fun `empty password means a plain file`() {
        assertEquals(BackupRead.Ok(backup), BackupCodec.decode(BackupCodec.encode(backup, "")))
    }

    @Test
    fun `protected file hides everything and opens only with its password`() {
        val text = BackupCodec.encode(backup, "lozinka šđ 1")
        assertFalse(text.contains("Kućna"))
        assertFalse(text.contains("izmisljen"))
        assertFalse(text.contains("tajna"))
        assertEquals(BackupRead.NeedsPassword, BackupCodec.decode(text))
        assertEquals(BackupRead.NeedsPassword, BackupCodec.decode(text, ""))
        assertEquals(BackupRead.WrongPassword, BackupCodec.decode(text, "lozinka"))
        assertEquals(BackupRead.Ok(backup), BackupCodec.decode(text, "lozinka šđ 1"))
    }

    @Test
    fun `two protected exports of the same data differ`() {
        assertFalse(BackupCodec.encode(backup, "a") == BackupCodec.encode(backup, "a"))
    }

    @Test
    fun `changed protected file is refused`() {
        val text = BackupCodec.encode(backup, "a")
        val start = text.indexOf("\"data\":\"") + 8
        val swapped = if (text[start + 20] == 'A') 'B' else 'A'
        val broken = text.substring(0, start + 20) + swapped + text.substring(start + 21)
        assertEquals(BackupRead.WrongPassword, BackupCodec.decode(broken, "a"))
    }

    @Test
    fun `other files are refused`() {
        assertEquals(BackupRead.Invalid, BackupCodec.decode(""))
        assertEquals(BackupRead.Invalid, BackupCodec.decode("#EXTM3U"))
        assertEquals(BackupRead.Invalid, BackupCodec.decode("{}"))
        assertEquals(BackupRead.Invalid, BackupCodec.decode("""{"format":"nesto-drugo","version":1,"backup":{}}"""))
        assertEquals(BackupRead.Invalid, BackupCodec.decode("""{"format":"glowbox-backup","version":1}"""))
        assertEquals(BackupRead.Invalid, BackupCodec.decode("""{"format":"glowbox-backup","version":1,"encrypted":true}""", "a"))
    }

    @Test
    fun `file from a newer app version is reported as newer`() {
        assertEquals(BackupRead.Newer, BackupCodec.decode("""{"format":"glowbox-backup","version":99,"backup":{}}"""))
    }

    @Test
    fun `unknown fields are ignored`() {
        val read = BackupCodec.decode("""{"format":"glowbox-backup","version":1,"novo":1,"backup":{"device":"phone","nepoznato":[1]}}""")
        assertEquals(BackupRead.Ok(Backup(device = Backup.DEVICE_PHONE)), read)
    }

    @Test
    fun `playlist file content survives packing`() {
        val bytes = "#EXTM3U\n#EXTINF:-1,Izmišljen kanal\nhttp://example.invalid/1\n".toByteArray()
        assertArrayEquals(bytes, BackupCodec.unpack(BackupCodec.pack(bytes)))
        assertNull(BackupCodec.unpack("nije base64 !!"))
        assertNull(BackupCodec.unpack("AAAA"))
    }
}
