package com.enes.liquidtube.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enes.liquidtube.MainActivity
import com.enes.liquidtube.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Keeps LiquidTube up to date without a store. It looks at the project's GitHub Releases
 * (twice a day in the background, and once each time the app opens), and when a newer
 * version is out it:
 *   - raises a quiet notification ("Update available"), and
 *   - shows an update sheet inside the app with the release notes.
 * Tapping Update downloads the release's APK and hands it to Android's installer, which
 * installs it straight over the current app. Nothing leaves the phone except the plain
 * GitHub request; there's no server of our own.
 *
 * It can only update phones that already run a build containing this code — an APK that's
 * already installed can't be changed after the fact. So the first build with the updater
 * has to be shared the usual way once; from then on every release updates itself.
 */
object AppUpdater {
    private const val TAG = "LiquidTube"
    private const val CHANNEL = "updates"
    private const val WORK = "app-update-check"
    private const val NOTIFICATION_ID = 7001

    /** The GitHub repository that hosts the releases. */
    private const val REPO = "enes-github/LiquidTube"
    private const val LATEST = "https://api.github.com/repos/$REPO/releases/latest"

    @Serializable
    data class UpdateInfo(
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    sealed interface Status {
        data object Idle : Status
        data class Downloading(val fraction: Float) : Status
        data object Installing : Status
        data class Failed(val message: String) : Status
    }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    /** The newest release, when it's newer than what's installed; null otherwise. */
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    private val _status = MutableStateFlow<Status>(Status.Idle)
    /** Download / install progress, for the update sheet. */
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _promptDismissed = MutableStateFlow(false)
    /** True once the user taps "Later"; reset when a newer version turns up or a tap asks for it. */
    val promptDismissed: StateFlow<Boolean> = _promptDismissed.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) = context.getSharedPreferences("app_update", Context.MODE_PRIVATE)

    fun init(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Whether automatic update checks are on (Settings → Updates). Default on. */
    private fun enabled(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(KEY_AUTO_CHECK, true)

    fun currentVersionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    /** Let the update sheet show again (e.g. after the user tapped the notification). */
    fun requestPrompt() { _promptDismissed.value = false }
    fun dismissPrompt() { _promptDismissed.value = true }

    /**
     * Looks at the latest release. If it's newer, remembers it (so the app can show the sheet)
     * and, when [notify] is set, raises the quiet notification once per new version. Returns the
     * update if there is one. Safe to call from anywhere; never throws.
     */
    suspend fun check(context: Context, notify: Boolean): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled(context)) return@withContext null
        val info = runCatching { fetchLatest() }.getOrElse {
            Log.i(TAG, "Update check failed: ${it.message}")
            return@withContext null
        } ?: return@withContext null

        if (!isNewer(info.versionName, currentVersionName(context))) {
            _available.value = null
            return@withContext null
        }
        if (_available.value?.versionName != info.versionName) _promptDismissed.value = false
        _available.value = info

        if (notify) {
            val prefs = prefs(context)
            if (prefs.getString("notified_version", null) != info.versionName) {
                notify(context, info)
                prefs.edit().putString("notified_version", info.versionName).apply()
            }
        }
        Log.i(TAG, "Update available: ${info.versionName} (on ${currentVersionName(context)})")
        info
    }

    private fun fetchLatest(): UpdateInfo? {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            // GitHub rejects requests without a User-Agent; keep it generic (no personal data).
            setRequestProperty("User-Agent", "LiquidTube-Updater")
        }
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString(GhRelease.serializer(), body)
            if (release.draft || release.prerelease) return null
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
            val version = release.tag_name.ifBlank { release.name }
            return UpdateInfo(
                versionName = version.trimStart('v', 'V').trim(),
                notes = release.body.trim(),
                apkUrl = apk.browser_download_url,
                sizeBytes = apk.size,
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Downloads the update's APK and asks Android to install it. The system shows its own
     * "install / update?" screen; the first time, it also asks the user to allow installs
     * from LiquidTube. Progress flows through [status].
     */
    suspend fun downloadAndInstall(context: Context) {
        val info = _available.value ?: return
        _status.value = Status.Downloading(0f)
        val apk = runCatching { download(context, info) }.getOrElse {
            Log.i(TAG, "Update download failed: ${it.message}")
            _status.value = Status.Failed("download")
            return
        }
        _status.value = Status.Installing
        runCatching { install(context, apk) }.onFailure {
            Log.i(TAG, "Update install failed: ${it.message}")
            _status.value = Status.Failed(it.message ?: "install")
        }
    }

    private suspend fun download(context: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // One file per version; a finished download is reused, a stale one cleared out.
        dir.listFiles()?.forEach { if (it.name != fileName(info)) it.delete() }
        val out = File(dir, fileName(info))

        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "LiquidTube-Updater")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = if (info.sizeBytes > 0) info.sizeBytes else conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { file ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        file.write(buffer, 0, read)
                        done += read
                        if (total > 0) _status.value = Status.Downloading((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            conn.disconnect()
        }
        out
    }

    private fun fileName(info: UpdateInfo) = "liquidtube-${info.versionName}.apk"

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("liquidtube", 0, apk.length()).use { out ->
                    input.copyTo(out, 64 * 1024)
                    session.fsync(out)
                }
            }
            val intent = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_INSTALL_RESULT)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }

    /** Called back by the installer (via [UpdateInstallReceiver]). */
    internal fun onInstalled() { _status.value = Status.Idle }
    internal fun onInstallFailed(message: String?) { _status.value = Status.Failed(message ?: "install") }

    private fun notify(context: Context, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        // IMPORTANCE_LOW: it appears in the shade, but stays silent (no sound, no heads-up).
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "App Updates", NotificationManager.IMPORTANCE_LOW))
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SHOW_UPDATE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, 0, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Update available")
            .setContentText("LiquidTube ${info.versionName} is ready to install")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
    }

    // ---- version comparison ------------------------------------------------

    private fun parts(version: String): List<Int> =
        Regex("""\d+""").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()

    /** True when [remote] is a strictly higher version than [local] (e.g. 0.3.2 > 0.3.1). */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote)
        val l = parts(local)
        if (r.isEmpty()) return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    const val KEY_AUTO_CHECK = "auto_update_check"
    const val ACTION_INSTALL_RESULT = "com.enes.liquidtube.INSTALL_RESULT"
}

@Serializable
private data class GhRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

@Serializable
private data class GhAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0,
)

class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        AppUpdater.check(applicationContext, notify = true)
        Result.success()
    }.getOrElse { Result.retry() }
}

/** Receives the installer's result: it either asks us to show the confirm screen, or reports done/failed. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
                    .onFailure { AppUpdater.onInstallFailed(it.message) }
            }
            PackageInstaller.STATUS_SUCCESS -> AppUpdater.onInstalled()
            else -> AppUpdater.onInstallFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
