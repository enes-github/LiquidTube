package com.enes.liquidtube.playback

import com.enes.liquidtube.ui.theme.tr
import android.media.audiofx.DynamicsProcessing
import android.util.Log

/**
 * Equalizer + Sound Check on the player's audio session, using Android's
 * DynamicsProcessing: a 5-band EQ, an input gain (Sound Check: brings every song to the
 * same loudness using YouTube's loudness data) and a limiter so boosts never clip.
 */
class AudioFx {
    private var dp: DynamicsProcessing? = null
    private var session = 0
    private var preset = PRESETS.first()
    private var gainDb = 0f

    fun attach(audioSessionId: Int) {
        if (audioSessionId == session && dp != null) return
        release()
        if (audioSessionId <= 0) return
        session = audioSessionId
        dp = runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2, true, BANDS.size, false, 0, false, 0, true,
            ).build()
            DynamicsProcessing(0, audioSessionId, config).apply {
                setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, -1f, 0f))
            }
        }.onFailure { Log.w(TAG, "Audio effects unavailable: ${it.message}") }.getOrNull()
        apply()
    }

    /** Update the EQ preset and Sound Check gain (dB). */
    fun set(presetId: String, soundCheckGainDb: Float) {
        preset = PRESETS.firstOrNull { it.id == presetId } ?: PRESETS.first()
        gainDb = soundCheckGainDb
        apply()
    }

    private fun apply() {
        val d = dp ?: return
        runCatching {
            BANDS.forEachIndexed { i, cutoff ->
                d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoff, preset.gains[i]))
            }
            // A boosting preset lowers the input a little so the limiter rarely has to work.
            val headroom = -(preset.gains.maxOrNull() ?: 0f).coerceAtLeast(0f) * 0.5f
            d.setInputGainAllChannelsTo(gainDb + headroom)
            d.enabled = preset.id != "off" || gainDb != 0f
        }.onFailure { Log.w(TAG, "Audio effects failed: ${it.message}") }
    }

    fun release() {
        runCatching { dp?.release() }
        dp = null
        session = 0
    }

    data class Preset(val id: String, private val en: String, val gains: FloatArray) {
        /** Shown in menus, in the app's language. */
        val label: String get() = tr(en, AudioFx.PRESET_TR[id] ?: en)
    }


    companion object {
        internal val PRESET_TR = mapOf("off" to "Kapalı", "bass" to "Bas Güçlendirici", "vocal" to "Vokal", "treble" to "Tiz Güçlendirici", "acoustic" to "Akustik", "electronic" to "Elektronik", "late" to "Gece", "loud" to "Yüksek Ses")
        private const val TAG = "LiquidTube"
        /** Upper cutoffs of the 5 bands (Hz): lows, low-mids, mids, presence, air. */
        val BANDS = floatArrayOf(150f, 600f, 2500f, 7000f, 20000f)
        val PRESETS = listOf(
            Preset("off", "Off", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
            Preset("bass", "Bass Booster", floatArrayOf(6f, 3f, 0f, 0f, 0f)),
            Preset("vocal", "Vocal", floatArrayOf(-2f, 0f, 3f, 3f, 0f)),
            Preset("treble", "Treble Booster", floatArrayOf(0f, 0f, 0f, 3f, 5f)),
            Preset("acoustic", "Acoustic", floatArrayOf(3f, 1f, 1f, 2f, 2f)),
            Preset("electronic", "Electronic", floatArrayOf(4f, 2f, -1f, 2f, 4f)),
            Preset("late", "Late Night", floatArrayOf(-3f, 0f, 2f, 1f, -3f)),
            Preset("loud", "Loudness", floatArrayOf(4f, 1f, 0f, 1f, 3f)),
        )

        /** Sound Check gain for a track: pull it toward YouTube's reference, boosting at most 6 dB. */
        fun soundCheckGain(loudnessDb: Float?): Float = if (loudnessDb == null) 0f else (-loudnessDb).coerceIn(-12f, 6f)
    }
}
