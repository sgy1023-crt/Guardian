package com.gangyi.guardian.guard

import android.content.Context
import android.util.Log
import com.gangyi.guardian.data.MonitorPrefs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 升级机制的决策中心：通行证、"继续"计数、递增停顿、封锁。
 *
 * ## 一条铁律：只有"用户做出的决定"才计数，时间流逝不计数
 *
 * 旧版本按"弹窗次数"计数，而弹窗又是"冷却一到人还在就再弹"——
 * 人什么都不做、只是在同一屏多待一分钟，就被记满三次然后封锁，
 * 关键词更惨：输一次词，词一直在屏幕上，每隔几秒再弹一次、再翻一倍，死循环。
 *
 * 现在每次停顿弹窗只有两个出口：
 * - **退出**：免费。有无障碍就直接送回桌面；没有就给 30 秒自己走（EXIT_GRACE_MS）。
 * - **继续**：记 1 次，换一张 passMinutes 分钟的通行证，期间这个 App 不弹不记。
 *
 * 不做决定就离开（按 Home、息屏）同样免费。这样激励结构才是对的：走人永远免费，留下永远有代价。
 *
 * 计数在 escalationWindowMinutes 的滑动窗口里滚动：
 * - 递增停顿：第 n 次继续后，下次停顿 = base × multiplier^n，封顶 countdownMaxSeconds
 * - 升级封锁：第 escalationThreshold 次"继续"不给通行证，直接封锁 lockdownMinutes 分钟
 *   （2 小时内再次被封同一 App 时长翻倍，最多 4 倍）
 *
 * 失信：点了"退出"却在宽限期后还留在那个 App 里 → 按"继续"记 1 次（不给通行证）。
 * 否则每 30 秒点一次"退出"就能白用，防自我欺骗就白做了。
 *
 * 进程内单例，状态经 EscalationStore 持久化，杀进程/重启都绕不过去。
 */
object EscalationTracker {

    private const val TAG = "GuardianEscalation"

    /** 递进加重的记忆期：2 小时内重复被封同一个 App，封锁时长翻倍 */
    private const val STRIKE_MEMORY_MS = 2 * 60 * 60 * 1000L
    private const val MAX_MULTIPLIER = 4

    /** 点"退出"但送不回桌面时，给用户自己离开的时间 */
    const val EXIT_GRACE_MS = 30_000L

    private lateinit var appContext: Context
    private lateinit var store: EscalationStore
    private lateinit var prefs: MonitorPrefs

    @Volatile
    private var initialized = false

    /** 幂等初始化，两个服务和 Activity 的 onCreate 都会调。 */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            store = EscalationStore(appContext)
            prefs = MonitorPrefs(appContext)
            store.pruneExpired(System.currentTimeMillis())
            initialized = true
        }
    }

    val isReady: Boolean get() = initialized

    private fun windowMs() = prefs.escalationWindowMinutes.coerceAtLeast(1) * 60_000L

    // ---------------- 状态查询 ----------------

    fun isLocked(pkg: String?): Boolean {
        if (!initialized || pkg == null) return false
        return store.lockUntil(pkg) > System.currentTimeMillis()
    }

    /** 剩余封锁毫秒数，未封锁返回 0。 */
    fun lockRemainingMs(pkg: String?): Long {
        if (!initialized || pkg == null) return 0L
        return (store.lockUntil(pkg) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** 生效中的封锁：包名 → 解锁时间戳。 */
    fun activeLocks(): Map<String, Long> {
        if (!initialized) return emptyMap()
        return store.activeLocks(System.currentTimeMillis())
    }

    /** 是否存在任何生效中的封锁。用于阻止用户在封锁期关掉总开关绕过。 */
    fun anyActiveLock(): Boolean = activeLocks().isNotEmpty()

    fun hasPass(pkg: String?): Boolean {
        if (!initialized || pkg == null) return false
        return store.passUntil(pkg) > System.currentTimeMillis()
    }

    fun passRemainingMs(pkg: String?): Long {
        if (!initialized || pkg == null) return 0L
        return (store.passUntil(pkg) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun inExitGrace(pkg: String?): Boolean {
        if (!initialized || pkg == null) return false
        return store.graceUntil(pkg) > System.currentTimeMillis()
    }

    /** 窗口内已"继续"的次数。 */
    fun continuesInWindow(pkg: String?): Int {
        if (!initialized || pkg == null) return 0
        return store.continuesInWindow(pkg, System.currentTimeMillis(), windowMs())
    }

    /** 下一次点"继续"是否会直接触发封锁（弹窗上要提前说清楚，不能让人被"埋伏"）。 */
    fun willLockOnContinue(pkg: String?): Boolean {
        if (!initialized || pkg == null || !prefs.escalationEnabled) return false
        if (!SystemPackages.isLockable(appContext, pkg)) return false
        return continuesInWindow(pkg) + 1 >= prefs.escalationThreshold.coerceAtLeast(1)
    }

    /**
     * 下一次弹窗该停顿多少秒：base × multiplier^(窗口内已继续次数)，封顶保护。
     * 没开递增就始终是 base。
     */
    fun nextCountdownSeconds(pkg: String?): Int {
        val base = prefs.overlayCountdownSeconds.coerceIn(3, 600)
        if (!initialized || pkg == null || !prefs.escalatingCountdownEnabled) return base
        val n = continuesInWindow(pkg)
        if (n == 0) return base
        val multiplier = prefs.countdownMultiplier.coerceIn(1.0f, 10.0f)
        val maxSec = prefs.countdownMaxSeconds.coerceIn(base, 3600)
        val raw = base * multiplier.toDouble().pow(n.toDouble())
        return raw.roundToInt().coerceIn(base, maxSec)
    }

    // ---------------- 决策 ----------------

    sealed class ContinueResult {
        /** 拿到通行证，到 untilMs 为止不再打扰 */
        data class Pass(val untilMs: Long) : ContinueResult()
        /** 次数用完，已封锁到 untilMs */
        data class Locked(val untilMs: Long) : ContinueResult()
        /** 记了一次但不给通行证（失信场景） */
        data class Strike(val count: Int) : ContinueResult()
    }

    /** 用户在停顿弹窗上点了"继续"。 */
    fun onContinue(pkg: String): ContinueResult {
        check(initialized)
        val now = System.currentTimeMillis()
        val count = store.addContinue(pkg, now, windowMs())
        Log.d(TAG, "继续 pkg=$pkg 本轮第 $count 次 / 阈值 ${prefs.escalationThreshold}")
        if (prefs.escalationEnabled &&
            count >= prefs.escalationThreshold.coerceAtLeast(1) &&
            SystemPackages.isLockable(appContext, pkg)
        ) {
            lock(pkg)
            return ContinueResult.Locked(store.lockUntil(pkg))
        }
        val until = now + prefs.passMinutes.coerceIn(1, 120) * 60_000L
        store.setPass(pkg, until)
        return ContinueResult.Pass(until)
    }

    /** 用户点了"退出"但没能送回桌面：给宽限期，让人自己走。 */
    fun grantExitGrace(pkg: String) {
        if (!initialized) return
        store.setGrace(pkg, System.currentTimeMillis() + EXIT_GRACE_MS)
    }

    /** 前台不是 keep 时，清掉其它包的宽限：人已经离开 = 守信，不追究。 */
    fun clearGraceExcept(keep: String?) {
        if (!initialized) return
        store.clearGraceExcept(keep)
    }

    /**
     * 前台轮询每秒调一次：pkg 的退出宽限已过期、人却还在这个 App 里 → 失信。
     * 按"继续"记 1 次；次数够了直接封锁。返回 null 表示没有失信发生。
     */
    fun consumeExpiredGrace(pkg: String): ContinueResult? {
        if (!initialized) return null
        val until = store.graceUntil(pkg)
        if (until == 0L) return null
        val now = System.currentTimeMillis()
        if (until > now) return null
        store.clearGrace(pkg)
        val count = store.addContinue(pkg, now, windowMs())
        Log.d(TAG, "失信 pkg=$pkg 本轮第 $count 次")
        if (prefs.escalationEnabled &&
            count >= prefs.escalationThreshold.coerceAtLeast(1) &&
            SystemPackages.isLockable(appContext, pkg)
        ) {
            lock(pkg)
            return ContinueResult.Locked(store.lockUntil(pkg))
        }
        return ContinueResult.Strike(count)
    }

    /** 按当前设置（含递进加重）封锁一个包。 */
    fun lock(pkg: String) {
        if (!initialized) return
        if (!SystemPackages.isLockable(appContext, pkg)) {
            Log.w(TAG, "拒绝封锁受保护的包: $pkg")
            return
        }
        val now = System.currentTimeMillis()
        val baseMs = prefs.lockdownMinutes.coerceIn(1, 120) * 60_000L

        // 上次被封还在记忆期内 → 累加 strike，时长翻倍（3→6→12 分钟，上限 4 倍）
        val prevStrike = store.strikeCount(pkg)
        val prevAt = store.lastLockTime(pkg)
        val strike = if (now - prevAt <= STRIKE_MEMORY_MS) prevStrike + 1 else 1
        val multiplier = (1 shl (strike - 1)).coerceAtMost(MAX_MULTIPLIER)
        val durationMs = baseMs * multiplier

        store.setLock(pkg, now + durationMs, strike, now)
        Log.d(TAG, "LOCK pkg=$pkg 时长=${durationMs / 1000}s strike=$strike x$multiplier")
    }

    /**
     * 紧急解除封锁。给"真的必须用"留的出口——调用方负责先过密码/等待那道门槛。
     * strike 记忆保留：解一次不会让下次封锁变轻。
     */
    fun unlockNow(pkg: String) {
        if (!initialized) return
        store.clearLock(pkg)
        Log.w(TAG, "紧急解除 pkg=$pkg")
    }

    /** 清理到期封锁/通行证，轮询循环里定期调。 */
    fun pruneExpired() {
        if (!initialized) return
        store.pruneExpired(System.currentTimeMillis())
    }
}
