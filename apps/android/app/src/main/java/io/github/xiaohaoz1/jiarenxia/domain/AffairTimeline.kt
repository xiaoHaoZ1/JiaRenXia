package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 纪念日/忌日时间线条目（家事页提醒段与桌面插件共用）。 */
data class DateRow(
    val kind: ImportantDateType,
    val personId: String,
    /** “爸爸妈妈的结婚纪念日”/“爷爷的忌日”。 */
    val label: String,
    val daysUntil: Int,
    val occurrence: LocalDate,
    /** “第 41 年”/“10 周年”，年份未知为 null。 */
    val yearLabel: String? = null,
    /** 农历原文（“农历八月廿五”），公历为 null。 */
    val lunarText: String? = null,
    /** 里程碑天数（如 1000）；普通纪念行为 null。 */
    val milestoneDays: Int? = null
)

/** 家事时间线共享合成逻辑（家事页与桌面插件同口径）。 */
object AffairTimeline {

    /** 纪念日/忌日 → “今天”与“未来 30 天”两组；生日不在此列（走 UpcomingBirthdays）。 */
    fun dateRows(
        dates: List<DateEntry>,
        titles: Map<String, String?>,
        today: LocalDate,
        lunar: LunarCalendar,
        overview: FamilyOverview? = null
    ): Pair<List<DateRow>, List<DateRow>> {
        val outToday = mutableListOf<DateRow>()
        val outNext = mutableListOf<DateRow>()
        for (e in dates) {
            // 生日（阳历/农历两类）走生日时间线，不重复进纪念日行
            if (e.type.isBirthday) continue
            val occ = BirthdayMath.nextOccurrence(e.date, today, lunar) ?: continue
            val days = ChronoUnit.DAYS.between(today, occ).toInt()
            if (days < 0 || days > 30) continue
            val label = when {
                e.type.isCoupleShared -> {
                    val a = titles[e.person.id] ?: e.person.name
                    val b = e.partnerId?.let { titles[it] ?: e.partnerName }
                        ?: e.partnerName
                    (a + b) + "的" + when (e.type) {
                        ImportantDateType.LICENSE_ANNIVERSARY -> "领证纪念日"
                        ImportantDateType.DATING_ANNIVERSARY -> "恋爱纪念日"
                        else -> "结婚纪念日"
                    }
                }
                else -> (titles[e.person.id] ?: e.person.name) + "的忌日"
            }
            val baseY = e.date.date.year
            val yearLabel = if (baseY != null && occ.year > baseY) {
                if (e.type.isCoupleShared) "第 " + (occ.year - baseY) + " 年"
                else (occ.year - baseY).toString() + " 周年"
            } else null
            val row = DateRow(
                kind = e.type, personId = e.person.id, label = label,
                daysUntil = days, occurrence = occ, yearLabel = yearLabel,
                lunarText = if (e.date.calendar == CalendarType.LUNAR
                ) "农历" + LunarFormat.monthName(e.date.date.month, e.date.leapMonth) +
                    LunarFormat.dayName(e.date.date.day)
                else null
            )
            (if (days == 0) outToday else outNext).add(row)
        }
        if (overview != null) {
            for (m in io.github.xiaohaoz1.jiarenxia.domain.anniversary.CoupleCounter.milestones(
                dates, overview, today, lunar, windowDays = 30)) {
                val row = DateRow(
                    kind = m.type, personId = m.partnerId, label = m.label,
                    daysUntil = m.daysUntil, occurrence = m.date,
                    milestoneDays = m.dayNumber
                )
                (if (m.daysUntil == 0) outToday else outNext).add(row)
            }
        }
        outToday.sortBy { it.label }; outNext.sortBy { it.occurrence }
        return outToday to outNext
    }
}
