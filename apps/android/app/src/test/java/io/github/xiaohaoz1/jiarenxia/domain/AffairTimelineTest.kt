package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarDate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 家事时间线纪念日/忌日合成（纯 JVM，农历历法用直通假实现）。 */
class AffairTimelineTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 15)

    /** 直通历法：农历 (y,m,d) 当公历 (y,m,d) 用，只验证换算链路被调用。 */
    private val passthroughLunar = object : LunarCalendar {
        override fun toSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate =
            LocalDate.of(year, month, day)

        override fun fromSolar(date: LocalDate) =
            LunarDate(date.year, date.monthValue, date.dayOfMonth, leap = false)

        override fun leapMonthOf(lunarYear: Int): Int? = null

        override fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean): Int = 30
    }

    private fun person(id: String, name: String) = Person(id, name, Gender.MALE)

    private fun entry(
        type: ImportantDateType,
        person: Person,
        date: PartialDate,
        calendar: CalendarType = CalendarType.SOLAR,
        partnerId: String? = null,
        partnerName: String? = null,
        partnerIsSelf: Boolean = false
    ) = DateEntry(
        type = type, person = person,
        date = BirthdayValue(date, calendar),
        reminderEnabled = false,
        partnerId = partnerId, partnerName = partnerName,
        partnerIsSelf = partnerIsSelf
    )

    @Test
    fun weddingToday_goesToTodayGroup_withYearLabel() {
        val dad = person("dad", "爸爸")
        val dates = listOf(
            entry(
                ImportantDateType.WEDDING_ANNIVERSARY, dad,
                PartialDate(1985, 10, 15),
                partnerId = "mom", partnerName = "妈妈"
            )
        )
        val (dToday, dNext) = AffairTimeline.dateRows(
            dates, emptyMap(), today, passthroughLunar)
        assertEquals(1, dToday.size)
        assertTrue(dNext.isEmpty())
        val row = dToday[0]
        assertEquals("爸爸妈妈的结婚纪念日", row.label)
        assertEquals("第 41 年", row.yearLabel)
        assertEquals(0, row.daysUntil)
        assertEquals("dad", row.personId)
        assertNull(row.lunarText)
    }

    @Test
    fun deathFuture_goesToNextGroup_withN周年() {
        val grandpa = person("gp", "爷爷")
        val dates = listOf(
            entry(
                ImportantDateType.DEATH_ANNIVERSARY, grandpa,
                PartialDate(2016, 10, 25)
            )
        )
        val (dToday, dNext) = AffairTimeline.dateRows(
            dates, emptyMap(), today, passthroughLunar)
        assertTrue(dToday.isEmpty())
        assertEquals(1, dNext.size)
        assertEquals("爷爷的忌日", dNext[0].label)
        assertEquals("10 周年", dNext[0].yearLabel)
        assertEquals(10, dNext[0].daysUntil)
    }

    @Test
    fun birthdaysAndOutOfWindow_areSkipped() {
        val p = person("p1", "张三")
        val dates = listOf(
            entry(ImportantDateType.BIRTHDAY_SOLAR, p, PartialDate(1990, 10, 16)),
            // 31 天后，超出 30 天窗口
            entry(
                ImportantDateType.DEATH_ANNIVERSARY, p,
                PartialDate(2016, 11, 15)
            )
        )
        val (dToday, dNext) = AffairTimeline.dateRows(
            dates, emptyMap(), today, passthroughLunar)
        assertTrue(dToday.isEmpty())
        assertTrue(dNext.isEmpty())
    }

    @Test
    fun lunarEntry_carriesLunarText() {
        val p = person("p1", "张三")
        val dates = listOf(
            entry(
                ImportantDateType.DEATH_ANNIVERSARY, p,
                PartialDate(2016, 10, 25),
                calendar = CalendarType.LUNAR
            )
        )
        // 直通历法下农历 10/25 → 公历 2026-10-25，落在窗口内
        val (_, dNext) = AffairTimeline.dateRows(
            dates, emptyMap(), today, passthroughLunar)
        assertEquals(1, dNext.size)
        assertEquals("农历十月廿五", dNext[0].lunarText)
    }

    @Test
    fun titleOverridesName_inLabel() {
        val dad = person("dad", "张三")
        val mom = person("mom", "李四")
        val dates = listOf(
            entry(
                ImportantDateType.LICENSE_ANNIVERSARY, dad,
                PartialDate(2000, 10, 20),
                partnerId = "mom", partnerName = mom.name
            )
        )
        val titles = mapOf("dad" to "爸爸", "mom" to "妈妈")
        val (_, dNext) = AffairTimeline.dateRows(
            dates, titles, today, passthroughLunar)
        assertEquals("爸爸妈妈的领证纪念日", dNext[0].label)
    }
}
