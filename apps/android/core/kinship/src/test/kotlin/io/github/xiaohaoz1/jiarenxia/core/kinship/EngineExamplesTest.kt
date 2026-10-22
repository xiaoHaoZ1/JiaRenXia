package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** KINSHIP_ENGINE 29–32 的四个示例。 */
class EngineExamplesTest {

    private val engine = KinshipEngine()

    @Test
    fun 示例一_爷爷() {
        val g = T.graph(
            listOf(
                T.p("杨明", Gender.MALE, self = true),
                T.p("杨建国", Gender.MALE),
                T.p("杨福生", Gender.MALE)
            ),
            listOf(
                T.rel("杨建国", "杨明", RelationshipType.PARENT),
                T.rel("杨福生", "杨建国", RelationshipType.PARENT)
            )
        )
        val r = engine.resolve(g, "杨明", "杨福生")
        assertEquals("父亲的父亲", r.structuralDescription)
        assertEquals("爷爷", r.standardTitle)
        assertEquals(Confidence.EXACT, r.confidence)
        assertEquals(KinshipKind.DERIVED, r.kind)
    }

    @Test
    fun 示例二_伯父() {
        val g = T.graph(
            listOf(
                T.p("杨明", Gender.MALE, self = true),
                T.p("杨建国", Gender.MALE),
                T.p("杨建春", Gender.MALE)
            ),
            listOf(
                T.rel("杨建国", "杨明", RelationshipType.PARENT),
                T.rel("杨建春", "杨建国", RelationshipType.SIBLING)
            ),
            mapOf(
                "杨建国" to T.bd(1970, 3, 15),
                "杨建春" to T.bd(1965, 8, 20)
            )
        )
        val r = engine.resolve(g, "杨明", "杨建春")
        assertEquals("父亲的哥哥", r.structuralDescription)
        assertEquals("伯父", r.standardTitle)
        assertEquals(Confidence.EXACT, r.confidence)
    }

    @Test
    fun 示例三_无法确定伯父还是叔叔() {
        val g = T.graph(
            listOf(
                T.p("杨明", Gender.MALE, self = true),
                T.p("杨建国", Gender.MALE),
                T.p("杨建春", Gender.MALE)
            ),
            listOf(
                T.rel("杨建国", "杨明", RelationshipType.PARENT),
                T.rel("杨建春", "杨建国", RelationshipType.SIBLING)
            )
        )
        val r = engine.resolve(g, "杨明", "杨建春")
        assertEquals("父亲的兄弟", r.structuralDescription)
        assertNull(r.standardTitle)
        assertEquals(Confidence.PARTIAL, r.confidence)
        assertEquals(setOf(MissingInformation.SIBLING_AGE_ORDER), r.missingInformation)
    }

    @Test
    fun 示例四_岳父() {
        val g = T.graph(
            listOf(
                T.p("杨明", Gender.MALE, self = true),
                T.p("李雪", Gender.FEMALE),
                T.p("李国强", Gender.MALE)
            ),
            listOf(
                T.rel("李雪", "杨明", RelationshipType.SPOUSE),
                T.rel("李国强", "李雪", RelationshipType.PARENT)
            )
        )
        val r = engine.resolve(g, "杨明", "李国强")
        assertEquals("妻子的父亲", r.structuralDescription)
        assertEquals("岳父", r.standardTitle)
        assertEquals(Confidence.EXACT, r.confidence)
    }
}
