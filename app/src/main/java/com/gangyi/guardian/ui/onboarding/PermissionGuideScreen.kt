package com.gangyi.guardian.ui.onboarding

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gangyi.guardian.permission.Permissions
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint

private data class GuideStep(
    val key: String,
    val title: String,
    val desc: String,
    val isOptional: Boolean = false
)

private val STEPS = listOf(
    GuideStep("usage", "用量访问", "用于检测你正在使用哪个 App，是守卫的核心权限。"),
    GuideStep("overlay", "悬浮窗", "用于在任意 App 上方弹出停顿提醒。"),
    GuideStep("accessibility", "无障碍", "用于在你复制含关键词的内容时触发提醒。可选，不开则只有应用监控。", isOptional = true),
    GuideStep("battery", "电池优化白名单", "防止系统在后台杀掉守卫服务。", isOptional = true),
    GuideStep("autostart", "自启动权限", "国产 ROM 必备，否则手机重启后守卫不会自动恢复。", isOptional = true),
)

@Composable
fun PermissionGuideScreen(
    onDone: () -> Unit
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableStateOf(0) }
    var hasUsage by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var hasOverlay by remember { mutableStateOf(Permissions.hasOverlay(context)) }
    var hasAccessibility by remember { mutableStateOf(Permissions.hasAccessibility(context)) }
    var hasBattery by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsage = Permissions.hasUsageAccess(context)
                hasOverlay = Permissions.hasOverlay(context)
                hasAccessibility = Permissions.hasAccessibility(context)
                hasBattery = Permissions.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun granted(key: String): Boolean = when (key) {
        "usage" -> hasUsage
        "overlay" -> hasOverlay
        "accessibility" -> hasAccessibility
        "battery" -> hasBattery
        "autostart" -> false // 没法自动检测，永远显示"去授权"
        else -> false
    }

    fun openIntent(key: String) {
        val intent = when (key) {
            "usage" -> Permissions.usageAccessIntent()
            "overlay" -> Permissions.overlayIntent(context)
            "accessibility" -> Permissions.accessibilityIntent()
            "battery" -> Permissions.batteryOptimizationIntent(context)
            "autostart" -> Permissions.autoStartIntent(context)
            else -> return
        }
        runCatching { context.startActivity(intent) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        Text("权限引导", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GuardianText)
        Text("逐项授权后，守卫才能完整工作", fontSize = 13.sp, color = GuardianTextDim)
        Spacer(Modifier.height(20.dp))

        // 进度条
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            STEPS.forEachIndexed { idx, _ ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (idx <= currentIndex) GuardianAccent else GuardianSurface2)
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // 步骤列表
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            STEPS.forEachIndexed { idx, step ->
                StepCard(
                    index = idx + 1,
                    step = step,
                    granted = granted(step.key),
                    isActive = idx == currentIndex,
                    onClick = {
                        currentIndex = idx
                        if (!granted(step.key)) openIntent(step.key)
                    }
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 进度提示
        val requiredDone = hasUsage && hasOverlay
        Text(
            if (requiredDone) "必填项已完成，可以进入主页" else "至少需要「用量访问」和「悬浮窗」两项",
            fontSize = 13.sp,
            color = if (requiredDone) GuardianSuccess else GuardianTextFaint,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onDone,
            enabled = requiredDone,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GuardianAccent,
                disabledContainerColor = GuardianSurface
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(
                if (requiredDone) "进入守卫" else "请先完成必填权限",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (requiredDone) Color.Black else GuardianTextDim
            )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("跳过引导（仅在已授权时建议）", fontSize = 12.sp, color = GuardianTextFaint)
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: GuideStep,
    granted: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val borderBg = when {
        granted -> GuardianSurface
        isActive -> GuardianSurface2
        else -> GuardianSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(borderBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号圆
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (granted) GuardianSuccess else GuardianAccent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (granted) "✓" else index.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GuardianText)
                if (step.isOptional) {
                    Spacer(Modifier.size(8.dp))
                    Text("可选", fontSize = 10.sp, color = GuardianTextFaint)
                }
            }
            Text(step.desc, fontSize = 12.sp, color = GuardianTextDim, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.size(8.dp))
        Text(
            if (granted) "已授权" else "去授权",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (granted) GuardianSuccess else GuardianAccent
        )
    }
}
