package io.github.xiaohaoz1.jiarenxia.domain.elder

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
import io.github.xiaohaoz1.jiarenxia.domain.DateRow
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombCalendar
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombEvent
import io.github.xiaohaoz1.jiarenxia.reminder.UpcomingBirthdays
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 长辈模式首页的纯逻辑：家人名单与“最近的日子”合成。
 * 界面只消费这里的结果，不自己排序/过滤。
 */
object ElderHome {

    /** “最近的日子”行类型（同日排序：生日 → 纪念日/忌日 → 上坟）。 */
    enum class UpcomingKind { BIRTHDAY, DATE, TOMB }

    /** 长辈首页“最近的日子”一行。 */
    data class UpcomingRow(
        val kind: UpcomingKind,
        val date: LocalDate,
        /** “今天/明天/后天/N天后”。 */
        val dayLabel: String,
        val title: String,
        val subtitle: String?,
        /** 生日行的人物 id（今天且有默认号码时行尾显示“打电话”）。 */
        val personId: String? = null
    )

    /** 长辈首页“家人”一行。 */
    data class MemberRow(
        val personId: String,
        /** 称谓（无称谓用姓名；UNRELATED 一律用姓名）。 */
        val label: String,
        val name: String,
        /** 有称谓时副标题显示姓名。 */
        val hasTitle: Boolean,
        val avatarPath: String?
    )

    /**
     * 家人名单：在世、非本人的全部家人，顺序同家庭页（core + others）。
     * UNRELATED 也列出，标签用姓名。
     */
    fun members(overview: FamilyOverview?): List<MemberRow> {
        if (overview == null) return emptyList()
        return (overview.core + overview.others)
            .filter { ContactSchedule.eligible(it.person) }
            .map { o -> memberRow(o) }
    }

    private fun memberRow(o: PersonOverview): MemberRow {
        // 称谓 = 自定义称呼 > 标准称谓；都没有（含 UNRELATED）用姓名
        val title = if (o.result.kind == KinshipKind.UNRELATED) null
        else o.customTitle ?: o.result.standardTitle
        return MemberRow(
            personId = o.person.id,
            label = title ?: o.person.name,
            name = o.person.name,
            hasTitle = title != null,
            avatarPath = o.person.avatarPath
        )
    }

    /**
     * “最近的日子”：今天起 30 天内的生日（today+upcoming）、纪念日/忌日、上坟日。
     * 按日期排序；同日 生日 → 纪念日/忌日 → 上坟。
     */
    fun upcoming(
        birthdays: UpcomingBirthdays.Result,
        dates: Pair<List<DateRow>, List<DateRow>>,
        tomb: List<TombEvent>,
        today: LocalDate
    ): List<UpcomingRow> {
        val out = mutableListOf<UpcomingRow>()

        for (row in birthdays.today + birthdays.upcoming) {
            val title = (row.title ?: row.person.name) + "的生日"
            val parts = mutableListOf<String>()
            if (row.title != null) parts.add(row.person.name)
            row.lunarText?.let { parts.add(it) }
            // 年份已知时显示周岁（BirthdayValue.date.year）
            row.birthdays.firstNotNullOfOrNull { it.date.year }?.let { y ->
                parts.add((today.year - y).toString() + " 岁")
            }
            out.add(
                UpcomingRow(
                    kind = UpcomingKind.BIRTHDAY,
                    date = row.nextSolar,
                    dayLabel = dayLabel(ChronoUnit.DAYS.between(today, row.nextSolar).toInt()),
                    title = title,
                    subtitle = parts.joinToString(" · ").ifEmpty { null },
                    personId = row.person.id
                )
            )
        }

        for (row in dates.first + dates.second) {
            out.add(
                UpcomingRow(
                    kind = UpcomingKind.DATE,
                    date = row.occurrence,
                    dayLabel = dayLabel(row.daysUntil),
                    title = row.label,
                    subtitle = listOfNotNull(row.yearLabel, row.lunarText)
                        .joinToString(" · ").ifEmpty { null },
                    personId = row.personId
                )
            )
        }

        for (event in tomb) {
            out.add(
                UpcomingRow(
                    kind = UpcomingKind.TOMB,
                    date = event.date,
                    dayLabel = dayLabel(
                        ChronoUnit.DAYS.between(today, event.date).toInt()),
                    title = event.title,
                    subtitle = TombCalendar.timelineSubtitle(event)
                )
            )
        }

        return out.sortedWith(
            compareBy(
                { it.date },
                { it.kind.ordinal },
                { it.title }
            )
        )
    }

    /** 距今天数 → “今天/明天/后天/N天后”。 */
    fun dayLabel(daysUntil: Int): String = when (daysUntil) {
        0 -> "今天"
        1 -> "明天"
        2 -> "后天"
        else -> daysUntil.toString() + "天后"
    }

    /** 页头行：“11月7日 星期六 · 农历十月廿七”。 */
    fun headerLine(today: LocalDate, lunar: LunarCalendar): String {
        val week = arrayOf("一", "二", "三", "四", "五", "六", "日")
        val lunarDate = lunar.fromSolar(today)
        val lunarText = "农历" + LunarFormat.monthName(lunarDate.month, lunarDate.leap) +
            LunarFormat.dayName(lunarDate.day)
        return today.monthValue.toString() + "月" + today.dayOfMonth + "日 " +
            "星期" + week[today.dayOfWeek.value - 1] + " · " + lunarText
    }
}
