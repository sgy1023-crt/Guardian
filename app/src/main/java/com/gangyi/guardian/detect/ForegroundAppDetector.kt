package com.gangyi.guardian.detect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** 基于 UsageStatsManager 的前台应用检测（主引擎）。 */
object ForegroundAppDetector {

    /**
     * 返回最近一段时间内最后进入前台的应用包名。
     * 用事件流（queryEvents）而非聚合统计，时效性更好。
     * 时间窗口设为 60s，避免权限刚授予/系统刚启动时返回空导致首启不触发。
     */
    fun getForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        val begin = end - LOOKBACK_MS
        val events = usm.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }
        // 事件流没拿到（极端情况：刚开机/系统没采集到），兜底用聚合统计取最长时间在前台的包
        if (lastPackage == null) {
            lastPackage = usm.queryAndAggregateUsageStats(begin, end)
                .values
                .maxByOrNull { it.totalTimeInForeground }
                ?.packageName
        }
        return lastPackage
    }

    private const val LOOKBACK_MS = 60_000L
}
