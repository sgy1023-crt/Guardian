package com.gangyi.guardian.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gangyi.guardian.ui.apps.AppPickerContent
import com.gangyi.guardian.ui.components.ScreenHeader
import com.gangyi.guardian.ui.components.SegmentedControl
import com.gangyi.guardian.ui.keywords.KeywordsContent
import com.gangyi.guardian.ui.reminders.RemindersContent
import com.gangyi.guardian.ui.theme.GuardianBg

/** 规则页：监控哪些应用、盯哪些关键词、弹什么话。三段一页，不用来回跳。 */
@Composable
fun RulesScreen() {
    var segment by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GuardianBg)
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))
            ScreenHeader(
                "规则",
                when (segment) {
                    0 -> "打开这些应用时弹出停顿点"
                    1 -> "打出这些词时弹出停顿点"
                    else -> "停顿点上显示的那句话"
                }
            )
            Spacer(Modifier.height(16.dp))
            SegmentedControl(
                options = listOf("监控应用", "关键词", "提醒语"),
                selected = segment,
                onSelect = { segment = it }
            )
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth().fillMaxSize()) {
            when (segment) {
                0 -> AppPickerContent()
                1 -> KeywordsContent()
                else -> RemindersContent()
            }
        }
    }
}
