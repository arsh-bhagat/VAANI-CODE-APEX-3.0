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
import java.io.File

class WalkthroughTest {
    @get:Rule val compose = createComposeRule()

    private var offered by mutableStateOf(true)
    private var eligible by mutableStateOf(true)
    private var disguised by mutableStateOf(false)
    private var suppressed = false
    private var actions = 0
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun launchApp() {
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

    @Test fun firstUseTourAndReplayNeverInvokeFeatureActions() {
        launchApp()
        compose.onNodeWithText("Start walkthrough").performClick()
        compose.runOnIdle { assertTrue(suppressed) }
        walkthroughSteps.forEachIndexed { index, step ->
            compose.waitForIdle()
            compose.onNodeWithText(context.getString(step.feature.title)).assertIsDisplayed()
            // The modal window consumes even a hold in the spotlight region.
            compose.onNodeWithTag("walkthrough_overlay").performTouchInput { longClick(androidx.compose.ui.geometry.Offset(20f, 180f), 2200L) }
            compose.onNodeWithText(if (index == walkthroughSteps.lastIndex) "Finish" else "Next").performClick()
        }
        compose.runOnIdle { assertFalse(offered); assertFalse(suppressed); assertEquals(0, actions) }
        compose.onNodeWithText("Settings", useUnmergedTree = true).performClick()
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
