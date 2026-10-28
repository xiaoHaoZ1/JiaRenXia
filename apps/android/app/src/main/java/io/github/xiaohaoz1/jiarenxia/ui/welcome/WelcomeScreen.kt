package io.github.xiaohaoz1.jiarenxia.ui.welcome

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.restore.OpenBackupDocument
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreDialogs
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreFlow
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreOutcome
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 欢迎页（FAMILY_AFFAIRS 16.1）：没有“我”时的起点。
 * 恢复流程与“我的 → 从备份恢复”共用 ui/restore/RestoreFlow。
 */
@HiltViewModel
class WelcomeViewModel @Inject constructor(
    backup: BackupManager,
    appEvents: AppEvents,
    private val db: AppDatabase
) : ViewModel() {

    val message = MutableStateFlow<String?>(null)

    /** 恢复流程（与“我的 → 从备份恢复”共用；分流规则、提示文案一致）。 */
    val restore = RestoreFlow(
        scope = viewModelScope,
        backup = backup,
        appEvents = appEvents,
        hasLocalData = { db.personDao().getAll().isNotEmpty() },
        onMessage = { message.value = it }
    )

    fun onMessageShown() { message.value = null }
}

@Composable
fun WelcomeScreen(
    onNewFamily: () -> Unit,
    /** 识别到共享文件 → 共享导入页（uri=null，plan 经 appEvents 传递）。 */
    onShareImport: () -> Unit,
    /** 恢复完成：hasSelf → 主界面；否则 → 创建“我”。 */
    onRestored: (hasSelf: Boolean) -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val restore = viewModel.restore

    val msg by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(msg) {
        msg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }
    val shareDetected by restore.shareDetected.collectAsStateWithLifecycle()
    LaunchedEffect(shareDetected) {
        if (shareDetected) {
            restore.shareDetected.value = false
            onShareImport()
        }
    }
    val outcome by restore.outcome.collectAsStateWithLifecycle()
    LaunchedEffect(outcome) {
        val o = outcome ?: return@LaunchedEffect
        restore.outcome.value = null
        onRestored(o == RestoreOutcome.RESTORED_WITH_SELF)
    }

    // 用已有资料开始：系统文件选择器（先定位「下载」）
    val pickLauncher = rememberLauncherForActivityResult(
        OpenBackupDocument()
    ) { uri -> restore.onSource(uri) }

    WelcomeContent(
        onNewFamily = onNewFamily,
        onPickFile = { pickLauncher.launch(arrayOf("*/*")) }
    )

    val step by restore.step.collectAsStateWithLifecycle()
    RestoreDialogs(
        step = step,
        onSubmitPassword = { restore.submitPassword(it, context) },
        onConfirm = restore::confirm,
        onNotMe = restore::notMe,
        onDismiss = restore::dismiss
    )
}

/** 欢迎页无状态主体（截图测试直接渲染）。 */
@Composable
fun WelcomeContent(
    onNewFamily: () -> Unit,
    onPickFile: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PageHeader {
                Column {
                    Text(
                        "欢迎使用家人侠",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "选一种方式开始",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("新建我的家庭", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "从填写自己开始，再添加家人。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onNewFamily,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("开始填写") }
                }
                JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("用已有资料开始", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "家人发来的共享文件，或你自己的备份。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onPickFile,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("选择文件") }
                }
                Text(
                    "家人用微信发来的文件：在微信里点开文件 → 右上角“…” → “用其他应用打开” → 选择“家人侠”。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }
        }
    }
}
