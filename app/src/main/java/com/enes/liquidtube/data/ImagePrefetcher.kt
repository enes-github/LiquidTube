package com.enes.liquidtube.data

import android.content.Context
import android.net.ConnectivityManager
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.enes.innertube.hiResThumbnail

/**
 * Downloads artwork ahead of time into the disk cache when a page loads, so fast scrolling
 * shows covers instantly instead of waiting for each one. On Wi-Fi it fetches everything on
 * the page (both the card and the small row size); on mobile data only what's near the top.
 */
object ImagePrefetcher {
    private var app: Context? = null
    private val done = HashSet<String>()

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun prefetch(urls: List<String?>) {
        val context = app ?: return
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = cm?.isActiveNetworkMetered == false
        val list = urls.filterNotNull().distinct().let { if (wifi) it.take(400) else it.take(24) }
        val loader = SingletonImageLoader.get(context)
        for (url in list) {
            // Cards use the full-size URL; list rows use a 226px version (see Artwork(small)).
            val variants = if (wifi) listOf(url, hiResThumbnail(url, 226)) else listOf(url)
            for (v in variants) {
                val fresh = synchronized(done) { done.add(v) }
                if (!fresh) continue
                loader.enqueue(
                    ImageRequest.Builder(context)
                        .data(v)
                        .memoryCachePolicy(CachePolicy.DISABLED) // disk only: cheap, no decoding cost now
                        .build()
                )
            }
        }
    }
}
