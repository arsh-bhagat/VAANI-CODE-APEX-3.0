package com.bithead.shelter.ui.components

import com.bithead.shelter.ui.localization.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bithead.shelter.ai.LanguageCatalog
import com.bithead.shelter.ai.SpeechPreferences

@Composable
fun SpeechSettingsCard(safeword: String, speechStatus: String, onUseSafeword: (String) -> Unit,
    onRefreshSpeech: () -> Unit, onDownloadSpeech: () -> Unit, enabled: Boolean) {
    val languageCode by SpeechPreferences.language.collectAsStateWithLifecycle()
    val offlineOnly by SpeechPreferences.offlineSpeechOnly.collectAsStateWithLifecycle()
    var showLanguages by remember { mutableStateOf(false) }
    var confirmPhrase by remember { mutableStateOf(false) }
    val language = LanguageCatalog.get(languageCode)
    BrutalPanel(header = "Voice recognition") {
        Text(uiText("Speech language"), style = MaterialTheme.typography.titleMedium)
        Text(language.name)
        OutlinedButton(onClick = { showLanguages = true }, enabled = enabled) { Text(uiText("Choose speech language")) }
        Text(uiText("Speech language only changes voice recognition and phrase suggestions. Interface language is set separately above."))
        HorizontalDivider()
        Text(uiText(speechStatus))
        Row {
            Checkbox(offlineOnly, { SpeechPreferences.setOfflineSpeechOnly(it) }, enabled = enabled)
            Text(uiText("Use on-device speech only"), Modifier.weight(1f))
        }
        Text(uiText("Turning this off allows your device speech service to use the network and potentially send microphone audio for recognition."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRefreshSpeech, enabled = enabled) { Text(uiText("Check voice support")) }
            TextButton(onClick = onDownloadSpeech, enabled = enabled) { Text(uiText("Download speech model")) }
        }
        Text(uiText("Suggested trigger phrase (review and test before use)"))
        Text(language.suggestedSafeword)
        OutlinedButton(onClick = { confirmPhrase = true }, enabled = enabled && safeword != language.suggestedSafeword) {
            Text(uiText("Use suggested phrase"))
        }
        Text(uiText("Changing speech language never changes your saved safeword. Only the saved phrase is a trigger."))
    }
    if (showLanguages) AlertDialog(onDismissRequest = { showLanguages = false },
        title = { Text(uiText("Choose speech language")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            LanguageCatalog.languages.forEach { entry ->
                TextButton(onClick = { SpeechPreferences.selectLanguage(entry.code); showLanguages = false }, enabled = enabled) { Text(entry.name) }
            }
        } }, confirmButton = { TextButton(onClick = { showLanguages = false }) { Text(uiText("Close")) } })
    if (confirmPhrase) AlertDialog(onDismissRequest = { confirmPhrase = false },
        title = { Text(uiText("Replace active safeword?")) },
        text = { Column {
            Text(uiText("This explicitly changes your active trigger. Test recognition in your chosen speech language."))
            Text("$safeword → ${language.suggestedSafeword}")
        } }, confirmButton = { TextButton(onClick = { onUseSafeword(language.suggestedSafeword); confirmPhrase = false }, enabled = enabled) { Text(uiText("Use suggested phrase")) } },
        dismissButton = { TextButton(onClick = { confirmPhrase = false }) { Text(uiText("Cancel")) } })
}
