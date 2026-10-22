package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 多条同级最短路径给出不同称谓时，standardTitle 置空而非静默择一。 */
class ConflictingPathsTest {

    private val engine = KinshipEngine()

    @Test
    fun 父辈与母辈都指向同一人物() {
        // x 既是父亲的哥哥（伯父），也是母亲的哥哥（舅舅）——两条最短路径冲突
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dadA", Gender.MALE),
                T.p("momB", Gender.FEMALE),
                T.p("x", Gender.MALE)
            ),
            listOf(
                T.rel("dadA", "me", RelationshipType.PARENT),
                T.rel("momB", "me", RelationshipType.PARENT),
                T.rel("x", "dadA", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
                T.rel("x", "momB", RelationshipType.SIBLING, RelativeAgeOrder.OLDER)
            )
        )
        val r = engine.resolve(g, "me", "x")
        assertEquals(KinshipKind.DERIVED, r.kind)
        assertEquals(Confidence.EXACT, r.confidence)
        // 确定性顺序：dadA 的 id 排在 momB 前 → 取第一条路径的结构关系
        assertEquals("父亲的哥哥", r.structuralDescription)
        assertNull(r.standardTitle)
    }

    @Test
    fun 多条路径称谓一致时保留称谓() {
        // x 通过父亲和祖父都是“父亲的哥哥”：不显式制造冲突
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dadA", Gender.MALE),
                T.p("x", Gender.MALE)
            ),
            listOf(
                T.rel("dadA", "me", RelationshipType.PARENT),
                T.rel("x", "dadA", RelationshipType.SIBLING, RelativeAgeOrder.OLDER)
            )
        )
        assertEquals("伯父", engine.resolve(g, "me", "x").standardTitle)
    }
}
