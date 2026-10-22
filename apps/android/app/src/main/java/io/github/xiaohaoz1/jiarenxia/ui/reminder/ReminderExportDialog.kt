package io.github.xiaohaoz1.jiarenxia.ui.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.ui.common.JrxDatePickerDialog
import java.time.LocalDate

/**
 * 提醒导出范围：未来 30 天（默认）/ 未来 90 天 / 自定义起止。
 */
internal enum class ExportRangePreset(val label: String) {
    DAYS_30("未来 30 天"), DAYS_90("未来 90 天"), CUSTOM("自定义")
}

/**
 * 提醒导出对话框：选日期范围与是否包含未完成逾期事项。
 * 自定义起止颠倒时确认时自动纠正（小的在前）。
 * 对话框本体拆为 [ReminderExportDialogBody] 便于截图测试。
 */
@Composable
internal fun ReminderExportDialog(
    onConfirm: (from: LocalDate, to: LocalDate, includeOverdue: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var preset by remember { mutableStateOf(ExportRangePreset.DAYS_30) }
    var customFrom by remember { mutableStateOf(LocalDate.now()) }
    var customTo by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    var includeOverdue by remember { mutableStateOf(true) }
    // 自定义模式下正打开的日期选择器（null=关闭，true=选开始，false=选结束）
    var pickingFrom by remember { mutableStateOf<Boolean?>(null) }

    if (preset == ExportRangePreset.CUSTOM && pickingFrom != null) {
        JrxDatePickerDialog(
            current = if (pickingFrom == true) customFrom else customTo,
            onPick = { d ->
                if (pickingFrom == true) customFrom = d else customTo = d
            },
            onDismiss = { pickingFrom = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出表格") },
        text = {
            ReminderExportDialogBody(
                preset = preset, onPreset = {
                    preset = it
                    if (it == ExportRangePreset.CUSTOM) pickingFrom = true
                },
                customFrom = customFrom, customTo = customTo,
                includeOverdue = includeOverdue, onToggleOverdue = { includeOverdue = it },
                onPickFrom = { pickingFrom = true }, onPickTo = { pickingFrom = false }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val today = LocalDate.now()
                val (from, to) = when (preset) {
                    ExportRangePreset.DAYS_30 -> today to today.plusDays(30)
                    ExportRangePreset.DAYS_90 -> today to today.plusDays(90)
                    ExportRangePreset.CUSTOM ->
                        if (customFrom <= customTo) customFrom to customTo
                        else customTo to customFrom
                }
                onConfirm(from, to, includeOverdue)
            }) { Text("导出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 对话框正文（无状态）：范围芯片 + 自定义起止 + 含逾期开关。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderExportDialogBody(
    preset: ExportRangePreset,
    onPreset: (ExportRangePreset) -> Unit,
    customFrom: LocalDate,
    customTo: LocalDate,
    includeOverdue: Boolean,
    onToggleOverdue: (Boolean) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit
) {
    Column {
        Text(
            "包含生日、纪念日和家庭事项",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportRangePreset.entries.forEach { p ->
                FilterChip(
                    selected = preset == p,
                    onClick = { onPreset(p) },
                    label = { Text(p.label) }
                )
            }
        }
        if (preset == ExportRangePreset.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPickFrom) { Text(customFrom.toString()) }
                Text(
                    "至",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onPickTo) { Text(customTo.toString()) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.weight(1f)) {
                Text("包含未完成逾期事项", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "逾期项不受所选范围限制",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = includeOverdue, onCheckedChange = onToggleOverdue)
        }
    }
}
