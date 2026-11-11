package io.github.xiaohaoz1.jiarenxia.ui.checklist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistItem
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.data.ChecklistRepository
import io.github.xiaohaoz1.jiarenxia.data.ChecklistWithItemsModel
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.checklist.ChecklistTemplates
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChecklistUiState(
    val loading: Boolean = true,
    val model: ChecklistWithItemsModel? = null,
    /** 事项 id → 事项（「定日期」条目的日期/已办状态用）。 */
    val tasks: Map<String, FamilyTask> = emptyMap(),
    /** personId → 当前姓名（POSTNATAL 宝宝卡标题）。 */
    val personNames: Map<String, String> = emptyMap(),
    /** personId → 称谓（POSTNATAL 孕妈条目前缀）。 */
    val personTitles: Map<String, String> = emptyMap(),
    val selfId: String? = null,
    /** personId → 归并出的阳历出生日（POSTNATAL 卡按出生顺序排）。 */
    val birthDates: Map<String, LocalDate> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    /** 一次性提示（删除条目等）。 */
    val notice: String? = null,
    /** 删除整张清单成功 → 页面返回。 */
    val deleted: Boolean = false,
    val error: String? = null
)

/** POSTNATAL 清单的一张卡（一个宝宝 / 孕妈）。 */
data class PostnatalCard(
    val key: String,
    /** 卡标题：宝宝当前姓名 / “妈妈”。 */
    val title: String,
    val groupName: String,
    val personId: String?,
    val items: List<ChecklistItem>
)

/**
 * POSTNATAL 清单分卡：group “宝宝” 的条目按 personId 每人一张卡
 * （按出生日期升序、无日期靠后再按姓名），“妈妈”组一张卡放最后。
 */
internal fun buildPostnatalCards(
    model: ChecklistWithItemsModel,
    personName: (String) -> String?,
    birthDate: (String) -> LocalDate?
): List<PostnatalCard> {
    val babyCards = model.items
        .filter { it.groupName == ChecklistTemplates.POSTNATAL_GROUP_BABY }
        .groupBy { it.personId }
        .map { (pid, its) ->
            PostnatalCard(
                key = pid ?: "?",
                title = pid?.let(personName) ?: "宝宝",
                groupName = ChecklistTemplates.POSTNATAL_GROUP_BABY,
                personId = pid,
                items = its
            )
        }
        .sortedWith(
            compareBy<PostnatalCard>(
                { birthDate(it.personId ?: "") == null },
                { birthDate(it.personId ?: "") },
                { it.title }
            )
        )
    val momItems = model.items
        .filter { it.groupName == ChecklistTemplates.POSTNATAL_GROUP_MOM }
    val momCard = if (momItems.isEmpty()) null else PostnatalCard(
        key = "mom",
        title = "妈妈",
        groupName = ChecklistTemplates.POSTNATAL_GROUP_MOM,
        personId = model.checklist.personId,
        items = momItems
    )
    return babyCards + listOfNotNull(momCard)
}

/** 清单页 ViewModel（FAMILY_AFFAIRS 26：待产包 / 出生后要办的事）。 */
@HiltViewModel
class ChecklistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChecklistRepository,
    private val taskRepository: FamilyTaskRepository,
    private val observeOverview: ObserveFamilyOverview
) : ViewModel() {

    private val checklistId: String = checkNotNull(savedStateHandle["checklistId"])

    private val _ui = MutableStateFlow(ChecklistUiState())
    val ui: StateFlow<ChecklistUiState> = _ui

    init {
        viewModelScope.launch {
            combine(
                repository.observe(checklistId),
                taskRepository.observeTasks(),
                observeOverview()
            ) { model, tasks, overview ->
                ChecklistUiState(
                    loading = false,
                    model = model,
                    tasks = tasks.associateBy { it.id },
                    personNames = overview?.byId.orEmpty()
                        .mapValues { it.value.person.name },
                    personTitles = overview?.byId.orEmpty()
                        .mapValues { it.value.displayTitle },
                    selfId = overview?.self?.person?.id,
                    birthDates = overview?.byId.orEmpty().mapValues { (_, o) ->
                        o.solarBirthday?.takeIf { it.isComplete }
                            ?.toLocalDateOrNull()
                    }.filterValues { it != null }
                        .mapValues { it.value!! },
                    today = LocalDate.now()
                )
            }.collect { _ui.value = it }
        }
    }

    fun setChecked(itemId: String, checked: Boolean) {
        viewModelScope.launch {
            val r = repository.setChecked(itemId, checked)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun addItem(groupName: String, title: String, personId: String?) {
        viewModelScope.launch {
            val r = repository.addItem(checklistId, groupName, title, personId)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /**
     * 「定日期」/「再定一次日期」：按条目归属拼事项标题——
     * 宝宝条目 “{宝宝当前姓名}的{条目}”，孕妈条目 “我的/{称谓}的{条目}”，
     * 无归属人物（待产包）的条目用原标题；person_id = 条目 person_id。
     */
    fun setTaskDate(item: ChecklistItem, date: LocalDate) {
        viewModelScope.launch {
            val st = _ui.value
            val pid = item.personId
            val title = when {
                pid == null -> item.title
                item.groupName == ChecklistTemplates.POSTNATAL_GROUP_BABY ->
                    (st.personNames[pid] ?: item.title) + "的" + item.title
                item.groupName == ChecklistTemplates.POSTNATAL_GROUP_MOM ->
                    if (pid == st.selfId) "我的" + item.title
                    else (st.personTitles[pid] ?: st.personNames[pid] ?: "") +
                        "的" + item.title
                else -> item.title
            }
            val r = repository.setItemTask(item.id, pid, title, date)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun renameItem(itemId: String, title: String) {
        viewModelScope.launch {
            val r = repository.renameItem(itemId, title)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /** 删除条目：不二次确认；成功后由页面弹“已删除「标题」”。 */
    fun deleteItem(item: ChecklistItem) {
        viewModelScope.launch {
            val r = repository.deleteItem(item.id)
            when (r) {
                is FamilyResult.Success -> _ui.update {
                    it.copy(notice = "已删除「" + item.title + "」")
                }
                is FamilyResult.Failure -> _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /** 删除整张清单（含全部条目），成功后页面返回。 */
    fun deleteChecklist() {
        viewModelScope.launch {
            val r = repository.deleteChecklist(checklistId)
            when (r) {
                is FamilyResult.Success -> _ui.update { it.copy(deleted = true) }
                is FamilyResult.Failure -> _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun onNoticeShown() = _ui.update { it.copy(notice = null) }
    fun onErrorShown() = _ui.update { it.copy(error = null) }
}
