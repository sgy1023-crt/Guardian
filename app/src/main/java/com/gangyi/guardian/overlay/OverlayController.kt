package com.gangyi.guardian.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gangyi.guardian.ui.theme.GuardianTheme

/**
 * 一次停顿弹窗的全部上下文：弹给谁、为什么弹、停顿多久、两个按钮各自干什么。
 *
 * 弹窗只有两个出口（详见 EscalationTracker）：
 * - onExit：退出，免费
 * - onContinue：继续，记一次、换通行证；次数用完则封锁
 * 没做决定就离开（按 Home / 息屏）走 onLeft，也免费。
 */
class InterventionSession(
    val pkg: String,
    val source: String,
    val appLabel: String,
    val reminder: String,
    /** 命中的关键词，加密模式下为 null 不显示 */
    val keywordHint: String?,
    val countdownSeconds: Int,
    val continuesInWindow: Int,
    val threshold: Int,
    val lockEnabled: Boolean,
    val willLockOnContinue: Boolean,
    val passMinutes: Int,
    val lockMinutes: Int,
    val windowMinutes: Int,
    /** 说了退出却没走，这次弹窗是"失信"补弹 */
    val brokenPromise: Boolean,
    val onContinue: () -> Unit,
    val onExit: () -> Unit,
    val onLeft: () -> Unit
)

/**
 * 负责把停顿弹窗作为系统级悬浮窗挂到任意界面之上。
 * 全屏 MATCH_PARENT 拦截所有触摸。必须在主线程调用 show/dismiss。
 *
 * 进程内单例：两个监控引擎共用同一个控制器，同一时刻最多一个弹窗。
 *
 * 每个窗可以"绑定"到一个包名：用户离开那个包（前台换人）时窗自动收起——
 * 走人永远免费，这是激励结构的根。踢回桌面后的封锁告知窗不绑定，挂几秒自己消失。
 *
 * 优先级：LOCKDOWN（封锁告知）> APP / KEYWORD（普通提醒）。封锁窗可以抢占普通提醒窗。
 */
class OverlayController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var currentSource: String? = null
    private var currentSession: InterventionSession? = null

    /** 当前窗绑定的包名；null = 不绑定（不随前台变化收起） */
    var boundPkg: String? = null
        private set

    /** 每挂一次窗 +1，用来让延时任务认出"我要收的还是不是那扇窗" */
    private var generation = 0

    val isShowing: Boolean get() = composeView != null

    val showingSource: String? get() = currentSource

    /** 弹停顿窗。返回 false = 已有窗在显示或加窗失败（悬浮窗权限被收回）。 */
    fun showIntervention(session: InterventionSession): Boolean {
        val ok = attach(session.source, bound = session.pkg) {
            InterventionContent(session)
        }
        if (ok) currentSession = session
        return ok
    }

    /**
     * 弹封锁窗。
     * @param kicked 已成功送回桌面：不绑定包名、几秒后自动消失，纯告知。
     *               没送回去：绑定到那个包，变成一堵"墙"——人只能自己走，走了窗就收。
     */
    fun showLockdown(
        pkg: String,
        appLabel: String,
        remainingMs: Long,
        kicked: Boolean,
        needsPassword: Boolean,
        onEmergencyUnlock: (String) -> Boolean,
        onHome: () -> Unit,
        /** false = 只是把"墙"换成告知卡这类重绘，别再振一次/响一次 */
        feedback: Boolean = true
    ): Boolean {
        // 人已经被送回桌面了，之前挂着的"墙"没意义了，换成告知卡
        if (kicked && isShowing && currentSource == SOURCE_LOCKDOWN && boundPkg != null) {
            dismissCurrent()
        }
        val ok = attach(SOURCE_LOCKDOWN, bound = if (kicked) null else pkg, feedback = feedback) {
            LockdownContent(
                appLabel = appLabel,
                remainingMs = remainingMs,
                kicked = kicked,
                needsPassword = needsPassword,
                onEmergencyUnlock = onEmergencyUnlock,
                onDismiss = { dismissCurrent() },
                onHome = onHome
            )
        }
        if (ok && kicked) {
            val gen = generation
            mainHandler.postDelayed({
                if (generation == gen && isShowing) dismissCurrent()
            }, LOCKDOWN_INFO_AUTO_DISMISS_MS)
        }
        return ok
    }

    /** 挂窗的公共流程。已有弹窗时按优先级决定抢占还是放弃。 */
    private fun attach(source: String, bound: String?, feedback: Boolean = true, content: @Composable () -> Unit): Boolean {
        if (isShowing) {
            // 只有封锁窗能抢占普通提醒；封锁窗之间不互抢（防节流期内反复重建导致闪烁）
            val canPreempt = source == SOURCE_LOCKDOWN && currentSource != SOURCE_LOCKDOWN
            if (!canPreempt) return false
            dismissCurrent()
        }

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                GuardianTheme { content() }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        return try {
            windowManager.addView(view, params)
            owner.onResume()
            composeView = view
            lifecycleOwner = owner
            currentSource = source
            boundPkg = bound
            generation++
            // 窗真挂上去了才给反馈——加窗失败时不该白响一声
            if (feedback) {
                if (source == SOURCE_LOCKDOWN) {
                    AlertFeedback.onLockdown(appContext)
                } else {
                    AlertFeedback.onRemind(appContext)
                }
            }
            true
        } catch (_: Exception) {
            runCatching { owner.onDestroy() }
            false
        }
    }

    /** 前台换成了别的包：收掉绑在其它包上的窗。停顿窗按"离开"记账。 */
    fun dismissIfBoundToOther(foregroundPkg: String?) {
        val bound = boundPkg ?: return
        if (!isShowing || bound == foregroundPkg) return
        autoDismiss()
    }

    /** 息屏等场景：无条件收掉，停顿窗按"离开"记账。 */
    fun dismissAll() {
        if (!isShowing) return
        autoDismiss()
    }

    private fun autoDismiss() {
        val session = currentSession
        dismissCurrent()
        session?.onLeft?.invoke()
    }

    fun dismissCurrent() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        lifecycleOwner?.onDestroy()
        composeView = null
        lifecycleOwner = null
        currentSource = null
        currentSession = null
        boundPkg = null
        generation++
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    companion object {
        const val SOURCE_APP = "APP"
        const val SOURCE_KEYWORD = "KEYWORD"
        const val SOURCE_LOCKDOWN = "LOCKDOWN"

        private const val LOCKDOWN_INFO_AUTO_DISMISS_MS = 6000L

        @Volatile
        private var instance: OverlayController? = null

        fun get(context: Context): OverlayController =
            instance ?: synchronized(this) {
                instance ?: OverlayController(context).also { instance = it }
            }
    }
}
