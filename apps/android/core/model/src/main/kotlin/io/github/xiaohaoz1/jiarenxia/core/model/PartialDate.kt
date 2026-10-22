package io.github.xiaohaoz1.jiarenxia.core.model

import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit

/**
 * 可缺少年份的日期（生日等场景：允许只知道月日）。
 *
 * @param year 年份，可为空表示未知出生年份
 * @param month 月（1–12）
 * @param day 日
 */
data class PartialDate(
    val year: Int?,
    val month: Int,
    val day: Int
) {
    /** 是否包含完整年月日。 */
    val isComplete: Boolean get() = year != null

    /**
     * 日期是否合法。
     *
     * 有年份时必须是真实存在的日期（含闰年判断）；
     * 没有年份时按闰年校验月日，因此 2 月 29 日合法。
     */
    fun isValid(): Boolean {
        if (month !in 1..12) return false
        val checkYear = year ?: LEAP_YEAR_FOR_CHECK
        return try {
            LocalDate.of(checkYear, month, day)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 完整且合法时转为 [LocalDate]，否则为 null。 */
    fun toLocalDateOrNull(): LocalDate? =
        if (isComplete && isValid()) LocalDate.of(year!!, month, day) else null

    /**
     * 该日期在指定年份中的发生日。
     * 平年中 2 月 29 日按 2 月 28 日处理（显示与提醒一致）。
     */
    fun occurrenceIn(year: Int): LocalDate =
        if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) {
            LocalDate.of(year, 2, 28)
        } else {
            LocalDate.of(year, month, day)
        }

    /** 距 today 最近的一次发生日：本年的发生日不早于 today 则取本年，否则取下一年。 */
    fun nextOccurrence(today: LocalDate): LocalDate {
        val thisYear = occurrenceIn(today.year)
        return if (thisYear >= today) thisYear else occurrenceIn(today.year + 1)
    }

    /** 距下一次发生日的天数；返回 0 表示就是今天。 */
    fun daysUntilNext(today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, nextOccurrence(today)).toInt()

    companion object {
        /** 无年份校验时使用的闰年基准。 */
        private const val LEAP_YEAR_FOR_CHECK = 2000

        /**
         * 比较 a 相对 b 的长幼（出生更早者为 OLDER）。
         *
         * 仅当双方日期都完整、合法且不同才返回结果；
         * 日期相同或任一信息不完整时返回 null，不做猜测。
         */
        fun compareSeniority(a: PartialDate?, b: PartialDate?): RelativeAgeOrder? {
            val da = a?.toLocalDateOrNull() ?: return null
            val db = b?.toLocalDateOrNull() ?: return null
            return when {
                da < db -> RelativeAgeOrder.OLDER
                da > db -> RelativeAgeOrder.YOUNGER
                else -> null
            }
        }
    }
}
