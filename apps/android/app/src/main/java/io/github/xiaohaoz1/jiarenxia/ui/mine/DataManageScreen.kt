package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.BuildConfig
import io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportDialogs
import io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportStep
import io.github.xiaohaoz1.jiarenxia.ui.backup.CreateBackupDocument
import io.github.xiaohaoz1.jiarenxia.ui.backup.backupFileName
import io.github.xiaohaoz1.jiarenxia.ui.backup.lastBackupSubtitle
import io.github.xiaohaoz1.jiarenxia.ui.common.ConfirmDialog
import io.github.xiaohaoz1.jiarenxia.ui.restore.OpenBackupDocument
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreDialogs
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreOutcome

/**
 * 数据管理（我的 → 二级页）：备份、恢复、清空，以及本机专用的测试工具。
 *
 * 改版前这些和「分享给家人」「碰一碰」挤在同一张「数据」卡里。现在按用途分开：
 * 这页只管「给自己留副本」，发给家人走 家庭页 → 共享与连接。
 */
@Composable
fun DataManageScreen(
    onBack: () -> Unit,
    onDataCleared: () -> Unit,
    onNeedsSelf: () -> Unit,
    /** 恢复时识别出的是家人共享文件 → 转共享导入页。 */
    onShareImport: () -> Unit,
    /** 恢复时识别出的是协作包 → 转协作接收页。 */
    onCollabDetected: () -> Unit = {},
    onHeartPreview: (Boolean) -> Unit = {},
    viewModel: MineViewModel = hiltViewModel(),
    snapshotVm: QuickSnapshotViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClear1 by remember { mutableStateOf(false) }
    var showClear2 by remember { mutableStateOf(false) }
    var clearText by remember { mutableStateOf("") }
    var showClearKeepSnapshots by remember { mutableStateOf(false) }
    var showDemoConfirm by remember { mutableStateOf(false) }

    // ---------- 备份导出：选保存位置（密码已在导出流程内暂存） ----------
    val exportLauncher = rememberLauncherForActivityResult(
        CreateBackupDocument()
    ) { uri -> viewModel.backupExport.onTarget(uri, context) }
    val backupStep by viewModel.backupExport.step.collectAsStateWithLifecycle()
    LaunchedEffect(backupStep) {
        if (backupStep == BackupExportStep.PickExportTarget) {
            exportLauncher.launch(backupFileName())
        }
    }

    // ---------- 恢复：选备份文件（共享/未知用途的分流在 RestoreFlow 内） ----------
    val restoreLauncher = rememberLauncherForActivityResult(
        OpenBackupDocument()
    ) { uri -> viewModel.restore.onSource(uri) }
    val restoreOutcome by viewModel.restore.outcome.collectAsStateWithLifecycle()
    LaunchedEffect(restoreOutcome) {
        if (restoreOutcome == RestoreOutcome.RESTORED_WITHOUT_SELF) {
            viewModel.restore.outcome.value = null
            onNeedsSelf()
        } else if (restoreOutcome != null) {
            viewModel.restore.outcome.value = null
        }
    }
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
    LaunchedEffect(st.cleared) { if (st.cleared) onDataCleared() }
    LaunchedEffect(st.message) {
        st.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }

    // ---------- 测试工具（快速存档 / 快速读档） ----------
    val testToolsEnabled by snapshotVm.testToolsEnabled.collectAsStateWithLifecycle()
    val snapList by snapshotVm.snapshots.collectAsStateWithLifecycle()
    val snapListOpen by snapshotVm.listOpen.collectAsStateWithLifecycle()
    val snapPending by snapshotVm.pendingRestore.collectAsStateWithLifecycle()
    val snapBusy by snapshotVm.busy.collectAsStateWithLifecycle()
    val snapOutcome by snapshotVm.outcome.collectAsStateWithLifecycle()
    val snapCleared by snapshotVm.cleared.collectAsStateWithLifecycle()
    val snapMessage by snapshotVm.message.collectAsStateWithLifecycle()
    LaunchedEffect(snapOutcome) {
        if (snapOutcome == RestoreOutcome.RESTORED_WITHOUT_SELF) {
            snapshotVm.onOutcomeConsumed()
            onNeedsSelf()
        } else if (snapOutcome != null) {
            snapshotVm.onOutcomeConsumed()
        }
    }
    LaunchedEffect(snapCleared) {
        if (snapCleared) {
            snapshotVm.cleared.value = false
            onDataCleared()
        }
    }
    LaunchedEffect(snapMessage) {
        snapMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            snapshotVm.onMessageShown()
        }
    }

    // ---------- 找回“上次接收前”资料（协作 §5.8） ----------
    val prereceive by snapshotVm.prereceive.collectAsStateWithLifecycle()
    val prereceiveAsk by snapshotVm.prereceiveAsk.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { snapshotVm.refreshPrereceive() }

    DataManageContent(
        lastBackupAt = st.lastBackupAt,
        testToolsVisible = BuildConfig.DEBUG || testToolsEnabled,
        onBack = onBack,
        onBackup = viewModel.backupExport::start,
        onRestore = { restoreLauncher.launch(arrayOf("*/*")) },
        onClearRequest = { showClear1 = true },
        onQuickSave = snapshotVm::save,
        onQuickRestore = snapshotVm::openList,
        onClearKeepSnapshots = { showClearKeepSnapshots = true },
        onImportDemo = { showDemoConfirm = true },
        onDebugCheck = {
            viewModel.debugCheckReminders()
            Toast.makeText(context, "已触发一次检查", Toast.LENGTH_SHORT).show()
        },
        onHeartPreview = onHeartPreview,
        prereceive = prereceive,
        onPrereceive = snapshotVm::askPrereceiveRestore
    )

    // 找回“上次接收前”资料：明确确认（副本只有一份，恢复即失效）
    if (prereceiveAsk) {
        AlertDialog(
            onDismissRequest = snapshotVm::dismissPrereceive,
            title = { Text("找回上次接收前的资料？") },
            text = {
                Text("恢复整个接收前副本，之后的修改会被替换。" +
                    "这份副本找回后即失效。")
            },
            confirmButton = {
                TextButton(onClick = snapshotVm::confirmPrereceiveRestore) {
                    Text("恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = snapshotVm::dismissPrereceive) {
                    Text("取消")
                }
            }
        )
    }

    // 清空：两段确认（弹窗 → 手动输入「清空」）
    if (showClear1) {
        ConfirmDialog(
            text = "将删除全部人物、关系、头像与设置，且无法恢复。",
            confirmText = "继续",
            onConfirm = { showClear1 = false; showClear2 = true },
            onDismiss = { showClear1 = false }
        )
    }
    if (showClear2) {
        AlertDialog(
            onDismissRequest = { showClear2 = false },
            title = { Text("清空所有数据") },
            text = {
                Column {
                    Text("请输入“清空”确认")
                    OutlinedTextField(
                        value = clearText,
                        onValueChange = { clearText = it },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = clearText == "清空",
                    onClick = {
                        showClear2 = false
                        clearText = ""
                        viewModel.clearAllData()
                    }
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClear2 = false; clearText = "" }) { Text("取消") }
            }
        )
    }
    if (showClearKeepSnapshots) {
        ConfirmDialog(
            text = "将删除全部人物、关系与设置，但保留快速存档。",
            confirmText = "清空",
            onConfirm = {
                showClearKeepSnapshots = false
                snapshotVm.clearDataKeepSnapshots()
            },
            onDismiss = { showClearKeepSnapshots = false }
        )
    }
    // 内置演示家庭（仅 debug 包）：覆盖当前数据前确认一次
    if (showDemoConfirm) {
        ConfirmDialog(
            text = "将导入明朝朱棣一家 25 位演示家人，覆盖当前全部数据。",
            confirmText = "导入",
            onConfirm = {
                showDemoConfirm = false
                snapshotVm.importDemoFamily(context)
            },
            onDismiss = { showDemoConfirm = false }
        )
    }

    // 备份导出对话框（设置密码 / 进行中），与关系图共用
    BackupExportDialogs(
        step = backupStep,
        onSubmitPassword = { viewModel.backupExport.submitPassword(it) },
        onDismiss = viewModel.backupExport::dismiss
    )
    // 恢复流程对话框（问密码 / 解密中 / 确认恢复），与欢迎页共用
    val restoreStep by viewModel.restore.step.collectAsStateWithLifecycle()
    RestoreDialogs(
        step = restoreStep,
        onSubmitPassword = { viewModel.restore.submitPassword(it, context) },
        onConfirm = viewModel.restore::confirm,
        onNotMe = viewModel.restore::notMe,
        onDismiss = viewModel.restore::dismiss
    )
    QuickSnapshotDialogs(
        listOpen = snapListOpen,
        snapshots = snapList,
        pendingRestore = snapPending,
        busy = snapBusy,
        onPick = snapshotVm::askRestore,
        onConfirmRestore = snapshotVm::confirmRestore,
        onDismissList = snapshotVm::dismissList
    )
}

@Composable
fun DataManageContent(
    lastBackupAt: Long? = null,
    testToolsVisible: Boolean = false,
    onBack: () -> Unit,
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onClearRequest: () -> Unit = {},
    onQuickSave: () -> Unit = {},
    onQuickRestore: () -> Unit = {},
    onClearKeepSnapshots: () -> Unit = {},
    onImportDemo: () -> Unit = {},
    onDebugCheck: () -> Unit = {},
    onHeartPreview: (Boolean) -> Unit = {},
    /** “上次接收前副本”信息（null = 无可找回副本，入口隐藏）。 */
    prereceive: io.github.xiaohaoz1.jiarenxia.data.backup
        .QuickSnapshotStore.Snapshot? = null,
    onPrereceive: () -> Unit = {}
) {
    SettingsScaffold("数据管理", onBack) {
        SettingsGroup("备份与恢复") {
            SettingsNavRow(
                icon = Icons.Rounded.Backup,
                title = "备份到文件",
                subtitle = lastBackupSubtitle(lastBackupAt) + " · 需设置密码",
                onClick = onBackup
            )
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Restore,
                title = "从备份恢复",
                subtitle = "打开以前保存的备份文件",
                onClick = onRestore
            )
            if (prereceive != null) {
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.History,
                    title = "找回上次接收前的资料",
                    subtitle = "接收协作包前的完整副本 · " +
                        prereceive.persons + " 位家人",
                    onClick = onPrereceive
                )
            }
        }
        SettingsHint(
            "备份是给自己留一份副本，文件由你自己保管。" +
                "想发给家人，请用 家庭页 → 共享与连接。"
        )

        SettingsGroup("高级操作") {
            SettingsNavRow(
                icon = Icons.Rounded.DeleteForever,
                title = "清空所有数据",
                subtitle = "需手动输入「清空」确认",
                danger = true,
                onClick = onClearRequest
            )
        }
        SettingsHint("清空后无法恢复，请先备份。")

        if (testToolsVisible) {
            Spacer(Modifier.height(4.dp))
            TestToolsCard(
                onQuickSave = onQuickSave,
                onQuickRestore = onQuickRestore,
                onClearKeepSnapshots = onClearKeepSnapshots,
                onImportDemo = onImportDemo
            )
        }
        if (BuildConfig.DEBUG) {
            SettingsGroup("调试（仅 debug 构建）") {
                SettingsNavRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "立即检查提醒",
                    onClick = onDebugCheck
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Favorite,
                    title = "预览半颗心（左）",
                    onClick = { onHeartPreview(true) }
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.FavoriteBorder,
                    title = "预览半颗心（右）",
                    onClick = { onHeartPreview(false) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
