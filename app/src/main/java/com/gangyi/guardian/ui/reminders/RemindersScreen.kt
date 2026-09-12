package com.gangyi.guardian.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.gangyi.guardian.data.MODE_FIXED
import com.gangyi.guardian.data.MODE_RANDOM
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.data.db.Reminder
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.components.GuardianDialog
import com.gangyi.guardian.ui.components.GuardianTextField
import com.gangyi.guardian.ui.components.SegmentedControl
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

/** 提醒语：随机抽 / 指定一条。内置条目只读，自定义可编辑删除。 */
@Composable
fun RemindersContent() {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }
    val prefs = remember { MonitorPrefs(context) }
    val scope = rememberCoroutineScope()
    val reminders by repo.reminders.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }
    var editText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Reminder?>(null) }

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

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Column {
                    SegmentedControl(
                        options = listOf("每次随机", "固定一条"),
                        selected = if (mode == MODE_FIXED) 1 else 0,
                        onSelect = { applyMode(if (it == 1) MODE_FIXED else MODE_RANDOM) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (mode == MODE_FIXED) "点下面任意一条设为固定提醒语" else "每次停顿从下面的话里随机抽一句",
                        fontSize = 12.sp, color = GuardianTextFaint, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
            }

            items(reminders, key = { if (it.builtin) "b_${it.id}" else "c_${it.id}" }) { r ->
                val isFixed = mode == MODE_FIXED && r.id == fixedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isFixed) GuardianAccentSoft else GuardianSurface)
                        .border(1.dp, if (isFixed) GuardianAccent.copy(alpha = 0.5f) else GuardianBorder, RoundedCornerShape(16.dp))
                        .clickable(enabled = mode == MODE_FIXED) { applyFixedId(r.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (mode == MODE_FIXED) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isFixed) GuardianAccent else GuardianSurface2)
                                .border(1.dp, if (isFixed) GuardianAccent else GuardianBorder, RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isFixed) Icon(Icons.Rounded.Check, null, tint = GuardianBg, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.text, fontSize = 15.sp, color = GuardianText, lineHeight = 22.sp)
                        if (r.builtin) {
                            Spacer(Modifier.height(6.dp))
                            GuardianChip("内置", GuardianTextDim, GuardianSurface2)
                        }
                    }
                    if (!r.builtin) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.Edit, "编辑", tint = GuardianTextDim,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).clickable {
                                editingReminder = r
                                editText = r.text
                            }.padding(7.dp)
                        )
                        Icon(
                            Icons.Rounded.Close, "删除", tint = GuardianTextDim,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).clickable {
                                pendingDelete = r
                            }.padding(7.dp)
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true; newText = "" },
            containerColor = GuardianAccent,
            contentColor = GuardianBg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(24.dp).align(Alignment.BottomEnd)
        ) { Icon(Icons.Rounded.Add, "添加") }
    }

    if (showAdd) {
        GuardianDialog(
            onDismiss = { showAdd = false },
            title = "添加提醒语",
            confirmText = "添加",
            confirmEnabled = newText.isNotBlank(),
            onConfirm = {
                showAdd = false
                val t = newText.trim()
                if (t.isNotEmpty()) {
                    scope.launch { withContext(Dispatchers.IO) { repo.addReminder(t) } }
                }
            }
        ) {
            Column {
                GuardianTextField(newText, { newText = it }, "写一句能让你停下来的话")
                Spacer(Modifier.height(8.dp))
                Text("用第二人称、说人话。例：你本来要做什么来着？", fontSize = 12.sp, color = GuardianTextFaint)
            }
        }
    }

    editingReminder?.let { r ->
        GuardianDialog(
            onDismiss = { editingReminder = null },
            title = "编辑提醒语",
            confirmText = "保存",
            confirmEnabled = editText.isNotBlank(),
            onConfirm = {
                val t = editText.trim()
                if (t.isNotEmpty()) {
                    scope.launch { withContext(Dispatchers.IO) { repo.updateCustomReminder(r.id, t) } }
                }
                editingReminder = null
            }
        ) {
            GuardianTextField(editText, { editText = it }, "提醒语")
        }
    }

    pendingDelete?.let { r ->
        GuardianDialog(
            onDismiss = { pendingDelete = null },
            title = "删除提醒语",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { repo.deleteCustomReminder(r.id) }
                    if (fixedId == r.id) {
                        applyFixedId(-1L)
                        applyMode(MODE_RANDOM)
                    }
                }
                pendingDelete = null
            }
        ) {
            Text("删除「${r.text}」？", fontSize = 14.sp, color = GuardianTextDim, lineHeight = 20.sp)
        }
    }
}
