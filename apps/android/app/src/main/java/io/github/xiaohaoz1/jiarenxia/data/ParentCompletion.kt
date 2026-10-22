package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType

/** 只为已录单亲的孩子推荐另一位父母；配偶关系本身不代表亲子关系，必须再确认。 */
object ParentCompletion {
    /** 依据真实父母、配偶边生成单个孩子的询问；缺失人物或多候选均不猜测。 */
    fun ask(graph: FamilyGraph, childId: String, noticeName: String? = null): CommonChildrenAsk? {
        val people = graph.persons.associateBy { it.id }
        val child = people[childId] ?: return null
        val parentIds = graph.relationships.mapNotNull { edge ->
            when {
                edge.type == RelationshipType.PARENT && edge.targetPersonId == childId ->
                    edge.sourcePersonId
                edge.type == RelationshipType.CHILD && edge.sourcePersonId == childId ->
                    edge.targetPersonId
                else -> null
            }
        }.toSet()
        val parentId = parentIds.singleOrNull() ?: return null
        if (parentId == childId) return null
        val parent = people[parentId] ?: return null
        val partnerIds = graph.relationships.filter { it.type == RelationshipType.SPOUSE }
            .mapNotNull { edge ->
                when (parentId) {
                    edge.sourcePersonId -> edge.targetPersonId
                    edge.targetPersonId -> edge.sourcePersonId
                    else -> null
                }
            }.toSet()
        val partnerId = partnerIds.singleOrNull() ?: return null
        if (partnerId == childId || partnerId == parentId) return null
        val partner = people[partnerId] ?: return null
        if (partner.gender == parent.gender) return null
        return CommonChildrenAsk(
            spouseAId = parentId,
            spouseAName = parent.name,
            spouseBId = partnerId,
            spouseBName = partner.name,
            candidates = listOf(CommonChildCandidate(
                child, parentId, if (child.gender == Gender.MALE) "儿子" else "女儿")),
            noticeName = noticeName,
            parentLabel = if (partner.gender == Gender.MALE) "父亲" else "母亲"
        )
    }
}
