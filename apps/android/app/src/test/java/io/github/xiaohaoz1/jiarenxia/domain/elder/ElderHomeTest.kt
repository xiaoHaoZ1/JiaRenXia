package io.github.xiaohaoz1.jiarenxia.domain.elder

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.kinship.MissingInformation
import io.github.xiaohaoz1.jiarenxia.core.kinship.PathStep
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.domain.DateRow
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.TableLunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombEvent
import io.github.xiaohaoz1.jiarenxia.reminder.BirthdayRow
import io.github.xiaohaoz1.jiarenxia.reminder.UpcomingBirthdays
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 长辈模式首页纯逻辑：家人名单与“最近的日子”合成。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ElderHomeTest {

    private val today = LocalDate.of(2026, 11, 7)
    private val lunar = TableLunarCalendar()

    private fun person(
        id: String, name: String, self: Boolean = false,
        deceased: Boolean = false
    ) = Person(
        id = id, name = name, gender = Gender.MALE,
        livingStatus = if (deceased) LivingStatus.DECEASED else LivingStatus.ALIVE,
        isSelf = self)

    private fun kinshipResult(
        targetId: String, title: String?, kind: KinshipKind,
        pathTypes: List<RelationshipType> = emptyList()
    ) = KinshipResult(
        observerId = "me", targetId = targetId,
        relationshipPath = pathTypes.map { PathStep(it, "me") },
        structuralDescription = "无直接关系",
        standardTitle = title,
        confidence = Confidence.EXACT,
        missingInformation = if (kind == KinshipKind.UNRELATED)
            setOf(MissingInformation.NO_PATH) else emptySet(),
        kind = kind
    )

    private fun ov(
        p: Person, title: String?, kind: KinshipKind,
        pathTypes: List<RelationshipType> = emptyList(),
        customTitle: String? = null
    ) = PersonOverview(
        p,
        kinshipResult(p.id, title, kind, pathTypes).copy(customTitle = customTitle))

    private fun overviewOf(vararg ovs: PersonOverview): FamilyOverview {
        val selfP = person("me", "我", self = true)
        val selfRes = KinshipResult(
            "me", "me", emptyList(), "本人", null, null,
            Confidence.EXACT, emptySet(), KinshipKind.SELF)
        val selfOv = PersonOverview(selfP, selfRes)
        return FamilyOverview(
            self = selfOv,
            graph = FamilyGraph(listOf(selfP) + ovs.map { it.person }, emptyList()),
            byId = (listOf(selfOv) + ovs).associateBy { it.person.id },
            core = ovs.filter { it.result.coreGroup != null },
            others = ovs.filter { it.result.coreGroup == null }
        )
    }

    // ---------- 家人名单 ----------

    @Test
    fun 家人名单排除本人与已故_顺序同core加others() {
        val deceasedOv = PersonOverview(
            person("old", "杨老太", deceased = true),
            kinshipResult("old", "爷爷", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT)))
        val overview = overviewOf(
            ov(person("dad", "杨建国"), "爸爸", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT)),
            ov(person("mom", "李秀英"), "妈妈", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT)),
            ov(person("uncle", "杨国强"), null, KinshipKind.UNRELATED),
            deceasedOv
        )
        val members = ElderHome.members(overview)
        assertEquals(listOf("dad", "mom", "uncle"), members.map { it.personId })
        // 有称谓 → label=称谓、hasTitle=true；UNRELATED → label=姓名、hasTitle=false
        assertEquals("爸爸", members[0].label)
        assertTrue(members[0].hasTitle)
        assertEquals("杨国强", members[2].label)
        assertTrue(!members[2].hasTitle)
        assertEquals("杨建国", members[0].name)
    }

    @Test
    fun 空总览家人名单为空() {
        assertTrue(ElderHome.members(null).isEmpty())
    }

    // ---------- 最近的日子 ----------

    private fun birthdayRow(
        pid: String, name: String, title: String?,
        nextSolar: LocalDate, lunarBd: BirthdayValue? = null,
        birthYear: Int? = null
    ): BirthdayRow {
        val p = person(pid, name)
        val bds = buildList {
            birthYear?.let {
                add(BirthdayValue(PartialDate(it, 1, 1), CalendarType.SOLAR))
            }
            lunarBd?.let { add(it) }
        }
        return BirthdayRow(
            person = p, birthdays = bds, title = title,
            daysUntil = java.time.temporal.ChronoUnit.DAYS
                .between(today, nextSolar).toInt(),
            reminderEnabled = true, nextSolar = nextSolar)
    }

    @Test
    fun 生日纪念日上坟合并排序_同日生日在前() {
        val same = today.plusDays(3)
        val birthdays = UpcomingBirthdays.Result(
            today = emptyList(),
            upcoming = listOf(
                birthdayRow("p1", "小明", "儿子", same),
                birthdayRow("p2", "小红", null, same.plusDays(2))
            )
        )
        val dates = listOf(
            DateRow(
                kind = io.github.xiaohaoz1.jiarenxia.core.model
                    .ImportantDateType.DEATH_ANNIVERSARY,
                personId = "p3", label = "爷爷的忌日",
                daysUntil = 3, occurrence = same)
        ) to emptyList<DateRow>()
        val tomb = listOf(
            TombEvent(
                day = TombDay.QINGMING, date = same,
                title = "清明节", lunarText = null,
                personIds = listOf("d1"), names = listOf("爷爷"))
        )
        val rows = ElderHome.upcoming(birthdays, dates, tomb, today)
        // 同日 same：生日 → 忌日 → 上坟
        assertEquals(
            listOf("儿子的生日", "爷爷的忌日", "清明节"),
            rows.filter { it.date == same }.map { it.title }
        )
        // p2 的生日在 same+2
        assertEquals(same.plusDays(2), rows.last().date)
        // 行类型顺序
        val kinds = rows.filter { it.date == same }.map { it.kind }
        assertEquals(
            listOf(
                ElderHome.UpcomingKind.BIRTHDAY,
                ElderHome.UpcomingKind.DATE,
                ElderHome.UpcomingKind.TOMB
            ), kinds)
    }

    @Test
    fun 生日行副标题含姓名农历与年龄_今天显示今天() {
        val solar = BirthdayValue(
            PartialDate(1990, 5, 20), CalendarType.SOLAR)
        val lunarBd = BirthdayValue(
            PartialDate(null, 8, 15), CalendarType.LUNAR)
        val row = birthdayRow(
            "p1", "小明", "儿子", today, lunarBd = lunarBd, birthYear = 1990)
        // 合并双生日为一行（同一公历日）
        val birthdays = UpcomingBirthdays.Result(
            today = listOf(row.copy(birthdays = listOf(solar, lunarBd))),
            upcoming = emptyList())
        val rows = ElderHome.upcoming(birthdays, emptyList<DateRow>() to emptyList(), emptyList(), today)
        val r = rows.single()
        assertEquals("儿子生日".let { "儿子" + "的生日" }, r.title)
        assertEquals("今天", r.dayLabel)
        // 有称谓 → 姓名；农历文案；年龄 = 2026-1990 = 36
        assertEquals("小明 · 农历八月十五 · 36 岁", r.subtitle)
        assertEquals("p1", r.personId)
    }

    @Test
    fun 纪念日行副标题为周年与农历_上坟行副标题用时间线口径() {
        val dates = listOf(
            DateRow(
                kind = io.github.xiaohaoz1.jiarenxia.core.model
                    .ImportantDateType.DEATH_ANNIVERSARY,
                personId = "p1", label = "爷爷的忌日",
                daysUntil = 5, occurrence = today.plusDays(5),
                yearLabel = "3 周年", lunarText = "农历三月初三")
        ) to emptyList<DateRow>()
        val tomb = listOf(
            TombEvent(
                day = TombDay.MOURNING, date = today.plusDays(1),
                title = "爷爷的三七", lunarText = null,
                personIds = listOf("d1"), names = listOf("爷爷"),
                mourningDayNumber = 21)
        )
        val rows = ElderHome.upcoming(
            UpcomingBirthdays.Result(emptyList(), emptyList()),
            dates, tomb, today)
        // 按日期排序：上坟（明天）在忌日（5天后）之前
        assertEquals("爷爷的三七", rows[0].title)
        assertEquals("明天", rows[0].dayLabel)
        assertEquals("去世第 21 天", rows[0].subtitle)
        assertEquals("爷爷的忌日", rows[1].title)
        assertEquals("5天后", rows[1].dayLabel)
        assertEquals("3 周年 · 农历三月初三", rows[1].subtitle)
    }

    @Test
    fun dayLabel口径() {
        assertEquals("今天", ElderHome.dayLabel(0))
        assertEquals("明天", ElderHome.dayLabel(1))
        assertEquals("后天", ElderHome.dayLabel(2))
        assertEquals("7天后", ElderHome.dayLabel(7))
    }

    @Test
    fun 页头行含公历星期与农历() {
        // 2026-11-07 是周六，农历九月廿九
        assertEquals(
            "11月7日 星期六 · 农历九月廿九",
            ElderHome.headerLine(today, lunar))
    }
}
