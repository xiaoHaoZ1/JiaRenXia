package io.github.xiaohaoz1.jiarenxia.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.ContactPrompt
import io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule

/**
 * “打完电话提示记录”（联系提醒）：页面恢复前台时，若 10 分钟窗口内
 * 从 App 拨过家人电话，弹 [PostCallPromptDialog] 问“联系上了吗”——
 * 联系上了按拨号时刻记一条电话联系；没联系上不记，提醒继续留着。
 * 拨号侧由 ViewModel.onDialMade 写入 pending；提示一次后清除。
 */
@Composable
fun PostCallPromptEffect(
    appEvents: AppEvents,
    onLog: (ContactPrompt) -> Unit
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
            onConnected = { onLog(p); prompt = null },
            onDismiss = { prompt = null }
        )
    }
}

/**
 * “联系上了吗”对话框：称谓/姓名 + 右上关闭钮；
 * 「没联系上」关闭不记（联系提醒不重置），「联系上了」记一条电话联系。
 */
@Composable
internal fun PostCallPromptDialog(
    label: String,
    onConnected: () -> Unit,
    onDismiss: () -> Unit
) {
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
            Text("刚才给" + label + "打了电话。" +
                "联系上了记一笔，没联系上提醒还会留着。")
        },
        confirmButton = {
            TextButton(onClick = onConnected) { Text("联系上了") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("没联系上") }
        }
    )
}
