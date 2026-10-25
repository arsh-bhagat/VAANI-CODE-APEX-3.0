package com.bithead.shelter.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Speech settings only. Interface language is stored separately; UI translations are bundled. */
object SpeechPreferences {
    private lateinit var app: Context
    private var initialized = false
    private val mutableLanguage = MutableStateFlow("en")
    val language = mutableLanguage.asStateFlow()
    private val mutableOfflineSpeech = MutableStateFlow(true)
    val offlineSpeechOnly = mutableOfflineSpeech.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        app = context.applicationContext
        val prefs = app.getSharedPreferences("shelter_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("speech_language", null) ?: prefs.getString("selected_language", "en") ?: "en"
        mutableLanguage.value = LanguageCatalog.get(saved).code
        mutableOfflineSpeech.value = prefs.getBoolean("offline_speech_only", true)
        prefs.edit().putString("speech_language", mutableLanguage.value).remove("selected_language").apply()
    }

    fun selectLanguage(code: String) {
        if (LanguageCatalog.languages.none { it.code == code }) return
        mutableLanguage.value = code
        app.getSharedPreferences("shelter_prefs", Context.MODE_PRIVATE).edit().putString("speech_language", code).apply()
    }

    fun setOfflineSpeechOnly(value: Boolean) {
        mutableOfflineSpeech.value = value
        app.getSharedPreferences("shelter_prefs", Context.MODE_PRIVATE).edit().putBoolean("offline_speech_only", value).apply()
    }
}
