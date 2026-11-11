package io.github.xiaohaoz1.jiarenxia.domain.baby

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 宝宝年龄（FAMILY_AFFAIRS 26 第 3 批，纯 JVM）。
 * 出生日 = 归并出的确切阳历日期（年月日齐全才算，调用方保证）。
 *
 * - 出生当天是第 1 天：[dayNumber] = today − birth + 1；
 * - 满月日 = birth.plusMonths(1)（Java 自动取月末，如 1 月 31 日 → 2 月末）；
 * - 百天日 = birth + 99 天；
 * - 周岁前显示 “M 个月 D 天”（m 按 plusMonths 逐月推取最大整月数）。
 */
object BabyMath {

    /** 出生第几天（出生当天 = 1）；today 早于出生日返回 null。 */
    fun dayNumber(birth: LocalDate, today: LocalDate): Int? {
        if (today.isBefore(birth)) return null
        return ChronoUnit.DAYS.between(birth, today).toInt() + 1
    }

    /** 满月日（birth.plusMonths(1)，月末自动收敛）。 */
    fun fullMonthDate(birth: LocalDate): LocalDate = birth.plusMonths(1)

    /** 百天日（birth + 99 天）。 */
    fun hundredDaysDate(birth: LocalDate): LocalDate = birth.plusDays(99)

    /** 周岁日。 */
    fun firstBirthday(birth: LocalDate): LocalDate = birth.plusYears(1)

    /**
     * 宝宝年龄文案：today < 出生日 → null；
     * 满月前 → “出生第 N 天”；满月 ≤ today < 周岁 → “M 个月 D 天”（d=0 时 “M 个月”）；
     * 满周岁 → null（照旧显示岁数）。
     */
    fun ageText(birth: LocalDate, today: LocalDate): String? {
        if (today.isBefore(birth)) return null
        if (today.isBefore(fullMonthDate(birth))) {
            return "出生第 " + dayNumber(birth, today) + " 天"
        }
        val first = firstBirthday(birth)
        if (!today.isBefore(first)) return null
        // 最大整月数 m：birth.plusMonths(m) <= today < birth.plusMonths(m+1)
        var m = 1
        while (m < 12 && !birth.plusMonths(m + 1L).isAfter(today)) m++
        val anchor = birth.plusMonths(m.toLong())
        val d = ChronoUnit.DAYS.between(anchor, today).toInt()
        return if (d == 0) m.toString() + " 个月" else m.toString() + " 个月 " + d + " 天"
    }
}
