package io.github.xiaohaoz1.jiarenxia.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PartialDateTest {

    @Test
    fun isValid_无年份闰日合法() {
        assertTrue(PartialDate(null, 2, 29).isValid())
    }

    @Test
    fun isValid_闰年2月29日合法() {
        assertTrue(PartialDate(2000, 2, 29).isValid())
    }

    @Test
    fun isValid_平年2月29日非法() {
        assertFalse(PartialDate(1999, 2, 29).isValid())
    }

    @Test
    fun isValid_四月没有31日() {
        assertFalse(PartialDate(null, 4, 31).isValid())
        assertFalse(PartialDate(2000, 4, 31).isValid())
    }

    @Test
    fun isValid_月份越界() {
        assertFalse(PartialDate(null, 0, 15).isValid())
        assertFalse(PartialDate(1990, 13, 1).isValid())
    }

    @Test
    fun isComplete_与toLocalDate() {
        assertTrue(PartialDate(1990, 1, 1).isComplete)
        assertFalse(PartialDate(null, 1, 1).isComplete)
        assertEquals(LocalDate.of(1990, 1, 1), PartialDate(1990, 1, 1).toLocalDateOrNull())
        assertNull(PartialDate(null, 1, 1).toLocalDateOrNull())
        assertNull(PartialDate(1999, 2, 29).toLocalDateOrNull())
    }

    @Test
    fun compareSeniority_完整且不同() {
        val a = PartialDate(1970, 3, 15)
        val b = PartialDate(1965, 8, 20)
        assertEquals(RelativeAgeOrder.YOUNGER, PartialDate.compareSeniority(a, b))
        assertEquals(RelativeAgeOrder.OLDER, PartialDate.compareSeniority(b, a))
    }

    @Test
    fun compareSeniority_同日返回null() {
        val a = PartialDate(1990, 5, 5)
        val b = PartialDate(1990, 5, 5)
        assertNull(PartialDate.compareSeniority(a, b))
    }

    @Test
    fun compareSeniority_缺少年份返回null() {
        val full = PartialDate(1990, 5, 5)
        val noYear = PartialDate(null, 3, 3)
        assertNull(PartialDate.compareSeniority(full, noYear))
        assertNull(PartialDate.compareSeniority(noYear, full))
        assertNull(PartialDate.compareSeniority(noYear, noYear))
    }

    @Test
    fun compareSeniority_null输入返回null() {
        val full = PartialDate(1990, 5, 5)
        assertNull(PartialDate.compareSeniority(null, full))
        assertNull(PartialDate.compareSeniority(full, null))
        assertNull(PartialDate.compareSeniority(null, null))
    }

    @Test
    fun occurrenceIn_平年闰日映射228() {
        val d = PartialDate(null, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), d.occurrenceIn(2025))
        assertEquals(LocalDate.of(2028, 2, 29), d.occurrenceIn(2028))
    }

    @Test
    fun daysUntilNext_当天为0() {
        val d = PartialDate(null, 6, 15)
        assertEquals(0, d.daysUntilNext(LocalDate.of(2025, 6, 15)))
    }

    @Test
    fun daysUntilNext_明天为1() {
        val d = PartialDate(null, 6, 15)
        assertEquals(1, d.daysUntilNext(LocalDate.of(2025, 6, 14)))
    }

    @Test
    fun daysUntilNext_跨年() {
        val d = PartialDate(null, 1, 1)
        assertEquals(1, d.daysUntilNext(LocalDate.of(2025, 12, 31)))
        assertEquals(LocalDate.of(2026, 1, 1), d.nextOccurrence(LocalDate.of(2025, 12, 31)))
    }

    @Test
    fun daysUntilNext_今年已过取下一年() {
        val d = PartialDate(null, 6, 1)
        assertEquals(LocalDate.of(2026, 6, 1), d.nextOccurrence(LocalDate.of(2025, 6, 2)))
    }
}
