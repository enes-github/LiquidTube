package com.enes.liquidtube.premium

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which songs were last fetched as Premium 256kbps streams. Downloads and the
 * listening cache both fetch through the same resolver, so this tells us which stored
 * copies are high quality and which still need upgrading.
 */
@Singleton
class QualityStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("quality", Context.MODE_PRIVATE)
    private val _premium = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    /** Video ids whose stored copy (or last stream) is 256kbps. */
    val premiumIds: StateFlow<Set<String>> = _premium.asStateFlow()

    fun mark(videoId: String, premium: Boolean) {
        val now = _premium.value
        val next = if (premium) now + videoId else now - videoId
        if (next == now) return
        _premium.value = next
        prefs.edit().putStringSet(KEY, next).apply()
    }

    fun isPremium(videoId: String) = videoId in _premium.value

    private val loudPrefs = prefs
    /** Track loudness (dB vs YouTube's reference), kept so Sound Check works offline too. */
    fun setLoudness(videoId: String, db: Float) {
        loudPrefs.edit().putFloat("l_$videoId", db).apply()
    }

    fun loudness(videoId: String): Float? =
        if (loudPrefs.contains("l_$videoId")) loudPrefs.getFloat("l_$videoId", 0f) else null

    private companion object { const val KEY = "premium_ids" }
}
