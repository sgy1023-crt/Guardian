package com.gangyi.guardian.overlay

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.gangyi.guardian.data.MonitorPrefs

/**
 * 弹窗的"体感提醒"：声音 + 振动。
 *
 * 为什么要这个：纯视觉弹窗太容易被无视——人可以眼睛扫过去、手指条件反射点掉，
 * 大脑根本没参与。加一层听觉/触觉信号，才会真的"愣一下"。
 *
 * **声音走闹钟通道（USAGE_ALARM）**，这是刻意的：
 * 手机长期静音是常态，普通通知音在静音下根本不响，那这个功能就等于没有。
 * 闹钟通道在静音/振动模式下依然出声（Android 就是这么设计的，防止静音后睡过头），
 * 才能保证"该响的时候一定响"。
 *
 * 但音量单独压低到 35%，不跟系统闹钟音量走——否则会像闹铃一样炸出来，
 * 在公共场合很尴尬。目标是"自己听得清、旁人不注意"。
 *
 * 用系统自带音色而非自带音频文件：不增加 APK 体积，而且这声音本来就在
 * 用户手机里天天响，旁人听到只当是普通提示。
 */
object AlertFeedback {

    private const val TAG = "GuardianAlert"

    /** 提示音音量：0~1。刻意压低，不跟系统闹钟音量走，避免像闹铃一样炸出来 */
    private const val VOLUME_REMIND = 0.35f
    private const val VOLUME_LOCKDOWN = 0.55f

    /**
     * 普通停顿提醒：两下短促顿挫。
     * 像被人轻轻拍了下肩膀，不是持续嗡嗡的骚扰感。
     */
    private val PATTERN_REMIND = longArrayOf(0, 60, 90, 60)

    /**
     * 封锁提醒：三下递增的重击。
     * 刻意跟普通提醒区分开，形成"这次不一样"的条件反射。
     */
    private val PATTERN_LOCKDOWN = longArrayOf(0, 120, 80, 120, 80, 260)

    fun onRemind(context: Context) = fire(context, PATTERN_REMIND, lockdown = false)

    fun onLockdown(context: Context) = fire(context, PATTERN_LOCKDOWN, lockdown = true)

    private fun fire(context: Context, pattern: LongArray, lockdown: Boolean) {
        val prefs = MonitorPrefs(context)
        if (prefs.alertVibrate) vibrate(context, pattern, lockdown)
        if (prefs.alertSound) playTone(context, lockdown)
    }

    private fun vibrate(context: Context, pattern: LongArray, lockdown: Boolean) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 振幅拉满，封锁比普通提醒更重
                val amp = if (lockdown) 255 else 180
                val amplitudes = IntArray(pattern.size) { i -> if (i % 2 == 0) 0 else amp }
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }.onFailure { Log.w(TAG, "振动失败", it) }
    }

    /**
     * 播放提示音。
     *
     * 走 USAGE_ALARM 通道：手机静音时依然出声。这是这个功能的命门——
     * 长期静音是常态，若跟随静音，声音提醒等于默认关闭。
     *
     * 用 MediaPlayer 而非 RingtoneManager.getRingtone().play()：
     * 后者没法精确控音量，会直接用系统闹钟音量炸出来。这里手动压到 35%。
     */
    private fun playTone(context: Context, lockdown: Boolean) {
        runCatching {
            // 通知音短促清脆，适合做提示；封锁时用同一个音色但音量抬高，
            // 不用闹钟音色是因为那个通常是长循环铃声，掐断了也突兀。
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return

            val volume = if (lockdown) VOLUME_LOCKDOWN else VOLUME_REMIND

            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // ALARM 通道：静音模式下照样响
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context.applicationContext, uri)
                setVolume(volume, volume)
                isLooping = false
                setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                }
                setOnErrorListener { mp, _, _ ->
                    runCatching { mp.release() }
                    true
                }
                prepare()
                start()

                // 兜底回收：万一 completion 回调没来（有些 ROM 会吞），
                // 3 秒后强制释放，避免 MediaPlayer 泄漏
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    runCatching { if (isPlaying) stop() }
                    runCatching { release() }
                }, 3000L)
            }
        }.onFailure { Log.w(TAG, "提示音失败", it) }
    }
}
