package com.enes.liquidtube.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.system.exitProcess

/**
 * Backup and restore of what's yours in LiquidTube: favorites, play counts and Replay
 * stats, Recently Played, settings, search history, what you were listening to and
 * the release history. Not included: your sign-in (sign in again after restoring) and
 * the downloaded music itself (it re-downloads).
 */
object Backup {
    /** The stores worth keeping (SharedPreferences file names). */
    private val STORES = listOf("library", "settings", "search", "session", "new_releases")
    private val json = Json { prettyPrint = false }

    suspend fun export(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = buildJsonObject {
                put("app", "LiquidTube")
                // Your downloads: the ones you made, and the Offline Mix's. A restore
                // downloads them again (the mix as automatic, so it keeps updating itself).
                runCatching {
                    val dl = dagger.hilt.android.EntryPointAccessors
                        .fromApplication(context.applicationContext, com.enes.liquidtube.download.DownloadEntryPoint::class.java)
                        .downloadUtil()
                    val auto = dl.autoIds.value
                    val done = dl.downloads.value.values.filter { it.isDone }.map { it.song }
                    val songs = kotlinx.serialization.builtins.ListSerializer(com.enes.innertube.models.SongItem.serializer())
                    put("downloads", json.encodeToJsonElement(songs, done.filter { it.id !in auto }))
                    put("mixDownloads", json.encodeToJsonElement(songs, done.filter { it.id in auto }))
                }
                put("version", 1)
                put("createdAt", System.currentTimeMillis())
                put("stores", buildJsonObject {
                    for (name in STORES) {
                        val all = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
                        put(name, buildJsonObject {
                            for ((key, value) in all) {
                                val typed = when (value) {
                                    is String -> buildJsonObject { put("t", "s"); put("v", value) }
                                    is Int -> buildJsonObject { put("t", "i"); put("v", value) }
                                    is Long -> buildJsonObject { put("t", "l"); put("v", value) }
                                    is Float -> buildJsonObject { put("t", "f"); put("v", value) }
                                    is Boolean -> buildJsonObject { put("t", "b"); put("v", value) }
                                    is Set<*> -> buildJsonObject {
                                        put("t", "set")
                                        put("v", JsonArray(value.filterIsInstance<String>().map { JsonPrimitive(it) }))
                                    }
                                    else -> null
                                } ?: continue
                                put(key, typed)
                            }
                        })
                    }
                })
            }
            val out = if (uri.scheme == "file") java.io.FileOutputStream(uri.path!!) else context.contentResolver.openOutputStream(uri, "wt")!!
            out.use {
                it.write(json.encodeToString(JsonObject.serializer(), root).toByteArray())
            }
        }
    }

    /** Replaces the current data with the backup's. The app restarts afterwards. */
    suspend fun restore(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val root = json.parseToJsonElement(text).jsonObject
            // "AppleTube": backups made before the app was renamed.
            require(root["app"]?.jsonPrimitive?.content in setOf("LiquidTube", "AppleTube")) { "Not a LiquidTube backup" }
            // Downloads to fetch again: handed to the download manager on the next start.
            context.getSharedPreferences("downloads_meta", Context.MODE_PRIVATE).edit().apply {
                root["downloads"]?.let { putString("restore_queue", it.toString()) }
                root["mixDownloads"]?.let { putString("restore_mix", it.toString()) }
            }.commit()
            val stores = root["stores"]!!.jsonObject
            for ((name, entries) in stores) {
                if (name !in STORES) continue
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                for ((key, typed) in entries.jsonObject) {
                    val o = typed.jsonObject
                    val v = o["v"] ?: continue
                    when (o["t"]?.jsonPrimitive?.content) {
                        "s" -> editor.putString(key, v.jsonPrimitive.content)
                        "i" -> editor.putInt(key, v.jsonPrimitive.content.toInt())
                        "l" -> editor.putLong(key, v.jsonPrimitive.content.toLong())
                        "f" -> editor.putFloat(key, v.jsonPrimitive.content.toFloat())
                        "b" -> editor.putBoolean(key, v.jsonPrimitive.booleanOrNull ?: false)
                        "set" -> editor.putStringSet(key, v.jsonArray.map { it.jsonPrimitive.content }.toSet())
                    }
                }
                editor.commit()
            }
        }
    }

    /** Start fresh so every store reloads what was just restored. */
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val restart = Intent.makeRestartActivityTask(launch.component)
        context.startActivity(restart)
        exitProcess(0)
    }
}
