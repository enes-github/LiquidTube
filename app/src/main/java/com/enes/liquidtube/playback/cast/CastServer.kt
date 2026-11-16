package com.enes.liquidtube.playback.cast

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.enes.liquidtube.playback.StreamResolver
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * A small web server on the phone that a Cast speaker/TV plays songs from.
 *
 * The speaker never talks to YouTube: it asks the phone for `/<token>/<videoId>` and the
 * phone reads the song through the same chain it plays from itself (downloads, then the
 * listening cache, then YouTube with the phone's own Premium stream). So downloads work
 * while casting, the cache fills, and YouTube only ever sees the phone.
 *
 * Only reachable on the local network, and only with the random token in the link.
 */
@OptIn(UnstableApi::class)
class CastServer(private val dataSources: DataSource.Factory) {
    private val token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "cast-server").apply { isDaemon = true } }
    private var server: ServerSocket? = null

    val isRunning get() = server?.isClosed == false

    fun start() {
        if (isRunning) return
        val socket = ServerSocket(0)
        server = socket
        pool.execute {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                pool.execute { handle(client) }
            }
        }
        Log.i(TAG, "Cast server on port ${socket.localPort}")
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    /** The link the speaker plays [videoId] from, or null without a Wi-Fi address. */
    fun urlFor(videoId: String): String? {
        val port = server?.localPort ?: return null
        val host = lanAddress() ?: return null
        return "http://$host:$port/$token/$videoId"
    }

    private fun handle(client: Socket) = client.use { socket ->
        runCatching {
            socket.soTimeout = 30_000
            val input = BufferedInputStream(socket.getInputStream())
            val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
            val requestLine = readLine(input) ?: return@use
            val headers = generateSequence { readLine(input)?.takeIf { it.isNotEmpty() } }
                .mapNotNull { line -> line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim().lowercase() to line.substring(it + 1).trim() } }
                .toMap()
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?').trim('/').split('/')
            if (method == "OPTIONS") return@use status(out, 204, "No Content")
            if ((method != "GET" && method != "HEAD") || path.size != 2 || path[0] != token || !VIDEO_ID.matches(path[1])) {
                return@use status(out, 404, "Not Found")
            }
            serve(out, path[1], headers["range"], head = method == "HEAD")
        }.onFailure { if (it !is java.net.SocketException) Log.w(TAG, "Cast server: ${it.message}") }
    }

    private fun serve(out: OutputStream, id: String, range: String?, head: Boolean) {
        val match = range?.let { RANGE.find(it) }
        val start = match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val askedEnd = match?.groupValues?.get(2)?.toLongOrNull()

        val source = dataSources.createDataSource()
        try {
            val spec = DataSpec.Builder()
                .setUri(StreamResolver.placeholderUri(id))
                .setKey(id)
                .setPosition(start)
                .build()
            val remaining = source.open(spec)
            val total = if (remaining == C.LENGTH_UNSET.toLong()) null else start + remaining
            if (total != null && start >= total) return status(out, 416, "Range Not Satisfiable", "Content-Range: bytes */$total\r\n")

            // Look at the first bytes to tell the speaker what kind of audio this is.
            val first = ByteArray(16 * 1024)
            val firstRead = readFully(source, first)
            val type = when {
                start > 0 -> null
                firstRead >= 8 && String(first, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> "audio/mp4"
                firstRead >= 4 && first[0] == 0x1A.toByte() && first[1] == 0x45.toByte() -> "audio/webm"
                else -> null
            } ?: guessedType[id] ?: "audio/mp4"
            if (start == 0L) guessedType[id] = type

            val end = when {
                total == null -> null
                askedEnd != null -> minOf(askedEnd, total - 1)
                else -> total - 1
            }
            val length = end?.let { it - start + 1 }
            val partial = range != null && total != null
            val header = buildString {
                append(if (partial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Type: $type\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Access-Control-Allow-Origin: *\r\n")
                append("Cache-Control: no-store\r\n")
                if (length != null) append("Content-Length: $length\r\n")
                if (partial) append("Content-Range: bytes $start-$end/$total\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(header.toByteArray(Charsets.ISO_8859_1))
            if (head) return out.flush()

            var left = length ?: Long.MAX_VALUE
            val firstChunk = minOf(firstRead.toLong(), left).toInt()
            out.write(first, 0, firstChunk)
            left -= firstChunk
            val buffer = ByteArray(64 * 1024)
            while (left > 0) {
                val n = source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n == C.RESULT_END_OF_INPUT) break
                out.write(buffer, 0, n)
                left -= n
            }
            out.flush()
        } catch (e: java.io.IOException) {
            if (e is java.net.SocketException) throw e
            Log.w(TAG, "Cast server: couldn't read $id: ${e.message}")
            runCatching { status(out, 502, "Bad Gateway") }
        } finally {
            runCatching { source.close() }
        }
    }

    private fun readFully(source: DataSource, into: ByteArray): Int {
        var read = 0
        while (read < into.size) {
            val n = source.read(into, read, into.size - read)
            if (n == C.RESULT_END_OF_INPUT) break
            read += n
        }
        return read
    }

    private fun status(out: OutputStream, code: Int, text: String, extra: String = "") {
        out.write(
            ("HTTP/1.1 $code $text\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: Range\r\n" +
                "${extra}Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1),
        )
        out.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > 8192) return null
            sb.append(b.toChar())
        }
    }

    private val guessedType = java.util.concurrent.ConcurrentHashMap<String, String>()

    companion object {
        private const val TAG = "LiquidTube"
        private val VIDEO_ID = Regex("""[A-Za-z0-9_-]{6,20}""")
        private val RANGE = Regex("""bytes=(\d+)-(\d*)""")

        /** The phone's address on the Wi-Fi (or Ethernet / hotspot) network. */
        fun lanAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else if (it.name.startsWith("eth")) 1 else 2 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
