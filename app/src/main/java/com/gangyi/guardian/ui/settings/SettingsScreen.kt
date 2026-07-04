package com.gangyi.guardian.ui.settings

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.core.content.FileProvider
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { MonitorPrefs(context) }
    val repo = remember { GuardianRepository(context) }
    val scope = rememberCoroutineScope()

    var cooldown by remember { mutableFloatStateOf(prefs.cooldownSeconds.toFloat()) }
    var exporting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                tint = GuardianText, modifier = Modifier.size(28.dp).clickable { onBack() }
            )
            Spacer(Modifier.weight(1f))
            Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GuardianText)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(28.dp))
        }
        Spacer(Modifier.height(28.dp))

        // 冷却时间
        Text("提醒冷却", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("同一 App 冷却时间", fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f))
                    Text("${cooldown.toInt()} 秒", fontSize = 14.sp, color = GuardianAccent)
                }
                Slider(
                    value = cooldown,
                    onValueChange = { cooldown = it },
                    onValueChangeFinished = { prefs.cooldownSeconds = cooldown.toInt() },
                    valueRange = 5f..300f,
                    steps = 58,
                    colors = SliderDefaults.colors(
                        thumbColor = GuardianAccent,
                        activeTrackColor = GuardianAccent,
                        inactiveTrackColor = GuardianBg
                    )
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // 数据导出
        Text("数据", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .clickable(enabled = !exporting) {
                    scope.launch {
                        exporting = true
                        try {
                            val uri = exportLogs(context, repo)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "导出触发记录"))
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            exporting = false
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (exporting) "导出中…" else "导出触发记录 JSON",
                fontSize = 15.sp, color = GuardianText, modifier = Modifier.weight(1f)
            )
            Text("→", fontSize = 20.sp, color = GuardianAccent)
        }

        Spacer(Modifier.height(40.dp))

        // 关于
        Text("关于", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GuardianTextFaint)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GuardianSurface)
                .padding(16.dp)
        ) {
            Column {
                Text("守卫 Guardian v1.0", fontSize = 15.sp, color = GuardianText)
                Spacer(Modifier.height(4.dp))
                Text("帮你守住专注的 Android 自律工具", fontSize = 13.sp, color = GuardianTextDim)
                Spacer(Modifier.height(4.dp))
                Text("对标 one sec · 本地运行 · 不联网不上传", fontSize = 12.sp, color = GuardianTextFaint)
            }
        }
    }
}

private suspend fun exportLogs(context: android.content.Context, repo: GuardianRepository): android.net.Uri {
    val logs = withContext(Dispatchers.IO) { repo.allLogs() }
    val arr = JSONArray()
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
    logs.forEach { log ->
        val obj = JSONObject()
        obj.put("id", log.id)
        obj.put("packageName", log.packageName ?: "")
        obj.put("triggerType", log.triggerType)
        obj.put("keyword", log.keyword ?: "")
        obj.put("timestamp", fmt.format(java.util.Date(log.timestamp)))
        obj.put("dismissedAt", log.dismissedAt?.let { fmt.format(java.util.Date(it)) } ?: "")
        arr.put(obj)
    }
    val file = File(context.cacheDir, "guardian_export_${System.currentTimeMillis()}.json")
    file.writeText(arr.toString(2))
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
