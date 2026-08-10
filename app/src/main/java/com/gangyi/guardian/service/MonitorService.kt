package com.gangyi.guardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gangyi.guardian.MainActivity
import com.gangyi.guardian.R
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.MODE_FIXED
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.TriggerLog
import com.gangyi.guardian.detect.ForegroundAppDetector
import com.gangyi.guardian.guard.CountdownEscalator
import com.gangyi.guardian.guard.EscalationTracker
import com.gangyi.guardian.overlay.DEFAULT_REMINDERS
import com.gangyi.guardian.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 守卫核心前台服务：常驻通知保活，协程每秒轮询前台 App，
 * 命中监控列表且过了冷却期则弹出停顿弹窗。
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null
    private lateinit var prefs: MonitorPrefs
    private lateinit var repo: GuardianRepository
    private lateinit var overlay: OverlayController
    private lateinit var powerManager: PowerManager

    // Flow 常驻缓存，避免轮询线程每秒查一次数据库
    @Volatile
    private var monitoredPkgs: Set<String> = emptySet()

    /** 上次踢桌面的时间，用于节流，避免每秒踢一次闪屏 */
    private var lastKickAt = 0L

    /** 上一轮轮询的前台包名。包名变化 = 用户离开过又进来（封锁要立刻响应） */
    private var lastForegroundPkg: String? = null

    override fun onCreate() {
        super.onCreate()
        prefs = MonitorPrefs(this)
        repo = GuardianRepository(this)
        overlay = OverlayController.get(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        EscalationTracker.init(this)
        startForeground(NOTIF_ID, buildNotification())
        scope.launch {
            repo.monitoredPackages.collect { monitoredPkgs = it.toSet() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            // 每个被监控 App 各自记一份"上次触发时间"，
            // 这样冷却完全基于时间，离开/回来都不会丢冷却进度。
            val lastTriggerAtByPkg = HashMap<String, Long>()
            while (isActive) {
                // 息屏：不查询不弹窗，收起已有弹窗，降频省电
                if (!powerManager.isInteractive) {
                    if (overlay.isShowing) {
                        withContext(Dispatchers.Main) {
                            overlay.dismiss(OverlayController.SOURCE_APP)
                        }
                    }
                    delay(SCREEN_OFF_POLL_MS)
                    continue
                }

                val pkg = ForegroundAppDetector.getForegroundPackage(this@MonitorService)

                // 记是否"刚闯入"：跟前一轮包名不同，说明离开过又进来。
                // 封锁只挡这一个动作——见下方节流逻辑。
                val isNewEntry = pkg != lastForegroundPkg
                lastForegroundPkg = pkg

                // 封锁优先于一切：被封的 App 一露头就踢回桌面，连弹窗判定都不用走。
                // 注意这里不看 monitoredPkgs——封锁是对"当时那个前台包"生效的，
                // 哪怕用户事后把它从监控列表删了，封锁期内照样踢。
                if (EscalationTracker.isLocked(pkg)) {
                    val now = System.currentTimeMillis()
                    val remaining = EscalationTracker.lockRemainingMs(pkg)
                    // 节流只防"人停在 App 里不动时每秒踢一次"的闪屏（3 秒一次观察期）。
                    // 但**重新闯入必须立刻踢**——用户退到桌面再点开，要的就是
                    // "一点就回桌面"的体验，不能被节流放行。
                    if (isNewEntry || now - lastKickAt > KICK_THROTTLE_MS) {
                        lastKickAt = now
                        val kicked = ClipboardWatcherService.kickToHome()
                        withContext(Dispatchers.Main) {
                            overlay.showLockdown(remaining, kicked)
                        }
                        Log.d(TAG, "封锁拦截 pkg=$pkg 剩余=${remaining / 1000}s 踢出=$kicked")
                    } else if (!overlay.isShowing) {
                        // 兜底：无障碍掉线踢不动人时，用户还留在被封 App 里，
                        // 而且可能刚把封锁窗点掉了。这里立刻补一层盖回去，
                        // 让"进不去"这件事不完全依赖无障碍（悬浮窗权限比它稳得多）。
                        withContext(Dispatchers.Main) {
                            overlay.showLockdown(remaining, kicked = false)
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                val hit = pkg != null && pkg in monitoredPkgs && pkg != packageName

                if (hit) {
                    val now = System.currentTimeMillis()
                    // 递增模式下冷却让位：冷却 30 秒会把第二次弹窗吃掉，
                    // 翻倍就永远触发不了。此时改用 CountdownEscalator 的最小间隔（3 秒），
                    // 它只防轮询抖动，"隔多久算连续挣扎"交给递增间隔那一项管。
                    val escalating = prefs.escalatingCountdownEnabled
                    val lastAt = lastTriggerAtByPkg[pkg] ?: 0L
                    val freshTrigger = if (escalating) {
                        CountdownEscalator.minGapPassed(pkg ?: "", now)
                    } else {
                        now - lastAt > prefs.cooldownSeconds * 1000L
                    }
                    if (freshTrigger && !overlay.isShowing) {
                        lastTriggerAtByPkg[pkg!!] = now
                        // 记一次触发；窗口内攒够次数就升级为封锁。
                        // 放在这个 if 里面 = 一次真实弹窗算一次，不会被轮询空转刷计数。
                        //
                        // 只在包名"刚被系统事件确认过"时才记：沿用缓存的值可能已经过期，
                        // 拿它去攒计数会记到错的 App 头上，最坏情况是封错对象。
                        // 漏记一次只是晚一轮封锁，封错却会让用户彻底不信这个功能。
                        val shouldLock = ForegroundAppDetector.isFreshlyConfirmed() &&
                            EscalationTracker.recordTrigger(pkg)
                        if (shouldLock) {
                            val remaining = EscalationTracker.lockRemainingMs(pkg)
                            val kicked = ClipboardWatcherService.kickToHome()
                            lastKickAt = now
                            withContext(Dispatchers.Main) {
                                overlay.showLockdown(remaining, kicked)
                            }
                            launch { repo.logTrigger(pkg, TriggerLog.TYPE_APP) }
                            Log.d(TAG, "升级封锁 pkg=$pkg 踢出=$kicked")
                        } else {
                            val reminder = pickReminder()
                            // 连续挣扎时倒计时翻倍：15 → 30 → 60…
                            val seconds = CountdownEscalator.nextCountdown(
                                this@MonitorService, pkg
                            )
                            withContext(Dispatchers.Main) {
                                overlay.show(reminder, OverlayController.SOURCE_APP, seconds)
                            }
                            launch { repo.logTrigger(pkg, TriggerLog.TYPE_APP) }
                        }
                    }
                } else {
                    // 离开被监控 App：收起自己弹的窗（不动关键词弹窗）。冷却时间不重置。
                    if (overlay.isShowing) {
                        withContext(Dispatchers.Main) {
                            overlay.dismiss(OverlayController.SOURCE_APP)
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        overlay.dismiss(OverlayController.SOURCE_APP)
        scope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 按当前模式取提醒语：固定模式取不到时回退到随机。 */
    private fun pickReminder(): String {
        if (prefs.reminderMode == MODE_FIXED) {
            val id = prefs.fixedReminderId
            if (id >= 0) {
                val fixed = repo.getReminderByIdSync(id)?.text
                if (!fixed.isNullOrBlank()) return fixed
            }
        }
        val list = repo.listRemindersSync()
        return list.ifEmpty { DEFAULT_REMINDERS }.random()
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "守卫运行状态", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "守卫正在后台守护你的专注" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("守卫运行中")
            .setContentText("正在守护你的专注")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "guardian_monitor"
        private const val POLL_INTERVAL_MS = 1000L
        private const val SCREEN_OFF_POLL_MS = 5000L
        private const val KICK_THROTTLE_MS = 3000L
        private const val TAG = "GuardianMonitor"

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            GuardianKeepAliveWorker.schedule(context)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
            GuardianKeepAliveWorker.cancel(context)
        }
    }
}
