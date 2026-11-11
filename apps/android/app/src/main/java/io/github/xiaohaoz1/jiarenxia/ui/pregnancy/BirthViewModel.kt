package io.github.xiaohaoz1.jiarenxia.ui.pregnancy

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.BirthBaby
import io.github.xiaohaoz1.jiarenxia.data.BirthResult
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PregnancyRepository
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOrdering
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 表单里一个宝宝的草稿（新建 / 已在家谱中）。 */
data class BirthBabyDraft(
    val key: Int,
    val isNew: Boolean = true,
    val name: String = "",
    val gender: Gender? = null,
    val birthDate: LocalDate,
    val birthMinute: Int? = null,
    val existingId: String? = null,
    /** “姓名（称谓）”展示与回显。 */
    val existingLabel: String? = null,
    /** 已有阳历生日 → 出生日期行只读（带“来自资料”）。 */
    val existingHasSolar: Boolean = false
) {
    /** 是否填全（新建要名字+性别；已有要选人）。 */
    val complete: Boolean
        get() = if (isNew) name.trim().isNotEmpty() && gender != null
        else existingId != null
}

/** 完成态里的一行宝宝。 */
data class DoneBaby(
    val name: String,
    /** 我视角称谓（算不出为 null，省略括号）。 */
    val title: String?,
    val birthDate: LocalDate,
    val birthMinute: Int?
)

data class BirthUiState(
    val loading: Boolean = true,
    val append: Boolean = false,
    val babies: List<BirthBabyDraft> = emptyList(),
    val momId: String? = null,
    val momName: String = "",
    /** 孕妈配偶：id + “姓名（称谓）”。 */
    val spouseId: String? = null,
    val spouseLabel: String? = null,
    /** 配偶性别（决定“父亲/母亲”文案）。 */
    val spouseIsFather: Boolean = true,
    val spouseChecked: Boolean = false,
    /** 正在为哪个宝宝选“已经在家谱中”的人（key）。 */
    val pickingKey: Int? = null,
    val pickerItems: List<PersonPickItem> = emptyList(),
    val submitting: Boolean = false,
    /** 提交成功 → 完成态。 */
    val done: List<DoneBaby>? = null,
    /** POSTNATAL 清单 id（「看看出生后要办的事」入口）。 */
    val checklistId: String? = null,
    /** 宝宝视角的家人称谓行（“称谓 · 姓名”，≤8）。 */
    val kinLines: List<String> = emptyList(),
    val error: String? = null
)

/**
 * 「宝宝出生了」表单 ViewModel（FAMILY_AFFAIRS 26 第 3 批）。
 * append=true 为孕期历史里的“补记一个宝宝”（孕期须已 BORN）。
 */
@HiltViewModel
class BirthViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pregnancyRepository: PregnancyRepository,
    private val familyRepository: FamilyRepository,
    private val observeOverview: ObserveFamilyOverview,
    private val engine: KinshipEngine
) : ViewModel() {

    private val pregnancyId: String =
        checkNotNull(savedStateHandle["pregnancyId"])
    private val append: Boolean =
        savedStateHandle.get<Boolean>("append") ?: false

    private var nextKey = 0
    private val _ui = MutableStateFlow(BirthUiState(append = append))
    val ui: StateFlow<BirthUiState> = _ui

    init {
        viewModelScope.launch {
            combine(
                observeOverview(),
                pregnancyRepository.observeLinkedBabyIds()
            ) { overview, linked ->
                overview to linked
            }.collect { (overview, linked) ->
                if (overview == null) {
                    _ui.update { it.copy(loading = false) }
                    return@collect
                }
                val preg = runCatching {
                    pregnancyRepository.get(pregnancyId)
                }.getOrNull()
                if (preg == null) {
                    _ui.update { it.copy(loading = false, error = "孕期记录不存在") }
                    return@collect
                }
                val mom = overview.byId[preg.personId]
                // 孕妈配偶（SPOUSE 关系另一端）
                val spouseRel = overview.graph.relationships.firstOrNull {
                    it.type == RelationshipType.SPOUSE &&
                        (it.sourcePersonId == preg.personId ||
                            it.targetPersonId == preg.personId)
                }
                val spouseId = spouseRel?.let {
                    if (it.sourcePersonId == preg.personId) it.targetPersonId
                    else it.sourcePersonId
                }
                val spouse = spouseId?.let { overview.byId[it] }
                // 候选：排除“我”、孕妈、已关联过任何孕期的人；
                // 孕妈子女排最前，其后其余在世家人
                val momChildIds = overview.graph.relationships
                    .filter {
                        it.type == RelationshipType.PARENT &&
                            it.sourcePersonId == preg.personId
                    }
                    .map { it.targetPersonId }.toSet()
                val candidates = overview.byId.values
                    .filter { o ->
                        o.person.livingStatus ==
                            io.github.xiaohaoz1.jiarenxia.core.model
                                .LivingStatus.ALIVE &&
                            !o.person.isSelf &&
                            o.person.id != preg.personId &&
                            o.person.id !in linked &&
                            // append 时排除本孕期已记的宝宝（补记不能重复）
                            o.person.id !in preg.babyIds
                    }
                    .sortedWith(
                        compareBy<PersonOverview>(
                            { it.person.id !in momChildIds },
                            { it.displayTitle }
                        )
                    )
                val items = candidates.map { o ->
                    PersonPickItem(
                        personId = o.person.id,
                        name = o.person.name,
                        label = pickLabel(o),
                        overview = o
                    )
                }
                _ui.update { st ->
                    st.copy(
                        loading = false,
                        momId = preg.personId,
                        momName = mom?.person?.name ?: "",
                        spouseId = spouseId,
                        spouseLabel = spouse?.let { pickLabel(it) },
                        spouseIsFather = spouse?.person?.gender == Gender.MALE,
                        pickerItems = items,
                        babies = if (st.babies.isEmpty() && st.done == null)
                            listOf(newDraft()) else st.babies
                    )
                }
            }
        }
    }

    private fun newDraft() = BirthBabyDraft(
        key = nextKey++, birthDate = LocalDate.now()
    )

    /** 候选行/回显标签：“姓名（称谓）”；我视角无称谓时只显示姓名。 */
    private fun pickLabel(o: io.github.xiaohaoz1.jiarenxia.domain.PersonOverview): String {
        val t = o.displayTitle
        val isPlain = t == o.person.name ||
            o.result.kind == KinshipKind.UNRELATED
        return if (isPlain || o.person.isSelf) o.person.name
        else o.person.name + "（" + t + "）"
    }

    // ---------- 表单操作 ----------

    fun addBaby() {
        if (_ui.value.babies.size >= 3) return
        _ui.update { it.copy(babies = it.babies + newDraft()) }
    }

    fun removeBaby(key: Int) {
        _ui.update { st ->
            st.copy(babies = st.babies.filter { it.key != key })
        }
    }

    fun setNew(key: Int, isNew: Boolean) {
        _ui.update { st ->
            st.copy(babies = st.babies.map {
                if (it.key == key) it.copy(
                    isNew = isNew, existingId = null, existingLabel = null,
                    existingHasSolar = false
                ) else it
            })
        }
    }

    fun updateName(key: Int, name: String) = update(key) { it.copy(name = name) }
    fun updateGender(key: Int, gender: Gender?) = update(key) { it.copy(gender = gender) }
    fun updateDate(key: Int, date: LocalDate) = update(key) { it.copy(birthDate = date) }
    fun updateMinute(key: Int, minute: Int?) = update(key) { it.copy(birthMinute = minute) }

    private fun update(key: Int, f: (BirthBabyDraft) -> BirthBabyDraft) {
        _ui.update { st ->
            st.copy(babies = st.babies.map { if (it.key == key) f(it) else it })
        }
    }

    fun openPicker(key: Int) {
        _ui.update { it.copy(pickingKey = key) }
    }

    fun onPicked(item: PersonPickItem) {
        val key = _ui.value.pickingKey ?: return
        val o = item.overview
        val hasSolar = o?.birthdays?.any {
            it.calendar == CalendarType.SOLAR
        } == true
        // 已有阳历生日 → 出生日期沿用资料（表单里只读展示）
        val solarDate = o?.birthdays?.firstOrNull {
            it.calendar == CalendarType.SOLAR
        }?.date?.takeIf { it.isComplete }
            ?.let { d -> LocalDate.of(d.year!!, d.month, d.day) }
        _ui.update { st ->
            st.copy(
                pickingKey = null,
                babies = st.babies.map {
                    if (it.key == key) it.copy(
                        isNew = false,
                        existingId = item.personId,
                        existingLabel = item.label,
                        existingHasSolar = hasSolar,
                        birthDate = solarDate ?: it.birthDate
                    ) else it
                }
            )
        }
    }

    fun toggleSpouse() = _ui.update { it.copy(spouseChecked = !it.spouseChecked) }

    fun dismissPicker() = _ui.update { it.copy(pickingKey = null) }

    /** 「完成」：全部填全才提交；成功进入完成态。 */
    fun submit() {
        val st = _ui.value
        if (st.submitting || st.done != null ||
            st.babies.isEmpty() || !st.babies.all { it.complete }
        ) return
        val babies = st.babies.map { d ->
            if (d.isNew) BirthBaby.New(
                name = d.name.trim(),
                gender = checkNotNull(d.gender),
                birthDate = d.birthDate,
                birthMinute = d.birthMinute
            ) else BirthBaby.Existing(
                personId = checkNotNull(d.existingId),
                birthDate = if (d.existingHasSolar) null else d.birthDate,
                birthMinute = d.birthMinute
            )
        }
        val otherParent = if (st.spouseChecked) st.spouseId else null
        viewModelScope.launch {
            _ui.update { it.copy(submitting = true, error = null) }
            when (val r = pregnancyRepository.recordBirth(
                pregnancyId, babies, otherParent, append, LocalDate.now()
            )) {
                is FamilyResult.Failure -> _ui.update {
                    it.copy(submitting = false, error = r.error.message)
                }
                is FamilyResult.Success -> {
                    val done = buildDone(st, r.value)
                    _ui.update {
                        it.copy(
                            submitting = false, done = done.first,
                            kinLines = done.second,
                            checklistId = r.value.checklistId
                        )
                    }
                }
            }
        }
    }

    /** 完成态数据：宝宝行（我视角称谓）+ “从宝宝的角度看”行。 */
    private suspend fun buildDone(
        st: BirthUiState,
        result: BirthResult
    ): Pair<List<DoneBaby>, List<String>> {
        val overview = observeOverview().first() ?: return emptyList<DoneBaby>() to emptyList()
        val babies = st.babies.mapIndexed { i, d ->
            val pid = result.babyIds.getOrNull(i) ?: ""
            val name = if (d.isNew) d.name.trim()
            else d.existingLabel?.substringBefore("（") ?: ""
            val o = overview.byId[pid]
            val title = o?.displayTitle?.takeIf {
                it != o.person.name &&
                    o.result.kind != KinshipKind.UNRELATED
            }
            DoneBaby(name, title, d.birthDate, d.birthMinute)
        }
        // 从第一个宝宝的角度看：至多 8 位家人“称谓 · 姓名”，算不出称谓的跳过
        val observerId = result.babyIds.firstOrNull()
        val kinLines = if (observerId == null) emptyList() else run {
            val graph = familyRepository.observeFamilyGraph().first()
            babyKinLines(graph, observerId)
        }
        return babies to kinLines
    }

    fun onErrorShown() = _ui.update { it.copy(error = null) }
}

/**
 * 完成态「从宝宝的角度看」行：以 [observerId]（第一个宝宝）为观察者，
 * 至多 [limit] 位家人“称谓 · 姓名”。
 * 顺序复用 [FamilyOrdering]：核心家人（宝宝视角的父母、祖辈等）在前，
 * 其余已关联者按路径长度、未关联者最后；不含宝宝本人，
 * UNRELATED / SELF / 算不出称谓的跳过。
 */
internal fun babyKinLines(
    graph: FamilyGraph,
    observerId: String,
    engine: KinshipEngine = KinshipEngine(),
    limit: Int = 8
): List<String> {
    val results = engine.resolveAll(graph, observerId)
    val byId = graph.persons.associate { p ->
        p.id to PersonOverview(p, results.getValue(p.id))
    }
    val rest = byId.values.filter { it.person.id != observerId }
    val ordered = FamilyOrdering.core(
        rest.filter { it.result.coreGroup != null }, byId
    ) + FamilyOrdering.others(
        rest.filter { it.result.coreGroup == null }
    )
    return ordered.mapNotNull { o ->
        val r = o.result
        if (r.kind == KinshipKind.UNRELATED || r.kind == KinshipKind.SELF) {
            return@mapNotNull null
        }
        r.displayTitle + " · " + o.person.name
    }.take(limit)
}
