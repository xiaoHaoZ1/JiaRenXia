package io.github.xiaohaoz1.jiarenxia.domain.anniversary

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import java.time.LocalDate
import java.time.Period

/**
 * 夫妻共享纪念日（结婚 / 领证 / 恋爱）的“已经多久了”计算与文案。
 * 详情页与碰一碰纪念页共用同一套口径。
 */
object AnniversaryDurations {

    /**
     * 起始日 → 时长文案：“8 年 3 个月” / “6 个月” / “46 天”。
     * 起始日在未来返回 null（不编造时长）；恰好当天返回“1 天”。
     */
    fun text(start: LocalDate, today: LocalDate): String? {
        if (start.isAfter(today)) return null
        val p = Period.between(start, today)
        return when {
            p.years > 0 ->
                p.years.toString() + " 年" +
                    (if (p.months > 0) " " + p.months + " 个月" else "")
            p.months > 0 ->
                p.months.toString() + " 个月" +
                    (if (p.days > 0) " " + p.days + " 天" else "")
            else -> java.time.temporal.ChronoUnit.DAYS
                .between(start, today).toString() + " 天"
        }
    }

    /** 记录值 → 公历起始日；只有月日（无年份）或农历无法换算时返回 null。 */
    fun startDate(v: BirthdayValue, lunar: LunarCalendar): LocalDate? =
        if (v.date.year == null) null else BirthdayMath.solarBirthDate(v, lunar)

    /** 类型 → 时长动词（相恋 / 领证 / 结婚），拼在时长文案前。 */
    fun verb(type: ImportantDateType): String = when (type) {
        ImportantDateType.LICENSE_ANNIVERSARY -> "领证"
        ImportantDateType.DATING_ANNIVERSARY -> "相恋"
        else -> "结婚"
    }

    /** 类型 → 纪念日名（恋爱纪念日 / 领证纪念日 / 结婚纪念日）。 */
    fun typeName(type: ImportantDateType): String = when (type) {
        ImportantDateType.LICENSE_ANNIVERSARY -> "领证纪念日"
        ImportantDateType.DATING_ANNIVERSARY -> "恋爱纪念日"
        else -> "结婚纪念日"
    }

    /** 便捷合成：“相恋 8 年 3 个月”；算不出来返回 null。 */
    fun fact(type: ImportantDateType, start: LocalDate, today: LocalDate): String? =
        text(start, today)?.let { verb(type) + " " + it }
}
