package io.github.xiaohaoz1.jiarenxia.ui.reminder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader

/**
 * 家事页顶部：页头（标题“家事” + 提醒副标题）+ 铃铛与“更多”菜单行。
 *
 * 2026-10-20 起人情/保障分段退出（A6），家事页只显示提醒内容：
 * 铃铛进三类提醒设置，“更多 → 导出表格”只导出家事提醒。
 * 独立成件以便截图测试直接渲染。
 */
@Composable
internal fun AffairsHeader(
    subtitle: String,
    onExport: () -> Unit,
    /** 铃铛：三类提醒的开关与时刻（2026-10-09 从「我的」搬来）。 */
    onReminderSettings: () -> Unit = {}
) {
    PageHeader {
        Column {
            Text("家事", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onReminderSettings) {
            Icon(Icons.Rounded.NotificationsNone, contentDescription = "提醒设置")
        }
        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("导出表格") },
                    onClick = { menuOpen = false; onExport() }
                )
            }
        }
    }
}
