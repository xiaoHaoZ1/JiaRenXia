package io.github.xiaohaoz1.jiarenxia.domain.lunar

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * IcuLunarCalendar 锚点校验（DEVELOPMENT_PLAN 6.2；锚点已对照多个权威来源核实）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class IcuLunarCalendarTest {

    private val lunar = IcuLunarCalendar()

    @Test
    fun 农历转公历锚点() {
        assertEquals(LocalDate.of(2026, 2, 17), lunar.toSolar(2026, 1, 1, false))
        assertEquals(LocalDate.of(2025, 1, 29), lunar.toSolar(2025, 1, 1, false))
        assertEquals(LocalDate.of(2023, 1, 22), lunar.toSolar(2023, 1, 1, false))
        assertEquals(LocalDate.of(2026, 9, 25), lunar.toSolar(2026, 8, 15, false))
        assertEquals(LocalDate.of(2027, 9, 15), lunar.toSolar(2027, 8, 15, false))
    }

    @Test
    fun 闰月锚点() {
        // 2025 闰六月初一
        assertEquals(LocalDate.of(2025, 7, 25), lunar.toSolar(2025, 6, 1, true))
        // 公历 → 农历回读
        val ld = lunar.fromSolar(LocalDate.of(2025, 7, 25))
        assertEquals(LunarDate(2025, 6, 1, true), ld)
        assertEquals(6, lunar.leapMonthOf(2025))
        assertEquals(2, lunar.leapMonthOf(2023))
        assertNull(lunar.leapMonthOf(2026))
    }

    @Test
    fun 月天数与不存在的农历日() {
        // 2025 闰六月为小月（29 天）
        assertEquals(29, lunar.daysInMonth(2025, 6, true))
        assertNull(lunar.toSolar(2025, 6, 30, true))
        // 2026 没有闰月
        assertNull(lunar.toSolar(2026, 6, 1, true))
    }

    @Test
    fun 公历农历往返() {
        var d = LocalDate.of(1950, 1, 1)
        val end = LocalDate.of(2049, 12, 31)
        while (d <= end) {
            val ld = lunar.fromSolar(d)
            assertEquals(d, lunar.toSolar(ld.year, ld.month, ld.day, ld.leap))
            d = d.plusDays(37)
        }
    }
}
