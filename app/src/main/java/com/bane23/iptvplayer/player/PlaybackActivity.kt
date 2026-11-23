package com.bane23.iptvplayer.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Da li neki plejer u aplikaciji upravo drži vezu sa provajderom. Po ovome preuzimanje zna kad da
 * stane: mnogi nalozi dozvoljavaju samo jednu vezu, pa bi preuzimanje prekinulo gledanje.
 */
object PlaybackActivity {
    private val owners = MutableStateFlow<Set<Any>>(emptySet())

    val playing: Flow<Boolean> = owners.map { it.isNotEmpty() }.distinctUntilChanged()

    /** Da li pušta neko drugi osim [owner] (npr. plejer, dok [owner] u pozadini meri kvalitet kanala). */
    fun others(owner: Any): Flow<Boolean> = owners.map { set -> set.any { it !== owner } }.distinctUntilChanged()

    fun set(owner: Any, active: Boolean) {
        owners.update { if (active) it + owner else it - owner }
    }
}
