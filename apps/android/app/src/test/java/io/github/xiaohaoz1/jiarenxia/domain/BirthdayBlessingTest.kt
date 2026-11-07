package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.kinship.PathStep
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 生日一键发祝福（FAMILY_AFFAIRS 22）：长辈判定与默认文案。 */
class BirthdayBlessingTest {

    private fun step(type: RelationshipType) = PathStep(type, "x")

    private fun result(vararg path: RelationshipType) = KinshipResult(
        observerId = "me",
        targetId = "x",
        relationshipPath = path.map { step(it) },
        structuralDescription = "",
        standardTitle = null,
        confidence = Confidence.EXACT,
        missingInformation = emptySet(),
        kind = KinshipKind.DERIVED
    )

    // ---------- isElder：称谓路径净向上（PARENT > CHILD） ----------

    @Test
    fun isElder_长辈路径为真() {
        // 父母 / 祖父母 / 配偶父母 / 姑姑（父母的兄弟姐妹）都是长辈
        assertTrue(BirthdayBlessing.isElder(result(RelationshipType.PARENT)))
        assertTrue(BirthdayBlessing.isElder(
            result(RelationshipType.PARENT, RelationshipType.PARENT)))
        assertTrue(BirthdayBlessing.isElder(
            result(RelationshipType.SPOUSE, RelationshipType.PARENT)))
        assertTrue(BirthdayBlessing.isElder(
            result(RelationshipType.PARENT, RelationshipType.SIBLING)))
    }

    @Test
    fun isElder_同辈与晚辈为假() {
        // 配偶 / 兄弟姐妹（含经父母绕行）/ 堂表亲 / 子女 / 孙辈
        assertFalse(BirthdayBlessing.isElder(result(RelationshipType.SPOUSE)))
        assertFalse(BirthdayBlessing.isElder(result(RelationshipType.SIBLING)))
        assertFalse(BirthdayBlessing.isElder(
            result(RelationshipType.PARENT, RelationshipType.CHILD)))
        assertFalse(BirthdayBlessing.isElder(
            result(RelationshipType.PARENT, RelationshipType.SIBLING,
                RelationshipType.CHILD)))
        assertFalse(BirthdayBlessing.isElder(result(RelationshipType.CHILD)))
        assertFalse(BirthdayBlessing.isElder(
            result(RelationshipType.CHILD, RelationshipType.CHILD)))
        // 空路径（本人/无关系）不算长辈
        assertFalse(BirthdayBlessing.isElder(result()))
    }

    // ---------- defaultText ----------

    @Test
    fun defaultText_长辈用您() {
        assertEquals(
            "妈妈，生日快乐！祝您身体健康，福寿安康！",
            BirthdayBlessing.defaultText("妈妈", elder = true))
    }

    @Test
    fun defaultText_非长辈用你() {
        assertEquals(
            "儿子，生日快乐！祝你天天开心，万事顺意！",
            BirthdayBlessing.defaultText("儿子", elder = false))
        // 无称谓时 label 退化为姓名
        assertEquals(
            "王秀兰，生日快乐！祝你天天开心，万事顺意！",
            BirthdayBlessing.defaultText("王秀兰", elder = false))
    }

    // ---------- elderIds ----------

    private fun person(id: String, name: String, self: Boolean = false) =
        Person(id = id, name = name, gender = Gender.MALE, isSelf = self)

    private fun overviewOf(vararg entries: Pair<Person, KinshipResult>): FamilyOverview {
        val byId = entries.associate { (p, r) ->
            p.id to PersonOverview(person = p, result = r)
        }
        val self = entries.first { it.first.isSelf }
        return FamilyOverview(
            self = byId.getValue(self.first.id),
            graph = FamilyGraph(emptyList(), emptyList()),
            byId = byId,
            core = emptyList(),
            others = emptyList()
        )
    }

    @Test
    fun elderIds_只收长辈() {
        val ov = overviewOf(
            person("me", "我", self = true) to
                result().copy(kind = KinshipKind.SELF),
            person("mom", "妈妈") to
                result(RelationshipType.PARENT),
            person("son", "儿子") to
                result(RelationshipType.CHILD),
            person("wife", "妻子") to
                result(RelationshipType.SPOUSE)
        )
        assertEquals(setOf("mom"), BirthdayBlessing.elderIds(ov))
        assertEquals(emptySet<String>(), BirthdayBlessing.elderIds(null))
    }
}
