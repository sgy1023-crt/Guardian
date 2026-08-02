package com.gangyi.guardian

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.guard.EscalationTracker
import com.gangyi.guardian.permission.Permissions
import com.gangyi.guardian.ui.apps.AppPickerScreen
import com.gangyi.guardian.ui.home.HomeScreen
import com.gangyi.guardian.ui.keywords.KeywordsScreen
import com.gangyi.guardian.ui.onboarding.PermissionGuideScreen
import com.gangyi.guardian.ui.reminders.RemindersScreen
import com.gangyi.guardian.ui.settings.SettingsScreen
import com.gangyi.guardian.ui.stats.StatsScreen
import com.gangyi.guardian.ui.theme.GuardianTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        // UI 层要读封锁状态（主页总开关的封锁期禁用判断），先确保已初始化
        EscalationTracker.init(this)
        setContent {
            GuardianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val prefs = remember { MonitorPrefs(context) }

                    // 首启路由：从未完成引导 + 必填权限缺失 → 走引导页
                    val initialScreen = remember {
                        val needsGuide = !prefs.onboardingDone &&
                            (!Permissions.hasUsageAccess(context) ||
                                !Permissions.hasOverlay(context))
                        if (needsGuide) "guide" else "home"
                    }
                    var screen by rememberSaveable { mutableStateOf(initialScreen) }

                    // 子页面按系统返回 = 回主页，而不是退出 App
                    BackHandler(enabled = screen != "home" && screen != "guide") {
                        screen = "home"
                    }

                    when (screen) {
                        "guide" -> PermissionGuideScreen(
                            onDone = {
                                prefs.onboardingDone = true
                                screen = "home"
                            }
                        )
                        "home" -> HomeScreen(
                        onNavigateApps = { screen = "picker" },
                        onNavigateKeywords = { screen = "keywords" },
                        onNavigateReminders = { screen = "reminders" },
                        onNavigateStats = { screen = "stats" },
                        onNavigateSettings = { screen = "settings" },
                        onRequestUsageAccess = {
                            startActivity(Permissions.usageAccessIntent())
                        },
                        onRequestOverlay = {
                            startActivity(Permissions.overlayIntent(this@MainActivity))
                        },
                        onRequestAccessibility = {
                            startActivity(Permissions.accessibilityIntent())
                        }
                    )
                    "picker" -> AppPickerScreen(
                        onBack = { screen = "home" }
                    )
                    "keywords" -> KeywordsScreen(
                        onBack = { screen = "home" }
                    )
                    "reminders" -> RemindersScreen(
                        onBack = { screen = "home" }
                    )
                    "stats" -> StatsScreen(
                        onBack = { screen = "home" }
                    )
                    "settings" -> SettingsScreen(
                        onBack = { screen = "home" }
                    )
                    }
                }
            }
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
