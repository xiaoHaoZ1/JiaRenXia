package io.github.xiaohaoz1.jiarenxia.domain.lunar

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 农历格式化文案。 */
class LunarFormatTest {

    @Test
    fun 月名() {
        assertEquals("正月", LunarFormat.monthName(1))
        assertEquals("冬月", LunarFormat.monthName(11))
        assertEquals("腊月", LunarFormat.monthName(12))
        assertEquals("闰六月", LunarFormat.monthName(6, leap = true))
    }

    @Test
    fun 日名() {
        assertEquals("初一", LunarFormat.dayName(1))
        assertEquals("初十", LunarFormat.dayName(10))
        assertEquals("十五", LunarFormat.dayName(15))
        assertEquals("二十", LunarFormat.dayName(20))
        assertEquals("廿一", LunarFormat.dayName(21))
        assertEquals("廿九", LunarFormat.dayName(29))
        assertEquals("三十", LunarFormat.dayName(30))
    }

    @Test
    fun formatBirthday文案() {
        assertEquals(
            "1964年8月20日",
            LunarFormat.formatBirthday(BirthdayValue(PartialDate(1964, 8, 20)))
        )
        assertEquals(
            "8月20日（未填年份）",
            LunarFormat.formatBirthday(BirthdayValue(PartialDate(null, 8, 20)))
        )
        assertEquals(
            "农历 1964年八月十五",
            LunarFormat.formatBirthday(
                BirthdayValue(PartialDate(1964, 8, 15), CalendarType.LUNAR)
            )
        )
        assertEquals(
            "农历 闰六月初三（未填年份）",
            LunarFormat.formatBirthday(
                BirthdayValue(PartialDate(null, 6, 3), CalendarType.LUNAR, leapMonth = true)
            )
        )
    }
}
