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
import androidx.core.app.NotificationCompat
import com.gangyi.guardian.MainActivity
import com.gangyi.guardian.R
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.MODE_FIXED
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.TriggerLog
import com.gangyi.guardian.detect.ForegroundAppDetector
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

    override fun onCreate() {
        super.onCreate()
        prefs = MonitorPrefs(this)
        repo = GuardianRepository(this)
        overlay = OverlayController.get(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
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
                val hit = pkg != null && pkg in monitoredPkgs && pkg != packageName

                if (hit) {
                    val now = System.currentTimeMillis()
                    val cooldownMs = prefs.cooldownSeconds * 1000L
                    val lastAt = lastTriggerAtByPkg[pkg] ?: 0L
                    val freshTrigger = now - lastAt > cooldownMs
                    if (freshTrigger && !overlay.isShowing) {
                        lastTriggerAtByPkg[pkg!!] = now
                        val reminder = pickReminder()
                        withContext(Dispatchers.Main) {
                            overlay.show(reminder, OverlayController.SOURCE_APP)
                        }
                        launch { repo.logTrigger(pkg, TriggerLog.TYPE_APP) }
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
