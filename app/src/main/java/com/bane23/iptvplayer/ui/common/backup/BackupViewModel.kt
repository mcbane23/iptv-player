package com.bane23.iptvplayer.ui.common.backup

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.backup.Backup
import com.bane23.iptvplayer.data.backup.BackupCodec
import com.bane23.iptvplayer.data.backup.BackupFileInfo
import com.bane23.iptvplayer.data.backup.BackupImportState
import com.bane23.iptvplayer.data.backup.BackupRead
import com.bane23.iptvplayer.data.backup.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kratka poruka za korisnika; [arg] se ubacuje u tekst kad ga tekst traži. */
data class BackupNotice(@StringRes val text: Int, val arg: Any? = null)

/** Ono što se o pročitanom fajlu pokazuje pre uvoza. */
data class BackupInfo(val createdAt: Long, val playlists: Int, val fromTv: Boolean)

data class BackupUiState(
    /** Izvoz ili čitanje fajla je u toku. */
    val working: Boolean = false,
    /** Izabrani fajl je zaštićen lozinkom i čeka da se ona unese. */
    val needsPassword: Boolean = false,
    val wrongPassword: Boolean = false,
    /** Fajl je pročitan; čeka se izbor između dopune i zamene. */
    val ready: BackupInfo? = null,
    /** TV: fajlovi izvoza u Downloads/Glowbox. */
    val files: List<BackupFileInfo> = emptyList(),
    /** TV: aplikacija vidi i fajlove koje nije sama napravila. */
    val canReadForeign: Boolean = true,
    /** TV: putanja poslednjeg izvoza, da se vidi gde je fajl. */
    val exportedTo: String? = null,
    val notice: BackupNotice? = null,
)

/** Izvoz i uvoz podešavanja i lista, za telefon i TV. */
@HiltViewModel
class BackupViewModel @Inject constructor(private val backups: BackupRepository) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    val importState: StateFlow<BackupImportState> = backups.importState

    /** Tekst fajla koji čeka lozinku. */
    private var lockedText: String? = null

    /** Pročitan izvoz koji čeka izbor načina uvoza. */
    private var pending: Backup? = null

    val suggestedFileName: String get() = "${backups.suggestedName()}.${BackupCodec.EXTENSION}"

    /**
     * Telefon: lozinka izvoza koji čeka da korisnik izabere mesto fajla (prazna = običan, čitljiv
     * fajl). Stoji ovde, a ne na ekranu, da preživi rotaciju dok je birač otvoren; null znači da
     * izvoz nije ni započet.
     */
    private var armedPassword: String? = null

    fun armExport(password: String) {
        armedPassword = password
    }

    /**
     * Telefon: izvoz u fajl koji je korisnik izabrao; null kad je odustao. Ako je aplikacija u
     * međuvremenu ugašena, lozinka više nije poznata, pa se ne izvozi ništa (umesto nezaštićenog fajla).
     */
    fun exportTo(uri: Uri?) {
        val password = armedPassword
        armedPassword = null
        if (uri == null) return
        if (password == null) {
            _state.update { it.copy(notice = BackupNotice(R.string.backup_export_failed)) }
            return
        }
        export(password) { text -> if (backups.writeTo(uri, text)) "" else null }
    }

    /** TV: izvoz u Downloads/Glowbox. */
    fun exportToFolder(password: String) = export(password) { text -> backups.writeToPublicFolder(text)?.absolutePath }

    /** [write] vraća putanju fajla (prazno kad je mesto birao korisnik), ili null kad upis ne uspe. */
    private fun export(password: String, write: suspend (String) -> String?) {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val (text, skipped) = backups.exportText(password.takeIf { it.isNotEmpty() })
            val written = write(text)
            val notice = when {
                written == null -> BackupNotice(R.string.backup_export_failed)
                skipped > 0 -> BackupNotice(R.string.backup_export_done_skipped, skipped)
                else -> BackupNotice(R.string.backup_export_done)
            }
            _state.update { it.copy(working = false, exportedTo = written?.takeIf(String::isNotEmpty) ?: it.exportedTo, notice = notice) }
            refreshFiles()
        }
    }

    /** Telefon: fajl izabran sistemskim biračem. */
    fun open(uri: Uri) = load { backups.readText(uri) }

    /** TV: fajl sa spiska. */
    fun open(path: String) = load { backups.readText(path) }

    private fun load(read: suspend () -> String?) {
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val text = read()
            if (text == null) {
                _state.update { it.copy(working = false, notice = BackupNotice(R.string.backup_file_unreadable)) }
            } else {
                decode(text, password = null)
            }
        }
    }

    /** Lozinka za fajl koji je čeka. */
    fun submitPassword(password: String) {
        val text = lockedText ?: return
        if (_state.value.working) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch { decode(text, password) }
    }

    private suspend fun decode(text: String, password: String?) {
        // Izvođenje ključa iz lozinke traje i do sekunde, pa ne ide na glavnoj niti.
        val read = withContext(Dispatchers.Default) { BackupCodec.decode(text, password) }
        when (read) {
            is BackupRead.Ok -> {
                lockedText = null
                pending = read.backup
                val info = BackupInfo(read.backup.createdAt, read.backup.bundle.playlists.size, read.backup.device == Backup.DEVICE_TV)
                _state.update { it.copy(working = false, needsPassword = false, wrongPassword = false, ready = info) }
            }

            BackupRead.NeedsPassword -> {
                lockedText = text
                _state.update { it.copy(working = false, needsPassword = true, wrongPassword = false) }
            }

            BackupRead.WrongPassword -> _state.update { it.copy(working = false, needsPassword = true, wrongPassword = true) }

            BackupRead.Newer -> fail(R.string.backup_file_newer)

            BackupRead.Invalid -> fail(R.string.backup_file_invalid)
        }
    }

    private fun fail(@StringRes text: Int) {
        lockedText = null
        _state.update { it.copy(working = false, needsPassword = false, wrongPassword = false, notice = BackupNotice(text)) }
    }

    /** Korisnik je odustao od fajla koji je izabrao. */
    fun cancelPending() {
        lockedText = null
        pending = null
        _state.update { it.copy(needsPassword = false, wrongPassword = false, ready = null) }
    }

    /** Pokreće uvoz pročitanog fajla: [replace] zamenjuje sve, inače se postojeće dopunjuje. */
    fun startImport(replace: Boolean) {
        val backup = pending ?: return
        pending = null
        _state.update { it.copy(ready = null) }
        backups.startImport(backup, replace)
    }

    fun clearImportResult() = backups.clearImportState()

    fun languageApplied() = backups.languageApplied()

    fun noticeShown() = _state.update { it.copy(notice = null) }

    /** TV: ponovo čita spisak fajlova i da li aplikacija vidi tuđe fajlove (posle povratka iz podešavanja sistema). */
    fun refreshFiles() {
        viewModelScope.launch {
            val files = backups.publicFiles()
            _state.update { it.copy(files = files, canReadForeign = backups.canReadForeignFiles()) }
        }
    }
}
