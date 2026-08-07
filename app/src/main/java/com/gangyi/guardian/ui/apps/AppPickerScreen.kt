package com.gangyi.guardian.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.MonitoredApp
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.util.AppInfo
import com.gangyi.guardian.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var appLimits by remember { mutableStateOf(mapOf<String, Int>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            selected = repo.listMonitoredPackages().toSet()
            appLimits = repo.listMonitoredApps().associate { it.packageName to it.dailyLimitMinutes }
        }
    }

    val apps by produceState<List<AppInfo>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(GuardianBg).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = GuardianText,
                modifier = Modifier.size(28.dp).clickable { onBack() }
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("选择要监控的 App", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianText)
                Text("已监控 App 可独立设置每日使用时长上限", fontSize = 12.sp, color = GuardianTextDim)
            }
        }
        Spacer(Modifier.height(16.dp))

        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GuardianAccent)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    val currentLimit = appLimits[app.packageName] ?: 0

                    AppRow(
                        app = app,
                        checked = checked,
                        currentLimit = currentLimit,
                        onToggle = {
                            val newSelected = if (checked) selected - app.packageName else selected + app.packageName
                            selected = newSelected
                            scope.launch(Dispatchers.IO) {
                                if (checked) repo.removeApp(app.packageName)
                                else repo.addApp(app.packageName)
                            }
                        },
                        onLimitChange = { minutes ->
                            appLimits = appLimits + (app.packageName to minutes)
                            scope.launch(Dispatchers.IO) {
                                repo.setAppDailyLimit(app.packageName, minutes)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    currentLimit: Int,
    onToggle: () -> Unit,
    onLimitChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val bmp = remember(app.packageName) { app.icon?.toBitmap(72, 72)?.asImageBitmap() }
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(Modifier.size(40.dp).background(GuardianTextDim))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontSize = 16.sp, color = GuardianText)
                Row {
                    Text(app.packageName, fontSize = 12.sp, color = GuardianTextDim)
                    if (checked && currentLimit > 0) {
                        Text(
                            " · 每日限额 ${currentLimit}min",
                            fontSize = 12.sp,
                            color = GuardianAccent
                        )
                    }
                }
            }
            Text(
                if (checked) "✓ 监控中" else "添加",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (checked) GuardianSuccess else GuardianAccent
            )
        }

        // 已监控 App 显示每日限额滑块
        if (checked) {
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "每日限额",
                    fontSize = 11.sp,
                    color = GuardianTextDim,
                    modifier = Modifier.width(72.dp)
                )
                Slider(
                    value = currentLimit.toFloat(),
                    onValueChange = { onLimitChange(it.roundToInt()) },
                    valueRange = 0f..180f,
                    steps = 35,
                    modifier = Modifier.weight(1f).height(32.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = GuardianAccent,
                        activeTrackColor = GuardianAccent,
                        inactiveTrackColor = GuardianBg
                    )
                )
                Text(
                    if (currentLimit == 0) "不限" else "${currentLimit}min",
                    fontSize = 11.sp,
                    color = if (currentLimit == 0) GuardianTextDim else GuardianAccent,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}
