package com.gangyi.guardian.guard

import com.gangyi.guardian.data.MonitorPrefs
import java.util.Calendar

/**
 * "现在该不该守护"的两个开关：临时暂停 + 守护时段。
 *
 * 两者只挡**新的**停顿弹窗和计数，不撤销已经生效的封锁——封锁是已经付出的代价，
 * 否则"暂停一下"就成了封锁的后门（所以封锁期间 UI 也不让暂停）。
 */
object GuardSchedule {

    // ---------------- 暂停 ----------------

    fun isPaused(prefs: MonitorPrefs, now: Long = System.currentTimeMillis()): Boolean =
        prefs.pausedUntil > now

    fun pauseRemainingMs(prefs: MonitorPrefs, now: Long = System.currentTimeMillis()): Long =
        (prefs.pausedUntil - now).coerceAtLeast(0L)

    // ---------------- 时段 ----------------

    /** 当前时刻是否落在守护时段内。没开时段功能 = 永远在。 */
    fun isInSchedule(prefs: MonitorPrefs, now: Long = System.currentTimeMillis()): Boolean {
        if (!prefs.scheduleEnabled) return true
        val days = prefs.scheduleDays
        if (days.isEmpty()) return false
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = prefs.scheduleStartMinutes
        val end = prefs.scheduleEndMinutes
        return when {
            start == end -> dow in days                                   // 全天
            start < end -> dow in days && minutes >= start && minutes < end
            else -> {                                                     // 跨夜：今天晚上 或 昨天延续到今天早上
                (dow in days && minutes >= start) ||
                    (previousDay(dow) in days && minutes < end)
            }
        }
    }

    /** 同时满足：没暂停 且 在时段内。引擎每轮都问它。 */
    fun isActive(prefs: MonitorPrefs, now: Long = System.currentTimeMillis()): Boolean =
        !isPaused(prefs, now) && isInSchedule(prefs, now)

    /** 下一次守护时段开始的时间戳；时段没开或一天都没选返回 null。 */
    fun nextScheduleStart(prefs: MonitorPrefs, now: Long = System.currentTimeMillis()): Long? {
        if (!prefs.scheduleEnabled) return null
        val days = prefs.scheduleDays
        if (days.isEmpty()) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        for (i in 0..7) {
            if (cal.get(Calendar.DAY_OF_WEEK) in days) {
                val startMs = cal.timeInMillis + prefs.scheduleStartMinutes * 60_000L
                if (startMs > now) return startMs
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    // ---------------- 文案 ----------------

    private val DAY_ORDER = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
    )
    private val DAY_SHORT = mapOf(
        Calendar.MONDAY to "一", Calendar.TUESDAY to "二", Calendar.WEDNESDAY to "三",
        Calendar.THURSDAY to "四", Calendar.FRIDAY to "五", Calendar.SATURDAY to "六", Calendar.SUNDAY to "日"
    )
    val WEEKDAYS: Set<Int> = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    val WEEKEND: Set<Int> = setOf(Calendar.SATURDAY, Calendar.SUNDAY)

    /** 按周一到周日的顺序给 UI 画一排 chip。 */
    fun dayOrder(): List<Pair<Int, String>> = DAY_ORDER.map { it to DAY_SHORT.getValue(it) }

    fun formatHm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    fun describeDays(days: Set<Int>): String = when {
        days.size == 7 -> "每天"
        days == WEEKDAYS -> "周一至周五"
        days == WEEKEND -> "周末"
        days.isEmpty() -> "未选择"
        else -> "周" + DAY_ORDER.filter { it in days }.joinToString("、") { DAY_SHORT.getValue(it) }
    }

    /** 例："周一至周五 09:00–18:00" / "每天 22:00–次日 06:00" */
    fun describe(prefs: MonitorPrefs): String {
        val start = prefs.scheduleStartMinutes
        val end = prefs.scheduleEndMinutes
        val range = when {
            start == end -> "全天"
            start < end -> "${formatHm(start)}–${formatHm(end)}"
            else -> "${formatHm(start)}–次日 ${formatHm(end)}"
        }
        return "${describeDays(prefs.scheduleDays)} $range"
    }

    /** 给主页用："今天 09:00" / "明天 09:00" / "周一 09:00" */
    fun describeNextStart(startMs: Long, now: Long = System.currentTimeMillis()): String {
        val target = Calendar.getInstance().apply { timeInMillis = startMs }
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayDiff = ((startMs - today.timeInMillis) / (24 * 60 * 60_000L)).toInt()
        val hm = formatHm(target.get(Calendar.HOUR_OF_DAY) * 60 + target.get(Calendar.MINUTE))
        return when (dayDiff) {
            0 -> "今天 $hm"
            1 -> "明天 $hm"
            else -> "周${DAY_SHORT.getValue(target.get(Calendar.DAY_OF_WEEK))} $hm"
        }
    }

    private fun previousDay(dow: Int): Int = if (dow == Calendar.SUNDAY) Calendar.SATURDAY else dow - 1
}
