package com.enes.liquidtube.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Locale

/**
 * Community interface translations. Each language is a file in assets/i18n/<code>.json,
 * a flat JSON object mapping the English source text to its translation. English and
 * Turkish are built into the app (see [tr]); every other language lives here, so people
 * can add one by dropping in a file — no code changes. See CONTRIBUTING-translations.md.
 */
object Translations {
    private const val DIR = "i18n"
    private val cache = HashMap<String, Map<String, String>>()

    /** The translation map for [lang], read from assets (cached). Empty if there's no file. */
    fun load(context: Context, lang: String): Map<String, String> {
        if (lang.isBlank() || lang == "en") return emptyMap()
        cache[lang]?.let { return it }
        val map = runCatching {
            context.assets.open("$DIR/$lang.json").bufferedReader().use { it.readText() }
                .let { JSONObject(it) }
                .let { obj -> buildMap { obj.keys().forEach { k -> put(k, obj.getString(k)) } } }
        }.onFailure { Log.w("LiquidTube", "No/invalid translations for $lang: ${it.message}") }
            .getOrDefault(emptyMap())
        cache[lang] = map
        return map
    }

    /**
     * Language codes that have a translation file (besides the built-in en/tr), e.g. ["de", "es"].
     * "en" is the template for contributors, not an extra language, so it's left out.
     */
    fun available(context: Context): List<String> = runCatching {
        context.assets.list(DIR).orEmpty()
            .filter { it.endsWith(".json") }
            .map { it.removeSuffix(".json") }
            .filter { it != "en" && it != "tr" }
            .sorted()
    }.getOrDefault(emptyList())

    /** The language's own name for itself, e.g. "de" → "Deutsch", for the picker. */
    fun nativeName(code: String): String =
        Locale(code).getDisplayLanguage(Locale(code)).replaceFirstChar { it.uppercase(Locale(code)) }

    /** Its English name, e.g. "de" → "German". */
    fun englishName(code: String): String =
        Locale(code).getDisplayLanguage(Locale.ENGLISH).replaceFirstChar { it.uppercase() }
}
