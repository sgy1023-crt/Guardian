package com.gangyi.guardian

import android.os.Bundle
import androidx.activity.ComponentActivity
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
}
