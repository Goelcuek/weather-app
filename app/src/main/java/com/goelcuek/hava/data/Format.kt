package com.goelcuek.hava.data

import android.content.Context
import com.goelcuek.hava.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

fun Double.deg(): String = "${roundToInt()}°"

fun hourLabel(t: LocalDateTime): String = String.format(Locale.US, "%02d:00", t.hour)

fun timeLabel(t: LocalDateTime?): String =
    if (t == null) "--" else String.format(Locale.US, "%02d:%02d", t.hour, t.minute)

fun dayLabel(ctx: Context, d: LocalDate, today: LocalDate, full: Boolean = false): String {
    if (d == today) return ctx.getString(R.string.today)
    val loc = Locale.getDefault()
    return d.dayOfWeek.getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, loc)
        .replaceFirstChar { it.titlecase(loc) }
}

fun pct(ctx: Context, v: Int): String = ctx.getString(R.string.pct, v)
