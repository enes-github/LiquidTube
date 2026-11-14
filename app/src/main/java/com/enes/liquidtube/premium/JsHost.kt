package com.enes.liquidtube.premium

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A hidden WebView acting as a small JavaScript engine with a real browser behind it,
 * on the https://www.youtube.com origin. YouTube's player code (to decipher signatures)
 * and Google's BotGuard (to mint PO tokens) both need a genuine browser environment,
 * which is exactly what this provides. Never shown on screen.
 */
@Singleton
class JsHost @Inject constructor(@ApplicationContext private val context: Context) {
    private var webView: WebView? = null
    private var ready = CompletableDeferred<Unit>()
    private val startLock = Mutex()
    private val calls = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val ids = AtomicInteger()

    /** Scripts served to the page by path (e.g. the patched player), so they load like real files. */
    private val served = ConcurrentHashMap<String, ByteArray>()

    fun serve(path: String, body: String) {
        served[path] = body.toByteArray()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun start() = startLock.withLock {
        if (webView != null) return@withLock
        ready = CompletableDeferred()
        withContext(Dispatchers.Main) {
            val wv = WebView(context.applicationContext)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true
            wv.settings.userAgentString = UA
            wv.addJavascriptInterface(Bridge(), "LiquidTubeBridge")
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    ready.complete(Unit)
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val path = request.url.path ?: return null
                    val body = served[path] ?: return null
                    return WebResourceResponse("application/javascript", "utf-8", ByteArrayInputStream(body))
                }
            }
            wv.loadDataWithBaseURL(
                "https://www.youtube.com/",
                "<!DOCTYPE html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
                "text/html", "utf-8", null,
            )
            webView = wv
        }
        withTimeout(15_000) { ready.await() }
    }

    /**
     * Evaluates a JavaScript *expression* (it may be a Promise) and returns its value as a
     * string. Throws with the JavaScript error message on failure.
     */
    suspend fun eval(expression: String, timeoutMs: Long = 30_000): String {
        start()
        val id = ids.incrementAndGet()
        val result = CompletableDeferred<String>()
        calls[id] = result
        val js = """
            (async () => {
              try { const v = await ($expression); LiquidTubeBridge.resolve($id, v == null ? "" : String(v)); }
              catch (e) { LiquidTubeBridge.reject($id, String((e && e.stack) || e)); }
            })();
        """.trimIndent()
        withContext(Dispatchers.Main) { webView?.evaluateJavascript(js, null) }
        return try {
            withTimeout(timeoutMs) { result.await() }
        } finally {
            calls.remove(id)
        }
    }

    /** Loads a served script into the page via a script tag. */
    suspend fun loadScript(path: String) {
        eval(
            """new Promise((res, rej) => { const s = document.createElement('script'); s.src = ${JSONObject.quote(path)};
               s.onload = () => res('ok'); s.onerror = () => rej(new Error('script failed: ' + ${JSONObject.quote(path)}));
               document.head.appendChild(s); })""",
            timeoutMs = 60_000,
        )
    }

    /** Throws the page away (e.g. after an unrecoverable error); the next call starts fresh. */
    suspend fun reset() = startLock.withLock {
        withContext(Dispatchers.Main) { webView?.destroy() }
        webView = null
        calls.values.forEach { it.completeExceptionally(IllegalStateException("reset")) }
        calls.clear()
        Log.i(TAG, "JsHost reset")
    }

    private inner class Bridge {
        @JavascriptInterface fun resolve(id: Int, value: String) { calls[id]?.complete(value) }
        @JavascriptInterface fun reject(id: Int, error: String) { calls[id]?.completeExceptionally(JsException(error)) }
    }

    class JsException(message: String) : Exception(message)

    companion object {
        const val TAG = "LiquidTube.Premium"
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}
