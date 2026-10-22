package io.github.xiaohaoz1.jiarenxia.domain.lunar

import java.time.LocalDate

/** 农历日期。 */
data class LunarDate(val year: Int, val month: Int, val day: Int, val leap: Boolean)

/**
 * 农历历法抽象（domain.lunar）。
 * year 为该农历年开始时所在的公历年；month 1..12，day 1..30；leap 表示闰月。
 */
interface LunarCalendar {
    /** 农历 → 公历；该农历日不存在时返回 null（如无此闰月、该月无三十）。 */
    fun toSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate?

    /** 公历 → 农历。 */
    fun fromSolar(date: LocalDate): LunarDate

    /** 该农历年的闰月是几月；无闰月返回 null。 */
    fun leapMonthOf(lunarYear: Int): Int?

    /** 该农历月的天数；该月不存在返回 null。 */
    fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean): Int?
}
