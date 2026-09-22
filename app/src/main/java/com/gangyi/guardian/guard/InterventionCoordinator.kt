package com.gangyi.guardian.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gangyi.guardian.MainActivity
import com.gangyi.guardian.R
import com.gangyi.guardian.data.MODE_FIXED
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.Reminder
import com.gangyi.guardian.data.db.TriggerLog
import com.gangyi.guardian.detect.ForegroundAppDetector
import com.gangyi.guardian.overlay.DEFAULT_REMINDERS
import com.gangyi.guardian.overlay.InterventionSession
import com.gangyi.guardian.overlay.OverlayController
import com.gangyi.guardian.util.InstalledApps
import com.gangyi.guardian.util.sha256
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 两个监控引擎共用的"弹窗 + 决定处理"入口。
 *
 * 引擎只负责判断"该不该弹"（前台是谁、有没有通行证、有没有被封），
 * 弹出来之后用户点了什么、记账、封锁、踢人，全在这里，两个引擎行为完全一致。
 *
 * 所有公开方法都必须在主线程调用（悬浮窗要求）。提醒语走内存缓存（Room Flow 常驻），
 * 主线程取不碰数据库。
 */
object InterventionCoordinator {

    private const val TAG = "GuardianIntervene"
    private const val ALERT_CHANNEL_ID = "guardian_alerts"
    private const val OVERLAY_FAIL_NOTIF_ID = 2001
    private const val OVERLAY_FAIL_NOTIFY_GAP_MS = 30 * 60_000L

    /** 用 HOME Intent 兜底踢人后，等这么久再确认人到底走没走 */
    private const val VERIFY_KICK_MS = 800L

    private lateinit var appContext: Context
    private lateinit var prefs: MonitorPrefs
    private lateinit var repo: GuardianRepository
    private lateinit var overlay: OverlayController
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var reminderCache: List<Reminder> = emptyList()

    @Volatile
    private var initialized = false
    private var lastOverlayFailNotifiedAt = 0L

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            prefs = MonitorPrefs(appContext)
            repo = GuardianRepository(appContext)
            overlay = OverlayController.get(appContext)
            EscalationTracker.init(appContext)
            scope.launch { repo.reminders.collect { reminderCache = it } }
            scope.launch { runCatching { repo.pruneOldLogs() } }
            initialized = true
        }
    }

    /**
     * 弹停顿窗。调用方须已确认：无通行证、未封锁、当前没有弹窗。
     * @return 是否真的弹出来了（悬浮窗权限被收回时为 false，此时不记账不计数）
     */
    fun showIntervention(
        pkg: String,
        source: String,
        keyword: String? = null,
        brokenPromise: Boolean = false
    ): Boolean {
        if (!initialized) return false
        val logId = CompletableDeferred<Long>()
        val lockable = prefs.escalationEnabled && SystemPackages.isLockable(appContext, pkg)
        val session = InterventionSession(
            pkg = pkg,
            source = source,
            appLabel = InstalledApps.label(appContext, pkg),
            reminder = pickReminder(),
            keywordHint = if (keyword != null && !prefs.keywordsEncrypted) keyword else null,
            countdownSeconds = EscalationTracker.nextCountdownSeconds(pkg),
            continuesInWindow = EscalationTracker.continuesInWindow(pkg),
            threshold = prefs.escalationThreshold.coerceAtLeast(1),
            lockEnabled = lockable,
            willLockOnContinue = EscalationTracker.willLockOnContinue(pkg),
            passMinutes = prefs.passMinutes.coerceIn(1, 120),
            lockMinutes = prefs.lockdownMinutes.coerceIn(1, 120),
            windowMinutes = prefs.escalationWindowMinutes.coerceAtLeast(1),
            brokenPromise = brokenPromise,
            onContinue = { handleContinue(pkg, logId) },
            onExit = { handleExit(pkg, logId) },
            onLeft = { mark(logId, TriggerLog.DECISION_LEFT) }
        )
        val shown = overlay.showIntervention(session)
        if (!shown) {
            notifyOverlayFailure()
            return false
        }
        val type = if (source == OverlayController.SOURCE_KEYWORD) TriggerLog.TYPE_CLIPBOARD else TriggerLog.TYPE_APP
        scope.launch {
            runCatching { repo.logTrigger(pkg, type, keyword) }
                .onSuccess { logId.complete(it) }
                .onFailure { logId.cancel() }
        }
        Log.d(TAG, "弹窗 pkg=$pkg source=$source 停顿=${session.countdownSeconds}s 已继续=${session.continuesInWindow}")
        return true
    }

    /**
     * 封锁期内 App 露头 / 刚被封锁时调用。
     * @param kick 是否尝试送回桌面（轮询节流期内只补墙不重复踢）
     */
    fun showLockdown(pkg: String, kick: Boolean): Boolean {
        if (!initialized) return false
        val kicked = kick && HomeKicker.kick(appContext)
        val shown = renderLockdown(pkg, kicked)
        // 踢人走的是 HOME Intent 兜底时（无障碍没绑），成不成没法同步知道：
        // 先立"墙"保证人还在里面时立刻被挡住，800ms 后看一眼前台——
        // 人真被送走了就换成"已送回桌面"告知卡，免得留一张一秒就消失的红屏闪。
        if (kick && !kicked && shown) {
            scope.launch {
                delay(VERIFY_KICK_MS)
                withContext(Dispatchers.Main) {
                    val fg = runCatching { ForegroundAppDetector.getForegroundPackage(appContext) }.getOrNull()
                    if (fg != pkg) renderLockdown(pkg, kicked = true, feedback = false)
                }
            }
        }
        return shown
    }

    private fun renderLockdown(pkg: String, kicked: Boolean, feedback: Boolean = true): Boolean = overlay.showLockdown(
        pkg = pkg,
        appLabel = InstalledApps.label(appContext, pkg),
        remainingMs = EscalationTracker.lockRemainingMs(pkg),
        kicked = kicked,
        needsPassword = prefs.keywordPasswordHash.isNotEmpty(),
        onEmergencyUnlock = { input -> emergencyUnlock(pkg, input) },
        onHome = { HomeKicker.kick(appContext) },
        feedback = feedback
    )

    /**
     * 紧急解除。给"真的必须用"留的出口：没设密码就是一道 5 秒等待（在弹窗里），
     * 设了密码就得输密码。解除会写一条 EMERGENCY 记录，统计里看得见——
     * 能被看见，才不会被滥用。
     * @return 密码对不对（没设密码时永远 true）
     */
    private fun emergencyUnlock(pkg: String, passwordInput: String): Boolean {
        val hash = prefs.keywordPasswordHash
        if (hash.isNotEmpty() && sha256(passwordInput.trim()) != hash) return false
        EscalationTracker.unlockNow(pkg)
        overlay.dismissCurrent()
        scope.launch {
            runCatching {
                repo.logTrigger(pkg, TriggerLog.TYPE_APP, decision = TriggerLog.DECISION_EMERGENCY)
            }
        }
        Log.w(TAG, "紧急解除封锁 pkg=$pkg")
        return true
    }

    private fun handleContinue(pkg: String, logId: CompletableDeferred<Long>) {
        overlay.dismissCurrent()
        when (EscalationTracker.onContinue(pkg)) {
            is EscalationTracker.ContinueResult.Locked -> {
                mark(logId, TriggerLog.DECISION_LOCKED)
                showLockdown(pkg, kick = true)
            }
            else -> mark(logId, TriggerLog.DECISION_CONTINUE)
        }
    }

    private fun handleExit(pkg: String, logId: CompletableDeferred<Long>) {
        overlay.dismissCurrent()
        mark(logId, TriggerLog.DECISION_EXIT)
        val kicked = HomeKicker.kick(appContext)
        if (!kicked) EscalationTracker.grantExitGrace(pkg)
    }

    private fun mark(logId: CompletableDeferred<Long>, decision: String) {
        scope.launch {
            runCatching {
                val id = logId.await()
                repo.markDecision(id, decision)
            }
        }
    }

    /** 按当前模式取提醒语：固定模式取不到时回退到随机。纯内存，主线程安全。 */
    private fun pickReminder(): String {
        val list = reminderCache
        if (prefs.reminderMode == MODE_FIXED) {
            val fixed = list.firstOrNull { it.id == prefs.fixedReminderId }?.text
            if (!fixed.isNullOrBlank()) return fixed
        }
        val texts = list.map { it.text }.filter { it.isNotBlank() }
        return texts.ifEmpty { DEFAULT_REMINDERS }.random()
    }

    /** 悬浮窗挂不上去 = 权限多半被系统收回了。静默失败等于守卫失效，必须让人知道。 */
    private fun notifyOverlayFailure() {
        notifyProblem(
            "守卫无法弹出提醒",
            "悬浮窗权限可能被系统收回了，点开检查权限状态"
        )
        Log.w(TAG, "悬浮窗加窗失败")
    }

    /** 运行中发现必需权限没了：发一条通知，半小时内不重复。 */
    fun notifyPermissionLost(what: String) {
        notifyProblem("守卫已停止工作", "「$what」权限被系统收回了，点开重新开启")
        Log.w(TAG, "权限丢失: $what")
    }

    private fun notifyProblem(title: String, text: String) {
        if (!initialized) return
        val now = System.currentTimeMillis()
        if (now - lastOverlayFailNotifiedAt < OVERLAY_FAIL_NOTIFY_GAP_MS) return
        lastOverlayFailNotifiedAt = now
        runCatching {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(ALERT_CHANNEL_ID, "守卫异常提醒", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val pi = PendingIntent.getActivity(
                appContext, 1, Intent(appContext, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(appContext, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(OVERLAY_FAIL_NOTIF_ID, n)
        }
    }
}
