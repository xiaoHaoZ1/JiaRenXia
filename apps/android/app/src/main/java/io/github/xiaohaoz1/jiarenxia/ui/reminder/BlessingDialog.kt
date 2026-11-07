package io.github.xiaohaoz1.jiarenxia.ui.reminder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.domain.BirthdayBlessing
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.DialogTitle

/**
 * 生日祝福对话框（FAMILY_AFFAIRS 22）：预填默认祝福语可改，
 * 有电话时「发短信」打开短信应用由用户点发送；没电话时「复制」去微信。
 * 只预填/复制，不申请短信权限。
 *
 * @param label 称谓（无称谓用姓名），用于标题与默认文案
 * @param elder 长辈判定（决定默认文案用“您/你”）
 * @param number 默认电话号码；null 表示没记电话（只给复制）
 * @param onSendSms 「发短信」：回调当前文本（仅 number != null 时出现）
 * @param onCopy 「复制」：回调当前文本
 * @param onDismiss 关闭对话框（各按钮与点外部都走这里）
 */
@Composable
fun BlessingDialog(
    label: String,
    elder: Boolean,
    number: String?,
    onSendSms: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(BirthdayBlessing.defaultText(label, elder))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("给" + label + "发祝福") },
        text = {
            BlessingDialogBody(number = number, text = text) { text = it }
        },
        confirmButton = {
            if (number != null) {
                TextButton(onClick = { onSendSms(text) }) { Text("发短信") }
            } else {
                TextButton(onClick = { onCopy(text) }) { Text("复制") }
            }
        },
        dismissButton = {
            Row {
                if (number != null) {
                    TextButton(onClick = { onCopy(text) }) { Text("复制") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 祝福对话框正文：多行祝福语输入 + 场景提示（截图测试直接渲染同款内容）。 */
@Composable
internal fun BlessingDialogBody(
    number: String?,
    text: String,
    onTextChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { onTextChange(it.take(200)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Text(
            if (number != null) "可以改一改再发，会打开短信，由你点发送"
            else "没有记电话，复制后可以粘贴到微信",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
