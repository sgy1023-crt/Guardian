package com.gangyi.guardian.data

import com.gangyi.guardian.data.db.TriggerLog

/**
 * 一次"冲动"。
 *
 * 为什么要归并：用户点了「退出」→ 回桌面 → 3 秒后又点开同一个 App，会立刻再弹一次
 * （这是对的，每次重新打开都是一次新的决定）。但如果每一次弹窗都算一次"今日停顿"，
 * 一次挣扎能被记成 5 次，首页那个大数字就虚高了，拦下率也跟着失真。
 *
 * 所以把同一个 App 在 [GAP_MS] 内的连续触发归并成一个"冲动事件"：
 * - 事件里只要出现过「继续」，就算这次冲动没拦住
 * - 全是「退出」/「离开」，才算拦下
 *
 * 这是全 app 唯一的统计口径，首页和统计页共用，别各自实现。
 */
data class Episode(
    val packageName: String?,
    val startMs: Long,
    val endMs: Long,
    val triggers: Int,
    /** 事件里有没有出现过「继续」（含因此触发的封锁） */
    val continued: Boolean,
    /** 事件里有没有任何一条做过决定；全没做过 = 还挂着/进程被杀，不计入分母 */
    val decided: Boolean,
    /** 事件里有没有紧急解锁 */
    val emergency: Boolean
) {
    val blocked: Boolean get() = decided && !continued
}

object Episodes {

    /** 同一个 App 超过这个间隔再触发，算新的一次冲动 */
    const val GAP_MS = 15 * 60_000L

    fun group(logs: List<TriggerLog>): List<Episode> {
        if (logs.isEmpty()) return emptyList()
        val sorted = logs.sortedBy { it.timestamp }
        val out = ArrayList<Episode>(sorted.size)
        var pkg: String? = sorted.first().packageName
        var start = sorted.first().timestamp
        var end = start
        var count = 0
        var continued = false
        var decided = false
        var emergency = false

        fun flush() {
            if (count > 0) out += Episode(pkg, start, end, count, continued, decided, emergency)
        }

        sorted.forEach { log ->
            val isNew = log.packageName != pkg || log.timestamp - end > GAP_MS
            if (isNew) {
                flush()
                pkg = log.packageName
                start = log.timestamp
                count = 0
                continued = false
                decided = false
                emergency = false
            }
            end = log.timestamp
            count++
            when (log.decision) {
                TriggerLog.DECISION_CONTINUE, TriggerLog.DECISION_LOCKED -> continued = true
                TriggerLog.DECISION_EMERGENCY -> { continued = true; emergency = true }
            }
            if (log.decision != null) decided = true
        }
        flush()
        return out
    }

    /** 每天一个桶：拦下的冲动数 / 继续的冲动数（按事件开始时间归日）。 */
    fun groupByDay(episodes: List<Episode>, rangeStart: Long, days: Int): List<DayEpisodes> {
        val dayMs = 24 * 60 * 60 * 1000L
        val blocked = IntArray(days)
        val continued = IntArray(days)
        episodes.forEach { e ->
            val idx = ((e.startMs - rangeStart) / dayMs).toInt()
            if (idx in 0 until days) {
                if (e.continued) continued[idx]++ else blocked[idx]++
            }
        }
        return (0 until days).map { DayEpisodes(blocked[it], continued[it]) }
    }
}

data class DayEpisodes(val blocked: Int, val continued: Int) {
    val total: Int get() = blocked + continued
}
