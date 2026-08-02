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
        if (found != null) {
            lastKnown = found
            lastConfirmedAt = end
        }
        return lastKnown
    }

    /**
     * 上次结果是否"刚由系统事件确认过"（而非沿用缓存）。
     *
     * 弹窗判定用缓存值没问题——用户没切 App 就该继续提醒。
     * 但**封锁**必须更严：万一缓存里是个过期包名，就会把触发计数记到错的 App 头上，
     * 甚至封错对象。宁可漏记一次，也不能封错——所以升级封锁前先问这里。
     */
    fun isFreshlyConfirmed(): Boolean =
        lastConfirmedAt > 0 && System.currentTimeMillis() - lastConfirmedAt <= FRESH_WINDOW_MS

    @Volatile
    private var lastConfirmedAt = 0L

    private const val LOOKBACK_MS = 60_000L
    private const val COLD_LOOKBACK_MS = 30 * 60_000L

    /** 事件确认后多久内仍算"新鲜"。略大于事件回看窗口，避免边界抖动 */
    private const val FRESH_WINDOW_MS = 70_000L
}
