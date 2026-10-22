package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.PathStep
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview

/** 为图谱中的远房亲属保留实际关系路径，避免五步推导上限误显示“未关联”。 */
object GraphFamilyTitles {
    /**
     * 固定以“我”做一次线性最短路搜索；不新增标准称谓，只补结构描述。
     * 已有标准称谓、自定义称呼以及不连通人物都保持原样。
     */
    fun resolve(overview: FamilyOverview): FamilyOverview {
        val self = overview.self.person.id
        val graph = overview.graph
        val adjacency = overview.byId.keys.associateWith { mutableListOf<PathStep>() }
        fun add(from: String, to: String, type: RelationshipType, implicit: Boolean = false) {
            if (from != to && to in overview.byId) adjacency[from]?.add(PathStep(type, to, implicit))
        }
        for (edge in graph.relationships) {
            val a = edge.sourcePersonId
            val b = edge.targetPersonId
            when (edge.type) {
                RelationshipType.PARENT -> {
                    add(a, b, RelationshipType.CHILD); add(b, a, RelationshipType.PARENT)
                }
                RelationshipType.CHILD -> {
                    add(a, b, RelationshipType.PARENT); add(b, a, RelationshipType.CHILD)
                }
                RelationshipType.SPOUSE, RelationshipType.SIBLING -> {
                    add(a, b, edge.type); add(b, a, edge.type)
                }
                RelationshipType.COUSIN -> Unit
            }
        }
        for (parent in overview.byId.keys) {
            val kids = GraphStructure.childrenOf(graph, parent)
            for (a in kids) for (b in kids) add(a, b, RelationshipType.SIBLING, true)
        }
        val paths = mutableMapOf<String, List<PathStep>>(self to emptyList())
        val queue = ArrayDeque<String>()
        queue.add(self)
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (step in adjacency[from].orEmpty().distinct().sortedWith(
                compareBy<PathStep> { it.personId }.thenBy { it.type.ordinal }.thenBy { it.implicit }
            )) {
                if (step.personId !in paths) {
                    paths[step.personId] = paths.getValue(from) + step
                    queue.add(step.personId)
                }
            }
        }
        fun word(step: PathStep): String {
            val male = overview.byId[step.personId]?.person?.gender == Gender.MALE
            return when (step.type) {
                RelationshipType.PARENT -> if (male) "父亲" else "母亲"
                RelationshipType.CHILD -> if (male) "儿子" else "女儿"
                RelationshipType.SPOUSE -> if (male) "丈夫" else "妻子"
                RelationshipType.SIBLING -> if (male) "兄弟" else "姐妹"
                RelationshipType.COUSIN -> "堂表亲"
            }
        }
        val updated = overview.byId.mapValues { (id, person) ->
            val result = person.result
            val path = if (result.kind == KinshipKind.UNRELATED) paths[id] else result.relationshipPath
            if (path.isNullOrEmpty() || result.standardTitle != null || id == self) person
            else {
                val prefix = path.indices.lastOrNull { index ->
                    overview.byId[path[index].personId]?.result?.standardTitle != null
                }
                val parts = if (prefix == null) path.map(::word) else
                    listOf(requireNotNull(overview.byId[path[prefix].personId]?.result?.standardTitle)) +
                        path.drop(prefix + 1).map(::word)
                val description = parts.joinToString("的")
                person.copy(result = result.copy(
                    relationshipPath = path,
                    structuralDescription = if (result.kind == KinshipKind.UNRELATED ||
                        description.length < result.structuralDescription.length) description
                        else result.structuralDescription,
                    kind = KinshipKind.STRUCTURAL,
                    confidence = if (result.kind == KinshipKind.UNRELATED) Confidence.EXACT else result.confidence,
                    missingInformation = if (result.kind == KinshipKind.UNRELATED) emptySet() else result.missingInformation
                ))
            }
        }
        return overview.copy(self = updated.getValue(self), byId = updated,
            core = overview.core.map { updated.getValue(it.person.id) },
            others = overview.others.map { updated.getValue(it.person.id) })
    }
}
