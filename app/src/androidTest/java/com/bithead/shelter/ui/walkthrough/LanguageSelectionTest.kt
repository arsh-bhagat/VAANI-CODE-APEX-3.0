package com.bithead.shelter.ui.walkthrough

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import com.bithead.shelter.ui.components.SpeechSettingsCard
import com.bithead.shelter.ui.theme.ShelterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LanguageSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun suggestedPhraseRequiresExplicitConfirmation() {
        var saved = "PINEAPPLE"
        compose.setContent { ShelterTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
            SpeechSettingsCard(saved, "No speech service", { saved = it }, {}, {}, true)
            }
        } }
        compose.runOnIdle { assertEquals("PINEAPPLE", saved) }
        compose.onNodeWithText("Use suggested phrase").performScrollTo().performClick()
        compose.onNodeWithText("Replace active safeword?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals("PINEAPPLE", saved) }
    }
}
