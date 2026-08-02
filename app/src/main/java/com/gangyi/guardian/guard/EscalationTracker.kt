package com.gangyi.guardian.guard

import android.content.Context
import android.util.Log
import com.gangyi.guardian.data.MonitorPrefs

/**
 * 升级封锁的决策中心。
 *
 * 解决的问题：光弹提醒挡不住"刻意反复点掉弹窗继续看"的自我欺骗。
 * 所以在滑动窗口内被弹够 N 次，就升级为硬手段——踢回桌面 + 定时封锁该 App，
 * 封锁期间一打开就再踢出来，没有解锁入口。
 *
 * Android 不允许普通应用杀别人的进程（killBackgroundProcesses 对他人无效），
 * 所以"强制清后台"做不到；而封锁其实比清后台更有效——清了他还能再点开，封锁点不开。
 *
 * 进程内单例，仿 OverlayController 的模式。状态经 EscalationStore 持久化，
 * 杀进程/重启都绕不过去。
 */
object EscalationTracker {

    private const val TAG = "GuardianEscalation"

    /** 递进加重的记忆期：2 小时内重复被封同一个 App，封锁时长翻倍 */
    private const val STRIKE_MEMORY_MS = 2 * 60 * 60 * 1000L
    private const val MAX_MULTIPLIER = 4

    private lateinit var appContext: Context
    private lateinit var store: EscalationStore
    private lateinit var prefs: MonitorPrefs

    @Volatile
    private var initialized = false

    /** 幂等初始化，两个服务的 onCreate 都会调。 */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            store = EscalationStore(appContext)
            prefs = MonitorPrefs(appContext)
            store.pruneExpiredLocks(System.currentTimeMillis())
            initialized = true
        }
    }

    val isReady: Boolean get() = initialized

    /**
     * 记录一次弹窗触发，返回是否应当升级为封锁。
     *
     * @param pkg 触发时的前台包名。关键词触发也算——用户就是在浏览器里反复搜，
     *            封的是当时那个前台 App，即使它不在监控列表里。
     */
    fun recordTrigger(pkg: String?): Boolean {
        if (!initialized || pkg == null) return false
        if (!prefs.escalationEnabled) return false
        if (!SystemPackages.isLockable(appContext, pkg)) return false

        val now = System.currentTimeMillis()
        val windowMs = prefs.escalationWindowMinutes * 60_000L
        val count = store.recordHit(pkg, now, windowMs)
        val threshold = prefs.escalationThreshold

        Log.d(TAG, "recordTrigger pkg=$pkg count=$count/$threshold")
        if (count < threshold) return false

        lock(pkg)
        return true
    }

    /** 按当前设置（含递进加重）封锁一个包。 */
    fun lock(pkg: String) {
        if (!initialized) return
        if (!SystemPackages.isLockable(appContext, pkg)) {
            Log.w(TAG, "拒绝封锁受保护的包: $pkg")
            return
        }
        val now = System.currentTimeMillis()
        val baseMs = prefs.lockdownMinutes * 60_000L

        // 上次被封还在记忆期内 → 累加 strike，时长翻倍（3→6→12 分钟，上限 4 倍）
        val prevStrike = store.strikeCount(pkg)
        val prevAt = store.lastLockTime(pkg)
        val strike = if (now - prevAt <= STRIKE_MEMORY_MS) prevStrike + 1 else 1
        val multiplier = (1 shl (strike - 1)).coerceAtMost(MAX_MULTIPLIER)
        val durationMs = baseMs * multiplier

        store.setLock(pkg, now + durationMs, strike, now)
        Log.d(TAG, "LOCK pkg=$pkg 时长=${durationMs / 1000}s strike=$strike x$multiplier")
    }

    fun isLocked(pkg: String?): Boolean {
        if (!initialized || pkg == null) return false
        return store.lockUntil(pkg) > System.currentTimeMillis()
    }

    /** 剩余封锁毫秒数，未封锁返回 0。 */
    fun lockRemainingMs(pkg: String?): Long {
        if (!initialized || pkg == null) return 0L
        return (store.lockUntil(pkg) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** 是否存在任何生效中的封锁。用于阻止用户在封锁期关掉总开关绕过。 */
    fun anyActiveLock(): Boolean {
        if (!initialized) return false
        val now = System.currentTimeMillis()
        store.pruneExpiredLocks(now)
        return store.hasActiveLock(now)
    }

    fun activeLockCount(): Int {
        if (!initialized) return 0
        return store.activeLockCount(System.currentTimeMillis())
    }

    /** 清理到期封锁，轮询循环里定期调。 */
    fun pruneExpired() {
        if (!initialized) return
        store.pruneExpiredLocks(System.currentTimeMillis())
    }
}
