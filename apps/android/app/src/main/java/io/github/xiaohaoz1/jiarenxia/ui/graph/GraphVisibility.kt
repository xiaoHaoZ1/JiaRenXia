package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview

/**
 * 关系图可见层（图谱视觉改版）：决定哪些人物入图、
 * 哪些由“展开”动作逐层揭示。纯计算，可 JVM 单测。
 *
 * 与分享/碰一碰的选人预设（[GraphBranches.visibleIds]）完全隔离：
 * 收起/展开只影响这张图，不改变分享人数；
 * 图谱的候选全集是“双方可达人物全集”（generations 的键集）。
 *
 * 展开是**方向化**的：[Expand] = 锚点 + 方向，便于
 * “长辈 N 位”这类芯片只揭示该方向的家庭块，不会顺带带出无关旁支；
 * 画布芯片（[Chip]）挂在可见家庭块的边缘，面板分组（[Group]）
 * 按“谁的家人”聚合供底部面板列出。
 */
object GraphVisibility {

    /** 展开方向：UP 上一辈 / SAME 同辈 / DOWN 下一辈；ALL 全方向（查找路径用）。 */
    enum class Dir { UP, SAME, DOWN, ALL }

    /** 一次展开动作：锚点人物 + 方向。 */
    data class Expand(val anchor: String, val dir: Dir)

    /** 画布芯片：挂在某个可见家庭块边缘的“可展开”入口。 */
    data class Chip(
        /** 依附的可见家庭块 id（FamilyBlock.id）。 */
        val blockId: String,
        /** 揭示方向，决定芯片贴边位置（上/同辈两侧/下）。 */
        val dir: Dir,
        /** 点击后将新增的人数（人物去重）。 */
        val count: Int,
        /** 点击时应加入展开集合的键。 */
        val expands: Set<Expand>,
        /** 该芯片将揭示的人物 id（用于落点归属侧判断）。 */
        val reveals: Set<String>
    )

    /** “展开家人”底部面板的一行：按 owner + 方向聚合。 */
    data class Group(
        /** 行文案，如“我的兄弟姐妹”“配偶的长辈”“大伯的家人”。 */
        val label: String,
        /** 本组对应的展开键集合。 */
        val expands: Set<Expand>,
        /** 还能新增的人数（组间人物去重；0 且已展开时显示“已展开·定位”）。 */
        val addCount: Int,
        /** 本组揭示的人物当前已全部可见。 */
        val expanded: Boolean,
        /** “已展开·定位”时居中的人物（组内第一个成员）。 */
        val focusId: String
    )

    data class Result(
        /** 当前应入图的人物 id。 */
        val visibleIds: Set<String>,
        /** 画布上的展开芯片（依附 block 与方向）。 */
        val chips: List<Chip>,
        /** 底部面板分组（按 owner+方向聚合、人物去重）。 */
        val groups: List<Group>,
        /** 当前被收起的家人数。 */
        val collapsedCount: Int
    )

    /**
     * 默认可见：主锚（self 与伴侣锚点）、双方已知父母、
     * 主锚的直接子女。祖辈、兄弟姐妹与旁支都在展开之后。
     */
    fun defaultIds(overview: FamilyOverview, anchors: GraphStructure.Anchors): Set<String> {
        val g = overview.graph
        val out = LinkedHashSet<String>()
        out += anchors.ids
        for (a in anchors.ids) {
            out += GraphStructure.parentsOf(g, a)
            out += GraphStructure.childrenOf(g, a)
        }
        return out
    }

    /** 全部候选 id（双方可达人物全集）。 */
    private fun candidates(overview: FamilyOverview, gens: Map<String, Int>): Set<String> =
        gens.keys.filter { it in overview.byId }.toSet()

    /**
     * 一次展开揭示的人：锚点在 [dir] 方向上每个可达邻居所在的完整家庭 block。
     * 邻居已可见时其 block 仍展开（把未可见的另一半带出来）；
     * 更深的旁支留给下一轮展开。
     */
    private fun reveal(
        anchor: String,
        dir: Dir,
        gens: Map<String, Int>,
        adjacency: Map<String, Set<String>>,
        fullBlocks: Map<String, GraphStructure.Block>
    ): Set<String> {
        val ga = gens[anchor] ?: return emptySet()
        val out = LinkedHashSet<String>()
        for (nb in adjacency[anchor].orEmpty()) {
            if (nb == anchor) continue
            val gb = gens[nb] ?: continue
            val ok = when (dir) {
                Dir.UP -> gb < ga
                Dir.DOWN -> gb > ga
                Dir.SAME -> gb == ga
                Dir.ALL -> true
            }
            if (!ok) continue
            val b = fullBlocks[nb] ?: continue
            out += b.memberIds
        }
        return out
    }

    /** 展示行方向次序：同辈在前，其次长辈、晚辈（面板阅读顺序）。 */
    private fun dirRank(d: Dir) = when (d) {
        Dir.SAME -> 0; Dir.UP -> 1; Dir.DOWN -> 2; Dir.ALL -> 3
    }

    fun compute(
        overview: FamilyOverview,
        gens: Map<String, Int>,
        expanded: Set<Expand>,
        focusedId: String? = null
    ): Result {
        val all = candidates(overview, gens)
        val anchors = GraphStructure.anchorsOf(overview.graph, overview.self.person.id)
        val adjacency = GraphStructure.adjacency(overview.graph)
        // block 在“双方可达全集”上计算：展开揭示的是完整家庭单元
        val blockOf = HashMap<String, GraphStructure.Block>()
        for (b in GraphStructure.blocksOf(overview.graph, gens, all, overview.byId)) {
            for (m in b.memberIds) blockOf[m] = b
        }

        val visible = LinkedHashSet<String>()
        visible += defaultIds(overview, anchors) intersect all
        // 展开迭代到不动点：expanded 的顺序无关——先被带出来的锚点
        // （如经别人 block 露出的配偶）同样要把自己的邻居揭示出来
        var changed = true
        while (changed) {
            changed = false
            for (e in expanded) {
                if (e.anchor in visible &&
                    visible.addAll(
                        reveal(e.anchor, e.dir, gens, adjacency, blockOf) intersect all)
                ) changed = true
            }
        }
        // 收起不得隐藏主锚/当前聚焦人物：聚焦人物连同所在 block 保留
        if (focusedId != null && focusedId in all && focusedId !in visible) {
            blockOf[focusedId]?.let { visible += it.memberIds } ?: run { visible += focusedId }
        }
        visible.retainAll(all)

        // ---------- 画布芯片：可见 block × 方向 ----------
        // 依附的 block 按“当前可见集”划分，与布局中的 block 一一对应
        val visBlockOf = HashMap<String, GraphStructure.Block>()
        for (b in GraphStructure.blocksOf(
            overview.graph, gens, visible, overview.byId)) {
            for (m in b.memberIds) visBlockOf[m] = b
        }
        val chips = ArrayList<Chip>()
        val chipKeys = HashMap<Pair<String, Dir>, Pair<MutableSet<Expand>, MutableSet<String>>>()
        for (v in visible) {
            val vb = visBlockOf[v] ?: continue
            for (d in listOf(Dir.UP, Dir.SAME, Dir.DOWN)) {
                val add = (reveal(v, d, gens, adjacency, blockOf) intersect all) - visible
                if (add.isEmpty()) continue
                val key = vb.id to d
                val (keys, ids) = chipKeys.getOrPut(key) {
                    mutableSetOf<Expand>() to mutableSetOf()
                }
                keys += Expand(v, d)
                ids += add
            }
        }
        for ((bd, pair) in chipKeys) {
            val (keys, ids) = pair
            chips += Chip(bd.first, bd.second, ids.size, keys.toSet(),
                ids.toSet())
        }
        chips.sortWith(compareBy({ it.blockId }, { dirRank(it.dir) }))

        // ---------- 面板分组：owner 称谓 + 方向 ----------
        // 每个“可展开的可见锚点 × 方向”是一组；组间人物去重计数。
        data class Acc(
            val label: String, val expands: MutableSet<Expand>,
            val members: LinkedHashSet<String>, val dir: Dir, val ownerRank: Int
        )
        val groupAcc = LinkedHashMap<Pair<String, Dir>, Acc>()
        val selfId = overview.self.person.id
        val partnerId = anchors.partnerId
        val partnerTitle = partnerId?.let { overview.byId[it]?.displayTitle } ?: "配偶"
        // UP 方向的 owner：block 成员的某个可见子女是主锚时，归到该主锚名下
        fun upOwner(v: String): Pair<String, Int> {
            val kids = GraphStructure.childrenOf(overview.graph, v)
                .filter { it in visible }
            for (k in kids) {
                if (k == selfId) return "我" to 0
                if (k == partnerId) return partnerTitle to 1
            }
            return (overview.byId[v]?.displayTitle ?: "家人") to 2
        }
        fun selfOwner(v: String): Pair<String, Int> = when (v) {
            selfId -> "我" to 0
            partnerId -> partnerTitle to 1
            else -> (overview.byId[v]?.displayTitle ?: "家人") to 2
        }
        // 按确定性顺序遍历锚点：先靠近主锚（|代差|小）、再按代际与 id
        val ordered = visible.sortedWith(
            compareBy({ kotlin.math.abs(gens[it] ?: 0) }, { gens[it] ?: 0 }, { it }))
        for (v in ordered) {
            for (d in listOf(Dir.SAME, Dir.UP, Dir.DOWN)) {
                val members = LinkedHashSet<String>()
                members += reveal(v, d, gens, adjacency, blockOf) intersect all
                if (members.isEmpty()) continue
                val expandedKey = Expand(v, d)
                val wasExpanded = expandedKey in expanded
                val hasNew = members.any { it !in visible }
                if (!wasExpanded && !hasNew) continue   // 零增量且无展开态不列出
                val (owner, rank) = when (d) {
                    Dir.UP -> upOwner(v)
                    Dir.SAME -> selfOwner(v)
                    else -> selfOwner(v)
                }
                val noun = when (d) {
                    Dir.UP -> "的长辈"
                    Dir.SAME -> "的兄弟姐妹"
                    else -> "的家人"
                }
                val key = (owner + noun) to d
                val acc = groupAcc.getOrPut(key) {
                    Acc(owner + noun, mutableSetOf(), LinkedHashSet(), d, rank)
                }
                acc.expands += expandedKey
                acc.members += members
            }
        }
        // 组间去重：同一人物只算进最先遇到的一组
        val counted = HashSet<String>()
        val groups = ArrayList<Group>()
        for (acc in groupAcc.values.sortedWith(
            compareBy({ dirRank(it.dir) }, { it.ownerRank }, { it.label }))) {
            val fresh = acc.members.filter { it !in visible && it !in counted }
            counted += acc.members
            val allShown = acc.members.all { it in visible }
            if (!allShown && fresh.isEmpty()) continue   // 新增量被更早的组占满
            groups += Group(
                label = acc.label,
                expands = acc.expands.toSet(),
                addCount = fresh.size,
                expanded = allShown,
                focusId = acc.members.first()
            )
        }

        return Result(
            visibleIds = visible,
            chips = chips,
            groups = groups,
            collapsedCount = (all - visible).size
        )
    }

    /**
     * 查找定位：为让 [target] 入图需要追加的展开键集合。
     * 沿连接路径逐层展开（每层揭示该锚点全部方向的家庭单元），
     * 直到目标可见；目标本就不可达时返回原展开集（调用方按未关联提示）。
     */
    fun expandPathTo(
        overview: FamilyOverview,
        gens: Map<String, Int>,
        expanded: Set<Expand>,
        target: String
    ): Set<Expand> {
        val all = candidates(overview, gens)
        if (target !in all) return expanded
        val adjacency = GraphStructure.adjacency(overview.graph)
        val blockOf = HashMap<String, GraphStructure.Block>()
        for (b in GraphStructure.blocksOf(overview.graph, gens, all, overview.byId)) {
            for (m in b.memberIds) blockOf[m] = b
        }
        val out = expanded.toMutableSet()
        var guard = 0
        while (guard++ < all.size + 2) {
            val visible = compute(overview, gens, out).visibleIds
            if (target in visible) return out
            // 从目标 BFS 找最近的可见节点，把它全方向展开一层
            val q = ArrayDeque<String>()
            val seen = HashSet<String>()
            q += target; seen += target
            var hit: String? = null
            while (q.isNotEmpty() && hit == null) {
                val n = q.removeFirst()
                for (nb in adjacency[n].orEmpty()) {
                    if (nb in seen) continue
                    if (nb in visible) { hit = nb; break }
                    seen += nb; q += nb
                }
            }
            if (hit == null) return out   // 理论上不可达（all 已过滤）
            out += Expand(hit, Dir.ALL)
        }
        return out
    }

    /** 全部展开（等价于可见全集；保留为显式操作语义）。 */
    fun expandAll(overview: FamilyOverview, gens: Map<String, Int>): Set<Expand> {
        val all = candidates(overview, gens)
        return all.mapTo(LinkedHashSet()) { Expand(it, Dir.ALL) }
    }
}
