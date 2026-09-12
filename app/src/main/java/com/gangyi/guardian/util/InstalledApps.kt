package com.gangyi.guardian.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppInfo(
    val packageName: String,
    val label: String
)

object InstalledApps {

    /** 图标按需加载并缓存：几百个 App 一次性把 Drawable 全解出来又慢又占内存。 */
    private val iconCache = LruCache<String, ImageBitmap>(200)

    /** 加载用户可见的、带启动入口的已安装应用（不含图标），按名称排序。IO 线程调用。 */
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val launchables = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return launchables.asSequence()
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .filter { it.packageName != context.packageName }
            .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** 单个应用的显示名，查不到就退回包名。 */
    fun label(context: Context, pkg: String): String {
        val pm = context.packageManager
        return runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    /** 单个应用的图标位图，带缓存。IO 线程调用。 */
    fun icon(context: Context, pkg: String): ImageBitmap? {
        iconCache.get(pkg)?.let { return it }
        val pm = context.packageManager
        val bmp = runCatching {
            pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull() ?: return null
        iconCache.put(pkg, bmp)
        return bmp
    }
}
