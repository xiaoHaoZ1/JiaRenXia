package io.github.xiaohaoz1.jiarenxia.ui.person

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.PhoneDraft
import io.github.xiaohaoz1.jiarenxia.domain.MiddleCandidate
import io.github.xiaohaoz1.jiarenxia.domain.NEW_PERSON
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.RelativePlan
import io.github.xiaohaoz1.jiarenxia.domain.SharedParents
import io.github.xiaohaoz1.jiarenxia.domain.SiblingLineage
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 编辑页模式：创建“我” / 新增亲属 / 编辑。 */
enum class PersonEditMode { SELF, NEW, EDIT }

/**
 * 人物表单的可校验字段：错误标出与自动定位共用同一套标识（声明顺序即页面顺序）。
 * ANCHOR/ROLE/LINEAGE 依次只在前一项满足后才出现（关联人 → 称谓 → 血缘细分）。
 */
enum class PersonEditField { NAME, GENDER, ANCHOR, ROLE, LINEAGE }

/**
 * 保存被拒后的自动定位目标（各自绑定对应控件与错误提示区域）：
 * NAME 聚焦姓名框并唤起键盘；GENDER 滚到性别选项；ANCHOR 滚到关联人行；
 * ROLE 滚到称谓选项；LINEAGE 滚到血缘细分区块。
 */
enum class EditFocusTarget { NAME, GENDER, ANCHOR, ROLE, LINEAGE }

/** 手机号表单行。 */
data class PhoneRowState(
    /**
     * 稳定行身份：已有电话用 "db:<id>"，新行用 "new:<n>"。
     * 错误定位/行级操作一律按 rowKey，不拿列表下标当身份。
     */
    val rowKey: String = "",
    val id: String? = null,
    val number: String = "",
    val label: PhoneLabel = PhoneLabel.MOBILE,
    val status: PhoneStatus = PhoneStatus.ACTIVE,
    val isPrimary: Boolean = false,
    val note: String? = null,
    /** 行内错误（保存校验时标出），改正后自动清除；有错误的行阻断保存。 */
    val error: String? = null,
    /** 行内提示（“这个号码已经添加了”等），不阻断保存。 */
    val notice: String? = null
) {
    /**
     * 完全空白的新行：无 id、号码为空、附加字段全默认。
     * 保存时整行跳过不写入，也不算必填缺项。
     */
    fun isBlankNew(): Boolean =
        id == null && number.isBlank() && label == PhoneLabel.MOBILE &&
            status == PhoneStatus.ACTIVE && !isPrimary && note.isNullOrBlank()

    /** 号码为空但必须填：已有号码被清空，或新空行已填附加内容。 */
    fun needsNumberError(): Boolean = number.isBlank() && !isBlankNew()
}

/** 单槽生日表单状态：on=本条已填写；农历才有 leapMonth。 */
data class BirthdayFormState(
    val on: Boolean = false,
    val leapMonth: Boolean = false,
    val yearText: String = "",
    val yearUnknown: Boolean = false,
    val month: Int = 1,
    val day: Int = 1
) {
    /** 表单值 → BirthdayValue；未启用或日期不完整写不出时按当前值构造（年份可缺）。 */
    fun toValue(calendar: CalendarType): BirthdayValue? {
        if (!on) return null
        return BirthdayValue(
            date = PartialDate(
                year = if (yearUnknown || yearText.isBlank()) null else yearText.toIntOrNull(),
                month = month,
                day = day
            ),
            calendar = calendar,
            leapMonth = calendar == CalendarType.LUNAR && leapMonth
        )
    }

    /** 已有完整年月日（换算另一历法的前提）。 */
    fun complete(): Boolean = on && !yearUnknown && yearText.toIntOrNull() != null

    companion object {
        fun of(b: BirthdayValue): BirthdayFormState = BirthdayFormState(
            on = true,
            leapMonth = b.leapMonth,
            yearText = b.date.year?.toString() ?: "",
            yearUnknown = b.date.year == null,
            month = b.date.month,
            day = b.date.day
        )
    }
}

data class PersonEditUiState(
    val loaded: Boolean = false,
    val mode: PersonEditMode = PersonEditMode.NEW,
    val name: String = "",
    val gender: Gender? = null,
    val deceased: Boolean = false,
    /** 阳历生日槽位。 */
    val solarBirthday: BirthdayFormState = BirthdayFormState(),
    /** 农历生日槽位。 */
    val lunarBirthday: BirthdayFormState = BirthdayFormState(),
    /** 两份完整生日换算结果不一致（需用户核对；再次保存视为确认保留）。 */
    val birthdayConflict: Boolean = false,
    /** 冲突提示已展示过一次（再次点保存放行）。 */
    val conflictAcked: Boolean = false,
    val phones: List<PhoneRowState> = emptyList(),
    val note: String = "",
    val avatarPath: String? = null,       // 已保存头像相对路径
    val avatarUri: Uri? = null,           // 新选取的头像
    val avatarRemoved: Boolean = false,   // 用户点了“移除头像”
    val anchorId: String? = null,
    val anchorName: String = "",
    val anchorTitle: String = "",
    val anchorSelectable: Boolean = false,
    val anchorCandidates: List<io.github.xiaohaoz1.jiarenxia.core.model.Person> = emptyList(),
    val roleOptions: List<RelationRole> = emptyList(),
    val role: RelationRole? = null,
    val saving: Boolean = false,
    val dirty: Boolean = false,
    val done: Boolean = false,
    val isSelf: Boolean = false,   // 被编辑人物是否为“我”
    val error: String? = null,
    /** 首次保存被拒后置真：自此 fieldErrors 随输入实时刷新（错即标、改即消）。 */
    val saveAttempted: Boolean = false,
    /** 当前无效字段集合（仅 saveAttempted 后非空），就地错误提示据此渲染。 */
    val fieldErrors: Set<PersonEditField> = emptySet(),
    /** 新增父母后待确认的“记为夫妻”询问（null 无）。 */
    val spouseAsk: io.github.xiaohaoz1.jiarenxia.data.SpouseAsk? = null,
    /** 结为夫妻后待确认的“共同子女”询问（null 无；与 spouseAsk 不同时出现）。 */
    val commonChildrenAsk: io.github.xiaohaoz1.jiarenxia.data.CommonChildrenAsk? = null,

    // ---------- 称谓录入（兄妹角色的血缘细分，DEVELOPMENT_PLAN 2.14） ----------

    /** 血缘细分（仅兄妹类角色有效）。null=未选择：称呼不推断血缘，必须用户显式选。 */
    val lineage: SiblingLineage? = null,
    /** 共同父母确认（REAL 时有效）。null=未确认，不得默认同父同母。 */
    val sharedParents: SharedParents? = null,
    /** 称谓上下文：关联人是“我”的父 / 母时为对应性别，否则 null。 */
    val anchorAsParent: Gender? = null,
    /** “我”的名字：称谓按“我”的视角时，句子的主语用它而不是关联人名。 */
    val selfName: String = "",
    /** 关联人的父 / 母候选（已录祖辈，不凭空创建）。 */
    val fatherCandidates: List<io.github.xiaohaoz1.jiarenxia.core.model.Person> = emptyList(),
    val motherCandidates: List<io.github.xiaohaoz1.jiarenxia.core.model.Person> = emptyList(),
    /** 多候选歧义时的用户选定；唯一候选自动选上。 */
    val pickedFatherId: String? = null,
    val pickedMotherId: String? = null,
    /** 多候选时显式选“不确定”（不补建该侧）。 */
    val fatherUnsure: Boolean = false,
    val motherUnsure: Boolean = false,
    /** 唯一候选时勾选是否补建祖辈关系（默认不勾：候选存在不等于用户确认）。 */
    val linkFather: Boolean = false,
    val linkMother: Boolean = false,
    /** 堂 / 表中间人候选与选择；middleNone = “以上都不是，只记称呼”。 */
    val middleCandidates: List<MiddleCandidate> = emptyList(),
    val middleId: String? = null,
    val middleNone: Boolean = false,
    /** 只记称呼的称呼文本（TITLE_ONLY 或堂/表无中间人时使用）。 */
    val titleOnlyText: String = "",
    /** 用户改过称呼文本后不再被称谓默认值覆盖。 */
    val titleTouched: Boolean = false,
    /** “保存并继续添加”后的提示（消费一次）。 */
    val savedNotice: String? = null,
    /** 需要滚动定位的电话行 rowKey（界面消费后回调清除）。 */
    val focusPhoneKey: String? = null,
    /** 定位电话行时是否顺带唤起键盘聚焦号码框（“手动填写”聚焦空行时为真）。 */
    val focusPhoneKeyboard: Boolean = false,
    /** 保存被拒/缺项点击后的定位目标（界面消费后回调清除）。 */
    val focusTarget: EditFocusTarget? = null
)

@HiltViewModel
class PersonEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FamilyRepository,
    private val observeOverview: ObserveFamilyOverview,
    val lunar: LunarCalendar
) : ViewModel() {

    private val personId: String? =
        savedStateHandle.get<String>("personId")?.takeIf { it.isNotBlank() }
    private val anchorIdArg: String = savedStateHandle["anchorId"] ?: ""
    private val rolesArg: String = savedStateHandle["roles"] ?: ""
    private val anchorsArg: String = savedStateHandle["anchors"] ?: ""

    private val _ui = MutableStateFlow(PersonEditUiState())
    val ui: StateFlow<PersonEditUiState> = _ui

    private var initialized = false

    /** 初始化表单（每种模式一次）。 */
    fun init(mode: PersonEditMode) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            when (mode) {
                PersonEditMode.SELF -> _ui.update { it.copy(loaded = true, mode = mode) }
                PersonEditMode.EDIT -> loadExisting()
                PersonEditMode.NEW -> initNew()
            }
        }
    }

    private suspend fun loadExisting() {
        val id = personId ?: return
        val overview = observeOverview().first()
        // 取一次详情快照填充表单
        val detail = repository.observePersonDetail(id).first()
        if (detail == null) { _ui.update { it.copy(loaded = true, mode = PersonEditMode.EDIT) }; return }
        val solar = detail.birthdays[CalendarType.SOLAR]?.value
        val lunar = detail.birthdays[CalendarType.LUNAR]?.value
        _ui.update {
            it.copy(
                loaded = true,
                mode = PersonEditMode.EDIT,
                name = detail.person.name,
                gender = detail.person.gender,
                deceased = detail.person.livingStatus == LivingStatus.DECEASED,
                solarBirthday = solar?.let { v -> BirthdayFormState.of(v) }
                    ?: BirthdayFormState(),
                lunarBirthday = lunar?.let { v -> BirthdayFormState.of(v) }
                    ?: BirthdayFormState(),
                phones = detail.phones.map { p ->
                    PhoneRowState("db:" + p.id, p.id, p.number,
                        p.label, p.status, p.isPrimary, p.note)
                },
                note = detail.person.note ?: "",
                avatarPath = detail.person.avatarPath,
                anchorTitle = overview?.byId?.get(id)?.displayTitle ?: "",
                isSelf = detail.person.isSelf
            )
        }
    }

    private suspend fun initNew() {
        val overview = observeOverview().first()
        val roleOptions = rolesArg.split(',').filter { it.isNotBlank() }
            .mapNotNull { runCatching { RelationRole.valueOf(it) }.getOrNull() }
            .ifEmpty { RelationRole.entries.toList() }
        val all = overview?.graph?.persons.orEmpty()
        val anchors = anchorsArg.split(',').filter { it.isNotBlank() }
        val candidates = if (anchors.isEmpty()) all else all.filter { it.id in anchors }
        val anchor = when {
            anchorIdArg.isNotBlank() -> all.firstOrNull { it.id == anchorIdArg }
            candidates.size == 1 -> candidates.first()
            else -> null
        }
        _ui.update {
            it.copy(
                loaded = true,
                mode = PersonEditMode.NEW,
                anchorId = anchor?.id,
                anchorName = anchor?.name ?: "",
                anchorTitle = anchor?.let { a -> overview?.byId?.get(a.id)?.displayTitle ?: "" } ?: "",
                anchorSelectable = anchorIdArg.isBlank(),
                anchorCandidates = candidates,
                selfName = overview?.self?.person?.name.orEmpty(),
                roleOptions = roleOptions,
                role = roleOptions.singleOrNull()
            )
        }
        // 固定单一选项时同步预填性别
        roleOptions.singleOrNull()?.let { r -> _ui.update { it.copy(gender = r.gender) } }
        refreshLineage()
    }

    /**
     * 重新计算血缘细分上下文：关联人的父母候选、堂/表中间人候选、
     * 关联人是否为“我”的父/母（称谓标签用）。
     */
    private suspend fun refreshLineage() {
        val anchorId = _ui.value.anchorId ?: return
        val ov = observeOverview().first() ?: return
        val father = RelativePlan.parentCandidates(ov.graph, anchorId, Gender.MALE)
        val mother = RelativePlan.parentCandidates(ov.graph, anchorId, Gender.FEMALE)
        val middles = RelativePlan.middleCandidates(ov.graph, anchorId)
        // “关联人是我爸/我妈”→ 兄妹角色显示为伯叔姑 / 舅姨称谓
        val asParent = ov.byId[anchorId]
            ?.takeIf {
                it.result.relationshipPath.map { s -> s.type } ==
                    listOf(RelationshipType.PARENT)
            }?.person?.gender
        _ui.update { st ->
            st.copy(
                fatherCandidates = father,
                motherCandidates = mother,
                middleCandidates = middles,
                anchorAsParent = asParent,
                // 唯一候选自动选；多候选保留已有选择（仍有效时）
                pickedFatherId = father.singleOrNull()?.id
                    ?: st.pickedFatherId?.takeIf { p -> father.any { it.id == p } },
                pickedMotherId = mother.singleOrNull()?.id
                    ?: st.pickedMotherId?.takeIf { p -> mother.any { it.id == p } },
                middleId = st.middleId?.takeIf { m -> middles.any { it.person.id == m } },
                titleOnlyText = if (st.titleTouched) st.titleOnlyText
                else st.role?.let { RelativePlan.baseAppellation(it, asParent) } ?: ""
            ).withFieldErrors()
        }
    }

    // ---------- 字段变更 ----------

    private fun mark(transform: PersonEditUiState.() -> PersonEditUiState) =
        _ui.update { it.transform().withFieldErrors().copy(dirty = true) }

    /**
     * 首次保存被拒后，字段错误随输入实时刷新：错误条件消失即从
     * fieldErrors 移除（就地提示自动消失）；未尝试过保存则不动。
     */
    private fun PersonEditUiState.withFieldErrors(): PersonEditUiState =
        if (!saveAttempted) this else copy(fieldErrors = invalidFields(this))

    fun onName(v: String) = mark { copy(name = v) }
    fun onGender(v: Gender) = mark { copy(gender = v) }
    fun onDeceased(v: Boolean) = mark { copy(deceased = v) }

    /** 阳历生日表单整体替换（Screen 侧先改字段再回传）。 */
    fun onSolarBirthday(f: BirthdayFormState) = mark {
        copy(
            solarBirthday = normalizeBirthday(f, CalendarType.SOLAR),
            conflictAcked = false
        ).recomputeConflict()
    }
    /** 农历生日表单整体替换。 */
    fun onLunarBirthday(f: BirthdayFormState) = mark {
        copy(
            lunarBirthday = normalizeBirthday(f, CalendarType.LUNAR),
            conflictAcked = false
        ).recomputeConflict()
    }

    /**
     * 用户主动触发：把 [from] 槽位的完整生日换算填入另一历法槽位。
     * 年份缺失或日期无法换算时忽略（缺年份不伪造固定对应日）；
     * 只填表单，不直接落库——仍由“保存”提交。
     */
    fun onConvert(from: CalendarType) {
        val st = _ui.value
        val src = if (from == CalendarType.SOLAR) st.solarBirthday else st.lunarBirthday
        val v = src.toValue(from) ?: return
        val y = v.date.year ?: return
        val target: BirthdayFormState = when (from) {
            CalendarType.SOLAR -> {
                if (!v.date.isValid()) return
                val ld = lunar.fromSolar(java.time.LocalDate.of(y, v.date.month, v.date.day))
                BirthdayFormState(
                    on = true, leapMonth = ld.leap, yearText = ld.year.toString(),
                    month = ld.month, day = ld.day
                )
            }
            CalendarType.LUNAR -> {
                val sd = lunar.toSolar(y, v.date.month, v.date.day, v.leapMonth) ?: return
                BirthdayFormState(
                    on = true, yearText = sd.year.toString(),
                    month = sd.monthValue, day = sd.dayOfMonth
                )
            }
        }
        mark {
            (if (from == CalendarType.SOLAR) copy(lunarBirthday = target)
            else copy(solarBirthday = target)).recomputeConflict()
        }
    }

    fun onNote(v: String) = mark { copy(note = v) }
    fun onAvatar(uri: Uri) = mark { copy(avatarUri = uri, avatarRemoved = false) }
    fun onRemoveAvatar() = mark { copy(avatarUri = null, avatarRemoved = true, avatarPath = null) }
    /** 血缘细分确认全部失效（换关联人/换称谓时调用，不沿用旧确认）。 */
    private fun PersonEditUiState.resetLineage() = copy(
        lineage = null, sharedParents = null,
        pickedFatherId = null, pickedMotherId = null,
        fatherUnsure = false, motherUnsure = false,
        linkFather = false, linkMother = false,
        middleId = null, middleNone = false, titleTouched = false
    )

    fun onAnchor(id: String) = mark {
        val c = anchorCandidates.firstOrNull { p -> p.id == id }
        copy(
            anchorId = id, anchorName = c?.name ?: anchorName
        ).resetLineage()
    }.also { viewModelScope.launch { refreshLineage() } }

    fun onRole(r: RelationRole) = mark {
        copy(
            role = r, gender = r.gender
        ).resetLineage()
    }.also { viewModelScope.launch { refreshLineage() } }

    // ---------- 血缘细分 ----------

    fun onLineage(v: SiblingLineage) = mark { copy(lineage = v) }
    fun onSharedParents(v: SharedParents) = mark { copy(sharedParents = v) }
    fun onPickFather(id: String) = mark { copy(pickedFatherId = id, fatherUnsure = false) }
    fun onFatherUnsure() = mark { copy(pickedFatherId = null, fatherUnsure = true) }
    fun onLinkFather(v: Boolean) = mark { copy(linkFather = v) }
    fun onPickMother(id: String) = mark { copy(pickedMotherId = id, motherUnsure = false) }
    fun onMotherUnsure() = mark { copy(pickedMotherId = null, motherUnsure = true) }
    fun onLinkMother(v: Boolean) = mark { copy(linkMother = v) }
    fun onMiddle(id: String) = mark { copy(middleId = id, middleNone = false) }
    fun onMiddleNone() = mark { copy(middleId = null, middleNone = true) }
    fun onTitleOnlyText(v: String) = mark { copy(titleOnlyText = v, titleTouched = true) }
    fun onSavedNoticeShown() = _ui.update { it.copy(savedNotice = null) }

    private var phoneSeq = 0
    private fun newPhoneRowKey() = "new:" + (++phoneSeq)

    /** “手动填写”：优先聚焦第一条完全空白的新行；没有才追加，不制造连击空行。 */
    fun addPhone() {
        val blank = _ui.value.phones.firstOrNull { it.isBlankNew() }
        if (blank != null) {
            // 只是定位已有空行，不改表单内容，不标 dirty
            _ui.update {
                it.copy(focusPhoneKey = blank.rowKey, focusPhoneKeyboard = true)
            }
            return
        }
        val key = newPhoneRowKey()
        mark {
            copy(
                phones = phones + PhoneRowState(rowKey = key),
                focusPhoneKey = key, focusPhoneKeyboard = true
            )
        }
    }

    /**
     * 卡级“从联系人选择”：先按 normalizePhone 去重——
     * 已存在则不追加、不改原行，定位该行并就地提示“这个号码已经添加了”；
     * 否则优先填第一条完全空白的新行，再没有才追加。
     * NEW 模式且姓名为空时按原规则顺带带出联系人姓名（已填姓名 / 编辑 / SELF 不改）。
     */
    fun addPhoneFromContact(name: String?, number: String) {
        val key = io.github.xiaohaoz1.jiarenxia.data.normalizePhone(number)
        // 结果无号码：不追加空行、不清已有值（取消选择时回调本就不触发）
        if (key.isBlank()) return
        val st = _ui.value
        val fillName = st.mode == PersonEditMode.NEW && st.name.isBlank() &&
            !name.isNullOrBlank()
        val dup = st.phones.firstOrNull {
            it.number.isNotBlank() &&
                io.github.xiaohaoz1.jiarenxia.data.normalizePhone(it.number) == key
        }
        if (dup != null) {
            _ui.update {
                it.copy(
                    phones = it.phones.map { r ->
                        if (r.rowKey == dup.rowKey)
                            r.copy(notice = "这个号码已经添加了")
                        else r
                    },
                    focusPhoneKey = dup.rowKey, focusPhoneKeyboard = false,
                    name = if (fillName) name.orEmpty().trim() else it.name,
                    dirty = it.dirty || fillName
                ).withFieldErrors()
            }
            return
        }
        val blank = st.phones.firstOrNull { it.isBlankNew() }
        mark {
            copy(
                phones = if (blank != null) phones.map { r ->
                    if (r.rowKey == blank.rowKey)
                        r.copy(number = number, error = null, notice = null)
                    else r
                } else phones + PhoneRowState(rowKey = newPhoneRowKey(), number = number),
                name = if (fillName) name.orEmpty().trim() else this.name
            ).withPhoneErrorsRecomputed()
        }
    }

    /**
     * 行级“从联系人选择”：只作用本行。撞上其它行同号码时保留当前输入、
     * 就地提示冲突在第几条，不改动其它行；否则把号码写入本行。
     */
    fun pickPhoneForRow(rowKey: String, name: String?, number: String) {
        val key = io.github.xiaohaoz1.jiarenxia.data.normalizePhone(number)
        if (key.isBlank()) return
        val st = _ui.value
        val idx = st.phones.indexOfFirst { it.rowKey == rowKey }
        if (idx < 0) return
        val dupIdx = st.phones.indexOfFirst { r ->
            r.rowKey != rowKey && r.number.isNotBlank() &&
                io.github.xiaohaoz1.jiarenxia.data.normalizePhone(r.number) == key
        }
        val fillName = st.mode == PersonEditMode.NEW && st.name.isBlank() &&
            !name.isNullOrBlank()
        if (dupIdx >= 0) {
            _ui.update {
                it.copy(
                    phones = it.phones.map { r ->
                        if (r.rowKey == rowKey)
                            r.copy(notice = "这个号码已经在第 " + (dupIdx + 1) + " 条了")
                        else r
                    },
                    focusPhoneKey = rowKey, focusPhoneKeyboard = false,
                    name = if (fillName) name.orEmpty().trim() else it.name,
                    dirty = it.dirty || fillName
                ).withFieldErrors()
            }
            return
        }
        mark {
            copy(
                phones = phones.map { r ->
                    if (r.rowKey == rowKey)
                        r.copy(number = number, error = null, notice = null)
                    else r
                },
                name = if (fillName) name.orEmpty().trim() else this.name
            ).withPhoneErrorsRecomputed()
        }
    }

    fun removePhone(rowKey: String) = mark {
        // 删除后行号变化：重算已标错行，错误不借位到其它行
        copy(phones = phones.filterNot { it.rowKey == rowKey })
            .withPhoneErrorsRecomputed()
    }

    fun updatePhone(rowKey: String, row: PhoneRowState) = mark {
        copy(phones = phones.map { r ->
            if (r.rowKey == rowKey) row.copy(rowKey = rowKey, error = null, notice = null)
            else r
        }).withPhoneErrorsRecomputed()
    }

    /** 主号码互斥：点选某行后其余行取消主号码。 */
    fun setPrimary(rowKey: String) = mark {
        copy(phones = phones.map { it.copy(isPrimary = it.rowKey == rowKey) })
    }

    /** 定位效果已被界面消费：清除行定位与字段定位标记。 */
    fun onFocusHandled() = _ui.update {
        it.copy(focusPhoneKey = null, focusPhoneKeyboard = false, focusTarget = null)
    }

    /** 计算第 i 行当前应有的错误文案；null 表示无错。行号指向另一重复行。 */
    private fun phoneErrorAt(phones: List<PhoneRowState>, i: Int): String? {
        val r = phones[i]
        if (r.number.isBlank()) {
            return if (r.needsNumberError()) "请填写号码，或删除这条电话" else null
        }
        val norm = io.github.xiaohaoz1.jiarenxia.data.normalizePhone(r.number)
        val dup = phones.indexOfFirst { other ->
            other !== r && other.number.isNotBlank() &&
                io.github.xiaohaoz1.jiarenxia.data.normalizePhone(other.number) == norm
        }
        return if (dup >= 0) "与第 " + (dup + 1) + " 条电话重复，请修改或删除一条"
        else null
    }

    /**
     * 只重算“已标错”的行：错误条件消失即清除、行号随位置刷新；
     * 不给本来无错的行主动标错（新增重复留到保存时统一标出）。
     */
    private fun PersonEditUiState.withPhoneErrorsRecomputed(): PersonEditUiState {
        if (phones.none { it.error != null }) return this
        return copy(phones = phones.mapIndexed { i, r ->
            if (r.error == null) r
            else {
                val err = phoneErrorAt(phones, i)
                if (err == r.error) r else r.copy(error = err)
            }
        })
    }

    /**
     * 保存前电话行校验：把全部行错误标出并返回第一处错误的 rowKey；
     * 完全空白的新行不算错误（保存时跳过）。无错误返回 null。
     */
    private fun markPhoneErrors(
        phones: List<PhoneRowState>
    ): Pair<List<PhoneRowState>, String?> {
        var first: String? = null
        val updated = phones.mapIndexed { i, r ->
            val err = phoneErrorAt(phones, i)
            if (err != null && first == null) first = r.rowKey
            if (err == r.error) r else r.copy(error = err)
        }
        return updated to first
    }

    fun onErrorShown() = _ui.update { it.copy(error = null) }

    private fun maxDay(month: Int, yearText: String): Int {
        val y = yearText.toIntOrNull()
        return if (y == null) java.time.Month.of(month).maxLength()
        else java.time.YearMonth.of(y, month).lengthOfMonth()
    }

    /** 该月该年是否允许闰月（农历）。 */
    fun leapAllowed(month: Int, year: Int?): Boolean =
        year != null && lunar.leapMonthOf(year) == month

    /** 农历某月天数上限；年份未知时按 30 上限。 */
    fun lunarDayMax(month: Int, leap: Boolean, year: Int?): Int =
        year?.let { lunar.daysInMonth(it, month, leap) } ?: 30

    /** 表单值按目标历法归一：日数夹取、失效闰月清除。 */
    private fun normalizeBirthday(
        f: BirthdayFormState, calendar: CalendarType
    ): BirthdayFormState {
        if (!f.on) return f.copy(leapMonth = false)
        val year = if (f.yearUnknown) null else f.yearText.toIntOrNull()
        return if (calendar == CalendarType.SOLAR) {
            f.copy(leapMonth = false, day = f.day.coerceAtMost(maxDay(f.month, f.yearText)))
        } else {
            val leap = f.leapMonth && (year == null || lunar.leapMonthOf(year) == f.month)
            val maxD = if (year == null) 30 else lunarDayMax(f.month, leap, year)
            f.copy(leapMonth = leap, day = f.day.coerceAtMost(maxD))
        }
    }

    /** 两份完整生日换算结果不一致 → birthdayConflict=true（提示核对，不自动覆盖）。 */
    private fun PersonEditUiState.recomputeConflict(): PersonEditUiState {
        val s = solarBirthday.toValue(CalendarType.SOLAR)
        val l = lunarBirthday.toValue(CalendarType.LUNAR)
        val conflict = s != null && l != null &&
            io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
                .resolveBirthDate(listOf(s, l), lunar).conflict
        return copy(birthdayConflict = conflict)
    }

    /** 称呼文本合法：trim 后 1–12 字（与 custom_titles 规则一致）。 */
    private fun titleOk(text: String) = text.trim().length in 1..12

    /**
     * 血缘细分是否已选完（兄妹角色专用）：
     * 多候选的共同父母必须“选定”或显式“不确定”，不能静默关联；
     * 堂/表必须选中间人，或明确退回“只记称呼”。
     */
    private fun lineageReady(st: PersonEditUiState): Boolean {
        val role = st.role ?: return false
        if (role !in RelativePlan.SIBLING_ROLES) return true
        fun sideReady(
            sideOn: Boolean,
            candidates: List<io.github.xiaohaoz1.jiarenxia.core.model.Person>,
            picked: String?, unsure: Boolean
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

    /**
     * 当前无效字段（与 canSave 同一套规则，声明顺序即页面顺序）：
     * 姓名、性别必填；NEW 模式依次要求关联人、称谓、血缘细分就绪。
     * 电话行错误单独按 rowKey 维护，不在此列。
     */
    fun invalidFields(st: PersonEditUiState = _ui.value): Set<PersonEditField> {
        val out = linkedSetOf<PersonEditField>()
        if (st.name.isBlank()) out += PersonEditField.NAME
        if (st.gender == null) out += PersonEditField.GENDER
        if (st.mode == PersonEditMode.NEW) {
            if (st.anchorId.isNullOrBlank()) out += PersonEditField.ANCHOR
            else if (st.role == null) out += PersonEditField.ROLE
            else if (!lineageReady(st)) out += PersonEditField.LINEAGE
        }
        return out
    }

    /** 当前可保存：无无效字段即可（电话行在 save() 内另行就地标错）。 */
    val canSave: Boolean
        get() = ui.value.let { !it.saving && invalidFields(it).isEmpty() }

    /** 确认的父 / 母侧祖辈 id（无补建返回 null）。 */
    private fun fatherLinkId(st: PersonEditUiState): String? = when {
        st.sharedParents?.fatherSide != true -> null
        st.fatherCandidates.size > 1 -> st.pickedFatherId   // 歧义：未选/不确定 → 不补
        else -> st.pickedFatherId?.takeIf { st.linkFather }
    }

    private fun motherLinkId(st: PersonEditUiState): String? = when {
        st.sharedParents?.motherSide != true -> null
        st.motherCandidates.size > 1 -> st.pickedMotherId
        else -> st.pickedMotherId?.takeIf { st.linkMother }
    }

    /**
     * NEW 模式的落库方案：落库规格（NEW_PERSON 占位）+ 可选自定义称呼。
     * 非兄妹角色退化为单条角色关系；称呼不参与血缘推断。
     */
    private fun buildPlan(
        st: PersonEditUiState
    ): Pair<List<RelationshipSpec>, String?> {
        val role = st.role ?: return emptyList<RelationshipSpec>() to null
        val anchor = st.anchorId ?: return emptyList<RelationshipSpec>() to null
        if (role !in RelativePlan.SIBLING_ROLES) {
            return listOf(role.toRelationshipSpec(NEW_PERSON, anchor)) to null
        }
        return when (st.lineage) {
            SiblingLineage.REAL ->
                RelativePlan.buildSpecs(
                    NEW_PERSON, anchor, role, SiblingLineage.REAL,
                    parentIds = listOfNotNull(fatherLinkId(st), motherLinkId(st))
                ) to null
            SiblingLineage.COUSIN -> {
                val hasMiddle = st.middleId != null && !st.middleNone
                // 申报堂表亲记录始终落库；无中间人时另记自定义称呼
                RelativePlan.buildSpecs(
                    NEW_PERSON, anchor, role, SiblingLineage.COUSIN,
                    middleId = st.middleId.takeIf { hasMiddle }
                ) to if (hasMiddle) null else st.titleOnlyText.trim()
            }
            SiblingLineage.TITLE_ONLY ->
                emptyList<RelationshipSpec>() to st.titleOnlyText.trim()
            // 未确认细分时不可达（canSave 拦截）；防御性不落血缘
            null -> emptyList<RelationshipSpec>() to null
        }
    }

    /**
     * 必填缺项（仅未满足时列出），供固定底栏展示“还差什么”——
     * 底栏摘要只做辅助，就地错误提示才是主要报错位置。
     * 电话的可选空行不算缺项。顺序即页面区块顺序。
     */
    fun missingRequirements(st: PersonEditUiState = _ui.value): List<String> =
        invalidFields(st).map {
            when (it) {
                PersonEditField.NAME -> "姓名"
                PersonEditField.GENDER -> "性别"
                PersonEditField.ANCHOR -> "关联人"
                PersonEditField.ROLE -> "称谓"
                PersonEditField.LINEAGE -> "血缘或称呼确认"
            }
        }

    /**
     * 标出全部无效字段并给出第一处的定位目标（按页面顺序）：
     * 保存被拒与缺项提示点击共用，NAME 顺带唤起键盘。
     */
    private fun PersonEditUiState.markInvalidFields(): PersonEditUiState {
        val invalid = invalidFields(this)
        return copy(
            saveAttempted = true,
            fieldErrors = invalid,
            focusTarget = invalid.firstOrNull()?.let {
                when (it) {
                    PersonEditField.NAME -> EditFocusTarget.NAME
                    PersonEditField.GENDER -> EditFocusTarget.GENDER
                    PersonEditField.ANCHOR -> EditFocusTarget.ANCHOR
                    PersonEditField.ROLE -> EditFocusTarget.ROLE
                    PersonEditField.LINEAGE -> EditFocusTarget.LINEAGE
                }
            }
        )
    }

    /** 缺项提示被点击：就地标出全部缺项并定位第一个。 */
    fun onMissingClick() {
        if (_ui.value.saving) return
        _ui.update { it.markInvalidFields() }
    }

    /**
     * 保存：先写人物/关系，再应用头像变更。
     * [continueAdd] 为“保存并继续添加”：人物照常落库，随后清空个人信息、
     * 保留关联人与血缘细分上下文，直接录下一位。
     */
    fun save(continueAdd: Boolean = false) {
        val st = _ui.value
        // 保存中/已完成：直接忽略重复点击，不能误入下方的缺项标记分支
        if (st.saving || st.done) return
        if (!canSave) {
            // 全部无效字段就地标错 + 电话行错误一并标出，定位页面第一个错误
            _ui.update { st2 ->
                st2.markInvalidFields()
                    .copy(phones = markPhoneErrors(st2.phones).first)
            }
            return
        }
        // 电话行校验：空白必填行/重复号码就地标错并定位第一处，不写库
        val (checkedPhones, firstPhoneErr) = markPhoneErrors(st.phones)
        if (firstPhoneErr != null) {
            _ui.update {
                it.copy(
                    phones = checkedPhones,
                    focusPhoneKey = firstPhoneErr,
                    focusPhoneKeyboard = false
                )
            }
            return
        }
        // 双生日冲突：先提示核对；用户再次保存视为确认（数据仍按两槽位原样写入，
        // 长幼/年龄对此人判未知而不是猜测）
        if (st.birthdayConflict && !st.conflictAcked) {
            _ui.update {
                it.copy(
                    conflictAcked = true,
                    error = "阳历与农历生日换算结果不一致，请核对；如确实如此请再点一次保存"
                )
            }
            return
        }
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val draft = PersonDraft(
                name = st.name,
                gender = st.gender!!,
                note = st.note.ifBlank { null },
                // “我”固定在世：创建/编辑“我”都不提供已故选项
                livingStatus = if (st.mode == PersonEditMode.SELF || st.isSelf) LivingStatus.ALIVE
                else if (st.deceased) LivingStatus.DECEASED else LivingStatus.ALIVE,
                birthdays = io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays(
                    solar = st.solarBirthday.toValue(CalendarType.SOLAR),
                    lunar = st.lunarBirthday.toValue(CalendarType.LUNAR)
                ),
                // 完全空白的新行不写入；其余行经上方校验后均为有效行
                phones = st.phones.filterNot { it.isBlankNew() }.map {
                    PhoneDraft(it.id, it.number, it.label, it.status, it.isPrimary, it.note)
                }
            )
            val result: FamilyResult<String> = when (st.mode) {
                PersonEditMode.SELF -> repository.createSelf(draft)
                PersonEditMode.NEW -> {
                    val (specs, title) = buildPlan(st)
                    repository.createRelative(draft, specs, title)
                }
                PersonEditMode.EDIT -> when (val r = repository.updatePerson(personId!!, draft)) {
                    is FamilyResult.Success -> FamilyResult.Success(personId)
                    is FamilyResult.Failure -> r
                }
            }
            when (result) {
                is FamilyResult.Failure -> _ui.update { it.copy(saving = false, error = result.error.message) }
                is FamilyResult.Success -> {
                    val pid = result.value
                    // 头像：保存成功后再应用（新选的优先于移除标记）
                    st.avatarUri?.let { repository.setAvatar(pid, it) }
                        ?: if (st.avatarRemoved) repository.removeAvatar(pid) else Unit
                    // 新增的是某人的父亲/母亲，且孩子另一位父母无配偶 → 询问记为夫妻
                    val ask = if (st.mode == PersonEditMode.NEW &&
                        (st.role == io.github.xiaohaoz1.jiarenxia.core.model
                            .RelationRole.FATHER ||
                            st.role == io.github.xiaohaoz1.jiarenxia.core.model
                                .RelationRole.MOTHER)
                    ) {
                        repository.coParentCandidate(pid)?.let { c ->
                            io.github.xiaohaoz1.jiarenxia.data.SpouseAsk(
                                pid, draft.name, c.id, c.name)
                        }
                    } else null
                    // 新增的是某人的丈夫/妻子：双方已录子女可能是共同子女 → 询问
                    val childAsk = if (st.mode == PersonEditMode.NEW &&
                        (st.role == io.github.xiaohaoz1.jiarenxia.core.model
                            .RelationRole.HUSBAND ||
                            st.role == io.github.xiaohaoz1.jiarenxia.core.model
                                .RelationRole.WIFE) && st.anchorId != null
                    ) {
                        repository.commonChildCandidates(st.anchorId, pid)
                            .takeIf { it.isNotEmpty() }
                            ?.let { cands ->
                                io.github.xiaohaoz1.jiarenxia.data.CommonChildrenAsk(
                                    spouseAId = st.anchorId,
                                    spouseAName = st.anchorName,
                                    spouseBId = pid,
                                    spouseBName = draft.name,
                                    candidates = cands,
                                    noticeName = draft.name)
                            }
                    } else if (st.mode == PersonEditMode.NEW && ask == null) {
                        // 堂表亲经中间人录入时也会创建亲子边，以保存后的真实图判断。
                        io.github.xiaohaoz1.jiarenxia.data.ParentCompletion.ask(
                            repository.observeFamilyGraph().first(), pid, draft.name)
                    } else null
                    val hasAsk = ask != null || childAsk != null
                    val continuing = continueAdd && st.mode == PersonEditMode.NEW
                    if (continuing) {
                        continueAfterAsk = hasAsk
                        if (!hasAsk) resetForNext(draft.name)
                        else _ui.update {
                            it.copy(saving = false, spouseAsk = ask,
                                commonChildrenAsk = childAsk)
                        }
                    } else {
                        _ui.update {
                            it.copy(saving = false, done = !hasAsk,
                                spouseAsk = ask, commonChildrenAsk = childAsk)
                        }
                    }
                }
            }
        }
    }

    /** “记为夫妻”询问关闭后，是回到上页还是进入下一位录入。 */
    private var continueAfterAsk = false

    /**
     * 连续添加：清空下一位的姓名/性别/在世/生日/手机号/备注/头像，
     * 保留关联人、角色与血缘细分选择（分支上下文）。
     */
    private fun resetForNext(savedName: String?) {
        _ui.update {
            it.copy(
                saving = false, dirty = false, done = false, spouseAsk = null,
                commonChildrenAsk = null,
                name = "", gender = it.role?.gender, deceased = false,
                // 下一位是全新表单：错误标出与定位从头再来
                saveAttempted = false, fieldErrors = emptySet(),
                focusTarget = null,
                solarBirthday = BirthdayFormState(),
                lunarBirthday = BirthdayFormState(), birthdayConflict = false,
                conflictAcked = false,
                phones = emptyList(), note = "",
                avatarPath = null, avatarUri = null, avatarRemoved = false,
                // 称呼文本属上一位：清空后由 refreshLineage 按当前称谓重取默认
                titleOnlyText = "", titleTouched = false,
                savedNotice = savedName?.let { n -> "已保存「" + n + "」" }
            )
        }
        // 新人物入库后关系图已变化：重算候选（共同父母/中间人选择保留）
        viewModelScope.launch { refreshLineage() }
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
                ask.newPersonId, ask.candidateId,
                io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.HUSBAND)
            if (r is FamilyResult.Failure) {
                _ui.update { it.copy(error = r.error.message) }
                return@launch
            }
            val children = repository.commonChildCandidates(
                ask.newPersonId, ask.candidateId)
            if (children.isEmpty()) {
                finishAsks(ask.newPersonName)
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
                                candidates = children,
                                noticeName = ask.newPersonName)
                    )
                }
            }
        }
    }

    /** 询问选“不是”：不建关系，直接关闭流程。 */
    fun dismissSpouseAsk() {
        finishAsks(_ui.value.spouseAsk?.newPersonName)
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
            finishAsks(ask.noticeName)
        }
    }

    /** “共同子女”选“都不是”：不建关系，直接关闭流程。 */
    fun dismissCommonChildren() {
        finishAsks(_ui.value.commonChildrenAsk?.noticeName)
    }

    /** 询问全部关闭后的收尾：连续添加进入下一位录入，否则返回上页。 */
    private fun finishAsks(savedName: String?) {
        if (continueAfterAsk) {
            continueAfterAsk = false
            resetForNext(savedName)
        } else {
            _ui.update {
                it.copy(spouseAsk = null, commonChildrenAsk = null, done = true)
            }
        }
    }
}
