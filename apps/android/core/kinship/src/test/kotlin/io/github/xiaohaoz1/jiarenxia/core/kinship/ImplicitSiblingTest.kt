package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** 隐式兄弟姐妹：共享父母即视为兄弟姐妹（DEVELOPMENT_PLAN 2.4 四）。 */
class ImplicitSiblingTest {

    private val engine = KinshipEngine()

    private fun uncleImplicit(birthdays: Map<String, PartialDate>): FamilyGraph =
        T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE),
                T.p("gp", Gender.MALE),
                T.p("x", Gender.MALE)
            ),
            listOf(
                T.rel("dad", "me", RelationshipType.PARENT),
                T.rel("gp", "dad", RelationshipType.PARENT),
                T.rel("gp", "x", RelationshipType.PARENT)  // x 与 dad 共享父亲 → 隐式兄弟
            ),
            birthdays
        )

    @Test
    fun 隐式伯父_生日可比较() {
        val g = uncleImplicit(mapOf("dad" to T.bd(1970, 3, 15), "x" to T.bd(1965, 8, 20)))
        val r = engine.resolve(g, "me", "x")
        assertEquals("父亲的哥哥", r.structuralDescription)
        assertEquals("伯父", r.standardTitle)
        assertEquals(Confidence.EXACT, r.confidence)
        // 隐式步骤标记
        assertEquals(true, r.relationshipPath.last().implicit)
    }

    @Test
    fun 隐式伯父_无生日降级() {
        val g = uncleImplicit(emptyMap())
        val r = engine.resolve(g, "me", "x")
        assertEquals("父亲的兄弟", r.structuralDescription)
        assertNull(r.standardTitle)
        assertEquals(Confidence.PARTIAL, r.confidence)
        assertEquals(setOf(MissingInformation.SIBLING_AGE_ORDER), r.missingInformation)
    }

    @Test
    fun 隐式兄弟_共享父母加生日() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("mom", Gender.FEMALE),
                T.p("y", Gender.MALE)
            ),
            listOf(
                T.rel("mom", "me", RelationshipType.PARENT),
                T.rel("mom", "y", RelationshipType.PARENT)
            ),
            mapOf("me" to T.bd(1995, 1, 1), "y" to T.bd(1990, 1, 1))
        )
        val r = engine.resolve(g, "me", "y")
        assertEquals("哥哥", r.standardTitle)
        assertEquals(true, r.relationshipPath.last().implicit)
    }

    @Test
    fun 显式记录优先于隐式兄弟() {
        // 共享父母 + 显式 SIBLING(YOUNGER)：走显式边，implicit=false，用记录的 relativeAgeOrder
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE),
                T.p("gp", Gender.MALE),
                T.p("x", Gender.MALE)
            ),
            listOf(
                T.rel("dad", "me", RelationshipType.PARENT),
                T.rel("gp", "dad", RelationshipType.PARENT),
                T.rel("gp", "x", RelationshipType.PARENT),
                T.rel("x", "dad", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER)
            )
        )
        val r = engine.resolve(g, "me", "x")
        assertEquals("父亲的弟弟", r.structuralDescription)
        assertEquals("叔叔", r.standardTitle)
        assertFalse(r.relationshipPath.last().implicit)
    }
}
