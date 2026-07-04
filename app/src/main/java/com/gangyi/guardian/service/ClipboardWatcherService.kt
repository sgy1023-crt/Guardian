package com.gangyi.guardian.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.TriggerLog
import com.gangyi.guardian.data.MODE_FIXED
import com.gangyi.guardian.data.MonitorPrefs
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
    private var lastScreenHash = 0
    private var lastTriggerAt = 0L
    private var pollCount = 0

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkScreenText()
            mainHandler.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        mainHandler.post(pollRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onCreate() {
        super.onCreate()
        repo = GuardianRepository(this)
        prefs = MonitorPrefs(this)
        overlay = OverlayController(this)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(pollRunnable)
        logScope.cancel()
        runCatching { overlay.dismiss() }
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
            val visited = IntArray(0)
            collectTexts(root, 0, sb, visited)
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
        out: StringBuilder,
        @Suppress("UNUSED_PARAMETER") visited: IntArray
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
                collectTexts(child, depth + 1, out, visited)
            } finally {
                child.recycle()
            }
        }
    }

    private fun dispatchText(text: String, pkg: String) {
        val hash = text.hashCode()
        if (hash == lastScreenHash) return
        lastScreenHash = hash
        logScope.launch { matchAndTrigger(text, pkg) }
    }

    private suspend fun matchAndTrigger(text: String, pkg: String) {
        val keywords = repo.listKeywords()
        if (keywords.isEmpty()) return
        val hit = keywords.firstOrNull { kw -> text.contains(kw, ignoreCase = true) }
            ?: return

        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < DEBOUNCE_MS) return
        lastTriggerAt = now

        val base = pickReminder()
        withContextMain {
            if (!overlay.isShowing) overlay.show("（含关键词「$hit」）\n$base")
        }
        repo.logTrigger(pkg, TriggerLog.TYPE_CLIPBOARD, hit)
        Log.d(TAG, "TRIGGER hit='$hit' pkg=$pkg screenLen=${text.length}")
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

    private companion object {
        const val POLL_MS = 1500L
        const val DEBOUNCE_MS = 3000L
        const val MAX_DEPTH = 30
        const val MAX_CHILDREN_PER_NODE = 30
        const val MAX_BUFFER = 20000
        const val TAG = "GuardianClip"
    }
}
