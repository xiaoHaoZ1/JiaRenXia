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
    viewModel: MineViewModel = hiltViewModel()
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
        onWeddingWordsClick = { showWeddingWords = true }
    )

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
    onWeddingWordsClick: () -> Unit = {}
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
