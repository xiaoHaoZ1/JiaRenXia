package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.data.backup.QuickSnapshotStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * “测试工具”分组卡片（我的页）：快速存档 / 快速读档 / 清空数据但保留存档。
 * 只做展示，业务在 [QuickSnapshotViewModel] 与 [QuickSnapshotStore]。
 */
@Composable
fun TestToolsCard(
    onQuickSave: () -> Unit,
    onQuickRestore: () -> Unit,
    onClearKeepSnapshots: () -> Unit
) {
    MineCard {
        CardRow(Icons.Rounded.Science, "测试工具")
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onQuickSave)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("快速存档")
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onQuickRestore)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("快速读档")
        }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClearKeepSnapshots)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("清空数据但保留存档")
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "存档只在本机有效：覆盖安装后仍可用，卸载、清除应用数据或" +
                "在 debug 与正式版之间互换后都会丢失；长期保存请用“备份到文件”。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 快速读档对话框：存档列表（时间 / 人数 / “我”）→ 选一份 → 覆盖确认。
 */
@Composable
fun QuickSnapshotDialogs(
    listOpen: Boolean,
    snapshots: List<QuickSnapshotStore.Snapshot>,
    pendingRestore: QuickSnapshotStore.Snapshot?,
    busy: Boolean,
    onPick: (QuickSnapshotStore.Snapshot) -> Unit,
    onConfirmRestore: () -> Unit,
    onDismissList: () -> Unit
) {
    if (listOpen && pendingRestore == null) {
        AlertDialog(
            onDismissRequest = { if (!busy) onDismissList() },
            title = { Text("快速读档") },
            text = {
                if (snapshots.isEmpty()) {
                    Text("还没有快速存档")
                } else {
                    Column(
                        Modifier.heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        snapshots.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = !busy) { onPick(s) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(formatSnapshotTime(s.createdAt))
                                    Text(
                                        s.persons.toString() + " 人 · " +
                                            s.relationships + " 条关系" +
                                            (s.selfName?.let { " · 我：$it" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = onDismissList
                ) { Text("取消") }
            }
        )
    }
    pendingRestore?.let { s ->
        AlertDialog(
            onDismissRequest = { if (!busy) onDismissList() },
            title = { Text("快速读档") },
            text = {
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("正在恢复…")
                    }
                } else {
                    Text(
                        formatSnapshotTime(s.createdAt) +
                            "（" + s.persons + " 人）\n\n" +
                            "将用这份存档覆盖当前全部数据。"
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = onConfirmRestore) {
                    Text("覆盖恢复")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = onDismissList) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatSnapshotTime(epochMs: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
