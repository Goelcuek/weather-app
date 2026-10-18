package com.goelcuek.hava.data

import android.content.Context
import android.location.Geocoder
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

object Api {

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Hava-Android/1.0")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IOException("HTTP $code ${body.take(160)}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    fun forecast(lat: Double, lon: Double, fahrenheit: Boolean): String {
        val sb = StringBuilder("https://api.open-meteo.com/v1/forecast")
        sb.append("?latitude=").append(lat.toString())
        sb.append("&longitude=").append(lon.toString())
        sb.append("&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m")
        sb.append("&hourly=temperature_2m,weather_code,is_day,precipitation_probability")
        sb.append("&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max")
        sb.append("&timezone=auto&forecast_days=7")
        if (fahrenheit) sb.append("&temperature_unit=fahrenheit")
        return get(sb.toString())
    }

    fun search(query: String, lang: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
            URLEncoder.encode(query, "UTF-8") + "&count=10&format=json&language=" + lang
        val arr = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val detail = listOf(r.optString("admin1"), r.optString("country"))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
            out += Place(r.getString("name"), detail, r.getDouble("latitude"), r.getDouble("longitude"))
        }
        return out
    }

    fun reverseName(ctx: Context, lat: Double, lon: Double): String? {
        if (Geocoder.isPresent()) {
            try {
                @Suppress("DEPRECATION")
                val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                val n = a?.locality ?: a?.subAdminArea ?: a?.adminArea
                if (!n.isNullOrBlank()) return n
            } catch (_: Exception) {
            }
        }
        return try {
            val o = JSONObject(
                get(
                    "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon" +
                        "&localityLanguage=" + Locale.getDefault().language
                )
            )
            listOf("locality", "city", "principalSubdivision").map { o.optString(it) }.firstOrNull { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
