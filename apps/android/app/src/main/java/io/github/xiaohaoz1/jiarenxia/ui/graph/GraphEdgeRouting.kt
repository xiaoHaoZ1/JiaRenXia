package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 关系图连线路由：按真实 generation 与实测卡片矩形计算全部折线路径；
 * 屏幕与 PNG 导出只绘制、不重算。纯计算，可 JVM 单测。
 *
 * 通道规格（主审视觉改版）：
 * - 夫妻：卡间短横线；同块夫妻的亲子主干从配偶线中点垂直下落，
 *   直接落到代际通道（不绕行侧廊）。
 * - 单亲：该人下端口垂直落到车道。
 * - 不同块的两位已知父母：各自下端口垂落，一条水平连接段并入，
 *   从连接段中点下落。
 * - 子女：行内从总线直落到卡上端口；折行且上方确有卡片阻挡时
 *   才走本 block 侧廊——不无条件进 corridorX。
 * - 兄弟/堂表亲虚线：同展示行走上缘短跳线；相邻展示行走行间隙
 *   局部横道；更远才走侧廊/通道。一律不绕整张图外缘、不穿卡片。
 */
object GraphEdgeRouting {

    /** 卡片端口到横道的短横移距离（dp）。 */
    private const val JOG = 8f
    /** 图外侧缘与内容的最小距离（dp）。 */
    private const val MARGIN = 10f
    /** 总线车道间距下限：横向范围相差小于它视为重叠（dp）。 */
    const val LANE_MARGIN = 18f

    data class Pt(val x: Float, val y: Float)

    enum class RouteStyle { SOLID, SPOUSE, DASHED }

    /** 一条折线路径；[edge] 为对应图边（家庭总线等合成段为 null）。 */
    data class RoutedPath(
        val edge: GraphEdge?,
        val style: RouteStyle,
        val points: List<Pt>
    )

    /** 关系词落点：由路由按实际空白线段算出，渲染方只换算像素。 */
    data class RoutedLabel(val text: String, val x: Float, val y: Float)

    data class Bounds(
        val left: Float, val top: Float, val right: Float, val bottom: Float
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    /** 非关联线的交叉：绘制跨线小桥，不能被误读为同一家庭的接点。 */
    data class Crossing(val x: Float, val y: Float, val radius: Float, val style: RouteStyle)

    data class Routing(
        val paths: List<RoutedPath>,
        val labels: List<RoutedLabel>,
        /**
         * 实际画上画布的展开芯片：布局选位之外再经路由级兜底，
         * 与任何路径段相交的芯片被剔除（入口仍在“展开家人”面板）。
         */
        val chips: List<PlacedChip>,
        /** 内容包围盒：节点+连线+标签+折行标记+入图芯片，供屏幕画布。 */
        val bounds: Bounds,
        /** 人物内容包围盒：不含展开芯片，供 PNG 导出。 */
        val nodeBounds: Bounds,
        val crossings: List<Crossing> = emptyList()
    )

    fun route(model: GraphModel): Routing {
        val nodeById = model.nodes.associateBy { it.personId }
        val bandByGen = model.bands.associateBy { it.generation }
        val bandIdx = model.bands.withIndex()
            .associate { (i, b) -> b.generation to i }
        val paths = ArrayList<RoutedPath>()
        val labels = ArrayList<RoutedLabel>()
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var nMinX = Float.MAX_VALUE; var nMinY = Float.MAX_VALUE
        var nMaxX = -Float.MAX_VALUE; var nMaxY = -Float.MAX_VALUE
        fun grow(x: Float, y: Float) {
            minX = minOf(minX, x); maxX = maxOf(maxX, x)
            minY = minOf(minY, y); maxY = maxOf(maxY, y)
        }
        fun growNode(x: Float, y: Float) {
            grow(x, y)
            nMinX = minOf(nMinX, x); nMaxX = maxOf(nMaxX, x)
            nMinY = minOf(nMinY, y); nMaxY = maxOf(nMaxY, y)
        }
        for (n in model.nodes) {
            growNode(n.x, n.y); growNode(n.right, n.bottom)
        }
        // 分组标签 / 折行标记：按完整实测矩形纳入 bounds
        //（展开芯片在路径算完后做相交兜底，只把留下的计入 bounds）
        for (l in model.groupLabels) {
            growNode(l.x - 2f, l.y - 1f); growNode(l.x + l.w + 2f, l.y + l.h + 1f)
        }
        for (m in model.laneMarks) {
            growNode(m.x - 2f, m.y); growNode(m.x + m.w + 2f, m.y + m.h)
        }
        fun add(style: RouteStyle, edge: GraphEdge?, pts: List<Pt>) {
            if (pts.size >= 2) paths += RoutedPath(edge, style, pts)
            pts.forEach { growNode(it.x, it.y) }
        }
        fun styleOf(e: GraphEdge) = when (e.type) {
            RelationshipType.SPOUSE -> RouteStyle.SPOUSE
            RelationshipType.SIBLING, RelationshipType.COUSIN -> RouteStyle.DASHED
            else -> RouteStyle.SOLID
        }
        fun topC(n: GraphNode) = Pt(n.cx, n.y)
        fun botC(n: GraphNode) = Pt(n.cx, n.bottom)
        fun corridorX(id: String) = model.blockOf(id)?.corridorX

        // ---------- 通道（band 间隙）车道 ----------
        val chTop = FloatArray(model.bands.size - 1) { model.bands[it].bottom }
        val chH = FloatArray(model.bands.size - 1) {
            model.bands[it + 1].top - model.bands[it].bottom
        }
        fun channelBelow(gen: Int) = bandIdx[gen]?.takeIf { it < chTop.size }
        fun channelAbove(gen: Int) = bandIdx[gen]?.takeIf { it in 1..chTop.size }?.minus(1)

        // ---------- 边分类 ----------
        // 标准化的 父母→子女 端点（PARENT 与反向 CHILD 统一方向）
        fun parentChild(e: GraphEdge): Pair<String, String>? = when (e.type) {
            RelationshipType.PARENT -> e.fromId to e.toId
            RelationshipType.CHILD -> e.toId to e.fromId
            else -> null
        }
        val byChild = LinkedHashMap<String, MutableList<GraphEdge>>()
        val loose = ArrayList<GraphEdge>()
        val conflicts = model.parentGenerationConflicts.toSet()
        for (e in model.edges) {
            // 反向/同代亲子记录由核对入口明确展示，不能按几何上下倒画关系。
            if (e in conflicts) continue
            val pc = parentChild(e)
            if (pc == null) { loose += e; continue }
            val np = nodeById[pc.first]; val nc = nodeById[pc.second]
            if (np != null && nc != null && nc.generation == np.generation + 1) {
                byChild.getOrPut(pc.second) { mutableListOf() }.add(e)
            } else {
                loose += e     // 跨代异常父母边：避卡折线，不挪 generation
            }
        }
        // 家庭单元：同组父母（按 id 排序）+ 他们在下一代的子女
        data class Unit(
            val parentIds: List<String>,
            val parentGen: Int,
            val childIds: List<String>,
            val edges: List<GraphEdge>
        )
        val units = LinkedHashMap<Pair<List<String>, Int>, Unit>()
        for ((child, es) in byChild) {
            val parentIds = es.map {
                if (it.type == RelationshipType.PARENT) it.fromId else it.toId
            }.distinct().sorted()
            val gen = nodeById.getValue(child).generation - 1
            val key = parentIds to gen
            val u = units[key] ?: Unit(parentIds, gen, mutableListOf(), mutableListOf())
            units[key] = Unit(
                u.parentIds, u.parentGen,
                (u.childIds + child).sortedBy { nodeById[it]?.x ?: 0f },
                u.edges + es
            )
        }
        // 每个单元占用的横向范围 = 主干落点与子女落点的横向跨度
        class PlacedUnit(
            val unit: Unit, val channel: Int,
            val spanStart: Float, val spanEnd: Float, var lane: Int = 0
        )
        fun unitStemX(u: Unit): Float {
            val pNodes = u.parentIds.mapNotNull { nodeById[it] }
            return when {
                pNodes.size == 2 &&
                    model.blockOf(pNodes[0].personId)?.id ==
                    model.blockOf(pNodes[1].personId)?.id -> {
                    val (l, r) = if (pNodes[0].x <= pNodes[1].x)
                        pNodes[0] to pNodes[1] else pNodes[1] to pNodes[0]
                    (l.right + r.x) / 2f
                }
                else -> pNodes.map { it.cx }.let { xs ->
                    (xs.min() + xs.max()) / 2f
                }
            }
        }
        val placed = units.values.mapNotNull { u ->
            val ch = channelBelow(u.parentGen) ?: return@mapNotNull null
            val stem = unitStemX(u)
            val xs = u.childIds.mapNotNull { nodeById[it] }.map { n ->
                // 落线 x：行内为卡心，折行受阻时为侧廊
                val row = model.blockOf(n.personId)?.row ?: 0
                if (row == 0 || !dropBlocked(n, model, ch, chTop))
                    n.cx
                else corridorX(n.personId) ?: n.cx
            }
            if (xs.isEmpty()) return@mapNotNull null
            PlacedUnit(u, ch,
                min(stem, xs.min()), max(stem, xs.max()))
        }
        val unitLaneCount = IntArray(chTop.size)
        placed.groupBy { it.channel }.forEach { (ch, us) ->
            // 横道不能只按左缘依次分配：向右接子女时，右边家庭应走上道，
            // 左边家庭走下道，避免一家的横道横穿另一家已经下落的子女线。
            val above = us.associateWith { mutableSetOf<PlacedUnit>() }
            fun inside(x: Float, u: PlacedUnit) = x > u.spanStart + 0.01f && x < u.spanEnd - 0.01f
            for (a in us) for (b in us) {
                if (a === b) continue
                if (inside(unitStemX(b.unit), a)) above.getValue(b) += a
                if (b.unit.childIds.mapNotNull { nodeById[it] }.any { inside(it.cx, a) })
                    above.getValue(a) += b
            }
            val remaining = us.toMutableSet()
            val lanes = mutableListOf<MutableList<PlacedUnit>>()
            val done = mutableSetOf<PlacedUnit>()
            while (remaining.isNotEmpty()) {
                val ready = remaining.filter { b -> remaining.none { a -> b in above.getValue(a) } }
                val u = ready.ifEmpty { remaining.toList() }
                    .minWith(compareBy({ it.spanStart }, { it.spanEnd }))
                val predecessors = done.filter { u in above.getValue(it) }
                var lane = (predecessors.maxOfOrNull { it.lane + 1 } ?: 0)
                while (lane < lanes.size && lanes[lane].any {
                    it.spanEnd + LANE_MARGIN >= u.spanStart && u.spanEnd + LANE_MARGIN >= it.spanStart
                }) lane++
                while (lanes.size <= lane) lanes += mutableListOf<PlacedUnit>()
                u.lane = lane
                lanes[lane] += u
                remaining.remove(u); done += u
            }
            unitLaneCount[ch] = lanes.size
        }

        // ---------- 松边横道分配 ----------
        // 每个松边：a=上端（同行取左），b=下端（同行取右）
        data class Loose(val e: GraphEdge, val a: GraphNode, val b: GraphNode)
        val looses = loose.mapNotNull { e ->
            val na = nodeById[e.fromId]; val nb = nodeById[e.toId]
            if (na == null || nb == null) return@mapNotNull null
            // 同 block 配偶：卡间短横线，不占车道
            if (e.type == RelationshipType.SPOUSE &&
                model.blockOf(e.fromId)?.id == model.blockOf(e.toId)?.id
            ) {
                val (l, r) = if (na.x <= nb.x) na to nb else nb to na
                val y = min(l.cy, r.cy)
                add(RouteStyle.SPOUSE, e, listOf(Pt(l.right, y), Pt(r.x, y)))
                e.label?.let {
                    labels += RoutedLabel(it, (l.right + r.x) / 2, y - 2f)
                }
                return@mapNotNull null
            }
            val (a, b) = when {
                na.y != nb.y -> if (na.y < nb.y) na to nb else nb to na
                else -> if (na.x <= nb.x) na to nb else nb to na
            }
            Loose(e, a, b)
        }

        // (band idx, 展示行) → 经该行上空档的松边（多行距走侧廊）
        val gapEdges = LinkedHashMap<Pair<Int, Int>, MutableList<Loose>>()
        // channel → 相邻 band 松边（同 channel 内一个车道）
        val adjacentEdges = LinkedHashMap<Int, MutableList<Loose>>()
        // 跨多 band 松边：上端 band 下缘车道 + 中间代际空列 + 下端 band 上缘车道
        data class Span(val loose: Loose, val upperCh: Int, val lowerCh: Int)
        val spans = ArrayList<Span>()
        val spanUpper = HashMap<Int, Int>()
        val spanLower = HashMap<Int, Int>()

        fun rowOf(id: String) = model.blockOf(id)?.row ?: 0
        fun bandGapY(gen: Int, lowerRow: Int, seq: Int, total: Int): Float {
            val band = bandByGen[gen] ?: return 0f
            val top = band.rowTops.getOrElse(lowerRow) { band.top }
            // 行间隙内的第 seq 条横道
            val gap = band.rowBottom(lowerRow - 1)
            return gap + (top - gap) * (seq + 1) / (total + 1)
        }

        for (l in looses) {
            val ga = l.a.generation; val gb = l.b.generation
            if (ga == gb) {
                val ra = rowOf(l.a.personId); val rb = rowOf(l.b.personId)
                val bi = bandIdx[ga] ?: continue
                when {
                    // 同展示行：行上缘空档短跳线，不进侧廊/通道
                    ra == rb -> {
                        val yJ = min(l.a.y, l.b.y) - JOG
                        val ta = topC(l.a); val tb = topC(l.b)
                        add(styleOf(l.e), l.e, listOf(
                            ta, Pt(ta.x, yJ), Pt(tb.x, yJ), tb))
                        l.e.label?.let {
                            labels += RoutedLabel(it, (ta.x + tb.x) / 2, yJ - 2f)
                        }
                    }
                    // 相邻展示行：较低者上端口进行间隙，较高者下端口接入
                    abs(ra - rb) == 1 -> {
                        val (upper, lower) =
                            if (ra < rb) l.a to l.b else l.b to l.a
                        val lowerRow = max(ra, rb)
                        gapEdges.getOrPut(bi to lowerRow) { mutableListOf() } +=
                            l.copy(a = lower, b = upper)
                    }
                    // 跨多个展示行：侧廊 + 行上空档
                    else -> gapEdges.getOrPut(bi to max(ra, rb)) { mutableListOf() } += l
                }
            } else {
                // 上下 band 相邻：同通道一个车道；否则走中间代际空列
                val cu = channelBelow(ga); val cl = channelAbove(gb)
                if (cu != null && cu == cl) {
                    adjacentEdges.getOrPut(cu) { mutableListOf() } += l
                } else if (cu != null && cl != null) {
                    spans += Span(l, cu, cl)
                    spanUpper[cu] = (spanUpper[cu] ?: 0) + 1
                    spanLower[cl] = (spanLower[cl] ?: 0) + 1
                }
            }
        }
        // 每通道车道总数 = 单元道数 + 各类松边道数
        val laneTotal = IntArray(chTop.size) { i ->
            unitLaneCount[i] +
                (adjacentEdges[i]?.size ?: 0) +
                (spanUpper[i] ?: 0) + (spanLower[i] ?: 0)
        }
        fun laneY(ch: Int, lane: Int) =
            chTop[ch] + chH[ch] * (lane + 1) / (laneTotal[ch] + 1)

        // ---------- 家庭单元路径 ----------
        /** 竖线 (x, y0..y1) 不穿任何卡片内部（允许贴边/共用端口）。 */
        fun vertFree(x: Float, y0: Float, y1: Float): Boolean {
            val lo = minOf(y0, y1); val hi = maxOf(y0, y1)
            for (n in model.nodes) {
                if (x <= n.x + 1f || x >= n.right - 1f) continue
                if (n.y + 1f < hi && n.bottom - 1f > lo) return false
            }
            return true
        }
        /** 横线 (y, x0..x1) 不穿任何卡片内部。 */
        fun horizFree(y: Float, x0: Float, x1: Float): Boolean {
            val lo = minOf(x0, x1); val hi = maxOf(x0, x1)
            for (n in model.nodes) {
                if (y <= n.y + 1f || y >= n.bottom - 1f) continue
                if (n.x + 1f < hi && n.right - 1f > lo) return false
            }
            return true
        }
        /** 父块所在展示行的行间隙 y：该行全部卡片底缘之下、下一行之上，横移必然无卡。 */
        fun rowGapY(n: GraphNode): Float {
            val band = bandByGen[n.generation] ?: return n.bottom + JOG
            val row = model.blockOf(n.personId)?.row ?: 0
            return band.rowBottom(row) + JOG
        }

        for (pu in placed) {
            val ly = laneY(pu.channel, pu.lane)
            val unit = pu.unit
            val busXs = ArrayList<Float>()
            // ---- 父母侧：主干在卡片横向包围内直落车道；仅受阻才经本块侧廊 ----
            val pNodes = unit.parentIds.mapNotNull { nodeById[it] }
            if (pNodes.isEmpty()) continue
            val corridorMemo = mutableMapOf<Float, Float>()
            fun unitCorridor(id: String): Float {
                val base = corridorX(id) ?: nodeById.getValue(id).cx
                return corridorMemo.getOrPut(base) {
                    val y0 = pNodes.minOf { it.y }
                    val y1 = unit.childIds.mapNotNull { nodeById[it] }.maxOf { it.y }
                    // 同列跨行时，不同家庭不能重叠使用一根竖线而形成伪接点。
                    listOf(0f, -4f, 4f, -8f, 8f).map { base + it }.firstOrNull { candidate ->
                        vertFree(candidate, y0, y1) && paths.none { path ->
                            path.points.zipWithNext().any { (a, b) ->
                                abs(a.x - b.x) < 0.01f && abs(a.x - candidate) < 2f &&
                                    minOf(a.y, b.y) < y1 && maxOf(a.y, b.y) > y0
                            }
                        }
                    } ?: base
                }
            }
            var stemX: Float
            if (pNodes.size == 2 &&
                model.blockOf(pNodes[0].personId)?.id ==
                model.blockOf(pNodes[1].personId)?.id) {
                // 同块夫妻：主干从配偶线中点垂直落到车道
                val (l, r) = if (pNodes[0].x <= pNodes[1].x)
                    pNodes[0] to pNodes[1] else pNodes[1] to pNodes[0]
                val mid = (l.right + r.x) / 2f
                val y0 = min(l.cy, r.cy)
                if (vertFree(mid, maxOf(l.bottom, r.bottom), ly)) {
                    stemX = mid
                    add(RouteStyle.SOLID, null, listOf(Pt(mid, y0), Pt(mid, ly)))
                } else {
                    // 折行受阻：中点下桩 → 行间隙横移 → 本块侧廊直落车道
                    val corr = unitCorridor(l.personId)
                    val jy = rowGapY(l)
                    add(RouteStyle.SOLID, null, listOf(
                        Pt(mid, y0), Pt(mid, jy), Pt(corr, jy), Pt(corr, ly)))
                    stemX = corr
                }
            } else if (pNodes.size == 2) {
                // 不同块的两位已知父母：各自下端口垂落 + 水平连接段并入；
                // 横段放在两位父母所在行底缘之下（同行可能有更高的配偶卡），
                // 任一受阻则各自经本块侧廊直落车道
                val joinY = pNodes.maxOf { rowGapY(it) }
                val xs = pNodes.map { it.cx }.sorted()
                val mid = (xs[0] + xs[1]) / 2f
                val direct = pNodes.all { vertFree(it.cx, it.bottom, joinY) } &&
                    horizFree(joinY, xs[0], xs[1]) &&
                    vertFree(mid, joinY, ly)
                if (direct) {
                    for (n in pNodes) {
                        add(RouteStyle.SOLID, null,
                            listOf(botC(n), Pt(n.cx, joinY)))
                    }
                    add(RouteStyle.SOLID, null,
                        listOf(Pt(xs[0], joinY), Pt(xs[1], joinY)))
                    stemX = mid
                    add(RouteStyle.SOLID, null,
                        listOf(Pt(stemX, joinY), Pt(stemX, ly)))
                } else {
                    val lands = ArrayList<Float>()
                    for (n in pNodes) {
                        val corr = unitCorridor(n.personId)
                        val jy = rowGapY(n)
                        add(RouteStyle.SOLID, null, listOf(
                            botC(n), Pt(n.cx, jy), Pt(corr, jy), Pt(corr, ly)))
                        lands += corr
                    }
                    stemX = (lands.min() + lands.max()) / 2f
                }
            } else {
                // 单亲：该人下端口垂直落到车道；受阻则经本块侧廊
                val n = pNodes[0]
                if (vertFree(n.cx, n.bottom, ly)) {
                    stemX = n.cx
                    add(RouteStyle.SOLID, null, listOf(botC(n), Pt(stemX, ly)))
                } else {
                    val corr = unitCorridor(n.personId)
                    val jy = rowGapY(n)
                    add(RouteStyle.SOLID, null, listOf(
                        botC(n), Pt(n.cx, jy), Pt(corr, jy), Pt(corr, ly)))
                    stemX = corr
                }
            }
            // ---- 子女：行内直落卡上端口；折行受阻才走本 block 侧廊 ----
            for (cid in unit.childIds) {
                val n = nodeById[cid] ?: continue
                val t = topC(n)
                if (dropBlocked(n, model, pu.channel, chTop)) {
                    val cxr = unitCorridor(cid)
                    add(RouteStyle.SOLID, null, listOf(
                        Pt(cxr, ly), Pt(cxr, t.y - JOG),
                        Pt(t.x, t.y - JOG), t))
                    busXs += cxr
                } else {
                    add(RouteStyle.SOLID, null, listOf(Pt(t.x, ly), t))
                    busXs += t.x
                }
            }
            busXs += stemX
            // 总线：车道内覆盖主干落点与全部子女落点
            add(RouteStyle.SOLID, null,
                listOf(Pt(busXs.min(), ly), Pt(busXs.max(), ly)))
            // 关系词落在主干段中点（默认不显示，仅“关系词”开启时出）
            for (e in unit.edges) {
                val lbl = e.label ?: continue
                val pid = if (e.type == RelationshipType.PARENT)
                    e.fromId else e.toId
                val n = nodeById[pid] ?: continue
                labels += RoutedLabel(lbl, stemX, n.bottom + JOG +
                        (if (unit.edges.size > 1 &&
                            pid != unit.parentIds[0]) 10f else 0f))
            }
        }

        // ---------- 松边路径（侧廊/车道） ----------
        fun emitLoose(l: Loose, laneYv: Float) {
            val aC = corridorX(l.a.personId) ?: return
            val bC = corridorX(l.b.personId) ?: return
            val ta = topC(l.a); val tb = topC(l.b); val ba = botC(l.a)
            val pts = if (l.a.y < l.b.y) {
                // a 下端口 → 行间隙 → a 侧廊 → 车道 → b 侧廊 → b 上端口
                val jy = rowGapY(l.a)
                listOf(ba, Pt(ba.x, jy), Pt(aC, jy), Pt(aC, laneYv),
                    Pt(bC, laneYv), Pt(bC, tb.y - JOG), Pt(tb.x, tb.y - JOG), tb)
            } else {
                // 同/较低展示行：a 上端口 → a 侧廊 → 车道 → b 侧廊 → b 上端口
                listOf(ta, Pt(ta.x, ta.y - JOG), Pt(aC, ta.y - JOG),
                    Pt(aC, laneYv), Pt(bC, laneYv),
                    Pt(bC, tb.y - JOG), Pt(tb.x, tb.y - JOG), tb)
            }
            add(styleOf(l.e), l.e, pts)
            l.e.label?.let {
                labels += RoutedLabel(it, (bC + tb.x) / 2, tb.y - JOG - 2f)
            }
        }
        // 相邻展示行松边：行间隙局部横道（较低者上端口，较高者下端口）
        for ((key, es) in gapEdges) {
            val (bi, row) = key
            val band = model.bands[bi]
            // 该行上方的行间隙：rowBottom(row-1)..rowTops[row]
            val gapTop = band.rowBottom(row - 1)
            val gapH = band.rowTops[row] - gapTop
            es.forEachIndexed { i, l ->
                val gy = gapTop + gapH * (i + 1) / (es.size + 1)
                if (rowOf(l.b.personId) == row - 1 &&
                    rowOf(l.a.personId) == row) {
                    // 相邻行：上者下端口 → 行间隙 → 下者上端口
                    val bu = botC(l.b); val ta = topC(l.a)
                    add(styleOf(l.e), l.e, listOf(
                        bu, Pt(bu.x, gy), Pt(ta.x, gy), ta))
                    l.e.label?.let {
                        labels += RoutedLabel(it, (bu.x + ta.x) / 2, gy - 2f)
                    }
                } else {
                    // 跨多行：两侧廊 + 行间隙横道
                    emitLoose(l, gy)
                }
            }
        }
        // 通道车道：松边排在单元总线之后
        for ((ch, es) in adjacentEdges) {
            es.forEachIndexed { i, l ->
                emitLoose(l, laneY(ch, unitLaneCount[ch] + i))
            }
        }
        // 跨多 band：上通道车道 → “中间代际的内部空列” → 下通道车道
        val marginLeftBase = model.nodes.minOfOrNull { it.x } ?: 0f
        val marginRightBase = model.nodes.maxOfOrNull { it.right } ?: 0f
        // 在 band 索引 (iA+1 .. iB-1) 范围内找一条不穿卡的竖线 x
        fun freeColumnX(iA: Int, iB: Int, near: Float): Float {
            val ivals = ArrayList<Pair<Float, Float>>()
            for (n in model.nodes) {
                val bi = bandIdx[n.generation] ?: continue
                if (bi in (iA + 1) until iB) {
                    ivals += (n.x - 4f) to (n.right + 4f)
                }
            }
            if (ivals.isEmpty()) return near
            ivals.sortBy { it.first }
            var best: Float? = null; var bestDist = Float.MAX_VALUE
            var end = ivals[0].second
            val gaps = ArrayList<Pair<Float, Float>>()
            for (iv in ivals.drop(1)) {
                if (iv.first <= end) { end = maxOf(end, iv.second); continue }
                gaps += end to iv.first
                end = iv.second
            }
            // 首尾之外的空档也计入（仍在中间代际卡片范围内，不算图外缘）
            gaps += marginLeftBase - 6f to ivals[0].first
            gaps += ivals.last().second to marginRightBase + 6f
            for ((g0, g1) in gaps) {
                if (g1 - g0 < 6f) continue
                val mid = (g0 + g1) / 2f
                val d = abs(mid - near)
                if (d < bestDist) { bestDist = d; best = mid }
            }
            return best ?: near
        }
        val upperSeq = HashMap<Int, Int>(); val lowerSeq = HashMap<Int, Int>()
        for (s in spans) {
            val aC = corridorX(s.loose.a.personId) ?: continue
            val bC = corridorX(s.loose.b.personId) ?: continue
            val ta = topC(s.loose.a); val tb = topC(s.loose.b)
            val iA = bandIdx[s.loose.a.generation] ?: continue
            val iB = bandIdx[s.loose.b.generation] ?: continue
            val colX = freeColumnX(iA, iB, (aC + bC) / 2f)
            val iU = upperSeq.getOrDefault(s.upperCh, 0); upperSeq[s.upperCh] = iU + 1
            val iL = lowerSeq.getOrDefault(s.lowerCh, 0); lowerSeq[s.lowerCh] = iL + 1
            val yU = laneY(s.upperCh,
                unitLaneCount[s.upperCh] + (adjacentEdges[s.upperCh]?.size ?: 0) + iU)
            val yL = laneY(s.lowerCh,
                unitLaneCount[s.lowerCh] + (adjacentEdges[s.lowerCh]?.size ?: 0) +
                    (spanUpper[s.lowerCh] ?: 0) + iL)
            add(styleOf(s.loose.e), s.loose.e, listOf(
                ta, Pt(aC, ta.y - JOG), Pt(aC, yU),
                Pt(colX, yU), Pt(colX, yL),
                Pt(bC, yL), Pt(bC, tb.y - JOG), Pt(tb.x, tb.y - JOG), tb))
            s.loose.e.label?.let {
                labels += RoutedLabel(it, (bC + tb.x) / 2, tb.y - JOG - 2f)
            }
        }

        // ---------- 展开芯片的路由级兜底 ----------
        // 布局已按“不遮挡卡片/不压亲子主干/不超出卡片包围”选位；
        // 这里再剔除与任何路径段相交（含 2dp 余量）的芯片——侧廊、
        // 行间隙横道等只在受阻时才生成，布局阶段无法预知。
        // 被剔除芯片的分组与人数仍保留在“展开家人”面板。
        fun segHitsRect(p0: Pt, p1: Pt, r: FloatRect): Boolean {
            if (p0.x == p1.x) {
                if (p0.x < r.left || p0.x > r.right) return false
                val lo = minOf(p0.y, p1.y); val hi = maxOf(p0.y, p1.y)
                return lo <= r.bottom && hi >= r.top
            }
            if (p0.y == p1.y) {
                if (p0.y < r.top || p0.y > r.bottom) return false
                val lo = minOf(p0.x, p1.x); val hi = maxOf(p0.x, p1.x)
                return lo <= r.right && hi >= r.left
            }
            return false // 路由仅产生水平/垂直段
        }
        val chips = model.expandChips.filterNot { c ->
            val r = FloatRect(c.x - 2f, c.y - 2f, c.right + 2f, c.bottom + 2f)
            paths.any { p ->
                (1 until p.points.size).any { i ->
                    segHitsRect(p.points[i - 1], p.points[i], r)
                }
            }
        }
        for (c in chips) {
            grow(c.x - 1f, c.y - 1f); grow(c.right + 1f, c.bottom + 1f)
        }

        if (minX > maxX) { minX = 0f; maxX = 1f; minY = 0f; maxY = 1f }
        if (nMinX > nMaxX) { nMinX = 0f; nMaxX = 1f; nMinY = 0f; nMaxY = 1f }
        // T 形端点属于真实家庭总线，不加桥；只有两条线的内部交叉才标记。
        // 保留夫妻顺序时，完整家谱可能无法消除所有交叉，跨线不得变成伪接点。
        val crossings = mutableListOf<Crossing>()
        for (h in paths) for ((a, b) in h.points.zipWithNext()) {
            if (abs(a.y - b.y) > 0.01f || abs(a.x - b.x) < 0.01f) continue
            for (v in paths) for ((c, d) in v.points.zipWithNext()) {
                if (h === v || abs(c.x - d.x) > 0.01f) continue
                val x = c.x; val y = a.y
                if (x <= minOf(a.x, b.x) + 0.01f || x >= maxOf(a.x, b.x) - 0.01f ||
                    y <= minOf(c.y, d.y) + 0.01f || y >= maxOf(c.y, d.y) - 0.01f) continue
                if (crossings.any { abs(it.x - x) < 0.1f && abs(it.y - y) < 0.1f }) continue
                val radius = minOf(4f, abs(x - a.x) / 2, abs(x - b.x) / 2,
                    abs(y - c.y) / 2, abs(y - d.y) / 2)
                crossings += Crossing(x, y, radius, h.style)
            }
        }
        return Routing(
            paths, labels, chips,
            Bounds(minX - 4f, minY - 4f, maxX + 4f, maxY + 4f),
            Bounds(nMinX - 4f, nMinY - 4f, nMaxX + 4f, nMaxY + 4f),
            crossings
        )
    }

    /**
     * 子女从车道直落其上端口是否被卡片阻挡：
     * 检查 (车道 y, 卡顶 y) 范围内有无卡片的横向区间覆盖该子女卡心。
     * 行内子女上方是通道，永不受阻；折行子女头顶有上排卡时才受阻。
     */
    private fun dropBlocked(
        n: GraphNode, model: GraphModel, channel: Int,
        chTop: FloatArray
    ): Boolean {
        val row = model.blockOf(n.personId)?.row ?: 0
        if (row == 0) return false
        val ly = chTop.getOrElse(channel) { n.y - GraphGeom.CHANNEL }
        val cx = n.cx
        for (o in model.nodes) {
            if (o === n) continue
            if (o.y + 1f >= n.y) continue           // 不在其上方的卡不算
            if (o.bottom <= ly + 1f) continue        // 在通道之上也不算
            if (cx > o.x + 2f && cx < o.right - 2f) return true
        }
        return false
    }
}
