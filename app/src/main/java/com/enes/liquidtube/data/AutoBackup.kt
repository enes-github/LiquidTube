package com.enes.liquidtube.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

/**
 * Online backup: one backup file that you place once with the system picker (Google
 * Drive, or any cloud app that shows up there), then kept up to date every day in the
 * background. The cloud app uploads it; no extra sign-in needed here.
 */
object AutoBackup {
    private const val WORK = "auto-backup"

    data class Target(val uri: Uri, val name: String, val where: String, val lastAt: Long, val lastFailed: Boolean)

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    // Not part of the backup itself (STORES in Backup), so a restore never points at someone else's file.
    private fun prefs(context: Context) = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)

    fun init(context: Context) {
        _target.value = read(context)
        if (_target.value != null) schedule(context)
    }

    private fun read(context: Context): Target? {
        val p = prefs(context)
        val uri = p.getString("uri", null)?.let(Uri::parse) ?: return null
        return Target(uri, p.getString("name", null) ?: "Backup", p.getString("where", null) ?: "", p.getLong("last", 0L), p.getBoolean("failed", false))
    }

    /** The file chosen in the picker becomes the backup, written right away and daily. */
    suspend fun setTarget(context: Context, uri: Uri): Result<Unit> {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "LiquidTube Backup.json"
        prefs(context).edit()
            .putString("uri", uri.toString())
            .putString("name", name)
            .putString("where", placeName(uri))
            .apply()
        schedule(context)
        return runNow(context)
    }

    fun clear(context: Context) {
        val old = _target.value
        if (old != null) runCatching {
            context.contentResolver.releasePersistableUriPermission(
                old.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs(context).edit().clear().apply()
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        _target.value = null
    }

    /** Writes the backup into the chosen file now. */
    suspend fun runNow(context: Context): Result<Unit> {
        val t = read(context) ?: return Result.failure(IllegalStateException("No backup file chosen"))
        val result = Backup.export(context, t.uri)
        prefs(context).edit()
            .putBoolean("failed", result.isFailure)
            .apply { if (result.isSuccess) putLong("last", System.currentTimeMillis()) }
            .apply()
        result.exceptionOrNull()?.let { Log.w("LiquidTube", "Auto backup failed", it) }
        _target.value = read(context)
        return result
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** True when the chosen file only lives on this phone (no cloud copy). */
    fun isLocal(t: Target) = t.where == "This phone"

    /**
     * One-off: hands a fresh backup to the Google Drive app, which shows its own
     * "Upload to Drive" screen (account and folder). Falls back to the share sheet
     * when Drive isn't installed.
     */
    suspend fun sendToDrive(context: Context): Result<Unit> = runCatching {
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val file = java.io.File(dir, "LiquidTube Backup $day.json")
        Backup.export(context, Uri.fromFile(file)).getOrThrow()
        val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val drive = Intent(send).setPackage("com.google.android.apps.docs")
        if (drive.resolveActivity(context.packageManager) != null) context.startActivity(drive)
        else context.startActivity(Intent.createChooser(send, "Save Backup").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** "Google Drive", "OneDrive"… from the app that holds the file. */
    private fun placeName(uri: Uri): String = when {
        uri.authority?.contains("com.google.android.apps.docs") == true -> "Google Drive"
        uri.authority?.contains("microsoft.skydrive") == true -> "OneDrive"
        uri.authority?.contains("dropbox") == true -> "Dropbox"
        uri.authority?.contains("externalstorage") == true || uri.authority?.contains("providers.downloads") == true -> "This phone"
        else -> uri.authority?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() } ?: "Cloud"
    }
}

class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (AutoBackup.runNow(applicationContext).isSuccess) Result.success() else Result.retry()
}
