package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** UpcomingBirthdays：未来 30 天窗口与排序。 */
class UpcomingBirthdaysTest {

    private fun e(id: String, month: Int, day: Int, enabled: Boolean = true) =
        BirthdayEntry(Person(id = id, name = id, gender = Gender.MALE), BirthdayValue(PartialDate(1990, month, day)), enabled)

    @Test
    fun 窗口边界_30含31不含() {
        val today = LocalDate.of(2025, 5, 12)
        val res = UpcomingBirthdays.build(
            listOf(e("in30", 6, 11), e("out31", 6, 12)), emptyMap(), today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(listOf("in30"), res.upcoming.map { it.person.id })
    }

    @Test
    fun 排序与今天拆分() {
        val today = LocalDate.of(2025, 5, 12)
        val res = UpcomingBirthdays.build(
            listOf(e("b", 5, 15), e("todayP", 5, 12), e("a", 5, 13)), emptyMap(), today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(listOf("todayP"), res.today.map { it.person.id })
        assertEquals(listOf("a", "b"), res.upcoming.map { it.person.id })
        assertEquals(listOf(1, 3), res.upcoming.map { it.daysUntil })
    }

    @Test
    fun 跨年_12月到1月() {
        val today = LocalDate.of(2025, 12, 20)
        val res = UpcomingBirthdays.build(
            listOf(e("jan", 1, 5), e("dec", 12, 25)), emptyMap(), today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(listOf("dec", "jan"), res.upcoming.map { it.person.id })
        assertEquals(listOf(5, 16), res.upcoming.map { it.daysUntil })
    }

    @Test
    fun 提醒关闭的行仍列出并标记() {
        val today = LocalDate.of(2025, 5, 12)
        val res = UpcomingBirthdays.build(
            listOf(e("a", 5, 20, enabled = false)), emptyMap(), today, io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar())
        assertEquals(1, res.upcoming.size)
        assertEquals(false, res.upcoming[0].reminderEnabled)
    }
}
