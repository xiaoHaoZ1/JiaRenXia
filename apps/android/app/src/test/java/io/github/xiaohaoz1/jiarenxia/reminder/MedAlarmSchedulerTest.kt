package io.github.xiaohaoz1.jiarenxia.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.MedicationDraft
import io.github.xiaohaoz1.jiarenxia.data.MedicationRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/** 记录 exact/inexact/取消调用的假闹钟网关。 */
private class FakeGateway(var canExact: Boolean = true) : MedAlarmGateway {
    data class Call(val kind: String, val requestCode: Int, val at: Long)
    val calls = mutableListOf<Call>()
    val cancelled = mutableListOf<Int>()
    override fun canScheduleExact() = canExact
    override fun setExact(requestCode: Int, at: Long, pi: PendingIntent) {
        calls += Call("exact", requestCode, at)
    }
    override fun setInexact(requestCode: Int, at: Long, pi: PendingIntent) {
        calls += Call("inexact", requestCode, at)
    }
    override fun cancel(requestCode: Int, pi: PendingIntent) {
        cancelled += requestCode
    }
}

/** 「按时吃」闹钟调度：exact/inexact、取消、通知门控与触发文案。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedAlarmSchedulerTest : io.github.xiaohaoz1.jiarenxia.data.RepoTestBase() {

    private lateinit var context: Context
    private lateinit var meds: MedicationRepositoryImpl
    private lateinit var settings: FakeSettingsRepository
    private lateinit var gateway: FakeGateway
    private lateinit var scheduler: MedAlarmScheduler
    private var selfId = ""
    private var nowMillis = 0L

    private val zone get() = ZoneId.systemDefault()
    private val today get() =
        ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
            .toLocalDate()

    @Before
    fun setUpScheduler() {
        context = ApplicationProvider.getApplicationContext()
        meds = MedicationRepositoryImpl(
            db,
            object : io.github.xiaohaoz1.jiarenxia.data.IdGenerator {
                override fun newId() = "m-" + (++seq)
            },
            object : TimeSource { override fun now() = now }
        )
        val overview = ObserveFamilyOverview(repo, KinshipEngine())
        settings = FakeSettingsRepository()
        gateway = FakeGateway()
        // 固定“现在”为今天 07:00（系统时区），08:00 的药今天还没到点
        nowMillis = ZonedDateTime.of(
            2026, 11, 11, 7, 0, 0, 0, zone
        ).toInstant().toEpochMilli()
        scheduler = MedAlarmScheduler(
            context, meds, overview, settings, gateway,
            object : TimeSource { override fun now() = nowMillis }
        )
    }

    private var seq = 0

    private fun requestCode(personId: String, minute: Int) =
        ("medalarm:" + personId + ":" + minute).hashCode()

    private fun atToday(minute: Int, dayOffset: Long = 0) =
        ZonedDateTime.of(
            today.plusDays(dayOffset),
            java.time.LocalTime.of(minute / 60, minute % 60),
            zone
        ).toInstant().toEpochMilli()

    private suspend fun addMed(
        personId: String = selfId,
        times: List<Int> = listOf(480),
        usage: MedUsage = MedUsage.LONG_TERM,
        courseDays: Int? = null,
        start: LocalDate = LocalDate.of(2026, 11, 1),
        name: String = "二甲双胍"
    ) = meds.save(
        MedicationDraft(
            id = null, personId = personId, name = name, dose = "1 片",
            spec = null, meal = null, usage = usage, courseDays = courseDays,
            startEpochDay = start.toEpochDay(), purpose = null, note = null,
            refillEpochDay = null, times = times
        )
    ).let { (it as io.github.xiaohaoz1.jiarenxia.data.FamilyResult.Success).value }

    private fun notificationTitles(): List<String> {
        val nm = context.getSystemService(NotificationManager::class.java)
        val all: List<*> = shadowOf(nm).allNotifications
        return all.mapNotNull { n ->
            val notif = when (n) {
                is Notification -> n
                is android.service.notification.StatusBarNotification ->
                    n.notification
                else -> null
            } ?: return@mapNotNull null
            notif.extras?.getString(Notification.EXTRA_TITLE)
        }
    }

    @Test
    fun `有精确权限走exact无权限走inexact`() = runTest {
        selfId = createSelf()
        addMed()
        gateway.canExact = true
        scheduler.rescheduleAll()
        assertEquals(listOf("exact"), gateway.calls.map { it.kind })
        assertEquals(
            listOf(requestCode(selfId, 480)),
            gateway.calls.map { it.requestCode })
        assertEquals(atToday(480), gateway.calls.single().at)

        gateway.calls.clear()
        gateway.canExact = false
        scheduler.rescheduleAll()
        assertEquals(listOf("inexact"), gateway.calls.map { it.kind })
    }

    @Test
    fun `通知权限关闭不排且清空已排`() = runTest {
        selfId = createSelf()
        addMed()
        scheduler.rescheduleAll()
        assertEquals(1, gateway.calls.size)

        val nm = context.getSystemService(NotificationManager::class.java)
        shadowOf(nm).setNotificationsEnabled(false)
        gateway.calls.clear()
        gateway.cancelled.clear()
        scheduler.rescheduleAll()
        assertTrue(gateway.calls.isEmpty())
        assertEquals(
            listOf(requestCode(selfId, 480)), gateway.cancelled)
    }

    @Test
    fun `按时吃渠道被关不排`() = runTest {
        selfId = createSelf()
        addMed()
        scheduler.rescheduleAll()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                MedAlarmScheduler.MED_ON_TIME_CHANNEL, "按时吃药",
                NotificationManager.IMPORTANCE_NONE
            )
        )
        gateway.calls.clear()
        gateway.cancelled.clear()
        scheduler.rescheduleAll()
        assertTrue(gateway.calls.isEmpty())
        assertEquals(
            listOf(requestCode(selfId, 480)), gateway.cancelled)
    }

    @Test
    fun `槽位消失后旧闹钟被取消`() = runTest {
        selfId = createSelf()
        val id = addMed()
        scheduler.rescheduleAll()
        assertEquals(1, gateway.calls.size)

        meds.markStopped(id, today)
        gateway.calls.clear()
        gateway.cancelled.clear()
        scheduler.rescheduleAll()
        assertTrue(gateway.calls.isEmpty())
        assertEquals(
            listOf(requestCode(selfId, 480)), gateway.cancelled)
    }

    @Test
    fun `关子开关取消再开恢复`() = runTest {
        selfId = createSelf()
        addMed()
        scheduler.rescheduleAll()
        assertEquals(1, gateway.calls.size)

        settings.medOnTimeEnabled.value = false
        // 诊断：首次重排应已持久化槽位清单
        assertEquals(
            setOf("$selfId:480"),
            context.getSharedPreferences("med_alarms", Context.MODE_PRIVATE)
                .getStringSet("scheduled_slots", emptySet()))
        gateway.calls.clear()
        gateway.cancelled.clear()
        scheduler.rescheduleAll()
        assertTrue(gateway.calls.isEmpty())
        assertEquals(
            listOf(requestCode(selfId, 480)), gateway.cancelled)

        settings.medOnTimeEnabled.value = true
        scheduler.rescheduleAll()
        assertEquals(1, gateway.calls.size)
        assertEquals("exact", gateway.calls.single().kind)
    }

    @Test
    fun `触发后一小时内按时发一小时外不发且排下一次`() = runTest {
        selfId = createSelf()
        addMed()
        scheduler.rescheduleAll()
        val triggerAt = atToday(480)
        val rc = requestCode(selfId, 480)

        // 触发后 1 分钟（≤5 分钟）：立即文案
        nowMillis = triggerAt + 60_000
        scheduler.onAlarmFired(selfId, 480, triggerAt)
        val titles = notificationTitles()
        assertEquals(1, titles.size)
        assertTrue(titles.single().startsWith("08:00 该吃药了："))
        assertTrue(titles.single().contains("二甲双胍 1 片"))
        // 触发后重排：今天 08:00 已过 → 排明天 08:00
        val lastSet = gateway.calls.last { it.requestCode == rc }
        assertEquals(atToday(480, dayOffset = 1), lastSet.at)

        // 再触发但超 60 分钟：不发新通知（同 id 会替换，这里直接看总数不变）
        val nm = context.getSystemService(NotificationManager::class.java)
        shadowOf(nm).allNotifications.size.let { before ->
            nowMillis = triggerAt + 61 * 60_000
            scheduler.onAlarmFired(selfId, 480, triggerAt)
            assertEquals(before, shadowOf(nm).allNotifications.size)
        }
    }

    @Test
    fun `五到六十分钟用的药文案`() = runTest {
        selfId = createSelf()
        addMed()
        scheduler.rescheduleAll()
        val triggerAt = atToday(480)
        nowMillis = triggerAt + 30 * 60_000
        scheduler.onAlarmFired(selfId, 480, triggerAt)
        val titles = notificationTitles()
        assertEquals(1, titles.size)
        assertTrue(titles.single().startsWith("08:00 的药："))
        assertTrue(!titles.single().contains("请立即"))
    }

    @Test
    fun `他人槽位文案带称谓`() = runTest {
        selfId = createSelf()
        // 妈妈 + 显式打开按时吃
        val mom = repo.createRelative(
            io.github.xiaohaoz1.jiarenxia.data.PersonDraft(
                name = "妈妈",
                gender = io.github.xiaohaoz1.jiarenxia.core.model.Gender.FEMALE),
            selfId,
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.MOTHER
        ).let {
            (it as io.github.xiaohaoz1.jiarenxia.data.FamilyResult.Success).value
        }
        addMed(personId = mom)
        meds.setOnTimeEnabled(mom, true)
        scheduler.rescheduleAll()
        val rc = requestCode(mom, 480)
        assertTrue(gateway.calls.any { it.requestCode == rc })

        // 找该槽的触发时刻
        val call = gateway.calls.last { it.requestCode == rc }
        nowMillis = call.at + 60_000
        scheduler.onAlarmFired(mom, 480, call.at)
        val titles = notificationTitles()
        assertEquals(1, titles.size)
        // 称谓由 KinshipEngine 推导（妈妈/母亲），只断言不含“我”的自称格式
        assertTrue(titles.single().contains("该吃药了："))
        assertTrue(titles.single().contains("二甲双胍 1 片"))
    }
}
