package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/**
 * Grad za temperaturu u čuvaru ekrana, uz desnu ivicu ekrana. „Sačuvaj” pamti upisan grad, a
 * „Automatski” ga briše, pa se mesto ponovo pogađa po internet vezi.
 */
@Composable
fun TvWeatherCityDialog(city: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(city) }
    TvSideDialog(onClose = onClose) {
        item {
            SideDialogTitle(
                title = stringResource(R.string.settings_weather_city),
                subtitle = stringResource(R.string.settings_weather_city_hint),
            )
        }
        item {
            TvFilterField(
                value = name,
                onChange = { name = it.take(MAX_CITY_NAME) },
                placeholder = stringResource(R.string.settings_weather_city_placeholder),
                icon = AppIcons.Edit,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
        }
        if (name.isNotBlank() && name.trim() != city) {
            item {
                TvTextRow(text = stringResource(R.string.action_save), onClick = {
                    onSave(name)
                    onClose()
                })
            }
        }
        if (city.isNotEmpty()) {
            item {
                TvTextRow(text = stringResource(R.string.settings_weather_city_auto), onClick = {
                    onSave("")
                    onClose()
                })
            }
        }
        item { TvTextRow(text = stringResource(R.string.action_cancel), onClick = onClose) }
    }
}

private const val MAX_CITY_NAME = 60
