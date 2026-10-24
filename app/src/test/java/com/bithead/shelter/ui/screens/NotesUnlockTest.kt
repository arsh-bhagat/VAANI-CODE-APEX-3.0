package com.bithead.shelter.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesUnlockTest {
    @Test fun acceptsConfiguredPhraseIgnoringCaseAndOuterWhitespace() {
        assertTrue(matchesNotesUnlock("  quiet harbor  ", "QUIET HARBOR"))
        assertTrue(matchesNotesUnlock("café", "CAFÉ"))
        assertTrue(matchesNotesUnlock("सुरक्षित", "सुरक्षित"))
    }

    @Test fun rejectsPartialPhrasesAndUnrelatedSearches() {
        assertFalse(matchesNotesUnlock("quiet", "QUIET HARBOR"))
        assertFalse(matchesNotesUnlock("quiet harbor groceries", "QUIET HARBOR"))
        assertFalse(matchesNotesUnlock("groceries", "QUIET HARBOR"))
    }

    @Test fun blankValuesNeverUnlock() {
        assertFalse(matchesNotesUnlock("", ""))
        assertFalse(matchesNotesUnlock("  ", "  "))
        assertFalse(matchesNotesUnlock("", "HELP"))
    }

    @Test fun changingSafewordDoesNotRetainThePreviousPhrase() {
        assertFalse(matchesNotesUnlock("old phrase", "NEW PHRASE"))
        assertTrue(matchesNotesUnlock("new phrase", "NEW PHRASE"))
    }

    @Test fun existingFallbackCodesRemainAvailable() {
        for (code in listOf("##", "VAANI", "911")) {
            assertTrue(matchesNotesUnlock(code, "QUIET HARBOR"))
        }
    }
}
