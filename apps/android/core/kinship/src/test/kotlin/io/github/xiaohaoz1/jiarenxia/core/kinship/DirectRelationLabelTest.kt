package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** directRelationLabel：直接关系的两端视角标签。 */
class DirectRelationLabelTest {

    private val engine = KinshipEngine()

    private val persons = listOf(
        T.p("me", Gender.MALE, self = true),
        T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
        T.p("son", Gender.MALE), T.p("daughter", Gender.FEMALE),
        T.p("wife", Gender.FEMALE),
        T.p("bro", Gender.MALE), T.p("sis", Gender.FEMALE)
    )

    @Test
    fun 父子两端() {
        val r = T.rel("dad", "me", RelationshipType.PARENT)
        val g = T.graph(persons, listOf(r))
        assertEquals("父亲", engine.directRelationLabel(g, "me", r))   // 我看到的是父亲
        assertEquals("儿子", engine.directRelationLabel(g, "dad", r))  // 父亲看到的是儿子
    }

    @Test
    fun 女儿端的父子关系() {
        val r = T.rel("me", "daughter", RelationshipType.PARENT)
        val g = T.graph(persons, listOf(r))
        assertEquals("女儿", engine.directRelationLabel(g, "me", r))
        assertEquals("父亲", engine.directRelationLabel(g, "daughter", r))
    }

    @Test
    fun 母女两端() {
        val r = T.rel("mom", "daughter", RelationshipType.PARENT)
        val g = T.graph(persons, listOf(r))
        assertEquals("女儿", engine.directRelationLabel(g, "mom", r))
        assertEquals("母亲", engine.directRelationLabel(g, "daughter", r))
    }

    @Test
    fun CHILD领域写法的两端() {
        // son CHILD me：son 是 me 的子女
        val r = T.rel("son", "me", RelationshipType.CHILD)
        val g = T.graph(persons, listOf(r))
        assertEquals("儿子", engine.directRelationLabel(g, "me", r))
        assertEquals("父亲", engine.directRelationLabel(g, "son", r))
    }

    @Test
    fun 配偶两端() {
        val r = T.rel("wife", "me", RelationshipType.SPOUSE)
        val g = T.graph(persons, listOf(r))
        assertEquals("妻子", engine.directRelationLabel(g, "me", r))
        assertEquals("丈夫", engine.directRelationLabel(g, "wife", r))
    }

    @Test
    fun 兄弟长幼翻转() {
        val r = T.rel("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER)
        val g = T.graph(persons, listOf(r))
        assertEquals("哥哥", engine.directRelationLabel(g, "me", r))   // bro 相对我年长
        assertEquals("弟弟", engine.directRelationLabel(g, "bro", r))  // 我相对 bro 年幼
    }

    @Test
    fun 长幼相同与未知() {
        val same = T.rel("sis", "me", RelationshipType.SIBLING, RelativeAgeOrder.SAME)
        val g1 = T.graph(persons, listOf(same))
        assertEquals("姐妹（长幼相同）", engine.directRelationLabel(g1, "me", same))
        assertEquals("兄弟（长幼相同）", engine.directRelationLabel(g1, "sis", same))

        val none = T.rel("sis", "me", RelationshipType.SIBLING, null)
        val g2 = T.graph(persons, listOf(none))
        assertEquals("姐妹", engine.directRelationLabel(g2, "me", none))
        assertEquals("兄弟", engine.directRelationLabel(g2, "sis", none))
    }

    @Test
    fun 生日优先于记录() {
        val r = T.rel("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER)
        val g = T.graph(
            persons, listOf(r),
            mapOf("me" to T.bd(1995, 1, 1), "bro" to T.bd(1990, 1, 1))
        )
        // 生日显示 bro 年长，推翻记录
        assertEquals("哥哥", engine.directRelationLabel(g, "me", r))
    }

    @Test
    fun viewpoint不在两端时抛异常() {
        val r = T.rel("dad", "mom", RelationshipType.SPOUSE)
        val g = T.graph(persons, listOf(r))
        assertThrows(IllegalArgumentException::class.java) {
            engine.directRelationLabel(g, "me", r)
        }
    }
}
