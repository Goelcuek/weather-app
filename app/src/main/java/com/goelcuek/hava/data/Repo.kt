package com.goelcuek.hava.data

import android.content.Context
import android.location.Location
import com.goelcuek.hava.R
import com.goelcuek.hava.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object Repo {
    private val mutex = Mutex()

    fun cached(ctx: Context): Weather? =
        Store(ctx).cachedJson?.let { runCatching { WeatherParser.parse(it) }.getOrNull() }

    suspend fun refresh(ctx: Context, allowLocation: Boolean): Result<Weather> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            val s = Store(app)
            s.lastAttempt = System.currentTimeMillis()
            val result = runCatching {
                if (s.useGps && allowLocation) {
                    val loc = LocationHelper.current(app)
                    if (loc != null) {
                        val moved = !s.hasLocation || s.placeName.isBlank() || distance(s.lat, s.lon, loc) > 1500f
                        s.lat = loc.latitude
                        s.lon = loc.longitude
                        if (moved) {
                            s.placeName = Api.reverseName(app, loc.latitude, loc.longitude)
                                ?: s.placeName.ifBlank { app.getString(R.string.my_location) }
                        }
                    }
                }
                if (!s.hasLocation) s.setLocation(41.0082, 28.9784, "İstanbul")
                if (s.placeName.isBlank()) s.placeName = app.getString(R.string.my_location)

                val json = Api.forecast(s.lat, s.lon, s.unit == "f")
                val w = WeatherParser.parse(json)
                s.cachedJson = json
                s.lastFetch = System.currentTimeMillis()
                w
            }
            WidgetUpdater.updateAll(app)
            result
        }
    }

    private fun distance(lat: Double, lon: Double, loc: Location): Float {
        val out = FloatArray(1)
        Location.distanceBetween(lat, lon, loc.latitude, loc.longitude, out)
        return out[0]
    }
}
