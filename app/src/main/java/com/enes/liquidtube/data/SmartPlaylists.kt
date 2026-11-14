package com.enes.liquidtube.data

import com.enes.liquidtube.download.DownloadInfo
import com.enes.innertube.models.SongItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Playlists that fill themselves from how you listen, updated as you play:
 *
 *  - On Repeat: what you've listened to most this month (by time, not just plays),
 *  - Most Played: your all-time most played songs,
 *  - Forgotten Favorites: songs you loved (favorites, played a lot) but haven't heard in a month,
 *  - Downloaded, Never Played: downloads you haven't got to yet.
 */
object SmartPlaylists {
    data class Smart(val id: String, val title: String, val subtitle: String, val songs: List<SongItem>)

    const val ON_REPEAT = "smart-onrepeat"
    const val MOST_PLAYED = "smart-mostplayed"
    const val FORGOTTEN = "smart-forgotten"
    const val UNPLAYED = "smart-unplayed"
    const val TIME_PREFIX = "smart-time-"

    private val PART_TITLES = mapOf(
        "morning" to ("Your Morning Mix" to "What you play in the mornings"),
        "afternoon" to ("Your Afternoon Mix" to "What you play in the afternoons"),
        "evening" to ("Your Evening Mix" to "What you play in the evenings"),
        "night" to ("Your Late Night Mix" to "What you play late at night"),
    )

    /**
     * The mix for a part of the day (now, by default): songs you actually play at that
     * time, most-played first with a light shuffle that changes once an hour. Null until
     * there's enough listening at that time (8 songs, a minute each).
     */
    fun timeMix(library: LibraryStore, part: String = daypart()): Smart? {
        val listened = library.dayparts.value[part].orEmpty().entries.filter { it.value >= 60_000 }
        if (listened.size < 8) return null
        val hour = System.currentTimeMillis() / 3_600_000
        val random = kotlin.random.Random(hour + part.hashCode())
        val songs = listened
            .map { it to it.value * (0.75 + random.nextDouble() * 0.5) } // most-played first, mixed a little
            .sortedByDescending { it.second }
            .mapNotNull { library.songFor(it.first.key) }
            .take(40)
        val (title, subtitle) = PART_TITLES.getValue(part)
        return Smart(TIME_PREFIX + part, title, subtitle, songs)
    }

    fun all(library: LibraryStore, downloads: Map<String, DownloadInfo>): List<Smart> {
        val counts = library.playCounts.value
        val last = library.lastPlayed.value
        val known = HashMap<String, SongItem>()
        (library.recents.value + library.frequent.value + library.favorites.value + downloads.values.map { it.song })
            .forEach { known.putIfAbsent(it.id, it) }
        fun song(id: String) = known[id] ?: library.songFor(id)

        val month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val onRepeat = library.listening.value[month].orEmpty().entries
            .filter { it.value >= 60_000 } // at least a minute this month
            .sortedByDescending { it.value }
            .mapNotNull { song(it.key) }
            .take(25)

        val mostPlayed = counts.entries.filter { it.value >= 2 }
            .sortedByDescending { it.value }
            .mapNotNull { song(it.key) }
            .take(50)

        val monthAgo = System.currentTimeMillis() - 30L * 24 * 3_600_000
        val forgotten = (library.favorites.value + library.frequent.value.filter { (counts[it.id] ?: 0) >= 3 })
            .distinctBy { it.id }
            .filter { (last[it.id] ?: 0L) < monthAgo }
            .sortedByDescending { counts[it.id] ?: 0 }
            .take(50)

        val unplayed = downloads.values.filter { it.isDone && (counts[it.song.id] ?: 0) == 0 }.map { it.song }

        return listOfNotNull(
            timeMix(library),
            Smart(ON_REPEAT, "On Repeat", "Your most played this month", onRepeat),
            Smart(MOST_PLAYED, "Most Played", "Your all-time most played", mostPlayed),
            Smart(FORGOTTEN, "Forgotten Favorites", "Loved, but not heard in a month", forgotten),
            Smart(UNPLAYED, "Downloaded, Never Played", "Downloads waiting for you", unplayed),
        )
    }
}
