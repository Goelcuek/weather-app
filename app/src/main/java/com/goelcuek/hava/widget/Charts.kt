package com.goelcuek.hava.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader

/** Small bitmaps drawn for RemoteViews (widgets can't host custom views). */
object Charts {

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun curvePath(pts: List<PointF>): Path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val cx = (pts[i - 1].x + pts[i].x) / 2f
            cubicTo(cx, pts[i - 1].y, cx, pts[i].y, pts[i].x, pts[i].y)
        }
    }

    /** Smooth temperature curve; point i sits at the centre of column i of n equal columns. */
    fun curve(temps: List<Double>, w: Int, h: Int, color: Int, density: Float): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(2), h.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        if (temps.size < 2) return bmp
        val c = Canvas(bmp)
        val r = 2.4f * density
        val pad = r * 1.3f + 1f * density
        val mn = temps.min()
        val mx = temps.max()
        val span = (mx - mn).takeIf { it > 0.01 } ?: 1.0
        val n = temps.size
        val pts = temps.mapIndexed { i, t ->
            val x = (i + 0.5f) / n * w
            val y = pad + (1f - ((t - mn) / span).toFloat()) * (h - 2 * pad)
            PointF(x, y)
        }

        val fill = curvePath(pts).apply {
            lineTo(pts.last().x, h.toFloat())
            lineTo(pts.first().x, h.toFloat())
            close()
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), withAlpha(color, 80), withAlpha(color, 0), Shader.TileMode.CLAMP)
        }
        c.drawPath(fill, fillPaint)

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
            strokeCap = Paint.Cap.ROUND
            this.color = withAlpha(color, 230)
        }
        c.drawPath(curvePath(pts), stroke)

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        pts.forEachIndexed { i, p -> c.drawCircle(p.x, p.y, if (i == 0) r * 1.3f else r * 0.85f, dot) }
        return bmp
    }

    /**
     * Liquid-glass overlay as an alpha-only bitmap (tinted white by the ImageView):
     * a soft top sheen, a blurred inner glow and a hairline rim. Rim brightness depends on
     * the angle around the centre (light from the top-left, reflection bottom-right), so it
     * flows smoothly through the corners instead of stopping at straight segments.
     */
    fun glass(wDp: Int, hDp: Int, density: Float, radiusDp: Float = 26f): Bitmap {
        // Keep the bitmap small enough for a widget update (alpha-only: 1 byte per pixel).
        val maxPixels = 520_000f
        val scale = minOf(density, kotlin.math.sqrt(maxPixels / (wDp * hDp).coerceAtLeast(1)))
        val w = (wDp * scale).toInt().coerceAtLeast(8)
        val h = (hDp * scale).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp)
        val r = minOf(radiusDp * scale, w / 2f, h / 2f)
        val stroke = 0.8f * scale
        val half = stroke / 2f

        val outline = Path().apply {
            addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW)
        }
        c.save()
        c.clipPath(outline)

        // Sheen: smooth vertical falloff, no hard bands.
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(a(0.05f), a(0.02f), a(0.0f), a(0.015f)),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), sheen)

        // Inner glow hugging the edge, lit the same way as the rim.
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f * scale
            maskFilter = android.graphics.BlurMaskFilter(2f * scale, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        drawLitRoundRect(c, RectF(0f, 0f, w.toFloat(), h.toFloat()), r, glow, 0.15f)
        c.restore()

        // Hairline rim.
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        drawLitRoundRect(c, RectF(half, half, w - half, h - half), r - half, rim, 1f)
        return bmp
    }

    /**
     * Brightness of an edge whose outward normal points at [thetaDeg]
     * (degrees clockwise from +x, screen coordinates). Key light from the top-left (225°),
     * weaker reflection from the bottom-right (45°).
     */
    private fun light(thetaDeg: Double, k: Float): Float {
        val dot = kotlin.math.cos(Math.toRadians(thetaDeg - 225.0))
        val pos = if (dot > 0) dot * dot else 0.0
        val neg = if (dot < 0) dot * dot else 0.0
        return ((0.16 + 0.40 * pos + 0.18 * neg) * k).toFloat()
    }

    /**
     * Strokes a rounded rect lit by its surface normal: straight edges get a constant
     * brightness (so they look even along their whole length) and each corner arc gets a
     * SweepGradient centred on that arc, so the light flows through the corners seamlessly.
     */
    private fun drawLitRoundRect(c: Canvas, rc: RectF, r: Float, paint: Paint, k: Float) {
        paint.strokeCap = Paint.Cap.BUTT
        paint.shader = null
        fun edge(theta: Double, x0: Float, y0: Float, x1: Float, y1: Float) {
            if (x1 - x0 < 0.5f && y1 - y0 < 0.5f) return
            paint.color = a(light(theta, k))
            c.drawLine(x0, y0, x1, y1, paint)
        }
        edge(270.0, rc.left + r, rc.top, rc.right - r, rc.top)
        edge(90.0, rc.left + r, rc.bottom, rc.right - r, rc.bottom)
        edge(180.0, rc.left, rc.top + r, rc.left, rc.bottom - r)
        edge(0.0, rc.right, rc.top + r, rc.right, rc.bottom - r)

        val n = 25
        val colors = IntArray(n) { a(light(it * 15.0, k)) }
        val stops = FloatArray(n) { it / (n - 1f) }
        val corners = listOf(
            Triple(rc.left + r, rc.top + r, 180f),
            Triple(rc.right - r, rc.top + r, 270f),
            Triple(rc.right - r, rc.bottom - r, 0f),
            Triple(rc.left + r, rc.bottom - r, 90f),
        )
        paint.color = 0xFFFFFFFF.toInt()
        for ((cx, cy, start) in corners) {
            paint.shader = android.graphics.SweepGradient(cx, cy, colors, stops)
            c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, 90f, false, paint)
        }
        paint.shader = null
    }

    private fun a(alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or 0xFFFFFF

    /** Day temperature range bar (min..max) on a week-wide track. */
    fun rangeBar(lo: Double, hi: Double, gMin: Double, gMax: Double, w: Int, h: Int, track: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(2), h.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = h / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track }
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, p)
        val span = (gMax - gMin).takeIf { it > 0.01 } ?: 1.0
        var x0 = ((lo - gMin) / span).toFloat() * w
        var x1 = ((hi - gMin) / span).toFloat() * w
        if (x1 - x0 < h) {
            val mid = (x0 + x1) / 2f
            x0 = (mid - h / 2f).coerceAtLeast(0f)
            x1 = (x0 + h).coerceAtMost(w.toFloat())
        }
        p.shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(0xFF6EC1FF.toInt(), 0xFF9BE07A.toInt(), 0xFFFFCF5C.toInt(), 0xFFFF8A57.toInt()),
            null, Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(RectF(x0, 0f, x1, h.toFloat()), r, r, p)
        return bmp
    }
}
