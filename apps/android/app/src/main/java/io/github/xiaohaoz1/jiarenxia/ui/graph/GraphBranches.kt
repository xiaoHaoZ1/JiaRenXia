package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview

/**
 * 关系图分支筛选（DEVELOPMENT_PLAN 2.11 图谱页）：以“我”为观察者的
 * 关系路径**第一步**把人分到父系 / 母系 / 配偶方 / 兄弟姐妹 / 子孙五条支线。
 * 与“我”无路径的（本人、未关联者）不属于任何分支。
 *
 * 配偶方再按路径第二步细分：第二步是配偶的父/母时，此人归入
 * “配偶父系”/“配偶母系”，其余（配偶本人、配偶的兄弟姐妹及其
 * 后代）留在“配偶方”。三条配偶支线统称“配偶一系”（SPOUSE_SIDE）。
 */
enum class GraphBranch(val label: String) {
    ALL("全部"),
    FATHER("父系"),
    MOTHER("母系"),
    SPOUSE("配偶方"),
    SPOUSE_FATHER("配偶父系"),
    SPOUSE_MOTHER("配偶母系"),
    SIBLINGS("兄弟姐妹"),
    DESCENDANTS("子孙")
}

object GraphBranches {

    /** 配偶一系的三条支线（“全部”视图里只保留配偶本人）。 */
    private val SPOUSE_SIDE = setOf(
        GraphBranch.SPOUSE, GraphBranch.SPOUSE_FATHER, GraphBranch.SPOUSE_MOTHER
    )

    /**
     * 人物所属分支：看 relationshipPath 的第一步。
     * - PARENT → 该步人物男性为父系、女性为母系；
     * - SPOUSE → 路径只有一步是配偶本人（配偶方）；第二步是配偶的
     *   父/母时分别归配偶父系/配偶母系，其余仍在配偶方；
     * - SIBLING / CHILD → 兄弟姐妹 / 子孙；
     * - 空路径（本人或尚未关联）→ null。
     */
    fun branchOf(ov: PersonOverview, byId: Map<String, PersonOverview>): GraphBranch? {
        val path = ov.result.relationshipPath
        val first = path.firstOrNull() ?: return null
        return when (first.type) {
            RelationshipType.PARENT ->
                if (byId[first.personId]?.person?.gender == Gender.MALE) GraphBranch.FATHER
                else GraphBranch.MOTHER
            RelationshipType.SPOUSE -> {
                val second = path.getOrNull(1)
                if (second?.type == RelationshipType.PARENT) {
                    if (byId[second.personId]?.person?.gender == Gender.MALE)
                        GraphBranch.SPOUSE_FATHER
                    else GraphBranch.SPOUSE_MOTHER
                } else {
                    GraphBranch.SPOUSE
                }
            }
            RelationshipType.SIBLING -> GraphBranch.SIBLINGS
            // 申报堂表亲（无真实路径）归入兄弟姐妹支线
            RelationshipType.COUSIN -> GraphBranch.SIBLINGS
            RelationshipType.CHILD -> GraphBranch.DESCENDANTS
        }
    }

    /** 配偶本人：到“我”的路径仅一步 SPOUSE。 */
    private fun isSpouse(ov: PersonOverview): Boolean {
        val path = ov.result.relationshipPath
        return path.size == 1 && path[0].type == RelationshipType.SPOUSE
    }

    /**
     * [branch] 视图应显示的人物 id 集合（关系图与分享圈选预设共用）：
     * - “全部”收起配偶一系、只留配偶本人（配偶全家在配偶方/配偶父系/配偶母系看）；
     * - “配偶方”是配偶全家视图：三条配偶支线（含配偶本人）+ 夫妻双方共同的子女；
     * - “配偶父系/配偶母系”保留配偶本人作锚点；
     * - 其余分支只显示本支线的人；“我”在任何视图都在。
     */
    fun visibleIds(overview: FamilyOverview, branch: GraphBranch): Set<String> {
        val selfId = overview.self.person.id
        val sharedKids =
            if (branch == GraphBranch.SPOUSE) sharedChildrenIds(overview)
            else emptySet()
        return overview.byId.values.mapNotNull { ov ->
            val id = ov.person.id
            val b = branchOf(ov, overview.byId)
            val shown = when {
                id == selfId || id in sharedKids -> true
                b == null -> false
                branch == GraphBranch.ALL -> b !in SPOUSE_SIDE || isSpouse(ov)
                branch == GraphBranch.SPOUSE -> b in SPOUSE_SIDE
                branch == GraphBranch.SPOUSE_FATHER ||
                    branch == GraphBranch.SPOUSE_MOTHER ->
                    b == branch || isSpouse(ov)
                else -> b == branch
            }
            if (shown) id else null
        }.toSet()
    }

    /** 夫妻双方共同的子女 id（“配偶方”视图附带显示）。 */
    private fun sharedChildrenIds(overview: FamilyOverview): Set<String> {
        val byId = overview.byId
        val spouseIds = byId.values.filter { isSpouse(it) }
            .map { it.person.id }.toSet()
        if (spouseIds.isEmpty()) return emptySet()
        val selfId = overview.self.person.id
        return overview.graph.relationships.mapNotNull { r ->
            if (r.type == RelationshipType.PARENT &&
                r.sourcePersonId in spouseIds &&
                r.targetPersonId != selfId &&
                r.targetPersonId in byId
            ) r.targetPersonId else null
        }.filter { id ->
            byId[id]?.let { branchOf(it, byId) == GraphBranch.DESCENDANTS } == true
        }.toSet()
    }

    /**
     * 按分支筛选参与布局的世代表：保留 [visibleIds] 里的人。
     */
    fun filter(
        generations: Map<String, Int>,
        overview: FamilyOverview,
        branch: GraphBranch
    ): Map<String, Int> {
        val visible = visibleIds(overview, branch)
        return generations.filterKeys { it in visible }
    }

    /** 有成员的分支（枚举序），ALL 恒在首位。 */
    fun available(overview: FamilyOverview): List<GraphBranch> =
        GraphBranch.entries.filter {
            it == GraphBranch.ALL ||
                overview.byId.values.any { ov -> branchOf(ov, overview.byId) == it }
        }
}
