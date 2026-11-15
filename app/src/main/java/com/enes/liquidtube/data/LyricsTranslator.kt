package com.enes.liquidtube.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Translates lyrics line by line (Google Translate's public web endpoint), so a song
 * in another language shows what it means under each line. Saved on the phone once
 * done. Songs already in the target language come back as "no translation needed".
 */
object LyricsTranslator {
    private var dir: File? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val memory = ConcurrentHashMap<String, List<String>>()
    private val sameLanguage = ConcurrentHashMap.newKeySet<String>()

    /** Choices in Settings: code to name ("" = the phone's language). */
    val LANGUAGES = listOf(
        "" to "Phone Language", "en" to "English", "tr" to "Turkish", "es" to "Spanish",
        "de" to "German", "fr" to "French", "it" to "Italian", "pt" to "Portuguese",
        "ru" to "Russian", "ar" to "Arabic", "ja" to "Japanese", "ko" to "Korean",
    )

    fun init(context: Context) {
        dir = File(context.filesDir, "lyrics_tr").apply { mkdirs() }
    }

    fun target(setting: String): String = setting.ifBlank { java.util.Locale.getDefault().language.ifBlank { "en" } }

    /**
     * One translation per line (blank lines stay blank), or null when the song is
     * already in [lang] or it couldn't be translated.
     */
    private val lock = kotlinx.coroutines.sync.Mutex()

    suspend fun translate(songId: String, lines: List<String>, lang: String): List<String>? =
        lock.withLock { translateLocked(songId, lines, lang) }

    private suspend fun translateLocked(songId: String, lines: List<String>, lang: String): List<String>? = withContext(Dispatchers.IO) {
        val key = "${songId}_$lang"
        memory[key]?.let { return@withContext it }
        if (key in sameLanguage) return@withContext null
        val file = dir?.let { File(it, "$key.json") }
        file?.takeIf { it.exists() }?.let { f ->
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), f.readText()) }.getOrNull()
                ?.takeIf { it.size == lines.size }
                ?.let { memory[key] = it; return@withContext it }
        }
        val texts = lines.map { it.trim() }
        val toSend = texts.filter { it.isNotEmpty() }
        if (toSend.isEmpty()) return@withContext null
        val (translated, detected) = (runCatching { requestLines(toSend, lang) }.getOrNull()
            ?.let { Result.success(it) } ?: runCatching { request(toSend, lang) })
            .onFailure { android.util.Log.w("LiquidTube", "Translation failed", it) }
            .getOrNull() ?: return@withContext null
        android.util.Log.i("LiquidTube", "Translated ${toSend.size} lines -> ${translated.size} ($detected -> $lang)")
        if (detected == lang) {
            sameLanguage += key
            return@withContext null
        }
        if (translated.size != toSend.size) return@withContext null
        var j = 0
        val out = texts.map { if (it.isEmpty()) "" else translated[j++] }
        memory[key] = out
        runCatching { file?.writeText(json.encodeToString(ListSerializer(String.serializer()), out)) }
        out
    }

    /**
     * The main route: every line sent as its own text, translated one-to-one (so lines
     * can't run together), in batches.
     */
    private fun requestLines(lines: List<String>, lang: String): Pair<List<String>, String?> {
        val out = ArrayList<String>(lines.size)
        val langs = ArrayList<String?>(lines.size)
        for (batch in lines.chunked(60)) {
            val conn = URL("https://translate.googleapis.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=$lang")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 8_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            val form = batch.joinToString("&") { "q=" + URLEncoder.encode(it, "UTF-8") }
            conn.outputStream.use { it.write(form.toByteArray()) }
            val body = conn.inputStream.use { it.readBytes().decodeToString() }
            conn.disconnect()
            val root = json.parseToJsonElement(body) as JsonArray
            for (item in root) {
                when (item) {
                    is JsonArray -> {
                        out += (item.getOrNull(0) as? JsonPrimitive)?.contentOrNull.orEmpty()
                        langs += (item.getOrNull(1) as? JsonPrimitive)?.contentOrNull
                    }
                    is JsonPrimitive -> { out += item.contentOrNull.orEmpty(); langs += null }
                    else -> { out += ""; langs += null }
                }
            }
        }
        // Each line has its own detected language: the song's language is the most common
        // one (a single English tag must not make a Spanish song count as English), and
        // lines already in the target language get no translation under them.
        val main = langs.filterNotNull().groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val shown = out.mapIndexed { i, t -> if (langs.getOrNull(i) == lang) "" else t }
        return shown to main
    }

    /** Fallback: sends all lines at once (one per line) and splits the answer back into lines. */
    private fun request(lines: List<String>, lang: String): Pair<List<String>, String?> {
        val conn = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$lang&dt=t&ie=UTF-8&oe=UTF-8")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
        conn.setRequestProperty("User-Agent", BROWSER_UA)
        conn.outputStream.use { it.write(("q=" + URLEncoder.encode(lines.joinToString("\n"), "UTF-8")).toByteArray()) }
        val body = conn.inputStream.use { it.readBytes().decodeToString() }
        conn.disconnect()
        val root = json.parseToJsonElement(body) as JsonArray
        val pieces = (root[0] as? JsonArray).orEmpty()
        val detected = (root.getOrNull(2) as? JsonPrimitive)?.contentOrNull
        // Each piece is [translated, original, ...]; a line can be several pieces (one per
        // sentence), and its last piece's original ends with the line break. Rebuild the
        // lines from that instead of trusting the translated text's own line breaks.
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (piece in pieces) {
            val arr = piece as? JsonArray ?: continue
            val translated = (arr.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: continue
            val original = (arr.getOrNull(1) as? JsonPrimitive)?.contentOrNull.orEmpty()
            current.append(translated.replace("\n", " ").trim()).append(' ')
            if (original.endsWith("\n")) {
                out += current.toString().trim()
                current.clear()
            }
        }
        if (current.isNotBlank()) out += current.toString().trim()
        return out to detected
    }

    /** Sent as a regular phone browser: Android's default agent ("Dalvik/…") gets a bot check page. */
    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}
