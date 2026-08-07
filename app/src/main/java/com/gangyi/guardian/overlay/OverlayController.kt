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

/** 弹窗内容类型。OverlayController 根据类型渲染不同 UI。 */
sealed class OverlayContent {
    /** 旧版简单提醒（保留兼容，关键词触发仍用这个） */
    data class Reminder(val text: String) : OverlayContent()

    /** 意图声明卡：打开被监控 App 时弹出，用户填理由 + 选时长 */
    data class IntentCard(
        val appLabel: String,
        val appPackage: String,
        val defaultTimeSeconds: Int,
        /** 冷却递增强制等待秒数。>0 时表单先禁用，倒计时结束后才能交互 */
        val forcedWaitSeconds: Int = 0,
        val onStart: (reason: String, timeLimitSeconds: Int) -> Unit,
        val onCancel: () -> Unit
    ) : OverlayContent()

    /** 到时回顾卡：限时到了，让用户选择退出 / 续时 */
    data class TimeUpCard(
        val appLabel: String,
        val reason: String,
        val remainingExtensions: Int,
        val extensionSeconds: Int,
        val onDone: () -> Unit,
        val onExtend: () -> Unit,
        val onExit: () -> Unit
    ) : OverlayContent()

    /** 每日额度耗尽卡：今天该 App 的使用配额已用完 */
    data class DailyLimitCard(
        val appLabel: String,
        val usedMinutes: Int,
        val limitMinutes: Int,
        val onExit: () -> Unit
    ) : OverlayContent()

    /** 学习时段封锁卡：学习时间内完全禁止打开监控 App */
    data class StudyBlockCard(
        val appLabel: String,
        val startTime: String,      // 如 "08:00"
        val endTime: String,        // 如 "12:00"
        val remainingMinutes: Int,  // 距离学习时段结束还有几分钟
        val onExit: () -> Unit
    ) : OverlayContent()
}

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

    /** 弹出新弹窗。同一时刻只有一个弹窗，如果已有弹窗则跳过。 */
    fun show(content: OverlayContent, source: String) {
        if (isShowing) return

        val owner = OverlayLifecycleOwner().apply { onCreate() }
        val view = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                GuardianTheme {
                    when (content) {
                        is OverlayContent.Reminder -> InterventionContent(
                            reminder = content.text,
                            onDismiss = { dismiss(source) }
                        )
                        is OverlayContent.IntentCard -> IntentCardContent(
                            appLabel = content.appLabel,
                            defaultTimeSeconds = content.defaultTimeSeconds,
                            forcedWaitSeconds = content.forcedWaitSeconds,
                            onStart = { reason, seconds ->
                                dismiss(source)
                                content.onStart(reason, seconds)
                            },
                            onCancel = {
                                dismiss(source)
                                content.onCancel()
                            }
                        )
                        is OverlayContent.TimeUpCard -> TimeUpCardContent(
                            appLabel = content.appLabel,
                            reason = content.reason,
                            remainingExtensions = content.remainingExtensions,
                            extensionSeconds = content.extensionSeconds,
                            onDone = {
                                dismiss(source)
                                content.onDone()
                            },
                            onExtend = {
                                dismiss(source)
                                content.onExtend()
                            },
                            onExit = {
                                dismiss(source)
                                content.onExit()
                            }
                        )
                        is OverlayContent.DailyLimitCard -> DailyLimitCardContent(
                            appLabel = content.appLabel,
                            usedMinutes = content.usedMinutes,
                            limitMinutes = content.limitMinutes,
                            onExit = {
                                dismiss(source)
                                content.onExit()
                            }
                        )
                        is OverlayContent.StudyBlockCard -> StudyBlockCardContent(
                            appLabel = content.appLabel,
                            startTime = content.startTime,
                            endTime = content.endTime,
                            remainingMinutes = content.remainingMinutes,
                            onExit = {
                                dismiss(source)
                                content.onExit()
                            }
                        )
                    }
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
