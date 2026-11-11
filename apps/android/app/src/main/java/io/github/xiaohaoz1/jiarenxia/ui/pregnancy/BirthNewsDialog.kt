package io.github.xiaohaoz1.jiarenxia.ui.pregnancy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** 报喜正文生成（纯函数，截图/测试可直接断言）。 */
object BirthNewsText {

    /**
     * 预填正文：“欢迎宝宝来到我们家！” + 每个宝宝一句
     * “{名字}于 M月D日[ HH:mm] 出生”，多个宝宝用“，”连接，末尾“。”。
     * 只有一个宝宝且填了体重时，在出生那句后追加“，体重 {体重}”。
     */
    fun body(babies: List<DoneBaby>, weight: String?): String {
        val w = weight?.trim()?.takeIf { it.isNotEmpty() }
        val suffix = if (w != null) "，体重 " + w else ""
        val clauses = babies.map { b ->
            b.name + "于 " + b.birthDate.monthValue + "月" +
                b.birthDate.dayOfMonth + "日" +
                (b.birthMinute?.let {
                    " %02d:%02d".format(it / 60, it % 60)
                } ?: "") +
                " 出生" + suffix
        }
        return "欢迎宝宝来到我们家！" + clauses.joinToString("，") + "。"
    }
}

/**
 * 报喜对话框（仿 BlessingDialog）：多行正文（≤200 字）+ 单宝宝时的体重行。
 * 用户手动改过正文后，体重变化不再覆盖正文。
 * [onShare] 由页面发 ACTION_SEND；[onCopy] 由页面写剪贴板并提示。
 */
@Composable
fun BirthNewsDialog(
    babies: List<DoneBaby>,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(BirthNewsText.body(babies, null)) }
    var weight by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("报喜") },
        text = {
            BirthNewsFields(
                text = text,
                onText = {
                    if (it.length <= 200) {
                        text = it
                        edited = true
                    }
                },
                weight = weight,
                onWeight = {
                    if (it.length <= 10) {
                        weight = it
                        if (!edited) {
                            text = BirthNewsText.body(babies, weight)
                        }
                    }
                },
                showWeight = babies.size == 1
            )
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

/** 报喜对话框正文（截图测试直接渲染）。 */
@Composable
internal fun BirthNewsFields(
    text: String,
    onText: (String) -> Unit,
    weight: String,
    onWeight: (String) -> Unit,
    showWeight: Boolean
) {
    Column {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            label = { Text("正文") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        if (showWeight) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = weight,
                onValueChange = onWeight,
                label = { Text("体重（可不填，如 6斤8两）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
