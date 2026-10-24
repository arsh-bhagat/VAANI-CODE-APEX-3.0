package com.bithead.shelter.sensors

import org.junit.Assert.*
import org.junit.Test

class ShakeTriggerGateTest {
    @Test fun singleBumpAndSustainedAccelerationDoNotWake() {
        val gate = ShakeTriggerGate()
        assertFalse(gate.onSample(2f, 0f, 0f, 0))
        for (t in 100L..1000L step 100) assertFalse(gate.onSample(2f, 0f, 0f, t))
    }
    @Test fun ordinaryMovementDoesNotWake() {
        val gate = ShakeTriggerGate()
        for (t in 0L..10000L step 100) assertFalse(gate.onSample(if (t % 200L == 0L) .7f else -.7f, .2f, .3f, t))
    }
    @Test fun deliberateAlternatingShakeWakesOnceAndHonorsCooldown() {
        val gate = ShakeTriggerGate()
        assertFalse(gate.onSample(2f, 0f, 0f, 0))
        assertFalse(gate.onSample(-2f, 0f, 0f, 150))
        assertTrue(gate.onSample(2f, 0f, 0f, 300))
        for (t in 400L..3000L step 100) assertFalse(gate.onSample(if (t % 200L == 0L) 2f else -2f, 0f, 0f, t))
        assertFalse(gate.onSample(2f, 0f, 0f, 3400))
        assertFalse(gate.onSample(-2f, 0f, 0f, 3550))
        assertTrue(gate.onSample(2f, 0f, 0f, 3700))
    }
    @Test fun isolatedImpulsesAndDifferentAxesDoNotAccumulate() {
        val gate = ShakeTriggerGate()
        assertFalse(gate.onSample(2f, 0f, 0f, 0))
        assertFalse(gate.onSample(0f, -2f, 0f, 150))
        assertFalse(gate.onSample(-2f, 0f, 0f, 1000))
        assertFalse(gate.onSample(2f, 0f, 0f, 2000))
    }
    @Test fun resetDropsPartialSequenceAndInvalidSamples() {
        val gate = ShakeTriggerGate()
        gate.onSample(2f, 0f, 0f, 0)
        gate.onSample(-2f, 0f, 0f, 150)
        gate.reset()
        assertFalse(gate.onSample(2f, 0f, 0f, 300))
        assertFalse(gate.onSample(Float.NaN, 0f, 0f, 450))
    }
}
