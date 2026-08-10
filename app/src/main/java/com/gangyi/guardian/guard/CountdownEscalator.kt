package com.gangyi.guardian.guard

import android.content.Context
import android.util.Log
import com.gangyi.guardian.data.MonitorPrefs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 递增倒计时：连续挣扎时，让每次弹窗等得更久。
 *
 * 为什么这个比"强制踢后台"更有效：
 * 踢后台是硬对抗，人会本能地想绕过；而递增摩擦不阻止你，只是让代价一次比一次高。
 * 第一次等 15 秒无所谓，第二次 30 秒开始烦，第三次 60 秒——多数人在这之前就放弃了，
 * 而且是"自己决定放弃"，不是"被系统拦住"，不会积累对抗情绪。
 *
 * 连击的判定：两次触发相隔在 escalationIntervalSeconds 内算"还在挣扎"，倒计时翻倍；
 * 超过这个间隔说明人是真的离开去干别的了，等级归零，重新从初始秒数开始。
 *
 * 状态按包名分开记，且只存在内存里——进程重启就归零。这是有意的：
 * 递增是针对"当下这一轮挣扎"的，不该跨天秋后算账（那是封锁功能的职责）。
 */
object CountdownEscalator {

    private const val TAG = "GuardianCountdown"

    private data class State(
        /** 连续第几次，1 = 首次 */
        var streak: Int,
        /** 上次触发时间戳 */
        var lastAt: Long
    )

    private val states = HashMap<String, State>()

    /**
     * 记一次触发，返回这次弹窗该用的倒计时秒数。
     *
     * @param key 归属标识（包名，或关键词场景下的前台包名）
     */
    @Synchronized
    fun nextCountdown(context: Context, key: String): Int {
        val prefs = MonitorPrefs(context)
        val base = prefs.overlayCountdownSeconds.coerceIn(1, 600)

        // 没开递增：始终用基础秒数，行为跟以前完全一致
        if (!prefs.escalatingCountdownEnabled) return base

        val now = System.currentTimeMillis()
        val intervalMs = prefs.escalationIntervalSeconds.coerceAtLeast(1) * 1000L
        val prev = states[key]

        val streak = if (prev != null && now - prev.lastAt <= intervalMs) {
            prev.streak + 1        // 还在挣扎期内 → 连击 +1
        } else {
            1                      // 隔太久 = 真的离开过 → 重新开始
        }
        states[key] = State(streak, now)

        val multiplier = prefs.countdownMultiplier.coerceIn(1.0f, 10.0f)
        val maxSec = prefs.countdownMaxSeconds.coerceIn(base, 3600)

        // base * multiplier^(streak-1)，封顶保护
        val raw = base * multiplier.toDouble().pow((streak - 1).toDouble())
        val seconds = raw.roundToInt().coerceIn(base, maxSec)

        Log.d(TAG, "递增倒计时 key=$key 连击=$streak 秒数=$seconds (base=$base x$multiplier)")
        return seconds
    }

    /**
     * 判断距上次触发是否已过"递增间隔"——递增模式下用它取代冷却时间做节流。
     *
     * 递增模式的节流必须比冷却短得多，否则第二次弹窗会被冷却吃掉，翻倍永远触发不了。
     * 这里只挡"同一秒内轮询重复触发"这种真正的抖动，留 3 秒够用。
     */
    @Synchronized
    fun minGapPassed(key: String, now: Long): Boolean {
        val prev = states[key] ?: return true
        return now - prev.lastAt >= MIN_GAP_MS
    }

    /** 手动清掉某个 key 的连击（比如用户主动关掉了那个 App 很久） */
    @Synchronized
    fun reset(key: String) {
        states.remove(key)
    }

    @Synchronized
    fun currentStreak(key: String): Int = states[key]?.streak ?: 0

    /** 递增模式下的最小触发间隔：只防轮询抖动，不承担"别反复弹"的职责 */
    private const val MIN_GAP_MS = 3000L
}
