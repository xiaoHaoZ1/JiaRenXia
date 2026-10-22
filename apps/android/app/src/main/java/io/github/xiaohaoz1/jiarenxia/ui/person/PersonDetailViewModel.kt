package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneNumber
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PersonDetail
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 关系区的一行：直接关系 + 另一端人物 + 称谓标签。 */
data class RelationRow(
    val relationship: Relationship,
    val other: Person,
    val label: String
)

/** combine 出参打包。 */
private data class PersonDetailCombine(val values: Array<out Any?>) {
    val detail get() = values[0] as io.github.xiaohaoz1.jiarenxia.data.PersonDetail?
    val overview get() = values[1] as io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview?
    @Suppress("UNCHECKED_CAST")
    val tasks get() = values[2] as List<io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask>
    @Suppress("UNCHECKED_CAST")
    val dateEntries get() =
        values[3] as List<io.github.xiaohaoz1.jiarenxia.data.DateEntry>
    @Suppress("UNCHECKED_CAST")
    val contactPlans get() = values[4] as Map<String, Int>
    @Suppress("UNCHECKED_CAST")
    val contactLogs get() =
        values[5] as List<io.github.xiaohaoz1.jiarenxia.core.model.ContactLog>
}

data class PersonDetailUiState(
    val loading: Boolean = true,
    val detail: PersonDetail? = null,
    val overview: PersonOverview? = null,
    val familyById: Map<String, PersonOverview> = emptyMap(),
    val relationRows: List<RelationRow> = emptyList(),
    val relatedIds: Set<String> = emptySet(),
    /** 这个人的家庭事项（全部；“家里的事”卡片取未完成前 3 条）。 */
    val tasks: List<io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask> = emptyList(),
    /** 夫妻共享纪念日（结婚/领证/恋爱各一条，挂任一方名下都算）。 */
    val coupleDates: Map<io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        io.github.xiaohaoz1.jiarenxia.data.DateEntry> = emptyMap(),
    /** 忌日。 */
    val deathDate: io.github.xiaohaoz1.jiarenxia.data.DateEntry? = null,
    /** 联系周期（天）；null = 不提醒。 */
    val contactPlanDays: Int? = null,
    /** 联系记录（新的在前）。 */
    val contactLogs: List<io.github.xiaohaoz1.jiarenxia.core.model.ContactLog> =
        emptyList(),
    /** 共同父母推荐（无配偶且恰有一位异性共同父母）：记为夫妻+纪念日。 */
    val weddingCandidateId: String? = null,
    val weddingCandidateName: String? = null,
    /** 记为夫妻后待确认的“共同子女”询问（null 无）。 */
    val commonChildrenAsk: io.github.xiaohaoz1.jiarenxia.data.CommonChildrenAsk? = null,
    /** 缺失另一位父母时的确认入口，仅读取候选，不自动建立血缘。 */
    val parentCompletion: io.github.xiaohaoz1.jiarenxia.data.CommonChildrenAsk? = null,
    /** 年龄文案：“62 岁”/“享年 81 岁”/“享年约 81 岁”。 */
    val ageText: String? = null,
    val error: String? = null,
    val deleted: Boolean = false
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FamilyRepository,
    private val contactRepository: io.github.xiaohaoz1.jiarenxia.data
        .ContactRepository,
    val appEvents: io.github.xiaohaoz1.jiarenxia.data.AppEvents,
    private val taskRepository: io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository,
    private val engine: KinshipEngine,
    observeOverview: ObserveFamilyOverview,
    val lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"])

    private val _ui = MutableStateFlow(PersonDetailUiState())
    val ui: StateFlow<PersonDetailUiState> = _ui

    init {
        viewModelScope.launch {
            combine(
                repository.observePersonDetail(personId),
                observeOverview(),
                taskRepository.observeTasksForPerson(personId),
                repository.observeDateEntries(),
                contactRepository.observePlans(),
                contactRepository.observeLogs(personId)
            ) { values -> PersonDetailCombine(values) }
                .collect { comb ->
                    val detail = comb.detail
                    val overview = comb.overview
                    val tasks = comb.tasks
                    val graph = overview?.graph
                    val rows = detail?.relationships.orEmpty().mapNotNull { rel ->
                        val otherId = if (rel.sourcePersonId == personId) rel.targetPersonId else rel.sourcePersonId
                        val other = graph?.persons?.firstOrNull { it.id == otherId } ?: return@mapNotNull null
                        val label = try {
                            engine.directRelationLabel(graph, personId, rel)
                        } catch (e: Exception) { "" }
                        RelationRow(rel, other, label)
                    }
                    // “记为夫妻”候选只在“我”的详情页才会用到
                    val weddingCandidate =
                        if (detail?.person?.isSelf == true)
                            repository.coParentCandidate(personId)
                        else null
                    _ui.update {
                        it.copy(
                            loading = false,
                            detail = detail,
                            overview = overview?.byId?.get(personId),
                            familyById = overview?.byId.orEmpty(),
                            relationRows = rows,
                            parentCompletion = graph?.let {
                                io.github.xiaohaoz1.jiarenxia.data.ParentCompletion.ask(it, personId)
                            },
                            relatedIds = detail?.relationships.orEmpty().flatMap { r ->
                                listOf(r.sourcePersonId, r.targetPersonId)
                            }.toSet(),
                            tasks = tasks,
                            coupleDates = comb.dateEntries.filter {
                                it.type.isCoupleShared &&
                                    (it.person.id == personId ||
                                        it.partnerId == personId)
                            }.associateBy { it.type },
                            deathDate = comb.dateEntries.firstOrNull {
                                it.type == io.github.xiaohaoz1.jiarenxia.core
                                    .model.ImportantDateType
                                    .DEATH_ANNIVERSARY &&
                                    it.person.id == personId
                            },
                            contactPlanDays =
                                comb.contactPlans[personId],
                            contactLogs = comb.contactLogs,
                            // “记为夫妻”候选只给“我”的页面用，其他人不查
                            weddingCandidateId = weddingCandidate?.id,
                            weddingCandidateName = weddingCandidate?.name,
                            ageText = detail?.person?.let { p ->
                                io.github.xiaohaoz1.jiarenxia.domain.AgeMath
                                    .ageText(
                                        overview?.byId?.get(personId)?.birthdays
                                            ?: emptyList(),
                                        p.livingStatus,
                                        comb.dateEntries.firstOrNull {
                                            it.type == io.github.xiaohaoz1
                                                .jiarenxia.core.model
                                                .ImportantDateType
                                                .DEATH_ANNIVERSARY &&
                                                it.person.id == personId
                                        }?.date, java.time.LocalDate.now(),
                                        lunar)
                            }
                        )
                    }
                }
        }
    }

    fun onErrorShown() = _ui.update { it.copy(error = null) }

    fun setSiblingOrder(relId: String, subjectId: String, order: RelativeAgeOrder?) {
        viewModelScope.launch {
            val r = repository.setSiblingAgeOrder(relId, subjectId, order)
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }

    fun removeRelationship(id: String) {
        viewModelScope.launch { repository.removeRelationship(id) }
    }

    /** 生日提醒开关：calendar 指定阳历/农历槽位。 */
    fun setBirthdayReminder(
        calendar: io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,
        enabled: Boolean
    ) = setReminderEnabledFor(
        io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
            .birthdayOf(calendar), enabled
    )

    /** 替换指定历法生日的提醒偏移（至少一个；repo 侧校验合法值）。 */
    fun setBirthdayReminderOffsets(
        calendar: io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,
        offsets: Set<Int>
    ) = setReminderOffsetsFor(
        io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
            .birthdayOf(calendar), offsets
    )

    /** 设置/清除该人物的自定义称呼（观察者为“我”）。 */
    fun setCustomTitle(title: String?) {
        viewModelScope.launch {
            val r = repository.setCustomTitle(personId, title)
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }



    /** 按日期类型设置提醒开关/偏移（纪念日、忌日）。 */
    fun setReminderEnabledFor(
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            // 夫妻共享纪念日记录可能挂在配偶名下——用记录持有人 id
            val holder = if (type.isCoupleShared)
                _ui.value.coupleDates[type]?.person?.id ?: personId
            else personId
            val r = repository.setReminderEnabled(holder, enabled, type)
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }

    fun setReminderOffsetsFor(
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        offsets: Set<Int>
    ) {
        viewModelScope.launch {
            val holder = if (type.isCoupleShared)
                _ui.value.coupleDates[type]?.person?.id ?: personId
            else personId
            val r = repository.setReminderOffsets(holder, offsets, type)
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }

    fun setDeathAnniversary(
        v: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ) {
        viewModelScope.launch {
            val r = repository.setDeathAnniversary(personId, v)
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }

    fun setWeddingAnniversary(
        v: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ) {
        viewModelScope.launch {
            val r = repository.setWeddingAnniversary(personId, v)
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }

    /** 夫妻共享纪念日（结婚/领证/恋爱）：一对夫妻每种类型共一条。 */
    fun setCoupleAnniversary(
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        v: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ) {
        viewModelScope.launch {
            val r = repository.setCoupleAnniversary(personId, type, v)
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }

    /** 拨给家人：记下待提示（10 分钟内回 App 弹“联系上了吗”）。 */
    fun onDialMade(person: io.github.xiaohaoz1.jiarenxia.core.model.Person,
        label: String) {
        appEvents.pendingContactPrompt =
            io.github.xiaohaoz1.jiarenxia.data.ContactPrompt(
                person.id, label, System.currentTimeMillis())
    }

    /** “联系上了”：按拨号时刻记一条电话类型的联系。 */
    fun logCallContact(prompt: io.github.xiaohaoz1.jiarenxia.data.ContactPrompt) {
        viewModelScope.launch {
            contactRepository.logContact(
                personId,
                io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod.CALL,
                null, prompt.atMillis)
        }
    }

    /** 联系周期：null 关闭提醒。 */
    fun setContactPlan(days: Int?) {
        viewModelScope.launch {
            contactRepository.setPlan(personId, days)
        }
    }

    /** 记一次联系（当前时间）。 */
    fun logContact(
        method: io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod,
        note: String?
    ) {
        viewModelScope.launch {
            contactRepository.logContact(
                personId, method, note, System.currentTimeMillis())
        }
    }

    fun deleteContactLog(id: String) {
        viewModelScope.launch { contactRepository.deleteLog(id) }
    }

    /**
     * 共同父母推荐：同一事务记为夫妻 + 写纪念日；
     * 成功后若双方已录子女中有共同子女候选，弹共同子女询问。
     */
    fun linkSpouseAndWedding(
        spouseId: String,
        v: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
    ) {
        viewModelScope.launch {
            val r = repository.linkSpouseAndSetWeddingAnniversary(
                personId, spouseId, v)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
                return@launch
            }
            val children = repository.commonChildCandidates(personId, spouseId)
            if (children.isEmpty()) return@launch
            _ui.update { st ->
                st.copy(
                    commonChildrenAsk = io.github.xiaohaoz1.jiarenxia.data
                        .CommonChildrenAsk(
                            spouseAId = personId,
                            spouseAName = st.detail?.person?.name ?: "",
                            spouseBId = spouseId,
                            spouseBName = st.familyById[spouseId]?.person?.name
                                ?: st.weddingCandidateName ?: "",
                            candidates = children)
                )
            }
        }
    }

    /** 勾选 / 取消一个共同子女候选（多候选时逐个勾选）。 */
    fun toggleCommonChild(childId: String) {
        _ui.update { st ->
            val ask = st.commonChildrenAsk ?: return@update st
            st.copy(commonChildrenAsk = ask.copy(
                checkedIds = if (childId in ask.checkedIds)
                    ask.checkedIds - childId else ask.checkedIds + childId))
        }
    }

    /** 用户点详情中的补全入口后，再显示明确的父母确认问句。 */
    fun askParentCompletion() {
        _ui.update { it.copy(commonChildrenAsk = it.parentCompletion) }
    }

    /** 确认后事务补齐父母关系；失败显示错误并保留询问。 */
    fun confirmCommonChildren() {
        val ask = _ui.value.commonChildrenAsk ?: return
        viewModelScope.launch {
            val r = repository.addRelationships(ask.checkedSpecs())
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
                return@launch
            }
            _ui.update { it.copy(commonChildrenAsk = null) }
        }
    }

    /** “共同子女”选“都不是”：不建关系，关闭询问。 */
    fun dismissCommonChildren() {
        _ui.update { it.copy(commonChildrenAsk = null) }
    }

    fun deletePerson() {
        viewModelScope.launch {
            when (repository.deletePerson(personId)) {
                is FamilyResult.Success -> _ui.update { it.copy(deleted = true) }
                is FamilyResult.Failure -> { /* CannotDeleteSelf 已由 UI 隐藏删除入口兜底 */ }
            }
        }
    }
}
