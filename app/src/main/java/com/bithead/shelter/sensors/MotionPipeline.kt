package com.bithead.shelter.sensors

import kotlin.math.PI
import kotlin.math.sqrt

/** Raw accelerometer m/s² (including gravity), gyroscope rad/s, monotonic nanoseconds. */
data class ImuFrame(val timestampNs: Long, val values: FloatArray)

object MotionContract {
    const val PERIOD_NS = 20_000_000L
    const val MAX_GAP_NS = 60_000_000L
    const val STEPS = 100
    const val CHANNELS = 6
    const val ASSET = "motion_threat_classifier.tflite"

    fun normalize(frames: List<ImuFrame>): FloatArray {
        require(frames.size == STEPS)
        return FloatArray(STEPS * CHANNELS) { index ->
            val channel = index % CHANNELS
            frames[index / CHANNELS].values[channel] / if (channel < 3) 9.81f else PI.toFloat()
        }
    }
}

/** Interpolates both streams at their shared watermark; never pairs stale latest values. */
class ImuResampler(private val onGap: () -> Unit, private val onFrame: (ImuFrame) -> Unit) {
    private data class Reading(val time: Long, val values: FloatArray)
    private val acceleration = ArrayDeque<Reading>()
    private val rotation = ArrayDeque<Reading>()
    private var nextTime: Long? = null

    fun reset() { acceleration.clear(); rotation.clear(); nextTime = null }

    fun add(isAcceleration: Boolean, timestampNs: Long, values: FloatArray) {
        if (values.size < 3 || values.take(3).any { !it.isFinite() }) {
            reset(); onGap(); return
        }
        val stream = if (isAcceleration) acceleration else rotation
        val previous = stream.lastOrNull()?.time
        if (previous != null && (timestampNs <= previous || timestampNs - previous > MotionContract.MAX_GAP_NS)) {
            reset(); onGap()
        }
        stream.addLast(Reading(timestampNs, values.copyOf(3)))
        // A missing second sensor must not grow memory indefinitely.
        if (stream.size > 128) { reset(); onGap(); return }
        if (acceleration.isEmpty() || rotation.isEmpty()) return
        var time = nextTime ?: maxOf(acceleration.first().time, rotation.first().time)
        val watermark = minOf(acceleration.last().time, rotation.last().time)
        while (time <= watermark) {
            val a = interpolate(acceleration, time)
            val g = interpolate(rotation, time)
            if (a != null && g != null) onFrame(ImuFrame(time, a + g)) else onGap()
            time += MotionContract.PERIOD_NS
        }
        nextTime = time
    }

    private fun interpolate(stream: ArrayDeque<Reading>, time: Long): FloatArray? {
        while (stream.size > 1 && stream[1].time <= time) stream.removeFirst()
        val left = stream.first()
        if (left.time == time) return left.values.copyOf()
        if (stream.size < 2 || left.time > time) return null
        val right = stream[1]
        if (right.time - left.time > MotionContract.MAX_GAP_NS) return null
        val fraction = (time - left.time).toFloat() / (right.time - left.time)
        return FloatArray(3) { left.values[it] + fraction * (right.values[it] - left.values[it]) }
    }
}

/** Candidate gate only: a candidate is never an emergency or a model prediction. */
class MotionAnomalyGate {
    var lastWasDrop: Boolean = false
        private set
    private var previous: ImuFrame? = null
    private var freefallStart: Long? = null
    private var freefallQualifiedAt: Long? = null

    fun reset() { previous = null; freefallStart = null; freefallQualifiedAt = null; lastWasDrop = false }

    fun add(frame: ImuFrame): Boolean {
        lastWasDrop = false
        val magnitude = magnitude(frame.values, 0)
        val last = previous
        previous = frame
        if (last != null && frame.timestampNs - last.timestampNs !in 1..MotionContract.MAX_GAP_NS) {
            reset(); previous = frame; return false
        }
        if (magnitude < 3f) {
            val start = freefallStart ?: frame.timestampNs.also { freefallStart = it }
            if (frame.timestampNs - start >= 60_000_000L) freefallQualifiedAt = frame.timestampNs
        } else freefallStart = null
        val qualified = freefallQualifiedAt
        val drop = qualified != null && frame.timestampNs - qualified <= 600_000_000L && magnitude > 35f
        lastWasDrop = drop
        if (drop || (qualified != null && frame.timestampNs - qualified > 600_000_000L)) freefallQualifiedAt = null
        val jerk = if (last == null) 0f else
            kotlin.math.abs(magnitude - magnitude(last.values, 0)) / ((frame.timestampNs - last.timestampNs) / 1e9f)
        return drop || (jerk > 150f && magnitude(frame.values, 3) > 6f)
    }

    private fun magnitude(values: FloatArray, offset: Int): Float =
        sqrt((0..2).sumOf { values[offset + it].toDouble() * values[offset + it] }.toFloat())
}

/** A 100-frame window covers (trigger - 1.5s, trigger + 0.5s], including the trigger. */
class MotionWindowPipeline(private val onWindow: (List<ImuFrame>) -> Unit) {
    private val frames = ArrayDeque<ImuFrame>()
    private val gate = MotionAnomalyGate()
    private var triggerTime: Long? = null
    private var lastTime: Long? = null

    fun reset() { frames.clear(); gate.reset(); triggerTime = null; lastTime = null }

    fun add(frame: ImuFrame) {
        val last = lastTime
        if (last != null && frame.timestampNs - last != MotionContract.PERIOD_NS) reset()
        lastTime = frame.timestampNs
        frames.addLast(ImuFrame(frame.timestampNs, frame.values.copyOf()))
        if (frames.size > MotionContract.STEPS) frames.removeFirst()
        val anomaly = gate.add(frame)
        if (triggerTime == null && frames.size >= 75 && anomaly) triggerTime = frame.timestampNs
        val trigger = triggerTime ?: return
        if (frame.timestampNs - trigger >= 500_000_000L) {
            if (frames.size == MotionContract.STEPS) onWindow(frames.toList())
            reset()
        }
    }
}

data class MotionPrediction(val normal: Float, val drop: Float, val snatch: Float) {
    val isThreat: Boolean get() = drop >= 0.80f || snatch >= 0.75f

    companion object {
        fun from(values: FloatArray): MotionPrediction {
            require(values.size == 3 && values.all { it.isFinite() && it in 0f..1f })
            require(kotlin.math.abs(values.sum() - 1f) <= 0.02f)
            return MotionPrediction(values[0], values[1], values[2])
        }
    }
}
