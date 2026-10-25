package com.bithead.shelter.ai

import org.junit.Assert.*
import org.junit.Test

class SafewordMatcherTest {
    @Test fun blankPhraseNeverTriggers() {
        assertFalse(SafewordMatcher.matches(listOf("any speech"), "  "))
        assertFalse(SafewordMatcher.matches(null, "HELP"))
    }
    @Test fun wordsMatchWithoutTriggeringOnSubstrings() {
        assertTrue(SafewordMatcher.matches(listOf("Please HELP!"), "help"))
        assertFalse(SafewordMatcher.matches(listOf("helpful helper"), "HELP"))
    }
    @Test fun customPhraseHasNoImplicitEnglishFallback() {
        assertFalse(SafewordMatcher.matches(listOf("help"), "pineapple"))
        assertTrue(SafewordMatcher.matches(listOf("nothing", "a PINEAPPLE please"), "pineapple"))
    }
    @Test fun unicodeCombiningMarksArePartOfWords() {
        assertTrue(SafewordMatcher.matches(listOf("मला मदत करा!"), "मदत"))
        assertTrue(SafewordMatcher.matches(listOf("मदद!"), "मदद"))
        assertFalse(SafewordMatcher.matches(listOf("मददगार"), "मदद"))
        assertTrue(SafewordMatcher.matches(listOf("உதவி!"), "உதவி"))
    }
    @Test fun normalizationPreservesLiteralPhraseMeaning() {
        assertTrue(SafewordMatcher.matches(listOf("please  get\tme out!"), "get me out"))
        assertTrue(SafewordMatcher.matches(listOf("ＨＥＬＰ"), "HELP"))
        assertFalse(SafewordMatcher.matches(listOf("madat"), "मदत"))
    }
}
