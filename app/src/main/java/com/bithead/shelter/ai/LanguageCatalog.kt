package com.bithead.shelter.ai

import java.text.Normalizer
import java.util.Locale

data class AppLanguage(val code: String, val name: String, val speechLocale: String, val suggestedSafeword: String)

object LanguageCatalog {
    val languages = listOf(
        AppLanguage("en", "English", "en-US", "HELP"),
        AppLanguage("hi", "हिन्दी / Hindi", "hi-IN", "मदद"),
        AppLanguage("mr", "मराठी / Marathi", "mr-IN", "मदत"),
        AppLanguage("bn", "বাংলা / Bengali", "bn-IN", "সাহায্য"),
        AppLanguage("ta", "தமிழ் / Tamil", "ta-IN", "உதவி"),
        AppLanguage("te", "తెలుగు / Telugu", "te-IN", "సహాయం"),
        AppLanguage("gu", "ગુજરાતી / Gujarati", "gu-IN", "મદદ"),
        AppLanguage("kn", "ಕನ್ನಡ / Kannada", "kn-IN", "ಸಹಾಯ"),
        AppLanguage("es", "Español / Spanish", "es-ES", "AYUDA"),
        AppLanguage("fr", "Français / French", "fr-FR", "AIDE")
    )
    fun get(code: String): AppLanguage = languages.firstOrNull { it.code == code } ?: languages.first()
}

/** Literal phrase matching with Unicode boundaries; never an implicit extra emergency trigger. */
object SafewordMatcher {
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    fun matchesExactly(text: String, phrase: String): Boolean {
        val target = normalize(phrase)
        return target.isNotEmpty() && normalize(text) == target
    }

    fun matches(transcripts: List<String>?, phrase: String): Boolean {
        val target = normalize(phrase)
        if (target.isEmpty()) return false
        val pattern = Regex("(?<![\\p{L}\\p{M}\\p{N}])${Regex.escape(target)}(?![\\p{L}\\p{M}\\p{N}])")
        return transcripts?.any { pattern.containsMatchIn(normalize(it)) } == true
    }
}
