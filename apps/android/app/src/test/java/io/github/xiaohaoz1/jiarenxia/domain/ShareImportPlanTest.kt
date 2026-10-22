package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ShareImport.spouseMergePlan（“和我结为夫妻”可行性预判，FAMILY_AFFAIRS 16.3）：
 * 0.9.4 缺陷回归——合并计划须以本机“我”计算；
 * 点“和我结为夫妻”并确认预览即确认“对方就是我的配偶”（approved 恒含
 * peerSelfId）；选中对方录入的“我”作为种子映射认回（名字不同也可）。
 */
class ShareImportPlanTest {

    private fun p(id: String, name: String, g: Gender, self: Boolean = false) =
        PersonEntity(
            id = id, name = name, gender = g, avatarPath = null,
            note = null, livingStatus = LivingStatus.ALIVE,
            isSelf = self, createdAt = 0, updatedAt = 0)

    private fun r(
        src: String, tgt: String, t: RelationshipType,
        id: String = "r-$src-$tgt-${t.name}"
    ) = RelationshipEntity(
        id = id, sourcePersonId = src, targetPersonId = tgt,
        type = t, relativeAgeOrder = null, createdAt = 0, updatedAt = 0)

    private fun snap(
        persons: List<PersonEntity>,
        rels: List<RelationshipEntity> = emptyList()
    ) = SpouseMerge.Snapshot(persons, emptyList(), rels, emptyList())

    /** 本机“我”正确时能得到合并计划（旧实现误传文件所选 id，永远算不出）。 */
    @Test
    fun 本机我可得到合并计划() {
        val local = snap(listOf(p("me", "杨明", Gender.MALE, self = true)))
        val remote = snap(
            listOf(
                p("peer", "李雪", Gender.FEMALE, self = true),
                p("pf", "李国强", Gender.MALE)),
            listOf(r("pf", "peer", RelationshipType.PARENT)))
        val plan = ShareImport.spouseMergePlan(
            local, remote, peerSelfId = "peer", localSelfId = "me",
            selectedId = null)
        assertNotNull(plan)
        // 对方家人全部新增，外加两人之间的 SPOUSE
        assertEquals(2, plan!!.newPersons.size)
        assertTrue(plan.newRelationships.any {
            it.type == RelationshipType.SPOUSE &&
                setOf(it.sourcePersonId, it.targetPersonId) == setOf("me", "peer")
        })
    }

    /**
     * 对方录入的“我”名字不同（“老公”）：未选中时按“对方已有配偶”拒绝 →
     * 不提供入口；“你是谁”里选中那条记录 → 种子映射认回，不新建重复的“我”。
     */
    @Test
    fun 对方录入的我_异名_须选中() {
        val local = snap(listOf(p("me", "杨明", Gender.MALE, self = true)))
        val remote = snap(
            listOf(
                p("peer", "李雪", Gender.FEMALE, self = true),
                p("pb", "老公", Gender.MALE)),
            listOf(r("peer", "pb", RelationshipType.SPOUSE)))
        assertNull(ShareImport.spouseMergePlan(
            local, remote, "peer", "me", selectedId = null))
        val plan = ShareImport.spouseMergePlan(
            local, remote, "peer", "me", selectedId = "pb")
        assertNotNull(plan)
        assertEquals("me", plan!!.idMap["pb"])
        assertTrue(plan.newPersons.none { it.name == "老公" })
    }

    /**
     * 我已录入配偶（与对方同名同性别）、对方文件里没有我：
     * approved 恒含 peerSelfId → 对方本人映射到我已录入的配偶记录，
     * 不新建对方、不重复建 SPOUSE。
     */
    @Test
    fun 我已录入配偶_对方无我记录() {
        val local = snap(
            listOf(
                p("me", "杨明", Gender.MALE, self = true),
                p("w", "李雪", Gender.FEMALE)),
            listOf(r("me", "w", RelationshipType.SPOUSE)))
        val remote = snap(listOf(p("peer", "李雪", Gender.FEMALE, self = true)))
        val plan = ShareImport.spouseMergePlan(
            local, remote, "peer", "me", selectedId = null)
        assertNotNull(plan)
        assertEquals("w", plan!!.idMap["peer"])
        assertTrue(plan.newPersons.none { it.id == "peer" })
        assertTrue(plan.newRelationships.none {
            it.type == RelationshipType.SPOUSE
        })
    }

}
