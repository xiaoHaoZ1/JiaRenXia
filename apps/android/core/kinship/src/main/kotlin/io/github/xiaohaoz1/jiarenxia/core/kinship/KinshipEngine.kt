package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder

/**
 * 亲属称谓推导引擎（KINSHIP_ENGINE 第 8–22、29–32、37、38 节，DEVELOPMENT_PLAN 2.4）。
 *
 * 无状态：每次调用基于传入的 [FamilyGraph] 构建邻接结构。
 * 防御性输入：CHILD 记录按反向 PARENT 处理；端点缺失的记录与自环被忽略。
 */
class KinshipEngine(private val maxDepth: Int = 5) {

    /** 邻接边。type 为遍历方向；siblingRecord 仅显式 SIBLING 边携带（含 relativeAgeOrder）。 */
    private data class Edge(
        val type: RelationshipType,
        val personId: String,
        val siblingRecord: Relationship? = null
    )

    /** 整理后的图结构：edges 按 personId+type 排序保证确定性，隐式 SIBLING 已并入。 */
    private class Adjacency(
        val persons: Map<String, Person>,
        val birthdays: Map<String, PartialDate>,
        val edges: Map<String, List<Edge>>,
        val siblingRecords: Map<String, Map<String, Relationship>>,
        /** 申报堂表亲记录（双向索引）：不参与路径遍历，仅存长幼事实。 */
        val cousinRecords: Map<String, Map<String, Relationship>>
    ) {
        fun genderOf(id: String): Gender? = persons[id]?.gender
    }

    private fun buildAdjacency(graph: FamilyGraph): Adjacency {
        val persons = graph.persons.associateBy { it.id }
        val parentsOf = HashMap<String, MutableList<String>>()
        val childrenOf = HashMap<String, MutableList<String>>()
        val spousesOf = HashMap<String, MutableList<String>>()
        val sib = HashMap<String, MutableMap<String, Relationship>>()
        val cousin = HashMap<String, MutableMap<String, Relationship>>()

        for (raw in graph.relationships) {
            if (raw.sourcePersonId == raw.targetPersonId) continue
            if (raw.sourcePersonId !in persons || raw.targetPersonId !in persons) continue
            // 防御：CHILD 记录统一翻转为反向 PARENT
            val r = if (raw.type == RelationshipType.CHILD) {
                raw.copy(
                    sourcePersonId = raw.targetPersonId,
                    targetPersonId = raw.sourcePersonId,
                    type = RelationshipType.PARENT
                )
            } else raw
            when (r.type) {
                RelationshipType.PARENT -> {
                    childrenOf.getOrPut(r.sourcePersonId) { mutableListOf() }.add(r.targetPersonId)
                    parentsOf.getOrPut(r.targetPersonId) { mutableListOf() }.add(r.sourcePersonId)
                }
                RelationshipType.SPOUSE -> {
                    spousesOf.getOrPut(r.sourcePersonId) { mutableListOf() }.add(r.targetPersonId)
                    spousesOf.getOrPut(r.targetPersonId) { mutableListOf() }.add(r.sourcePersonId)
                }
                RelationshipType.SIBLING -> {
                    sib.getOrPut(r.sourcePersonId) { mutableMapOf() }[r.targetPersonId] = r
                    sib.getOrPut(r.targetPersonId) { mutableMapOf() }[r.sourcePersonId] = r
                }
                // COUSIN 只登记“申报堂表亲 + 长幼”，不产生可遍历边
                RelationshipType.COUSIN -> {
                    cousin.getOrPut(r.sourcePersonId) { mutableMapOf() }[r.targetPersonId] = r
                    cousin.getOrPut(r.targetPersonId) { mutableMapOf() }[r.sourcePersonId] = r
                }
                RelationshipType.CHILD -> Unit
            }
        }

        // 隐式兄弟姐妹：共享至少一位父母；已有显式记录的对不重复加入
        val implicit = HashMap<String, MutableSet<String>>()
        for (kids in childrenOf.values) {
            for (a in kids) for (b in kids) {
                if (a == b) continue
                if (sib[a]?.containsKey(b) == true) continue
                implicit.getOrPut(a) { mutableSetOf() }.add(b)
            }
        }

        val edges = HashMap<String, List<Edge>>()
        for (id in persons.keys) {
            val list = mutableListOf<Edge>()
            parentsOf[id]?.forEach { list += Edge(RelationshipType.PARENT, it) }
            childrenOf[id]?.forEach { list += Edge(RelationshipType.CHILD, it) }
            spousesOf[id]?.forEach { list += Edge(RelationshipType.SPOUSE, it) }
            sib[id]?.forEach { (sibId, rec) -> list += Edge(RelationshipType.SIBLING, sibId, rec) }
            implicit[id]?.forEach { list += Edge(RelationshipType.SIBLING, it, null) }
            edges[id] = list.sortedWith(compareBy({ it.personId }, { it.type.ordinal }))
        }
        return Adjacency(persons, graph.birthdays, edges, sib, cousin)
    }

    /**
     * Y 相对 X 的长幼（DEVELOPMENT_PLAN 2.4 三）：
     * 1) 双方生日完整、合法且不同 → 按生日；
     * 2) 显式 SIBLING 记录的 relativeAgeOrder（Y 为 source 时原值，Y 为 target 时取反）；
     * 3) 否则无法确定。
     */
    private fun siblingOrder(adj: Adjacency, y: String, x: String): RelativeAgeOrder? {
        PartialDate.compareSeniority(adj.birthdays[y], adj.birthdays[x])?.let { return it }
        val rec = adj.siblingRecords[x]?.get(y) ?: adj.siblingRecords[y]?.get(x) ?: return null
        val order = rec.relativeAgeOrder ?: return null
        return if (rec.sourcePersonId == y) order else order.inverse()
    }

    /**
     * 堂表亲长幼（KINSHIP_ENGINE 37.1 修订）：
     * 1) 两人间的申报 COUSIN 记录（relativeAgeOrder，Y 为 source 时原值，
     *    Y 为 target 时取反）——用户明确申报的长幼优先于生日推断；
     * 2) 双方完整生日（完整且同一天 → SAME）；
     * 3) 否则无法确定。
     */
    private fun cousinOrder(adj: Adjacency, y: String, x: String): RelativeAgeOrder? {
        val rec = adj.cousinRecords[x]?.get(y) ?: adj.cousinRecords[y]?.get(x)
        rec?.relativeAgeOrder?.let {
            return if (rec.sourcePersonId == y) it else it.inverse()
        }
        return compareSeniority(adj, y, x)
    }

    /**
     * 只比双方完整生日（KINSHIP_ENGINE 37.1 / 38.1）。
     * 完整且同一天 → SAME；缺失、不完整或不合法 → null。
     */
    private fun compareSeniority(adj: Adjacency, y: String, x: String): RelativeAgeOrder? =
        PartialDate.compareSeniority(adj.birthdays[y], adj.birthdays[x])
            ?: run {
                val a = adj.birthdays[y]?.toLocalDateOrNull()
                val b = adj.birthdays[x]?.toLocalDateOrNull()
                if (a != null && a == b) RelativeAgeOrder.SAME else null
            }

    /** 紧凑写法用的长幼字：可判断为哥/弟/姐/妹，SAME 或无法判断为兄弟/姐妹。 */
    private fun seniorityChar(order: RelativeAgeOrder?, male: Boolean): String = when (order) {
        RelativeAgeOrder.OLDER -> if (male) "哥" else "姐"
        RelativeAgeOrder.YOUNGER -> if (male) "弟" else "妹"
        else -> if (male) "兄弟" else "姐妹"
    }

    /**
     * BFS 枚举所有最短路径（最多 [maxDepth] 步，收集不超过 [MAX_PATHS] 条）。
     * visited 逐层生效：同层允许经不同前驱到达同一节点（保留并列的最短路径），
     * 已到过的层不再回访，天然防循环。
     */
    private fun shortestPaths(adj: Adjacency, from: String, to: String): List<List<PathStep>> {
        val results = mutableListOf<List<PathStep>>()
        val visited = hashSetOf(from)
        var frontier = listOf(emptyList<PathStep>() to from)
        var depth = 0
        while (frontier.isNotEmpty() && depth < maxDepth) {
            val next = mutableListOf<Pair<List<PathStep>, String>>()
            val reachedThisLevel = hashSetOf<String>()
            for ((path, node) in frontier) {
                for (e in adj.edges[node].orEmpty()) {
                    if (e.personId in visited) continue
                    val step = PathStep(
                        type = e.type,
                        personId = e.personId,
                        implicit = e.type == RelationshipType.SIBLING && e.siblingRecord == null
                    )
                    val np = path + step
                    if (e.personId == to) {
                        results.add(np)
                        if (results.size >= MAX_PATHS) return results
                        continue
                    }
                    reachedThisLevel.add(e.personId)
                    next.add(np to e.personId)
                }
            }
            if (results.isNotEmpty()) break
            visited.addAll(reachedThisLevel)
            frontier = next
            depth++
        }
        return results
    }

    /** 单条路径的称谓推导。 */
    private fun resolveInternal(adj: Adjacency, observerId: String, targetId: String): KinshipResult {
        if (observerId == targetId) {
            return KinshipResult(
                observerId, targetId, emptyList(),
                structuralDescription = "我",
                standardTitle = "我",
                confidence = Confidence.EXACT,
                missingInformation = emptySet(),
                kind = KinshipKind.SELF
            )
        }
        if (observerId !in adj.persons || targetId !in adj.persons) return unrelated(observerId, targetId)
        val paths = shortestPaths(adj, observerId, targetId)
        // 无真实路径但存在“申报堂表亲”记录 → 按申报长幼给通用堂表称谓
        if (paths.isEmpty()) {
            return declaredCousin(adj, observerId, targetId)
                ?: unrelated(observerId, targetId)
        }

        val results = paths.map { evaluate(adj, observerId, it) }
        val bestRank = results.minOf { rank(it) }
        val tier = results.filter { rank(it) == bestRank }
        val first = tier.first()
        // 同优先级的多条最短路径给出不同非空称谓时，不静默择一
        val conflict = tier.drop(1).any {
            it.standardTitle != null && it.standardTitle != first.standardTitle
        }
        return if (conflict) first.copy(standardTitle = null) else first
    }

    /** 结果优先级：DERIVED+EXACT 优于 DERIVED+PARTIAL 优于 STRUCTURAL。 */
    private fun rank(r: KinshipResult): Int = when {
        r.kind == KinshipKind.DERIVED && r.confidence == Confidence.EXACT -> 0
        r.kind == KinshipKind.DERIVED && r.confidence == Confidence.PARTIAL -> 1
        else -> 2
    }

    private fun unrelated(observerId: String, targetId: String) = KinshipResult(
        observerId, targetId, emptyList(),
        structuralDescription = "未关联到我",
        standardTitle = null,
        confidence = Confidence.PARTIAL,
        missingInformation = setOf(MissingInformation.NO_PATH),
        kind = KinshipKind.UNRELATED
    )

    /**
     * 申报堂表亲（无真实路径）：用户录了“两人是堂/表亲”但中间链条未建，
     * 按申报长幼给“堂表兄/弟/姐/妹”通用称谓；长幼未申报再退生日，
     * 仍无则给“堂表兄弟/姐妹”并标 PARTIAL。
     */
    private fun declaredCousin(
        adj: Adjacency, observerId: String, targetId: String
    ): KinshipResult? {
        adj.cousinRecords[observerId]?.get(targetId) ?: return null
        val tMale = adj.genderOf(targetId) == Gender.MALE
        val order = cousinOrder(adj, targetId, observerId)
        val path = listOf(PathStep(RelationshipType.COUSIN, targetId))
        return when (order) {
            RelativeAgeOrder.OLDER, RelativeAgeOrder.YOUNGER, RelativeAgeOrder.SAME ->
                derived(
                    observerId, targetId, path,
                    "堂表亲（" + (if (tMale) "兄弟" else "姐妹") + "）",
                    "堂表" + seniorityChar(order, tMale)
                )
            null -> derived(
                observerId, targetId, path,
                "堂表亲（" + (if (tMale) "兄弟" else "姐妹") + "）",
                "堂表" + (if (tMale) "兄弟" else "姐妹"),
                confidence = Confidence.PARTIAL,
                missing = setOf(MissingInformation.BIRTH_DATES)
            )
        }
    }

    private fun derived(
        observerId: String, targetId: String, path: List<PathStep>,
        structural: String, title: String?,
        confidence: Confidence = Confidence.EXACT,
        missing: Set<MissingInformation> = emptySet()
    ) = KinshipResult(
        observerId, targetId, path, structural, title,
        confidence = confidence, missingInformation = missing, kind = KinshipKind.DERIVED
    )

    private fun evaluate(adj: Adjacency, observerId: String, path: List<PathStep>): KinshipResult {
        val types = path.map { it.type }
        val targetId = path.last().personId
        val tGender = adj.genderOf(targetId)
        val tMale = tGender == Gender.MALE

        return when {
            types == listOf(RelationshipType.PARENT) ->
                derived(observerId, targetId, path, if (tMale) "父亲" else "母亲", if (tMale) "父亲" else "母亲")

            types == listOf(RelationshipType.CHILD) ->
                derived(observerId, targetId, path, if (tMale) "儿子" else "女儿", if (tMale) "儿子" else "女儿")

            types == listOf(RelationshipType.SPOUSE) -> {
                // V0.3（38.2 A）：结构关系与称谓都按配偶性别；性别未知退回“配偶”
                val label = when (tGender) {
                    Gender.FEMALE -> "妻子"
                    Gender.MALE -> "丈夫"
                    else -> "配偶"
                }
                derived(observerId, targetId, path, label, label)
            }

            types == listOf(RelationshipType.SIBLING) -> {
                val order = siblingOrder(adj, targetId, observerId)
                when (order) {
                    RelativeAgeOrder.OLDER ->
                        derived(observerId, targetId, path, if (tMale) "哥哥" else "姐姐", if (tMale) "哥哥" else "姐姐")
                    RelativeAgeOrder.YOUNGER ->
                        derived(observerId, targetId, path, if (tMale) "弟弟" else "妹妹", if (tMale) "弟弟" else "妹妹")
                    RelativeAgeOrder.SAME ->
                        derived(observerId, targetId, path, if (tMale) "兄弟" else "姐妹", null)
                    null ->
                        derived(observerId, targetId, path, if (tMale) "兄弟" else "姐妹", null,
                            confidence = Confidence.PARTIAL,
                            missing = setOf(MissingInformation.SIBLING_AGE_ORDER))
                }
            }

            types == listOf(RelationshipType.PARENT, RelationshipType.PARENT) -> {
                val midMale = adj.genderOf(path[0].personId) == Gender.MALE
                if (midMale) {
                    if (tMale) derived(observerId, targetId, path, "父亲的父亲", "爷爷")
                    else derived(observerId, targetId, path, "父亲的母亲", "奶奶")
                } else {
                    if (tMale) derived(observerId, targetId, path, "母亲的父亲", "姥爷")
                    else derived(observerId, targetId, path, "母亲的母亲", "姥姥")
                }
            }

            types == listOf(RelationshipType.SPOUSE, RelationshipType.PARENT) -> {
                val spouseGender = adj.genderOf(path[0].personId)
                val observerGender = adj.genderOf(observerId)
                // V0.3（38.3）：结构描述随配偶性别使用「妻子的/丈夫的」前缀
                val structural = composedLabel(adj, observerId, path)
                val title = when {
                    observerGender == Gender.MALE && spouseGender == Gender.FEMALE ->
                        if (tMale) "岳父" else "岳母"
                    observerGender == Gender.FEMALE && spouseGender == Gender.MALE ->
                        if (tMale) "公公" else "婆婆"
                    else -> null
                }
                derived(observerId, targetId, path, structural, title)
            }

            types == listOf(RelationshipType.PARENT, RelationshipType.SIBLING) -> {
                val midGender = adj.genderOf(path[0].personId)
                if (midGender == Gender.MALE) {
                    if (tMale) {
                        when (siblingOrder(adj, targetId, path[0].personId)) {
                            RelativeAgeOrder.OLDER -> derived(observerId, targetId, path, "父亲的哥哥", "伯父")
                            RelativeAgeOrder.YOUNGER -> derived(observerId, targetId, path, "父亲的弟弟", "叔叔")
                            RelativeAgeOrder.SAME -> derived(observerId, targetId, path, "父亲的兄弟", null)
                            null -> derived(observerId, targetId, path, "父亲的兄弟", null,
                                confidence = Confidence.PARTIAL,
                                missing = setOf(MissingInformation.SIBLING_AGE_ORDER))
                        }
                    } else {
                        derived(observerId, targetId, path, "父亲的姐妹", "姑姑")
                    }
                } else {
                    if (tMale) derived(observerId, targetId, path, "母亲的兄弟", "舅舅")
                    else derived(observerId, targetId, path, "母亲的姐妹", "姨妈")
                }
            }

            else -> extended(adj, observerId, path)
                ?: spouseNamed(adj, observerId, path)
                ?: structural(observerId, targetId, adj, path)
        }
    }

    /** 路径中第 i 步的标签（结构描述拼接的最小单元；SIBLING 带长幼标签）。 */
    private fun stepLabel(
        adj: Adjacency, observerId: String, path: List<PathStep>, i: Int
    ): String {
        val step = path[i]
        val g = adj.genderOf(step.personId)
        return when (step.type) {
            RelationshipType.PARENT -> if (g == Gender.MALE) "父亲" else "母亲"
            RelationshipType.CHILD -> if (g == Gender.MALE) "儿子" else "女儿"
            RelationshipType.SPOUSE -> if (g == Gender.MALE) "丈夫" else "妻子"
            RelationshipType.SIBLING -> {
                val prev = if (i == 0) observerId else path[i - 1].personId
                when (siblingOrder(adj, step.personId, prev)) {
                    RelativeAgeOrder.OLDER -> if (g == Gender.MALE) "哥哥" else "姐姐"
                    RelativeAgeOrder.YOUNGER -> if (g == Gender.MALE) "弟弟" else "妹妹"
                    else -> if (g == Gender.MALE) "兄弟" else "姐妹"
                }
            }
            // COUSIN 步只会出现在申报堂表亲的单步路径里
            RelationshipType.COUSIN -> "堂表亲"
        }
    }

    /** 逐层拼接的结构描述（DERIVED 规则也用它保证文字一致）。 */
    private fun composedLabel(
        adj: Adjacency, observerId: String, path: List<PathStep>
    ): String = path.indices.joinToString("的") { stepLabel(adj, observerId, path, it) }

    /**
     * V0.2 扩展规则（KINSHIP_ENGINE 37.2）：姻亲与堂表亲。
     * 命中时返回 DERIVED 结果，否则 null 交给外层继续判断。
     */
    private fun extended(
        adj: Adjacency, observerId: String, path: List<PathStep>
    ): KinshipResult? {
        val types = path.map { it.type }
        val targetId = path.last().personId
        val tMale = adj.genderOf(targetId) == Gender.MALE
        val label = composedLabel(adj, observerId, path)
        fun d(title: String?, conf: Confidence = Confidence.EXACT,
              missing: Set<MissingInformation> = emptySet()) =
            derived(observerId, targetId, path, label, title, conf, missing)

        when (types) {
            // 兄弟姐妹的配偶（嫂子/弟媳/姐夫/妹夫）
            listOf(RelationshipType.SIBLING, RelationshipType.SPOUSE) -> {
                val midMale = adj.genderOf(path[0].personId) == Gender.MALE
                if (midMale && tMale || !midMale && !tMale) {
                    return d(null)  // 配偶性别组合不适用传统称谓
                }
                val order = siblingOrder(adj, path[0].personId, observerId)
                val titles = if (midMale) "嫂子" to "弟媳" else "姐夫" to "妹夫"
                return when (order) {
                    RelativeAgeOrder.OLDER -> d(titles.first)
                    RelativeAgeOrder.YOUNGER -> d(titles.second)
                    RelativeAgeOrder.SAME -> d(null)
                    null -> d(null, Confidence.PARTIAL,
                        setOf(MissingInformation.SIBLING_AGE_ORDER))
                }
            }

            // 配偶的兄弟姐妹（大舅子/小舅子/大姨子/小姨子/大伯子/小叔子/大姑子/小姑子）
            listOf(RelationshipType.SPOUSE, RelationshipType.SIBLING) -> {
                val spouseFemale = adj.genderOf(path[0].personId) == Gender.FEMALE
                val observerMale = adj.genderOf(observerId) == Gender.MALE
                val titles = when {
                    observerMale && spouseFemale ->
                        if (tMale) "大舅子" to "小舅子" else "大姨子" to "小姨子"
                    !observerMale && !spouseFemale &&
                        adj.genderOf(observerId) == Gender.FEMALE &&
                        adj.genderOf(path[0].personId) == Gender.MALE ->
                        if (tMale) "大伯子" to "小叔子" else "大姑子" to "小姑子"
                    else -> return d(null)
                }
                return when (siblingOrder(adj, targetId, path[0].personId)) {
                    RelativeAgeOrder.OLDER -> d(titles.first)
                    RelativeAgeOrder.YOUNGER -> d(titles.second)
                    RelativeAgeOrder.SAME -> d(null)
                    null -> d(null, Confidence.PARTIAL,
                        setOf(MissingInformation.SIBLING_AGE_ORDER))
                }
            }

            // 子女的配偶（儿媳/女婿）
            listOf(RelationshipType.CHILD, RelationshipType.SPOUSE) -> {
                val childMale = adj.genderOf(path[0].personId) == Gender.MALE
                return when {
                    childMale && !tMale -> d("儿媳")
                    !childMale && tMale -> d("女婿")
                    else -> d(null)
                }
            }

            // 孙辈（孙子/孙女/外孙/外孙女）
            listOf(RelationshipType.CHILD, RelationshipType.CHILD) -> {
                val childMale = adj.genderOf(path[0].personId) == Gender.MALE
                return d(
                    if (childMale) (if (tMale) "孙子" else "孙女")
                    else (if (tMale) "外孙" else "外孙女")
                )
            }

            // 兄弟姐妹的子女（侄子/侄女/外甥/外甥女）
            listOf(RelationshipType.SIBLING, RelationshipType.CHILD) -> {
                val midMale = adj.genderOf(path[0].personId) == Gender.MALE
                return d(
                    if (midMale) (if (tMale) "侄子" else "侄女")
                    else (if (tMale) "外甥" else "外甥女")
                )
            }

            // 父母的兄弟姐妹的配偶（伯母/婶婶/姑父/舅妈/姨夫）
            listOf(RelationshipType.PARENT, RelationshipType.SIBLING,
                   RelationshipType.SPOUSE) -> {
                val parentMale = adj.genderOf(path[0].personId) == Gender.MALE
                val midMale = adj.genderOf(path[1].personId) == Gender.MALE
                return when {
                    parentMale && midMale -> {
                        if (tMale) return d(null)  // 兄弟的丈夫不适用
                        when (siblingOrder(adj, path[1].personId, path[0].personId)) {
                            RelativeAgeOrder.OLDER -> d("伯母")
                            RelativeAgeOrder.YOUNGER -> d("婶婶")
                            RelativeAgeOrder.SAME -> d(null)
                            null -> d(null, Confidence.PARTIAL,
                                setOf(MissingInformation.SIBLING_AGE_ORDER))
                        }
                    }
                    parentMale && !midMale -> d(if (tMale) "姑父" else null)
                    !parentMale && midMale -> d(if (!tMale) "舅妈" else null)
                    else -> d(if (tMale) "姨夫" else null)
                }
            }

            // 堂表兄弟姐妹（父母的兄弟姐妹的子女；长幼先读申报 COUSIN 记录，再比生日）
            listOf(RelationshipType.PARENT, RelationshipType.SIBLING,
                   RelationshipType.CHILD) -> {
                val tang = adj.genderOf(path[0].personId) == Gender.MALE &&
                    adj.genderOf(path[1].personId) == Gender.MALE
                val gen = if (tang) "堂" else "表"
                val generic = gen + (if (tMale) "兄弟" else "姐妹")
                // 申报长幼优先，其次双方完整生日；完整且同一天 → SAME
                val order = cousinOrder(adj, targetId, observerId)
                return when (order) {
                    RelativeAgeOrder.OLDER -> d(gen + (if (tMale) "哥" else "姐"))
                    RelativeAgeOrder.YOUNGER -> d(gen + (if (tMale) "弟" else "妹"))
                    RelativeAgeOrder.SAME -> d(generic)
                    null -> d(generic, Confidence.PARTIAL,
                        setOf(MissingInformation.BIRTH_DATES))
                }
            }

            // 配偶的兄弟姐妹的配偶（连襟/妯娌）
            listOf(RelationshipType.SPOUSE, RelationshipType.SIBLING,
                   RelationshipType.SPOUSE) -> {
                val observerMale = adj.genderOf(observerId) == Gender.MALE
                val spouseFemale = adj.genderOf(path[0].personId) == Gender.FEMALE
                val midFemale = adj.genderOf(path[1].personId) == Gender.FEMALE
                return when {
                    // 我为男、妻子的姐妹的丈夫 → 连襟
                    observerMale && spouseFemale && midFemale && tMale -> d("连襟")
                    // 我为女、丈夫的兄弟的妻子 → 妯娌
                    !observerMale && !spouseFemale &&
                        adj.genderOf(observerId) == Gender.FEMALE &&
                        adj.genderOf(path[0].personId) == Gender.MALE &&
                        !midFemale && !tMale -> d("妯娌")
                    // 非连襟/妯娌的组合交给 38.3 随配偶称（38.4）
                    else -> return null
                }
            }

            // ---------- KINSHIP_ENGINE 38.2：本人一侧 ----------

            // B. 曾祖辈（太爷爷/太奶奶/曾外祖父/曾外祖母/太姥爷/太姥姥/外曾外祖父/外曾外祖母）
            listOf(RelationshipType.PARENT, RelationshipType.PARENT,
                   RelationshipType.PARENT) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val p1Male = adj.genderOf(path[1].personId) == Gender.MALE
                return d(when {
                    p0Male && p1Male -> if (tMale) "太爷爷" else "太奶奶"
                    p0Male -> if (tMale) "曾外祖父" else "曾外祖母"
                    p1Male -> if (tMale) "太姥爷" else "太姥姥"
                    else -> if (tMale) "外曾外祖父" else "外曾外祖母"
                })
            }

            // C. 祖辈的兄弟姐妹（伯祖父/叔祖父/姑奶奶/舅爷爷/姨奶奶/外伯祖父/外叔祖父/姑姥姥/舅姥爷/姨姥姥）
            // 长幼指目标相对祖辈（p1），走 siblingOrder（生日或显式 SIBLING 记录）
            listOf(RelationshipType.PARENT, RelationshipType.PARENT,
                   RelationshipType.SIBLING) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val p1Male = adj.genderOf(path[1].personId) == Gender.MALE
                return when {
                    p0Male && p1Male && tMale ->
                        when (siblingOrder(adj, targetId, path[1].personId)) {
                            RelativeAgeOrder.OLDER -> d("伯祖父")
                            RelativeAgeOrder.YOUNGER -> d("叔祖父")
                            RelativeAgeOrder.SAME -> d(null)
                            null -> d(null, Confidence.PARTIAL,
                                setOf(MissingInformation.SIBLING_AGE_ORDER))
                        }
                    p0Male && p1Male -> d("姑奶奶")
                    p0Male && !p1Male -> d(if (tMale) "舅爷爷" else "姨奶奶")
                    !p0Male && p1Male && tMale ->
                        when (siblingOrder(adj, targetId, path[1].personId)) {
                            RelativeAgeOrder.OLDER -> d("外伯祖父")
                            RelativeAgeOrder.YOUNGER -> d("外叔祖父")
                            RelativeAgeOrder.SAME -> d(null)
                            null -> d(null, Confidence.PARTIAL,
                                setOf(MissingInformation.SIBLING_AGE_ORDER))
                        }
                    !p0Male && p1Male -> d("姑姥姥")
                    else -> d(if (tMale) "舅姥爷" else "姨姥姥")
                }
            }

            // D. 祖辈的兄弟姐妹的配偶：先按 C 求 p2 称谓，再按 t 性别取对应祖母/祖父辈称谓
            listOf(RelationshipType.PARENT, RelationshipType.PARENT,
                   RelationshipType.SIBLING, RelationshipType.SPOUSE) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val p1Male = adj.genderOf(path[1].personId) == Gender.MALE
                val p2Male = adj.genderOf(path[2].personId) == Gender.MALE
                return when {
                    // 伯祖父/叔祖父 → 伯祖母/叔祖母；外伯祖父/外叔祖父 → 外伯祖母/外叔祖母
                    p1Male && p2Male -> {
                        if (tMale) return d(null)  // 兄弟的丈夫不适用
                        when (siblingOrder(adj, path[2].personId, path[1].personId)) {
                            RelativeAgeOrder.OLDER ->
                                d(if (p0Male) "伯祖母" else "外伯祖母")
                            RelativeAgeOrder.YOUNGER ->
                                d(if (p0Male) "叔祖母" else "外叔祖母")
                            RelativeAgeOrder.SAME -> d(null)
                            null -> d(null, Confidence.PARTIAL,
                                setOf(MissingInformation.SIBLING_AGE_ORDER))
                        }
                    }
                    // 姑奶奶 → 姑爷爷；姑姥姥 → 姑姥爷
                    p1Male && !p2Male -> d(if (tMale) (if (p0Male) "姑爷爷" else "姑姥爷") else null)
                    // 舅爷爷 → 舅奶奶；舅姥爷 → 舅姥姥
                    !p1Male && p2Male -> d(if (!tMale) (if (p0Male) "舅奶奶" else "舅姥姥") else null)
                    // 姨奶奶 → 姨爷爷；姨姥姥 → 姨姥爷
                    else -> d(if (tMale) (if (p0Male) "姨爷爷" else "姨姥爷") else null)
                }
            }

            // E. 父母的堂表兄弟姐妹（堂伯/堂叔/堂姑、表伯/表叔/表姑；堂舅/堂姨、表舅/表姨）
            // 堂表看 p1（祖辈）与 p2（祖辈的兄弟姐妹）是否都为男；长幼指 t 相对 p0，只比生日
            listOf(RelationshipType.PARENT, RelationshipType.PARENT,
                   RelationshipType.SIBLING, RelationshipType.CHILD) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val tang = adj.genderOf(path[1].personId) == Gender.MALE &&
                    adj.genderOf(path[2].personId) == Gender.MALE
                val gen = if (tang) "堂" else "表"
                val order = cousinOrder(adj, targetId, path[0].personId)
                val struct = (if (p0Male) "父亲" else "母亲") + "的" +
                    gen + seniorityChar(order, tMale)
                fun dd(title: String?, conf: Confidence = Confidence.EXACT,
                       missing: Set<MissingInformation> = emptySet()) =
                    derived(observerId, targetId, path, struct, title, conf, missing)
                return when {
                    p0Male && tMale -> when (order) {
                        RelativeAgeOrder.OLDER -> dd(gen + "伯")
                        RelativeAgeOrder.YOUNGER -> dd(gen + "叔")
                        RelativeAgeOrder.SAME -> dd(null)
                        null -> dd(null, Confidence.PARTIAL,
                            setOf(MissingInformation.BIRTH_DATES))
                    }
                    p0Male -> dd(gen + "姑")
                    tMale -> dd(gen + "舅")
                    else -> dd(gen + "姨")
                }
            }

            // F. 父母的堂表兄弟姐妹的配偶（5 步）：先按 E 求 p3 称谓再取 t 的称谓
            listOf(RelationshipType.PARENT, RelationshipType.PARENT,
                   RelationshipType.SIBLING, RelationshipType.CHILD,
                   RelationshipType.SPOUSE) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val p3Male = adj.genderOf(path[3].personId) == Gender.MALE
                val tang = adj.genderOf(path[1].personId) == Gender.MALE &&
                    adj.genderOf(path[2].personId) == Gender.MALE
                val gen = if (tang) "堂" else "表"
                val order3 = cousinOrder(adj, path[3].personId, path[0].personId)
                val struct = (if (p0Male) "父亲" else "母亲") + "的" + gen +
                    seniorityChar(order3, p3Male) + (if (tMale) "的丈夫" else "的妻子")
                fun dd(title: String?, conf: Confidence = Confidence.EXACT,
                       missing: Set<MissingInformation> = emptySet()) =
                    derived(observerId, targetId, path, struct, title, conf, missing)
                return when {
                    // 堂伯/堂叔、表伯/表叔 → 堂伯母/堂婶、表伯母/表婶
                    p0Male && p3Male -> {
                        if (tMale) return dd(null)  // 伯/叔的丈夫不适用
                        when (order3) {
                            RelativeAgeOrder.OLDER -> dd(gen + "伯母")
                            RelativeAgeOrder.YOUNGER -> dd(gen + "婶")
                            RelativeAgeOrder.SAME -> dd(null)
                            null -> dd(null, Confidence.PARTIAL,
                                setOf(MissingInformation.BIRTH_DATES))
                        }
                    }
                    // 堂姑/表姑 → 堂姑父/表姑父
                    p0Male -> dd(if (tMale) gen + "姑父" else null)
                    // 堂舅/表舅 → 堂舅妈/表舅妈
                    p3Male -> dd(if (tMale) null else gen + "舅妈")
                    // 堂姨/表姨 → 堂姨夫/表姨夫
                    else -> dd(if (tMale) gen + "姨夫" else null)
                }
            }

            // G. 堂表兄弟姐妹的配偶（堂嫂/堂弟媳/堂姐夫/堂妹夫、表…）
            // 堂表看 p0（父母）与 p1（父母的兄弟姐妹）是否都为男；长幼指 p2 相对我，只比生日
            listOf(RelationshipType.PARENT, RelationshipType.SIBLING,
                   RelationshipType.CHILD, RelationshipType.SPOUSE) -> {
                val tang = adj.genderOf(path[0].personId) == Gender.MALE &&
                    adj.genderOf(path[1].personId) == Gender.MALE
                val gen = if (tang) "堂" else "表"
                val p2Male = adj.genderOf(path[2].personId) == Gender.MALE
                val order = cousinOrder(adj, path[2].personId, observerId)
                val struct = gen + seniorityChar(order, p2Male) +
                    (if (tMale) "的丈夫" else "的妻子")
                fun dd(title: String?, conf: Confidence = Confidence.EXACT,
                       missing: Set<MissingInformation> = emptySet()) =
                    derived(observerId, targetId, path, struct, title, conf, missing)
                return when {
                    p2Male == tMale -> dd(null)   // 同性别组合不适用
                    p2Male -> when (order) {
                        RelativeAgeOrder.OLDER -> dd(gen + "嫂")
                        RelativeAgeOrder.YOUNGER -> dd(gen + "弟媳")
                        RelativeAgeOrder.SAME -> dd(null)
                        null -> dd(null, Confidence.PARTIAL,
                            setOf(MissingInformation.BIRTH_DATES))
                    }
                    else -> when (order) {
                        RelativeAgeOrder.OLDER -> dd(gen + "姐夫")
                        RelativeAgeOrder.YOUNGER -> dd(gen + "妹夫")
                        RelativeAgeOrder.SAME -> dd(null)
                        null -> dd(null, Confidence.PARTIAL,
                            setOf(MissingInformation.BIRTH_DATES))
                    }
                }
            }

            // H. 堂表兄弟姐妹的子女（堂侄/堂侄女/堂外甥/堂外甥女、表…），不依赖长幼
            listOf(RelationshipType.PARENT, RelationshipType.SIBLING,
                   RelationshipType.CHILD, RelationshipType.CHILD) -> {
                val tang = adj.genderOf(path[0].personId) == Gender.MALE &&
                    adj.genderOf(path[1].personId) == Gender.MALE
                val gen = if (tang) "堂" else "表"
                val p2Male = adj.genderOf(path[2].personId) == Gender.MALE
                val order = cousinOrder(adj, path[2].personId, observerId)
                val struct = gen + seniorityChar(order, p2Male) +
                    (if (tMale) "的儿子" else "的女儿")
                return derived(observerId, targetId, path, struct, when {
                    p2Male && tMale -> gen + "侄"
                    p2Male -> gen + "侄女"
                    tMale -> gen + "外甥"
                    else -> gen + "外甥女"
                })
            }

            // I. 兄弟姐妹的子女的配偶（侄媳/侄女婿/外甥媳/外甥女婿）
            listOf(RelationshipType.SIBLING, RelationshipType.CHILD,
                   RelationshipType.SPOUSE) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val p1Male = adj.genderOf(path[1].personId) == Gender.MALE
                return d(when {
                    p0Male && p1Male && !tMale -> "侄媳"
                    p0Male && !p1Male && tMale -> "侄女婿"
                    !p0Male && p1Male && !tMale -> "外甥媳"
                    !p0Male && !p1Male && tMale -> "外甥女婿"
                    else -> null
                })
            }

            // J. 兄弟姐妹的孙辈（侄孙/侄孙女/侄外孙/侄外孙女/外甥孙/外甥孙女/外甥外孙/外甥外孙女）
            listOf(RelationshipType.SIBLING, RelationshipType.CHILD,
                   RelationshipType.CHILD) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val p1Male = adj.genderOf(path[1].personId) == Gender.MALE
                return d(when {
                    p0Male && p1Male -> if (tMale) "侄孙" else "侄孙女"
                    p0Male -> if (tMale) "侄外孙" else "侄外孙女"
                    p1Male -> if (tMale) "外甥孙" else "外甥孙女"
                    else -> if (tMale) "外甥外孙" else "外甥外孙女"
                })
            }

            // K. 亲家（子女的配偶的父母）
            listOf(RelationshipType.CHILD, RelationshipType.SPOUSE,
                   RelationshipType.PARENT) ->
                return d(if (tMale) "亲家公" else "亲家母")

            // L. 孙辈的配偶（孙媳/孙女婿/外孙媳/外孙女婿）
            listOf(RelationshipType.CHILD, RelationshipType.CHILD,
                   RelationshipType.SPOUSE) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val p1Male = adj.genderOf(path[1].personId) == Gender.MALE
                return d(when {
                    p0Male && p1Male && !tMale -> "孙媳"
                    p0Male && !p1Male && tMale -> "孙女婿"
                    !p0Male && p1Male && !tMale -> "外孙媳"
                    !p0Male && !p1Male && tMale -> "外孙女婿"
                    else -> null
                })
            }

            // M. 曾孙辈（曾孙/曾孙女/曾外孙/曾外孙女/外曾孙/外曾孙女/外曾外孙/外曾外孙女）
            listOf(RelationshipType.CHILD, RelationshipType.CHILD,
                   RelationshipType.CHILD) -> {
                val p0Male = adj.genderOf(path[0].personId) == Gender.MALE
                val p1Male = adj.genderOf(path[1].personId) == Gender.MALE
                return d(when {
                    p0Male && p1Male -> if (tMale) "曾孙" else "曾孙女"
                    p0Male -> if (tMale) "曾外孙" else "曾外孙女"
                    p1Male -> if (tMale) "外曾孙" else "外曾孙女"
                    else -> if (tMale) "外曾外孙" else "外曾外孙女"
                })
            }

            else -> return null
        }
    }

    /**
     * 随配偶称（KINSHIP_ENGINE 38.3）：配偶一侧的亲属按配偶的称谓称呼。
     * SPOUSE 之后的子路径以配偶为观察者套用全部规则；DERIVED 则前缀“妻子/丈夫的”，
     * STRUCTURAL 则退回普通逐层拼接（返回 null 交给外层）。
     * 不适用于单独的 [PARENT]/[SIBLING]（岳父/舅子等已有规则）与 CHILD/SPOUSE 开头。
     */
    private fun spouseNamed(
        adj: Adjacency, observerId: String, path: List<PathStep>
    ): KinshipResult? {
        if (path.first().type != RelationshipType.SPOUSE) return null
        val p = path.drop(1)
        if (p.isEmpty()) return null
        if (p.first().type != RelationshipType.PARENT &&
            p.first().type != RelationshipType.SIBLING) return null
        if (p.size == 1) return null
        // [SIBLING, SPOUSE] 只有连襟/妯娌不匹配时才走到这（extended 先判）
        val spouseId = path[0].personId
        val sub = evaluate(adj, spouseId, p)
        if (sub.kind != KinshipKind.DERIVED) return null
        val spouseWord = if (adj.genderOf(spouseId) == Gender.FEMALE) "妻子" else "丈夫"
        return derived(
            observerId, path.last().personId, path,
            spouseWord + "的" + sub.structuralDescription,
            sub.standardTitle, sub.confidence, sub.missingInformation
        )
    }

    /**
     * 列表外路径的结构化拼接（DEVELOPMENT_PLAN 2.4 二）：逐步取标签，以“的”相连。
     */
    private fun structural(
        observerId: String, targetId: String,
        adj: Adjacency, path: List<PathStep>
    ): KinshipResult {
        val parts = path.indices.map { i -> stepLabel(adj, observerId, path, i) }
        return KinshipResult(
            observerId, targetId, path,
            structuralDescription = parts.joinToString("的"),
            standardTitle = null,
            confidence = Confidence.EXACT,
            missingInformation = emptySet(),
            kind = KinshipKind.STRUCTURAL
        )
    }

    /**
     * 推导 observer 视角下 target 的称谓。
     */
    fun resolve(graph: FamilyGraph, observerId: String, targetId: String): KinshipResult =
        resolveInternal(buildAdjacency(graph), observerId, targetId)

    /**
     * 推导 observer 视角下图中所有人物（含其本人）的称谓。
     * 邻接结构只构建一次。
     */
    fun resolveAll(graph: FamilyGraph, observerId: String): Map<String, KinshipResult> {
        val adj = buildAdjacency(graph)
        return graph.persons.associate { it.id to resolveInternal(adj, observerId, it.id) }
    }

    /**
     * 直接关系在 viewpoint 视角下的称谓标签（另一端人物对该关系的称呼）。
     *
     * PARENT：viewpoint 为 target → 父亲/母亲；为 source → 儿子/女儿。
     * CHILD（领域写法）：viewpoint 为 source → 父亲/母亲；为 target → 儿子/女儿。
     * SPOUSE → 丈夫/妻子。SIBLING 按另一端相对 viewpoint 的长幼给哥哥/姐姐/弟弟/妹妹，
     * SAME 给出“兄弟（长幼相同）/姐妹（长幼相同）”，无法确定给出“兄弟/姐妹”。
     *
     * @throws IllegalArgumentException viewpoint 不是该关系的任一端，或另一端人物不存在
     */
    fun directRelationLabel(
        graph: FamilyGraph,
        viewpointId: String,
        relationship: Relationship
    ): String {
        val isSource = relationship.sourcePersonId == viewpointId
        val isTarget = relationship.targetPersonId == viewpointId
        require(isSource || isTarget) { "viewpointId 不在该关系的两端" }
        val otherId = if (isSource) relationship.targetPersonId else relationship.sourcePersonId
        val other = graph.persons.firstOrNull { it.id == otherId }
        requireNotNull(other) { "关系另一端的人物不存在" }
        val male = other.gender == Gender.MALE
        return when (relationship.type) {
            RelationshipType.PARENT ->
                if (isTarget) (if (male) "父亲" else "母亲") else (if (male) "儿子" else "女儿")
            RelationshipType.CHILD ->
                if (isSource) (if (male) "父亲" else "母亲") else (if (male) "儿子" else "女儿")
            RelationshipType.SPOUSE -> if (male) "丈夫" else "妻子"
            RelationshipType.SIBLING -> {
                val order = PartialDate.compareSeniority(
                    graph.birthdays[otherId], graph.birthdays[viewpointId]
                ) ?: relationship.relativeAgeOrder?.let {
                    if (relationship.sourcePersonId == otherId) it else it.inverse()
                }
                when (order) {
                    RelativeAgeOrder.OLDER -> if (male) "哥哥" else "姐姐"
                    RelativeAgeOrder.YOUNGER -> if (male) "弟弟" else "妹妹"
                    RelativeAgeOrder.SAME -> if (male) "兄弟（长幼相同）" else "姐妹（长幼相同）"
                    null -> if (male) "兄弟" else "姐妹"
                }
            }
            // 申报堂表亲：称谓用记录里申报的长幼（这是该记录存在的原因）
            RelationshipType.COUSIN -> {
                val order = relationship.relativeAgeOrder?.let {
                    if (relationship.sourcePersonId == otherId) it else it.inverse()
                }
                "堂表" + when (order) {
                    RelativeAgeOrder.OLDER -> if (male) "哥" else "姐"
                    RelativeAgeOrder.YOUNGER -> if (male) "弟" else "妹"
                    else -> if (male) "兄弟" else "姐妹"
                }
            }
        }
    }

    /**
     * 每个人物相对 observer 的代数（关系图分代用）：
     * observer 为 0，沿 PARENT 走 −1，沿 CHILD 走 +1，SPOUSE / SIBLING（含隐式）不变；
     * 申报 COUSIN 也按同代展开（无路径的申报堂表亲落到 0 代而不是游离）。
     * 不可达的人物不出现在结果中。
     */
    fun generations(graph: FamilyGraph, observerId: String): Map<String, Int> {
        val adj = buildAdjacency(graph)
        if (observerId !in adj.persons) return emptyMap()
        val gen = hashMapOf(observerId to 0)
        val queue = ArrayDeque<String>()
        queue.add(observerId)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val base = gen.getValue(node)
            for (e in adj.edges[node].orEmpty()) {
                if (e.personId in gen) continue
                gen[e.personId] = base + when (e.type) {
                    RelationshipType.PARENT -> -1
                    RelationshipType.CHILD -> 1
                    else -> 0
                }
                queue.add(e.personId)
            }
            for (cid in adj.cousinRecords[node].orEmpty().keys) {
                if (cid in gen) continue
                gen[cid] = base
                queue.add(cid)
            }
        }
        return gen
    }

    private companion object {
        /** 单目标收集的最短路径上限，防止极端图结构下结果膨胀。 */
        const val MAX_PATHS = 64
    }
}
