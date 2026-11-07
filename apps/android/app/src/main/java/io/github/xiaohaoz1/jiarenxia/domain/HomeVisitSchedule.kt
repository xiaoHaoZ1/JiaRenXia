package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.ContactLog
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.data.HomePlan
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import java.time.LocalDate

/**
 * 回家看看提醒的状态与文案计算（FAMILY_AFFAIRS 20），纯 JVM。
 *
 * - “家”= 一组在世、非本人的家人 + 回家周期；成员里**任何一位**有
 *   VISIT（见面）记录就算回过家，电话/微信不算；
 * - 到期口径复用 [ContactSchedule]：起算日 = 最近一次见面日，
 *   从未见面用计划创建日；逾期 ≥0 到期，到期当天提醒一次、
 *   之后每 7 天再提醒一次；
 * - 节前提醒：春节/中秋/国庆与成员生日恰好在 7 天后、该家未到期、
 *   且距上次见面（从未见面则距创建）≥ 周期一半时命中，同一天只发一条。
 */
object HomeVisitSchedule {

    /** 节前提醒提前天数（固定，不可配）。 */
    const val PRE_HOLIDAY_DAYS = 7

    /** 家庭页顺序的成员标签（称谓，无称谓用姓名）。 */
    data class Member(val personId: String, val label: String)

    /** 一个家：计划 + 有效成员（家庭页顺序）+ 到期状态；有效成员为空不产生。 */
    data class Home(
        val planId: String,
        val intervalDays: Int,
        val createdAt: Long,
        val members: List<Member>,
        val status: ContactSchedule.Status
    ) {
        /** 家名（见 [homeLabel]）。 */
        val label: String get() = homeLabel(members.map { it.label })
    }

    /** 家事页“今天”行：所有到期（不要求 remindToday）的家。 */
    data class DueRow(
        val planId: String,
        val label: String,
        /** 距上次见面天数；从未见面为 null。 */
        val daysSinceLast: Int?
    )

    /** 节前提醒命中（通知去重键与文案已备好）。 */
    data class PreHoliday(
        val planId: String,
        /** 事件日（= 今天 + 7）。 */
        val eventDate: LocalDate,
        /** 通知正文。 */
        val text: String,
        /** 通知去重键 "homepre:{planId}:{事件日 epochDay}"。 */
        val dedupKey: String
    )

    /**
     * 全部“家”的提醒状态。成员按家庭页顺序（core + others）排列；
     * 只保留在世、非本人的有效成员，没有有效成员的家不出现。
     */
    fun homes(
        plans: List<HomePlan>,
        overview: FamilyOverview?,
        logs: List<ContactLog>,
        today: LocalDate
    ): List<Home> {
        if (overview == null) return emptyList()
        val ordered = overview.core + overview.others
        val visitLogs = logs.filter { it.method == ContactMethod.VISIT }
        return plans.mapNotNull { plan ->
            val memberSet = plan.memberIds.toSet()
            val members = ordered.mapNotNull { ov ->
                if (ov.person.id !in memberSet) return@mapNotNull null
                if (!ContactSchedule.eligible(ov.person)) return@mapNotNull null
                val k = ov.result.kind
                val label = if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF)
                    ov.person.name else ov.displayTitle
                Member(ov.person.id, label)
            }
            if (members.isEmpty()) return@mapNotNull null
            val mIds = members.mapTo(HashSet()) { it.personId }
            val status = ContactSchedule.status(
                visitLogs.filter { it.personId in mIds },
                plan.intervalDays, plan.createdAt, today)
            Home(plan.id, plan.intervalDays, plan.createdAt, members, status)
        }
    }

    /**
     * 家名：[labels] 为成员称谓（无称谓用姓名，家庭页顺序）。
     * 1 人直接用；2 人直接拼（“爸爸妈妈”）；3 人及以上用“、”连接；
     * 空列表兜底“这个家”。
     */
    fun homeLabel(labels: List<String>): String = when {
        labels.isEmpty() -> "这个家"
        labels.size == 1 -> labels[0]
        labels.size == 2 -> labels[0] + labels[1]
        else -> labels.joinToString("、")
    }

    /** 到期通知正文：“已经 N 天没回家看看{家名}了” / 从未见面文案。 */
    fun dueText(home: Home): String =
        if (home.status.daysSinceLast == null) {
            "还没有记录过回家看看" + home.label + "，找时间回去吧"
        } else {
            "已经 " + home.status.daysSinceLast + " 天没回家看看" + home.label + "了"
        }

    /** 家事页“今天”段：所有到期的家（overdueDays ≥ 0，不看 remindToday）。 */
    fun dueRows(homes: List<Home>): List<DueRow> =
        homes.filter { it.status.due }.map {
            DueRow(it.planId, it.label, it.status.daysSinceLast)
        }

    /** 节前提醒候选事件的一条（rank 小者优先：春节 < 中秋 < 国庆 < 生日）。 */
    private data class Candidate(
        val date: LocalDate,
        val rank: Int,
        /** 节日名（春节/中秋节/国庆）；生日事件为 null。 */
        val festival: String?,
        /** 生日事件对应的成员称谓；节日事件为 null。 */
        val memberLabel: String?
    )

    /**
     * 节前提醒命中列表（每家至多一条）。
     * 命中条件：事件恰好在 [PRE_HOLIDAY_DAYS] 天后、该家未到期、
     * 且距上次见面（从未见面则距创建）≥ 周期一半（整数除法）。
     */
    fun preHoliday(
        homes: List<Home>,
        overview: FamilyOverview?,
        lunar: LunarCalendar,
        today: LocalDate
    ): List<PreHoliday> {
        if (overview == null) return emptyList()
        val target = today.plusDays(PRE_HOLIDAY_DAYS.toLong())
        return homes.mapNotNull { home ->
            preHolidayFor(home, overview, lunar, today, target)
        }
    }

    private fun preHolidayFor(
        home: Home,
        overview: FamilyOverview,
        lunar: LunarCalendar,
        today: LocalDate,
        target: LocalDate
    ): PreHoliday? {
        // 已到期的家走到期提醒，不再发节前提醒
        if (home.status.due) return null
        // 距上次见面天数（从未见面 = 距计划创建）；ContactSchedule 的
        // overdueDays = elapsed - intervalDays，两者相加即 elapsed
        val elapsed = home.status.overdueDays + home.intervalDays
        if (elapsed < home.intervalDays / 2) return null

        val candidates = ArrayList<Candidate>()
        // 春节（农历正月初一，非闰）、中秋（农历八月十五，非闰）：
        // 公历今年与明年都算（覆盖跨年窗口）
        listOf(today.year, today.year + 1).forEach { y ->
            lunar.toSolar(y, 1, 1, false)?.let {
                candidates += Candidate(it, 0, "春节", null)
            }
            lunar.toSolar(y, 8, 15, false)?.let {
                candidates += Candidate(it, 1, "中秋节", null)
            }
            candidates += Candidate(LocalDate.of(y, 10, 1), 2, "国庆", null)
        }
        // 每位有效成员的每条生日（双生日口径，各自算下一次发生日）
        home.members.forEach { m ->
            val ov = overview.byId[m.personId] ?: return@forEach
            ov.birthdays.forEach { b ->
                val occ = BirthdayMath.nextOccurrence(b, today, lunar) ?: return@forEach
                if (occ == target) {
                    candidates += Candidate(target, 3, null, m.label)
                }
            }
        }

        // 恰好 7 天后命中；同一天多事件只发一条（日期最早 →
        // 春节/中秋/国庆/生日 顺序第一个；命中日都相同，按 rank 取）
        val hit = candidates.filter { it.date == target }
            .minWithOrNull(compareBy({ it.date }, { it.rank })) ?: return null

        val md = target.monthValue.toString() + "月" + target.dayOfMonth + "日"
        val daysSince = home.status.daysSinceLast
        val text = if (hit.festival != null) {
            val head = hit.festival + "快到了（" + md + "）"
            if (daysSince == null) head + "，回家看看" + home.label + "吧"
            else head + "，已经 " + daysSince + " 天没回家看看" + home.label + "了"
        } else {
            val head = hit.memberLabel + "的生日快到了（" + md + "）"
            if (daysSince == null) head + "，回家看看" + home.label + "吧"
            else head + "，已经 " + daysSince + " 天没回家看看" + home.label + "了"
        }
        return PreHoliday(
            planId = home.planId,
            eventDate = target,
            text = text,
            dedupKey = "homepre:" + home.planId + ":" + target.toEpochDay()
        )
    }
}
