package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 申报堂表亲（COUSIN 记录）回归（真机反馈：设了“我是他的哥哥（堂）”，
 * 对方却推不出“堂弟”——长幼在落库时被丢弃）。
 * 规则（KINSHIP_ENGINE 37.1 修订）：长幼先读两人间申报 COUSIN 记录，
 * 其次双方完整生日；无路径的申报堂表亲给通用“堂表兄/弟/姐/妹”并接入 0 代。
 */
class DeclaredCousinTest {

    private val engine = KinshipEngine()

    /** 我(男) + 爸 + 伯(爸的哥) + 堂弟(伯的儿子)：真实链条 [P,S,C] 齐备。 */
    private fun chainGraph(
        extraRels: List<Relationship> = emptyList(),
        birthdays: Map<String, PartialDate> = emptyMap()
    ) = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("dad", Gender.MALE),
            T.p("uncle", Gender.MALE),
            T.p("cousin", Gender.MALE)
        ),
        listOf(
            T.rel("dad", "me", RelationshipType.PARENT),
            T.rel("dad", "uncle", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
            T.rel("uncle", "cousin", RelationshipType.PARENT)
        ) + extraRels,
        birthdays
    )

    @Test
    fun `申报我是哥哥_对方推得堂弟_无需生日`() {
        // 用户在对方资料页选“我是他的哥哥（堂）”→ COUSIN(我→他, OLDER)
        val g = chainGraph(
            listOf(T.rel("me", "cousin", RelationshipType.COUSIN, RelativeAgeOrder.OLDER))
        )
        val r = engine.resolve(g, "me", "cousin")
        assertEquals(KinshipKind.DERIVED, r.kind)
        assertEquals("堂弟", r.standardTitle)
    }

    @Test
    fun `申报方向取反_对方是我哥哥_我推得堂哥`() {
        val g = chainGraph(
            listOf(T.rel("cousin", "me", RelationshipType.COUSIN, RelativeAgeOrder.OLDER))
        )
        val r = engine.resolve(g, "me", "cousin")
        assertEquals("堂哥", r.standardTitle)
    }

    @Test
    fun `申报长幼优先于生日_生日说对方年长但申报我年长`() {
        val g = chainGraph(
            listOf(T.rel("me", "cousin", RelationshipType.COUSIN, RelativeAgeOrder.OLDER)),
            birthdays = mapOf(
                "me" to T.bd(1995, 3, 1),
                "cousin" to T.bd(1990, 5, 1)   // 生日上对方年长
            )
        )
        // 申报记录是用户明确陈述的事实，压过（可能录错的）生日推断
        assertEquals("堂弟", engine.resolve(g, "me", "cousin").standardTitle)
    }

    @Test
    fun `无申报记录退生日_行为不变`() {
        val g = chainGraph(
            birthdays = mapOf(
                "me" to T.bd(1995, 3, 1),
                "cousin" to T.bd(1990, 5, 1)
            )
        )
        assertEquals("堂哥", engine.resolve(g, "me", "cousin").standardTitle)
    }

    @Test
    fun `无申报无生日_通用堂兄弟_PARTIAL`() {
        val r = engine.resolve(chainGraph(), "me", "cousin")
        assertEquals("堂兄弟", r.standardTitle)
        assertEquals(Confidence.PARTIAL, r.confidence)
        assertEquals(setOf(MissingInformation.BIRTH_DATES), r.missingInformation)
    }

    @Test
    fun `申报记录无长幼_退生日_仍无则通用称谓`() {
        // “兄弟（长幼未知）”角色 → COUSIN 记录 order=null
        val g = chainGraph(
            listOf(T.rel("me", "cousin", RelationshipType.COUSIN, null))
        )
        val r = engine.resolve(g, "me", "cousin")
        assertEquals("堂兄弟", r.standardTitle)
        assertEquals(Confidence.PARTIAL, r.confidence)
    }

    @Test
    fun `无路径申报堂表亲_给通用堂表称谓`() {
        // 只录了“两人是堂表亲”，中间链条未建
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("cousin", Gender.MALE)
            ),
            listOf(T.rel("me", "cousin", RelationshipType.COUSIN, RelativeAgeOrder.OLDER))
        )
        val r = engine.resolve(g, "me", "cousin")
        assertEquals(KinshipKind.DERIVED, r.kind)
        assertEquals("堂表弟", r.standardTitle)
        assertTrue(r.structuralDescription.startsWith("堂表亲"))
    }

    @Test
    fun `无路径申报堂表亲_长幼未知给通用称谓_PARTIAL`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("cousin", Gender.MALE)
            ),
            listOf(T.rel("me", "cousin", RelationshipType.COUSIN, null))
        )
        val r = engine.resolve(g, "me", "cousin")
        assertEquals("堂表兄弟", r.standardTitle)
        assertEquals(Confidence.PARTIAL, r.confidence)
        assertEquals(setOf(MissingInformation.BIRTH_DATES), r.missingInformation)
    }

    @Test
    fun `申报堂表亲入0代_不再游离`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("cousin", Gender.MALE)
            ),
            listOf(T.rel("me", "cousin", RelationshipType.COUSIN, RelativeAgeOrder.OLDER))
        )
        assertEquals(0, engine.generations(g, "me")["cousin"])
    }

    @Test
    fun `申报堂表亲记录不产生路径_不掩盖真实称谓`() {
        // 链条齐备时 COUSIN 记录不抢路径：仍是规范堂表称谓而非通用兜底
        val g = chainGraph(
            listOf(T.rel("me", "cousin", RelationshipType.COUSIN, RelativeAgeOrder.OLDER))
        )
        val r = engine.resolve(g, "me", "cousin")
        assertEquals(listOf(
            RelationshipType.PARENT, RelationshipType.SIBLING, RelationshipType.CHILD
        ), r.relationshipPath.map { it.type })
    }

    @Test
    fun `directRelationLabel_申报堂表亲称谓`() {
        val rel = T.rel("a", "b", RelationshipType.COUSIN, RelativeAgeOrder.OLDER)
        val g = T.graph(
            listOf(T.p("a", Gender.MALE), T.p("b", Gender.MALE)),
            listOf(rel)
        )
        // a 视角：b 年幼 → 堂表弟；b 视角：a 年长 → 堂表哥
        assertEquals("堂表弟", engine.directRelationLabel(g, "a", rel))
        assertEquals("堂表哥", engine.directRelationLabel(g, "b", rel))
        val none = T.rel("a", "b", RelationshipType.COUSIN, null)
        val g2 = T.graph(
            listOf(T.p("a", Gender.FEMALE), T.p("b", Gender.FEMALE)),
            listOf(none)
        )
        assertEquals("堂表姐妹", engine.directRelationLabel(g2, "a", none))
    }

    @Test
    fun `二代表亲_父母与堂表亲间申报记录生效`() {
        // 爷爷 → 爸 → 我；爷爷的哥哥 → 堂叔公（爸的堂兄弟）→ 再下一代不涉及
        // 爸与堂叔公之间申报“爸年幼” → 爸的堂哥 → 对我 = 堂伯
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("dad", Gender.MALE),
                T.p("gfd", Gender.MALE),
                T.p("guncle", Gender.MALE),
                T.p("cousin2", Gender.MALE)
            ),
            listOf(
                T.rel("gfd", "dad", RelationshipType.PARENT),
                T.rel("dad", "me", RelationshipType.PARENT),
                T.rel("gfd", "guncle", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
                T.rel("guncle", "cousin2", RelationshipType.PARENT),
                // 堂叔公是爸的堂哥：cousin2 比 dad 年长
                T.rel("cousin2", "dad", RelationshipType.COUSIN, RelativeAgeOrder.OLDER)
            )
        )
        val r = engine.resolve(g, "me", "cousin2")
        assertEquals("堂伯", r.standardTitle)
    }
}
