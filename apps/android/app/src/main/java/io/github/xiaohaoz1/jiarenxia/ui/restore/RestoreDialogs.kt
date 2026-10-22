package io.github.xiaohaoz1.jiarenxia.ui.restore

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupSummary

/**
 * 恢复流程对话框（FAMILY_AFFAIRS 16.1/16.2）：问密码 / 进行中 / 确认恢复。
 * 我的页（从备份恢复）与欢迎页（用已有资料开始）共用。
 */
@Composable
fun RestoreDialogs(
    step: RestoreStep,
    onSubmitPassword: (CharArray) -> Unit,
    onConfirm: () -> Unit,
    onNotMe: () -> Unit,
    onDismiss: () -> Unit
) {
    when (step) {
        RestoreStep.Idle -> {}
        RestoreStep.AskPassword -> PasswordDialog(
            title = "输入文件密码",
            onSubmit = onSubmitPassword,
            onDismiss = onDismiss
        )
        is RestoreStep.Working -> AlertDialog(
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
        is RestoreStep.Confirm -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("恢复此备份？") },
            text = {
                RestoreConfirmBody(step.summary, step.overwrites, onNotMe)
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    // 覆盖现有数据才用警示色（空机恢复无风险）
                    Text(
                        "恢复",
                        color = if (step.overwrites)
                            MaterialTheme.colorScheme.error
                        else androidx.compose.ui.graphics.Color.Unspecified
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        )
    }
}

/**
 * 确认恢复对话框正文（特大字号可滚动；抽出供截图测试）。
 * 备份里有“我”时给出“我不是 X，按家人共享导入”的改道入口（16.2）。
 */
@Composable
fun RestoreConfirmBody(
    summary: BackupSummary,
    overwrites: Boolean,
    onNotMe: () -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            (summary.createdAt?.let {
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm", java.util.Locale.CHINA
                ).format(java.util.Date(it)) + " 导出的备份，"
            } ?: "") +
            "包含 " + summary.persons + " 位家人、" + summary.relationships +
            " 条关系、" + summary.birthdays + " 个生日、" + summary.anniversaries +
            " 个纪念日、" + summary.tasks + " 个事项。" +
            (summary.selfName?.let { "\n恢复后，“我”是" + it + "。" } ?: "") +
            (if (overwrites) "\n恢复将覆盖当前全部数据，且无法撤销。" else "")
        )
        summary.selfName?.let {
            TextButton(
                onClick = onNotMe,
                modifier = Modifier.align(Alignment.End)
            ) { Text("我不是" + it + "，按家人共享导入") }
        }
    }
}

/** 单密码输入对话框（恢复用）。 */
@Composable
internal fun PasswordDialog(
    title: String,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit
) {
    var pw by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                io.github.xiaohaoz1.jiarenxia.ui.common.DisableAutofill()
                PasswordField(pw, { pw = it }, visible, { visible = !visible }, "密码")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val arr = pw.toCharArray()
                onSubmit(arr)
                arr.fill(' ')
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 密码输入框：默认遮蔽，尾部眼睛切换。 */
@Composable
internal fun PasswordField(
    value: String, onChange: (String) -> Unit,
    visible: Boolean, onToggle: () -> Unit, label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible)
            androidx.compose.ui.text.input.VisualTransformation.None
        else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggle) {
                Text(if (visible) "隐藏" else "显示",
                    style = MaterialTheme.typography.labelMedium)
            }
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
}

/** SAF「下载」目录的初始定位 Uri（API 26+，minSdk 26 可用）。 */
internal val DOWNLOAD_INITIAL_URI = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents", "primary:Download")

/**
 * 打开家人侠文件（备份恢复 / 欢迎页选资料共用）：系统选择器先定位到「下载」
 * （HyperOS 的 Download 视图不列出 .jrxb，不定位只能靠搜索）。
 */
internal class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOAD_INITIAL_URI)
}
