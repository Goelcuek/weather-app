package com.goelcuek.hava.data

import com.goelcuek.hava.R

/** Sky look used for the app background and the "Dynamic" widget style. */
enum class Sky(val widgetBg: Int, val top: Int, val bottom: Int) {
    CLEAR_DAY(R.drawable.bg_clear_day, 0xFF2E7FE8.toInt(), 0xFF5CC2F2.toInt()),
    CLEAR_NIGHT(R.drawable.bg_clear_night, 0xFF111A2E.toInt(), 0xFF2B4170.toInt()),
    CLOUDY_DAY(R.drawable.bg_cloudy_day, 0xFF55708F.toInt(), 0xFF8CA6C0.toInt()),
    CLOUDY_NIGHT(R.drawable.bg_cloudy_night, 0xFF232B38.toInt(), 0xFF46526A.toInt()),
    RAIN(R.drawable.bg_rain, 0xFF34495E.toInt(), 0xFF5F7A94.toInt()),
    THUNDER(R.drawable.bg_thunder, 0xFF262A40.toInt(), 0xFF4B4F78.toInt()),
    SNOW(R.drawable.bg_snow, 0xFF6F93B6.toInt(), 0xFFA9C3DC.toInt()),
    FOG(R.drawable.bg_fog, 0xFF5F6B78.toInt(), 0xFF98A3AE.toInt()),
}

object WeatherCodes {

    fun icon(code: Int, isDay: Boolean): Int = when (code) {
        0, 1 -> if (isDay) R.drawable.ic_w_sun else R.drawable.ic_w_moon
        2 -> if (isDay) R.drawable.ic_w_partly_day else R.drawable.ic_w_partly_night
        3 -> R.drawable.ic_w_overcast
        45, 48 -> R.drawable.ic_w_fog
        51, 53, 55 -> R.drawable.ic_w_drizzle
        56, 57, 66, 67 -> R.drawable.ic_w_sleet
        61, 63, 65 -> R.drawable.ic_w_rain
        80, 81, 82 -> if (isDay) R.drawable.ic_w_showers_day else R.drawable.ic_w_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_w_snow
        95, 96, 99 -> R.drawable.ic_w_thunder
        else -> R.drawable.ic_w_cloud
    }

    fun label(code: Int): Int = when (code) {
        0 -> R.string.c_clear
        1 -> R.string.c_mainly_clear
        2 -> R.string.c_partly
        3 -> R.string.c_overcast
        45, 48 -> R.string.c_fog
        51, 53, 55 -> R.string.c_drizzle
        56, 57 -> R.string.c_freezing_drizzle
        61 -> R.string.c_rain_light
        63 -> R.string.c_rain
        65 -> R.string.c_rain_heavy
        66, 67 -> R.string.c_freezing_rain
        71, 77 -> R.string.c_snow_light
        73 -> R.string.c_snow
        75 -> R.string.c_snow_heavy
        80, 81, 82 -> R.string.c_showers
        85, 86 -> R.string.c_snow_showers
        95 -> R.string.c_thunder
        96, 99 -> R.string.c_hail
        else -> R.string.c_overcast
    }

    fun sky(code: Int, isDay: Boolean): Sky = when (code) {
        0, 1 -> if (isDay) Sky.CLEAR_DAY else Sky.CLEAR_NIGHT
        2, 3 -> if (isDay) Sky.CLOUDY_DAY else Sky.CLOUDY_NIGHT
        45, 48 -> Sky.FOG
        in 51..67, 80, 81, 82 -> Sky.RAIN
        in 71..77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.THUNDER
        else -> if (isDay) Sky.CLOUDY_DAY else Sky.CLOUDY_NIGHT
    }
}
