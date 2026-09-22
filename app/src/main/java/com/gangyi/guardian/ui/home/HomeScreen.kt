package com.gangyi.guardian.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.gangyi.guardian.data.Episodes
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.guard.EscalationTracker
import com.gangyi.guardian.guard.GuardSchedule
import com.gangyi.guardian.permission.PermissionState
import com.gangyi.guardian.service.MonitorService
import com.gangyi.guardian.ui.components.AllGrantedRow
import com.gangyi.guardian.ui.components.GuardianCard
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.components.GuardianDialog
import com.gangyi.guardian.ui.components.GuardianSwitch
import com.gangyi.guardian.ui.components.GuardianTextField
import com.gangyi.guardian.ui.components.IconBadge
import com.gangyi.guardian.ui.components.PermissionRow
import com.gangyi.guardian.ui.components.RowDivider
import com.gangyi.guardian.ui.components.ScreenHeader
import com.gangyi.guardian.ui.components.SectionLabel
import com.gangyi.guardian.ui.components.StatTile
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianDangerSoft
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import com.gangyi.guardian.util.InstalledApps
import com.gangyi.guardian.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    perms: PermissionState,
    onRequestUsageAccess: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onGoRules: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { MonitorPrefs(context) }
    val repo = remember { GuardianRepository(context) }

    var serviceRunning by remember { mutableStateOf(prefs.serviceEnabled) }
    val monitored by repo.monitoredPackages.collectAsState(initial = emptyList())
    val keywords by repo.keywords.collectAsState(initial = emptyList())

    val todayStart = remember { startOfToday() }
    val todayLogs by repo.observeLogsSince(todayStart).collectAsState(initial = emptyList())
    // 统计口径：按"冲动事件"算，不是按弹窗次数（一次挣扎可能弹好几次）
    val episodes = remember(todayLogs) { Episodes.group(todayLogs) }
    val decidedEpisodes = episodes.count { it.decided }
    val blockedEpisodes = episodes.count { it.blocked }

    var streak by remember { mutableIntStateOf(0) }
    LaunchedEffect(todayLogs.size) {
        streak = withContext(Dispatchers.IO) { computeStreak(repo, todayStart) }
    }

    // 封锁横幅 / 暂停剩余 / 时段状态，每秒刷新一次
    var locks by remember { mutableStateOf(EscalationTracker.activeLocks()) }
    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            locks = EscalationTracker.activeLocks()
            nowTick = System.currentTimeMillis()
            delay(1000L)
        }
    }
    var pausedUntil by remember { mutableLongStateOf(prefs.pausedUntil) }
    val paused = pausedUntil > nowTick
    val inSchedule = GuardSchedule.isInSchedule(prefs, nowTick)
    var showPause by remember { mutableStateOf(false) }

    // 关闭守护的摩擦：设过密码就输密码，没设就等 3 秒
    var showCloseGuard by remember { mutableStateOf(false) }
    var closeWait by remember { mutableIntStateOf(CLOSE_WAIT_SEC) }
    var closePwd by remember { mutableStateOf("") }
    var closePwdError by remember { mutableStateOf(false) }

    val hasRules = monitored.isNotEmpty() || keywords.isNotEmpty()
    val canStart = perms.requiredGranted && hasRules
    val preset = prefs.currentPreset()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenHeader("守卫", "帮你守住专注")
        Spacer(Modifier.height(20.dp))

        // ---- 状态主卡
        val guarding = serviceRunning && !paused && inSchedule
        GuardianCard(
            glowColor = if (guarding) GuardianAccent else null,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        guarding -> GuardianSuccess
                                        serviceRunning -> GuardianAccent
                                        else -> GuardianTextFaint
                                    }
                                )
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            when {
                                serviceRunning && paused -> "暂停中"
                                serviceRunning && !inSchedule -> "时段外"
                                serviceRunning -> "守护中"
                                !perms.usage || !perms.overlay -> "还没准备好"
                                !hasRules -> "还没有规则"
                                else -> "已暂停"
                            },
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GuardianText
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when {
                            serviceRunning && paused -> "${formatHm(pausedUntil)} 自动恢复守护"
                            serviceRunning && !inSchedule -> GuardSchedule.nextScheduleStart(prefs, nowTick)
                                ?.let { "${GuardSchedule.describeNextStart(it, nowTick)} 开始守护" }
                                ?: "守护时段里没有选中任何一天"
                            !perms.usage -> "需要开启「用量访问」权限"
                            !perms.overlay -> "需要开启「悬浮窗」权限"
                            !hasRules -> "去「规则」里选几个应用或关键词"
                            else -> "${monitored.size} 个应用 · ${keywords.size} 个关键词 · ${preset.label}强度"
                        },
                        fontSize = 13.sp, color = GuardianTextDim
                    )
                }
                GuardianSwitch(
                    checked = serviceRunning,
                    enabled = canStart || serviceRunning,
                    onCheckedChange = { on ->
                        if (on && !canStart) {
                            val msg = when {
                                !perms.usage -> "请先开启用量访问权限"
                                !perms.overlay -> "请先开启悬浮窗权限"
                                else -> "请先添加要监控的应用或关键词"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            return@GuardianSwitch
                        }
                        // 封锁期内不许关守护——否则关掉开关就能绕过封锁
                        if (!on && EscalationTracker.anyActiveLock()) {
                            Toast.makeText(context, "封锁期间无法关闭守护，等封锁结束再说", Toast.LENGTH_SHORT).show()
                            return@GuardianSwitch
                        }
                        if (!on) {
                            // 关守护是"绕过整个 app"最短的那条路，必须有点摩擦
                            closeWait = CLOSE_WAIT_SEC
                            closePwd = ""
                            closePwdError = false
                            showCloseGuard = true
                            return@GuardianSwitch
                        }
                        serviceRunning = true
                        prefs.serviceEnabled = true
                        MonitorService.start(context)
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // 暂停一会 / 立即恢复：出差开会临时放开，不用碰总开关
            if (serviceRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            paused -> "暂停期间不弹不计数，已封锁的照旧"
                            prefs.scheduleEnabled -> "守护时段：${GuardSchedule.describe(prefs)}"
                            else -> "全天守护 · 需要临时放开就点右边"
                        },
                        fontSize = 12.sp, color = GuardianTextFaint, lineHeight = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (paused) GuardianAccent else GuardianSurface2)
                            .clickable {
                                if (paused) {
                                    prefs.pausedUntil = 0L
                                    pausedUntil = 0L
                                } else if (EscalationTracker.anyActiveLock()) {
                                    Toast.makeText(context, "封锁期间不能暂停守护", Toast.LENGTH_SHORT).show()
                                } else {
                                    showPause = true
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (paused) "立即恢复" else "暂停一会",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (paused) GuardianBg else GuardianText
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("今日冲动", "${episodes.size}", Modifier.weight(1f), unit = "次")
                StatTile(
                    "拦下率",
                    if (decidedEpisodes == 0) "—" else "${blockedEpisodes * 100 / decidedEpisodes}",
                    Modifier.weight(1f),
                    unit = if (decidedEpisodes == 0) null else "%",
                    accent = if (decidedEpisodes > 0 && blockedEpisodes * 2 >= decidedEpisodes) GuardianSuccess else GuardianText
                )
                StatTile("连续清醒", "$streak", Modifier.weight(1f), unit = "天", accent = GuardianAccent)
            }
        }

        // ---- 封锁横幅
        if (locks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val now = System.currentTimeMillis()
            GuardianCard(background = GuardianDangerSoft, bordered = false, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                locks.entries.sortedBy { it.value }.forEachIndexed { i, (pkg, until) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Lock, tint = GuardianDanger, background = GuardianDanger.copy(alpha = 0.15f), size = 36.dp, iconSize = 18.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(InstalledApps.label(context, pkg), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GuardianText)
                            Text("封锁中，打开会被送回桌面", fontSize = 12.sp, color = GuardianTextDim)
                        }
                        Text(formatMmSs(until - now), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GuardianDanger)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- 权限
        SectionLabel("权限")
        GuardianCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            if (perms.allGranted) {
                AllGrantedRow("权限已就绪，守卫可以完整工作")
            } else {
                PermissionRow(
                    Icons.Rounded.Visibility, "用量访问",
                    "知道你正在用哪个应用", perms.usage, onRequestUsageAccess
                )
                RowDivider(66.dp)
                PermissionRow(
                    Icons.Rounded.Layers, "悬浮窗",
                    "在任何应用上方弹出停顿点", perms.overlay, onRequestOverlay
                )
                RowDivider(66.dp)
                PermissionRow(
                    Icons.Rounded.Accessibility, "无障碍",
                    when {
                        perms.accessibility && !perms.accessibilityRunning ->
                            "系统里开着，但服务没在运行（更新或系统回收会这样）。点这里去关掉再打开一次"
                        else -> "识别你输入的关键词；封锁时送你回桌面"
                    },
                    perms.accessibilityUsable,
                    onRequestAccessibility
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ---- 机制说明：非技术用户也要一眼看懂"它会怎么对我"
        SectionLabel("它怎么工作")
        GuardianCard(onClick = onGoRules) {
            StepRow(1, "打开监控的应用、或打出关键词", "弹出停顿点，先冷静 ${prefs.overlayCountdownSeconds} 秒")
            Spacer(Modifier.height(12.dp))
            StepRow(2, "「退出」永远免费", "「继续」记一次，放行 ${prefs.passMinutes} 分钟不打扰")
            Spacer(Modifier.height(12.dp))
            if (prefs.escalationEnabled) {
                StepRow(
                    3, "${prefs.escalationWindowMinutes} 分钟内第 ${prefs.escalationThreshold} 次继续",
                    "送回桌面并封锁 ${prefs.lockdownMinutes} 分钟，期间打不开"
                )
            } else {
                StepRow(3, "不会封锁", "当前是温和模式，只提醒不拦")
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GuardianChip("${preset.label}强度", GuardianAccent, GuardianAccentSoft)
                Spacer(Modifier.width(8.dp))
                Text("在「设置」里一键切换", fontSize = 12.sp, color = GuardianTextFaint)
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showPause) {
        val now = System.currentTimeMillis()
        val endOfToday = startOfToday() + TimeUnit.DAYS.toMillis(1)
        val options = listOf(
            "30 分钟" to 30 * 60_000L,
            "1 小时" to 60 * 60_000L,
            "3 小时" to 3 * 60 * 60_000L,
            "今天剩下的时间" to (endOfToday - now).coerceAtLeast(60_000L)
        )
        AlertDialog(
            onDismissRequest = { showPause = false },
            containerColor = GuardianSurface,
            titleContentColor = GuardianText,
            shape = RoundedCornerShape(24.dp),
            title = { Text("暂停多久", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("暂停期间不弹停顿点、不计数。已经生效的封锁不受影响。", fontSize = 13.sp, color = GuardianTextDim, lineHeight = 19.sp)
                    Spacer(Modifier.height(12.dp))
                    options.forEach { (label, ms) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(GuardianSurface2)
                                .clickable {
                                    val until = System.currentTimeMillis() + ms
                                    prefs.pausedUntil = until
                                    pausedUntil = until
                                    showPause = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = GuardianText, modifier = Modifier.weight(1f))
                            Text("至 ${formatHm(now + ms)}", fontSize = 12.sp, color = GuardianTextFaint)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPause = false }) { Text("取消", color = GuardianTextDim) }
            }
        )
    }

    // 关闭守护：设过密码就输密码，没设就等 3 秒。它是绕过整个 app 最短的那条路。
    if (showCloseGuard) {
        LaunchedEffect(Unit) {
            closeWait = CLOSE_WAIT_SEC
            while (closeWait > 0) {
                delay(1000L)
                closeWait--
            }
        }
        val needsPwd = prefs.keywordPasswordHash.isNotEmpty()
        GuardianDialog(
            onDismiss = { showCloseGuard = false },
            title = "关闭守护",
            confirmText = if (closeWait > 0) "等待 $closeWait 秒" else "确认关闭",
            confirmEnabled = closeWait == 0,
            danger = true,
            onConfirm = {
                if (needsPwd && sha256(closePwd.trim()) != prefs.keywordPasswordHash) {
                    closePwdError = true
                } else {
                    showCloseGuard = false
                    serviceRunning = false
                    prefs.serviceEnabled = false
                    MonitorService.stop(context)
                }
            }
        ) {
            Column {
                Text(
                    "关闭后不再有任何提醒，已经设的规则都不生效。只是临时想放开的话，用「暂停一会」更合适。",
                    fontSize = 13.sp, color = GuardianTextDim, lineHeight = 19.sp
                )
                if (needsPwd) {
                    Spacer(Modifier.height(12.dp))
                    GuardianTextField(
                        value = closePwd,
                        onValueChange = { closePwd = it; closePwdError = false },
                        placeholder = "输入关键词密码",
                        isError = closePwdError,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardType = KeyboardType.NumberPassword
                    )
                    if (closePwdError) {
                        Spacer(Modifier.height(6.dp))
                        Text("密码不对", fontSize = 12.sp, color = GuardianDanger)
                    }
                }
            }
        }
    }
}

/** 关闭守护的等待秒数（没设密码时的门槛） */
private const val CLOSE_WAIT_SEC = 3

/** 时间戳 → HH:mm */
internal fun formatHm(ms: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

@Composable
private fun StepRow(n: Int, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(GuardianSurface2),
            contentAlignment = Alignment.Center
        ) {
            Text("$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GuardianAccent)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianText)
            Text(desc, fontSize = 12.sp, color = GuardianTextDim, lineHeight = 17.sp)
        }
    }
}

internal fun startOfToday(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** 连续清醒 = 从今天往回数，连续多少天完全没有触发记录。 */
internal suspend fun computeStreak(repo: GuardianRepository, todayStart: Long): Int {
    val lastTs = repo.lastTimestamp() ?: return 0
    val cal = Calendar.getInstance()
    cal.timeInMillis = lastTs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return ((todayStart - cal.timeInMillis) / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
}

internal fun formatMmSs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
