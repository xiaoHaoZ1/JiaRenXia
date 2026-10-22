package io.github.xiaohaoz1.jiarenxia.core.model

/** 手工录入及合并共用的辈分约束；只校验，不推断或补写亲属关系。 */
object GenerationRules {
    private fun delta(type: RelationshipType): Int? = when (type) {
        RelationshipType.PARENT -> 1
        RelationshipType.CHILD -> -1
        RelationshipType.SPOUSE, RelationshipType.SIBLING -> 0
        RelationshipType.COUSIN -> null // 堂表亲申报不作为结构性辈分证据
    }

    /**
     * 新关系是否与现有亲子、夫妻、同胞路径的辈分矛盾。
     * 既有连通分量本身矛盾时不凭某条任意路径裁决，留给关系核对入口处理。
     */
    fun conflicts(existing: Iterable<RelationshipSpec>, proposed: RelationshipSpec): Boolean {
        val expected = delta(proposed.type) ?: return false
        val adjacent = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        for (r in existing) {
            val d = delta(r.type) ?: continue
            adjacent.getOrPut(r.sourcePersonId) { mutableListOf() } += r.targetPersonId to d
            adjacent.getOrPut(r.targetPersonId) { mutableListOf() } += r.sourcePersonId to -d
        }
        val levels = mutableMapOf(proposed.sourcePersonId to 0)
        val queue = ArrayDeque<String>()
        queue.add(proposed.sourcePersonId)
        var ambiguous = false
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            for ((other, d) in adjacent[id].orEmpty()) {
                val level = levels.getValue(id) + d
                val old = levels[other]
                if (old == null) { levels[other] = level; queue.add(other) }
                else if (old != level) ambiguous = true
            }
        }
        return !ambiguous && levels[proposed.targetPersonId]?.let { it != expected } == true
    }
}
