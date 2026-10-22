package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Test

/** 随配偶称（KINSHIP_ENGINE 38.3 / 38.4）与 38.5 仍不推导的路径。 */
class V03SpouseSideTest {

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

    /** 男方视角图谱：我(男)—妻子—妻方亲属，覆盖 38.3 示例表各行。 */
    private fun wifeSideGraph(): FamilyGraph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("wife", Gender.FEMALE),
            T.p("fil", Gender.MALE), T.p("mil", Gender.FEMALE),
            T.p("filBO", Gender.MALE), T.p("filBY", Gender.MALE),
            T.p("filS", Gender.FEMALE),
            T.p("milB", Gender.MALE), T.p("milS", Gender.FEMALE),
            T.p("gff", Gender.MALE), T.p("gfm", Gender.FEMALE),
            T.p("mgf", Gender.MALE), T.p("mgm", Gender.FEMALE),
            T.p("shen", Gender.FEMALE),          // 岳父的弟弟的妻子 → 婶婶
            T.p("tangGe", Gender.MALE),          // 岳父的哥哥的儿子（比妻子大）→ 堂哥
            T.p("filDadBro", Gender.MALE), T.p("tangShu", Gender.MALE), // 岳父的堂弟
            T.p("wifeBro", Gender.MALE), T.p("zhizi", Gender.MALE),     // 妻哥之子 → 侄子
            T.p("wifeSis", Gender.FEMALE), T.p("waisn", Gender.FEMALE), // 妻姐之女 → 外甥女
            T.p("saoZi", Gender.FEMALE)          // 妻哥之妻 → 嫂子（38.4）
        ),
        listOf(
            T.rel("wife", "me", RelationshipType.SPOUSE),
            T.rel("fil", "wife", RelationshipType.PARENT),
            T.rel("mil", "wife", RelationshipType.PARENT),
            T.rel("filBO", "fil", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("filBY", "fil", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            T.rel("filS", "fil", RelationshipType.SIBLING),
            T.rel("milB", "mil", RelationshipType.SIBLING),
            T.rel("milS", "mil", RelationshipType.SIBLING),
            T.rel("gff", "fil", RelationshipType.PARENT),
            T.rel("gfm", "fil", RelationshipType.PARENT),
            T.rel("mgf", "mil", RelationshipType.PARENT),
            T.rel("mgm", "mil", RelationshipType.PARENT),
            T.rel("filBY", "shen", RelationshipType.SPOUSE),
            T.rel("filBO", "tangGe", RelationshipType.PARENT),
            T.rel("filDadBro", "gff", RelationshipType.SIBLING),
            T.rel("filDadBro", "tangShu", RelationshipType.PARENT),
            T.rel("wifeBro", "wife", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("wifeBro", "zhizi", RelationshipType.PARENT),
            T.rel("wifeSis", "wife", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            T.rel("wifeSis", "waisn", RelationshipType.PARENT),
            T.rel("wifeBro", "saoZi", RelationshipType.SPOUSE)
        ),
        mapOf(
            "wife" to T.bd(1995, 3, 15),
            "fil" to T.bd(1968, 1, 1),
            "tangGe" to T.bd(1990, 1, 1),   // 比妻子大 → 堂哥
            "tangShu" to T.bd(1978, 1, 1)   // 比岳父小 → 堂叔
        )
    )

    @Test
    fun `配偶方_岳父的弟弟解析为叔叔`() {
        // 我=杨明(男)，配偶=李雪(女)，岳父是李雪的父亲，叔是岳父的弟弟（叔为关系来源）
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("wife", Gender.FEMALE),
                T.p("fil", Gender.MALE), T.p("shu", Gender.MALE)
            ),
            listOf(
                T.rel("wife", "me", RelationshipType.SPOUSE),
                T.rel("fil", "wife", RelationshipType.PARENT),
                T.rel("shu", "fil", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER)
            )
        )
        val r = engine.resolve(g, "me", "shu")
        assertEquals(KinshipKind.DERIVED, r.kind)
        assertEquals(Confidence.EXACT, r.confidence)
        assertEquals("妻子的父亲的弟弟", r.structuralDescription)
        assertEquals("叔叔", r.standardTitle)
    }

    @Test
    fun `38-3-随配偶称示例表`() {
        val g = wifeSideGraph()
        check(g, "filBO", "妻子的父亲的哥哥", "伯父")
        check(g, "filBY", "妻子的父亲的弟弟", "叔叔")
        check(g, "filS", "妻子的父亲的姐妹", "姑姑")
        check(g, "milB", "妻子的母亲的兄弟", "舅舅")
        check(g, "milS", "妻子的母亲的姐妹", "姨妈")
        check(g, "gff", "妻子的父亲的父亲", "爷爷")
        check(g, "gfm", "妻子的父亲的母亲", "奶奶")
        check(g, "mgf", "妻子的母亲的父亲", "姥爷")
        check(g, "mgm", "妻子的母亲的母亲", "姥姥")
        check(g, "shen", "妻子的父亲的弟弟的妻子", "婶婶")
        check(g, "tangGe", "妻子的父亲的哥哥的儿子", "堂哥")
        check(g, "zhizi", "妻子的哥哥的儿子", "侄子")
        check(g, "waisn", "妻子的姐姐的女儿", "外甥女")
    }

    @Test
    fun `38-3-五步_岳父的堂弟`() {
        // 妻子 →父 岳父 →父 岳父之父 →兄弟 其弟 →子 堂弟（第 5 步）
        val g = wifeSideGraph()
        val r = engine.resolve(g, "me", "tangShu")
        assertEquals(KinshipKind.DERIVED, r.kind)
        assertEquals("妻子的父亲的堂弟", r.structuralDescription)
        assertEquals("堂叔", r.standardTitle)
        assertEquals(5, r.relationshipPath.size)
    }

    @Test
    fun `38-4-妻子的哥哥的妻子-嫂子`() {
        val g = wifeSideGraph()
        check(g, "saoZi", "妻子的哥哥的妻子", "嫂子")
    }

    @Test
    fun `38-4-丈夫的姐姐的丈夫-姐夫`() {
        // 女方视角：我(女)—丈夫—丈夫的姐姐—其丈夫
        val g = T.graph(
            listOf(
                T.p("me", Gender.FEMALE, self = true),
                T.p("husb", Gender.MALE),
                T.p("hSis", Gender.FEMALE), T.p("hSisH", Gender.MALE)
            ),
            listOf(
                T.rel("husb", "me", RelationshipType.SPOUSE),
                T.rel("hSis", "husb", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
                T.rel("hSis", "hSisH", RelationshipType.SPOUSE)
            )
        )
        check(g, "hSisH", "丈夫的姐姐的丈夫", "姐夫")
    }

    @Test
    fun `38-4-长幼未知-PARTIAL`() {
        // 妻子的兄弟的妻子：兄弟长幼未知 → PARTIAL + SIBLING_AGE_ORDER
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("wife", Gender.FEMALE),
                T.p("wBro", Gender.MALE), T.p("wBroW", Gender.FEMALE)
            ),
            listOf(
                T.rel("wife", "me", RelationshipType.SPOUSE),
                T.rel("wBro", "wife", RelationshipType.SIBLING),
                T.rel("wBro", "wBroW", RelationshipType.SPOUSE)
            )
        )
        check(g, "wBroW", "妻子的兄弟的妻子", null,
            Confidence.PARTIAL, setOf(MissingInformation.SIBLING_AGE_ORDER))
    }

    @Test
    fun `38-5-父亲的妻子不推导继母`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE), T.p("stepW", Gender.FEMALE)
            ),
            listOf(
                T.rel("dad", "me", RelationshipType.PARENT),
                T.rel("dad", "stepW", RelationshipType.SPOUSE)
            )
        )
        val r = engine.resolve(g, "me", "stepW")
        assertEquals(KinshipKind.STRUCTURAL, r.kind)
        assertEquals("父亲的妻子", r.structuralDescription)
    }

    @Test
    fun `38-5-妻子的子女不推导继子女`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("wife", Gender.FEMALE), T.p("wSon", Gender.MALE), T.p("wDau", Gender.FEMALE)
            ),
            listOf(
                T.rel("wife", "me", RelationshipType.SPOUSE),
                T.rel("wife", "wSon", RelationshipType.PARENT),
                T.rel("wife", "wDau", RelationshipType.PARENT)
            )
        )
        val r1 = engine.resolve(g, "me", "wSon")
        assertEquals(KinshipKind.STRUCTURAL, r1.kind)
        assertEquals("妻子的儿子", r1.structuralDescription)
        val r2 = engine.resolve(g, "me", "wDau")
        assertEquals(KinshipKind.STRUCTURAL, r2.kind)
        assertEquals("妻子的女儿", r2.structuralDescription)
    }
}
