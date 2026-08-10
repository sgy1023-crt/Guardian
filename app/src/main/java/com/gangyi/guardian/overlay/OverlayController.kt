package com.gangyi.guardian.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.gangyi.guardian.ui.theme.GuardianTheme

/**
 * 负责把停顿弹窗作为系统级悬浮窗挂到任意界面之上。
 * 全屏 MATCH_PARENT 拦截所有触摸，用户只能点"我清醒了"关闭。
 * 必须在主线程调用 show/dismiss。
 *
 * 进程内单例：两个监控引擎共用同一个控制器，同一时刻最多一个弹窗，
 * 不会叠两层；show/dismiss 带来源标记，App 引擎自动收起时不会误伤关键词弹窗。
 *
 * 优先级：LOCKDOWN（封锁告知）> APP / KEYWORD（普通提醒）。
 * 封锁窗可以抢占普通提醒窗，反之不行——否则用户攒够次数被封时，
 * 屏幕上正挂着的普通提醒会把封锁提示挡掉，人就不知道自己被封了。
 */
class OverlayController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var currentSource: String? = null

    val isShowing: Boolean get() = composeView != null

    /** 当前弹窗的来源标记，没弹窗时为 null。 */
    val showingSource: String? get() = currentSource

    fun show(reminder: String, source: String, countdownSeconds: Int = 0) {
        attach(source) {
            InterventionContent(
                reminder = reminder,
                countdownSeconds = countdownSeconds,
                onDismiss = { dismissCurrent() }
            )
        }
    }

    /**
     * 弹封锁告知窗。
     *
     * @param remainingMs 剩余封锁毫秒数
     * @param kicked 是否成功踢回桌面。false 表示无障碍没绑定，
     *               此时降级为"强制冷静"——按钮要等倒计时结束才能点，
     *               因为人还留在那个 App 里，直接放走等于没拦。
     */
    fun showLockdown(remainingMs: Long, kicked: Boolean) {
        attach(SOURCE_LOCKDOWN) {
            LockdownContent(
                remainingMs = remainingMs,
                kicked = kicked,
                onDismiss = { dismissCurrent() }
            )
        }
    }

    /** 挂窗的公共流程。已有弹窗时按优先级决定抢占还是放弃。 */
    private fun attach(source: String, content: @Composable () -> Unit) {
        if (isShowing) {
            // 只有封锁窗能抢占；封锁窗之间不互抢（防节流期内反复重建导致闪烁）
            val canPreempt = source == SOURCE_LOCKDOWN && currentSource != SOURCE_LOCKDOWN
            if (!canPreempt) return
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
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(view, params)
            owner.onResume()
            composeView = view
            lifecycleOwner = owner
            currentSource = source
            // 窗真挂上去了才给反馈——加窗失败时不该白响一声
            if (source == SOURCE_LOCKDOWN) {
                AlertFeedback.onLockdown(appContext)
            } else {
                AlertFeedback.onRemind(appContext)
            }
        } catch (_: Exception) {
            runCatching { owner.onDestroy() }
        }
    }

    /** 只收起 source 自己弹的窗。 */
    fun dismiss(source: String) {
        if (currentSource == source) dismissCurrent()
    }

    fun dismissCurrent() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        lifecycleOwner?.onDestroy()
        composeView = null
        lifecycleOwner = null
        currentSource = null
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

        @Volatile
        private var instance: OverlayController? = null

        fun get(context: Context): OverlayController =
            instance ?: synchronized(this) {
                instance ?: OverlayController(context).also { instance = it }
            }
    }
}
