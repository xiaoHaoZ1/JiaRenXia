package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview

/** 家庭图的六个有界范围；只决定入图成员，称谓观察者始终保留为“我”。 */
object GraphFamilyScope {
    val tabs = listOf(GraphBranch.ALL, GraphBranch.FATHER, GraphBranch.MOTHER,
        GraphBranch.SPOUSE, GraphBranch.SPOUSE_FATHER, GraphBranch.SPOUSE_MOTHER)

    /** 图谱入口文案，与分享圈选原有的分支分类独立。 */
    fun labelOf(branch: GraphBranch): String = when (branch) {
        GraphBranch.ALL -> "我的家庭"
        GraphBranch.SPOUSE -> "配偶系"
        else -> branch.label
    }

    private fun spouses(graph: FamilyGraph, id: String): List<String> =
        graph.relationships.filter { it.type == RelationshipType.SPOUSE }
            .mapNotNull { when (id) {
                it.sourcePersonId -> it.targetPersonId
                it.targetPersonId -> it.sourcePersonId
                else -> null
            } }.distinct().sorted()

    private fun siblings(graph: FamilyGraph, id: String): Set<String> {
        val result = graph.relationships.filter { it.type == RelationshipType.SIBLING }
            .mapNotNull { when (id) {
                it.sourcePersonId -> it.targetPersonId
                it.targetPersonId -> it.sourcePersonId
                else -> null
            } }.toMutableSet()
        GraphStructure.parentsOf(graph, id).forEach { parent ->
            result += GraphStructure.childrenOf(graph, parent)
        }
        result -= id
        return result
    }

    /** 范围锚点：本人、父母、配偶或配偶父母；不推测缺失的亲属。 */
    fun anchorOf(overview: FamilyOverview, branch: GraphBranch): String? {
        val graph = overview.graph
        val self = overview.self.person.id
        fun parent(id: String?, gender: Gender) = id?.let {
            GraphStructure.parentsOf(graph, it).sorted().firstOrNull { p ->
                overview.byId[p]?.person?.gender == gender
            }
        }
        val partner = spouses(graph, self).firstOrNull { it in overview.byId }
        return when (branch) {
            GraphBranch.ALL, GraphBranch.SIBLINGS, GraphBranch.DESCENDANTS -> self
            GraphBranch.FATHER -> parent(self, Gender.MALE)
            GraphBranch.MOTHER -> parent(self, Gender.FEMALE)
            GraphBranch.SPOUSE -> partner
            GraphBranch.SPOUSE_FATHER -> parent(partner, Gender.MALE)
            GraphBranch.SPOUSE_MOTHER -> parent(partner, Gender.FEMALE)
        }
    }

    /**
     * 祖父母只保留直系；父母及其亲手足各支向下三代，再补齐成员配偶。
     * 不沿配偶再引入其原生家庭，也不改变任何原始人物、关系或称谓。
     */
    fun visibleIds(overview: FamilyOverview, branch: GraphBranch): Set<String> {
        val self = overview.self.person.id
        val anchor = anchorOf(overview, branch) ?: return setOf(self)
        val graph = overview.graph
        val blood = linkedSetOf(anchor, self)
        val parents = GraphStructure.parentsOf(graph, anchor)
        val hand = siblings(graph, anchor)
        blood += hand
        blood += parents
        blood += parents.flatMap { GraphStructure.parentsOf(graph, it) }
        val roots = (parents + parents.flatMap { siblings(graph, it) }).distinct()
        blood += roots
        fun descendants(seeds: Collection<String>, depth: Int) {
            var level = seeds.toSet()
            repeat(depth) {
                level = level.flatMap { GraphStructure.childrenOf(graph, it) }.toSet()
                blood += level
            }
        }
        descendants(roots, 3)
        // 父母尚未录入但已明确记录手足时，也保留这几家的已知后代。
        descendants(hand + anchor, 2)
        val visible = blood.toMutableSet()
        blood.forEach { visible += spouses(graph, it) }
        return visible.filterTo(linkedSetOf()) { it in overview.byId }
    }

    /** 当前资料中有明确范围锚点的入口。 */
    fun available(overview: FamilyOverview): List<GraphBranch> =
        tabs.filter { anchorOf(overview, it) != null }

    /** 家支定位锚点；“我的家庭”不提供，夫妻重复锚点只保留一个。 */
    fun familyRoots(overview: FamilyOverview, branch: GraphBranch): List<String> {
        if (branch == GraphBranch.ALL) return emptyList()
        val anchor = anchorOf(overview, branch) ?: return emptyList()
        val graph = overview.graph
        val visible = visibleIds(overview, branch)
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        GraphStructure.parentsOf(graph, anchor).sortedWith(
            compareBy<String> { overview.byId[it]?.person?.gender != Gender.MALE }
                .thenBy { it }
        ).flatMap { listOf(it) + siblings(graph, it).sorted() }.forEach { id ->
            if (id in visible && seen.add(id)) {
                result += id
                seen += spouses(graph, id)
            }
        }
        return result
    }
}
