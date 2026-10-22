package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import java.time.LocalDate

/** 提醒页一行：同一天的阳历+农历生日合并为一行展示。 */
data class BirthdayRow(
    val person: Person,
    /** 该行包含的生日（阳历在前；同一天到期时最多两条）。 */
    val birthdays: List<BirthdayValue>,
    val title: String?,
    val daysUntil: Int,
    val reminderEnabled: Boolean,
    /** 下一次生日的公历日期（农历生日已换算）。 */
    val nextSolar: LocalDate
) {
    /** 兼容旧取法：第一个生日（阳历优先）。 */
    val birthday: BirthdayValue get() = birthdays.first()
    /** 阳历与农历生日在同一天到期（需要给“双历同日”说明）。 */
    val merged: Boolean get() = birthdays.size > 1
    /** 农历生日原文（“农历八月十五”）；行内无农历生日为 null。 */
    val lunarText: String?
        get() = birthdays.firstOrNull {
            it.calendar == io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR
        }?.let {
            io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat.lunarShort(it)
        }
}

/**
 * “未来 30 天”生日列表（PRD 4.4 提醒页 / DEVELOPMENT_PLAN 2.5 三）。
 * 只收 daysUntil 0..30；按天数再按姓名排序；拆成“今天生日”和“未来 30 天”两段。
 * 同一个人阳历/农历生日落在同一公历日时合并为一行。
 */
object UpcomingBirthdays {

    const val WINDOW_DAYS = 30

    data class Result(
        val today: List<BirthdayRow>,
        val upcoming: List<BirthdayRow>
    )

    fun build(
        entries: List<BirthdayEntry>,
        titles: Map<String, String?>,
        today: LocalDate,
        lunar: LunarCalendar
    ): Result {
        // 每条生日行独立算下一次发生日；同一人同一公历日合并
        val rows = entries.mapNotNull { e ->
            val until = BirthdayMath.daysUntil(e.birthday, today, lunar) ?: return@mapNotNull null
            if (until > WINDOW_DAYS) return@mapNotNull null
            Triple(e, until, BirthdayMath.nextOccurrence(e.birthday, today, lunar)!!)
        }.groupBy { (e, _, next) -> e.person.id to next }
            .map { (_, group) ->
                val e0 = group.first().first
                BirthdayRow(
                    person = e0.person,
                    birthdays = group.map { it.first.birthday }
                        .sortedBy { it.calendar },
                    title = if (e0.person.isSelf) null else titles[e0.person.id],
                    daysUntil = group.minOf { it.second },
                    reminderEnabled = group.any { it.first.reminderEnabled },
                    nextSolar = group.first().third
                )
            }.sortedWith(compareBy({ it.daysUntil }, { it.person.name }))
        return Result(
            today = rows.filter { it.daysUntil == 0 },
            upcoming = rows.filter { it.daysUntil > 0 }
        )
    }
}
