package io.github.xiaohaoz1.jiarenxia.ui.graph

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 兄弟姐妹排行在关系图上的排序：不知道生日时按"第几个孩子"大→小，
 * 姓名拼音相反也不干扰（排行优先于姓名兜底）。
 */
class SiblingRankGraphTest {

    /**
     * 伯公一家：pgf 与 gu 是兄弟（共同父亲 ggf），gu 的四个儿子
     * s1..s4 只有排行没有生日；s1 已婚带上配偶块验证块内成员分辨。
     * 姓名拼音序与排行序故意相反：安安<大明<石头<小伟 = s4,s2,s3,s1。
     */
    private fun overview(withRank: Boolean): FamilyOverview {
        fun rankOf(id: String) = if (!withRank) null else
            mapOf("s1" to 1, "s2" to 2, "s3" to 3, "s4" to 4)[id]
        val people = listOf(
            Triple("self", "我", Gender.MALE), Triple("father", "爸爸", Gender.MALE),
            Triple("mother", "妈妈", Gender.FEMALE), Triple("pgf", "爷爷", Gender.MALE),
            Triple("pgm", "奶奶", Gender.FEMALE), Triple("ggf", "曾祖", Gender.MALE),
            Triple("gu", "伯公", Gender.MALE), Triple("s1", "小伟", Gender.MALE),
            Triple("w1", "小伟妻", Gender.FEMALE), Triple("s2", "大明", Gender.MALE),
            Triple("s3", "石头", Gender.MALE), Triple("s4", "安安", Gender.MALE)
        ).map { (id, name, gender) ->
            Person(id, name, gender, isSelf = id == "self",
                siblingRank = rankOf(id))
        }
        val rels = mutableListOf<Relationship>()
        fun r(a: String, b: String, t: RelationshipType) {
            rels += Relationship("r${rels.size}", a, b, t, null)
        }
        fun couple(a: String, b: String) = r(a, b, RelationshipType.SPOUSE)
        fun parent(a: String, b: String) = r(a, b, RelationshipType.PARENT)
        couple("father", "mother"); couple("pgf", "pgm"); couple("s1", "w1")
        parent("father", "self"); parent("mother", "self")
        parent("pgf", "father")
        parent("ggf", "pgf"); parent("ggf", "gu")
        for (s in listOf("s1", "s2", "s3", "s4")) parent("gu", s)
        val graph = FamilyGraph(people, rels)
        val results = KinshipEngine().resolveAll(graph, "self")
        val byId = people.associate {
            it.id to PersonOverview(it, results.getValue(it.id))
        }
        return FamilyOverview(byId.getValue("self"), graph, byId,
            emptyList(), emptyList())
    }

    private fun GraphModel.at(id: String) = nodes.first { it.personId == id }

    @Test
    fun 无生日时兄弟四家按排行从左到右() {
        val ov = overview(withRank = true)
        val gens = KinshipEngine().generations(ov.graph, "self")
        // 两套布局引擎都该给出 大→小 的横向次序
        for (m in listOf(
            GraphFamilyLayout.build(ov, gens),
            GraphLayout.build(ov, gens))) {
            val xs = listOf("s1", "s2", "s3", "s4").map { m.at(it).x }
            assertTrue("排行 1→2→3→4 应严格从左到右：$xs",
                xs[0] < xs[1] && xs[1] < xs[2] && xs[2] < xs[3])
            assertEquals("四个儿子同代", m.at("father").generation,
                m.at("s1").generation)
        }
    }

    @Test
    fun 未记排行回落到原有排序() {
        val ov = overview(withRank = false)
        val gens = KinshipEngine().generations(ov.graph, "self")
        val m = GraphFamilyLayout.build(ov, gens)
        // 无排行时按姓名：安安(s4) 应排在 小伟(s1) 之前，不产生崩溃
        assertTrue(m.at("s4").x < m.at("s1").x)
        assertEquals(m.at("s1").y, m.at("s4").y, 0.01f)
    }
}
