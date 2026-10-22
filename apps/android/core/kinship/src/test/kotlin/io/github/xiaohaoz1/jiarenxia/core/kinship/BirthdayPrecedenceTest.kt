package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 生日优先于 relativeAgeOrder（长幼判断优先级：生日 > 显式记录）。 */
class BirthdayPrecedenceTest {

    private val engine = KinshipEngine()

    private fun graph(order: RelativeAgeOrder?, birthdays: Map<String, PartialDate>) =
        T.graph(
            listOf(T.p("me", Gender.MALE, self = true), T.p("bro", Gender.MALE)),
            listOf(T.rel("bro", "me", RelationshipType.SIBLING, order)),
            birthdays
        )

    @Test
    fun 完整生日推翻矛盾的长幼记录() {
        // 记录说 bro 年幼，但生日显示 bro 年长 → 以生日为准
        val g = graph(
            RelativeAgeOrder.YOUNGER,
            mapOf("me" to T.bd(1995, 1, 1), "bro" to T.bd(1990, 1, 1))
        )
        assertEquals("哥哥", engine.resolve(g, "me", "bro").standardTitle)
    }

    @Test
    fun 完整生日相同且无记录_无法确定() {
        val g = graph(null, mapOf("me" to T.bd(1990, 5, 5), "bro" to T.bd(1990, 5, 5)))
        val r = engine.resolve(g, "me", "bro")
        assertEquals("兄弟", r.structuralDescription)
        assertNull(r.standardTitle)
        assertEquals(Confidence.PARTIAL, r.confidence)
        assertEquals(setOf(MissingInformation.SIBLING_AGE_ORDER), r.missingInformation)
    }

    @Test
    fun 完整生日相同但有SAME记录_EXACT() {
        val g = graph(
            RelativeAgeOrder.SAME,
            mapOf("me" to T.bd(1990, 5, 5), "bro" to T.bd(1990, 5, 5))
        )
        val r = engine.resolve(g, "me", "bro")
        assertEquals("兄弟", r.structuralDescription)
        assertNull(r.standardTitle)
        assertEquals(Confidence.EXACT, r.confidence)
    }

    @Test
    fun 完整生日相同但记录OLDER_用记录() {
        val g = graph(
            RelativeAgeOrder.OLDER,
            mapOf("me" to T.bd(1990, 5, 5), "bro" to T.bd(1990, 5, 5))
        )
        assertEquals("哥哥", engine.resolve(g, "me", "bro").standardTitle)
    }

    @Test
    fun 一方生日无年份_退回记录() {
        val g = graph(
            RelativeAgeOrder.OLDER,
            mapOf("me" to T.bd(1995, 1, 1), "bro" to T.bd(2, 10))
        )
        assertEquals("哥哥", engine.resolve(g, "me", "bro").standardTitle)
    }

    @Test
    fun 双方生日都无年份且无记录_PARTIAL() {
        val g = graph(null, mapOf("me" to T.bd(1, 1), "bro" to T.bd(2, 10)))
        val r = engine.resolve(g, "me", "bro")
        assertNull(r.standardTitle)
        assertEquals(Confidence.PARTIAL, r.confidence)
        assertEquals(setOf(MissingInformation.SIBLING_AGE_ORDER), r.missingInformation)
    }
}
