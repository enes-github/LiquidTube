package com.enes.liquidtube

import androidx.test.platform.app.InstrumentationRegistry
import com.enes.liquidtube.data.Translations
import com.enes.liquidtube.ui.theme.AppLanguage
import com.enes.liquidtube.ui.theme.tr
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The community-translation overlay: a non-English/Turkish language is read from assets and
 * used by [tr], untranslated lines fall back to English, and — the thing we must not break —
 * Turkish and English are completely unaffected.
 */
class TranslationsTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun reset() {
        AppLanguage.code = ""
        AppLanguage.translations = emptyMap()
    }

    @Test fun spanishFileLoadsFromAssets() {
        val es = Translations.load(ctx, "es")
        assertTrue("es.json should be bundled and parse", es.isNotEmpty())
        assertEquals("Inicio", es["Home"])
        assertEquals("Biblioteca", es["Library"])
    }

    @Test fun germanIsUsedWithEnglishFallback() {
        AppLanguage.code = "de"
        AppLanguage.translations = Translations.load(ctx, "de")
        assertEquals("Start", tr("Home", "Ana Sayfa"))
        assertEquals("Mediathek", tr("Library", "Kitaplık"))
        assertEquals("Only in English", tr("Only in English", "X"))
    }

    /** German and Spanish must cover every English line that's in the template. */
    @Test fun germanAndSpanishAreComplete() {
        val obj = org.json.JSONObject(ctx.assets.open("i18n/en.json").bufferedReader().use { it.readText() })
        val enKeys = obj.keys().asSequence().toList()
        assertTrue("template should have the full string set", enKeys.size >= 390)
        for (lang in listOf("de", "es")) {
            val map = Translations.load(ctx, lang)
            val missing = enKeys.filter { it !in map }
            assertTrue("$lang is missing ${missing.size} strings, e.g. ${missing.take(5)}", missing.isEmpty())
        }
    }

    @Test fun spanishIsUsedWithEnglishFallback() {
        AppLanguage.code = "es"
        AppLanguage.translations = Translations.load(ctx, "es")
        assertEquals("Inicio", tr("Home", "Ana Sayfa"))                 // translated
        assertEquals("Buscar", tr("Search", "Ara"))                    // translated
        assertEquals("Not translated yet", tr("Not translated yet", "X")) // falls back to English
    }

    @Test fun turkishIsUntouched() {
        AppLanguage.code = "tr"
        AppLanguage.translations = Translations.load(ctx, "tr") // no tr.json; stays empty
        assertEquals("Ana Sayfa", tr("Home", "Ana Sayfa"))
        assertEquals("Ara", tr("Search", "Ara"))
    }

    @Test fun englishIsUntouched() {
        AppLanguage.code = "en"
        AppLanguage.translations = Translations.load(ctx, "en")
        assertEquals("Home", tr("Home", "Ana Sayfa"))
        assertEquals("Search", tr("Search", "Ara"))
    }

    @Test fun spanishIsOfferedInThePicker() {
        assertTrue("es should be discovered in assets/i18n", Translations.available(ctx).contains("es"))
    }
}
