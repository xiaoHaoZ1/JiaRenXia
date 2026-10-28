package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 兄弟姐妹排行（siblingRank）：录入校验、落库、备份往返、
 * 共享合并冲突。图排序见 ui/graph/SiblingRankGraphTest，
 * 家庭页子女排序见 domain/FamilyOrderingTest。
 */
class SiblingRankTest : RepoTestBase() {

    @Test
    fun 排行保存编辑与清空() = runTest {
        val me = createSelf()
        val son = repo.createRelative(
            PersonDraft("三儿子", Gender.MALE, siblingRank = 3),
            me, RelationRole.SON).ok()
        assertEquals(3, db.personDao().getById(son)!!.siblingRank)
        // 表单未填排行 → 清除字段
        repo.updatePerson(son, PersonDraft("三儿子", Gender.MALE)).ok()
        assertNull(db.personDao().getById(son)!!.siblingRank)
    }

    @Test
    fun 排行校验范围() = runTest {
        val me = createSelf()
        assertErr(
            repo.createRelative(PersonDraft("x", Gender.MALE, siblingRank = 0),
                me, RelationRole.SON), FamilyError.InvalidSiblingRank)
        assertErr(
            repo.createRelative(PersonDraft("x", Gender.MALE, siblingRank = 51),
                me, RelationRole.SON), FamilyError.InvalidSiblingRank)
        // 边界 1 与 50 合法；null 合法
        repo.createRelative(PersonDraft("a", Gender.MALE, siblingRank = 1),
            me, RelationRole.SON).ok()
        repo.createRelative(PersonDraft("b", Gender.MALE, siblingRank = 50),
            me, RelationRole.SON).ok()
    }

    @Test
    fun 备份往返保留排行_未记录不写键() {
        val ranked = PersonEntity(
            id = "a", name = "老大", gender = Gender.MALE, avatarPath = null,
            note = null, livingStatus = LivingStatus.ALIVE, isSelf = false,
            siblingRank = 1, createdAt = 1, updatedAt = 1)
        val unranked = ranked.copy(id = "b", name = "老小", siblingRank = null)
        val zip = BackupJson.writeZip(
            persons = listOf(ranked, unranked), phones = emptyList(),
            relationships = emptyList(), dates = emptyList(),
            prefs = emptyList(), customTitles = emptyList(),
            settings = null, avatars = emptyMap(),
            createdAt = 1L, appVersion = "t")
        // 写出的 JSON：有排行才有 siblingRank 键（旧备份没键读出来是 null）
        val jsonText = java.util.zip.ZipInputStream(
            java.io.ByteArrayInputStream(zip)).use { z ->
            generateSequence { z.nextEntry }
                .first { it.name == "backup.json" }
                .let { z.readBytes().toString(Charsets.UTF_8) }
        }
        assertTrue(jsonText.contains("\"siblingRank\":1"))
        assertEquals(1, Regex("siblingRank").findAll(jsonText).count())
        val plan = BackupJson.parsePlan(zip)
        assertEquals(1, plan.persons.first { it.id == "a" }.siblingRank)
        assertNull(plan.persons.first { it.id == "b" }.siblingRank)
    }

    @Test
    fun 共享合并_排行差异是字段冲突() {
        val me = p("me", "我", Gender.MALE, self = true)
        val local = side(listOf(me, p("kid", "儿子", Gender.MALE, rank = 1)))
        val remote = side(listOf(me, p("kid", "儿子", Gender.MALE, rank = 3)))
        // 不采用远端 → 本机值保留
        val plan1 = FamilyMerge.plan(FamilyMerge.Request(
            local = local, remote = remote, localSelfId = "me",
            now = 1, newId = { "n" })).let { (it as FamilyMerge.Result.Ok).plan }
        val c = plan1.conflicts.single {
            it.key == FamilyMerge.ConflictKey("kid", "siblingRank") }
        assertEquals("排行", c.fieldLabel)
        assertEquals("第 1", c.localValue)
        assertEquals("第 3", c.remoteValue)
        assertFalse(c.takeRemote)
        assertTrue(plan1.personUpdates.isEmpty())
        // 采用远端 → rank=3 写入更新
        val plan2 = FamilyMerge.plan(FamilyMerge.Request(
            local = local, remote = remote, localSelfId = "me",
            takeRemote = setOf(FamilyMerge.ConflictKey("kid", "siblingRank")),
            now = 1, newId = { "n" })).let { (it as FamilyMerge.Result.Ok).plan }
        assertEquals(3, plan2.personUpdates.single().siblingRank)
    }

    private fun p(id: String, name: String, g: Gender,
                  self: Boolean = false, rank: Int? = null) = PersonEntity(
        id = id, name = name, gender = g, avatarPath = null, note = null,
        livingStatus = LivingStatus.ALIVE, isSelf = self,
        siblingRank = rank, createdAt = 0, updatedAt = 0)

    private fun side(persons: List<PersonEntity>) = FamilyMerge.Side(
        persons, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
}
