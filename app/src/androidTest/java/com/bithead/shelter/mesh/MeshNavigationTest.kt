package com.bithead.shelter.mesh

import android.annotation.SuppressLint
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bithead.shelter.ui.localization.ProvideUiLanguage
import com.bithead.shelter.ui.localization.UiLanguage
import com.bithead.shelter.ui.screens.CommunityScreen
import com.bithead.shelter.ui.theme.ShelterTheme
import com.bithead.shelter.ui.walkthrough.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MeshNavigationTest {
    @get:Rule val compose = createComposeRule()

    // Lint 8.5.2 misidentifies this constructor result as Unit; Kotlin compiles it as WalkthroughScope.
    @SuppressLint("RememberReturnType")
    @Test fun localizedMeshPaneReturnsToMapAndYieldsToWalkthroughWithoutStartingRadio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("shelter_prefs", 0)
        val original = prefs.getString("ui_language", null)
        UiLanguage.initialize(context)
        UiLanguage.select("en")
        var tour: WalkthroughScope? = null
        try {
            compose.setContent {
                ProvideUiLanguage { ShelterTheme {
                    val lists = List(5) { rememberLazyListState() }
                    val scope = remember<WalkthroughScope> { WalkthroughScope(lists) }
                    SideEffect { tour = scope }
                    CompositionLocalProvider(LocalWalkthrough provides scope) {
                        CommunityScreen(null, null, null, null, {}, {})
                    }
                } }
            }
            val cases = listOf(
                Triple("en", "Real-time mesh", "Back to community map"),
                Triple("hi", "लाइव मेश", "समुदाय के नक्शे पर लौटें"),
                Triple("mr", "थेट मेश", "समुदायाच्या नकाशावर परत जा"),
            )
            cases.forEach { (code, entry, back) ->
                compose.runOnIdle { UiLanguage.select(code) }
                compose.onNodeWithText(entry).performClick()
                compose.onNodeWithText(back).assertIsDisplayed().performClick()
                compose.onNodeWithText(entry).assertIsDisplayed()
            }
            compose.onNodeWithText("थेट मेश").performClick()
            compose.runOnIdle { requireNotNull(tour).active = true }
            compose.onNodeWithText("समुदायाच्या नकाशावर परत जा").assertDoesNotExist()
            compose.onNodeWithText("थेट मेश").assertDoesNotExist()
            compose.waitUntil(5_000L) { tour?.targets?.containsKey(FeatureHelp.MAP) == true }
            compose.runOnIdle { assertTrue(requireNotNull(tour).targets.containsKey(FeatureHelp.MAP)) }
            // Start nearby mesh is deliberately never tapped, so this checks UI integration only.
        } finally {
            prefs.edit().apply {
                if (original == null) remove("ui_language") else putString("ui_language", original)
            }.commit()
            UiLanguage.initialize(context)
        }
    }
}
