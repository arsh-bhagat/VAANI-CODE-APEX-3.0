package com.bithead.shelter.ui.walkthrough

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bithead.shelter.ui.VaaniApp
import com.bithead.shelter.ui.components.EvidencePlaybackState
import com.bithead.shelter.ui.components.EvidenceMediaPreviewState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import java.io.File

class WalkthroughTest {
    @get:Rule val compose = createComposeRule()

    private var offered by mutableStateOf(true)
    private var eligible by mutableStateOf(true)
    private var disguised by mutableStateOf(false)
    private var suppressed = false
    private var actions = 0
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private var originalSetupDone: Boolean? = null
    private var setupPreferenceTouched = false

    @After fun restoreAccountSetupPreference() {
        if (setupPreferenceTouched) {
            context.getSharedPreferences("vaani_account_setup", 0).edit().apply {
                originalSetupDone?.let { putBoolean("done", it) } ?: remove("done")
            }.commit()
        }
    }

    private fun launchApp(setupDone: Boolean = true) {
        val prefs = context.getSharedPreferences("vaani_account_setup", 0)
        originalSetupDone = if (prefs.contains("done")) prefs.getBoolean("done", false) else null
        setupPreferenceTouched = true
        prefs.edit().putBoolean("done", setupDone).commit()
        compose.setContent {
            MaterialTheme {
                VaaniApp(
                    walkthroughPending = offered, walkthroughEligible = eligible,
                    onWalkthroughActive = { suppressed = it },
                    onWalkthroughHandled = { offered = false },
                    isEmergency = false, isSealing = false, safeword = "TEST PHRASE",
                    threatLabel = "Ready", threatScore = 0, lat = null, lng = null,
                    evidence = emptyList(), vaultUnlocked = false,
                    mapLat = null, mapLng = null, mapAccuracyMeters = null, mapLocationTimestamp = null,
                    biometricAvailable = false, snackbarHostState = remember { SnackbarHostState() },
                    onTrigger = { actions++ }, onEditSafeword = { actions++ },
                    playbackState = EvidencePlaybackState(), onPlaybackToggle = { actions++ },
                    onPlaybackStop = {}, onPlaybackSeek = { _, _ -> actions++ },
                    mediaPreviewState = EvidenceMediaPreviewState(), onOpenMedia = { actions++ }, onCloseMedia = {},
                    onExport = { actions++ }, onDeleteEvidence = { actions++ },
                    onVerifyChain = { actions++ }, onUnlockVault = { actions++ },
                    listening = false, listeningActive = false, onListeningChange = { actions++ },
                    gestureWakeMode = true, gestureWindowOpening = false, gestureWindowSecondsRemaining = 0,
                    onGestureWakeModeChange = { actions++ }, onLockVault = {}, onVaultHidden = {},
                    disguiseEnabled = disguised, onDisguiseEnabledChange = { actions++ }, onRefreshMapLocation = { actions++ },
                    trustedContacts = emptyList(), smsStatuses = emptyList(), locationTracking = false, cachedLocation = null,
                    onAddContact = { _, _ -> actions++ }, onRemoveContact = { actions++ },
                    onPickContact = { actions++ }, onLocationTrackingChange = { actions++ },
                    onRequestSmsPermission = { actions++ }, onImportMedia = { actions++ },
                    pendingEvidenceDir = File(context.cacheDir, "tour-test"), activeIncidentId = null,
                    onMediaCaptured = { actions++ }
                )
            }
        }
    }

    @Test fun accountSetupSurvivesPickerPauseAndCanBeSkippedBeforeTour() {
        launchApp(setupDone = false)
        compose.onNodeWithText("Sign in with Google").assertIsDisplayed()
        compose.onNodeWithText("Start walkthrough").assertDoesNotExist()
        compose.runOnIdle { eligible = false }
        compose.onNodeWithText("Continue without an account").assertIsDisplayed()
        compose.runOnIdle { eligible = true }
        compose.onNodeWithText("Continue without an account").performClick()
        compose.onNodeWithText("Start walkthrough").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(context.getSharedPreferences("vaani_account_setup", 0).getBoolean("done", false))
            assertEquals(0, actions)
        }
    }

    @Test fun firstUseTourAndReplayNeverInvokeFeatureActions() {
        launchApp()
        compose.onNodeWithText("Start walkthrough").performClick()
        compose.runOnIdle { assertTrue(suppressed) }
        walkthroughSteps.forEachIndexed { index, step ->
            compose.waitForIdle()
            compose.onNodeWithText(context.getString(step.feature.title)).assertIsDisplayed()
            compose.waitUntil(5_000L) {
                compose.onNodeWithTag("walkthrough_shade").fetchSemanticsNode()
                    .config[WalkthroughHighlightBounds] != androidx.compose.ui.geometry.Rect.Zero
            }
            val overlay = compose.onNodeWithTag("walkthrough_overlay").fetchSemanticsNode().boundsInRoot
            val highlight = compose.onNodeWithTag("walkthrough_shade").fetchSemanticsNode()
                .config[WalkthroughHighlightBounds].translate(overlay.topLeft)
            val card = compose.onNodeWithTag("walkthrough_card").fetchSemanticsNode().boundsInRoot
            assertTrue("Step ${index + 1}: card covers highlight",
                card.bottom <= highlight.top || card.top >= highlight.bottom)
            assertTrue("Step ${index + 1}: card outside viewport",
                card.top >= overlay.top && card.bottom <= overlay.bottom)
            // The overlay consumes even a hold in the spotlight region.
            compose.onNodeWithTag("walkthrough_overlay").performTouchInput { longClick(androidx.compose.ui.geometry.Offset(20f, 180f), 2200L) }
            compose.onNodeWithText(if (index == walkthroughSteps.lastIndex) "Finish" else "Next").performClick()
        }
        compose.runOnIdle { assertFalse(offered); assertFalse(suppressed); assertEquals(0, actions) }
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Replay walkthrough"))
        compose.onNodeWithText("Replay walkthrough").performClick()
        compose.onNodeWithText("Start walkthrough").performClick()
        compose.onNodeWithText("Skip tour").performClick()
        compose.onNodeWithText("Replay walkthrough").assertIsDisplayed()
        compose.runOnIdle { assertFalse(suppressed); assertEquals(0, actions) }
    }

    @Test fun notNowDismissesOfferAndHelpRemainsAvailable() {
        launchApp()
        compose.onNodeWithText("Not now").performClick()
        compose.runOnIdle { assertFalse(offered); assertFalse(suppressed) }
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Feature help"))
        compose.onNodeWithText("Feature help").performClick()
        compose.onNodeWithText("Learn about a feature").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Replay walkthrough").performClick()
        compose.onNodeWithText("Start walkthrough").assertIsDisplayed()
        compose.runOnIdle { assertTrue(suppressed); assertEquals(0, actions) }
    }

    @Test fun backgroundEligibilityHidesAndResumesTheSameStep() {
        launchApp()
        compose.onNodeWithText("Start walkthrough").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Step 2 of 11").assertIsDisplayed()
        compose.runOnIdle { eligible = false }
        compose.onNodeWithTag("walkthrough_overlay").assertDoesNotExist()
        compose.runOnIdle { assertTrue(suppressed); eligible = true }
        compose.onNodeWithText("Step 2 of 11").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithText("Step 1 of 11").assertIsDisplayed()
        compose.onNodeWithText("Skip tour").performClick()
        compose.runOnIdle { assertFalse(suppressed); assertEquals(0, actions) }
    }

    @Test fun typedConfiguredSafewordLeavesNotesWithoutActivatingFeatures() {
        offered = false
        disguised = true
        launchApp()
        compose.onNode(hasSetTextAction()).performTextInput("  test phrase  ")
        compose.onNodeWithText("All Notes").assertDoesNotExist()
        compose.onNodeWithText("ARMED OPERATIONS").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, actions)
            assertTrue(disguised)
        }
    }

    @Test fun deferredOfferInNotesDoesNotActivateSuppression() {
        disguised = true
        launchApp()
        compose.onNodeWithTag("walkthrough_overlay").assertDoesNotExist()
        compose.runOnIdle { assertTrue(offered); assertFalse(suppressed) }
        compose.onNodeWithText("PERSONAL").performTouchInput { longClick(durationMillis = 2200L) }
        compose.onNodeWithText("Start walkthrough").assertIsDisplayed()
        compose.runOnIdle { assertTrue(suppressed); assertEquals(0, actions) }
    }
}
