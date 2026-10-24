package com.bithead.shelter.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class GestureDetector(context: Context, private val onJerkDetected: () -> Unit) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gate = ShakeTriggerGate()
    private val gravity = FloatArray(3)
    private var lastTimestamp = 0L
    private var started = false
    val isAvailable: Boolean get() = accelerometer != null

    fun start(): Boolean {
        if (started) return true
        if (accelerometer == null) return false
        reset()
        started = manager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        return started
    }

    fun reset() {
        gate.reset()
        lastTimestamp = 0L
    }

    fun stop() {
        if (started) manager.unregisterListener(this)
        started = false
        reset()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!started || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (lastTimestamp == 0L || event.timestamp - lastTimestamp > 500_000_000L) {
            event.values.copyInto(gravity, endIndex = 3)
            lastTimestamp = event.timestamp
            gate.reset()
            return
        }
        val dt = (event.timestamp - lastTimestamp) / 1_000_000_000f
        lastTimestamp = event.timestamp
        // Time-based low pass estimates gravity; rotation or a static tilted phone is not a shake.
        val alpha = 0.8f / (0.8f + dt.coerceAtLeast(0f))
        val linear = FloatArray(3)
        for (i in 0..2) {
            gravity[i] = alpha * gravity[i] + (1f - alpha) * event.values[i]
            linear[i] = (event.values[i] - gravity[i]) / SensorManager.GRAVITY_EARTH
        }
        if (gate.onSample(linear[0], linear[1], linear[2], event.timestamp / 1_000_000L)) onJerkDetected()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Requires three strong, alternating impulses on one axis, not a single bump.
 * Conservative defaults need physical-device calibration before release.
 */
internal class ShakeTriggerGate(
    private val thresholdG: Float = 1.6f,
    private val minImpulseMs: Long = 80L,
    private val sequenceMs: Long = 900L,
    private val cooldownMs: Long = 3_000L
) {
    private var axis = -1
    private var sign = 0
    private var count = 0
    private var firstAt = 0L
    private var lastImpulseAt = 0L
    private var lastTriggerAt: Long? = null

    fun onSample(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return false
        if (lastTriggerAt?.let { nowMs - it < cooldownMs } == true) return false
        if (count > 0 && (nowMs < lastImpulseAt || nowMs - firstAt > sequenceMs)) clearSequence()
        val values = floatArrayOf(x, y, z)
        val candidate = values.indices.maxByOrNull { kotlin.math.abs(values[it]) } ?: return false
        if (kotlin.math.abs(values[candidate]) < thresholdG) return false
        val nextSign = if (values[candidate] > 0) 1 else -1
        if (count == 0) {
            axis = candidate
            sign = nextSign
            count = 1
            firstAt = nowMs
            lastImpulseAt = nowMs
            return false
        }
        if (candidate != axis || nextSign == sign || nowMs - lastImpulseAt < minImpulseMs) return false
        sign = nextSign
        lastImpulseAt = nowMs
        count++
        if (count < 3) return false
        lastTriggerAt = nowMs
        clearSequence()
        return true
    }

    private fun clearSequence() { axis = -1; sign = 0; count = 0 }
    fun reset() { clearSequence(); lastTriggerAt = null }
}
