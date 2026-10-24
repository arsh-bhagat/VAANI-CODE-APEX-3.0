package com.bithead.shelter.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bithead.shelter.ui.theme.GoldPrimary
import java.util.Locale
import kotlin.math.sin

/** Decorative activity indicator; deliberately not presented as measured audio amplitude. */
@Composable
fun AudioWaveformSimulator(active: Boolean, modifier: Modifier = Modifier, color: Color = GoldPrimary) {
    val phase = if (active) {
        val transition = rememberInfiniteTransition(label = "audioActivity")
        val value by transition.animateFloat(0f, 6.283185f,
            infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "wavePhase")
        value
    } else 0f
    Canvas(modifier.height(48.dp).width(156.dp)) {
        val step = size.width / 13f
        repeat(13) { i ->
            val fraction = if (active) 0.18f + 0.82f * ((sin(phase + i * 1.7f) + 1f) / 2f) else 0.12f
            val height = size.height * fraction
            drawRect(color, Offset(step * i + step * 0.3f, (size.height - height) / 2), Size(step * 0.4f, height))
        }
    }
}

@Composable
fun TimerDisplay(elapsedSeconds: Long, modifier: Modifier = Modifier) {
    val seconds = elapsedSeconds.coerceAtLeast(0)
    Text(String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60), modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 32.sp, lineHeight = 40.sp,
            fontFeatureSettings = "tnum"))
}
