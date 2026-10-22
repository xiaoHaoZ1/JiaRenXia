package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.model.ContactLog
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 联系提醒到期计算（纯 JVM；毫秒统一取当天 12:00，避开时区边界）。 */
class ContactScheduleTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 15)

    /** 某一天中午 12:00 的毫秒（本机时区）。 */
    private fun atNoon(d: LocalDate): Long =
        d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
            .toEpochMilli()

    private fun log(daysAgo: Int) = ContactLog(
        id = "l" + daysAgo, personId = "p1",
        atMillis = atNoon(today.minusDays(daysAgo.toLong())),
        method = ContactMethod.CALL, note = null
    )

    @Test
    fun 从未联系过按计划创建日起算() {
        val status = ContactSchedule.status(
            logs = emptyList(), intervalDays = 14,
            planCreatedAt = atNoon(today.minusDays(14)), today = today)
        assertEquals(0, status.overdueDays)
        assertTrue(status.due)
        assertTrue(ContactSchedule.remindToday(status, today))
        assertNull(status.daysSinceLast)
        assertEquals("还没有记录过和妈妈的联系，打个电话吧",
            ContactSchedule.notifyText(status, "妈妈"))
    }

    @Test
    fun 逾期天数与复提节奏() {
        // 21 天前联系过，周期 14 → 逾期 7，恰逢复提日
        val s21 = ContactSchedule.status(
            listOf(log(21)), 14, atNoon(today.minusDays(30)), today)
        assertEquals(7, s21.overdueDays)
        assertTrue(s21.due)
        assertTrue(ContactSchedule.remindToday(s21, today))
        assertEquals("已经 21 天没联系妈妈了，打个电话吧",
            ContactSchedule.notifyText(s21, "妈妈"))

        // 17 天前 → 逾期 3：到期但不在提醒日
        val s17 = ContactSchedule.status(
            listOf(log(17)), 14, atNoon(today.minusDays(30)), today)
        assertEquals(3, s17.overdueDays)
        assertTrue(s17.due)
        assertFalse(ContactSchedule.remindToday(s17, today))

        // 5 天前，周期 7 → 未到期
        val s5 = ContactSchedule.status(
            listOf(log(5)), 7, atNoon(today.minusDays(30)), today)
        assertFalse(s5.due)
        assertFalse(ContactSchedule.remindToday(s5, today))
    }

    @Test
    fun 只提醒在世非本人的家人() {
        assertFalse(ContactSchedule.eligible(null))
        assertFalse(ContactSchedule.eligible(
            Person(id = "me", name = "我", gender = Gender.MALE,
                isSelf = true)))
        assertFalse(ContactSchedule.eligible(
            Person(id = "gp", name = "爷爷", gender = Gender.MALE,
                livingStatus = LivingStatus.DECEASED)))
        assertTrue(ContactSchedule.eligible(
            Person(id = "mom", name = "妈妈", gender = Gender.FEMALE)))
    }
}
