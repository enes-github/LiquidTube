package com.enes.liquidtube.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The app's interface language. Held in Compose state, so every screen that shows a
 * [tr] text redraws the moment the language changes; no restart needed.
 *
 * English and Turkish are built in (the two arguments of [tr]). Any other language comes
 * from a community translation file in assets/i18n/<code>.json, loaded into [translations]
 * by the settings store and keyed by the English source string. See CONTRIBUTING-translations.md.
 */
object AppLanguage {
    /** "" = follow the phone, otherwise a language code ("en", "tr", "de", …). */
    var code by mutableStateOf("")

    /** Active language's translations, English text → translated text. Empty for en/tr. */
    var translations by mutableStateOf<Map<String, String>>(emptyMap())

    /** The language actually in use right now. */
    val effective: String
        get() = code.ifBlank { Locale.getDefault().language.ifBlank { "en" } }

    val isTurkish: Boolean get() = effective.equals("tr", ignoreCase = true)
    val isEnglish: Boolean get() = effective.equals("en", ignoreCase = true)
}

/**
 * The right text for the current language: [trText] in Turkish, [enText] in English, and
 * for any other language its community translation of [enText] (falling back to English
 * when a line hasn't been translated yet). Works anywhere — screens, labels, menus; inside
 * a screen it also makes that screen follow language changes.
 */
fun tr(enText: String, trText: String): String = when {
    AppLanguage.isTurkish -> trText
    AppLanguage.isEnglish -> enText
    else -> AppLanguage.translations[enText] ?: enText
}
