package com.gangyi.guardian.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.Preset
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.guard.GuardSchedule
import com.gangyi.guardian.permission.PermissionState
import com.gangyi.guardian.ui.components.GuardianCard
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.components.GuardianDialog
import com.gangyi.guardian.ui.components.GuardianSlider
import com.gangyi.guardian.ui.components.GuardianTextField
import com.gangyi.guardian.ui.components.NavRow
import com.gangyi.guardian.ui.components.RowDivider
import com.gangyi.guardian.ui.components.ScreenHeader
import com.gangyi.guardian.ui.components.SectionLabel
import com.gangyi.guardian.ui.components.SwitchRow
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianDangerSoft
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import com.gangyi.guardian.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 设置页。给非技术用户的第一层是三档强度，一键切换；
 * 想细调的人展开"高级参数"，所有滑块都在那里面。
 */
@Composable
fun SettingsScreen(perms: PermissionState) {
    val context = LocalContext.current
    val prefs = remember { MonitorPrefs(context) }
    val repo = remember { GuardianRepository(context) }
    val scope = rememberCoroutineScope()

    // 机制参数：改任何一项都要重算当前档位
    var countdown by remember { mutableFloatStateOf(prefs.overlayCountdownSeconds.toFloat()) }
    var passMin by remember { mutableFloatStateOf(prefs.passMinutes.toFloat()) }
    var escCdEnabled by remember { mutableStateOf(prefs.escalatingCountdownEnabled) }
    var cdMultiplier by remember { mutableFloatStateOf(prefs.countdownMultiplier) }
    var cdMax by remember { mutableFloatStateOf(prefs.countdownMaxSeconds.toFloat()) }
    var escEnabled by remember { mutableStateOf(prefs.escalationEnabled) }
    var escThreshold by remember { mutableFloatStateOf(prefs.escalationThreshold.toFloat()) }
    var escWindow by remember { mutableFloatStateOf(prefs.escalationWindowMinutes.toFloat()) }
    var lockMinutes by remember { mutableFloatStateOf(prefs.lockdownMinutes.toFloat()) }
    var preset by remember { mutableStateOf(prefs.currentPreset()) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    fun reloadMechanics() {
        countdown = prefs.overlayCountdownSeconds.toFloat()
        passMin = prefs.passMinutes.toFloat()
        escCdEnabled = prefs.escalatingCountdownEnabled
        cdMultiplier = prefs.countdownMultiplier
        cdMax = prefs.countdownMaxSeconds.toFloat()
        escEnabled = prefs.escalationEnabled
        escThreshold = prefs.escalationThreshold.toFloat()
        escWindow = prefs.escalationWindowMinutes.toFloat()
        lockMinutes = prefs.lockdownMinutes.toFloat()
        preset = prefs.currentPreset()
    }

    // 提醒方式
    var alertVibrate by remember { mutableStateOf(prefs.alertVibrate) }
    var alertSound by remember { mutableStateOf(prefs.alertSound) }
    var soundInSilent by remember { mutableStateOf(prefs.alertSoundInSilent) }

    // 守护时段
    var scheduleEnabled by remember { mutableStateOf(prefs.scheduleEnabled) }
    var schedStart by remember { mutableIntStateOf(prefs.scheduleStartMinutes) }
    var schedEnd by remember { mutableIntStateOf(prefs.scheduleEndMinutes) }
    var schedDays by remember { mutableStateOf(prefs.scheduleDays) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    // 关键词
    var inputOnly by remember { mutableStateOf(prefs.keywordInputOnly) }
    var scanInterval by remember { mutableFloatStateOf(prefs.screenScanIntervalSeconds) }
    var encrypted by remember { mutableStateOf(prefs.keywordsEncrypted) }
    var showSetPwd by remember { mutableStateOf(false) }
    var showVerifyPwd by remember { mutableStateOf(false) }

    var exporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenHeader("设置", "守卫对你有多严")
        Spacer(Modifier.height(20.dp))

        // ================= 强度
        SectionLabel("强度")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Preset.GENTLE, Preset.STANDARD, Preset.STRICT).forEach { p ->
                PresetCard(
                    preset = p,
                    selected = preset == p,
                    onClick = {
                        prefs.applyPreset(p)
                        reloadMechanics()
                    }
                )
            }
        }
        if (preset == Preset.CUSTOM) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                GuardianChip("自定义", GuardianAccent, GuardianAccentSoft)
                Spacer(Modifier.width(8.dp))
                Text("你改过高级参数，跟三档都不一样", fontSize = 12.sp, color = GuardianTextFaint)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ================= 高级参数（折叠）
        GuardianCard(contentPadding = PaddingValues(0.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("高级参数", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = GuardianText)
                    Text(
                        "停顿 ${countdown.toInt()} 秒 · 放行 ${passMin.toInt()} 分钟 · " +
                            if (escEnabled) "第 ${escThreshold.toInt()} 次继续封锁 ${lockMinutes.toInt()} 分钟" else "不封锁",
                        fontSize = 12.sp, color = GuardianTextDim
                    )
                }
                Icon(
                    if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null, tint = GuardianTextDim
                )
            }
            AnimatedVisibility(visible = showAdvanced) {
                Column {
                    RowDivider()
                    Spacer(Modifier.height(4.dp))
                    GuardianSlider(
                        label = "停顿时长",
                        valueText = "${countdown.toInt()} 秒",
                        hint = "停顿点弹出后，要等这么久才能点「继续」。「退出」随时可点",
                        value = countdown, valueRange = 3f..60f,
                        onValueChange = { countdown = it },
                        onValueChangeFinished = { prefs.overlayCountdownSeconds = countdown.toInt(); preset = prefs.currentPreset() }
                    )
                    GuardianSlider(
                        label = "继续后放行",
                        valueText = "${passMin.toInt()} 分钟",
                        hint = "点「继续」后这段时间内同一应用不再打扰，到时再弹",
                        value = passMin, valueRange = 1f..30f,
                        onValueChange = { passMin = it },
                        onValueChangeFinished = { prefs.passMinutes = passMin.toInt(); preset = prefs.currentPreset() }
                    )
                    RowDivider()
                    SwitchRow(
                        title = "越继续、停顿越久",
                        subtitle = "每多继续一次，下次停顿翻倍：${countdown.toInt()} → ${(countdown * cdMultiplier).toInt().coerceAtMost(cdMax.toInt())} → ${(countdown * cdMultiplier * cdMultiplier).toInt().coerceAtMost(cdMax.toInt())} 秒",
                        checked = escCdEnabled,
                        onCheckedChange = { escCdEnabled = it; prefs.escalatingCountdownEnabled = it; preset = prefs.currentPreset() }
                    )
                    AnimatedVisibility(visible = escCdEnabled) {
                        Column {
                            GuardianSlider(
                                label = "翻倍系数",
                                valueText = String.format("%.1f 倍", cdMultiplier),
                                value = cdMultiplier, valueRange = 1.2f..4f,
                                onValueChange = { cdMultiplier = it },
                                onValueChangeFinished = { prefs.countdownMultiplier = cdMultiplier; preset = prefs.currentPreset() }
                            )
                            GuardianSlider(
                                label = "停顿封顶",
                                valueText = "${cdMax.toInt()} 秒",
                                hint = "翻倍最多加长到这里",
                                value = cdMax, valueRange = 30f..600f,
                                onValueChange = { cdMax = it },
                                onValueChangeFinished = { prefs.countdownMaxSeconds = cdMax.toInt(); preset = prefs.currentPreset() }
                            )
                        }
                    }
                    RowDivider()
                    SwitchRow(
                        title = "反复继续时封锁",
                        subtitle = "短时间内多次选择继续，说明提醒已经没用了。此时把你送回桌面，并暂时锁住那个应用",
                        icon = Icons.Rounded.Lock,
                        danger = true,
                        checked = escEnabled,
                        onCheckedChange = { escEnabled = it; prefs.escalationEnabled = it; preset = prefs.currentPreset() }
                    )
                    AnimatedVisibility(visible = escEnabled) {
                        Column {
                            if (!perms.accessibility) {
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(GuardianDangerSoft)
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Accessibility, null, tint = GuardianDanger, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "没开无障碍权限，封锁时没法自动送你回桌面，只能挡一堵墙。去「守护」页开启。",
                                            fontSize = 12.sp, color = GuardianDanger, lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                            GuardianSlider(
                                label = "第几次继续就封锁",
                                valueText = "第 ${escThreshold.toInt()} 次",
                                hint = "前面几次各给一段放行；设为 1 = 第一次点继续就封",
                                value = escThreshold, valueRange = 1f..6f,
                                onValueChange = { escThreshold = it },
                                onValueChangeFinished = { prefs.escalationThreshold = escThreshold.toInt(); preset = prefs.currentPreset() }
                            )
                            GuardianSlider(
                                label = "计数窗口",
                                valueText = "${escWindow.toInt()} 分钟",
                                hint = "只数最近这段时间内的次数；真去干别的事了，计数自动清零",
                                value = escWindow, valueRange = 5f..120f,
                                onValueChange = { escWindow = it },
                                onValueChangeFinished = { prefs.escalationWindowMinutes = escWindow.toInt(); preset = prefs.currentPreset() }
                            )
                            GuardianSlider(
                                label = "封锁时长",
                                valueText = "${lockMinutes.toInt()} 分钟",
                                hint = "2 小时内再次被封同一个应用，时长翻倍（最多 4 倍）",
                                value = lockMinutes, valueRange = 1f..30f,
                                onValueChange = { lockMinutes = it },
                                onValueChangeFinished = { prefs.lockdownMinutes = lockMinutes.toInt(); preset = prefs.currentPreset() }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================= 守护时段
        SectionLabel("守护时段")
        GuardianCard(contentPadding = PaddingValues(0.dp)) {
            SwitchRow(
                title = "只在特定时段守护",
                subtitle = if (scheduleEnabled) GuardSchedule.describe(prefs) + "，其余时间不弹不计数"
                else "关闭时全天守护。工作日上班时间、每晚睡前，这类需求打开它",
                icon = Icons.Rounded.Schedule,
                checked = scheduleEnabled,
                onCheckedChange = { scheduleEnabled = it; prefs.scheduleEnabled = it }
            )
            AnimatedVisibility(visible = scheduleEnabled) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeCell("开始", GuardSchedule.formatHm(schedStart), Modifier.weight(1f)) { pickingStart = true }
                        TimeCell(
                            if (schedEnd <= schedStart && schedEnd != schedStart) "结束（次日）" else "结束",
                            GuardSchedule.formatHm(schedEnd), Modifier.weight(1f)
                        ) { pickingEnd = true }
                    }
                    if (schedStart == schedEnd) {
                        Spacer(Modifier.height(6.dp))
                        Text("开始和结束相同 = 选中的那几天全天守护", fontSize = 12.sp, color = GuardianTextFaint)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("守护日", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GuardianTextFaint)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GuardSchedule.dayOrder().forEach { (day, label) ->
                            val on = day in schedDays
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) GuardianAccent else GuardianSurface2)
                                    .clickable {
                                        val next = if (on) schedDays - day else schedDays + day
                                        schedDays = next
                                        prefs.scheduleDays = next
                                    }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) GuardianBg else GuardianTextDim)
                            }
                        }
                    }
                    if (schedDays.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("一天都没选，守卫不会在任何时候工作", fontSize = 12.sp, color = GuardianDanger)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickChip("工作时间 · 周一至五 9–18") {
                            prefs.scheduleStartMinutes = 9 * 60; prefs.scheduleEndMinutes = 18 * 60
                            prefs.scheduleDays = GuardSchedule.WEEKDAYS
                            schedStart = 9 * 60; schedEnd = 18 * 60; schedDays = GuardSchedule.WEEKDAYS
                        }
                        QuickChip("睡前 · 每天 22–次日 6") {
                            prefs.scheduleStartMinutes = 22 * 60; prefs.scheduleEndMinutes = 6 * 60
                            prefs.scheduleDays = MonitorPrefs.ALL_DAYS
                            schedStart = 22 * 60; schedEnd = 6 * 60; schedDays = MonitorPrefs.ALL_DAYS
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================= 提醒方式
        SectionLabel("提醒方式")
        GuardianCard(contentPadding = PaddingValues(0.dp)) {
            SwitchRow(
                title = "振动",
                subtitle = "停顿点弹出时短促振两下",
                icon = Icons.Rounded.Vibration,
                checked = alertVibrate,
                onCheckedChange = { alertVibrate = it; prefs.alertVibrate = it }
            )
            RowDivider(66.dp)
            SwitchRow(
                title = "提示音",
                subtitle = "音量已压低，跟随系统铃声模式",
                icon = Icons.Rounded.VolumeUp,
                checked = alertSound,
                onCheckedChange = { alertSound = it; prefs.alertSound = it }
            )
            AnimatedVisibility(visible = alertSound) {
                Column {
                    RowDivider(66.dp)
                    SwitchRow(
                        title = "静音时也响",
                        subtitle = "走闹钟通道，手机静音/振动模式下照样出声。开会、深夜慎用",
                        icon = Icons.Rounded.VolumeOff,
                        checked = soundInSilent,
                        onCheckedChange = { soundInSilent = it; prefs.alertSoundInSilent = it }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ================= 关键词
        SectionLabel("关键词")
        GuardianCard(contentPadding = PaddingValues(0.dp)) {
            SwitchRow(
                title = "只看你输入的文字",
                subtitle = if (inputOnly) "自己打出关键词才提醒。别人发来的、页面上出现的不算"
                else "整屏任何文字出现关键词都提醒。容易误触发，也会把别人的消息算到你头上",
                icon = Icons.Rounded.Keyboard,
                checked = inputOnly,
                onCheckedChange = { inputOnly = it; prefs.keywordInputOnly = it }
            )
            RowDivider(66.dp)
            SwitchRow(
                title = "加密模式",
                subtitle = "关键词列表遮罩显示，停顿点不显示原文，删除和导出都要密码",
                icon = Icons.Rounded.Lock,
                checked = encrypted,
                onCheckedChange = { want ->
                    if (want) {
                        showSetPwd = true
                    } else if (prefs.keywordPasswordHash.isEmpty()) {
                        encrypted = false
                        prefs.keywordsEncrypted = false
                    } else {
                        showVerifyPwd = true
                    }
                }
            )
            AnimatedVisibility(visible = encrypted) {
                Column {
                    RowDivider(66.dp)
                    NavRow(
                        title = "修改密码",
                        icon = Icons.Rounded.Password,
                        tint = GuardianTextDim,
                        onClick = { showSetPwd = true }
                    )
                }
            }
            RowDivider(66.dp)
            GuardianSlider(
                label = "扫描间隔",
                valueText = String.format("%.1f 秒", scanInterval),
                hint = "多久看一次屏幕。越小越灵敏越费电，1.5 秒够用",
                value = scanInterval, valueRange = 1f..10f,
                onValueChange = { scanInterval = it },
                onValueChangeFinished = { prefs.screenScanIntervalSeconds = scanInterval }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ================= 数据
        SectionLabel("数据")
        GuardianCard(contentPadding = PaddingValues(0.dp)) {
            NavRow(
                title = if (exporting) "导出中…" else "导出停顿记录",
                subtitle = if (encrypted) "JSON 格式；加密模式下关键词会打码" else "JSON 格式，含每次停顿的时间和你的决定",
                icon = Icons.Rounded.IosShare,
                onClick = {
                    if (exporting) return@NavRow
                    scope.launch {
                        exporting = true
                        try {
                            val uri = exportLogs(context, repo, maskKeywords = prefs.keywordsEncrypted)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "导出停顿记录"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            exporting = false
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(24.dp))

        // ================= 关于
        SectionLabel("关于")
        GuardianCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(GuardianAccentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Notifications, null, tint = GuardianAccent, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("守卫 Guardian", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GuardianText)
                    Text("v${versionName(context)} · 本地运行 · 不联网不上传", fontSize = 12.sp, color = GuardianTextDim)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "灵感来自 one sec。不锁机、不说教，只在你伸手的那一刻，给你一个停顿。",
                fontSize = 12.sp, color = GuardianTextFaint, lineHeight = 18.sp
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (pickingStart) {
        TimePickerDialog(
            title = "守护开始",
            initialMinutes = schedStart,
            onDismiss = { pickingStart = false },
            onConfirm = { m -> schedStart = m; prefs.scheduleStartMinutes = m; pickingStart = false }
        )
    }
    if (pickingEnd) {
        TimePickerDialog(
            title = "守护结束",
            initialMinutes = schedEnd,
            onDismiss = { pickingEnd = false },
            onConfirm = { m -> schedEnd = m; prefs.scheduleEndMinutes = m; pickingEnd = false }
        )
    }

    if (showVerifyPwd) {
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }
        GuardianDialog(
            onDismiss = { showVerifyPwd = false },
            title = "验证密码",
            confirmText = "关闭加密",
            onConfirm = {
                if (sha256(input.trim()) == prefs.keywordPasswordHash) {
                    encrypted = false
                    prefs.keywordsEncrypted = false
                    showVerifyPwd = false
                    Toast.makeText(context, "加密模式已关闭", Toast.LENGTH_SHORT).show()
                } else {
                    error = true
                }
            }
        ) {
            Column {
                Text("关闭加密模式需要输入密码", fontSize = 13.sp, color = GuardianTextDim)
                Spacer(Modifier.height(12.dp))
                GuardianTextField(
                    value = input, onValueChange = { input = it; error = false },
                    placeholder = "4~6 位数字", isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.NumberPassword
                )
                if (error) {
                    Spacer(Modifier.height(6.dp))
                    Text("密码错误", fontSize = 12.sp, color = GuardianDanger)
                }
            }
        }
    }

    if (showSetPwd) {
        var pwd1 by remember { mutableStateOf("") }
        var pwd2 by remember { mutableStateOf("") }
        var pwdError by remember { mutableStateOf("") }
        val firstTime = prefs.keywordPasswordHash.isEmpty()
        GuardianDialog(
            onDismiss = { showSetPwd = false },
            title = if (firstTime) "设置密码" else "修改密码",
            confirmText = "确定",
            onConfirm = {
                when {
                    pwd1.length < 4 || pwd1.length > 6 -> pwdError = "密码长度 4~6 位"
                    !pwd1.all { it.isDigit() } -> pwdError = "只能是数字"
                    pwd1 != pwd2 -> pwdError = "两次输入不一致"
                    else -> {
                        prefs.keywordPasswordHash = sha256(pwd1)
                        prefs.keywordsEncrypted = true
                        encrypted = true
                        showSetPwd = false
                        Toast.makeText(context, if (firstTime) "加密模式已开启" else "密码已修改", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        ) {
            Column {
                GuardianTextField(
                    value = pwd1, onValueChange = { pwd1 = it; pwdError = "" },
                    placeholder = "4~6 位数字", isError = pwdError.isNotEmpty(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.NumberPassword
                )
                Spacer(Modifier.height(10.dp))
                GuardianTextField(
                    value = pwd2, onValueChange = { pwd2 = it; pwdError = "" },
                    placeholder = "再输一次", isError = pwdError.isNotEmpty(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.NumberPassword
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    pwdError.ifEmpty { "记牢它。忘了密码没有找回途径，只能清除应用数据重来。" },
                    fontSize = 12.sp,
                    color = if (pwdError.isNotEmpty()) GuardianDanger else GuardianTextFaint,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun PresetCard(preset: Preset, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) GuardianAccentSoft else GuardianSurface)
            .border(1.dp, if (selected) GuardianAccent.copy(alpha = 0.6f) else GuardianBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) GuardianAccent else GuardianSurface2)
                .border(1.dp, if (selected) GuardianAccent else GuardianBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(GuardianBg))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                preset.label,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = if (selected) GuardianAccent else GuardianText
            )
            Spacer(Modifier.height(2.dp))
            Text(preset.summary, fontSize = 12.sp, color = GuardianTextDim, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun TimeCell(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GuardianSurface2)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = GuardianTextFaint)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GuardianAccent)
    }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GuardianAccentSoft)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GuardianAccent)
    }
}

/** 时间选择：用 Dialog + Surface 自己包 TimePicker，AlertDialog 的宽度装不下表盘。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = GuardianSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GuardianBorder)
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GuardianText, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = GuardianSurface2,
                        clockDialSelectedContentColor = GuardianBg,
                        clockDialUnselectedContentColor = GuardianText,
                        selectorColor = GuardianAccent,
                        containerColor = GuardianSurface,
                        timeSelectorSelectedContainerColor = GuardianAccentSoft,
                        timeSelectorUnselectedContainerColor = GuardianSurface2,
                        timeSelectorSelectedContentColor = GuardianAccent,
                        timeSelectorUnselectedContentColor = GuardianText
                    )
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消", color = GuardianTextDim) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                        Text("确定", color = GuardianAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun versionName(context: android.content.Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"

private suspend fun exportLogs(
    context: android.content.Context,
    repo: GuardianRepository,
    maskKeywords: Boolean
): android.net.Uri = withContext(Dispatchers.IO) {
    val logs = repo.allLogs()
    val arr = JSONArray()
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    logs.forEach { log ->
        val obj = JSONObject()
        obj.put("id", log.id)
        obj.put("packageName", log.packageName ?: "")
        obj.put("triggerType", log.triggerType)
        obj.put("keyword", when {
            log.keyword == null -> ""
            maskKeywords -> "•".repeat(log.keyword.length.coerceAtMost(8))
            else -> log.keyword
        })
        obj.put("decision", log.decision ?: "")
        obj.put("timestamp", fmt.format(java.util.Date(log.timestamp)))
        obj.put("dismissedAt", log.dismissedAt?.let { fmt.format(java.util.Date(it)) } ?: "")
        arr.put(obj)
    }
    val file = File(context.cacheDir, "guardian_export_${System.currentTimeMillis()}.json")
    file.writeText(arr.toString(2))
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
