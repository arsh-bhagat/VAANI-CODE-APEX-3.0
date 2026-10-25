package com.bithead.shelter.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bithead.shelter.ui.localization.*

@Composable
fun UiLanguageSettingsCard(enabled: Boolean) {
    val selected = LocalUiLanguage.current
    BrutalPanel(header = "Interface language") {
        Text(uiText("Choose the language used throughout VAANI."))
        UiLanguage.choices.forEach { (code, name) ->
            TextButton(onClick = { UiLanguage.select(code) }, enabled = enabled,
                modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == code, onClick = null, enabled = enabled)
                    Spacer(Modifier.width(12.dp))
                    Text(name)
                }
            }
        }
        Text(uiText("Changing interface language does not change speech recognition, your safeword or saved notes."))
    }
}
