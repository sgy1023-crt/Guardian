package com.gangyi.guardian.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.StudyBlock
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { MonitorPrefs(context) }
    val repo = remember { GuardianRepository(context) }
    val scope = rememberCoroutineScope()

    var cooldown by remember { mutableStateOf(prefs.cooldownSeconds.toFloat()) }
    var scanInterval by remember { mutableStateOf(prefs.screenScanIntervalSeconds) }
    var overlayCountdown by remember { mutableStateOf(prefs.overlayCountdownSeconds.toFloat()) }
    var defaultTimeLimit by remember { mutableStateOf(prefs.defaultTimeLimitSeconds.toFloat()) }
    var extensionSecs by remember { mutableStateOf(prefs.extensionSeconds.toFloat()) }
    var maxExtensions by remember { mutableStateOf(prefs.maxExtensionCount.toFloat()) }
    var studyEnabled by remember { mutableStateOf(prefs.studyBlockEnabled) }
    val studyBlocks = remember { androidx.compose.runtime.mutableStateListOf<StudyBlock>() }
    // 初始化：从 prefs 加载已有时段
    LaunchedEffect(Unit) { studyBlocks.addAll(prefs.studyBlocks) }
    // 每次修改后同步到 prefs
    fun syncStudyBlocks() { prefs.studyBlocks = studyBlocks.toList() }
    var exporting by remember { mutableStateOf(false) }
    var encrypted by remember { mutableStateOf(prefs.keywordsEncrypted) }
    var showSetPwd by remember { mutableStateOf(false) }
    var pwd1 by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var showVerifyPwd by remember { mutableStateOf(false) }
    var verifyInput by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                tint = GuardianText, modifier = Modifier.size(28.dp).clickable { onBack() }
            )
            Spacer(Modifier.weight(1f))
            Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianText)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(28.dp))
        }
        Spacer(Modifier.height(28.dp))

        // 冷却时间
        Text("提醒冷却", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("同一 App / 关键词触发后冷却", fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
                    Text("${cooldown.roundToInt()} 秒", fontSize = 14.sp, color = GuardianAccent)
                }
                Slider(
                    value = cooldown,
                    onValueChange = { cooldown = it },
                    onValueChangeFinished = { prefs.cooldownSeconds = cooldown.roundToInt() },
                    valueRange = 5f..300f,
                    steps = 58,
                    colors = SliderDefaults.colors(
                        thumbColor = GuardianAccent,
                        activeTrackColor = GuardianAccent,
                        inactiveTrackColor = GuardianBg
                    )
                )
                Text(
                    "弹窗关闭后这段时间内，同一关键词不再触发（避免反复弹）",
                    fontSize = 12.sp, color = GuardianTextFaint
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 屏幕扫描间隔
        Text("屏幕扫描灵敏度", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("扫描间隔", fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
                    Text(String.format("%.1f 秒", scanInterval), fontSize = 14.sp, color = GuardianAccent)
                }
                Slider(
                    value = scanInterval,
                    onValueChange = { scanInterval = it },
                    onValueChangeFinished = { prefs.screenScanIntervalSeconds = scanInterval },
                    valueRange = 1f..10f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = GuardianAccent,
                        activeTrackColor = GuardianAccent,
                        inactiveTrackColor = GuardianBg
                    )
                )
                Text(
                    "越小越灵敏越费电；1.5 秒够用，慢机器可调到 3~5 秒",
                    fontSize = 12.sp, color = GuardianTextFaint
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 弹窗倒计时
        Text("弹窗倒计时", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("倒计时秒数", fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
                    Text("${overlayCountdown.roundToInt()} 秒", fontSize = 14.sp, color = GuardianAccent)
                }
                Slider(
                    value = overlayCountdown,
                    onValueChange = { overlayCountdown = it },
                    onValueChangeFinished = { prefs.overlayCountdownSeconds = overlayCountdown.roundToInt() },
                    valueRange = 3f..180f,
                    steps = 176,
                    colors = SliderDefaults.colors(
                        thumbColor = GuardianAccent,
                        activeTrackColor = GuardianAccent,
                        inactiveTrackColor = GuardianBg
                    )
                )
                Text(
                    "弹窗出现后必须冷静这么多秒才能点关闭，默认 5 秒，最高 3 分钟",
                    fontSize = 12.sp, color = GuardianTextFaint
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 限时使用（意图声明模式）
        Text("限时使用", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("默认使用时长", fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
                    Text("${defaultTimeLimit.roundToInt() / 60} 分钟", fontSize = 14.sp, color = GuardianAccent)
                }
                Slider(
                    value = defaultTimeLimit,
                    onValueChange = { defaultTimeLimit = it },
                    onValueChangeFinished = { prefs.defaultTimeLimitSeconds = defaultTimeLimit.roundToInt() },
                    valueRange = 60f..3600f,
                    steps = 58,
                    colors = SliderDefaults.colors(
                        thumbColor = GuardianAccent,
                        activeTrackColor = GuardianAccent,
                        inactiveTrackColor = GuardianBg
                    )
                )
                Text("打开被监控 App 时，时长选择器的默认值", fontSize = 12.sp, color = GuardianTextFaint)
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("每次续时时长", fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
                    Text("${extensionSecs.roundToInt() / 60} 分钟", fontSize = 14.sp, color = GuardianAccent)
                }
                Slider(
                    value = extensionSecs,
                    onValueChange = { extensionSecs = it },
                    onValueChangeFinished = { prefs.extensionSeconds = extensionSecs.roundToInt() },
                    valueRange = 60f..600f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = GuardianAccent,
                        activeTrackColor = GuardianAccent,
                        inactiveTrackColor = GuardianBg
                    )
                )
                Text("时间到了点「续时」时可以续多久", fontSize = 12.sp, color = GuardianTextFaint)
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("最大续时次数", fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
                    Text("${maxExtensions.roundToInt()} 次", fontSize = 14.sp, color = GuardianAccent)
                }
                Slider(
                    value = maxExtensions,
                    onValueChange = { maxExtensions = it },
                    onValueChangeFinished = { prefs.maxExtensionCount = maxExtensions.roundToInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = GuardianAccent,
                        activeTrackColor = GuardianAccent,
                        inactiveTrackColor = GuardianBg
                    )
                )
                Text("单次打开最多续几次；设为 1 最严格", fontSize = 12.sp, color = GuardianTextFaint)
            }
        }

        Spacer(Modifier.height(20.dp))

        // 学习时段封锁（多时段）
        Text("学习封锁", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("学习时段封锁", fontSize = 15.sp, color = GuardianText)
                        Text(
                            "学习时间内禁止所有被监控 App",
                            fontSize = 12.sp, color = GuardianTextFaint
                        )
                    }
                    Switch(
                        checked = studyEnabled,
                        onCheckedChange = {
                            studyEnabled = it
                            prefs.studyBlockEnabled = it
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = GuardianAccent,
                            checkedTrackColor = GuardianAccent.copy(alpha = 0.4f)
                        )
                    )
                }

                if (studyEnabled) {
                    Spacer(Modifier.height(16.dp))

                    // 已有时段列表
                    studyBlocks.forEachIndexed { idx, block ->
                        val blockIdx = idx // capture for callbacks
                        // 每块用一个 Box 包裹，加分隔和删除按钮
                        if (idx > 0) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth().height(1.dp)
                                    .background(GuardianBg)
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "时段 ${idx + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = GuardianAccent,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                studyBlocks.removeAt(blockIdx)
                                syncStudyBlocks()
                            }) {
                                Text("删除", fontSize = 12.sp, color = GuardianAccent)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // 开始时间
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                block.startTimeStr(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GuardianAccent,
                                modifier = Modifier.width(48.dp)
                            )
                            Text(" → ", fontSize = 12.sp, color = GuardianTextFaint)
                            Text(
                                block.endTimeStr(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = GuardianAccent
                            )
                        }
                        Spacer(Modifier.height(2.dp))

                        // 开始时
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("始时", fontSize = 10.sp, color = GuardianTextFaint, modifier = Modifier.width(32.dp))
                            Slider(
                                value = block.startHour.toFloat(),
                                onValueChange = {
                                    studyBlocks[blockIdx] = block.copy(startHour = it.roundToInt())
                                    syncStudyBlocks()
                                },
                                valueRange = 0f..23f,
                                steps = 22,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = GuardianAccent,
                                    activeTrackColor = GuardianAccent,
                                    inactiveTrackColor = GuardianBg
                                )
                            )
                        }
                        // 开始分
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("始分", fontSize = 10.sp, color = GuardianTextFaint, modifier = Modifier.width(32.dp))
                            Slider(
                                value = block.startMinute.toFloat(),
                                onValueChange = {
                                    studyBlocks[blockIdx] = block.copy(startMinute = it.roundToInt())
                                    syncStudyBlocks()
                                },
                                valueRange = 0f..55f,
                                steps = 10,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = GuardianAccent,
                                    activeTrackColor = GuardianAccent,
                                    inactiveTrackColor = GuardianBg
                                )
                            )
                        }
                        // 结束时
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("终时", fontSize = 10.sp, color = GuardianTextFaint, modifier = Modifier.width(32.dp))
                            Slider(
                                value = block.endHour.toFloat(),
                                onValueChange = {
                                    studyBlocks[blockIdx] = block.copy(endHour = it.roundToInt())
                                    syncStudyBlocks()
                                },
                                valueRange = 0f..23f,
                                steps = 22,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = GuardianAccent,
                                    activeTrackColor = GuardianAccent,
                                    inactiveTrackColor = GuardianBg
                                )
                            )
                        }
                        // 结束分
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("终分", fontSize = 10.sp, color = GuardianTextFaint, modifier = Modifier.width(32.dp))
                            Slider(
                                value = block.endMinute.toFloat(),
                                onValueChange = {
                                    studyBlocks[blockIdx] = block.copy(endMinute = it.roundToInt())
                                    syncStudyBlocks()
                                },
                                valueRange = 0f..55f,
                                steps = 10,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = GuardianAccent,
                                    activeTrackColor = GuardianAccent,
                                    inactiveTrackColor = GuardianBg
                                )
                            )
                        }

                        // 跨夜提示
                        if (block.startTotalMinutes >= block.endTotalMinutes) {
                            Text("⚠ 跨夜时段", fontSize = 11.sp, color = GuardianAccent)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 添加新时段按钮
                    if (studyBlocks.size < 5) {
                        TextButton(onClick = {
                            // 默认添加一个 14:00-18:00 的时段
                            studyBlocks.add(StudyBlock(startHour = 14, startMinute = 0, endHour = 18, endMinute = 0))
                            syncStudyBlocks()
                        }) {
                            Text("＋ 添加时段（${studyBlocks.size}/5）", fontSize = 13.sp, color = GuardianAccent)
                        }
                    } else {
                        Text("已达上限 5 个时段", fontSize = 12.sp, color = GuardianTextFaint)
                    }

                    Text(
                        "可分别设置上午/下午/晚上多个时段；支持跨夜（如 22:00~06:00）",
                        fontSize = 11.sp, color = GuardianTextFaint
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 关键词加密
        Text("隐私", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("关键词加密模式", fontSize = 15.sp, color = GuardianText)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "开启后关键词列表遮罩显示，弹窗不显示原文，删除需密码",
                            fontSize = 12.sp, color = GuardianTextFaint
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = encrypted,
                        onCheckedChange = { want ->
                            if (want) {
                                // 开启加密：必须先设密码
                                pwd1 = ""; pwd2 = ""
                                showSetPwd = true
                            } else if (prefs.keywordPasswordHash.isEmpty()) {
                                // 从没设过密码（异常残留状态），直接关
                                encrypted = false
                                prefs.keywordsEncrypted = false
                            } else {
                                // 关闭加密同样要验密码，否则"防自己"形同虚设
                                verifyInput = ""
                                verifyError = false
                                showVerifyPwd = true
                            }
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = GuardianAccent,
                            checkedTrackColor = GuardianAccent
                        )
                    )
                }
                if (encrypted) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(GuardianBg)
                            .clickable {
                                pwd1 = ""; pwd2 = ""
                                showSetPwd = true
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("修改密码", fontSize = 13.sp, color = GuardianAccent)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // 数据导出
        Text("数据", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .clickable(enabled = !exporting) {
                    scope.launch {
                        exporting = true
                        try {
                            val uri = exportLogs(context, repo)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "导出触发记录"))
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            exporting = false
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (exporting) "导出中…" else "导出触发记录 JSON",
                fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f)
            )
            Text("→", fontSize = 20.sp, color = GuardianAccent)
        }

        Spacer(Modifier.height(40.dp))

        // 关于
        Text("关于", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(16.dp)
        ) {
            Column {
                Text("守卫 Guardian v1.0", fontSize = 15.sp, color = GuardianText)
                Spacer(Modifier.height(4.dp))
                Text("帮你守住专注的 Android 自律工具", fontSize = 13.sp, color = GuardianTextDim)
                Spacer(Modifier.height(4.dp))
                Text("对标 one sec · 本地运行 · 不联网不上传", fontSize = 12.sp, color = GuardianTextFaint)
            }
        }
    }

    if (showVerifyPwd) {
        AlertDialog(
            onDismissRequest = { showVerifyPwd = false; verifyInput = "" },
            title = { Text("验证密码") },
            text = {
                Column {
                    Text("关闭加密模式需要输入密码", fontSize = 13.sp, color = GuardianTextDim)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = verifyInput,
                        onValueChange = { verifyInput = it; verifyError = false },
                        placeholder = { Text("4~6 位数字") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = verifyError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (verifyError) {
                        Spacer(Modifier.height(6.dp))
                        Text("密码错误", fontSize = 12.sp, color = GuardianAccent)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (sha256(verifyInput.trim()) == prefs.keywordPasswordHash) {
                        encrypted = false
                        prefs.keywordsEncrypted = false
                        showVerifyPwd = false
                        verifyInput = ""
                        Toast.makeText(context, "加密模式已关闭", Toast.LENGTH_SHORT).show()
                    } else {
                        verifyError = true
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showVerifyPwd = false; verifyInput = "" }) { Text("取消") }
            }
        )
    }

    if (showSetPwd) {
        var pwdError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSetPwd = false; pwd1 = ""; pwd2 = "" },
            title = { Text(if (prefs.keywordPasswordHash.isEmpty()) "设置关键词密码" else "修改关键词密码") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pwd1, onValueChange = { pwd1 = it; pwdError = "" },
                        placeholder = { Text("4~6 位数字") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pwdError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pwd2, onValueChange = { pwd2 = it; pwdError = "" },
                        placeholder = { Text("再输一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pwdError.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pwdError.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(pwdError, fontSize = 12.sp, color = GuardianAccent)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pwd1.length < 4 || pwd1.length > 6 -> pwdError = "密码长度 4~6 位"
                        pwd1 != pwd2 -> pwdError = "两次输入不一致"
                        !pwd1.all { it.isDigit() } -> pwdError = "只能数字"
                        else -> {
                            prefs.keywordPasswordHash = sha256(pwd1)
                            prefs.keywordsEncrypted = true
                            encrypted = true
                            showSetPwd = false
                            pwd1 = ""; pwd2 = ""
                            Toast.makeText(context, "密码已设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSetPwd = false; pwd1 = ""; pwd2 = "" }) { Text("取消") }
            }
        )
    }
}

private fun sha256(s: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

private suspend fun exportLogs(context: android.content.Context, repo: GuardianRepository): android.net.Uri =
    withContext(Dispatchers.IO) {
        val logs = repo.allLogs()
        val arr = JSONArray()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        logs.forEach { log ->
            val obj = JSONObject()
            obj.put("id", log.id)
            obj.put("packageName", log.packageName ?: "")
            obj.put("triggerType", log.triggerType)
            obj.put("keyword", log.keyword ?: "")
            obj.put("timestamp", fmt.format(java.util.Date(log.timestamp)))
            obj.put("dismissedAt", log.dismissedAt?.let { fmt.format(java.util.Date(it)) } ?: "")
            arr.put(obj)
        }
        val file = File(context.cacheDir, "guardian_export_${System.currentTimeMillis()}.json")
        file.writeText(arr.toString(2))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
