package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import io.github.xiaohaoz1.jiarenxia.data.update.LatestRelease
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import java.util.Locale

/**
 * 「帮助与关于」页的更新区块：只剩联网开关（承诺弹窗门控）。
 * 「检查更新」动作行在「我的」页（UpdateSection 的开关是它的前置条件）。
 */
@Composable
fun UpdateSection(vm: UpdateViewModel = hiltViewModel()) {
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    var showConsent by remember { mutableStateOf(false) }

    UpdateSwitchCard(
        enabled = enabled,
        onCheckedChange = { want -> if (want) showConsent = true else vm.setEnabled(false) }
    )

    // 开启前的承诺确认：不同意不开启
    if (showConsent) {
        UpdateConsentDialog(
            onEnable = { showConsent = false; vm.enableAndCheck() },
            onDownloadPage = vm::openDownloadPage,
            onDismiss = { showConsent = false }
        )
    }

    UpdateDialogs(vm)
}

/**
 * 「开启联网检查更新？」承诺框：「开启」= 打开开关并立刻查一次；
 * 不想联网的用户可以走「去下载页」用浏览器看新版。
 * 「我的」页检查更新行与「帮助与关于」开关共用。
 */
@Composable
fun UpdateConsentDialog(
    onEnable: () -> Unit,
    onDownloadPage: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开启联网检查更新？") },
        text = {
            Text(
                "开启即表示同意家人侠联网。\n\n" +
                    "联网仅用于：查询最新版本、下载安装包。\n" +
                    "不会上传任何家庭资料，可随时关闭。\n\n" +
                    "不开启时 App 完全不联网。"
            )
        },
        confirmButton = {
            TextButton(onClick = onEnable) { Text("开启") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDismiss(); onDownloadPage() }) { Text("去下载页") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/**
 * 应用级启动更新提醒：挂在导航层，自动检查发现新版时弹窗。
 * 三个出口——「下载安装」走现有下载链路、「稍后」本次关闭（不写状态，
 * 下次到期检查再弹）、「不再提示此版本」持久化跳过该 versionCode。
 * 手动检查的弹窗在关于页内自己弹，不经过这里。
 */
@Composable
fun UpdatePromptHost(vm: UpdateViewModel = hiltViewModel()) {
    var prompt by remember { mutableStateOf<LatestRelease?>(null) }
    LaunchedEffect(Unit) {
        vm.updatePrompt.collect { prompt = it }
    }

    prompt?.let { r ->
        AlertDialog(
            onDismissRequest = { prompt = null; vm.dismissUpdatePrompt() },
            title = { Text("发现新版本 ${r.versionName}") },
            text = {
                Text(
                    (if (r.notes.isNotBlank()) r.notes + "\n\n" else "") +
                        "当前 ${io.github.xiaohaoz1.jiarenxia.BuildConfig.VERSION_NAME}" +
                        "，安装包 ${"%.1f".format(Locale.US, r.sizeBytes / 1048576f)} MB"
                )
            },
            confirmButton = {
                TextButton(onClick = { prompt = null; vm.dismissUpdatePrompt(); vm.startDownload(r) }) {
                    Text("下载安装")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { prompt = null; vm.skipVersion(r) }) {
                        Text("不再提示此版本")
                    }
                    TextButton(onClick = { prompt = null; vm.dismissUpdatePrompt() }) {
                        Text("稍后")
                    }
                }
            }
        )
    }

    UpdateDialogs(vm)
}

/**
 * 更新流程共享弹窗：检查结果（发现新版/失败）、下载进度、下载完成，
 * 以及 toast。「关于」页手动检查与应用级启动提醒共用这一套。
 */
@Composable
fun UpdateDialogs(vm: UpdateViewModel) {
    val context = LocalContext.current
    val found by vm.foundRelease.collectAsStateWithLifecycle()
    val error by vm.checkError.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    val downloaded by vm.downloadedApk.collectAsStateWithLifecycle()

    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    // 手动检查发现新版
    found?.let { r ->
        AlertDialog(
            onDismissRequest = vm::dismissFound,
            title = { Text("发现新版本 ${r.versionName}") },
            text = {
                Text(
                    (if (r.notes.isNotBlank()) r.notes + "\n\n" else "") +
                        "当前 ${io.github.xiaohaoz1.jiarenxia.BuildConfig.VERSION_NAME}" +
                        "，安装包 ${"%.1f".format(Locale.US, r.sizeBytes / 1048576f)} MB"
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissFound(); vm.startDownload(r) }) { Text("下载安装") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissFound(); vm.openDownloadPage() }) {
                    Text("去官网")
                }
            }
        )
    }

    // 检查失败：给浏览器兜底
    error?.let { reason ->
        AlertDialog(
            onDismissRequest = vm::dismissError,
            title = { Text("检查更新失败") },
            text = { Text(reason) },
            confirmButton = {
                TextButton(onClick = { vm.dismissError(); vm.openDownloadPage() }) {
                    Text("去官网下载页")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissError) { Text("取消") }
            }
        )
    }

    // 下载进度
    progress?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(p * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = vm::cancelDownload) { Text("取消") }
            }
        )
    }

    // 下载完成：拉起系统安装器（未授权时先跳「安装未知应用」）
    if (downloaded != null) {
        AlertDialog(
            onDismissRequest = vm::dismissDownloaded,
            title = { Text("下载完成") },
            text = { Text("安装包已通过完整性校验。接下来由系统安装器处理，界面中点「更新」即可。") },
            confirmButton = {
                TextButton(onClick = vm::installDownloaded) { Text("立即安装") }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDownloaded) { Text("稍后") }
            }
        )
    }
}

/** 更新区块卡片本体（无状态，便于预览与截图测试）：只有联网开关一行。 */
@Composable
fun UpdateSwitchCard(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    JrxCard(Modifier.fillMaxWidth()) {
        SettingsSwitchRow(
            icon = Icons.Rounded.Wifi,
            title = "联网检查更新",
            subtitle = "开启后仅用于查版本与下载安装包，可随时关闭",
            checked = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
