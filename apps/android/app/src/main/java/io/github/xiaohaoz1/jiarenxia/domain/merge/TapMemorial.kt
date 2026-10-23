package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.domain.anniversary.AnniversaryDurations
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 已配对纪念模式的文案与日期规则（FAMILY_AFFAIRS 第 10 节，纯 JVM）。
 *
 * HELLO v4 起双方各携带一组夫妻共享纪念日（恋爱/领证/结婚各至多一条）。
 *
 * - 主行沿用结婚日期规则：双方都记录了完全一致的结婚日期且含年份时，
 *   普通日子显示“与你相伴第 N 天”（自然日期差 + 1，结婚当天第 1 天）；
 *   纪念日当天显示“结婚 X 周年”；结婚当天显示新婚文案。
 * - 时长行：每种纪念日两边记录一致且含年份时，按 相恋→领证→结婚 顺序
 *   列出“相恋 8 年 3 个月 · 领证 6 年 · 结婚 5 年”；算不出的类型跳过。
 * - 只有月日（无年份）：结婚纪念日当天显示纪念日祝福，其余日子普通祝福，
 *   不编造周年或相伴天数；无年份的类型不进时长行。
 * - 两边都没有：普通祝福；任一种类型只有一边有 / 两边不一致 / 日期在未来 /
 *   农历日无法换算：普通祝福，并提示回家核对（不自动覆盖任何一边）。
 * - 绝不用第一次碰手机的日期当结婚日期。
 * - 农历/闰月/2 月 29 日的发生日规则与生日一致（复用 [BirthdayMath]）。
 */
object TapMemorial {

    /** 展示行。 */
    sealed class Line {
        /** 普通祝福。 */
        data object Blessing : Line()
        /** 喜结连理（结婚当天）。 */
        data object Newlywed : Line()
        /** 今天是你们的结婚纪念日（无年份，不显示数字）。 */
        data object AnniversaryDay : Line()
        /** 与你相伴第 n 天（结婚当天为第 1 天）。 */
        data class Days(val n: Int) : Line()
        /** 结婚 x 周年（x>=1）。 */
        data class Anniversary(val years: Int) : Line()
    }

    data class Display(
        val line: Line,
        /** 任一种类型两边记录不一致或一边缺失：显示“核对日期”提示。 */
        val dateCheckHint: Boolean,
        /**
         * 两边一致且含年份可算的时长文案（“相恋 8 年 · 领证 6 年”），
         * 顺序为 相恋 → 领证 → 结婚。
         */
        val facts: List<String> = emptyList()
    ) {
        /** 周年日（用于强化动画效果）。 */
        val isAnniversary: Boolean get() =
            line is Line.Anniversary || line is Line.AnniversaryDay
    }

    /** 时长行展示顺序：相恋 → 领证 → 结婚。 */
    private val factOrder = listOf(
        TapProtocol.DATE_DATING,
        TapProtocol.DATE_LICENSE,
        TapProtocol.DATE_WEDDING
    )

    private fun typeOf(kind: Int) = when (kind) {
        TapProtocol.DATE_LICENSE -> ImportantDateType.LICENSE_ANNIVERSARY
        TapProtocol.DATE_DATING -> ImportantDateType.DATING_ANNIVERSARY
        else -> ImportantDateType.WEDDING_ANNIVERSARY
    }

    /** DateWire → BirthdayValue（year=0 视为无年份）。 */
    private fun toValue(w: TapProtocol.DateWire) = BirthdayValue(
        date = PartialDate(
            year = if (w.year > 0) w.year else null,
            month = w.month, day = w.day),
        calendar = if (w.lunar) CalendarType.LUNAR else CalendarType.SOLAR,
        leapMonth = w.leapMonth)

    /**
     * 计算纪念页文案。[own]/[peer] 为双方各自携带的纪念日列表（可空元素）。
     * 两端用同样的输入得到同样的结果，不会显示互相矛盾的数字。
     */
    fun display(
        own: List<TapProtocol.DateWire>,
        peer: List<TapProtocol.DateWire>,
        today: LocalDate,
        lunar: LunarCalendar
    ): Display {
        fun find(l: List<TapProtocol.DateWire>, kind: Int) =
            l.firstOrNull { it.kind == kind }

        val ownW = find(own, TapProtocol.DATE_WEDDING)
        val peerW = find(peer, TapProtocol.DATE_WEDDING)

        // 主行：结婚日期规则（与 v3 相同）
        val main: Display = when {
            ownW == null && peerW == null ->
                Display(Line.Blessing, dateCheckHint = false)
            ownW == null || peerW == null || !ownW.sameAs(peerW) ->
                // 一边缺失或两边不一致：普通祝福 + 核对入口，不自动覆盖
                Display(Line.Blessing, dateCheckHint = true)
            else -> weddingLine(toValue(ownW), today, lunar)
        }

        // 时长行：每种类型双边一致且能换算出公历起始日才有条目
        var extraHint = false
        val facts = ArrayList<String>()
        for (kind in factOrder) {
            val a = find(own, kind)
            val b = find(peer, kind)
            when {
                a == null && b == null -> Unit
                a == null || b == null || !a.sameAs(b) -> extraHint = true
                else -> {
                    val v = toValue(a)
                    val start = AnniversaryDurations.startDate(v, lunar)
                    when {
                        start == null -> Unit        // 无年份/无法换算：不出时长
                        start > today -> extraHint = true // 未来日期按不一致处理
                        else -> AnniversaryDurations
                            .fact(typeOf(kind), start, today)
                            ?.let { facts += it }
                    }
                }
            }
        }

        return main.copy(
            dateCheckHint = main.dateCheckHint || extraHint,
            facts = facts)
    }

    /** 结婚日期主行（v3 规则，未变）。 */
    private fun weddingLine(
        v: BirthdayValue,
        today: LocalDate,
        lunar: LunarCalendar
    ): Display = when {
        v.date.year == null -> {
            // 只知月日：纪念日当天给专属祝福，不编造数字
            val occ = BirthdayMath.nextOccurrence(v, today, lunar)
            Display(
                if (occ == today) Line.AnniversaryDay else Line.Blessing,
                dateCheckHint = false)
        }
        else -> {
            val wedding = BirthdayMath.solarBirthDate(v, lunar)
                ?: return Display(Line.Blessing, dateCheckHint = false)
            when {
                wedding > today ->
                    // 未来日期不编造天数
                    Display(Line.Blessing, dateCheckHint = true)
                wedding == today -> Display(Line.Newlywed, false)
                else -> {
                    val occ = BirthdayMath.nextOccurrence(v, today, lunar)
                    val years = if (v.calendar == CalendarType.LUNAR) {
                        lunar.fromSolar(today).year - v.date.year!!
                    } else today.year - v.date.year!!
                    if (occ == today && years >= 1) {
                        Display(Line.Anniversary(years), false)
                    } else {
                        val n = ChronoUnit.DAYS.between(
                            wedding, today).toInt() + 1
                        Display(Line.Days(n), false)
                    }
                }
            }
        }
    }

    /** 主行文案（纯文本，供 UI 与无障碍读出）。 */
    fun text(line: Line): String = when (line) {
        Line.Blessing -> "愿你们白头偕老，岁岁常欢"
        Line.Newlywed -> "喜结连理，新婚快乐"
        Line.AnniversaryDay -> "今天是你们的结婚纪念日"
        is Line.Days -> "与你相伴第 ${line.n} 天"
        is Line.Anniversary -> "结婚 ${line.years} 周年"
    }

    /** 完整展示文案：主行 + 时长行（“相恋 8 年 · 领证 6 年 · 结婚 5 年”）。 */
    fun text(d: Display): String =
        if (d.facts.isEmpty()) text(d.line)
        else text(d.line) + "\n" + d.facts.joinToString(" · ")
}
