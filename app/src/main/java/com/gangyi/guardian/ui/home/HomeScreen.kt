package com.gangyi.guardian.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.guard.EscalationTracker
import com.gangyi.guardian.permission.Permissions
import com.gangyi.guardian.service.MonitorService
import com.gangyi.guardian.ui.components.NavCard
import com.gangyi.guardian.ui.components.PermissionRow
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onNavigateApps: () -> Unit,
    onNavigateKeywords: () -> Unit,
    onNavigateReminders: () -> Unit,
    onNavigateStats: () -> Unit,
    onNavigateSettings: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { MonitorPrefs(context) }
    val repo = remember { GuardianRepository(context) }

    var serviceRunning by remember { mutableStateOf(prefs.serviceEnabled) }
    var hasUsage by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var hasOverlay by remember { mutableStateOf(Permissions.hasOverlay(context)) }
    var hasAccessibility by remember { mutableStateOf(Permissions.hasAccessibility(context)) }
    var monitoredCount by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()

    // 初加载时异步读 Room 获取监控数
    DisposableEffect(Unit) {
        val job = scope.launch(Dispatchers.IO) {
            val count = repo.listMonitoredPackages().size
            withContext(Dispatchers.Main) { monitoredCount = count }
        }
        onDispose { job.cancel() }
    }

    // 从权限设置页/选 App 页回来时刷新状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsage = Permissions.hasUsageAccess(context)
                hasOverlay = Permissions.hasOverlay(context)
                hasAccessibility = Permissions.hasAccessibility(context)
                scope.launch(Dispatchers.IO) {
                    val count = repo.listMonitoredPackages().size
                    withContext(Dispatchers.Main) { monitoredCount = count }
                }
                serviceRunning = prefs.serviceEnabled
                // 每次回到主界面时自动兜底拉起服务（APK 更新/系统杀进程后 prefs 记得开但服务早就死了）
                if (prefs.serviceEnabled && hasUsage && hasOverlay) {
                    MonitorService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val canStart = hasUsage && hasOverlay && monitoredCount > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text("守卫", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = GuardianText)
        Text("Guardian", fontSize = 14.sp, color = GuardianTextDim)

        Spacer(Modifier.height(40.dp))

        // 总开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("守护开关", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = GuardianText)
                Text(
                    when {
                        serviceRunning -> "正在守护 · 监控 $monitoredCount 个应用"
                        !hasUsage -> "需要授予用量访问权限"
                        !hasOverlay -> "需要授予悬浮窗权限"
                        monitoredCount == 0 -> "请先选择要监控的 App"
                        else -> "已关闭"
                    },
                    fontSize = 13.sp, color = GuardianTextDim
                )
            }
            Switch(
                checked = serviceRunning,
                enabled = canStart || serviceRunning,
                onCheckedChange = { on ->
                    if (on && !canStart) {
                        val msg = when {
                            !hasUsage -> "请先授予用量访问权限"
                            !hasOverlay -> "请先授予悬浮窗权限"
                            monitoredCount == 0 -> "请先选择要监控的 App"
                            else -> "尚未满足启动条件"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        return@Switch
                    }
                    // 封锁期内不许关守护——否则关掉开关就能绕过封锁，
                    // 整个"防自我欺骗"就白做了。
                    if (!on && EscalationTracker.anyActiveLock()) {
                        Toast.makeText(
                            context,
                            "封锁期间无法关闭守护，等封锁结束再说",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Switch
                    }
                    serviceRunning = on
                    prefs.serviceEnabled = on
                    // 同步写 Prefs（供 BootReceiver/Worker 读）和 Room（供 MonitorService 读）
                    if (on) MonitorService.start(context) else MonitorService.stop(context)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GuardianBg,
                    checkedTrackColor = GuardianAccent,
                    uncheckedThumbColor = GuardianTextDim,
                    uncheckedTrackColor = GuardianSurface
                )
            )
        }

        Spacer(Modifier.height(24.dp))

        // 权限状态
        Text("权限状态", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(10.dp))

        PermissionRow("用量访问", hasUsage) { onRequestUsageAccess() }
        Spacer(Modifier.height(8.dp))
        PermissionRow("悬浮窗", hasOverlay) { onRequestOverlay() }
        Spacer(Modifier.height(8.dp))
        PermissionRow("无障碍（剪贴板监控）", hasAccessibility) { onRequestAccessibility() }

        Spacer(Modifier.height(32.dp))

        NavCard(
            icon = Icons.Filled.Apps,
            title = "监控列表",
            subtitle = "已选 $monitoredCount 个应用",
            onClick = onNavigateApps
        )

        Spacer(Modifier.height(10.dp))

        NavCard(
            icon = Icons.Filled.Key,
            title = "关键词管理",
            subtitle = "屏幕出现关键词时弹出提醒",
            onClick = onNavigateKeywords
        )

        Spacer(Modifier.height(10.dp))

        NavCard(
            icon = Icons.AutoMirrored.Filled.Message,
            title = "提醒语管理",
            subtitle = "弹窗提醒语：随机抽取或固定一条",
            onClick = onNavigateReminders
        )

        Spacer(Modifier.height(10.dp))

        NavCard(
            icon = Icons.Filled.BarChart,
            title = "统计",
            subtitle = "查看你的自律数据",
            onClick = onNavigateStats
        )

        Spacer(Modifier.height(10.dp))

        NavCard(
            icon = Icons.Filled.Settings,
            title = "设置",
            subtitle = "冷却、封锁强度、数据导出",
            onClick = onNavigateSettings
        )

        Spacer(Modifier.height(32.dp))
    }
}
