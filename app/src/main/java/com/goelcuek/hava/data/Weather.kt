package com.goelcuek.hava.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class Current(
    val time: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val code: Int,
    val isDay: Boolean,
    val wind: Double,
)

data class Hour(val time: LocalDateTime, val temp: Double, val code: Int, val isDay: Boolean, val pop: Int)

data class Day(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val pop: Int,
    val uv: Double,
)

data class Snapshot(val temp: Double, val code: Int, val isDay: Boolean)

data class Weather(
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val utcOffset: Int,
) {
    /** "Now" in the forecast location's local time. */
    fun nowLocal(): LocalDateTime =
        LocalDateTime.ofEpochSecond(System.currentTimeMillis() / 1000 + utcOffset, 0, ZoneOffset.UTC)

    fun upcomingHours(count: Int): List<Hour> {
        val start = nowLocal().truncatedTo(ChronoUnit.HOURS)
        return hours.filter { !it.time.isBefore(start) }.take(count)
    }

    fun upcomingDays(): List<Day> {
        val today = nowLocal().toLocalDate()
        return days.filter { !it.date.isBefore(today) }
    }

    fun today(): Day? = upcomingDays().firstOrNull()

    /** Current conditions; falls back to the hourly forecast when the cache is old. */
    fun snapshot(): Snapshot {
        val age = Duration.between(current.time, nowLocal()).toMinutes()
        if (age in -90..90) return Snapshot(current.temp, current.code, current.isDay)
        val h = upcomingHours(1).firstOrNull() ?: return Snapshot(current.temp, current.code, current.isDay)
        return Snapshot(h.temp, h.code, h.isDay)
    }
}

object WeatherParser {
    fun parse(json: String): Weather {
        val o = JSONObject(json)
        val offset = o.optInt("utc_offset_seconds", 0)

        val c = o.getJSONObject("current")
        val temp = c.getDouble("temperature_2m")
        val current = Current(
            time = LocalDateTime.parse(c.getString("time")),
            temp = temp,
            feels = c.optDouble("apparent_temperature", temp).nanTo(temp),
            humidity = c.optInt("relative_humidity_2m", 0),
            code = c.optInt("weather_code", 0),
            isDay = c.optInt("is_day", 1) == 1,
            wind = c.optDouble("wind_speed_10m", 0.0).nanTo(0.0),
        )

        val h = o.getJSONObject("hourly")
        val hTime = h.getJSONArray("time")
        val hTemp = h.getJSONArray("temperature_2m")
        val hCode = h.getJSONArray("weather_code")
        val hDay = h.optJSONArray("is_day") ?: JSONArray()
        val hPop = h.optJSONArray("precipitation_probability") ?: JSONArray()
        val hours = ArrayList<Hour>(hTime.length())
        for (i in 0 until hTime.length()) {
            if (hTemp.isNull(i)) continue
            hours += Hour(
                time = LocalDateTime.parse(hTime.getString(i)),
                temp = hTemp.getDouble(i),
                code = hCode.optInt(i, 0),
                isDay = hDay.optInt(i, 1) == 1,
                pop = hPop.optInt(i, 0),
            )
        }

        val d = o.getJSONObject("daily")
        val dTime = d.getJSONArray("time")
        val dCode = d.getJSONArray("weather_code")
        val dMax = d.getJSONArray("temperature_2m_max")
        val dMin = d.getJSONArray("temperature_2m_min")
        val dRise = d.optJSONArray("sunrise") ?: JSONArray()
        val dSet = d.optJSONArray("sunset") ?: JSONArray()
        val dPop = d.optJSONArray("precipitation_probability_max") ?: JSONArray()
        val dUv = d.optJSONArray("uv_index_max") ?: JSONArray()
        val days = ArrayList<Day>(dTime.length())
        for (i in 0 until dTime.length()) {
            if (dMax.isNull(i) || dMin.isNull(i)) continue
            days += Day(
                date = LocalDate.parse(dTime.getString(i)),
                code = dCode.optInt(i, 0),
                max = dMax.getDouble(i),
                min = dMin.getDouble(i),
                sunrise = dRise.optString(i, "").takeIf { it.length >= 16 }?.let { LocalDateTime.parse(it) },
                sunset = dSet.optString(i, "").takeIf { it.length >= 16 }?.let { LocalDateTime.parse(it) },
                pop = dPop.optInt(i, 0),
                uv = dUv.optDouble(i, 0.0).nanTo(0.0),
            )
        }
        return Weather(current, hours, days, offset)
    }

    private fun Double.nanTo(v: Double) = if (isNaN()) v else this
}
