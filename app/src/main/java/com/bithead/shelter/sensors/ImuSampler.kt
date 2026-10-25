package com.bithead.shelter.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/** Main-thread lifecycle and callbacks. No microphone or storage access. */
class ImuSampler(context: Context, onGap: () -> Unit, onFrame: (ImuFrame) -> Unit) : SensorEventListener {
    private val manager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val acceleration = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val rotation = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val resampler = ImuResampler(onGap, onFrame)
    private var started = false
    val isAvailable: Boolean get() = acceleration != null && rotation != null

    fun start(): Boolean {
        if (started) return true
        if (!isAvailable) return false
        resampler.reset()
        val a = manager.registerListener(this, acceleration, 20_000, 0)
        val g = manager.registerListener(this, rotation, 20_000, 0)
        started = a && g
        if (!started) manager.unregisterListener(this)
        return started
    }

    fun stop() { manager.unregisterListener(this); started = false; resampler.reset() }
    override fun onSensorChanged(event: SensorEvent) {
        if (started) resampler.add(event.sensor.type == Sensor.TYPE_ACCELEROMETER, event.timestamp, event.values)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
