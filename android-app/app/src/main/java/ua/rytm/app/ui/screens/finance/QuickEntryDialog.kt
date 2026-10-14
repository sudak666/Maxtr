package ua.rytm.app.ui.screens.finance

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.icons.Mic
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.theme.RytmRadii

/** "кава 65 моно" typed or spoken → the transaction form, pre-filled (never saved here). */
@Composable
fun QuickEntryDialog(wallets: List<Wallet>, onDismiss: () -> Unit, onParsed: (QuickEntryParser.Result) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val locale = LocalConfiguration.current.locales[0]
    val voiceAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val hint = stringResource(R.string.quick_entry_hint)

    fun submit() {
        val result = QuickEntryParser.parse(text, wallets)
        if (result == null) showError = true else onParsed(result)
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
        text = spoken
        showError = false
        // A spoken line that parses goes straight on; otherwise it stays editable.
        QuickEntryParser.parse(spoken, wallets)?.let(onParsed)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RytmRadii.Sheet),
        title = { Text(stringResource(R.string.quick_entry_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.quick_entry_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; showError = false },
                    placeholder = { Text(stringResource(R.string.quick_entry_hint)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) { { Text(stringResource(R.string.quick_entry_no_amount)) } } else null,
                    trailingIcon = if (voiceAvailable) {
                        {
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, hint)
                                runCatching { voice.launch(intent) }
                            }) { Icon(RytmIcons.Mic, contentDescription = stringResource(R.string.quick_entry_voice)) }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(RytmRadii.Input),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            Button(onClick = ::submit, enabled = text.isNotBlank(), shape = RoundedCornerShape(RytmRadii.Control)) { Text(stringResource(R.string.quick_entry_next)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
