package com.bithead.shelter.ui.localization

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bithead.shelter.R
import com.bithead.shelter.ai.SpeechPreferences
import com.bithead.shelter.ui.components.UiLanguageSettingsCard
import com.bithead.shelter.ui.theme.ShelterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UiLanguageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchesUiAndWalkthroughWithoutChangingSavedProtectionSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("shelter_prefs", 0)
        SpeechPreferences.initialize(context)
        val originalUi = prefs.getString("ui_language", null)
        val originalSpeech = prefs.getString("speech_language", null)
        val originalSafeword = prefs.getString("user_safeword", null)
        UiLanguage.initialize(context)
        val speech = SpeechPreferences.language.value
        UiLanguage.select("en")
        try {
            compose.setContent {
                ProvideUiLanguage { ShelterTheme { Column {
                    UiLanguageSettingsCard(true)
                    Text(stringResource(R.string.tour_start))
                    Text(stringResource(R.string.mesh_entry_title))
                } } }
            }
            compose.onNodeWithText("हिन्दी").performClick()
            compose.onNodeWithText("इंटरफ़ेस की भाषा").assertIsDisplayed()
            compose.onNodeWithText("परिचय शुरू करें").assertExists()
            compose.onNodeWithText("लाइव मेश").assertExists()
            compose.onNodeWithText("मराठी").performClick()
            compose.onNodeWithText("इंटरफेसची भाषा").assertIsDisplayed()
            compose.onNodeWithText("परिचय सुरू करा").assertExists()
            compose.onNodeWithText("थेट मेश").assertExists()
            compose.runOnIdle {
                assertEquals("mr", prefs.getString("ui_language", null))
                UiLanguage.initialize(context)
                assertEquals("mr", UiLanguage.language.value)
                assertEquals(speech, SpeechPreferences.language.value)
                assertEquals(originalSpeech, prefs.getString("speech_language", null))
                assertEquals(originalSafeword, prefs.getString("user_safeword", null))
            }
        } finally {
            prefs.edit().apply {
                if (originalUi == null) remove("ui_language") else putString("ui_language", originalUi)
            }.commit()
            UiLanguage.initialize(context)
        }
    }
}
