package io.github.xiaohaoz1.jiarenxia.domain.lunar

import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * 基于 android.icu.util.ChineseCalendar 的农历实现。
 *
 * 约定：
 * - 时区固定 UTC，日期粒度用 floorDiv(millis, 86400000) 取 epochDay；
 * - EXTENDED_YEAR = lunarYear + 2637（lunarYear 为该农历年开始所在的公历年）；
 * - MONTH 0 起；IS_LEAP_MONTH 0/1；
 * - 写字段后回读校验：ICU 宽容回滚（如 29 日月的 30 日、无该闰月）会改变字段，
 *   读回不一致即该农历日不存在 → null；
 * - 支持的农历年范围 1900..2100。
 */
class IcuLunarCalendar @Inject constructor() : LunarCalendar {

    // 缓存该年闰月（0 = 无闰月，不可存 null）
    private val leapCache = ConcurrentHashMap<Int, Int>()

    private fun newCal(): ChineseCalendar =
        ChineseCalendar(TimeZone.getTimeZone("UTC"))

    override fun toSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate? {
        if (year !in MIN_YEAR..MAX_YEAR) return null
        val cal = newCal()
        cal.clear()
        cal.set(ChineseCalendar.EXTENDED_YEAR, year + YEAR_OFFSET)
        cal.set(ChineseCalendar.MONTH, month - 1)
        cal.set(ChineseCalendar.IS_LEAP_MONTH, if (leap) 1 else 0)
        cal.set(ChineseCalendar.DAY_OF_MONTH, day)
        val millis = cal.timeInMillis
        // 宽容回滚校验：字段被改动说明该农历日不存在
        if (cal.get(ChineseCalendar.EXTENDED_YEAR) != year + YEAR_OFFSET ||
            cal.get(ChineseCalendar.MONTH) != month - 1 ||
            cal.get(ChineseCalendar.IS_LEAP_MONTH) != (if (leap) 1 else 0) ||
            cal.get(ChineseCalendar.DAY_OF_MONTH) != day
        ) return null
        return LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY))
    }

    override fun fromSolar(date: LocalDate): LunarDate {
        val cal = newCal()
        cal.timeInMillis = date.toEpochDay() * MILLIS_PER_DAY
        return LunarDate(
            year = cal.get(ChineseCalendar.EXTENDED_YEAR) - YEAR_OFFSET,
            month = cal.get(ChineseCalendar.MONTH) + 1,
            day = cal.get(ChineseCalendar.DAY_OF_MONTH),
            leap = cal.get(ChineseCalendar.IS_LEAP_MONTH) == 1
        )
    }

    override fun leapMonthOf(lunarYear: Int): Int? =
        leapCache.getOrPut(lunarYear) {
            (1..12).firstOrNull { toSolar(lunarYear, it, 1, true) != null } ?: 0
        }.takeIf { it != 0 }

    override fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean): Int? =
        when {
            toSolar(lunarYear, month, 30, leap) != null -> 30
            toSolar(lunarYear, month, 29, leap) != null -> 29
            else -> null
        }

    companion object {
        /** lunarYear 与 EXTENDED_YEAR 的固定偏移。 */
        const val YEAR_OFFSET = 2637
        const val MIN_YEAR = 1900
        const val MAX_YEAR = 2100
        private const val MILLIS_PER_DAY = 86_400_000L
    }
}
