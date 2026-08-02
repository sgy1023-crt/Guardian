package com.gangyi.guardian.guard

import android.content.Context
import android.content.Intent

/**
 * 封锁安全阀：判断一个包名到底能不能被封锁。
 *
 * 这是整个封锁功能里最要紧的防灾代码。一旦误封桌面，就会陷入
 * "回桌面 → 被踢回桌面 → 再被踢" 的死循环，手机直接不可用。
 * 所以封锁前**必须**先过这道闸。
 */
object SystemPackages {

    @Volatile
    private var cachedLauncher: String? = null

    /** 当前默认桌面的包名。运行时解析（各家 ROM 桌面包名都不一样，硬编码盖不全）。 */
    fun launcherPackage(context: Context): String? {
        cachedLauncher?.let { return it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = runCatching {
            context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
        }.getOrNull()
        if (pkg != null) cachedLauncher = pkg
        return pkg
    }

    /** 用户换了默认桌面时清缓存。 */
    fun invalidate() {
        cachedLauncher = null
    }

    /**
     * 绝不封锁的包：系统框架、系统界面、设置、各家桌面、电话与紧急呼叫。
     * 封了它们会让手机丧失基本可用性，甚至打不出急救电话。
     */
    private val NEVER_LOCK = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        // 桌面（resolveActivity 兜不住时的硬防线）
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.miui.home",
        "com.mi.android.globallauncher",
        "com.huawei.android.launcher",
        "com.bbk.launcher2",
        "com.vivo.launcher",
        "com.oppo.launcher",
        "com.coloros.launcher",
        "com.realme.launcher",
        "com.sec.android.app.launcher",
        // 安全中心/权限管理：封了会导致用户改不回设置
        "com.miui.securitycenter",
        "com.iqoo.secure",
        "com.coloros.safecenter",
        "com.huawei.systemmanager",
        // 通话与紧急
        "com.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.contacts",
        "com.android.emergency",
        "com.google.android.dialer"
    )

    /** 唯一入口：这个包允许被封锁吗？ */
    fun isLockable(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (pkg == context.packageName) return false            // 不封自己，否则改不了设置
        if (pkg in NEVER_LOCK) return false
        if (pkg == launcherPackage(context)) return false        // 死循环防线
        if (pkg.startsWith("com.android.inputmethod")) return false
        return true
    }
}
