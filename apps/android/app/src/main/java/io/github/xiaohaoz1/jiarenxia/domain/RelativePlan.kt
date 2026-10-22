package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder

/**
 * 称谓录入中“新人物”在计划规格里的占位 id。
 * 数据层在事务内先建好人物，再把规格里的占位替换成真实 id。
 */
const val NEW_PERSON = "__new_person__"

/**
 * 兄妹类称谓的血缘细分（DEVELOPMENT_PLAN 2.14）。
 * 称呼不推断血缘：选了“姑姑”不代表她一定是爸爸的亲姐妹，必须由用户显式选择。
 */
enum class SiblingLineage {
    /** 亲兄弟姐妹：与关联人有共同父母（再确认共享哪几位）。 */
    REAL,

    /** 堂 / 表兄弟姐妹：关联人的父母的兄弟姐妹的子女，按真实路径关联。 */
    COUSIN,

    /** 其他关系或只记称呼：不建关系，只写观察者=“我”的自定义称呼。 */
    TITLE_ONLY
}

/**
 * 共同父母确认（REAL 分支）。
 * 称呼不能推出“同父同母”：亲姐妹也可能只同父或只同母，必须用户确认。
 */
enum class SharedParents(
    val fatherSide: Boolean,
    val motherSide: Boolean,
    val label: String
) {
    BOTH(true, true, "同父同母"),
    FATHER(true, false, "同父"),
    MOTHER(false, true, "同母"),
    UNKNOWN(false, false, "暂不确定")
}

/** 堂 / 表分支的中间人候选：关联人的父母的兄弟姐妹。 */
data class MiddleCandidate(
    val person: Person,
    /** 来源说明，如“杨福生的哥哥”；同一人是两位父母的兄妹时用“；”连接。 */
    val viaLabel: String
)

/**
 * 称谓录入的领域规则（DEVELOPMENT_PLAN 2.14）。
 * 首页“添加家人”与详情页“添加关系”共用同一套规则，保证两处一致。
 */
object RelativePlan {

    /** 兄妹六个角色（带长幼的四个 + 长幼未知两个）。 */
    val SIBLING_ROLES: Set<RelationRole> = setOf(
        RelationRole.ELDER_BROTHER, RelationRole.YOUNGER_BROTHER,
        RelationRole.ELDER_SISTER, RelationRole.YOUNGER_SISTER,
        RelationRole.BROTHER, RelationRole.SISTER
    )

    /**
     * 角色在“我”的视角下的称谓标签（DEVELOPMENT_PLAN 2.14 用户视角选称谓）。
     *
     * @param anchorAsParent 关联人是“我”的父 / 母时为对应性别，否则 null
     *        （由调用方按 relationshipPath == [PARENT] 判断）。非空时整句
     *        选项都按“TA 是我的：……”翻译；null 时回落到锚点视角的通用标签。
     */
    fun roleLabel(role: RelationRole, anchorAsParent: Gender?): String =
        when (anchorAsParent) {
            Gender.MALE -> when (role) {
                RelationRole.FATHER -> "爷爷"
                RelationRole.MOTHER -> "奶奶"
                RelationRole.SON -> "兄弟（长幼未知）"
                RelationRole.DAUGHTER -> "姐妹（长幼未知）"
                RelationRole.HUSBAND -> "继父"
                RelationRole.WIFE -> "继母"
                RelationRole.ELDER_BROTHER -> "伯伯"
                RelationRole.YOUNGER_BROTHER -> "叔叔"
                RelationRole.BROTHER -> "伯叔（长幼未知）"
                RelationRole.ELDER_SISTER -> "姑姑（姐姐）"
                RelationRole.YOUNGER_SISTER -> "姑姑（妹妹）"
                RelationRole.SISTER -> "姑姑（长幼未知）"
            }
            Gender.FEMALE -> when (role) {
                RelationRole.FATHER -> "姥爷"
                RelationRole.MOTHER -> "姥姥"
                RelationRole.SON -> "兄弟（长幼未知）"
                RelationRole.DAUGHTER -> "姐妹（长幼未知）"
                RelationRole.HUSBAND -> "继父"
                RelationRole.WIFE -> "继母"
                RelationRole.ELDER_BROTHER -> "舅舅（哥哥）"
                RelationRole.YOUNGER_BROTHER -> "舅舅（弟弟）"
                RelationRole.BROTHER -> "舅舅（长幼未知）"
                RelationRole.ELDER_SISTER -> "姨妈（姐姐）"
                RelationRole.YOUNGER_SISTER -> "姨妈（妹妹）"
                RelationRole.SISTER -> "姨妈（长幼未知）"
            }
            else -> role.label
        }

    /** “只记称呼”的默认称呼：不带长幼限定的家庭称呼，用户可改。 */
    fun baseAppellation(role: RelationRole, anchorAsParent: Gender?): String =
        when (anchorAsParent) {
            Gender.MALE -> when (role) {
                RelationRole.FATHER -> "爷爷"
                RelationRole.MOTHER -> "奶奶"
                RelationRole.SON -> "兄弟"
                RelationRole.DAUGHTER -> "姐妹"
                RelationRole.HUSBAND -> "继父"
                RelationRole.WIFE -> "继母"
                RelationRole.ELDER_BROTHER -> "伯伯"
                RelationRole.YOUNGER_BROTHER -> "叔叔"
                RelationRole.BROTHER -> "伯叔"
                RelationRole.ELDER_SISTER,
                RelationRole.YOUNGER_SISTER,
                RelationRole.SISTER -> "姑姑"
            }
            Gender.FEMALE -> when (role) {
                RelationRole.FATHER -> "姥爷"
                RelationRole.MOTHER -> "姥姥"
                RelationRole.SON -> "兄弟"
                RelationRole.DAUGHTER -> "姐妹"
                RelationRole.HUSBAND -> "继父"
                RelationRole.WIFE -> "继母"
                RelationRole.ELDER_BROTHER,
                RelationRole.YOUNGER_BROTHER,
                RelationRole.BROTHER -> "舅舅"
                RelationRole.ELDER_SISTER,
                RelationRole.YOUNGER_SISTER,
                RelationRole.SISTER -> "姨妈"
            }
            else -> role.label.substringBefore("（")
        }

    /** anchor 作为 target 的 PARENT 记录 source（其已录父母）。 */
    fun parentIdsOf(rels: List<Relationship>, anchorId: String): List<String> =
        rels.filter {
            it.type == RelationshipType.PARENT && it.targetPersonId == anchorId
        }.map { it.sourcePersonId }

    /** anchor 的指定性别父母候选（共同父母补全用，0–2 位）。 */
    fun parentCandidates(
        graph: FamilyGraph, anchorId: String, gender: Gender
    ): List<Person> {
        val ids = parentIdsOf(graph.relationships, anchorId).toSet()
        return graph.persons.filter { it.id in ids && it.gender == gender }
    }

    /**
     * 堂 / 表分支中间人：anchor 的父母的兄弟姐妹
     * （显式 SIBLING，或共享父母的隐式兄妹），按人物去重。
     */
    fun middleCandidates(graph: FamilyGraph, anchorId: String): List<MiddleCandidate> {
        val byId = graph.persons.associateBy { it.id }
        val parentIds = parentIdsOf(graph.relationships, anchorId)
        val out = LinkedHashMap<String, MiddleCandidate>()
        for (pid in parentIds) {
            val parent = byId[pid] ?: continue
            for (sibId in siblingIdsOf(graph, pid)) {
                if (sibId == anchorId || sibId in parentIds) continue
                val sib = byId[sibId] ?: continue
                val via = parent.name + "的" +
                    siblingLabel(graph, sibId, pid)
                out.merge(sibId, MiddleCandidate(sib, via)) { a, b ->
                    a.copy(viaLabel = a.viaLabel + "；" + b.viaLabel)
                }
            }
        }
        return out.values.toList()
    }

    /** personId 的兄弟姐妹 id：显式 SIBLING + 共享至少一位父母的隐式兄妹。 */
    private fun siblingIdsOf(graph: FamilyGraph, personId: String): Set<String> {
        val out = LinkedHashSet<String>()
        for (r in graph.relationships) {
            if (r.type != RelationshipType.SIBLING) continue
            if (r.sourcePersonId == personId) out += r.targetPersonId
            if (r.targetPersonId == personId) out += r.sourcePersonId
        }
        val parents = parentIdsOf(graph.relationships, personId).toSet()
        if (parents.isNotEmpty()) {
            for (r in graph.relationships) {
                if (r.type == RelationshipType.PARENT &&
                    r.sourcePersonId in parents &&
                    r.targetPersonId != personId
                ) out += r.targetPersonId
            }
        }
        return out
    }

    /**
     * sib 相对 ref 的兄妹称呼：先比双方完整生日，再读显式 relativeAgeOrder，
     * 都无法确定时只分性别（兄弟 / 姐妹）。与引擎 directRelationLabel 同规则。
     */
    private fun siblingLabel(
        graph: FamilyGraph, sibId: String, refId: String
    ): String {
        val sib = graph.persons.firstOrNull { it.id == sibId } ?: return "亲戚"
        val male = sib.gender == Gender.MALE
        var order = PartialDate.compareSeniority(
            graph.birthdays[sibId], graph.birthdays[refId])
        if (order == null) {
            val rel = graph.relationships.firstOrNull {
                it.type == RelationshipType.SIBLING &&
                    ((it.sourcePersonId == sibId && it.targetPersonId == refId) ||
                        (it.sourcePersonId == refId && it.targetPersonId == sibId))
            }
            order = rel?.relativeAgeOrder?.let {
                if (rel.sourcePersonId == sibId) it else it.inverse()
            }
        }
        return when (order) {
            RelativeAgeOrder.OLDER -> if (male) "哥哥" else "姐姐"
            RelativeAgeOrder.YOUNGER -> if (male) "弟弟" else "妹妹"
            RelativeAgeOrder.SAME -> if (male) "兄弟（长幼相同）" else "姐妹（长幼相同）"
            null -> if (male) "兄弟" else "姐妹"
        }
    }

    /**
     * 生成落库规格：[personId] 为待关联人物（新建时传 [NEW_PERSON] 占位）。
     *
     * - REAL → 兄妹关系（长幼按所选角色）+ [parentIds] 中每位已确认共同父母
     *   各一条 PARENT；只补已录祖辈，不凭空创建。
     * - COUSIN → “中间人是 TA 的父母”（有中间人时）+ 两人间的申报 COUSIN
     *   记录（携带所选角色的长幼）；申报记录不参与路径遍历，
     *   引擎堂表称谓长幼优先读它（KINSHIP_ENGINE 37.1 修订）。
     * - TITLE_ONLY → 空列表，称呼由调用方走 custom_titles 写入。
     */
    fun buildSpecs(
        personId: String,
        anchorId: String,
        role: RelationRole,
        lineage: SiblingLineage,
        parentIds: List<String> = emptyList(),
        middleId: String? = null
    ): List<RelationshipSpec> = when (lineage) {
        SiblingLineage.REAL ->
            listOf(role.toRelationshipSpec(personId, anchorId)) +
                parentIds.distinct().map {
                    RelationshipSpec(it, personId, RelationshipType.PARENT, null)
                }
        SiblingLineage.COUSIN -> listOfNotNull(middleId).map {
            RelationshipSpec(it, personId, RelationshipType.PARENT, null)
        } + cousinSpec(personId, anchorId, role)
        SiblingLineage.TITLE_ONLY -> emptyList()
    }

    /** 堂/表分支的申报记录：两人是堂表亲，携带所选角色的长幼（“长幼未知”为 null）。 */
    fun cousinSpec(personId: String, anchorId: String, role: RelationRole): RelationshipSpec =
        role.toRelationshipSpec(personId, anchorId).copy(type = RelationshipType.COUSIN)
}
