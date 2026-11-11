package io.github.xiaohaoz1.jiarenxia.domain.pregnancy

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 孕期计算与文案（FAMILY_AFFAIRS 26）：
 * 预产期 ↔ 孕周/孕天数、距预产期天数、保存校验、UI 与测试共用的中文文案。
 * 纯函数、不依赖 Android。
 */
object PregnancyMath {

    /** 末次月经 → 预产期（+280 天）。 */
    fun dueFromLmp(lmp: LocalDate): LocalDate = lmp.plusDays(280)

    /** 已孕天数 = 280 − (预产期 − 今天)，下限夹到 0。 */
    fun gestationalDays(due: LocalDate, today: LocalDate): Int {
        val days = 280 - (due.toEpochDay() - today.toEpochDay()).toInt()
        return maxOf(days, 0)
    }

    /** 已孕整周数 = 孕天数 / 7。 */
    fun gestationalWeeks(due: LocalDate, today: LocalDate): Int =
        gestationalDays(due, today) / 7

    /** 本周已过天数 = 孕天数 % 7。 */
    fun dayInWeek(due: LocalDate, today: LocalDate): Int =
        gestationalDays(due, today) % 7

    /**
     * 距预产期天数：正 = 还有 N 天，0 = 就是今天，负 = 已过 N 天。
     */
    fun daysToDue(due: LocalDate, today: LocalDate): Int =
        (due.toEpochDay() - today.toEpochDay()).toInt()

    /**
     * 保存校验：按推导后的预产期判断，须落在
     * [today − 42 天, today + 280 天]（即孕 6 周到 40 周之间）。
     */
    fun isDueInValidRange(due: LocalDate, today: LocalDate): Boolean {
        val min = today.minusDays(42)
        val max = today.plusDays(280)
        return !due.isBefore(min) && !due.isAfter(max)
    }

    /**
     * 末次月经保存校验：须在 [today − 322 天, today] 之间
     * （对应推导预产期在 [today − 42 天, today + 280 天]）。
     */
    fun isLmpInValidRange(lmp: LocalDate, today: LocalDate): Boolean {
        val min = today.minusDays(322)
        return !lmp.isBefore(min) && !lmp.isAfter(today)
    }

    // ---------- 文案（UI 与测试共用） ----------

    private val CN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

    /** “孕 32 周 3 天”（数字两侧各一个半角空格）。 */
    fun gestationalText(due: LocalDate, today: LocalDate): String =
        "孕 " + gestationalWeeks(due, today) + " 周 " +
            dayInWeek(due, today) + " 天"

    /**
     * 预产期行：“预产期 2027年1月20日 · 还有 54 天” /
     * “预产期 2027年1月20日 · 就是今天” / “预产期 2027年1月20日 · 已过 3 天”。
     */
    fun dueLineText(due: LocalDate, today: LocalDate): String {
        val days = daysToDue(due, today)
        val tail = when {
            days > 0 -> "还有 " + days + " 天"
            days == 0 -> "就是今天"
            else -> "已过 " + (-days) + " 天"
        }
        return "预产期 " + due.format(CN_DATE) + " · " + tail
    }

    /**
     * 家庭页短状态：未到 “孕 32 周 3 天”；当天 “今天预产期”；
     * 已过 “已过预产期 3 天”。
     */
    fun familyShortText(due: LocalDate, today: LocalDate): String {
        val days = daysToDue(due, today)
        return when {
            days > 0 -> gestationalText(due, today)
            days == 0 -> "今天预产期"
            else -> "已过预产期 " + (-days) + " 天"
        }
    }

    /** “末次月经 2026年4月15日”。 */
    fun lmpLineText(lmp: LocalDate): String =
        "末次月经 " + lmp.format(CN_DATE)
}
