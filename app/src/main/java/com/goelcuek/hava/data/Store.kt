package com.goelcuek.hava.data

import android.content.Context

class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("hava", Context.MODE_PRIVATE)

    val hasLocation: Boolean get() = p.contains("lat")

    var lat: Double
        get() = p.getString("lat", null)?.toDoubleOrNull() ?: 41.0082
        set(v) = p.edit().putString("lat", v.toString()).apply()

    var lon: Double
        get() = p.getString("lon", null)?.toDoubleOrNull() ?: 28.9784
        set(v) = p.edit().putString("lon", v.toString()).apply()

    var placeName: String
        get() = p.getString("place", "") ?: ""
        set(v) = p.edit().putString("place", v).apply()

    var useGps: Boolean
        get() = p.getBoolean("gps", true)
        set(v) = p.edit().putBoolean("gps", v).apply()

    /** "c" or "f" */
    var unit: String
        get() = p.getString("unit", "c") ?: "c"
        set(v) = p.edit().putString("unit", v).apply()

    /** "glass", "dynamic", "dark" or "light" */
    var widgetStyle: String
        get() = p.getString("style", "glass") ?: "glass"
        set(v) = p.edit().putString("style", v).apply()

    var cachedJson: String?
        get() = p.getString("json", null)
        set(v) = p.edit().putString("json", v).apply()

    var lastFetch: Long
        get() = p.getLong("fetch", 0L)
        set(v) = p.edit().putLong("fetch", v).apply()

    var lastAttempt: Long
        get() = p.getLong("attempt", 0L)
        set(v) = p.edit().putLong("attempt", v).apply()

    fun setLocation(lat: Double, lon: Double, name: String) {
        p.edit()
            .putString("lat", lat.toString())
            .putString("lon", lon.toString())
            .putString("place", name)
            .apply()
    }
}
