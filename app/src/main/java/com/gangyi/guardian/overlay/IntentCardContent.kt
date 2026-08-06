package com.gangyi.guardian.overlay

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint

/** 时长预设（秒） */
private val TIME_PRESETS = listOf(
    300 to "5 分钟",
    600 to "10 分钟",
    900 to "15 分钟",
    1800 to "30 分钟"
)

@Composable
fun IntentCardContent(
    appLabel: String,
    defaultTimeSeconds: Int,
    onStart: (reason: String, timeLimitSeconds: Int) -> Unit,
    onCancel: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var reason by remember { mutableStateOf("") }
    var selectedSeconds by remember { mutableIntStateOf(defaultTimeSeconds) }
    var customMinutes by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    var reasonError by remember { mutableStateOf(false) }

    val effectiveSeconds = if (showCustom) {
        (customMinutes.toIntOrNull() ?: 0) * 60
    } else {
        selectedSeconds
    }
    val canStart = reason.trim().length >= 3 && effectiveSeconds >= 60

    fun doStart() {
        val r = reason.trim()
        if (r.length < 3) {
            reasonError = true
            return
        }
        val secs = effectiveSeconds.coerceIn(60, 7200) // 1 分钟 ~ 2 小时
        onStart(r, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE8000000))
            .clickable(enabled = false) { /* 拦截穿透点击 */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(GuardianSurface, RoundedCornerShape(24.dp))
                .padding(28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 呼吸圆点
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(GuardianAccent, CircleShape)
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "你要打开「$appLabel」",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GuardianText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "先想清楚：为什么？用多久？",
                fontSize = 14.sp,
                color = GuardianTextDim,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            // 理由输入
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it; reasonError = false },
                placeholder = {
                    Text("比如：查考研政治时政热点…", color = GuardianTextFaint)
                },
                label = { Text("打开理由") },
                isError = reasonError,
                supportingText = if (reasonError) {
                    { Text("理由最少 3 个字，请诚实地写") }
                } else {
                    { Text("${reason.length}/3 字最少 · 对自己诚实") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GuardianText,
                    unfocusedTextColor = GuardianText,
                    focusedBorderColor = GuardianAccent,
                    unfocusedBorderColor = GuardianTextDim,
                    focusedLabelColor = GuardianAccent,
                    unfocusedLabelColor = GuardianTextDim,
                    cursorColor = GuardianAccent,
                    errorBorderColor = GuardianAccent,
                    errorLabelColor = GuardianAccent,
                    errorSupportingTextColor = GuardianAccent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            // 时长选择
            Text(
                "使用时长",
                fontSize = 13.sp,
                color = GuardianTextFaint,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TIME_PRESETS.forEach { (secs, label) ->
                    val isSelected = !showCustom && selectedSeconds == secs
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) GuardianAccent else GuardianBg,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                showCustom = false
                                selectedSeconds = secs
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label.replace(" ", "\n"),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else GuardianTextDim,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 自定义时长
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (showCustom) GuardianAccent else GuardianBg,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { showCustom = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "自定义",
                        fontSize = 11.sp,
                        color = if (showCustom) Color.Black else GuardianTextDim
                    )
                }
                if (showCustom) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("分钟") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GuardianText,
                            unfocusedTextColor = GuardianText,
                            focusedBorderColor = GuardianAccent,
                            unfocusedBorderColor = GuardianTextDim,
                            cursorColor = GuardianAccent
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // 操作按钮
            Button(
                onClick = { doStart() },
                enabled = canStart,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GuardianSuccess,
                    disabledContainerColor = GuardianBg
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(
                    text = if (effectiveSeconds >= 60)
                        "开始使用（${formatSeconds(effectiveSeconds)}）"
                    else
                        "请选择使用时长",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canStart) Color.Black else GuardianTextDim
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("还是算了", fontSize = 14.sp, color = GuardianTextDim)
            }
        }
    }
}

private fun formatSeconds(totalSecs: Int): String {
    val m = totalSecs / 60
    val s = totalSecs % 60
    return if (s == 0) "${m} 分钟" else "${m} 分 ${s} 秒"
}
