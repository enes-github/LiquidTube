package com.enes.innertube

import com.enes.innertube.models.AccountInfo
import com.enes.innertube.models.BrowsePage
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.CollectionType
import com.enes.innertube.models.FeedShelf
import com.enes.innertube.models.Lyrics
import com.enes.innertube.models.PlaylistOption
import com.enes.innertube.models.SearchResults
import com.enes.innertube.models.SearchFilter
import com.enes.innertube.models.SearchPage
import com.enes.innertube.models.HomeChip
import com.enes.innertube.models.HomeFeed
import com.enes.innertube.models.SectionRef
import com.enes.innertube.models.SectionChip
import com.enes.innertube.models.SectionPage
import com.enes.innertube.models.SongItem
import com.enes.innertube.models.StreamInfo
import com.enes.innertube.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URLDecoder
import java.security.MessageDigest

/** Shared HTTP client for the module (InnerTube + LrcLib). */
internal object Http {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        expectSuccess = true
    }
}

/**
 * InnerTube client for YouTube Music.
 *
 * - Browsing/search/library go to music.youtube.com as WEB_REMIX. When a Google
 *   account cookie is set, those requests are authenticated (SAPISIDHASH), so
 *   home, library, liked songs etc. are the user's own.
 * - Playback resolves through VISIONOS then ANDROID_VR on youtubei.googleapis.com,
 *   anonymously, with a visitorData. Both return direct, un-ciphered audio URLs
 *   that download in full without a poToken.
 */
object InnerTube {

    /** Diagnostic logger (the app routes this to Logcat). Never receives cookies or URLs. */
    @Volatile var logger: (String) -> Unit = {}

    @Volatile var hl: String = "en"
    @Volatile var gl: String = "US"

    /** Full Cookie header from a signed-in music.youtube.com session, or null. */
    @Volatile var cookie: String? = null
        set(value) {
            field = value?.takeIf { it.isNotBlank() }
            sapisid = field?.let(::parseSapisid)
        }

    @Volatile private var sapisid: String? = null
    val isLoggedIn: Boolean get() = sapisid != null

    private const val ORIGIN = "https://music.youtube.com"
    private const val MUSIC_API = "$ORIGIN/youtubei/v1"
    private const val YT_API = "https://youtubei.googleapis.com/youtubei/v1"
    private const val SONGS_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"

    private val PLAYER_CLIENTS = listOf(YouTubeClient.VISIONOS, YouTubeClient.ANDROID_VR)
    private val DURATION = Regex("""\d+(:\d{2}){1,2}""")
    private val YEAR = Regex("""\d{4}""")
    private val TYPE_PREFIXES = setOf("Song", "Video", "Single", "EP", "Album", "Playlist", "Artist")

    // ------------------------------------------------------------ visitor id

    @Volatile private var visitorData: String? = null
    private val visitorLock = Mutex()

    private suspend fun visitorData(): String? {
        visitorData?.let { return it }
        return visitorLock.withLock {
            visitorData ?: runCatching {
                post("$MUSIC_API/visitor_id", YouTubeClient.WEB_REMIX, JsonObject(emptyMap()), includeVisitor = false)
                    .obj("responseContext")?.str("visitorData")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
            }.getOrNull()?.also { visitorData = it }
        }
    }

    // ----------------------------------------------------------------- search

    suspend fun searchSongs(query: String): List<SongItem> {
        val res = web("search", buildJsonObject {
            put("query", query)
            put("params", SONGS_PARAMS)
        })
        return collect(res, "musicResponsiveListItemRenderer")
            .mapNotNull { parseResponsive(it) as? SongItem }
            .distinctBy { it.id }
    }

    /** One filter's results (e.g. only albums), paged. */
    suspend fun searchFiltered(query: String, filter: SearchFilter): SearchPage {
        val res = web("search", buildJsonObject {
            put("query", query)
            filter.params?.let { put("params", java.net.URLDecoder.decode(it, "UTF-8")) }
        })
        return searchPage(res)
    }

    suspend fun searchMore(token: String): SearchPage {
        val res = post("$MUSIC_API/search", YouTubeClient.WEB_REMIX, JsonObject(emptyMap()),
            query = mapOf("ctoken" to token, "continuation" to token, "type" to "next"))
        return searchPage(res)
    }

    private fun searchPage(res: JsonObject): SearchPage {
        val items = collect(res, "musicResponsiveListItemRenderer").mapNotNull(::parseResponsive)
            .distinctBy { (it as? SongItem)?.id ?: (it as CollectionItem).browseId }
        return SearchPage(items, findFirst(res, "nextContinuationData")?.str("continuation"))
    }

    /** Unfiltered search: top result plus songs, artists, albums, playlists. */
    suspend fun search(query: String): SearchResults {
        val res = web("search", buildJsonObject { put("query", query) })

        var topSong: SongItem? = null
        var topCollection: CollectionItem? = null
        collect(res, "musicCardShelfRenderer").firstOrNull()?.let { card ->
            val title = card.obj("title")?.runs()?.joinToString("") ?: return@let
            val thumb = card.thumbUrl()?.let(::hiResThumbnail)
            val sub = card.obj("subtitle")?.runs().orEmpty()
            val nav = card.obj("title")?.let { firstNav(it) }
            val videoId = findFirst(card.obj("title") ?: card, "watchEndpoint")?.str("videoId")
            val groups = splitBullets(sub)
            if (videoId != null) {
                topSong = buildSong(videoId, title, groups, thumb)
            } else if (nav != null) {
                val type = pageType(nav.second) ?: return@let
                topCollection = CollectionItem(
                    browseId = normalizeBrowseId(nav.first),
                    title = title,
                    // "Artist • 223M monthly audience": the type is shown separately.
                    subtitle = (if (groups.firstOrNull() in TYPE_PREFIXES) groups.drop(1) else groups)
                        .joinToString(" · ").ifBlank { null },
                    thumbnailUrl = thumb,
                    type = type,
                )
            }
        }

        // Songs listed inside an artist's top-result card omit the artist (it's implied
        // by the card), so fill it in from the card.
        val cardArtist = topCollection?.takeIf { it.type == CollectionType.ARTIST }
        val items = collect(res, "musicResponsiveListItemRenderer").mapNotNull(::parseResponsive).map { item ->
            if (item is SongItem && item.artists == UNKNOWN_ARTIST && cardArtist != null) {
                item.copy(artists = cardArtist.title, artistId = item.artistId ?: cardArtist.browseId)
            } else item
        }
        val collections = items.filterIsInstance<CollectionItem>().distinctBy { it.browseId }
        return SearchResults(
            topSong = topSong,
            topCollection = topCollection,
            songs = items.filterIsInstance<SongItem>().distinctBy { it.id },
            artists = collections.filter { it.type == CollectionType.ARTIST },
            albums = collections.filter { it.type == CollectionType.ALBUM },
            playlists = collections.filter { it.type == CollectionType.PLAYLIST },
        )
    }

    // -------------------------------------------------------------- feeds

    /** Home feed (personalized when signed in), following a few continuation pages. */
    suspend fun home(maxPages: Int = 4): List<FeedShelf> = homeFeed(null, maxPages).shelves

    /** Home feed plus its mood chips; [params] (from a chip) filters the feed by mood. */
    suspend fun homeFeed(params: String?, maxPages: Int = 4): HomeFeed {
        var res = web("browse", buildJsonObject {
            put("browseId", "FEmusic_home")
            params?.let { put("params", it) }
        })
        val chips = collect(res, "chipCloudChipRenderer").mapNotNull { chip ->
            val title = chip.obj("text")?.runs()?.joinToString("")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val p = findFirst(chip, "browseEndpoint")?.str("params") ?: return@mapNotNull null
            HomeChip(title, p)
        }.distinctBy { it.title }
        val shelves = parseCarousels(res).toMutableList()
        repeat(maxPages - 1) {
            val token = findFirst(res, "nextContinuationData")?.str("continuation") ?: return HomeFeed(shelves, chips)
            res = webContinuation(token, query = true)
            val more = parseCarousels(res)
            if (more.isEmpty()) return HomeFeed(shelves, chips)
            shelves += more
        }
        return HomeFeed(shelves, chips)
    }

    /** Explore: new albums & singles, trending, new videos. */
    suspend fun explore(): List<FeedShelf> =
        parseCarousels(web("browse", buildJsonObject { put("browseId", "FEmusic_explore") }))

    suspend fun charts(): List<FeedShelf> =
        parseCarousels(web("browse", buildJsonObject { put("browseId", "FEmusic_charts") }))

    // ------------------------------------------------------------ browse page

    suspend fun browse(browseIdIn: String): BrowsePage {
        val browseId = normalizeBrowseId(browseIdIn)
        val res = web("browse", buildJsonObject { put("browseId", browseId) })
        val type = typeForBrowseId(browseId)

        val header = HEADER_KEYS.firstNotNullOfOrNull { findFirst(res, it) }
        val title = header?.obj("title")?.runs()?.joinToString("")
            ?: findFirst(res, "microformatDataRenderer")?.str("title")
            ?: ""
        val subtitle = header?.obj("subtitle")?.runs()?.let { splitBullets(it).joinToString(" · ") }
        val straplineObj = header?.obj("straplineTextOne")
        val strapline = straplineObj?.runs()?.joinToString("")?.takeIf { it.isNotBlank() }
        val straplineId = straplineObj?.let { firstNav(it)?.first }
        val meta = header?.obj("secondSubtitle")?.runs()?.joinToString("")?.takeIf { it.isNotBlank() }
        val description = findFirst(res, "musicDescriptionShelfRenderer")
            ?.obj("description")?.runs()?.joinToString("")
            ?: header?.obj("description")?.runs()?.joinToString("")
        val thumb = header?.let { findFirstAny(it, "thumbnails") }
            ?.let { (it as? JsonArray) }
            ?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }
            ?: (findFirstAny(res, "thumbnails") as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }
        val pageThumb = thumb?.let(::hiResThumbnail)

        val listContainers = collect(res, "musicShelfRenderer") + collect(res, "musicPlaylistShelfRenderer")
        val songs = listContainers
            .flatMap { collect(it, "musicResponsiveListItemRenderer") }
            .mapNotNull { parseResponsive(it) as? SongItem }
            .distinctBy { it.id }
            .map { s ->
                // Album tracks have no artwork/artist of their own: inherit the page's.
                s.copy(
                    thumbnailUrl = s.thumbnailUrl ?: pageThumb,
                    artists = if (s.artists == UNKNOWN_ARTIST && strapline != null) strapline else s.artists,
                    artistId = s.artistId ?: straplineId,
                    album = s.album ?: if (type == CollectionType.ALBUM) title else null,
                    albumId = s.albumId ?: if (type == CollectionType.ALBUM) browseId else null,
                )
            }

        return BrowsePage(
            browseId = browseId,
            type = type,
            title = title,
            subtitle = subtitle,
            strapline = strapline,
            straplineId = straplineId,
            meta = meta,
            description = description,
            thumbnailUrl = pageThumb,
            songs = songs,
            shelves = parseCarousels(res),
            continuation = listContainers.firstNotNullOfOrNull(::continuationToken),
            playlistId = when (type) {
                CollectionType.PLAYLIST -> browseId.removePrefix("VL")
                CollectionType.ALBUM -> (allStrings(res, "playlistId") + allStrings(res, "audioPlaylistId")).firstOrNull { it.startsWith("OLAK5uy_") }
                else -> null
            },
            editable = findFirstAny(res, "musicEditablePlaylistDetailHeaderRenderer") != null,
            channelId = header?.let { findFirst(it, "subscribeButtonRenderer") }?.str("channelId"),
            // In the library already: the header's like/save toggle is on.
            saved = header?.let { h ->
                // The save toggle (a bookmark icon) is the header toggle that sends a like.
                collect(h, "toggleButtonRenderer").any { t ->
                    (t["isToggled"] as? JsonPrimitive)?.contentOrNull == "true" &&
                        findFirstAny(t, "likeEndpoint") != null
                }
            } ?: false,
            subscribed = header?.let { findFirst(it, "subscribeButtonRenderer") }
                ?.let { (it["subscribed"] as? JsonPrimitive)?.contentOrNull == "true" } ?: false,
            audience = header?.obj("monthlyListenerCount")?.runs()?.joinToString("")?.takeIf { it.isNotBlank() },
            songsMore = if (type == CollectionType.ARTIST) {
                collect(res, "musicShelfRenderer").firstOrNull()?.obj("title")
                    ?.let { findFirst(it, "browseEndpoint") }?.let(::sectionRef)
            } else null,
        )
    }

    private fun sectionRef(be: JsonObject): SectionRef? {
        val id = be.str("browseId") ?: return null
        return SectionRef(id, be.str("params")?.let { java.net.URLDecoder.decode(it, "UTF-8") })
    }

    /** A full section: all top songs, the whole discography, all videos… */
    suspend fun section(ref: SectionRef): SectionPage {
        val res = if (ref.reload != null) webContinuation(ref.reload, query = false)
        else web("browse", buildJsonObject {
            put("browseId", ref.browseId)
            ref.params?.let { put("params", it) }
        })
        val title = HEADER_KEYS.firstNotNullOfOrNull { findFirst(res, it) }?.obj("title")?.runs()?.joinToString("")
            ?: findFirst(res, "musicHeaderRenderer")?.obj("title")?.runs()?.joinToString("")
        val chips = collect(res, "chipCloudChipRenderer").mapNotNull { chip ->
            val t = chip.obj("text")?.runs()?.joinToString("")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val nav = chip.obj("navigationEndpoint") ?: return@mapNotNull null
            val r = findFirst(nav, "browseEndpoint")?.let(::sectionRef)
                ?: findFirst(nav, "reloadContinuationData")?.str("continuation")?.let { SectionRef(ref.browseId, ref.params, reload = it) }
                ?: return@mapNotNull null
            SectionChip(t, r, (chip["isSelected"] as? JsonPrimitive)?.contentOrNull == "true")
        }
        return SectionPage(title, sectionItems(res), chips, continuationToken(res))
    }

    suspend fun sectionMore(token: String): SectionPage {
        val (raw, isCommand) = decodeToken(token)
        val res = webContinuation(raw, query = !isCommand)
        return SectionPage(null, sectionItems(res), emptyList(), continuationToken(res))
    }

    private fun sectionItems(res: JsonObject): List<Any> {
        val twoRow = collect(res, "musicTwoRowItemRenderer").mapNotNull(::parseTwoRow)
        val rows = collect(res, "musicResponsiveListItemRenderer").mapNotNull(::parseResponsive)
        return (twoRow + rows).distinctBy { (it as? SongItem)?.id ?: (it as CollectionItem).browseId }
    }

    /** Follow / unfollow an artist (subscribes to their YouTube channel). */
    suspend fun subscribe(channelId: String, subscribe: Boolean) {
        web(if (subscribe) "subscription/subscribe" else "subscription/unsubscribe", buildJsonObject {
            put("channelIds", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(channelId))))
        })
    }

    // -------------------------------------------------------------- playlists

    /** The user's playlists that a song can be added to (YouTube Music's "Save to playlist"). */
    suspend fun addToPlaylistOptions(videoIds: List<String>): List<PlaylistOption> {
        val res = web("playlist/get_add_to_playlist", buildJsonObject {
            put("videoIds", kotlinx.serialization.json.JsonArray(videoIds.map { JsonPrimitive(it) }))
        })
        return collect(res, "playlistAddToOptionRenderer").mapNotNull { o ->
            val id = o.str("playlistId") ?: return@mapNotNull null
            PlaylistOption(
                playlistId = id,
                title = o.obj("title")?.runs()?.joinToString("") ?: return@mapNotNull null,
                thumbnailUrl = o.thumbUrl()?.let(::hiResThumbnail),
                containsSongs = o.str("containsSelectedVideos") == "ALL",
            )
        }
    }

    /** Creates a private playlist; returns its id. */
    suspend fun createPlaylist(title: String, videoIds: List<String>): String? {
        val res = web("playlist/create", buildJsonObject {
            put("title", title)
            put("description", "")
            put("privacyStatus", "PRIVATE")
            if (videoIds.isNotEmpty()) {
                put("videoIds", kotlinx.serialization.json.JsonArray(videoIds.map { JsonPrimitive(it) }))
            }
        })
        return res.str("playlistId")
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) {
        editPlaylist(playlistId, videoIds.map { id ->
            buildJsonObject {
                put("action", "ACTION_ADD_VIDEO")
                put("addedVideoId", id)
                put("dedupeOption", "DEDUPE_OPTION_SKIP")
            }
        })
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) {
        editPlaylist(playlistId, listOf(buildJsonObject {
            put("action", "ACTION_REMOVE_VIDEO")
            put("removedVideoId", videoId)
            put("setVideoId", setVideoId)
        }))
    }

    suspend fun renamePlaylist(playlistId: String, name: String) {
        editPlaylist(playlistId, listOf(buildJsonObject {
            put("action", "ACTION_SET_PLAYLIST_NAME")
            put("playlistName", name)
        }))
    }

    private suspend fun editPlaylist(playlistId: String, actions: List<JsonObject>) {
        web("browse/edit_playlist", buildJsonObject {
            put("playlistId", playlistId.removePrefix("VL"))
            put("actions", kotlinx.serialization.json.JsonArray(actions))
        })
    }

    suspend fun deletePlaylist(playlistId: String) {
        web("playlist/delete", buildJsonObject { put("playlistId", playlistId.removePrefix("VL")) })
    }

    /** Save (or remove) someone else's playlist or an album to/from the library. */
    suspend fun savePlaylist(playlistId: String, save: Boolean) {
        web(if (save) "like/like" else "like/removelike", buildJsonObject {
            putJsonObject("target") { put("playlistId", playlistId.removePrefix("VL")) }
        })
    }

    /** Next page of a playlist's songs. */
    suspend fun browseContinuation(token: String): Pair<List<SongItem>, String?> {
        val (raw, isCommand) = decodeToken(token)
        val res = webContinuation(raw, query = !isCommand)
        val songs = collect(res, "musicResponsiveListItemRenderer")
            .mapNotNull { parseResponsive(it) as? SongItem }
        return songs to continuationToken(res)
    }

    // ---------------------------------------------------------------- library

    /** Library grids: FEmusic_liked_playlists, FEmusic_liked_albums, FEmusic_library_corpus_track_artists. */
    suspend fun library(browseId: String): List<CollectionItem> {
        val res = web("browse", buildJsonObject { put("browseId", browseId) })
        val twoRow = collect(res, "musicTwoRowItemRenderer").mapNotNull { parseTwoRow(it) as? CollectionItem }
        val rows = collect(res, "musicResponsiveListItemRenderer").mapNotNull { parseResponsive(it) as? CollectionItem }
        return (twoRow + rows).distinctBy { it.browseId }
    }

    /**
     * Your YouTube Music listening history: what you played anywhere (phone, computer,
     * the official app, other LiquidTube installs), newest first.
     */
    suspend fun history(): List<SongItem> {
        val res = web("browse", buildJsonObject { put("browseId", "FEmusic_history") })
        return collect(res, "musicResponsiveListItemRenderer")
            .mapNotNull { parseResponsive(it) as? SongItem }
            .distinctBy { it.id }
    }

    /**
     * Records a play in your YouTube Music history, the way the official app does (its
     * "playback" statistics ping), so other devices see it under Recently Played.
     */
    suspend fun reportPlayback(videoId: String): Boolean {
        val sapisid = sapisid ?: return false
        val authCookie = cookie ?: return false
        val player = webMusicPlayer(videoId, null)
        val base = (player["playbackTracking"] as? JsonObject)
            ?.let { it["videostatsPlaybackUrl"] as? JsonObject }
            ?.let { (it["baseUrl"] as? JsonPrimitive)?.contentOrNull } ?: return false
        val cpnChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val cpn = (1..16).map { cpnChars.random() }.joinToString("")
        val response = Http.client.get(base) {
            parameter("ver", 2)
            parameter("c", "WEB_REMIX")
            parameter("cpn", cpn)
            header(HttpHeaders.UserAgent, YouTubeClient.WEB_REMIX.userAgent)
            header("Origin", ORIGIN)
            header("Referer", "$ORIGIN/")
            header(HttpHeaders.Cookie, authCookie)
            header(HttpHeaders.Authorization, sapisidHash(sapisid))
            header("X-Goog-AuthUser", "0")
        }
        return response.status.value in 200..299
    }

    suspend fun accountInfo(): AccountInfo? {
        val res = web("account/account_menu", JsonObject(emptyMap()))
        val header = findFirst(res, "activeAccountHeaderRenderer") ?: return null
        return AccountInfo(
            name = header.obj("accountName")?.runs()?.joinToString("") ?: return null,
            handle = header.obj("channelHandle")?.runs()?.joinToString(""),
            photoUrl = header.thumbUrl()?.let(::hiResThumbnail),
        )
    }

    suspend fun like(videoId: String, liked: Boolean) {
        web(if (liked) "like/like" else "like/removelike", buildJsonObject {
            putJsonObject("target") { put("videoId", videoId) }
        })
    }

    // ------------------------------------------------------------------ radio

    /** YouTube Music's "up next" radio for a song, the same as autoplay in the official app. */
    suspend fun radio(videoId: String): List<SongItem> {
        val res = web("next", buildJsonObject {
            put("videoId", videoId)
            put("playlistId", "RDAMVM$videoId")
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
            put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
        })
        return collect(res, "playlistPanelVideoRenderer")
            .mapNotNull(::parsePanelItem)
            .distinctBy { it.id }
    }

    /** Plain (unsynced) lyrics from YouTube Music's Lyrics tab. */
    suspend fun lyrics(videoId: String): Lyrics? {
        val next = web("next", buildJsonObject {
            put("videoId", videoId)
            put("isAudioOnly", true)
        })
        val lyricsId = collect(next, "tabRenderer")
            .mapNotNull { findFirst(it, "browseEndpoint")?.str("browseId") }
            .firstOrNull { it.startsWith("MPLY") } ?: return null
        val res = web("browse", buildJsonObject { put("browseId", lyricsId) })
        val shelf = findFirst(res, "musicDescriptionShelfRenderer") ?: return null
        val text = shelf.obj("description")?.runs()?.joinToString("")?.takeIf { it.isNotBlank() } ?: return null
        val footer = shelf.obj("footer")?.runs()?.joinToString("")
        return Lyrics(plain = text, source = footer ?: "YouTube Music")
    }

    // ------------------------------------------------------------ stream resolve

    private val PREMIUM_CLIENTS = listOf(YouTubeClient.WEB_REMIX, YouTubeClient.IOS_MUSIC, YouTubeClient.TVHTML5)

    /** The old premium probe (can't decipher); the app turns it off when its own premium module runs. */
    @Volatile var legacyPremiumProbe = true

    // ---- hooks for the app's premium module (app/.../premium) ----

    /** Signed-in WEB_REMIX player response (formats are signature-ciphered). */
    suspend fun webMusicPlayer(videoId: String, signatureTimestamp: Int?): JsonObject =
        post("$MUSIC_API/player", YouTubeClient.WEB_REMIX, buildJsonObject {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            if (signatureTimestamp != null) {
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") { put("signatureTimestamp", signatureTimestamp) }
                }
            }
        }, auth = true)

    /** The signed-in account's DataSync ID (the identifier PO tokens are bound to). */
    suspend fun dataSyncId(): String? {
        val res = web("account/account_menu", JsonObject(emptyMap()))
        return (findFirstAny(res, "datasyncId") as? JsonPrimitive)?.contentOrNull
            ?.substringBefore("||")?.takeIf { it.isNotBlank() }
    }

    suspend fun currentVisitorData(): String? = visitorData()
    @Volatile private var premiumRetryAt = 0L

    /**
     * Resolve a videoId to an audio-only stream. Signed in (and not in data saver),
     * it first tries the account's Premium formats; otherwise the anonymous clients.
     */
    suspend fun resolveStream(videoId: String, lowQuality: Boolean = false): StreamInfo? {
        if (legacyPremiumProbe && isLoggedIn && !lowQuality && System.currentTimeMillis() >= premiumRetryAt) {
            val premium = runCatching { resolvePremium(videoId) }
                .onFailure { if (it is CancellationException) throw it }
                .getOrNull()
            if (premium != null) return premium
            premiumRetryAt = System.currentTimeMillis() + 30 * 60_000L // avoid slowing every track
        }
        resolveOnce(videoId, lowQuality)?.let { return it }
        // Possibly a stale/flagged visitor id: get a fresh one and try once more.
        visitorData = null
        return resolveOnce(videoId, lowQuality)
    }

    /** Authenticated player requests; accepted only if the stream really downloads past 1MB. */
    private suspend fun resolvePremium(videoId: String): StreamInfo? {
        for (yt in PREMIUM_CLIENTS) {
            val url = if (yt.isWeb) "$MUSIC_API/player" else "$YT_API/player"
            val res = try {
                post(url, yt, buildJsonObject {
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }, auth = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger("premium probe ${yt.clientName}: request failed (${e.javaClass.simpleName})")
                continue
            }
            val status = res.obj("playabilityStatus")?.str("status")
            val formats = (res.obj("streamingData")?.get("adaptiveFormats") as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it.str("mimeType")?.startsWith("audio/") == true }
            val direct = formats.filter { it.str("url") != null }
            val best = direct.maxByOrNull { it.str("bitrate")?.toIntOrNull() ?: 0 }
            logger(
                "premium probe ${yt.clientName}: status=$status itags=${formats.map { it.str("itag") }} " +
                    "direct=${direct.size}/${formats.size} best=${(best?.str("bitrate")?.toIntOrNull() ?: 0) / 1000}kbps"
            )
            if (status != "OK" || best == null) continue
            val bitrate = best.str("bitrate")?.toIntOrNull() ?: 0
            if (bitrate < 200_000) continue // not better than anonymous

            val streamUrl = best.str("url")!!
            val ok = runCatching {
                Http.client.get(streamUrl) {
                    header(HttpHeaders.UserAgent, yt.userAgent)
                    header(HttpHeaders.Range, "bytes=1500000-1500001")
                }.status.value
            }.getOrNull()
            logger("premium probe ${yt.clientName}: mid-stream check http=$ok")
            if (ok != 206 && ok != 200) continue

            val expiresIn = res.obj("streamingData")?.str("expiresInSeconds")?.toLongOrNull() ?: 21_540L
            return StreamInfo(
                url = streamUrl,
                mimeType = best.str("mimeType") ?: "audio/mp4",
                bitrate = bitrate,
                userAgent = yt.userAgent,
                clientName = "${yt.clientName} (Premium)",
                expiresAtMs = System.currentTimeMillis() + (expiresIn - 120).coerceAtLeast(60) * 1000,
            )
        }
        return null
    }

    private suspend fun resolveOnce(videoId: String, lowQuality: Boolean): StreamInfo? {
        for (yt in PLAYER_CLIENTS) {
            val res = try {
                post("$YT_API/player", yt, buildJsonObject {
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }

            if (res.obj("playabilityStatus")?.str("status") != "OK") continue
            val streaming = res.obj("streamingData") ?: continue
            val expiresIn = streaming.str("expiresInSeconds")?.toLongOrNull() ?: 21_540L

            val audio = (streaming["adaptiveFormats"] as? JsonArray).orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it.str("mimeType")?.startsWith("audio/") == true && it.str("url") != null }
            val best = (
                if (lowQuality) audio.minByOrNull { it.str("bitrate")?.toIntOrNull() ?: 0 }
                else audio.maxByOrNull { it.str("bitrate")?.toIntOrNull() ?: 0 }
                ) ?: continue

            return StreamInfo(
                url = best.str("url")!!,
                mimeType = best.str("mimeType") ?: "audio/mp4",
                bitrate = best.str("bitrate")?.toIntOrNull() ?: 0,
                userAgent = yt.userAgent,
                clientName = yt.clientName,
                expiresAtMs = System.currentTimeMillis() + (expiresIn - 120).coerceAtLeast(60) * 1000,
                loudnessDb = loudnessOf(res, best),
            )
        }
        return null
    }

    /** Track loudness (dB) from the format, or the player's audio config. */
    fun loudnessOf(res: JsonObject, format: JsonObject): Float? =
        format.str("loudnessDb")?.toFloatOrNull()
            ?: (findFirst(res, "audioConfig")?.let { it.str("loudnessDb") ?: it.str("perceptualLoudnessDb") })?.toFloatOrNull()

    // --------------------------------------------------------- request helpers

    private suspend fun web(endpoint: String, body: JsonObject): JsonObject =
        post("$MUSIC_API/$endpoint", YouTubeClient.WEB_REMIX, body)

    private suspend fun webContinuation(token: String, query: Boolean): JsonObject =
        if (query) {
            post("$MUSIC_API/browse", YouTubeClient.WEB_REMIX, JsonObject(emptyMap()),
                query = mapOf("ctoken" to token, "continuation" to token, "type" to "next"))
        } else {
            web("browse", buildJsonObject { put("continuation", token) })
        }

    private suspend fun post(
        url: String,
        yt: YouTubeClient,
        body: JsonObject,
        includeVisitor: Boolean = true,
        query: Map<String, String> = emptyMap(),
        auth: Boolean = yt.isWeb,
    ): JsonObject {
        val visitor = if (includeVisitor) visitorData() else null
        val authCookie = cookie.takeIf { auth }
        val sapisid = sapisid.takeIf { auth }
        return Http.client.post(url) {
            parameter("prettyPrint", false)
            query.forEach { (k, v) -> parameter(k, v) }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, yt.userAgent)
            header("X-YouTube-Client-Name", yt.clientId)
            header("X-YouTube-Client-Version", yt.clientVersion)
            visitor?.let { header("X-Goog-Visitor-Id", it) }
            if (yt.isWeb) {
                header("Origin", ORIGIN)
                header("Referer", "$ORIGIN/")
                header("X-Origin", ORIGIN)
            }
            if (authCookie != null && sapisid != null) {
                header(HttpHeaders.Cookie, authCookie)
                header(HttpHeaders.Authorization, sapisidHash(sapisid))
                header("X-Goog-AuthUser", "0")
            }
            setBody(buildJsonObject {
                put("context", yt.context(hl, gl, visitor))
                body.forEach { (k, v) -> put(k, v) }
            })
        }.body()
    }

    private fun sapisidHash(sapisid: String): String {
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$ts $sapisid $ORIGIN".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$digest"
    }

    private fun parseSapisid(cookie: String): String? {
        val map = cookie.split(";").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i).trim() to it.substring(i + 1).trim()
        }.toMap()
        return map["SAPISID"] ?: map["__Secure-3PAPISID"]
    }

    // --------------------------------------------------------------- parsing

    private const val UNKNOWN_ARTIST = "Unknown Artist"

    private val HEADER_KEYS = listOf(
        "musicResponsiveHeaderRenderer",
        "musicImmersiveHeaderRenderer",
        "musicVisualHeaderRenderer",
        "musicDetailHeaderRenderer",
        "musicEditablePlaylistDetailHeaderRenderer",
    )

    private fun parseCarousels(res: JsonObject): List<FeedShelf> =
        collect(res, "musicCarouselShelfRenderer").mapNotNull { shelf ->
            val title = findFirst(shelf.obj("header") ?: return@mapNotNull null, "title")
                ?.runs()?.joinToString("")
                ?: return@mapNotNull null
            val items = (shelf["contents"] as? JsonArray).orEmpty().mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                o.obj("musicTwoRowItemRenderer")?.let(::parseTwoRow)
                    ?: o.obj("musicResponsiveListItemRenderer")?.let(::parseResponsive)
            }
            // The same release can come twice under two ids (e.g. a single and its
            // soundtrack edition): one card per title + details.
            fun key(it: Any) = (it as? SongItem)?.id
                ?: (it as CollectionItem).let { c -> "${c.type}|${c.title.lowercase()}|${c.subtitle?.lowercase()}" }
            val unique = items.distinctBy { (it as? SongItem)?.id ?: (it as CollectionItem).browseId }.distinctBy(::key)
            val songs = unique.filterIsInstance<SongItem>()
            val collections = unique.filterIsInstance<CollectionItem>()
            if (songs.isEmpty() && collections.isEmpty()) null
            else FeedShelf(
                title, songs, collections,
                items = unique,
                more = shelf.obj("header")?.let { findFirst(it, "browseEndpoint") }?.let(::sectionRef),
            )
        }

    /** musicTwoRowItemRenderer → song (has watchEndpoint) or collection (has browseEndpoint). */
    private fun parseTwoRow(r: JsonObject): Any? {
        val title = r.obj("title")?.runs()?.joinToString("") ?: return null
        val subtitleRuns = r.obj("subtitle")?.runs().orEmpty()
        val thumb = r.thumbUrl() ?: findFirstAny(r, "thumbnails")?.let { it as? JsonArray }
            ?.lastOrNull()?.let { (it as? JsonObject)?.str("url") }
        val nav = r.obj("navigationEndpoint")

        nav?.obj("watchEndpoint")?.str("videoId")?.let { videoId ->
            val groups = splitBullets(subtitleRuns)
            return buildSong(videoId, title, groups, thumb, artistId = r.obj("subtitle")?.let(::artistIdIn))
        }
        val browse = nav?.obj("browseEndpoint") ?: return null
        val browseId = browse.str("browseId") ?: return null
        val type = pageType(findFirstAny(browse, "pageType")?.let { (it as? JsonPrimitive)?.contentOrNull }) ?: return null
        return CollectionItem(
            browseId = normalizeBrowseId(browseId),
            title = title,
            subtitle = splitBullets(subtitleRuns).joinToString(" · ").ifBlank { null },
            thumbnailUrl = thumb?.let(::hiResThumbnail),
            type = type,
        )
    }

    /** musicResponsiveListItemRenderer → song (has videoId) or collection (has browseEndpoint). */
    private fun parseResponsive(r: JsonObject): Any? {
        val cols = (r["flexColumns"] as? JsonArray).orEmpty().mapNotNull {
            (it as? JsonObject)?.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")
        }
        val title = cols.getOrNull(0)?.runs()?.joinToString("")?.takeIf { it.isNotBlank() } ?: return null
        val thumb = collect(r, "musicThumbnailRenderer").firstOrNull()?.thumbUrl()

        // A row that opens a page (album, playlist, artist) is that page, even when its
        // play-button overlay carries the first track's videoId (album search results do).
        val page = r.obj("navigationEndpoint")?.obj("browseEndpoint")
        val pageKind = page?.let { findFirstAny(it, "pageType")?.let { t -> (t as? JsonPrimitive)?.contentOrNull } }?.let(::pageType)
        if (page != null && pageKind != null && r.obj("playlistItemData") == null) {
            val browseId = page.str("browseId")
            if (browseId != null) {
                val sub = splitBullets(cols.getOrNull(1)?.runs().orEmpty()).let {
                    if (it.firstOrNull() in TYPE_PREFIXES) it.drop(1) else it
                }
                return CollectionItem(
                    browseId = normalizeBrowseId(browseId),
                    title = title,
                    subtitle = sub.joinToString(" · ").ifBlank { null },
                    thumbnailUrl = thumb?.let(::hiResThumbnail),
                    type = pageKind,
                )
            }
        }

        val videoId = r.obj("playlistItemData")?.str("videoId")
            ?: r.obj("overlay")?.let { findFirst(it, "watchEndpoint") }?.str("videoId")
            ?: cols.getOrNull(0)?.let { findFirst(it, "watchEndpoint") }?.str("videoId")

        if (videoId != null) {
            val col1 = cols.getOrNull(1)?.runs().orEmpty()
            val groups = splitBullets(col1)
            if (groups.firstOrNull() in setOf("Episode", "Podcast")) return null
            val col2 = cols.getOrNull(2)?.runs()?.joinToString("")?.trim()
            val fixed = (r["fixedColumns"] as? JsonArray)?.firstOrNull()
                ?.let { (it as? JsonObject)?.obj("musicResponsiveListItemFixedColumnRenderer")?.obj("text") }
                ?.runs()?.joinToString("")?.trim()
            val extra = buildList {
                addAll(groups)
                // Playlist rows: [title, artist, album] with no bullets in col1.
                if (groups.size <= 1 && !col2.isNullOrBlank() && !isCountText(col2)) add(col2)
                if (fixed != null && DURATION.matches(fixed) && groups.none { DURATION.matches(it) }) add(fixed)
            }
            return buildSong(
                videoId, title, extra, thumb,
                artistId = cols.getOrNull(1)?.let(::artistIdIn),
                albumId = cols.drop(1).firstNotNullOfOrNull(::albumIdIn),
            ).copy(setVideoId = r.obj("playlistItemData")?.str("playlistSetVideoId"))
        }

        val nav = r.obj("navigationEndpoint")?.obj("browseEndpoint") ?: return null
        val browseId = nav.str("browseId") ?: return null
        val type = pageType(findFirstAny(nav, "pageType")?.let { (it as? JsonPrimitive)?.contentOrNull }) ?: return null
        val sub = splitBullets(cols.getOrNull(1)?.runs().orEmpty()).let {
            if (it.firstOrNull() in TYPE_PREFIXES) it.drop(1) else it
        }
        return CollectionItem(
            browseId = normalizeBrowseId(browseId),
            title = title,
            subtitle = sub.joinToString(" · ").ifBlank { null },
            thumbnailUrl = thumb?.let(::hiResThumbnail),
            type = type,
        )
    }

    private fun parsePanelItem(r: JsonObject): SongItem? {
        val videoId = r.str("videoId") ?: return null
        val title = r.obj("title")?.runs()?.firstOrNull() ?: return null
        val byline = r.obj("longBylineText")
        val groups = splitBullets(byline?.runs().orEmpty()).filterNot { YEAR.matches(it) }
        val duration = r.obj("lengthText")?.runs()?.firstOrNull()
        return buildSong(
            videoId, title, groups + listOfNotNull(duration), r.thumbUrl(),
            artistId = byline?.let(::artistIdIn), albumId = byline?.let(::albumIdIn),
        )
    }

    private fun buildSong(
        id: String,
        title: String,
        groupsIn: List<String>,
        thumb: String?,
        artistId: String? = null,
        albumId: String? = null,
    ): SongItem {
        var groups = groupsIn
        if (groups.firstOrNull() in TYPE_PREFIXES) groups = groups.drop(1)
        val duration = groups.lastOrNull { DURATION.matches(it) }
        val meta = groups.filterNot { it == duration || isCountText(it) || YEAR.matches(it) }
        return SongItem(
            id = id,
            title = title,
            artists = meta.firstOrNull() ?: UNKNOWN_ARTIST,
            album = meta.getOrNull(1),
            durationText = duration,
            thumbnailUrl = thumb?.let(::hiResThumbnail),
            artistId = artistId,
            albumId = albumId,
        )
    }

    private fun isCountText(s: String) =
        s.endsWith(" views") || s.endsWith(" plays") || s.endsWith(" songs") || s.endsWith(" listeners") ||
            s.endsWith(" monthly audience") || s.endsWith(" subscribers")

    private fun pageType(type: String?): CollectionType? = when (type) {
        "MUSIC_PAGE_TYPE_ALBUM", "MUSIC_PAGE_TYPE_AUDIOBOOK" -> CollectionType.ALBUM
        "MUSIC_PAGE_TYPE_PLAYLIST" -> CollectionType.PLAYLIST
        "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_LIBRARY_ARTIST" -> CollectionType.ARTIST
        else -> null
    }

    fun typeForBrowseId(id: String): CollectionType = when {
        id.startsWith("MPRE") -> CollectionType.ALBUM
        id.startsWith("UC") || id.startsWith("MPLA") -> CollectionType.ARTIST
        else -> CollectionType.PLAYLIST
    }

    /** Library-artist pages (MPLAUC…) only list your songs; show the real artist page instead. */
    private fun normalizeBrowseId(id: String) =
        if (id.startsWith("MPLAUC")) id.removePrefix("MPLA") else id

    private fun artistIdIn(text: JsonObject): String? = navIdOfType(text, "MUSIC_PAGE_TYPE_ARTIST")
    private fun albumIdIn(text: JsonObject): String? = navIdOfType(text, "MUSIC_PAGE_TYPE_ALBUM")

    private fun navIdOfType(text: JsonObject, type: String): String? =
        (text["runs"] as? JsonArray).orEmpty().firstNotNullOfOrNull { run ->
            val browse = (run as? JsonObject)?.obj("navigationEndpoint")?.obj("browseEndpoint") ?: return@firstNotNullOfOrNull null
            val pt = findFirstAny(browse, "pageType")?.let { (it as? JsonPrimitive)?.contentOrNull }
            browse.str("browseId")?.takeIf { pt == type }
        }

    private fun firstNav(text: JsonObject): Pair<String, String?>? =
        (text["runs"] as? JsonArray).orEmpty().firstNotNullOfOrNull { run ->
            val browse = (run as? JsonObject)?.obj("navigationEndpoint")?.obj("browseEndpoint") ?: return@firstNotNullOfOrNull null
            val id = browse.str("browseId") ?: return@firstNotNullOfOrNull null
            id to findFirstAny(browse, "pageType")?.let { (it as? JsonPrimitive)?.contentOrNull }
        }

    private fun continuationToken(e: JsonElement): String? {
        findFirst(e, "continuationCommand")?.str("token")?.let { return "c:$it" }
        findFirst(e, "nextContinuationData")?.str("continuation")?.let { return "n:$it" }
        return null
    }

    private fun decodeToken(token: String): Pair<String, Boolean> = when {
        token.startsWith("c:") -> token.drop(2) to true
        token.startsWith("n:") -> token.drop(2) to false
        else -> token to false
    }

    private fun splitBullets(runs: List<String>): List<String> {
        val groups = mutableListOf(StringBuilder())
        runs.forEach { if (it.trim() == "•") groups += StringBuilder() else groups.last().append(it) }
        return groups.map { it.toString().trim() }.filter { it.isNotEmpty() }
    }

    private fun collect(element: JsonElement, key: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun recurse(e: JsonElement) {
            when (e) {
                is JsonObject -> {
                    (e[key] as? JsonObject)?.let(out::add)
                    e.values.forEach(::recurse)
                }
                is JsonArray -> e.forEach(::recurse)
                else -> Unit
            }
        }
        recurse(element)
        return out
    }

    /** First value (of any JSON kind) stored under [key], depth-first. */
    private fun findFirstAny(element: JsonElement, key: String): JsonElement? {
        when (element) {
            is JsonObject -> {
                element[key]?.let { return it }
                for (v in element.values) findFirstAny(v, key)?.let { return it }
            }
            is JsonArray -> for (v in element) findFirstAny(v, key)?.let { return it }
            else -> Unit
        }
        return null
    }

    /** Every string value stored under [key], depth-first. */
    private fun allStrings(element: JsonElement, key: String): List<String> {
        val out = mutableListOf<String>()
        fun recurse(e: JsonElement) {
            when (e) {
                is JsonObject -> {
                    (e[key] as? JsonPrimitive)?.contentOrNull?.let(out::add)
                    e.values.forEach(::recurse)
                }
                is JsonArray -> e.forEach(::recurse)
                else -> Unit
            }
        }
        recurse(element)
        return out
    }

    /** First object stored under [key], depth-first. */
    private fun findFirst(element: JsonElement, key: String): JsonObject? = findFirstAny(element, key) as? JsonObject

    private fun JsonObject.thumbUrl(): String? =
        (findFirstAny(this, "thumbnails") as? JsonArray)
            ?.lastOrNull()?.let { it as? JsonObject }?.str("url")

    private fun JsonObject.runs(): List<String> =
        (this["runs"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("text") }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}

/**
 * YouTube returns tiny (60–120px) thumbnails in lists. googleusercontent URLs take
 * a size suffix, so ask for a large square instead; this is what makes artwork crisp.
 */
fun hiResThumbnail(url: String, size: Int = 544): String = when {
    "googleusercontent.com" in url || "ggpht.com" in url ->
        url.replace(Regex("""=w\d+-h\d+"""), "=w$size-h$size")
            .replace(Regex("""=s\d+"""), "=s$size")
    "i.ytimg.com" in url ->
        url.replace(Regex("""/(default|mqdefault|sddefault)\.jpg"""), "/hqdefault.jpg")
    else -> url
}
