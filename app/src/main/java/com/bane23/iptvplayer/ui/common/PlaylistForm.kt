package com.bane23.iptvplayer.ui.common

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bane23.iptvplayer.R
import com.bane23.iptvplayer.domain.PlaylistError
import com.bane23.iptvplayer.domain.PlaylistType

/**
 * Polja za unos liste. Zajednička su za telefon i TV: Material3 polja za tekst rade i na dodir
 * i sa daljinskim (fokus D-padom, OK otvara tastaturu).
 *
 * [plainKeyboard] traži tastaturu bez predloga i ispravki i za adrese i korisničko ime. Koristi se na
 * TV-u, gde tastature umeju da ubace razmak ili veliko slovo i kad je polje označeno kao adresa.
 *
 * [pickFileButton] crta dugme za izbor fajla u stilu platforme (telefon ili TV).
 */
@Composable
fun PlaylistFormFields(
    state: EditPlaylistUiState,
    viewModel: EditPlaylistViewModel,
    modifier: Modifier = Modifier,
    plainKeyboard: Boolean = false,
    pickFileButton: @Composable (label: String, onClick: () -> Unit) -> Unit,
) {
    val enabled = !state.saving
    // Polje tipa lozinke (bez sakrivanja teksta) je jedino kod koga tastature pouzdano ne diraju unos.
    val addressKeyboard = if (plainKeyboard) KeyboardType.Password else KeyboardType.Uri
    val plainTextKeyboard = if (plainKeyboard) KeyboardType.Password else KeyboardType.Text
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = stringResource(R.string.field_name),
            enabled = enabled,
            keyboardType = plainTextKeyboard,
        )
        when (state.type) {
            PlaylistType.M3U_URL -> FormField(
                value = state.url,
                onValueChange = viewModel::setUrl,
                label = stringResource(R.string.field_m3u_url),
                enabled = enabled,
                keyboardType = addressKeyboard,
            )

            PlaylistType.M3U_FILE -> {
                val pickFile = rememberFilePicker(viewModel)
                pickFileButton(stringResource(R.string.action_pick_file), pickFile)
                Text(
                    text = if (state.fileUri.isEmpty()) {
                        stringResource(R.string.file_not_selected)
                    } else {
                        stringResource(R.string.file_selected, displayName(state.fileUri))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PlaylistType.XTREAM -> {
                FormField(
                    value = state.server,
                    onValueChange = viewModel::setServer,
                    label = stringResource(R.string.field_server),
                    enabled = enabled,
                    keyboardType = addressKeyboard,
                )
                FormField(
                    value = state.username,
                    onValueChange = viewModel::setUsername,
                    label = stringResource(R.string.field_username),
                    enabled = enabled,
                    keyboardType = plainTextKeyboard,
                )
                FormField(
                    value = state.password,
                    onValueChange = viewModel::setPassword,
                    label = stringResource(R.string.field_password),
                    enabled = enabled,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    supportingText = if (state.isEditing) stringResource(R.string.field_password_keep) else null,
                )
            }
        }
        if (state.type != PlaylistType.M3U_FILE) {
            FormField(
                value = state.userAgent,
                onValueChange = viewModel::setUserAgent,
                label = stringResource(R.string.field_user_agent),
                enabled = enabled,
                supportingText = stringResource(R.string.field_user_agent_hint),
            )
        }
        FormField(
            value = state.epgUrl,
            onValueChange = viewModel::setEpgUrl,
            label = stringResource(R.string.field_epg_url),
            enabled = enabled,
            keyboardType = addressKeyboard,
            supportingText = stringResource(R.string.field_epg_url_hint),
        )
        FormField(
            value = state.epgShift,
            onValueChange = viewModel::setEpgShift,
            label = stringResource(R.string.field_epg_shift),
            enabled = enabled,
            // Tastatura za brojeve na mnogim uređajima nema minus, zato obična.
            imeAction = ImeAction.Done,
            supportingText = stringResource(R.string.field_epg_shift_hint),
        )
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    isPassword: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        // Bez automatskih ispravki i velikog početnog slova: adrese i nazivi se unose tačno kako su otkucani.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        supportingText = supportingText?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Otvara sistemski izbor fajla i pamti trajnu dozvolu čitanja, da bi lista mogla kasnije da se osveži. */
@Composable
private fun rememberFilePicker(viewModel: EditPlaylistViewModel): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // izvor ne podržava trajne dozvole; fajl će se pročitati sada, ali osvežavanje možda neće raditi
            }
            val name = queryDisplayName(context, uri)?.substringBeforeLast('.')
            viewModel.setFile(uri.toString(), name)
        }
    }
    return {
        try {
            launcher.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            // npr. TV uređaj bez aplikacije za izbor fajlova
            viewModel.showError(PlaylistError.Kind.FILE_UNREADABLE)
        }
    }
}

@Composable
private fun displayName(uri: String): String {
    val context = LocalContext.current
    return queryDisplayName(context, Uri.parse(uri)) ?: uri
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (_: Exception) {
    null
}
