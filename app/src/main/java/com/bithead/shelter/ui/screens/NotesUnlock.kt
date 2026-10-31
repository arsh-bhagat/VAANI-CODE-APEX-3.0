package com.bithead.shelter.ui.screens

import java.util.Locale

private val legacyNotesPasscodes = setOf("##", "vaani", "911")

/** Typed Notes navigation only; this does not activate emergency recording. */
internal fun matchesNotesUnlock(input: String, safeword: String): Boolean {
    val phrase = input.trim()
    return phrase.isNotEmpty() && (
        phrase.equals(safeword.trim(), ignoreCase = true) ||
            phrase.lowercase(Locale.ROOT) in legacyNotesPasscodes
        )
}
