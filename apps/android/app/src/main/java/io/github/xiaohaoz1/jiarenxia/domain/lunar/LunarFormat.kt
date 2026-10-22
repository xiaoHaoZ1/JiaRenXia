package io.github.xiaohaoz1.jiarenxia.domain.lunar

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import java.time.LocalDate

/** 农历月名与日名的显示格式化。 */
object LunarFormat {

    private val MONTH_NAMES = listOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    /** 农历月名（1..12；leap 时加“闰”前缀）。 */
    fun monthName(month: Int, leap: Boolean = false): String =
        (if (leap) "闰" else "") + MONTH_NAMES.getOrElse(month - 1) { month.toString() + "月" }

    /** 全部农历月名（下拉用；leapMonth 年该月显示闰字）。 */
    fun monthNames(leapMonth: Int? = null): List<String> =
        MONTH_NAMES.mapIndexed { i, n -> monthName(i + 1, leapMonth == i + 1) }

    /** 农历日名（1..30）：初一…初十 / 十一…十九 / 二十 / 廿一…廿九 / 三十。 */
    fun dayName(day: Int): String = when (day) {
        20 -> "二十"
        30 -> "三十"
        in 1..10 -> "初" + DIGITS[day]
        in 11..19 -> "十" + DIGITS[day - 10]
        in 21..29 -> "廿" + DIGITS[day - 20]
        else -> day.toString()
    }

    private val DIGITS = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

    /**
     * 生日显示文案：
     * SOLAR —— "1964年8月20日" / "8月20日（未填年份）"
     * LUNAR —— "农历 1964年八月十五" / "农历 闰六月初三（未填年份）"
     */
    fun formatBirthday(b: BirthdayValue?): String = when {
        b == null -> "未填写"
        b.calendar == CalendarType.SOLAR -> {
            if (b.date.year != null)
                "${b.date.year}年${b.date.month}月${b.date.day}日"
            else "${b.date.month}月${b.date.day}日（未填年份）"
        }
        else -> {
            val md = monthName(b.date.month, b.leapMonth) + dayName(b.date.day)
            if (b.date.year != null) "农历 ${b.date.year}年$md"
            else "农历 $md（未填年份）"
        }
    }

    /** 农历原文短格式：“农历八月十五”/“农历闰六月初三”；非农历返回 null。 */
    fun lunarShort(b: BirthdayValue?): String? =
        if (b?.calendar != CalendarType.LUNAR) null
        else "农历" + monthName(b.date.month, b.leapMonth) + dayName(b.date.day)

    /**
     * 农历生日的公历提示：落在今年 → "今年公历 9月25日"，明年 → "明年公历 9月25日"。
     */
    fun nextSolarHint(b: BirthdayValue, today: LocalDate, lunar: LunarCalendar): String? {
        val occ = BirthdayMath.nextOccurrence(b, today, lunar) ?: return null
        val yearTag = if (occ.year == today.year) "今年" else "明年"
        return yearTag + "公历 " + occ.monthValue + "月" + occ.dayOfMonth + "日"
    }
}
