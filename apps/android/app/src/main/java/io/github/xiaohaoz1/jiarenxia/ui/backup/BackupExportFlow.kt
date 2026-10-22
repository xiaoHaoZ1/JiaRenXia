package io.github.xiaohaoz1.jiarenxia.ui.backup

import android.content.Context
import android.net.Uri
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 备份导出流程的界面状态机（密码不落盘，只在对话框与内存变量中流转）。 */
sealed interface BackupExportStep {
    data object Idle : BackupExportStep
    /** 导出：设置密码对话框（两次输入）。 */
    data object SetPassword : BackupExportStep
    /** 导出：等待用户选保存位置（由界面发起 CreateDocument）。 */
    data object PickExportTarget : BackupExportStep
    /** 耗时操作进行中（加密写盘）。 */
    data class Working(val label: String) : BackupExportStep
}

/**
 * “备份到文件”共用流程：我的页与关系图共用。
 * 设密码 → 界面发起系统保存对话框（CreateDocument）→ 加密写出；
 * 成功后把时刻写入 SettingsRepository.lastBackupAt（“上次备份”副标题的数据源）。
 * 密码只短暂持有于内存，用完即以 NUL 清空。
 */
class BackupExportFlow(
    private val scope: CoroutineScope,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val onMessage: (String) -> Unit
) {
    private val _step = MutableStateFlow<BackupExportStep>(BackupExportStep.Idle)
    val step: StateFlow<BackupExportStep> = _step

    /** 仅在提交后短暂持有的导出密码；用完即清空。 */
    private var pendingPassword: CharArray? = null

    fun start() { _step.value = BackupExportStep.SetPassword }

    /**
     * 密码校验通过 → 界面接着发起 CreateDocument。
     * 对话框提交后会清空它持有的数组，这里必须复制一份再保存。
     */
    fun submitPassword(pw: CharArray) {
        pendingPassword = pw.copyOf()
        _step.value = BackupExportStep.PickExportTarget
    }

    /** 系统保存对话框返回：uri 为 null 即用户取消，清空暂存密码并复位。 */
    fun onTarget(uri: Uri?, context: Context) {
        val pw = pendingPassword ?: run {
            _step.value = BackupExportStep.Idle; return
        }
        if (uri == null) { clearPending(); _step.value = BackupExportStep.Idle; return }
        _step.value = BackupExportStep.Working("正在加密备份…")
        scope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    val summary = backup.export(out, pw)
                    // 文件已写出：时间戳写不进只影响“上次备份”副标题，不算导出失败
                    runCatching {
                        settings.setLastBackupAt(System.currentTimeMillis())
                    }
                    onMessage("已备份 " + summary.persons + " 位家人")
                } ?: run { onMessage("备份失败，请重试") }
            } catch (e: Exception) {
                onMessage("备份失败，请重试")
            } finally {
                clearPending()
                _step.value = BackupExportStep.Idle
            }
        }
    }

    fun dismiss() { clearPending(); _step.value = BackupExportStep.Idle }

    private fun clearPending() {
        pendingPassword?.fill(' '); pendingPassword = null
    }
}
