package com.bithead.shelter.sensors

import org.junit.Assert.*
import org.junit.Test

class ListeningWindowTest {
    @Test fun retriesAndRepeatedReadyCallbacksCannotExtendTheDeadline() {
        val window = ListeningWindow()
        assertTrue(window.start(1_000L))
        assertEquals(12_000L, window.remaining(1_000L))
        assertFalse(window.start(8_000L))
        assertEquals(5_000L, window.remaining(8_000L))
        assertEquals(0L, window.remaining(13_000L))
        assertFalse(window.start(14_000L))
        assertEquals(0L, window.remaining(14_000L))
    }
    @Test fun cancellationClearsDeadlineAndRequiresANewStart() {
        val window = ListeningWindow()
        assertFalse(window.started)
        assertEquals(0L, window.remaining(0L))
        window.start(1_000L)
        window.clear()
        assertFalse(window.started)
        assertEquals(0L, window.remaining(2_000L))
        assertTrue(window.start(20_000L))
        assertEquals(12_000L, window.remaining(20_000L))
    }
}
