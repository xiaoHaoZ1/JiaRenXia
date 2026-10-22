package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** relativeAgeOrder 翻转矩阵：记录方向 × {OLDER, YOUNGER, SAME, null}。 */
class SiblingAgeFlipTest {

    private val engine = KinshipEngine()

    private fun siblingGraph(src: String, tgt: String, order: RelativeAgeOrder?): FamilyGraph =
        T.graph(
            listOf(T.p("me", Gender.MALE, self = true), T.p("bro", Gender.MALE)),
            listOf(T.rel(src, tgt, RelationshipType.SIBLING, order))
        )

    private fun uncleGraph(src: String, tgt: String, order: RelativeAgeOrder?): FamilyGraph =
        T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE), T.p("x", Gender.MALE)
            ),
            listOf(
                T.rel("dad", "me", RelationshipType.PARENT),
                T.rel(src, tgt, RelationshipType.SIBLING, order)
            )
        )

    @Test
    fun 直接兄弟_记录正向() {
        assertEquals("哥哥", engine.resolve(siblingGraph("bro", "me", RelativeAgeOrder.OLDER), "me", "bro").standardTitle)
        assertEquals("弟弟", engine.resolve(siblingGraph("bro", "me", RelativeAgeOrder.YOUNGER), "me", "bro").standardTitle)
        val same = engine.resolve(siblingGraph("bro", "me", RelativeAgeOrder.SAME), "me", "bro")
        assertEquals("兄弟", same.structuralDescription)
        assertNull(same.standardTitle)
        assertEquals(Confidence.EXACT, same.confidence)
        assertTrue(same.missingInformation.isEmpty())
        val unknown = engine.resolve(siblingGraph("bro", "me", null), "me", "bro")
        assertEquals("兄弟", unknown.structuralDescription)
        assertNull(unknown.standardTitle)
        assertEquals(Confidence.PARTIAL, unknown.confidence)
        assertEquals(setOf(MissingInformation.SIBLING_AGE_ORDER), unknown.missingInformation)
    }

    @Test
    fun 直接兄弟_记录反向() {
        // 记录为 me SIBLING bro：order 表示 me 相对 bro
        assertEquals("弟弟", engine.resolve(siblingGraph("me", "bro", RelativeAgeOrder.OLDER), "me", "bro").standardTitle)
        assertEquals("哥哥", engine.resolve(siblingGraph("me", "bro", RelativeAgeOrder.YOUNGER), "me", "bro").standardTitle)
        assertEquals(Confidence.EXACT,
            engine.resolve(siblingGraph("me", "bro", RelativeAgeOrder.SAME), "me", "bro").confidence)
        assertEquals(Confidence.PARTIAL,
            engine.resolve(siblingGraph("me", "bro", null), "me", "bro").confidence)
    }

    @Test
    fun 父辈兄弟_记录正向() {
        // x SIBLING dad：order 表示 x 相对 dad
        assertEquals("伯父", engine.resolve(uncleGraph("x", "dad", RelativeAgeOrder.OLDER), "me", "x").standardTitle)
        assertEquals("叔叔", engine.resolve(uncleGraph("x", "dad", RelativeAgeOrder.YOUNGER), "me", "x").standardTitle)
        val same = engine.resolve(uncleGraph("x", "dad", RelativeAgeOrder.SAME), "me", "x")
        assertEquals("父亲的兄弟", same.structuralDescription)
        assertNull(same.standardTitle)
        assertEquals(Confidence.EXACT, same.confidence)
        val unknown = engine.resolve(uncleGraph("x", "dad", null), "me", "x")
        assertEquals("父亲的兄弟", unknown.structuralDescription)
        assertEquals(Confidence.PARTIAL, unknown.confidence)
        assertEquals(setOf(MissingInformation.SIBLING_AGE_ORDER), unknown.missingInformation)
    }

    @Test
    fun 父辈兄弟_记录反向() {
        // dad SIBLING x：order 表示 dad 相对 x，需取反
        assertEquals("叔叔", engine.resolve(uncleGraph("dad", "x", RelativeAgeOrder.OLDER), "me", "x").standardTitle)
        assertEquals("伯父", engine.resolve(uncleGraph("dad", "x", RelativeAgeOrder.YOUNGER), "me", "x").standardTitle)
        assertEquals(Confidence.EXACT,
            engine.resolve(uncleGraph("dad", "x", RelativeAgeOrder.SAME), "me", "x").confidence)
        assertEquals(Confidence.PARTIAL,
            engine.resolve(uncleGraph("dad", "x", null), "me", "x").confidence)
    }
}
