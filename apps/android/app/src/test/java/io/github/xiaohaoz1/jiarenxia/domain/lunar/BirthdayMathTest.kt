package io.github.xiaohaoz1.jiarenxia.domain.lunar

import android.os.Build
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** BirthdayMath 的公历/农历下一次生日计算。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class BirthdayMathTest {

    private val lunar = IcuLunarCalendar()

    private fun lunarB(month: Int, day: Int, year: Int? = null, leap: Boolean = false) =
        BirthdayValue(PartialDate(year, month, day), CalendarType.LUNAR, leap)

    @Test
    fun 农历无年份_跨年取明年() {
        // 农历八月十五无年份；2026-10-02 已过 2026-09-25 → 2027-09-15
        val b = lunarB(8, 15)
        val next = BirthdayMath.nextOccurrence(b, LocalDate.of(2026, 10, 2), lunar)
        assertEquals(LocalDate.of(2027, 9, 15), next)
        assertEquals(348, BirthdayMath.daysUntil(b, LocalDate.of(2026, 10, 2), lunar))
    }

    @Test
    fun 农历无年份_今年未到() {
        val b = lunarB(8, 15)
        val next = BirthdayMath.nextOccurrence(b, LocalDate.of(2026, 9, 20), lunar)
        assertEquals(LocalDate.of(2026, 9, 25), next)
        assertEquals(5, BirthdayMath.daysUntil(b, LocalDate.of(2026, 9, 20), lunar))
    }

    @Test
    fun 闰月出生_无闰月年份按正常月() {
        // 闰六月十五出生；2026 无闰六月 → 按六月十五
        val b = lunarB(6, 15, leap = true)
        val next = BirthdayMath.nextOccurrence(b, LocalDate.of(2026, 1, 1), lunar)
        assertEquals(lunar.toSolar(2026, 6, 15, false), next)
    }

    @Test
    fun 农历三十日_小月回退到廿九() {
        // 闰六月三十出生；2025 闰六月仅 29 天（初一 = 2025-07-25）→ 廿九 = 2025-08-22
        val b = lunarB(6, 30, leap = true)
        val next = BirthdayMath.nextOccurrence(b, LocalDate.of(2025, 7, 1), lunar)
        assertEquals(LocalDate.of(2025, 8, 22), next)
    }

    @Test
    fun 公历闰日规则不变() {
        // 2 月 29 日：平年按 28 日
        val b = BirthdayValue(PartialDate(2000, 2, 29))
        assertEquals(
            LocalDate.of(2025, 2, 28),
            BirthdayMath.nextOccurrence(b, LocalDate.of(2025, 3, 1).minusDays(1), lunar)
        )
    }

    @Test
    fun 出生公历日期() {
        // 农历带年份 → toSolar；无年份 → null（不参与长幼）
        val withYear = lunarB(8, 15, year = 1964)
        assertEquals(
            lunar.toSolar(1964, 8, 15, false),
            BirthdayMath.solarBirthDate(withYear, lunar)
        )
        assertNull(BirthdayMath.solarBirthDate(lunarB(8, 15), lunar))
    }
}
