package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.PhoneDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/**
 * 备份导出/校验/恢复（真实 DB + 临时文件目录）。
 * ConscryptMode.OFF：Robolectric 默认注册 Conscrypt 进 JCE，使
 * javax.crypto.JarVerifier 签名自检失败；关掉才能跑 AES/GCM。
 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class BackupManagerTest : RepoTestBase() {

    private val fakeSettings = InMemorySettings()
    private lateinit var backup: BackupManager
    private lateinit var ctx: Context

    @Before
    fun setUpBackup() {
        ctx = ApplicationProvider.getApplicationContext()
        backup = BackupManager(ctx, db, fakeSettings)
    }

    /** 内存设置实现（测试用）。 */
    private class InMemorySettings : SettingsRepository {
        private val _reminders = MutableStateFlow(true)
        private val _theme = MutableStateFlow(ThemeMode.LIGHT)
        private val _notif = MutableStateFlow(false)
        var runState: Pair<Long, Set<String>>? = 12345L to setOf("k")
        override val remindersEnabled: Flow<Boolean> = _reminders
        override suspend fun setRemindersEnabled(enabled: Boolean) { _reminders.value = enabled }
        override val taskRemindersEnabled = MutableStateFlow(true)
    override val contactRemindersEnabled = MutableStateFlow(true)
        override suspend fun setTaskRemindersEnabled(enabled: Boolean) {
            taskRemindersEnabled.value = enabled
        }
    override suspend fun setContactRemindersEnabled(enabled: Boolean) {
        contactRemindersEnabled.value = enabled
    }


        override suspend fun getReminderRunState() = runState
        override suspend fun setReminderRunState(epochDay: Long, notifiedKeys: Set<String>) {
            runState = epochDay to notifiedKeys
        }
        override suspend fun clearReminderRunState() { runState = null }
        override val notificationPermissionRequested: Flow<Boolean> = _notif
        override suspend fun setNotificationPermissionRequested(requested: Boolean) {
            _notif.value = requested
        }
        override val themeMode: Flow<ThemeMode> = _theme
        override val fontScaleFactor = kotlinx.coroutines.flow.MutableStateFlow(1.0f)
        override suspend fun setFontScaleFactor(f: Float) {
            fontScaleFactor.value = f
        }
        override suspend fun setThemeMode(mode: ThemeMode) { _theme.value = mode }
        override val reminderMinuteOfDay = kotlinx.coroutines.flow.MutableStateFlow(540)
        override suspend fun setReminderMinuteOfDay(minute: Int) {
            reminderMinuteOfDay.value = minute
        }
        override val taskReminderMinuteOfDay = kotlinx.coroutines.flow.MutableStateFlow(540)
        override suspend fun setTaskReminderMinuteOfDay(minute: Int) {
            taskReminderMinuteOfDay.value = minute
        }
        override val contactReminderMinuteOfDay = kotlinx.coroutines.flow.MutableStateFlow(540)
        override suspend fun setContactReminderMinuteOfDay(minute: Int) {
            contactReminderMinuteOfDay.value = minute
        }
        override val lastBackupAt = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
        override suspend fun setLastBackupAt(atMillis: Long) { lastBackupAt.value = atMillis }
        override suspend fun clear() {}
    }

    private val pw = "备份密码123456".toCharArray()

    /** 造数据：self + 6 亲属 + 农历闰月生日 + 长幼序 + 手机号 + 关闭的偏好 + 头像。 */
    private suspend fun seed(): String {
        val me = repo.createSelf(
            PersonDraft(
                name = "我", gender = Gender.MALE,
                birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1990, 5, 12))),
                phones = listOf(PhoneDraft(number = "13800138000", isPrimary = true))
            )
        ).ok()
        val dad = repo.createRelative(
            PersonDraft(
                "爸", Gender.MALE,
                birthdays = PersonBirthdays(lunar = BirthdayValue(
                    // 2023 农历闰二月（锚点已核实；测试时钟为 2023）
                    PartialDate(2023, 2, 5), CalendarType.LUNAR, leapMonth = true
                )),
                phones = listOf(PhoneDraft(number = "131", status = PhoneStatus.INACTIVE))
            ), me, RelationRole.FATHER
        ).ok()
        val bro = repo.createRelative(
            PersonDraft("哥", Gender.MALE), me, RelationRole.ELDER_BROTHER
        ).ok()
        val broRel = db.relationshipDao().getBetween(bro, me).single()
        repo.setSiblingAgeOrder(broRel.id, bro, RelativeAgeOrder.OLDER)
        // 自定义称呼（schemaVersion 3）：给“哥”设“老舅”，验证备份往返
        repo.setCustomTitle(bro, "老舅")
        fakeSettings.setFontScaleFactor(1.15f)
        repo.createRelative(
            PersonDraft("妈", Gender.FEMALE), me, RelationRole.MOTHER
        ).ok()
        val wife = repo.createRelative(
            PersonDraft("妻", Gender.FEMALE), me, RelationRole.WIFE
        ).ok()
        // 结婚纪念日（schemaVersion 7：一对夫妻一条）
        repo.setWeddingAnniversary(
            me, BirthdayValue(PartialDate(2015, 5, 20))).ok()
        // 已故爷爷（爸的父亲）+ 忌日
        val gp = repo.createRelative(
            PersonDraft("爷", Gender.MALE,
                livingStatus = io.github.xiaohaoz1.jiarenxia.core.model
                    .LivingStatus.DECEASED),
            dad, RelationRole.FATHER).ok()
        repo.setDeathAnniversary(
            gp, BirthdayValue(PartialDate(2020, 8, 25),
                io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR))
            .ok()
        repo.createRelative(
            PersonDraft("子", Gender.MALE), me, RelationRole.SON
        ).ok()
        repo.createRelative(
            PersonDraft("姑", Gender.FEMALE), me, RelationRole.SISTER
        ).ok()
        // 关闭爸的生日提醒偏好（导出后应保留 enabled=false）
        repo.setReminderEnabled(dad, false, ImportantDateType.BIRTHDAY_LUNAR)
        // “我”的生日提醒偏移改成 {0,3,7}（导出后应保留三天集）
        repo.setReminderEnabled(me, true, ImportantDateType.BIRTHDAY_SOLAR)
        repo.setReminderOffsets(me, setOf(0, 3, 7), ImportantDateType.BIRTHDAY_SOLAR)
        // 头像文件
        val avatarDir = File(ctx.filesDir, "avatars").apply { mkdirs() }
        File(avatarDir, "p1_avatar.jpg").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        db.personDao().updateAvatar(me, "avatars/p1_avatar.jpg", now)
        fakeSettings.setThemeMode(ThemeMode.DARK)
        // 家庭事项（schemaVersion 4）：一条关联“妈”，一条全家的事
        val momId = db.personDao().getAll().first { it.name == "妈" }.id
        db.familyTaskDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = "tk1", personId = momId, kind = "MEDICAL_INSURANCE",
                    title = "妈妈的居民医保缴费", anchorEpochDay = 20000,
                    repeat = "YEARLY", periodIndex = 1,
                    lastDoneEpochDay = 19990, done = false, amountFen = 38000,
                    note = "社区缴费", enabled = true,
                    createdAt = 1, updatedAt = 2
                ),
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = "tk2", personId = null, kind = "ID_EXPIRY",
                    title = "宽带到期", anchorEpochDay = 20100,
                    repeat = "NONE", periodIndex = 0, lastDoneEpochDay = null,
                    done = true, amountFen = null, note = null, enabled = false,
                    createdAt = 1, updatedAt = 1
                )
            )
        )
        db.familyTaskOffsetDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity("to1", "tk1", 7),
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity("to2", "tk1", 0),
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity("to3", "tk2", 1)
            )
        )
        fakeSettings.setTaskRemindersEnabled(false)
        db.familyTaskDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = "ptask1", personId = momId, kind = "INSURANCE_PREMIUM",
                    title = "妈的百万医疗缴费", anchorEpochDay = 20200,
                    repeat = "YEARLY", periodIndex = 0, lastDoneEpochDay = null,
                    done = false, amountFen = 68000, note = null,
                    enabled = true, createdAt = 1, updatedAt = 1
                )
            )
        )
        db.familyTaskOffsetDao().insertAll(
            listOf(15, 3, 0).mapIndexed { i, d ->
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    "ptask1-o" + i, "ptask1", d
                )
            }
        )
        return me
    }

    private suspend fun sortedAll(): Map<String, List<Any>> = mapOf<String, List<Any>>(
        "persons" to db.personDao().getAll().sortedBy { it.id },
        "phones" to db.phoneNumberDao().getAll().sortedBy { it.id },
        "relationships" to db.relationshipDao().getAll().sortedBy { it.id },
        "dates" to db.importantDateDao().getAllDates().sortedBy { it.id },
        "prefs" to db.reminderPreferenceDao().getAll().sortedBy { it.id },
        // offsets 在备份 JSON 中仅存 daysBefore，恢复时确定性重建 id → 比对按内容
        "offsets" to db.reminderOffsetDao().getAll()
            .map { it.reminderPreferenceId + ":" + it.daysBefore }.sorted(),
        "customTitles" to db.customTitleDao().getAll()
            .map { it.targetPersonId + ":" + it.title }.sorted(),
        "tasks" to db.familyTaskDao().getAll().sortedBy { it.id },
        "taskOffsets" to db.familyTaskOffsetDao().getAll()
            .map { it.taskId + ":" + it.daysBefore }.sorted()
    )

    @Test
    fun 导出恢复往返() = runTest {
        val me = seed()
        val before = sortedAll()
        val avatarBytes = File(ctx.filesDir, "avatars/p1_avatar.jpg").readBytes()

        val out = ByteArrayOutputStream()
        val summary = backup.export(out, pw)
        assertEquals(8, summary.persons)
        assertEquals(2, summary.birthdays)   // 我 + 爸（农历闰月）
        assertEquals(2, summary.anniversaries) // 纪念日 + 忌日
        assertEquals(3, summary.tasks)
        assertEquals(1, summary.avatars)

        repo.clearAllData()
        assertEquals(0, db.personDao().count())

        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
        backup.restore(plan)

        assertEquals(before, sortedAll())
        assertEquals(
            avatarBytes.toList(),
            File(ctx.filesDir, "avatars/p1_avatar.jpg").readBytes().toList()
        )
        assertEquals(ThemeMode.DARK, fakeSettings.themeMode.first())
        assertNull(fakeSettings.runState)
        // 农历闰月字段保留
        val dad = db.personDao().getAll().first { it.name == "爸" }
        val bd = db.importantDateDao().getBirthdays(dad.id).single()
        assertEquals(CalendarType.LUNAR, bd.calendarType)
        assertTrue(bd.isLeapMonth)
        // 长幼序保留
        val bro = db.personDao().getAll().first { it.name == "哥" }
        val rel = db.relationshipDao().getAll().first {
            it.sourcePersonId == bro.id || it.targetPersonId == bro.id
        }
        // 规范化存储 (我→哥, YOUNGER)：哥是年长者
        assertEquals(RelativeAgeOrder.YOUNGER, rel.relativeAgeOrder)
        // 关闭的偏好保留 enabled=false
        assertFalse(db.reminderPreferenceDao().getForDate(bd.id)!!.enabled)
        // 手机号状态/主号码保留
        val selfPhones = db.phoneNumberDao().getForPerson(me)
        assertTrue(selfPhones.single().isPrimary)
        assertEquals(
            PhoneStatus.INACTIVE,
            db.phoneNumberDao().getForPerson(dad.id).single().status
        )
    }

    @Test
    fun 恢复整体替换() = runTest {
        seed()   // 备份内容 Y
        File(ctx.filesDir, "avatars/old_only.jpg").writeBytes(byteArrayOf(9, 9))
        val out = ByteArrayOutputStream()
        backup.export(out, pw)

        // 改成另一套数据 X
        repo.clearAllData()
        File(ctx.filesDir, "avatars").deleteRecursively()
        val me2 = repo.createSelf(PersonDraft("新我", Gender.FEMALE)).ok()
        repo.createRelative(PersonDraft("新亲", Gender.MALE), me2, RelationRole.SON).ok()
        File(ctx.filesDir, "avatars").apply { mkdirs() }
        File(ctx.filesDir, "avatars/x_only.jpg").writeBytes(byteArrayOf(7, 7))

        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
        backup.restore(plan)
        // 只剩 Y 的 7 人，且“我”是原名
        val all = db.personDao().getAll()
        assertEquals(8, all.size)
        assertEquals("我", all.first { it.isSelf }.name)
        assertTrue(db.personDao().getAll().none { it.name == "新我" || it.name == "新亲" })
        // 旧头像文件已清，备份的头像回来
        assertFalse(File(ctx.filesDir, "avatars/x_only.jpg").exists())
        assertTrue(File(ctx.filesDir, "avatars/p1_avatar.jpg").exists())
    }

    @Test
    fun 校验失败_数据不动() = runTest {
        seed()
        val before = sortedAll()
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plain = BackupCrypto.decrypt(out.toByteArray(), pw)
        val plan = BackupJson.parsePlan(plain)
        // 悬空 personId → InvalidContent
        val badPhones = plan.phones + PhoneNumberEntity(
            id = "badPhone", personId = "noSuchPerson", number = "x",
            label = PhoneLabel.MOBILE, status = PhoneStatus.ACTIVE,
            isPrimary = false, note = null, createdAt = 1, updatedAt = 1
        )
        val badZip = BackupJson.writeZip(
            plan.persons, badPhones, plan.relationships, plan.importantDates,
            plan.prefs, plan.customTitles, emptyList(), settings = plan.settings, avatars = plan.avatars, createdAt = 1L, appVersion = "0.1.0"
        )
        val res = backup.inspect(
            ByteArrayInputStream(BackupCrypto.encrypt(badZip, pw, iterations = 10_000)), pw
        )
        assertTrue(res.isFailure)
        assertTrue(
            ((res.exceptionOrNull() as BackupException).error) is BackupError.InvalidContent
        )
        assertEquals(before, sortedAll())
    }

    @Test
    fun 路径穿越与双我() = runTest {
        seed()
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plain = BackupCrypto.decrypt(out.toByteArray(), pw)
        val plan = BackupJson.parsePlan(plain)
        // 路径穿越头像名 → InvalidContent
        val badPersons = plan.persons.map {
            if (it.name == "我") it.copy(avatarPath = "avatars/../evil.png") else it
        }
        val zip1 = BackupJson.writeZip(
            badPersons, plan.phones, plan.relationships, plan.importantDates,
            plan.prefs, plan.customTitles, emptyList(), settings = plan.settings, avatars = plan.avatars, createdAt = 1L, appVersion = "0.1.0"
        )
        val res1 = backup.inspect(
            ByteArrayInputStream(BackupCrypto.encrypt(zip1, pw, iterations = 10_000)), pw
        )
        assertTrue(res1.isFailure)
        assertTrue(
            (res1.exceptionOrNull() as BackupException).error is BackupError.InvalidContent
        )
        // 两个 self → InvalidContent
        val twoSelves = plan.persons.mapIndexed { i, p -> if (i < 2) p.copy(isSelf = true) else p }
        val zip2 = BackupJson.writeZip(
            twoSelves, plan.phones, plan.relationships, plan.importantDates,
            plan.prefs, plan.customTitles, emptyList(), settings = plan.settings, avatars = plan.avatars, createdAt = 1L, appVersion = "0.1.0"
        )
        val res2 = backup.inspect(
            ByteArrayInputStream(BackupCrypto.encrypt(zip2, pw, iterations = 10_000)), pw
        )
        assertTrue(res2.isFailure)
    }

    @Test
    fun 密码错误不动数据() = runTest {
        seed()
        val before = sortedAll()
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val res = backup.inspect(
            ByteArrayInputStream(out.toByteArray()), "错密码错密码".toCharArray()
        )
        assertTrue(res.isFailure)
        assertEquals(
            BackupError.WrongPasswordOrCorrupted,
            (res.exceptionOrNull() as BackupException).error
        )
        assertEquals(before, sortedAll())
    }

    @Test
    fun 自定义称呼随备份往返() = runTest {
        seed()
        val me = db.personDao().getSelf()!!.id
        val before = db.customTitleDao().getAll()
        assertEquals(1, before.size)
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plain = BackupCrypto.decrypt(out.toByteArray(), pw)
        val plan = BackupJson.parsePlan(plain)
        assertEquals(11, io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson.SCHEMA_VERSION)
        // 字号系数随设置往返（新格式为数值串）
        assertEquals("1.15", plan.settings?.fontScale)
        assertEquals(1, plan.customTitles.size)
        assertEquals("老舅", plan.customTitles[0].title)
        assertEquals(me, plan.customTitles[0].observerPersonId)
        // 恢复后内容与表快照一致（sortedAll 已含 customTitles 比对）
        fakeSettings.setFontScaleFactor(1.0f)
        backup.restore(plan)
        assertEquals(
            setOf("老舅"),
            db.customTitleDao().getAll().map { it.title }.toSet()
        )
        assertEquals(1.15f, fakeSettings.fontScaleFactor.value, 0.001f)
    }

    /** 旧版备份里的称谓叫法字段：解析保留、恢复时忽略（该设置已移除）。 */
    @Test
    fun 旧备份的称谓叫法字段兼容() = runTest {
        seed()
        val zip = BackupJson.writeZip(
            persons = listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity(
                    id = "p1", name = "我", gender = Gender.MALE,
                    avatarPath = null, note = null,
                    livingStatus =
                        io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE,
                    isSelf = true, createdAt = 1, updatedAt = 1
                )
            ),
            phones = emptyList(), relationships = emptyList(), dates = emptyList(),
            prefs = emptyList(), customTitles = emptyList(),
            settings = BackupSettings(
                themeMode = ThemeMode.LIGHT, remindersEnabled = true,
                titleStyle = "SOUTH"
            ),
            avatars = emptyMap(), createdAt = 1L, appVersion = "0.9.4"
        )
        val plan = BackupJson.parsePlan(zip)
        // 旧字段仍能解析出来（兼容旧文件），恢复流程忽略它
        assertEquals("SOUTH", plan.settings?.titleStyle)
        backup.restore(plan)
    }

    /** 三项提醒时间各自随备份往返；旧备份缺字段时保留当前设置。 */
    @Test
    fun 提醒时间随备份往返() = runTest {
        seed()
        fakeSettings.setReminderMinuteOfDay(20 * 60 + 30)
        fakeSettings.setTaskReminderMinuteOfDay(8 * 60 + 15)
        fakeSettings.setContactReminderMinuteOfDay(19 * 60)
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
        assertEquals(20 * 60 + 30, plan.settings?.reminderMinuteOfDay)
        assertEquals(8 * 60 + 15, plan.settings?.taskReminderMinuteOfDay)
        assertEquals(19 * 60, plan.settings?.contactReminderMinuteOfDay)

        fakeSettings.setReminderMinuteOfDay(540)
        fakeSettings.setTaskReminderMinuteOfDay(540)
        fakeSettings.setContactReminderMinuteOfDay(540)
        backup.restore(plan)
        assertEquals(20 * 60 + 30, fakeSettings.reminderMinuteOfDay.first())
        assertEquals(8 * 60 + 15, fakeSettings.taskReminderMinuteOfDay.first())
        assertEquals(19 * 60, fakeSettings.contactReminderMinuteOfDay.first())

        // 缺字段（旧备份）→ null，恢复时保留当前时间
        val legacy = plan.copy(
            settings = plan.settings?.copy(
                reminderMinuteOfDay = null,
                taskReminderMinuteOfDay = null,
                contactReminderMinuteOfDay = null
            )
        )
        fakeSettings.setReminderMinuteOfDay(7 * 60)
        fakeSettings.setTaskReminderMinuteOfDay(6 * 60)
        fakeSettings.setContactReminderMinuteOfDay(22 * 60)
        backup.restore(legacy)
        assertEquals(7 * 60, fakeSettings.reminderMinuteOfDay.first())
        assertEquals(6 * 60, fakeSettings.taskReminderMinuteOfDay.first())
        assertEquals(22 * 60, fakeSettings.contactReminderMinuteOfDay.first())
    }

    @Test
    fun 非法自定义称呼拒收() = runTest {
        seed()
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plain = BackupCrypto.decrypt(out.toByteArray(), pw)
        val plan = BackupJson.parsePlan(plain)
        // 超过 12 字的称呼 → InvalidContent
        val badTitles = plan.customTitles +
            io.github.xiaohaoz1.jiarenxia.data.db.CustomTitleEntity(
                id = "bad", observerPersonId = plan.persons.first { it.isSelf }.id,
                targetPersonId = plan.persons.first { !it.isSelf }.id,
                title = "一二三四五六七八九十十一十二十三",
                createdAt = 1, updatedAt = 1
            )
        val zip = BackupJson.writeZip(
            plan.persons, plan.phones, plan.relationships, plan.importantDates,
            plan.prefs, badTitles, emptyList(), settings = plan.settings, avatars = plan.avatars, createdAt = 1L, appVersion = "0.1.0"
        )
        val res = backup.inspect(
            ByteArrayInputStream(BackupCrypto.encrypt(zip, pw, iterations = 10_000)), pw
        )
        assertTrue(res.isFailure)
        assertTrue(
            (res.exceptionOrNull() as BackupException).error is BackupError.InvalidContent
        )
        // 悬空引用 → InvalidContent
        val dangle = plan.customTitles +
            io.github.xiaohaoz1.jiarenxia.data.db.CustomTitleEntity(
                id = "bad2", observerPersonId = plan.persons.first { it.isSelf }.id,
                targetPersonId = "noSuch", title = "大伯", createdAt = 1, updatedAt = 1
            )
        val zip2 = BackupJson.writeZip(
            plan.persons, plan.phones, plan.relationships, plan.importantDates,
            plan.prefs, dangle, emptyList(), settings = plan.settings, avatars = plan.avatars, createdAt = 1L, appVersion = "0.1.0"
        )
        val res2 = backup.inspect(
            ByteArrayInputStream(BackupCrypto.encrypt(zip2, pw, iterations = 10_000)), pw
        )
        assertTrue(res2.isFailure)
    }

    // ---------- 家庭事项（schemaVersion 4）----------

    @Test
    fun 事项与开关往返() = runTest {
        seed()
        val before = sortedAll()
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertEquals(3, plan.summary.tasks)
        // settings 里有 taskRemindersEnabled=false
        assertEquals(false, plan.settings?.taskRemindersEnabled)
        fakeSettings.setTaskRemindersEnabled(true)
        backup.restore(plan)
        assertEquals(before, sortedAll())
        assertEquals(false, fakeSettings.taskRemindersEnabled.value)
    }

    @Test
    fun 旧备份无事项字段_事项清空_开关保留() = runTest {
        seed()
        // 手写一份“v3 风格”备份：无 familyTasks、settings 缺 taskRemindersEnabled
        val plan = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(
                    BackupJson.writeZip(
                        persons = db.personDao().getAll(),
                        phones = db.phoneNumberDao().getAll(),
                        relationships = db.relationshipDao().getAll(),
                        dates = db.importantDateDao().getAllDates(),
                        prefs = db.reminderPreferenceDao().getAll().map { p ->
                            p to db.reminderOffsetDao().getForPreference(p.id)
                        },
                        customTitles = emptyList(),
                        tasks = emptyList(),
                        settings = BackupSettings(
                            themeMode = ThemeMode.LIGHT,
                            remindersEnabled = true,
                            taskRemindersEnabled = null
                        ),
                        avatars = emptyMap(),
                        createdAt = 1L, appVersion = "0.4.0"
                    ), pw, iterations = 10_000
                )
            ), pw
        ).getOrThrow()
        fakeSettings.setTaskRemindersEnabled(false)
        backup.restore(plan)
        assertEquals(0, db.familyTaskDao().count())
        // 旧备份缺字段 → 保留当前设置（false）
        assertEquals(false, fakeSettings.taskRemindersEnabled.value)
    }

    @Test
    fun 畸形事项备份被校验拦截() = runTest {
        seed()
        val badTask = io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
            id = "bad", personId = "ghost", kind = "CHECKUP", title = "x",
            anchorEpochDay = 1, repeat = "NONE", periodIndex = 0,
            lastDoneEpochDay = null, done = false, amountFen = null,
            note = null, enabled = true,
            createdAt = 1, updatedAt = 1
        )
        val zip = BackupJson.writeZip(
            persons = db.personDao().getAll(),
            phones = db.phoneNumberDao().getAll(),
            relationships = db.relationshipDao().getAll(),
            dates = db.importantDateDao().getBirthdays(),
            prefs = emptyList(), customTitles = emptyList(),
            tasks = listOf(
                badTask to listOf(
                    io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity("o", "bad", 0)
                )
            ),
            settings = null, avatars = emptyMap(), createdAt = 1L, appVersion = "0.4.0"
        )
        val res = backup.inspect(
            ByteArrayInputStream(BackupCrypto.encrypt(zip, pw, iterations = 10_000)), pw
        )
        assertTrue(res.isFailure)
    }

    @Test
    fun 旧备份的退出字段被忽略() = runTest {
        // A6：人情/保单彻底退出。含 giftEvents/giftRecords/policies/
        // task.policyId/模块开关的旧备份必须照常恢复家人与事项，
        // 退出字段（哪怕是畸形内容）不再解析、不再校验。
        seed()
        val base = BackupJson.writeZip(
            persons = db.personDao().getAll(),
            phones = db.phoneNumberDao().getAll(),
            relationships = db.relationshipDao().getAll(),
            dates = db.importantDateDao().getAllDates(),
            prefs = emptyList(), customTitles = emptyList(),
            tasks = db.familyTaskDao().getAll().map { t ->
                t to db.familyTaskOffsetDao().getForTask(t.id)
            },
            settings = BackupSettings(
                themeMode = ThemeMode.LIGHT, remindersEnabled = true),
            avatars = emptyMap(),
            createdAt = 1L, appVersion = "1.2.0"
        )
        // 解开 zip → 往 backup.json 塞回旧字段（内容故意畸形：悬空引用）
        val files = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(base)).use { z ->
            var e = z.nextEntry
            while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
        }
        val root = org.json.JSONObject(files["backup.json"]!!.decodeToString())
        root.getJSONArray("familyTasks").getJSONObject(0).put("policyId", "ghost")
        root.put("giftEvents", org.json.JSONArray().put(org.json.JSONObject()
            .put("id", "ge9").put("occasion", "NOT_A_REAL_KIND")
            .put("title", "x").put("date", "not-a-date")))
        root.put("giftRecords", org.json.JSONArray().put(org.json.JSONObject()
            .put("id", "gr9").put("direction", "???").put("counterpartName", "")))
        root.put("policies", org.json.JSONArray().put(org.json.JSONObject()
            .put("id", "pol9").put("insuredPersonId", "nobody")))
        root.getJSONObject("settings").put("giftModuleEnabled", false)
            .put("insuranceModuleEnabled", false)
        files["backup.json"] = root.toString().encodeToByteArray()
        val out2 = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out2).use { z ->
            files.forEach { (name, bytes) ->
                if (!name.endsWith("/")) {
                    z.putNextEntry(java.util.zip.ZipEntry(name))
                    z.write(bytes); z.closeEntry()
                }
            }
        }
        val res = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(out2.toByteArray(), pw, iterations = 10_000)
            ), pw
        )
        if (res.isFailure) {
            val e = res.exceptionOrNull() as? BackupException
            if (e?.error is BackupError.InvalidContent) {
                throw AssertionError(
                    "InvalidContent: " + (e.error as BackupError.InvalidContent).reason
                )
            }
            throw res.exceptionOrNull()!!
        }
        val plan = res.getOrThrow()
        backup.restore(plan)
        // 家人与事项完整恢复，退出数据一律未落库
        assertEquals(3, db.familyTaskDao().count())
        assertEquals(8, db.personDao().count())
    }

    @Test
    fun 导出不再写licenseCode且旧备份恢复时忽略该字段() = runTest {
        seed()
        // 会员功能已下线：licenseCode 仅为兼容旧备份保留的字段。
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertNull(plan.settings?.licenseCode)
        // 旧备份里带 licenseCode：恢复照常完成、字段被忽略
        val legacyPlan = plan.copy(
            settings = plan.settings?.copy(licenseCode = "JRX1LEGACY")
        )
        backup.restore(legacyPlan)
    }

    @Test
    fun 联系记录与周期随备份往返() = runTest {
        seed()
        val momId = db.personDao().getAll().first { it.name == "妈" }.id
        db.contactPlanDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity(
                    personId = momId, intervalDays = 14,
                    createdAt = 1, updatedAt = 2
                )
            )
        )
        db.contactLogDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity(
                    id = "cl1", personId = momId, atMillis = 1000,
                    method = "CALL", note = "聊了会儿", createdAt = 3
                )
            )
        )
        val plansBefore = db.contactPlanDao().getAll()
        val logsBefore = db.contactLogDao().getAll()

        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        repo.clearAllData()
        assertTrue(db.contactPlanDao().getAll().isEmpty())
        assertTrue(db.contactLogDao().getAll().isEmpty())

        val plan = backup.inspect(
            ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
        backup.restore(plan)
        // 逐字段比对（实体为 data class）
        assertEquals(plansBefore, db.contactPlanDao().getAll())
        assertEquals(logsBefore, db.contactLogDao().getAll())
    }

    @Test
    fun 共享导出不带联系数据() = runTest {
        val me = seed()
        val momId = db.personDao().getAll().first { it.name == "妈" }.id
        db.contactPlanDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity(
                    personId = momId, intervalDays = 7,
                    createdAt = 1, updatedAt = 1
                )
            )
        )
        db.contactLogDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity(
                    id = "cl1", personId = momId, atMillis = 1000,
                    method = "VISIT", note = null, createdAt = 1
                )
            )
        )
        val out = ByteArrayOutputStream()
        backup.exportShare(out, pw, sharedBy = me)
        val plan = backup.inspect(
            ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
        // 联系记录与周期是私密数据，不进共享文件
        assertTrue(plan.contactLogs.isEmpty())
        assertTrue(plan.contactPlans.isEmpty())
    }
}
