package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.db.toModel
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOrdering
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.ShareImport
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar

/**
 * 碰一碰“合心”展示的新家人名单（FAMILY_AFFAIRS 第 10 节）：
 * 在合并后的内存关系图上，从“我”的视角推导对方家人的新称谓
 * （妻子/丈夫、岳父/岳母、公公/婆婆…），配偶在前。
 */
object TapReveal {

    /** 飘进半颗心的一位新家人。 */
    data class Label(
        val personId: String,
        val title: String,
        val name: String
    )

    /**
     * 计算合心名单：[local] 本机快照、[plan] 合并计划（未落库也可算）。
     * 称谓 = 标准称谓，退化到结构描述。
     */
    fun labels(
        local: SpouseMerge.Snapshot,
        plan: SpouseMerge.MergePlan,
        myId: String,
        engine: KinshipEngine,
        lunar: LunarCalendar
    ): List<Label> {
        // 合并后的内存图：本机现有 + 计划新增（id 已按 idMap 重写）
        val persons = local.persons + plan.newPersons
        val relationships = local.relationships + plan.newRelationships
        val dates = local.dates + plan.newDates
        val graph = ShareImport.graphOf(persons, relationships, dates, lunar)
        val results = engine.resolveAll(graph, myId)

        // 配偶 = 合并后关系里与“我”相连的那条 SPOUSE 的另一端
        val spouseId = relationships.firstOrNull {
            it.type == RelationshipType.SPOUSE &&
                (it.sourcePersonId == myId || it.targetPersonId == myId)
        }?.let {
            if (it.sourcePersonId == myId) it.targetPersonId
            else it.sourcePersonId
        }

        val candidates = buildSet {
            spouseId?.let { add(it) }
            plan.newPersons.forEach { add(it.id) }
        }
        val overviews = candidates.mapNotNull { id ->
            val person = persons.firstOrNull { it.id == id } ?: return@mapNotNull null
            val result = results[id] ?: return@mapNotNull null
            if (result.kind == KinshipKind.UNRELATED ||
                result.kind == KinshipKind.SELF
            ) return@mapNotNull null
            PersonOverview(person.toModel(), result)
        }

        // 配偶在最前；其余按家庭页规则：核心家人 → 其他家人
        val (spouseOv, rest) = overviews.partition { it.person.id == spouseId }
        val core = FamilyOrdering.core(
            rest.filter { it.result.coreGroup != null },
            overviews.associateBy { it.person.id })
        val others = FamilyOrdering.others(
            rest.filter { it.result.coreGroup == null })
        val ordered = spouseOv + core + others
        return ordered.map {
            Label(
                personId = it.person.id,
                title = it.result.standardTitle
                    ?: it.result.structuralDescription,
                name = it.person.name
            )
        }
    }
}
