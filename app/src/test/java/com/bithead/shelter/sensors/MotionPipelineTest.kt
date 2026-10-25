package com.bithead.shelter.sensors

import org.junit.Assert.*
import org.junit.Test

class MotionPipelineTest {
    private fun frame(ms: Long, a: Float = 9.81f, g: Float = 0f) =
        ImuFrame(ms * 1_000_000, floatArrayOf(0f, 0f, a, g, 0f, 0f))

    @Test fun stationaryPhoneNeverQualifiesAsFreefall() {
        val gate = MotionAnomalyGate()
        for (ms in 0L..2_000L step 20) assertFalse(gate.add(frame(ms)))
        assertFalse(gate.add(frame(2_020, 40f))) // no preceding freefall or rotational shock
    }

    @Test fun dropRequiresSixtyMillisecondsOfFreefallAndTimelyImpact() {
        val gate = MotionAnomalyGate()
        for (ms in 0L..60L step 20) assertFalse(gate.add(frame(ms, 0f)))
        assertTrue(gate.add(frame(80, 40f)))
        gate.reset()
        for (ms in 0L..40L step 20) assertFalse(gate.add(frame(ms, 0f)))
        assertFalse(gate.add(frame(60, 40f)))
        gate.reset()
        for (ms in 0L..60L step 20) gate.add(frame(ms, 0f))
        for (ms in 80L..680L step 20) gate.add(frame(ms))
        assertFalse(gate.add(frame(700, 40f)))
    }

    @Test fun snatchNeedsJerkAndRotationTogether() {
        val gate = MotionAnomalyGate()
        gate.add(frame(0))
        assertFalse(gate.add(frame(20, 20f)))
        assertFalse(gate.add(frame(40, 20f, 7f)))
        assertTrue(gate.add(frame(60, 30f, 7f)))
    }

    @Test fun windowHasExactPreAndPostHistoryAndNoDuplicateTrigger() {
        val windows = mutableListOf<List<ImuFrame>>()
        val pipeline = MotionWindowPipeline { windows.add(it) }
        for (ms in 0L..1_480L step 20) pipeline.add(frame(ms))
        pipeline.add(frame(1_500, 25f, 7f))
        for (ms in 1_520L..1_980L step 20) pipeline.add(frame(ms))
        assertTrue(windows.isEmpty())
        pipeline.add(frame(2_000))
        assertEquals(1, windows.size)
        assertEquals(100, windows.single().size)
        assertEquals(20_000_000L, windows.single().first().timestampNs)
        assertEquals(2_000_000_000L, windows.single().last().timestampNs)
        assertEquals(25f, windows.single()[74].values[2], 0f)
        pipeline.add(frame(2_020, 25f, 7f))
        assertEquals(1, windows.size)
    }

    @Test fun gapDiscardsCandidateAndWarmupIsRequired() {
        var count = 0
        val pipeline = MotionWindowPipeline { count++ }
        pipeline.add(frame(0))
        pipeline.add(frame(20, 25f, 7f))
        for (ms in 40L..1_500L step 20) pipeline.add(frame(ms))
        assertEquals(0, count)
        pipeline.add(frame(1_520, 25f, 7f))
        for (ms in 1_560L..2_100L step 20) pipeline.add(frame(ms))
        assertEquals(0, count)
    }

    @Test fun asynchronousStreamsAreInterpolatedWithoutStalePairing() {
        val frames = mutableListOf<ImuFrame>()
        var gaps = 0
        val sampler = ImuResampler({ gaps++ }) { frames.add(it) }
        sampler.add(true, 0, floatArrayOf(0f, 0f, 0f))
        sampler.add(false, 10_000_000, floatArrayOf(1f, 0f, 0f))
        assertTrue(frames.isEmpty())
        sampler.add(true, 20_000_000, floatArrayOf(2f, 0f, 0f))
        sampler.add(false, 30_000_000, floatArrayOf(3f, 0f, 0f))
        sampler.add(true, 40_000_000, floatArrayOf(4f, 0f, 0f))
        assertEquals(listOf(10_000_000L, 30_000_000L), frames.map { it.timestampNs })
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f), frames[0].values, 0.0001f)
        assertArrayEquals(floatArrayOf(3f, 0f, 0f, 3f, 0f, 0f), frames[1].values, 0.0001f)
        sampler.add(true, 200_000_000, floatArrayOf(0f, 0f, 0f))
        assertEquals(1, gaps)
        assertEquals(2, frames.size)
    }

    @Test fun normalizationAndClassOrderMatchRuntimeContract() {
        val values = MotionContract.normalize(List(100) { frame(it * 20L, 9.81f, Math.PI.toFloat()) })
        assertEquals(600, values.size)
        assertEquals(1f, values[2], 0.0001f)
        assertEquals(1f, values[3], 0.0001f)
        assertTrue(MotionPrediction.from(floatArrayOf(.1f, .8f, .1f)).isThreat)
        assertTrue(MotionPrediction.from(floatArrayOf(.2f, .05f, .75f)).isThreat)
        assertFalse(MotionPrediction.from(floatArrayOf(.8f, .1f, .1f)).isThreat)
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedProbabilitiesCannotTrigger() {
        MotionPrediction.from(floatArrayOf(Float.NaN, 1f, 0f))
    }
}
