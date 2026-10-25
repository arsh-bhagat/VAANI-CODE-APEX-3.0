package com.bithead.shelter.ui.components

import com.bithead.shelter.ui.localization.uiText

import androidx.compose.material3.Text
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.bithead.shelter.ui.theme.*
import java.util.Locale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun BrutalSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, label: String = "Toggle") {
    val localizedLabel = uiText(label)
    Box(modifier.size(64.dp, 48.dp).semantics { contentDescription = localizedLabel }
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(60.dp, 28.dp).brutalBorder().background(CanvasDark, RoundedCornerShape(4.dp))) {
            Box(Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(2.dp).size(32.dp, 24.dp)
                .background(SurfaceElevated, RoundedCornerShape(2.dp))
                .border(1.dp, if (checked && enabled) GoldPrimaryDim else BorderMedium, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
fun BrutalPanel(modifier: Modifier = Modifier, color: Color = SurfacePanel,
    header: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().neoShadow().brutalBorder()
        .clip(RoundedCornerShape(4.dp)).background(color)) {
        if (header != null) {
            Text(uiText(header.uppercase(Locale.ROOT)), Modifier.padding(16.dp),
                style = MaterialTheme.typography.labelMedium, color = TextMidGray)
            HorizontalDivider(color = BorderSubtle)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

enum class BrutalButtonTone { Primary, Emergency, Neutral }

@Composable
fun BrutalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    tone: BrutalButtonTone = BrutalButtonTone.Primary, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when (tone) {
        BrutalButtonTone.Primary -> GoldPrimaryDim
        BrutalButtonTone.Emergency -> CrimsonAlert
        BrutalButtonTone.Neutral -> SurfaceElevated
    }
    Button(onClick, modifier.offset(if (pressed) 1.dp else 0.dp, if (pressed) 1.dp else 0.dp)
        .heightIn(min = 52.dp).neoShadow().brutalBorder(), enabled = enabled,
        interactionSource = interaction, shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background,
            contentColor = if (tone == BrutalButtonTone.Primary) GoldOnPrimary else TextBone),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp), content = content)
}

enum class StatusTone { Armed, Critical, Mesh }

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier, tone: StatusTone = StatusTone.Armed) {
    val (background, foreground) = when (tone) {
        StatusTone.Armed -> GoldSubtleContainer to GoldPrimary
        StatusTone.Critical -> CrimsonContainer to CrimsonText
        StatusTone.Mesh -> SlateMeshSubtle to ShelterBlueSoft
    }
    Surface(modifier = modifier.heightIn(min = 22.dp), color = background,
        shape = RoundedCornerShape(9999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, foreground.copy(alpha = 0.5f))) {
        Text(uiText(text.uppercase(Locale.ROOT)), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = foreground)
    }
}

/** Completion means the hold finished; delivery status belongs to the emergency coordinator. */
@Composable
fun TactileHoldButton(onComplete: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var progress by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    val complete by rememberUpdatedState(onComplete)
    val haptics = LocalHapticFeedback.current
    val holdDescription = uiText("Hold for two seconds to alert trusted contacts and record evidence")
    val holdState = uiText(if (holding) "Holding" else "Ready")
    val activationLabel = uiText("Activate emergency recording")
    Box(modifier.fillMaxWidth().heightIn(min = 64.dp).neoShadow().brutalBorder(color = GoldPrimaryDim)
        .clip(RoundedCornerShape(4.dp)).background(SurfaceLowest)
        .semantics {
            role = Role.Button
            contentDescription = holdDescription
            stateDescription = holdState
            if (!enabled) disabled()
            // Accessibility services have no physical press-and-hold gesture.
            customActions = listOf(CustomAccessibilityAction(activationLabel) {
                if (enabled) { complete(); true } else false
            })
        }.pointerInput(enabled) {
            if (enabled) detectTapGestures(onPress = {
                coroutineScope {
                    holding = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val job = launch {
                        val started = SystemClock.uptimeMillis()
                        var lastPulse = 0
                        while (progress < 1f) {
                            withFrameNanos { }
                            progress = ((SystemClock.uptimeMillis() - started) / 2000f).coerceIn(0f, 1f)
                            val pulse = (progress * 4).toInt()
                            if (pulse > lastPulse) {
                                lastPulse = pulse
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        complete()
                    }
                    try { tryAwaitRelease() } finally {
                        job.cancel()
                        progress = 0f
                        holding = false
                    }
                }
            })
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(GoldSubtleContainer))
        }
        Text(uiText(if (holding) "HOLD FIRM…" else "PRESS & HOLD 2 SECONDS"),
            Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge,
            color = if (enabled) GoldPrimary else TextMutedCarbon)
    }
}
