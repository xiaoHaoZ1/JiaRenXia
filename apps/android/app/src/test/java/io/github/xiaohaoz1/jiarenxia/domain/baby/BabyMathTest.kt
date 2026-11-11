package io.github.xiaohaoz1.jiarenxia.domain.baby

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 宝宝年龄：第 N 天、满月/百天/周岁边界、月末与闰年。 */
class BabyMathTest {

    private val birth = LocalDate.of(2027, 1, 18)

    @Test
    fun `出生当天是第1天_前一天为null`() {
        assertEquals(1, BabyMath.dayNumber(birth, birth))
        assertEquals(18, BabyMath.dayNumber(birth, birth.plusDays(17)))
        assertNull(BabyMath.dayNumber(birth, birth.minusDays(1)))
        assertNull(BabyMath.ageText(birth, birth.minusDays(1)))
    }

    @Test
    fun `满月前一天与当天`() {
        val full = BabyMath.fullMonthDate(birth) // 2027-02-18
        assertEquals(LocalDate.of(2027, 2, 18), full)
        assertEquals("出生第 31 天", BabyMath.ageText(birth, full.minusDays(1)))
        assertEquals("1 个月", BabyMath.ageText(birth, full))
    }

    @Test
    fun `月末出生满月收敛到月底`() {
        // 1 月 31 日出生 → 满月 = 2 月末（平年 28 天）
        val b = LocalDate.of(2027, 1, 31)
        assertEquals(LocalDate.of(2027, 2, 28), BabyMath.fullMonthDate(b))
        assertEquals("出生第 28 天", BabyMath.ageText(b, LocalDate.of(2027, 2, 27)))
        assertEquals("1 个月", BabyMath.ageText(b, LocalDate.of(2027, 2, 28)))
        // 闰年 2 月 29 日出生 → 满月 = 3 月 29 日
        val leap = LocalDate.of(2028, 2, 29)
        assertEquals(LocalDate.of(2028, 3, 29), BabyMath.fullMonthDate(leap))
        assertEquals("1 个月 1 天", BabyMath.ageText(leap, LocalDate.of(2028, 3, 30)))
    }

    @Test
    fun `百天日`() {
        assertEquals(
            LocalDate.of(2027, 4, 27), BabyMath.hundredDaysDate(birth))
        // 2027-01-18 + 99 天 = 2027-04-27
    }

    @Test
    fun `整月加余天与整月`() {
        // 满月后第 12 天：1 个月 12 天
        assertEquals(
            "1 个月 12 天",
            BabyMath.ageText(birth, birth.plusMonths(1).plusDays(12)))
        // 恰好 2 个月：2 个月
        assertEquals(
            "2 个月",
            BabyMath.ageText(birth, birth.plusMonths(2)))
        // 11 个月 30 天
        assertEquals(
            "11 个月 30 天",
            BabyMath.ageText(birth, birth.plusMonths(11).plusDays(30)))
    }

    @Test
    fun `满周岁返回null`() {
        assertNull(BabyMath.ageText(birth, BabyMath.firstBirthday(birth)))
        assertNull(BabyMath.ageText(birth, birth.plusYears(2)))
    }
}
