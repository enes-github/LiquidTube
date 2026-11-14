package com.enes.liquidtube.ui.player

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Builds the whole Now Playing backdrop as ONE image with a truly progressive blur:
 * the cover (center-cropped to [coverAspect]) continues downward as a mirror image,
 * and every row gets its own blur radius, from none near the top, rising smoothly
 * past the title, to very heavy at the bottom. No layers, so no visible bands.
 *
 * Done once per song on a small (≈360px-wide) bitmap with a variable-radius box blur
 * (3 passes ≈ gaussian), then cached by Coil and simply drawn: free at frame time.
 */
class ProgressiveArtTransformation(
    private val coverAspect: Float,
    private val width: Int = 360,
    private val heightRatio: Float = 2.3f,
) : Transformation() {

    override val cacheKey: String = "progressive-art-v6-$coverAspect-$width-$heightRatio"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val src = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val w = width
        val coverH = (w / coverAspect).roundToInt()
        val h = (w * heightRatio).roundToInt()

        // Center-crop to the cover box's aspect, like ContentScale.Crop does on screen.
        val srcAspect = src.width.toFloat() / src.height
        val (cw, ch) = if (srcAspect > coverAspect) {
            (src.height * coverAspect).roundToInt() to src.height
        } else {
            src.width to (src.width / coverAspect).roundToInt()
        }
        val crop = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw.coerceAtMost(src.width), ch.coerceAtMost(src.height))
        val cover = Bitmap.createScaledBitmap(crop, w, coverH, true)
        val coverPx = IntArray(w * coverH)
        cover.getPixels(coverPx, 0, w, 0, 0, w, coverH)

        val r = IntArray(w * h)
        val g = IntArray(w * h)
        val b = IntArray(w * h)
        // Mirror a little above the cover's bottom edge: covers often end in a border or a
        // dark strip, and repeating it would leave a band right where the cover melts away.
        val pivot = (coverH * 0.94f).roundToInt()
        for (y in 0 until h) {
            val sy = when {
                y < pivot -> y
                else -> (2 * pivot - 1 - y).coerceIn(0, coverH - 1)
            }
            val so = sy * w
            val o = y * w
            for (x in 0 until w) {
                val c = coverPx[so + x]
                r[o + x] = (c shr 16) and 0xFF
                g[o + x] = (c shr 8) and 0xFF
                b[o + x] = c and 0xFF
            }
        }

        // Blur radius per row (in px of this bitmap). Two-stage ease: barely anything
        // down to the cover's lower edge, then it builds steadily under the title.
        val maxR = (w * 0.085f)
        val radius = IntArray(h) { y ->
            val f = y.toFloat() / coverH            // 1.0 = bottom of the cover box
            // Sharp until just above the title, a light blur at the title, then more and more.
            val early = smooth((f - 0.84f) / 0.16f) * 0.12f
            val late = smooth((f - 0.98f) / 0.6f).pow(1.1f) * 0.88f
            (maxR * (early + late)).roundToInt()
        }

        repeat(3) {
            for (ch2 in arrayOf(r, g, b)) {
                blurRows(ch2, w, h, radius)
                blurColumns(ch2, w, h, radius)
            }
        }

        val out = IntArray(w * h)
        for (i in out.indices) out[i] = (0xFF shl 24) or (r[i] shl 16) or (g[i] shl 8) or b[i]
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }

    /** Horizontal box blur; each row uses its own radius. */
    private fun blurRows(ch: IntArray, w: Int, h: Int, radius: IntArray) {
        val prefix = IntArray(w + 1)
        for (y in 0 until h) {
            val rad = radius[y]
            if (rad <= 0) continue
            val o = y * w
            for (x in 0 until w) prefix[x + 1] = prefix[x] + ch[o + x]
            for (x in 0 until w) {
                val a = max(0, x - rad)
                val z = min(w, x + rad + 1)
                ch[o + x] = (prefix[z] - prefix[a]) / (z - a)
            }
        }
    }

    /** Vertical box blur; the radius follows the row being written, so it varies smoothly. */
    private fun blurColumns(ch: IntArray, w: Int, h: Int, radius: IntArray) {
        val prefix = IntArray(h + 1)
        val col = IntArray(h)
        for (x in 0 until w) {
            for (y in 0 until h) prefix[y + 1] = prefix[y] + ch[y * w + x]
            for (y in 0 until h) {
                val rad = radius[y]
                col[y] = if (rad <= 0) ch[y * w + x] else {
                    val a = max(0, y - rad)
                    val z = min(h, y + rad + 1)
                    (prefix[z] - prefix[a]) / (z - a)
                }
            }
            for (y in 0 until h) ch[y * w + x] = col[y]
        }
    }
}
