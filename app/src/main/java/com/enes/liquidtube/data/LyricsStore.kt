package com.enes.liquidtube.data

import android.content.Context
import com.enes.innertube.models.LyricLine
import com.enes.innertube.models.Lyrics
import com.enes.innertube.models.SongItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Lyrics kept on the phone once fetched: they show offline, the lyrics widget can use
 * them, and Search can find a song in your library by a line you remember.
 */
object LyricsStore {
    private var dir: File? = null
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Saved(
        val song: SongItem,
        val times: List<Long> = emptyList(),
        val lines: List<String> = emptyList(),
        val plain: String? = null,
        val source: String,
    )

    /** For searching: song + its lines, normalized. Loaded on first search. */
    private class Entry(val song: SongItem, val lines: List<String>, val normalized: List<String>)

    private var index: MutableMap<String, Entry>? = null

    fun init(context: Context) {
        dir = File(context.filesDir, "lyrics").apply { mkdirs() }
    }

    private fun file(id: String) = dir?.let { File(it, "${id.replace('/', '_')}.json") }

    fun has(id: String) = file(id)?.exists() == true || noneFile(id)?.exists() == true

    private fun noneFile(id: String) = dir?.let { File(it, "${id.replace('/', '_')}.none") }

    /** Both lyrics sources answered "none": don't ask again for this song. */
    fun markNone(id: String) {
        runCatching { noneFile(id)?.createNewFile() }
    }

    /** All saved lyrics (file name → contents), for the backup. */
    fun exportRaw(): Map<String, String> =
        dir?.listFiles { f -> f.name.endsWith(".json") }?.associate { it.name to it.readText() }.orEmpty()

    /** Lyrics from a backup. */
    fun importRaw(files: Map<String, String>) {
        val d = dir ?: return
        files.forEach { (name, text) ->
            // Plain file names only (never a path out of the lyrics folder).
            if (name.endsWith(".json") && name == File(name).name) runCatching { File(d, name).writeText(text) }
        }
        synchronized(this) { index = null }
    }

    fun read(id: String): Lyrics? = runCatching {
        val f = file(id)?.takeIf { it.exists() } ?: return null
        val s = json.decodeFromString(Saved.serializer(), f.readText())
        Lyrics(
            synced = s.times.zip(s.lines).map { (t, l) -> LyricLine(t, l) }.takeIf { it.isNotEmpty() },
            plain = s.plain,
            source = s.source,
        )
    }.getOrNull()

    fun write(song: SongItem, lyrics: Lyrics) {
        runCatching {
            val f = file(song.id) ?: return
            val synced = lyrics.synced.orEmpty()
            val saved = Saved(song, synced.map { it.timeMs }, synced.map { it.text }, lyrics.plain, lyrics.source)
            f.writeText(json.encodeToString(Saved.serializer(), saved))
            synchronized(this) { index?.put(song.id, entry(saved)) }
        }
    }

    private fun entry(s: Saved): Entry {
        val lines = (s.lines.ifEmpty { s.plain.orEmpty().lines() }).map { it.trim() }.filter { it.isNotEmpty() }
        return Entry(s.song, lines, lines.map(FuzzySearch::normalize))
    }

    private fun loadIndex(): MutableMap<String, Entry> = synchronized(this) {
        index ?: HashMap<String, Entry>().also { map ->
            dir?.listFiles()?.forEach { f ->
                runCatching { json.decodeFromString(Saved.serializer(), f.readText()) }.getOrNull()
                    ?.let { map[it.song.id] = entry(it) }
            }
            index = map
        }
    }

    /** A song whose lyrics contain the search, with the line that matched. */
    data class Match(val song: SongItem, val line: String)

    /**
     * Songs whose lyrics contain [query] (accents and case ignored), best first: the
     * words together in one line beat words spread over several lines. Call off the main thread.
     */
    fun search(query: String, limit: Int = 6): List<Match> {
        val q = FuzzySearch.normalize(query)
        val words = q.split(' ').filter { it.length >= 2 }
        if (q.length < 4 || words.isEmpty()) return emptyList()
        return loadIndex().values.mapNotNull { e ->
            val exact = e.normalized.indexOfFirst { it.contains(q) }
            if (exact >= 0) return@mapNotNull Triple(e, exact, 3)
            val allWords = e.normalized.indexOfFirst { line -> words.all { line.contains(it) } }
            if (allWords >= 0) return@mapNotNull Triple(e, allWords, 2)
            null
        }
            .sortedByDescending { it.third }
            .take(limit)
            .map { (e, i, _) -> Match(e.song, e.lines[i]) }
    }
}
