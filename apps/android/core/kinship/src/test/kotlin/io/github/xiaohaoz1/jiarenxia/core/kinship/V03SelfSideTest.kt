package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** V0.3 本人一侧扩展称谓（KINSHIP_ENGINE 38.2 表 A–M）。 */
class V03SelfSideTest {

    private val engine = KinshipEngine()

    private fun check(
        graph: FamilyGraph, target: String, structural: String, title: String?,
        confidence: Confidence = Confidence.EXACT,
        missing: Set<MissingInformation> = emptySet()
    ) {
        val r = engine.resolve(graph, "me", target)
        assertEquals("target= kind", KinshipKind.DERIVED, r.kind)
        assertEquals("target= structural", structural, r.structuralDescription)
        assertEquals("target= title", title, r.standardTitle)
        assertEquals("target= confidence", confidence, r.confidence)
        assertEquals("target= missing", missing, r.missingInformation)
    }

    // ---------- A. 配偶 ----------

    @Test
    fun `A-配偶按性别`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("wife", Gender.FEMALE), T.p("husb", Gender.MALE)
            ),
            listOf(
                T.rel("me", "wife", RelationshipType.SPOUSE),
                T.rel("me", "husb", RelationshipType.SPOUSE)
            )
        )
        check(g, "wife", "妻子", "妻子")
        check(g, "husb", "丈夫", "丈夫")
    }

    // ---------- B. 曾祖辈 ----------

    @Test
    fun `B-曾祖辈八种`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
                T.p("gfP", Gender.MALE), T.p("gmP", Gender.FEMALE),
                T.p("gfM", Gender.MALE), T.p("gmM", Gender.FEMALE),
                T.p("fff", Gender.MALE), T.p("ffm", Gender.FEMALE),
                T.p("fmf", Gender.MALE), T.p("fmm", Gender.FEMALE),
                T.p("mff", Gender.MALE), T.p("mfm", Gender.FEMALE),
                T.p("mmf", Gender.MALE), T.p("mmm", Gender.FEMALE)
            ),
            listOf(
                T.rel("dad", "me", RelationshipType.PARENT),
                T.rel("mom", "me", RelationshipType.PARENT),
                T.rel("gfP", "dad", RelationshipType.PARENT),
                T.rel("gmP", "dad", RelationshipType.PARENT),
                T.rel("gfM", "mom", RelationshipType.PARENT),
                T.rel("gmM", "mom", RelationshipType.PARENT),
                T.rel("fff", "gfP", RelationshipType.PARENT),
                T.rel("ffm", "gfP", RelationshipType.PARENT),
                T.rel("fmf", "gmP", RelationshipType.PARENT),
                T.rel("fmm", "gmP", RelationshipType.PARENT),
                T.rel("mff", "gfM", RelationshipType.PARENT),
                T.rel("mfm", "gfM", RelationshipType.PARENT),
                T.rel("mmf", "gmM", RelationshipType.PARENT),
                T.rel("mmm", "gmM", RelationshipType.PARENT)
            )
        )
        check(g, "fff", "父亲的父亲的父亲", "太爷爷")
        check(g, "ffm", "父亲的父亲的母亲", "太奶奶")
        check(g, "fmf", "父亲的母亲的父亲", "曾外祖父")
        check(g, "fmm", "父亲的母亲的母亲", "曾外祖母")
        check(g, "mff", "母亲的父亲的父亲", "太姥爷")
        check(g, "mfm", "母亲的父亲的母亲", "太姥姥")
        check(g, "mmf", "母亲的母亲的父亲", "外曾外祖父")
        check(g, "mmm", "母亲的母亲的母亲", "外曾外祖母")
    }

    // ---------- C / D. 祖辈的兄弟姐妹及其配偶 ----------

    private fun grandSibGraph(): FamilyGraph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
            T.p("gfP", Gender.MALE), T.p("gmP", Gender.FEMALE),
            T.p("gfM", Gender.MALE), T.p("gmM", Gender.FEMALE),
            T.p("gsO", Gender.MALE), T.p("gsY", Gender.MALE),
            T.p("gsU", Gender.MALE), T.p("gsSame", Gender.MALE),
            T.p("gsA", Gender.FEMALE),
            T.p("gmbM", Gender.MALE), T.p("gmsF", Gender.FEMALE),
            T.p("mgfO", Gender.MALE), T.p("mgfY", Gender.MALE),
            T.p("mgfU", Gender.MALE), T.p("mgfA", Gender.FEMALE),
            T.p("mgmB", Gender.MALE), T.p("mgmS", Gender.FEMALE),
            T.p("wBo", Gender.FEMALE), T.p("wShu", Gender.FEMALE),
            T.p("wUnk", Gender.FEMALE),
            T.p("hGu", Gender.MALE), T.p("wGu", Gender.FEMALE),
            T.p("wJiu", Gender.FEMALE), T.p("hYi", Gender.MALE),
            T.p("wWBo", Gender.FEMALE), T.p("wWShu", Gender.FEMALE),
            T.p("wWUnk", Gender.FEMALE),
            T.p("hGLao", Gender.MALE), T.p("wJLao", Gender.FEMALE), T.p("hYLao", Gender.MALE)
        ),
        listOf(
            T.rel("dad", "me", RelationshipType.PARENT),
            T.rel("mom", "me", RelationshipType.PARENT),
            T.rel("gfP", "dad", RelationshipType.PARENT),
            T.rel("gmP", "dad", RelationshipType.PARENT),
            T.rel("gfM", "mom", RelationshipType.PARENT),
            T.rel("gmM", "mom", RelationshipType.PARENT),
            T.rel("gsO", "gfP", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("gsY", "gfP", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            T.rel("gsU", "gfP", RelationshipType.SIBLING),
            T.rel("gsSame", "gfP", RelationshipType.SIBLING, RelativeAgeOrder.SAME),
            T.rel("gsA", "gfP", RelationshipType.SIBLING),
            T.rel("gmbM", "gmP", RelationshipType.SIBLING),
            T.rel("gmsF", "gmP", RelationshipType.SIBLING),
            T.rel("mgfO", "gfM", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("mgfY", "gfM", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            T.rel("mgfU", "gfM", RelationshipType.SIBLING),
            T.rel("mgfA", "gfM", RelationshipType.SIBLING),
            T.rel("mgmB", "gmM", RelationshipType.SIBLING),
            T.rel("mgmS", "gmM", RelationshipType.SIBLING),
            T.rel("gsO", "wBo", RelationshipType.SPOUSE),
            T.rel("gsY", "wShu", RelationshipType.SPOUSE),
            T.rel("gsU", "wUnk", RelationshipType.SPOUSE),
            T.rel("gsA", "hGu", RelationshipType.SPOUSE),
            T.rel("gsA", "wGu", RelationshipType.SPOUSE),
            T.rel("gmbM", "wJiu", RelationshipType.SPOUSE),
            T.rel("gmsF", "hYi", RelationshipType.SPOUSE),
            T.rel("mgfO", "wWBo", RelationshipType.SPOUSE),
            T.rel("mgfY", "wWShu", RelationshipType.SPOUSE),
            T.rel("mgfU", "wWUnk", RelationshipType.SPOUSE),
            T.rel("mgfA", "hGLao", RelationshipType.SPOUSE),
            T.rel("mgmB", "wJLao", RelationshipType.SPOUSE),
            T.rel("mgmS", "hYLao", RelationshipType.SPOUSE)
        )
    )

    @Test
    fun `C-祖辈的兄弟姐妹`() {
        val g = grandSibGraph()
        check(g, "gsO", "父亲的父亲的哥哥", "伯祖父")
        check(g, "gsY", "父亲的父亲的弟弟", "叔祖父")
        check(g, "gsU", "父亲的父亲的兄弟", null,
            Confidence.PARTIAL, setOf(MissingInformation.SIBLING_AGE_ORDER))
        check(g, "gsSame", "父亲的父亲的兄弟", null)   // SAME → 空称谓 EXACT
        check(g, "gsA", "父亲的父亲的姐妹", "姑奶奶")
        check(g, "gmbM", "父亲的母亲的兄弟", "舅爷爷")
        check(g, "gmsF", "父亲的母亲的姐妹", "姨奶奶")
        check(g, "mgfO", "母亲的父亲的哥哥", "外伯祖父")
        check(g, "mgfY", "母亲的父亲的弟弟", "外叔祖父")
        check(g, "mgfU", "母亲的父亲的兄弟", null,
            Confidence.PARTIAL, setOf(MissingInformation.SIBLING_AGE_ORDER))
        check(g, "mgfA", "母亲的父亲的姐妹", "姑姥姥")
        check(g, "mgmB", "母亲的母亲的兄弟", "舅姥爷")
        check(g, "mgmS", "母亲的母亲的姐妹", "姨姥姥")
    }

    @Test
    fun `D-祖辈兄弟姐妹的配偶`() {
        val g = grandSibGraph()
        check(g, "wBo", "父亲的父亲的哥哥的妻子", "伯祖母")
        check(g, "wShu", "父亲的父亲的弟弟的妻子", "叔祖母")
        check(g, "wUnk", "父亲的父亲的兄弟的妻子", null,
            Confidence.PARTIAL, setOf(MissingInformation.SIBLING_AGE_ORDER))
        check(g, "hGu", "父亲的父亲的姐妹的丈夫", "姑爷爷")
        check(g, "wGu", "父亲的父亲的姐妹的妻子", null)   // 其余性别组合 → 空 EXACT
        check(g, "wJiu", "父亲的母亲的兄弟的妻子", "舅奶奶")
        check(g, "hYi", "父亲的母亲的姐妹的丈夫", "姨爷爷")
        check(g, "wWBo", "母亲的父亲的哥哥的妻子", "外伯祖母")
        check(g, "wWShu", "母亲的父亲的弟弟的妻子", "外叔祖母")
        check(g, "wWUnk", "母亲的父亲的兄弟的妻子", null,
            Confidence.PARTIAL, setOf(MissingInformation.SIBLING_AGE_ORDER))
        check(g, "hGLao", "母亲的父亲的姐妹的丈夫", "姑姥爷")
        check(g, "wJLao", "母亲的母亲的兄弟的妻子", "舅姥姥")
        check(g, "hYLao", "母亲的母亲的姐妹的丈夫", "姨姥爷")
    }

    // ---------- E / F. 父母的堂表兄弟姐妹及其配偶 ----------

    private fun parentCousinGraph(): FamilyGraph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
            T.p("gfP", Gender.MALE), T.p("gmP", Gender.FEMALE),
            T.p("gfM", Gender.MALE), T.p("gmM", Gender.FEMALE),
            T.p("gfBro", Gender.MALE),   // 爷爷的兄弟 → 堂
            T.p("gfSis", Gender.FEMALE), // 爷爷的姐妹 → 表
            T.p("gmBro", Gender.MALE),   // 奶奶的兄弟 → 表
            T.p("mgBro", Gender.MALE),   // 姥爷的兄弟 → 堂
            T.p("mgmBro", Gender.MALE),  // 姥姥的兄弟 → 表
            T.p("tangO", Gender.MALE), T.p("tangY", Gender.MALE),
            T.p("tangU", Gender.MALE), T.p("tangS", Gender.MALE),
            T.p("tangA", Gender.FEMALE),
            T.p("biaoO", Gender.MALE), T.p("biaoY", Gender.MALE),
            T.p("biaoA", Gender.FEMALE), T.p("biaoSis", Gender.MALE),
            T.p("tangJiu", Gender.MALE), T.p("tangYi", Gender.FEMALE),
            T.p("biaoJiu", Gender.MALE), T.p("biaoYi", Gender.FEMALE),
            // F：堂表亲的配偶
            T.p("tangOw", Gender.FEMALE), T.p("tangYw", Gender.FEMALE),
            T.p("tangUw", Gender.FEMALE), T.p("tangUh", Gender.MALE),
            T.p("tangAh", Gender.MALE), T.p("tangAhW", Gender.FEMALE),
            T.p("biaoOw", Gender.FEMALE), T.p("biaoAh", Gender.MALE),
            T.p("tangJiuW", Gender.FEMALE), T.p("tangYiH", Gender.MALE),
            T.p("biaoJiuW", Gender.FEMALE), T.p("biaoYiH", Gender.MALE)
        ),
        listOf(
            T.rel("dad", "me", RelationshipType.PARENT),
            T.rel("mom", "me", RelationshipType.PARENT),
            T.rel("gfP", "dad", RelationshipType.PARENT),
            T.rel("gmP", "dad", RelationshipType.PARENT),
            T.rel("gfM", "mom", RelationshipType.PARENT),
            T.rel("gmM", "mom", RelationshipType.PARENT),
            T.rel("gfBro", "gfP", RelationshipType.SIBLING),
            T.rel("gfSis", "gfP", RelationshipType.SIBLING),
            T.rel("gmBro", "gmP", RelationshipType.SIBLING),
            T.rel("mgBro", "gfM", RelationshipType.SIBLING),
            T.rel("mgmBro", "gmM", RelationshipType.SIBLING),
            T.rel("gfBro", "tangO", RelationshipType.PARENT),
            T.rel("gfBro", "tangY", RelationshipType.PARENT),
            T.rel("gfBro", "tangU", RelationshipType.PARENT),
            T.rel("gfBro", "tangS", RelationshipType.PARENT),
            T.rel("gfBro", "tangA", RelationshipType.PARENT),
            T.rel("gmBro", "biaoO", RelationshipType.PARENT),
            T.rel("gmBro", "biaoY", RelationshipType.PARENT),
            T.rel("gmBro", "biaoA", RelationshipType.PARENT),
            T.rel("gfSis", "biaoSis", RelationshipType.PARENT),
            T.rel("mgBro", "tangJiu", RelationshipType.PARENT),
            T.rel("mgBro", "tangYi", RelationshipType.PARENT),
            T.rel("mgmBro", "biaoJiu", RelationshipType.PARENT),
            T.rel("mgmBro", "biaoYi", RelationshipType.PARENT),
            T.rel("tangO", "tangOw", RelationshipType.SPOUSE),
            T.rel("tangY", "tangYw", RelationshipType.SPOUSE),
            T.rel("tangU", "tangUw", RelationshipType.SPOUSE),
            T.rel("tangU", "tangUh", RelationshipType.SPOUSE),
            T.rel("tangA", "tangAh", RelationshipType.SPOUSE),
            T.rel("tangA", "tangAhW", RelationshipType.SPOUSE),
            T.rel("biaoO", "biaoOw", RelationshipType.SPOUSE),
            T.rel("biaoA", "biaoAh", RelationshipType.SPOUSE),
            T.rel("tangJiu", "tangJiuW", RelationshipType.SPOUSE),
            T.rel("tangYi", "tangYiH", RelationshipType.SPOUSE),
            T.rel("biaoJiu", "biaoJiuW", RelationshipType.SPOUSE),
            T.rel("biaoYi", "biaoYiH", RelationshipType.SPOUSE)
        ),
        mapOf(
            "dad" to T.bd(1962, 3, 1), "mom" to T.bd(1964, 5, 6),
            "tangO" to T.bd(1958, 1, 1),   // 长于父亲 → 堂伯
            "tangY" to T.bd(1970, 1, 1),   // 幼于父亲 → 堂叔
            "tangS" to T.bd(1962, 3, 1),   // 同日 → SAME
            "tangA" to T.bd(1960, 6, 6),   // 长于父亲 → 堂姐
            "biaoO" to T.bd(1955, 1, 1), "biaoY" to T.bd(1968, 1, 1),
            "biaoA" to T.bd(1960, 2, 2), "biaoSis" to T.bd(1968, 7, 7),
            "tangJiu" to T.bd(1960, 1, 1), "tangYi" to T.bd(1960, 1, 1),
            "biaoJiu" to T.bd(1960, 1, 1), "biaoYi" to T.bd(1968, 1, 1)
        )
    )

    @Test
    fun `E-父母的堂表兄弟姐妹`() {
        val g = parentCousinGraph()
        check(g, "tangO", "父亲的堂哥", "堂伯")
        check(g, "tangY", "父亲的堂弟", "堂叔")
        check(g, "tangU", "父亲的堂兄弟", null,
            Confidence.PARTIAL, setOf(MissingInformation.BIRTH_DATES))
        check(g, "tangS", "父亲的堂兄弟", null)     // SAME → 空 EXACT
        check(g, "tangA", "父亲的堂姐", "堂姑")
        check(g, "biaoO", "父亲的表哥", "表伯")      // 经奶奶的兄弟
        check(g, "biaoY", "父亲的表弟", "表叔")
        check(g, "biaoA", "父亲的表姐", "表姑")
        check(g, "biaoSis", "父亲的表弟", "表叔")    // 经爷爷的姐妹 → 表
        check(g, "tangJiu", "母亲的堂哥", "堂舅")
        check(g, "tangYi", "母亲的堂姐", "堂姨")
        check(g, "biaoJiu", "母亲的表哥", "表舅")
        check(g, "biaoYi", "母亲的表妹", "表姨")
    }

    @Test
    fun `E-隐式堂亲_曾祖父为共同父亲`() {
        // 曾祖父是爷爷与其兄弟的共同父亲 → 隐式兄弟 → 仍按“堂”
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE), T.p("gfP", Gender.MALE),
                T.p("ggf", Gender.MALE), T.p("gfB", Gender.MALE),
                T.p("tangC", Gender.MALE)
            ),
            listOf(
                T.rel("dad", "me", RelationshipType.PARENT),
                T.rel("gfP", "dad", RelationshipType.PARENT),
                T.rel("ggf", "gfP", RelationshipType.PARENT),
                T.rel("ggf", "gfB", RelationshipType.PARENT),
                T.rel("gfB", "tangC", RelationshipType.PARENT)
            ),
            mapOf("dad" to T.bd(1962, 3, 1), "tangC" to T.bd(1970, 1, 1))
        )
        check(g, "tangC", "父亲的堂弟", "堂叔")
        assertEquals(true, engine.resolve(g, "me", "tangC").relationshipPath[2].implicit)
    }

    @Test
    fun `F-父母堂表亲的配偶_五步`() {
        val g = parentCousinGraph()
        check(g, "tangOw", "父亲的堂哥的妻子", "堂伯母")
        check(g, "tangYw", "父亲的堂弟的妻子", "堂婶")
        check(g, "tangUw", "父亲的堂兄弟的妻子", null,
            Confidence.PARTIAL, setOf(MissingInformation.BIRTH_DATES))
        check(g, "tangUh", "父亲的堂兄弟的丈夫", null)    // 其余性别组合
        check(g, "tangAh", "父亲的堂姐的丈夫", "堂姑父")
        check(g, "tangAhW", "父亲的堂姐的妻子", null)     // 其余性别组合
        check(g, "biaoOw", "父亲的表哥的妻子", "表伯母")
        check(g, "biaoAh", "父亲的表姐的丈夫", "表姑父")
        check(g, "tangJiuW", "母亲的堂哥的妻子", "堂舅妈")
        check(g, "tangYiH", "母亲的堂姐的丈夫", "堂姨夫")
        check(g, "biaoJiuW", "母亲的表哥的妻子", "表舅妈")
        check(g, "biaoYiH", "母亲的表妹的丈夫", "表姨夫")
    }

    // ---------- G / H. 堂表兄弟姐妹的配偶与子女 ----------

    private fun myCousinGraph(): FamilyGraph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
            T.p("uncle", Gender.MALE),   // 父亲的兄弟 → 堂
            T.p("auntM", Gender.FEMALE), // 母亲的姐妹 → 表
            T.p("coO", Gender.MALE), T.p("coY", Gender.MALE),
            T.p("coU", Gender.MALE), T.p("coS", Gender.MALE),
            T.p("coA", Gender.FEMALE), T.p("coAY", Gender.FEMALE),
            T.p("bcoO", Gender.MALE), T.p("bcoA", Gender.FEMALE),
            T.p("coOh", Gender.MALE),
            T.p("wO", Gender.FEMALE), T.p("wY", Gender.FEMALE),
            T.p("wU", Gender.FEMALE), T.p("wS", Gender.FEMALE),
            T.p("hA", Gender.MALE), T.p("hAY", Gender.MALE),
            T.p("bwO", Gender.FEMALE), T.p("bhA", Gender.MALE),
            // H：堂表亲的子女
            T.p("coSon", Gender.MALE), T.p("coDau", Gender.FEMALE),
            T.p("coFson", Gender.MALE), T.p("coFdau", Gender.FEMALE),
            T.p("bcoSon", Gender.MALE), T.p("bcoDau", Gender.FEMALE),
            T.p("bcoFson", Gender.MALE), T.p("bcoFdau", Gender.FEMALE)
        ),
        listOf(
            T.rel("dad", "me", RelationshipType.PARENT),
            T.rel("mom", "me", RelationshipType.PARENT),
            T.rel("uncle", "dad", RelationshipType.SIBLING),
            T.rel("auntM", "mom", RelationshipType.SIBLING),
            T.rel("uncle", "coO", RelationshipType.PARENT),
            T.rel("uncle", "coY", RelationshipType.PARENT),
            T.rel("uncle", "coU", RelationshipType.PARENT),
            T.rel("uncle", "coS", RelationshipType.PARENT),
            T.rel("uncle", "coA", RelationshipType.PARENT),
            T.rel("uncle", "coAY", RelationshipType.PARENT),
            T.rel("auntM", "bcoO", RelationshipType.PARENT),
            T.rel("auntM", "bcoA", RelationshipType.PARENT),
            T.rel("coO", "wO", RelationshipType.SPOUSE),
            T.rel("coY", "wY", RelationshipType.SPOUSE),
            T.rel("coU", "wU", RelationshipType.SPOUSE),
            T.rel("coS", "wS", RelationshipType.SPOUSE),
            T.rel("coA", "hA", RelationshipType.SPOUSE),
            T.rel("coAY", "hAY", RelationshipType.SPOUSE),
            T.rel("coO", "coOh", RelationshipType.SPOUSE),
            T.rel("bcoO", "bwO", RelationshipType.SPOUSE),
            T.rel("bcoA", "bhA", RelationshipType.SPOUSE),
            T.rel("coO", "coSon", RelationshipType.PARENT),
            T.rel("coO", "coDau", RelationshipType.PARENT),
            T.rel("coA", "coFson", RelationshipType.PARENT),
            T.rel("coA", "coFdau", RelationshipType.PARENT),
            T.rel("bcoO", "bcoSon", RelationshipType.PARENT),
            T.rel("bcoO", "bcoDau", RelationshipType.PARENT),
            T.rel("bcoA", "bcoFson", RelationshipType.PARENT),
            T.rel("bcoA", "bcoFdau", RelationshipType.PARENT)
        ),
        mapOf(
            "me" to T.bd(1990, 5, 1),
            "coO" to T.bd(1986, 1, 1), "coY" to T.bd(1995, 1, 1),
            "coS" to T.bd(1990, 5, 1),   // 同日 → SAME
            "coA" to T.bd(1985, 1, 1), "coAY" to T.bd(1996, 1, 1),
            "bcoO" to T.bd(1988, 1, 1), "bcoA" to T.bd(1987, 1, 1)
        )
    )

    @Test
    fun `G-堂表兄弟姐妹的配偶`() {
        val g = myCousinGraph()
        check(g, "wO", "堂哥的妻子", "堂嫂")
        check(g, "wY", "堂弟的妻子", "堂弟媳")
        check(g, "wU", "堂兄弟的妻子", null,
            Confidence.PARTIAL, setOf(MissingInformation.BIRTH_DATES))
        check(g, "wS", "堂兄弟的妻子", null)      // SAME → 空 EXACT
        check(g, "hA", "堂姐的丈夫", "堂姐夫")
        check(g, "hAY", "堂妹的丈夫", "堂妹夫")
        check(g, "coOh", "堂哥的丈夫", null)       // 同性别组合 → 空 EXACT
        check(g, "bwO", "表哥的妻子", "表嫂")
        check(g, "bhA", "表姐的丈夫", "表姐夫")
    }

    @Test
    fun `H-堂表兄弟姐妹的子女`() {
        val g = myCousinGraph()
        check(g, "coSon", "堂哥的儿子", "堂侄")
        check(g, "coDau", "堂哥的女儿", "堂侄女")
        check(g, "coFson", "堂姐的儿子", "堂外甥")
        check(g, "coFdau", "堂姐的女儿", "堂外甥女")
        check(g, "bcoSon", "表哥的儿子", "表侄")
        check(g, "bcoDau", "表哥的女儿", "表侄女")
        check(g, "bcoFson", "表姐的儿子", "表外甥")
        check(g, "bcoFdau", "表姐的女儿", "表外甥女")
    }

    // ---------- I / J. 兄弟姐妹的子女的配偶与孙辈 ----------

    private fun sibDescGraph(): FamilyGraph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("bro", Gender.MALE), T.p("sis", Gender.FEMALE),
            T.p("bSon", Gender.MALE), T.p("bDau", Gender.FEMALE),
            T.p("sSon", Gender.MALE), T.p("sDau", Gender.FEMALE),
            T.p("bSonW", Gender.FEMALE), T.p("bSonH", Gender.MALE),
            T.p("bDauH", Gender.MALE),
            T.p("sSonW", Gender.FEMALE), T.p("sDauH", Gender.MALE),
            T.p("bGs", Gender.MALE), T.p("bGd", Gender.FEMALE),
            T.p("bGs2", Gender.MALE), T.p("bGd2", Gender.FEMALE),
            T.p("sGs", Gender.MALE), T.p("sGd", Gender.FEMALE),
            T.p("sGs2", Gender.MALE), T.p("sGd2", Gender.FEMALE)
        ),
        listOf(
            T.rel("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("sis", "me", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            T.rel("bro", "bSon", RelationshipType.PARENT),
            T.rel("bro", "bDau", RelationshipType.PARENT),
            T.rel("sis", "sSon", RelationshipType.PARENT),
            T.rel("sis", "sDau", RelationshipType.PARENT),
            T.rel("bSon", "bSonW", RelationshipType.SPOUSE),
            T.rel("bSon", "bSonH", RelationshipType.SPOUSE),
            T.rel("bDau", "bDauH", RelationshipType.SPOUSE),
            T.rel("sSon", "sSonW", RelationshipType.SPOUSE),
            T.rel("sDau", "sDauH", RelationshipType.SPOUSE),
            T.rel("bSon", "bGs", RelationshipType.PARENT),
            T.rel("bSon", "bGd", RelationshipType.PARENT),
            T.rel("bDau", "bGs2", RelationshipType.PARENT),
            T.rel("bDau", "bGd2", RelationshipType.PARENT),
            T.rel("sSon", "sGs", RelationshipType.PARENT),
            T.rel("sSon", "sGd", RelationshipType.PARENT),
            T.rel("sDau", "sGs2", RelationshipType.PARENT),
            T.rel("sDau", "sGd2", RelationshipType.PARENT)
        )
    )

    @Test
    fun `I-兄弟姐妹的子女的配偶`() {
        val g = sibDescGraph()
        check(g, "bSonW", "哥哥的儿子的妻子", "侄媳")
        check(g, "bDauH", "哥哥的女儿的丈夫", "侄女婿")
        check(g, "sSonW", "妹妹的儿子的妻子", "外甥媳")
        check(g, "sDauH", "妹妹的女儿的丈夫", "外甥女婿")
        check(g, "bSonH", "哥哥的儿子的丈夫", null)   // 同性别组合 → 空 EXACT
    }

    @Test
    fun `J-兄弟姐妹的孙辈`() {
        val g = sibDescGraph()
        check(g, "bGs", "哥哥的儿子的儿子", "侄孙")
        check(g, "bGd", "哥哥的儿子的女儿", "侄孙女")
        check(g, "bGs2", "哥哥的女儿的儿子", "侄外孙")
        check(g, "bGd2", "哥哥的女儿的女儿", "侄外孙女")
        check(g, "sGs", "妹妹的儿子的儿子", "外甥孙")
        check(g, "sGd", "妹妹的儿子的女儿", "外甥孙女")
        check(g, "sGs2", "妹妹的女儿的儿子", "外甥外孙")
        check(g, "sGd2", "妹妹的女儿的女儿", "外甥外孙女")
    }

    // ---------- K / L / M. 子女一侧 ----------

    private fun childSideGraph(): FamilyGraph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("son", Gender.MALE), T.p("dau", Gender.FEMALE),
            T.p("sonW", Gender.FEMALE), T.p("dauH", Gender.MALE),
            T.p("qjg", Gender.MALE), T.p("qjm", Gender.FEMALE),
            T.p("qjg2", Gender.MALE), T.p("qjm2", Gender.FEMALE),
            T.p("gs", Gender.MALE), T.p("gd", Gender.FEMALE),
            T.p("wgs", Gender.MALE), T.p("wgd", Gender.FEMALE),
            T.p("gsw", Gender.FEMALE), T.p("gsh", Gender.MALE),
            T.p("gdh", Gender.MALE), T.p("wgsw", Gender.FEMALE),
            T.p("wgdh", Gender.MALE),
            T.p("ggs", Gender.MALE), T.p("ggd", Gender.FEMALE),
            T.p("ggs2", Gender.MALE), T.p("ggd2", Gender.FEMALE),
            T.p("wgs2", Gender.MALE), T.p("wgd2", Gender.FEMALE),
            T.p("wgs3", Gender.MALE), T.p("wgd3", Gender.FEMALE)
        ),
        listOf(
            T.rel("me", "son", RelationshipType.PARENT),
            T.rel("me", "dau", RelationshipType.PARENT),
            T.rel("son", "sonW", RelationshipType.SPOUSE),
            T.rel("dau", "dauH", RelationshipType.SPOUSE),
            T.rel("qjg", "sonW", RelationshipType.PARENT),
            T.rel("qjm", "sonW", RelationshipType.PARENT),
            T.rel("qjg2", "dauH", RelationshipType.PARENT),
            T.rel("qjm2", "dauH", RelationshipType.PARENT),
            T.rel("son", "gs", RelationshipType.PARENT),
            T.rel("son", "gd", RelationshipType.PARENT),
            T.rel("dau", "wgs", RelationshipType.PARENT),
            T.rel("dau", "wgd", RelationshipType.PARENT),
            T.rel("gs", "gsw", RelationshipType.SPOUSE),
            T.rel("gs", "gsh", RelationshipType.SPOUSE),
            T.rel("gd", "gdh", RelationshipType.SPOUSE),
            T.rel("wgs", "wgsw", RelationshipType.SPOUSE),
            T.rel("wgd", "wgdh", RelationshipType.SPOUSE),
            T.rel("gs", "ggs", RelationshipType.PARENT),
            T.rel("gs", "ggd", RelationshipType.PARENT),
            T.rel("gd", "ggs2", RelationshipType.PARENT),
            T.rel("gd", "ggd2", RelationshipType.PARENT),
            T.rel("wgs", "wgs2", RelationshipType.PARENT),
            T.rel("wgs", "wgd2", RelationshipType.PARENT),
            T.rel("wgd", "wgs3", RelationshipType.PARENT),
            T.rel("wgd", "wgd3", RelationshipType.PARENT)
        )
    )

    @Test
    fun `K-亲家`() {
        val g = childSideGraph()
        check(g, "qjg", "儿子的妻子的父亲", "亲家公")
        check(g, "qjm", "儿子的妻子的母亲", "亲家母")
        check(g, "qjg2", "女儿的丈夫的父亲", "亲家公")
        check(g, "qjm2", "女儿的丈夫的母亲", "亲家母")
    }

    @Test
    fun `L-孙辈的配偶`() {
        val g = childSideGraph()
        check(g, "gsw", "儿子的儿子的妻子", "孙媳")
        check(g, "gsh", "儿子的儿子的丈夫", null)     // 同性别组合 → 空 EXACT
        check(g, "gdh", "儿子的女儿的丈夫", "孙女婿")
        check(g, "wgsw", "女儿的儿子的妻子", "外孙媳")
        check(g, "wgdh", "女儿的女儿的丈夫", "外孙女婿")
    }

    @Test
    fun `M-曾孙辈`() {
        val g = childSideGraph()
        check(g, "ggs", "儿子的儿子的儿子", "曾孙")
        check(g, "ggd", "儿子的儿子的女儿", "曾孙女")
        check(g, "ggs2", "儿子的女儿的儿子", "曾外孙")
        check(g, "ggd2", "儿子的女儿的女儿", "曾外孙女")
        check(g, "wgs2", "女儿的儿子的儿子", "外曾孙")
        check(g, "wgd2", "女儿的儿子的女儿", "外曾孙女")
        check(g, "wgs3", "女儿的女儿的儿子", "外曾外孙")
        check(g, "wgd3", "女儿的女儿的女儿", "外曾外孙女")
    }
}
