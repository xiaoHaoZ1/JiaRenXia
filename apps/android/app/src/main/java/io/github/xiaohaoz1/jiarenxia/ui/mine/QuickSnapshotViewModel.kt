package io.github.xiaohaoz1.jiarenxia.ui.mine

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.QuickSnapshotStore
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.ClearAllDataUseCase
import io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreOutcome
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * “我的”页测试工具（快速存档/读档）的界面状态。
 *
 * - 快速存档：不要密码、不弹文件选择器，写入应用私有目录，
 *   只保留最近 [QuickSnapshotStore.KEEP] 份；
 * - 快速读档：列出存档 → 用户确认覆盖后整库恢复；
 *   存档的本机标识与当前安装不符或密钥解不开时拒绝恢复；
 * - “清空数据但保留存档”：清空数据库与设置，但不动存档目录；
 * - 正式版隐藏入口：版本号连点 7 次切换 [SettingsRepository.testToolsEnabled]。
 */
@HiltViewModel
class QuickSnapshotViewModel @Inject constructor(
    private val store: QuickSnapshotStore,
    private val settings: SettingsRepository,
    private val clearAll: ClearAllDataUseCase,
    private val backup: BackupManager
) : ViewModel() {

    /** 存档列表（新的在前）；打开读档对话框时 [refresh]。 */
    private val _snapshots =
        MutableStateFlow<List<QuickSnapshotStore.Snapshot>>(emptyList())
    val snapshots: StateFlow<List<QuickSnapshotStore.Snapshot>> = _snapshots

    /** 一次性提示（Toast 文案），界面消费后调 [onMessageShown]。 */
    val message = MutableStateFlow<String?>(null)

    /** 读档完成后的去向（界面消费后置回 null）。 */
    val outcome = MutableStateFlow<RestoreOutcome?>(null)

    /** “清空数据但保留存档”完成：界面据此离开本页（复用清空后的导航）。 */
    val cleared = MutableStateFlow(false)

    /** 读档列表对话框是否打开。 */
    val listOpen = MutableStateFlow(false)

    /** 待确认恢复的存档（点了某一条之后）。 */
    val pendingRestore = MutableStateFlow<QuickSnapshotStore.Snapshot?>(null)

    /** 读写进行中（读档确认后阻塞按钮，防重复点）。 */
    val busy = MutableStateFlow(false)

    /** 正式版开关（debug 构建界面恒显示入口，与该项无关）。 */
    val testToolsEnabled: StateFlow<Boolean> = settings.testToolsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---------- 快速存档 ----------

    fun save() {
        viewModelScope.launch {
            store.save().fold(
                onSuccess = { message.value = "已存为快速存档（" + it.persons + " 人）" },
                onFailure = { message.value = "快速存档失败，请重试" }
            )
        }
    }

    // ---------- 快速读档 ----------

    fun openList() {
        listOpen.value = true
        viewModelScope.launch { _snapshots.value = store.list() }
    }

    fun refresh() {
        viewModelScope.launch { _snapshots.value = store.list() }
    }

    fun dismissList() {
        listOpen.value = false
        pendingRestore.value = null
    }

    /** 点了某一份存档 → 弹覆盖确认。 */
    fun askRestore(snapshot: QuickSnapshotStore.Snapshot) {
        pendingRestore.value = snapshot
    }

    /** 确认覆盖 → 本机校验 + 整库恢复。 */
    fun confirmRestore() {
        val snap = pendingRestore.value ?: return
        if (busy.value) return
        // 恢复期间保留 pendingRestore：确认框据 busy 显示“正在恢复…”，结束后再关闭
        busy.value = true
        viewModelScope.launch {
            val r = store.restore(snap.id)
            pendingRestore.value = null
            when (r) {
                is QuickSnapshotStore.RestoreResult.Ok -> {
                    listOpen.value = false
                    message.value = "已恢复 " + r.persons + " 位家人"
                    outcome.value = if (r.hasSelf)
                        RestoreOutcome.RESTORED_WITH_SELF
                    else
                        RestoreOutcome.RESTORED_WITHOUT_SELF
                }
                QuickSnapshotStore.RestoreResult.NotFromThisDevice ->
                    message.value = "这份存档不是本机保存的"
                QuickSnapshotStore.RestoreResult.Failed ->
                    message.value = "读档失败，请重试"
            }
            busy.value = false
        }
    }

    // ---------- 内置演示家庭（仅 debug 包：assets 里的明朝朱棣一家） ----------

    /**
     * 一键导入内置演示家庭：直接读 assets/ming_yongle_family.jrxb
     * （testdata 里同名文件的拷贝，密码固定 test1234，不弹输入框）。
     * 与整库恢复同口径——覆盖当前全部数据，界面确认后才调用。
     */
    fun importDemoFamily(context: Context) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            val persons = runCatching {
                val plan = context.assets.open(DEMO_ASSET).use { input ->
                    backup.inspect(input, DEMO_PASSWORD.toCharArray()).getOrThrow()
                }
                backup.restore(plan)
                plan.summary.persons
            }
            busy.value = false
            persons.fold(
                onSuccess = {
                    message.value = "已导入演示家庭（" + it + " 人）"
                    outcome.value = RestoreOutcome.RESTORED_WITH_SELF
                },
                onFailure = { message.value = "导入演示家庭失败，请重试" }
            )
        }
    }

    // ---------- 清空数据但保留存档 ----------

    fun clearDataKeepSnapshots() {
        viewModelScope.launch {
            // 恢复入口开关：settings.clear() 会连它一起清，正式版下
            // 清完就再也回不到测试工具了——清完重新写回
            val keepTools = settings.testToolsEnabled.first()
            clearAll.run(keepSnapshots = true)
            if (keepTools) settings.setTestToolsEnabled(true)
            cleared.value = true
        }
    }

    // ---------- 版本号连点 ----------

    private var tapCount = 0
    private var lastTapAt = 0L

    /** “关于家人侠”版本号连点 7 次开/关测试工具入口。 */
    fun onVersionTap() {
        val now = System.currentTimeMillis()
        tapCount = if (now - lastTapAt < TAP_WINDOW_MS) tapCount + 1 else 1
        lastTapAt = now
        if (tapCount >= TAP_TARGET) {
            tapCount = 0
            viewModelScope.launch {
                val target = !settings.testToolsEnabled.first()
                settings.setTestToolsEnabled(target)
                message.value = if (target) "已开启测试工具" else "已关闭测试工具"
            }
        }
    }

    fun onMessageShown() {
        message.value = null
    }

    fun onOutcomeConsumed() {
        outcome.value = null
    }

    companion object {
        private const val TAP_TARGET = 7
        private const val TAP_WINDOW_MS = 2000L

        /** 内置演示家庭：assets 路径与备份密码（生成见 testdata 下 MingFamily*）。 */
        const val DEMO_ASSET = "ming_yongle_family.jrxb"
        const val DEMO_PASSWORD = "test1234"
    }
}
