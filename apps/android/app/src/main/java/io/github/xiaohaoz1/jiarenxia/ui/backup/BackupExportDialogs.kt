package io.github.xiaohaoz1.jiarenxia.ui.backup

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import io.github.xiaohaoz1.jiarenxia.ui.restore.DOWNLOAD_INITIAL_URI
import io.github.xiaohaoz1.jiarenxia.ui.restore.PasswordField
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * 备份导出对话框（设置密码 / 进行中），我的页与关系图共用。
 * 到达 PickExportTarget 时由界面发起系统保存对话框（见 CreateBackupDocument）。
 */
@Composable
fun BackupExportDialogs(
    step: BackupExportStep,
    onSubmitPassword: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    /** 密码对话框里的附加说明（如关系图按分支筛选时提示备份的是全部数据）。 */
    notice: String? = null
) {
    when (step) {
        BackupExportStep.Idle, BackupExportStep.PickExportTarget -> {}
        BackupExportStep.SetPassword -> ExportPasswordDialog(
            onSubmit = onSubmitPassword,
            onDismiss = onDismiss,
            notice = notice
        )
        is BackupExportStep.Working -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(step.label)
                }
            },
            confirmButton = {}
        )
    }
}

/** 导出密码最短长度（备份与家人共享共用）。 */
const val EXPORT_PASSWORD_MIN_LENGTH = 6

/** 始终显示在密码框下方的规则说明。 */
const val EXPORT_PASSWORD_RULE = "密码规则：至少 6 位，字母、数字、符号都可以。"

/**
 * 随输入实时给出的问题提示；null 表示暂无问题（不代表已可提交）。
 * 第二次输入只要还是第一次的前缀就不报错，避免边输入边提示“不一致”。
 */
fun exportPasswordProblem(p1: String, p2: String): String? = when {
    p1.isNotEmpty() && p1.length < EXPORT_PASSWORD_MIN_LENGTH ->
        "还差 " + (EXPORT_PASSWORD_MIN_LENGTH - p1.length) + " 位"
    p2.isNotEmpty() && !p1.startsWith(p2) -> "两次输入不一致"
    else -> null
}

/** 导出密码对话框：两次输入 + 可见切换 + ≥6 位 + 一致校验，规则与问题实时显示。 */
@Composable
fun ExportPasswordDialog(
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    title: String = "设置备份密码",
    hint: String = "请牢记密码，忘记密码将无法恢复备份。",
    notice: String? = null
) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var v1 by remember { mutableStateOf(false) }
    var v2 by remember { mutableStateOf(false) }
    val ok = p1.length >= EXPORT_PASSWORD_MIN_LENGTH && p1 == p2
    val problem = exportPasswordProblem(p1, p2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                io.github.xiaohaoz1.jiarenxia.ui.common.DisableAutofill()
                PasswordField(p1, { p1 = it }, v1, { v1 = !v1 }, "密码")
                Spacer(Modifier.height(8.dp))
                PasswordField(p2, { p2 = it }, v2, { v2 = !v2 }, "再输入一次")
                Spacer(Modifier.height(8.dp))
                Text(
                    EXPORT_PASSWORD_RULE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (problem != null) {
                    Text(
                        problem,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (notice != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                val arr = p1.toCharArray()
                onSubmit(arr)
                arr.fill(' ')
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 备份默认文件名：家人侠备份_yyyyMMdd_HHmm.jrxb。 */
fun backupFileName(now: Date = Date()): String =
    "家人侠备份_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.CHINA).format(now) + ".jrxb"

/**
 * “备份到文件”行的副标题：从未备份过 → “还没有备份过”；
 * 否则 “上次备份：yyyy-MM-dd（今天 / N 天前）”。
 */
fun lastBackupSubtitle(
    atMillis: Long?,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    if (atMillis == null) return "还没有备份过"
    val day = Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(day, today)
    val rel = if (daysAgo <= 0) "今天" else daysAgo.toString() + " 天前"
    return "上次备份：" +
        day.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "（" + rel + "）"
}

/** 导出选保存位置：保存对话框先定位到「下载」。 */
class CreateBackupDocument :
    ActivityResultContracts.CreateDocument("application/octet-stream") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOAD_INITIAL_URI)
}
