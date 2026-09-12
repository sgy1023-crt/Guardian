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
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.detect.ForegroundAppDetector
import com.gangyi.guardian.guard.EscalationTracker
import com.gangyi.guardian.guard.InterventionCoordinator
import com.gangyi.guardian.overlay.OverlayController
import com.gangyi.guardian.permission.Permissions
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
 * 守卫核心前台服务：常驻通知保活，协程每秒轮询前台 App。
 *
 * 每一轮做四件事，顺序就是优先级：
 * 1. 前台换人 → 收掉绑在别的包上的弹窗（走人免费）、清掉别的包的退出宽限（守信）
 * 2. 前台是被封锁的 App → 踢回桌面 / 补一堵墙（不看监控列表，封锁对"当时那个包"生效）
 * 3. 前台是"说了退出却没走"的 App → 失信补弹（任何包，不限监控列表）
 * 4. 前台是监控列表里的 App 且没有通行证、不在宽限期、当前没弹窗 → 弹停顿窗
 *
 * 弹出来之后用户点什么、怎么记账、什么时候封锁，全在 InterventionCoordinator，
 * 这里不碰。
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null
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

    private var pollCount = 0L

    override fun onCreate() {
        super.onCreate()
        repo = GuardianRepository(this)
        overlay = OverlayController.get(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        EscalationTracker.init(this)
        InterventionCoordinator.init(this)
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
            while (isActive) {
                // 息屏：不查询不弹窗，收起已有弹窗（按"离开"记），降频省电
                if (!powerManager.isInteractive) {
                    if (overlay.isShowing) {
                        withContext(Dispatchers.Main) { overlay.dismissAll() }
                    }
                    delay(SCREEN_OFF_POLL_MS)
                    continue
                }

                pollCount++
                if (pollCount % PRUNE_EVERY_POLLS == 0L) {
                    EscalationTracker.pruneExpired()
                    // 权限被 ROM 悄悄收回是国产机常态，静默失效比不工作更糟——每 30 秒核对一次
                    if (!Permissions.hasUsageAccess(this@MonitorService)) {
                        ForegroundAppDetector.reset()
                        InterventionCoordinator.notifyPermissionLost("用量访问")
                        delay(POLL_INTERVAL_MS)
                        continue
                    }
                    if (!Permissions.hasOverlay(this@MonitorService)) {
                        InterventionCoordinator.notifyPermissionLost("悬浮窗")
                    }
                }

                val pkg = ForegroundAppDetector.getForegroundPackage(this@MonitorService)
                val isNewEntry = pkg != lastForegroundPkg
                lastForegroundPkg = pkg

                // 1. 前台换人：走人免费、守信不追究
                if (overlay.isShowing) {
                    withContext(Dispatchers.Main) { overlay.dismissIfBoundToOther(pkg) }
                }
                EscalationTracker.clearGraceExcept(pkg)

                if (pkg == null || pkg == packageName) {
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // 2. 封锁：只在包名"刚由系统事件确认过"时执法。
                //    缓存里可能是过期包名——拿过期值去踢人，最坏情况是人在桌面上被反复"踢回桌面"。
                if (EscalationTracker.isLocked(pkg)) {
                    if (ForegroundAppDetector.isFreshlyConfirmed()) {
                        val now = System.currentTimeMillis()
                        // 重新闯入必须立刻踢；停在里面不动时 3 秒一次，防闪屏
                        if (isNewEntry || now - lastKickAt > KICK_THROTTLE_MS) {
                            lastKickAt = now
                            withContext(Dispatchers.Main) {
                                InterventionCoordinator.showLockdown(pkg, kick = true)
                            }
                            Log.d(TAG, "封锁拦截 pkg=$pkg 剩余=${EscalationTracker.lockRemainingMs(pkg) / 1000}s")
                        } else if (!overlay.isShowing) {
                            // 踢不动（无障碍掉线/ROM 拦了）时人还在里面，补一堵墙
                            withContext(Dispatchers.Main) {
                                InterventionCoordinator.showLockdown(pkg, kick = false)
                            }
                        }
                    }
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                // 3. 失信：说了退出、宽限过了、人还在
                when (EscalationTracker.consumeExpiredGrace(pkg)) {
                    is EscalationTracker.ContinueResult.Locked -> {
                        lastKickAt = System.currentTimeMillis()
                        withContext(Dispatchers.Main) {
                            InterventionCoordinator.showLockdown(pkg, kick = true)
                        }
                        Log.d(TAG, "失信升级封锁 pkg=$pkg")
                        delay(POLL_INTERVAL_MS)
                        continue
                    }
                    is EscalationTracker.ContinueResult.Strike -> {
                        if (!overlay.isShowing) {
                            withContext(Dispatchers.Main) {
                                InterventionCoordinator.showIntervention(
                                    pkg, OverlayController.SOURCE_APP, brokenPromise = true
                                )
                            }
                        }
                        delay(POLL_INTERVAL_MS)
                        continue
                    }
                    else -> Unit
                }

                // 4. 普通停顿：监控列表里、没通行证、不在宽限期、当前没弹窗
                val hit = pkg in monitoredPkgs &&
                    !EscalationTracker.hasPass(pkg) &&
                    !EscalationTracker.inExitGrace(pkg)
                if (hit && !overlay.isShowing) {
                    withContext(Dispatchers.Main) {
                        InterventionCoordinator.showIntervention(pkg, OverlayController.SOURCE_APP)
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onDestroy() {
        loopJob?.cancel()
        runCatching { overlay.dismissAll() }
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
        private const val PRUNE_EVERY_POLLS = 30L
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
