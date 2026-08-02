package com.gangyi.guardian.data

import android.content.Context

/**
 * 轻量配置的本地存储。监控 App / 关键词已迁移到 Room（见 GuardianRepository），
 * 这里只留服务开关、各项秒数、加密与引导标记这类真正的"设置项"。
 */
class MonitorPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)

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

    /** 弹窗振动开关，默认开。别人察觉不到，但身体会记住 */
    var alertVibrate: Boolean
        get() = sp.getBoolean(KEY_ALERT_VIBRATE, true)
        set(value) = sp.edit().putBoolean(KEY_ALERT_VIBRATE, value).apply()

    /** 弹窗提示音开关，默认开。跟随系统静音，静音时只振动 */
    var alertSound: Boolean
        get() = sp.getBoolean(KEY_ALERT_SOUND, true)
        set(value) = sp.edit().putBoolean(KEY_ALERT_SOUND, value).apply()

    /** 关键词加密模式开关 */
    var keywordsEncrypted: Boolean
        get() = sp.getBoolean(KEY_KW_ENCRYPTED, false)
        set(value) = sp.edit().putBoolean(KEY_KW_ENCRYPTED, value).apply()

    /** 关键词加密密码的 hash（SHA-256 hex）。空字符串表示未设密码 */
    var keywordPasswordHash: String
        get() = sp.getString(KEY_KW_PASSWORD_HASH, "") ?: ""
        set(value) = sp.edit().putString(KEY_KW_PASSWORD_HASH, value).apply()

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

    /** 升级封锁总开关：反复点掉弹窗时是否升级为踢回桌面 + 定时封锁 */
    var escalationEnabled: Boolean
        get() = sp.getBoolean(KEY_ESC_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_ESC_ENABLED, value).apply()

    /** 窗口内弹够几次触发封锁，默认 3 次 */
    var escalationThreshold: Int
        get() = sp.getInt(KEY_ESC_THRESHOLD, 3)
        set(value) = sp.edit().putInt(KEY_ESC_THRESHOLD, value).apply()

    /** 计数的滑动窗口分钟数，默认 30 分钟。超窗的旧记录自动丢弃，不秋后算账 */
    var escalationWindowMinutes: Int
        get() = sp.getInt(KEY_ESC_WINDOW, 30)
        set(value) = sp.edit().putInt(KEY_ESC_WINDOW, value).apply()

    /** 封锁基础时长分钟数，默认 3 分钟。短时间内重复触发会翻倍 */
    var lockdownMinutes: Int
        get() = sp.getInt(KEY_LOCKDOWN_MINUTES, 3)
        set(value) = sp.edit().putInt(KEY_LOCKDOWN_MINUTES, value).apply()

    private companion object {
        const val KEY_ENABLED = "service_enabled"
        const val KEY_COOLDOWN = "cooldown_seconds"
        const val KEY_SCAN_INTERVAL = "screen_scan_interval"
        const val KEY_OVERLAY_COUNTDOWN = "overlay_countdown"
        const val KEY_ALERT_VIBRATE = "alert_vibrate"
        const val KEY_ALERT_SOUND = "alert_sound"
        const val KEY_ONBOARDING = "onboarding_done"
        const val KEY_REMINDER_MODE = "reminder_mode"
        const val KEY_FIXED_REMINDER_ID = "fixed_reminder_id"
        const val KEY_KW_ENCRYPTED = "kw_encrypted"
        const val KEY_KW_PASSWORD_HASH = "kw_password_hash"
        const val KEY_ESC_ENABLED = "escalation_enabled"
        const val KEY_ESC_THRESHOLD = "escalation_threshold"
        const val KEY_ESC_WINDOW = "escalation_window_minutes"
        const val KEY_LOCKDOWN_MINUTES = "lockdown_minutes"
    }
}

const val MODE_RANDOM = "MODE_RANDOM"
const val MODE_FIXED = "MODE_FIXED"
