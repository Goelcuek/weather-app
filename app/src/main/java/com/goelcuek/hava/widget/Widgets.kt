package com.goelcuek.hava.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import com.goelcuek.hava.data.Store

enum class WidgetKind { SMALL, COMPACT, HOURLY, LARGE }

abstract class BaseWidget(private val kind: WidgetKind) : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.update(context, appWidgetManager, it, kind) }
        RefreshWorker.schedule(context)
        val s = Store(context)
        val now = System.currentTimeMillis()
        // Re-fetch if data is stale, but never more often than every 15 min (avoids update loops).
        if (now - s.lastFetch > 45 * 60_000L && now - s.lastAttempt > 15 * 60_000L) {
            RefreshWorker.runNow(context)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetUpdater.update(context, appWidgetManager, appWidgetId, kind)
    }

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
    }
}

class SmallWidget : BaseWidget(WidgetKind.SMALL)
class CompactWidget : BaseWidget(WidgetKind.COMPACT)
class HourlyWidget : BaseWidget(WidgetKind.HOURLY)
class LargeWidget : BaseWidget(WidgetKind.LARGE)

object WidgetUpdater {
    val providers: Map<WidgetKind, Class<*>> = mapOf(
        WidgetKind.SMALL to SmallWidget::class.java,
        WidgetKind.COMPACT to CompactWidget::class.java,
        WidgetKind.HOURLY to HourlyWidget::class.java,
        WidgetKind.LARGE to LargeWidget::class.java,
    )

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        providers.forEach { (kind, cls) ->
            mgr.getAppWidgetIds(ComponentName(ctx, cls)).forEach { update(ctx, mgr, it, kind) }
        }
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, kind: WidgetKind) {
        try {
            mgr.updateAppWidget(id, WidgetRenderer.build(ctx, mgr, id, kind))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
