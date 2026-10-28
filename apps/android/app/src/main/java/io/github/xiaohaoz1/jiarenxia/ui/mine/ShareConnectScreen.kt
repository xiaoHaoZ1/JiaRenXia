package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diversity1
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.domain.ceremony.WeddingWords
import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
import io.github.xiaohaoz1.jiarenxia.ui.restore.OpenBackupDocument
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreDialogs
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreOutcome

/**
 * 共享与连接（家庭页 → 二级页）：把家庭档案发给家人、接收家人的分享、碰一碰。
 *
 * 这些动作本质是「和家人交换资料」，2026-10-09 改版从「我的 → 数据」搬到家庭页，
 * 与「数据管理」（只给自己留副本）分开。
 * 「分享给家人」与关系图的分享是同一入口：直接进选人页（默认全选）。
 * 共享文件只含家人档案——事项/人情/保单/备注一律不附带。
 */
@Composable
fun ShareConnectScreen(
    onBack: () -> Unit,
    /** 分享给家人 → 选人页。 */
    onShareScope: () -> Unit,
    /** 识别出共享文件 → 共享导入页。 */
    onShareImport: () -> Unit,
    /** 碰一碰：kind = TapProtocol.KIND_*。 */
    onTap: (Byte) -> Unit,
    onNeedsSelf: () -> Unit = {},
    /** 识别出协作包 → 协作接收页。 */
    onCollabDetected: () -> Unit = {},
    viewModel: MineViewModel = hiltViewModel(),
    collabViewModel: io.github.xiaohaoz1.jiarenxia.ui.share
        .CollabViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 接收：打开文件 → RestoreFlow 按 purpose 分流（共享文件转共享导入页）
    val receiveLauncher = rememberLauncherForActivityResult(
        OpenBackupDocument()
    ) { uri -> viewModel.restore.onSource(uri) }
    val shareDetected by viewModel.restore.shareDetected.collectAsStateWithLifecycle()
    LaunchedEffect(shareDetected) {
        if (shareDetected) {
            viewModel.restore.shareDetected.value = false
            onShareImport()
        }
    }
    // 协作包 → 协作接收页
    val collabDetected by viewModel.restore.collabDetected
        .collectAsStateWithLifecycle()
    LaunchedEffect(collabDetected) {
        if (collabDetected) {
            viewModel.restore.collabDetected.value = false
            onCollabDetected()
        }
    }
    // 协作状态/仅本机名单（每次进入页时刷新）
    LaunchedEffect(Unit) { collabViewModel.refresh() }
    val collabStatus by collabViewModel.status.collectAsStateWithLifecycle()
    val collabPendingDiff by collabViewModel.pendingDiff
        .collectAsStateWithLifecycle()
    val collabLocalOnly by collabViewModel.localOnlyPersons
        .collectAsStateWithLifecycle()
    val collabSendStep by collabViewModel.sendStep.collectAsStateWithLifecycle()
    var localOnlyOpen by remember { mutableStateOf(false) }
    val collabMessage by collabViewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(collabMessage) {
        collabMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            collabViewModel.onMessageShown()
        }
    }
    val restoreOutcome by viewModel.restore.outcome.collectAsStateWithLifecycle()
    LaunchedEffect(restoreOutcome) {
        if (restoreOutcome == RestoreOutcome.RESTORED_WITHOUT_SELF) {
            viewModel.restore.outcome.value = null
            onNeedsSelf()
        } else if (restoreOutcome != null) {
            viewModel.restore.outcome.value = null
        }
    }
    LaunchedEffect(st.message) {
        st.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }

    var showWeddingWords by remember { mutableStateOf(false) }

    ShareConnectContent(
        weddingWords = WeddingWords.split(st.weddingWords),
        onBack = onBack,
        onShare = onShareScope,
        onReceive = { receiveLauncher.launch(arrayOf("*/*")) },
        onTap = onTap,
        onWeddingWordsClick = { showWeddingWords = true },
        collabStatus = collabStatus,
        collabPendingDiff = collabPendingDiff,
        onPublish = collabViewModel::publish,
        onContribute = collabViewModel::contribute,
        onResumeDraft = onCollabDetected,
        localOnlyCount = collabLocalOnly.size,
        onLocalOnlyClick = { localOnlyOpen = true }
    )

    // ---------- 协作发送流程（密码→生成→ACTION_SEND） ----------
    io.github.xiaohaoz1.jiarenxia.ui.share.CollabSendFlow(
        sendStep = collabSendStep,
        onSubmitPassword = collabViewModel::submitSendPassword,
        onDismiss = collabViewModel::dismissSend,
        onSent = collabViewModel::sendSent
    )

    // ---------- 仅本机保留名单 ----------
    if (localOnlyOpen) {
        AlertDialog(
            onDismissRequest = { localOnlyOpen = false },
            title = { Text("仅本机保留（" + collabLocalOnly.size + "）") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (collabLocalOnly.isEmpty()) {
                        Text("没有仅本机保留的家人")
                    }
                    collabLocalOnly.forEach { (person, lo) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(person.name,
                                    style = MaterialTheme.typography.bodyLarge)
                                Text(lo.note ?: "仅本机保留",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                collabViewModel.unmark(person.id)
                            }) { Text("恢复共享") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { localOnlyOpen = false }) { Text("关闭") }
            }
        )
    }

    if (showWeddingWords) {
        WeddingWordsDialog(
            current = WeddingWords.split(st.weddingWords),
            onSave = { viewModel.setWeddingWords(it); showWeddingWords = false },
            onDismiss = { showWeddingWords = false }
        )
    }

    // 接收到的若是普通备份文件，仍走恢复确认（与我的页同一套对话框）
    val restoreStep by viewModel.restore.step.collectAsStateWithLifecycle()
    RestoreDialogs(
        step = restoreStep,
        onSubmitPassword = { viewModel.restore.submitPassword(it, context) },
        onConfirm = viewModel.restore::confirm,
        onNotMe = viewModel.restore::notMe,
        onDismiss = viewModel.restore::dismiss
    )
}

@Composable
fun ShareConnectContent(
    weddingWords: Pair<String, String> = WeddingWords.DEFAULT,
    onBack: () -> Unit,
    onShare: () -> Unit = {},
    onReceive: () -> Unit = {},
    onTap: (Byte) -> Unit = {},
    onWeddingWordsClick: () -> Unit = {},
    collabStatus: io.github.xiaohaoz1.jiarenxia.data.collab
        .FamilyCollaborationService.Status? = null,
    /** 本机相对基准的差异项数（成员“发回补充”副文案）。 */
    collabPendingDiff: Int = 0,
    onPublish: () -> Unit = {},
    onContribute: () -> Unit = {},
    onResumeDraft: () -> Unit = {},
    localOnlyCount: Int = 0,
    onLocalOnlyClick: () -> Unit = {}
) {
    SettingsScaffold("共享与连接", onBack) {
        SettingsHint("和家人一起，把关系补完整。")
        SettingsGroup("家庭档案") {
            SettingsNavRow(
                icon = Icons.Rounded.Share,
                title = "分享给家人",
                subtitle = "选择家人范围，生成共享文件",
                onClick = onShare
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Download,
                title = "接收家人分享",
                subtitle = "打开共享文件，预览后合并",
                onClick = onReceive
            )
        }
        SettingsHint("共享文件带密码，请通过电话等方式单独告诉对方。")

        // ---------- 家庭协作（版本化长期补充，FAMILY_AFFAIRS §5） ----------
        SettingsGroup("家庭协作（持续补充版本）") {
            SettingsNavRow(
                icon = Icons.Rounded.Publish,
                title = "发布确认版",
                subtitle = if (collabStatus?.role == "member")
                    "本机是成员，确认版由汇总者发布"
                else "汇总者整理后发出" +
                    (collabStatus?.revision?.takeIf { it > 0 }
                        ?.let { "，当前第 " + it + " 版" } ?: "") +
                    "，可反复补充",
                onClick = onPublish
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.AutoMirrored.Rounded.ReplyAll,
                title = "发回补充",
                subtitle = if (collabStatus?.role == "member")
                    "把本机的补充和纠正发给汇总者 · " +
                        (if (collabPendingDiff > 0)
                            "尚有 " + collabPendingDiff + " 项与基准不同"
                        else "没有新的补充")
                else "加入协作后，成员用这里发回补充",
                onClick = onContribute
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.CheckCircle,
                title = "继续未完成的接收",
                subtitle = if (collabStatus?.pendingDraftPackageId != null)
                    "有一份协作包待核对"
                else "暂无待处理的协作包",
                badge = if (collabStatus?.pendingDraftPackageId != null)
                    "待办" else null,
                onClick = onResumeDraft
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.PersonAddAlt1,
                title = "仅本机保留",
                subtitle = "不参与共享和协作的家人",
                value = if (localOnlyCount > 0) "" + localOnlyCount + " 人"
                    else null,
                onClick = onLocalOnlyClick
            )
        }
        SettingsHint(
            if (collabStatus?.bound == true)
                "已加入家庭协作系列，当前第 " + collabStatus.revision + " 版" +
                    (if (collabStatus.role == "coordinator") "（你是汇总者）"
                    else "（你是成员）")
            else "还没有加入协作：汇总者先「发布确认版」，家人收到后即可反复补充。"
        )

        SettingsGroup("碰一碰 · 实验功能") {
            SettingsNavRow(
                icon = Icons.Rounded.Favorite,
                title = "结为夫妻",
                subtitle = "领证拍照时一碰 · 两家关系合并",
                onClick = { onTap(TapProtocol.KIND_SPOUSE) }
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.FormatQuote,
                title = "祝福语",
                value = WeddingWords.phrase(weddingWords),
                onClick = onWeddingWordsClick
            )
        }
        SettingsHint("需要两部带 NFC 的安卓手机；如果连接不上，请按提示交换连接方式。", center = true)
        Spacer(Modifier.height(16.dp))
    }
}
