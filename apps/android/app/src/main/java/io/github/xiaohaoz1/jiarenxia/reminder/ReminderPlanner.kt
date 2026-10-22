package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.ReminderPreference
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import java.time.LocalDate

/** 双生日中单个历法生日的命中：发生日 + 命中的提前天数。 */
data class BirthdayPart(
    val calendar: CalendarType,
    /** 该生日的下一次公历发生日（农历生日为换算结果）。 */
    val occurrence: LocalDate,
    /** 命中的提前天数（0=当天）。 */
    val daysBefore: Int
)

/**
 * 到期提醒：按每个提醒偏移计算哪些生日需要今天通知。
 * 双生日：一个人一天最多一条 [DueReminder]；多个历法生日同日命中时
 * 通过 [birthdayParts] 携带明细，文案合并为一条。
 */
data class DueReminder(
    val personId: String,
    val name: String,
    /** 称谓；未关联 / 本人 为 null。 */
    val title: String?,
    val daysBefore: Int,
    /** 生日的下一次公历发生日（农历生日为换算结果）；文案需要“M月D日”时用。 */
    val occurrence: LocalDate? = null,
    /** 日期类型：生日（按历法）/ 忌日 / 结婚纪念日（通知渠道与文案分流）。 */
    val kind: ImportantDateType = ImportantDateType.BIRTHDAY_SOLAR,
    /** 结婚纪念日的另一方姓名/称谓。 */
    val partnerName: String? = null,
    val partnerTitle: String? = null,
    /** 记录持有人是“我”。 */
    val isSelfEntry: Boolean = false,
    val partnerIsSelf: Boolean = false,
    /** 农历日期文案（如“农历八月廿五”），公历为 null。 */
    val lunarDateText: String? = null,
    /** 年数文案：“第 41 年”（纪念日）/ “10 周年”（忌日）。 */
    val yearLabel: String? = null,
    /** 生日命中明细（按发生日排序）；非生日提醒为空。 */
    val birthdayParts: List<BirthdayPart> = emptyList(),
    /** 该人一共登记了几条生日（>1 时文案带历法限定词）。 */
    val birthdayCount: Int = 0
)

/**
 * 通知文案（DEVELOPMENT_PLAN 2.5 四）：
 * - “今天是{title}{name}的生日” / “明天是{title}{name}的生日”
 * - 无称谓：“今天是{name}的生日” / “明天是{name}的生日”
 * - 本人：“今天是你的生日” / “明天是你的生日”
 * - 双生日同日：“今天是{name}的生日（阳历和农历同一天）”
 * - 双生日同日触发不同目标：“今天是{name}的农历生日；N天后（M月D日）是阳历生日”
 */
object ReminderPlanner {

    /**
     * 纪念日/忌日到期通知（V0.8）：与生日共用一个每日检查，
     * 文案庄重，忌日附农历原文。生日行由 [dueNotifications] 统一处理，这里跳过。
     */
    fun dueDateReminders(
        entries: List<io.github.xiaohaoz1.jiarenxia.data.DateEntry>,
        titles: Map<String, String?>,
        today: LocalDate,
        lunar: LunarCalendar
    ): List<DueReminder> {
        val out = mutableListOf<DueReminder>()
        for (e in entries) {
            if (!e.reminderEnabled) continue
            // 生日由 dueNotifications 合并处理（双生日按人聚合）；已故者生日不提醒
            if (e.type.isBirthday) continue
            val until = BirthdayMath.daysUntil(e.date, today, lunar) ?: continue
            val occ = BirthdayMath.nextOccurrence(e.date, today, lunar)
            val baseYear = e.date.date.year
            val yearLabel = if (occ != null && baseYear != null) {
                val n = occ.year - baseYear
                if (n <= 0) null else when {
                    e.type.isCoupleShared -> "第 " + n + " 年"
                    e.type == ImportantDateType.DEATH_ANNIVERSARY ->
                        n.toString() + " 周年"
                    else -> null
                }
            } else null
            val lunarText = if (e.date.calendar == CalendarType.LUNAR) {
                "农历" + io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
                    .monthName(e.date.date.month, e.date.leapMonth) +
                    io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
                        .dayName(e.date.date.day)
            } else null
            for (offset in e.reminderOffsets) {
                if (until == offset) {
                    out += DueReminder(
                        personId = e.person.id,
                        name = e.person.name,
                        title = titles[e.person.id],
                        daysBefore = offset,
                        occurrence = occ,
                        kind = e.type,
                        partnerName = e.partnerName,
                        partnerTitle = e.partnerId?.let { titles[it] },
                        isSelfEntry = e.person.isSelf,
                        partnerIsSelf = e.partnerIsSelf,
                        lunarDateText = lunarText,
                        yearLabel = yearLabel
                    )
                }
            }
        }
        return out
    }

    /**
     * 生日到期通知：每条生日行独立计算；同一人在同一天命中的多条
     * （双生日同日 / 不同目标日同日触发）合并成一条 DueReminder。
     */
    fun dueNotifications(
        entries: List<BirthdayEntry>,
        titles: Map<String, String?>,
        today: LocalDate,
        lunar: LunarCalendar
    ): List<DueReminder> {
        // 每人登记了几条生日（决定文案是否带历法限定词）
        val countByPerson = entries.groupBy { it.person.id }.mapValues { it.value.size }
        val partsByPerson = LinkedHashMap<String, Pair<BirthdayEntry, MutableList<BirthdayPart>>>()
        for (e in entries) {
            if (!e.reminderEnabled) continue
            val until = BirthdayMath.daysUntil(e.birthday, today, lunar) ?: continue
            for (offset in e.reminderOffsets) {
                if (until == offset) {
                    val holder = partsByPerson.getOrPut(e.person.id) { e to mutableListOf() }
                    holder.second += BirthdayPart(
                        calendar = e.birthday.calendar,
                        occurrence = today.plusDays(until.toLong()),
                        daysBefore = offset
                    )
                }
            }
        }
        return partsByPerson.map { (pid, pair) ->
            val (e, parts) = pair
            val sorted = parts.sortedWith(
                compareBy({ it.occurrence }, { it.calendar })
            )
            val first = sorted.first()
            DueReminder(
                personId = pid,
                name = e.person.name,
                title = if (e.person.isSelf) null else titles[pid],
                daysBefore = first.daysBefore,
                occurrence = first.occurrence,
                kind = ImportantDateType.birthdayOf(first.calendar),
                birthdayParts = sorted,
                birthdayCount = countByPerson[pid] ?: sorted.size
            )
        }
    }

    /** 到期提醒的通知文案；isSelf 用“你”的称谓。 */
    fun text(due: DueReminder, isSelf: Boolean): String = when (due.kind) {
        ImportantDateType.WEDDING_ANNIVERSARY,
        ImportantDateType.LICENSE_ANNIVERSARY,
        ImportantDateType.DATING_ANNIVERSARY -> {
            val who = weddingLabel(due)
            val year = due.yearLabel?.let { "（" + it + "）" } ?: ""
            dayLabel(due.daysBefore, due.occurrence) + "是" + who + "的" +
                coupleName(due.kind) + year
        }
        ImportantDateType.DEATH_ANNIVERSARY -> {
            val who = due.title?.let { it + due.name } ?: due.name
            val lunar = due.lunarDateText?.let { "（" + it + "）" } ?: ""
            dayLabel(due.daysBefore, due.occurrence) + "是" + who + "的忌日" + lunar
        }
        else -> birthdayText(due, isSelf)
    }
}

/** “今天”/“明天”/“N天后（M月D日）”。 */
private fun dayLabel(daysBefore: Int, occurrence: LocalDate?): String = when {
    daysBefore == 0 -> "今天"
    daysBefore == 1 -> "明天"
    else -> daysBefore.toString() + "天后" +
        (occurrence?.let { "（" + it.monthValue + "月" + it.dayOfMonth + "日）" } ?: "")
}

/** 历法限定词：阳历 / 农历。 */
private fun calLabel(calendar: CalendarType): String =
    if (calendar == CalendarType.SOLAR) "阳历" else "农历"

/**
 * 生日通知文案（含双生日合并规则）：
 * - 单生日 → 原文案；
 * - 双生日只命中一条 → 带历法词（“农历生日”）；
 * - 两条同一天 → “生日（阳历和农历同一天）”；
 * - 两条不同天同日触发 → 分号合并两条子句。
 */
private fun birthdayText(due: DueReminder, isSelf: Boolean): String {
    val who = when {
        isSelf -> "你"
        due.title != null -> due.title + due.name
        else -> due.name
    }
    val parts = due.birthdayParts
    return when {
        parts.isEmpty() ->
            dayLabel(due.daysBefore, due.occurrence) + "是" + who + "的生日"
        parts.size == 1 -> {
            val p = parts[0]
            val qualifier = if (due.birthdayCount > 1) calLabel(p.calendar) else ""
            dayLabel(p.daysBefore, p.occurrence) + "是" + who + "的" + qualifier + "生日"
        }
        else -> {
            val sameDay = parts.all { it.occurrence == parts[0].occurrence }
            if (sameDay) {
                dayLabel(parts[0].daysBefore, parts[0].occurrence) +
                    "是" + who + "的生日（阳历和农历同一天）"
            } else {
                parts.mapIndexed { i, p ->
                    if (i == 0) dayLabel(p.daysBefore, p.occurrence) +
                        "是" + who + "的" + calLabel(p.calendar) + "生日"
                    else dayLabel(p.daysBefore, p.occurrence) +
                        "是" + calLabel(p.calendar) + "生日"
                }.joinToString("；")
            }
        }
    }
}

/** 夫妻共享纪念日的名称（结婚 / 领证 / 恋爱）。 */
private fun coupleName(t: ImportantDateType) = when (t) {
    ImportantDateType.LICENSE_ANNIVERSARY -> "领证纪念日"
    ImportantDateType.DATING_ANNIVERSARY -> "恋爱纪念日"
    else -> "结婚纪念日"
}

/** 纪念日人物组合：“爸爸妈妈”/ “你和李雪” / 姓名组合。 */
private fun weddingLabel(due: DueReminder): String = when {
    due.isSelfEntry -> "你和" + (due.partnerName ?: "")
    due.partnerIsSelf -> "你和" + due.name
    due.title != null && due.partnerTitle != null ->
        due.title + due.partnerTitle
    else -> due.name + (due.partnerName?.let { "和" + it } ?: "")
}
