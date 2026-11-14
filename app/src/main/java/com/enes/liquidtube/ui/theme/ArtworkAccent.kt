package com.enes.liquidtube.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's accent taken from the playing song's cover (Settings → Accent Color →
 * Artwork): its most vivid colour, tuned so buttons and text stay readable in light
 * and dark. Grey or black-and-white covers keep the usual red. Fades between songs.
 */
@Composable
fun rememberArtworkAccent(url: String?): Color {
    val context = LocalContext.current
    val picked by produceState(AppleRed, url) {
        if (url == null) { value = AppleRed; return@produceState }
        value = withContext(Dispatchers.Default) {
            runCatching {
                val request = ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build()
                val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                    ?: return@runCatching AppleRed
                val palette = Palette.from(bitmap).maximumColorCount(16).generate()
                val swatch = listOfNotNull(
                    palette.vibrantSwatch, palette.lightVibrantSwatch, palette.darkVibrantSwatch,
                    palette.mutedSwatch, palette.dominantSwatch,
                ).maxByOrNull { s ->
                    val hsl = s.hsl
                    hsl[1] * 2f + (s.population / 1000f).coerceAtMost(1f) // colourful first, then common
                } ?: return@runCatching AppleRed
                val hsl = swatch.hsl.copyOf()
                if (hsl[1] < 0.22f) return@runCatching AppleRed // a grey cover: no colour to take
                hsl[1] = hsl[1].coerceIn(0.55f, 0.9f)
                hsl[2] = hsl[2].coerceIn(0.5f, 0.62f) // readable on black and on white
                Color(ColorUtils.HSLToColor(hsl))
            }.getOrDefault(AppleRed)
        }
    }
    val animated by animateColorAsState(picked, tween(600), label = "artAccent")
    return animated
}

@Suppress("unused")
private fun Color.argb() = toArgb()
