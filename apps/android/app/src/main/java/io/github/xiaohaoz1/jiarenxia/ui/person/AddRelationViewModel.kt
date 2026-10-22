package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.domain.MiddleCandidate
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.RelativePlan
import io.github.xiaohaoz1.jiarenxia.domain.SharedParents
import io.github.xiaohaoz1.jiarenxia.domain.SiblingLineage
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddRelationUiState(
    val loading: Boolean = true,
    val thisName: String = "",
    /** 候选人：除本人与已直连者外的所有人物。 */
    val candidates: List<PersonOverview> = emptyList(),
    val otherId: String? = null,
    val otherName: String = "",
    val roleOptions: List<RelationRole> = emptyList(),
    val role: RelationRole? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    /** 新增父母后待确认的“记为夫妻”询问（null 无）。 */
    val spouseAsk: io.github.xiaohaoz1.jiarenxia.data.SpouseAsk? = null,
    /** 结为夫妻后待确认的“共同子女”询问（null 无；与 spouseAsk 不同时出现）。 */
    val commonChildrenAsk: io.github.xiaohaoz1.jiarenxia.data.CommonChildrenAsk? = null,

    // ---------- 血缘细分（兄妹角色，DEVELOPMENT_PLAN 2.14，与新增人物同一套规则） ----------

    /** 本人是否为“我”的父 / 母（称谓标签用）。 */
    val anchorAsParent: Gender? = null,
    /** “我”的名字：称谓按“我”的视角时，句子的主语用它而不是本人名。 */
    val selfName: String = "",
    /** null=未选择：称呼不推断血缘，必须用户显式选。 */
    val lineage: SiblingLineage? = null,
    /** null=未确认，不默认同父同母。 */
    val sharedParents: SharedParents? = null,
    /** 本人的父 / 母候选（已录祖辈；多候选必须显式选或“不确定”）。 */
    val fatherCandidates: List<Person> = emptyList(),
    val motherCandidates: List<Person> = emptyList(),
    val pickedFatherId: String? = null,
    val pickedMotherId: String? = null,
    val fatherUnsure: Boolean = false,
    val motherUnsure: Boolean = false,
    /** 唯一候选时是否补建祖辈关系（默认不勾：候选存在不等于用户确认）。 */
    val linkFather: Boolean = false,
    val linkMother: Boolean = false,
    /** 堂 / 表中间人候选与选择。 */
    val middleCandidates: List<MiddleCandidate> = emptyList(),
    val middleId: String? = null,
    val middleNone: Boolean = false,
    /** 只记称呼的称呼文本（对已有人物写观察者称呼）。 */
    val titleOnlyText: String = "",
    val titleTouched: Boolean = false
)

/** 添加关系页：选择对方人物 + 选择“对方是本人的……”。 */
@HiltViewModel
class AddRelationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FamilyRepository,
    observeOverview: ObserveFamilyOverview
) : ViewModel() {

    private val personId: String = checkNotNull(savedStateHandle["personId"])

    private val _ui = MutableStateFlow(AddRelationUiState())
    val ui: StateFlow<AddRelationUiState> = _ui

    init {
        viewModelScope.launch {
            combine(
                repository.observePersonDetail(personId),
                observeOverview()
            ) { d, o -> d to o }.collect { (detail, overview) ->
                val related = detail?.relationships.orEmpty()
                    .flatMap { listOf(it.sourcePersonId, it.targetPersonId) }.toSet()
                val candidates = overview?.byId?.values.orEmpty()
                    .filter { it.person.id != personId && it.person.id !in related }
                // 血缘细分上下文（本人固定，不随对方变化）
                val graph = overview?.graph
                val father = graph?.let {
                    RelativePlan.parentCandidates(it, personId, Gender.MALE)
                }.orEmpty()
                val mother = graph?.let {
                    RelativePlan.parentCandidates(it, personId, Gender.FEMALE)
                }.orEmpty()
                val middles = graph?.let {
                    RelativePlan.middleCandidates(it, personId)
                }.orEmpty()
                val asParent = overview?.byId?.get(personId)
                    ?.takeIf {
                        it.result.relationshipPath.map { s -> s.type } ==
                            listOf(RelationshipType.PARENT)
                    }?.person?.gender
                _ui.update { st ->
                    st.copy(
                        loading = false,
                        thisName = detail?.person?.name ?: "",
                        selfName = overview?.self?.person?.name.orEmpty(),
                        candidates = candidates,
                        fatherCandidates = father,
                        motherCandidates = mother,
                        middleCandidates = middles,
                        anchorAsParent = asParent,
                        pickedFatherId = father.singleOrNull()?.id
                            ?: st.pickedFatherId?.takeIf { p -> father.any { it.id == p } },
                        pickedMotherId = mother.singleOrNull()?.id
                            ?: st.pickedMotherId?.takeIf { p -> mother.any { it.id == p } },
                        middleId = st.middleId?.takeIf { m ->
                            middles.any { it.person.id == m } }
                    )
                }
            }
        }
    }

    /** 血缘细分确认全部失效（换对方/换称谓时调用，不沿用旧确认）。 */
    private fun AddRelationUiState.resetLineage() = copy(
        lineage = null, sharedParents = null,
        pickedFatherId = null, pickedMotherId = null,
        fatherUnsure = false, motherUnsure = false,
        linkFather = false, linkMother = false,
        middleId = null, middleNone = false, titleTouched = false
    )

    fun onPick(other: PersonOverview) {
        // 按对方性别过滤角色选项（FATHER/SON/HUSBAND/… 为男性角色）
        val roles = RelationRole.entries.filter { it.gender == other.person.gender }
        _ui.update {
            it.copy(otherId = other.person.id, otherName = other.person.name,
                roleOptions = roles, role = null).resetLineage()
        }
        refreshTitle()
    }

    fun onRole(r: RelationRole) {
        _ui.update { it.copy(role = r).resetLineage() }
        refreshTitle()
    }

    /** 未手改称呼时跟随所选角色给默认称呼。 */
    private fun refreshTitle() {
        _ui.update { st ->
            if (st.titleTouched) st else st.copy(
                titleOnlyText = st.role?.let {
                    RelativePlan.baseAppellation(it, st.anchorAsParent)
                } ?: ""
            )
        }
    }

    // ---------- 血缘细分 ----------

    fun onLineage(v: SiblingLineage) = _ui.update { it.copy(lineage = v) }
    fun onSharedParents(v: SharedParents) = _ui.update { it.copy(sharedParents = v) }
    fun onPickFather(id: String) = _ui.update {
        it.copy(pickedFatherId = id, fatherUnsure = false)
    }
    fun onFatherUnsure() = _ui.update {
        it.copy(pickedFatherId = null, fatherUnsure = true)
    }
    fun onLinkFather(v: Boolean) = _ui.update { it.copy(linkFather = v) }
    fun onPickMother(id: String) = _ui.update {
        it.copy(pickedMotherId = id, motherUnsure = false)
    }
    fun onMotherUnsure() = _ui.update {
        it.copy(pickedMotherId = null, motherUnsure = true)
    }
    fun onLinkMother(v: Boolean) = _ui.update { it.copy(linkMother = v) }
    fun onMiddle(id: String) = _ui.update {
        it.copy(middleId = id, middleNone = false)
    }
    fun onMiddleNone() = _ui.update { it.copy(middleId = null, middleNone = true) }
    fun onTitleOnlyText(v: String) = _ui.update {
        it.copy(titleOnlyText = v, titleTouched = true)
    }

    fun onErrorShown() = _ui.update { it.copy(error = null) }

    private fun titleOk(text: String) = text.trim().length in 1..12

    /** 与 PersonEditViewModel.lineageReady 同一套规则。 */
    private fun lineageReady(st: AddRelationUiState): Boolean {
        val role = st.role ?: return false
        if (role !in RelativePlan.SIBLING_ROLES) return true
        fun sideReady(
            sideOn: Boolean, candidates: List<Person>, picked: String?, unsure: Boolean
        ) = !sideOn || candidates.size <= 1 || picked != null || unsure
        return when (st.lineage) {
            // 未选择血缘细分：称呼不推断血缘，不能保存
            null -> false
            SiblingLineage.REAL -> {
                // 共同父母必须显式确认，不默认同父同母
                val sp = st.sharedParents ?: return false
                sideReady(sp.fatherSide, st.fatherCandidates,
                    st.pickedFatherId, st.fatherUnsure) &&
                sideReady(sp.motherSide, st.motherCandidates,
                    st.pickedMotherId, st.motherUnsure)
            }
            SiblingLineage.COUSIN -> when {
                st.middleId != null && !st.middleNone -> true
                st.middleCandidates.isEmpty() || st.middleNone -> titleOk(st.titleOnlyText)
                else -> false
            }
            SiblingLineage.TITLE_ONLY -> titleOk(st.titleOnlyText)
        }
    }

    /** 当前可保存（界面据此启用“保存”）。 */
    val canSave: Boolean
        get() = _ui.value.let {
            !it.saving && it.otherId != null && it.role != null && lineageReady(it)
        }

    /**
     * 固定底栏缺项定位：返回（提示文本，LazyColumn 目标下标）。
     * 下标约定与 AddRelationContent 一致：0=选择家人卡，1=角色/血缘卡。
     */
    fun missingTarget(): Pair<String, Int>? {
        val st = _ui.value
        return when {
            st.otherId == null -> "还差：选择家人" to 0
            st.role == null -> "还差：选择关系" to 1
            !lineageReady(st) -> "还差：完善血缘信息" to 1
            else -> null
        }
    }

    private fun fatherLinkId(st: AddRelationUiState): String? = when {
        st.sharedParents?.fatherSide != true -> null
        st.fatherCandidates.size > 1 -> st.pickedFatherId
        else -> st.pickedFatherId?.takeIf { st.linkFather }
    }

    private fun motherLinkId(st: AddRelationUiState): String? = when {
        st.sharedParents?.motherSide != true -> null
        st.motherCandidates.size > 1 -> st.pickedMotherId
        else -> st.pickedMotherId?.takeIf { st.linkMother }
    }

    /**
     * 落库方案（对方已存在）：specs 走 addRelationships 事务；
     * “只记称呼”为空 specs + 称呼文本（写 custom_titles）。
     */
    private fun buildPlan(
        st: AddRelationUiState
    ): Pair<List<RelationshipSpec>, String?> {
        val role = st.role ?: return emptyList<RelationshipSpec>() to null
        val other = st.otherId ?: return emptyList<RelationshipSpec>() to null
        if (role !in RelativePlan.SIBLING_ROLES) {
            return listOf(role.toRelationshipSpec(other, personId)) to null
        }
        return when (st.lineage) {
            SiblingLineage.REAL ->
                RelativePlan.buildSpecs(
                    other, personId, role, SiblingLineage.REAL,
                    parentIds = listOfNotNull(fatherLinkId(st), motherLinkId(st))
                ) to null
            SiblingLineage.COUSIN -> {
                val hasMiddle = st.middleId != null && !st.middleNone
                // 申报堂表亲记录始终落库；无中间人时另记自定义称呼
                RelativePlan.buildSpecs(
                    other, personId, role, SiblingLineage.COUSIN,
                    middleId = st.middleId.takeIf { hasMiddle }
                ) to if (hasMiddle) null else st.titleOnlyText.trim()
            }
            SiblingLineage.TITLE_ONLY ->
                emptyList<RelationshipSpec>() to st.titleOnlyText.trim()
            // 未确认细分时不可达（canSave 拦截）；防御性不落血缘
            null -> emptyList<RelationshipSpec>() to null
        }
    }

    fun save() {
        val st = _ui.value
        if (!canSave) return
        val other = st.otherId ?: return
        val role = st.role ?: return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val (specs, title) = buildPlan(st)
            // 补录已有堂表亲的长幼时，中间人的亲子关系可能早已存在。
            // 只跳过完全相同的亲子事实；新关系仍在仓库事务内校验。
            val existing = repository.observeFamilyGraph().first().relationships
            val newSpecs = specs.filterNot { spec ->
                spec.type == RelationshipType.PARENT && existing.any {
                    it.type == spec.type && it.sourcePersonId == spec.sourcePersonId &&
                        it.targetPersonId == spec.targetPersonId
                }
            }
            // 关系与自定义称呼同一事务写入：任一失败整体回滚并显示错误，
            // 不会出现“写了关系但称呼丢失”或“失败却显示完成”
            when (val write = repository.addRelationships(newSpecs, other, title)) {
                is FamilyResult.Failure -> _ui.update {
                    it.copy(saving = false, error = write.error.message)
                }
                is FamilyResult.Success -> {
                    // 对方被记为本人的父/母：孩子的另一位父母无配偶 → 询问记为夫妻
                    val ask = if (role == RelationRole.FATHER ||
                        role == RelationRole.MOTHER
                    ) {
                        repository.coParentCandidate(other)?.let { c ->
                            io.github.xiaohaoz1.jiarenxia.data.SpouseAsk(
                                other, _ui.value.otherName, c.id, c.name)
                        }
                    } else null
                    // 对方被记为本人的丈夫/妻子：双方已录子女可能是共同子女 → 询问
                    val childAsk = if (role == RelationRole.HUSBAND ||
                        role == RelationRole.WIFE
                    ) {
                        repository.commonChildCandidates(personId, other)
                            .takeIf { it.isNotEmpty() }
                            ?.let { cands ->
                                io.github.xiaohaoz1.jiarenxia.data.CommonChildrenAsk(
                                    spouseAId = personId,
                                    spouseAName = _ui.value.thisName,
                                    spouseBId = other,
                                    spouseBName = _ui.value.otherName,
                                    candidates = cands)
                            }
                    } else if (ask == null) {
                        // 只检查本次新建亲子边所指向的孩子，不追问其他已有家人。
                        val childId = specs.mapNotNull { spec ->
                            when (spec.type) {
                                RelationshipType.PARENT -> spec.targetPersonId
                                RelationshipType.CHILD -> spec.sourcePersonId
                                else -> null
                            }
                        }.distinct().singleOrNull()
                        childId?.let {
                            io.github.xiaohaoz1.jiarenxia.data.ParentCompletion.ask(
                                repository.observeFamilyGraph().first(), it)
                        }
                    } else null
                    _ui.update {
                        it.copy(saving = false, done = ask == null && childAsk == null,
                            spouseAsk = ask, commonChildrenAsk = childAsk)
                    }
                }
            }
        }
    }

    /**
     * 询问选“是”：建立两人 SPOUSE 关系；随后若双方已录子女中有共同子女
     * 候选，接着弹共同子女询问（两个对话框不同时出现）；失败显示错误并
     * 保留询问。
     */
    fun confirmSpouse() {
        val ask = _ui.value.spouseAsk ?: return
        viewModelScope.launch {
            val r = repository.addRelationship(
                ask.newPersonId, ask.candidateId, RelationRole.HUSBAND)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
                return@launch
            }
            val children = repository.commonChildCandidates(
                ask.newPersonId, ask.candidateId)
            if (children.isEmpty()) {
                _ui.update { it.copy(spouseAsk = null, done = true) }
            } else {
                _ui.update {
                    it.copy(
                        spouseAsk = null,
                        commonChildrenAsk = io.github.xiaohaoz1.jiarenxia.data
                            .CommonChildrenAsk(
                                spouseAId = ask.candidateId,
                                spouseAName = ask.candidateName,
                                spouseBId = ask.newPersonId,
                                spouseBName = ask.newPersonName,
                                candidates = children)
                    )
                }
            }
        }
    }

    /** 询问选“不是”：不建关系，直接关闭流程。 */
    fun dismissSpouseAsk() {
        _ui.update { it.copy(spouseAsk = null, done = true) }
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

    /**
     * “共同子女”选“确定”：勾选项经 addRelationships 一次写入
     * “另一方是该子女的父/母”；失败显示错误并保留询问。
     */
    fun confirmCommonChildren() {
        val ask = _ui.value.commonChildrenAsk ?: return
        viewModelScope.launch {
            val r = repository.addRelationships(ask.checkedSpecs())
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
                return@launch
            }
            _ui.update { it.copy(commonChildrenAsk = null, done = true) }
        }
    }

    /** “共同子女”选“都不是”：不建关系，直接关闭流程。 */
    fun dismissCommonChildren() {
        _ui.update { it.copy(commonChildrenAsk = null, done = true) }
    }
}
