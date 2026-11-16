package com.enes.liquidtube.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The app's interface language. Held in Compose state, so every screen that shows a
 * [tr] text redraws the moment the language changes; no restart needed.
 */
object AppLanguage {
    /** "" = follow the phone, otherwise a language code ("en", "tr"). */
    var code by mutableStateOf("")

    /** The language actually in use right now. */
    val effective: String
        get() = code.ifBlank { Locale.getDefault().language.ifBlank { "en" } }

    val isTurkish: Boolean get() = effective.equals("tr", ignoreCase = true)
}

/**
 * [trText] when the app is in Turkish, otherwise [enText]. Works anywhere (screens,
 * labels, menus); inside a screen it also makes that screen follow language changes.
 */
fun tr(enText: String, trText: String): String = if (AppLanguage.isTurkish) trText else enText
