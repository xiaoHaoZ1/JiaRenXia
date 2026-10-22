package io.github.xiaohaoz1.jiarenxia.ui.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.MoneyText
import io.github.xiaohaoz1.jiarenxia.core.model.TASK_OFFSETS
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository
import io.github.xiaohaoz1.jiarenxia.data.TaskDraft
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.reminder.TaskTemplates
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 事项编辑页可选人物：全家（null）+ 本人（我的…）+ 其他家人（称谓/姓名）。 */
/** 关联人条目（统一走 PersonPickItem：姓名（称谓））。 */
typealias TaskPersonOption = io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem

data class TaskEditUiState(
    val taskId: String? = null,
    val kind: TaskKind = TaskKind.CUSTOM,
    val personId: String? = null,
    val title: String = "",
    /** 用户手动改过标题后，模板/人物变化不再覆盖。 */
    val titleEdited: Boolean = false,
    val anchorDate: LocalDate = LocalDate.now(),
    val repeat: TaskRepeat = TaskRepeat.NONE,
    val offsets: Set<Int> = setOf(0),
    /** 一天内提醒时刻（分钟数）；null = 跟随家事提醒时刻设置。 */
    val remindMinutes: Int? = null,
    /** 金额输入（元，两位小数）。 */
    val amountText: String = "",
    val note: String = "",
    val enabled: Boolean = true,
    val lastDoneDate: LocalDate? = null,
    val persons: List<TaskPersonOption> = emptyList(),
    /** 保存进行中：阻止重复提交（按钮禁用之外的 ViewModel 层保护）。 */
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val deleted: Boolean = false
) {
    val templateHint: String get() = TaskTemplates.of(kind).hint
    /** 关联人物当前展示名（供选择行显示）。 */
    /** 当前关联人显示名（picker 条目 label）。 */
    fun personLabel(): String =
        persons.firstOrNull { it.personId == personId }?.label ?: "全家（不关联）"
}

/** 事项编辑：新建（kind/personId 预填）或按 taskId 载入已有事项。 */
@HiltViewModel
class TaskEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val taskRepository: FamilyTaskRepository,
    private val repository: FamilyRepository,
    private val observeOverview: ObserveFamilyOverview
) : ViewModel() {

    private val _ui = MutableStateFlow(TaskEditUiState())
    val ui: StateFlow<TaskEditUiState> = _ui

    init {
        val taskId = savedState.get<String>("taskId")?.takeIf { it.isNotEmpty() }
        val kindName = savedState.get<String>("kind")?.takeIf { it.isNotEmpty() }
        val personId = savedState.get<String>("personId")?.takeIf { it.isNotEmpty() }
        // 文本分享预填（微信等 → 记为家事）
        val shared = savedState.get<String>("shared")?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            val overview = observeOverview().first()
            val options = io.github.xiaohaoz1.jiarenxia.domain.PersonPickList
                .build(overview)
            val existing = taskId?.let { taskRepository.getTask(it) }
            if (existing != null) {
                _ui.value = TaskEditUiState(
                    taskId = existing.id,
                    kind = existing.kind,
                    personId = existing.personId,
                    title = existing.title,
                    titleEdited = true,
                    anchorDate = existing.anchorDate,
                    repeat = existing.repeat,
                    offsets = existing.offsets,
                    remindMinutes = existing.remindMinutes,
                    amountText = existing.amountFen?.let { MoneyText.formatYuanInput(it) } ?: "",
                    note = existing.note ?: "",
                    enabled = existing.enabled,
                    lastDoneDate = existing.lastDoneDate,
                    persons = options
                )
            } else {
                val kind = kindName?.let { runCatching { TaskKind.valueOf(it) }.getOrNull() }
                    ?: TaskKind.CUSTOM
                val tpl = TaskTemplates.of(kind)
                // 分享文本：标题/备注/建议日期预填，titleEdited 置位防模板覆盖
                val parsed = shared?.let {
                    io.github.xiaohaoz1.jiarenxia.core.model.SharedTaskText.parse(
                        it, LocalDate.now())
                }
                _ui.value = TaskEditUiState(
                    taskId = null,
                    kind = kind,
                    personId = personId,
                    title = parsed?.title ?: defaultTitle(kind, personId, overview),
                    titleEdited = parsed != null,
                    anchorDate = parsed?.anchorDate ?: LocalDate.now(),
                    repeat = tpl.repeat,
                    offsets = tpl.offsets,
                    remindMinutes = parsed?.remindMinutes,
                    note = parsed?.note ?: "",
                    persons = options
                )
            }
        }
    }

    /** 默认标题：称谓/“我的” + 模板显示名；CUSTOM 恒空。 */
    private fun defaultTitle(kind: TaskKind, personId: String?, overview: io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview?): String {
        if (personId == null || overview == null) {
            return TaskTemplates.defaultTitle(kind, null, null, false)
        }
        val po = overview.byId[personId] ?: return TaskTemplates.defaultTitle(kind, null, null, false)
        return TaskTemplates.defaultTitle(
            kind, po.person.name, po.displayTitle.ifEmpty { null },
            personId == overview.self.person.id
        )
    }

    private fun refreshTitle(kind: TaskKind, personId: String?) {
        val cur = _ui.value
        if (cur.titleEdited) return
        viewModelScope.launch {
            val overview = observeOverview().first()
            _ui.value = _ui.value.copy(title = defaultTitle(kind, personId, overview))
        }
    }

    fun setKind(kind: TaskKind) {
        val tpl = TaskTemplates.of(kind)
        _ui.value = _ui.value.copy(kind = kind, repeat = tpl.repeat, offsets = tpl.offsets)
        refreshTitle(kind, _ui.value.personId)
    }

    fun setPerson(personId: String?) {
        _ui.value = _ui.value.copy(personId = personId)
        refreshTitle(_ui.value.kind, personId)
    }

    fun setTitle(v: String) {
        _ui.value = _ui.value.copy(title = v, titleEdited = true)
    }

    fun setDate(d: LocalDate) {
        _ui.value = _ui.value.copy(anchorDate = d)
    }

    fun setRepeat(r: TaskRepeat) {
        _ui.value = _ui.value.copy(repeat = r)
    }

    fun toggleOffset(d: Int) {
        val cur = _ui.value.offsets
        _ui.value = _ui.value.copy(offsets = if (d in cur) cur - d else cur + d)
    }

    /** null 表示跟随家事提醒时刻设置。 */
    fun setRemindMinutes(v: Int?) {
        _ui.value = _ui.value.copy(remindMinutes = v)
    }

    fun setAmount(v: String) {
        _ui.value = _ui.value.copy(amountText = v.filter { it.isDigit() || it == '.' })
    }

    fun setNote(v: String) {
        _ui.value = _ui.value.copy(note = v.take(200))
    }

    fun setEnabled(v: Boolean) {
        _ui.value = _ui.value.copy(enabled = v)
    }

    /** 保存：金额元转分（十进制），校验失败显示中文错误；进行中与成功后拒绝重复提交。 */
    fun save() {
        val st = _ui.value
        // 保存成功即终态：导航完成前再次调用不允许把新建记录写两遍
        if (st.saving || st.saved) return
        val amountFen = when (val r = MoneyText.parse(st.amountText)) {
            MoneyText.Result.Empty -> null
            MoneyText.Result.Invalid -> {
                _ui.value = st.copy(error = "金额不正确")
                return
            }
            is MoneyText.Result.Fen -> r.value
        }
        if (st.offsets.isEmpty()) {
            _ui.value = st.copy(error = "提醒时间无效")
            return
        }
        _ui.value = st.copy(saving = true)
        viewModelScope.launch {
            try {
                val res = taskRepository.save(
                    TaskDraft(
                        id = st.taskId, personId = st.personId, kind = st.kind,
                        title = st.title, anchorDate = st.anchorDate,
                        repeat = st.repeat, amountFen = amountFen,
                        note = st.note, enabled = st.enabled,
                        offsets = st.offsets,
                        remindMinutes = st.remindMinutes
                    )
                )
                when (res) {
                    is FamilyResult.Success ->
                        _ui.value = _ui.value.copy(saved = true, error = null)
                    is FamilyResult.Failure ->
                        _ui.value = _ui.value.copy(error = res.error.message)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = "保存失败，请重试")
            } finally {
                // 成功/失败/异常/取消都要放行下一次提交
                _ui.value = _ui.value.copy(saving = false)
            }
        }
    }

    fun delete() {
        val id = _ui.value.taskId ?: return
        viewModelScope.launch {
            taskRepository.delete(id)
            _ui.value = _ui.value.copy(deleted = true)
        }
    }

    fun dismissError() {
        _ui.value = _ui.value.copy(error = null)
    }
}
