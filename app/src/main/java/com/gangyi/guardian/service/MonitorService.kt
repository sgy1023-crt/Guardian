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
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.TriggerLog
import com.gangyi.guardian.detect.ForegroundAppDetector
import com.gangyi.guardian.overlay.OverlayContent
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
 * 守卫核心前台服务：常驻通知保活，协程每秒轮询前台 App。
 * 新机制 —— 意图声明 + 限时使用 + 到时自动退出：
 * 1. 打开被监控 App → 弹出意图声明卡（填理由 + 选时长）
 * 2. 填写完毕 → 记录会话，放行使用
 * 3. 时间到 → 弹出回顾卡，用户选退出则回桌面
 * 关键词触发（ClipboardWatcherService）继续沿用旧的提醒弹窗。
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null
    private lateinit var prefs: MonitorPrefs
    private lateinit var repo: GuardianRepository
    private lateinit var overlay: OverlayController
    private lateinit var powerManager: PowerManager
    private lateinit var notificationManager: NotificationManager

    // Flow 常驻缓存
    @Volatile private var monitoredPkgs: Set<String> = emptySet()
    // 会话预期结束时间缓存（毫秒 timestamp），避免每次轮询查 DB
    private val sessionEndMsByPkg = HashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        prefs = MonitorPrefs(this)
        repo = GuardianRepository(this)
        overlay = OverlayController.get(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        initNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(null))
        // 服务启动时清掉所有残留的进行中会话（上次被杀可能留脏数据）
        scope.launch { repo.expireAllActiveSessions() }
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
                    handleMonitoredApp(pkg!!)
                } else {
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

    /** 核心决策：前台 App 命中监控列表时，根据是否有活跃会话决定弹什么。 */
    private suspend fun handleMonitoredApp(pkg: String) {
        val now = System.currentTimeMillis()

        // 0. 学习时段检查（最高优先级——学习时间禁止一切）
        if (prefs.studyBlockEnabled && isInStudyBlock()) {
            if (!overlay.isShowing) {
                val label = getAppLabel(pkg)
                val remaining = remainingStudyMinutes()
                val startStr = timeStr(prefs.studyBlockStartHour, prefs.studyBlockStartMinute)
                val endStr = timeStr(prefs.studyBlockEndHour, prefs.studyBlockEndMinute)
                withContext(Dispatchers.Main) {
                    overlay.show(
                        OverlayContent.StudyBlockCard(
                            appLabel = label,
                            startTime = startStr,
                            endTime = endStr,
                            remainingMinutes = remaining,
                            onExit = { goHome() }
                        ),
                        OverlayController.SOURCE_APP
                    )
                }
            }
            return
        }

        // 1. 检查是否有进行中的限时会话
        val session = withContext(Dispatchers.IO) { repo.getActiveSession(pkg) }

        if (session == null) {
            // 无会话 → 先检查每日配额，再弹出意图声明卡
            if (!overlay.isShowing) {
                val label = getAppLabel(pkg)
                val todayStart = getTodayStartMs()

                // 每日配额检查
                val limitMinutes = prefs.dailyLimitMinutes
                if (limitMinutes > 0) {
                    val usedSeconds = withContext(Dispatchers.IO) { repo.totalSecondsToday(pkg, todayStart) }
                    val usedMinutes = usedSeconds / 60
                    if (usedMinutes >= limitMinutes) {
                        withContext(Dispatchers.Main) {
                            overlay.show(
                                OverlayContent.DailyLimitCard(
                                    appLabel = label,
                                    usedMinutes = usedMinutes,
                                    limitMinutes = limitMinutes,
                                    onExit = { goHome() }
                                ),
                                OverlayController.SOURCE_APP
                            )
                        }
                        return
                    }
                }

                val openCount = withContext(Dispatchers.IO) { repo.countTodaySessions(pkg, todayStart) }
                val forcedWait = escalationWaitSeconds(openCount)
                withContext(Dispatchers.Main) {
                    overlay.show(
                        OverlayContent.IntentCard(
                            appLabel = label,
                            appPackage = pkg,
                            defaultTimeSeconds = prefs.defaultTimeLimitSeconds,
                            forcedWaitSeconds = forcedWait,
                            onStart = { reason, seconds ->
                                scope.launch {
                                    val sid = repo.startSession(pkg, reason, seconds)
                                    sessionEndMsByPkg[pkg] = now + seconds * 1000L
                                    logTrigger(pkg, reason)
                                    updateNotification(pkg, seconds)
                                }
                            },
                            onCancel = {
                                scope.launch {
                                    repo.logTrigger(pkg, TriggerLog.TYPE_APP, "CANCELLED")
                                }
                            }
                        ),
                        OverlayController.SOURCE_APP
                    )
                }
            }
            return
        }

        // 2. 活跃会话存在 → 检查是否到时
        val endMs = sessionEndMsByPkg[pkg] ?: run {
            val calculated = session.startTime + session.timeLimitSeconds * 1000L
            sessionEndMsByPkg[pkg] = calculated
            calculated
        }

        if (now >= endMs) {
            // 时间到了 → 弹出回顾卡
            if (!overlay.isShowing) {
                val label = getAppLabel(pkg)
                val canExtend = session.extensionCount < prefs.maxExtensionCount
                withContext(Dispatchers.Main) {
                    overlay.show(
                        OverlayContent.TimeUpCard(
                            appLabel = label,
                            reason = session.reason,
                            remainingExtensions = if (canExtend) prefs.maxExtensionCount - session.extensionCount else 0,
                            extensionSeconds = prefs.extensionSeconds,
                            onDone = {
                                scope.launch {
                                    repo.finishSession(session.id)
                                    sessionEndMsByPkg.remove(pkg)
                                    goHome()
                                }
                            },
                            onExtend = {
                                scope.launch {
                                    repo.extendSession(session.id, prefs.extensionSeconds)
                                    sessionEndMsByPkg[pkg] = now + prefs.extensionSeconds * 1000L
                                    updateNotification(pkg, prefs.extensionSeconds)
                                }
                            },
                            onExit = {
                                scope.launch {
                                    repo.finishSession(session.id)
                                    sessionEndMsByPkg.remove(pkg)
                                    goHome()
                                }
                            }
                        ),
                        OverlayController.SOURCE_APP
                    )
                }
            }
        } else {
            // 时间未到 → 放行，更新通知里的剩余时间
            val remaining = ((endMs - now) / 1000L).toInt()
            // 每 30 秒刷新一次通知，避免频繁更新
            if (remaining % 30 == 0) {
                updateNotification(pkg, remaining)
            }
        }
    }

    private fun getAppLabel(pkg: String): String = runCatching {
        val pm = applicationContext.packageManager
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai).toString()
    }.getOrDefault(pkg)

    private suspend fun logTrigger(pkg: String, reason: String) {
        repo.logTrigger(pkg, TriggerLog.TYPE_APP)
    }

    /** 回到桌面——用户选择退出时调用。 */
    private fun goHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }
    }

    private fun updateNotification(pkg: String, remainingSeconds: Int) {
        val label = getAppLabel(pkg)
        val text = if (remainingSeconds > 60) {
            "「$label」还剩 ${remainingSeconds / 60} 分钟"
        } else {
            "「$label」还剩 $remainingSeconds 秒"
        }
        val n = buildNotification(text)
        notificationManager.notify(NOTIF_ID, n)
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

    private fun initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "守卫运行状态", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "守卫正在后台守护你的专注" }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /** 构建常驻通知。statusText 为 null 时显示默认文案，否则显示剩余时间。 */
    private fun buildNotification(statusText: String?): Notification {
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("守卫运行中")
            .setContentText(statusText ?: "正在守护你的专注")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    /** 当前时间是否处于学习时段内。支持跨夜（如 22:00~06:00）。 */
    private fun isInStudyBlock(): Boolean {
        val cal = java.util.Calendar.getInstance()
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val startMinutes = prefs.studyBlockStartHour * 60 + prefs.studyBlockStartMinute
        val endMinutes = prefs.studyBlockEndHour * 60 + prefs.studyBlockEndMinute

        return if (startMinutes <= endMinutes) {
            // 当天内：如 08:00 ~ 12:00
            nowMinutes in startMinutes..<endMinutes
        } else {
            // 跨夜：如 22:00 ~ 06:00
            nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    /** 距离学习时段结束还有多少分钟（0 表示即将结束） */
    private fun remainingStudyMinutes(): Int {
        val cal = java.util.Calendar.getInstance()
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val endMinutes = prefs.studyBlockEndHour * 60 + prefs.studyBlockEndMinute

        val startMinutes = prefs.studyBlockStartHour * 60 + prefs.studyBlockStartMinute
        return if (startMinutes <= endMinutes) {
            endMinutes - nowMinutes
        } else {
            // 跨夜：如果当前在 start~24:00，距离结束 = (24*60 - nowMinutes) + endMinutes
            if (nowMinutes >= startMinutes) (24 * 60 - nowMinutes) + endMinutes
            else endMinutes - nowMinutes
        }.coerceAtLeast(0)
    }

    private fun timeStr(hour: Int, minute: Int): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    /** 冷却递增强制等待：打开次数越多，等待越久。 */
    private fun escalationWaitSeconds(openCount: Int): Int = when {
        openCount <= 0 -> 0
        openCount == 1 -> 10
        openCount == 2 -> 30
        openCount == 3 -> 60
        else -> 180 // 4+
    }

    /** 今天 00:00:00.000 的时间戳 */
    private fun getTodayStartMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
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
