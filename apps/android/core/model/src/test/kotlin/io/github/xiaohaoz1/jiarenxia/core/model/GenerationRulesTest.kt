package io.github.xiaohaoz1.jiarenxia.core.model

import org.junit.Assert.*
import org.junit.Test

/** 辈分约束跨亲子、配偶与同胞路径检查，使用合成 id。 */
class GenerationRulesTest {
    private fun r(a: String, b: String, t: RelationshipType) = RelationshipSpec(a, b, t, null)
    private val family = listOf(r("mother", "girl", RelationshipType.PARENT),
        r("father", "mother", RelationshipType.SPOUSE))

    @Test fun 母亲的丈夫不能成为女儿的儿子_父女方向有效() {
        assertTrue(GenerationRules.conflicts(family, r("girl", "father", RelationshipType.PARENT)))
        assertFalse(GenerationRules.conflicts(family, r("father", "girl", RelationshipType.PARENT)))
        assertTrue(GenerationRules.conflicts(family, r("father", "girl", RelationshipType.CHILD)))
    }

    @Test fun 最后添加夫妻或同胞关系也要发现矛盾() {
        val parents = listOf(r("mother", "girl", RelationshipType.PARENT),
            r("girl", "father", RelationshipType.PARENT))
        assertTrue(GenerationRules.conflicts(parents, r("father", "mother", RelationshipType.SPOUSE)))
        assertTrue(GenerationRules.conflicts(parents, r("father", "mother", RelationshipType.SIBLING)))
    }

    @Test fun 未连通人物和堂表亲申报不硬推辈分() {
        assertFalse(GenerationRules.conflicts(family, r("new", "girl", RelationshipType.PARENT)))
        val cousin = listOf(r("a", "b", RelationshipType.COUSIN))
        assertFalse(GenerationRules.conflicts(cousin, r("a", "b", RelationshipType.PARENT)))
    }

    @Test fun 旧分量已矛盾时不凭任意路径继续判错() {
        val dirty = family + r("girl", "father", RelationshipType.PARENT)
        assertFalse(GenerationRules.conflicts(dirty, r("mother", "new", RelationshipType.PARENT)))
    }
}
