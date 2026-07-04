package com.gangyi.guardian.ui.keywords

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun KeywordsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }
    val scope = rememberCoroutineScope()
    val keywords by repo.keywords.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }

    // 回来时刷新
    fun refresh() { /* Flow 自动推送，无需手动刷新 */ }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().background(GuardianBg)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = GuardianText, modifier = Modifier.size(28.dp).clickable { onBack() }
                )
                Spacer(Modifier.weight(1f))
                Text("关键词管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianText)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("复制包含以下关键词的内容时，弹出提醒", fontSize = 13.sp, color = GuardianTextDim)
            Spacer(Modifier.height(16.dp))

            if (keywords.isEmpty()) {
                Text("暂无关键词", fontSize = 14.sp, color = GuardianTextDim)
            } else {
                LazyColumn {
                    items(keywords, key = { it }) { kw ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    scope.launch { withContext(Dispatchers.IO) { repo.removeKeyword(kw) }; refresh() }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(kw, fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.Close, "删除", tint = GuardianTextDim, modifier = Modifier.size(20.dp))
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
            title = { Text("添加关键词") },
            text = {
                OutlinedTextField(
                    value = newText, onValueChange = { newText = it },
                    placeholder = { Text("输入关键词") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAdd = false
                    val t = newText.trim()
                    if (t.isNotEmpty()) {
                        scope.launch { withContext(Dispatchers.IO) { repo.addKeyword(t) }; refresh() }
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}
