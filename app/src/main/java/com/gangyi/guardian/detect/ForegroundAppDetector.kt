package com.gangyi.guardian.detect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/** 基于 UsageStatsManager 的前台应用检测（主引擎）。 */
object ForegroundAppDetector {

    // 窗口内没有新切换事件时沿用它——用户没切 App 就意味着还停在这个包里。
    @Volatile
    private var lastKnown: String? = null

    /**
     * 返回当前前台应用包名。
     * 用事件流（queryEvents）取最后一个进入前台的包；窗口内没有事件则沿用上次结果。
     * 绝不用 queryAndAggregateUsageStats 兜底——那返回的是"今天累计用得最久的 App"，
     * 在同一 App 停留超过窗口时长后会张冠李戴（误弹窗/误收弹窗）。
     */
    fun getForegroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val end = System.currentTimeMillis()
        // 冷启动（进程刚起、还没有缓存）用长窗口找回当前前台 App
        val lookback = if (lastKnown == null) COLD_LOOKBACK_MS else LOOKBACK_MS
        val events = usm.queryEvents(end - lookback, end)
        val event = UsageEvents.Event()
        var found: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                found = event.packageName
            }
        }
        if (found != null) lastKnown = found
        return lastKnown
    }

    private const val LOOKBACK_MS = 60_000L
    private const val COLD_LOOKBACK_MS = 30 * 60_000L
}
