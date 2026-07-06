package com.gangyi.guardian.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
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
 */
class OverlayController private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var currentSource: String? = null

    val isShowing: Boolean get() = composeView != null

    fun show(reminder: String, source: String) {
        if (isShowing) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                GuardianTheme {
                    InterventionContent(
                        reminder = reminder,
                        onDismiss = { dismissCurrent() }
                    )
                }
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

        @Volatile
        private var instance: OverlayController? = null

        fun get(context: Context): OverlayController =
            instance ?: synchronized(this) {
                instance ?: OverlayController(context).also { instance = it }
            }
    }
}
