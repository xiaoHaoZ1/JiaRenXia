package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarDate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** “我来到这世界第 N 天”（桌面插件焦点卡，纯 JVM，农历历法用直通假实现）。 */
class LifeDaysTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 12)

    private val me = Person(
        id = "me", name = "我", gender = Gender.MALE,
        isSelf = true, livingStatus = LivingStatus.ALIVE
    )

    private val passthroughLunar = object : LunarCalendar {
        override fun toSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate =
            LocalDate.of(year, month, day)

        override fun fromSolar(date: LocalDate) =
            LunarDate(date.year, date.monthValue, date.dayOfMonth, leap = false)

        override fun leapMonthOf(lunarYear: Int): Int? = null

        override fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean): Int = 30
    }

    private fun entry(
        p: Person, year: Int?, month: Int, day: Int,
        calendar: CalendarType = CalendarType.SOLAR,
    ) = BirthdayEntry(
        p, BirthdayValue(PartialDate(year, month, day), calendar),
        reminderEnabled = false
    )

    @Test
    fun solarBirthday_countsDaysSinceBirth() {
        val line = LifeDays.line(
            listOf(entry(me, 2000, 1, 1)), today, passthroughLunar)!!
        assertEquals("me", line.personId)
        assertEquals("我来到这世界第 9813 天", line.title)
        assertEquals("26 岁 · 离第 10000 天还有 187 天", line.note)
    }

    @Test
    fun bornToday_isDayOne() {
        val line = LifeDays.line(
            listOf(entry(me, 2026, 11, 12)), today, passthroughLunar)!!
        assertEquals("我来到这世界第 1 天", line.title)
        assertEquals("离第 1000 天还有 999 天", line.note)
    }

    @Test
    fun exactThousandthDay_nextMilestonePlus1000() {
        val born = today.minusDays(9999)
        val line = LifeDays.line(
            listOf(entry(me, born.year, born.monthValue, born.dayOfMonth)),
            today, passthroughLunar)!!
        assertEquals("我来到这世界第 10000 天", line.title)
        assertEquals("27 岁 · 离第 11000 天还有 1000 天", line.note)
    }

    @Test
    fun lunarOnlyBirthday_sameAsSolar() {
        val line = LifeDays.line(
            listOf(entry(me, 2000, 1, 1, CalendarType.LUNAR)),
            today, passthroughLunar)!!
        assertEquals("我来到这世界第 9813 天", line.title)
    }

    @Test
    fun solarAndLunarConsistent_usesMergedDate() {
        val line = LifeDays.line(
            listOf(
                entry(me, 2000, 1, 1),
                entry(me, 2000, 1, 1, CalendarType.LUNAR),
            ),
            today, passthroughLunar)!!
        assertEquals("我来到这世界第 9813 天", line.title)
    }

    @Test
    fun conflictingBirthdays_hidden() {
        val line = LifeDays.line(
            listOf(
                entry(me, 2000, 1, 1),
                entry(me, 2000, 2, 1, CalendarType.LUNAR),
            ),
            today, passthroughLunar)
        assertNull(line)
    }

    @Test
    fun missingYear_hidden() {
        val line = LifeDays.line(
            listOf(entry(me, null, 1, 1)), today, passthroughLunar)
        assertNull(line)
    }

    @Test
    fun noSelfPerson_hidden() {
        val other = Person(
            id = "p1", name = "张三", gender = Gender.MALE,
            livingStatus = LivingStatus.ALIVE
        )
        val line = LifeDays.line(
            listOf(entry(other, 2000, 1, 1)), today, passthroughLunar)
        assertNull(line)
    }

    @Test
    fun birthInFuture_hidden() {
        val line = LifeDays.line(
            listOf(entry(me, 2027, 1, 1)), today, passthroughLunar)
        assertNull(line)
    }
}
