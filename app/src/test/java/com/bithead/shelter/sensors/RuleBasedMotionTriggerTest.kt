package com.bithead.shelter.sensors

import org.junit.Assert.*
import org.junit.Test

class RuleBasedMotionTriggerTest {
    private fun frame(ms: Long, acceleration: Float = 9.81f, rotation: Float = 0f) =
        ImuFrame(ms * 1_000_000L, floatArrayOf(0f, 0f, acceleration, rotation, 0f, 0f))

    @Test fun snatchOpensWindowImmediatelyWithoutModelOrWindowWarmup() {
        val trigger = RuleBasedMotionTrigger()
        assertFalse(trigger.add(frame(0), true, 0))
        assertTrue(trigger.add(frame(20, 25f, 7f), true, 20))
    }

    @Test fun asynchronousSensorReplayRoutesDropDirectlyToConfirmation() {
        val trigger = RuleBasedMotionTrigger()
        val openings = mutableListOf<Long>()
        val sampler = ImuResampler({ trigger.reset() }) { frame ->
            if (trigger.add(frame, true, frame.timestampNs / 1_000_000L)) openings.add(frame.timestampNs)
        }
        // Independent sensor streams: gyro timestamps are offset by 10 ms.
        for (ms in 0L..160L step 20) {
            val acceleration = when (ms) {
                0L -> 9.81f
                in 20L..100L -> 0f
                120L -> 80f
                else -> 9.81f
            }
            sampler.add(true, ms * 1_000_000L, floatArrayOf(0f, 0f, acceleration))
            sampler.add(false, (ms + 10) * 1_000_000L, floatArrayOf(0f, 0f, 0f))
        }
        assertEquals(listOf(110_000_000L), openings)
    }

    @Test fun dropOpensWindowOnlyAfterFreefallAndImpact() {
        val trigger = RuleBasedMotionTrigger()
        for (ms in 0L..60L step 20) assertFalse(trigger.add(frame(ms, 0f), true, ms))
        assertTrue(trigger.add(frame(80, 40f), true, 80))
    }

    @Test fun mlModeReservesDropCandidatesForClassifierButKeepsSnatchRules() {
        val trigger = RuleBasedMotionTrigger()
        for (ms in 0L..60L step 20) assertFalse(trigger.add(frame(ms, 0f), true, ms, acceptDrop = false))
        assertFalse(trigger.add(frame(80, 40f), true, 80, acceptDrop = false))
        trigger.reset()
        assertFalse(trigger.add(frame(100), true, 100, acceptDrop = false))
        assertTrue(trigger.add(frame(120, 25f, 7f), true, 120, acceptDrop = false))
    }

    @Test fun cooldownSurvivesResetAndRequiresFreshMotion() {
        val trigger = RuleBasedMotionTrigger()
        trigger.add(frame(0), true, 0)
        assertTrue(trigger.add(frame(20, 25f, 7f), true, 20))
        trigger.reset()
        assertFalse(trigger.add(frame(40), true, 40))
        assertFalse(trigger.add(frame(60, 25f, 7f), true, 60))
        assertFalse(trigger.add(frame(5_020), true, 5_020))
        assertTrue(trigger.add(frame(5_040, 25f, 7f), true, 5_040))
    }

    @Test fun confirmationAndEmergencySuppressionDiscardPartialGestures() {
        val trigger = RuleBasedMotionTrigger()
        for (ms in 0L..60L step 20) trigger.add(frame(ms, 0f), true, ms)
        assertFalse(trigger.add(frame(80, 40f), false, 80))
        assertFalse(trigger.add(frame(100, 40f), true, 100))
        assertFalse(trigger.add(frame(120, 9.81f, 7f), false, 120))
    }

    @Test fun pauseOrSensorGapDiscardsFreefallHistory() {
        val trigger = RuleBasedMotionTrigger()
        for (ms in 0L..60L step 20) trigger.add(frame(ms, 0f), true, ms)
        trigger.reset()
        assertFalse(trigger.add(frame(80, 40f), true, 80))
    }

    @Test fun normalMovementDoesNotOpenWindow() {
        val trigger = RuleBasedMotionTrigger()
        for (ms in 0L..5_000L step 20) assertFalse(trigger.add(frame(ms), true, ms))
        assertFalse(trigger.add(frame(5_020, 20f), true, 5_020)) // translation alone
        assertFalse(trigger.add(frame(5_040, 20f, 7f), true, 5_040)) // rotation alone
    }
}
