package com.gangyi.guardian.guard

import android.content.Context
import android.content.Intent
import com.gangyi.guardian.service.ClipboardWatcherService

/**
 * 把人送回桌面。
 *
 * 首选无障碍的 performGlobalAction(HOME)，各 ROM 都稳。
 * 没绑无障碍时退而求其次，直接拉起桌面 Intent——有悬浮窗权限的 App 在部分系统上
 * 允许后台启动 Activity，但国产 ROM 常有"后台弹出界面"开关拦着，成不成没法同步得知，
 * 所以只有无障碍那条路返回 true。调用方按 false 走"墙"模式，桌面真拉起来了会被
 * 轮询自然收掉，不会卡住。
 */
object HomeKicker {

    fun kick(context: Context): Boolean {
        if (ClipboardWatcherService.kickToHome()) return true
        runCatching {
            context.applicationContext.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }
}
