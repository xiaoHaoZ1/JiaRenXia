package io.github.xiaohaoz1.jiarenxia.widget

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.ContactLog
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry
import io.github.xiaohaoz1.jiarenxia.data.ContactPlan
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarDate
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 桌面插件时间线组装（纯 JVM，农历历法用直通假实现）。 */
class WidgetTimelineTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 15)

    private val passthroughLunar = object : LunarCalendar {
        override fun toSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate =
            LocalDate.of(year, month, day)

        override fun fromSolar(date: LocalDate) =
            LunarDate(date.year, date.monthValue, date.dayOfMonth, leap = false)

        override fun leapMonthOf(lunarYear: Int): Int? = null

        override fun daysInMonth(lunarYear: Int, month: Int, leap: Boolean): Int = 30
    }

    /** 当天中午 12:00 毫秒（联系计划起算用，避开时区边界）。 */
    private fun atNoon(d: LocalDate): Long =
        d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun person(id: String, name: String, self: Boolean = false) = Person(
        id = id, name = name, gender = Gender.MALE, isSelf = self,
        livingStatus = LivingStatus.ALIVE
    )

    private fun task(id: String, due: LocalDate, title: String = "办" + id) = FamilyTask(
        id = id, personId = null, kind = TaskKind.CUSTOM, title = title,
        anchorDate = due, repeat = TaskRepeat.NONE, done = false
    )

    private fun ovOf(p: Person, title: String, self: Boolean = false) = PersonOverview(
        person = p,
        result = KinshipResult(
            observerId = "me", targetId = p.id,
            relationshipPath = emptyList(),
            structuralDescription = title, standardTitle = title,
            confidence = Confidence.EXACT,
            missingInformation = emptySet(),
            kind = if (self) KinshipKind.SELF else KinshipKind.DERIVED
        )
    )

    /** “我”+ 一个家人 的最小总览（联系提醒与称谓需要）。 */
    private fun overviewWith(vararg members: Pair<Person, String>): FamilyOverview {
        val self = person("me", "我", self = true)
        val selfOv = ovOf(self, "我", self = true)
        val ovs = listOf(selfOv) + members.map { (p, t) -> ovOf(p, t) }
        return FamilyOverview(
            self = selfOv,
            graph = FamilyGraph(
                persons = ovs.map { it.person },
                relationships = emptyList()
            ),
            byId = ovs.associateBy { it.person.id },
            core = ovs.filter { it.person.id != "me" },
            others = emptyList()
        )
    }

    @Test
    fun emptyInputs_emptyState() {
        val st = WidgetTimeline.assemble(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            overview = null, today = today, lunar = passthroughLunar
        )
        assertTrue(st is WidgetState.Empty)
    }

    @Test
    fun overdueTaskSortsFirst_thenTodayItems() {
        val dad = person("dad", "张三")
        val st = WidgetTimeline.assemble(
            birthdays = listOf(
                BirthdayEntry(
                    dad, BirthdayValue(PartialDate(1960, 10, 15)),
                    reminderEnabled = false
                )
            ),
            dates = emptyList(),
            tasks = listOf(
                task("t_far", today.plusDays(5), "换驾驶证"),
                task("t_overdue", today.minusDays(3), "体检")
            ),
            plans = emptyList(), logs = emptyList(),
            overview = null, today = today, lunar = passthroughLunar
        ) as WidgetState.Ready
        assertEquals(listOf("t:t_overdue", "b:dad", "t:t_far"), st.items.map { it.key })
        assertEquals(-3, st.items[0].daysUntil)
        assertEquals("张三生日", st.items[1].title)
        assertEquals(WidgetItemKind.TASK, st.items[2].kind)
    }

    @Test
    fun contactDue_showsAsToday_withDaysSinceLast() {
        val dad = person("dad", "张三")
        val overview = overviewWith(dad to "爸爸")
        val st = WidgetTimeline.assemble(
            birthdays = emptyList(), dates = emptyList(), tasks = emptyList(),
            plans = listOf(
                ContactPlan("dad", intervalDays = 7, createdAt = atNoon(today.minusDays(20)))
            ),
            logs = listOf(
                ContactLog(
                    id = "l1", personId = "dad",
                    atMillis = atNoon(today.minusDays(10)),
                    method = ContactMethod.CALL, note = null
                )
            ),
            overview = overview, today = today, lunar = passthroughLunar
        ) as WidgetState.Ready
        assertEquals(1, st.items.size)
        val item = st.items[0]
        assertEquals("该联系爸爸了", item.title)
        assertEquals("已经 10 天没联系", item.note)
        assertEquals(0, item.daysUntil)
        assertEquals("dad", item.personId)
        assertEquals(WidgetItemKind.CONTACT, item.kind)
    }

    @Test
    fun solarAndLunarSameDay_mergeIntoOneRow() {
        val p = person("p1", "张三")
        val st = WidgetTimeline.assemble(
            birthdays = listOf(
                BirthdayEntry(
                    p, BirthdayValue(PartialDate(1960, 10, 15)),
                    reminderEnabled = false
                ),
                BirthdayEntry(
                    p, BirthdayValue(
                        PartialDate(1960, 10, 15), CalendarType.LUNAR),
                    reminderEnabled = false
                )
            ),
            dates = emptyList(), tasks = emptyList(),
            plans = emptyList(), logs = emptyList(),
            overview = null, today = today, lunar = passthroughLunar
        ) as WidgetState.Ready
        assertEquals(1, st.items.size)
        assertEquals("双历同日", st.items[0].note)
    }

    @Test
    fun rowsCappedAt30() {
        val st = WidgetTimeline.assemble(
            birthdays = emptyList(), dates = emptyList(),
            tasks = (1..40).map { task("t$it", today, "事$it") },
            plans = emptyList(), logs = emptyList(),
            overview = null, today = today, lunar = passthroughLunar
        ) as WidgetState.Ready
        assertEquals(WidgetTimeline.MAX_ROWS, st.items.size)
    }
}
