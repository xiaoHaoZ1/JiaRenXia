package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UNRELATED / SELF / 最大深度 / 循环保护。 */
class GraphEdgeCaseTest {

    private val engine = KinshipEngine()

    @Test
    fun 未关联人物() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE),
                T.p("stranger", Gender.MALE)
            ),
            listOf(T.rel("dad", "me", RelationshipType.PARENT))
        )
        val r = engine.resolve(g, "me", "stranger")
        assertEquals(KinshipKind.UNRELATED, r.kind)
        assertEquals("未关联到我", r.structuralDescription)
        assertNull(r.standardTitle)
        assertEquals(Confidence.PARTIAL, r.confidence)
        assertEquals(setOf(MissingInformation.NO_PATH), r.missingInformation)
        assertTrue(r.relationshipPath.isEmpty())
    }

    @Test
    fun 删除关系后祖辈失联() {
        // 只有 爷爷 PARENT 父亲，父亲与“我”的记录被移除 → 爷爷不可达
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE),
                T.p("gp", Gender.MALE)
            ),
            listOf(T.rel("gp", "dad", RelationshipType.PARENT))
        )
        assertEquals(KinshipKind.UNRELATED, engine.resolve(g, "me", "gp").kind)
        assertEquals(KinshipKind.UNRELATED, engine.resolve(g, "me", "dad").kind)
    }

    @Test
    fun 自身() {
        val g = T.graph(
            listOf(T.p("me", Gender.MALE, self = true), T.p("dad", Gender.MALE)),
            listOf(T.rel("dad", "me", RelationshipType.PARENT))
        )
        val r = engine.resolve(g, "me", "me")
        assertEquals(KinshipKind.SELF, r.kind)
        assertEquals("我", r.structuralDescription)
        assertEquals("我", r.standardTitle)
        assertEquals(Confidence.EXACT, r.confidence)
        assertTrue(r.missingInformation.isEmpty())
        assertTrue(r.relationshipPath.isEmpty())
    }

    @Test
    fun 深度限制_五步可达六步不可达() {
        // me ←P a ←P b ←P c ←P d ←P e ←P f（a..f 依次为更远祖辈，f 为第 6 步）
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("a", Gender.MALE), T.p("b", Gender.MALE), T.p("c", Gender.MALE),
                T.p("d", Gender.MALE), T.p("e", Gender.MALE), T.p("f", Gender.MALE)
            ),
            listOf(
                T.rel("a", "me", RelationshipType.PARENT),
                T.rel("b", "a", RelationshipType.PARENT),
                T.rel("c", "b", RelationshipType.PARENT),
                T.rel("d", "c", RelationshipType.PARENT),
                T.rel("e", "d", RelationshipType.PARENT),
                T.rel("f", "e", RelationshipType.PARENT)
            )
        )
        val c3 = engine.resolve(g, "me", "c")
        assertEquals(KinshipKind.DERIVED, c3.kind)
        assertEquals("父亲的父亲的父亲", c3.structuralDescription)
        assertEquals("太爷爷", c3.standardTitle)
        val d4 = engine.resolve(g, "me", "d")
        assertEquals(KinshipKind.STRUCTURAL, d4.kind)
        assertEquals("父亲的父亲的父亲的父亲", d4.structuralDescription)
        val e5 = engine.resolve(g, "me", "e")
        assertEquals(KinshipKind.STRUCTURAL, e5.kind)
        assertEquals("父亲的父亲的父亲的父亲的父亲", e5.structuralDescription)
        assertEquals(5, e5.relationshipPath.size)
        assertEquals(KinshipKind.UNRELATED, engine.resolve(g, "me", "f").kind)
    }


    @Test
    fun resolveAll覆盖图中所有人物() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE),
                T.p("stranger", Gender.MALE)
            ),
            listOf(T.rel("dad", "me", RelationshipType.PARENT))
        )
        val all = engine.resolveAll(g, "me")
        assertEquals(setOf("me", "dad", "stranger"), all.keys)
        assertEquals("父亲", all.getValue("dad").standardTitle)
        assertEquals(KinshipKind.SELF, all.getValue("me").kind)
        assertEquals(KinshipKind.UNRELATED, all.getValue("stranger").kind)
    }

    @Test
    fun 循环保护_终止且返回结果() {
        // 绕过校验的循环数据：A PARENT B + B PARENT A
        val g = T.graph(
            listOf(T.p("me", Gender.MALE, self = true), T.p("x", Gender.MALE)),
            listOf(
                T.rel("me", "x", RelationshipType.PARENT),   // me 是 x 的父母
                T.rel("x", "me", RelationshipType.PARENT)    // x 是 me 的父母
            )
        )
        val r = engine.resolve(g, "me", "x")
        // me→x 有两条一级路径：[CHILD]=儿子 与 [PARENT]=父亲；同级冲突 → 称谓置空
        assertEquals(KinshipKind.DERIVED, r.kind)
        assertEquals("父亲", r.structuralDescription)
        assertNull(r.standardTitle)
        assertEquals(Confidence.EXACT, r.confidence)
    }
}
