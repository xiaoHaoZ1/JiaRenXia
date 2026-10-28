package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.CoreGroup
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import java.text.Collator
import java.util.Locale

/**
 * 家庭页排序规则（DEVELOPMENT_PLAN 2.11 一）。
 * 纯函数，可单元测试。姓名比较使用中文 Collator。
 */
object FamilyOrdering {

    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    private val groupRank = mapOf(
        CoreGroup.PARENTS to 0,
        CoreGroup.GRANDPARENTS to 1,
        CoreGroup.SPOUSE to 2,
        CoreGroup.CHILDREN to 3,
        CoreGroup.SPOUSE_PARENTS to 4
    )

    /** 核心家人分组序号（选择器等共用排序基准）。 */
    fun groupRankOf(g: CoreGroup): Int = groupRank[g] ?: 99

    private fun nameCompare(a: PersonOverview, b: PersonOverview): Int =
        collator.compare(a.person.name, b.person.name)

    private fun maleRank(o: PersonOverview): Int = if (o.person.gender == Gender.MALE) 0 else 1

    /**
     * 核心家人排序：组序 父母 > 祖辈 > 配偶 > 子女 > 配偶父母；
     * 组内规则见各组实现；最终平手按姓名。
     */
    fun core(
        items: List<PersonOverview>,
        byId: Map<String, PersonOverview>
    ): List<PersonOverview> = items.sortedWith { a, b ->
        val ga = groupRank[a.result.coreGroup] ?: 99
        val gb = groupRank[b.result.coreGroup] ?: 99
        if (ga != gb) return@sortedWith ga - gb
        val within = when (a.result.coreGroup) {
            CoreGroup.PARENTS, CoreGroup.SPOUSE_PARENTS -> maleRank(a) - maleRank(b)
            CoreGroup.GRANDPARENTS -> compareGrandparents(a, b, byId)
            CoreGroup.CHILDREN -> compareChildren(a, b)
            else -> 0
        }
        if (within != 0) within else nameCompare(a, b)
    }

    /** 祖辈：父系（路径第一步为男性=父亲一方）在前，同侧男性在前。 */
    private fun compareGrandparents(
        a: PersonOverview,
        b: PersonOverview,
        byId: Map<String, PersonOverview>
    ): Int {
        val sideA = paternalSideRank(a, byId)
        val sideB = paternalSideRank(b, byId)
        if (sideA != sideB) return sideA - sideB
        return maleRank(a) - maleRank(b)
    }

    private fun paternalSideRank(o: PersonOverview, byId: Map<String, PersonOverview>): Int {
        val mid = o.result.relationshipPath.firstOrNull()?.personId ?: return 1
        return if (byId[mid]?.person?.gender == Gender.MALE) 0 else 1
    }

    /**
     * 子女：完整生日（年长者在前）→ 兄弟姐妹排行（小者在前，有记录优先
     * 于未记录）→ 录入时间。排行只在生日缺席/相同时定先后。
     */
    private fun compareChildren(a: PersonOverview, b: PersonOverview): Int {
        val da = a.solarBirthday?.takeIf { it.isComplete }?.toLocalDateOrNull()
        val db = b.solarBirthday?.takeIf { it.isComplete }?.toLocalDateOrNull()
        if ((da != null) != (db != null)) return if (da != null) -1 else 1
        if (da != null && db != null && da != db) return da.compareTo(db)
        val ra = a.person.siblingRank; val rb = b.person.siblingRank
        if (ra != null && rb != null && ra != rb) return ra - rb
        if ((ra != null) != (rb != null)) return if (ra != null) -1 else 1
        return (a.person.createdAt - b.person.createdAt).let {
            if (it < 0) -1 else if (it > 0) 1 else 0
        }
    }

    /**
     * 把 core() 排好序的核心家人切成两列网格的行：每个分组都从
     * 新的一行开始，成对的人（父母、祖辈、配偶父母等）同排，
     * 不被上一组的奇数人数顶错位；组内单出的人右格留空。
     */
    fun coreRows(core: List<PersonOverview>): List<List<PersonOverview>> =
        core.groupBy { it.result.coreGroup }
            .entries
            .sortedBy { it.key?.let(::groupRankOf) ?: 99 }
            .flatMap { it.value.chunked(2) }

    /**
     * 其他家人：已关联者在前（按路径长度，再按姓名），未关联者在最后按姓名。
     */
    fun others(items: List<PersonOverview>): List<PersonOverview> =
        items.sortedWith { a, b ->
            val ua = a.result.kind == KinshipKind.UNRELATED
            val ub = b.result.kind == KinshipKind.UNRELATED
            if (ua != ub) return@sortedWith if (ua) 1 else -1
            if (!ua) {
                val c = a.result.relationshipPath.size - b.result.relationshipPath.size
                if (c != 0) return@sortedWith c
            }
            nameCompare(a, b)
        }
}
