package com.bithead.shelter.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bithead.shelter.ui.theme.BorderSubtle

fun Modifier.neoShadow(offset: Dp = 2.dp, color: Color = Color.Black): Modifier = drawBehind {
    drawRoundRect(color, topLeft = Offset(offset.toPx(), offset.toPx()), size = size,
        cornerRadius = CornerRadius(4.dp.toPx()))
}

fun Modifier.brutalBorder(width: Dp = 1.dp, color: Color = BorderSubtle): Modifier =
    border(width, color, RoundedCornerShape(4.dp))
