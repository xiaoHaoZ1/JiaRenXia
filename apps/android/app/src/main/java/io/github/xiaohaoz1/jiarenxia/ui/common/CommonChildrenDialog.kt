package io.github.xiaohaoz1.jiarenxia.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.xiaohaoz1.jiarenxia.data.CommonChildrenAsk

/** 已有孩子详情中的补全入口，点击后仍需确认实际父母关系。 */
@Composable
fun ParentCompletionSuggestion(ask: CommonChildrenAsk, onClick: () -> Unit) {
    Text(
        "已记录的" + (if (ask.parentLabel == "父亲") "母亲" else "父亲") +
            "「" + ask.spouseAName + "」有配偶「" + ask.spouseBName + "」，可确认是否也是TA的" + ask.parentLabel + "。",
        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
    )
    TextButton(onClick = onClick) { Text("确认" + ask.parentLabel + " · " + ask.spouseBName) }
}

/**
 * 结为夫妻后的“共同子女”询问对话框：
 * 一个候选时标题直问“X是你们共同的女儿吗？”，由“确定 / 都不是”作答；
 * 多个候选时逐个勾选（默认都不勾），“确定”只写勾选项，“都不是”不写入。
 */
@Composable
fun CommonChildrenDialog(
    ask: CommonChildrenAsk,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onNone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNone,
        title = {
            Text(
                if (ask.parentLabel != null && ask.candidates.size == 1) {
                    ask.spouseBName + "也是" + ask.candidates.first().child.name + "的" + ask.parentLabel + "吗？"
                } else if (ask.candidates.size == 1) {
                    val c = ask.candidates.first()
                    c.child.name + "是你们共同的" + c.childLabel + "吗？"
                } else {
                    "下面哪些是你们共同的孩子？"
                }
            )
        },
        text = if (ask.parentLabel != null) {
            {
                Text("已记录「" + ask.spouseAName + "」是孩子的" +
                    (if (ask.parentLabel == "父亲") "母亲" else "父亲") +
                    "，两人是夫妻。确认后会补上父母关系，并在关系图中连线。")
            }
        } else if (ask.candidates.size <= 1) null else {
            {
                Column {
                    ask.candidates.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // 整行可点：勾选目标比单个 Checkbox 大
                            modifier = Modifier.fillMaxWidth().toggleable(
                                value = c.child.id in ask.checkedIds,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(c.child.id) }
                            )
                        ) {
                            Checkbox(
                                checked = c.child.id in ask.checkedIds,
                                onCheckedChange = null
                            )
                            Text(
                                c.child.name + "（" + ask.sideNameOf(c) +
                                    "的" + c.childLabel + "）"
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确定") } },
        dismissButton = { TextButton(onClick = onNone) { Text(if (ask.parentLabel != null) "暂不添加" else "都不是") } }
    )
}
