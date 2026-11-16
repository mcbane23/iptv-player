package com.bane23.iptvplayer.ui.tv

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Prazno stanje ili greška na TV-u, isto na svim ekranima: ikonica i kratak tekst. Nije fokusirano;
 * dugme (npr. „Pokušaj ponovo”) dodaje ekran ispod, da bi sam upravljao fokusom.
 */
@Composable
fun TvEmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(40.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
