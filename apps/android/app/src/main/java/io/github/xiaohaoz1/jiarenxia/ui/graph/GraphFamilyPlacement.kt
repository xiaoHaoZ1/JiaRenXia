package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * 按原生家庭排列夫妻块，再沿真实亲子关系对齐上下代。
 * 左右归属继续使用全图结构；同一区内部不再用 BFS 距离混排父系、母系。
 * 这里只计算坐标，不补写父母、配偶或兄弟姐妹关系。
 */
internal object GraphFamilyPlacement {
    private const val COLUMNS = 4

    data class Result(
        val x: Map<String, Float>, val y: Map<String, Float>,
        val blocks: List<FamilyBlock>, val bands: List<GraphBand>
    )

    fun place(
        overview: FamilyOverview, generations: Map<String, Int>,
        visible: Set<String>, blocks: List<GraphStructure.Block>,
        assignment: Map<String, GraphStructure.Assignment>,
        anchors: GraphStructure.Anchors,
        widths: Map<String, Float>, heights: Map<String, Float>
    ): Result {
        val graph = overview.graph
        val collator = Collator.getInstance(Locale.CHINA)
        val blockByPerson = blocks.flatMap { b -> b.memberIds.map { it to b } }.toMap()
        val ancestors = mutableSetOf<String>()
        val queue = ArrayDeque(anchors.ids)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!ancestors.add(id)) continue
            GraphStructure.parentsOf(graph, id).forEach(queue::addLast)
        }
        fun distance(id: String) = assignment[id]?.dist ?: Int.MAX_VALUE
        fun priority(id: String) = when (id) {
            in anchors.ids -> 0
            in ancestors -> 1
            else -> 2
        }
        val personOrder = Comparator<String> { a, b ->
            fun birthday(id: String): Int {
                val d = overview.byId[id]?.solarBirthday ?: return Int.MAX_VALUE
                return (d.year ?: return Int.MAX_VALUE) * 10000 + d.month * 100 + d.day
            }
            compareValues(priority(a), priority(b)).takeIf { it != 0 }
                ?: compareValues(distance(a), distance(b)).takeIf { it != 0 }
                ?: compareValues(birthday(a), birthday(b)).takeIf { it != 0 }
                ?: collator.compare(overview.byId[a]?.person?.name ?: a,
                    overview.byId[b]?.person?.name ?: b).takeIf { it != 0 }
                ?: a.compareTo(b)
        }
        fun link(b: GraphStructure.Block) = b.memberIds.minWith(personOrder)
        fun rankOf(id: String) = overview.byId[id]?.person?.siblingRank
        fun width(b: GraphStructure.Block) = b.memberIds.sumOf {
            (widths[it] ?: GraphGeom.CARD_MIN_W).toDouble()
        }.toFloat() + GraphGeom.MEMBER_GAP * (b.memberIds.size - 1)
        fun height(b: GraphStructure.Block) = b.memberIds.maxOf {
            heights[it] ?: GraphGeom.CARD_MIN_H
        }
        fun side(b: GraphStructure.Block): GraphSide {
            if (b.memberIds.any { it in anchors.ids }) return GraphSide.CENTER
            return b.memberIds.minWith(compareBy<String> { distance(it) }
                .thenBy { assignment[it]?.side?.ordinal ?: 1 })
                .let { assignment[it]?.side ?: GraphSide.CENTER }
        }
        val sideByBlock = blocks.associate { it.id to side(it) }
        val anchor = blocks.firstOrNull { anchors.selfId in it.memberIds }

        // 同胞组来自已记录的共同父母或直接兄弟姐妹边，配偶不参与合并。
        val component = generations.keys.associateWith { it }.toMutableMap()
        fun root(id: String): String {
            var x = id
            while (component[x] != null && component[x] != x) x = component.getValue(x)
            return x
        }
        fun unite(a: String, b: String) {
            if (a !in component || b !in component || generations[a] != generations[b]) return
            val ra = root(a); val rb = root(b)
            if (ra != rb) component[rb] = ra
        }
        val children = graph.persons.associate { it.id to GraphStructure.childrenOf(graph, it.id) }
        for (ids in children.values) for (id in ids.drop(1)) unite(ids.first(), id)
        for (r in graph.relationships) if (r.type == RelationshipType.SIBLING)
            unite(r.sourcePersonId, r.targetPersonId)
        val siblingGroups = generations.keys.groupBy(::root).values.filter { it.size > 1 }

        // 同胞组内排行：两边都有名次且不同时按名次（1=老大在左）。
        // 只在同一组（同 root）内生效，不跨支系混排。
        fun kinOrder(a: GraphStructure.Block, b: GraphStructure.Block): Int {
            for (ma in a.memberIds) for (mb in b.memberIds) {
                if (ma != mb && root(ma) == root(mb)) {
                    val ra = rankOf(ma); val rb = rankOf(mb)
                    if (ra != null && rb != null && ra != rb) return ra - rb
                }
            }
            return personOrder.compare(link(a), link(b))
        }

        // 每个同胞组以离主家庭最近的一人为接口：男成员接左侧原生家庭，
        // 女成员接右侧原生家庭。父亲和母亲的夫妻块因此成为两组之间的接口。
        val precedes = mutableSetOf<Pair<String, String>>()
        for (group in siblingGroups) {
            val members = group.filter { it in blockByPerson }
            val pivot = members.minWithOrNull(personOrder) ?: continue
            val pivotBlock = blockByPerson.getValue(pivot)
            val others = members.mapNotNull { blockByPerson[it] }.distinctBy { it.id }
                .filter { it.id != pivotBlock.id }
            val male = overview.byId[pivot]?.person?.gender == Gender.MALE
            for (b in others) {
                if (sideByBlock[b.id] != sideByBlock[pivotBlock.id]) continue
                precedes += if (male) b.id to pivotBlock.id else pivotBlock.id to b.id
            }
        }

        val x = mutableMapOf<String, Float>()
        val placed = mutableMapOf<String, FamilyBlock>()
        val rowHeights = mutableMapOf<Int, List<Float>>()
        val rowLabels = mutableMapOf<Int, List<String>>()

        fun seniority(b: GraphStructure.Block): Int {
            if (b.id == anchor?.id) return 0
            val person = link(b)
            val ref = if (anchors.partnerId == null) anchors.selfId else anchors.ids.firstOrNull {
                val male = overview.byId[it]?.person?.gender == Gender.MALE
                male == (sideByBlock[b.id] == GraphSide.LEFT)
            } ?: anchors.selfId
            val direct = graph.relationships.firstOrNull {
                it.type in listOf(RelationshipType.SIBLING, RelationshipType.COUSIN) &&
                    setOf(it.sourcePersonId, it.targetPersonId) == setOf(person, ref)
            }
            val declared = direct?.relativeAgeOrder?.let {
                if (direct.sourcePersonId == person) it else it.inverse()
            }
            val birthday = PartialDate.compareSeniority(
                overview.byId[person]?.solarBirthday, overview.byId[ref]?.solarBirthday)
            // 排行作第三信号：生日与成对记录都缺时，名次定同代长幼层
            val rank = rankOf(person)?.let { rp ->
                rankOf(ref)?.let { rr ->
                    if (rp < rr) RelativeAgeOrder.OLDER
                    else if (rp > rr) RelativeAgeOrder.YOUNGER else null
                }
            }
            val order = if (direct?.type == RelationshipType.COUSIN) declared ?: birthday
                else birthday ?: declared ?: rank
            return when (order) {
                RelativeAgeOrder.OLDER -> -1
                RelativeAgeOrder.YOUNGER -> 1
                else -> 0
            }
        }
        fun center(id: String) = x[id]?.plus((widths[id] ?: GraphGeom.CARD_MIN_W) / 2f)
        fun middle(xs: List<Float>) = (xs.min() + xs.max()) / 2f
        fun desired(b: GraphStructure.Block): Float {
            if (b.id == anchor?.id) return 0f
            if (b.generation < 0) {
                val knownChildren = b.memberIds.flatMap { children[it].orEmpty() }
                    .distinct().filter { generations[it] == b.generation + 1 }
                    .mapNotNull(::center)
                if (knownChildren.isNotEmpty()) return middle(knownChildren)
            } else {
                // 配偶有各自原生家庭时，按离主家庭近的成员依附，不把两家混成一支。
                for (id in b.memberIds.sortedWith(personOrder)) {
                    val knownParents = GraphStructure.parentsOf(graph, id)
                        .filter { generations[it] == b.generation - 1 }.mapNotNull(::center)
                    if (knownParents.isNotEmpty()) return middle(knownParents)
                }
            }
            var cursor: String? = assignment[link(b)]?.treeParent
            val seen = mutableSetOf<String>()
            while (cursor != null && seen.add(cursor)) {
                center(cursor)?.let { return it }
                cursor = assignment[cursor]?.treeParent
            }
            return 0f
        }

        fun ordered(items: List<GraphStructure.Block>): List<GraphStructure.Block> {
            val remaining = items.associateBy { it.id }.toMutableMap()
            val result = mutableListOf<GraphStructure.Block>()
            // 主夫妻同代先遵守男方左、女方右的归属，再按各自父母的位置
            // 排列支内家庭；不能为了缩短连线让男方表亲越过主夫妻。
            fun origin(b: GraphStructure.Block): Float {
                if (b.generation >= 0) for (id in b.memberIds.sortedWith(personOrder)) {
                    val ps = GraphStructure.parentsOf(graph, id)
                        .filter { generations[it] == b.generation - 1 }.mapNotNull(::center)
                    if (ps.isNotEmpty()) return middle(ps)
                }
                return desired(b)
            }
            val comparator = compareBy<GraphStructure.Block> {
                if (it.generation == 0) sideByBlock.getValue(it.id).ordinal else 0
            }.thenBy { origin(it) }
                .thenComparator { a, b -> kinOrder(a, b) }
            while (remaining.isNotEmpty()) {
                val ready = remaining.values.filter { b ->
                    precedes.none { (a, c) -> c == b.id && a in remaining }
                }
                // 近亲婚配或矛盾输入可能产生排序环；稳定降级，所有人和边仍保留。
                val next = (ready.ifEmpty { remaining.values.toList() }).minWith(comparator)
                result += next; remaining.remove(next.id)
            }
            return result
        }

        /** 最小二乘紧凑排列：在不重叠的前提下，尽量保持每列在自己的父母下方。 */
        fun pack(columnWidths: List<Float>, targets: List<Float>): List<Float> {
            val prefix = mutableListOf<Float>()
            var cursor = 0f
            for (w in columnWidths) { prefix += cursor; cursor += w + GraphGeom.BLOCK_GAP }
            data class Pool(val first: Int, val last: Int, val sum: Float, val count: Int) {
                val mean get() = sum / count
            }
            val pools = mutableListOf<Pool>()
            for (i in columnWidths.indices) {
                pools += Pool(i, i, targets[i] - columnWidths[i] / 2 - prefix[i], 1)
                while (pools.size > 1 && pools[pools.lastIndex - 1].mean > pools.last().mean) {
                    val b = pools.removeAt(pools.lastIndex); val a = pools.removeAt(pools.lastIndex)
                    pools += Pool(a.first, b.last, a.sum + b.sum, a.count + b.count)
                }
            }
            val lefts = FloatArray(columnWidths.size)
            for (p in pools) for (i in p.first..p.last) lefts[i] = prefix[i] + p.mean
            return lefts.toList()
        }

        /** 晚辈按上代家庭的顺序排，不把旁支子女重新挤到本人轴线旁。 */
        fun layoutDescendants(gen: Int) {
            val list = ordered(blocks.filter { it.generation == gen })
            if (list.isEmpty()) return
            val pivot = list.firstOrNull { it.id == anchor?.id }
                ?: list.minWith(Comparator { a, b -> personOrder.compare(link(a), link(b)) })
            val start = (list.indexOf(pivot) - COLUMNS / 2).coerceIn(0, max(0, list.size - COLUMNS))
            val first = list.drop(start).take(COLUMNS)
            val rows = listOf(first) + (list.take(start) + list.drop(start + first.size)).chunked(COLUMNS)
            val count = rows.maxOf { it.size }
            val ws = (0 until count).map { c -> rows.maxOf { row -> row.getOrNull(c)?.let(::width) ?: 0f } }
            val targets = (0 until count).map { c -> rows.mapNotNull { it.getOrNull(c) }.map(::desired).average().toFloat() }
            val lefts = pack(ws, targets)
            val anchorCol = first.indexOfFirst { it.id == anchor?.id }
            val offset = if (anchorCol >= 0) -(lefts[anchorCol] + ws[anchorCol] / 2f) else 0f
            for ((r, row) in rows.withIndex()) for ((c, b) in row.withIndex()) {
                val colLeft = lefts[c] + offset
                var px = colLeft + (ws[c] - width(b)) / 2f
                for (id in b.memberIds) { x[id] = px; px += (widths[id] ?: GraphGeom.CARD_MIN_W) + GraphGeom.MEMBER_GAP }
                placed[b.id] = FamilyBlock(b.id, b.memberIds, gen,
                    sideByBlock.getValue(b.id), r, c,
                    if (sideByBlock.getValue(b.id) == GraphSide.LEFT)
                        colLeft - GraphGeom.BLOCK_GAP / 2f
                    else colLeft + ws[c] + GraphGeom.BLOCK_GAP / 2f,
                    b.id == anchor?.id)
            }
            rowHeights[gen] = rows.map { row -> row.maxOf(::height) }
        }

        fun layoutBand(gen: Int, centerOnly: Boolean = false) {
            val items = blocks.filter { it.generation == gen &&
                (!centerOnly || it.id == anchor?.id) }
            val rowAssignments = mutableMapOf<GraphSide, List<List<GraphStructure.Block>>>()
            fun rowsOf(list: List<GraphStructure.Block>): List<List<GraphStructure.Block>> {
                if (list.isEmpty()) return emptyList()
                val pivot = list.minWith(Comparator { a, b -> personOrder.compare(link(a), link(b)) })
                val start = (list.indexOf(pivot) - COLUMNS / 2).coerceIn(0, max(0, list.size - COLUMNS))
                val first = list.drop(start).take(COLUMNS)
                val rest = list.take(start) + list.drop(start + first.size)
                return listOf(first) + rest.chunked(COLUMNS)
            }
            if (gen == 0 && !centerOnly) {
                // 同一个真实世代内部再分长幼，三侧共用行号；本人始终在中组首行。
                val groups = GraphSide.entries.associateWith { s ->
                    (-1..1).associateWith { tier -> rowsOf(ordered(items.filter {
                        sideByBlock[it.id] == s && seniority(it) == tier
                    })) }
                }
                val counts = (-1..1).associateWith { tier ->
                    maxOf(if (tier == 0) 1 else 0,
                        groups.values.maxOf { it.getValue(tier).size })
                }
                for (s in GraphSide.entries) if (items.any { sideByBlock[it.id] == s }) {
                    rowAssignments[s] = (-1..1).flatMap { tier ->
                        List(counts.getValue(tier)) { r -> groups.getValue(s).getValue(tier).getOrElse(r) { emptyList() } }
                    }
                }
                rowLabels[gen] = (-1..1).flatMap { tier -> List(counts.getValue(tier)) {
                    when (tier) { -1 -> "同辈 · 哥哥姐姐"; 1 -> "同辈 · 弟弟妹妹"; else -> "同辈 · 我与家人" }
                } }.takeIf { it.size > 1 }.orEmpty()
            } else for (s in GraphSide.entries) {
                if (centerOnly && s != GraphSide.CENTER) continue
                val rows = rowsOf(ordered(items.filter { sideByBlock[it.id] == s }))
                if (rows.isNotEmpty()) rowAssignments[s] = rows
            }
            var centerHalf = 0f
            for (s in listOf(GraphSide.CENTER, GraphSide.LEFT, GraphSide.RIGHT)) {
                val rows = rowAssignments[s] ?: continue
                val count = rows.maxOf { it.size }
                val columnWidths = (0 until count).map { c -> rows.maxOf { row -> row.getOrNull(c)?.let(::width) ?: 0f } }
                val targets = (0 until count).map { c ->
                    rows.mapNotNull { it.getOrNull(c) }.map(::desired).average().toFloat()
                }
                val lefts = pack(columnWidths, targets)
                val inner = if (centerHalf > 0f) centerHalf + GraphGeom.BLOCK_GAP else GraphGeom.BLOCK_GAP / 2
                val anchorCol = rows.firstOrNull { row -> row.any { it.id == anchor?.id } }
                    ?.indexOfFirst { it.id == anchor?.id } ?: -1
                val offset = when (s) {
                    GraphSide.CENTER -> if (anchorCol >= 0)
                        -(lefts[anchorCol] + columnWidths[anchorCol] / 2f) else 0f
                    GraphSide.LEFT -> minOf(0f, -inner - lefts.last() - columnWidths.last())
                    GraphSide.RIGHT -> maxOf(0f, inner - lefts.first())
                }
                for ((r, row) in rows.withIndex()) for ((c, b) in row.withIndex()) {
                    val columnLeft = lefts[c] + offset
                    val bx = when (s) {
                        GraphSide.LEFT -> columnLeft + columnWidths[c] - width(b)
                        GraphSide.CENTER -> columnLeft + (columnWidths[c] - width(b)) / 2f
                        GraphSide.RIGHT -> columnLeft
                    }
                    var px = bx
                    for (id in b.memberIds) { x[id] = px; px += (widths[id] ?: GraphGeom.CARD_MIN_W) + GraphGeom.MEMBER_GAP }
                    val corridor = if (s == GraphSide.LEFT) columnLeft - GraphGeom.BLOCK_GAP / 2
                        else columnLeft + columnWidths[c] + GraphGeom.BLOCK_GAP / 2
                    placed[b.id] = FamilyBlock(b.id, b.memberIds, gen, s, r, c, corridor, b.id == anchor?.id)
                    if (s == GraphSide.CENTER) centerHalf = max(centerHalf, max(abs(bx), abs(bx + width(b))))
                }
            }
            if (!centerOnly) {
                val nRows = rowAssignments.values.maxOfOrNull { it.size } ?: 1
                rowHeights[gen] = (0 until nRows).map { r ->
                    rowAssignments.values.flatMap { it.getOrNull(r).orEmpty() }.maxOfOrNull(::height) ?: GraphGeom.CARD_MIN_H
                }
            }
        }

        val gens = visible.mapNotNull { generations[it] }.distinct().sorted()
        // 先固定本人夫妻，再向祖辈展开；旁支子女随后依附已经就位的父母。
        // 第二遍让祖辈纳入已经出现的旁支子女，避免展开后远离自己的家庭。
        repeat(2) {
            if (0 in gens) layoutBand(0, centerOnly = true)
            for (gen in gens.filter { it < 0 }.sortedDescending()) layoutBand(gen)
            // 主夫妻这一代按侧分别打包，折行也不能让男方表亲进入女方侧。
            if (0 in gens) layoutBand(0)
            for (gen in gens.filter { it > 0 }) layoutDescendants(gen)
        }
        val bands = mutableListOf<GraphBand>()
        var y = GraphGeom.EDGE
        for (gen in gens) {
            val hs = rowHeights[gen] ?: listOf(GraphGeom.CARD_MIN_H)
            val tops = hs.map { h -> y.also { y += h + GraphGeom.ROW_GAP } }
            bands += GraphBand(gen, tops.first(), hs.size, tops, hs, rowLabels[gen].orEmpty())
            y += GraphGeom.CHANNEL - GraphGeom.ROW_GAP
        }
        val bandByGen = bands.associateBy { it.generation }
        val py = placed.values.flatMap { b ->
            b.memberIds.map { it to bandByGen.getValue(b.generation).rowTops[b.row] }
        }.toMap()
        return Result(x, py, placed.values.toList(), bands)
    }
}
