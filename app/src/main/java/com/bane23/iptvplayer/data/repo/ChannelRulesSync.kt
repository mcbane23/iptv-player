package com.bane23.iptvplayer.data.repo

import androidx.room.withTransaction
import com.bane23.iptvplayer.data.db.AppDatabase
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.domain.ChannelFilter
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

/**
 * Održava kolonu `channels.ruleHidden` prema pravilima za kanale iz Podešavanja (sakrij SD kad
 * postoji HD, sakrij kanale +1, spoji istoimene) i prema filteru kanala iz Uživo (kvalitet, zemlja),
 * da upiti mogu da izostave takve kanale. Računa se
 * iznova kad se promeni neko pravilo i kad se neka lista učita ili osveži. Kanali se ne brišu:
 * isključivanje pravila ih vraća.
 */
@Singleton
class ChannelRulesSync @Inject constructor(
    private val settings: SettingsStore,
    private val db: AppDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Oznaka sadržaja lista: menja se kad se lista doda, obriše ili osveži.
            val content = db.playlistDao().observeAllWithCount()
                .map { lists -> lists.map { Triple(it.playlist.id, it.playlist.lastRefreshedAt, it.channelCount) } }
                .distinctUntilChanged()
            combine(settings.channelRules, settings.liveFilter, content) { rules, filter, _ -> rules to filter }.collectLatest { (rules, filter) ->
                try {
                    apply(rules, filter)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Sledeća promena pravila ili lista pokušava ponovo.
                }
            }
        }
    }

    private suspend fun apply(rules: ChannelRuleSettings, filter: ChannelFilter) {
        val dao = db.channelDao()
        if (!rules.any && !filter.active) {
            dao.clearRuleHidden()
            return
        }
        val rows = dao.ruleRows()
        val wanted = ChannelRules.hidden(rows.map { ChannelRules.Row(it.id, it.playlistId, it.name, it.kept, it.category) }, rules, filter)
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
