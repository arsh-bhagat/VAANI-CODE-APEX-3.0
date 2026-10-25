package com.bithead.shelter.ai

import org.junit.Assert.*
import org.junit.Test

class ListeningPolicyTest {
    @Test fun acousticOffStillAllowsMotionConfirmation() {
        assertFalse(ListeningPolicy.mayListen(false, true, false))
        assertTrue(ListeningPolicy.mayListen(false, true, true))
        assertTrue(ListeningPolicy.mayMatch(false, true, true, false, 15_000, 14_999))
        assertTrue(ListeningPolicy.mayActivate(false, true, true))
    }

    @Test fun motionWindowMustBeReadyAndStrictlyBeforeDeadline() {
        for (acoustic in listOf(false, true)) {
            assertFalse(ListeningPolicy.mayMatch(acoustic, true, true, true, 15_000, 100))
            assertFalse(ListeningPolicy.mayMatch(acoustic, true, true, false, 15_000, 15_000))
            assertFalse(ListeningPolicy.mayMatch(acoustic, true, true, false, 15_000, 16_000))
        }
    }

    @Test fun independentContinuousModeWorksWithMotionOnOrOff() {
        for (motion in listOf(false, true)) {
            assertTrue(ListeningPolicy.mayListen(true, motion, false))
            assertTrue(ListeningPolicy.mayMatch(true, motion, false, false, 0, 100))
            assertTrue(ListeningPolicy.mayActivate(true, motion, false))
        }
    }

    @Test fun noAcousticActivationOutsideMotionWindowWhenAcousticIsOff() {
        assertFalse(ListeningPolicy.mayMatch(false, true, false, false, 15_000, 100))
        assertFalse(ListeningPolicy.mayActivate(false, true, false))
        assertFalse(ListeningPolicy.mayListen(false, false, true))
        assertFalse(ListeningPolicy.mayMatch(false, false, true, false, 15_000, 100))
    }

    @Test fun disablingMotionCancelsDeferredMotionActivationEvenIfAcousticIsOn() {
        assertFalse(ListeningPolicy.mayActivate(true, false, true))
        assertFalse(ListeningPolicy.mayActivate(false, false, true))
    }
}
