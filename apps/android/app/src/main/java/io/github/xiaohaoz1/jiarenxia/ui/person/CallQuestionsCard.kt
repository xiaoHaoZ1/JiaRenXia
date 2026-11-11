package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.xiaohaoz1.jiarenxia.data.CallQuestionRepository
import io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.DialogTitle
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard

/** 「要问的事」单条文字上限（仓库同口径）。 */
private const val QUESTION_TEXT_MAX = CallQuestionRepository.MAX_TEXT

/**
 * 人物页「打电话要问的事」卡（FAMILY_AFFAIRS 26 第 7 批）：
 * 「联系」卡之前；只对在世、非“我”的人物显示（外层按 ContactSchedule.eligible 门控）。
 * 空态给说明 + 「添加」；有条目时逐行展示（keep 行下标「每次都问」），
 * 行尾 ⋮ 菜单「改一改 / 每次都问|只问一次 / 删除」，末尾「添加」（满 10 条隐藏）。
 *
 * @param onAdd 新增（文字已 trim、1–40 字由仓库再校验）
 * @param onUpdate 改文字与/或 keep（null 表示不改该项）
 * @param onDelete 删除（页面负责 snackbar「已删除」）
 */
@Composable
fun CallQuestionsCard(
    questions: List<CallQuestionEntity>,
    onAdd: (text: String, keep: Boolean) -> Unit,
    onUpdate: (id: String, text: String?, keep: Boolean?) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CallQuestionEntity?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    JrxCard(modifier.fillMaxWidth()) {
        SectionTitle("打电话要问的事")
        if (questions.isEmpty()) {
            Text(
                "想在电话里问的事记在这里，从家人侠拨号前会提醒你。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { adding = true }) { Text("添加") }
        } else {
            questions.forEach { q ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(q.text, style = MaterialTheme.typography.bodyLarge)
                        if (q.keep) {
                            Text(
                                "每次都问",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuFor = q.id }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "要问的事操作：" + q.text
                            )
                        }
                        DropdownMenu(
                            expanded = menuFor == q.id,
                            onDismissRequest = { menuFor = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("改一改") },
                                onClick = {
                                    menuFor = null
                                    editing = q
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (q.keep) "只问一次" else "每次都问")
                                },
                                onClick = {
                                    menuFor = null
                                    onUpdate(q.id, null, !q.keep)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    menuFor = null
                                    onDelete(q.id)
                                }
                            )
                        }
                    }
                }
            }
            if (questions.size < CallQuestionRepository.MAX_PER_PERSON) {
                TextButton(onClick = { adding = true }) { Text("添加") }
            }
        }
    }

    if (adding) {
        CallQuestionEditDialog(
            initial = null,
            onSave = { text, keep -> onAdd(text, keep) },
            onDismiss = { adding = false }
        )
    }
    editing?.let { q ->
        CallQuestionEditDialog(
            initial = q,
            onSave = { text, keep -> onUpdate(q.id, text, keep) },
            onDismiss = { editing = null }
        )
    }
}

/**
 * 「要问的事」添加 / 改一改对话框：
 * 标题随新增/编辑切换；文字 ≤40 字（超出不让输入）；
 * 「每次打电话都问」勾选（新增默认不勾）；「保存」trim 后为空不可点。
 */
@Composable
internal fun CallQuestionEditDialog(
    initial: CallQuestionEntity?,
    onSave: (text: String, keep: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial?.text ?: "") }
    var keep by remember { mutableStateOf(initial?.keep ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogTitle(if (initial == null) "添加要问的事" else "改一改")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(QUESTION_TEXT_MAX) },
                    label = { Text("要问什么") },
                    placeholder = { Text("比如：有没有按时吃药") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text("每次打电话都问")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.trim().isNotEmpty(),
                onClick = { onSave(text.trim(), keep); onDismiss() }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
