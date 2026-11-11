package io.github.xiaohaoz1.jiarenxia.ui.pregnancy

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 完成态「从宝宝的角度看」（FAMILY_AFFAIRS 26 第 3 批）：
 * 父母在前、祖辈随后、其余相关者最后；不含宝宝本人。
 */
class BabyKinLinesTest {

    private fun p(id: String, name: String, gender: Gender) = Person(
        id = id, name = name, gender = gender,
        livingStatus = LivingStatus.ALIVE
    )

    private fun rel(src: String, tgt: String, type: RelationshipType) =
        Relationship("r-" + src + "-" + tgt, src, tgt, type, null)

    /** 杨明+李雪的儿子小宝；双方父母与杨明的弟弟（叔叔辈，非核心家人）。 */
    private fun graph(): FamilyGraph {
        val persons = listOf(
            p("me", "杨明", Gender.MALE),
            p("wife", "李雪", Gender.FEMALE),
            p("baby", "小宝", Gender.MALE),
            p("gfd", "杨福生", Gender.MALE),
            p("gmd", "李桂英", Gender.FEMALE),
            p("fil", "李国强", Gender.MALE),
            p("mil", "陈美华", Gender.FEMALE),
            p("uncle", "杨建春", Gender.MALE)
        )
        val rels = listOf(
            rel("me", "wife", RelationshipType.SPOUSE),
            rel("me", "baby", RelationshipType.PARENT),
            rel("wife", "baby", RelationshipType.PARENT),
            rel("gfd", "me", RelationshipType.PARENT),
            rel("gmd", "me", RelationshipType.PARENT),
            rel("fil", "wife", RelationshipType.PARENT),
            rel("mil", "wife", RelationshipType.PARENT),
            rel("gfd", "uncle", RelationshipType.PARENT)
        )
        return FamilyGraph(persons, rels)
    }

    @Test
    fun 父母在前祖辈随后_不含宝宝本人() {
        val lines = babyKinLines(graph(), "baby", KinshipEngine())

        // 不含宝宝本人（SELF 被跳过；也不应有指向宝宝的称谓行）
        assertFalse(lines.any { it.substringAfter("· ") == "小宝" })
        assertFalse(lines.any { it.startsWith("儿子") || it.startsWith("女儿") })

        // 父母排最前（引擎标准称谓为「父亲/母亲」，男性在前）
        assertEquals("父亲 · 杨明", lines[0])
        assertEquals("母亲 · 李雪", lines[1])

        // 祖辈随后：父系（杨明一方）在前，同侧男性在前；
        // 母亲一方的父母从宝宝视角为姥爷/姥姥
        val gfdIdx = lines.indexOf("爷爷 · 杨福生")
        val gmdIdx = lines.indexOf("奶奶 · 李桂英")
        val filIdx = lines.indexOf("姥爷 · 李国强")
        val milIdx = lines.indexOf("姥姥 · 陈美华")
        assertTrue(gfdIdx > 1 && gmdIdx > gfdIdx)
        assertTrue(filIdx > gmdIdx && milIdx > filIdx)

        // 其余相关者（叔叔辈）在祖辈之后
        val uncleIdx = lines.indexOfFirst { it.endsWith("杨建春") }
        assertTrue(uncleIdx > milIdx)
    }

    @Test
    fun 已故家人不出现在列表() {
        // 杨福生（爷爷）已故 → 整行不出现，其余顺序不变
        val g = graph()
        val lines = babyKinLines(
            g.copy(persons = g.persons.map {
                if (it.id == "gfd") it.copy(livingStatus = LivingStatus.DECEASED)
                else it
            }), "baby", KinshipEngine())
        assertFalse(lines.any { it.endsWith("杨福生") })
        assertEquals("父亲 · 杨明", lines[0])
        assertEquals("母亲 · 李雪", lines[1])
        assertEquals("奶奶 · 李桂英", lines[2])
    }

    @Test
    fun 至多八行() {
        val lines = babyKinLines(graph(), "baby", KinshipEngine())
        assertTrue(lines.size <= 8)
        assertEquals(7, lines.size)
    }
}
