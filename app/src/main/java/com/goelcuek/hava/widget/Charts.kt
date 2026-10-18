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
