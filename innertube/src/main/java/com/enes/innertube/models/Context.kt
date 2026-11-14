package com.enes.innertube.models

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * A client identity we impersonate when talking to InnerTube. Which one matters:
 *  - WEB_REMIX  → search / next (radio) / visitor_id on music.youtube.com.
 *  - VISIONOS   → `player`; direct, un-ciphered audio URLs that download in full
 *                 without a poToken. Needs a visitorData or it gets a bot check.
 *  - ANDROID_VR → `player` fallback, same properties (bot-checks some music tracks).
 *
 * Verified 2026-11-13 on the user's network:
 *  - IOS: URLs resolve, but without a poToken only the first 1MB downloads (403 after).
 *  - ANDROID_MUSIC / IOS_MUSIC: LOGIN_REQUIRED.
 * If playback breaks again, re-probe clients first.
 */
data class YouTubeClient(
    val clientName: String,
    val clientId: String,          // X-YouTube-Client-Name
    val clientVersion: String,
    val userAgent: String,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null,
) {
    val isWeb: Boolean get() = clientName.startsWith("WEB")

    fun context(hl: String, gl: String, visitorData: String?): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", clientName)
            put("clientVersion", clientVersion)
            put("hl", hl)
            put("gl", gl)
            visitorData?.let { put("visitorData", it) }
            deviceMake?.let { put("deviceMake", it) }
            deviceModel?.let { put("deviceModel", it) }
            osName?.let { put("osName", it) }
            osVersion?.let { put("osVersion", it) }
            androidSdkVersion?.let { put("androidSdkVersion", it) }
        }
    }

    companion object {
        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientId = "67",
            clientVersion = "1.20241211.01.00",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        )

        val ANDROID_VR = YouTubeClient(
            clientName = "ANDROID_VR",
            clientId = "28",
            clientVersion = "1.62.27",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            osName = "Android",
            osVersion = "12L",
            androidSdkVersion = 32,
        )

        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientId = "101",
            clientVersion = "0.1",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
            osName = "visionOS",
            osVersion = "1.3.21O771",
        )

        /** Account-authenticated candidates for Premium (256kbps) formats. */
        val IOS_MUSIC = YouTubeClient(
            clientName = "IOS_MUSIC",
            clientId = "26",
            clientVersion = "7.27.0",
            userAgent = "com.google.ios.youtubemusic/7.27.0 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientId = "7",
            clientVersion = "7.20250923.13.00",
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientId = "5",
            clientVersion = "20.10.4",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
        )
    }
}
