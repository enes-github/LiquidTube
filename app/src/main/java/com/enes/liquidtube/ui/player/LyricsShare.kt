package com.enes.liquidtube.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.enes.liquidtube.R
import com.enes.liquidtube.ui.components.SoftBlurTransformation
import com.enes.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lyric cards (1080×1350), Apple Music–style: a rounded card in the cover's colours,
 * floating on a blurred copy of the cover. Song on top, the lines big and separated,
 * a small wordmark at the bottom. The shared link starts the song at the lyric.
 */
object LyricsShare {

    /** [startMs]: where the first shared line is sung, so the link plays from there. */
    suspend fun share(context: Context, song: SongItem, lines: List<String>, startMs: Long?) {
        val file = withContext(Dispatchers.Default) {
            val cover = song.thumbnailUrl?.let { loadCover(context, it) }
            val card = render(context, song, lines, cover)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            context.externalCacheDir?.let { ext ->
                runCatching { File(ext, "lyrics.png").outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }
            File(dir, "lyrics.png").also { f -> f.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val link = "https://music.youtube.com/watch?v=${song.id}" + (startMs?.let { "&t=${(it / 1000).coerceAtLeast(0)}s" } ?: "")
        // Stories can't receive a link from other apps, so it's on the clipboard for a Link sticker.
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Song link", link))
        Toast.makeText(context, "Link copied — add it as a Link sticker", Toast.LENGTH_SHORT).show()

        val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "${song.title} — ${song.artists}\n$link")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share Lyrics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private suspend fun loadCover(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(com.enes.innertube.hiResThumbnail(url, 544))
            .allowHardware(false)
            .build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    private suspend fun render(context: Context, song: SongItem, lines: List<String>, cover: Bitmap?): Bitmap {
        val w = 1080
        val h = 1350
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)

        val palette = cover?.let { Palette.from(it).generate() }
        val vibrant = palette?.let { it.getVibrantColor(it.getDominantColor(DEFAULT)) } ?: DEFAULT
        val deep = palette?.let { it.getDarkMutedColor(it.getDarkVibrantColor(shade(vibrant, 0.45f))) } ?: shade(DEFAULT, 0.4f)

        // 1. Backdrop: the cover blurred smooth and dimmed (a plain gradient without a cover).
        if (cover != null) {
            val blurred = SoftBlurTransformation(width = 64, radius = 6).transform(cover, coil3.size.Size.ORIGINAL)
            drawCover(c, blurred, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawColor(Color.argb(95, 0, 0, 0))
        } else {
            c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), vibrant, deep, Shader.TileMode.CLAMP) })
        }

        val inter = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
        fun paint(size: Float, weight: Int, alpha: Int = 255) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = inter
            textSize = size
            color = Color.argb(alpha, 255, 255, 255)
            fontVariationSettings = "'wght' $weight, 'opsz' 32"
            letterSpacing = -0.015f
        }

        // 2. Measure the card's content so the card hugs it.
        val margin = 72f
        val inner = 64f
        val cardW = w - margin * 2
        val textW = (cardW - inner * 2).toInt()
        val art = 112f
        val headerH = art
        val lineGap = 46f
        val lyric = paint(78f, 800)
        fun layouts() = lines.map { line ->
            StaticLayout.Builder.obtain(line, 0, line.length, lyric, textW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.04f).build()
        }
        fun lyricsH(ls: List<StaticLayout>) = ls.sumOf { it.height } + lineGap * (ls.size - 1).coerceAtLeast(0)
        val footerH = 34f
        val maxCardH = h - margin * 2
        fun cardH(ls: List<StaticLayout>) = inner + headerH + 56f + lyricsH(ls) + 60f + footerH + inner
        var ls = layouts()
        while (cardH(ls) > maxCardH && lyric.textSize > 46f) {
            lyric.textSize -= 4f
            ls = layouts()
        }
        val ch = cardH(ls).coerceAtLeast(620f)
        val card = RectF(margin, (h - ch) / 2f, w - margin, (h + ch) / 2f)
        val radius = 56f

        // 3. The card: soft shadow, cover-coloured gradient, hairline rim.
        c.drawRoundRect(RectF(card.left, card.top + 24f, card.right, card.bottom + 24f), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0); maskFilter = BlurMaskFilter(48f, BlurMaskFilter.Blur.NORMAL) })
        c.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(card.left, card.top, card.right * 0.6f, card.bottom, shade(vibrant, 0.78f), shade(deep, 0.92f), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(card, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.argb(40, 255, 255, 255)
        })

        // 4. Header: cover + title + artist.
        val x = card.left + inner
        var y = card.top + inner
        if (cover != null) {
            val dst = RectF(x, y, x + art, y + art)
            c.save()
            c.clipPath(Path().apply { addRoundRect(dst, 18f, 18f, Path.Direction.CW) })
            c.drawBitmap(cover, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
        val titleX = x + art + 28f
        val titleW = (card.right - inner - titleX).toInt()
        val title = paint(38f, 700)
        val artist = paint(32f, 500, 185)
        c.drawText(ellipsize(song.title, title, titleW), titleX, y + art / 2f - 4f, title)
        c.drawText(ellipsize(song.artists, artist, titleW), titleX, y + art / 2f + 40f, artist)
        y += headerH + 56f

        // 5. The lyrics, each sung line its own block.
        ls.forEach { layout ->
            c.save()
            c.translate(x, y)
            layout.draw(c)
            c.restore()
            y += layout.height + lineGap
        }

        // 6. Wordmark.
        val brand = paint(28f, 700, 150)
        c.drawText("♪  LiquidTube", x, card.bottom - inner + 6f, brand)
        return out
    }

    private fun drawCover(c: Canvas, bmp: Bitmap, dst: RectF, paint: Paint) {
        val scale = maxOf(dst.width() / bmp.width, dst.height() / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        c.drawBitmap(bmp, null, RectF(dst.centerX() - dw / 2, dst.centerY() - dh / 2, dst.centerX() + dw / 2, dst.centerY() + dh / 2), paint)
    }

    private const val DEFAULT = 0xFF3A3A40.toInt()

    private fun shade(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt(), (Color.green(color) * factor).toInt(), (Color.blue(color) * factor).toInt(),
    )

    private fun ellipsize(text: String, paint: TextPaint, width: Int): String =
        android.text.TextUtils.ellipsize(text, paint, width.toFloat(), android.text.TextUtils.TruncateAt.END).toString()
}
