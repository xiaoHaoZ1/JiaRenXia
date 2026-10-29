package io.github.xiaohaoz1.jiarenxia.reminder

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 仅实现 Worker 需要的最小仓储 fake。 */
private class FakeRepo(
    private val self: Person,
    private val entries: List<BirthdayEntry>,
    private val dateEntries: List<io.github.xiaohaoz1.jiarenxia.data.DateEntry> = emptyList(),
    extraPersons: List<Person> = emptyList()
) : FamilyRepository {
    private val graph = FamilyGraph(
        listOf(self) + extraPersons, emptyList(), emptyMap(),
        birthdayValues = entries.groupBy({ it.person.id }, { it.birthday })
    )
    override fun observeSelf() = flowOf<Person?>(self)
    override fun observeFamilyGraph() = flowOf(graph)
    override fun observePersonDetail(personId: String) = flowOf(null)
    override fun observeBirthdayEntries() = flowOf(entries)
    override fun observeDefaultNumbers() = flowOf(emptyMap<String, String>())
    override suspend fun getBirthdayEntries() = entries
    override suspend fun createSelf(draft: PersonDraft) = FamilyResult.Success("x")
    override suspend fun createRelative(d: PersonDraft, a: String, r: RelationRole) = FamilyResult.Success("x")
    override suspend fun createRelative(
        d: PersonDraft,
        specs: List<io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec>,
        customTitle: String?
    ) = FamilyResult.Success("x")
    override suspend fun addRelationships(
        specs: List<io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec>,
        customTitleFor: String?,
        customTitle: String?
    ) = FamilyResult.Success(Unit)
    override suspend fun updatePerson(personId: String, draft: PersonDraft) = FamilyResult.Success(Unit)
    override suspend fun deletePerson(personId: String) = FamilyResult.Success(Unit)
    override suspend fun addRelationship(p: String, o: String, r: RelationRole) = FamilyResult.Success("x")
    override suspend fun removeRelationship(id: String) = FamilyResult.Success(Unit)
    override suspend fun setSiblingAgeOrder(r: String, s: String, o: RelativeAgeOrder?) = FamilyResult.Success(Unit)
    override fun observeCustomTitles() = kotlinx.coroutines.flow.flowOf(emptyMap<String, String>())
    override suspend fun setCustomTitle(t: String, title: String?) = FamilyResult.Success(Unit)
    override suspend fun setBirthday(

        personId: String,

        calendar: io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,

        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?

    ) = FamilyResult.Success(Unit)

    override suspend fun setReminderEnabled(
        personId: String, enabled: Boolean,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
    ) = FamilyResult.Success(Unit)
    override suspend fun setReminderOffsets(
        personId: String, offsets: Set<Int>,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
    ) = FamilyResult.Success(Unit)
    override suspend fun setDeathAnniversary(
        personId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ) = FamilyResult.Success(Unit)
    override suspend fun setWeddingAnniversary(
        personId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?
    ) = FamilyResult.Success(Unit)
    override suspend fun getDateEntries() = dateEntries
    override suspend fun coParentCandidate(personId: String) = null
    override suspend fun commonChildCandidates(a: String, b: String) =
        emptyList<io.github.xiaohaoz1.jiarenxia.data.CommonChildCandidate>()
    override suspend fun linkSpouseAndSetWeddingAnniversary(
        personId: String, spouseId: String,
        value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
    ) = FamilyResult.Success(Unit)
    override fun observeDateEntries() =
        kotlinx.coroutines.flow.flowOf(
            emptyList<io.github.xiaohaoz1.jiarenxia.data.DateEntry>())

    override suspend fun setAvatar(p: String, s: Uri) = FamilyResult.Success(Unit)
    override suspend fun removeAvatar(p: String) = FamilyResult.Success(Unit)
    override suspend fun clearAllData() {}
}

/** 内存版设置仓储。 */
private class FakeSettings : SettingsRepository {
    private val _reminders = MutableStateFlow(true)
    var runState: Pair<Long, Set<String>>? = null
    var notifRequested = false
    override val remindersEnabled: Flow<Boolean> get() = _reminders
    override val notificationPermissionRequested: Flow<Boolean> get() = MutableStateFlow(notifRequested)
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
    override suspend fun setNotificationPermissionRequested(requested: Boolean) { notifRequested = requested }
    private val _themeMode = MutableStateFlow(io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode.LIGHT)
    override val themeMode: Flow<io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode> get() = _themeMode
    override val fontScaleFactor = kotlinx.coroutines.flow.MutableStateFlow(1.0f)
    override suspend fun setFontScaleFactor(f: Float) {
        fontScaleFactor.value = f
    }
    override suspend fun setThemeMode(mode: io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode) { _themeMode.value = mode }
    override val reminderMinuteOfDay = MutableStateFlow(540)
    override suspend fun setReminderMinuteOfDay(minute: Int) { reminderMinuteOfDay.value = minute }
    override val taskReminderMinuteOfDay = MutableStateFlow(540)
    override suspend fun setTaskReminderMinuteOfDay(minute: Int) {
        taskReminderMinuteOfDay.value = minute
    }
    override val contactReminderMinuteOfDay = MutableStateFlow(540)
    override suspend fun setContactReminderMinuteOfDay(minute: Int) {
        contactReminderMinuteOfDay.value = minute
    }
    override val lastBackupAt = MutableStateFlow<Long?>(null)
    override suspend fun setLastBackupAt(atMillis: Long) { lastBackupAt.value = atMillis }
    override suspend fun clear() {}
}

/** 固定到指定时刻的时钟（毫秒→本地时刻）。 */
private class FakeClock(private val at: java.time.LocalDateTime) : TimeSource {
    override fun now(): Long =
        at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

/** BirthdayReminderWorker：执行成功并记录运行状态（work-testing + Robolectric）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun buildWorker(
        settings: FakeSettings,
        force: Boolean = false,
        taskRepo: FakeTaskRepo = FakeTaskRepo(),
        clock: TimeSource = FakeClock(LocalDate.now().atTime(12, 0)),
        dateEntries: List<io.github.xiaohaoz1.jiarenxia.data.DateEntry> = emptyList(),
        spouse: Person? = null
    ): BirthdayReminderWorker {
        val self = Person(id = "me", name = "我", gender = Gender.MALE, isSelf = true)
        val entries = listOf(
            BirthdayEntry(self, BirthdayValue(PartialDate(1990, LocalDate.now().monthValue, LocalDate.now().dayOfMonth)), true)
        )
        val repo = FakeRepo(self, entries, dateEntries,
            listOfNotNull(spouse))
        val overview = ObserveFamilyOverview(repo, KinshipEngine())
        return TestListenableWorkerBuilder<BirthdayReminderWorker>(context)
            .setInputData(
                androidx.work.Data.Builder()
                    .putBoolean(BirthdayReminderWorker.KEY_FORCE, force).build()
            )
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context, workerClassName: String, params: WorkerParameters
                ): ListenableWorker =
                    BirthdayReminderWorker(
                        appContext, params, repo, taskRepo,
                        io.github.xiaohaoz1.jiarenxia.data.FakeContactRepository(),
                        settings, overview,
                        io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar(),
                        clock
                    )
            })
            .build()
    }

    @Test
    fun 执行成功并记录运行状态() = runTest {
        val settings = FakeSettings()
        val result = buildWorker(settings).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(LocalDate.now().toEpochDay(), settings.runState?.first)
    }

    @Test
    fun 今天已通知过的提醒_补跑不再发() = runTest {
        val settings = FakeSettings()
        // 今天已发过这条生日提醒 → 再跑不再发（“我”今天生日的键）
        settings.runState = LocalDate.now().toEpochDay() to
            setOf("date:BIRTHDAY_SOLAR:me:0")
        val taskRepo = FakeTaskRepo(listOf(newTask("交电费")))
        val result = buildWorker(settings, taskRepo = taskRepo).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val texts = postedTexts()
        assertTrue(texts.none { it.contains("生日") })
        // 新增的事项仍正常发出
        assertTrue(texts.any { it.contains("今天要办：交电费") })
    }

    @Test
    fun 时刻未到的类当天不发() = runTest {
        val settings = FakeSettings()
        // 事项提醒设在 21:00，当前（中午 12:00）还没到 → 不发；生日 09:00 已到照发
        settings.taskReminderMinuteOfDay.value = 21 * 60
        val taskRepo = FakeTaskRepo(listOf(newTask("交电费")))
        buildWorker(settings, taskRepo = taskRepo).doWork()
        val texts = postedTexts()
        assertTrue(texts.any { it.contains("生日") })
        assertTrue(texts.none { it.contains("今天要办") })
    }

    @Test
    fun 调试强制运行_不改动运行状态() = runTest {
        val settings = FakeSettings()
        val result = buildWorker(settings, force = true).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(null, settings.runState)
    }

    // ---------- 家庭事项通知 ----------

    private fun newTask(title: String, daysFromToday: Int = 0) =
        io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask(
            id = "t1", personId = null,
            kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CUSTOM,
            title = title,
            anchorDate = LocalDate.now().plusDays(daysFromToday.toLong()),
            repeat = io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.NONE,
            offsets = setOf(0, 1, 3, 7, 15, 30, 60)
        )

    /** 通知内容文本（锁屏公开版不外泄内容，用私有版断言）。 */
    @Test
    fun 相恋第1000天里程碑当天发通知且不与同日纪念冲突() = runTest {
        val settings = FakeSettings()
        val today = LocalDate.now()
        val wife = Person(id = "w1", name = "李雪", gender = Gender.FEMALE)
        val dating = io.github.xiaohaoz1.jiarenxia.data.DateEntry(
            type = io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType.DATING_ANNIVERSARY,
            person = Person(id = "me", name = "我", gender = Gender.MALE, isSelf = true),
            date = BirthdayValue(PartialDate(today.minusDays(999).year,
                today.minusDays(999).monthValue, today.minusDays(999).dayOfMonth)),
            reminderEnabled = true, reminderOffsets = setOf(0),
            partnerId = "w1", partnerName = "李雪")
        val wedding = dating.copy(
            type = io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType.WEDDING_ANNIVERSARY,
            date = BirthdayValue(PartialDate(today.year - 5, today.monthValue, today.dayOfMonth)))
        val result = buildWorker(settings,
            dateEntries = listOf(dating, wedding), spouse = wife).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val texts = postedTexts()
        assertTrue(texts.any { it == "今天是你和李雪相恋第 1000 天" })
        // 同日的结婚里程碑也单独发出（通知 id 不冲突）
        // 同日另一类纪念（结婚纪念日，非里程碑）单独发出
        assertTrue(texts.any { it.contains("结婚纪念日") })
    }

    private fun postedTexts(): List<String> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        return org.robolectric.Shadows.shadowOf(nm).allNotifications.mapNotNull { n ->
            n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        }
    }

    @Test
    fun 事项到期_发出事项通知() = runTest {
        val settings = FakeSettings()
        val taskRepo = FakeTaskRepo(listOf(newTask("交电费")))
        val result = buildWorker(settings, taskRepo = taskRepo).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(postedTexts().any { it.contains("今天要办：交电费") })
    }

    @Test
    fun 事项提醒关闭_不发事项通知但生日照发() = runTest {
        val settings = FakeSettings()
        settings.taskRemindersEnabled.value = false
        val taskRepo = FakeTaskRepo(listOf(newTask("交电费")))
        val result = buildWorker(settings, taskRepo = taskRepo).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val texts = postedTexts()
        // 生日照发（setUp 里今天过生日的“我”）
        assertTrue(texts.any { it.contains("生日") })
        assertTrue(texts.none { it.contains("今天要办") })
    }

    @Test
    fun 调试强制运行_事项也发() = runTest {
        val settings = FakeSettings()
        val taskRepo = FakeTaskRepo(listOf(newTask("交电费")))
        val result = buildWorker(settings, taskRepo = taskRepo, force = true).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(postedTexts().any { it.contains("今天要办：交电费") })
        assertEquals(null, settings.runState)
    }

    @Test
    fun 调试强制运行_事项开关关闭不发() = runTest {
        val settings = FakeSettings()
        settings.taskRemindersEnabled.value = false
        val taskRepo = FakeTaskRepo(listOf(newTask("交电费")))
        buildWorker(settings, taskRepo = taskRepo, force = true).doWork()
        assertTrue(postedTexts().none { it.contains("今天要办") })
    }

    @Test
    fun 事项逾期不发通知() = runTest {
        val settings = FakeSettings()
        val taskRepo = FakeTaskRepo(listOf(newTask("过期的事", daysFromToday = -3)))
        buildWorker(settings, taskRepo = taskRepo).doWork()
        assertTrue(postedTexts().none { it.contains("过期的事") })
    }
}

/** 事项仓储 fake：字段即全部数据。 */
private class FakeTaskRepo(
    var tasks: List<io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask> = emptyList()
) : io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository {
    override fun observeTasks() = kotlinx.coroutines.flow.flowOf(tasks)
    override fun observeTasksForPerson(personId: String) =
        kotlinx.coroutines.flow.flowOf(tasks.filter { it.personId == personId })
    override suspend fun getTasks() = tasks
    override suspend fun getTask(id: String) = tasks.firstOrNull { it.id == id }
    override suspend fun save(draft: io.github.xiaohaoz1.jiarenxia.data.TaskDraft) =
        FamilyResult.Success(draft.id ?: "new-id")
    override suspend fun markDone(id: String, today: LocalDate) = FamilyResult.Success(Unit)
    override suspend fun restore(task: io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask) =
        FamilyResult.Success(Unit)
    override suspend fun delete(id: String) {}
}
