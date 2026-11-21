package com.bane23.iptvplayer.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * Vraćanje fokusa na stavku sa koje je otvoren drugi ekran (detalji, plejer). Bez toga se posle Nazad
 * fokus izgubi i ode na prvi element ekrana ili u bočni meni. Ekran javi [focused] kad stavka dobije
 * fokus i [opened] kad se sa nje nešto otvori, stavkama doda [target], a pri prikazu pozove
 * [restoreIfBack].
 */
@Stable
class TvReturnFocus internal constructor(
    private val lastKey: MutableState<String?>,
    private val returning: MutableState<Boolean>,
) {
    private val requester = FocusRequester()

    /** Stavka [key] je dobila fokus. */
    fun focused(key: String) {
        lastKey.value = key
    }

    /** Sa stavke u fokusu je otvoren drugi ekran; pri sledećem prikazu fokus se vraća na nju. */
    fun opened() {
        returning.value = true
    }

    /**
     * Modifikator za stavku [key]: samo ona na kojoj je fokus bio poslednji nosi zahtev za fokus. Stavka
     * prati samo taj odgovor, pa se pri koraku D-pada iznova crtaju dve stavke, a ne ceo spisak.
     */
    @Composable
    fun target(key: String): Modifier {
        val isTarget = remember(key) { derivedStateOf { lastKey.value == key } }.value
        return if (isTarget) Modifier.focusRequester(requester) else Modifier
    }

    /**
     * Ako je ovaj prikaz povratak sa otvorene stavke, vraća fokus na nju i javlja da li je uspelo.
     * Spiskovi se slažu tek pri crtanju (a neki stižu iz baze), pa se pokušava više kadrova zaredom.
     */
    suspend fun restoreIfBack(): Boolean {
        if (!returning.value) return false
        returning.value = false
        repeat(RESTORE_FRAMES) {
            if (requester.tryRequestFocus()) return true
            withFrameNanos { }
        }
        return false
    }
}

@Composable
fun rememberTvReturnFocus(): TvReturnFocus {
    val lastKey = rememberSaveable { mutableStateOf<String?>(null) }
    val returning = rememberSaveable { mutableStateOf(false) }
    return remember { TvReturnFocus(lastKey, returning) }
}

private const val RESTORE_FRAMES = 45
