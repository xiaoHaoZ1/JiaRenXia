package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V0.1 全部一级 / 二级称谓规则（KINSHIP_ENGINE 9–15、34）。 */
class DerivedTitlesTest {

    private val engine = KinshipEngine()

    /** 完整家庭图：覆盖所有 V0.1 称谓。 */
    private val graph: FamilyGraph = run {
        val persons = listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
            T.p("son", Gender.MALE), T.p("daughter", Gender.FEMALE),
            T.p("wife", Gender.FEMALE),
            T.p("eBro", Gender.MALE), T.p("yBro", Gender.MALE),
            T.p("eSis", Gender.FEMALE), T.p("ySis", Gender.FEMALE),
            T.p("gfP", Gender.MALE), T.p("gmP", Gender.FEMALE),
            T.p("gfM", Gender.MALE), T.p("gmM", Gender.FEMALE),
            T.p("fil", Gender.MALE), T.p("mil", Gender.FEMALE),
            T.p("uncleO", Gender.MALE), T.p("uncleY", Gender.MALE),
            T.p("auntD", Gender.FEMALE),
            T.p("uncleM", Gender.MALE), T.p("auntM", Gender.FEMALE)
        )
        val rels = listOf(
            T.rel("dad", "me", RelationshipType.PARENT),
            T.rel("mom", "me", RelationshipType.PARENT),
            T.rel("me", "son", RelationshipType.PARENT),
            T.rel("me", "daughter", RelationshipType.PARENT),
            T.rel("wife", "me", RelationshipType.SPOUSE),
            T.rel("eBro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("yBro", "me", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            T.rel("eSis", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("ySis", "me", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            T.rel("gfP", "dad", RelationshipType.PARENT),
            T.rel("gmP", "dad", RelationshipType.PARENT),
            T.rel("gfM", "mom", RelationshipType.PARENT),
            T.rel("gmM", "mom", RelationshipType.PARENT),
            T.rel("fil", "wife", RelationshipType.PARENT),
            T.rel("mil", "wife", RelationshipType.PARENT),
            T.rel("uncleO", "dad", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("uncleY", "dad", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            T.rel("auntD", "dad", RelationshipType.SIBLING),
            T.rel("uncleM", "mom", RelationshipType.SIBLING),
            T.rel("auntM", "mom", RelationshipType.SIBLING)
        )
        T.graph(persons, rels)
    }

    private fun check(target: String, structural: String, title: String?) {
        val r = engine.resolve(graph, "me", target)
        assertEquals("target= kind", KinshipKind.DERIVED, r.kind)
        assertEquals("target= structural", structural, r.structuralDescription)
        assertEquals("target= title", title, r.standardTitle)
        assertEquals("target= confidence", Confidence.EXACT, r.confidence)
        assertTrue("target= missing", r.missingInformation.isEmpty())
        assertEquals("target= displayTitle", title ?: structural, r.displayTitle)
    }

    @Test
    fun 一级称谓() {
        check("dad", "父亲", "父亲")
        check("mom", "母亲", "母亲")
        check("son", "儿子", "儿子")
        check("daughter", "女儿", "女儿")
        check("wife", "妻子", "妻子")
    }

    @Test
    fun 兄弟姐妹() {
        check("eBro", "哥哥", "哥哥")
        check("yBro", "弟弟", "弟弟")
        check("eSis", "姐姐", "姐姐")
        check("ySis", "妹妹", "妹妹")
    }

    @Test
    fun 祖辈() {
        check("gfP", "父亲的父亲", "爷爷")
        check("gmP", "父亲的母亲", "奶奶")
        check("gfM", "母亲的父亲", "姥爷")
        check("gmM", "母亲的母亲", "姥姥")
    }

    @Test
    fun 配偶父母_男性视角() {
        check("fil", "妻子的父亲", "岳父")
        check("mil", "妻子的母亲", "岳母")
    }

    @Test
    fun 配偶父母_女性视角() {
        val persons = listOf(
            T.p("me", Gender.FEMALE, self = true),
            T.p("husband", Gender.MALE),
            T.p("hf", Gender.MALE), T.p("hm", Gender.FEMALE)
        )
        val rels = listOf(
            T.rel("husband", "me", RelationshipType.SPOUSE),
            T.rel("hf", "husband", RelationshipType.PARENT),
            T.rel("hm", "husband", RelationshipType.PARENT)
        )
        val g = T.graph(persons, rels)
        assertEquals("公公", engine.resolve(g, "me", "hf").standardTitle)
        assertEquals("丈夫的父亲", engine.resolve(g, "me", "hf").structuralDescription)
        assertEquals("婆婆", engine.resolve(g, "me", "hm").standardTitle)
        assertEquals("丈夫的母亲", engine.resolve(g, "me", "hm").structuralDescription)
    }

    @Test
    fun 配偶父母_同性别配偶退化() {
        // 同性别配偶无法确定传统称谓：标题为空、结构关系保留、EXACT（KINSHIP 13.3）
        val persons = listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("spouse", Gender.MALE),
            T.p("sf", Gender.MALE), T.p("sm", Gender.FEMALE)
        )
        val rels = listOf(
            T.rel("spouse", "me", RelationshipType.SPOUSE),
            T.rel("sf", "spouse", RelationshipType.PARENT),
            T.rel("sm", "spouse", RelationshipType.PARENT)
        )
        val g = T.graph(persons, rels)
        val r = engine.resolve(g, "me", "sf")
        assertEquals(KinshipKind.DERIVED, r.kind)
        assertEquals("丈夫的父亲", r.structuralDescription)
        assertNull(r.standardTitle)
        assertEquals(Confidence.EXACT, r.confidence)
        assertTrue(r.missingInformation.isEmpty())
    }

    @Test
    fun 父系伯叔姑与母系舅姨() {
        check("uncleO", "父亲的哥哥", "伯父")
        check("uncleY", "父亲的弟弟", "叔叔")
        check("auntD", "父亲的姐妹", "姑姑")
        check("uncleM", "母亲的兄弟", "舅舅")
        check("auntM", "母亲的姐妹", "姨妈")
    }
}
