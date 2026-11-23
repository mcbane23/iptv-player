package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.VodItem
import com.bane23.iptvplayer.data.repo.VodRepository
import com.bane23.iptvplayer.ui.common.vod.metaLine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Šta se prikazuje iznad postera: naziv, kratki podaci i opis filma ili serije u fokusu. */
data class VodPreview(val id: Long, val name: String, val meta: String = "", val plot: String? = null)

/**
 * Opis filma ili serije na kojoj je fokus, za zaglavlje iznad postera na TV-u. Detalji se traže
 * tek kad fokus kratko stoji na posteru, da brzo listanje ne šalje zahtev za svaki, i pamte se
 * dok je ekran otvoren.
 */
@HiltViewModel
class TvVodPreviewViewModel @Inject constructor(
    private val repository: VodRepository,
    settings: SettingsStore,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settings.tvVodPreview
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _preview = MutableStateFlow<VodPreview?>(null)
    val preview: StateFlow<VodPreview?> = _preview.asStateFlow()

    private val cache = HashMap<Long, VodPreview>()
    private var job: Job? = null

    fun focus(item: VodItem, series: Boolean) {
        if (_preview.value?.id == item.id) return
        job?.cancel()
        // Naziv se vidi odmah; opis stiže kad se detalji učitaju.
        _preview.value = cache[item.id] ?: VodPreview(item.id, item.name)
        if (!enabled.value || item.id in cache) return
        job = viewModelScope.launch {
            delay(DETAILS_DELAY_MS)
            val loaded = runCatching {
                if (series) {
                    val details = repository.series(item.id)
                    VodPreview(item.id, item.name, details.metaLine(), details.plot)
                } else {
                    val details = repository.movie(item.id)
                    VodPreview(item.id, item.name, details.metaLine(), details.plot)
                }
            }.getOrNull() ?: return@launch
            cache[item.id] = loaded
            if (_preview.value?.id == item.id) _preview.value = loaded
        }
    }

    /** Fokus je izašao iz postera (bočni meni, kategorije): zaglavlje više ne opisuje ništa. */
    fun clear() {
        job?.cancel()
        _preview.value = null
    }

    private companion object {
        const val DETAILS_DELAY_MS = 600L
    }
}

/**
 * Zaglavlje iznad postera: naziv, godina, trajanje, ocena i žanr, i opis u tri reda. Visina je
 * stalna, da se mreža postera ne pomera dok se fokus kreće.
 */
@Composable
fun TvVodPreviewHeader(preview: VodPreview?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT)
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
    ) {
        if (preview == null) return@Column
        Text(
            text = preview.name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (preview.meta.isNotBlank()) {
            Text(
                text = preview.meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        preview.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val PREVIEW_HEIGHT = 132.dp
