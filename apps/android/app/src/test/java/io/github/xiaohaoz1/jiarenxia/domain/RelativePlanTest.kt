package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 称谓录入的领域规划（DEVELOPMENT_PLAN 2.14）：
 * 称呼不推断血缘——父母候选、中间人、落库规格各归各位。
 */
class RelativePlanTest {

    private fun p(id: String, gender: Gender, self: Boolean = false) =
        Person(id = id, name = id, gender = gender, isSelf = self)

    private fun rel(src: String, tgt: String, type: RelationshipType,
                    order: RelativeAgeOrder? = null) =
        Relationship("r-$src-$tgt-$type", src, tgt, type, order)

    /** 我 ← 爸/妈；爸 ← 爷爷；爷爷有兄弟伯公；爸有姐姐（姑姑）。 */
    private fun graph(): FamilyGraph {
        val persons = listOf(
            p("me", Gender.MALE, self = true), p("dad", Gender.MALE),
            p("mom", Gender.FEMALE), p("gfd", Gender.MALE),
            p("aunt", Gender.FEMALE), p("guncle", Gender.MALE)
        )
        val rels = listOf(
            rel("dad", "me", RelationshipType.PARENT),
            rel("mom", "me", RelationshipType.PARENT),
            rel("gfd", "dad", RelationshipType.PARENT),
            // 姑姑：与爸显式兄妹
            rel("aunt", "dad", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
            // 伯公：爷爷的哥哥（显式 SIBLING）
            rel("guncle", "gfd", RelationshipType.SIBLING, RelativeAgeOrder.OLDER)
        )
        return FamilyGraph(persons = persons, relationships = rels)
    }

    // ---------- 称谓标签 ----------

    @Test
    fun 关联人是父亲时兄妹显示伯叔姑() {
        assertEquals("伯伯", RelativePlan.roleLabel(RelationRole.ELDER_BROTHER, Gender.MALE))
        assertEquals("叔叔", RelativePlan.roleLabel(RelationRole.YOUNGER_BROTHER, Gender.MALE))
        assertEquals("姑姑（姐姐）", RelativePlan.roleLabel(RelationRole.ELDER_SISTER, Gender.MALE))
        assertEquals("姑姑（妹妹）", RelativePlan.roleLabel(RelationRole.YOUNGER_SISTER, Gender.MALE))
        assertEquals("姑姑（长幼未知）", RelativePlan.roleLabel(RelationRole.SISTER, Gender.MALE))
    }

    @Test
    fun 关联人是母亲时兄妹显示舅姨() {
        assertEquals("舅舅（弟弟）", RelativePlan.roleLabel(RelationRole.YOUNGER_BROTHER, Gender.FEMALE))
        assertEquals("姨妈（姐姐）", RelativePlan.roleLabel(RelationRole.ELDER_SISTER, Gender.FEMALE))
        assertEquals("姨妈（长幼未知）", RelativePlan.roleLabel(RelationRole.SISTER, Gender.FEMALE))
    }

    @Test
    fun 非父母关联人回落到通用标签() {
        assertEquals("姐姐", RelativePlan.roleLabel(RelationRole.ELDER_SISTER, null))
        // 非兄妹角色不受影响
        assertEquals("父亲", RelativePlan.roleLabel(RelationRole.FATHER, null))
    }

    /** 关联人是“我”的父/母时，全部角色都按“TA 是我的”翻译，句子主语与选项同一视角。 */
    @Test
    fun 关联人是父亲时全部角色显示我的称谓() {
        val male = Gender.MALE
        assertEquals("爷爷", RelativePlan.roleLabel(RelationRole.FATHER, male))
        assertEquals("奶奶", RelativePlan.roleLabel(RelationRole.MOTHER, male))
        assertEquals("兄弟（长幼未知）", RelativePlan.roleLabel(RelationRole.SON, male))
        assertEquals("姐妹（长幼未知）", RelativePlan.roleLabel(RelationRole.DAUGHTER, male))
        assertEquals("继父", RelativePlan.roleLabel(RelationRole.HUSBAND, male))
        assertEquals("继母", RelativePlan.roleLabel(RelationRole.WIFE, male))
    }

    @Test
    fun 关联人是母亲时全部角色显示我的称谓() {
        val female = Gender.FEMALE
        assertEquals("姥爷", RelativePlan.roleLabel(RelationRole.FATHER, female))
        assertEquals("姥姥", RelativePlan.roleLabel(RelationRole.MOTHER, female))
        assertEquals("兄弟（长幼未知）", RelativePlan.roleLabel(RelationRole.SON, female))
        assertEquals("姐妹（长幼未知）", RelativePlan.roleLabel(RelationRole.DAUGHTER, female))
        assertEquals("继父", RelativePlan.roleLabel(RelationRole.HUSBAND, female))
        assertEquals("继母", RelativePlan.roleLabel(RelationRole.WIFE, female))
    }

    @Test
    fun 父母关联人只记称呼默认称谓带祖辈继亲() {
        assertEquals("爷爷", RelativePlan.baseAppellation(RelationRole.FATHER, Gender.MALE))
        assertEquals("姥爷", RelativePlan.baseAppellation(RelationRole.FATHER, Gender.FEMALE))
        assertEquals("兄弟", RelativePlan.baseAppellation(RelationRole.SON, Gender.MALE))
        assertEquals("继母", RelativePlan.baseAppellation(RelationRole.WIFE, Gender.MALE))
    }

    @Test
    fun 只记称呼默认称谓不带长幼() {
        assertEquals("姑姑", RelativePlan.baseAppellation(RelationRole.ELDER_SISTER, Gender.MALE))
        assertEquals("姑姑", RelativePlan.baseAppellation(RelationRole.SISTER, Gender.MALE))
        assertEquals("舅舅", RelativePlan.baseAppellation(RelationRole.BROTHER, Gender.FEMALE))
        assertEquals("姨妈", RelativePlan.baseAppellation(RelationRole.YOUNGER_SISTER, Gender.FEMALE))
        assertEquals("姐姐", RelativePlan.baseAppellation(RelationRole.ELDER_SISTER, null))
    }

    // ---------- 共同父母候选 ----------

    @Test
    fun 父母候选按性别区分() {
        val g = graph()
        val fathers = RelativePlan.parentCandidates(g, "dad", Gender.MALE)
        val mothers = RelativePlan.parentCandidates(g, "dad", Gender.FEMALE)
        assertEquals(listOf("gfd"), fathers.map { it.id })
        assertTrue(mothers.isEmpty())
        // 我有一位父亲一位母亲
        assertEquals(listOf("dad"), RelativePlan.parentCandidates(g, "me", Gender.MALE).map { it.id })
        assertEquals(listOf("mom"), RelativePlan.parentCandidates(g, "me", Gender.FEMALE).map { it.id })
    }

    // ---------- 堂/表中间人候选 ----------

    @Test
    fun 中间人是关联人父母的兄弟姐妹() {
        val g = graph()
        // 爸爸（dad）的父母是爷爷（gfd）；爷爷的兄弟是伯公（guncle）
        val middles = RelativePlan.middleCandidates(g, "dad")
        assertEquals(listOf("guncle"), middles.map { it.person.id })
        assertTrue(middles.first().viaLabel.contains("gfd"))
        // 我的中间人：爸爸的兄妹姑姑（显式）——注意隐式兄妹也会算
        val mine = RelativePlan.middleCandidates(g, "me")
        assertEquals(listOf("aunt"), mine.map { it.person.id })
    }

    @Test
    fun 共享父母推导出的隐式兄妹也算中间人() {
        // 姑姑不录显式兄妹，只录“姑姑是爷爷的女儿”→ 与爸爸隐式兄妹
        val persons = listOf(
            p("me", Gender.MALE, self = true), p("dad", Gender.MALE),
            p("gfd", Gender.MALE), p("aunt", Gender.FEMALE)
        )
        val g = FamilyGraph(
            persons = persons,
            relationships = listOf(
                rel("dad", "me", RelationshipType.PARENT),
                rel("gfd", "dad", RelationshipType.PARENT),
                rel("gfd", "aunt", RelationshipType.PARENT)
            )
        )
        val middles = RelativePlan.middleCandidates(g, "dad")
        // dad 的父母只有爷爷；爷爷没有兄妹 → 无中间人
        assertTrue(middles.isEmpty())
        // 对“我”而言中间人 = 爸爸的兄妹：姑姑经共享父亲（爷爷）隐式成为兄妹
        assertEquals(listOf("aunt"), RelativePlan.middleCandidates(g, "me").map { it.person.id })
    }

    // ---------- 落库规格 ----------

    @Test
    fun 亲兄妹规格_兄妹加确认的共同父母() {
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, "dad", RelationRole.ELDER_SISTER,
            SiblingLineage.REAL, parentIds = listOf("gfd")
        )
        assertEquals(2, specs.size)
        val sib = specs.first { it.type == RelationshipType.SIBLING }
        assertEquals(NEW_PERSON, sib.sourcePersonId)
        assertEquals("dad", sib.targetPersonId)
        assertEquals(RelativeAgeOrder.OLDER, sib.relativeAgeOrder)
        val parent = specs.first { it.type == RelationshipType.PARENT }
        assertEquals("gfd", parent.sourcePersonId)
        assertEquals(NEW_PERSON, parent.targetPersonId)
    }

    @Test
    fun 长幼未知兄妹不带长幼() {
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, "me", RelationRole.SISTER, SiblingLineage.REAL
        )
        assertEquals(1, specs.size)
        assertEquals(RelationshipType.SIBLING, specs.first().type)
        assertEquals(null, specs.first().relativeAgeOrder)
    }

    @Test
    fun 堂表写中间人父母加申报堂表亲_不造直接兄妹() {
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, "dad", RelationRole.SISTER,
            SiblingLineage.COUSIN, middleId = "guncle"
        )
        assertEquals(2, specs.size)
        val parent = specs.first { it.type == RelationshipType.PARENT }
        assertEquals("guncle", parent.sourcePersonId)
        assertEquals(NEW_PERSON, parent.targetPersonId)
        // 与锚点之间申报一条堂表亲记录（SISTER 角色：长幼未知）
        val cousin = specs.first { it.type == RelationshipType.COUSIN }
        assertEquals(NEW_PERSON, cousin.sourcePersonId)
        assertEquals("dad", cousin.targetPersonId)
        assertEquals(null, cousin.relativeAgeOrder)
        assertTrue(specs.none { it.type == RelationshipType.SIBLING })
    }

    @Test
    fun 堂表申报记录带所选角色的长幼() {
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, "dad", RelationRole.ELDER_SISTER,
            SiblingLineage.COUSIN, middleId = "guncle"
        )
        val cousin = specs.first { it.type == RelationshipType.COUSIN }
        assertEquals(RelativeAgeOrder.OLDER, cousin.relativeAgeOrder)
    }

    @Test
    fun 只记称呼不写任何关系() {
        val specs = RelativePlan.buildSpecs(
            NEW_PERSON, "dad", RelationRole.SISTER, SiblingLineage.TITLE_ONLY
        )
        assertTrue(specs.isEmpty())
    }
}
