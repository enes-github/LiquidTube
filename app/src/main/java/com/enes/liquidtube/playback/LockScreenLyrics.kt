package com.enes.liquidtube.playback

import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Lyrics on the lock screen and in the media notification: the line being sung shows
 * where the artist usually is (under the song title), and moves on with the song.
 * [PlayerConnection] works out the line; the playback service shows it.
 */
object LockScreenLyrics {
    @Volatile internal var player: LyricsMetadataPlayer? = null
    private val main = Handler(Looper.getMainLooper())
    private var last: String? = null

    /** [line] = the lyric being sung, or null to show the artist as usual. */
    fun publish(line: String?) {
        if (line == last) return
        last = line
        main.post { player?.setLyric(line) }
    }
}

/**
 * The player the media session sees: the real one, except that its "now playing"
 * details carry the current lyric line instead of the artist while lyrics are on.
 * Only the session's view changes: the songs and the queue are untouched.
 */
@OptIn(UnstableApi::class)
class LyricsMetadataPlayer(player: Player) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()
    private var lyric: String? = null

    init {
        // A new song: its own details first, until its first line comes.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                lyric = null
            }
        })
    }

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val line = lyric ?: return base
        return base.buildUpon()
            .setArtist(line)
            .setSubtitle(line)
            .build()
    }

    fun setLyric(line: String?) {
        if (line == lyric) return
        lyric = line
        val metadata = mediaMetadata
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
        listeners.forEach {
            it.onMediaMetadataChanged(metadata)
            it.onEvents(this, events)
        }
    }
}
