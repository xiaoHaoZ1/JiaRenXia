package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.kinship.PathStep
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import org.junit.Assert.assertEquals
import org.junit.Test

/** FamilyOrdering 的排序规则。 */
class FamilyOrderingTest {

    private fun step(type: RelationshipType, id: String, implicit: Boolean = false) =
        PathStep(type, id, implicit)

    private fun ov(
        id: String,
        name: String = id,
        gender: Gender = Gender.MALE,
        path: List<PathStep> = emptyList(),
        birthday: PartialDate? = null,
        kind: KinshipKind = KinshipKind.DERIVED,
        createdAt: Long = 0
    ) = PersonOverview(
        person = Person(id = id, name = name, gender = gender, createdAt = createdAt),
        result = KinshipResult(
            observerId = "me", targetId = id, relationshipPath = path,
            structuralDescription = "s", standardTitle = null,
            confidence = Confidence.EXACT, missingInformation = emptySet(), kind = kind
        ),
        birthdays = birthday?.let { listOf(io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue(it)) } ?: emptyList(),
        solarBirthday = birthday
    )

    @Test
    fun 组序() {
        val items = listOf(
            ov("mil", gender = Gender.FEMALE,
                path = listOf(step(RelationshipType.SPOUSE, "sp"), step(RelationshipType.PARENT, "mil"))),
            ov("son", path = listOf(step(RelationshipType.CHILD, "son"))),
            ov("sp", gender = Gender.FEMALE, path = listOf(step(RelationshipType.SPOUSE, "sp"))),
            ov("gp", path = listOf(step(RelationshipType.PARENT, "dad"), step(RelationshipType.PARENT, "gp"))),
            ov("dad", path = listOf(step(RelationshipType.PARENT, "dad")))
        )
        val byId = items.associateBy { it.person.id } +
            ("dad" to ov("dad", gender = Gender.MALE)) // 路径中间人也在 byId
        val sorted = FamilyOrdering.core(items, byId)
        assertEquals(listOf("dad", "gp", "sp", "son", "mil"), sorted.map { it.person.id })
    }

    @Test
    fun 父母组男性在前() {
        val dad = ov("dad", path = listOf(step(RelationshipType.PARENT, "dad")))
        val mom = ov("mom", gender = Gender.FEMALE, path = listOf(step(RelationshipType.PARENT, "mom")))
        val sorted = FamilyOrdering.core(listOf(mom, dad), mapOf("dad" to dad, "mom" to mom))
        assertEquals(listOf("dad", "mom"), sorted.map { it.person.id })
    }

    @Test
    fun 祖辈父系在前同侧男性在前() {
        val dad = ov("dad")
        val mom = ov("mom", gender = Gender.FEMALE)
        val gfP = ov("gfP", path = listOf(step(RelationshipType.PARENT, "dad"), step(RelationshipType.PARENT, "gfP")))
        val gmP = ov("gmP", gender = Gender.FEMALE, path = listOf(step(RelationshipType.PARENT, "dad"), step(RelationshipType.PARENT, "gmP")))
        val gfM = ov("gfM", path = listOf(step(RelationshipType.PARENT, "mom"), step(RelationshipType.PARENT, "gfM")))
        val gmM = ov("gmM", gender = Gender.FEMALE, path = listOf(step(RelationshipType.PARENT, "mom"), step(RelationshipType.PARENT, "gmM")))
        val byId = mapOf("dad" to dad, "mom" to mom, "gfP" to gfP, "gmP" to gmP, "gfM" to gfM, "gmM" to gmM)
        val sorted = FamilyOrdering.core(listOf(gmM, gfM, gmP, gfP), byId)
        assertEquals(listOf("gfP", "gmP", "gfM", "gmM"), sorted.map { it.person.id })
    }

    @Test
    fun 子女按生日与录入时间() {
        val older = ov("c1", path = listOf(step(RelationshipType.CHILD, "c1")),
            birthday = PartialDate(2010, 1, 1), createdAt = 2)
        val younger = ov("c2", path = listOf(step(RelationshipType.CHILD, "c2")),
            birthday = PartialDate(2015, 1, 1), createdAt = 1)
        val noYear = ov("c3", path = listOf(step(RelationshipType.CHILD, "c3")),
            birthday = PartialDate(null, 3, 3), createdAt = 0)
        val noBd = ov("c4", path = listOf(step(RelationshipType.CHILD, "c4")),
            birthday = null, createdAt = 0)
        val sorted = FamilyOrdering.core(listOf(noBd, noYear, younger, older), emptyMap())
        // 生日完整者在前且年长者在前；不完整/无生日按录入时间
        assertEquals(listOf("c1", "c2", "c3", "c4"), sorted.map { it.person.id })
    }

    @Test
    fun 姓名平手时用中文Collator() {
        val a = ov("a", name = "安安", path = listOf(step(RelationshipType.PARENT, "a")))
        val b = ov("b", name = "白白", path = listOf(step(RelationshipType.PARENT, "b")))
        // 同组（都是 PARENTS）同性 → 按拼音：安安 < 白白
        val sorted = FamilyOrdering.core(listOf(b, a), mapOf("a" to a, "b" to b))
        assertEquals(listOf("a", "b"), sorted.map { it.person.id })
    }

    @Test
    fun 核心家人分行_每组从新行开始() {
        // 与真实家庭同构：父母 + 四位祖辈 + 配偶 + 配偶父母
        val dad = ov("dad", path = listOf(step(RelationshipType.PARENT, "dad")))
        val mom = ov("mom", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.PARENT, "mom")))
        val gfd = ov("gfd",
            path = listOf(step(RelationshipType.PARENT, "dad"), step(RelationshipType.PARENT, "gfd")))
        val gmd = ov("gmd", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.PARENT, "dad"), step(RelationshipType.PARENT, "gmd")))
        val gfm = ov("gfm",
            path = listOf(step(RelationshipType.PARENT, "mom"), step(RelationshipType.PARENT, "gfm")))
        val gmm = ov("gmm", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.PARENT, "mom"), step(RelationshipType.PARENT, "gmm")))
        val wife = ov("wife", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.SPOUSE, "wife")))
        val fil = ov("fil",
            path = listOf(step(RelationshipType.SPOUSE, "wife"), step(RelationshipType.PARENT, "fil")))
        val mil = ov("mil", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.SPOUSE, "wife"), step(RelationshipType.PARENT, "mil")))
        val all = listOf(mil, fil, wife, gmm, gfm, gmd, gfd, mom, dad)
        val byId = all.associateBy { it.person.id }
        val rows = FamilyOrdering.coreRows(FamilyOrdering.core(all, byId))
        // 成对的同排，单出的配偶独占一行——不能出现“妻子|岳父”
        assertEquals(
            listOf(
                listOf("dad", "mom"),
                listOf("gfd", "gmd"),
                listOf("gfm", "gmm"),
                listOf("wife"),
                listOf("fil", "mil")
            ),
            rows.map { r -> r.map { it.person.id } }
        )
    }

    @Test
    fun 核心家人分行_奇数组不顶错下一组() {
        val mom = ov("mom", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.PARENT, "mom")))
        val wife = ov("wife", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.SPOUSE, "wife")))
        val dau = ov("dau", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.CHILD, "dau")))
        val fil = ov("fil",
            path = listOf(step(RelationshipType.SPOUSE, "wife"), step(RelationshipType.PARENT, "fil")))
        val mil = ov("mil", gender = Gender.FEMALE,
            path = listOf(step(RelationshipType.SPOUSE, "wife"), step(RelationshipType.PARENT, "mil")))
        val all = listOf(mil, dau, fil, mom, wife)
        val byId = all.associateBy { it.person.id }
        val rows = FamilyOrdering.coreRows(FamilyOrdering.core(all, byId))
        assertEquals(
            listOf(listOf("mom"), listOf("wife"), listOf("dau"), listOf("fil", "mil")),
            rows.map { r -> r.map { it.person.id } }
        )
    }

    @Test
    fun 其他家人_关联者在前按路径长度() {
        val near = ov("n1", path = listOf(step(RelationshipType.SIBLING, "n1")))
        val far = ov("n2", path = listOf(
            step(RelationshipType.PARENT, "x"), step(RelationshipType.SIBLING, "n2")))
        val unrel = ov("n3", kind = KinshipKind.UNRELATED)
        val sorted = FamilyOrdering.others(listOf(unrel, far, near))
        assertEquals(listOf("n1", "n2", "n3"), sorted.map { it.person.id })
    }

    @Test
    fun 其他家人_未关联按姓名() {
        val a = ov("u1", name = "安安", kind = KinshipKind.UNRELATED)
        val b = ov("u2", name = "白白", kind = KinshipKind.UNRELATED)
        assertEquals(listOf("u1", "u2"), FamilyOrdering.others(listOf(b, a)).map { it.person.id })
    }
}
