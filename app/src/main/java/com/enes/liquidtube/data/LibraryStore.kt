package com.enes.liquidtube.data

import android.content.Context
import com.enes.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Local library: recently played + favorites, persisted across launches. */
@Singleton
class LibraryStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SongItem.serializer())

    private val _recents = MutableStateFlow(load(KEY_RECENTS))
    val recents: StateFlow<List<SongItem>> = _recents.asStateFlow()

    private val _favorites = MutableStateFlow(load(KEY_FAVORITES))
    val favorites: StateFlow<List<SongItem>> = _favorites.asStateFlow()

    private val countSerializer = kotlinx.serialization.builtins.MapSerializer(
        String.serializer(), Int.serializer(),
    )
    private val _playCounts = MutableStateFlow(
        prefs.getString(KEY_COUNTS, null)?.let { runCatching { json.decodeFromString(countSerializer, it) }.getOrNull() }.orEmpty()
    )
    /** How often each song was played (by video id): feeds the smart offline mix. */
    val playCounts: StateFlow<Map<String, Int>> = _playCounts.asStateFlow()

    private val _known = MutableStateFlow(load(KEY_KNOWN))
    /** Every song played at least twice, kept (up to 400) even after it leaves Recents. */
    val frequent: StateFlow<List<SongItem>> = _known.asStateFlow()

    private val timeSerializer = kotlinx.serialization.builtins.MapSerializer(String.serializer(), Long.serializer())
    private val _lastPlayed = MutableStateFlow(
        prefs.getString(KEY_LAST, null)?.let { runCatching { json.decodeFromString(timeSerializer, it) }.getOrNull() }.orEmpty()
    )
    /** When each song was last played (video id → epoch ms): smart playlists use it. */
    val lastPlayed: StateFlow<Map<String, Long>> = _lastPlayed.asStateFlow()

    fun addRecent(song: SongItem) {
        _lastPlayed.update { (it + (song.id to System.currentTimeMillis())).entries.sortedByDescending { e -> e.value }.take(4000).associate { e -> e.key to e.value } }
        prefs.edit().putString(KEY_LAST, json.encodeToString(timeSerializer, _lastPlayed.value)).apply()
        _recents.update { list -> (listOf(song) + list.filterNot { it.id == song.id }).take(100) }
        save(KEY_RECENTS, _recents.value)
        val count = (_playCounts.value[song.id] ?: 0) + 1
        _playCounts.update { it + (song.id to count) }
        prefs.edit().putString(KEY_COUNTS, json.encodeToString(countSerializer, _playCounts.value)).apply()
        if (count >= 2) {
            _known.update { list -> (listOf(song) + list.filterNot { it.id == song.id }).take(400) }
            save(KEY_KNOWN, _known.value)
        }
    }

    // ------------------------------------------------------------ Replay stats

    private val statSerializer = kotlinx.serialization.builtins.MapSerializer(
        String.serializer(), kotlinx.serialization.builtins.MapSerializer(String.serializer(), Long.serializer()),
    )
    private val _listening = MutableStateFlow(
        prefs.getString(KEY_LISTEN, null)?.let { runCatching { json.decodeFromString(statSerializer, it) }.getOrNull() }.orEmpty()
    )
    /** Listening time: month ("2026-11") → video id → milliseconds. */
    val listening: StateFlow<Map<String, Map<String, Long>>> = _listening.asStateFlow()

    private val songMeta = HashMap<String, SongItem>().apply { load(KEY_META).forEach { put(it.id, it) } }
    private var unsaved = 0L

    /** Songs seen in stats, by id (for titles and artwork). */
    fun songFor(id: String): SongItem? = synchronized(songMeta) { songMeta[id] }

    private val _dayparts = MutableStateFlow(
        prefs.getString(KEY_PARTS, null)?.let { runCatching { json.decodeFromString(statSerializer, it) }.getOrNull() }.orEmpty()
    )
    /** Listening time by part of the day ("morning", "afternoon", "evening", "night") → id → ms. */
    val dayparts: StateFlow<Map<String, Map<String, Long>>> = _dayparts.asStateFlow()

    /** Add [ms] of listening to [song] in the current month. */
    fun listen(song: SongItem, ms: Long) {
        val part = daypart()
        _dayparts.update { all ->
            val m = all[part].orEmpty()
            all + (part to (m + (song.id to ((m[song.id] ?: 0L) + ms))))
        }
        val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        synchronized(songMeta) { if (song.id !in songMeta) songMeta[song.id] = song }
        _listening.update { all ->
            val m = all[month].orEmpty()
            all + (month to (m + (song.id to ((m[song.id] ?: 0L) + ms))))
        }
        unsaved += ms
        if (unsaved >= 20_000) saveStats()
    }

    fun saveStats() {
        unsaved = 0
        prefs.edit()
            .putString(KEY_LISTEN, json.encodeToString(statSerializer, _listening.value))
            .putString(KEY_PARTS, json.encodeToString(statSerializer, _dayparts.value.mapValues { (_, m) ->
                // Keep each part's 400 most listened songs.
                m.entries.sortedByDescending { it.value }.take(400).associate { it.key to it.value }
            }))
            .apply()
        save(KEY_META, synchronized(songMeta) { songMeta.values.toList().takeLast(3000) })
    }

    fun toggleFavorite(song: SongItem) {
        _favorites.update { list ->
            if (list.any { it.id == song.id }) list.filterNot { it.id == song.id } else listOf(song) + list
        }
        save(KEY_FAVORITES, _favorites.value)
    }

    private fun load(key: String): List<SongItem> =
        prefs.getString(key, null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private fun save(key: String, list: List<SongItem>) {
        prefs.edit().putString(key, json.encodeToString(serializer, list)).apply()
    }

    private companion object {
        const val KEY_RECENTS = "recents"
        const val KEY_FAVORITES = "favorites"
        const val KEY_COUNTS = "play_counts"
        const val KEY_LISTEN = "listening"
        const val KEY_META = "stat_songs"
        const val KEY_KNOWN = "frequent"
        const val KEY_LAST = "last_played"
        const val KEY_PARTS = "dayparts"
    }
}

/** The part of the day right now: morning 5–11, afternoon 11–17, evening 17–22, night 22–5. */
fun daypart(hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): String = when (hour) {
    in 5..10 -> "morning"
    in 11..16 -> "afternoon"
    in 17..21 -> "evening"
    else -> "night"
}
