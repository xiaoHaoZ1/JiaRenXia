package io.github.xiaohaoz1.jiarenxia.domain.export

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
import io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule
import io.github.xiaohaoz1.jiarenxia.reminder.TaskTemplates
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 家事提醒导出：
 * 把公历范围 [from, to] 内的生日、纪念日/忌日与家庭事项展开为表格行。
 *
 * 规则：
 * - 生日与纪念日按年计次，范围内每个公历年最多一次（范围跨年则多年各一次，
 *   不无限展开）；事项只取当前到期实例，重复事项不按期铺开。
 * - [includeOverdue] 为真时，到期日早于今天且未办结的事项无论是否落在范围内
 *   都收录（状态标“已逾期 N 天”）。
 * - 生日/纪念日不是“待办”，状态列恒为空，不伪造完成状态。
 * - 排序与家事页一致：日期升序，同一天先重要日期、后事项。
 */
object ReminderExport {

    /** 一行导出数据。[status] 仅事项有值；[person] 为 null 表示全家的事。 */
    data class Row(
        val date: LocalDate,
        val person: String?,
        val type: String,
        val title: String,
        val status: String,
        /** 历法说明：公历条恒为“公历”；农历条为原文，如“农历1964年八月十五”。 */
        val calendarNote: String
    )

    /**
     * 生成范围内全部行。
     * [labelOf]：personId → 展示名（“我”/称谓/姓名），查不到时回退原始姓名。
     */
    fun build(
        birthdays: List<BirthdayEntry>,
        dates: List<DateEntry>,
        tasks: List<FamilyTask>,
        labelOf: (String) -> String?,
        from: LocalDate,
        to: LocalDate,
        includeOverdue: Boolean,
        today: LocalDate,
        lunar: LunarCalendar
    ): List<Row> {
        // weight：同一天生日/纪念日/忌日在前，事项在后（与家事页同序）
        val out = mutableListOf<Triple<LocalDate, Int, Row>>()

        // 同一人物、同一公历发生日合并双生日，与提醒时间线保持一致。
        val birthdayGroups = linkedMapOf<Pair<String, LocalDate>, MutableList<BirthdayEntry>>()
        birthdays.forEach { e ->
            eachOccurrence(e.birthday, from, to, lunar) { occ ->
                birthdayGroups.getOrPut(e.person.id to occ) { mutableListOf() }.add(e)
            }
        }
        birthdayGroups.forEach { (key, entries) ->
            val who = labelOf(key.first) ?: entries.first().person.name
            val values = entries.map { it.birthday }.distinctBy { it.calendar }
                .sortedBy { it.calendar }
            val notes = values.joinToString("；") { calendarNote(it) }
            out += Triple(
                key.second, 0,
                Row(key.second, who, "生日", who + "的生日", "",
                    if (values.size > 1) "阳历和农历同一天；" + notes else notes)
            )
        }

        dates.forEach { e ->
            // 生日由 birthdays 源处理；dateEntries 中的生日条跳过避免重复
            val who = labelOf(e.person.id) ?: e.person.name
            val (person, typeLabel, title) = when {
                e.type.isCoupleShared -> {
                    val b = e.partnerId?.let { labelOf(it) } ?: e.partnerName
                    val name = when (e.type) {
                        ImportantDateType.LICENSE_ANNIVERSARY -> "领证纪念日"
                        ImportantDateType.DATING_ANNIVERSARY -> "恋爱纪念日"
                        else -> "结婚纪念日"
                    }
                    Triple(
                        listOfNotNull(who, b).joinToString("、"),
                        name,
                        listOfNotNull(who, b).joinToString("") + "的" + name
                    )
                }
                e.type == ImportantDateType.DEATH_ANNIVERSARY ->
                    Triple(who, "忌日", who + "的忌日")
                else -> return@forEach
            }
            val baseY = e.date.date.year
            eachOccurrence(e.date, from, to, lunar) { occ ->
                // 周年/第几年沿用家事页口径，附在标题后
                val suffix = if (baseY != null && occ.year > baseY) {
                    if (e.type.isCoupleShared)
                        "（第 " + (occ.year - baseY) + " 年）"
                    else "（" + (occ.year - baseY) + " 周年）"
                } else ""
                out += Triple(
                    occ, 1,
                    Row(occ, person, typeLabel, title + suffix, "", calendarNote(e.date))
                )
            }
        }

        tasks.forEach { t ->
            val due = TaskSchedule.dueDate(t)
            val overdueDays = if (!t.done && due < today)
                ChronoUnit.DAYS.between(due, today).toInt() else 0
            val inRange = !due.isBefore(from) && !due.isAfter(to)
            if (!inRange && !(includeOverdue && overdueDays > 0)) return@forEach
            val status = when {
                t.done -> "已办"
                overdueDays > 0 -> "已逾期 " + overdueDays + " 天"
                else -> "待办"
            }
            out += Triple(
                due, 2,
                Row(
                    due, t.personId?.let { labelOf(it) } ?: "全家",
                    TaskTemplates.of(t.kind).displayName, t.title, status, "公历"
                )
            )
        }

        return out.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
    }

    /**
     * 年计次日期在 [from, to] 内的每次发生日（公历），逐个回调。
     * 通过 nextOccurrence 步进保证每步至少前进一年，循环次数有上界。
     */
    private inline fun eachOccurrence(
        b: BirthdayValue,
        from: LocalDate,
        to: LocalDate,
        lunar: LunarCalendar,
        emit: (LocalDate) -> Unit
    ) {
        var cursor = from
        var guard = 0
        // 防御上限：年事件在 ~150 年跨度内也到不了 200 次
        while (guard++ < 200) {
            val occ = BirthdayMath.nextOccurrence(b, cursor, lunar) ?: break
            if (occ > to) break
            emit(occ)
            cursor = occ.plusDays(1)
        }
    }

    /** 历法说明：农历日期写出原文（年份已知则带年），公历恒为“公历”。 */
    private fun calendarNote(b: BirthdayValue): String =
        if (b.calendar == CalendarType.LUNAR) {
            "农历" + (b.date.year?.let { it.toString() + "年" } ?: "") +
                LunarFormat.monthName(b.date.month, b.leapMonth) +
                LunarFormat.dayName(b.date.day)
        } else "公历"
}
