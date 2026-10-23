package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.ConnectWithoutContact
import androidx.compose.material.icons.rounded.Diversity1
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 功能介绍与管理（我的 → 二级页）：第一组介绍核心功能，
 * 第二组跳到共享/碰一碰。
 * 2026-10-20（A6）人情/保障彻底移除后，原「家事页的分段」开关组删除；
 * 旧开关值随功能一并清理，不再保留入口或历史资料页。
 * C3（2026-10-20）：守护拆为独立应用「守护侠」，本页不再放守护入口。
 */
@Composable
fun ModulesScreen(
    onBack: () -> Unit,
    /** 跳到「共享与连接」。 */
    onOpenShareConnect: () -> Unit = {}
) {
    ModulesContent(onBack = onBack, onOpenShareConnect = onOpenShareConnect)
}

@Composable
fun ModulesContent(
    onBack: () -> Unit,
    onOpenShareConnect: () -> Unit = {}
) {
    SettingsScaffold("功能介绍与管理", onBack) {
        SettingsGroup("基础功能") {
            ModuleInfoRow(
                icon = Icons.Rounded.Diversity1,
                title = "记家人",
                subtitle = "添加家人，记下生日、电话和称呼"
            )
            RowDivider()
            ModuleInfoRow(
                icon = Icons.Rounded.Hub,
                title = "查关系",
                subtitle = "家庭关系图自动连成，看清谁是谁"
            )
            RowDivider()
            ModuleInfoRow(
                icon = Icons.Rounded.Cake,
                title = "记生日",
                subtitle = "生日、纪念日、要办的事都会提醒"
            )
        }
        SettingsGroup("更多功能") {
            SettingsNavRow(
                icon = Icons.Rounded.ConnectWithoutContact,
                title = "共享与连接",
                subtitle = "分享家庭档案 · 接收家人分享 · 碰一碰",
                onClick = onOpenShareConnect
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 介绍行：图标 + 标题 + 说明，不可点。 */
@Composable
private fun ModuleInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
