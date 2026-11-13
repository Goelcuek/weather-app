package com.goelcuek.hava.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.goelcuek.hava.MainActivity
import com.goelcuek.hava.R
import com.goelcuek.hava.data.Repo
import com.goelcuek.hava.data.Store
import com.goelcuek.hava.data.WeatherCodes
import com.goelcuek.hava.data.deg
import com.goelcuek.hava.data.hourLabel

/**
 * Weather card for the lock screen. Honor's MagicOS lock screen does not host third-party
 * app widgets, so this is a silent, ongoing notification with public lock-screen visibility,
 * using the same icons and hourly strip as the home-screen widgets.
 */
object LockCard {
    private const val CHANNEL = "lock_weather"
    private const val ID = 7001

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)

    fun update(ctx: Context) {
        val store = Store(ctx)
        if (!store.lockCard) {
            cancel(ctx)
            return
        }
        if (!canPost(ctx)) return
        val w = Repo.cached(ctx) ?: return
        val snap = w.snapshot()
        val today = w.today()

        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, ctx.getString(R.string.lock_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = ctx.getString(R.string.lock_channel_desc)
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
            )
        }

        val cond = ctx.getString(WeatherCodes.label(snap.code))
        val hilo = today?.let { "${it.max.deg()} / ${it.min.deg()}" } ?: ""
        val icon = WeatherCodes.icon(snap.code, snap.isDay)

        fun header(rv: RemoteViews) {
            rv.setImageViewResource(R.id.icon, icon)
            rv.setTextViewText(R.id.temp, snap.temp.deg())
            rv.setTextViewText(R.id.place, store.placeName)
            rv.setTextViewText(R.id.cond, if (hilo.isEmpty()) cond else "$cond · $hilo")
        }

        fun hours(rv: RemoteViews, count: Int) {
            rv.removeAllViews(R.id.hours)
            w.upcomingHours(count).forEachIndexed { i, h ->
                val item = RemoteViews(ctx.packageName, R.layout.item_hour_notif)
                item.setTextViewText(R.id.h_time, if (i == 0) ctx.getString(R.string.now) else hourLabel(h.time))
                item.setImageViewResource(R.id.h_icon, WeatherCodes.icon(h.code, h.isDay))
                item.setTextViewText(R.id.h_temp, h.temp.deg())
                rv.addView(R.id.hours, item)
            }
        }

        val small = RemoteViews(ctx.packageName, R.layout.notif_small).also { header(it); hours(it, 4) }
        val big = RemoteViews(ctx.packageName, R.layout.notif_big).also {
            header(it)
            hours(it, 6)
            val d = ctx.resources.displayMetrics.density
            val temps = w.upcomingHours(6).map { h -> h.temp }
            it.setImageViewBitmap(R.id.curve, Charts.curve(temps, (300 * d).toInt(), (16 * d).toInt(), 0xFFFF9F0A.toInt(), d))
        }

        val open = PendingIntent.getActivity(
            ctx, 1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle("${snap.temp.deg()}  ${store.placeName}")
            .setContentText(cond)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(small)
            .setCustomBigContentView(big)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(ID, n)
        } catch (_: SecurityException) {
        }
    }
}

/** Re-posts the lock card and kicks a refresh after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LockCard.update(context)
        RefreshWorker.schedule(context)
        RefreshWorker.runNow(context)
    }
}
