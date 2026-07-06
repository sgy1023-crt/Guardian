package com.gangyi.guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.service.GuardianKeepAliveWorker
import com.gangyi.guardian.service.MonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = MonitorPrefs(context)
        if (prefs.serviceEnabled) {
            // 个别 ROM 可能拒绝从广播启动前台服务，别让接收器崩溃
            runCatching { MonitorService.start(context) }
            GuardianKeepAliveWorker.schedule(context)
        }
    }
}
