package com.bane23.iptvplayer.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Plejer izlazi iz tunel režima („Hardverski (direktan)”). Na nekim TV-ima (Philips, MediaTek) površina
 * za sliku ostaje vezana za tunel, pa običan dekoder crta u prazno: zvuk i titl idu, a slika je crna.
 * Površina zato mora da se napravi iznova; to radi prikaz slike koji drži tog plejera.
 */
object SurfaceReset {
    private val _requests = MutableSharedFlow<Player>(extraBufferCapacity = 4)
    val requests: SharedFlow<Player> = _requests.asSharedFlow()

    fun request(player: Player) {
        _requests.tryEmit(player)
    }
}
