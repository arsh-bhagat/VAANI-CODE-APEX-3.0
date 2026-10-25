package com.bithead.shelter.ui.walkthrough

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class WalkthroughPlacementTest {
    private val safe = Rect(16f, 40f, 384f, 760f)
    private val viewport = Rect(0f, 24f, 400f, 784f)

    @Test fun allElevenStepsKeepTheirFullTargetAndClearTheCard() {
        val targets = listOf(
            Rect(0f, 704f, 400f, 784f), // Navigation includes icons and labels, not system gestures.
            Rect(16f, 120f, 384f, 344f), // Safeword panel, taller than the old 64dp strip.
            Rect(32f, 148f, 368f, 212f), // Hold control.
            Rect(32f, 120f, 368f, 388f), // Contacts form.
            Rect(16f, 120f, 384f, 208f), // Vault.
            Rect(16f, 120f, 384f, 180f), // Camera actions.
            Rect(16f, 120f, 384f, 480f), // Entire map.
            Rect(16f, 120f, 384f, 450f), // Support panel.
            Rect(32f, 164f, 368f, 224f), // Decoy actions.
            Rect(16f, 120f, 384f, 380f), // Disguise.
            Rect(16f, 510f, 384f, 730f), // Replay near the end of Settings.
        )
        targets.forEachIndexed { index, target ->
            val placement = placeWalkthrough(safe, target, 20f, 200f, 432f, 520f, viewport)
            assertEquals("Step ${index + 1}", target, placement.highlight)
            assertClear(placement)
            assertTrue(placement.cardArea.top >= safe.top)
            assertTrue(placement.cardArea.bottom <= safe.bottom)
        }
    }

    @Test fun shortLandscapeWindowKeepsActionsAndVisibleHighlightSeparate() {
        val safe = Rect(16f, 40f, 784f, 336f)
        val placement = placeWalkthrough(safe, Rect(32f, 80f, 768f, 320f),
            20f, 200f, safe.height * .6f, 520f)
        assertTrue(placement.cardArea.height >= safe.height / 2f)
        assertTrue(placement.highlight!!.height > 0f)
        assertClear(placement)
        assertTrue(placement.cardArea.width <= 520f)
    }

    @Test fun missingAndOffscreenTargetsHaveNoFalseHighlight() {
        listOf(null, Rect(0f, -300f, 400f, -20f), Rect(0f, 900f, 400f, 950f)).forEach {
            assertNull(placeWalkthrough(safe, it, 20f, 200f, 432f, 520f).highlight)
        }
    }

    private fun assertClear(placement: WalkthroughPlacement) {
        val highlight = requireNotNull(placement.highlight)
        val card = placement.cardArea
        assertTrue("Card overlaps highlighted control", card.bottom <= highlight.top || card.top >= highlight.bottom)
        val gap = if (placement.cardAboveTarget) highlight.top - card.bottom else card.top - highlight.bottom
        assertTrue("Pointer needs clearance", gap >= 19.9f)
    }
}
