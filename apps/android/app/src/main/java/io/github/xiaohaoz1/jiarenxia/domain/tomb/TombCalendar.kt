package io.github.xiaohaoz1.jiarenxia.domain.tomb

import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor

/** 上坟提醒的日子（节日按公历/农历推算；忌日走原有重要日期逻辑）。 */
enum class TombDay {
    /** 清明节。 */
    QINGMING,
    /** 中元节（农历七月十四/十五，可配）。 */
    ZHONGYUAN,
    /** 寒衣节（农历十月初一）。 */
    HANYI,
    /** 除夕。 */
    CHUXI,
    /** 冬至。 */
    DONGZHI,
    /** 忌日（沿用每条忌日自己的提醒偏好）。 */
    DEATH_DAY,
    /** 做七与百日（按忌日推算）。 */
    MOURNING
}

/**
 * 一次上坟日：节日（全家一条）或某位新近去世家人的做七/百日。
 *
 * @param day 节日或 [TombDay.MOURNING]
 * @param date 事件发生的公历日
 * @param title 如“清明节” / “爷爷的三七”
 * @param lunarText 农历原文（“农历七月十五”）；清明/冬至/除夕/做七为 null
 * @param personIds 关联人物（节日为名单全员；做七为单人）
 * @param names 展示名列表（称谓优先、否则姓名），顺序同 personIds
 * @param mourningDayNumber 做七序号 7/21/35/49/100；节日为 null
 */
data class TombEvent(
    val day: TombDay,
    val date: LocalDate,
    val title: String,
    val lunarText: String?,
    val personIds: List<String>,
    val names: List<String>,
    val mourningDayNumber: Int? = null
)

/** “给谁上坟”名单一行：称谓（无称谓为 null）+ 姓名。 */
data class TombMourner(
    val personId: String,
    val title: String?,
    val name: String
) {
    /** 名单与文案用的展示名：称谓优先，否则姓名。 */
    val label: String get() = title ?: name
}

/** 上坟日子与名单设置（[TombCalendar.events] 的输入；总开关与时刻由调用方把关）。 */
data class TombPrefs(
    val days: Set<TombDay> = DEFAULT_DAYS,
    /** 中元节按农历七月十五（默认）还是十四；其他取值一律按十五。 */
    val zhongyuanDay: Int = 15,
    /** “给谁上坟”里被关掉的人（只影响节日名单，忌日与做七不受影响）。 */
    val excludedIds: Set<String> = emptySet()
) {
    companion object {
        /** 默认开启的日子：清明、中元、寒衣、忌日、做七（除夕与冬至默认关）。 */
        val DEFAULT_DAYS: Set<TombDay> = setOf(
            TombDay.QINGMING, TombDay.ZHONGYUAN, TombDay.HANYI,
            TombDay.DEATH_DAY, TombDay.MOURNING
        )
    }
}

/**
 * 上坟日推算（纯函数；农历通过注入的 [LunarCalendar] 计算，便于单测替换）。
 *
 * 节日日期每年自动推算：清明/冬至按 2000–2099 寿星公式，
 * 中元/寒衣/除夕按农历换算；做七/百日由忌日（须带去世年份）推算。
 */
object TombCalendar {

    /** 通知检查的最大提前窗口（清明提前 7 天，取最大值）。 */
    const val NOTIFY_WINDOW_DAYS = 7

    /** 节日/做七的提前天数（固定，不可配）。DEATH_DAY 沿用各忌日自己的偏好。 */
    fun advanceDays(day: TombDay): Set<Int> = when (day) {
        TombDay.QINGMING -> setOf(7, 1)
        TombDay.ZHONGYUAN, TombDay.HANYI, TombDay.DONGZHI -> setOf(3, 0)
        TombDay.CHUXI, TombDay.MOURNING -> setOf(1, 0)
        TombDay.DEATH_DAY -> emptySet()
    }

    /**
     * 清明：寿星公式（21 世纪 C=4.81），Y = 年 % 100，
     * day = floor(Y*0.2422 + 4.81) - floor(Y/4)，公历四月。
     * 仅支持 2000..2099，范围外返回 null。
     */
    fun qingming(year: Int): LocalDate? {
        if (year !in 2000..2099) return null
        val y = year % 100
        val day = floor(y * 0.2422 + 4.81).toInt() - floor(y / 4.0).toInt()
        return LocalDate.of(year, 4, day)
    }

    /**
     * 冬至：寿星公式，day = floor(Y*0.2422 + 21.94) - floor(Y/4)，公历十二月；
     * 2021 年例外为 12 月 21 日（公式算出 22 日，实际节气为 21 日）。
     * 仅支持 2000..2099，范围外返回 null。
     */
    fun dongzhi(year: Int): LocalDate? {
        if (year !in 2000..2099) return null
        if (year == 2021) return LocalDate.of(2021, 12, 21)
        val y = year % 100
        val day = floor(y * 0.2422 + 21.94).toInt() - floor(y / 4.0).toInt()
        return LocalDate.of(year, 12, day)
    }

    /** 中元节：农历七月（非闰）十四或十五，按 [day] 选择。 */
    fun zhongyuan(year: Int, day: Int, lunar: LunarCalendar): LocalDate? =
        lunar.toSolar(year, 7, day, false)

    /** 寒衣节：农历十月（非闰）初一。 */
    fun hanyi(year: Int, lunar: LunarCalendar): LocalDate? =
        lunar.toSolar(year, 10, 1, false)

    /** 除夕：该农历年的正月初一前一天（落在公历同年的 1/2 月）。 */
    fun chuxi(year: Int, lunar: LunarCalendar): LocalDate? =
        lunar.toSolar(year, 1, 1, false)?.minusDays(1)

    /**
     * “给谁上坟”名单：全部已故、非本人的家人，排序同家庭页
     * （核心家人在前、未关联最后）；UNRELATED 的人也进名单、排最后，标签用姓名。
     */
    fun mourners(overview: FamilyOverview?): List<TombMourner> {
        if (overview == null) return emptyList()
        return (overview.core + overview.others)
            .filter {
                it.person.livingStatus == LivingStatus.DECEASED && !it.person.isSelf
            }
            .map { o ->
                val title = if (o.result.kind == KinshipKind.UNRELATED) null
                else o.displayTitle
                TombMourner(o.person.id, title, o.person.name)
            }
    }

    /**
     * [today, today+windowDays] 内按设置启用的上坟事件（节日 + 做七/百日）。
     * 忌日（[TombDay.DEATH_DAY]）不在这里产出——仍由现有重要日期逻辑产出。
     * 节日只在其 [TombDay] 在 [TombPrefs.days] 里、且名单（排除 [TombPrefs.excludedIds]）
     * 非空时产出；做七只看 [TombDay.MOURNING] 是否在 [TombPrefs.days] 里。
     */
    fun events(
        today: LocalDate,
        windowDays: Int,
        lunar: LunarCalendar,
        mourners: List<TombMourner>,
        deathEntries: List<DateEntry>,
        prefs: TombPrefs
    ): List<TombEvent> {
        val until = today.plusDays(windowDays.toLong())
        val out = mutableListOf<TombEvent>()

        // ---- 节日：公历今年与明年（农历节日多算一年候选，窗口过滤兜底）----
        val active = mourners.filter { it.personId !in prefs.excludedIds }
        if (active.isNotEmpty()) {
            val personIds = active.map { it.personId }
            val names = active.map { it.label }
            fun festival(day: TombDay, title: String, date: LocalDate?, lunarText: String?) {
                if (day !in prefs.days || date == null) return
                if (date in today..until) {
                    out += TombEvent(day, date, title, lunarText, personIds, names)
                }
            }
            for (y in listOf(today.year, today.year + 1)) {
                festival(TombDay.QINGMING, "清明节", qingming(y), null)
                festival(TombDay.DONGZHI, "冬至", dongzhi(y), null)
            }
            for (y in listOf(today.year - 1, today.year, today.year + 1)) {
                val zy = prefs.zhongyuanDay.let { if (it == 14) 14 else 15 }
                festival(
                    TombDay.ZHONGYUAN, "中元节",
                    zhongyuan(y, zy, lunar),
                    "农历七月" + LunarFormat.dayName(zy)
                )
                festival(
                    TombDay.HANYI, "寒衣节",
                    hanyi(y, lunar),
                    "农历十月初一"
                )
                festival(TombDay.CHUXI, "除夕", chuxi(y, lunar), null)
            }
        }

        // ---- 做七与百日：须有去世年份；农历忌日先换算成去世当天公历 ----
        if (TombDay.MOURNING in prefs.days) {
            val labelById = mourners.associate { it.personId to it.label }
            for (e in deathEntries) {
                if (e.type != ImportantDateType.DEATH_ANNIVERSARY) continue
                val died = deathSolarDate(e.date, lunar) ?: continue
                for ((n, name) in MOURNING_STAGES) {
                    val date = died.plusDays((n - 1).toLong())
                    if (date !in today..until) continue
                    out += TombEvent(
                        day = TombDay.MOURNING,
                        date = date,
                        title = (labelById[e.person.id] ?: e.person.name) + "的" + name,
                        lunarText = null,
                        personIds = listOf(e.person.id),
                        names = listOf(labelById[e.person.id] ?: e.person.name),
                        mourningDayNumber = n
                    )
                }
            }
        }

        return out.sortedWith(compareBy({ it.date }, { it.title }))
    }

    /**
     * 距今天的天数若命中该事件类的提醒提前天数则返回该天数，否则 null。
     * 节日/做七用固定提前天数；忌日走各自条目的偏好（不在此判定）。
     */
    fun dueDaysBefore(event: TombEvent, today: LocalDate): Int? {
        val until = ChronoUnit.DAYS.between(today, event.date).toInt()
        return if (until in advanceDays(event.day)) until else null
    }

    /** 名单串：≤3 人全列；>3 人取前 3 + “等 N 位家人”。 */
    fun rosterText(names: List<String>): String = when {
        names.size <= 3 -> names.joinToString("、")
        else -> names.take(3).joinToString("、") + "等 " + names.size + " 位家人"
    }

    /** 节日通知文案：“{dayLabel}是{title}{（lunarText）}，记得给{名单}上坟”。 */
    fun festivalText(event: TombEvent, daysBefore: Int): String {
        val lunar = event.lunarText?.let { "（" + it + "）" } ?: ""
        return dayLabel(daysBefore, event.date) + "是" + event.title + lunar +
            "，记得给" + rosterText(event.names) + "上坟"
    }

    /** 做七通知文案：“{dayLabel}是{title}”。 */
    fun mourningText(event: TombEvent, daysBefore: Int): String =
        dayLabel(daysBefore, event.date) + "是" + event.title

    /** 家事页时间线副标题：节日 = “农历七月十五 · 给爷爷、奶奶上坟”；做七 = “去世第 N 天”。 */
    fun timelineSubtitle(event: TombEvent): String = when (event.day) {
        TombDay.MOURNING -> "去世第 " + (event.mourningDayNumber ?: 0) + " 天"
        else -> listOfNotNull(
            event.lunarText,
            "给" + rosterText(event.names) + "上坟"
        ).joinToString(" · ")
    }

    /**
     * 忌日条目的去世当天公历：阳历直接取完整日期；农历先换算
     * （无年份或农历日不存在 → null，做七不推算）。
     */
    private fun deathSolarDate(date: BirthdayValue, lunar: LunarCalendar): LocalDate? {
        if (date.calendar == CalendarType.SOLAR) return date.date.toLocalDateOrNull()
        val year = date.date.year ?: return null
        return lunar.toSolar(year, date.date.month, date.date.day, date.leapMonth)
    }

    /** 做七/百日阶段：天数 → 名称（头七、三七、五七、七七、百日；二/四/六七不做）。 */
    private val MOURNING_STAGES = listOf(
        7 to "头七", 21 to "三七", 35 to "五七", 49 to "七七", 100 to "百日"
    )
}

/** “今天”/“明天”/“N天后（M月D日）”——与 ReminderPlanner 的文案口径一致。 */
internal fun dayLabel(daysBefore: Int, occurrence: LocalDate?): String = when {
    daysBefore == 0 -> "今天"
    daysBefore == 1 -> "明天"
    else -> daysBefore.toString() + "天后" +
        (occurrence?.let { "（" + it.monthValue + "月" + it.dayOfMonth + "日）" } ?: "")
}
