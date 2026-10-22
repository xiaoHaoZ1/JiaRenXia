package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** ReminderPlanner：到期提醒与文案。 */
class ReminderPlannerTest {

    private fun p(id: String, name: String = id, self: Boolean = false) =
        Person(id = id, name = name, gender = Gender.MALE, isSelf = self)

    private fun entry(person: Person, month: Int, day: Int, year: Int? = null, enabled: Boolean = true) =
        BirthdayEntry(person, BirthdayValue(PartialDate(year, month, day)), enabled)

    @Test
    fun 今天与明天各一条() {
        val today = LocalDate.of(2025, 5, 12)
        val entries = listOf(
            entry(p("a", "张三"), 5, 12),   // 今天
            entry(p("b", "李四"), 5, 13),   // 明天
            entry(p("c", "王五"), 6, 1)     // 不到期
        )
        val dues = ReminderPlanner.dueNotifications(entries, emptyMap(), today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(listOf("a", "b"), dues.map { it.personId })
        assertEquals(listOf(0, 1), dues.map { it.daysBefore })
    }

    @Test
    fun 提醒关闭的不通知() {
        val today = LocalDate.of(2025, 5, 12)
        val dues = ReminderPlanner.dueNotifications(
            listOf(entry(p("a"), 5, 12, enabled = false)), emptyMap(), today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(emptyList<DueReminder>(), dues)
    }

    @Test
    fun 闰日_平年二月二十八算今天() {
        val today = LocalDate.of(2025, 2, 28)
        val e = entry(p("a"), 2, 29)
        val dues = ReminderPlanner.dueNotifications(listOf(e), emptyMap(), today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(1, dues.size)
        assertEquals(0, dues[0].daysBefore)
        // 2-27 → 明天
        val dues27 = ReminderPlanner.dueNotifications(listOf(e), emptyMap(), LocalDate.of(2025, 2, 27), io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(1, dues27[0].daysBefore)
    }

    @Test
    fun 称谓_未关联与本人为空() {
        val today = LocalDate.of(2025, 5, 12)
        val entries = listOf(entry(p("a"), 5, 12), entry(p("me", "我", self = true), 5, 12))
        val titles = mapOf("a" to "父亲", "me" to "我")
        val dues = ReminderPlanner.dueNotifications(entries, titles, today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals("父亲", dues.first { it.personId == "a" }.title)
        assertEquals(null, dues.first { it.personId == "me" }.title)
        // 表中本来就 null（未关联）也保持 null
        val dues2 = ReminderPlanner.dueNotifications(
            listOf(entry(p("x"), 5, 12)), mapOf("x" to null), today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(null, dues2[0].title)
    }

    @Test
    fun 文案变体() {
        assertEquals("今天是父亲张三的生日",
            ReminderPlanner.text(DueReminder("a", "张三", "父亲", 0), false))
        assertEquals("明天是父亲张三的生日",
            ReminderPlanner.text(DueReminder("a", "张三", "父亲", 1), false))
        assertEquals("今天是李四的生日",
            ReminderPlanner.text(DueReminder("a", "李四", null, 0), false))
        assertEquals("明天是李四的生日",
            ReminderPlanner.text(DueReminder("a", "李四", null, 1), false))
        assertEquals("今天是你的生日", ReminderPlanner.text(DueReminder("me", "我", null, 0), true))
        assertEquals("明天是你的生日", ReminderPlanner.text(DueReminder("me", "我", null, 1), true))
    }

    // ---------- 纪念日 / 忌日（V0.8） ----------

    private fun dateEntry(
        person: Person,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        month: Int, day: Int, year: Int? = null,
        calendar: io.github.xiaohaoz1.jiarenxia.core.model.CalendarType =
            io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.SOLAR,
        partnerName: String? = null, partnerId: String? = null,
        partnerIsSelf: Boolean = false
    ) = io.github.xiaohaoz1.jiarenxia.data.DateEntry(
        type = type, person = person,
        date = BirthdayValue(PartialDate(year, month, day), calendar),
        reminderEnabled = true,
        reminderOffsets = setOf(0, 1, 3),
        partnerId = partnerId, partnerName = partnerName,
        partnerIsSelf = partnerIsSelf
    )

    @Test
    fun 结婚纪念日_今天带第N年() {
        val today = LocalDate.of(2025, 10, 18)
        val dad = p("dad", "杨建国")
        val e = dateEntry(
            dad,
            io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
                .WEDDING_ANNIVERSARY,
            10, 18, 1984, partnerId = "mom", partnerName = "王秀兰")
        val dues = ReminderPlanner.dueDateReminders(
            listOf(e), mapOf("dad" to "爸爸", "mom" to "妈妈"),
            today, io.github.xiaohaoz1.jiarenxia.domain.lunar
                .IcuLunarCalendar())
        assertEquals(1, dues.size)
        assertEquals(
            "今天是爸爸妈妈的结婚纪念日（第 41 年）",
            ReminderPlanner.text(dues[0], false))
    }

    @Test
    fun 结婚纪念日_有一方是我() {
        val today = LocalDate.of(2025, 10, 18)
        val me = p("me", "杨明", self = true)
        val e = dateEntry(
            me,
            io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
                .WEDDING_ANNIVERSARY,
            10, 18, null, partnerId = "w", partnerName = "李雪")
        val dues = ReminderPlanner.dueDateReminders(
            listOf(e), emptyMap(), today,
            io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(
            "今天是你和李雪的结婚纪念日",
            ReminderPlanner.text(dues[0], true))
    }


    @Test
    fun 周年_带去世年份() {
        // 今天恰好是忌日发生日
        val lunarCal = io.github.xiaohaoz1.jiarenxia.domain.lunar
            .IcuLunarCalendar()
        val gp = p("gp", "杨福生")
        val e = dateEntry(
            gp,
            io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
                .DEATH_ANNIVERSARY,
            5, 10, 2016)
        val dues = ReminderPlanner.dueDateReminders(
            listOf(e), mapOf("gp" to "爷爷"),
            LocalDate.of(2026, 5, 10), lunarCal)
        assertEquals(1, dues.size)
        assertEquals("10 周年", dues[0].yearLabel)
        assertTrue(ReminderPlanner.text(dues[0], false).contains("忌日"))
    }
}
