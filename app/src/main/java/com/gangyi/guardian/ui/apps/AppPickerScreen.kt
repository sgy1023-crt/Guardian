package com.gangyi.guardian.ui.apps

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.components.GuardianChip
import com.gangyi.guardian.ui.components.GuardianTopBar
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import com.gangyi.guardian.util.AppInfo
import com.gangyi.guardian.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppPickerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            selected = repo.listMonitoredPackages().toSet()
        }
    }

    val apps by produceState<List<AppInfo>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(GuardianBg).padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        GuardianTopBar("选择要监控的 App", onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "已选 ${selected.size} 个",
            fontSize = 13.sp,
            color = GuardianTextFaint,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GuardianAccent)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.packageName }) { app ->
                    val checked = app.packageName in selected
                    AppRow(app, checked) {
                        val newSelected = if (checked) selected - app.packageName else selected + app.packageName
                        selected = newSelected
                        scope.launch(Dispatchers.IO) {
                            if (checked) repo.removeApp(app.packageName)
                            else repo.addApp(app.packageName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GuardianSurface)
            // 选中态用主色描边，一眼扫得出哪些在监控，不用逐行读文字
            .border(1.dp, if (checked) GuardianAccent.copy(alpha = 0.55f) else GuardianBorder, shape)
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = remember(app.packageName) { app.icon?.toBitmap(72, 72)?.asImageBitmap() }
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
            )
        } else {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(GuardianTextDim))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontSize = 16.sp, color = GuardianText)
            Text(app.packageName, fontSize = 12.sp, color = GuardianTextDim)
        }
        if (checked) {
            GuardianChip("监控中", GuardianAccent, GuardianAccentSoft, Icons.Filled.Check)
        } else {
            Text("添加", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = GuardianTextFaint)
        }
    }
}
