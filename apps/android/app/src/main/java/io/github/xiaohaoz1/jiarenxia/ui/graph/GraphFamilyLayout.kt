package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import kotlin.math.max

/** 分代家庭图：同代不折行，夫妻成块，完整后代宽度决定各家所占空间。 */
object GraphFamilyLayout {
    private const val GAP = 30f

    private class Tree(val block: FamilyBlock, val width: Float) {
        var parent: Tree? = null
        val children = mutableListOf<Tree>()
        var span = width
        var x = 0f
    }

    /** 在原始称谓、卡片测量与连线路由上重排几何，不修改领域关系。 */
    fun build(
        overview: FamilyOverview,
        generations: Map<String, Int>,
        sizer: GraphCardSizer = GraphCardSizer.Default
    ): GraphModel {
        val base = GraphLayout.build(overview, generations,
            visibleIds = generations.keys, sizer = sizer)
        if (base.nodes.isEmpty()) return base
        val original = base.nodes.associateBy { it.personId }
        val trees = base.blocks.map { b ->
            Tree(b, b.memberIds.sumOf { original.getValue(it).w.toDouble() }.toFloat() +
                GraphGeom.MEMBER_GAP * (b.memberIds.size - 1))
        }
        val ofPerson = trees.flatMap { t -> t.block.memberIds.map { it to t } }.toMap()
        val graph = overview.graph
        val self = overview.self.person.id
        val adjacency = GraphStructure.adjacency(graph)
        val reachable = mutableSetOf(self)
        val queue = ArrayDeque<String>()
        queue.add(self)
        while (queue.isNotEmpty()) {
            adjacency[queue.removeFirst()].orEmpty().forEach { id ->
                if (reachable.add(id)) queue.add(id)
            }
        }
        for (t in trees) {
            // 夫妻双方都有原生家庭时优先本人的出生家庭；其余跨家族连接仍全部保留。
            val candidates = t.block.memberIds.flatMap { member ->
                GraphStructure.parentsOf(graph, member).mapNotNull { parent ->
                    ofPerson[parent]?.takeIf {
                        it !== t && it.block.generation == t.block.generation - 1
                    }?.let { member to it }
                }
            }.sortedWith(compareBy<Pair<String, Tree>> { it.first != self }
                .thenBy { overview.byId[it.first]?.person?.gender != Gender.MALE }
                .thenBy { it.second.block.id })
            candidates.firstOrNull()?.second?.let { parent ->
                t.parent = parent
                parent.children += t
            }
        }
        fun name(t: Tree) = overview.byId[t.block.memberIds.first()]?.person?.name.orEmpty()
        // 子树的血亲成员：其父母落在父块里的人；找不到退化到首成员
        fun blood(t: Tree): String {
            val p = t.parent ?: return t.block.memberIds.first()
            val ids = p.block.memberIds.toSet()
            return t.block.memberIds.firstOrNull { m ->
                GraphStructure.parentsOf(graph, m).any { it in ids } }
                ?: t.block.memberIds.first()
        }
        fun birthKey(id: String): Int {
            val d = overview.byId[id]?.solarBirthday ?: return Int.MAX_VALUE
            return (d.year ?: return Int.MAX_VALUE) * 10000 + d.month * 100 + d.day
        }
        fun rankKey(id: String) =
            overview.byId[id]?.person?.siblingRank ?: Int.MAX_VALUE
        // 子代/同胞家庭排序信号：完整生日 → 排行 → 姓名 → id
        // （生日客观最强；生日缺失时名次定先后，长者在左）
        val childCmp = compareBy<Tree> { birthKey(blood(it)) }
            .thenBy { rankKey(blood(it)) }
            .thenBy { name(it) }.thenBy { it.block.id }
        val sibCmp = compareBy<String>(::birthKey).thenBy(::rankKey)
            .thenBy { overview.byId[it]?.person?.name.orEmpty() }
        fun width(t: Tree): Float {
            t.children.sortWith(childCmp)
            val childrenWidth = t.children.sumOf { width(it).toDouble() }.toFloat() +
                GAP * (t.children.size - 1).coerceAtLeast(0)
            t.span = max(t.width, childrenWidth)
            return t.span
        }
        fun place(t: Tree, left: Float) {
            t.x = left + (t.span - t.width) / 2f
            val childrenWidth = t.children.sumOf { it.span.toDouble() }.toFloat() +
                GAP * (t.children.size - 1).coerceAtLeast(0)
            var cursor = left + (t.span - childrenWidth) / 2f
            for (child in t.children) {
                place(child, cursor)
                cursor += child.span + GAP
            }
        }
        val roots = trees.filter { it.parent == null }
        roots.forEach(::width)
        var main = ofPerson[self] ?: trees.first()
        while (main.parent != null) main = requireNotNull(main.parent)
        place(main, -main.span / 2f)
        var left = -main.span / 2f - GraphGeom.CHANNEL
        var right = main.span / 2f + GraphGeom.CHANNEL
        val others = roots.filter { it !== main }.sortedWith(
            compareBy<Tree> { it.block.side.ordinal }.thenBy { name(it) }.thenBy { it.block.id })
        for (root in others) {
            if (root.block.side == GraphSide.LEFT) {
                place(root, left - root.span)
                left -= root.span + GraphGeom.CHANNEL
            } else {
                place(root, right)
                right += root.span + GraphGeom.CHANNEL
            }
        }
        // 小家庭优先把双方祖辈放在父母两侧，叔伯姑舅的整家放到对应侧边。
        // 大家族仍按完整树宽度排列，不为了塞进一屏折行或压缩卡片。
        if (trees.size <= 18) {
            val own = ofPerson.getValue(self)
            fun parents(id: String) = GraphStructure.parentsOf(graph, id)
                .filter { it in ofPerson }
            fun siblings(id: String): Set<String> =
                (parents(id).flatMap { GraphStructure.childrenOf(graph, it) } +
                    graph.relationships.filter { it.type == RelationshipType.SIBLING }
                        .mapNotNull { edge -> when (id) {
                            edge.sourcePersonId -> edge.targetPersonId
                            edge.targetPersonId -> edge.sourcePersonId
                            else -> null
                        } }).filterTo(linkedSetOf()) { it != id && it in ofPerson }
            fun familyUnits(ids: Collection<String>) = ids.mapNotNull { ofPerson[it] }.distinct()
            val origin = own.block.memberIds.firstOrNull { it == self && parents(it).isNotEmpty() }
                ?: own.block.memberIds.firstOrNull { parents(it).isNotEmpty() } ?: self
            val done = mutableSetOf<Tree>()
            fun mark(t: Tree) { done += t; t.children.forEach(::mark) }
            fun subtree(t: Tree, x: Float) { place(t, x); mark(t) }
            fun center(t: Tree, x: Float) { t.x = x - t.width / 2f; done += t }
            subtree(own, -own.span / 2f)
            var innerLeft = -own.span / 2f - GAP
            var innerRight = own.span / 2f + GAP
            // 本人同胞在左：先放最幼（贴着本人），长者排最左
            familyUnits(siblings(origin).sortedWith(sibCmp).asReversed())
                .forEach { t ->
                if (t !in done) {
                    subtree(t, innerLeft - t.span)
                    innerLeft -= t.span + GAP
                }
            }
            val parentIds = parents(origin)
            val parentUnits = familyUnits(parentIds)
            val parentWidth = parentUnits.sumOf { it.width.toDouble() }.toFloat() +
                GAP * (parentUnits.size - 1).coerceAtLeast(0)
            var parentCursor = -parentWidth / 2f
            parentUnits.forEach { t -> center(t, parentCursor + t.width / 2f); parentCursor += t.width + GAP }
            for (parent in parentIds) {
                val leftSide = overview.byId[parent]?.person?.gender == Gender.MALE
                // 左侧（父系）倒序摆放、右侧（母系）正序，统一"长者在左、幼者在右"
                val sibTrees = familyUnits(siblings(parent).sortedWith(sibCmp)
                    .let { if (leftSide) it.asReversed() else it })
                sibTrees.forEach { t ->
                    if (t !in done) {
                        if (leftSide) {
                            subtree(t, innerLeft - t.span)
                            innerLeft -= t.span + GAP
                        } else {
                            subtree(t, innerRight)
                            innerRight += t.span + GAP
                        }
                    }
                }
            }
            fun personX(id: String): Float {
                val t = ofPerson.getValue(id)
                var x = t.x
                for (member in t.block.memberIds) {
                    val n = original.getValue(member)
                    if (member == id) return x + n.w / 2f
                    x += n.w + GraphGeom.MEMBER_GAP
                }
                return t.x + t.width / 2f
            }
            val grandparents = linkedMapOf<Tree, MutableList<Float>>()
            for (parent in parentIds) {
                val family = listOf(parent) + siblings(parent)
                val target = family.map(::personX).average().toFloat()
                familyUnits(parents(parent)).filter { it !in done }.forEach { t ->
                    grandparents.getOrPut(t) { mutableListOf() }.add(target)
                }
            }
            var previousRight = Float.NEGATIVE_INFINITY
            grandparents.entries.sortedBy { it.value.average() }.forEach { (t, xs) ->
                val x = max(xs.average().toFloat(), previousRight + GAP + t.width / 2f)
                center(t, x)
                previousRight = x + t.width / 2f
            }
            var extra = (done.maxOfOrNull { it.x + it.width } ?: 0f) + GraphGeom.CHANNEL
            trees.sortedWith(compareBy<Tree> { it.block.generation }.thenBy { name(it) })
                .forEach { t -> if (t !in done) { subtree(t, extra); extra += t.span + GAP } }
        }
        val bands = mutableListOf<GraphBand>()
        var top = GraphGeom.EDGE
        for (gen in generations.values.distinct().sorted()) {
            val height = base.nodes.filter { it.generation == gen }.maxOfOrNull { it.h }
                ?: continue
            bands += GraphBand(gen, top, 1, listOf(top), listOf(height))
            top += height + GraphGeom.CHANNEL
        }
        val bandOf = bands.associateBy { it.generation }
        val nodes = trees.flatMap { t ->
            var x = t.x
            t.block.memberIds.map { id ->
                original.getValue(id).copy(x = x,
                    y = bandOf.getValue(t.block.generation).top).also {
                    x += it.w + GraphGeom.MEMBER_GAP
                }
            }
        }.sortedWith(compareBy<GraphNode> { it.y }.thenBy { it.x }.thenBy { it.personId })
        val columns = trees.groupBy { it.block.generation }.values.flatMap { row ->
            row.sortedBy { it.x }.mapIndexed { index, tree -> tree.block.id to index }
        }.toMap()
        return base.copy(
            unconnectedCount = graph.persons.count { it.id !in reachable },
            nodes = nodes, bands = bands,
            blocks = trees.map { t -> t.block.copy(row = 0,
                column = columns.getValue(t.block.id), corridorX = t.x - GAP / 2f) },
            collapsedCount = 0, expandChips = emptyList(),
            groupLabels = emptyList(), laneMarks = emptyList()
        )
    }
}
