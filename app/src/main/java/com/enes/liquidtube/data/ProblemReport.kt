package com.enes.liquidtube.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash and problem reports. Nothing is ever sent by itself: a crash is saved on the phone,
 * and the next launch asks whether to send it; "Report a Problem" in Settings does the same
 * on demand. Sending opens a pre-filled GitHub issue that the user reads before submitting.
 * Reports never include sign-in data, cookies, links with tokens or email addresses.
 */
object ProblemReport {
    private const val REPO = "enes-github/LiquidTube"
    private var dir: File? = null

    /** Saves any crash for the next launch, then lets Android close the app as usual. */
    fun init(context: Context) {
        val app = context.applicationContext
        dir = File(app.filesDir, "crash").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    appendLine("Crash on thread \"${thread.name}\" at ${stamp()}")
                    appendLine()
                    appendLine(redact(error.stackTraceToString()).take(6_000))
                }
                File(dir, "last-crash.txt").writeText(text)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The crash saved last time, if the app crashed. */
    fun pendingCrash(): String? = dir?.let { File(it, "last-crash.txt") }?.takeIf { it.exists() }?.readText()

    fun clearCrash() {
        dir?.let { File(it, "last-crash.txt").delete() }
    }

    /**
     * Opens a new GitHub issue with the report filled in, and copies the full report to the
     * clipboard (an issue link can only carry so much text).
     */
    suspend fun send(context: Context, crash: String?) {
        val report = build(context, crash)
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("LiquidTube report", report))
        val title = if (crash != null) "Crash: " + crash.lineSequence().drop(2).firstOrNull()?.take(80).orEmpty()
        else "Problem report"
        // GitHub links stop working past ~8 000 characters: keep the newest part of the log.
        var body = report
        while (Uri.encode(body).length > 6_500) body = body.lines().let { it.take(14) + it.drop(30) }.joinToString("\n")
        val url = "https://github.com/$REPO/issues/new?title=${Uri.encode(title)}&body=${Uri.encode(body)}"
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun build(context: Context, crash: String?): String = withContext(Dispatchers.IO) {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.versionName} (${it.longVersionCode})" }
        }.getOrDefault("?")
        buildString {
            appendLine("**What happened?**")
            appendLine("<!-- Describe what you were doing and what went wrong. -->")
            appendLine()
            appendLine("**Device**")
            appendLine("- LiquidTube $version")
            appendLine("- Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- Language: ${Locale.getDefault().toLanguageTag()}")
            if (crash != null) {
                appendLine()
                appendLine("**Crash**")
                appendLine("```")
                appendLine(crash.trim())
                appendLine("```")
            }
            appendLine()
            appendLine("**Recent log**")
            appendLine("```")
            appendLine(recentLog())
            appendLine("```")
        }
    }

    /** The app's own recent log lines (an app can only read its own), cleaned of anything private. */
    private fun recentLog(): String = runCatching {
        val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "250", "-v", "time", "--pid", Process.myPid().toString()))
        val lines = proc.inputStream.bufferedReader().readLines()
        lines.filter { l -> KEEP_TAGS.any { it in l } }
            .takeLast(120)
            .joinToString("\n") { redact(it) }
            .ifBlank { "(no log lines)" }
    }.getOrDefault("(log unavailable)")

    private val KEEP_TAGS = listOf("LiquidTube", "AndroidRuntime", "ExoPlayer", "MediaSession", "System.err")

    /** Strips sign-in data, tokens in links, and email addresses. */
    fun redact(text: String): String = text
        .replace(Regex("""(?i)(authorization|cookie)\s*[=:][^\n]*"""), "$1=[removed]")
        .replace(Regex("""(?i)(cookie|sapisid|authorization|set-cookie|__secure-[\w-]+|token|signature|sig|key)\s*[=:]\s*[^\s,;&"]+"""), "$1=[removed]")
        .replace(Regex("""https?://([^/\s"']+)[^\s"']*"""), "https://$1/…")
        .replace(Regex("""[\w.+-]+@[\w-]+\.[\w.-]+"""), "[email]")

    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
