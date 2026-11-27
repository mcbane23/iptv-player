package com.bane23.iptvplayer.ui.common.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.FavoriteList
import com.bane23.iptvplayer.ui.common.FavoritePick
import com.bane23.iptvplayer.ui.tv.tryRequestFocus

/** Širina oblačića „Favoriti” na TV-u i na telefonu: sekcije stoje kao dugmići u redovima. */
val TvFavoriteMenuWidth = 420.dp
val MobileFavoriteMenuWidth = 320.dp

/** Najduže ime sekcije, da stane u dugme i u red levog panela. */
private const val MAX_SECTION_NAME = 30

private data class SectionChip(val label: String, val active: Boolean, val onClick: () -> Unit)

/**
 * Sadržaj oblačića „Favoriti” u plejeru: sekcije kao dugmići u redovima. Dok sekcija nema, prvo
 * dugme je „Favoriti” (dodaje kao i pre); posle sekcija „Bez sekcije” i sve sekcije, pa
 * „+ Nova sekcija” i, za kanal koji je već među favoritima, „Ukloni”. Sekcija u kojoj je kanal je u
 * boji teme. Na TV-u ([focusFirst]) fokus odmah staje na sekciju u kojoj je kanal, a za novi kanal
 * na poslednju korišćenu, pa je najčešći slučaj samo OK.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerFavoriteMenu(
    pick: FavoritePick,
    sections: List<FavoriteList>,
    onChoose: (Long?) -> Unit,
    onNewSection: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    focusFirst: Boolean = false,
) {
    val chips = buildList {
        if (sections.isEmpty()) {
            add(SectionChip(stringResource(R.string.category_favorites), pick.favorite) { onChoose(null) })
        } else {
            add(SectionChip(stringResource(R.string.favorite_section_none), pick.favorite && pick.sectionId == null) { onChoose(null) })
            sections.forEach { section ->
                add(SectionChip(section.name, pick.favorite && pick.sectionId == section.id) { onChoose(section.id) })
            }
        }
        add(SectionChip(stringResource(R.string.player_favorite_new_section), false, onNewSection))
        if (pick.favorite) add(SectionChip(stringResource(R.string.player_favorite_remove), false, onRemove))
    }
    // Predložena sekcija: 0 je „Bez sekcije” (ili „Favoriti”), a sekcije idu od 1.
    val target = pick.suggestedSectionId
        ?.let { id -> sections.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1) }
        ?: 0
    val focus = remember { FocusRequester() }
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = stringResource(
                if (pick.favorite) R.string.player_favorite_in_title else R.string.player_favorite_add_title,
            ),
            color = PlayerMenuMuted,
            style = PlayerMenuText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        ) {
            chips.forEachIndexed { index, chip ->
                PlayerChip(
                    label = chip.label,
                    onClick = chip.onClick,
                    active = chip.active,
                    modifier = if (index == target) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
    if (focusFirst) LaunchedEffect(pick.channelId) { focus.tryRequestFocus() }
}

/**
 * Ime nove sekcije za kanal [channelName]. Tastatura se otvara sama, a „Gotovo” na njoj pravi
 * sekciju i dodaje kanal u nju ([onDone]). Na telefonu ([button]) pored polja je i dugme koje radi isto.
 */
@Composable
fun PlayerFavoriteNameBox(
    channelName: String,
    name: String,
    onName: (String) -> Unit,
    nameTaken: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 360.dp,
    button: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val primary = MaterialTheme.colorScheme.primary
    val fieldShape = RoundedCornerShape(8.dp)
    val done = { if (name.isNotBlank()) onDone() }
    Column(
        modifier = modifier
            .width(width)
            .playerPanel(RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.player_favorite_new_for, channelName),
            color = PlayerMenuMuted,
            style = PlayerMenuText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = name,
                onValueChange = { onName(it.take(MAX_SECTION_NAME)) },
                singleLine = true,
                textStyle = PlayerMenuText.copy(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { done() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
                    .border(2.dp, primary, fieldShape)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            )
            if (button) {
                PlayerChip(
                    label = stringResource(R.string.favorite_list_create),
                    onClick = done,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Text(
            text = stringResource(if (nameTaken) R.string.favorite_list_name_taken else R.string.player_favorite_new_hint),
            color = if (nameTaken) MaterialTheme.colorScheme.error else PlayerMenuMuted,
            style = PlayerMenuNote,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
    LaunchedEffect(Unit) {
        focus.tryRequestFocus()
        keyboard?.show()
    }
}
