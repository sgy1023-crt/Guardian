package com.gangyi.guardian.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.guard.EscalationTracker
import com.gangyi.guardian.guard.GuardSchedule
import com.gangyi.guardian.guard.InterventionCoordinator
import com.gangyi.guardian.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 关键词监控服务（无障碍服务）。
 *
 * 核心方案：定时轮询 rootInActiveWindow，递归遍历节点树收集文字 → 关键词匹配 → 弹停顿窗。
 * 不依赖 AccessibilityEvent.text（各 ROM 填充行为不一致：vivo 有、小米 HyperOS 空），
 * 不依赖 ClipboardManager（国产 ROM 后台返回 null）。
 *
 * 三条防死循环的规则：
 * 1. **默认只看输入框**（keywordInputOnly）：自己打出来的才算"在找"，别人发来的、
 *    搜索结果里出现的不算。全屏匹配作为可选项保留。
 * 2. **同一 App 同一关键词，从屏幕上消失 10 秒后再出现才算新一次**（REAPPEAR_MS）。
 *    词一直在屏幕上 = 还是那一次，不重复弹、不重复计数。
 * 3. 弹窗之后的一切（通行证 / 退出宽限 / 封锁）跟 App 引擎共用同一套状态，
 *    有通行证就闭嘴，被封锁就踢人（MonitorService 挂了也照踢，双保险）。
 */
class ClipboardWatcherService : AccessibilityService() {

    private lateinit var repo: GuardianRepository
    private lateinit var prefs: MonitorPrefs
    private lateinit var overlay: OverlayController
    private lateinit var powerManager: PowerManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 关键词 Flow 常驻缓存：轮询在主线程跑，不能碰 Room
    @Volatile
    private var keywordCache: List<String> = emptyList()

    /** key = "包名|关键词"，value = 上次在屏幕上看到的时间。只在主线程读写。 */
    private val lastSeen = HashMap<String, Long>()
    private var lastScanPkg: String? = null
    private var lastKickAt = 0L
    private var pollCount = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            // 息屏不扫：整夜每 1.5 秒一次 binder 调用纯属浪费
            if (!powerManager.isInteractive) {
                mainHandler.postDelayed(this, SCREEN_OFF_POLL_MS)
                return
            }
            runCatching { checkScreenText() }.onFailure { Log.w(TAG, "扫描异常", it) }
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
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        EscalationTracker.init(this)
        InterventionCoordinator.init(this)
        scope.launch { repo.keywords.collect { keywordCache = it } }
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
        scope.cancel()
        super.onDestroy()
    }

    /** 一次轮询：扫窗口树 → 找出屏幕上的关键词 → 只对"新出现"的关键词做反应。 */
    private fun checkScreenText() {
        pollCount++
        val root = rootInActiveWindow
        if (root == null) {
            if (pollCount % 100 == 0L) Log.d(TAG, "poll#$pollCount: rootInActiveWindow=null")
            return
        }
        val rootPkg = root.packageName?.toString()
        if (rootPkg == null || rootPkg == packageName) {
            // 前台是我们自己的弹窗：把所有"上次看到"顺延，别让词在弹窗背后悄悄"消失又出现"
            if (rootPkg == packageName) {
                val now = System.currentTimeMillis()
                lastSeen.replaceAll { _, _ -> now }
            }
            root.recycle()
            return
        }

        // 换了 App：上一个应用的记录作废
        if (rootPkg != lastScanPkg) {
            lastSeen.clear()
            lastScanPkg = rootPkg
        }

        // 封锁双保险：MonitorService 挂了也照踢
        if (EscalationTracker.isLocked(rootPkg)) {
            root.recycle()
            val now = System.currentTimeMillis()
            if (now - lastKickAt > KICK_THROTTLE_MS) {
                lastKickAt = now
                InterventionCoordinator.showLockdown(rootPkg, kick = true)
            }
            return
        }

        val keywords = keywordCache
        // 暂停中 / 时段外不扫（省电），恢复后屏幕上的词按"新出现"处理
        if (keywords.isEmpty() || !GuardSchedule.isActive(prefs)) {
            root.recycle()
            return
        }

        val text = try {
            val sb = StringBuilder(512)
            collectTexts(root, 0, sb, prefs.keywordInputOnly)
            sb.toString()
        } finally {
            root.recycle()
        }
        if (text.isBlank()) return

        val now = System.currentTimeMillis()
        var newHit: String? = null
        for (kw in keywords) {
            if (!text.contains(kw, ignoreCase = true)) continue
            val key = "$rootPkg|$kw"
            val seenAt = lastSeen[key]
            val isNew = seenAt == null || now - seenAt > REAPPEAR_MS
            lastSeen[key] = now
            if (isNew && newHit == null) newHit = kw
        }
        if (lastSeen.size > MAX_SEEN_ENTRIES) {
            lastSeen.entries.removeAll { now - it.value > REAPPEAR_MS }
        }
        val hit = newHit ?: return

        // 有通行证 / 在退出宽限期 / 已有弹窗 → 不打扰
        if (EscalationTracker.hasPass(rootPkg) || EscalationTracker.inExitGrace(rootPkg)) return
        if (overlay.isShowing) return

        val shown = InterventionCoordinator.showIntervention(
            rootPkg, OverlayController.SOURCE_KEYWORD, keyword = hit
        )
        if (shown) Log.d(TAG, "TRIGGER hit='$hit' pkg=$rootPkg screenLen=${text.length}")
    }

    /**
     * 递归收集节点文字，限深度限总节点数防卡顿。
     * inputOnly 时只收可编辑节点（输入框）的文字；密码框永远跳过。
     */
    private fun collectTexts(
        node: AccessibilityNodeInfo,
        depth: Int,
        out: StringBuilder,
        inputOnly: Boolean
    ) {
        if (depth > MAX_DEPTH) return
        if (out.length > MAX_BUFFER) return

        val include = !node.isPassword && (!inputOnly || node.isEditable)
        if (include) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                out.append(text).append('\n')
            }
            if (!inputOnly) {
                val desc = node.contentDescription?.toString()?.trim()
                if (!desc.isNullOrBlank() && desc != text) {
                    out.append(desc).append('\n')
                }
            }
        }

        val childCount = node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)
        for (i in 0 until childCount) {
            if (out.length > MAX_BUFFER) return
            val child = node.getChild(i) ?: continue
            try {
                collectTexts(child, depth + 1, out, inputOnly)
            } finally {
                child.recycle()
            }
        }
    }

    /**
     * 扫描参数 + 对外暴露的踢回桌面能力。
     *
     * 只有 AccessibilityService 实例本身能调 performGlobalAction，
     * 所以别处想踢桌面必须借道这里的静态引用。
     */
    companion object {
        private const val MAX_DEPTH = 30
        private const val MAX_CHILDREN_PER_NODE = 30
        private const val MAX_BUFFER = 20000
        private const val TAG = "GuardianClip"

        /** 同一 App 同一关键词，消失多久后再出现才算新一次 */
        private const val REAPPEAR_MS = 10_000L
        private const val MAX_SEEN_ENTRIES = 64
        private const val SCREEN_OFF_POLL_MS = 5000L
        private const val KICK_THROTTLE_MS = 3000L

        @Volatile
        private var instance: ClipboardWatcherService? = null

        /** 无障碍服务是否已绑定。没绑定时封锁只能降级为"墙"。 */
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
