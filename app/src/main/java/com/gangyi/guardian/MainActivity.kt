package com.gangyi.guardian

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.guard.EscalationTracker
import com.gangyi.guardian.guard.InterventionCoordinator
import com.gangyi.guardian.permission.PermissionState
import com.gangyi.guardian.permission.Permissions
import com.gangyi.guardian.service.MonitorService
import com.gangyi.guardian.ui.home.HomeScreen
import com.gangyi.guardian.ui.onboarding.PermissionGuideScreen
import com.gangyi.guardian.ui.rules.RulesScreen
import com.gangyi.guardian.ui.settings.SettingsScreen
import com.gangyi.guardian.ui.stats.StatsScreen
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTheme

private data class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("守护", Icons.Rounded.Shield),
    Tab("规则", Icons.Rounded.Tune),
    Tab("统计", Icons.Rounded.BarChart),
    Tab("设置", Icons.Rounded.Settings)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // UI 层要读封锁状态（主页总开关的封锁期禁用判断），先确保已初始化
        EscalationTracker.init(this)
        InterventionCoordinator.init(this)
        setContent {
            GuardianTheme { GuardianApp() }
        }
    }

    /** Android 13+ 通知权限不申请的话，"守卫运行中"常驻通知不会显示，服务也更容易被杀。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}

@Composable
private fun GuardianApp() {
    val context = LocalContext.current
    val prefs = remember { MonitorPrefs(context) }

    // 权限状态在 Activity 层统一维护：每次从系统设置回来刷新一次，各页面共用
    var perms by remember { mutableStateOf(PermissionState.read(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                perms = PermissionState.read(context)
                // 每次回到前台自动兜底拉起服务（APK 更新/系统杀进程后 prefs 记得开但服务早就死了）
                if (prefs.serviceEnabled && perms.usage && perms.overlay) {
                    MonitorService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 首启路由：从未完成引导 + 必填权限缺失 → 走引导页
    var showGuide by rememberSaveable {
        mutableStateOf(!prefs.onboardingDone && (!perms.usage || !perms.overlay))
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // 非首页按系统返回 = 回首页，而不是退出 App
    BackHandler(enabled = !showGuide && tab != 0) { tab = 0 }

    Scaffold(
        containerColor = GuardianBg,
        bottomBar = {
            if (!showGuide) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(GuardianBorder))
                    NavigationBar(containerColor = GuardianSurface, tonalElevation = 0.dp) {
                        TABS.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = GuardianAccent,
                                    selectedTextColor = GuardianAccent,
                                    indicatorColor = GuardianAccentSoft,
                                    unselectedIconColor = GuardianTextDim,
                                    unselectedTextColor = GuardianTextDim
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            if (showGuide) {
                PermissionGuideScreen(
                    onDone = {
                        prefs.onboardingDone = true
                        perms = PermissionState.read(context)
                        showGuide = false
                    }
                )
            } else {
                when (tab) {
                    0 -> HomeScreen(
                        perms = perms,
                        onRequestUsageAccess = { context.startActivity(Permissions.usageAccessIntent()) },
                        onRequestOverlay = { context.startActivity(Permissions.overlayIntent(context)) },
                        onRequestAccessibility = { context.startActivity(Permissions.accessibilityIntent()) },
                        onGoRules = { tab = 1 }
                    )
                    1 -> RulesScreen()
                    2 -> StatsScreen()
                    else -> SettingsScreen(perms = perms)
                }
            }
        }
    }
}
