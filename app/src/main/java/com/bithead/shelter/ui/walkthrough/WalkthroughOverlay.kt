package com.bithead.shelter.ui.walkthrough

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bithead.shelter.R
import kotlinx.coroutines.delay

class WalkthroughScope(val lists: List<LazyListState>) {
    val targets = mutableStateMapOf<FeatureHelp, Rect>()
    var active by mutableStateOf(false)
    var destination by mutableIntStateOf(0)
    val list: LazyListState get() = lists[destination]
}

val LocalWalkthrough = staticCompositionLocalOf<WalkthroughScope?> { null }

@Composable
fun Modifier.featureTarget(feature: FeatureHelp): Modifier {
    val scope = LocalWalkthrough.current ?: return this
    val maxHighlightHeight = with(LocalDensity.current) { 180.dp.toPx() }
    val headingHeight = with(LocalDensity.current) { 64.dp.toPx() }
    DisposableEffect(scope, feature) { onDispose { scope.targets.remove(feature) } }
    return onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        if (bounds.width > 0 && bounds.height > 0) scope.targets[feature] = if (bounds.height > maxHighlightHeight)
            Rect(bounds.left, bounds.top, bounds.right, bounds.top + headingHeight) else bounds
    }
}

@Composable
fun FeatureDescription(feature: FeatureHelp) {
    val description = if (feature == FeatureHelp.DISGUISE)
        stringResource(feature.description, stringResource(R.string.ui_personal))
        else stringResource(feature.description)
    Text(description, style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun FeatureHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tour_choose_feature)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FeatureHelp.entries.forEach { feature ->
                    Text(stringResource(feature.title), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp).semantics { heading() })
                    FeatureDescription(feature)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.tour_close)) } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalkthroughOverlay(stepIndex: Int, target: Rect?, ready: Boolean,
    onStart: () -> Unit, onBack: () -> Unit, onNext: () -> Unit, onSkip: () -> Unit) {
    val welcome = stepIndex < 0
    val step = walkthroughSteps.getOrNull(stepIndex)
    var timedOut by remember(stepIndex, ready) { mutableStateOf(false) }
    LaunchedEffect(stepIndex, ready) { if (!ready) { delay(1800); timedOut = true } }
    Dialog(onDismissRequest = onSkip,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler { if (stepIndex > 0) onBack() else onSkip() }
        BoxWithConstraints(Modifier.fillMaxSize().testTag("walkthrough_overlay").pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                }
            }
        }) {
            val cardAtTop = target != null && target.center.y > constraints.maxHeight * .53f
            Canvas(Modifier.fillMaxSize()) {
                val rect = if (ready) target?.intersect(Rect(0f, 0f, size.width, size.height)) else null
                val shade = Color.Black.copy(alpha = .78f)
                if (rect == null || rect.width <= 0f || rect.height <= 0f) drawRect(shade)
                else {
                    drawRect(shade, size = Size(size.width, rect.top))
                    drawRect(shade, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
                    drawRect(shade, Offset(0f, rect.top), Size(rect.left, rect.height))
                    drawRect(shade, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
                    drawRect(Color(0xFFB4E3BE), rect.topLeft, rect.size,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
                    val x = rect.center.x.coerceIn(20.dp.toPx(), size.width - 20.dp.toPx())
                    val y = if (cardAtTop) rect.top else rect.bottom
                    val direction = if (cardAtTop) -1 else 1
                    val arrow = Path().apply {
                        moveTo(x, y)
                        lineTo(x - 9.dp.toPx(), y + direction * 18.dp.toPx())
                        lineTo(x + 9.dp.toPx(), y + direction * 18.dp.toPx())
                        close()
                    }
                    drawPath(arrow, Color(0xFFB4E3BE))
                }
            }
            Card(
                modifier = Modifier.align(if (welcome) androidx.compose.ui.Alignment.Center
                    else if (cardAtTop) androidx.compose.ui.Alignment.TopCenter else androidx.compose.ui.Alignment.BottomCenter)
                    .statusBarsPadding().navigationBarsPadding().padding(20.dp)
                    .widthIn(max = 520.dp).fillMaxWidth().heightIn(max = maxHeight * .52f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!welcome) Text(stringResource(R.string.tour_progress, stepIndex + 1, walkthroughSteps.size),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(stringResource(if (welcome) R.string.tour_welcome_title else step!!.feature.title),
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    if (welcome) Text(stringResource(R.string.tour_welcome_body))
                    else FeatureDescription(step!!.feature)
                    Text(stringResource(R.string.tour_passive), style = MaterialTheme.typography.bodySmall)
                    if (!welcome && timedOut) Text(stringResource(R.string.tour_target_unavailable),
                        style = MaterialTheme.typography.bodySmall)
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onSkip) {
                            Text(stringResource(if (welcome) R.string.tour_not_now else R.string.tour_skip))
                        }
                        if (stepIndex > 0) TextButton(onClick = onBack) { Text(stringResource(R.string.tour_back)) }
                        Button(onClick = if (welcome) onStart else onNext) {
                            Text(stringResource(if (welcome) R.string.tour_start else
                                if (stepIndex == walkthroughSteps.lastIndex) R.string.tour_finish else R.string.tour_next))
                        }
                    }
                }
            }
        }
    }
}

/** Lightweight contextual entry replaces repeated prose without coupling copy to behavior. */
@Composable
fun FeatureHelpLink(feature: FeatureHelp) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = true }) { Text(stringResource(com.bithead.shelter.R.string.tour_learn_feature, stringResource(feature.title))) }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false },
        title = { Text(stringResource(feature.title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { FeatureDescription(feature) } },
        confirmButton = { TextButton(onClick = { expanded = false }) { Text(stringResource(com.bithead.shelter.R.string.tour_close)) } })
}
