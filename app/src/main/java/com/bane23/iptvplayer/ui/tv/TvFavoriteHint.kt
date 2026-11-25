package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import com.bane23.iptvplayer.ui.tv.text.Text
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TvFavoriteHintViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    /** null dok se ne pročita iz podešavanja, da se uputstvo ne bi trepnulo. */
    val shown: StateFlow<Boolean?> = settings.tvFavoriteHintShown
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markShown() {
        viewModelScope.launch { settings.setTvFavoriteHintShown() }
    }
}

/**
 * Uputstvo „drži OK za omiljene” iznad liste kanala, samo pri prvom otvaranju sekcije Uživo
 * i samo nekoliko sekundi. Posle toga ga nema, da ekran ostane čist.
 */
@Composable
fun TvFavoriteHint(modifier: Modifier = Modifier, viewModel: TvFavoriteHintViewModel = hiltViewModel()) {
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (shown == false) {
            visible = true
            viewModel.markShown()
            delay(FAVORITE_HINT_MS)
            visible = false
        }
    }
    if (!visible) return
    Text(
        text = stringResource(R.string.favorite_tv_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

private const val FAVORITE_HINT_MS = 8_000L
