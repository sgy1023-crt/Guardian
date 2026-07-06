package com.gangyi.guardian.data

import android.content.Context

/** 监控配置的本地存储；阶段四会迁移到 Room，目前用 SharedPreferences 够用。 */
class MonitorPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)

    /** 被监控的应用包名集合 */
    var monitoredPackages: Set<String>
        get() = sp.getStringSet(KEY_PACKAGES, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_PACKAGES, value).apply()

    /** 监控总开关（用户期望服务是否运行） */
    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 同一应用触发后的冷却秒数，默认 30 秒 */
    var cooldownSeconds: Int
        get() = sp.getInt(KEY_COOLDOWN, 30)
        set(value) = sp.edit().putInt(KEY_COOLDOWN, value).apply()

    /** 屏幕文字扫描间隔秒数，默认 1.5 秒。越小越灵敏越费电 */
    var screenScanIntervalSeconds: Float
        get() = sp.getFloat(KEY_SCAN_INTERVAL, 1.5f)
        set(value) = sp.edit().putFloat(KEY_SCAN_INTERVAL, value).apply()

    /** 弹窗倒计时秒数，默认 5 秒。倒计时结束才能点"我清醒了" */
    var overlayCountdownSeconds: Int
        get() = sp.getInt(KEY_OVERLAY_COUNTDOWN, 5)
        set(value) = sp.edit().putInt(KEY_OVERLAY_COUNTDOWN, value).apply()

    /** 关键词加密模式开关 */
    var keywordsEncrypted: Boolean
        get() = sp.getBoolean(KEY_KW_ENCRYPTED, false)
        set(value) = sp.edit().putBoolean(KEY_KW_ENCRYPTED, value).apply()

    /** 关键词加密密码的 hash（SHA-256 hex）。空字符串表示未设密码 */
    var keywordPasswordHash: String
        get() = sp.getString(KEY_KW_PASSWORD_HASH, "") ?: ""
        set(value) = sp.edit().putString(KEY_KW_PASSWORD_HASH, value).apply()

    /** 剪贴板关键词集合（任一子串命中即触发提醒） */
    var keywords: Set<String>
        get() = sp.getStringSet(KEY_KEYWORDS, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_KEYWORDS, value).apply()

    /** 用户是否已经走完一次权限引导（不论是否完整授权，跳过/完成都算） */
    var onboardingDone: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDING, value).apply()

    /** 提醒语模式：MODE_RANDOM = 每次随机抽；MODE_FIXED = 固定用某一条 */
    var reminderMode: String
        get() = sp.getString(KEY_REMINDER_MODE, MODE_RANDOM) ?: MODE_RANDOM
        set(value) = sp.edit().putString(KEY_REMINDER_MODE, value).apply()

    /** 固定提醒语对应的 Reminder id（Room 自增主键）；-1 表示未选 */
    var fixedReminderId: Long
        get() = sp.getLong(KEY_FIXED_REMINDER_ID, -1L)
        set(value) = sp.edit().putLong(KEY_FIXED_REMINDER_ID, value).apply()

    fun addPackage(pkg: String) {
        monitoredPackages = monitoredPackages + pkg
    }

    fun removePackage(pkg: String) {
        monitoredPackages = monitoredPackages - pkg
    }

    fun addKeyword(word: String) {
        keywords = keywords + word
    }

    fun removeKeyword(word: String) {
        keywords = keywords - word
    }

    private companion object {
        const val KEY_PACKAGES = "monitored_packages"
        const val KEY_ENABLED = "service_enabled"
        const val KEY_COOLDOWN = "cooldown_seconds"
        const val KEY_SCAN_INTERVAL = "screen_scan_interval"
        const val KEY_OVERLAY_COUNTDOWN = "overlay_countdown"
        const val KEY_KEYWORDS = "keywords"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_REMINDER_MODE = "reminder_mode"
        const val KEY_FIXED_REMINDER_ID = "fixed_reminder_id"
        const val KEY_KW_ENCRYPTED = "kw_encrypted"
        const val KEY_KW_PASSWORD_HASH = "kw_password_hash"
    }
}

const val MODE_RANDOM = "MODE_RANDOM"
const val MODE_FIXED = "MODE_FIXED"
