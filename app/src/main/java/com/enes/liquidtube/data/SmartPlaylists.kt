package com.enes.liquidtube.data

import com.enes.liquidtube.ui.theme.tr
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

    /** Title and subtitle for a part of the day, in the app's language. */
    private fun partTitles(part: String): Pair<String, String> = when (part) {
        "morning" -> tr("Your Morning Mix", "Sabah Miksiniz") to tr("What you play in the mornings", "Sabahları dinledikleriniz")
        "afternoon" -> tr("Your Afternoon Mix", "Öğleden Sonra Miksiniz") to tr("What you play in the afternoons", "Öğleden sonraları dinledikleriniz")
        "evening" -> tr("Your Evening Mix", "Akşam Miksiniz") to tr("What you play in the evenings", "Akşamları dinledikleriniz")
        else -> tr("Your Late Night Mix", "Gece Yarısı Miksiniz") to tr("What you play late at night", "Gece geç saatlerde dinledikleriniz")
    }

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
        val (title, subtitle) = partTitles(part)
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
            Smart(ON_REPEAT, tr("On Repeat", "Tekrar Tekrar"), tr("Your most played this month", "Bu ay en çok dinledikleriniz"), onRepeat),
            Smart(MOST_PLAYED, tr("Most Played", "En Çok Dinlenenler"), tr("Your all-time most played", "Tüm zamanların en çok dinlenenleri"), mostPlayed),
            Smart(FORGOTTEN, tr("Forgotten Favorites", "Unutulan Favoriler"), tr("Loved, but not heard in a month", "Sevilen ama bir aydır dinlenmeyenler"), forgotten),
            Smart(UNPLAYED, tr("Downloaded, Never Played", "İndirilen, Hiç Dinlenmeyen"), tr("Downloads waiting for you", "Sizi bekleyen indirmeler"), unplayed),
        )
    }
}
