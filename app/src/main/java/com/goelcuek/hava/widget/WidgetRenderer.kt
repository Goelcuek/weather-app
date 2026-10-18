package com.goelcuek.hava.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.goelcuek.hava.MainActivity
import com.goelcuek.hava.R
import com.goelcuek.hava.data.Repo
import com.goelcuek.hava.data.Sky
import com.goelcuek.hava.data.Store
import com.goelcuek.hava.data.Weather
import com.goelcuek.hava.data.WeatherCodes
import com.goelcuek.hava.data.dayLabel
import com.goelcuek.hava.data.deg
import com.goelcuek.hava.data.hourLabel
import com.goelcuek.hava.data.pct

private data class WPalette(val bg: Int, val text: Int, val sub: Int, val accent: Int, val track: Int)

private fun palette(style: String, sky: Sky): WPalette = when (style) {
    "glass" -> WPalette(R.drawable.bg_glass, 0xFFFFFFFF.toInt(), 0xE0FFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x40FFFFFF)
    "dark" -> WPalette(R.drawable.bg_dark, 0xFFFFFFFF.toInt(), 0x99FFFFFF.toInt(), 0xFFFFB547.toInt(), 0x33FFFFFF)
    "light" -> WPalette(R.drawable.bg_light, 0xFF1C1C1E.toInt(), 0xFF86868B.toInt(), 0xFFFF8A1F.toInt(), 0x1F000000)
    else -> WPalette(sky.widgetBg, 0xFFFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x40FFFFFF)
}

object WidgetRenderer {

    fun build(ctx: Context, mgr: AppWidgetManager, id: Int, kind: WidgetKind): RemoteViews {
        val store = Store(ctx)
        val w = Repo.cached(ctx)
        val snap = w?.snapshot()
        val sky = snap?.let { WeatherCodes.sky(it.code, it.isDay) } ?: Sky.CLEAR_DAY
        val p = palette(store.widgetStyle, sky)

        val layout = when (kind) {
            WidgetKind.SMALL -> R.layout.widget_small
            WidgetKind.COMPACT -> R.layout.widget_compact
            WidgetKind.HOURLY -> R.layout.widget_hourly
            WidgetKind.LARGE -> R.layout.widget_large
        }
        val rv = RemoteViews(ctx.packageName, layout)
        rv.setInt(R.id.root, "setBackgroundResource", p.bg)
        rv.setOnClickPendingIntent(R.id.root, openApp(ctx))
        rv.setTextColor(R.id.place, p.text)
        rv.setTextColor(R.id.temp, p.text)
        rv.setTextColor(R.id.cond, p.sub)
        rv.setTextColor(R.id.hilo, p.sub)

        if (w == null || snap == null) {
            rv.setTextViewText(R.id.place, ctx.getString(R.string.app_name))
            rv.setTextViewText(R.id.temp, "--°")
            rv.setTextViewText(R.id.cond, ctx.getString(R.string.tap_to_setup))
            rv.setTextViewText(R.id.hilo, "")
            return rv
        }

        val today = w.today()
        rv.setTextViewText(R.id.place, store.placeName)
        rv.setTextViewText(R.id.temp, snap.temp.deg())
        rv.setImageViewResource(R.id.icon, WeatherCodes.icon(snap.code, snap.isDay))
        rv.setTextViewText(R.id.cond, ctx.getString(WeatherCodes.label(snap.code)))
        rv.setTextViewText(R.id.hilo, today?.let { "${it.max.deg()} / ${it.min.deg()}" } ?: "")

        val density = ctx.resources.displayMetrics.density
        val opts = mgr.getAppWidgetOptions(id)
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

        when (kind) {
            WidgetKind.SMALL -> Unit
            WidgetKind.COMPACT -> fillHours(ctx, rv, w, 4, R.layout.item_hour_mini, p)
            WidgetKind.HOURLY -> {
                fillHours(ctx, rv, w, 6, R.layout.item_hour, p)
                drawCurve(rv, w, 6, (if (minW > 0) minW else 300) - 32, 14, density, p)
            }
            WidgetKind.LARGE -> {
                rv.setTextColor(R.id.extra, p.sub)
                rv.setTextViewText(
                    R.id.extra,
                    ctx.getString(R.string.extra_line, w.current.feels.deg(), pct(ctx, w.current.humidity)),
                )
                fillHours(ctx, rv, w, 6, R.layout.item_hour, p)
                val inner = (if (minW > 0) minW else 300) - 32
                drawCurve(rv, w, 6, inner, 16, density, p)
                rv.setInt(R.id.divider, "setBackgroundColor", p.track)
                fillDays(ctx, rv, w, inner, density, p)
            }
        }
        return rv
    }

    private fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun fillHours(ctx: Context, rv: RemoteViews, w: Weather, count: Int, itemLayout: Int, p: WPalette) {
        rv.removeAllViews(R.id.hours)
        w.upcomingHours(count).forEachIndexed { i, h ->
            val item = RemoteViews(ctx.packageName, itemLayout)
            item.setTextViewText(R.id.h_time, if (i == 0) ctx.getString(R.string.now) else hourLabel(h.time))
            item.setTextColor(R.id.h_time, p.sub)
            item.setImageViewResource(R.id.h_icon, WeatherCodes.icon(h.code, h.isDay))
            item.setTextViewText(R.id.h_temp, h.temp.deg())
            item.setTextColor(R.id.h_temp, p.text)
            rv.addView(R.id.hours, item)
        }
    }

    private fun drawCurve(rv: RemoteViews, w: Weather, count: Int, widthDp: Int, heightDp: Int, density: Float, p: WPalette) {
        val temps = w.upcomingHours(count).map { it.temp }
        val wPx = (widthDp.coerceIn(120, 600) * density).toInt()
        val hPx = (heightDp * density).toInt()
        rv.setImageViewBitmap(R.id.curve, Charts.curve(temps, wPx, hPx, p.accent, density))
    }

    private fun fillDays(ctx: Context, rv: RemoteViews, w: Weather, innerDp: Int, density: Float, p: WPalette) {
        rv.removeAllViews(R.id.days)
        val days = w.upcomingDays().take(5)
        if (days.isEmpty()) return
        val gMin = days.minOf { it.min }
        val gMax = days.maxOf { it.max }
        val today = w.nowLocal().toLocalDate()
        val barW = ((innerDp.coerceIn(120, 600) * 0.32f) * density).toInt()
        val barH = (5 * density).toInt()
        days.forEach { d ->
            val item = RemoteViews(ctx.packageName, R.layout.item_day)
            item.setTextViewText(R.id.d_name, dayLabel(ctx, d.date, today))
            item.setTextColor(R.id.d_name, p.text)
            item.setImageViewResource(R.id.d_icon, WeatherCodes.icon(d.code, true))
            item.setTextViewText(R.id.d_pop, if (d.pop >= 20) pct(ctx, d.pop) else "")
            item.setTextViewText(R.id.d_min, d.min.deg())
            item.setTextColor(R.id.d_min, p.sub)
            item.setTextViewText(R.id.d_max, d.max.deg())
            item.setTextColor(R.id.d_max, p.text)
            item.setImageViewBitmap(R.id.d_bar, Charts.rangeBar(d.min, d.max, gMin, gMax, barW, barH, p.track))
            rv.addView(R.id.days, item)
        }
    }
}
