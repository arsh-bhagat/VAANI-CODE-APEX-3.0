package com.bithead.shelter.sensors

/** Opens confirmation on a physics candidate, never an emergency directly. */
class RuleBasedMotionTrigger(private val cooldownMs: Long = 5_000L) {
    private val gate = MotionAnomalyGate()
    private var lastTriggerMs: Long? = null

    // Discard partial gestures across pause, sensor gaps and confirmation windows.
    // Preserve the cooldown so toggling protection cannot immediately retrigger it.
    fun reset() = gate.reset()

    fun add(frame: ImuFrame, eligible: Boolean, nowMs: Long, acceptDrop: Boolean = true): Boolean {
        val last = lastTriggerMs
        if (!eligible || (last != null && nowMs - last < cooldownMs)) {
            gate.reset()
            return false
        }
        if (!gate.add(frame)) return false
        if (!acceptDrop && gate.lastWasDrop) return false
        lastTriggerMs = nowMs
        gate.reset()
        return true
    }
}
