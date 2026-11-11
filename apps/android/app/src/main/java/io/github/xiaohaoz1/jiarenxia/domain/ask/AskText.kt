package io.github.xiaohaoz1.jiarenxia.domain.ask

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.baby.BabyMath
import io.github.xiaohaoz1.jiarenxia.domain.pregnancy.PregnancyMath
import java.time.LocalDate

/**
 * 「问一问」拼问题（FAMILY_AFFAIRS §25 方案 C / 问一问规则，纯 JVM）。
 * 只用已记录的字段拼问题，不推断未记录的事实；
 * 永远不写真名、手机号、住址、生日日期、其他家人姓名。
 */
object AskText {

    /** 「问一问」的称呼与年龄（各 ViewModel 由总览数据算好后传入）。 */
    data class PersonAsk(val label: String, val age: String?)

    /**
     * 称呼：[isSelf] 是“我” → `"我"`；有称谓 → `"我" + 称谓`；
     * 没有称谓 → `"我的一位家人"`。
     */
    fun label(isSelf: Boolean, kinTitle: String?): String =
        if (isSelf) {
            "我"
        } else {
            kinTitle?.takeIf { it.isNotBlank() }?.let { "我" + it }
                ?: "我的一位家人"
        }

    /**
     * 我视角的称谓（不含“我”）：本人 / 无关系且无自定义称呼 → null；
     * 其余取 displayTitle（自定义称呼 > 标准称谓 > 结构描述）。
     */
    fun kinTitle(overview: PersonOverview?): String? {
        if (overview == null || overview.person.isSelf) return null
        if (overview.result.kind == KinshipKind.UNRELATED &&
            overview.customTitle == null
        ) return null
        return overview.displayTitle
    }

    /**
     * 年龄约数：无确切出生年份或未满一岁 → null（省略）；
     * 1–9 岁 → `"{N} 岁"`；10 岁及以上 → `"{X}0 多岁"`（62 → `"60 多岁"`）。
     */
    fun age(years: Int?): String? = when {
        years == null || years <= 0 -> null
        years < 10 -> years.toString() + " 岁"
        else -> (years / 10 * 10).toString() + " 多岁"
    }

    /** 由总览算出 [PersonAsk]：label 用我视角称谓，age 用 [AgeMath.ageAt] 后分档。 */
    fun personAsk(
        overview: PersonOverview?,
        birthdays: List<io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue>,
        today: LocalDate,
        lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
    ): PersonAsk {
        val o = overview
        return PersonAsk(
            label = label(o?.person?.isSelf == true, kinTitle(o)),
            age = age(
                io.github.xiaohaoz1.jiarenxia.domain.AgeMath.ageAt(
                    birthdays, today, lunar)
            )
        )
    }

    /**
     * 用药问题：只写已记录的字段（规格/用量/用途/开始日期/用法），
     * 天数一律按“记录的……”表述，不写服药事实。
     */
    fun forMedication(
        label: String,
        age: String?,
        med: Medication,
        today: LocalDate
    ): String {
        val start = LocalDate.ofEpochDay(med.startEpochDay)
        return buildString {
            append(label)
            age?.let { append('，').append(it) }
            append("。记录的药是").append(med.name)
            med.spec?.takeIf { it.isNotBlank() }
                ?.let { append("，规格 ").append(it) }
            med.dose?.takeIf { it.isNotBlank() }
                ?.let { append("，每次 ").append(it) }
            med.purpose?.takeIf { it.isNotBlank() }
                ?.let { append("，记录的用途是").append(it) }
            append("，记录的开始日期是 ")
                .append(start.monthValue).append("月")
                .append(start.dayOfMonth).append("日，")
            append(usageSentence(med, today)).append('。')
            append(defaultQuestion(med.usage))
        }
    }

    /** 用法句（按记录的用法与日期推算，不含停用事实）。 */
    private fun usageSentence(med: Medication, today: LocalDate): String =
        when (med.usage) {
            MedUsage.SHORT_COURSE -> {
                val days = med.courseDays
                if (days == null) {
                    "疗程还没问清楚"
                } else {
                    val last = LocalDate.ofEpochDay(med.startEpochDay)
                        .plusDays((days - 1).toLong())
                    if (today.isAfter(last)) {
                        "记录的 " + days + " 天疗程已到期"
                    } else {
                        "记录的疗程是 " + days + " 天"
                    }
                }
            }
            MedUsage.LONG_TERM -> "记录为长期吃"
            MedUsage.AS_NEEDED -> "记录为需要时吃"
        }

    /** 默认问题：短期（含待确认、到期）/ 长期 / 需要时吃各一。 */
    private fun defaultQuestion(usage: MedUsage): String = when (usage) {
        MedUsage.SHORT_COURSE -> "这个药一般吃多久？能不能长期吃？"
        MedUsage.LONG_TERM -> "这个药长期吃要注意什么？"
        MedUsage.AS_NEEDED -> "这个药什么情况下吃？一天最多吃几次？"
    }

    /** 孕期问题：孕周整周数（[PregnancyMath.gestationalWeeks]）。 */
    fun forPregnancy(label: String, due: LocalDate, today: LocalDate): String =
        label + "怀孕 " + PregnancyMath.gestationalWeeks(due, today) +
            " 周。这个阶段要注意什么？"

    /**
     * 新生宝宝问题：固定“我家宝宝”（不用称谓）；
     * 未满月写出生第 N 天（出生当天为第 1 天），满月后写 M 个月（不带零头天数）。
     */
    fun forNewborn(birth: LocalDate, today: LocalDate): String {
        val head = if (today.isBefore(BabyMath.fullMonthDate(birth))) {
            "我家宝宝出生 " +
                (BabyMath.dayNumber(birth, today) ?: 1) + " 天。"
        } else {
            // 最大整月数：birth.plusMonths(m) <= today < birth.plusMonths(m+1)
            var m = 1
            while (m < 12 && !birth.plusMonths(m + 1L).isAfter(today)) m++
            "我家宝宝 " + m + " 个月。"
        }
        return head + "宝宝这个阶段要注意什么？妈妈坐月子要注意什么？"
    }
}
