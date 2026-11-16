package com.enes.innertube

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live checks against YouTube Music (run daily by .github/workflows/health.yml).
 * YouTube changes its internal API without notice; these catch it before users do.
 * Signed out, like a fresh install.
 */
class HealthCheckTest {

    @Test
    fun search() = runBlocking {
        val results = InnerTube.search("Adele Hello")
        assertTrue("search returned no songs", results.songs.isNotEmpty())
        assertTrue("search returned no albums or artists", results.albums.isNotEmpty() || results.artists.isNotEmpty())
    }

    @Test
    fun songSearch() = runBlocking {
        val songs = InnerTube.searchSongs("Daft Punk One More Time")
        assertTrue("song search returned nothing", songs.isNotEmpty())
        assertTrue("songs are missing titles", songs.all { it.title.isNotBlank() && it.id.isNotBlank() })
    }

    @Test
    fun homeFeed() = runBlocking {
        val feed = InnerTube.homeFeed(null, maxPages = 1)
        assertTrue("home feed has no shelves", feed.shelves.isNotEmpty())
    }

    @Test
    fun album() = runBlocking {
        val album = InnerTube.search("Adele 25").albums.firstOrNull()
        assertNotNull("no album found to open", album)
        val page = InnerTube.browse(album!!.browseId)
        assertTrue("album page has no songs", page.songs.isNotEmpty())
    }

    @Test
    fun radio() = runBlocking {
        val seed = InnerTube.searchSongs("Adele Hello").first()
        assertTrue("radio returned nothing", InnerTube.radio(seed.id).size > 3)
    }

    @Test
    fun stream() = runBlocking {
        // YouTube refuses to play songs to data-centre computers (GitHub's included), so
        // on CI this would always fail. Run it locally; on phones the app's own startup
        // self-test watches playback and warns on Home.
        assumeFalse("playback can't be tested from a data centre", System.getenv("CI") == "true")
        val seed = InnerTube.searchSongs("Adele Hello").first()
        val stream = InnerTube.resolveStream(seed.id)
        assertNotNull("no playable stream (playback is broken)", stream)
        // The audio itself must download, not just the link.
        val conn = URL(stream!!.url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Range", "bytes=0-65535")
        conn.setRequestProperty("User-Agent", stream.userAgent)
        val code = conn.responseCode
        conn.disconnect()
        assertTrue("stream URL answered HTTP $code", code == 200 || code == 206)
    }

    @Test
    fun lyrics() = runBlocking {
        val lyrics = LrcLib.lyrics("Hello", "Adele", 295)
        assertNotNull("LRCLIB returned no lyrics", lyrics)
    }
}
