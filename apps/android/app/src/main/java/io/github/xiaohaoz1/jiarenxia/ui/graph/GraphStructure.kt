package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview

/** 图谱左右归属：男方侧（左）/ 居中（主锚、子孙与无侧归属者）/ 女方侧（右）。 */
enum class GraphSide { LEFT, CENTER, RIGHT }

/**
 * 关系图结构分析（DEVELOPMENT_PLAN 图谱改版）：主锚解析、邻接、
 * 家庭 block 划分与左右归属 BFS。纯计算，可 JVM 单测。
 *
 * 这些结果只描述“怎么摆”，不写任何人物关系；
 * 与分享/碰一碰的选人预设（[GraphBranches]）完全独立。
 */
object GraphStructure {

    /**
     * 主锚：self 与伴侣锚点。显式配偶优先；无配偶时可用唯一的
     * 明确共同父母（同一孩子的另一位已记录父母）作布局组合；
     * 都没有则 self 单人居中。多条配偶记录取排序后第一条，
     * 其余配偶关系保留为独立边（异常数据不丢人不猜人）。
     */
    data class Anchors(
        val selfId: String,
        val partnerId: String?,
        /** partner 是显式配偶（true）还是共同父母组合（false）。 */
        val partnerIsSpouse: Boolean
    ) {
        val ids: List<String> get() = listOfNotNull(selfId, partnerId)
    }

    fun anchorsOf(graph: FamilyGraph, selfId: String): Anchors {
        val spouse = graph.relationships.asSequence()
            .filter { it.type == RelationshipType.SPOUSE }
            .mapNotNull {
                when (selfId) {
                    it.sourcePersonId -> it.targetPersonId
                    it.targetPersonId -> it.sourcePersonId
                    else -> null
                }
            }
            .distinct().sorted().firstOrNull()
        if (spouse != null) return Anchors(selfId, spouse, true)
        // 共同父母组合：仅当恰好一位“孩子的另一位父母”时启用
        val kids = childrenOf(graph, selfId)
        val coParents = kids.flatMap { kid ->
            parentsOf(graph, kid).filter { it != selfId }
        }.distinct()
        return if (coParents.size == 1) {
            Anchors(selfId, coParents.first(), false)
        } else {
            Anchors(selfId, null, false)
        }
    }

    /** 某人的已记录父母（PARENT 的 source；CHILD 的 target）。 */
    fun parentsOf(graph: FamilyGraph, id: String): List<String> =
        graph.relationships.mapNotNull {
            when {
                it.type == RelationshipType.PARENT && it.targetPersonId == id ->
                    it.sourcePersonId
                it.type == RelationshipType.CHILD && it.sourcePersonId == id ->
                    it.targetPersonId
                else -> null
            }
        }.distinct()

    /** 某人的已记录子女（PARENT 的 target；CHILD 的 source）。 */
    fun childrenOf(graph: FamilyGraph, id: String): List<String> =
        graph.relationships.mapNotNull {
            when {
                it.type == RelationshipType.PARENT && it.sourcePersonId == id ->
                    it.targetPersonId
                it.type == RelationshipType.CHILD && it.targetPersonId == id ->
                    it.sourcePersonId
                else -> null
            }
        }.distinct()

    /**
     * 全图邻接：所有关系类型都算连通（PARENT/CHILD 互为反向，
     * 申报 COUSIN 也是真实连接）。仅用于可达性与归属判断。
     */
    fun adjacency(graph: FamilyGraph): Map<String, Set<String>> {
        val adj = HashMap<String, MutableSet<String>>()
        for (r in graph.relationships) {
            adj.getOrPut(r.sourcePersonId) { mutableSetOf() }.add(r.targetPersonId)
            adj.getOrPut(r.targetPersonId) { mutableSetOf() }.add(r.sourcePersonId)
        }
        return adj
    }

    /**
     * 家庭 block：同一真实世代内的两人组合或单人。
     * - 显式夫妻优先：可见的同代配偶对组成不可拆 block；
     * - 其次同代明确共同父母（同一孩子的两位已记录父母）；
     * - 每人最多进一个 block；多余配对/跨代配对不组合，
     *   保持各自 generation 与独立节点，关系由独立边表达。
     */
    data class Block(
        /** 成员 id（1~2 人）；双人时按 男左女右 排好序。 */
        val memberIds: List<String>,
        val generation: Int
    ) {
        val id: String get() = memberIds.joinToString("+")
    }

    fun blocksOf(
        graph: FamilyGraph,
        gens: Map<String, Int>,
        ids: Set<String>,
        byId: Map<String, io.github.xiaohaoz1.jiarenxia.domain.PersonOverview>
    ): List<Block> {
        val paired = HashSet<String>()
        val out = ArrayList<Block>()
        fun genderOf(id: String): Gender =
            byId[id]?.person?.gender ?: Gender.MALE
        // 双人成员排序：男左女右；同性/未知按 id 稳定序
        fun pairOrder(a: String, b: String): List<String> {
            val ga = genderOf(a); val gb = genderOf(b)
            return when {
                ga == Gender.MALE && gb != Gender.MALE -> listOf(a, b)
                gb == Gender.MALE && ga != Gender.MALE -> listOf(b, a)
                else -> listOf(a, b).sorted()
            }
        }
        // 1) 显式夫妻（同真实世代才可成 block）
        for (r in graph.relationships) {
            if (r.type != RelationshipType.SPOUSE) continue
            val a = r.sourcePersonId; val b = r.targetPersonId
            if (a !in ids || b !in ids) continue
            if (a in paired || b in paired) continue
            if (gens[a] != gens[b]) continue   // 跨代异常配对各自独立
            out += Block(pairOrder(a, b), gens.getValue(a))
            paired += a; paired += b
        }
        // 2) 明确共同父母（两位未配对者同为某孩子父母）
        val parentsOfChild = HashMap<String, MutableList<String>>()
        for (r in graph.relationships) {
            when (r.type) {
                RelationshipType.PARENT ->
                    parentsOfChild.getOrPut(r.targetPersonId) { mutableListOf() }
                        .add(r.sourcePersonId)
                RelationshipType.CHILD ->
                    parentsOfChild.getOrPut(r.sourcePersonId) { mutableListOf() }
                        .add(r.targetPersonId)
                else -> Unit
            }
        }
        for ((_, parents) in parentsOfChild) {
            val candidates = parents.distinct()
                .filter { it in ids && it !in paired }
            for (i in candidates.indices) for (j in i + 1 until candidates.size) {
                val a = candidates[i]; val b = candidates[j]
                if (a in paired || b in paired) continue
                if (gens[a] == null || gens[a] != gens[b]) continue
                out += Block(pairOrder(a, b), gens.getValue(a))
                paired += a; paired += b
            }
        }
        // 3) 其余单人
        for (id in ids) {
            if (id in paired) continue
            val g = gens[id] ?: continue
            out += Block(listOf(id), g)
        }
        return out
    }

    /** 归属结果：侧、到本侧根的步数、BFS 树父（同侧排序用）。 */
    data class Assignment(
        val side: GraphSide,
        val dist: Int,
        val treeParent: String?
    )

    /**
     * 左右归属：以主锚男/女分别为左/右根做双源 BFS。
     * - 排除主锚之间的夫妻边，且不把主锚直接子女当通道
     *   （防止经共同子女绕到另一根；子孙统一居中）；
     * - 单根可达归该侧；双根可达取更近；等距固定归 LEFT（男方侧）；
     * - 不可达（只经居中子孙连接者）归 CENTER。
     * 无伴侣锚：self 与通过 self 连接者（兄弟姐妹/子孙）居中，
     * 父亲路径 LEFT、母亲路径 RIGHT——等价于以已知父母为左右根、
     * self 不作通道。
     */
    fun assign(
        graph: FamilyGraph,
        anchors: Anchors,
        byId: Map<String, io.github.xiaohaoz1.jiarenxia.domain.PersonOverview>
    ): Map<String, Assignment> {
        val adj = adjacency(graph)
        // 主锚直接子女：可达但不再是通道
        val directChildren = anchors.ids
            .flatMap { childrenOf(graph, it) }.toSet()
        val blocked = HashSet<Pair<String, String>>()
        if (anchors.partnerId != null && anchors.partnerIsSpouse) {
            blocked += anchors.selfId to anchors.partnerId
            blocked += anchors.partnerId to anchors.selfId
        }
        fun expands(node: String): List<String> =
            if (node in directChildren) emptyList()
            else adj[node].orEmpty().filter { node to it !in blocked }

        val maleRoot: String?
        val femaleRoot: String?
        if (anchors.partnerId != null) {
            val selfMale = byId[anchors.selfId]?.person?.gender == Gender.MALE
            maleRoot = if (selfMale) anchors.selfId else anchors.partnerId
            femaleRoot = if (selfMale) anchors.partnerId else anchors.selfId
        } else {
            // 单锚：以已知父母为左右根，self 及经 self 连通者居中
            maleRoot = parentsOf(graph, anchors.selfId)
                .firstOrNull {
                    byId[it]?.person?.gender == Gender.MALE
                }
            femaleRoot = parentsOf(graph, anchors.selfId)
                .firstOrNull {
                    byId[it]?.person?.gender == Gender.FEMALE
                }
        }

        val out = HashMap<String, Assignment>()
        val distL = HashMap<String, Int>()
        val distR = HashMap<String, Int>()
        val parL = HashMap<String, String>()
        val parR = HashMap<String, String>()
        fun bfs(root: String?,
                dist: MutableMap<String, Int>, par: MutableMap<String, String>) {
            if (root == null) return
            val q = ArrayDeque<String>()
            dist[root] = 0
            q += root
            while (q.isNotEmpty()) {
                val n = q.removeFirst()
                for (nb in expands(n)) {
                    if (nb in dist) continue
                    dist[nb] = dist.getValue(n) + 1
                    par[nb] = n
                    q += nb
                }
            }
        }
        bfs(maleRoot, distL, parL)
        bfs(femaleRoot, distR, parR)
        val all = distL.keys + distR.keys
        for (id in all) {
            val dl = distL[id]; val dr = distR[id]
            val (side, d, par) = when {
                dl != null && (dr == null || dl <= dr) ->
                    Triple(GraphSide.LEFT, dl, parL[id])
                else -> Triple(GraphSide.RIGHT, dr!!, parR[id])
            }
            out[id] = Assignment(side, d, par)
        }
        // 主锚本体与其直接子女归 CENTER（子孙居中，两侧归属只到旁支）
        for (id in anchors.ids + directChildren) {
            out[id] = Assignment(GraphSide.CENTER, 0, null)
        }
        // 双根都不可达的（仅经居中子孙/锚点相连）也归 CENTER
        for (p in graph.persons) {
            out.putIfAbsent(p.id, Assignment(GraphSide.CENTER, Int.MAX_VALUE, null))
        }
        return out
    }
}
