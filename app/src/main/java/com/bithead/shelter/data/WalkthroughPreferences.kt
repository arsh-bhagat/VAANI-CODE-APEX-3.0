package com.bithead.shelter.data

import android.content.Context

class WalkthroughPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("vaani_walkthrough", Context.MODE_PRIVATE)
    val shouldOffer: Boolean get() = preferences.getInt("handled_version", 0) < VERSION
    fun handle(completed: Boolean) {
        preferences.edit().putInt("handled_version", VERSION).apply {
            if (completed) putInt("completed_version", VERSION)
        }.apply()
    }
    companion object { const val VERSION = 1 }
}
