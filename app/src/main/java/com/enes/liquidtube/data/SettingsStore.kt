package com.enes.liquidtube.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LanguageOption(val code: String, val displayName: String, val nativeName: String)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    private val _appLanguage = MutableStateFlow(prefs.getString(KEY_APP_LANGUAGE, "") ?: "")
    /** Selected app language code ("" = System Default, "tr" = Turkish, "en" = English, etc.). */
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    init {
        applyLanguage(_appLanguage.value)
    }

    fun setAppLanguage(code: String) {
        _appLanguage.value = code
        prefs.edit().putString(KEY_APP_LANGUAGE, code).apply()
        applyLanguage(code)
    }

    /** Interface language (redraws every screen) and the language YouTube Music answers in. */
    private fun applyLanguage(code: String) {
        com.enes.liquidtube.ui.theme.AppLanguage.code = code
        val lang = com.enes.liquidtube.ui.theme.AppLanguage.effective
        // English and Turkish are built in; any other language loads its community file.
        com.enes.liquidtube.ui.theme.AppLanguage.translations = Translations.load(appContext, lang)
        com.enes.innertube.InnerTube.hl = lang
        com.enes.innertube.InnerTube.gl = if (lang == "tr") "TR" else java.util.Locale.getDefault().country.ifBlank { "US" }
    }

    /** System Default + English + Turkish, plus any community languages found in assets/i18n. */
    fun languageOptions(): List<LanguageOption> = buildList {
        addAll(SUPPORTED_LANGUAGES)
        Translations.available(appContext).forEach { code ->
            add(LanguageOption(code, Translations.englishName(code), Translations.nativeName(code)))
        }
    }

    private val _dataSaver = MutableStateFlow(prefs.getBoolean(KEY_DATA_SAVER, false))
    /** Prefer the lowest-bitrate stream (saves mobile data). */
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
        prefs.edit().putBoolean(KEY_DATA_SAVER, enabled).apply()
    }

    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    /** Keep the music going with similar songs when the queue runs out (the ∞ button). */
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    fun setAutoplay(enabled: Boolean) {
        _autoplay.value = enabled
        prefs.edit().putBoolean(KEY_AUTOPLAY, enabled).apply()
    }

    private val _crossfade = MutableStateFlow(prefs.getInt(KEY_CROSSFADE, 0))
    /** Seconds to fade out the ending song and fade in the next one; 0 = off (gapless). */
    val crossfadeSeconds: StateFlow<Int> = _crossfade.asStateFlow()

    fun setCrossfadeSeconds(seconds: Int) {
        _crossfade.value = seconds
        prefs.edit().putInt(KEY_CROSSFADE, seconds).apply()
    }

    private val _accent = MutableStateFlow(prefs.getInt(KEY_ACCENT, 0))
    /** Index into the accent palette (0 = Apple red). */
    val accent: StateFlow<Int> = _accent.asStateFlow()

    fun setAccent(index: Int) {
        _accent.value = index
        prefs.edit().putInt(KEY_ACCENT, index).apply()
    }

    private val _glassRefraction = MutableStateFlow(prefs.getBoolean(KEY_GLASS, true))
    /** Liquid Glass lens refraction; off = plain frosted glass (lighter on the GPU). */
    val glassRefraction: StateFlow<Boolean> = _glassRefraction.asStateFlow()

    fun setGlassRefraction(enabled: Boolean) {
        _glassRefraction.value = enabled
        prefs.edit().putBoolean(KEY_GLASS, enabled).apply()
    }

    private val _smart = MutableStateFlow(prefs.getBoolean(KEY_SMART, true))
    /** Automatically keep a smart Offline Mix of your music downloaded. */
    val smartDownloads: StateFlow<Boolean> = _smart.asStateFlow()

    fun setSmartDownloads(enabled: Boolean) {
        _smart.value = enabled
        prefs.edit().putBoolean(KEY_SMART, enabled).apply()
    }

    private val _smartSize = MutableStateFlow(prefs.getInt(KEY_SMART_SIZE, 50))
    /** How many songs the Offline Mix keeps. */
    val smartSize: StateFlow<Int> = _smartSize.asStateFlow()

    fun setSmartSize(size: Int) {
        _smartSize.value = size
        prefs.edit().putInt(KEY_SMART_SIZE, size).apply()
    }

    private val _smartCellular = MutableStateFlow(prefs.getBoolean(KEY_SMART_CELL, false))
    /** Allow Offline Mix downloads on mobile data (default: Wi-Fi only). */
    val smartOnCellular: StateFlow<Boolean> = _smartCellular.asStateFlow()

    fun setSmartOnCellular(enabled: Boolean) {
        _smartCellular.value = enabled
        prefs.edit().putBoolean(KEY_SMART_CELL, enabled).apply()
    }

    private val _cacheSize = MutableStateFlow(prefs.getInt(KEY_CACHE_MB, 2048))
    /** Listening cache limit in MB (songs you play are kept here for offline). */
    val cacheSizeMb: StateFlow<Int> = _cacheSize.asStateFlow()

    fun setCacheSizeMb(mb: Int) {
        _cacheSize.value = mb
        prefs.edit().putInt(KEY_CACHE_MB, mb).apply()
    }

    private val _premium = MutableStateFlow(prefs.getBoolean(KEY_PREMIUM, true))
    /** Try YouTube Music Premium's 256kbps streams (signed-in Premium accounts). */
    val premiumAudio: StateFlow<Boolean> = _premium.asStateFlow()

    fun setPremiumAudio(enabled: Boolean) {
        _premium.value = enabled
        prefs.edit().putBoolean(KEY_PREMIUM, enabled).apply()
    }

    private val _eq = MutableStateFlow(prefs.getString(KEY_EQ, "off") ?: "off")
    /** Equalizer preset id (see AudioFx.PRESETS). */
    val eqPreset: StateFlow<String> = _eq.asStateFlow()

    fun setEqPreset(id: String) {
        _eq.value = id
        prefs.edit().putString(KEY_EQ, id).apply()
    }

    private val _soundCheck = MutableStateFlow(prefs.getBoolean(KEY_SOUNDCHECK, false))
    /** Even out volume between songs (YouTube loudness data). */
    val soundCheck: StateFlow<Boolean> = _soundCheck.asStateFlow()

    fun setSoundCheck(enabled: Boolean) {
        _soundCheck.value = enabled
        prefs.edit().putBoolean(KEY_SOUNDCHECK, enabled).apply()
    }

    private val _translate = MutableStateFlow(prefs.getBoolean(KEY_TRANSLATE, false))
    /** Show a translation under each lyric line. */
    val translateLyrics: StateFlow<Boolean> = _translate.asStateFlow()

    fun setTranslateLyrics(enabled: Boolean) {
        _translate.value = enabled
        prefs.edit().putBoolean(KEY_TRANSLATE, enabled).apply()
    }

    private val _translateTo = MutableStateFlow(prefs.getString(KEY_TRANSLATE_TO, "") ?: "")
    /** Language code to translate lyrics into ("" = the phone's language). */
    val translateTo: StateFlow<String> = _translateTo.asStateFlow()

    fun setTranslateTo(code: String) {
        _translateTo.value = code
        prefs.edit().putString(KEY_TRANSLATE_TO, code).apply()
    }

    private val _lockLyrics = MutableStateFlow(prefs.getBoolean(KEY_LOCK_LYRICS, true))
    /** Show the line being sung in the media notification / lock screen. */
    val lockScreenLyrics: StateFlow<Boolean> = _lockLyrics.asStateFlow()

    fun setLockScreenLyrics(enabled: Boolean) {
        _lockLyrics.value = enabled
        prefs.edit().putBoolean(KEY_LOCK_LYRICS, enabled).apply()
    }

    private val _autoResume = MutableStateFlow(prefs.getBoolean(KEY_AUTO_RESUME, true))
    /** Pick up where you left off when headphones or a car connect. */
    val autoResume: StateFlow<Boolean> = _autoResume.asStateFlow()

    fun setAutoResume(enabled: Boolean) {
        _autoResume.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_RESUME, enabled).apply()
    }

    private val _releaseAlerts = MutableStateFlow(prefs.getBoolean(KEY_RELEASES, true))
    /** Notify about new releases from artists you follow. */
    val releaseAlerts: StateFlow<Boolean> = _releaseAlerts.asStateFlow()

    fun setReleaseAlerts(enabled: Boolean) {
        _releaseAlerts.value = enabled
        prefs.edit().putBoolean(KEY_RELEASES, enabled).apply()
    }

    private val _autoUpdate = MutableStateFlow(prefs.getBoolean(AppUpdater.KEY_AUTO_CHECK, true))
    /** Check GitHub for a newer LiquidTube and offer to install it. */
    val autoUpdateCheck: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    fun setAutoUpdateCheck(enabled: Boolean) {
        _autoUpdate.value = enabled
        prefs.edit().putBoolean(AppUpdater.KEY_AUTO_CHECK, enabled).apply()
    }

    private val _station = MutableStateFlow(prefs.getString(KEY_STATION, "balanced") ?: "balanced")
    /** Station style: "familiar", "balanced" or "discovery". */
    val stationStyle: StateFlow<String> = _station.asStateFlow()

    fun setStationStyle(style: String) {
        _station.value = style
        prefs.edit().putString(KEY_STATION, style).apply()
    }

    private val _onboardingSeen = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))
    /** Whether the first-launch walkthrough has been shown (once per install). */
    val onboardingSeen: StateFlow<Boolean> = _onboardingSeen.asStateFlow()

    fun setOnboardingSeen() {
        _onboardingSeen.value = true
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
    }

    companion object {
        private const val KEY_ONBOARDING = "onboarding_seen"
        /** Languages the interface is translated into ("" = follow the phone). */
        val SUPPORTED_LANGUAGES = listOf(
            LanguageOption("", "System Default", "Sistem Varsayılanı"),
            LanguageOption("en", "English", "English"),
            LanguageOption("tr", "Turkish", "Türkçe"),
        )

        private const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_TRANSLATE = "translate_lyrics"
        const val KEY_TRANSLATE_TO = "translate_to"
        const val KEY_LOCK_LYRICS = "lock_lyrics"
        const val KEY_RELEASES = "release_alerts"
        const val KEY_EQ = "eq_preset"
        const val KEY_SOUNDCHECK = "sound_check"
        const val KEY_STATION = "station_style"
        const val KEY_PREMIUM = "premium_audio"
        const val KEY_CACHE_MB = "cache_mb"
        const val KEY_SMART = "smart_downloads"
        const val KEY_SMART_SIZE = "smart_size"
        const val KEY_SMART_CELL = "smart_cellular"
        const val KEY_DATA_SAVER = "data_saver"
        const val KEY_AUTOPLAY = "autoplay"
        const val KEY_CROSSFADE = "crossfade_s"
        const val KEY_ACCENT = "accent"
        const val KEY_GLASS = "glass_refraction"
    }
}
