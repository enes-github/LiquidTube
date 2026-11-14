package com.enes.liquidtube.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enes.liquidtube.MainActivity
import com.enes.liquidtube.R
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.CollectionType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * New releases from the artists you follow. Twice a day (in the background, on any
 * connection) it looks at each artist's newest albums and singles; anything it hasn't
 * seen before becomes a notification and joins "New from Your Artists" on the New tab.
 */
object NewReleases {
    private const val TAG = "LiquidTube"
    private const val CHANNEL = "releases"
    private const val WORK = "new-releases"

    @Serializable
    data class Release(val item: CollectionItem, val artist: String, val foundAt: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(Release.serializer())

    private val _recent = MutableStateFlow<List<Release>>(emptyList())
    /** Releases found in the last 6 weeks, newest first. */
    val recent: StateFlow<List<Release>> = _recent.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences("new_releases", Context.MODE_PRIVATE)

    fun init(context: Context) {
        _recent.value = load(context)
        val request = PeriodicWorkRequestBuilder<ReleaseWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun load(context: Context): List<Release> {
        val cutoff = System.currentTimeMillis() - 42L * 24 * 3_600_000
        return prefs(context).getString("recent", null)
            ?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }
            .orEmpty()
            .filter { it.foundAt >= cutoff }
    }

    /** Checks now if the last check is older than [maxAgeMs] (the New tab calls this). */
    suspend fun checkIfStale(context: Context, maxAgeMs: Long = 12 * 3_600_000L) {
        val last = prefs(context).getLong("last_check", 0L)
        if (System.currentTimeMillis() - last >= maxAgeMs) check(context)
    }

    suspend fun check(context: Context): Int = withContext(Dispatchers.IO) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ReleaseEntryPoint::class.java)
        val repository = entry.repository()
        val settings = entry.settings()
        if (!entry.auth().isLoggedIn.value) return@withContext 0
        val prefs = prefs(context)
        val firstRun = !prefs.contains("seen")
        val seen = prefs.getStringSet("seen", emptySet()).orEmpty().toMutableSet()

        // The artists you follow (subscriptions), else the artists in your library.
        val artists = (repository.library("FEmusic_library_corpus_artists").getOrNull().orEmpty()
            .ifEmpty { repository.library("FEmusic_library_corpus_track_artists").getOrNull().orEmpty() })
            .filter { it.type == CollectionType.ARTIST }
            .take(80)
        if (artists.isEmpty()) return@withContext 0

        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearRe = Regex("""\b(19|20)\d{2}\b""")
        val gate = Semaphore(4)
        val found: List<Release> = coroutineScope {
            artists.map { artist ->
                async {
                    gate.withPermit {
                        val page = repository.browse(artist.browseId).getOrNull() ?: return@withPermit emptyList()
                        // Each shelf lists newest first: the first few albums/singles are what's new.
                        page.shelves.flatMap { it.collections.take(3) }
                            .filter { it.type == CollectionType.ALBUM }
                            .filter { c ->
                                val year = c.subtitle?.let { yearRe.find(it)?.value?.toIntOrNull() } ?: thisYear
                                year >= thisYear - 1
                            }
                            .map { Release(it, artist.title, System.currentTimeMillis()) }
                    }
                }
            }.awaitAll().flatten().distinctBy { it.item.browseId }
        }

        val fresh = found.filter { it.item.browseId !in seen }
        seen += found.map { it.item.browseId }
        val now = System.currentTimeMillis()
        // First run: everything counts as known (no flood of alerts), but this year's
        // releases fill the New tab row so it isn't empty.
        val added = if (firstRun) found.filter { r ->
            r.item.subtitle?.let { yearRe.find(it)?.value?.toIntOrNull() } == thisYear
        } else fresh
        val recent = (added + load(context)).distinctBy { it.item.browseId }.take(60)
        prefs.edit()
            .putStringSet("seen", seen)
            .putString("recent", json.encodeToString(listSerializer, recent))
            .putLong("last_check", now)
            .apply()
        _recent.value = recent
        Log.i(TAG, "New releases: ${artists.size} artists checked, ${fresh.size} new${if (firstRun) " (first run)" else ""}")

        if (!firstRun && fresh.isNotEmpty() && settings.releaseAlerts.value) notify(context, fresh)
        fresh.size
    }

    private fun notify(context: Context, releases: List<Release>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "New Releases", NotificationManager.IMPORTANCE_DEFAULT))
        releases.take(5).forEachIndexed { i, r ->
            val open = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_COLLECTION, r.item.browseId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pi = PendingIntent.getActivity(
                context, r.item.browseId.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val kind = r.item.subtitle?.substringBefore(" • ")?.substringBefore(" · ")?.takeIf { it.length < 14 } ?: "Release"
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle("New from ${r.artist}")
                .setContentText("${r.item.title} · $kind")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setGroup(CHANNEL)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(4000 + i, n) }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReleaseEntryPoint {
    fun repository(): MusicRepository
    fun settings(): SettingsStore
    fun auth(): AuthStore
}

class ReleaseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        NewReleases.check(applicationContext)
        Result.success()
    }.getOrElse { Result.retry() }
}
