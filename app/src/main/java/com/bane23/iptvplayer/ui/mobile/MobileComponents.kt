package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.ui.common.icons.AppIcons

/**
 * Zaglavlje ekrana na telefonu: strelica nazad (ako je [onBack] zadat), naslov i ikonice desno.
 * Naslov se skraćuje sa „…”, da se nikad ne preklopi sa ikonicama.
 */
@Composable
fun SectionTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            ToolbarIcon(AppIcons.Back, stringResource(R.string.action_back), onBack)
        }
        Text(
            text = title,
            style = if (onBack == null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 0.dp else 4.dp, end = 8.dp),
        )
        actions()
    }
}

/** Dugme sa ikonicom u zaglavlju. [description] čitaju čitači ekrana. */
@Composable
fun ToolbarIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = description)
    }
}
