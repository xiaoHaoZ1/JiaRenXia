package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 年龄 / 享年（V0.8）。 */
class AgeMathTest {

    private val lunar = IcuLunarCalendar()
    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun 在世年龄_生日已过与未过() {
        val b = BirthdayValue(PartialDate(1964, 10, 2))
        // 10/2 还没到 → 61
        assertEquals(61, AgeMath.ageAt(listOf(b), today, lunar))
        assertEquals(62, AgeMath.ageAt(listOf(b), LocalDate.of(2026, 10, 2), lunar))
    }

    @Test
    fun 无年份不显示() {
        assertNull(AgeMath.ageAt(
            listOf(BirthdayValue(PartialDate(null, 5, 1))), today, lunar))
    }

    @Test
    fun 享年_日期齐全() {
        val birth = BirthdayValue(PartialDate(1940, 3, 1))
        val death = BirthdayValue(PartialDate(2021, 2, 28))
        // 2021-02-28 还没过 3/1 生日 → 80
        assertEquals(80 to false, AgeMath.lifespan(birth, death, lunar))
    }

    @Test
    fun 享年_只知年份按年差并标约() {
        val birth = BirthdayValue(PartialDate(1940, 3, 1))
        // 无效公历日（2/30）换算不出完整日期 → 按年份差 81 标“约”
        val death = BirthdayValue(PartialDate(2021, 2, 30))
        assertEquals(81 to true, AgeMath.lifespan(birth, death, lunar))
    }

    @Test
    fun 享年_年份缺失不显示() {
        val birth = BirthdayValue(PartialDate(null, 3, 1))
        val death = BirthdayValue(PartialDate(2021, 3, 1))
        assertNull(AgeMath.lifespan(birth, death, lunar))
    }

    @Test
    fun 页头文案() {
        assertEquals("62 岁", AgeMath.ageText(
            listOf(BirthdayValue(PartialDate(1964, 3, 1))),
            LivingStatus.ALIVE, null, today, lunar))
        assertEquals("享年 80 岁", AgeMath.ageText(
            listOf(BirthdayValue(PartialDate(1940, 3, 1))),
            LivingStatus.DECEASED,
            BirthdayValue(PartialDate(2021, 2, 28)), today, lunar))
        assertEquals("享年约 81 岁", AgeMath.ageText(
            listOf(BirthdayValue(PartialDate(1940, 3, 1))),
            LivingStatus.DECEASED,
            BirthdayValue(PartialDate(2021, 2, 30)), today, lunar))
        assertNull(AgeMath.ageText(
            emptyList(), LivingStatus.ALIVE, null, today, lunar))
    }

    @Test
    fun 去世年份缺失_同样不显示() {
        val birth = BirthdayValue(PartialDate(1940, 3, 1))
        val death = BirthdayValue(PartialDate(null, 5, 1))
        assertNull(AgeMath.lifespan(birth, death, lunar))
    }
}
