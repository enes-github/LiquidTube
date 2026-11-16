package com.enes.liquidtube.playback.cast

import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.URL

/** The phone-side server a Cast speaker plays from: whole files, seeking, and locked to its token. */
class CastServerTest {
    // An "m4a": 'ftyp' at byte 4, then 300 KB of numbered bytes.
    private val song = ByteArray(300_000) { (it % 251).toByte() }.also {
        "\u0000\u0000\u0000 ftypM4A ".toByteArray(Charsets.ISO_8859_1).copyInto(it)
    }
    private val server = CastServer(DataSource.Factory { ByteArrayDataSource(song) })
    private lateinit var url: String

    @Before fun start() {
        server.start()
        val u = server.urlFor("dQw4w9WgXcQ")
        assumeTrue("no LAN address on this machine", u != null)
        url = u!!
    }

    @After fun stop() = server.stop()

    /** A bare HTTP client (the app itself isn't allowed plain-HTTP requests; speakers are). */
    private fun get(u: String, range: String? = null, method: String = "GET"): Triple<Int, Map<String, String>, ByteArray> {
        val url = URL(u)
        java.net.Socket(url.host, url.port).use { sock ->
            val nl = String(charArrayOf(13.toChar(), 10.toChar()))
            val req = "$method ${url.path} HTTP/1.1${nl}Host: ${url.host}$nl" + (range?.let { "Range: $it$nl" } ?: "") + nl
            sock.getOutputStream().write(req.toByteArray())
            val all = sock.getInputStream().readBytes()
            val split = (0 until all.size - 3).first { all[it] == 13.toByte() && all[it + 1] == 10.toByte() && all[it + 2] == 13.toByte() && all[it + 3] == 10.toByte() }
            val head = String(all, 0, split, Charsets.ISO_8859_1).split(nl)
            val code = head[0].split(' ')[1].toInt()
            val headers = head.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            return Triple(code, headers, all.copyOfRange(split + 4, all.size))
        }
    }

    @Test fun wholeSong() {
        val (code, h, body) = get(url)
        assertEquals(200, code)
        assertEquals("audio/mp4", h["content-type"])
        assertEquals("bytes", h["accept-ranges"])
        assertEquals(song.size.toString(), h["content-length"])
        assertArrayEquals(song, body)
    }

    @Test fun seeking() {
        val (code, h, body) = get(url, "bytes=100000-")
        assertEquals(206, code)
        assertEquals("bytes 100000-299999/300000", h["content-range"])
        assertArrayEquals(song.copyOfRange(100_000, 300_000), body)

        val (code2, h2, body2) = get(url, "bytes=5-14")
        assertEquals(206, code2)
        assertEquals("bytes 5-14/300000", h2["content-range"])
        assertArrayEquals(song.copyOfRange(5, 15), body2)
    }

    @Test fun headOnly() {
        val (code, h, _) = get(url, method = "HEAD")
        assertEquals(200, code)
        assertEquals(song.size.toString(), h["content-length"])
    }

    @Test fun lockedToToken() {
        val wrong = url.replaceAfterLast(':', url.substringAfterLast(':').substringBefore('/') + "/nottoken/dQw4w9WgXcQ")
        assertEquals(404, get(wrong).first)
        assertEquals(404, get(url.substringBeforeLast('/') + "/../../etc").first)
    }

    @Test fun pastTheEnd() {
        assertEquals(416, get(url, "bytes=300000-").first)
    }
}
