package io.github.xiaohaoz1.jiarenxia.ui.addfamily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * “添加家人”快捷入口（DEVELOPMENT_PLAN 2.3 选项表）。
 *
 * @param anchorId 固定锚点 id（唯一候选时使用）
 * @param anchorCandidates 锚点候选 id 列表；仅当锚点可选且被限定候选时非空
 * @param roles 可选关系选项
 * @param enabled 禁用（缺前置人物）时置灰
 * @param hint 禁用原因文案，如“请先添加父亲”
 * @param hintRoles 点击提示按钮时跳转表单的角色（如 FATHER）
 * @param hintAnchorId 提示跳转表单的锚点（通常为“我”）
 */
data class QuickEntry(
    val label: String,
    val anchorId: String = "",
    val anchorCandidates: List<String> = emptyList(),
    val roles: List<RelationRole> = emptyList(),
    val enabled: Boolean = true,
    val hint: String? = null,
    val hintRoles: List<RelationRole> = emptyList(),
    val hintAnchorId: String = "",
    /** “更多亲戚”分区（普通模式才显示；该分区排在最后）。 */
    val moreSection: Boolean = false,
    /** 永远垫底的条目（其他亲属）：不属于“更多亲戚”分区，onboarding 也显示。 */
    val trailing: Boolean = false
) {
    val roleNames: List<String> get() = roles.map { it.name }
}

data class AddFamilyUiState(
    val loading: Boolean = true,
    val entries: List<QuickEntry> = emptyList()
)

@HiltViewModel
class AddFamilyViewModel @Inject constructor(
    observeOverview: ObserveFamilyOverview
) : ViewModel() {

    val uiState: StateFlow<AddFamilyUiState> = observeOverview()
        .map { overview ->
            if (overview == null) AddFamilyUiState(loading = false)
            else AddFamilyUiState(
                loading = false, entries = buildQuickEntries(overview)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AddFamilyUiState())

}

/**
 * 快捷入口构建（纯函数，可单测）：配偶/祖辈按对合并成一张卡；
 * “更多亲戚”分区的锚点按 relationshipPath 类型链定位（隐式兄弟姐妹同样算数）。
 */
internal fun buildQuickEntries(
    overview: io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
): List<QuickEntry> {
    val self = overview.self.person
    val rels = overview.graph.relationships
    val byId = overview.byId

    val parents = rels.filter {
        it.type == RelationshipType.PARENT && it.targetPersonId == self.id
    }.mapNotNull { byId[it.sourcePersonId]?.person }
    val maleParents = parents.filter { it.gender == Gender.MALE }
    val femaleParents = parents.filter { it.gender == Gender.FEMALE }
    val spouse = rels.filter {
        it.type == RelationshipType.SPOUSE &&
            (it.sourcePersonId == self.id || it.targetPersonId == self.id)
    }.map {
        if (it.sourcePersonId == self.id) it.targetPersonId else it.sourcePersonId
    }.mapNotNull { byId[it]?.person }.firstOrNull()

    fun hasParentOf(personId: String, gender: Gender): Boolean =
        rels.any {
            it.type == RelationshipType.PARENT && it.targetPersonId == personId &&
                byId[it.sourcePersonId]?.person?.gender == gender
        }

    val entries = mutableListOf<QuickEntry>()

    if (maleParents.isEmpty()) {
        entries += QuickEntry("父亲", anchorId = self.id, roles = listOf(RelationRole.FATHER))
    }
    if (femaleParents.isEmpty()) {
        entries += QuickEntry("母亲", anchorId = self.id, roles = listOf(RelationRole.MOTHER))
    }
    if (spouse == null) {
        entries += QuickEntry("配偶", anchorId = self.id,
            roles = listOf(RelationRole.HUSBAND, RelationRole.WIFE))
    }
    entries += QuickEntry("子女", anchorId = self.id,
        roles = listOf(RelationRole.SON, RelationRole.DAUGHTER))
    entries += QuickEntry("兄弟姐妹", anchorId = self.id,
        roles = listOf(
            RelationRole.ELDER_BROTHER, RelationRole.YOUNGER_BROTHER,
            RelationRole.ELDER_SISTER, RelationRole.YOUNGER_SISTER,
            RelationRole.BROTHER, RelationRole.SISTER
        ))

    /** 祖辈对（爷爷奶奶 / 姥爷姥姥）：anchors 为指定性别的父母，尚缺父或母者可选。 */
    fun grandparentPair(
        label: String,
        anchors: List<io.github.xiaohaoz1.jiarenxia.core.model.Person>,
        missingParentHint: String,
        missingParentRole: RelationRole
    ) {
        if (anchors.isEmpty()) {
            entries += QuickEntry(label, enabled = false, hint = missingParentHint,
                hintRoles = listOf(missingParentRole), hintAnchorId = self.id)
            return
        }
        // 尚缺祖辈的父母 → 可选锚点；缺的角色 → 表单角色项
        val candidates = anchors.filter {
            !hasParentOf(it.id, Gender.MALE) || !hasParentOf(it.id, Gender.FEMALE)
        }
        if (candidates.isEmpty()) return   // 两方祖辈都已存在 → 隐藏
        val anchor = candidates.first()
        val roles = buildList {
            if (!hasParentOf(anchor.id, Gender.MALE)) add(RelationRole.FATHER)
            if (!hasParentOf(anchor.id, Gender.FEMALE)) add(RelationRole.MOTHER)
        }
        entries += QuickEntry(
            label,
            anchorId = candidates.singleOrNull()?.id ?: "",
            anchorCandidates = if (candidates.size > 1) candidates.map { it.id } else emptyList(),
            roles = roles
        )
    }
    grandparentPair("爷爷奶奶", maleParents, "请先添加父亲", RelationRole.FATHER)
    grandparentPair("姥爷姥姥", femaleParents, "请先添加母亲", RelationRole.MOTHER)

    // 配偶的父母（称谓随 self 与配偶性别变化）
    val pairLabel = when {
        self.gender == Gender.MALE && spouse?.gender == Gender.FEMALE -> "岳父岳母"
        self.gender == Gender.FEMALE && spouse?.gender == Gender.MALE -> "公公婆婆"
        else -> "配偶的父母"
    }
    if (spouse == null) {
        entries += QuickEntry(pairLabel, enabled = false, hint = "请先添加配偶",
            hintRoles = listOf(RelationRole.HUSBAND, RelationRole.WIFE), hintAnchorId = self.id)
    } else {
        val roles = buildList {
            if (!hasParentOf(spouse.id, Gender.MALE)) add(RelationRole.FATHER)
            if (!hasParentOf(spouse.id, Gender.FEMALE)) add(RelationRole.MOTHER)
        }
        if (roles.isNotEmpty()) {
            entries += QuickEntry(pairLabel, anchorId = spouse.id, roles = roles)
        }
    }

    // —— 更多亲戚（普通模式的分区；锚点按称谓路径类型定位，隐式兄弟姐妹同样算数）——
    val sibRoles = listOf(
        RelationRole.ELDER_BROTHER, RelationRole.YOUNGER_BROTHER,
        RelationRole.ELDER_SISTER, RelationRole.YOUNGER_SISTER,
        RelationRole.BROTHER, RelationRole.SISTER
    )
    /** 称谓路径类型链恰好等于 path 的人物 id（不含“我”）。 */
    fun idsAtPath(path: List<RelationshipType>): List<String> =
        byId.keys.filter { id ->
            id != self.id &&
                byId.getValue(id).result.relationshipPath.map { it.type } == path
        }
    fun moreEntry(
        label: String, anchors: List<String>, roles: List<RelationRole>,
        hint: String,
        hintRoles: List<RelationRole> = emptyList(),
        hintAnchorId: String = ""
    ) {
        entries += if (anchors.isEmpty()) {
            QuickEntry(label, enabled = false, hint = hint,
                hintRoles = hintRoles, hintAnchorId = hintAnchorId, moreSection = true)
        } else {
            QuickEntry(label,
                anchorId = anchors.singleOrNull() ?: "",
                anchorCandidates = if (anchors.size > 1) anchors else emptyList(),
                roles = roles, moreSection = true)
        }
    }
    val sonDaughter = listOf(RelationRole.SON, RelationRole.DAUGHTER)
    moreEntry("伯叔姑", maleParents.map { it.id }, sibRoles,
        "请先添加父亲", listOf(RelationRole.FATHER), self.id)
    moreEntry("舅舅姨妈", femaleParents.map { it.id }, sibRoles,
        "请先添加母亲", listOf(RelationRole.MOTHER), self.id)
    moreEntry("堂表兄弟姐妹",
        idsAtPath(listOf(RelationshipType.PARENT, RelationshipType.SIBLING)),
        sonDaughter, "请先添加伯叔姑或舅舅姨妈")
    moreEntry("侄子外甥",
        idsAtPath(listOf(RelationshipType.SIBLING)),
        sonDaughter, "请先添加兄弟姐妹", sibRoles, self.id)
    moreEntry("儿媳女婿",
        idsAtPath(listOf(RelationshipType.CHILD)),
        listOf(RelationRole.HUSBAND, RelationRole.WIFE),
        "请先添加子女", sonDaughter, self.id)
    moreEntry("孙辈",
        idsAtPath(listOf(RelationshipType.CHILD)),
        sonDaughter, "请先添加子女", sonDaughter, self.id)
    moreEntry("祖辈的兄弟姐妹",
        idsAtPath(listOf(RelationshipType.PARENT, RelationshipType.PARENT)),
        sibRoles,
        "请先添加爷爷奶奶或姥爷姥姥")
    moreEntry("父母的堂表亲",
        idsAtPath(listOf(RelationshipType.PARENT, RelationshipType.PARENT,
            RelationshipType.SIBLING)),
        sonDaughter, "请先添加祖辈的兄弟姐妹")
    moreEntry("配偶的兄弟姐妹",
        idsAtPath(listOf(RelationshipType.SPOUSE)),
        sibRoles, "请先添加配偶",
        listOf(RelationRole.HUSBAND, RelationRole.WIFE), self.id)
    moreEntry("配偶的叔伯姑姨",
        idsAtPath(listOf(RelationshipType.SPOUSE, RelationshipType.PARENT)),
        sibRoles,
        if (spouse != null) "请先添加配偶的父母" else "请先添加配偶",
        if (spouse != null) listOf(RelationRole.FATHER, RelationRole.MOTHER)
        else listOf(RelationRole.HUSBAND, RelationRole.WIFE),
        if (spouse != null) spouse.id else self.id)

    entries += QuickEntry("其他亲属", anchorId = "", anchorCandidates = emptyList(),
        roles = RelationRole.entries.toList(), trailing = true)

    return entries
}
