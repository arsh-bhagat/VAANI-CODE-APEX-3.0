package com.bithead.shelter.ui.walkthrough

import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.toSize
import com.bithead.shelter.R
import com.bithead.shelter.ui.theme.GoldPrimary
import kotlinx.coroutines.delay

class WalkthroughScope(val lists: List<LazyListState>) {
    val targets = mutableStateMapOf<FeatureHelp, Rect>()
    var viewport by mutableStateOf(Rect.Zero)
    var active by mutableStateOf(false)
}

val LocalWalkthrough = staticCompositionLocalOf<WalkthroughScope?> { null }
val WalkthroughHighlightBounds = SemanticsPropertyKey<Rect>("WalkthroughHighlightBounds")

@Composable
fun Modifier.featureTarget(feature: FeatureHelp): Modifier {
    val scope = LocalWalkthrough.current ?: return this
    DisposableEffect(scope, feature) { onDispose { scope.targets.remove(feature) } }
    return onGloballyPositioned { coordinates ->
        val bounds = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
        if (bounds.width > 0 && bounds.height > 0) scope.targets[feature] = bounds
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
    val focus = remember { FocusRequester() }
    LaunchedEffect(stepIndex) { focus.requestFocus() }
    BackHandler { if (stepIndex > 0) onBack() else onSkip() }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val leftInset = insets.getLeft(density, direction)
    val rightInset = insets.getRight(density, direction)
    val topInset = insets.getTop(density)
    val bottomInset = insets.getBottom(density)
    val margin = with(density) { 16.dp.toPx() }
    val gap = with(density) { 20.dp.toPx() }
    val minimumCardHeight = with(density) { 200.dp.toPx() }
    val maximumCardWidth = with(density) { 520.dp.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val pane = stringResource(if (welcome) R.string.tour_welcome_title else step!!.feature.title)

    // A sibling of Scaffold in the same Compose root: no second window or status-bar offset.
    SubcomposeLayout(Modifier.fillMaxSize().testTag("walkthrough_overlay")
        .onGloballyPositioned { origin = it.positionInRoot() }
        .focusRequester(focus).focusable()
        .semantics { paneTitle = pane; isTraversalGroup = true }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        }) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val safe = Rect(leftInset + margin, topInset + margin,
            (width - rightInset - margin).coerceAtLeast(leftInset + margin + 1f),
            (height - bottomInset - margin).coerceAtLeast(topInset + margin + 1f))
        val viewport = Rect(leftInset.toFloat(), topInset.toFloat(),
            (width - rightInset).toFloat(), (height - bottomInset).toFloat())
        val localTarget = if (ready && !welcome) target?.translate(-origin) else null
        val placement = placeWalkthrough(safe, localTarget, gap, minimumCardHeight,
            safe.height * .60f, maximumCardWidth, viewport)
        val card = subcompose("card") {
            key(stepIndex) {
                Card(Modifier.fillMaxWidth().testTag("walkthrough_card"),
                    shape = RoundedCornerShape(4.dp)) {
                    // The actions stay visible even with large text or a short landscape window.
                    Column(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!welcome) Text(stringResource(R.string.tour_progress,
                                stepIndex + 1, walkthroughSteps.size),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            Text(pane, style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.semantics { heading() })
                            if (welcome) Text(stringResource(R.string.tour_welcome_body))
                            else FeatureDescription(step!!.feature)
                            Text(stringResource(R.string.tour_passive), style = MaterialTheme.typography.bodySmall)
                            if (!welcome && timedOut) Text(stringResource(R.string.tour_target_unavailable),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        }.single().measure(Constraints(maxWidth = placement.cardArea.width.toInt().coerceAtLeast(1),
            maxHeight = (if (welcome) safe.height * .8f else placement.cardArea.height).toInt().coerceAtLeast(1)))
        val cardY = when {
            placement.highlight == null -> safe.center.y - card.height / 2f
            placement.cardAboveTarget -> placement.cardArea.bottom - card.height
            else -> placement.cardArea.top
        }
        val shade = subcompose("shade") {
            Canvas(Modifier.fillMaxSize().testTag("walkthrough_shade").semantics {
                this[WalkthroughHighlightBounds] = placement.highlight ?: Rect.Zero
            }) {
                val rect = placement.highlight
                val shadeColor = Color.Black.copy(alpha = .78f)
                if (rect == null) drawRect(shadeColor)
                else {
                    drawRect(shadeColor, size = Size(size.width, rect.top))
                    drawRect(shadeColor, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
                    drawRect(shadeColor, Offset(0f, rect.top), Size(rect.left, rect.height))
                    drawRect(shadeColor, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
                    drawRect(GoldPrimary, rect.topLeft, rect.size,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                    val x = rect.center.x
                    val y = if (placement.cardAboveTarget) rect.top else rect.bottom
                    val directionY = if (placement.cardAboveTarget) -1 else 1
                    val arrow = Path().apply {
                        moveTo(x, y)
                        lineTo(x - 7.dp.toPx(), y + directionY * 12.dp.toPx())
                        lineTo(x + 7.dp.toPx(), y + directionY * 12.dp.toPx())
                        close()
                    }
                    drawPath(arrow, GoldPrimary)
                }
            }
        }.single().measure(Constraints.fixed(width, height))
        layout(width, height) {
            shade.place(0, 0)
            card.place(placement.cardArea.left.toInt(), cardY.toInt())
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
