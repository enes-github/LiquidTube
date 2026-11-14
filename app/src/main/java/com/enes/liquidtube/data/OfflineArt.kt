package com.enes.liquidtube.data

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Covers of the songs you have offline, kept with them (not in the image cache, which
 * forgets old images as you browse). Every image request for a cover saved here is
 * answered from the file, so downloads show their artwork without a connection.
 */
object OfflineArt {
    private var dir: File? = null
    private val saved = ConcurrentHashMap.newKeySet<String>()

    fun init(context: Context) {
        val d = File(context.filesDir, "art").apply { mkdirs() }
        dir = d
        d.list()?.forEach { saved += it.removeSuffix(".jpg") }
    }

    /** One key per cover, whatever size the URL asks for. */
    fun key(url: String): String {
        val vi = url.indexOf("/vi/")
        if (vi >= 0) return "vi_" + url.substring(vi + 4).substringBefore('/').filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        val base = url.substringBefore('?')
        val path = base.substringAfterLast('/')
        val stem = if ('=' in path) base.substringBeforeLast('=') else base
        return Integer.toHexString(stem.hashCode()) + "_" + stem.length
    }

    /** The saved cover for [url], if there is one. */
    fun fileFor(url: String): File? {
        val k = key(url)
        if (k !in saved) return null
        return dir?.let { File(it, "$k.jpg") }?.takeIf { it.exists() }
    }

    fun has(url: String) = key(url) in saved

    /** Saves the cover (in good quality) if it isn't saved yet. Needs a connection the first time. */
    suspend fun save(context: Context, url: String?) {
        url ?: return
        val k = key(url)
        if (k in saved) return
        val d = dir ?: return
        val request = ImageRequest.Builder(context)
            .data(com.enes.innertube.hiResThumbnail(url, 544))
            .size(544)
            .allowHardware(false)
            .build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val tmp = File(d, "$k.tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                tmp.renameTo(File(d, "$k.jpg"))
                saved += k
            }
        }
    }

    /** Answers image requests for saved covers from the file. */
    object CoilInterceptor : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val data = chain.request.data
            if (data is String && data.startsWith("http")) {
                val file = fileFor(data)
                if (file != null) {
                    return chain.withRequest(chain.request.newBuilder().data(file).build()).proceed()
                }
            }
            return chain.proceed()
        }
    }
}
