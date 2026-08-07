package com.gangyi.guardian.ui.apps

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.gangyi.guardian.data.StudyBlock
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
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
    var appCustomBlocks by remember { mutableStateOf(mapOf<String, List<StudyBlock>>()) }
    var appUseCustom by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            selected = repo.listMonitoredPackages().toSet()
            val apps = repo.listMonitoredApps()
            appLimits = apps.associate { it.packageName to it.dailyLimitMinutes }
            appCustomBlocks = apps
                .filter { it.useCustomStudyBlocks && !it.studyBlocksJson.isNullOrBlank() }
                .associate { a ->
                    a.packageName to runCatching {
                        StudyBlock.listFromJson(a.studyBlocksJson!!)
                    }.getOrDefault(emptyList())
                }
            appUseCustom = apps.filter { it.useCustomStudyBlocks }.map { it.packageName }.toSet()
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
                Text("已监控 App 可独立设置每日限额和学习时段", fontSize = 12.sp, color = GuardianTextDim)
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
                    val customBlocks = appCustomBlocks[app.packageName] ?: emptyList()
                    val useCustom = app.packageName in appUseCustom

                    AppRow(
                        app = app,
                        checked = checked,
                        currentLimit = currentLimit,
                        customBlocks = customBlocks,
                        useCustomBlocks = useCustom,
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
                        },
                        onBlocksChange = { blocks, useC ->
                            appCustomBlocks = appCustomBlocks + (app.packageName to blocks)
                            appUseCustom = if (useC) appUseCustom + app.packageName
                                          else appUseCustom - app.packageName
                            scope.launch(Dispatchers.IO) {
                                repo.setAppStudyBlocks(app.packageName, blocks, useC)
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
    customBlocks: List<StudyBlock>,
    useCustomBlocks: Boolean,
    onToggle: () -> Unit,
    onLimitChange: (Int) -> Unit,
    onBlocksChange: (List<StudyBlock>, Boolean) -> Unit
) {
    // 本地编辑缓冲（mutableStateListOf 支持逐个修改）
    val editBlocks = remember(app.packageName, useCustomBlocks) {
        androidx.compose.runtime.mutableStateListOf<StudyBlock>().also {
            if (useCustomBlocks) it.addAll(customBlocks)
            else it.clear()
        }
    }
    var editUseCustom by remember(app.packageName) { mutableStateOf(useCustomBlocks) }

    // 将本地编辑同步到父级
    fun syncBlocks() {
        if (editUseCustom && editBlocks.isNotEmpty()) {
            onBlocksChange(editBlocks.toList(), true)
        } else if (editUseCustom && editBlocks.isEmpty()) {
            // 开启了自定义但没添加时段 → 视为空列表（等于全天不禁）
            onBlocksChange(emptyList(), true)
        } else {
            onBlocksChange(emptyList(), false)
        }
    }

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
                    if (checked && editUseCustom && editBlocks.isNotEmpty()) {
                        Text(
                            " · 自定义学习时段",
                            fontSize = 12.sp,
                            color = GuardianSuccess
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

        // 已监控 App 显示每日限额滑块 + 自定义学习时段
        if (checked) {
            Spacer(Modifier.height(4.dp))

            // 每日限额
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

            Spacer(Modifier.height(8.dp))

            // 自定义学习时段开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GuardianSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("自定义学习时段", fontSize = 12.sp, color = GuardianText)
                    Text(
                        if (editUseCustom) "覆盖全局学习时段设置" else "使用全局学习时段设置",
                        fontSize = 10.sp,
                        color = GuardianTextFaint
                    )
                }
                Switch(
                    checked = editUseCustom,
                    onCheckedChange = { on ->
                        editUseCustom = on
                        if (on && editBlocks.isEmpty()) {
                            // 默认添加一个 14:00-18:00 时段
                            editBlocks.add(StudyBlock(startHour = 14, startMinute = 0, endHour = 18, endMinute = 0))
                        }
                        syncBlocks()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GuardianAccent,
                        checkedTrackColor = GuardianAccent.copy(alpha = 0.4f)
                    )
                )
            }

            // 自定义时段编辑区
            if (editUseCustom) {
                Spacer(Modifier.height(6.dp))

                editBlocks.forEachIndexed { idx, block ->
                    val blockIdx = idx
                    if (idx > 0) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(1.dp)
                                .background(GuardianBg)
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    // 时段标题 + 删除
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "时段 ${idx + 1}  ${block.startTimeStr()} → ${block.endTimeStr()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GuardianAccent,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            editBlocks.removeAt(blockIdx)
                            syncBlocks()
                        }) {
                            Text("删除", fontSize = 11.sp, color = GuardianAccent)
                        }
                    }

                    // 始时
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("始时", fontSize = 10.sp, color = GuardianTextFaint, modifier = Modifier.width(28.dp))
                        Slider(
                            value = block.startHour.toFloat(),
                            onValueChange = {
                                editBlocks[blockIdx] = block.copy(startHour = it.roundToInt())
                                syncBlocks()
                            },
                            valueRange = 0f..23f,
                            steps = 22,
                            modifier = Modifier.weight(1f).height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = GuardianAccent,
                                activeTrackColor = GuardianAccent,
                                inactiveTrackColor = GuardianBg
                            )
                        )
                        Text("${block.startHour}h", fontSize = 10.sp, color = GuardianAccent, modifier = Modifier.width(28.dp))
                    }
                    // 始分
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("始分", fontSize = 10.sp, color = GuardianTextFaint, modifier = Modifier.width(28.dp))
                        Slider(
                            value = block.startMinute.toFloat(),
                            onValueChange = {
                                editBlocks[blockIdx] = block.copy(startMinute = it.roundToInt())
                                syncBlocks()
                            },
                            valueRange = 0f..55f,
                            steps = 10,
                            modifier = Modifier.weight(1f).height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = GuardianAccent,
                                activeTrackColor = GuardianAccent,
                                inactiveTrackColor = GuardianBg
                            )
                        )
                        Text("${block.startMinute}m", fontSize = 10.sp, color = GuardianAccent, modifier = Modifier.width(28.dp))
                    }
                    // 终时
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("终时", fontSize = 10.sp, color = GuardianTextFaint, modifier = Modifier.width(28.dp))
                        Slider(
                            value = block.endHour.toFloat(),
                            onValueChange = {
                                editBlocks[blockIdx] = block.copy(endHour = it.roundToInt())
                                syncBlocks()
                            },
                            valueRange = 0f..23f,
                            steps = 22,
                            modifier = Modifier.weight(1f).height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = GuardianAccent,
                                activeTrackColor = GuardianAccent,
                                inactiveTrackColor = GuardianBg
                            )
                        )
                        Text("${block.endHour}h", fontSize = 10.sp, color = GuardianAccent, modifier = Modifier.width(28.dp))
                    }
                    // 终分
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("终分", fontSize = 10.sp, color = GuardianTextFaint, modifier = Modifier.width(28.dp))
                        Slider(
                            value = block.endMinute.toFloat(),
                            onValueChange = {
                                editBlocks[blockIdx] = block.copy(endMinute = it.roundToInt())
                                syncBlocks()
                            },
                            valueRange = 0f..55f,
                            steps = 10,
                            modifier = Modifier.weight(1f).height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = GuardianAccent,
                                activeTrackColor = GuardianAccent,
                                inactiveTrackColor = GuardianBg
                            )
                        )
                        Text("${block.endMinute}m", fontSize = 10.sp, color = GuardianAccent, modifier = Modifier.width(28.dp))
                    }

                    // 跨夜提示
                    if (block.startTotalMinutes >= block.endTotalMinutes) {
                        Text("⚠ 跨夜时段", fontSize = 10.sp, color = GuardianAccent)
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 添加时段
                if (editBlocks.size < 5) {
                    TextButton(onClick = {
                        editBlocks.add(StudyBlock(startHour = 14, startMinute = 0, endHour = 18, endMinute = 0))
                        syncBlocks()
                    }) {
                        Text("＋ 添加时段（${editBlocks.size}/5）", fontSize = 11.sp, color = GuardianAccent)
                    }
                } else {
                    Text("已达上限 5 个时段", fontSize = 10.sp, color = GuardianTextFaint)
                }
            }
        }
    }
}
