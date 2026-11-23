package com.bane23.iptvplayer.ui.mobile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bane23.iptvplayer.ui.common.theme.appBackground

/**
 * Okvir ekrana na telefonu koji nije kartica početnog ekrana: ista pozadina sa prelivom kao svuda,
 * umesto ravne boje. Svaki novi ekran na telefonu koristi ovaj okvir, ne običan `Scaffold`.
 */
@Composable
fun AppScaffold(content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        // Providna pozadina ne daje boju teksta, pa se zadaje izričito (inače je crn).
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.appBackground(),
        content = content,
    )
}
