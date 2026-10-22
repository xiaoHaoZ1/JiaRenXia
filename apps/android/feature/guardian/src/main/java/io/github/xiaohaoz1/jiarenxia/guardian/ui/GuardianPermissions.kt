package io.github.xiaohaoz1.jiarenxia.guardian.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.guardian.sys.Permissions
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.TitleChip
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors

/** 权限清单页（stateless；状态的刷新与跳转动作由外层完成）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianPermissions(
    items: List<Permissions.PermissionItem>,
    onItemClick: (Permissions.PermissionItem) -> Unit,
    onOpenAutostart: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenAppDetails: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("权限清单") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                }
            }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                items.forEach { item ->
                    PermRow(item, onItemClick)
                }
            }

            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("手机系统设置", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "不同品牌的后台策略差别很大，建议全部手动确认一遍。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenAutostart,
                    modifier = Modifier.fillMaxWidth()) { Text("自启动管理") }
                OutlinedButton(onClick = onOpenBattery,
                    modifier = Modifier.fillMaxWidth()) { Text("省电策略") }
                OutlinedButton(onClick = onOpenAppDetails,
                    modifier = Modifier.fillMaxWidth()) { Text("应用信息页") }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun PermRow(
    item: Permissions.PermissionItem,
    onItemClick: (Permissions.PermissionItem) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        val ok = item.granted
        val tint = if (ok) LocalJrxColors.current.success else MaterialTheme.colorScheme.error
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                if (item.optional) {
                    Spacer(Modifier.width(6.dp))
                    TitleChip("可选")
                }
            }
            Text(
                item.desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (ok) "✓ 已开启" else "未开启",
                style = MaterialTheme.typography.bodySmall,
                color = tint
            )
        }
        if (!ok) {
            TextButton(onClick = { onItemClick(item) }) { Text("去开启") }
        }
    }
}
