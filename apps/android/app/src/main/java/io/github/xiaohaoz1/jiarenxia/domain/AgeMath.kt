package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import java.time.LocalDate

/** 年龄与享年（V0.8 纪念日批）。农历先换算成公历；双生日取归并出的真实出生日。 */
object AgeMath {

    /** 归并出生日 → 等价的公历生日值（供现有换算逻辑复用）。 */
    private fun resolvedValue(
        birthdays: List<BirthdayValue>, lunar: LunarCalendar
    ): BirthdayValue? = BirthdayMath.resolveBirthDate(birthdays, lunar).date?.let {
        BirthdayValue(PartialDate(it.year, it.monthValue, it.dayOfMonth), CalendarType.SOLAR)
    }

    /** 在世年龄（周岁）：生日没过少一岁；无确定出生年份返回 null。 */
    fun ageAt(birthdays: List<BirthdayValue>, today: LocalDate,
        lunar: LunarCalendar): Int? {
        val born = BirthdayMath.resolveBirthDate(birthdays, lunar).date ?: return null
        if (born.year <= 0) return null
        var age = today.year - born.year
        if (today.monthValue < born.monthValue ||
            (today.monthValue == born.monthValue &&
                today.dayOfMonth < born.dayOfMonth)
        ) age--
        return if (age >= 0) age else null
    }

    /**
     * 页头文案：在世 “62 岁”；已故 “享年 81 岁”/“享年约 81 岁”。
     * 无生日、年份缺失或两份完整生日冲突时返回 null。
     */
    fun ageText(
        birthdays: List<BirthdayValue>,
        livingStatus: io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus,
        death: BirthdayValue?, today: LocalDate,
        lunar: LunarCalendar): String? {
        val b = resolvedValue(birthdays, lunar) ?: return null
        return if (livingStatus ==
            io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.DECEASED
        ) {
            if (death == null) null
            else lifespan(b, death, lunar)?.let { (y, approx) ->
                "享年" + (if (approx) "约 " else " ") + y + " 岁"
            }
        } else ageAt(birthdays, today, lunar)?.let { it.toString() + " 岁" }
    }

    /**
     * 享年（周岁）：death - birth。返回 (岁数, 是否约数)。
     * 两个日期都有完整年月日 → 准确；只知道年份 → 年份差并标注“约”；
     * 任一年份缺失 → null。
     */
    fun lifespan(birth: BirthdayValue, death: BirthdayValue,
        lunar: LunarCalendar): Pair<Int, Boolean>? {
        val by = birth.date.year ?: return null
        val dy = death.date.year ?: return null
        val b = BirthdayMath.solarBirthDate(birth, lunar)
        val d = BirthdayMath.solarBirthDate(death, lunar)
        if (b != null && d != null) {
            var age = d.year - b.year
            if (d.monthValue < b.monthValue ||
                (d.monthValue == b.monthValue && d.dayOfMonth < b.dayOfMonth)
            ) age--
            return if (age >= 0) age to false else null
        }
        // 只知年份：按年份差，标注“约”
        return (dy - by).let { if (it >= 0) it to true else null }
    }
}
