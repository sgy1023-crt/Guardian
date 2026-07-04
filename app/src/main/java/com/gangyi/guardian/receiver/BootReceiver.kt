package com.gangyi.guardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.service.GuardianKeepAliveWorker
import com.gangyi.guardian.service.MonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = MonitorPrefs(context)
        if (prefs.serviceEnabled) {
            MonitorService.start(context)
            GuardianKeepAliveWorker.schedule(context)
        }
    }
}
