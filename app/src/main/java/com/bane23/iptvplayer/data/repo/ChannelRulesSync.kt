package com.bane23.iptvplayer.data.repo

import androidx.room.withTransaction
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.domain.ChannelRuleSettings
import com.bane23.iptvplayer.domain.ChannelRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Održava kolonu `channels.ruleHidden` prema pravilima za kanale iz Podešavanja (sakrij SD kad
 * postoji HD, sakrij kanale +1, spoji istoimene) i kolonu `channels.filterHidden` prema filteru
 * kanala iz Uživo (kvalitet, zemlja), da upiti mogu da izostave takve kanale. Pravila se računaju
 * iznova kad se promeni neko pravilo i kad se neka lista učita ili osveži. Filter se primenjuje
 * jednom naredbom nad kolonama `quality` i `country`, koje se popune jednom posle uvoza liste, pa je
 * promena filtera brza i na sporim uređajima. Kanali se ne brišu: isključivanje ih vraća. Uz kvalitet
 * i zemlju upisuje se i `variantKey`, po kome plejer nalazi isti kanal u drugom kvalitetu.
 */
@Singleton
class ChannelRulesSync @Inject constructor(
    private val settings: SettingsStore,
    private val db: AppDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private val classifying = Mutex()

    fun start() {
        if (started) return
        started = true
        // Oznaka sadržaja lista: menja se kad se lista doda, obriše ili osveži.
        val content = db.playlistDao().observeAllWithCount()
            .map { lists -> lists.map { Triple(it.playlist.id, it.playlist.lastRefreshedAt, it.channelCount) } }
            .distinctUntilChanged()
        // Filter i pravila idu odvojeno: promena filtera je jedna naredba nad bazom i ne čeka pravila,
        // koja čitaju nazive svih kanala.
        scope.launch {
            combine(settings.liveFilter, content) { filter, _ -> filter }.collectLatest { filter ->
                guarded {
                    classify()
                    db.channelDao().applyFilter(filter.minQuality, ChannelRules.countryCode(filter.country))
                }
            }
        }
        scope.launch {
            combine(settings.channelRules, content) { rules, _ -> rules }.collectLatest { rules -> guarded { applyRules(rules) } }
        }
    }

    /** Greška se preskače: sledeća promena podešavanja ili lista pokušava ponovo. */
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Upisuje kvalitet, zemlju i ključ verzije kanalima koji ih još nemaju (posle uvoza ili osvežavanja liste), da
     * filter posle toga radi samo nad kolonama. Kad nema novih kanala, ovo je jedan kratak upit.
     */
    private suspend fun classify() = classifying.withLock {
        val dao = db.channelDao()
        val rows = dao.unclassified()
        if (rows.isEmpty()) return@withLock
        val groups = rows.groupBy({ ChannelRules.quality(it.name) to ChannelRules.country(it.name, it.category) }, { it.id })
        // Ključ istog kanala u drugom kvalitetu, za verzije kanala u plejeru (štednja mobilnih podataka).
        val variants = rows.groupBy({ ChannelRules.variantKey(it.name, it.category) }, { it.id })
        db.withTransaction {
            for ((key, ids) in groups) ids.chunked(ID_CHUNK).forEach { dao.classify(it, key.first, key.second) }
            for ((key, ids) in variants) ids.chunked(ID_CHUNK).forEach { dao.setVariantKey(it, key) }
        }
    }

    private suspend fun applyRules(rules: ChannelRuleSettings) {
        val dao = db.channelDao()
        if (!rules.any) {
            dao.clearRuleHidden()
            return
        }
        val rows = dao.ruleRows()
        val wanted = ChannelRules.hidden(rows.map { ChannelRules.Row(it.id, it.playlistId, it.name, it.kept, it.category) }, rules)
        val toHide = rows.filter { !it.hidden && it.id in wanted }.map { it.id }
        val toShow = rows.filter { it.hidden && it.id !in wanted }.map { it.id }
        if (toHide.isEmpty() && toShow.isEmpty()) return
        db.withTransaction {
            toHide.chunked(ID_CHUNK).forEach { dao.setRuleHidden(it, true) }
            toShow.chunked(ID_CHUNK).forEach { dao.setRuleHidden(it, false) }
        }
    }

    private companion object {
        const val ID_CHUNK = 500
    }
}
