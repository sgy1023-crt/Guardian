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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.FileProvider
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.permission.Permissions
import com.gangyi.guardian.ui.components.GuardianCard
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.components.GuardianSlider
import com.gangyi.guardian.ui.components.GuardianTopBar
import com.gangyi.guardian.ui.components.SectionLabel
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianDangerSoft
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

    var cooldown by remember { mutableFloatStateOf(prefs.cooldownSeconds.toFloat()) }
    var scanInterval by remember { mutableFloatStateOf(prefs.screenScanIntervalSeconds) }
    var overlayCountdown by remember { mutableFloatStateOf(prefs.overlayCountdownSeconds.toFloat()) }
    var alertVibrate by remember { mutableStateOf(prefs.alertVibrate) }
    var alertSound by remember { mutableStateOf(prefs.alertSound) }
    var escCdEnabled by remember { mutableStateOf(prefs.escalatingCountdownEnabled) }
    var cdMultiplier by remember { mutableFloatStateOf(prefs.countdownMultiplier) }
    var escInterval by remember { mutableFloatStateOf(prefs.escalationIntervalSeconds.toFloat()) }
    var cdMax by remember { mutableFloatStateOf(prefs.countdownMaxSeconds.toFloat()) }
    var exporting by remember { mutableStateOf(false) }
    var encrypted by remember { mutableStateOf(prefs.keywordsEncrypted) }
    var showSetPwd by remember { mutableStateOf(false) }
    var pwd1 by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var showVerifyPwd by remember { mutableStateOf(false) }
    var verifyInput by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf(false) }

    // 升级封锁
    var escEnabled by remember { mutableStateOf(prefs.escalationEnabled) }
    var escThreshold by remember { mutableFloatStateOf(prefs.escalationThreshold.toFloat()) }
    var escWindow by remember { mutableFloatStateOf(prefs.escalationWindowMinutes.toFloat()) }
    var lockMinutes by remember { mutableFloatStateOf(prefs.lockdownMinutes.toFloat()) }
    val hasAccessibility = remember { Permissions.hasAccessibility(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        GuardianTopBar("设置", onBack)
        Spacer(Modifier.height(28.dp))

        // 冷却时间
        SectionLabel("提醒冷却")
        GuardianSlider(
            label = "同一 App / 关键词触发后冷却",
            valueText = "${cooldown.toInt()} 秒",
            hint = if (escCdEnabled)
                "已被「递增倒计时」接管，当前不生效——节流改由下方「递增间隔」控制"
            else
                "弹窗关闭后这段时间内，同一目标不再触发（避免反复弹）",
            value = cooldown,
            valueRange = 5f..300f,
            onValueChange = { cooldown = it },
            onValueChangeFinished = { prefs.cooldownSeconds = cooldown.toInt() }
        )

        Spacer(Modifier.height(20.dp))

        // 屏幕扫描间隔
        SectionLabel("屏幕扫描灵敏度")
        GuardianSlider(
            label = "扫描间隔",
            valueText = String.format("%.1f 秒", scanInterval),
            hint = "越小越灵敏越费电；1.5 秒够用，慢机器可调到 3~5 秒",
            value = scanInterval,
            valueRange = 1f..10f,
            onValueChange = { scanInterval = it },
            onValueChangeFinished = { prefs.screenScanIntervalSeconds = scanInterval }
        )

        Spacer(Modifier.height(20.dp))

        // 弹窗倒计时
        SectionLabel("弹窗倒计时")
        GuardianSlider(
            label = "倒计时秒数",
            valueText = "${overlayCountdown.toInt()} 秒",
            hint = "弹窗出现后必须冷静这么多秒才能点关闭，默认 5 秒，最高 3 分钟",
            value = overlayCountdown,
            valueRange = 3f..180f,
            onValueChange = { overlayCountdown = it },
            onValueChangeFinished = { prefs.overlayCountdownSeconds = overlayCountdown.toInt() }
        )

        Spacer(Modifier.height(10.dp))

        // 递增倒计时：越挣扎、等得越久
        GuardianCard(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("递增倒计时", fontSize = 15.sp, color = GuardianText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "连续被抓到时，倒计时按倍数加长：${overlayCountdown.toInt()} 秒 → " +
                            "${(overlayCountdown * cdMultiplier).toInt()} 秒 → " +
                            "${(overlayCountdown * cdMultiplier * cdMultiplier).toInt().coerceAtMost(cdMax.toInt())} 秒…" +
                            "\n开启后，下面的「递增间隔」取代「提醒冷却」生效",
                        fontSize = 12.sp, color = GuardianTextFaint
                    )
                }
                androidx.compose.material3.Switch(
                    checked = escCdEnabled,
                    onCheckedChange = {
                        escCdEnabled = it
                        prefs.escalatingCountdownEnabled = it
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = GuardianAccent,
                        checkedTrackColor = GuardianAccent
                    )
                )
            }
        }

        if (escCdEnabled) {
            Spacer(Modifier.height(10.dp))
            GuardianSlider(
                label = "翻倍系数",
                valueText = String.format("%.1f 倍", cdMultiplier),
                hint = "每连续触发一次，倒计时乘以这个数。2 倍即 15→30→60 秒",
                value = cdMultiplier,
                valueRange = 1.2f..5f,
                onValueChange = { cdMultiplier = it },
                onValueChangeFinished = { prefs.countdownMultiplier = cdMultiplier }
            )
            Spacer(Modifier.height(10.dp))
            GuardianSlider(
                label = "递增间隔",
                valueText = "${escInterval.toInt()} 秒",
                hint = "两次触发相隔在这个时间内算「还在挣扎」，倒计时翻倍；超过则重新从头算",
                value = escInterval,
                valueRange = 10f..600f,
                onValueChange = { escInterval = it },
                onValueChangeFinished = { prefs.escalationIntervalSeconds = escInterval.toInt() }
            )
            Spacer(Modifier.height(10.dp))
            GuardianSlider(
                label = "倒计时封顶",
                valueText = "${cdMax.toInt()} 秒",
                hint = "翻倍最多加长到这里为止，防止误触发时痛苦到无法忍受",
                value = cdMax,
                valueRange = 30f..1800f,
                onValueChange = { cdMax = it },
                onValueChangeFinished = { prefs.countdownMaxSeconds = cdMax.toInt() }
            )
        }

        Spacer(Modifier.height(20.dp))

        // 弹窗提醒方式：光靠看容易被无视，加一层身体上的信号
        SectionLabel("提醒方式")
        GuardianCard(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("弹窗振动", fontSize = 15.sp, color = GuardianText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "弹窗出现时短促振两下。旁人完全察觉不到，但身体会记住这个信号",
                        fontSize = 12.sp, color = GuardianTextFaint
                    )
                }
                androidx.compose.material3.Switch(
                    checked = alertVibrate,
                    onCheckedChange = {
                        alertVibrate = it
                        prefs.alertVibrate = it
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = GuardianAccent,
                        checkedTrackColor = GuardianAccent
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("弹窗提示音", fontSize = 15.sp, color = GuardianText)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "手机静音时也会响（走闹钟通道），音量已压低。这是最难被忽略的一层提醒",
                        fontSize = 12.sp, color = GuardianTextFaint
                    )
                }
                androidx.compose.material3.Switch(
                    checked = alertSound,
                    onCheckedChange = {
                        alertSound = it
                        prefs.alertSound = it
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = GuardianAccent,
                        checkedTrackColor = GuardianAccent
                    )
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // 升级封锁：反复点掉弹窗时上硬手段
        SectionLabel("升级封锁")
        GuardianCard(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("反复触发时封锁", fontSize = 15.sp, color = GuardianText)
                        Spacer(Modifier.size(8.dp))
                        GuardianChip("硬手段", GuardianDanger, GuardianDangerSoft)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "短时间内被提醒多次，说明提醒已经没用了。此时把你送回桌面，" +
                            "并暂时锁住那个应用——期间打开就再送出来，没有解锁入口。",
                        fontSize = 12.sp, color = GuardianTextFaint
                    )
                }
                androidx.compose.material3.Switch(
                    checked = escEnabled,
                    onCheckedChange = {
                        escEnabled = it
                        prefs.escalationEnabled = it
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = GuardianDanger,
                        checkedTrackColor = GuardianDanger.copy(alpha = 0.5f)
                    )
                )
            }
            if (escEnabled && !hasAccessibility) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(GuardianDangerSoft)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "未开启无障碍权限，封锁只能弹窗强制冷静，无法把你送回桌面。" +
                            "去主页开启无障碍可获得完整效果。",
                        fontSize = 11.sp, color = GuardianDanger
                    )
                }
            }
        }

        if (escEnabled) {
            Spacer(Modifier.height(10.dp))
            GuardianSlider(
                label = "触发次数阈值",
                valueText = "${escThreshold.toInt()} 次",
                hint = "窗口内被提醒这么多次就封锁。设为 1 = 一打开就直接封锁，不先提醒",
                value = escThreshold,
                valueRange = 1f..10f,
                onValueChange = { escThreshold = it },
                onValueChangeFinished = { prefs.escalationThreshold = escThreshold.toInt() }
            )
            Spacer(Modifier.height(10.dp))
            GuardianSlider(
                label = "统计窗口",
                valueText = "${escWindow.toInt()} 分钟",
                hint = "只看最近这段时间内的次数；真去干别的事，计数会自动清零",
                value = escWindow,
                valueRange = 5f..60f,
                onValueChange = { escWindow = it },
                onValueChangeFinished = { prefs.escalationWindowMinutes = escWindow.toInt() }
            )
            Spacer(Modifier.height(10.dp))
            GuardianSlider(
                label = "封锁时长",
                valueText = "${lockMinutes.toInt()} 分钟",
                hint = "基础时长。短时间内重复被封同一个应用，时长会翻倍（最多 4 倍）",
                value = lockMinutes,
                valueRange = 1f..30f,
                onValueChange = { lockMinutes = it },
                onValueChangeFinished = { prefs.lockdownMinutes = lockMinutes.toInt() }
            )
        }

        Spacer(Modifier.height(20.dp))

        // 关键词加密
        SectionLabel("隐私")
        GuardianCard(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
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

        Spacer(Modifier.height(32.dp))

        // 数据导出
        SectionLabel("数据")
        GuardianCard(
            onClick = if (exporting) null else {
                {
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
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (exporting) "导出中…" else "导出触发记录 JSON",
                    fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f)
                )
                Text("→", fontSize = 18.sp, color = GuardianAccent)
            }
        }

        Spacer(Modifier.height(40.dp))

        // 关于
        SectionLabel("关于")
        GuardianCard(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            Text("守卫 Guardian v1.1", fontSize = 15.sp, color = GuardianText)
            Spacer(Modifier.height(4.dp))
            Text("帮你守住专注的 Android 自律工具", fontSize = 13.sp, color = GuardianTextDim)
            Spacer(Modifier.height(4.dp))
            Text("对标 one sec · 本地运行 · 不联网不上传", fontSize = 12.sp, color = GuardianTextFaint)
        }

        Spacer(Modifier.height(32.dp))
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
