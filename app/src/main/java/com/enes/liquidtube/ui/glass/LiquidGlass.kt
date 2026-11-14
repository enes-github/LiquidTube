package com.enes.liquidtube.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min

/*
 * Liquid Glass, Compose edition.
 *
 * 1. The page content is recorded once per frame into a GraphicsLayer (the backdrop).
 * 2. Each glass element re-draws the slice of that backdrop behind itself into its own
 *    layer, with a RenderEffect chain applied: blur → AGSL lens shader.
 * 3. The lens bends the image near the shape's rim (strongest at the edge, zero in the
 *    middle) with a hint of chromatic dispersion; that's the "liquid" refraction.
 * 4. A tint, a top sheen and a specular rim stroke finish the material.
 *
 * Runtime shaders need API 33; on 31–32 the glass falls back to blur only.
 */

private const val LENS_SHADER = """
uniform shader content;
uniform float2 origin;
uniform float2 size;
uniform float radius;
uniform float band;
uniform float amount;
uniform float chroma;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - origin - hs;
    float d = sdRoundRect(p, hs, radius);
    if (d > 0.0 || d < -band) {
        return content.eval(coord);
    }
    float2 n = normalize(float2(
        sdRoundRect(p + float2(1.0, 0.0), hs, radius) - sdRoundRect(p - float2(1.0, 0.0), hs, radius),
        sdRoundRect(p + float2(0.0, 1.0), hs, radius) - sdRoundRect(p - float2(0.0, 1.0), hs, radius)
    ));
    float x = 1.0 + d / band;            // 0 inside the band edge → 1 at the rim
    float bend = amount * x * x * x;     // ease-in: flat centre, strong rim
    float2 o = n * bend;
    half4 g = content.eval(coord - o);
    half r = content.eval(coord - o * (1.0 + chroma)).r;
    half b = content.eval(coord - o * (1.0 - chroma)).b;
    return half4(r, g.g, b, g.a);
}
"""

/** Holds the recorded page content that glass elements refract. */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var background by mutableStateOf(Color.Black)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Marks the content glass elements should see through (e.g. the NavHost). */
fun Modifier.glassSource(backdrop: GlassBackdrop, background: Color): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.background = background
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

@Stable
data class GlassStyle(
    val tint: Color,
    val blur: Dp = 14.dp,
    val refraction: Dp = 22.dp,     // max displacement at the rim
    val band: Dp = 26.dp,           // how far in from the edge the lens reaches
    val chroma: Float = 0.12f,
    val sheen: Float = 0.10f,
    val rim: Float = 0.55f,
    /** Hairline outer edge that separates the glass from busy content. */
    val edge: Color = Color.Transparent,
)

/**
 * Real Liquid Glass material for floating chrome (tab bar, mini-player, buttons).
 * [corner] is clamped to a capsule automatically.
 */
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop,
    style: GlassStyle,
    corner: Dp = 1000.dp,
): Modifier = composed {
    val layer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    val refract = com.enes.liquidtube.ui.theme.LocalGlassRefraction.current
    val shader = remember(style.refraction, refract) {
        if (refract && Build.VERSION.SDK_INT >= 33 && style.refraction > 0.dp) RuntimeShader(LENS_SHADER) else null
    }

    this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawBehind {
            val pad = (style.blur * 3).toPx()
            val r = min(corner.toPx(), size.minDimension / 2f)
            val rec = IntSize((size.width + pad * 2).toInt().coerceAtLeast(1), (size.height + pad * 2).toInt().coerceAtLeast(1))
            val dx = position.x - backdrop.origin.x
            val dy = position.y - backdrop.origin.y

            layer.record(size = rec) {
                drawRect(backdrop.background)
                translate(-dx + pad, -dy + pad) { drawLayer(backdrop.layer) }
            }

            val blurPx = style.blur.toPx().coerceAtLeast(0.1f)
            val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            layer.renderEffect = if (shader != null) {
                shader.setFloatUniform("origin", pad, pad)
                shader.setFloatUniform("size", size.width, size.height)
                shader.setFloatUniform("radius", r)
                shader.setFloatUniform("band", min(style.band.toPx(), size.minDimension / 2f))
                shader.setFloatUniform("amount", style.refraction.toPx())
                shader.setFloatUniform("chroma", style.chroma)
                RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(shader, "content"),
                    blur,
                ).asComposeRenderEffect()
            } else {
                blur.asComposeRenderEffect()
            }

            val shape = Path().apply {
                addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r)))
            }
            clipPath(shape) {
                translate(-pad, -pad) { drawLayer(layer) }
                drawRect(style.tint)
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = style.sheen), Color.Transparent),
                        endY = size.height * 0.6f,
                    )
                )
            }
            drawSpecularRim(r, style.rim)
            if (style.edge.alpha > 0f) {
                val e = 0.75.dp.toPx()
                drawRoundRect(
                    color = style.edge,
                    topLeft = Offset(e / 2, e / 2),
                    size = Size(size.width - e, size.height - e),
                    cornerRadius = CornerRadius(r - e / 2),
                    style = Stroke(e),
                )
            }
        }
}

/**
 * iOS 26 scroll-edge effect: the content scrolled under a bar is blurred and tinted,
 * and the effect melts away over [fade] at the bottom instead of ending in a hard line.
 * The bar must be a *sibling* of the [glassSource] it samples, never inside it.
 */
fun Modifier.scrollEdge(
    backdrop: GlassBackdrop,
    tint: Color,
    blur: Dp = 22.dp,
    fade: Dp = 22.dp,
): Modifier = composed {
    val layer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { position = it.positionInRoot() }
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            val pad = (blur * 3).toPx()
            val rec = IntSize((size.width + pad * 2).toInt().coerceAtLeast(1), (size.height + pad * 2).toInt().coerceAtLeast(1))
            val dx = position.x - backdrop.origin.x
            val dy = position.y - backdrop.origin.y
            layer.record(size = rec) {
                drawRect(backdrop.background)
                translate(-dx + pad, -dy + pad) { drawLayer(backdrop.layer) }
            }
            val b = blur.toPx().coerceAtLeast(0.1f)
            layer.renderEffect = RenderEffect.createBlurEffect(b, b, Shader.TileMode.CLAMP).asComposeRenderEffect()
            translate(-pad, -pad) { drawLayer(layer) }
            drawRect(tint)
            val f = fade.toPx().coerceAtMost(size.height)
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Black,
                    ((size.height - f) / size.height).coerceIn(0f, 1f) to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
            drawContent()
        }
}

/** A full-size blurred copy of [backdrop], for dimming the screen behind menus and sheets. */
fun Modifier.blurredBackdrop(backdrop: GlassBackdrop, blur: Dp): Modifier = composed {
    val layer = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawBehind {
            val pad = (blur * 2).toPx()
            val rec = IntSize((size.width + pad * 2).toInt().coerceAtLeast(1), (size.height + pad * 2).toInt().coerceAtLeast(1))
            layer.record(size = rec) {
                drawRect(backdrop.background)
                translate(backdrop.origin.x - position.x + pad, backdrop.origin.y - position.y + pad) { drawLayer(backdrop.layer) }
            }
            val b = blur.toPx().coerceAtLeast(0.1f)
            layer.renderEffect = RenderEffect.createBlurEffect(b, b, Shader.TileMode.CLAMP).asComposeRenderEffect()
            translate(-pad, -pad) { drawLayer(layer) }
        }
}

/** The bright, angle-dependent edge highlight of glass. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpecularRim(radius: Float, strength: Float) {
    val w = 1.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = strength),
            0.35f to Color.White.copy(alpha = strength * 0.12f),
            0.65f to Color.White.copy(alpha = strength * 0.08f),
            1f to Color.White.copy(alpha = strength * 0.6f),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius - w / 2),
        style = Stroke(w),
    )
}

/**
 * Lightweight glass for small controls drawn *inside* content (back buttons over
 * artwork, Now Playing chips): translucent fill + specular rim, no backdrop needed.
 */
fun Modifier.glassChip(tint: Color = Color.White.copy(alpha = 0.16f), corner: Dp = 1000.dp, rim: Float = 0.5f): Modifier =
    this
        .clip(RoundedCornerShape(corner))
        .background(tint)
        .drawBehind {
            val r = min(corner.toPx(), size.minDimension / 2f)
            drawRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), endY = size.height * 0.6f)
            )
            drawSpecularRim(r, rim)
        }
