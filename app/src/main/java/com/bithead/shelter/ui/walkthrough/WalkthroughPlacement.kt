package com.bithead.shelter.ui.walkthrough

import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

internal data class WalkthroughPlacement(
    val highlight: Rect?,
    val cardArea: Rect,
    val cardAboveTarget: Boolean,
)

/** All arguments use overlay-local pixels, including the usable system-inset bounds. */
internal fun placeWalkthrough(
    safe: Rect,
    target: Rect?,
    gap: Float,
    minimumCardHeight: Float,
    maximumCardHeight: Float,
    maximumCardWidth: Float,
    highlightBounds: Rect = safe,
): WalkthroughPlacement {
    val width = min(maximumCardWidth, safe.width)
    val left = safe.center.x - width / 2f
    val maxHeight = min(maximumCardHeight, safe.height)
    val visible = target?.intersect(highlightBounds)?.takeIf { it.width > 0f && it.height > 0f }
    if (visible == null) {
        return WalkthroughPlacement(null,
            Rect(left, safe.top, left + width, safe.bottom), false)
    }
    val above = max(0f, visible.top - gap - safe.top)
    val below = max(0f, safe.bottom - visible.bottom - gap)
    val cardAbove = above > below
    val room = if (cardAbove) above else below
    val minimum = min(minimumCardHeight, safe.height / 2f)
    // Very short windows / large targets cannot show the entire target and the card.
    // Keep the card usable and highlight only the visible portion outside its reserved area.
    val height = min(maxHeight, max(minimum, room))
    val area = if (room >= minimum) {
        if (cardAbove) Rect(left, visible.top - gap - height, left + width, visible.top - gap)
        else Rect(left, visible.bottom + gap, left + width, visible.bottom + gap + height)
    } else {
        if (cardAbove) Rect(left, safe.top, left + width, safe.top + height)
        else Rect(left, safe.bottom - height, left + width, safe.bottom)
    }
    val highlightArea = if (cardAbove)
        Rect(highlightBounds.left, area.bottom + gap, highlightBounds.right, highlightBounds.bottom)
        else Rect(highlightBounds.left, highlightBounds.top, highlightBounds.right, area.top - gap)
    val highlight = visible.intersect(highlightArea).takeIf { it.width > 0f && it.height > 0f }
    return WalkthroughPlacement(highlight, area, cardAbove)
}
