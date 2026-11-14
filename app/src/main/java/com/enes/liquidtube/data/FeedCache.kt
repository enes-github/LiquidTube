package com.enes.liquidtube.data

import android.content.Context
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.FeedShelf
import com.enes.innertube.models.HomeChip
import com.enes.innertube.models.SectionRef
import com.enes.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last Home feed, saved on disk, so the app opens straight onto it while the fresh
 * one loads behind (like Apple Music showing your last Home instantly).
 */
@Singleton
class FeedCache @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "home_feed.json")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Shelf(
        val title: String,
        val songs: List<SongItem>,
        val collections: List<CollectionItem>,
        /** Original mixed order: "s3" = songs[3], "c0" = collections[0]. */
        val order: List<String>,
        val moreBrowseId: String? = null,
        val moreParams: String? = null,
        val moreReload: String? = null,
    )

    @Serializable
    private data class Chip(val title: String, val params: String)

    @Serializable
    private data class Feed(val shelves: List<Shelf>, val chips: List<Chip>, val signedIn: Boolean)

    suspend fun read(signedIn: Boolean): Pair<List<FeedShelf>, List<HomeChip>>? = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) return@runCatching null
            val feed = json.decodeFromString(Feed.serializer(), file.readText())
            if (feed.signedIn != signedIn) return@runCatching null // someone else's Home
            feed.shelves.map { s ->
                val items = s.order.mapNotNull { k ->
                    val i = k.drop(1).toIntOrNull() ?: return@mapNotNull null
                    if (k.startsWith("s")) s.songs.getOrNull(i) else s.collections.getOrNull(i)
                }
                FeedShelf(
                    s.title, s.songs, s.collections, items,
                    s.moreBrowseId?.let { SectionRef(it, s.moreParams, s.moreReload) },
                )
            } to feed.chips.map { HomeChip(it.title, it.params) }
        }.getOrNull()
    }

    suspend fun write(shelves: List<FeedShelf>, chips: List<HomeChip>, signedIn: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            val feed = Feed(
                shelves.map { sh ->
                    Shelf(
                        sh.title, sh.songs, sh.collections,
                        sh.items.mapNotNull { item ->
                            when (item) {
                                is SongItem -> sh.songs.indexOf(item).takeIf { it >= 0 }?.let { "s$it" }
                                is CollectionItem -> sh.collections.indexOf(item).takeIf { it >= 0 }?.let { "c$it" }
                                else -> null
                            }
                        },
                        sh.more?.browseId, sh.more?.params, sh.more?.reload,
                    )
                },
                chips.map { Chip(it.title, it.params) },
                signedIn,
            )
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(Feed.serializer(), feed))
            tmp.renameTo(file)
        }
    }
}
