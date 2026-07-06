package com.gangyi.guardian.ui.keywords

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

@Composable
fun KeywordsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }
    val prefs = remember { MonitorPrefs(context) }
    val scope = rememberCoroutineScope()
    val keywords by repo.keywords.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pwdInput by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf(false) }

    val encrypted = prefs.keywordsEncrypted

    fun maskKeyword(kw: String): String = buildString {
        append("•".repeat(kw.length.coerceAtMost(8)))
        if (kw.length > 8) append("…")
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
                Text("关键词管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianText)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (encrypted) "加密模式：列表遮罩显示，删除需密码。屏幕出现即提醒"
                else "屏幕出现以下关键词时，弹出提醒。点击删除",
                fontSize = 13.sp, color = GuardianTextDim
            )
            if (encrypted) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = GuardianAccent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("加密模式已开启", fontSize = 12.sp, color = GuardianAccent)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (keywords.isEmpty()) {
                Text("暂无关键词", fontSize = 14.sp, color = GuardianTextDim)
            } else {
                LazyColumn {
                    items(keywords, key = { it }) { kw ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(GuardianSurface, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (encrypted) {
                                        pwdInput = ""
                                        pwdError = false
                                        pendingDelete = kw
                                    } else {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { repo.removeKeyword(kw) }
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (encrypted) maskKeyword(kw) else kw,
                                fontSize = 15.sp,
                                color = if (encrypted) GuardianTextFaint else GuardianText,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Filled.Close, "删除", tint = GuardianTextDim, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(6.dp))
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
                    visualTransformation = if (encrypted) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (encrypted) KeyboardType.Password else KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                if (encrypted) {
                    Spacer(Modifier.height(8.dp))
                    Text("加密模式下输入自动遮罩，添加后列表也不可见", fontSize = 11.sp, color = GuardianTextFaint)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAdd = false
                    val t = newText.trim()
                    if (t.isNotEmpty()) {
                        scope.launch { withContext(Dispatchers.IO) { repo.addKeyword(t) } }
                        newText = ""
                    }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }

    pendingDelete?.let { kw ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null; pwdInput = ""; pwdError = false },
            title = { Text("验证密码") },
            text = {
                Column {
                    Text("删除加密关键词需要密码", fontSize = 13.sp, color = GuardianTextDim)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pwdInput, onValueChange = { pwdInput = it; pwdError = false },
                        placeholder = { Text("4~6 位数字") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = pwdError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pwdError) {
                        Spacer(Modifier.height(6.dp))
                        Text("密码错误", fontSize = 12.sp, color = GuardianAccent)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val hash = sha256(pwdInput.trim())
                    if (hash == prefs.keywordPasswordHash) {
                        scope.launch {
                            withContext(Dispatchers.IO) { repo.removeKeyword(kw) }
                        }
                        pendingDelete = null
                        pwdInput = ""
                        pwdError = false
                    } else {
                        pwdError = true
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null; pwdInput = ""; pwdError = false }) { Text("取消") }
            }
        )
    }
}

private fun sha256(s: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
