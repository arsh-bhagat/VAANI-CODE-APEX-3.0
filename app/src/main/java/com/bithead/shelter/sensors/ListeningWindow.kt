package com.bithead.shelter.sensors

/** Monotonic deadline shared by retries; repeated ready callbacks cannot extend a window. */
internal class ListeningWindow(private val durationMs: Long = 12_000L) {
    private var deadline: Long? = null
    val started: Boolean get() = deadline != null
    fun start(nowMs: Long): Boolean {
        if (started) return false
        deadline = nowMs + durationMs
        return true
    }
    fun remaining(nowMs: Long): Long = deadline?.let { (it - nowMs).coerceAtLeast(0L) } ?: 0L
    fun clear() { deadline = null }
}
