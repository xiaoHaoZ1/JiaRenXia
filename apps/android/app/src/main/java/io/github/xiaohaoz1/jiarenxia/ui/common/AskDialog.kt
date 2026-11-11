package io.github.xiaohaoz1.jiarenxia.ui.common

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
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.DialogTitle

/** 「问一问」输入上限（规格 ≤300 字）。 */
const val ASK_TEXT_MAX = 300

/**
 * 「问一问」对话框（FAMILY_AFFAIRS §25 方案 C）：
 * 预填拼好的问题的多行输入框（可改，≤300 字）；
 * 「分享…」走 ACTION_SEND text/plain chooser（不预设目标应用），
 * 「复制」写剪贴板，「取消」关闭。App 不发任何请求。
 *
 * @param initialText 预填问题（[AskText] 各入口拼好）
 * @param onShare 「分享…」：回调当前文本（由页面发 ACTION_SEND）
 * @param onCopy 「复制」：回调当前文本（由页面写剪贴板并提示）
 * @param onDismiss 关闭对话框
 */
@Composable
fun AskDialog(
    initialText: String,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("问一问") },
        text = {
            AskDialogBody(text = text) { text = it.take(ASK_TEXT_MAX) }
        },
        confirmButton = {
            TextButton(onClick = { onShare(text) }) { Text("分享…") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onCopy(text) }) { Text("复制") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 「问一问」对话框正文：问题输入框 + 场景提示（截图测试直接渲染同款内容）。 */
@Composable
internal fun AskDialogBody(
    text: String,
    onTextChange: (String) -> Unit
) {
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("问题") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "拼好的问题可以发给 AI 应用，或在微信里问医生和家人。" +
                "回答仅供参考，用药请以医生、药师的意见为准。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
