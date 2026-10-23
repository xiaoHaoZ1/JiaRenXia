package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.AffairTimeline
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.anniversary.CoupleCounter
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarDate
import io.github.xiaohaoz1.jiarenxia.widget.WidgetState
import io.github.xiaohaoz1.jiarenxia.widget.WidgetTimeline
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** 里程碑提醒/时间线/插件组装（ReminderPlanner.dueDateReminders 概览路径）。 */
class CoupleMilestoneReminderTest {

    private val today = LocalDate.of(2026, 10, 23)

    private val lunar = object : LunarCalendar {
        override fun toSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate =
            LocalDate.of(year, month, day)
        override fun fromSolar(date: LocalDate) =
            LunarDate(date.year, date.monthValue, date.dayOfMonth, leap = false)
        override fun leapMonthOf(lunarYear: Int): Int? = null
        override fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean): Int = 30
    }

    private fun p(id: String, name: String, self: Boolean = false) =
        Person(id = id, name = name, gender = Gender.FEMALE,
               isSelf = self, livingStatus = LivingStatus.ALIVE)

    private fun e(type: ImportantDateType, start: LocalDate,
                  pid: String? = "w1", pname: String? = "李雪",
                  enabled: Boolean = true, offsets: Set<Int> = setOf(0, 3)) =
        DateEntry(type = type, person = p("me", "Alex", self = true),
            date = BirthdayValue(PartialDate(start.year, start.monthValue, start.dayOfMonth)),
            reminderEnabled = enabled, reminderOffsets = offsets,
            partnerId = pid, partnerName = pname)

    private fun ov(customTitle: String? = null): FamilyOverview {
        val me = p("me", "Alex", self = true)
        val wife = p("w1", "李雪")
        fun ovx(pp: Person) = PersonOverview(
            person = pp,
            result = KinshipResult(
                observerId = "me", targetId = pp.id,
                relationshipPath = emptyList(),
                structuralDescription = "", standardTitle = "",
                confidence = Confidence.EXACT,
                missingInformation = emptySet(),
                kind = if (pp.isSelf) KinshipKind.SELF else KinshipKind.DERIVED),
            customTitle = if (pp.id == "w1") customTitle else null)
        val ovs = listOf(ovx(me), ovx(wife))
        return FamilyOverview(ovx(me),
            FamilyGraph(listOf(me, wife), emptyList()),
            ovs.associateBy { it.person.id }, listOf(ovx(wife)), emptyList())
    }

    private val start1000 = today.minusDays(999 - 3) // 第 1000 天在 3 天后

    @Test
    fun `里程碑文案_3天后与偏移匹配`() {
        val dues = ReminderPlanner.dueDateReminders(
            listOf(e(ImportantDateType.DATING_ANNIVERSARY, start1000)),
            emptyMap(), today, lunar, ov("老婆"))
        val m = dues.filter { it.milestoneDays == 1000 }
        assertEquals(1, m.size)
        assertEquals("3天后（10月26日）是你和老婆相恋第 1000 天",
            ReminderPlanner.text(m[0], isSelf = true))
    }

    @Test
    fun `里程碑文案_今天`() {
        val dues = ReminderPlanner.dueDateReminders(
            listOf(e(ImportantDateType.DATING_ANNIVERSARY, today.minusDays(999))),
            emptyMap(), today, lunar, ov("老婆"))
        assertEquals("今天是你和老婆相恋第 1000 天",
            ReminderPlanner.text(dues.single { it.milestoneDays == 1000 }, true))
    }

    @Test
    fun `里程碑_不在偏移内与关闭提醒都不发`() {
        val notOffset = e(ImportantDateType.DATING_ANNIVERSARY, start1000,
            offsets = setOf(0, 1))
        assertTrue(ReminderPlanner.dueDateReminders(
            listOf(notOffset), emptyMap(), today, lunar, ov())
            .none { it.milestoneDays != null })
        val off = e(ImportantDateType.DATING_ANNIVERSARY, today.minusDays(999),
            enabled = false)
        assertTrue(ReminderPlanner.dueDateReminders(
            listOf(off), emptyMap(), today, lunar, ov())
            .none { it.milestoneDays != null })
    }

    @Test
    fun `dateRows_出里程碑行带personId与天数`() {
        val rows = AffairTimeline.dateRows(
            listOf(e(ImportantDateType.DATING_ANNIVERSARY, start1000)),
            emptyMap(), today, lunar, ov("老婆"))
        val m = (rows.first + rows.second).single { it.milestoneDays == 1000 }
        assertEquals("w1", m.personId)
        assertEquals(3, m.daysUntil)
        assertEquals("和老婆相恋第 1000 天", m.label)
        assertNull(m.yearLabel); assertNull(m.lunarText)
    }

    @Test
    fun `assemble_只有计数无条目也Ready`() {
        val st = WidgetTimeline.assemble(
            emptyList(), listOf(e(ImportantDateType.DATING_ANNIVERSARY, start1000)),
            emptyList(), emptyList(), emptyList(), ov(), today, lunar)
        assertTrue(st is WidgetState.Ready)
        val r = st as WidgetState.Ready
        assertNotNull(r.couple)
        // 里程碑行经 dateRows 进入 items
        assertTrue(r.items.any { it.title.contains("第 1000 天") })
    }

    @Test
    fun `多位候选取起始日最新的那位`() {
        val e1 = e(ImportantDateType.DATING_ANNIVERSARY, today.minusDays(999),
            pid = "w1", pname = "旧人")
        // 另一位“配偶”（同名 partnerId 不同的情形少见，构造另一位）
        val e2 = DateEntry(ImportantDateType.WEDDING_ANNIVERSARY,
            p("me", "Alex", self = true),
            BirthdayValue(PartialDate(today.minusDays(500).year,
                today.minusDays(500).monthValue, today.minusDays(500).dayOfMonth)),
            true, partnerId = "w2", partnerName = "新人")
        val line = CoupleCounter.counter(listOf(e1, e2), ov(), today, lunar)
        // w2 起始更新 → 主计数应为新人
        assertEquals("w2", line!!.partnerId)
    }
}
