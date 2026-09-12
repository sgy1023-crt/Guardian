package com.gangyi.guardian.ui.apps

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gangyi.guardian.data.db.GuardianRepository
import com.gangyi.guardian.ui.components.EmptyState
import com.gangyi.guardian.ui.components.SearchField
import com.gangyi.guardian.ui.theme.GuardianAccent
import com.gangyi.guardian.ui.theme.GuardianAccentSoft
import com.gangyi.guardian.ui.theme.GuardianBg
import com.gangyi.guardian.ui.theme.GuardianBorder
import com.gangyi.guardian.ui.theme.GuardianSurface
import com.gangyi.guardian.ui.theme.GuardianSurface2
import com.gangyi.guardian.ui.theme.GuardianText
import com.gangyi.guardian.ui.theme.GuardianTextDim
import com.gangyi.guardian.ui.theme.GuardianTextFaint
import com.gangyi.guardian.util.AppInfo
import com.gangyi.guardian.util.InstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 选要监控的应用：搜索 + 已选一排 + 全量列表。图标按行懒加载。 */
@Composable
fun AppPickerContent() {
    val context = LocalContext.current
    val repo = remember { GuardianRepository(context) }
    val scope = rememberCoroutineScope()
    val selectedList by repo.monitoredPackages.collectAsState(initial = emptyList())
    val selected = remember(selectedList) { selectedList.toSet() }
    var query by rememberSaveable { mutableStateOf("") }

    val apps by produceState<List<AppInfo>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { InstalledApps.load(context) }
    }

    fun toggle(pkg: String) {
        val on = pkg in selected
        scope.launch(Dispatchers.IO) {
            if (on) repo.removeApp(pkg) else repo.addApp(pkg)
        }
    }

    val list = apps
    if (list == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GuardianAccent)
        }
        return
    }

    val labelOf = remember(list) { list.associate { it.packageName to it.label } }
    val filtered = remember(list, query) {
        val q = query.trim()
        if (q.isEmpty()) list
        else list.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SearchField(query, { query = it }, "搜索应用名或包名")
        }

        if (selected.isNotEmpty()) {
            item {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text("已选 ${selected.size} 个", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GuardianTextFaint, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedList, key = { "sel_$it" }) { pkg ->
                            SelectedChip(labelOf[pkg] ?: pkg) { toggle(pkg) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.Apps, "没找到应用", "换个关键词试试")
            }
        }

        items(filtered, key = { it.packageName }) { app ->
            AppRow(app, checked = app.packageName in selected) { toggle(app.packageName) }
        }
    }
}

@Composable
private fun SelectedChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GuardianAccentSoft)
            .clickable { onRemove() }
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GuardianAccent)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.Close, contentDescription = "移除", tint = GuardianAccent, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = withContext(Dispatchers.IO) { InstalledApps.icon(context, app.packageName) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GuardianSurface)
            .border(1.dp, if (checked) GuardianAccent.copy(alpha = 0.55f) else GuardianBorder, shape)
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val bmp = icon
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp))
            )
        } else {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(GuardianSurface2))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = GuardianText)
            Text(app.packageName, fontSize = 11.sp, color = GuardianTextFaint)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (checked) GuardianAccent else GuardianSurface2)
                .border(1.dp, if (checked) GuardianAccent else GuardianBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Rounded.Check, contentDescription = "监控中", tint = GuardianBg, modifier = Modifier.size(16.dp))
            }
        }
    }
}
