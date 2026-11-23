package com.bane23.iptvplayer.ui.common.player

import androidx.compose.runtime.Composable
import com.bane23.iptvplayer.ui.common.AppMessages
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.repo.ChannelSource
import com.bane23.iptvplayer.domain.ChannelVariants
import kotlinx.coroutines.flow.Flow

/**
 * Kratka poruka kad plejer sam pusti drugi izvor kanala: prethodni se nije javio (isti kanal iz druge
 * liste ili druga verzija kanala), ili štednja mobilnih podataka pušta verziju niže rezolucije.
 */
@Composable
fun SourceNotices(notices: Flow<SourceNotice>) {
    val resources = LocalResources.current
    LaunchedEffect(notices, resources) {
        notices.collect { notice ->
            when (notice.kind) {
                SourceNoticeKind.PLAYLIST ->
                    AppMessages.show(resources.getString(R.string.player_source_switched, notice.text), long = true)
                SourceNoticeKind.VERSION ->
                    AppMessages.show(resources.getString(R.string.player_source_switched_version, notice.text), long = true)
                SourceNoticeKind.DATA_SAVER ->
                    AppMessages.show(resources.getString(R.string.player_source_data_saver, notice.text))
            }
        }
    }
}

/**
 * Opis izvora bez stanja: naziv verzije kanala kad se izvori razlikuju po nazivu, naziv liste kad ih
 * ima iz više lista, i izmeren kvalitet („720p · 2.3 Mb/s”) kad se zna.
 */
fun sourceText(source: ChannelSource, all: List<ChannelSource>): String {
    val named = all.any { it.name != source.name }
    val listed = all.any { it.playlistId != source.playlistId }
    val parts = listOfNotNull(
        source.name.takeIf { named && it.isNotBlank() },
        source.playlistName.takeIf { (listed || !named) && it.isNotBlank() },
        ChannelVariants.heightLabel(source.height),
        bitrateLabel(source.bitrate),
    )
    return parts.joinToString(" · ").ifEmpty { source.name }
}

/** Naziv izvora za panel „Izvori”: opis izvora i, po potrebi, da se gleda sada ili da se nije javio. */
@Composable
fun ChannelSource.label(ui: PlayerUiState): String {
    val text = sourceText(this, ui.sources)
    return when {
        channelId == ui.sourceId -> stringResource(R.string.player_source_current, text)
        channelId in ui.failedSources -> stringResource(R.string.player_source_failed, text)
        else -> text
    }
}
