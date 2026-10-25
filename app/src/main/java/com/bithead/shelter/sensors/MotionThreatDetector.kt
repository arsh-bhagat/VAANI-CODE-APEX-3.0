package com.bithead.shelter.sensors

import android.content.Context
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import com.bithead.shelter.ai.MotionClassifier
import java.util.concurrent.Executors

/** Optional loose-drop ML verification; rules remain the fallback and detect snatches. */
class MotionThreatDetector(
    context: Context,
    private val canDetect: () -> Boolean,
    private val onThreatDetected: () -> Unit
) : AutoCloseable {
    private var started = false
    private var closed = false
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val classifier = MotionClassifier.looseDropOrNull(context.applicationContext)
    private var modelFailed = false
    private var inferencePending = false
    private var generation = 0L
    private var lastNotificationMs: Long? = null
    val usingMl: Boolean get() = classifier != null && !modelFailed
    private val trigger = RuleBasedMotionTrigger()
    private val pipeline: MotionWindowPipeline = MotionWindowPipeline { frames ->
        val model = classifier
        if (model != null && usingMl && !inferencePending) {
            inferencePending = true
            val token = generation
            worker.execute {
                val result = runCatching { model.classify(frames) }
                main.post {
                    inferencePending = false
                    if (closed) return@post
                    if (result.isFailure) {
                        modelFailed = true
                        resetHistory()
                    } else if (token == generation && result.getOrThrow().drop >= 0.80f) {
                        notifyThreat()
                    }
                }
            }
        }
    }
    private val sampler = ImuSampler(context, { resetHistory() }) { frame ->
        val now = SystemClock.elapsedRealtime()
        val coolingDown = lastNotificationMs?.let { now - it < 5_000L } == true
        if (!started || !canDetect() || coolingDown) {
            resetHistory()
        } else {
            // A trained binary drop model must not disable the separate snatch trigger.
            if (trigger.add(frame, true, now, acceptDrop = !usingMl)) {
                notifyThreat()
            } else if (usingMl && !inferencePending) {
                pipeline.add(frame)
            }
        }
    }

    private fun resetHistory() {
        generation++
        trigger.reset()
        pipeline.reset()
    }

    private fun notifyThreat() {
        val now = SystemClock.elapsedRealtime()
        val last = lastNotificationMs
        if (!started || closed || !canDetect() || (last != null && now - last < 5_000L)) return
        lastNotificationMs = now
        resetHistory()
        onThreatDetected()
    }

    val unavailableReason: String get() = if (!sampler.isAvailable)
        "Motion detection requires an accelerometer and gyroscope" else "Motion sensors could not be started"
    val isAvailable: Boolean get() = sampler.isAvailable && !closed

    fun start(): Boolean {
        if (started) return true
        if (!isAvailable) return false
        resetHistory()
        started = sampler.start()
        return started
    }

    fun stop() {
        started = false
        sampler.stop()
        resetHistory()
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        // Interpreter.close is serialized after any running inference.
        worker.execute { classifier?.close() }
        worker.shutdown()
    }
}
