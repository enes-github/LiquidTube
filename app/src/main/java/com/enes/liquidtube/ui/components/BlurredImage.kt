package com.enes.liquidtube.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.enes.liquidtube.ui.theme.AppTheme
import kotlin.math.max

/**
 * Blurs a *tiny* copy of the image once (a 3-pass box blur ≈ gaussian) and caches it.
 * Drawn scaled up with bilinear filtering it looks like a huge live blur, but costs
 * nothing per frame. Live RenderEffect blurs of full-screen images made page
 * transitions stutter, because every glass layer re-renders them each frame.
 */
class SoftBlurTransformation(private val width: Int = 64, private val radius: Int = 4) : Transformation() {
    override val cacheKey: String = "softblur-$width-$radius"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = width
        val h = max(1, input.height * w / max(1, input.width))
        val src = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) {
            boxBlur(px, w, h, radius, horizontal = true)
            boxBlur(px, w, h, radius, horizontal = false)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val line = IntArray(len)
        val div = 2 * r + 1
        for (l in 0 until lines) {
            for (i in 0 until len) line[i] = px[if (horizontal) l * w + i else i * w + l]
            var ra = 0; var rr = 0; var rg = 0; var rb = 0
            for (k in -r..r) {
                val c = line[k.coerceIn(0, len - 1)]
                ra += c ushr 24; rr += (c shr 16) and 0xFF; rg += (c shr 8) and 0xFF; rb += c and 0xFF
            }
            for (i in 0 until len) {
                px[if (horizontal) l * w + i else i * w + l] =
                    ((ra / div) shl 24) or ((rr / div) shl 16) or ((rg / div) shl 8) or (rb / div)
                val out = line[(i - r).coerceIn(0, len - 1)]
                val inn = line[(i + r + 1).coerceIn(0, len - 1)]
                ra += (inn ushr 24) - (out ushr 24)
                rr += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                rg += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                rb += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}

/** A heavily blurred rendition of [url], cheap enough for full-screen backgrounds. */
@Composable
fun BlurredImage(url: String?, modifier: Modifier = Modifier, saturation: Float = 1f, layer: GraphicsLayerScope.() -> Unit = {}) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url?.let { com.enes.innertube.hiResThumbnail(it, 226) })
            .size(226)
            .transformations(SoftBlurTransformation())
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High,
        colorFilter = if (saturation != 1f) remember(saturation) {
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(saturation) })
        } else null,
        modifier = modifier.graphicsLayer(layer),
    )
}

/**
 * The iOS 27 page background: the artwork blurred into a colour field, tinted toward the
 * theme so text stays readable, fixed behind the scrolling content. [drift] (px) moves it
 * slightly with scrolling.
 */
@Composable
fun TintedBackdrop(url: String?, glow: Boolean = false, drift: () -> Float = { 0f }) {
    val c = AppTheme.colors
    // Clipped: the scaled-up blur must not spill outside the page while it slides (back swipe).
    Box(Modifier.fillMaxSize().clipToBounds().background(c.background)) {
        BlurredImage(
            url,
            Modifier
                .fillMaxSize()
                .then(
                    // Glow mode: colour pools at the top and melts into the plain background,
                    // so grey or busy artwork never turns the whole page muddy.
                    if (glow) Modifier
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to Color.Black,
                                    0.28f to Color.Black.copy(alpha = 0.75f),
                                    0.62f to Color.Transparent,
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        }
                    else Modifier
                ),
            saturation = if (glow) 1.5f else 1f,
        ) {
            scaleX = 1.35f
            scaleY = 1.35f
            translationY = -drift() * (if (glow) 0.3f else 0.12f)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        c.isDark && glow -> Color.Black.copy(alpha = 0.38f)
                        c.isDark -> Color.Black.copy(alpha = 0.5f)
                        glow -> Color.White.copy(alpha = 0.35f)
                        else -> Color.White.copy(alpha = 0.42f)
                    }
                ),
        )
    }
}
