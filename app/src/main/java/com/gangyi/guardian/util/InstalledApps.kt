package com.gangyi.guardian.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

object InstalledApps {

    /** 加载用户可见的、带启动入口的已安装应用，按名称排序。 */
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val launchables = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return launchables.asSequence()
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != context.packageName }
            .map {
                AppInfo(
                    packageName = it.packageName,
                    label = pm.getApplicationLabel(it).toString(),
                    icon = runCatching { pm.getApplicationIcon(it) }.getOrNull()
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
