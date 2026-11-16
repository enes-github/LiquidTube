package com.enes.liquidtube.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.enes.liquidtube.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadEntryPoint {
    fun downloadUtil(): DownloadUtil
}

/** Foreground service that runs downloads, with a progress notification. */
@OptIn(UnstableApi::class)
class AppDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads_channel,
    0,
) {
    private val util by lazy {
        EntryPointAccessors.fromApplication(applicationContext, DownloadEntryPoint::class.java).downloadUtil()
    }

    override fun getDownloadManager(): DownloadManager = util.manager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val base = DownloadNotificationHelper(this, CHANNEL_ID).buildProgressNotification(
            this, android.R.drawable.stat_sys_download, null, null, downloads, notMetRequirements,
        )
        // Some ROMs (ROG UI, HyperOS…) show the app's newest notification as a status-bar
        // chip. This one re-posts every time a song starts or finishes downloading, which
        // made the chip flick from the song's artwork to the download icon. Keep it quiet
        // and always "older" than the media notification, in a group of its own.
        return Notification.Builder.recoverBuilder(this, base)
            .setWhen(0)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setGroup(CHANNEL_ID)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    private companion object {
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "downloads"
    }
}
