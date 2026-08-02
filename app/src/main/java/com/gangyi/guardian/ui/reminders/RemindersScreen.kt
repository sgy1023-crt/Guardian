package com.gangyi.guardian.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.MODE_FIXED
import com.gangyi.guardian.data.MODE_RANDOM
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.Reminder
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }
    val prefs = remember { MonitorPrefs(context) }
    val scope = rememberCoroutineScope()
    val reminders by repo.reminders.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var editText by remember { mutableStateOf("") }

    var mode by remember { mutableStateOf(prefs.reminderMode) }
    var fixedId by remember { mutableStateOf(prefs.fixedReminderId) }

    fun applyMode(newMode: String) {
        mode = newMode
        prefs.reminderMode = newMode
    }
    fun applyFixedId(id: Long) {
        fixedId = id
        prefs.fixedReminderId = id
    }

    Box(modifier = Modifier.fillMaxSize().background(GuardianBg)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = GuardianText, modifier = Modifier.size(28.dp).clickable { onBack() }
                )
                Spacer(Modifier.weight(1f))
                Text("提醒语管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianText)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "弹窗时按下方选择的方式取提醒语；内置条目只读",
                fontSize = 13.sp, color = GuardianTextDim
            )
            Spacer(Modifier.height(16.dp))

            // 提醒语模式选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeChip(
                    text = "随机",
                    selected = mode == MODE_RANDOM,
                    modifier = Modifier.weight(1f)
                ) { applyMode(MODE_RANDOM) }
                ModeChip(
                    text = "指定",
                    selected = mode == MODE_FIXED,
                    modifier = Modifier.weight(1f)
                ) { applyMode(MODE_FIXED) }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (mode == MODE_FIXED)
                    "点下面任意一条设为固定提醒语，弹窗每次都用这条"
                else
                    "弹窗每次从提醒语池里随机抽一条",
                fontSize = 12.sp, color = GuardianTextFaint
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reminders, key = { if (it.builtin) "b_${it.id}" else "c_${it.id}" }) { r ->
                    val isFixed = mode == MODE_FIXED && r.id == fixedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GuardianSurface)
                            .border(
                                1.dp,
                                if (isFixed) GuardianAccent.copy(alpha = 0.5f) else GuardianBorder,
                                RoundedCornerShape(14.dp)
                            )
                            // 只有"指定"模式下点条目才有意义，随机模式下点击不做事，避免莫名切换模式
                            .clickable(enabled = mode == MODE_FIXED) { applyFixedId(r.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(r.text, fontSize = 15.sp, color = GuardianText)
                            if (isFixed) {
                                Spacer(Modifier.height(6.dp))
                                GuardianChip("固定提醒语", GuardianAccent, GuardianAccentSoft, Icons.Filled.Check)
                            }
                        }
                        if (r.builtin) {
                            GuardianChip("内置", GuardianTextDim, GuardianSurface2)
                        } else {
                            Icon(
                                Icons.Filled.Edit, "编辑", tint = GuardianTextDim,
                                modifier = Modifier.size(20.dp).clickable {
                                    editingReminder = r
                                    editText = r.text
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                Icons.Filled.Close, "删除", tint = GuardianTextDim,
                                modifier = Modifier.size(20.dp).clickable {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            repo.deleteCustomReminder(r.id)
                                            if (fixedId == r.id) {
                                                applyFixedId(-1L)
                                                applyMode(MODE_RANDOM)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true; newText = "" },
            containerColor = GuardianAccent,
            modifier = Modifier.padding(24.dp).align(Alignment.BottomEnd)
        ) { Icon(Icons.Filled.Add, "添加", tint = GuardianBg) }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加提醒语") },
            text = {
                OutlinedTextField(
                    value = newText, onValueChange = { newText = it },
                    placeholder = { Text("输入提醒语") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAdd = false
                    val t = newText.trim()
                    if (t.isNotEmpty()) {
                        scope.launch { withContext(Dispatchers.IO) { repo.addReminder(t) } }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }

    editingReminder?.let { r ->
        AlertDialog(
            onDismissRequest = { editingReminder = null },
            title = { Text("编辑提醒语") },
            text = {
                OutlinedTextField(
                    value = editText, onValueChange = { editText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = editText.trim()
                    if (t.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.updateCustomReminder(r.id, t) }
                        }
                    }
                    editingReminder = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingReminder = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun ModeChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) GuardianAccent else GuardianSurface
    val fg = if (selected) GuardianBg else GuardianTextDim
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = GuardianBg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg)
        }
    }
}
