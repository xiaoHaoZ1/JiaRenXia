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
import io.github.xiaohaoz1.jiarenxia.data.db.PersonFactEntity
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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
        override val tombRemindersEnabled = kotlinx.coroutines.flow.MutableStateFlow(true)
        override suspend fun setTombRemindersEnabled(enabled: Boolean) {
            tombRemindersEnabled.value = enabled
        }
        override val tombReminderMinuteOfDay = kotlinx.coroutines.flow.MutableStateFlow(540)
        override suspend fun setTombReminderMinuteOfDay(minute: Int) {
            tombReminderMinuteOfDay.value = minute
        }
        override val tombDays = kotlinx.coroutines.flow.MutableStateFlow(
            io.github.xiaohaoz1.jiarenxia.domain.tomb.TombPrefs.DEFAULT_DAYS)
        override suspend fun setTombDays(
            days: Set<io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay>
        ) {
            tombDays.value = days
        }
        override val tombZhongyuanDay = kotlinx.coroutines.flow.MutableStateFlow(15)
        override suspend fun setTombZhongyuanDay(day: Int) {
            tombZhongyuanDay.value = day
        }
        override val tombExcludedPersonIds =
            kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>())
        override suspend fun setTombExcludedPersonIds(ids: Set<String>) {
            tombExcludedPersonIds.value = ids
        }
        override val tombIncludedPersonIds =
            kotlinx.coroutines.flow.MutableStateFlow(emptySet<String>())
        override suspend fun setTombIncludedPersonIds(ids: Set<String>) {
            tombIncludedPersonIds.value = ids
        }
        override suspend fun setTombMournerOn(personId: String, on: Boolean) {
            if (on) {
                tombIncludedPersonIds.value += personId
                tombExcludedPersonIds.value -= personId
            } else {
                tombExcludedPersonIds.value += personId
                tombIncludedPersonIds.value -= personId
            }
        }
        override val homeVisitHolidayNudge =
            kotlinx.coroutines.flow.MutableStateFlow(true)
        override suspend fun setHomeVisitHolidayNudge(enabled: Boolean) {
            homeVisitHolidayNudge.value = enabled
        }
        override val elderMode = kotlinx.coroutines.flow.MutableStateFlow(false)
        override suspend fun setElderMode(enabled: Boolean) {
            elderMode.value = enabled
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

    /** 长辈模式是本机偏好：开启状态也不进备份，恢复也不改动它。 */
    @Test
    fun 长辈模式不进备份() = runTest {
        seed()
        fakeSettings.setElderMode(true)
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plain = BackupCrypto.decrypt(out.toByteArray(), pw)

        // 直接检查 backup.json：settings 与顶层都不得出现 elder 相关键
        val files = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(plain)).use { z ->
            var e = z.nextEntry
            while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
        }
        val root = org.json.JSONObject(files["backup.json"]!!.decodeToString())
        val settingsObj = root.getJSONObject("settings")
        val keys = settingsObj.keys().asSequence().toList() +
            root.keys().asSequence().toList()
        assertTrue(
            "备份 JSON 不应包含 elder 相关键: " +
                keys.filter { it.contains("elder", ignoreCase = true) },
            keys.none { it.contains("elder", ignoreCase = true) }
        )

        // 恢复流程同样不触碰 elderMode（BackupSettings 无此字段）
        val plan = BackupJson.parsePlan(plain)
        backup.restore(plan)
        assertEquals(true, fakeSettings.elderMode.first())
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

    @Test
    fun 关于TA随备份往返() = runTest {
        seed()
        val momId = db.personDao().getAll().first { it.name == "妈" }.id
        val before = listOf(
            PersonFactEntity("f1", momId, "SIZE", "鞋码", "37", 20_000, 1, 2),
            PersonFactEntity("f2", momId, "HOBBY", null, "广场舞", 20_001, 3, 4)
        )
        db.personFactDao().insertAll(before)
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        repo.clearAllData()
        assertTrue(db.personFactDao().getAll().isEmpty())

        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
        backup.restore(plan)
        assertEquals(before, db.personFactDao().getAll())
    }

    @Test
    fun 共享导出不带关于TA() = runTest {
        val me = seed()
        val momId = db.personDao().getAll().first { it.name == "妈" }.id
        db.personFactDao().insert(
            PersonFactEntity("f1", momId, "HOBBY", null, "广场舞", 1, 1, 1)
        )
        val out = ByteArrayOutputStream()
        backup.exportShare(out, pw, sharedBy = me)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw).getOrThrow()
        assertTrue(plan.personFacts.isEmpty())
    }

    @Test
    fun 旧备份无personFacts键恢复为空() = runTest {
        seed()
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plain = BackupCrypto.decrypt(out.toByteArray(), pw)
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes()
            }
        }
        val root = org.json.JSONObject(files.getValue("backup.json").decodeToString())
        root.remove("personFacts")
        files["backup.json"] = root.toString().encodeToByteArray()
        val rewritten = ByteArrayOutputStream()
        ZipOutputStream(rewritten).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        val encrypted = BackupCrypto.encrypt(rewritten.toByteArray(), pw)
        val plan = backup.inspect(ByteArrayInputStream(encrypted), pw).getOrThrow()
        assertTrue(plan.personFacts.isEmpty())
        backup.restore(plan)
        assertTrue(db.personFactDao().getAll().isEmpty())
    }

    @Test
    fun personFacts校验() = runTest {
        seed()
        val exported = ByteArrayOutputStream()
        backup.export(exported, pw)
        val base = backup.inspect(ByteArrayInputStream(exported.toByteArray()), pw).getOrThrow()
        val personId = base.persons.first().id
        suspend fun inspectFact(fact: PersonFactEntity) = backup.inspect(
            ByteArrayInputStream(encryptedPlan(base, listOf(fact))), pw
        )
        fun assertInvalid(result: Result<RestorePlan>) {
            assertTrue(result.isFailure)
            assertTrue(
                (result.exceptionOrNull() as BackupException).error is
                    BackupError.InvalidContent
            )
        }
        val valid = PersonFactEntity("f", personId, "FUTURE_X", null, "值", 1, 1, 1)
        assertInvalid(inspectFact(valid.copy(personId = "missing")))
        assertInvalid(inspectFact(valid.copy(content = "甲".repeat(101))))
        assertInvalid(inspectFact(valid.copy(label = "甲".repeat(21))))
        val future = inspectFact(valid).getOrThrow().personFacts.single()
        assertEquals("FUTURE_X", future.category)
    }

    // ---------- 上坟提醒设置（FAMILY_AFFAIRS 19） ----------

    @Test
    fun 上坟设置随备份往返() = runTest {
        seed()
        fakeSettings.setTombRemindersEnabled(false)
        fakeSettings.setTombReminderMinuteOfDay(19 * 60 + 30)
        fakeSettings.setTombDays(
            setOf(
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.QINGMING,
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.MOURNING))
        fakeSettings.setTombZhongyuanDay(14)
        fakeSettings.setTombExcludedPersonIds(setOf("p1", "p2"))
        fakeSettings.setTombIncludedPersonIds(setOf("p7"))
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertEquals(false, plan.settings?.tombRemindersEnabled)
        assertEquals(19 * 60 + 30, plan.settings?.tombReminderMinuteOfDay)
        assertEquals(setOf("MOURNING", "QINGMING"), plan.settings?.tombDays)
        assertEquals(14, plan.settings?.tombZhongyuanDay)
        assertEquals(setOf("p1", "p2"), plan.settings?.tombExcludedPersonIds)
        assertEquals(setOf("p7"), plan.settings?.tombIncludedPersonIds)

        // 恢复前把本机设置改成别的值，验证确实被备份值覆盖
        fakeSettings.setTombRemindersEnabled(true)
        fakeSettings.setTombReminderMinuteOfDay(540)
        fakeSettings.setTombDays(
            io.github.xiaohaoz1.jiarenxia.domain.tomb.TombPrefs.DEFAULT_DAYS)
        fakeSettings.setTombZhongyuanDay(15)
        fakeSettings.setTombExcludedPersonIds(emptySet())
        fakeSettings.setTombIncludedPersonIds(emptySet())
        backup.restore(plan)
        assertEquals(false, fakeSettings.tombRemindersEnabled.first())
        assertEquals(19 * 60 + 30, fakeSettings.tombReminderMinuteOfDay.first())
        assertEquals(
            setOf(
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.QINGMING,
                io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.MOURNING),
            fakeSettings.tombDays.first())
        assertEquals(14, fakeSettings.tombZhongyuanDay.first())
        assertEquals(setOf("p1", "p2"), fakeSettings.tombExcludedPersonIds.first())
        assertEquals(setOf("p7"), fakeSettings.tombIncludedPersonIds.first())
    }

    @Test
    fun 旧备份缺上坟字段_保留当前设置() = runTest {
        seed()
        // 手写“旧版”备份：settings 只有 themeMode/remindersEnabled，
        // 五个上坟键写成 null（等价于旧文件里根本没有这些键）
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
                            remindersEnabled = true
                        ),
                        avatars = emptyMap(),
                        createdAt = 1L, appVersion = "0.4.0"
                    ), pw, iterations = 10_000
                )
            ), pw
        ).getOrThrow()
        // 缺字段 → null（不是默认值），恢复时保留当前设置
        assertNull(plan.settings?.tombRemindersEnabled)
        assertNull(plan.settings?.tombReminderMinuteOfDay)
        assertNull(plan.settings?.tombDays)
        assertNull(plan.settings?.tombZhongyuanDay)
        assertNull(plan.settings?.tombExcludedPersonIds)
        assertNull(plan.settings?.tombIncludedPersonIds)

        fakeSettings.setTombRemindersEnabled(false)
        fakeSettings.setTombReminderMinuteOfDay(19 * 60)
        fakeSettings.setTombDays(
            setOf(io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.CHUXI))
        fakeSettings.setTombZhongyuanDay(14)
        fakeSettings.setTombExcludedPersonIds(setOf("p9"))
        fakeSettings.setTombIncludedPersonIds(setOf("p8"))
        backup.restore(plan)
        assertEquals(false, fakeSettings.tombRemindersEnabled.first())
        assertEquals(19 * 60, fakeSettings.tombReminderMinuteOfDay.first())
        assertEquals(
            setOf(io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.CHUXI),
            fakeSettings.tombDays.first())
        assertEquals(14, fakeSettings.tombZhongyuanDay.first())
        assertEquals(setOf("p9"), fakeSettings.tombExcludedPersonIds.first())
        assertEquals(setOf("p8"), fakeSettings.tombIncludedPersonIds.first())
    }

    @Test
    fun 上坟设置非法值_按缺失处理() = runTest {
        seed()
        // 时刻越界 / 中元非法值 → 解析为 null（恢复保留当前）；
        // 日子集合里的未知枚举名原样带出、恢复时丢弃
        val plan = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(
                    BackupJson.writeZip(
                        persons = db.personDao().getAll(),
                        phones = db.phoneNumberDao().getAll(),
                        relationships = db.relationshipDao().getAll(),
                        dates = db.importantDateDao().getAllDates(),
                        prefs = emptyList(),
                        customTitles = emptyList(),
                        tasks = emptyList(),
                        settings = BackupSettings(
                            themeMode = ThemeMode.LIGHT,
                            remindersEnabled = true,
                            tombReminderMinuteOfDay = 2000,
                            tombZhongyuanDay = 16,
                            tombDays = setOf("QINGMING", "NOT_A_DAY")
                        ),
                        avatars = emptyMap(),
                        createdAt = 1L, appVersion = "0.4.0"
                    ), pw, iterations = 10_000
                )
            ), pw
        ).getOrThrow()
        assertNull(plan.settings?.tombReminderMinuteOfDay)
        assertNull(plan.settings?.tombZhongyuanDay)
        assertEquals(setOf("QINGMING", "NOT_A_DAY"), plan.settings?.tombDays)

        fakeSettings.setTombReminderMinuteOfDay(19 * 60)
        fakeSettings.setTombZhongyuanDay(14)
        fakeSettings.setTombDays(
            setOf(io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.HANYI))
        backup.restore(plan)
        assertEquals(19 * 60, fakeSettings.tombReminderMinuteOfDay.first())
        assertEquals(14, fakeSettings.tombZhongyuanDay.first())
        // 未知枚举名被丢弃，只剩 QINGMING
        assertEquals(
            setOf(io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay.QINGMING),
            fakeSettings.tombDays.first())
    }

    // ---------- 回家看看（FAMILY_AFFAIRS 20） ----------

    /** 造两个“家”：爸妈家（dad+mom 成员）、岳父母家（仅 mom 一人）。 */
    private suspend fun seedHomes(): Triple<String, String, String> {
        val me = db.personDao().getAll().first { it.isSelf }.id
        val dad = db.personDao().getAll().first { it.name == "爸" }.id
        val mom = db.personDao().getAll().first { it.name == "妈" }.id
        db.homeVisitPlanDao().insert(
            io.github.xiaohaoz1.jiarenxia.data.db.HomeVisitPlanEntity(
                "hv1", 90, 1000, 2000))
        db.homeVisitPlanDao().insert(
            io.github.xiaohaoz1.jiarenxia.data.db.HomeVisitPlanEntity(
                "hv2", 180, 3000, 3000))
        db.homeVisitMemberDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.HomeVisitMemberEntity(
                    "hv1", dad),
                io.github.xiaohaoz1.jiarenxia.data.db.HomeVisitMemberEntity(
                    "hv1", mom),
                io.github.xiaohaoz1.jiarenxia.data.db.HomeVisitMemberEntity(
                    "hv2", mom)
            )
        )
        return Triple(me, dad, mom)
    }

    @Test
    fun 回家看看计划与节前开关随备份往返() = runTest {
        seed()
        val (me, dad, mom) = seedHomes()
        fakeSettings.setHomeVisitHolidayNudge(false)
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        // 计划字段 + 成员 id 原样带出
        assertEquals(2, plan.homeVisits.size)
        val hv1 = plan.homeVisits.first { it.id == "hv1" }
        assertEquals(90, hv1.intervalDays)
        assertEquals(1000L, hv1.createdAt)
        assertEquals(2000L, hv1.updatedAt)
        assertEquals(setOf(dad, mom), hv1.memberIds.toSet())
        assertEquals(listOf(mom),
            plan.homeVisits.first { it.id == "hv2" }.memberIds)
        assertEquals(false, plan.settings?.homeVisitHolidayNudge)
        // “我”不应成为任何成员（seedHomes 没选他）
        assertTrue(plan.homeVisits.all { me !in it.memberIds })

        // 恢复前清空，验证写回
        repo.clearAllData()
        assertTrue(db.homeVisitPlanDao().getAll().isEmpty())
        backup.restore(plan)
        val plans = db.homeVisitPlanDao().getAll()
        assertEquals(2, plans.size)
        val members = db.homeVisitMemberDao().getAll()
        assertEquals(3, members.size)
        assertEquals(setOf("hv1", "hv2"), members.map { it.planId }.toSet())
        assertEquals(setOf(dad, mom),
            members.filter { it.planId == "hv1" }.map { it.personId }.toSet())
        assertEquals(false, fakeSettings.homeVisitHolidayNudge.first())
    }

    @Test
    fun 旧备份缺回家看看键_解析为空且保留当前设置() = runTest {
        seed()
        // 手写“旧版”备份：不传 homeVisits（键缺失）+ settings 无 nudge 字段
        val plan = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(
                    BackupJson.writeZip(
                        persons = db.personDao().getAll(),
                        phones = db.phoneNumberDao().getAll(),
                        relationships = db.relationshipDao().getAll(),
                        dates = db.importantDateDao().getAllDates(),
                        prefs = emptyList(),
                        customTitles = emptyList(),
                        tasks = emptyList(),
                        settings = BackupSettings(
                            themeMode = ThemeMode.LIGHT,
                            remindersEnabled = true
                        ),
                        avatars = emptyMap(),
                        createdAt = 1L, appVersion = "0.4.0"
                    ), pw, iterations = 10_000
                )
            ), pw
        ).getOrThrow()
        assertTrue(plan.homeVisits.isEmpty())
        assertNull(plan.settings?.homeVisitHolidayNudge)

        fakeSettings.setHomeVisitHolidayNudge(false)
        backup.restore(plan)
        assertTrue(db.homeVisitPlanDao().getAll().isEmpty())
        // 缺字段 → 保留当前（false 不被覆盖成默认 true）
        assertEquals(false, fakeSettings.homeVisitHolidayNudge.first())
    }

    @Test
    fun 恢复时丢弃指向不存在人物的成员() = runTest {
        seed()
        val mom = db.personDao().getAll().first { it.name == "妈" }.id
        // 手写备份：成员含一个备份里不存在的人物 id
        val bytes = BackupCrypto.encrypt(
            BackupJson.writeZip(
                persons = db.personDao().getAll(),
                phones = db.phoneNumberDao().getAll(),
                relationships = db.relationshipDao().getAll(),
                dates = db.importantDateDao().getAllDates(),
                prefs = emptyList(),
                customTitles = emptyList(),
                tasks = emptyList(),
                homeVisits = listOf(
                    HomeVisitBackup(
                        id = "hvX", intervalDays = 60,
                        createdAt = 1, updatedAt = 2,
                        memberIds = listOf(mom, "ghost-person"))
                ),
                settings = null,
                avatars = emptyMap(),
                createdAt = 1L, appVersion = "test"
            ), pw, iterations = 10_000
        )
        val plan = backup.inspect(ByteArrayInputStream(bytes), pw).getOrThrow()
        // 解析层不校验成员存在性（规格：恢复时丢弃）
        assertEquals(listOf(mom, "ghost-person"), plan.homeVisits.single().memberIds)

        repo.clearAllData()
        backup.restore(plan)
        val members = db.homeVisitMemberDao().getAll()
        assertEquals(listOf(mom), members.map { it.personId })
        assertEquals(1, db.homeVisitPlanDao().getAll().size)
    }

    @Test
    fun 家人共享导出不含回家看看() = runTest {
        seed()
        seedHomes()
        val me = db.personDao().getAll().first { it.isSelf }.id
        val out = ByteArrayOutputStream()
        backup.exportShare(out, pw, sharedBy = me)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertTrue(plan.homeVisits.isEmpty())
        // 私密备份导出仍带
        val priv = ByteArrayOutputStream()
        backup.export(priv, pw)
        val privPlan = backup.inspect(
            ByteArrayInputStream(priv.toByteArray()), pw).getOrThrow()
        assertEquals(2, privPlan.homeVisits.size)
    }

    // ---------- 孕期记录（v20，FAMILY_AFFAIRS 26） ----------

    /** 造一条孕期 + 宝宝关联 + 两条产检事项（未归档/已归档）。 */
    private suspend fun seedPregnancy(): Triple<String, String, String> {
        val mom = db.personDao().getAll().first { it.name == "妈" }
        val baby = db.personDao().getAll().first { it.name == "子" }
        val pregId = "pg1"
        db.pregnancyDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity(
                    id = pregId, personId = mom.id,
                    dueEpochDay = 20300, lmpEpochDay = 20020,
                    status = "ACTIVE", endedEpochDay = null,
                    createdAt = 5, updatedAt = 6
                ),
                // 姑的已结束孕期（覆盖 endedEpochDay 与 BORN 之外的状态）
                io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity(
                    id = "pg2",
                    personId = db.personDao().getAll().first { it.name == "姑" }.id,
                    dueEpochDay = 19900, lmpEpochDay = null,
                    status = "ENDED", endedEpochDay = 19850,
                    createdAt = 7, updatedAt = 8
                )
            )
        )
        db.pregnancyBabyDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.PregnancyBabyEntity(
                    pregId, baby.id)
            )
        )
        db.familyTaskDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = "pren1", personId = mom.id, kind = "CHECKUP",
                    title = "妈的产检", anchorEpochDay = 20250,
                    repeat = "NONE", periodIndex = 0, lastDoneEpochDay = null,
                    done = false, amountFen = null, note = null, enabled = true,
                    pregnancyId = pregId, archived = false,
                    createdAt = 1, updatedAt = 1
                ),
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = "pren2", personId = mom.id, kind = "CHECKUP",
                    title = "妈的产检（已归档）", anchorEpochDay = 20100,
                    repeat = "NONE", periodIndex = 0, lastDoneEpochDay = null,
                    done = false, amountFen = null, note = null, enabled = true,
                    pregnancyId = pregId, archived = true,
                    createdAt = 1, updatedAt = 1
                )
            )
        )
        db.familyTaskOffsetDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    "pren1-o0", "pren1", 1),
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    "pren1-o1", "pren1", 0),
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    "pren2-o0", "pren2", 0)
            )
        )
        return Triple(pregId, mom.id, baby.id)
    }

    @Test
    fun 孕期记录与产检事项往返() = runTest {
        seed()
        val (pregId, momId, babyId) = seedPregnancy()
        val beforePregs = db.pregnancyDao().getAll().sortedBy { it.id }
        val beforeBabies = db.pregnancyBabyDao().getAll().sortedBy {
            it.pregnancyId + it.babyPersonId
        }
        val beforeTasks = db.familyTaskDao().getAll()
            .filter { it.pregnancyId != null }.sortedBy { it.id }

        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        repo.clearAllData()
        assertEquals(0, db.pregnancyDao().getAll().size)

        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertEquals(2, plan.pregnancies.size)
        backup.restore(plan)

        // 孕期全字段 + 宝宝关联 + 产检事项（pregnancyId/archived）原样回来
        assertEquals(beforePregs, db.pregnancyDao().getAll().sortedBy { it.id })
        assertEquals(beforeBabies, db.pregnancyBabyDao().getAll()
            .sortedBy { it.pregnancyId + it.babyPersonId })
        assertEquals(beforeTasks, db.familyTaskDao().getAll()
            .filter { it.pregnancyId != null }.sortedBy { it.id })
        assertEquals(momId, db.pregnancyDao().getAll()
            .first { it.id == pregId }.personId)
        assertEquals(babyId, db.pregnancyBabyDao().getAll()
            .single { it.pregnancyId == pregId }.babyPersonId)
    }

    @Test
    fun 旧备份无孕期键_按空恢复() = runTest {
        seed()
        seedPregnancy()
        // 手写“v20 之前”备份：解开 zip 删掉 pregnancies 键、
        // 事项上的 pregnancyId/archived 键（模拟旧版本写出的文件）
        val base = BackupJson.writeZip(
            persons = db.personDao().getAll(),
            phones = db.phoneNumberDao().getAll(),
            relationships = db.relationshipDao().getAll(),
            dates = db.importantDateDao().getAllDates(),
            prefs = emptyList(), customTitles = emptyList(),
            tasks = db.familyTaskDao().getAll().map { t ->
                t to db.familyTaskOffsetDao().getForTask(t.id)
            },
            settings = null, avatars = emptyMap(),
            createdAt = 1L, appVersion = "0.9.0"
        )
        val files = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(base)).use { z ->
            var e = z.nextEntry
            while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
        }
        val root = org.json.JSONObject(files["backup.json"]!!.decodeToString())
        root.remove("pregnancies")
        val taskArr = root.getJSONArray("familyTasks")
        (0 until taskArr.length()).forEach { i ->
            val o = taskArr.getJSONObject(i)
            o.remove("pregnancyId")
            o.remove("archived")
        }
        files["backup.json"] = root.toString().encodeToByteArray()
        val out2 = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out2).use { z ->
            files.forEach { (n, b) ->
                z.putNextEntry(java.util.zip.ZipEntry(n))
                z.write(b)
                z.closeEntry()
            }
        }
        val plan = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(out2.toByteArray(), pw, iterations = 10_000)),
            pw
        ).getOrThrow()
        assertTrue(plan.pregnancies.isEmpty())
        backup.restore(plan)
        assertTrue(db.pregnancyDao().getAll().isEmpty())
        assertTrue(db.pregnancyBabyDao().getAll().isEmpty())
        // 缺键的事项按 null/false 恢复
        db.familyTaskDao().getAll().forEach {
            assertNull(it.pregnancyId)
            assertFalse(it.archived)
        }
    }

    @Test
    fun 孕期备份校验_非法状态与悬空引用() = runTest {
        seed()
        val persons = db.personDao().getAll()
        val mom = persons.first { it.name == "妈" }
        val offsets = listOf(
            io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                "o1", "t1", 0)
        )
        val baseTasks = listOf(
            io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                id = "t1", personId = mom.id, kind = "CHECKUP", title = "产检",
                anchorEpochDay = 20250, repeat = "NONE", periodIndex = 0,
                lastDoneEpochDay = null, done = false, amountFen = null,
                note = null, enabled = true, pregnancyId = "pgA",
                archived = false, createdAt = 1, updatedAt = 1
            ) to offsets
        )
        suspend fun zipWith(rootMutate: (org.json.JSONObject) -> Unit): ByteArray {
            val zip = BackupJson.writeZip(
                persons = persons,
                phones = db.phoneNumberDao().getAll(),
                relationships = db.relationshipDao().getAll(),
                dates = db.importantDateDao().getAllDates(),
                prefs = emptyList(), customTitles = emptyList(),
                tasks = baseTasks,
                settings = null, avatars = emptyMap(),
                createdAt = 1L, appVersion = "0.9.0",
                pregnancies = listOf(
                    io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity(
                        id = "pgA", personId = mom.id, dueEpochDay = 20300,
                        lmpEpochDay = null, status = "ACTIVE",
                        endedEpochDay = null, createdAt = 1, updatedAt = 1
                    ) to listOf(
                        // babyIds 指向不存在人物 → 恢复时丢弃
                        io.github.xiaohaoz1.jiarenxia.data.db.PregnancyBabyEntity(
                            "pgA", "ghost-baby")
                    ),
                    // personId 悬空 → 整份失败
                    io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity(
                        id = "pgB", personId = "ghost-person",
                        dueEpochDay = 20300, lmpEpochDay = null,
                        status = "ENDED", endedEpochDay = 20200,
                        createdAt = 1, updatedAt = 1
                    ) to emptyList()
                )
            )
            val files = mutableMapOf<String, ByteArray>()
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zip)).use { z ->
                var e = z.nextEntry
                while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
            }
            val root = org.json.JSONObject(files["backup.json"]!!.decodeToString())
            rootMutate(root)
            files["backup.json"] = root.toString().encodeToByteArray()
            val out = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { z ->
                files.forEach { (n, b) ->
                    z.putNextEntry(java.util.zip.ZipEntry(n))
                    z.write(b)
                    z.closeEntry()
                }
            }
            return out.toByteArray()
        }
        suspend fun inspectZip(bytes: ByteArray) = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(bytes, pw, iterations = 10_000)), pw)

        // personId 悬空 → 校验失败
        assertTrue(inspectZip(zipWith { }).isFailure)
        // 非法 status → 校验失败（先把悬空人物那条删掉再测 status）
        val badStatus = zipWith { root ->
            root.getJSONArray("pregnancies").remove(1)
            root.getJSONArray("pregnancies").getJSONObject(0)
                .put("status", "NOT_A_STATUS")
        }
        assertTrue(inspectZip(badStatus).isFailure)
        // 合法文件：babyIds 悬空丢弃、事项 pregnancyId 保留
        val ok = zipWith { root ->
            root.getJSONArray("pregnancies").remove(1)
            // 再塞一条事项指向不存在孕期 → pregnancyId 置 null
            val tasksArr = root.getJSONArray("familyTasks")
            tasksArr.put(
                org.json.JSONObject()
                    .put("id", "t2")
                    .put("personId", org.json.JSONObject.NULL)
                    .put("kind", "CHECKUP").put("title", "孤儿产检")
                    .put("anchorEpochDay", 20260).put("repeat", "NONE")
                    .put("periodIndex", 0).put("done", false)
                    .put("enabled", true).put("pregnancyId", "pgGhost")
                    .put("offsets", org.json.JSONArray().put(0))
                    .put("createdAt", 1).put("updatedAt", 1)
            )
        }
        val plan = inspectZip(ok).getOrThrow()
        assertEquals(1, plan.pregnancies.size)
        // babyIds 中的 ghost-baby 已被丢弃
        assertTrue(plan.pregnancies[0].second.isEmpty())
        val t2 = plan.tasks.first { it.first.id == "t2" }
        assertNull(t2.first.pregnancyId)
        val t1 = plan.tasks.first { it.first.id == "t1" }
        assertEquals("pgA", t1.first.pregnancyId)
    }

    // ---------- 清单（v21，FAMILY_AFFAIRS 26） ----------

    /** 造一张待产包清单 + 两条条目（挂在 seedPregnancy 的 pg1 上）。 */
    private suspend fun seedChecklist(pregId: String, momId: String) {
        db.checklistDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity(
                    id = "cl1", kind = "BIRTH_BAG", title = "待产包",
                    personId = momId, pregnancyId = pregId,
                    createdAt = 3, updatedAt = 4
                )
            )
        )
        db.checklistItemDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity(
                    id = "ci1", checklistId = "cl1", groupName = "证件资料",
                    title = "医保卡", note = null, checked = true,
                    sortOrder = 0, personId = momId,
                    createdAt = 3, updatedAt = 4
                ),
                io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity(
                    id = "ci2", checklistId = "cl1", groupName = "妈妈用品",
                    title = "防滑拖鞋", note = "最好是静音的",
                    checked = false, sortOrder = 5, personId = null,
                    createdAt = 3, updatedAt = 4
                )
            )
        )
    }

    @Test
    fun 清单随备份往返() = runTest {
        seed()
        val (pregId, momId, _) = seedPregnancy()
        seedChecklist(pregId, momId)
        val beforeCl = db.checklistDao().getAll().sortedBy { it.id }
        val beforeItems = db.checklistItemDao().getAll().sortedBy { it.id }

        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        repo.clearAllData()
        assertEquals(0, db.checklistDao().getAll().size)

        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertEquals(1, plan.checklists.size)
        backup.restore(plan)

        assertEquals(beforeCl, db.checklistDao().getAll().sortedBy { it.id })
        assertEquals(beforeItems, db.checklistItemDao().getAll().sortedBy { it.id })
        // 条目挂回原清单
        assertTrue(db.checklistItemDao().getAll().all { it.checklistId == "cl1" })
    }

    @Test
    fun 旧备份无清单键_按空恢复() = runTest {
        seed()
        val (pregId, momId, _) = seedPregnancy()
        seedChecklist(pregId, momId)
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        // 手写“v21 之前”备份：解密后解开 zip 删掉 checklists 键
        // （本机明明有清单，旧版本文件里不会有这个键）
        val plain = BackupCrypto.decrypt(out.toByteArray(), pw)
        val files = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(plain)).use { z ->
            var e = z.nextEntry
            while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
        }
        val root = org.json.JSONObject(files["backup.json"]!!.decodeToString())
        // 导出的 zip 里本有 checklists 键；删掉即模拟旧版本文件
        assertTrue(root.has("checklists"))
        root.remove("checklists")
        files["backup.json"] = root.toString().encodeToByteArray()
        val out2 = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out2).use { z ->
            files.forEach { (n, b) ->
                z.putNextEntry(java.util.zip.ZipEntry(n))
                z.write(b)
                z.closeEntry()
            }
        }
        val plan = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(out2.toByteArray(), pw, iterations = 10_000)),
            pw
        ).getOrThrow()
        assertTrue(plan.checklists.isEmpty())
        backup.restore(plan)
        assertTrue(db.checklistDao().getAll().isEmpty())
        assertTrue(db.checklistItemDao().getAll().isEmpty())
    }

    @Test
    fun 清单备份校验_悬空引用置空与非法内容拒收() = runTest {
        seed()
        val persons = db.personDao().getAll()
        val mom = persons.first { it.name == "妈" }
        val preg = io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity(
            id = "pgA", personId = mom.id, dueEpochDay = 20300,
            lmpEpochDay = null, status = "ACTIVE",
            endedEpochDay = null, createdAt = 1, updatedAt = 1
        )
        val cl = io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity(
            id = "cl1", kind = "BIRTH_BAG", title = "待产包",
            personId = mom.id, pregnancyId = "pgA",
            createdAt = 1, updatedAt = 1
        )
        val item = io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity(
            id = "ci1", checklistId = "cl1", groupName = "证件资料",
            title = "医保卡", note = null, checked = false,
            sortOrder = 0, personId = mom.id, createdAt = 1, updatedAt = 1
        )
        suspend fun zipWith(
            checklistsArg: List<Pair<
                io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity,
                List<io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity>
            >> = listOf(cl to listOf(item)),
            pregsArg: List<Pair<
                io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity,
                List<io.github.xiaohaoz1.jiarenxia.data.db.PregnancyBabyEntity>
            >> = listOf(preg to emptyList()),
            rootMutate: (org.json.JSONObject) -> Unit = {}
        ): ByteArray {
            val zip = BackupJson.writeZip(
                persons = persons,
                phones = db.phoneNumberDao().getAll(),
                relationships = db.relationshipDao().getAll(),
                dates = db.importantDateDao().getAllDates(),
                prefs = emptyList(), customTitles = emptyList(),
                settings = null, avatars = emptyMap(),
                createdAt = 1L, appVersion = "0.9.0",
                pregnancies = pregsArg,
                checklists = checklistsArg
            )
            val files = mutableMapOf<String, ByteArray>()
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zip)).use { z ->
                var e = z.nextEntry
                while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
            }
            val root = org.json.JSONObject(files["backup.json"]!!.decodeToString())
            rootMutate(root)
            files["backup.json"] = root.toString().encodeToByteArray()
            val out = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { z ->
                files.forEach { (n, b) ->
                    z.putNextEntry(java.util.zip.ZipEntry(n))
                    z.write(b)
                    z.closeEntry()
                }
            }
            return out.toByteArray()
        }
        suspend fun inspectZip(bytes: ByteArray) = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(bytes, pw, iterations = 10_000)), pw)

        // 非法 kind → 第 3 批起改为跳过这张清单（连同条目），不再整份失败
        val badKind = zipWith { root ->
            root.getJSONArray("checklists").getJSONObject(0)
                .put("kind", "NOT_A_KIND")
        }
        val skippedPlan = inspectZip(badKind).getOrThrow()
        assertTrue(skippedPlan.checklists.isEmpty())
        // 清单标题超长 → 校验失败
        val badTitle = zipWith { root ->
            root.getJSONArray("checklists").getJSONObject(0)
                .put("title", "x".repeat(31))
        }
        assertTrue(inspectZip(badTitle).isFailure)
        // 条目标题为空 → 校验失败
        val badItem = zipWith { root ->
            root.getJSONArray("checklists").getJSONObject(0)
                .getJSONArray("items").getJSONObject(0).put("title", "  ")
        }
        assertTrue(inspectZip(badItem).isFailure)
        // 悬空引用 → personId/pregnancyId/条目 personId 全部置 null，不失败
        val ok = zipWith { root ->
            val c = root.getJSONArray("checklists").getJSONObject(0)
            c.put("personId", "ghost-person")
            c.put("pregnancyId", "pgGhost")
            c.getJSONArray("items").getJSONObject(0)
                .put("personId", "ghost-person")
        }
        val plan = inspectZip(ok).getOrThrow()
        val (planCl, planItems) = plan.checklists.single()
        assertNull(planCl.personId)
        assertNull(planCl.pregnancyId)
        assertNull(planItems.single().personId)
        assertEquals("待产包", planCl.title)
    }

    // ---------- 第 3 批：birthMinutes / taskId / babyCelebrations ----------

    @Test
    fun 第三批字段往返() = runTest {
        seed()
        val (pregId, momId, babyId) = seedPregnancy()
        // 出生时刻：pg1 的宝宝关联补 birth_minute（同主键先删后插）
        db.pregnancyBabyDao().deleteForPregnancy(pregId)
        db.pregnancyBabyDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.PregnancyBabyEntity(
                    pregnancyId = pregId, babyPersonId = babyId,
                    birthMinute = 510
                )
            )
        )
        // POSTNATAL 清单：一条带 taskId 的条目 + 一条不带
        db.checklistDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity(
                    id = "clP", kind = "POSTNATAL", title = "出生后要办的事",
                    personId = momId, pregnancyId = pregId,
                    createdAt = 3, updatedAt = 4
                )
            )
        )
        db.familyTaskDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = "tkB", personId = babyId, kind = "CUSTOM",
                    title = "豆豆的出生医学证明", anchorEpochDay = 20300,
                    repeat = "NONE", periodIndex = 0, lastDoneEpochDay = null,
                    done = false, amountFen = null, note = null, enabled = true,
                    pregnancyId = null, archived = false,
                    createdAt = 1, updatedAt = 1
                )
            )
        )
        db.familyTaskOffsetDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    "tkB-o0", "tkB", 1),
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    "tkB-o1", "tkB", 0)
            )
        )
        db.checklistItemDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity(
                    id = "ciP1", checklistId = "clP", groupName = "宝宝",
                    title = "出生医学证明", note = null, checked = false,
                    sortOrder = 0, personId = babyId, taskId = "tkB",
                    createdAt = 3, updatedAt = 4
                ),
                io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity(
                    id = "ciP2", checklistId = "clP", groupName = "妈妈",
                    title = "产后复查", note = null, checked = true,
                    sortOrder = 1, personId = momId, taskId = null,
                    createdAt = 3, updatedAt = 4
                )
            )
        )
        db.babyCelebrationDao().upsertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.BabyCelebrationEntity(
                    personId = babyId, kind = "FULL_MONTH", epochDay = 20400),
                io.github.xiaohaoz1.jiarenxia.data.db.BabyCelebrationEntity(
                    personId = babyId, kind = "HUNDRED_DAYS", epochDay = 20500)
            )
        )

        val beforeMinute = db.pregnancyBabyDao().getAll()
            .first { it.birthMinute != null }.birthMinute
        val out = ByteArrayOutputStream()
        backup.export(out, pw)
        repo.clearAllData()
        assertEquals(0, db.babyCelebrationDao().getAll().size)

        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        // JSON 层：birthMinutes 键随孕期、babyCelebrations 独立键
        assertEquals(2, plan.babyCelebrations.size)
        assertEquals(510, plan.pregnancies
            .first { it.first.id == pregId }.second
            .first { it.babyPersonId == babyId }.birthMinute)
        backup.restore(plan)

        assertEquals(beforeMinute,
            db.pregnancyBabyDao().getAll()
                .first { it.birthMinute != null }.birthMinute)
        val items = db.checklistItemDao().getAll()
        assertEquals("tkB", items.first { it.id == "ciP1" }.taskId)
        assertNull(items.first { it.id == "ciP2" }.taskId)
        assertEquals(
            setOf("FULL_MONTH", "HUNDRED_DAYS"),
            db.babyCelebrationDao().getAll()
                .map { it.kind }.toSet()
        )
        assertEquals(20400, db.babyCelebrationDao().getAll()
            .first { it.kind == "FULL_MONTH" }.epochDay)
    }

    @Test
    fun 第三批字段_悬空置空与未知跳过() = runTest {
        seed()
        val persons = db.personDao().getAll()
        val mom = persons.first { it.name == "妈" }
        val baby = persons.first { it.name == "子" }
        val preg = io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity(
            id = "pgA", personId = mom.id, dueEpochDay = 20300,
            lmpEpochDay = null, status = "BORN", endedEpochDay = 20200,
            createdAt = 1, updatedAt = 1
        )
        val task = io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
            id = "t1", personId = baby.id, kind = "CUSTOM", title = "办事",
            anchorEpochDay = 20250, repeat = "NONE", periodIndex = 0,
            lastDoneEpochDay = null, done = false, amountFen = null,
            note = null, enabled = true, pregnancyId = null,
            archived = false, createdAt = 1, updatedAt = 1
        )
        val cl = io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity(
            id = "cl1", kind = "POSTNATAL", title = "出生后要办的事",
            personId = mom.id, pregnancyId = "pgA",
            createdAt = 1, updatedAt = 1
        )
        val item = io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity(
            id = "ci1", checklistId = "cl1", groupName = "宝宝",
            title = "出生医学证明", note = null, checked = false,
            sortOrder = 0, personId = baby.id, taskId = "t1",
            createdAt = 1, updatedAt = 1
        )
        val zip = BackupJson.writeZip(
            persons = persons,
            phones = db.phoneNumberDao().getAll(),
            relationships = db.relationshipDao().getAll(),
            dates = db.importantDateDao().getAllDates(),
            prefs = emptyList(), customTitles = emptyList(),
            tasks = listOf(task to listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    "t1-o0", "t1", 0)
            )),
            settings = null, avatars = emptyMap(),
            createdAt = 1L, appVersion = "0.9.0",
            pregnancies = listOf(
                preg to listOf(
                    io.github.xiaohaoz1.jiarenxia.data.db.PregnancyBabyEntity(
                        "pgA", baby.id, 510)
                )
            ),
            checklists = listOf(cl to listOf(item)),
            babyCelebrations = listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.BabyCelebrationEntity(
                    baby.id, "FULL_MONTH", 20400)
            )
        )
        // 解 zip 改 JSON：taskId 悬空、celebration 人物悬空 + 未知 kind
        val files = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zip)).use { z ->
            var e = z.nextEntry
            while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
        }
        val root = org.json.JSONObject(files["backup.json"]!!.decodeToString())
        // 条目 taskId 指向不存在事项 → 置 null
        root.getJSONArray("checklists").getJSONObject(0)
            .getJSONArray("items").getJSONObject(0).put("taskId", "ghost-task")
        // birthMinutes 里塞一个越界值 → fail（合法性仍校验）
        // （先不动它，单独测悬空/未知）
        val ce = root.getJSONArray("babyCelebrations")
        ce.put(
            org.json.JSONObject()
                .put("personId", "ghost-person")
                .put("kind", "FULL_MONTH").put("epochDay", 20500)
        )
        ce.put(
            org.json.JSONObject()
                .put("personId", baby.id)
                .put("kind", "NOT_A_KIND").put("epochDay", 20600)
        )
        files["backup.json"] = root.toString().encodeToByteArray()
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { z ->
            files.forEach { (n, b) ->
                z.putNextEntry(java.util.zip.ZipEntry(n))
                z.write(b)
                z.closeEntry()
            }
        }
        val plan = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(out.toByteArray(), pw, iterations = 10_000)),
            pw
        ).getOrThrow()
        // 悬空人物与未知 kind 的 celebration 都被丢弃，只剩合法一条
        assertEquals(1, plan.babyCelebrations.size)
        assertEquals(baby.id, plan.babyCelebrations[0].personId)
        assertEquals("FULL_MONTH", plan.babyCelebrations[0].kind)
        // taskId 悬空 → null
        val (planCl, planItems) = plan.checklists.single()
        assertNull(planItems.single().taskId)
        assertEquals("cl1", planCl.id)
        // birthMinutes 合法值保留
        assertEquals(510, plan.pregnancies.single().second
            .single().birthMinute)
    }

    @Test
    fun 第三批字段_非法值拒收() = runTest {
        seed()
        val persons = db.personDao().getAll()
        val mom = persons.first { it.name == "妈" }
        val baby = persons.first { it.name == "子" }
        val preg = io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity(
            id = "pgA", personId = mom.id, dueEpochDay = 20300,
            lmpEpochDay = null, status = "BORN", endedEpochDay = 20200,
            createdAt = 1, updatedAt = 1
        )
        suspend fun zipWithBirthMinute(minute: Int): ByteArray {
            val zip = BackupJson.writeZip(
                persons = persons,
                phones = db.phoneNumberDao().getAll(),
                relationships = db.relationshipDao().getAll(),
                dates = db.importantDateDao().getAllDates(),
                prefs = emptyList(), customTitles = emptyList(),
                settings = null, avatars = emptyMap(),
                createdAt = 1L, appVersion = "0.9.0",
                pregnancies = listOf(
                    preg to listOf(
                        io.github.xiaohaoz1.jiarenxia.data.db.PregnancyBabyEntity(
                            "pgA", baby.id, minute)
                    )
                )
            )
            // writeZip 不写非法值？——直接构造：先看 JSON 里 birthMinutes 形态
            val files = mutableMapOf<String, ByteArray>()
            java.util.zip.ZipInputStream(
                java.io.ByteArrayInputStream(zip)).use { z ->
                var e = z.nextEntry
                while (e != null) { files[e.name] = z.readBytes(); e = z.nextEntry }
            }
            val root = org.json.JSONObject(
                files["backup.json"]!!.decodeToString())
            // birthMinutes 形如 {"子id": 510}；改成越界值
            val pregJson = root.getJSONArray("pregnancies").getJSONObject(0)
            pregJson.getJSONObject("birthMinutes").put(baby.id, 9999)
            files["backup.json"] = root.toString().encodeToByteArray()
            val out = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { z ->
                files.forEach { (n, b) ->
                    z.putNextEntry(java.util.zip.ZipEntry(n))
                    z.write(b)
                    z.closeEntry()
                }
            }
            return out.toByteArray()
        }
        val res = backup.inspect(
            ByteArrayInputStream(
                BackupCrypto.encrypt(zipWithBirthMinute(510), pw,
                    iterations = 10_000)),
            pw
        )
        assertTrue(res.isFailure)
    }

    @Test
    fun 家人共享导出不含清单() = runTest {
        seed()
        val (pregId, momId, babyId) = seedPregnancy()
        seedChecklist(pregId, momId)
        // 满月/百天另设日期同样不进共享（第 3 批边界）
        db.babyCelebrationDao().upsertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.BabyCelebrationEntity(
                    babyId, "FULL_MONTH", 20400)
            )
        )
        val me = db.personDao().getAll().first { it.isSelf }.id
        val out = ByteArrayOutputStream()
        backup.exportShare(out, pw, sharedBy = me)
        val plan = backup.inspect(ByteArrayInputStream(out.toByteArray()), pw)
            .getOrThrow()
        assertTrue(plan.checklists.isEmpty())
        assertTrue(plan.babyCelebrations.isEmpty())
        // 私密备份仍带
        val priv = ByteArrayOutputStream()
        backup.export(priv, pw)
        val privPlan = backup.inspect(
            ByteArrayInputStream(priv.toByteArray()), pw).getOrThrow()
        assertEquals(1, privPlan.checklists.size)
        assertEquals(1, privPlan.babyCelebrations.size)
    }

    private fun encryptedPlan(
        plan: RestorePlan,
        personFacts: List<PersonFactEntity>
    ): ByteArray = BackupCrypto.encrypt(
        BackupJson.writeZip(
            persons = plan.persons,
            phones = plan.phones,
            relationships = plan.relationships,
            dates = plan.importantDates,
            prefs = plan.prefs,
            customTitles = plan.customTitles,
            tasks = plan.tasks,
            contactLogs = plan.contactLogs,
            contactPlans = plan.contactPlans,
            personFacts = personFacts,
            settings = plan.settings,
            avatars = plan.avatars,
            createdAt = plan.summary.createdAt ?: 1,
            appVersion = plan.summary.appVersion ?: "test",
            purpose = plan.purpose,
            sharedBy = plan.sharedBy,
            personOrigins = plan.personOrigins
        ),
        pw
    )
}
