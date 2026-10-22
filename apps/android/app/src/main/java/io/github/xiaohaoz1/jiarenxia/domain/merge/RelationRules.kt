package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity

/** 违反关系约束的说明（[identical]=与本机完全相同 → 静默跳过，不算矛盾）。 */
data class Violation(val reason: String, val identical: Boolean = false)

/**
 * 合并用的内存关系图（FAMILY_AFFAIRS 16.6）：以本机（或合并中）人物+关系为
 * 初态，逐条 [check] + [add]。约束与手工录入一致，另加一条“同一性别的
 * 父母最多一位”：每人最多两位父母、最多一位配偶、两人之间只能有一条关系、
 * 不形成辈分循环。
 *
 * 传入的 r 须已经 [SpouseMerge.normalizeRel] 规范化（SPOUSE/SIBLING
 * 按无序对存储）。
 */
internal class RelationGraph(
    persons: Map<String, PersonEntity>,
    relationships: List<RelationshipEntity>
) {
    private val persons = persons
    private val rels = relationships.toMutableList()

    /** 逐条检查：[null]=可加入；identical=完全相同（调用方静默跳过）。 */
    fun check(r: RelationshipEntity): Violation? {
        // 两端相同不允许
        if (r.sourcePersonId == r.targetPersonId) {
            return Violation("两端是同一个人")
        }
        // 两人之间只能有一条关系（PARENT/CHILD 看方向，SPOUSE/SIBLING 看无序对）
        for (e in rels) {
            val samePair = when (r.type) {
                RelationshipType.PARENT, RelationshipType.CHILD ->
                    e.type == r.type &&
                        e.sourcePersonId == r.sourcePersonId &&
                        e.targetPersonId == r.targetPersonId
                RelationshipType.SPOUSE, RelationshipType.SIBLING,
                RelationshipType.COUSIN ->
                    e.type == r.type && sameUnorderedPair(e, r)
            }
            if (samePair) {
                return Violation("已存在相同关系", identical = true)
            }
        }
        // 同一对两人之间已有其他类型的关系 → 矛盾（描述本机已有那条）
        val other = rels.firstOrNull { sharesPair(it, r) }
        if (other != null) {
            return Violation("本机已记为：" + describe(other))
        }
        when (r.type) {
            RelationshipType.PARENT -> {
                val child = persons[r.targetPersonId]
                val parent = persons[r.sourcePersonId]
                // 最多两位父母
                val parents = parentIds(r.targetPersonId)
                if (parents.size >= 2) {
                    return Violation((child?.name ?: "") + "已经记了两位父母")
                }
                // 同一性别的父母最多一位（两位“母亲”多半是重复录入）
                if (parent != null && parents.any {
                        persons[it]?.gender == parent.gender
                    }) {
                    return Violation(
                        (child?.name ?: "") + "已经记了" +
                            if (parent.gender == Gender.MALE) "父亲" else "母亲")
                }
                // 子女是新父母的祖先 → 成环
                // （PARENT: source=父母 target=子女；子女已在新父母上方即成环）
                if (r.targetPersonId in ancestors(r.sourcePersonId)) {
                    return Violation("会形成辈分循环")
                }
            }
            RelationshipType.SPOUSE -> {
                val a = spouseId(r.sourcePersonId)
                if (a != null) {
                    return Violation(
                        (persons[r.sourcePersonId]?.name ?: "") + "已经有配偶了")
                }
                val b = spouseId(r.targetPersonId)
                if (b != null) {
                    return Violation(
                        (persons[r.targetPersonId]?.name ?: "") + "已经有配偶了")
                }
            }
            // CHILD 为历史遗留类型（写入时已统一为 PARENT），此处不做额外约束；
            // COUSIN 申报记录不产生结构性约束（不参与路径、不限制人数）
            RelationshipType.SIBLING, RelationshipType.CHILD,
            RelationshipType.COUSIN -> {}
        }
        fun spec(e: RelationshipEntity) = io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec(
            e.sourcePersonId, e.targetPersonId, e.type, e.relativeAgeOrder)
        if (io.github.xiaohaoz1.jiarenxia.core.model.GenerationRules.conflicts(rels.map(::spec), spec(r))) {
            return Violation("与已记录的亲子、夫妻或兄弟姐妹关系辈分冲突")
        }
        return null
    }

    /** 校验通过后加入图（供后续关系的约束判定）。 */
    fun add(r: RelationshipEntity) {
        rels += r
    }

    /** 某人在当前图中已有配偶 id（无序对查找），无则 null。 */
    fun spouseOf(personId: String): String? = spouseId(personId)

    /** 某人已有配偶 id（无序对查找），无则 null。 */
    private fun spouseId(personId: String): String? {
        for (e in rels) {
            if (e.type != RelationshipType.SPOUSE) continue
            if (e.sourcePersonId == personId) return e.targetPersonId
            if (e.targetPersonId == personId) return e.sourcePersonId
        }
        return null
    }

    /** 某人的全部父母 id（PARENT: 父/母 → 子女）。 */
    private fun parentIds(personId: String): List<String> =
        rels.filter {
            it.type == RelationshipType.PARENT && it.targetPersonId == personId
        }.map { it.sourcePersonId }

    /** 某人的祖先集合（沿 PARENT 边向上；已访问集合防已有脏数据成环）。 */
    private fun ancestors(personId: String): Set<String> {
        val seen = HashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(personId)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (pid in parentIds(cur)) if (seen.add(pid)) queue.add(pid)
        }
        return seen
    }

    private fun sameUnorderedPair(
        a: RelationshipEntity, b: RelationshipEntity
    ) = unorderedEnds(a) == unorderedEnds(b)

    /** 两条关系是否在同一对人之间（任意类型/方向）。 */
    private fun sharesPair(a: RelationshipEntity, b: RelationshipEntity) =
        unorderedEnds(a) == unorderedEnds(b)

    private fun unorderedEnds(r: RelationshipEntity) =
        if (r.sourcePersonId < r.targetPersonId)
            r.sourcePersonId to r.targetPersonId
        else r.targetPersonId to r.sourcePersonId

    /** 关系的一行文字描述（预览/冲突清单用）。 */
    fun describe(r: RelationshipEntity): String {
        val s = persons[r.sourcePersonId]?.name ?: r.sourcePersonId
        val t = persons[r.targetPersonId]?.name ?: r.targetPersonId
        return when (r.type) {
            RelationshipType.PARENT -> {
                val g = persons[r.sourcePersonId]?.gender
                s + " 是 " + t + " 的" +
                    if (g == Gender.MALE) "父亲" else "母亲"
            }
            RelationshipType.SPOUSE -> s + " 和 " + t + " 是夫妻"
            RelationshipType.SIBLING -> s + " 和 " + t + " 是兄弟姐妹"
            RelationshipType.COUSIN -> s + " 和 " + t + " 是堂表亲"
            RelationshipType.CHILD -> s + " 是 " + t + " 的子女"
        }
    }
}
