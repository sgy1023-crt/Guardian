package com.gangyi.guardian.ui.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gangyi.guardian.permission.PermissionState
import com.gangyi.guardian.permission.Permissions
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.components.IconBadge
import com.gangyi.guardian.ui.components.PrimaryButton
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianSuccess
import com.gangyi.guardian.ui.theme.GuardianSuccessSoft
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint

private data class GuideStep(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val required: Boolean,
    /** 没法自动检测的项（自启动），点过一次就当办完 */
    val manualOnly: Boolean = false
)

private val STEPS = listOf(
    GuideStep("usage", Icons.Rounded.Visibility, "用量访问", "知道你正在用哪个应用。这是守卫的核心，没有它什么都做不了。", required = true),
    GuideStep("overlay", Icons.Rounded.Layers, "悬浮窗", "在任何应用上方弹出停顿点。", required = true),
    GuideStep("accessibility", Icons.Rounded.Accessibility, "无障碍", "识别你输入的关键词；封锁时把你送回桌面。不开的话只有应用监控，封锁只能挡一堵墙。", required = false),
    GuideStep("battery", Icons.Rounded.BatteryChargingFull, "电池优化白名单", "防止系统在后台杀掉守卫。", required = false),
    GuideStep("autostart", Icons.Rounded.RestartAlt, "自启动", "国产手机必备，否则重启后守卫不会自动恢复。系统没法告诉我们开没开，请你亲自确认。", required = false, manualOnly = true),
)

@Composable
fun PermissionGuideScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var perms by remember { mutableStateOf(PermissionState.read(context)) }
    var autostartVisited by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) perms = PermissionState.read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun granted(key: String): Boolean = when (key) {
        "usage" -> perms.usage
        "overlay" -> perms.overlay
        "accessibility" -> perms.accessibility
        "battery" -> perms.battery
        "autostart" -> autostartVisited
        else -> false
    }

    fun open(key: String) {
        val intent = when (key) {
            "usage" -> Permissions.usageAccessIntent()
            "overlay" -> Permissions.overlayIntent(context)
            "accessibility" -> Permissions.accessibilityIntent()
            "battery" -> Permissions.batteryOptimizationIntent(context)
            "autostart" -> { autostartVisited = true; Permissions.autoStartIntent(context) }
            else -> return
        }
        runCatching { context.startActivity(intent) }
    }

    val requiredDone = perms.requiredGranted
    val doneCount = STEPS.count { granted(it.key) }
    // 第一个没办的就是"当前该办的"
    val activeKey = STEPS.firstOrNull { !granted(it.key) }?.key

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(36.dp))
            IconBadge(Icons.Rounded.Shield, size = 56.dp, iconSize = 28.dp)
            Spacer(Modifier.height(20.dp))
            Text("先把权限开好", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GuardianText)
            Spacer(Modifier.height(6.dp))
            Text(
                "守卫要在别的应用上方弹窗、要知道你在用什么，这些都得你亲手允许。前两项必须开，其余的强烈建议。",
                fontSize = 14.sp, color = GuardianTextDim, lineHeight = 21.sp
            )
            Spacer(Modifier.height(20.dp))

            // 进度
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    STEPS.forEach { s ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (granted(s.key)) GuardianSuccess else GuardianSurface2)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("$doneCount / ${STEPS.size}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GuardianTextDim)
            }
            Spacer(Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                STEPS.forEachIndexed { idx, step ->
                    StepCard(
                        index = idx + 1,
                        step = step,
                        granted = granted(step.key),
                        isActive = step.key == activeKey,
                        onClick = { if (!granted(step.key)) open(step.key) }
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // 底部固定动作区
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GuardianBg)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                if (requiredDone) "必填项已开好，随时可以开始" else "「用量访问」和「悬浮窗」开好后才能继续",
                fontSize = 12.sp,
                color = if (requiredDone) GuardianSuccess else GuardianTextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                text = if (requiredDone) "开始使用" else "先完成必填权限",
                onClick = onDone,
                enabled = requiredDone,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("先跳过，稍后在「守护」页补开", fontSize = 12.sp, color = GuardianTextFaint)
            }
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
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (granted) GuardianSurface else if (isActive) GuardianAccentSoft else GuardianSurface)
            .border(
                1.dp,
                when {
                    granted -> GuardianSuccess.copy(alpha = 0.35f)
                    isActive -> GuardianAccent.copy(alpha = 0.6f)
                    else -> GuardianBorder
                },
                shape
            )
            .clickable(enabled = !granted) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (granted) GuardianSuccessSoft else if (isActive) GuardianAccent.copy(alpha = 0.18f) else GuardianSurface2),
            contentAlignment = Alignment.Center
        ) {
            if (granted) {
                Icon(Icons.Rounded.Check, null, tint = GuardianSuccess, modifier = Modifier.size(20.dp))
            } else {
                Icon(step.icon, null, tint = if (isActive) GuardianAccent else GuardianTextDim, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(step.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GuardianText)
                Spacer(Modifier.width(8.dp))
                if (step.required) {
                    GuardianChip("必须", GuardianAccent, GuardianAccentSoft)
                } else {
                    GuardianChip("建议", GuardianTextDim, GuardianSurface2)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(step.desc, fontSize = 12.sp, color = GuardianTextDim, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (!granted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) GuardianAccent else GuardianSurface2)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (step.manualOnly) "去看看" else "去开启",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (isActive) GuardianBg else GuardianTextDim
                )
            }
        }
    }
}
