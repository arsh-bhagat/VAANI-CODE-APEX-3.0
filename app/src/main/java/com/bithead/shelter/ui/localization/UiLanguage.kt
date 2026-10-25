package com.bithead.shelter.ui.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Interface language is independent of recognition language and the stored trigger phrase. */
object UiLanguage {
    val choices: Map<String, String> = linkedMapOf("en" to "English", "hi" to "हिन्दी", "mr" to "मराठी")
    private val selected = MutableStateFlow("en")
    val language = selected.asStateFlow()
    private var context: Context? = null

    fun initialize(context: Context) {
        this.context = context.applicationContext
        selected.value = context.getSharedPreferences("shelter_prefs", Context.MODE_PRIVATE)
            .getString("ui_language", "en").orEmpty().takeIf { it in choices } ?: "en"
    }

    fun select(code: String) {
        require(code in choices)
        context?.getSharedPreferences("shelter_prefs", Context.MODE_PRIVATE)
            ?.edit()?.putString("ui_language", code)?.apply()
        selected.value = code
    }
}

val LocalUiLanguage = staticCompositionLocalOf { "en" }

/** Recompose in place: changing language must not restart recording or clear a Notes draft. */
@Composable
fun ProvideUiLanguage(content: @Composable () -> Unit) {
    val code by UiLanguage.language.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localized = remember(context, configuration, code) {
        Configuration(configuration).apply { setLocale(Locale.forLanguageTag(code)) }
    }
    val localizedContext = remember(context, localized) {
        android.view.ContextThemeWrapper(context, 0).apply { applyOverrideConfiguration(localized) }
    }
    CompositionLocalProvider(LocalUiLanguage provides code, LocalContext provides localizedContext,
        LocalConfiguration provides localized, content = content)
}

@Composable
fun uiText(text: String): String = translateUi(text, LocalUiLanguage.current)

/** For Android dialogs, notifications and callbacks outside composition. */
fun tr(source: String): String = translateUi(source, UiLanguage.language.value)

internal data class UiCopy(val en: String, val hi: String, val mr: String)

private val exactCopy by lazy { uiCopy.associateBy { it.en.lowercase(Locale.ROOT) } }
private val templates by lazy {
    uiCopy.filter { it.en.contains(Regex("%[1-9]\\d*\\\$s")) }.sortedByDescending { it.en.length }.map { copy ->
        val slots = Regex("%([1-9]\\d*)\\\$s").findAll(copy.en).toList()
        var end = 0
        val pattern = buildString {
            append("^")
            slots.forEach { slot ->
                append(Regex.escape(copy.en.substring(end, slot.range.first)))
                append("(.*?)")
                end = slot.range.last + 1
            }
            append(Regex.escape(copy.en.substring(end))).append("$")
        }
        Triple(copy, Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
            slots.map { it.groupValues[1].toInt() })
    }
}

fun translateUi(source: String, code: String): String {
    if (code !in setOf("hi", "mr")) return source
    fun UiCopy.value() = if (code == "hi") hi else mr
    exactCopy[source.lowercase(Locale.ROOT)]?.let { return it.value() }
    for ((copy, pattern, indices) in templates) {
        val match = pattern.matchEntire(source) ?: continue
        val arguments = indices.zip(match.groupValues.drop(1)).toMap()
        return Regex("%([1-9]\\d*)\\\$s").replace(copy.value()) { token ->
            val argument = arguments[token.groupValues[1].toInt()].orEmpty()
            // Translate controlled labels (e.g. media type); never rewrite arbitrary user content.
            exactCopy[argument.lowercase(Locale.ROOT)]?.value() ?: argument
        }
    }
    return source
}
