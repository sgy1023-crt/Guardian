package com.gangyi.guardian.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.permission.Permissions
import java.util.concurrent.TimeUnit

/**
 * 每 15 分钟检查一次：用户想开着守护、服务却不在了，就拉起来。
 *
 * Android 12+ 限制后台启动前台服务，但**持有悬浮窗权限的应用在豁免名单里**
 * （官方文档 "Exemptions from background start restrictions"）——而悬浮窗正是守卫的必需权限。
 * 所以这里直接尝试，被拒就记一笔等下次，不再要求"App 必须在前台"
 * （那个条件下 HomeScreen 早就自己拉过服务了，Worker 等于白跑）。
 */
class GuardianKeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = MonitorPrefs(applicationContext)
        if (!prefs.serviceEnabled) return Result.success()
        if (!Permissions.hasUsageAccess(applicationContext) || !Permissions.hasOverlay(applicationContext)) {
            Log.d(TAG, "skip: required permission missing")
            return Result.success()
        }

        return try {
            MonitorService.start(applicationContext)
            Log.d(TAG, "keep-alive: service (re)started")
            Result.success()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "FGS not allowed from background, deferring to next resume")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "keep-alive failed", e)
            Result.success()
        }
    }

    companion object {
        private const val TAG = "GuardianKeeper"
        private const val WORK_NAME = "guardian_keep_alive"
        private const val INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<GuardianKeepAliveWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
