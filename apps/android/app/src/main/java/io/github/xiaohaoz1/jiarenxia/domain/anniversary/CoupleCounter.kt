package io.github.xiaohaoz1.jiarenxia.domain.anniversary

import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * “我和另一半”的计数与里程碑（桌面插件 / 家事时间线 / 通知共用）。
 *
 * 自己夫妻的条目：`person.isSelf || partnerIsSelf`；另一方 id 为
 * `partnerId`（记在自己名下）或 `person.id`（记在对方名下）。
 * 称谓取用户在详情页自定义的称呼（customTitle，如“老婆”），没有就用姓名，
 * 不用系统算出来的称谓（“妻子”）。
 */
object CoupleCounter {

    /** 插件首行文案：主标题 + 次要备注。 */
    data class CoupleLine(val partnerId: String, val title: String, val note: String?)

    /** 里程碑：相恋/结婚/领证第 N 天临近。 */
    data class Milestone(
        val entry: DateEntry,
        val partnerId: String,
        val type: ImportantDateType,
        val dayNumber: Int,
        val date: LocalDate,
        val daysUntil: Int,
        val label: String,
    )

    private val COUPLE_TYPES = listOf(
        ImportantDateType.DATING_ANNIVERSARY,
        ImportantDateType.WEDDING_ANNIVERSARY,
        ImportantDateType.LICENSE_ANNIVERSARY,
    )

    /** 里程碑天数序列：100、520、1000、1314，之后每 1000。 */
    private val MILESTONE_NUMBERS = sequenceOf(100, 520, 1000, 1314) +
        generateSequence(2000) { it + 1000 }

    private data class SelfEntry(val entry: DateEntry, val partnerId: String)

    private fun selfEntries(
        dates: List<DateEntry>,
        overview: FamilyOverview?,
    ): List<SelfEntry> {
        val out = mutableListOf<SelfEntry>()
        for (e in dates) {
            if (!e.type.isCoupleShared) continue
            val selfCouple = e.person.isSelf || e.partnerIsSelf
            if (!selfCouple) continue
            val pid = if (e.person.isSelf) e.partnerId else e.person.id
            if (pid == null) continue
            // 已故配偶不出现在计数里
            val status = overview?.byId?.get(pid)?.person?.livingStatus
            if (status != null && status != LivingStatus.ALIVE) continue
            out += SelfEntry(e, pid)
        }
        return out
    }

    /** 选定“另一半”：能算出起始日、不在未来的条目里，起始日最新的那个人。 */
    private fun currentPartner(
        entries: List<SelfEntry>,
        today: LocalDate,
        lunar: LunarCalendar,
    ): String? {
        var best: String? = null
        var bestStart: LocalDate? = null
        for (se in entries) {
            val start = AnniversaryDurations.startDate(se.entry.date, lunar) ?: continue
            if (start.isAfter(today)) continue
            if (bestStart == null || start.isAfter(bestStart)) {
                best = se.partnerId; bestStart = start
            }
        }
        return best
    }

    /** 称谓标签：自定义称呼优先，否则姓名。 */
    private fun label(overview: FamilyOverview?, partnerId: String, entry: DateEntry): String {
        val custom = overview?.byId?.get(partnerId)?.customTitle
        if (!custom.isNullOrBlank()) return custom
        val name = overview?.byId?.get(partnerId)?.person?.name
        if (!name.isNullOrBlank()) return name
        return entry.partnerName ?: entry.person.name
    }

    /**
     * 主计数行：DATING > WEDDING > LICENSE 取第一个能算出起始日的；
     * 备注 = 其余两类（WEDDING、LICENSE 顺序）的时长，同日起始的结婚/领证只显示结婚。
     */
    fun counter(
        dates: List<DateEntry>,
        overview: FamilyOverview?,
        today: LocalDate,
        lunar: LunarCalendar,
    ): CoupleLine? {
        var entries = selfEntries(dates, overview)
        val partnerId = currentPartner(entries, today, lunar) ?: return null
        entries = entries.filter { it.partnerId == partnerId }
        val byType = entries.groupBy { it.entry.type }
        var primary: SelfEntry? = null
        var primaryStart: LocalDate? = null
        for (t in COUPLE_TYPES) {
            for (se in byType[t].orEmpty()) {
                val start = AnniversaryDurations.startDate(se.entry.date, lunar) ?: continue
                if (start.isAfter(today)) continue
                if (primaryStart == null || start.isAfter(primaryStart)) {
                    primary = se; primaryStart = start
                }
            }
            if (primary != null) break
        }
        if (primary == null || primaryStart == null) return null
        val days = ChronoUnit.DAYS.between(primaryStart, today) + 1
        val lab = label(overview, primary.partnerId, primary.entry)
        val title = "和" + lab + AnniversaryDurations.verb(primary.entry.type) +
            "第 " + days + " 天"
        val notes = mutableListOf<String>()
        val seen = mutableSetOf<LocalDate>()
        seen += primaryStart
        for (t in listOf(
            ImportantDateType.WEDDING_ANNIVERSARY,
            ImportantDateType.LICENSE_ANNIVERSARY)) {
            if (t == primary.entry.type) continue
            for (se in byType[t].orEmpty()) {
                val start = AnniversaryDurations.startDate(se.entry.date, lunar) ?: continue
                if (start.isAfter(today)) continue
                // 同一日的结婚/领证不重复列（结婚优先，LICENSE 在后被跳过）
                if (t == ImportantDateType.LICENSE_ANNIVERSARY && start in seen) continue
                seen += start
                AnniversaryDurations.fact(t, start, today)?.let { notes += it }
            }
        }
        return CoupleLine(
            partnerId = primary.partnerId,
            title = title,
            note = notes.joinToString(" · ").ifEmpty { null },
        )
    }

    /** 窗口内将到的里程碑（自己夫妻三类都算），同 (date, N) 去重保优先级。 */
    fun milestones(
        dates: List<DateEntry>,
        overview: FamilyOverview?,
        today: LocalDate,
        lunar: LunarCalendar,
        windowDays: Int,
    ): List<Milestone> {
        var entries = selfEntries(dates, overview)
        val partnerId = currentPartner(entries, today, lunar) ?: return emptyList()
        entries = entries.filter { it.partnerId == partnerId }
        val out = mutableListOf<Milestone>()
        val seen = mutableSetOf<Pair<LocalDate, Int>>()
        for (t in COUPLE_TYPES) {
            for (se in entries.filter { it.entry.type == t }) {
                val start = AnniversaryDurations.startDate(se.entry.date, lunar) ?: continue
                if (start.isAfter(today)) continue
                val lab = label(overview, se.partnerId, se.entry)
                for (n in MILESTONE_NUMBERS) {
                    val date = start.plusDays((n - 1).toLong())
                    val daysUntil = ChronoUnit.DAYS.between(today, date).toInt()
                    if (daysUntil > windowDays) break
                    if (daysUntil < 0) continue
                    if (!seen.add(date to n)) continue
                    out += Milestone(
                        entry = se.entry, partnerId = se.partnerId, type = t,
                        dayNumber = n, date = date, daysUntil = daysUntil,
                        label = "和" + lab + AnniversaryDurations.verb(t) +
                            "第 " + n + " 天",
                    )
                }
            }
        }
        return out.sortedBy { it.date }
    }
}
