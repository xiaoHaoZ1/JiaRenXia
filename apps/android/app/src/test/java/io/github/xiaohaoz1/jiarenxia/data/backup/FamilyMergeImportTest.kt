package io.github.xiaohaoz1.jiarenxia.data.backup

import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 通用合并写入（FAMILY_AFFAIRS 16.5）：同源更新→importFamilyMerge→幂等；
 * 字段改写、头像只增不删、事务回滚连带清理新头像文件。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class FamilyMergeImportTest : RepoTestBase() {

    private lateinit var backup: BackupManager
    private lateinit var ctx: android.content.Context
    private val pw = "test1234".toCharArray()
    private lateinit var ids: Map<String, String>
    private val selfId get() = ids.getValue(TestFamily.SELF_NAME)
    private val dadId get() = ids.getValue("杨建国")

    @Before
    fun setUpMerge() = runTest {
        ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, FakeSettingsRepository())
        ids = TestFamily.seed(repo, db)
    }

    /** 导出共享文件并解密为 plan。 */
    private suspend fun exportPlan(): RestorePlan {
        val out = ByteArrayOutputStream()
        backup.exportShare(out, pw, sharedBy = selfId)
        return backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
    }

    private suspend fun planOf(
        remote: RestorePlan,
        approved: Set<String> = emptySet(),
        takeRemote: Set<FamilyMerge.ConflictKey> = emptySet()
    ) = (FamilyMerge.plan(
        FamilyMerge.Request(
            local = backup.localFamilySide(),
            remote = backup.familySideOf(remote),
            localSelfId = selfId,
            anchors = mapOf(selfId to selfId),
            approved = approved,
            takeRemote = takeRemote)
    ) as FamilyMerge.Result.Ok).plan

    /** 对方更新：加新家人+关系+手机号、给“我”补农历生日、给爸改名。 */
    private fun remoteUpdated(base: RestorePlan): RestorePlan {
        val newGuy = PersonEntity(
            id = "r-new", name = "杨涛", gender = Gender.MALE,
            avatarPath = null, note = null,
            livingStatus = LivingStatus.ALIVE, isSelf = false,
            createdAt = 0, updatedAt = 0)
        val newRel = RelationshipEntity(
            id = "r-new-rel", sourcePersonId = "r-new",
            targetPersonId = selfId, type = RelationshipType.SIBLING,
            relativeAgeOrder = null, createdAt = 0, updatedAt = 0)
        val newPhone = PhoneNumberEntity(
            id = "r-new-ph", personId = dadId, number = "138-9999-0000",
            label = PhoneLabel.MOBILE, status = PhoneStatus.ACTIVE,
            isPrimary = true, note = null, createdAt = 0, updatedAt = 0)
        val lunar = ImportantDateEntity(
            id = "r-new-d", personId = selfId,
            type = ImportantDateType.BIRTHDAY_LUNAR,
            year = 1990, month = 4, day = 18,
            calendarType = CalendarType.LUNAR, isLeapMonth = false,
            note = null, createdAt = 0, updatedAt = 0)
        return base.copy(
            persons = base.persons.map {
                if (it.id == dadId) it.copy(name = "杨国栋") else it
            } + newGuy,
            relationships = base.relationships + newRel,
            phones = base.phones + newPhone,
            importantDates = base.importantDates + lunar)
    }

    @Test
    fun 同源更新_写入与幂等() = runTest {
        val personsBefore = db.personDao().count()
        val remote = remoteUpdated(exportPlan())
        val plan = planOf(remote)
        // 新人、新关系、新手机号、新日期都有；姓名冲突默认保留
        assertTrue(plan.newPersons.any { it.id == "r-new" })
        assertTrue(plan.conflicts.any {
            it.key == FamilyMerge.ConflictKey(dadId, "name") })
        backup.importFamilyMerge(plan, remote.avatars)

        assertNotNull(db.personDao().getById("r-new"))
        assertFalse(db.personDao().getById("r-new")!!.isSelf)
        assertTrue(db.relationshipDao().getBetween("r-new", selfId)
            .any { it.type == RelationshipType.SIBLING })
        assertTrue(db.phoneNumberDao().getForPerson(dadId)
            .any { it.number.contains("9999") })
        val lunar = db.importantDateDao()
            .getByPersonAndType(selfId, "BIRTHDAY_LUNAR")
        assertNotNull(lunar)
        // 新日期带默认偏好（文件无偏好 → {0,1}）
        val pref = db.reminderPreferenceDao().getForDate(lunar!!.id)
        assertNotNull(pref)
        assertTrue(pref!!.enabled)
        assertEquals(setOf(0, 1), db.reminderOffsetDao()
            .getForPreference(pref.id).map { it.daysBefore }.toSet())
        // 姓名冲突默认保留本机
        assertEquals("杨建国", db.personDao().getById(dadId)!!.name)
        // 总人物数 = 原数 + 1（没有重复认人产生的多余人物）
        assertEquals(personsBefore + 1, db.personDao().count())

        // 幂等：再算一次 → 无写入，只剩姓名冲突
        val again = planOf(remote)
        assertFalse(again.hasWrites())
        assertEquals(listOf(FamilyMerge.ConflictKey(dadId, "name")),
            again.conflicts.map { it.key })

        // takeRemote 改名 → personUpdates 生效
        val take = planOf(remote,
            takeRemote = setOf(FamilyMerge.ConflictKey(dadId, "name")))
        assertEquals(1, take.personUpdates.size)
        backup.importFamilyMerge(take, remote.avatars)
        assertEquals("杨国栋", db.personDao().getById(dadId)!!.name)
    }

    @Test
    fun 头像_只增不删() = runTest {
        // 本机“我”先有自己头像文件
        val avatarDir = File(ctx.filesDir, "avatars")
        avatarDir.mkdirs()
        val selfFile = File(avatarDir, "self-local.jpg")
        selfFile.writeBytes(byteArrayOf(1, 2, 3))
        db.personDao().updateAvatar(
            selfId, "avatars/self-local.jpg", 0L)

        val base = exportPlan()
        // 对方文件：新人物带头像 + 爸（本机无头像）带头像 + 我（本机已有）带头像
        val remote = base.copy(
            persons = base.persons.map {
                when (it.id) {
                    selfId -> it.copy(avatarPath = "avatars/r-self.jpg")
                    dadId -> it.copy(avatarPath = "avatars/r-dad.jpg")
                    else -> it
                }
            } + PersonEntity(
                id = "r-new", name = "杨涛", gender = Gender.MALE,
                avatarPath = "avatars/r-new.jpg", note = null,
                livingStatus = LivingStatus.ALIVE, isSelf = false,
                createdAt = 0, updatedAt = 0),
            avatars = base.avatars + mapOf(
                "r-self.jpg" to byteArrayOf(9),
                "r-dad.jpg" to byteArrayOf(8),
                "r-new.jpg" to byteArrayOf(7)))
        val plan = planOf(remote)
        assertEquals(2, plan.avatarAdds.size)   // self 已有 → 不加
        backup.importFamilyMerge(plan, remote.avatars)

        // 新人物与爸都拿到了新文件；各自路径不同
        val newP = db.personDao().getById("r-new")!!
        val dadP = db.personDao().getById(dadId)!!
        assertNotNull(newP.avatarPath)
        assertNotNull(dadP.avatarPath)
        assertTrue(File(ctx.filesDir, newP.avatarPath!!).exists())
        assertEquals(8,
            File(ctx.filesDir, dadP.avatarPath!!).readBytes()[0].toInt())
        // 本机已有头像：文件字节与路径都没动
        assertEquals("avatars/self-local.jpg",
            db.personDao().getById(selfId)!!.avatarPath)
        assertEquals(3, selfFile.readBytes().size)
    }

    @Test
    fun 事务失败_回滚且清理新头像() = runTest {
        val base = exportPlan()
        val remote = base.copy(
            persons = base.persons + PersonEntity(
                id = "r-new", name = "杨涛", gender = Gender.MALE,
                avatarPath = "avatars/r-new.jpg", note = null,
                livingStatus = LivingStatus.ALIVE, isSelf = false,
                createdAt = 0, updatedAt = 0),
            avatars = base.avatars + mapOf("r-new.jpg" to byteArrayOf(7)))
        val plan = planOf(remote)
        val before = db.personDao().count()
        val avatarDir = File(ctx.filesDir, "avatars")
        val filesBefore = (avatarDir.listFiles()?.size ?: 0)
        try {
            backup.importFamilyMerge(plan, remote.avatars) {
                throw RuntimeException("tx boom")
            }
            throw AssertionError("应当抛异常")
        } catch (e: RuntimeException) {
            assertEquals("tx boom", e.message)
        }
        // 库无任何新增
        assertEquals(before, db.personDao().count())
        assertNull(db.personDao().getById("r-new"))
        // 新写的头像文件已删（目录里不多文件）
        assertEquals(filesBefore, avatarDir.listFiles()?.size ?: 0)
    }
}
