package io.github.xiaohaoz1.jiarenxia.domain.anniversary

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarDate
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CoupleCounterTest {

    private val today = LocalDate.of(2026, 1, 10)
    private val lunar = object : LunarCalendar {
        override fun toSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate =
            LocalDate.of(year, month, day)
        override fun fromSolar(date: LocalDate) =
            LunarDate(date.year, date.monthValue, date.dayOfMonth, leap = false)
        override fun leapMonthOf(lunarYear: Int): Int? = null
        override fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean): Int = 30
    }

    private fun person(id: String, name: String, self: Boolean = false,
                       status: LivingStatus = LivingStatus.ALIVE,
                       gender: Gender = Gender.FEMALE) =
        Person(id = id, name = name, gender = gender, isSelf = self,
               livingStatus = status)

    private fun entry(type: ImportantDateType, who: Person, y: Int, m: Int, d: Int,
                    pid: String? = "w1", pname: String? = "李雪",
                    pSelf: Boolean = false, cal: CalendarType = CalendarType.SOLAR) =
        DateEntry(type = type, person = who,
            date = BirthdayValue(PartialDate(y, m, d), cal),
            reminderEnabled = true,
            partnerId = pid, partnerName = pname, partnerIsSelf = pSelf)

    private fun ovOf(p: Person, customTitle: String? = null) = PersonOverview(
        person = p,
        result = KinshipResult(
            observerId = "me", targetId = p.id,
            relationshipPath = emptyList(),
            structuralDescription = "", standardTitle = "",
            confidence = Confidence.EXACT,
            missingInformation = emptySet(),
            kind = if (p.isSelf) KinshipKind.SELF else KinshipKind.DERIVED
        ),
        customTitle = customTitle,
    )

    private fun overviewOf(vararg ps: Person, titles: Map<String, String> = emptyMap()): FamilyOverview {
        val ovs = ps.map { ovOf(it, titles[it.id]) }
        val selfOv = ovs.first { it.person.isSelf }
        return FamilyOverview(
            self = selfOv,
            graph = FamilyGraph(persons = ps.toList(), relationships = emptyList()),
            byId = ovs.associateBy { it.person.id },
            core = ovs.filter { !it.person.isSelf },
            others = emptyList(),
        )
    }

    private val me get() = person("me", "我", self = true, gender = Gender.MALE)
    private val wife get() = person("w1", "李雪")

    @Test
    fun `恋爱优先于结婚和领证 同日结婚领证只显结婚`() {
        val dates = listOf(
            entry(ImportantDateType.DATING_ANNIVERSARY, me, 2020, 1, 10),
            entry(ImportantDateType.WEDDING_ANNIVERSARY, me, 2021, 5, 20),
            entry(ImportantDateType.LICENSE_ANNIVERSARY, me, 2021, 5, 20))
        val line = CoupleCounter.counter(dates, overviewOf(me, wife), today, lunar)!!
        assertEquals("和李雪相恋第 2193 天", line.title)
        assertEquals("结婚 4 年 7 个月", line.note)
    }

    @Test
    fun `无恋爱时结婚优先 备注含领证`() {
        val dates = listOf(
            entry(ImportantDateType.WEDDING_ANNIVERSARY, me, 2021, 5, 20),
            entry(ImportantDateType.LICENSE_ANNIVERSARY, me, 2021, 6, 1))
        val line = CoupleCounter.counter(dates, overviewOf(me, wife), today, lunar)!!
        assertTrue(line.title.startsWith("和李雪结婚第"))
        assertEquals("领证 4 年 7 个月", line.note)
    }

    @Test
    fun `自定义称呼优先于姓名`() {
        val dates = listOf(entry(ImportantDateType.DATING_ANNIVERSARY, me, 2020, 1, 10))
        val line = CoupleCounter.counter(dates,
            overviewOf(me, wife, titles = mapOf("w1" to "老婆")), today, lunar)!!
        assertTrue(line.title.startsWith("和老婆相恋第"))
    }

    @Test
    fun `无年份与未来起始都算不出`() {
        val noYear = DateEntry(ImportantDateType.DATING_ANNIVERSARY, me,
            BirthdayValue(PartialDate(null, 5, 20), CalendarType.SOLAR), true,
            partnerId = "w1", partnerName = "李雪")
        assertNull(CoupleCounter.counter(listOf(noYear), overviewOf(me, wife), today, lunar))
        assertNull(CoupleCounter.counter(
            listOf(entry(ImportantDateType.DATING_ANNIVERSARY, me, 2030, 1, 1)),
            overviewOf(me, wife), today, lunar))
    }

    @Test
    fun `已故配偶不出现在计数`() {
        val late = person("w1", "李雪", status = LivingStatus.DECEASED)
        val dates = listOf(entry(ImportantDateType.DATING_ANNIVERSARY, me, 2020, 1, 10))
        assertNull(CoupleCounter.counter(dates, overviewOf(me, late), today, lunar))
    }

    @Test
    fun `记录在配偶名下也认`() {
        val dates = listOf(entry(ImportantDateType.DATING_ANNIVERSARY, wife, 2020, 1, 10,
            pid = "me", pname = "我", pSelf = true))
        val line = CoupleCounter.counter(dates, overviewOf(me, wife), today, lunar)!!
        assertEquals("w1", line.partnerId)
        assertTrue(line.title.startsWith("和李雪相恋第"))
    }

    @Test
    fun `起始日就是第 1 天`() {
        val dates = listOf(entry(ImportantDateType.DATING_ANNIVERSARY, me, 2026, 1, 10))
        val line = CoupleCounter.counter(dates, overviewOf(me, wife), today, lunar)!!
        assertTrue(line.title.endsWith("第 1 天"))
    }

    @Test
    fun `农历起始日能换算`() {
        val dates = listOf(entry(ImportantDateType.DATING_ANNIVERSARY, me,
            1989, 8, 16, cal = CalendarType.LUNAR))
        val line = CoupleCounter.counter(dates, overviewOf(me, wife), today, lunar)
        assertNotNull(line)
        assertTrue(line!!.title.contains("相恋第"))
    }

    @Test
    fun `里程碑 1314 落在窗口内`() {
        val start = today.minusDays(1313 - 5)
        val dates = listOf(entry(ImportantDateType.DATING_ANNIVERSARY, me,
            start.year, start.monthValue, start.dayOfMonth))
        val ms = CoupleCounter.milestones(dates, overviewOf(me, wife), today, lunar, 30)
        assertEquals(1, ms.size)
        assertEquals(1314, ms[0].dayNumber)
        assertEquals(5, ms[0].daysUntil)
        assertTrue(ms[0].label.contains("相恋第 1314 天"))
    }

    @Test
    fun `窗口边界 0 与 30 都收`() {
        val dates = listOf(
            entry(ImportantDateType.DATING_ANNIVERSARY, me, today.minusDays(99).year,
                today.minusDays(99).monthValue, today.minusDays(99).dayOfMonth),
            entry(ImportantDateType.WEDDING_ANNIVERSARY, me, today.minusDays(69).year,
                today.minusDays(69).monthValue, today.minusDays(69).dayOfMonth))
        val ms = CoupleCounter.milestones(dates, overviewOf(me, wife), today, lunar, 30)
        assertEquals(setOf(0, 30), ms.map { it.daysUntil }.toSet())
    }

    @Test
    fun `同日起始跨类型去重保优先`() {
        val s = today.minusDays(99)
        val dates = listOf(
            entry(ImportantDateType.WEDDING_ANNIVERSARY, me, s.year, s.monthValue, s.dayOfMonth),
            entry(ImportantDateType.LICENSE_ANNIVERSARY, me, s.year, s.monthValue, s.dayOfMonth))
        val ms = CoupleCounter.milestones(dates, overviewOf(me, wife), today, lunar, 30)
        assertEquals(1, ms.size)
        assertEquals(ImportantDateType.WEDDING_ANNIVERSARY, ms[0].type)
    }

    @Test
    fun `非自己夫妻的条目不参与`() {
        val dad = person("dad", "爸", gender = Gender.MALE)
        val dates = listOf(entry(ImportantDateType.WEDDING_ANNIVERSARY, dad,
            1990, 1, 1, pid = "w1", pname = "妈"))
        val ov = overviewOf(me, dad, wife)
        assertNull(CoupleCounter.counter(dates, ov, today, lunar))
        assertTrue(CoupleCounter.milestones(dates, ov, today, lunar, 30).isEmpty())
    }
}
