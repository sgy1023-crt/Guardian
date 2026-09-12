package com.gangyi.guardian.data

import android.content.Context

/**
 * 轻量配置的本地存储。监控 App / 关键词已迁移到 Room（见 GuardianRepository），
 * 这里只留服务开关、各项秒数、加密与引导标记这类真正的"设置项"。
 *
 * 机制相关的几项含义（详见 EscalationTracker）：
 * - overlayCountdownSeconds：弹窗出现后要停顿多久才能点"继续"（"退出"永远可点）
 * - passMinutes：点"继续"后放行多久，期间同一 App 不再打扰
 * - escalation*：滑动窗口内"继续"多少次就升级为封锁
 * - escalatingCountdown*：窗口内每多"继续"一次，下次停顿时间翻倍
 */
class MonitorPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)

    /** 监控总开关（用户期望服务是否运行） */
    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_ENABLED, value).apply()

    /** 屏幕文字扫描间隔秒数，默认 1.5 秒。越小越灵敏越费电 */
    var screenScanIntervalSeconds: Float
        get() = sp.getFloat(KEY_SCAN_INTERVAL, 1.5f)
        set(value) = sp.edit().putFloat(KEY_SCAN_INTERVAL, value).apply()

    /** 关键词只匹配输入框里的文字（自己打出来的才算），关掉则匹配整屏所有文字 */
    var keywordInputOnly: Boolean
        get() = sp.getBoolean(KEY_KW_INPUT_ONLY, true)
        set(value) = sp.edit().putBoolean(KEY_KW_INPUT_ONLY, value).apply()

    /** 弹窗停顿秒数，默认 5 秒。停顿结束才能点"继续" */
    var overlayCountdownSeconds: Int
        get() = sp.getInt(KEY_OVERLAY_COUNTDOWN, 5)
        set(value) = sp.edit().putInt(KEY_OVERLAY_COUNTDOWN, value).apply()

    /** 点"继续"后的放行分钟数，默认 5 分钟 */
    var passMinutes: Int
        get() = sp.getInt(KEY_PASS_MINUTES, 5)
        set(value) = sp.edit().putInt(KEY_PASS_MINUTES, value).apply()

    /** 递增停顿总开关：窗口内每多"继续"一次，下次停顿按倍数加长 */
    var escalatingCountdownEnabled: Boolean
        get() = sp.getBoolean(KEY_ESC_CD_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_ESC_CD_ENABLED, value).apply()

    /** 停顿翻倍系数，默认 2.0 倍。5s → 10s → 20s */
    var countdownMultiplier: Float
        get() = sp.getFloat(KEY_CD_MULTIPLIER, 2.0f)
        set(value) = sp.edit().putFloat(KEY_CD_MULTIPLIER, value).apply()

    /** 停顿翻倍的封顶秒数，默认 60 秒 */
    var countdownMaxSeconds: Int
        get() = sp.getInt(KEY_CD_MAX, 60)
        set(value) = sp.edit().putInt(KEY_CD_MAX, value).apply()

    /** 弹窗振动开关，默认开 */
    var alertVibrate: Boolean
        get() = sp.getBoolean(KEY_ALERT_VIBRATE, true)
        set(value) = sp.edit().putBoolean(KEY_ALERT_VIBRATE, value).apply()

    /** 弹窗提示音开关，默认开；默认跟随系统铃声模式（静音时不响） */
    var alertSound: Boolean
        get() = sp.getBoolean(KEY_ALERT_SOUND, true)
        set(value) = sp.edit().putBoolean(KEY_ALERT_SOUND, value).apply()

    /** 静音/振动模式下也出声（走闹钟通道）。默认关，避免会议/深夜炸出来 */
    var alertSoundInSilent: Boolean
        get() = sp.getBoolean(KEY_ALERT_SOUND_SILENT, false)
        set(value) = sp.edit().putBoolean(KEY_ALERT_SOUND_SILENT, value).apply()

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

    /** 升级封锁总开关：窗口内"继续"够次数就踢回桌面 + 定时封锁 */
    var escalationEnabled: Boolean
        get() = sp.getBoolean(KEY_ESC_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_ESC_ENABLED, value).apply()

    /** 窗口内第几次"继续"触发封锁，默认第 3 次（前两次各给一段通行） */
    var escalationThreshold: Int
        get() = sp.getInt(KEY_ESC_THRESHOLD, 3)
        set(value) = sp.edit().putInt(KEY_ESC_THRESHOLD, value).apply()

    /** 计数的滑动窗口分钟数，默认 30 分钟。超窗的旧记录自动丢弃 */
    var escalationWindowMinutes: Int
        get() = sp.getInt(KEY_ESC_WINDOW, 30)
        set(value) = sp.edit().putInt(KEY_ESC_WINDOW, value).apply()

    /** 封锁基础时长分钟数，默认 3 分钟。短时间内重复被封同一 App 会翻倍 */
    var lockdownMinutes: Int
        get() = sp.getInt(KEY_LOCKDOWN_MINUTES, 3)
        set(value) = sp.edit().putInt(KEY_LOCKDOWN_MINUTES, value).apply()

    /** 当前设置对应的强度档位；跟三档预设都不完全一致时返回 CUSTOM。 */
    fun currentPreset(): Preset =
        Preset.entries.firstOrNull { it != Preset.CUSTOM && it.matches(this) } ?: Preset.CUSTOM

    /** 一键套用预设。 */
    fun applyPreset(preset: Preset) {
        val v = preset.values ?: return
        overlayCountdownSeconds = v.countdown
        escalatingCountdownEnabled = v.escalating
        countdownMultiplier = 2.0f
        countdownMaxSeconds = v.countdownMax
        passMinutes = v.pass
        escalationEnabled = v.lockEnabled
        escalationThreshold = v.threshold
        escalationWindowMinutes = 30
        lockdownMinutes = v.lockMinutes
    }

    private companion object {
        const val KEY_ENABLED = "service_enabled"
        const val KEY_SCAN_INTERVAL = "screen_scan_interval"
        const val KEY_KW_INPUT_ONLY = "kw_input_only"
        const val KEY_OVERLAY_COUNTDOWN = "overlay_countdown"
        const val KEY_PASS_MINUTES = "pass_minutes"
        const val KEY_ESC_CD_ENABLED = "escalating_countdown_enabled"
        const val KEY_CD_MULTIPLIER = "countdown_multiplier"
        const val KEY_CD_MAX = "countdown_max_seconds"
        const val KEY_ALERT_VIBRATE = "alert_vibrate"
        const val KEY_ALERT_SOUND = "alert_sound"
        const val KEY_ALERT_SOUND_SILENT = "alert_sound_in_silent"
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

/** 强度预设。数值是产品决定，不是技术参数：温和=只提醒，标准=提醒+封锁，严格=更快封、封更久。 */
enum class Preset(
    val label: String,
    val summary: String,
    val values: PresetValues?
) {
    GENTLE(
        "温和",
        "只停顿提醒，不封锁。每次继续放行 15 分钟",
        PresetValues(countdown = 5, escalating = false, countdownMax = 60, pass = 15, lockEnabled = false, threshold = 3, lockMinutes = 3)
    ),
    STANDARD(
        "标准",
        "停顿 5 秒起、越继续等越久；30 分钟内第 3 次继续封锁 3 分钟",
        PresetValues(countdown = 5, escalating = true, countdownMax = 60, pass = 5, lockEnabled = true, threshold = 3, lockMinutes = 3)
    ),
    STRICT(
        "严格",
        "停顿 10 秒起；每次只放行 3 分钟；第 2 次继续就封锁 10 分钟",
        PresetValues(countdown = 10, escalating = true, countdownMax = 120, pass = 3, lockEnabled = true, threshold = 2, lockMinutes = 10)
    ),
    CUSTOM("自定义", "你在高级设置里改过参数", null);

    fun matches(p: MonitorPrefs): Boolean {
        val v = values ?: return false
        return p.overlayCountdownSeconds == v.countdown &&
            p.escalatingCountdownEnabled == v.escalating &&
            p.countdownMaxSeconds == v.countdownMax &&
            p.passMinutes == v.pass &&
            p.escalationEnabled == v.lockEnabled &&
            (!v.lockEnabled || (p.escalationThreshold == v.threshold && p.lockdownMinutes == v.lockMinutes)) &&
            p.escalationWindowMinutes == 30 &&
            p.countdownMultiplier == 2.0f
    }
}

data class PresetValues(
    val countdown: Int,
    val escalating: Boolean,
    val countdownMax: Int,
    val pass: Int,
    val lockEnabled: Boolean,
    val threshold: Int,
    val lockMinutes: Int
)
