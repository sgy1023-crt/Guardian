package com.gangyi.guardian.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.TriggerLog
import com.gangyi.guardian.data.MODE_FIXED
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.guard.EscalationTracker
import com.gangyi.guardian.overlay.DEFAULT_REMINDERS
import com.gangyi.guardian.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 文本监控服务（无障碍服务）—— "桌宠"模式。
 *
 * 核心方案：每 1.5s 轮询 rootInActiveWindow，递归遍历整棵节点树收集所有 text/contentDescription，
 * 拼成大字符串 → 关键词匹配 → 命中即弹呼吸圆点悬浮窗。
 *
 * 不依赖 AccessibilityEvent.text（各 ROM 填充行为不一致：vivo 有、小米 HyperOS 空）。
 * 不依赖 ClipboardManager（国产 ROM 后台返回 null）。
 *
 * 扫描限深度限节点数，避免大窗口卡顿；用整屏 hash 去重，同屏内容不变不重复触发。
 */
class ClipboardWatcherService : AccessibilityService() {

    private lateinit var repo: GuardianRepository
    private lateinit var prefs: MonitorPrefs
    private lateinit var overlay: OverlayController
    private val mainHandler = Handler(Looper.getMainLooper())
    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastTriggerAt = 0L
    private var pollCount = 0

    /**
     * 防抖记录：key = "包名|命中的关键词"，value = 上次命中时间。
     *
     * 早先这里是一个永久的整屏 hash（同屏内容不变就 return），结果盯着同一张含关键词的
     * 图片永远只弹一次——冷却过了也不再弹，连升级封锁的计数都攒不够。
     * 现在只做短时防抖：同一 App 里同一个关键词在 DEBOUNCE_MS 内不重复处理，
     * 超过就重新判定，"别反复弹"交回给用户可调的冷却时间去管。
     */
    private val recentHits = HashMap<String, Long>()
    private var lastScanPkg: String? = null

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkScreenText()
            val intervalMs = (prefs.screenScanIntervalSeconds * 1000L).toLong()
                .coerceIn(500L, 30_000L)
            mainHandler.postDelayed(this, intervalMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // 先移除再挂，防止系统重复回调时叠出双份轮询
        mainHandler.removeCallbacks(pollRunnable)
        mainHandler.post(pollRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onCreate() {
        super.onCreate()
        repo = GuardianRepository(this)
        prefs = MonitorPrefs(this)
        overlay = OverlayController.get(this)
        EscalationTracker.init(this)
    }

    /** 用户在系统设置里关掉无障碍走这里，漏清会留下野引用导致 kickToHome 打空。 */
    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        mainHandler.removeCallbacks(pollRunnable)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        mainHandler.removeCallbacks(pollRunnable)
        logScope.cancel()
        runCatching { overlay.dismiss(OverlayController.SOURCE_KEYWORD) }
        super.onDestroy()
    }

    /** 一次轮询：扫整棵窗口树，把所有节点文字拼成一个字符串去匹配关键词。 */
    private fun checkScreenText() {
        pollCount++
        val root = rootInActiveWindow
        if (root == null) {
            if (pollCount % 100 == 0) Log.d(TAG, "poll#$pollCount: rootInActiveWindow=null")
            return
        }
        val rootPkg = root.packageName?.toString() ?: "null"
        if (rootPkg == packageName) {
            root.recycle()
            return
        }

        try {
            val sb = StringBuilder(512)
            collectTexts(root, 0, sb)
            val screenText = sb.toString()
            if (screenText.isBlank()) return
            dispatchText(screenText, rootPkg)
        } finally {
            root.recycle()
        }
    }

    /** 递归收集节点 text + contentDescription，限深度限总节点数防卡顿。 */
    private fun collectTexts(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: StringBuilder
    ) {
        if (depth > MAX_DEPTH) return
        if (out.length > MAX_BUFFER) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            out.append(text).append('\n')
        }
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrBlank() && desc != text) {
            out.append(desc).append('\n')
        }

        val childCount = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (i in 0 until childCount) {
            if (out.length > MAX_BUFFER) return
            val child = node.getChild(i) ?: continue
            try {
                collectTexts(child, depth + 1, out)
            } finally {
                child.recycle()
            }
        }
    }

    private fun dispatchText(text: String, pkg: String) {
        // 换了 App 就把防抖记录清空，避免上一个应用的记录误伤当前应用
        if (pkg != lastScanPkg) {
            recentHits.clear()
            lastScanPkg = pkg
        }
        logScope.launch { matchAndTrigger(text, pkg) }
    }

    private suspend fun matchAndTrigger(text: String, pkg: String) {
        val keywords = repo.listKeywords()
        if (keywords.isEmpty()) return
        val hit = keywords.firstOrNull { kw -> text.contains(kw, ignoreCase = true) }
            ?: return

        val now = System.currentTimeMillis()

        // 短时防抖：同一 App 同一关键词在几秒内不重复处理，纯粹为了省电，
        // 不承担"别反复弹"的职责——那件事由下面用户可调的冷却时间负责。
        val debounceKey = "$pkg|$hit"
        val lastSame = recentHits[debounceKey] ?: 0L
        if (now - lastSame < DEBOUNCE_MS) return
        recentHits[debounceKey] = now
        if (recentHits.size > MAX_DEBOUNCE_ENTRIES) {
            recentHits.entries.removeAll { now - it.value > DEBOUNCE_MS }
        }

        val cooldownMs = prefs.cooldownSeconds * 1000L
        if (now - lastTriggerAt < cooldownMs) return

        // 攒够次数就升级为封锁。封的是当前前台包（浏览器里反复搜关键词正是核心场景），
        // 哪怕这个包不在监控列表里——但会先过 SystemPackages 的安全闸。
        val shouldLock = EscalationTracker.recordTrigger(pkg)
        if (shouldLock) {
            val remaining = EscalationTracker.lockRemainingMs(pkg)
            val kicked = kickToHome()
            withContextMain { overlay.showLockdown(remaining, kicked) }
            lastTriggerAt = now
            repo.logTrigger(pkg, TriggerLog.TYPE_CLIPBOARD, hit)
            Log.d(TAG, "升级封锁 pkg=$pkg hit='$hit' 踢出=$kicked")
            return
        }

        val base = pickReminder()
        val message = if (prefs.keywordsEncrypted) base else "（含关键词「$hit」）\n$base"
        var shown = false
        withContextMain {
            if (!overlay.isShowing) {
                overlay.show(message, OverlayController.SOURCE_KEYWORD)
                shown = true
            }
        }
        // 只有真弹出来才消耗冷却、才计入统计，弹窗被占用时不白扣
        if (shown) {
            lastTriggerAt = now
            repo.logTrigger(pkg, TriggerLog.TYPE_CLIPBOARD, hit)
            Log.d(TAG, "TRIGGER hit='$hit' pkg=$pkg screenLen=${text.length}")
        }
    }

    /** 按当前模式取提醒语：固定模式取不到时回退到随机。 */
    private fun pickReminder(): String {
        if (prefs.reminderMode == MODE_FIXED) {
            val id = prefs.fixedReminderId
            if (id >= 0) {
                val fixed = runCatching { repo.getReminderByIdSync(id)?.text }.getOrNull()
                if (!fixed.isNullOrBlank()) return fixed
            }
        }
        val list = runCatching { repo.listRemindersSync() }.getOrDefault(emptyList())
        return list.ifEmpty { DEFAULT_REMINDERS }.random()
    }

    private suspend fun withContextMain(block: () -> Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { block() }
    }

    /**
     * 扫描参数 + 对外暴露的踢回桌面能力。
     *
     * 只有 AccessibilityService 实例本身能调 performGlobalAction，
     * 所以 MonitorService 想踢桌面必须借道这里的静态引用。
     * 一个类只能有一个 companion object，所以常量和静态方法合在一起。
     */
    companion object {
        private const val MAX_DEPTH = 30
        private const val MAX_CHILDREN_PER_NODE = 30
        private const val MAX_BUFFER = 20000
        private const val TAG = "GuardianClip"

        /** 同一 App 同一关键词的最小重复处理间隔（仅防抖省电，不是"不再提醒"） */
        private const val DEBOUNCE_MS = 4000L
        private const val MAX_DEBOUNCE_ENTRIES = 64

        @Volatile
        private var instance: ClipboardWatcherService? = null

        /** 无障碍服务是否已绑定。没绑定时封锁只能降级为"强制冷静窗"。 */
        fun isConnected(): Boolean = instance != null

        /** 踢回桌面。返回 false 表示服务未绑定，踢不动。 */
        fun kickToHome(): Boolean {
            val svc = instance ?: return false
            return runCatching {
                svc.performGlobalAction(GLOBAL_ACTION_HOME)
            }.getOrDefault(false)
        }
    }
}
