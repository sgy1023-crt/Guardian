package com.gangyi.guardian.service

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.gangyi.guardian.data.MonitorPrefs
import java.util.concurrent.TimeUnit

class GuardianKeepAliveWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = MonitorPrefs(applicationContext)
        if (!prefs.serviceEnabled) return Result.success()

        if (!isAppInForeground()) {
            // Android 12+ 不允许后台启动前台服务；不在前台时直接退出，
            // 让 START_STICKY / BootReceiver / 用户下次进 app 三条路径自然恢复服务。
            Log.d(TAG, "skip: app not in foreground, will rely on STICKY restart")
            return Result.success()
        }

        return try {
            MonitorService.start(applicationContext)
            Log.d(TAG, "keep-alive: service started")
            Result.success()
        } catch (e: ForegroundServiceStartNotAllowedException) {
            // 系统 Android 12+ 拒绝，不再 retry，避免每 15 分钟刷错误日志
            Log.w(TAG, "FGS not allowed, deferring to STICKY restart")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "keep-alive failed", e)
            Result.success()
        }
    }

    private fun isAppInForeground(): Boolean {
        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val processes = am.runningAppProcesses ?: return false
        return processes.any { it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
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
