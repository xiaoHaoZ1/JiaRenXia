package io.github.xiaohaoz1.jiarenxia.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.ContactPrompt
import io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
import io.github.xiaohaoz1.jiarenxia.domain.call.CallQuestionPrompt

/**
 * “打完电话提示记录”（联系提醒）：页面恢复前台时，若 10 分钟窗口内
 * 从 App 拨过家人电话，弹 [PostCallPromptDialog] 问“联系上了吗”——
 * 联系上了按拨号时刻记一条电话联系，并删除勾选的「只问一次」要问的事；
 * 没联系上不记也不删，提醒继续留着。
 * 拨号侧由 ViewModel.onDialMade 写入 pending；提示一次后清除。
 *
 * @param onLog 「联系上了」：回调 prompt 与勾选中的只问一次条目 id
 *              （没联系上/关闭时不回调，什么都不删）。
 */
@Composable
fun PostCallPromptEffect(
    appEvents: AppEvents,
    onLog: (ContactPrompt, Set<String>) -> Unit
) {
    var prompt by remember { mutableStateOf<ContactPrompt?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val pending = appEvents.pendingContactPrompt ?: return@LifecycleEventObserver
            appEvents.pendingContactPrompt = null
            if (!ContactSchedule.promptVisible(
                    pending.atMillis, System.currentTimeMillis())
            ) return@LifecycleEventObserver
            prompt = pending
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    prompt?.let { p ->
        PostCallPromptDialog(
            label = p.label,
            oneTimeQuestions = p.oneTimeQuestions,
            onConnected = { checked -> onLog(p, checked); prompt = null },
            onDismiss = { prompt = null }
        )
    }
}

/**
 * “联系上了吗”对话框：称谓/姓名 + 右上关闭钮；
 * 本次拨号前弹过要问的事且有「只问一次」条目时，
 * 正文下方加「问过的就划掉：」勾选区（默认全勾）；
 * 「没联系上」关闭不记也不删（联系提醒不重置、要问的事不删），
 * 「联系上了」记一条电话联系并删除勾选的只问一次条目。
 */
@Composable
internal fun PostCallPromptDialog(
    label: String,
    oneTimeQuestions: List<CallQuestionPrompt> = emptyList(),
    onConnected: (Set<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    // 「问过的就划掉」勾选：默认全勾；「联系上了」时回传勾选集
    var checked by remember {
        mutableStateOf<Set<String>>(
            oneTimeQuestions.mapTo(HashSet()) { it.id })
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("联系上了吗？", modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Column {
                Text("刚才给" + label + "打了电话。" +
                    "联系上了记一笔，没联系上提醒还会留着。")
                if (oneTimeQuestions.isNotEmpty()) {
                    Text(
                        "问过的就划掉：",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    oneTimeQuestions.forEach { q ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = q.id in checked,
                                onCheckedChange = { on ->
                                    checked = if (on) checked + q.id
                                    else checked - q.id
                                }
                            )
                            Text(
                                q.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnected(checked) }) {
                Text("联系上了")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("没联系上") }
        }
    )
}
