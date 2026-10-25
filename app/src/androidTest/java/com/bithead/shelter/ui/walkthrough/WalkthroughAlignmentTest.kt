package com.bithead.shelter.ui.walkthrough

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WalkthroughAlignmentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allStepsUseFullMeasuredBoundsEvenWhenOverlayRootHasAnOffset() {
        var index by mutableIntStateOf(0)
        var actual by mutableStateOf(Rect.Zero)
        compose.setContent {
            MaterialTheme {
                val scope = rememberWalkthroughScope()
                val feature = walkthroughSteps[index].feature
                // Reproduce a host with a status-bar-like root offset; dialog-local coordinates
                // must not be substituted for this app-root measurement.
                Box(Modifier.fillMaxSize().padding(top = 32.dp, bottom = 28.dp)) {
                    CompositionLocalProvider(LocalWalkthrough provides scope) {
                        key(index) {
                            Box(Modifier.padding(start = 24.dp, top = 64.dp).size(240.dp, 200.dp)
                                .featureTarget(feature).onGloballyPositioned {
                                    actual = Rect(it.positionInRoot(), it.size.toSize())
                                })
                        }
                    }
                    WalkthroughOverlay(index, scope.targets[feature], scope.targets[feature] != null,
                        {}, {}, {}, {})
                }
            }
        }
        walkthroughSteps.indices.forEach { step ->
            compose.runOnIdle { index = step }
            compose.waitUntil(5_000L) {
                compose.onNodeWithTag("walkthrough_shade").fetchSemanticsNode()
                    .config[WalkthroughHighlightBounds].height > 0f
            }
            val overlay = compose.onNodeWithTag("walkthrough_overlay").fetchSemanticsNode().boundsInRoot
            val highlight = compose.onNodeWithTag("walkthrough_shade").fetchSemanticsNode()
                .config[WalkthroughHighlightBounds].translate(overlay.topLeft)
            compose.runOnIdle {
                assertEquals("Step ${step + 1}: horizontal offset", actual.left, highlight.left, 1f)
                assertEquals("Step ${step + 1}: vertical offset", actual.top, highlight.top, 1f)
                assertEquals("Step ${step + 1}: target was truncated", actual.height, highlight.height, 1f)
                assertEquals(actual.width, highlight.width, 1f)
            }
        }
    }

    @Composable
    private fun rememberWalkthroughScope(): WalkthroughScope {
        val lists = List(5) { rememberLazyListState() }
        return remember { WalkthroughScope(lists) }
    }
}
