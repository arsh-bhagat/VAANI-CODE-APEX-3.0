package com.bithead.shelter.ai

/** Continuous acoustic activation and motion arming are independent preferences. */
object ListeningPolicy {
    fun mayListen(acousticEnabled: Boolean, motionEnabled: Boolean, windowPending: Boolean): Boolean =
        acousticEnabled || (motionEnabled && windowPending)

    fun mayMatch(acousticEnabled: Boolean, motionEnabled: Boolean, windowPending: Boolean,
        windowOpening: Boolean, deadlineMs: Long, nowMs: Long): Boolean =
        if (windowPending) motionEnabled && !windowOpening && deadlineMs > nowMs
        else acousticEnabled

    fun mayActivate(acousticEnabled: Boolean, motionEnabled: Boolean, confirmedInWindow: Boolean): Boolean =
        if (confirmedInWindow) motionEnabled else acousticEnabled
}
