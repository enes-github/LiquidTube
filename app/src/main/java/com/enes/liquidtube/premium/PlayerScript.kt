package com.enes.liquidtube.premium

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube's own web player code, run inside [JsHost], used to decipher stream URLs.
 *
 * The player is heavily obfuscated and renamed with every release, so instead of
 * re-implementing its cipher we load the real file with one tiny hook added inside its
 * closure, find its stream-URL builder by shape (it's the function that sets "alr=yes"
 * and writes the deciphered signature under the "sp" key), and call it directly.
 */
@Singleton
class PlayerScript @Inject constructor(
    @ApplicationContext private val context: Context,
    private val js: JsHost,
) {
    private val lock = Mutex()
    private var loadedId: String? = null
    private var checkedAt = 0L

    /** The player's signatureTimestamp; YouTube needs it to hand out matching ciphers. */
    var signatureTimestamp: Int? = null
        private set

    /** Makes sure the current player is loaded and hooked. Re-checks the version every 6 hours. */
    suspend fun ensure() = lock.withLock {
        val now = System.currentTimeMillis()
        if (loadedId != null && now - checkedAt < 6 * 3600_000L) return@withLock
        val id = withContext(Dispatchers.IO) { currentPlayerId() } ?: loadedId ?: error("player id not found")
        checkedAt = now
        if (id == loadedId) return@withLock

        val source = withContext(Dispatchers.IO) { download(id) }
        signatureTimestamp = Regex("""signatureTimestamp:(\d+)""").find(source)?.groupValues?.get(1)?.toIntOrNull()
        val builder = URL_BUILDER.find(source)?.groupValues?.get(1)
            ?: error("URL builder not found in player $id")

        // The hook: an eval with access to everything inside the player's closure.
        val end = source.lastIndexOf("})(_yt_player);")
        require(end > 0) { "unexpected player wrapper" }
        val patched = source.substring(0, end) + ";window.__ytEval=function(x){return eval(x)};" + source.substring(end)

        js.reset() // a clean page per player version
        js.serve(SCRIPT_PATH, patched)
        js.loadScript(SCRIPT_PATH)
        js.eval(
            """(() => {
                 const build = window.__ytEval(${JSONObject.quote(builder)});
                 let method = null;
                 window.__ytDecipher = (url, sp, s) => {
                   const o = build(url, sp, s);
                   if (method && typeof o[method] === 'function') return o[method]();
                   // The serialising method is renamed every release: find it by what it returns.
                   const names = new Set();
                   for (let p = o; p && p !== Object.prototype; p = Object.getPrototypeOf(p)) Object.getOwnPropertyNames(p).forEach(n => names.add(n));
                   for (const n of names) {
                     if (n === 'constructor' || typeof o[n] !== 'function' || o[n].length > 0) continue;
                     try { const v = o[n](); if (typeof v === 'string' && v.startsWith('http') && v.includes('videoplayback')) { method = n; return v; } } catch (e) {}
                   }
                   throw new Error('could not serialise URL');
                 };
                 return 'ok';
               })()""",
        )
        loadedId = id
        Log.i(JsHost.TAG, "Player $id loaded (sts=$signatureTimestamp, builder=$builder)")
    }

    /** Turns a format's signatureCipher into a playable URL (signature deciphered). */
    suspend fun decipher(signatureCipher: String): String {
        ensure()
        val q = signatureCipher.split("&").associate {
            val k = it.substringBefore("=")
            k to java.net.URLDecoder.decode(it.substringAfter("=", ""), "UTF-8")
        }
        val url = q["url"] ?: error("cipher without url")
        val sp = q["sp"] ?: "signature"
        val s = q["s"] ?: return url
        return js.eval("window.__ytDecipher(${JSONObject.quote(url)}, ${JSONObject.quote(sp)}, ${JSONObject.quote(s)})")
    }

    /** Forget the loaded player (e.g. after YouTube rejected our URLs). */
    fun invalidate() {
        loadedId = null
    }

    private fun currentPlayerId(): String? {
        val html = get("https://music.youtube.com/") ?: return null
        return Regex("""/s/player/([0-9a-fA-F]{8})/""").find(html)?.groupValues?.get(1)
    }

    private fun download(id: String): String {
        val cache = File(context.cacheDir, "yt_player_$id.js")
        if (cache.exists() && cache.length() > 100_000) return cache.readText()
        // The "ias" build keeps the classic structure our hook relies on.
        val text = get("https://www.youtube.com/s/player/$id/player_ias.vflset/en_US/base.js")
            ?: error("player download failed")
        context.cacheDir.listFiles { f -> f.name.startsWith("yt_player_") }?.forEach { it.delete() }
        cache.writeText(text)
        return text
    }

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", JsHost.UA)
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private companion object {
        const val SCRIPT_PATH = "/__liquidtube/player.js"
        // ko=function(a,b,c){b=b===void 0?"":b;c=c===void 0?"":c;a=new g.io(a,!0);a.set("alr","yes");...
        val URL_BUILDER = Regex(
            """([\w$]+)=function\(a,b,c\)\{b=b===void 0\?"":b;c=c===void 0\?"":c;a=new g\.[\w$]+\(a,!0\);a\.set\("alr","yes"\)""",
        )
    }
}
