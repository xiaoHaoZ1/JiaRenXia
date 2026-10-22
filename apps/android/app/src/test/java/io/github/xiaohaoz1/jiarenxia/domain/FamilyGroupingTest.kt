package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.kinship.PathStep
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Test

/** FamilyGrouping 的分组规则与顺序。 */
class FamilyGroupingTest {

    private fun step(t: RelationshipType, id: String) = PathStep(t, id)

    private fun ov(
        id: String,
        gender: Gender = Gender.MALE,
        path: List<PathStep> = emptyList(),
        kind: KinshipKind = KinshipKind.DERIVED
    ) = PersonOverview(
        person = Person(id = id, name = id, gender = gender),
        result = KinshipResult(
            observerId = "me", targetId = id, relationshipPath = path,
            structuralDescription = "s", standardTitle = null,
            confidence = Confidence.EXACT, missingInformation = emptySet(), kind = kind
        ),
        birthdays = emptyList()
    )

    /** 一组典型亲属：兄弟、侄子、伯叔（父系）、姨（母系）、岳父、儿媳、未关联。 */
    private fun fixture(): Pair<List<PersonOverview>, Map<String, PersonOverview>> {
        val dad = ov("dad", path = listOf(step(RelationshipType.PARENT, "dad")))
        val mom = ov("mom", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.PARENT, "mom")))
        val bro = ov("bro", path = listOf(step(RelationshipType.SIBLING, "bro")))
        val nephew = ov("nephew", path = listOf(
            step(RelationshipType.SIBLING, "bro"), step(RelationshipType.CHILD, "nephew")))
        val uncle = ov("uncle", path = listOf(
            step(RelationshipType.PARENT, "dad"), step(RelationshipType.SIBLING, "uncle")))
        val aunt = ov("aunt", gender = Gender.FEMALE, path = listOf(
            step(RelationshipType.PARENT, "mom"), step(RelationshipType.SIBLING, "aunt")))
        val fil = ov("fil", path = listOf(
            step(RelationshipType.SPOUSE, "wife"), step(RelationshipType.PARENT, "fil")))
        val dil = ov("dil", gender = Gender.FEMALE, path = listOf(
            step(RelationshipType.CHILD, "son"), step(RelationshipType.SPOUSE, "dil")))
        val stranger = ov("stranger", kind = KinshipKind.UNRELATED)
        val others = listOf(nephew, bro, stranger, uncle, aunt, fil, dil)
        val byId = (others + dad + mom).associateBy { it.person.id }
        return others to byId
    }

    @Test
    fun 每组归类正确() {
        val (others, byId) = fixture()
        val g = others.associate { it.person.id to FamilyGrouping.groupOf(it, byId) }
        assertEquals(FamilyGrouping.OtherGroup.SIBLINGS, g["bro"])
        assertEquals(FamilyGrouping.OtherGroup.SIBLING_FAMILY, g["nephew"])
        assertEquals(FamilyGrouping.OtherGroup.PATERNAL, g["uncle"])
        assertEquals(FamilyGrouping.OtherGroup.MATERNAL, g["aunt"])
        assertEquals(FamilyGrouping.OtherGroup.SPOUSE_FAMILY, g["fil"])
        assertEquals(FamilyGrouping.OtherGroup.CHILD_FAMILY, g["dil"])
        assertEquals(FamilyGrouping.OtherGroup.UNRELATED, g["stranger"])
    }

    @Test
    fun 伯母归入父系且非核心() {
        // 杨建春（伯父）的妻子 → 路径 PARENT+SIBLING+SPOUSE，不在核心组
        val dad = ov("dad", path = listOf(step(RelationshipType.PARENT, "dad")))
        val bomu = ov("bomu", gender = Gender.FEMALE, path = listOf(
            step(RelationshipType.PARENT, "dad"),
            step(RelationshipType.SIBLING, "uncle"),
            step(RelationshipType.SPOUSE, "bomu")))
        val byId = listOf(dad, bomu).associateBy { it.person.id }
        assertEquals(null, bomu.result.coreGroup)
        assertEquals(FamilyGrouping.OtherGroup.PATERNAL,
            FamilyGrouping.groupOf(bomu, byId))
    }

    @Test
    fun 组序固定且组内保持传入顺序() {
        val (others, byId) = fixture()
        val groups = FamilyGrouping.group(others, byId)
        assertEquals(
            listOf(
                FamilyGrouping.OtherGroup.SIBLINGS,
                FamilyGrouping.OtherGroup.SIBLING_FAMILY,
                FamilyGrouping.OtherGroup.PATERNAL,
                FamilyGrouping.OtherGroup.MATERNAL,
                FamilyGrouping.OtherGroup.SPOUSE_FAMILY,
                FamilyGrouping.OtherGroup.CHILD_FAMILY,
                FamilyGrouping.OtherGroup.UNRELATED
            ),
            groups.map { it.first }
        )
        // 未关联在最后
        assertEquals(listOf("stranger"), groups.last().second.map { it.person.id })
        // 兄弟姐妹组内保持传入顺序（nephew 在 bro 之前传入，但分属不同组）
        assertEquals(listOf("bro"), groups[0].second.map { it.person.id })
    }

    @Test
    fun 同组多人保序() {
        val a = ov("ua", path = listOf(
            step(RelationshipType.PARENT, "dad"), step(RelationshipType.SIBLING, "ua")))
        val b = ov("ub", path = listOf(
            step(RelationshipType.PARENT, "dad"), step(RelationshipType.SIBLING, "ub")))
        val dad = ov("dad", path = listOf(step(RelationshipType.PARENT, "dad")))
        val others = listOf(b, a)   // 传入顺序 b 在前 → 组内也应 b 在前
        val byId = (others + dad).associateBy { it.person.id }
        val groups = FamilyGrouping.group(others, byId)
        assertEquals(1, groups.size)
        assertEquals(listOf("ub", "ua"), groups[0].second.map { it.person.id })
    }

    @Test
    fun 申报堂表亲归堂表兄弟姐妹组() {
        // 无真实路径的申报 COUSIN：单步路径 [COUSIN]
        val cousin = ov("cousin", path = listOf(
            step(RelationshipType.COUSIN, "cousin")))
        val byId = listOf(cousin).associateBy { it.person.id }
        assertEquals(FamilyGrouping.OtherGroup.COUSINS,
            FamilyGrouping.groupOf(cousin, byId))
        val groups = FamilyGrouping.group(listOf(cousin), byId)
        assertEquals(listOf(FamilyGrouping.OtherGroup.COUSINS),
            groups.map { it.first })
    }

    @Test
    fun 空组不出现在结果里() {
        val dad = ov("dad", path = listOf(step(RelationshipType.PARENT, "dad")))
        val bro = ov("bro", path = listOf(step(RelationshipType.SIBLING, "bro")))
        val byId = listOf(bro, dad).associateBy { it.person.id }
        val groups = FamilyGrouping.group(listOf(bro), byId)
        assertEquals(listOf(FamilyGrouping.OtherGroup.SIBLINGS), groups.map { it.first })
    }
}
