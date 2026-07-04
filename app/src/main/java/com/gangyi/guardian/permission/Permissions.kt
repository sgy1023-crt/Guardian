package com.gangyi.guardian.permission

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.gangyi.guardian.service.ClipboardWatcherService

/** 集中处理守卫需要的各项权限的检测与跳转。 */
object Permissions {

    /** 用量访问权限（检测前台 App 的主引擎，需用户在系统设置手动授予）。 */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** 悬浮窗权限（弹出提醒弹窗）。 */
    fun hasOverlay(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun overlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )

    /** 无障碍权限（剪贴板监控所必需，因为 Android 10+ 限制了后台读剪贴板）。 */
    fun hasAccessibility(context: Context): Boolean {
        val expected = ComponentName(context, ClipboardWatcherService::class.java)
            .flattenToString()

        // 优先用启用列表字符串匹配（更可靠，覆盖所有 ROM）
        val enabledStr = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        if (enabledStr.isNotEmpty()) {
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledStr)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) return true
            }
        }

        // 兜底：AccessibilityManager 列表
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.installedAccessibilityServiceList.any {
            it.id.equals(expected, ignoreCase = true) ||
                it.resolveInfo?.serviceInfo?.let { si ->
                    si.packageName == context.packageName &&
                        si.name == ClipboardWatcherService::class.java.name
                } == true
        } && am.isEnabled
    }

    fun accessibilityIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** 电池优化白名单 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * 自启动权限是国产 ROM 私有特性，没有公开 API 检测，只能"按需跳"。
     * 各厂商自启动设置页 component 名通过逆向得到，老型号可能找不到对应页面，
     * 失败时兜底到应用详情页让用户自己找。
     */
    fun autoStartIntent(context: Context): Intent {
        val brand = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()
        val candidates = mutableListOf<ComponentName>()
        when {
            brand.contains("xiaomi") || brand.contains("redmi") -> {
                candidates += ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            brand.contains("huawei") || brand.contains("honor") -> {
                candidates += ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
                candidates += ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                )
            }
            brand.contains("oppo") || brand.contains("realme") -> {
                candidates += ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                )
                candidates += ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity"
                )
            }
            brand.contains("vivo") || brand.contains("iqoo") -> {
                candidates += ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                )
                candidates += ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                )
            }
            brand.contains("samsung") -> {
                candidates += ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
            }
        }
        for (cn in candidates) {
            val intent = Intent().apply {
                component = cn
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) return intent
        }
        // 兜底：应用详情页
        return Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }
}
