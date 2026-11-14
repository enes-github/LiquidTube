package com.enes.liquidtube

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        com.enes.innertube.InnerTube.logger = { android.util.Log.i("LiquidTube", it) }
        com.enes.liquidtube.data.ImagePrefetcher.init(this)
        com.enes.liquidtube.data.LyricsStore.init(this)
        com.enes.liquidtube.data.OfflineArt.init(this)
        com.enes.liquidtube.data.LyricsTranslator.init(this)
        com.enes.liquidtube.data.NewReleases.init(this)
        com.enes.liquidtube.data.AutoBackup.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(160)
            // Covers of songs you have offline come from their saved file.
            .components { add(com.enes.liquidtube.data.OfflineArt.CoilInterceptor) }
            // Big caches: covers come back instantly when you scroll back or reopen a page.
            .memoryCache { coil3.memory.MemoryCache.Builder().maxSizePercent(context, 0.3).build() }
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(okio.Path.Companion.run { context.cacheDir.resolve("artwork").absolutePath.toPath() })
                    .maxSizeBytes(1024L * 1024 * 1024)
                    .build()
            }
            .build()
}
