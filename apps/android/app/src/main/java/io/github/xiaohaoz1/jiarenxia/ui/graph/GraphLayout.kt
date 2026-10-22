package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 关系图画布几何（全部为 dp、系统字号 1.0 基准，屏幕与 PNG 导出共用）：
 * 卡片按文字实测、最小 72×96；同块成员间隔 MEMBER_GAP；
 * 家庭块/区之间间隔 BLOCK_GAP（侧廊居中于列缝）；
 * 世代间连线通道 CHANNEL；折叠展示行距 ROW_GAP。
 */
object GraphGeom {
    const val CARD_MIN_W = 72f
    const val CARD_MIN_H = 96f
    const val CARD_MAX_W = 104f
    const val AVATAR = 28f
    const val NAME_SIZE = 16f   // 姓名基准字号（sp）
    const val NAME_LH = 20f     // 姓名显式行高（sp），测量/屏幕/导出一致
    const val TITLE_SIZE = 13f  // 称谓基准字号（sp）
    const val TITLE_LH = 18f    // 称谓显式行高（sp），测量/屏幕/导出一致
    const val LABEL_SIZE = 12f
    const val CARD_PAD = 8f
    const val MEMBER_GAP = 8f
    const val BLOCK_GAP = 20f
    const val CHANNEL = 56f
    const val ROW_GAP = 28f
    const val EDGE = 14f
    const val CHIP_GAP = 6f
    const val CHIP_H = 48f
}

/**
 * 卡片尺寸测量器（dp，fontScale=1 基准单位）。
 * 默认实现为确定性估算：CJK 字宽约 1em、ASCII 约 0.65em，
 * 供纯 JVM/离线路径使用；应用运行期注入基于真实字体管线的
 * 测量实现（见 GraphTextMeasure），保证卡片、连线与导出
 * 几何来自同一份测量结果。
 */
interface GraphCardSizer {
    fun measure(name: String, title: String, self: Boolean): Pair<Float, Float>

    /** 展开芯片实测：默认估算，真实测量实现可覆盖。 */
    fun chipOf(label: String): Pair<Float, Float> =
        Companion.chipOf(label)

    /** 分组标签/折行标记尺寸：默认估算，可覆盖。 */
    fun labelOf(text: String): Pair<Float, Float> =
        Companion.labelOf(text)

    companion object {
        val Default = object : GraphCardSizer {
            override fun measure(name: String, title: String, self: Boolean) =
                cardOf(name, title)
        }

        /**
         * 文本估算宽（dp）：CJK≈1em、ASCII/数字≈0.65em。
         * ASCII 系数略留余量：大写字母接近 0.9em，低估会让姓名真实折行数
         * 超过预测行数，导致卡高不足、称谓被挤出卡片。
         */
        fun textW(text: String, sizeSp: Float): Float {
            var w = 0f
            for (ch in text) w += if (ch.code > 0x2E7F) 1f else 0.65f
            return w * sizeSp
        }

        /**
         * 卡片实测：宽由姓名一行自然宽决定（72..104），
         * 高按姓名/称谓在卡内宽折行后的实际行数增长，不设行数上限——
         * 长文字增高而不是截断。
         */
        fun cardOf(name: String, title: String): Pair<Float, Float> {
            val nameW = textW(name, GraphGeom.NAME_SIZE)
            val titleW = textW(title, GraphGeom.TITLE_SIZE)
            val w = maxOf(
                GraphGeom.CARD_MIN_W,
                minOf(nameW + GraphGeom.CARD_PAD * 2, GraphGeom.CARD_MAX_W))
            val inner = w - GraphGeom.CARD_PAD * 2
            val nameLines = ceil(nameW / inner).toInt().coerceAtLeast(1)
            val titleLines = if (title.isEmpty()) 0
                else ceil(titleW / inner).toInt().coerceAtLeast(1)
            val h = 10f + GraphGeom.AVATAR + 6f +
                nameLines * GraphGeom.NAME_LH +
                (if (titleLines > 0) 4f + titleLines * GraphGeom.TITLE_LH
                 else 0f) + 11f
            return w to maxOf(GraphGeom.CARD_MIN_H, h)
        }

        /** 展开芯片实测：高固定 48（触控下限），宽按文案。 */
        fun chipOf(label: String): Pair<Float, Float> =
            textW(label, GraphGeom.TITLE_SIZE) + 28f to GraphGeom.CHIP_H

        /** 分组标签/折行标记估算尺寸。 */
        fun labelOf(text: String): Pair<Float, Float> =
            textW(text, GraphGeom.LABEL_SIZE) + 8f to 18f
    }
}

/** 真实世代 band：同真实 generation 的人在同一 band，视觉分若干展示行。 */
data class GraphBand(
    val generation: Int,
    val top: Float,
    val rows: Int,
    /** 各展示行的行顶 y（dp）。 */
    val rowTops: List<Float>,
    /** 各展示行的行高（dp）= 该行最大卡片实测高。 */
    val rowHeights: List<Float>,
    /** 同辈内的长幼分排说明，不改变 generation。 */
    val rowLabels: List<String> = emptyList()
) {
    /** 该 band 占用的视觉底缘。 */
    val bottom: Float get() = rowTops.last() + rowHeights.last()
    /** 第 r 展示行的行底 y。 */
    fun rowBottom(r: Int) =
        if (r < rows) rowTops[r] + rowHeights[r] else bottom
}

/** 家庭 block：不可拆的 1~2 人单元，落在某侧某展示行的某 block 列。 */
data class FamilyBlock(
    val id: String,
    /** 成员（1~2 人）；双人已按 男左女右 排序。 */
    val memberIds: List<String>,
    val generation: Int,
    val side: GraphSide,
    /** band 内展示行号。 */
    val row: Int,
    /** 展示行内 block 列号（0=靠中行）。 */
    val column: Int,
    /** 本 block 的侧廊 x：折行/受阻挡连线使用的列间空缝中线。 */
    val corridorX: Float,
    val isAnchor: Boolean
)

/** 图节点：x/y 为卡左上角（dp），w/h 为按文字实测的卡片尺寸。 */
data class GraphNode(
    val personId: String,
    val x: Float,
    val y: Float,
    val generation: Int,
    val blockId: String = "",
    val w: Float = GraphGeom.CARD_MIN_W,
    val h: Float = GraphGeom.CARD_MIN_H
) {
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
    val right get() = x + w
    val bottom get() = y + h
}

/** 图边：fromId→toId 即关系记录的 source→target（PARENT 即父母→子女）。 */
data class GraphEdge(
    val fromId: String, val toId: String, val type: RelationshipType,
    /** “我”相邻边的关系词（父子/夫妻/兄弟…），其余边为 null。 */
    val label: String? = null
)

/** 画布文字元素（分组标签、折行标记）：x/y 为左上角 dp，w/h 为估算尺寸。 */
data class GraphNote(
    val text: String,
    val x: Float,
    val y: Float,
    val w: Float = 0f,
    val h: Float = 0f
)

/** 画布上的展开芯片：x/y/w/h 为完整实测矩形（dp），渲染层只负责画。 */
data class PlacedChip(
    /** 揭示方向：UP=长辈 / SAME=同辈 / DOWN=晚辈。 */
    val dir: GraphVisibility.Dir,
    /** 芯片相对锚点 block 的方位。 */
    val align: Align,
    val count: Int,
    /** 点击后应加入展开集合的键。 */
    val expands: Set<GraphVisibility.Expand>,
    /** 将揭示的人物 id（相机定位到新增家庭用）。 */
    val reveals: Set<String>,
    /** 芯片依附的家庭块 id（聚焦范围与分组标签避让用）。 */
    val blockId: String,
    val x: Float, val y: Float, val w: Float, val h: Float
) {
    enum class Align { ABOVE, BELOW, LEFT, RIGHT }
    val cx get() = x + w / 2f
    val cy get() = y + h / 2f
    val right get() = x + w
    val bottom get() = y + h
}

/** 简单浮点矩形。 */
data class FloatRect(
    val left: Float, val top: Float, val right: Float, val bottom: Float
) {
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val width get() = right - left
    val height get() = bottom - top
    fun union(o: FloatRect) = FloatRect(
        minOf(left, o.left), minOf(top, o.top),
        maxOf(right, o.right), maxOf(bottom, o.bottom))
}

data class GraphModel(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val unconnectedCount: Int,
    val bands: List<GraphBand> = emptyList(),
    val blocks: List<FamilyBlock> = emptyList(),
    /** 收起的家人数（可见层统计）。 */
    val collapsedCount: Int = 0,
    /** 已解析落点的展开芯片（画布“N 位 ＋”入口），含完整实测矩形。 */
    val expandChips: List<PlacedChip> = emptyList(),
    /** 家庭分组标签（如“我的父母”“妻子的父母”）。 */
    val groupLabels: List<GraphNote> = emptyList(),
    /** 同辈折行标记（“同辈 · 接上行”）。 */
    val laneMarks: List<GraphNote> = emptyList()
) {
    /**
     * 已记录的子女处在父母同辈或上辈：保留原始边供核对，不倒画成合法亲子线。
     * 只检查两端均可见的记录，不根据称谓补关系，也不修改人物资料。
     */
    val parentGenerationConflicts: List<GraphEdge> by lazy {
        val gens = nodes.associate { it.personId to it.generation }
        edges.filter { e ->
            val pc = when (e.type) {
                RelationshipType.PARENT -> e.fromId to e.toId
                RelationshipType.CHILD -> e.toId to e.fromId
                else -> null
            } ?: return@filter false
            val parent = gens[pc.first] ?: return@filter false
            val child = gens[pc.second] ?: return@filter false
            child <= parent
        }
    }

    private val blockOfMap: Map<String, FamilyBlock> by lazy {
        HashMap<String, FamilyBlock>().also { m ->
            for (b in blocks) for (p in b.memberIds) m[p] = b
        }
    }
    fun blockOf(personId: String): FamilyBlock? = blockOfMap[personId]

    /** 若干节点卡片的包围矩形。 */
    fun rectOfNodes(ids: Iterable<String>): FloatRect? {
        var r: FloatRect? = null
        for (id in ids) {
            val n = nodes.firstOrNull { it.personId == id } ?: continue
            val nr = FloatRect(n.x, n.y, n.right, n.bottom)
            r = r?.union(nr) ?: nr
        }
        return r
    }

    /** 家庭聚焦范围：块成员卡片 ∪ 贴附在该块上的展开芯片完整矩形。 */
    fun focusBlockOf(personId: String): FloatRect? {
        val b = blockOf(personId) ?: return null
        var r = rectOfNodes(b.memberIds) ?: return null
        for (c in expandChips) if (c.blockId == b.id) {
            r = r.union(FloatRect(c.x, c.y, c.right, c.bottom))
        }
        return r
    }
}

/**
 * 家庭关系图布局算法（家庭 block + 世代 band + 同辈多行，实测卡片 dp 坐标）。
 *
 * - [generations] 是“双方可达人物全集”的真实世代表（self=0）；
 * - [visibleIds] 只决定当前入图的人，左右归属仍按全集结构计算——
 *   收起旁支不改变可见人物的家族归属，也不会让他们漂到中间；
 * - 男左女右、主夫妻居中、父路径左母路径右由 [GraphStructure] 保证；
 * - 同区内部先按原生家庭排序，每行最多四个夫妻块，较大支系在同 band 续行；
 * - 卡片宽高按真实姓名/称谓由 [sizer] 实测，行高取同行卡片最大实测高。
 */
object GraphLayout {

    fun build(
        overview: FamilyOverview,
        generations: Map<String, Int>,
        visibleIds: Set<String> = generations.keys,
        collapsedCount: Int = 0,
        expandChips: List<GraphVisibility.Chip> = emptyList(),
        sizer: GraphCardSizer = GraphCardSizer.Default
    ): GraphModel {
        val self = overview.self.person
        val byId = overview.byId
        val graph = overview.graph
        val anchors = GraphStructure.anchorsOf(graph, self.id)
        val assign = GraphStructure.assign(graph, anchors, byId)
        val visible = visibleIds.filter { it in generations && it in byId }.toSet()
        val blocks = GraphStructure.blocksOf(graph, generations, visible, byId)
        val blockOf = HashMap<String, GraphStructure.Block>()
        for (b in blocks) for (m in b.memberIds) blockOf[m] = b

        // ---------- 实测卡片尺寸 ----------
        val cardW = HashMap<String, Float>()
        val cardH = HashMap<String, Float>()
        for (id in visible) {
            val o = byId[id] ?: continue
            val (w, h) = sizer.measure(
                o.person.name, o.displayTitle, id == self.id)
            cardW[id] = w; cardH[id] = h
        }
        fun wOf(id: String) = cardW[id] ?: GraphGeom.CARD_MIN_W
        fun hOf(id: String) = cardH[id] ?: GraphGeom.CARD_MIN_H
        // 夫妻块内部保留男左女右；块之间按原生家庭排序，旁支子女沿自己的父母落位。
        val familyPlacement = GraphFamilyPlacement.place(
            overview, generations, visible, blocks, assign, anchors, cardW, cardH)
        val px = familyPlacement.x
        val py = familyPlacement.y
        val placedBlocks = familyPlacement.blocks.associateBy { it.id }
        val bands = familyPlacement.bands

        val nodes = visible.mapNotNull { id ->
            val gen = generations[id] ?: return@mapNotNull null
            val bx = px[id] ?: return@mapNotNull null
            val by = py[id] ?: return@mapNotNull null
            GraphNode(
                personId = id, x = bx, y = by, generation = gen,
                blockId = blockOf[id]?.id ?: "",
                w = wOf(id), h = hOf(id)
            )
        }.sortedWith(compareBy({ it.y }, { it.x }))

        // ---------- 边：共同父母已表达的兄弟不重复画 ----------

        val parentsOf = HashMap<String, MutableSet<String>>()
        for (r in graph.relationships) {
            when (r.type) {
                RelationshipType.PARENT ->
                    parentsOf.getOrPut(r.targetPersonId) { mutableSetOf() }
                        .add(r.sourcePersonId)
                RelationshipType.CHILD ->
                    parentsOf.getOrPut(r.sourcePersonId) { mutableSetOf() }
                        .add(r.targetPersonId)
                else -> Unit
            }
        }
        val structuralAdj = mutableMapOf<String, MutableSet<String>>()
        for (r in graph.relationships) if (r.type != RelationshipType.COUSIN &&
            r.sourcePersonId in visible && r.targetPersonId in visible) {
            structuralAdj.getOrPut(r.sourcePersonId) { mutableSetOf() } += r.targetPersonId
            structuralAdj.getOrPut(r.targetPersonId) { mutableSetOf() } += r.sourcePersonId
        }
        fun alreadyExplained(a: String, b: String): Boolean {
            val seen = mutableSetOf(a)
            val queue = ArrayDeque<String>(); queue.add(a)
            while (queue.isNotEmpty()) for (id in structuralAdj[queue.removeFirst()].orEmpty()) {
                if (id == b) return true
                if (seen.add(id)) queue.add(id)
            }
            return false
        }
        val edges = graph.relationships.asSequence()
            .filter { it.sourcePersonId in visible && it.targetPersonId in visible }
            .filterNot { r ->
                // 兄弟关系已由可见的共同父母连线表达时不再画兄弟边；
                // 关系记录本身保留在资料里
                r.type == RelationshipType.SIBLING &&
                    parentsOf[r.sourcePersonId].orEmpty()
                        .intersect(parentsOf[r.targetPersonId].orEmpty())
                        .any { it in visible }
            }
            .filterNot { r ->
                // 同辈堂表亲已有完整可见的真实路径时，申报记录只携带长幼，
                // 不重复横跨整张图画一条虚线；孤立申报仍保留连线入口。
                r.type == RelationshipType.COUSIN &&
                    generations[r.sourcePersonId] == generations[r.targetPersonId] &&
                    alreadyExplained(r.sourcePersonId, r.targetPersonId)
            }
            .map {
                GraphEdge(
                    fromId = it.sourcePersonId, toId = it.targetPersonId,
                    type = it.type, label = edgeLabel(it, self, byId)
                )
            }
            .toList()

        // ---------- 画布元素：分组标签 / 折行标记 / 展开芯片 ----------

        val nodeById = nodes.associateBy { it.personId }
        fun blockRect(b: FamilyBlock): FloatRect {
            var l = Float.MAX_VALUE; var r = -Float.MAX_VALUE
            var t = Float.MAX_VALUE; var bt = -Float.MAX_VALUE
            for (m in b.memberIds) {
                val n = nodeById[m] ?: continue
                l = min(l, n.x); r = max(r, n.right)
                t = min(t, n.y); bt = max(bt, n.bottom)
            }
            return FloatRect(l, t, r, bt)
        }
        val placedBlockRect = placedBlocks.values.associate { it.id to blockRect(it) }

        // 展开芯片落点：UP 贴块上缘、DOWN 贴块下缘、
        // SAME 按被揭示人物的归属侧贴块左右缘（归中则贴下缘）；
        // 同块同方位多芯片沿切向堆叠，矩形整体计入布局。
        // 芯片不得扩大横向布局、不得遮挡卡片/连线：
        // - 横向必须完整落在全部卡片的包围范围之内；
        // - 不与任何卡片矩形相交；
        // - 块成员已有可见父母/子女时，上下方向不贴芯片
        //   （该方位是亲子关系主干经过的通道）。
        // 找不到位置的芯片入口仍保留在“展开家人”面板，不丢分组计数。
        val cardEnv = run {
            var l = Float.MAX_VALUE; var r = -Float.MAX_VALUE
            var t = Float.MAX_VALUE; var bt = -Float.MAX_VALUE
            for (n in nodes) {
                l = min(l, n.x); r = max(r, n.right)
                t = min(t, n.y); bt = max(bt, n.bottom)
            }
            FloatRect(l, t, r, bt)
        }
        fun overlapsCard(x: Float, y: Float, w: Float, h: Float): Boolean =
            nodes.any { n ->
                x < n.right && x + w > n.x && y < n.bottom && y + h > n.y }
        /** 块成员是否有可见的父代（上入线）或子代（下落线）连接。 */
        fun blockHasVisibleLink(b: FamilyBlock, up: Boolean): Boolean {
            val members = b.memberIds.toSet()
            return edges.any { e ->
                when (e.type) {
                    RelationshipType.PARENT, RelationshipType.CHILD -> {
                        val (par, child) = if (e.type == RelationshipType.PARENT)
                            e.fromId to e.toId else e.toId to e.fromId
                        if (up) child in members else par in members
                    }
                    else -> false
                }
            }
        }
        val placedChips = ArrayList<PlacedChip>()
        val chipSlots = HashMap<Pair<String, PlacedChip.Align>, Int>()
        for (c in expandChips) {
            val b = placedBlocks[c.blockId] ?: continue
            val rc = placedBlockRect[b.id] ?: continue
            val cxm = rc.cx; val cym = rc.cy
            val align = when (c.dir) {
                GraphVisibility.Dir.UP -> PlacedChip.Align.ABOVE
                GraphVisibility.Dir.DOWN -> PlacedChip.Align.BELOW
                GraphVisibility.Dir.SAME -> {
                    val leftN = c.reveals.count {
                        assign[it]?.side == GraphSide.LEFT }
                    val rightN = c.reveals.count {
                        assign[it]?.side == GraphSide.RIGHT }
                    when {
                        leftN > rightN -> PlacedChip.Align.LEFT
                        rightN > leftN -> PlacedChip.Align.RIGHT
                        else -> PlacedChip.Align.BELOW
                    }
                }
                GraphVisibility.Dir.ALL -> continue
            }
            val label = chipNoun(c.dir) + " " + c.count + " 位 ＋"
            val (cw, ch) = sizer.chipOf(label)
            val slot = chipSlots.getOrDefault(b.id to align, 0)
            val (x, y) = when (align) {
                PlacedChip.Align.ABOVE ->
                    (cxm - cw / 2f + slot * (cw + 4f)) to
                        (rc.top - GraphGeom.CHIP_GAP - ch)
                PlacedChip.Align.BELOW ->
                    (cxm - cw / 2f + slot * (cw + 4f)) to
                        (rc.bottom + GraphGeom.CHIP_GAP)
                PlacedChip.Align.LEFT ->
                    (rc.left - GraphGeom.CHIP_GAP - cw) to
                        (cym - ch / 2f + slot * (ch + 4f))
                PlacedChip.Align.RIGHT ->
                    (rc.right + GraphGeom.CHIP_GAP) to
                        (cym - ch / 2f + slot * (ch + 4f))
            }
            val bad = when (align) {
                PlacedChip.Align.ABOVE -> blockHasVisibleLink(b, up = true)
                PlacedChip.Align.BELOW -> blockHasVisibleLink(b, up = false)
                else -> false
            }
            if (bad || x < cardEnv.left || x + cw > cardEnv.right ||
                overlapsCard(x, y, cw, ch)) continue
            chipSlots[b.id to align] = slot + 1
            placedChips += PlacedChip(
                c.dir, align, c.count, c.expands, c.reveals, b.id, x, y, cw, ch)
        }

        // 主锚父母的家庭块标签：“我的父母”/“<配偶称谓>的父母”；
        // 该块若已有上贴芯片，标签再上移到芯片上方
        val groupLabels = ArrayList<GraphNote>()
        val partnerTitle = anchors.partnerId
            ?.let { byId[it]?.displayTitle } ?: "配偶"
        val chipAbove = placedChips
            .filter { it.align == PlacedChip.Align.ABOVE }
            .groupBy { it.blockId }
        for ((anchor, label) in listOf(
            anchors.selfId to "我的父母",
            anchors.partnerId to (partnerTitle + "的父母")
        )) {
            if (anchor == null) continue
            val parents = GraphStructure.parentsOf(graph, anchor).toSet()
            if (parents.isEmpty()) continue
            for (b in placedBlocks.values) {
                if (b.isAnchor || b.memberIds.none { it in parents }) continue
                val rc = placedBlockRect[b.id] ?: continue
                val (lw, lh) = sizer.labelOf(label)
                val above = chipAbove[b.id]?.minOfOrNull { it.y }
                val top = (above ?: rc.top) - 4f - lh
                groupLabels += GraphNote(label, rc.cx - lw / 2f, top, lw, lh)
            }
        }

        // “同辈 · 接上行”：同真实世代第 2 行起的行首标记（位于行间隙正中）
        val laneMarks = ArrayList<GraphNote>()
        for (band in bands) {
            if (band.rows <= 1) continue
            for (r in (if (band.rowLabels.isNotEmpty()) 0 else 1) until band.rows) {
                val rowBlocks = placedBlocks.values.filter {
                    it.generation == band.generation && it.row == r
                }
                if (rowBlocks.isEmpty()) continue
                val minX = rowBlocks.minOf {
                    it.memberIds.minOf { m -> px[m] ?: 0f }
                }
                val text = band.rowLabels.getOrNull(r) ?: "同辈 · 接上行"
                val (mw, mh) = sizer.labelOf(text)
                laneMarks += GraphNote(
                    text, minX,
                    band.rowTops[r] - GraphGeom.ROW_GAP +
                        (GraphGeom.ROW_GAP - mh) / 2f, mw, mh)
            }
        }

        return GraphModel(
            nodes = nodes, edges = edges,
            unconnectedCount = graph.persons.count { it.id !in generations.keys },
            bands = bands,
            blocks = placedBlocks.values.toList(),
            collapsedCount = collapsedCount,
            expandChips = placedChips,
            groupLabels = groupLabels,
            laneMarks = laneMarks
        )
    }

    /** 芯片文案的方向词。 */
    fun chipNoun(dir: GraphVisibility.Dir) = when (dir) {
        GraphVisibility.Dir.UP -> "长辈"
        GraphVisibility.Dir.SAME -> "平辈"
        GraphVisibility.Dir.DOWN -> "晚辈"
        GraphVisibility.Dir.ALL -> "家人"
    }

    /**
     * 每条边的关系词：父子/母子/父女/母女/夫妻/兄弟/姐弟/兄妹/姐妹。
     * 父母—子女取父母方性别字 + 子女方性别字；兄弟姐妹看两端性别，
     * 男女各半时按 relativeAgeOrder 定长幼序；长幼未记录且贴“我”时
     * 用对方标准称谓（哥哥/姐姐…）兜底，再否则按“男年长于女”近似。
     */
    private fun edgeLabel(
        rel: Relationship,
        self: io.github.xiaohaoz1.jiarenxia.core.model.Person,
        byId: Map<String, PersonOverview>
    ): String? {
        val src = byId[rel.sourcePersonId] ?: return null
        val tgt = byId[rel.targetPersonId] ?: return null
        val srcM = src.person.gender == Gender.MALE
        val tgtM = tgt.person.gender == Gender.MALE
        return when (rel.type) {
            RelationshipType.SPOUSE -> "夫妻"
            RelationshipType.SIBLING -> {
                if (srcM == tgtM) return if (srcM) "兄弟" else "姐妹"
                val olderMale = when (rel.relativeAgeOrder) {
                    RelativeAgeOrder.OLDER -> srcM
                    RelativeAgeOrder.YOUNGER -> tgtM
                    else -> null
                } ?: selfAdjacentOlderMale(rel, self, src, tgt) ?: srcM
                if (olderMale) "兄妹" else "姐弟"
            }
            RelationshipType.PARENT, RelationshipType.CHILD -> {
                // CHILD 记录方向与 PARENT 相反：source 为子女、target 为父母
                val parentM = if (rel.type == RelationshipType.PARENT) srcM else tgtM
                val childM = if (rel.type == RelationshipType.PARENT) tgtM else srcM
                (if (parentM) "父" else "母") + (if (childM) "子" else "女")
            }
            // 申报堂表亲：不判堂/表（记录本身不含支系），只标通用词
            RelationshipType.COUSIN -> "堂表亲"
        }
    }

    /** “我”相邻的兄弟姐妹边：用对方标准称谓推出年长方性别；非相邻边返回 null。 */
    private fun selfAdjacentOlderMale(
        rel: Relationship,
        self: io.github.xiaohaoz1.jiarenxia.core.model.Person,
        src: PersonOverview,
        tgt: PersonOverview
    ): Boolean? {
        val other = when (self.id) {
            rel.sourcePersonId -> tgt
            rel.targetPersonId -> src
            else -> return null
        }
        val otherMale = other.person.gender == Gender.MALE
        return when (other.result.standardTitle) {
            "哥哥", "姐姐" -> otherMale      // 对方年长
            "弟弟", "妹妹" -> !otherMale     // “我”年长
            else -> null
        }
    }
}
