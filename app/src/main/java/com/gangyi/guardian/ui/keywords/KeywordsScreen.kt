package com.gangyi.guardian.ui.keywords

import android.widget.Toast
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.MonitorPrefs
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.components.EmptyState
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.components.GuardianDialog
import com.gangyi.guardian.ui.components.GuardianTextField
import com.gangyi.guardian.ui.components.IconBadge
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianDanger
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import com.gangyi.guardian.util.sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 关键词列表：加密模式下遮罩显示、删除要密码；普通模式删除也要确认，别一点就没。 */
@Composable
fun KeywordsContent() {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }
    val prefs = remember { MonitorPrefs(context) }
    val scope = rememberCoroutineScope()
    val keywords by repo.keywords.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var revealNew by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pwdInput by remember { mutableStateOf("") }
    var pwdError by remember { mutableStateOf(false) }

    val encrypted = prefs.keywordsEncrypted
    val inputOnly = prefs.keywordInputOnly

    fun maskKeyword(kw: String): String = buildString {
        append("•".repeat(kw.length.coerceAtMost(8)))
        if (kw.length > 8) append("…")
    }

    fun delete(kw: String) {
        scope.launch { withContext(Dispatchers.IO) { repo.removeKeyword(kw) } }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    GuardianChip(
                        if (inputOnly) "只看你输入的" else "匹配整屏文字",
                        GuardianTextDim, GuardianSurface
                    )
                    if (encrypted) {
                        Spacer(Modifier.width(8.dp))
                        GuardianChip("已加密", GuardianAccent, GuardianAccentSoft, Icons.Rounded.Lock)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${keywords.size} 个", fontSize = 12.sp, color = GuardianTextFaint)
                }
            }

            if (keywords.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Key,
                        title = "还没有关键词",
                        hint = "点右下角加号添加。你在任何应用里打出这些词时，守卫会弹出停顿点。"
                    )
                }
            }

            items(keywords, key = { it }) { kw ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(GuardianSurface)
                        .border(1.dp, GuardianBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(
                        if (encrypted) Icons.Rounded.Lock else Icons.Rounded.Key,
                        tint = if (encrypted) GuardianTextDim else GuardianAccent,
                        background = if (encrypted) GuardianBg else GuardianAccentSoft,
                        size = 36.dp, iconSize = 18.dp
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        if (encrypted) maskKeyword(kw) else kw,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (encrypted) GuardianTextFaint else GuardianText,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                pwdInput = ""
                                pwdError = false
                                pendingDelete = kw
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, "删除", tint = GuardianTextDim, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true; newText = ""; revealNew = false },
            containerColor = GuardianAccent,
            contentColor = GuardianBg,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.padding(24.dp).align(Alignment.BottomEnd)
        ) { Icon(Icons.Rounded.Add, "添加") }
    }

    if (showAdd) {
        val trimmed = newText.trim()
        val duplicate = keywords.any { it.equals(trimmed, ignoreCase = true) }
        GuardianDialog(
            onDismiss = { showAdd = false },
            title = "添加关键词",
            confirmText = "添加",
            confirmEnabled = trimmed.isNotEmpty() && !duplicate,
            onConfirm = {
                showAdd = false
                if (trimmed.isNotEmpty()) {
                    scope.launch { withContext(Dispatchers.IO) { repo.addKeyword(trimmed) } }
                    Toast.makeText(context, "已添加", Toast.LENGTH_SHORT).show()
                }
                newText = ""
            }
        ) {
            Column {
                GuardianTextField(
                    value = newText,
                    onValueChange = { newText = it },
                    placeholder = "输入关键词",
                    visualTransformation = if (encrypted && !revealNew) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardType = if (encrypted && !revealNew) KeyboardType.Password else KeyboardType.Text,
                    trailingIcon = if (encrypted) {
                        {
                            Icon(
                                if (revealNew) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (revealNew) "隐藏" else "显示",
                                tint = GuardianTextDim,
                                modifier = Modifier.size(20.dp).clickable { revealNew = !revealNew }
                            )
                        }
                    } else null
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        duplicate -> "这个关键词已经在列表里了"
                        encrypted -> "加密模式下添加后列表里不可见，点右侧眼睛可核对输入"
                        else -> "不分大小写。词越短越容易误触发，建议 2 个字以上"
                    },
                    fontSize = 12.sp,
                    color = if (duplicate) GuardianDanger else GuardianTextFaint,
                    lineHeight = 17.sp
                )
            }
        }
    }

    pendingDelete?.let { kw ->
        if (encrypted) {
            GuardianDialog(
                onDismiss = { pendingDelete = null; pwdInput = ""; pwdError = false },
                title = "验证密码",
                confirmText = "删除",
                danger = true,
                onConfirm = {
                    if (sha256(pwdInput.trim()) == prefs.keywordPasswordHash) {
                        delete(kw)
                        pendingDelete = null
                        pwdInput = ""
                        pwdError = false
                    } else {
                        pwdError = true
                    }
                }
            ) {
                Column {
                    Text("删除加密关键词需要密码", fontSize = 13.sp, color = GuardianTextDim)
                    Spacer(Modifier.height(12.dp))
                    GuardianTextField(
                        value = pwdInput,
                        onValueChange = { pwdInput = it; pwdError = false },
                        placeholder = "4~6 位数字",
                        isError = pwdError,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardType = KeyboardType.NumberPassword
                    )
                    if (pwdError) {
                        Spacer(Modifier.height(6.dp))
                        Text("密码错误", fontSize = 12.sp, color = GuardianDanger)
                    }
                }
            }
        } else {
            GuardianDialog(
                onDismiss = { pendingDelete = null },
                title = "删除关键词",
                confirmText = "删除",
                danger = true,
                onConfirm = { delete(kw); pendingDelete = null }
            ) {
                Text("删除「$kw」？之后打出这个词不会再提醒。", fontSize = 14.sp, color = GuardianTextDim, lineHeight = 20.sp)
            }
        }
    }
}
