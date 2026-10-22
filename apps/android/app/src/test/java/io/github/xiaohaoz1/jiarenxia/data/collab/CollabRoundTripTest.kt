package io.github.xiaohaoz1.jiarenxia.data.collab

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.FamilyError
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.PhoneDraft
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorageImpl
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupCrypto
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.ChainLink
import io.github.xiaohaoz1.jiarenxia.data.backup.CollabMeta
import io.github.xiaohaoz1.jiarenxia.data.backup.QuickSnapshotStore
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyRevisionPlan
import io.github.xiaohaoz1.jiarenxia.ui.share.CollabViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

/**
 * 离线协作双设备往返（FAMILY_AFFAIRS §5）：真实内存库 ×2 + 真实
 * 备份/快照/协作服务，模拟“汇总者发布 → 成员接收 → 成员补充 →
 * 汇总者汇入 → 再发布 → 成员再接收”的完整闭环。
 * ConscryptMode.OFF：JCE 自检会被 Robolectric 注册的 Conscrypt 破坏。
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [34])
class CollabRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** 模拟一台设备：独立内存库、独立暂存/存档目录。 */
    private inner class Dev(val name: String) {
        var seq = 0
        val db: AppDatabase = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java).build()
        val ids = object : IdGenerator {
            override fun newId() = name + "-id-" + (++seq)
        }
        val clock = object : TimeSource {
            override fun now() = 1_700_000_000_000L
        }
        val storage = AvatarStorageImpl(context)
        val repo = FamilyRepositoryImpl(
            db = db, avatarStorage = storage, ids = ids, clock = clock,
            lunar = IcuLunarCalendar())
        // FakeSettingsRepository：A6 后模块开关已删除，无初始化门闩。
        val backup = BackupManager(context, db, FakeSettingsRepository())
        val snaps = QuickSnapshotStore(context, db, backup)
            .apply { dirName = "qs_$name" }
        val service = FamilyCollaborationService(
            context, db, repo, snaps, storage, ids, clock)
            .apply { incomingDirName = "inc_$name" }

        fun close() {
            db.close()
            File(context.filesDir, "qs_$name").deleteRecursively()
            File(context.filesDir, "inc_$name").deleteRecursively()
        }
    }

    private val devices = ArrayList<Dev>()
    private fun dev(name: String) = Dev(name).also { devices += it }

    @After
    fun tearDown() {
        devices.forEach { it.close() }
    }

    private val pw = "test-pw".toCharArray()

    /** 汇总者家庭：我 + 父 + 母（合成名）。 */
    private suspend fun buildCoordinatorFamily(a: Dev) {
        val self = a.repo.createSelf(
            PersonDraft(name = "汇总", gender = Gender.MALE)).let {
            (it as FamilyResult.Success).value
        }
        a.repo.createRelative(
            PersonDraft(name = "老杨", gender = Gender.MALE),
            self, RelationRole.FATHER)
        a.repo.createRelative(
            PersonDraft(name = "秀兰", gender = Gender.FEMALE),
            self, RelationRole.MOTHER)
    }

    /** 发布确认版 → 字节（模拟传输介质）。 */
    private suspend fun publish(a: Dev): ByteArray {
        val out = ByteArrayOutputStream()
        a.service.exportConfirmed(out, pw)
        return out.toByteArray()
    }

    private suspend fun contribute(b: Dev): ByteArray {
        val out = ByteArrayOutputStream()
        b.service.exportContribution(out, pw)
        return out.toByteArray()
    }

    // ---------- 发布 → 接收闭环 ----------

    @Test
    fun `发布接收闭环_成员首次绑定`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)

        val pkg = publish(a)
        // 成员预览：全部是自动新增（无候选、无基准）
        val preview = b.service.prepareReceive(pkg, pw)
        assertEquals("confirmed", preview.meta.mode)
        assertEquals(1, preview.meta.revision)
        assertTrue(!preview.hasLocalSelf)
        assertTrue(preview.plan.items.isNotEmpty())
        assertTrue(preview.plan.decisions.isEmpty())

        // 成员应用：选协调者人物为“我”
        b.service.applyReceived(
            packageId = preview.draft.packageId, password = pw,
            decisions = emptyMap(),
            selfCanonicalChoice = preview.meta.coordinatorPersonId)
        assertEquals(3, b.db.personDao().getAll().size)
        assertNotNull(b.db.personDao().getSelf())
        val st = b.db.familyCollabDao().get()!!
        assertEquals("member", st.role)
        assertEquals(1, st.acceptedRevision)
        assertEquals(preview.meta.snapshotHash, st.acceptedHash)
        // 保护副本已生效；草稿转 APPLIED；暂存文件删除
        assertTrue(b.snaps.hasPrereceive())
        assertEquals("APPLIED",
            b.db.familyReceiveDraftDao().get(preview.draft.packageId)!!.status)
        assertNull(b.service.pendingDraft())
    }

    @Test
    fun `补充包往返_成员补充_汇总者汇入_再发布`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val v1 = publish(a)
        val p1 = b.service.prepareReceive(v1, pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 成员补充：给爸爸加电话 + 新增一个人
        val dadLocal = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadLocal.id, PersonDraft(
            name = "老杨", gender = Gender.MALE,
            phones = listOf(PhoneDraft(
                number = "13911112222", isPrimary = true))))
        val bSelf = b.db.personDao().getSelf()!!.id
        b.repo.createRelative(
            PersonDraft(name = "妹妹", gender = Gender.FEMALE),
            bSelf, RelationRole.YOUNGER_SISTER)

        val contrib = contribute(b)
        val prevA = a.service.prepareReceive(contrib, pw)
        assertEquals("contribution", prevA.meta.mode)
        assertEquals(a.db.familyCollabDao().get()!!.seriesId,
            prevA.meta.seriesId)
        // 电话新增 + 新人“妹妹” + 她与“我”的姐弟关系 → 自动应用
        val applies = prevA.plan.items.filter {
            it.state == FamilyRevisionPlan.State.APPLY }
        assertTrue(applies.size >= 2)
        a.service.applyReceived(prevA.draft.packageId, pw, emptyMap())

        // 汇总者侧已汇入：爸爸电话 + 妹妹 + 关系
        val aDad = a.db.personDao().getAll().first { it.name == "老杨" }
        assertEquals(listOf("13911112222"),
            a.db.phoneNumberDao().getForPerson(aDad.id).map { it.number })
        assertNotNull(a.db.personDao().getAll()
            .firstOrNull { it.name == "妹妹" })

        // 汇总者再发布 v2 → 成员接收后与本地一致
        val v2 = publish(a)
        assertEquals(2, a.db.familyCollabDao().get()!!.acceptedRevision)
        val p2 = b.service.prepareReceive(v2, pw)
        assertEquals(2, p2.meta.revision)
        assertTrue(p2.plan.items.isEmpty())
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap())
        assertEquals(2, b.db.familyCollabDao().get()!!.acceptedRevision)
    }

    @Test
    fun `幂等_同包重复接收拒绝`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val pkg = publish(a)
        val p1 = b.service.prepareReceive(pkg, pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        // 同包再投：已处理
        try {
            b.service.prepareReceive(pkg, pw)
            fail("应拒绝重复包")
        } catch (e: CollabException) {
            assertTrue(e.message!!.contains("已经接收"))
        }
        // apply 路径同样拦截
        try {
            b.service.applyReceived(p1.draft.packageId, pw, emptyMap())
            fail("应拒绝重复应用")
        } catch (e: CollabException) {
            assertTrue(e.message!!.contains("已处理"))
        }
    }

    @Test
    fun `幂等_同id不同内容拒绝`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val pkg = publish(a)
        b.service.prepareReceive(pkg, pw)  // 留 PENDING 不应用

        // 伪造：相同 packageId、不同内容的确认版
        val meta = CollabMeta(
            mode = "confirmed",
            seriesId = a.db.familyCollabDao().get()!!.seriesId,
            coordinatorPersonId = "x",
            packageId = b.service.pendingDraft()!!.packageId,
            revision = 1, parentRevision = 0, parentHash = null,
            snapshotHash = "different",
            revisionChain = listOf(ChainLink(1, "different", null)),
            baseRevision = null, baseHash = null, baseSnapshot = null,
            idMap = emptyMap(), scope = "full")
        val zip = BackupJson.writeZip(
            persons = listOf(PersonEntity(
                id = "x", name = "x", gender = Gender.MALE,
                avatarPath = null, note = null,
                livingStatus = LivingStatus.ALIVE,
                isSelf = false, createdAt = 1, updatedAt = 1)),
            phones = emptyList(), relationships = emptyList(),
            dates = emptyList(), prefs = emptyList(),
            customTitles = emptyList(),
            settings = null, avatars = emptyMap(), createdAt = 1,
            appVersion = "t", purpose = "share", sharedBy = "x",
            collaboration = meta)
        val forged = BackupCrypto.encrypt(zip, pw)
        try {
            b.service.prepareReceive(forged, pw)
            fail("应拒绝同 id 不同内容")
        } catch (e: CollabException) {
            assertTrue(e.message!!.contains("不一致"))
        }
    }

    @Test
    fun `旧版本与错系列拒绝`() = runTest {
        val a = dev("a")
        val a2 = dev("a2")
        val b = dev("b")
        buildCoordinatorFamily(a)
        buildCoordinatorFamily(a2)
        val v1 = publish(a)
        val p1 = b.service.prepareReceive(v1, pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        // 另一台汇总者发布的 v1：系列不同
        val foreign = publish(a2)
        try {
            b.service.prepareReceive(foreign, pw)
            fail("应拒绝其他系列")
        } catch (e: CollabException) {
            assertTrue(e.message!!.contains("不属于本机加入的家庭系列"))
        }
    }

    @Test
    fun `仅本机保留不进补充包`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 成员加了一个“私人”人物并标为仅本机保留
        val bSelf = b.db.personDao().getSelf()!!.id
        b.repo.createRelative(
            PersonDraft(name = "私密友人", gender = Gender.MALE),
            bSelf, RelationRole.BROTHER)
        val priv = b.db.personDao().getAll().first { it.name == "私密友人" }
        b.service.markLocalOnly(priv.id)

        // 快照与补充包都不含此人
        val roster = b.repo.canonicalRosterSnapshot()
        assertTrue(roster.snap.persons.none { it.name == "私密友人" })
        assertTrue(priv.id in b.service.observeLocalOnlyIds().first())

        val contrib = contribute(b)
        val prevA = a.service.prepareReceive(contrib, pw)
        assertTrue(prevA.plan.items.none {
            it is FamilyRevisionPlan.PersonAdd && it.person.name == "私密友人" })
        a.service.applyReceived(prevA.draft.packageId, pw, emptyMap())
        assertNull(a.db.personDao().getAll()
            .firstOrNull { it.name == "私密友人" })
    }

    @Test
    fun `未汇入保护_成员本地改动不被覆盖`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 成员本地把“老杨”改名（未汇入远端）
        val dadLocal = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadLocal.id,
            PersonDraft(name = "杨老", gender = Gender.MALE))

        // 汇总者加人后发 v2（同快照复用版号；此处名册有真实变化）
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val v2 = publish(a)
        val p2 = b.service.prepareReceive(v2, pw)
        assertTrue(p2.plan.pendingLocal.any { it.text.contains("杨老") })
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap())
        assertEquals("杨老",
            b.db.personDao().getById(dadLocal.id)!!.name)
        assertEquals(2, b.db.familyCollabDao().get()!!.acceptedRevision)
    }

    @Test
    fun `名册哈希复核_直接调用方带错哈希被拒`() = runTest {
        val a = dev("a")
        buildCoordinatorFamily(a)
        val r = a.repo.applyFamilyRevision(
            CollabApply(expectedRosterHash = "bogus"))
        assertTrue(r is FamilyResult.Failure)
        assertEquals(FamilyError.CollabStale,
            (r as FamilyResult.Failure).error)
    }

    @Test
    fun `自我移除阻止_事务回滚`() = runTest {
        val a = dev("a")
        buildCoordinatorFamily(a)
        val self = a.db.personDao().getSelf()!!
        val roster = a.repo.canonicalRosterSnapshot()
        val r = a.repo.applyFamilyRevision(CollabApply(
            expectedRosterHash = roster.hash,
            removePersons = listOf(self.id)))
        assertTrue(r is FamilyResult.Failure)
        assertEquals(FamilyError.CollabSelfBlocked,
            (r as FamilyResult.Failure).error)
        // 事务整体回滚：名册不变
        assertEquals(3, a.db.personDao().getAll().size)
        assertNotNull(a.db.personDao().getSelf())
    }

    @Test
    fun `私人引用人物被移除_转仅本机保留`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 成员给“老杨”记了私人联系计划（不随名册共享的私人外键）
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.db.contactPlanDao().upsert(
            io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity(
                personId = dadB.id, intervalDays = 30,
                createdAt = 1, updatedAt = 1))

        // 汇总者把“老杨”从名册移除并发布 v2
        val dadA = a.db.personDao().getAll().first { it.name == "老杨" }
        a.repo.deletePerson(dadA.id)
        val p2 = b.service.prepareReceive(publish(a), pw)
        // 采用远端移除（所有决策都取远端）
        b.service.applyReceived(p2.draft.packageId, pw,
            decisions = p2.plan.decisions.associate {
                it.key to FamilyRevisionPlan.Choice.TAKE_REMOTE.wire },
            reviewedToken = b.service.reviewToken(p2.draft.packageId))

        // 有私人引用 → 不物理删除，转“仅本机保留”
        assertNotNull(b.db.personDao().getById(dadB.id))
        assertNotNull(b.db.contactPlanDao().get(dadB.id))
        assertTrue(dadB.id in b.service.observeLocalOnlyIds().first())
        assertEquals(2, b.db.familyCollabDao().get()!!.acceptedRevision)
    }

    @Test
    fun `新增人物的电话与生日随采用到达`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 汇总者 v2 新增“弟弟”：带电话与阳历生日
        val aSelf = a.db.personDao().getSelf()!!.id
        a.repo.createRelative(
            PersonDraft(
                name = "弟弟", gender = Gender.MALE,
                birthdays = PersonBirthdays(solar = BirthdayValue(
                    PartialDate(1990, 3, 5))),
                phones = listOf(PhoneDraft(
                    number = "13800001111", isPrimary = true))),
            aSelf, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap())

        val bro = b.db.personDao().getAll().first { it.name == "弟弟" }
        assertEquals(listOf("13800001111"),
            b.db.phoneNumberDao().getForPerson(bro.id).map { it.number })
        assertNotNull(b.db.importantDateDao()
            .getByPersonAndType(bro.id, "BIRTHDAY_SOLAR"))
    }

    @Test
    fun `放弃补充_以确认版为准`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 成员本机改动（未汇入）：改名 + 加电话 + 新增一个人
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "杨老", gender = Gender.MALE,
            phones = listOf(PhoneDraft(number = "13577776666"))))
        val bSelf = b.db.personDao().getSelf()!!.id
        b.repo.createRelative(
            PersonDraft(name = "本机新增", gender = Gender.FEMALE),
            bSelf, RelationRole.YOUNGER_SISTER)

        // 汇总者加人后发 v2；成员选“放弃这些补充，以确认版为准”
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        assertTrue(p2.plan.pendingLocal.isNotEmpty())
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap(),
            dropPending = true,
            reviewedToken = b.service.reviewToken(p2.draft.packageId))

        // 本机改动全部回写为收到的值
        assertEquals("老杨", b.db.personDao().getById(dadB.id)!!.name)
        assertTrue(b.db.phoneNumberDao().getForPerson(dadB.id).isEmpty())
        assertNull(b.db.personDao().getAll()
            .firstOrNull { it.name == "本机新增" })
        assertEquals(2, b.db.familyCollabDao().get()!!.acceptedRevision)
    }

    @Test
    fun `草稿决策持久化`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val pkg = publish(a)
        val p = b.service.prepareReceive(pkg, pw)
        // 只接受计划中真实存在的项（残留/伪造键不算有效确认）
        val key = p.plan.items.first().key
        b.service.saveDecisions(p.draft.packageId, mapOf(key to "keep"))
        assertEquals(mapOf(key to "keep"),
            b.service.draftDecisions(p.draft.packageId))
        b.service.saveDecisions(p.draft.packageId, mapOf("bogus" to "keep"))
        assertTrue("bogus" !in b.service.draftDecisions(p.draft.packageId))
        assertTrue(b.service.draftPlanCache(p.draft.packageId).isNotEmpty())
        b.service.discardDraft(p.draft.packageId)
        assertNull(b.service.pendingDraft())
    }

    // ---------- 审查回归：R1 仅本机再纳入的同键条目 ----------

    @Test
    fun `仅本机再纳入_同键电话日期原位更新且提醒不丢`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        // v1：老杨带电话与阳历生日
        val dadA = a.db.personDao().getAll().first { it.name == "老杨" }
        a.repo.updatePerson(dadA.id, PersonDraft(
            name = "老杨", gender = Gender.MALE,
            birthdays = PersonBirthdays(solar = BirthdayValue(
                PartialDate(1970, 3, 2))),
            phones = listOf(PhoneDraft(number = "13800001111"))))
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 记住本机条目行 id，并给生日挂上私人提醒偏好
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        val phoneId = b.db.phoneNumberDao().getForPerson(dadB.id).single().id
        val dateId = b.db.importantDateDao()
            .getByPersonAndType(dadB.id, "BIRTHDAY_SOLAR")!!.id
        b.db.reminderPreferenceDao().insert(
            io.github.xiaohaoz1.jiarenxia.data.db.ReminderPreferenceEntity(
                id = "rp-$dateId", importantDateId = dateId, enabled = true,
                createdAt = 1, updatedAt = 1))
        b.db.reminderOffsetDao().insertAll(listOf(
            io.github.xiaohaoz1.jiarenxia.data.db.ReminderOffsetEntity(
                id = "ro-$dateId", reminderPreferenceId = "rp-$dateId",
                daysBefore = 3)))

        // 成员把老杨标为仅本机保留；汇总者加人后重发（老杨仍在包里）
        b.service.markLocalOnly(dadB.id)
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        // 老杨在远端与基准、不在本机名册 → 待汇入；选“以确认版为准”
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap(),
            dropPending = true,
            reviewedToken = b.service.reviewToken(p2.draft.packageId))

        // 原位恢复：人物行不变、电话/生日按同键更新并保留行 id
        val dad = b.db.personDao().getById(dadB.id)!!
        assertEquals("老杨", dad.name)
        val phones = b.db.phoneNumberDao().getForPerson(dadB.id)
        assertEquals(listOf("13800001111"), phones.map { it.number })
        assertEquals(phoneId, phones.single().id)
        val date = b.db.importantDateDao()
            .getByPersonAndType(dadB.id, "BIRTHDAY_SOLAR")!!
        assertEquals(dateId, date.id)
        assertEquals(2, date.day)
        // 提醒偏好与偏移跟着行 id 走，不能丢
        val pref = b.db.reminderPreferenceDao().getForDate(dateId)
        assertNotNull(pref)
        assertEquals(listOf(3), b.db.reminderOffsetDao()
            .getForPreference(pref!!.id).map { it.daysBefore })
        assertTrue(b.db.familyLocalOnlyDao().getAll()
            .none { it.personId == dadB.id })
    }

    // ---------- 审查回归：R3 头像引用保护 ----------

    /** 给文件系统写一个头像文件并让指定人物引用它。 */
    private suspend fun giveAvatar(dev: Dev, personName: String,
                                   file: String, bytes: ByteArray) {
        File(context.filesDir, "avatars").mkdirs()
        File(context.filesDir, "avatars/$file").writeBytes(bytes)
        val p = dev.db.personDao().getAll().first { it.name == personName }
        dev.db.personDao().update(p.copy(avatarPath = "avatars/$file"))
    }

    @Test
    fun `私人引用人物转仅本机_头像文件保留`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        giveAvatar(a, "老杨", "dad.jpg", byteArrayOf(1, 2, 3))
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        assertNotNull(dadB.avatarPath)
        // 私人引用 → 被移除时转仅本机
        b.db.contactPlanDao().upsert(
            io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity(
                personId = dadB.id, intervalDays = 30,
                createdAt = 1, updatedAt = 1))
        a.repo.deletePerson(
            a.db.personDao().getAll().first { it.name == "老杨" }.id)
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p2.draft.packageId, pw,
            decisions = p2.plan.decisions.associate {
                it.key to FamilyRevisionPlan.Choice.TAKE_REMOTE.wire },
            reviewedToken = b.service.reviewToken(p2.draft.packageId))
        // 人转仅本机保留，头像路径仍有效、文件仍在
        val kept = b.db.personDao().getById(dadB.id)!!
        assertNotNull(kept.avatarPath)
        assertTrue(b.storage.resolve(kept.avatarPath!!).isFile)
        assertTrue(dadB.id in b.service.observeLocalOnlyIds().first())
    }

    @Test
    fun `同内容头像共享文件_一人换头像不误删`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        // 老杨与秀兰引用同一份头像内容
        giveAvatar(a, "老杨", "same.jpg", byteArrayOf(9, 8, 7))
        giveAvatar(a, "秀兰", "same.jpg", byteArrayOf(9, 8, 7))
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val momB = b.db.personDao().getAll().first { it.name == "秀兰" }
        val sharedPath = momB.avatarPath!!
        assertTrue(b.storage.resolve(sharedPath).isFile)

        // v2：只换老杨的头像
        giveAvatar(a, "老杨", "newdad.jpg", byteArrayOf(4, 5, 6))
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap())
        // 秀兰仍引用旧文件 → 不能删
        val mom = b.db.personDao().getById(momB.id)!!
        assertEquals(sharedPath, mom.avatarPath)
        assertTrue(b.storage.resolve(sharedPath).isFile)
    }

    @Test
    fun `仅本机再纳入_同头像复用与空头像清除`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        giveAvatar(a, "老杨", "dad.jpg", byteArrayOf(1, 2, 3))
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        val avatarPath = dadB.avatarPath!!
        assertTrue(b.storage.resolve(avatarPath).isFile)

        // 成员仅本机保留；汇总者名册变化（加弟弟）后重发
        b.service.markLocalOnly(dadB.id)
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap(),
            dropPending = true,
            reviewedToken = b.service.reviewToken(p2.draft.packageId))
        // 再纳入时远端头像与本机相同 → 文件复用不能删
        val back = b.db.personDao().getById(dadB.id)!!
        assertNotNull(back.avatarPath)
        assertTrue(b.storage.resolve(back.avatarPath!!).isFile)

        // 再仅本机；汇总者把老杨头像清掉后重发 → 按完整快照明确清除
        b.service.markLocalOnly(dadB.id)
        val dadA2 = a.db.personDao().getAll().first { it.name == "老杨" }
        a.db.personDao().update(dadA2.copy(avatarPath = null))
        a.repo.createRelative(
            PersonDraft(name = "妹妹", gender = Gender.FEMALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_SISTER)
        val p3 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p3.draft.packageId, pw, emptyMap(),
            dropPending = true,
            reviewedToken = b.service.reviewToken(p3.draft.packageId))
        assertNull(b.db.personDao().getById(dadB.id)!!.avatarPath)
    }

    // ---------- 审查回归：R4 提交后故障与副本归位 ----------

    @Test
    fun `副本激活失败不回滚已提交接收`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        // 故障注入：正式副本位置被非空目录占用 → 激活写不进
        val dir = File(context.filesDir, "qs_b").apply { mkdirs() }
        File(dir, "prereceive.jrxs").mkdirs()
        File(dir, "prereceive.jrxs/hold").writeBytes(byteArrayOf(0))
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        // 数据库提交是有效的：资料到位、草稿转已应用、不再可重放
        assertEquals(3, b.db.personDao().getAll().size)
        assertEquals("APPLIED",
            b.db.familyReceiveDraftDao().get(p1.draft.packageId)!!.status)
        // 移除障碍后归位：候选副本仍能找回
        File(dir, "prereceive.jrxs").deleteRecursively()
        assertTrue(b.snaps.hasPrereceive())
    }

    @Test
    fun `头像写入失败_提交前失败不留垃圾`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        giveAvatar(a, "老杨", "dad.jpg", byteArrayOf(1, 2, 3))
        val p1 = b.service.prepareReceive(publish(a), pw)
        // 故障注入：avatars 路径被文件占用 → 头像写不进
        val block = File(context.filesDir, "avatars")
        block.deleteRecursively()
        block.writeBytes(byteArrayOf(0))
        try {
            b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
                selfCanonicalChoice = p1.meta.coordinatorPersonId)
            fail("头像写失败应报错")
        } catch (e: Exception) {
            // 预期失败
        } finally {
            block.delete()
        }
        // 提交前失败：草稿仍待处理、没有新增人物、没有遗留协作头像
        assertEquals("PENDING",
            b.db.familyReceiveDraftDao().get(p1.draft.packageId)!!.status)
        assertEquals(0, b.db.personDao().getAll().size)
        val avatars = File(context.filesDir, "avatars")
        assertTrue(!avatars.isDirectory ||
            avatars.list()!!.none { it.startsWith("collab-") })
    }

    // ---------- 审查回归：R6 基准与版本链校验 ----------

    /** 取出真实包的协作元数据。 */
    private fun metaOf(pkg: ByteArray) =
        BackupJson.parsePlan(BackupCrypto.decrypt(pkg, pw)).collaboration!!

    /** 用同一批内容换一份协作元数据重新打包（篡改用）。 */
    private fun repack(plan: io.github.xiaohaoz1.jiarenxia.data.backup
                           .RestorePlan,
                       meta: CollabMeta): ByteArray =
        BackupCrypto.encrypt(BackupJson.writeZip(
            persons = plan.persons, phones = plan.phones,
            relationships = plan.relationships, dates = plan.importantDates,
            prefs = plan.prefs, customTitles = plan.customTitles,
            tasks = plan.tasks,
            contactLogs = plan.contactLogs, contactPlans = plan.contactPlans,
            settings = plan.settings, avatars = plan.avatars,
            createdAt = 1, appVersion = "t", purpose = plan.purpose,
            sharedBy = plan.sharedBy, personOrigins = plan.personOrigins,
            collaboration = meta, avatarHashes = plan.avatarHashes), pw)

    @Test
    fun `补充基准未发布_降级保守比较不删资料`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        // 成员：删掉老杨 + 给秀兰补电话
        b.repo.deletePerson(
            b.db.personDao().getAll().first { it.name == "老杨" }.id)
        val momB = b.db.personDao().getAll().first { it.name == "秀兰" }
        b.repo.updatePerson(momB.id, PersonDraft(
            name = "秀兰", gender = Gender.FEMALE,
            phones = listOf(PhoneDraft(number = "13600001111"))))
        val parsed = BackupJson.parsePlan(
            BackupCrypto.decrypt(contribute(b), pw))
        // 伪造：基准序号改为汇总者从未发布的 99（快照与哈希自洽）
        val pkg = repack(parsed, parsed.collaboration!!.copy(baseRevision = 99))
        val prevA = a.service.prepareReceive(pkg, pw)
        // 未知基准不能按缺项删除：老杨的移除不得成为可应用项
        assertTrue(prevA.plan.items.none { it.key.startsWith("rm:") })
        a.service.applyReceived(prevA.draft.packageId, pw, emptyMap())
        assertNotNull(a.db.personDao().getAll()
            .firstOrNull { it.name == "老杨" })
        // 正常新增补充仍可收
        val momA = a.db.personDao().getAll().first { it.name == "秀兰" }
        assertEquals(listOf("13600001111"),
            a.db.phoneNumberDao().getForPerson(momA.id).map { it.number })
    }

    @Test
    fun `补充基准序号已知摘要不符_拒绝`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val parsed = BackupJson.parsePlan(
            BackupCrypto.decrypt(contribute(b), pw))
        // 伪造：baseRevision=1 确有其版，但基准快照换了个名字 → 摘要矛盾
        val baseObj = org.json.JSONObject(
            parsed.collaboration!!.baseSnapshot!!)
        baseObj.getJSONArray("persons").getJSONObject(0)
            .put("name", "改名甲")
        val tamperedHash = io.github.xiaohaoz1.jiarenxia.domain.merge
            .FamilySnapshot.hash(io.github.xiaohaoz1.jiarenxia.domain.merge
                .FamilySnapshot.parse(baseObj))
        val forged = parsed.collaboration.copy(
            baseSnapshot = baseObj.toString(), baseHash = tamperedHash)
        try {
            a.service.prepareReceive(repack(parsed, forged), pw)
            fail("基准与已发布摘要不符应拒绝")
        } catch (e: CollabException) {
            assertTrue(e.message!!.contains("基准") ||
                e.message!!.contains("已发布"))
        }
    }

    @Test
    fun `确认版链_缺环错父摘要乱序拒绝_合法跳版可收`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        // 汇总者连发 v2、v3，成员只收过 v1（合法跳版）
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        publish(a)
        a.repo.createRelative(
            PersonDraft(name = "妹妹", gender = Gender.FEMALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_SISTER)
        val v3 = publish(a)
        val parsed3 = BackupJson.parsePlan(BackupCrypto.decrypt(v3, pw))
        val meta3 = parsed3.collaboration!!
        assertEquals(listOf(1, 2, 3),
            meta3.revisionChain.map { it.revision })

        suspend fun expectReject(meta: CollabMeta, why: String) {
            try {
                b.service.prepareReceive(repack(parsed3, meta), pw)
                fail("$why 应拒绝")
            } catch (e: CollabException) { /* 预期 */ }
        }
        // 缺第 2 环
        expectReject(meta3.copy(revisionChain =
            meta3.revisionChain.filter { it.revision != 2 }), "缺环")
        // 中间环节父摘要被改
        expectReject(meta3.copy(revisionChain = meta3.revisionChain.map {
            if (it.revision == 2) it.copy(parentHash = "forged") else it
        }), "错父摘要")
        // 顶层父信息与末环不符
        expectReject(meta3.copy(parentHash = "forged"), "顶层父摘要")
        // 链乱序
        expectReject(meta3.copy(
            revisionChain = meta3.revisionChain.reversed()), "乱序")

        // 原包合法跳版可收
        val pv = b.service.prepareReceive(v3, pw)
        assertEquals(3, pv.meta.revision)
        b.service.applyReceived(pv.draft.packageId, pw, emptyMap())
        assertEquals(3, b.db.familyCollabDao().get()!!.acceptedRevision)
    }

    // ---------- 审查回归：R5/R9 决策绑定与选择持久化 ----------

    @Test
    fun `草稿决策绑定资料_本机改动后须重新确认`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 成员改老杨备注（待汇入）；汇总者改成另一个值 → v2 分歧
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        a.repo.updatePerson(
            a.db.personDao().getAll().first { it.name == "老杨" }.id,
            PersonDraft(name = "老杨", gender = Gender.MALE, note = "汇总注"))
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        val item = p2.plan.decisions.single { it.key.endsWith(":note") }
        // 预览时对当前值做了决策并持久化
        b.service.saveDecisions(p2.draft.packageId,
            mapOf(item.key to "take"))
        // 退出核实期间，本机又把备注改成第三值
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "又改"))

        // 不续草稿直接应用：旧决策指纹对不上，该项回到未确认而不放行
        try {
            b.service.applyReceived(p2.draft.packageId, pw,
                mapOf(item.key to "take"))
            fail("本机已改后旧决策不应再有效")
        } catch (e: CollabException) {
            assertTrue(e.message!!.contains("没有确认"))
        }
        // 资料未被写坏、草稿仍待处理
        assertEquals("又改", b.db.personDao().getById(dadB.id)!!.note)
        assertEquals("PENDING",
            b.db.familyReceiveDraftDao().get(p2.draft.packageId)!!.status)
        // 续草稿：该项决策应被作废、需重新确认
        val resumed = b.service.resumeReview(p2.draft.packageId)!!
        assertTrue(item.key !in resumed.decisions)
        assertEquals(1, resumed.invalidCount)
        // 重新确认后可正常接收（携回本次预览令牌）
        b.service.applyReceived(p2.draft.packageId, pw,
            resumed.decisions + mapOf(item.key to "take"),
            reviewedToken = resumed.planToken)
        assertEquals("汇总注", b.db.personDao().getById(dadB.id)!!.note)
    }

    @Test
    fun `接收选择随草稿持久化与恢复`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val v1 = publish(a)
        // 首次绑定：我是谁选择持久化
        val p1 = b.service.prepareReceive(v1, pw)
        b.service.saveDecisions(p1.draft.packageId, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val r1 = b.service.resumeReview(p1.draft.packageId)!!
        assertEquals(p1.meta.coordinatorPersonId, r1.selfCanonicalChoice)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)

        // 成员补充待汇入 + 仅本机勾选 + 放弃补充选择持久化
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        val canonDad = b.repo.canonicalRosterSnapshot()
            .canonicalToLocal.entries.first { it.value == dadB.id }.key
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.saveDecisions(p2.draft.packageId, emptyMap(),
            dropPending = true, localOnlyMarks = setOf(canonDad))
        val r2 = b.service.resumeReview(p2.draft.packageId)!!
        assertTrue(r2.dropPending)
        assertEquals(setOf(canonDad), r2.localOnlyMarks)
        // 应用时草稿里的放弃补充与仅本机标记生效（无需调用方再传）
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap())
        assertNull(b.db.personDao().getById(dadB.id)!!.note)
        assertTrue(dadB.id in b.service.observeLocalOnlyIds().first())
    }

    // ---------- 审查回归 2：决策必须绑定实际看过的资料 ----------

    @Test
    fun `作废后重复提交旧决策被拒绝`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        // 双方对同一字段产生分歧（v2）
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        a.repo.updatePerson(
            a.db.personDao().getAll().first { it.name == "老杨" }.id,
            PersonDraft(name = "老杨", gender = Gender.MALE, note = "汇总注"))
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        val item = p2.plan.decisions.single { it.key.endsWith(":note") }
        b.service.saveDecisions(p2.draft.packageId,
            mapOf(item.key to "take"))
        // 本机资料再变 → 续草稿作废该项
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "又改"))
        val resumed = b.service.resumeReview(p2.draft.packageId)!!
        assertEquals(1, resumed.invalidCount)
        // 旧审阅请求再次提交同一决策：服务端不得放行
        try {
            b.service.applyReceived(p2.draft.packageId, pw,
                mapOf(item.key to "take"))
            fail("作废后的旧决策不应再被应用")
        } catch (e: CollabException) {
            // 预期：未确认/资料已变化
        }
        assertEquals("又改", b.db.personDao().getById(dadB.id)!!.note)
        assertEquals("PENDING",
            b.db.familyReceiveDraftDao().get(p2.draft.packageId)!!.status)
    }

    @Test
    fun `无指纹旧草稿_本机变化后重传旧决策不放行`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        a.repo.updatePerson(
            a.db.personDao().getAll().first { it.name == "老杨" }.id,
            PersonDraft(name = "老杨", gender = Gender.MALE, note = "汇总注"))
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        val item = p2.plan.decisions.single { it.key.endsWith(":note") }
        // 模拟旧格式草稿：扁平 {key:wire}，无指纹
        val d = b.db.familyReceiveDraftDao().get(p2.draft.packageId)!!
        b.db.familyReceiveDraftDao().upsert(d.copy(
            decisionsJson = JSONObject().put(item.key, "take").toString()))
        // 本机资料又变化
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "又改"))
        try {
            b.service.applyReceived(p2.draft.packageId, pw,
                mapOf(item.key to "take"))
            fail("旧格式草稿资料变化后，重传决策不应放行")
        } catch (e: CollabException) {
            // 预期
        }
        assertEquals("又改", b.db.personDao().getById(dadB.id)!!.note)
    }

    @Test
    fun `放弃补充后新增本机改动_不自动覆盖未核对内容`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        // 本机改备注（待汇入 P1）→ 汇总者发 v2 → 用户选“放弃补充”
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.saveDecisions(p2.draft.packageId, emptyMap(),
            dropPending = true)
        // 退出后又产生新的待汇入改动（用户没核过它）
        val momB = b.db.personDao().getAll().first { it.name == "秀兰" }
        b.repo.updatePerson(momB.id, PersonDraft(
            name = "秀兰", gender = Gender.FEMALE, note = "新改的"))
        // 直接应用：旧的放弃选择只对当时核对过的待汇入生效
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap())
        assertEquals("新改的", b.db.personDao().getById(momB.id)!!.note)
    }

    @Test
    fun `仅本机勾选_待汇入上下文变化后失效`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        val canonDad = b.repo.canonicalRosterSnapshot()
            .canonicalToLocal.entries.first { it.value == dadB.id }.key
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.saveDecisions(p2.draft.packageId, emptyMap(),
            localOnlyMarks = setOf(canonDad))
        // 待汇入上下文变化（另一人也出现新改动）
        val momB = b.db.personDao().getAll().first { it.name == "秀兰" }
        b.repo.updatePerson(momB.id, PersonDraft(
            name = "秀兰", gender = Gender.FEMALE, note = "新改的"))
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap())
        assertTrue(dadB.id !in b.service.observeLocalOnlyIds().first())
    }

    @Test
    fun `取消放弃补充后立即接收_旧选择不复活`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.saveDecisions(p2.draft.packageId, emptyMap(),
            dropPending = true)
        // 用户随后明确取消放弃
        b.service.saveDecisions(p2.draft.packageId, emptyMap(),
            dropPending = false)
        // 明确传入“不放弃”（false 是确定的选择，不是未提供）
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap(),
            dropPending = false)
        assertEquals("本机注", b.db.personDao().getById(dadB.id)!!.note)
    }

    @Test
    fun `取消仅本机勾选后立即接收_旧勾选不复活`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        val canonDad = b.repo.canonicalRosterSnapshot()
            .canonicalToLocal.entries.first { it.value == dadB.id }.key
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.saveDecisions(p2.draft.packageId, emptyMap(),
            localOnlyMarks = setOf(canonDad))
        // 用户随后明确清空勾选（空集是选择，不是未提供）
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap(),
            localOnlyMarks = emptySet())
        assertTrue(dadB.id !in b.service.observeLocalOnlyIds().first())
    }

    @Test
    fun `晚完成的旧保存不覆盖新选择`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        val key = p1.plan.items.first().key
        // 新保存(seq=2)先落库，旧保存(seq=1)晚到 → 不得回写
        b.service.saveDecisions(p1.draft.packageId,
            mapOf(key to "take"), dropPending = true, seq = 2)
        b.service.saveDecisions(p1.draft.packageId,
            mapOf(key to "keep"), dropPending = false, seq = 1)
        val c = b.service.draftChoices(p1.draft.packageId)
        assertEquals("take", c.decisions[key])
        assertTrue(c.dropPending)
        // 无序调用方（seq=0）视为兼容路径：仍可整份覆盖
        b.service.saveDecisions(p1.draft.packageId,
            emptyMap(), dropPending = false)
        assertTrue(!b.service.draftChoices(p1.draft.packageId).dropPending)
    }

    @Test
    fun `已应用草稿不再被保存回写`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        assertEquals("APPLIED",
            b.db.familyReceiveDraftDao().get(p1.draft.packageId)!!.status)
        // 晚到的旧保存不得改写已应用的草稿
        b.service.saveDecisions(p1.draft.packageId,
            mapOf("x" to "take"), dropPending = true)
        val d = b.db.familyReceiveDraftDao().get(p1.draft.packageId)!!
        assertEquals("APPLIED", d.status)
        val c = b.service.draftChoices(p1.draft.packageId)
        assertTrue(!c.dropPending)
        assertTrue("x" !in c.decisions)
    }

    // ---------- 审查回归 2：保护副本提交态与世代配对 ----------

    /** 用真实快照走完“候选→生效”一轮，返回 qs 目录。 */
    private suspend fun commitPrereceiveOf(b: Dev): File {
        val dir = File(context.filesDir, "qs_b").apply { mkdirs() }
        assertTrue(b.snaps.savePrereceiveCandidate())
        File(dir, "prereceive-candidate.state").writeText("committed")
        b.snaps.commitPrereceive()
        assertTrue(b.snaps.hasPrereceive())
        return dir
    }

    @Test
    fun `未提交候选不自动激活_实际恢复旧副本`() = runTest {
        val b = dev("b")
        buildCoordinatorFamily(b)
        val dir = commitPrereceiveOf(b)  // live = 三口之家 A
        // 删掉一人，产生“接收前候选 B”（两人），但数据库还没提交
        b.db.personDao().deleteById(
            b.db.personDao().getAll().first { it.name == "秀兰" }.id)
        assertTrue(b.snaps.savePrereceiveCandidate())
        File(dir, "prereceive-candidate.state").writeText("pending")
        // 模拟中断态：live 已挪 bak、候选尚未提升
        File(dir, "prereceive.jrxs")
            .renameTo(File(dir, "prereceive-bak.jrxs"))
        File(dir, "prereceive.meta")
            .renameTo(File(dir, "prereceive-bak.meta"))
        // 查询触发的归位不得把未提交候选当正式副本
        assertTrue(b.snaps.hasPrereceive())
        val r = b.snaps.restorePrereceive()
        assertTrue(r is QuickSnapshotStore.RestoreResult.Ok)
        assertEquals(3, b.db.personDao().getAll().size)
        assertNotNull(
            b.db.personDao().getAll().firstOrNull { it.name == "秀兰" })
    }

    @Test
    fun `已提交候选中断_完成激活并实际恢复`() = runTest {
        val b = dev("b")
        buildCoordinatorFamily(b)
        val dir = commitPrereceiveOf(b)  // live = A（三人）
        b.db.personDao().deleteById(
            b.db.personDao().getAll().first { it.name == "秀兰" }.id)
        assertTrue(b.snaps.savePrereceiveCandidate())
        // 数据库已提交、激活中断：live 已挪 bak，候选已标 committed
        File(dir, "prereceive-candidate.state").writeText("committed")
        File(dir, "prereceive.jrxs")
            .renameTo(File(dir, "prereceive-bak.jrxs"))
        File(dir, "prereceive.meta")
            .renameTo(File(dir, "prereceive-bak.meta"))
        assertTrue(b.snaps.hasPrereceive())
        val r = b.snaps.restorePrereceive()
        assertTrue(r is QuickSnapshotStore.RestoreResult.Ok)
        assertEquals(2, b.db.personDao().getAll().size)
        assertNull(
            b.db.personDao().getAll().firstOrNull { it.name == "秀兰" })
    }

    @Test
    fun `meta与数据跨代不混配_回退有效bak`() = runTest {
        val b = dev("b")
        buildCoordinatorFamily(b)
        val dir = commitPrereceiveOf(b)  // live = A
        b.db.personDao().deleteById(
            b.db.personDao().getAll().first { it.name == "秀兰" }.id)
        assertTrue(b.snaps.savePrereceiveCandidate())
        File(dir, "prereceive-candidate.state").writeText("pending")
        // 模拟“仅数据文件轮换成功、meta 未动”的跨代混配：
        // live 位置是 B 数据 + A meta；bak 位置只有 A 数据；候选（未
        // 提交）只有 B meta
        File(dir, "prereceive.jrxs")
            .renameTo(File(dir, "prereceive-bak.jrxs"))
        File(dir, "prereceive-candidate.jrxs")
            .renameTo(File(dir, "prereceive.jrxs"))
        // 归位：跨代 live 不算数；A 代由 live.meta + bak 数据重组回
        // 位；未提交候选 B 配对完整但绝不自动激活
        assertTrue(b.snaps.hasPrereceive())
        val r = b.snaps.restorePrereceive()
        assertTrue(r is QuickSnapshotStore.RestoreResult.Ok)
        assertEquals(3, b.db.personDao().getAll().size)
        assertNotNull(
            b.db.personDao().getAll().firstOrNull { it.name == "秀兰" })
    }

    @Test
    fun `部分写入的副本不掩盖有效bak`() = runTest {
        val b = dev("b")
        buildCoordinatorFamily(b)
        val dir = commitPrereceiveOf(b)  // live = A
        // bak = A 完整对；live.jrxs 被截断（部分复制失败）、meta 未变
        File(dir, "prereceive.jrxs")
            .copyTo(File(dir, "prereceive-bak.jrxs"))
        File(dir, "prereceive.meta")
            .copyTo(File(dir, "prereceive-bak.meta"))
        val full = File(dir, "prereceive.jrxs").readBytes()
        File(dir, "prereceive.jrxs")
            .writeBytes(full.copyOf(full.size / 2))
        assertTrue(b.snaps.hasPrereceive())
        val r = b.snaps.restorePrereceive()
        assertTrue(r is QuickSnapshotStore.RestoreResult.Ok)
        assertEquals(3, b.db.personDao().getAll().size)
    }

    @Test
    fun `首次接收无旧副本_提交后候选生效可恢复`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        // b 首次接收：无旧 live/bak，候选经提交生效
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        assertTrue(b.snaps.hasPrereceive())
        val r = b.snaps.restorePrereceive()
        assertTrue(r is QuickSnapshotStore.RestoreResult.Ok)
        // 接收前本机为空 → 恢复后无人
        assertEquals(0, b.db.personDao().getAll().size)
    }

    // ---------- 审查回归：R7 同快照复用版号 ----------

    @Test
    fun `相同共享快照重发复用版号`() = runTest {
        val a = dev("a")
        buildCoordinatorFamily(a)
        val out1 = ByteArrayOutputStream()
        val m1 = a.service.exportConfirmed(out1, pw)
        val out2 = ByteArrayOutputStream()
        val m2 = a.service.exportConfirmed(out2, pw)
        assertEquals(1, m1.revision)
        assertEquals(1, m2.revision)          // 同一共享快照不推进版号
        assertTrue(m1.packageId != m2.packageId)  // 仍是新加密包
        assertEquals(1,
            a.db.familyPublicationDao().getAll().size)
        // 共享内容变化后才推进
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val out3 = ByteArrayOutputStream()
        val m3 = a.service.exportConfirmed(out3, pw)
        assertEquals(2, m3.revision)
        assertEquals(2, a.db.familyPublicationDao().getAll().size)
    }

    // ---------- 审查回归 3：写序原子性 / 提交态认领 / 跨代回退 / 缺凭据 ----------

    /** 等某个条件成立（VM/服务保存走真实 IO 线程）。 */
    private fun waitFor(desc: String, cond: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!runBlocking { cond() } &&
            System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("等待超时: $desc", runBlocking { cond() })
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `重建ViewModel后首次修改正常保存`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val a = dev("a")
            val b = dev("b")
            buildCoordinatorFamily(a)
            val p1 = b.service.prepareReceive(publish(a), pw)
            val key = p1.plan.items.first().key
            val vm1 = CollabViewModel(
                context, b.service, b.db, b.repo, AppEvents())
            vm1.startReview()
            waitFor("vm1 载入") {
                vm1.review.value.packageId == p1.draft.packageId }
            vm1.setDecision(key, "take")
            waitFor("vm1 保存落库") {
                b.service.draftChoices(p1.draft.packageId)
                    .decisions[key] == "take" }
            // 退出再进：重建 VM 续审，首次修改也必须能保存
            val vm2 = CollabViewModel(
                context, b.service, b.db, b.repo, AppEvents())
            vm2.startReview()
            waitFor("vm2 载入") {
                vm2.review.value.packageId == p1.draft.packageId }
            vm2.setDecision(key, "keep")
            waitFor("vm2 保存落库") {
                b.service.draftChoices(p1.draft.packageId)
                    .decisions[key] == "keep" }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `保存与应用交错_已应用状态不被旧保存回写`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        val key = p1.plan.items.first().key
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // 可控交错：保存已通过 PENDING/序号检查，卡在落库前
        b.service.saveGate = { entered.complete(Unit); release.await() }
        val save = async(Dispatchers.IO) {
            b.service.saveDecisions(p1.draft.packageId,
                mapOf(key to "take"), seq = 9)
        }
        entered.await()
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        release.complete(Unit)
        save.await()
        // 应用已 APPLIED；晚完成的旧保存不得把草稿写回 PENDING
        val d = b.db.familyReceiveDraftDao().get(p1.draft.packageId)!!
        assertEquals("APPLIED", d.status)
        assertTrue(b.service.draftChoices(p1.draft.packageId)
            .decisions[key] != "take")
    }

    @Test
    fun `数据库已提交未写标记_重启认领候选并恢复接收前资料`() = runTest {
        val a = dev("a")
        val b = dev("b")
        // 接收前本机资料：b 已有自己的“我”
        b.repo.createSelf(PersonDraft(name = "主人", gender = Gender.MALE))
        buildCoordinatorFamily(a)
        // 模拟“数据库已提交、commitPrereceive 尚未写标记”处中止
        b.snaps.blockCommitWrite = true
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap())
        b.snaps.blockCommitWrite = false
        assertEquals("APPLIED",
            b.db.familyReceiveDraftDao().get(p1.draft.packageId)!!.status)
        // 故障注入须真的停在“写标记前”：候选未激活，提交态仍是
        // pending:<pkg>，正式副本位没有文件
        val dir = File(context.filesDir, "qs_b")
        assertEquals("pending:" + p1.draft.packageId,
            File(dir, "prereceive-candidate.state").readText())
        assertTrue(File(dir, "prereceive-candidate.jrxs").isFile)
        assertTrue(!File(dir, "prereceive.jrxs").isFile)
        // 重启：新建存储实例即可认领（归位直查同库事务落库的
        // APPLIED 记录），不依赖再次实例化协作服务接线
        val fresh = QuickSnapshotStore(context, b.db, b.backup)
            .apply { dirName = "qs_b" }
        assertTrue(fresh.hasPrereceive())
        val r = fresh.restorePrereceive()
        assertTrue(r is QuickSnapshotStore.RestoreResult.Ok)
        // 恢复的是这次接收前的家庭资料：只有“主人”一人
        assertEquals(1, b.db.personDao().getAll().size)
        assertEquals("主人", b.db.personDao().getAll().first().name)
    }

    @Test
    fun `冷启动找回副本_新建存储实例认领最新提交而非更旧副本`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        // 第一轮正常接收：b 原有“主人”一人，生效的副本 = {主人}
        b.repo.createSelf(PersonDraft(name = "主人", gender = Gender.MALE))
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap())
        assertEquals(4, b.db.personDao().getAll().size)
        // 汇总者删掉秀兰发 v2；b 本轮接收的候选副本 = 接收前的四人
        a.repo.deletePerson(
            a.db.personDao().getAll().first { it.name == "秀兰" }.id)
        b.snaps.blockCommitWrite = true  // 数据库提交后、标记/激活前中止
        val p2 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p2.draft.packageId, pw,
            decisions = p2.plan.decisions.associate {
                it.key to FamilyRevisionPlan.Choice.TAKE_REMOTE.wire },
            reviewedToken = b.service.reviewToken(p2.draft.packageId))
        b.snaps.blockCommitWrite = false
        assertEquals("APPLIED",
            b.db.familyReceiveDraftDao().get(p2.draft.packageId)!!.status)
        // 注入生效确认：候选未被激活——正式副本位仍是上一代的一人副本
        val dir = File(context.filesDir, "qs_b")
        assertEquals("pending:" + p2.draft.packageId,
            File(dir, "prereceive-candidate.state").readText())
        assertTrue(File(dir, "prereceive-candidate.jrxs").isFile)
        assertEquals(1, JSONObject(
            File(dir, "prereceive.meta").readText()).getInt("persons"))
        // 模拟冷启动直接进数据管理：新建存储实例、不构建协作服务，
        // 归位凭同库事务落库的 APPLIED 记录认领并完成激活
        val fresh = QuickSnapshotStore(context, b.db, b.backup)
            .apply { dirName = "qs_b" }
        val restored = fresh.restorePrereceive()
        assertTrue(restored is QuickSnapshotStore.RestoreResult.Ok)
        // 找回的是本次接收前的四人资料，不是上一代的一人副本
        assertEquals(4, b.db.personDao().getAll().size)
        assertNotNull(
            b.db.personDao().getAll().firstOrNull { it.name == "秀兰" })
    }

    @Test
    fun `live新一代数据损坏_回退不同代的有效bak`() = runTest {
        val b = dev("b")
        buildCoordinatorFamily(b)
        val dir = commitPrereceiveOf(b)          // live = A（三人）
        b.db.personDao().deleteById(
            b.db.personDao().getAll().first { it.name == "秀兰" }.id)
        assertTrue(b.snaps.savePrereceiveCandidate("p-b2"))
        // 模拟激活进行到一半中止：live(A) 已挪 bak、候选(B) 已挪 live、
        // 提交态仍是 committed——干净提交会顺手删 bak，这里要的是
        // bak 仍在的残局
        File(dir, "prereceive.jrxs")
            .renameTo(File(dir, "prereceive-bak.jrxs"))
        File(dir, "prereceive.meta")
            .renameTo(File(dir, "prereceive-bak.meta"))
        File(dir, "prereceive-candidate.jrxs")
            .renameTo(File(dir, "prereceive.jrxs"))
        File(dir, "prereceive-candidate.meta")
            .renameTo(File(dir, "prereceive.meta"))
        File(dir, "prereceive-candidate.state").writeText("committed")
        // live 数据损坏：截断后与 meta 记录摘要不符（部分写入残件）
        val f = File(dir, "prereceive.jrxs")
        f.writeBytes(f.readBytes().copyOf(16))
        // live 未通过同代+完整性校验，不得删仍有效的 bak；恢复要回退到 A
        val r = b.snaps.restorePrereceive()
        assertTrue(r is QuickSnapshotStore.RestoreResult.Ok)
        assertEquals(3, b.db.personDao().getAll().size)
        assertNotNull(
            b.db.personDao().getAll().firstOrNull { it.name == "秀兰" })
    }

    @Test
    fun `缺域凭据的放弃与勾选不得生效_重新审阅后可提交`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        val p1 = b.service.prepareReceive(publish(a), pw)
        b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
            selfCanonicalChoice = p1.meta.coordinatorPersonId)
        val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
        b.repo.updatePerson(dadB.id, PersonDraft(
            name = "老杨", gender = Gender.MALE, note = "本机注"))
        val canonDad = b.repo.canonicalRosterSnapshot()
            .canonicalToLocal.entries.first { it.value == dadB.id }.key
        a.repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_BROTHER)
        val p2 = b.service.prepareReceive(publish(a), pw)
        // 模拟旧版草稿：存了放弃+勾选，但没有域凭据（无 ps 字段）
        val d0 = b.db.familyReceiveDraftDao().get(p2.draft.packageId)!!
        b.db.familyReceiveDraftDao().upsert(d0.copy(decisionsJson =
            JSONObject().apply {
                put("v", 2)
                put("d", JSONObject())
                put("drop", true)
                put("lo", JSONArray().apply { put(canonDad) })
            }.toString()))
        val staleToken = b.service.reviewToken(p2.draft.packageId)
        // 预览之后又出现新的本机改动 → 旧令牌过期
        val momB = b.db.personDao().getAll().first { it.name == "秀兰" }
        b.repo.updatePerson(momB.id, PersonDraft(
            name = "秀兰", gender = Gender.FEMALE, note = "再改"))
        b.service.applyReceived(p2.draft.packageId, pw, emptyMap(),
            dropPending = true, localOnlyMarks = setOf(canonDad),
            reviewedToken = staleToken)
        // 无凭据的危险选择不得生效：本机改动不被覆盖，人物不被藏起
        assertEquals("本机注", b.db.personDao().getById(dadB.id)!!.note)
        assertEquals("再改", b.db.personDao().getById(momB.id)!!.note)
        assertTrue(dadB.id !in b.service.observeLocalOnlyIds().first())
        // 重新审阅取得新令牌后可正常提交
        a.repo.createRelative(
            PersonDraft(name = "妹妹", gender = Gender.FEMALE),
            a.db.personDao().getSelf()!!.id, RelationRole.YOUNGER_SISTER)
        val p3 = b.service.prepareReceive(publish(a), pw)
        val r3 = b.service.resumeReview(p3.draft.packageId)!!
        b.service.applyReceived(p3.draft.packageId, pw, emptyMap(),
            dropPending = true, reviewedToken = r3.planToken)
        assertNull(b.db.personDao().getById(dadB.id)!!.note)
    }

    // ---------- 审查回归 4：身份裁决跨保存/重开持续有效 ----------

    @Test
    fun `身份裁决保存字段选择后不丢_退出重开确认接收`() = runTest {
        val a = dev("a")
        val b = dev("b")
        buildCoordinatorFamily(a)
        // 两端各有同名同性别的“汇总”，备注不同；本机的挂着私人记录
        a.repo.updatePerson(a.db.personDao().getSelf()!!.id, PersonDraft(
            name = "汇总", gender = Gender.MALE, note = "远端备注"))
        b.repo.createSelf(PersonDraft(
            name = "汇总", gender = Gender.MALE, note = "本机备注"))
        val bSelf = b.db.personDao().getSelf()!!.id
        b.db.contactPlanDao().upsert(
            io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity(
                personId = bSelf, intervalDays = 30,
                createdAt = 1, updatedAt = 1))

        // 远端“汇总”与本机“汇总”裁决为同一个人
        val p = b.service.prepareReceive(publish(a), pw)
        val identity = p.plan.decisions
            .filterIsInstance<FamilyRevisionPlan.PersonAdd>()
            .single { it.state == FamilyRevisionPlan.State.IDENTITY }
        val choice = "same:" + identity.candidates.single().localId
        b.service.saveDecisions(p.draft.packageId,
            mapOf(identity.key to choice), seq = 1)

        // 核对字段：身份并入重算后，分歧字段项要显示出来
        val review = b.service.resumeReview(p.draft.packageId)!!
        assertEquals(choice, review.decisions[identity.key])
        val fields = review.items.filter { it.state == "CONFLICT" }
        assertTrue("身份映射后应能核对字段分歧", fields.isNotEmpty())
        // 保存字段选择不得丢掉已被计划吸收的身份裁决
        b.service.saveDecisions(p.draft.packageId,
            review.decisions + fields.associate { it.key to "keep" },
            seq = 2)
        assertEquals(choice, b.service.draftChoices(p.draft.packageId)
            .decisions[identity.key])

        // 退出重开：身份裁决仍在，无需重新裁决
        val r2 = b.service.resumeReview(p.draft.packageId)!!
        assertEquals(choice, r2.decisions[identity.key])
        assertTrue(r2.items.none {
            it.state == FamilyRevisionPlan.State.IDENTITY.name })
        // 确认接收
        b.service.applyReceived(p.draft.packageId, pw, r2.decisions,
            reviewedToken = r2.planToken)

        // 人物不重复：“汇总”仍是一条本机行，远端另两人汇入
        assertEquals(3, b.db.personDao().getAll().size)
        assertEquals(1,
            b.db.personDao().getAll().count { it.name == "汇总" })
        // 字段按选择保留本机值；私人记录挂在原行不丢
        assertEquals("本机备注", b.db.personDao().getSelf()!!.note)
        assertNotNull(b.db.contactPlanDao().get(bSelf))
        assertEquals("APPLIED",
            b.db.familyReceiveDraftDao().get(p.draft.packageId)!!.status)
    }

    // ---------- 审查回归 5：身份裁决后页内重算刷新（不退出重进） ----------

    /** 经 AppEvents.pendingCollab 走真实界面交接路径启动接收页 VM。 */
    private fun reviewVm(b: Dev, bytes: ByteArray): CollabViewModel {
        val events = AppEvents()
        events.pendingCollab =
            io.github.xiaohaoz1.jiarenxia.data.PendingCollab(
                bytes, pw.copyOf())
        val vm = CollabViewModel(context, b.service, b.db, b.repo, events)
        vm.startReview()
        return vm
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `接收页确认同一人后当前页出现新分歧并完成接收`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val a = dev("a")
            val b = dev("b")
            buildCoordinatorFamily(a)
            a.repo.updatePerson(a.db.personDao().getSelf()!!.id, PersonDraft(
                name = "汇总", gender = Gender.MALE, note = "远端备注"))
            b.repo.createSelf(PersonDraft(
                name = "汇总", gender = Gender.MALE, note = "本机备注"))
            val bSelf = b.db.personDao().getSelf()!!.id
            b.db.contactPlanDao().upsert(
                io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity(
                    personId = bSelf, intervalDays = 30,
                    createdAt = 1, updatedAt = 1))

            val vm = reviewVm(b, publish(a))
            waitFor("接收页载入") { vm.review.value.packageId != null }
            val pkg = vm.review.value.packageId!!
            val identity = vm.review.value.decisionItems
                .single { it.state == "IDENTITY" }
            val same = "same:" + identity.candidates.single().first

            // 选择“是同一个人”：当前页必须重算出新分歧，不能要退出重进
            vm.setDecision(identity.key, same)
            waitFor("身份映射后的备注分歧出现在当前页") {
                vm.review.value.decisionItems.any {
                    it.key.endsWith(":note") } }
            // 身份映射带来的关系项也随之出现
            assertTrue(vm.review.value.applyItems.any {
                it.key.startsWith("rel:") })
            assertTrue("新分歧未确认时不得放行接收", !vm.review.value.ready)
            val noteKey = vm.review.value.decisionItems
                .single { it.key.endsWith(":note") }.key

            // 选择“保留本机” → 全部确认 → 同一页面直接接收
            vm.setDecision(noteKey,
                FamilyRevisionPlan.Choice.KEEP_LOCAL.wire)
            waitFor("全部选择就绪") { vm.review.value.ready }
            vm.applyReview()
            waitFor("接收完成") { vm.review.value.done }

            // 身份决定不丢、人物不重复、字段按选择更新、私人记录保留
            assertEquals(same,
                b.service.draftChoices(pkg).decisions[identity.key])
            assertEquals(3, b.db.personDao().getAll().size)
            assertEquals(1,
                b.db.personDao().getAll().count { it.name == "汇总" })
            assertEquals("本机备注", b.db.personDao().getSelf()!!.note)
            assertNotNull(b.db.contactPlanDao().get(bSelf))
            assertEquals("APPLIED",
                b.db.familyReceiveDraftDao().get(pkg)!!.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `快速连续修改身份选择以最新选择为准`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val a = dev("a")
            val b = dev("b")
            buildCoordinatorFamily(a)
            a.repo.updatePerson(a.db.personDao().getSelf()!!.id, PersonDraft(
                name = "汇总", gender = Gender.MALE, note = "远端备注"))
            b.repo.createSelf(PersonDraft(
                name = "汇总", gender = Gender.MALE, note = "本机备注"))

            val vm = reviewVm(b, publish(a))
            waitFor("接收页载入") { vm.review.value.packageId != null }
            val pkg = vm.review.value.packageId!!
            val identity = vm.review.value.decisionItems
                .single { it.state == "IDENTITY" }
            val same = "same:" + identity.candidates.single().first

            // 快速连点 same → new → same：最终以 same 为准，
            // 先完成的异步保存/重算不得把中间态写回页面
            vm.setDecision(identity.key, same)
            vm.setDecision(identity.key, "new")
            vm.setDecision(identity.key, same)
            waitFor("最新身份选择落库并刷新出分歧") {
                b.service.draftChoices(pkg).decisions[identity.key] == same &&
                    vm.review.value.decisionItems
                        .any { it.key.endsWith(":note") } }
            assertEquals(same, vm.review.value.decisions[identity.key])
            assertTrue(vm.review.value.decisionItems.none {
                it.state == "IDENTITY" })

            // 再快速切回“按新家人加入”：新分歧收起，身份项回到待确认
            vm.setDecision(identity.key, "new")
            waitFor("切回 new 后页态稳定") {
                b.service.draftChoices(pkg).decisions[identity.key] == "new" &&
                    vm.review.value.decisionItems
                        .none { it.key.endsWith(":note") } &&
                    vm.review.value.decisionItems
                        .any { it.state == "IDENTITY" } }
            assertTrue(vm.review.value.ready)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `本机已删人物的仅本机勾选项显示姓名而非规范id`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val a = dev("a")
            val b = dev("b")
            buildCoordinatorFamily(a)
            val v1 = publish(a)
            val p1 = b.service.prepareReceive(v1, pw)
            b.service.applyReceived(p1.draft.packageId, pw, emptyMap(),
                selfCanonicalChoice = p1.meta.coordinatorPersonId)

            // 成员有一处改动供发回补充
            val dadB = b.db.personDao().getAll().first { it.name == "老杨" }
            b.repo.updatePerson(dadB.id, PersonDraft(
                name = "老杨", gender = Gender.MALE,
                phones = listOf(PhoneDraft(
                    number = "13911112222", isPrimary = true))))

            // 汇总者删除「秀兰」：本机查不到姓名，远端名册仍含她
            // （未映射人物的规范 id = 本机 id，与 rosterSnapshotInTx 同规则）
            val momA = a.db.personDao().getAll().first { it.name == "秀兰" }
            val momCanon = a.repo.canonicalRosterSnapshot()
                .canonicalToLocal.entries
                .firstOrNull { it.value == momA.id }?.key ?: momA.id
            a.repo.deletePerson(momA.id)

            val vm = reviewVm(a, contribute(b))
            waitFor("接收页载入") { vm.review.value.packageId != null }
            assertTrue("删除项应出现在本机待汇入里",
                vm.review.value.pendingItems.any { it.personId == momCanon })
            assertEquals("已删人物的仅本机勾选项不得回退成规范 id",
                "秀兰", vm.review.value.pendingNames[momCanon])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `多身份裁决与待汇入并存时确认接收`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val a = dev("a")
            val b = dev("b")
            // 汇总者：我 + 父母 + 哥姐弟 + 妻子 + 岳父母 + 儿子
            val aSelf = a.repo.createSelf(PersonDraft(
                name = "ZhaoWei", gender = Gender.MALE))
                .let { (it as FamilyResult.Success).value }
            a.repo.updatePerson(aSelf, PersonDraft(
                name = "ZhaoWei", gender = Gender.MALE,
                phones = listOf(PhoneDraft(number = "13900001001"))))
            a.repo.createRelative(PersonDraft(name = "ZhaoQiang",
                gender = Gender.MALE, note = "coord-father-note"),
                aSelf, RelationRole.FATHER)
            a.repo.createRelative(PersonDraft(name = "SunLi",
                gender = Gender.FEMALE), aSelf, RelationRole.MOTHER)
            a.repo.createRelative(PersonDraft(name = "ZhaoJun",
                gender = Gender.MALE), aSelf, RelationRole.ELDER_BROTHER)
            a.repo.createRelative(PersonDraft(name = "ZhaoMin",
                gender = Gender.FEMALE), aSelf, RelationRole.ELDER_SISTER)
            a.repo.createRelative(PersonDraft(name = "ZhaoDi",
                gender = Gender.MALE), aSelf, RelationRole.YOUNGER_BROTHER)
            val aWife = a.repo.createRelative(PersonDraft(name = "LiuYan",
                gender = Gender.FEMALE), aSelf, RelationRole.WIFE)
                .let { (it as FamilyResult.Success).value }
            a.repo.createRelative(PersonDraft(name = "LiuGuo",
                gender = Gender.MALE), aWife, RelationRole.FATHER)
            a.repo.createRelative(PersonDraft(name = "WangFang",
                gender = Gender.FEMALE), aWife, RelationRole.MOTHER)
            a.repo.createRelative(PersonDraft(name = "ZhaoXiao",
                gender = Gender.MALE), aSelf, RelationRole.SON)

            // 成员：同名异 id 的我+父母+哥，外加私人的儿子与备注差异
            val bSelf = b.repo.createSelf(PersonDraft(
                name = "ZhaoWei", gender = Gender.MALE))
                .let { (it as FamilyResult.Success).value }
            b.repo.createRelative(PersonDraft(name = "ZhaoQiang",
                gender = Gender.MALE, note = "member-father-note"),
                bSelf, RelationRole.FATHER)
            b.repo.createRelative(PersonDraft(name = "SunLi",
                gender = Gender.FEMALE), bSelf, RelationRole.MOTHER)
            b.repo.createRelative(PersonDraft(name = "ZhaoJun",
                gender = Gender.MALE), bSelf, RelationRole.ELDER_BROTHER)
            b.repo.createRelative(PersonDraft(name = "ZhaoSi",
                gender = Gender.MALE), bSelf, RelationRole.SON)

            val vm = reviewVm(b, publish(a))
            waitFor("接收页载入") { vm.review.value.packageId != null }

            // 逐条裁决身份项：有“同名”候选选 same，否则按新家人（与真机一致：
            // 每条之间等页内重算完成再处理下一条）
            repeat(30) {
                val item = vm.review.value.decisionItems.firstOrNull {
                    it.state == "IDENTITY" &&
                        !vm.review.value.decisions.containsKey(it.key) }
                    ?: return@repeat
                val cand = item.candidates.firstOrNull { "同名" in it.third }
                val wire = if (cand != null) "same:" + cand.first else "new"
                vm.setDecision(item.key, wire)
                waitFor("裁决落库重算 " + item.key) {
                    b.service.draftChoices(vm.review.value.packageId!!)
                        .decisions[item.key] == wire }
                Thread.sleep(150)
            }
            waitFor("身份项全部裁决") {
                vm.review.value.decisionItems.none {
                    it.state == "IDENTITY" &&
                        !vm.review.value.decisions.containsKey(it.key) } }
            waitFor("备注分歧出现") { vm.review.value.decisionItems
                .any { it.key.endsWith(":note") } }
            val noteKey = vm.review.value.decisionItems
                .single { it.key.endsWith(":note") }.key
            vm.setDecision(noteKey,
                FamilyRevisionPlan.Choice.KEEP_LOCAL.wire)
            waitFor("全部就绪") { vm.review.value.ready }

            vm.applyReview()
            waitFor("接收完成或报错") {
                vm.review.value.done || vm.review.value.error != null }
            assertNull("接收不应被拒绝：" + vm.review.value.error,
                vm.review.value.error)

            // 私人记录与本地备注保留、人物合并不重复
            assertEquals("member-father-note", b.db.personDao().getAll()
                .first { it.name == "ZhaoQiang" }.note)
            assertEquals(1, b.db.personDao().getAll()
                .count { it.name == "ZhaoWei" })
            assertNotNull(b.db.personDao().getAll()
                .firstOrNull { it.name == "ZhaoSi" })

            // 裁决为新家人的人物的远端关系必须随包落库——曾出现
            // “未决”门把已裁决 New 的人的关系永远排除在计划外，
            // 应用后 ZhaoMin/ZhaoDi/LiuYan/ZhaoXiao 的关系全部静默丢失
            val byName = b.db.personDao().getAll().associateBy { it.name }
            val rels = b.db.relationshipDao().getAll()
            // 对称关系按 source<target 规范存储，方向不可预知
            fun hasRel(a: String, type: String, bName: String): Boolean {
                val x = byName[a]!!.id; val y = byName[bName]!!.id
                return rels.any {
                    it.type.name == type &&
                        it.sourcePersonId == x && it.targetPersonId == y }
            }
            fun hasPair(a: String, type: String, bName: String) =
                hasRel(a, type, bName) || hasRel(bName, type, a)
            assertTrue("ZhaoMin-ZhaoWei 兄弟姐妹应落库",
                hasPair("ZhaoMin", "SIBLING", "ZhaoWei"))
            assertTrue("ZhaoDi-ZhaoWei 兄弟姐妹应落库",
                hasPair("ZhaoDi", "SIBLING", "ZhaoWei"))
            assertTrue("ZhaoWei→ZhaoXiao 父母关系应落库",
                hasRel("ZhaoWei", "PARENT", "ZhaoXiao"))
            assertTrue("LiuGuo→LiuYan 父母关系应落库",
                hasRel("LiuGuo", "PARENT", "LiuYan"))
            assertTrue("ZhaoWei-LiuYan 夫妻应落库",
                hasPair("ZhaoWei", "SPOUSE", "LiuYan"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ---------- 发送回归：密码数组被对话框清空时包仍按原密码加密 ----------

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `发布包按输入密码加密_对话框清空数组不影响`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val a = dev("a")
            buildCoordinatorFamily(a)
            val vm = CollabViewModel(
                context, a.service, a.db, a.repo, AppEvents())
            vm.publish()
            val sendPw = "dev-secret".toCharArray()
            // 对话框在 onSubmit 返回后立刻 fill(' ')：复现真实竞态时序
            vm.submitSendPassword(sendPw)
            sendPw.fill(' ')
            waitFor("确认版生成") {
                vm.sendStep.value is CollabViewModel.SendStep.Ready }
            val file =
                (vm.sendStep.value as CollabViewModel.SendStep.Ready).file
            val bytes = file.readBytes()
            // 用提交时的原密码可解；用空格串（被清空后的数组）应失败
            val ok = BackupCrypto.decrypt(bytes, "dev-secret".toCharArray())
            assertTrue(ok.isNotEmpty())
            try {
                BackupCrypto.decrypt(bytes, CharArray(10) { ' ' })
                fail("空格密码不应能解开")
            } catch (expected: Exception) { }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
