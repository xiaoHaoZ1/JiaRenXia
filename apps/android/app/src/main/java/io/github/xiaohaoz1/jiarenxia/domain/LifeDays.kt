package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * “我来到这世界第 N 天”（桌面插件焦点卡）。
 * 出生当天算第 1 天，与“相恋第 N 天”“出生第 N 天”同一算法；出生日取“我”的阳历/农历
 * 生日归并出的真实出生日（BirthdayMath.resolveBirthDate）。没有“我”、没填出生年份、
 * 两份生日换算冲突、或出生日在今天之后时不显示（返回 null）。
 */
object LifeDays {

    /** 焦点卡文案：主标题 + 副文案。 */
    data class LifeLine(val personId: String, val title: String, val note: String)

    /** 副文案里的下一座整千天里程碑步长。 */
    private const val MILESTONE_STEP = 1000

    fun line(
        birthdays: List<BirthdayEntry>,
        today: LocalDate,
        lunar: LunarCalendar,
    ): LifeLine? {
        val mine = birthdays.filter { it.person.isSelf }
        if (mine.isEmpty()) return null
        val values = mine.map { it.birthday }
        val born = BirthdayMath.resolveBirthDate(values, lunar).date ?: return null
        if (born.isAfter(today)) return null
        val n = ChronoUnit.DAYS.between(born, today) + 1
        val next = (n / MILESTONE_STEP + 1) * MILESTONE_STEP
        val age = AgeMath.ageAt(values, today, lunar)
        val parts = mutableListOf<String>()
        if (age != null && age > 0) parts += age.toString() + " 岁"
        parts += "离第 " + next + " 天还有 " + (next - n) + " 天"
        return LifeLine(
            personId = mine.first().person.id,
            title = "我来到这世界第 " + n + " 天",
            note = parts.joinToString(" · "),
        )
    }
}
