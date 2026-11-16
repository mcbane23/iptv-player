package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/**
 * Polje za filtriranje spiska (npr. kategorija) na TV-u. Dok se samo prolazi D-padom preko njega,
 * ponaša se kao običan red i ne otvara tastaturu; tek OK prelazi na kucanje. Fokus se vidi kao
 * okvir u akcentnoj boji.
 */
@Composable
fun TvFilterField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // Posle „Gotovo” na tastaturi fokus se vraća na red, da daljinski ne ostane bez fokusa.
    var restoreFocus by remember { mutableStateOf(false) }
    var fieldHadFocus by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    LaunchedEffect(editing, restoreFocus) {
        if (!editing && restoreFocus) {
            restoreFocus = false
            rowFocus.tryRequestFocus()
        }
    }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(colors.surface, shape)
            .border(2.dp, if (focused) colors.primary else Color.Transparent, shape)
            .then(
                if (editing) {
                    Modifier
                } else {
                    Modifier
                        .focusRequester(rowFocus)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { editing = true }
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (editing) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        restoreFocus = true
                        editing = false
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .onFocusChanged {
                            // Prvi poziv stiže pre nego što polje dobije fokus, pa se gleda samo gubitak fokusa.
                            if (it.isFocused) fieldHadFocus = true
                            focused = it.isFocused
                            // Kad fokus ode sa polja, ono se vraća u običan red.
                            if (!it.isFocused && fieldHadFocus) {
                                fieldHadFocus = false
                                editing = false
                            }
                        },
                )
                LaunchedEffect(Unit) { fieldFocus.tryRequestFocus() }
            } else if (value.isNotEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}
