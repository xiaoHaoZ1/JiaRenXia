package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.PersonFact
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
    @Suppress("UNCHECKED_CAST")
    val facts get() = values[6] as List<PersonFact>
    @Suppress("UNCHECKED_CAST")
    val pregnancies get() = values[7] as List<io.github.xiaohaoz1.jiarenxia
        .core.model.Pregnancy>
    @Suppress("UNCHECKED_CAST")
    val checklists get() = values[8] as List<io.github.xiaohaoz1.jiarenxia.data
        .ChecklistProgress>
    val postnatalItems get() = values[9] as io.github.xiaohaoz1.jiarenxia.data
        .ItemsProgress?
    @Suppress("UNCHECKED_CAST")
    val celebrations get() = values[10] as Map<
        io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebrationKind,
        java.time.LocalDate>
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
    /** 「关于TA」记录，按创建时间升序。 */
    val facts: List<PersonFact> = emptyList(),
    /** 这个人的孕期记录（ACTIVE 在前）。 */
    val pregnancies: List<io.github.xiaohaoz1.jiarenxia.core.model.Pregnancy> =
        emptyList(),
    /** 各孕期的待产包进度（pregnancyId → ChecklistProgress；无清单不在表内）。 */
    val birthBagByPregnancy: Map<String, io.github.xiaohaoz1.jiarenxia.data
        .ChecklistProgress> = emptyMap(),
    /** 各孕期的「出生后要办的事」进度（pregnancyId → ChecklistProgress）。 */
    val postnatalByPregnancy: Map<String, io.github.xiaohaoz1.jiarenxia.data
        .ChecklistProgress> = emptyMap(),
    /** 新生宝宝卡（未满一岁窗口内或产后清单未办完；否则 null）。 */
    val newbornCard: NewbornCardState? = null,
    /** 待产包按模板建好后的待跳转清单 id（页面消费后清空）。 */
    val pendingChecklistId: String? = null,
    /** 女性在世且（是“我”或“我”的配偶）且无 ACTIVE 孕期 → 「重要日期」卡“添加预产期”入口。 */
    val canRecordPregnancy: Boolean = false,
    /** 女性在世且无 ACTIVE 孕期（不限关系）→ ⋮ 菜单“记录孕期”（菜单本身只对非“我”显示）。 */
    val canRecordPregnancyMenu: Boolean = false,
    /** 操作成功后的轻提示（结束/删除孕期记录）。 */
    val notice: String? = null,
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
) {
    /** 进行中的孕期；人物已故时为 null（孕期卡不显示，数据保留在 [pregnancies]）。 */
    val activePregnancy: io.github.xiaohaoz1.jiarenxia.core.model.Pregnancy?
        get() = if (detail?.person?.livingStatus ==
            io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE)
            pregnancies.firstOrNull {
                it.status == io.github.xiaohaoz1.jiarenxia.core.model
                    .PregnancyStatus.ACTIVE
            } else null
}

/** 新生宝宝卡数据（FAMILY_AFFAIRS 26 第 3 批）。 */
data class NewbornCardState(
    val birthDate: java.time.LocalDate,
    /** 生效满月/百天日（另设覆盖优先，否则按出生日算）。 */
    val fullMonth: java.time.LocalDate,
    val hundredDays: java.time.LocalDate,
    /** 按出生日算的满月/百天日（覆盖时小字对照用）。 */
    val fullMonthByBirth: java.time.LocalDate,
    val hundredByBirth: java.time.LocalDate,
    val fullMonthOverride: Boolean,
    val hundredOverride: Boolean,
    /** 「出生后要办的事」行：清单 id + 该宝宝条目进度。 */
    val postnatalChecklistId: String?,
    val postnatalDone: Int,
    val postnatalTotal: Int
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
    val lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    private val factRepository: io.github.xiaohaoz1.jiarenxia.data.PersonFactRepository,
    private val pregnancyRepository: io.github.xiaohaoz1.jiarenxia.data
        .PregnancyRepository,
    private val checklistRepository: io.github.xiaohaoz1.jiarenxia.data
        .ChecklistRepository,
    private val celebrationRepository: io.github.xiaohaoz1.jiarenxia.data
        .BabyCelebrationRepository
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
                contactRepository.observeLogs(personId),
                factRepository.observeForPerson(personId),
                pregnancyRepository.observeForPerson(personId),
                checklistRepository.observeAll(),
                checklistRepository.observeItemsProgressForPerson(
                    personId,
                    io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.POSTNATAL
                ),
                celebrationRepository.observeOverrides(personId)
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
                            facts = comb.facts,
                            pregnancies = comb.pregnancies,
                            birthBagByPregnancy = comb.checklists
                                .filter {
                                    it.checklist.kind == io.github.xiaohaoz1
                                        .jiarenxia.core.model.ChecklistKind.BIRTH_BAG &&
                                        it.checklist.pregnancyId != null
                                }
                                .associateBy { it.checklist.pregnancyId!! },
                            postnatalByPregnancy = comb.checklists
                                .filter {
                                    it.checklist.kind == io.github.xiaohaoz1
                                        .jiarenxia.core.model.ChecklistKind.POSTNATAL &&
                                        it.checklist.pregnancyId != null
                                }
                                .associateBy { it.checklist.pregnancyId!! },
                            newbornCard = newbornCardOf(detail, comb),
                            canRecordPregnancy = run {
                                val p = detail?.person ?: return@run false
                                if (p.gender != io.github.xiaohaoz1.jiarenxia
                                        .core.model.Gender.FEMALE ||
                                    p.livingStatus != io.github.xiaohaoz1.jiarenxia
                                        .core.model.LivingStatus.ALIVE
                                ) return@run false
                                val selfId = overview?.self?.person?.id
                                val isSelfOrSpouse = p.isSelf ||
                                    (selfId != null && detail.relationships.any {
                                        it.type == io.github.xiaohaoz1.jiarenxia
                                            .core.model.RelationshipType.SPOUSE &&
                                            setOf(it.sourcePersonId,
                                                it.targetPersonId) ==
                                                setOf(personId, selfId)
                                    })
                                isSelfOrSpouse && comb.pregnancies.none {
                                    it.status == io.github.xiaohaoz1.jiarenxia
                                        .core.model.PregnancyStatus.ACTIVE
                                }
                            },
                            canRecordPregnancyMenu = run {
                                val p = detail?.person ?: return@run false
                                p.gender == io.github.xiaohaoz1.jiarenxia
                                    .core.model.Gender.FEMALE &&
                                    p.livingStatus == io.github.xiaohaoz1.jiarenxia
                                        .core.model.LivingStatus.ALIVE &&
                                    comb.pregnancies.none {
                                        it.status == io.github.xiaohaoz1.jiarenxia
                                            .core.model.PregnancyStatus.ACTIVE
                                    }
                            },
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

    fun onNoticeShown() = _ui.update { it.copy(notice = null) }

    /** 新建孕期记录（预产期或末次月经二选一由调用方换算好）。 */
    fun createPregnancy(due: java.time.LocalDate, lmp: java.time.LocalDate?) {
        viewModelScope.launch {
            val r = pregnancyRepository.create(
                personId, due, lmp, java.time.LocalDate.now())
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /** 修改预产期/末次月经（仅 ACTIVE）。 */
    fun updatePregnancyDates(
        id: String, due: java.time.LocalDate, lmp: java.time.LocalDate?
    ) {
        viewModelScope.launch {
            val r = pregnancyRepository.updateDates(
                id, due, lmp, java.time.LocalDate.now())
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /** 结束这条孕期记录：未完成产检自动归档。 */
    fun endPregnancy(id: String) {
        viewModelScope.launch {
            val r = pregnancyRepository.end(id, java.time.LocalDate.now())
            when (r) {
                is FamilyResult.Success -> _ui.update {
                    it.copy(notice = "已结束这条孕期记录")
                }
                is FamilyResult.Failure -> _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /** 删除孕期记录（连同关联产检事项）。 */
    fun deletePregnancy(id: String) {
        viewModelScope.launch {
            val r = pregnancyRepository.delete(id)
            when (r) {
                is FamilyResult.Success -> _ui.update {
                    it.copy(notice = "已删除孕期记录")
                }
                is FamilyResult.Failure -> _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    /** 某次孕期关联的全部事项（含归档与已办），供历史对话框订阅。 */
    fun tasksForPregnancy(pregnancyId: String) =
        taskRepository.observeTasksForPregnancy(pregnancyId)

    /**
     * 孕期卡“待产包”入口：按模板建清单（幂等，已有直接返回），
     * 成功后把清单 id 放进 pendingChecklistId 由页面跳转；失败走 error。
     */
    fun openBirthBag(pregnancyId: String) {
        viewModelScope.launch {
            val r = checklistRepository.createFromTemplate(
                io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.BIRTH_BAG,
                pregnancyId
            )
            when (r) {
                is FamilyResult.Success -> _ui.update {
                    it.copy(pendingChecklistId = r.value)
                }
                is FamilyResult.Failure -> _ui.update { it.copy(error = r.error.message) }
            }
        }
    }

    fun onPendingChecklistHandled() = _ui.update { it.copy(pendingChecklistId = null) }

    /**
     * 新生宝宝卡（FAMILY_AFFAIRS 26 第 3 批）：在世 + 出生日确切，且
     * （未满一岁且今天 ≤ 生效百天日 + 30 天）或名下 POSTNATAL 条目未办完。
     */
    private fun newbornCardOf(
        detail: io.github.xiaohaoz1.jiarenxia.data.PersonDetail?,
        comb: PersonDetailCombine
    ): NewbornCardState? {
        val d = detail ?: return null
        val p = d.person
        if (p.livingStatus != io.github.xiaohaoz1.jiarenxia.core.model
                .LivingStatus.ALIVE
        ) return null
        val birth = io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
            .resolveBirthDate(
                d.birthdays.values.map { it.value }, lunar
            ).date ?: return null
        val today = java.time.LocalDate.now()
        val babyMath = io.github.xiaohaoz1.jiarenxia.domain.baby.BabyMath
        val fmByBirth = babyMath.fullMonthDate(birth)
        val hdByBirth = babyMath.hundredDaysDate(birth)
        val ov = comb.celebrations
        val fm = ov[io.github.xiaohaoz1.jiarenxia.core.model
            .BabyCelebrationKind.FULL_MONTH] ?: fmByBirth
        val hd = ov[io.github.xiaohaoz1.jiarenxia.core.model
            .BabyCelebrationKind.HUNDRED_DAYS] ?: hdByBirth
        val pn = comb.postnatalItems
        val postnatalIncomplete = pn != null && pn.doneCount < pn.totalCount
        val inWindow = today.isBefore(babyMath.firstBirthday(birth)) &&
            !today.isAfter(hd.plusDays(30))
        if (!inWindow && !postnatalIncomplete) return null
        return NewbornCardState(
            birthDate = birth,
            fullMonth = fm,
            hundredDays = hd,
            fullMonthByBirth = fmByBirth,
            hundredByBirth = hdByBirth,
            fullMonthOverride =
                io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebrationKind
                    .FULL_MONTH in ov,
            hundredOverride =
                io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebrationKind
                    .HUNDRED_DAYS in ov,
            postnatalChecklistId = pn?.checklistId,
            postnatalDone = pn?.doneCount ?: 0,
            postnatalTotal = pn?.totalCount ?: 0
        )
    }

    /** 满月 / 百天庆祝日期：date 为 null 表示「按出生日算」（清除覆盖）。 */
    fun setCelebration(
        kind: io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebrationKind,
        date: java.time.LocalDate?
    ) {
        viewModelScope.launch {
            celebrationRepository.set(personId, kind, date)
        }
    }

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

    /** 新增「关于TA」；预设尺码同名时替换旧值。 */
    fun addFact(
        category: io.github.xiaohaoz1.jiarenxia.core.model.FactCategory,
        label: String?,
        content: String
    ) {
        viewModelScope.launch {
            val normalizedLabel = label?.trim()
            val existing = if (
                category == io.github.xiaohaoz1.jiarenxia.core.model.FactCategory.SIZE &&
                normalizedLabel in io.github.xiaohaoz1.jiarenxia.core.model.FACT_SIZE_LABELS
            ) {
                _ui.value.facts.firstOrNull {
                    it.category == category && it.label == normalizedLabel
                }
            } else null
            val r = if (existing != null) {
                factRepository.update(existing.id, label, content)
            } else {
                factRepository.add(personId, category, label, content)
            }
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }

    /** 修改一条「关于TA」记录。 */
    fun updateFact(id: String, label: String?, content: String) {
        viewModelScope.launch {
            val r = factRepository.update(id, label, content)
            if (r is FamilyResult.Failure) _ui.update { it.copy(error = r.error.message) }
        }
    }

    /** 删除一条「关于TA」记录。 */
    fun deleteFact(id: String) {
        viewModelScope.launch { factRepository.delete(id) }
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
