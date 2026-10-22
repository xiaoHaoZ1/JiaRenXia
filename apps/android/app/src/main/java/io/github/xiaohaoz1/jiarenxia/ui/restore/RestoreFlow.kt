package io.github.xiaohaoz1.jiarenxia.ui.restore

import android.content.Context
import android.net.Uri
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupException
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupSummary
import io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan
import io.github.xiaohaoz1.jiarenxia.domain.ShareImport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 恢复流程的界面状态机（密码不落盘，只在对话框与内存变量中流转）。 */
sealed interface RestoreStep {
    data object Idle : RestoreStep
    /** 恢复：输入密码对话框。 */
    data object AskPassword : RestoreStep
    /** 耗时操作进行中（文案区分解密/恢复）。 */
    data class Working(val label: String) : RestoreStep
    /** 解密成功的备份 → 确认恢复；overwrites = 本机已有人物会被覆盖。 */
    data class Confirm(val summary: BackupSummary, val overwrites: Boolean) : RestoreStep
}

/** 恢复完成后的去向（界面消费后置回 null）。 */
enum class RestoreOutcome { RESTORED_WITH_SELF, RESTORED_WITHOUT_SELF }

/**
 * “从备份恢复”共用流程（FAMILY_AFFAIRS 16.1/16.2）：我的页与欢迎页共用。
 * 选文件 → 密码 → 按文件用途分流：备份 → 确认恢复（可整库恢复，也可
 * “我不是 X”改按家人共享导入）；共享 → 交给共享导入页；其他 → 提示。
 * 密码只短暂持有于内存，用完即以 NUL 清空。
 */
class RestoreFlow(
    private val scope: CoroutineScope,
    private val backup: BackupManager,
    private val appEvents: AppEvents,
    /** 本机是否已有人物数据（决定确认页是否显示覆盖警告）。 */
    private val hasLocalData: suspend () -> Boolean,
    private val onMessage: (String) -> Unit
) {
    private val _step = MutableStateFlow<RestoreStep>(RestoreStep.Idle)
    val step: StateFlow<RestoreStep> = _step

    /** 识别出共享文件（或“我不是 X”）→ 界面导航到共享导入页后置回 false。 */
    val shareDetected = MutableStateFlow(false)

    /** 识别出协作包（purpose=share 且带 collaboration）→ 导航到协作接收页。 */
    val collabDetected = MutableStateFlow(false)

    /** 恢复完成：有“我” → 主界面；无“我” → 创建“我”。 */
    val outcome = MutableStateFlow<RestoreOutcome?>(null)

    /** 仅在提交后短暂持有的密码；用完即清空。 */
    private var pendingPassword: CharArray? = null
    private var pendingPlan: RestorePlan? = null
    private var pendingUri: Uri? = null

    /** 用户已选好文件 → 询问密码。 */
    fun onSource(uri: Uri?) {
        if (uri == null) return
        pendingUri = uri
        _step.value = RestoreStep.AskPassword
    }

    fun submitPassword(pw: CharArray, context: Context) {
        val uri = pendingUri ?: run { _step.value = RestoreStep.Idle; return }
        val pwCopy = pw.copyOf()   // 对话框会清空它自己的数组，这里复制一份
        pendingPassword = pwCopy
        _step.value = RestoreStep.Working("正在解密…")
        scope.launch {
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input == null) {
                    onMessage("无法读取备份文件")
                    clearPending()
                    _step.value = RestoreStep.Idle
                    return@launch
                }
                input.use { stream ->
                    // 原文先读入内存：识别为协作包时要把加密字节交给协作服务暂存
                    val raw = stream.readBytes()
                    backup.inspect(raw.inputStream(), pwCopy).fold(
                        onSuccess = { plan ->
                            when (plan.purpose) {
                                "backup" -> {
                                    pendingPlan = plan
                                    pendingPassword?.fill('\u0000'); pendingPassword = null
                                    _step.value = RestoreStep.Confirm(
                                        plan.summary,
                                        overwrites = hasLocalData()
                                    )
                                }
                                "share" -> if (plan.collaboration != null) {
                                    // 协作包：加密原文与密码所有权随交接转移给
                                    // 协作接收页（应用时要重解密暂存文件）
                                    appEvents.pendingCollab =
                                        io.github.xiaohaoz1.jiarenxia.data
                                            .PendingCollab(raw, pwCopy)
                                    pendingPassword = null
                                    pendingPlan = null; pendingUri = null
                                    _step.value = RestoreStep.Idle
                                    collabDetected.value = true
                                } else {
                                    // 共享文件里分享者带 is_self：按备份整库恢复会把
                                    // 接收方的“我”换成分享者，转去共享导入页选“我是谁”
                                    appEvents.pendingSharePlan = plan
                                    clearPending()
                                    _step.value = RestoreStep.Idle
                                    shareDetected.value = true
                                }
                                else -> {
                                    onMessage("无法识别这个文件，请确认是家人侠的备份或共享文件")
                                    clearPending()
                                    _step.value = RestoreStep.Idle
                                }
                            }
                        },
                        onFailure = { e ->
                            onMessage(
                                (e as? BackupException)?.error?.message
                                    ?: "密码错误或文件已损坏"
                            )
                            clearPending()
                            _step.value = RestoreStep.Idle
                        }
                    )
                }
            } catch (e: Exception) {
                onMessage("备份文件内容无效")
                clearPending()
                _step.value = RestoreStep.Idle
            }
        }
    }

    fun confirm() {
        val plan = pendingPlan ?: run { _step.value = RestoreStep.Idle; return }
        _step.value = RestoreStep.Working("正在恢复…")
        scope.launch {
            try {
                backup.restore(plan)
                onMessage("已恢复 " + plan.summary.persons + " 位家人")
                outcome.value = if (plan.summary.selfName == null)
                    RestoreOutcome.RESTORED_WITHOUT_SELF
                else RestoreOutcome.RESTORED_WITH_SELF
            } catch (e: Exception) {
                onMessage("恢复失败，请重试")
            } finally {
                clearPending()
                _step.value = RestoreStep.Idle
            }
        }
    }

    /**
     * “我不是 X”（FAMILY_AFFAIRS 16.2）：把这份备份改按家人共享处理
     * （剥掉私密模块与设置，见 ShareImport.fromBackup），转去共享导入页
     * 选“我是谁”。仅在确认页且备份里有“我”时有效；不写库。
     */
    fun notMe() {
        val plan = pendingPlan ?: return
        if (_step.value !is RestoreStep.Confirm) return
        if (plan.summary.selfName == null) return
        appEvents.pendingSharePlan = ShareImport.fromBackup(plan)
        clearPending()
        _step.value = RestoreStep.Idle
        shareDetected.value = true
    }

    fun dismiss() {
        clearPending()
        _step.value = RestoreStep.Idle
    }

    private fun clearPending() {
        pendingPassword?.fill('\u0000'); pendingPassword = null
        pendingPlan = null
        pendingUri = null
    }
}
