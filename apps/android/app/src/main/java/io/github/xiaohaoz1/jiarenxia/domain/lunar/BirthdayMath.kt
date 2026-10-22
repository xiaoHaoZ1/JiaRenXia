package io.github.xiaohaoz1.jiarenxia.domain.lunar

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 双生日归并出的“真实出生日”（公历）：
 * [date] 为可确定的唯一出生日；两份完整生日换算结果不一致时 [conflict]=true
 * 且 [date]=null——冲突不猜、未知不猜。
 */
data class ResolvedBirthDate(val date: LocalDate?, val conflict: Boolean)

/**
 * 生日换算（纯函数；农历通过注入的 [LunarCalendar] 计算，便于单测替换）。
 */
object BirthdayMath {

    /** 出生当天的公历日期；无年份（或农历日不存在）时为 null —— 不参与长幼比较。 */
    fun solarBirthDate(b: BirthdayValue, lunar: LunarCalendar): LocalDate? {
        if (b.calendar == CalendarType.SOLAR) return b.date.toLocalDateOrNull()
        val year = b.date.year ?: return null
        return lunar.toSolar(year, b.date.month, b.date.day, b.leapMonth)
    }

    /** 距 today 最近的一次生日（公历日）。SOLAR 沿用 2/29→2/28 规则。 */
    fun nextOccurrence(b: BirthdayValue, today: LocalDate, lunar: LunarCalendar): LocalDate? {
        if (b.calendar == CalendarType.SOLAR) return b.date.nextOccurrence(today)
        val y = lunar.fromSolar(today).year
        for (yy in listOf(y - 1, y, y + 1)) {
            val occ = occurrenceInLunarYear(b, yy, lunar) ?: continue
            if (occ >= today) return occ
        }
        return null
    }

    /** 距下次生日的天数；农历日完全无法计算时为 null。 */
    fun daysUntil(b: BirthdayValue, today: LocalDate, lunar: LunarCalendar): Int? =
        nextOccurrence(b, today, lunar)?.let {
            ChronoUnit.DAYS.between(today, it).toInt()
        }

    /**
     * 由一个人的全部生日归并出可确定的真实出生日（年龄/长幼共用）：
     * - 只有一份可解析的完整日期 → 用它；
     * - 两份完整日期换算一致 → 采用；
     * - 换算结果不一致 → conflict=true，date=null（冲突不猜）；
     * - 都没有年份 / 都解析失败 → date=null（未知，不伪造精确生日）。
     */
    fun resolveBirthDate(
        values: List<BirthdayValue>, lunar: LunarCalendar
    ): ResolvedBirthDate {
        val dates = values.mapNotNull { solarBirthDate(it, lunar) }.distinct()
        return when {
            dates.size > 1 -> ResolvedBirthDate(null, conflict = true)
            dates.size == 1 -> ResolvedBirthDate(dates[0], conflict = false)
            else -> ResolvedBirthDate(null, conflict = false)
        }
    }

    /**
     * 该农历年内的生日发生日（公历）。
     * 闰月出生者在无此闰月的年份按对应正常月处理；
     * 月天数不足时取该月最后一天（如闰六月无三十则廿九）。
     */
    private fun occurrenceInLunarYear(
        b: BirthdayValue, lunarYear: Int, lunar: LunarCalendar
    ): LocalDate? {
        val month = b.date.month
        val leap = b.leapMonth && lunar.leapMonthOf(lunarYear) == month
        val days = lunar.daysInMonth(lunarYear, month, leap) ?: return null
        return lunar.toSolar(lunarYear, month, minOf(b.date.day, days), leap)
    }
}
