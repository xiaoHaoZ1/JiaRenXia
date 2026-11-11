package io.github.xiaohaoz1.jiarenxia.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity
import io.github.xiaohaoz1.jiarenxia.domain.call.CallQuestionPrompt
import io.github.xiaohaoz1.jiarenxia.domain.call.CallQuestions
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.DialogTitle

/**
 * 拨号前「打给…之前」待确认请求（FAMILY_AFFAIRS 26 第 7 批）：
 * 该人有「打电话要问的事」时，拨号先弹 [PreCallQuestionsDialog]；
 * 「去拨号」才继续走各页原有拨号流程（ACTION_DIAL + 写“联系上了吗”待提示）。
 */
class PreCallRequest(
    /** 称谓或姓名（与“联系上了吗”同一 label 规则：称谓优先，无则姓名）。 */
    val label: String,
    /** 该人当前要问的事快照（含 keep 标记；顺序即展示顺序）。 */
    val questions: List<CallQuestionEntity>,
    /** 「去拨号」：各页注入真正发起拨号与写待提示的动作。 */
    val onConfirm: () -> Unit
)

/**
 * 三页共用的拨号前检查：现取该人要问的事；
 * 有 → 返回 [PreCallRequest] 由页面弹框；没有 → 直接调 [onDial] 并返回 null。
 * [onDial] 收到本次「只问一次」条目（没有问题或全是「每次都问」时为空表），
 * 供 ContactPrompt 带往「联系上了吗」划掉区。
 */
suspend fun preCallRequest(
    questionsFor: suspend (String) -> List<CallQuestionEntity>,
    personId: String,
    label: String,
    onDial: (List<CallQuestionPrompt>) -> Unit
): PreCallRequest? {
    val qs = questionsFor(personId)
    val once = CallQuestions.onceQuestions(qs)
    return if (CallQuestions.shouldPrompt(qs)) {
        PreCallRequest(label, qs) { onDial(once) }
    } else {
        onDial(once)
        null
    }
}

/**
 * 「打给…之前」对话框：逐条列出要问的事；
 * 「去拨号」继续拨号，「取消」不拨号。
 */
@Composable
fun PreCallQuestionsDialog(request: PreCallRequest, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle("打给" + request.label + "之前") },
        text = { PreCallQuestionsBody(request.questions) },
        confirmButton = {
            TextButton(onClick = {
                request.onConfirm()
                onDismiss()
            }) { Text("去拨号") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 「打给…之前」正文：「记得问：」+ 逐条列出（截图测试直接渲染同款内容）。 */
@Composable
internal fun PreCallQuestionsBody(questions: List<CallQuestionEntity>) {
    Column {
        Text("记得问：", style = MaterialTheme.typography.bodyLarge)
        questions.forEach { q ->
            Text(
                "· " + q.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
