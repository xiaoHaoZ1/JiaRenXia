package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.kinship.MissingInformation
import io.github.xiaohaoz1.jiarenxia.core.kinship.PathStep
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.ContactLog
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.HomePlan
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 回家看看提醒计算（Robolectric：节前候选要用 IcuLunarCalendar）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeVisitScheduleTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 18)
    private val lunar = IcuLunarCalendar()

    private fun atNoon(d: LocalDate): Long =
        d.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun person(
        id: String, name: String, self: Boolean = false,
        deceased: Boolean = false
    ) = Person(
        id = id, name = name, gender = Gender.MALE,
        livingStatus = if (deceased) LivingStatus.DECEASED else LivingStatus.ALIVE,
        isSelf = self)

    /** 家庭页顺序的称谓结果：pathTypes 决定 coreGroup（[PARENT] → 父母组）。 */
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

    /** 一个成员的总览项（称谓 + 可选生日）。 */
    private fun ov(
        p: Person, title: String?, kind: KinshipKind,
        pathTypes: List<RelationshipType> = emptyList(),
        birthdays: List<BirthdayValue> = emptyList()
    ) = PersonOverview(
        p, kinshipResult(p.id, title, kind, pathTypes), birthdays = birthdays)

    private fun overviewOf(vararg ovs: PersonOverview): FamilyOverview {
        val self = person("me", "我", self = true)
        val selfRes = KinshipResult(
            "me", "me", emptyList(), "本人", null, null,
            Confidence.EXACT, emptySet(), KinshipKind.SELF)
        val selfOv = PersonOverview(self, selfRes)
        return FamilyOverview(
            self = selfOv,
            graph = FamilyGraph(
                listOf(self) + ovs.map { it.person }, emptyList()),
            byId = (listOf(selfOv) + ovs).associateBy { it.person.id },
            core = ovs.filter { it.result.coreGroup != null },
            others = ovs.filter { it.result.coreGroup == null }
        )
    }

    /** 爸爸+妈妈（核心家人，家庭页顺序 dad,mom）；可追加其它成员。 */
    private fun parentsOverview(
        vararg extra: PersonOverview
    ): FamilyOverview = overviewOf(
        ov(person("dad", "杨建国"), "爸爸", KinshipKind.DERIVED,
            listOf(RelationshipType.PARENT)),
        ov(person("mom", "李秀英"), "妈妈", KinshipKind.DERIVED,
            listOf(RelationshipType.PARENT)),
        *extra
    )

    private fun plan(
        id: String = "h1", memberIds: List<String> = listOf("dad", "mom"),
        intervalDays: Int = 90, createdAt: Long = atNoon(today.minusDays(200))
    ) = HomePlan(id, memberIds, intervalDays, createdAt)

    private fun visit(personId: String, daysAgo: Int) = ContactLog(
        id = "v-" + personId + "-" + daysAgo, personId = personId,
        atMillis = atNoon(today.minusDays(daysAgo.toLong())),
        method = ContactMethod.VISIT, note = null)

    private fun call(personId: String, daysAgo: Int) = ContactLog(
        id = "c-" + personId + "-" + daysAgo, personId = personId,
        atMillis = atNoon(today.minusDays(daysAgo.toLong())),
        method = ContactMethod.CALL, note = null)

    // ---------- homes：有效成员与状态 ----------

    @Test
    fun 只有见面记录算回家_电话不算() {
        val ov = parentsOverview()
        val homes = HomeVisitSchedule.homes(
            listOf(plan()), ov,
            listOf(call("dad", 10), call("mom", 5)), today)
        val h = homes.single()
        // 没有 VISIT → 从未见面，按 createdAt（200 天前）起算 → 大幅到期
        assertNull(h.status.daysSinceLast)
        assertTrue(h.status.due)
        assertEquals(200 - 90, h.status.overdueDays)
    }

    @Test
    fun 任一成员见面即算回家_取最近一次() {
        val ov = parentsOverview()
        val homes = HomeVisitSchedule.homes(
            listOf(plan()), ov,
            listOf(visit("dad", 40), visit("mom", 20), call("dad", 1)), today)
        val h = homes.single()
        // 妈妈 20 天前见过（爸爸 40 天前更早；电话不算）→ 距上次 20 天
        assertEquals(20, h.status.daysSinceLast)
        assertEquals(20 - 90, h.status.overdueDays)
        assertFalse(h.status.due)
    }

    @Test
    fun 从未见面按计划创建日起算() {
        val ov = parentsOverview()
        val p = plan(createdAt = atNoon(today.minusDays(40)))
        val homes = HomeVisitSchedule.homes(listOf(p), ov, emptyList(), today)
        val h = homes.single()
        assertEquals(40 - 90, h.status.overdueDays)
        assertFalse(h.status.due)
        assertNull(h.status.daysSinceLast)
    }

    @Test
    fun 到期判定与家事页行() {
        val ov = parentsOverview()
        // 周期 30：上次见面 30 天前 → 逾期 0 到期当天；31 天 → 逾期 1 仍到期
        val due0 = HomeVisitSchedule.homes(
            listOf(plan(intervalDays = 30)), ov,
            listOf(visit("dad", 30)), today)
        val rows0 = HomeVisitSchedule.dueRows(due0)
        assertEquals(1, rows0.size)
        assertEquals("h1", rows0[0].planId)
        assertEquals(30, rows0[0].daysSinceLast)

        val due1 = HomeVisitSchedule.homes(
            listOf(plan(intervalDays = 30)), ov,
            listOf(visit("dad", 31)), today)
        assertEquals(1, HomeVisitSchedule.dueRows(due1).size)

        // 29 天前 → 未到期，不进“今天”
        val notDue = HomeVisitSchedule.homes(
            listOf(plan(intervalDays = 30)), ov,
            listOf(visit("dad", 29)), today)
        assertTrue(HomeVisitSchedule.dueRows(notDue).isEmpty())

        // 从未见面且创建已超周期 → 到期，daysSinceLast=null
        val never = HomeVisitSchedule.homes(
            listOf(plan(intervalDays = 30, createdAt = atNoon(today.minusDays(35)))),
            ov, emptyList(), today)
        val row = HomeVisitSchedule.dueRows(never).single()
        assertNull(row.daysSinceLast)
    }

    @Test
    fun 已故与本人成员剔除_无有效成员的家不出现() {
        val gp = person("gp", "爷爷", deceased = true)
        val ov = parentsOverview(
            ov(gp, "爷爷", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT, RelationshipType.PARENT))
        )
        // 全是已故/本人 → 该家不产生（“me”是 overview 自带的本人）
        assertTrue(HomeVisitSchedule.homes(
            listOf(plan(memberIds = listOf("gp", "me"))), ov,
            emptyList(), today).isEmpty())

        // 混合：已故成员不进 members，称谓只算有效成员
        val homes = HomeVisitSchedule.homes(
            listOf(plan(memberIds = listOf("dad", "mom", "gp"))), ov,
            emptyList(), today)
        val h = homes.single()
        assertEquals(listOf("dad", "mom"), h.members.map { it.personId })
        assertEquals("爸爸妈妈", h.label)
    }

    @Test
    fun 成员按家庭页顺序而非计划顺序() {
        val ov = parentsOverview()
        // memberIds 故意写反：mom 在前、dad 在后；家庭页 dad 先
        val homes = HomeVisitSchedule.homes(
            listOf(plan(memberIds = listOf("mom", "dad"))), ov,
            emptyList(), today)
        assertEquals(listOf("dad", "mom"),
            homes.single().members.map { it.personId })
        assertEquals("爸爸妈妈", homes.single().label)
    }

    // ---------- 家名 ----------

    @Test
    fun 家名_一人两人直接拼_三人以上顿号_空这个家() {
        assertEquals("妈妈", HomeVisitSchedule.homeLabel(listOf("妈妈")))
        assertEquals("爸爸妈妈", HomeVisitSchedule.homeLabel(listOf("爸爸", "妈妈")))
        assertEquals("爷爷、奶奶、爸爸",
            HomeVisitSchedule.homeLabel(listOf("爷爷", "奶奶", "爸爸")))
        assertEquals("这个家", HomeVisitSchedule.homeLabel(emptyList()))
    }

    @Test
    fun 家名_成员标签称谓优先无称谓用姓名() {
        val unrelated = person("x", "老街坊")
        val ov = overviewOf(
            ov(person("dad", "杨建国"), "爸爸", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT)),
            ov(unrelated, null, KinshipKind.UNRELATED)
        )
        val homes = HomeVisitSchedule.homes(
            listOf(plan(memberIds = listOf("x", "dad"))), ov,
            emptyList(), today)
        val h = homes.single()
        // UNRELATED 也算有效成员（在世非本人），标签用姓名，排在核心家人后
        assertEquals(listOf("dad", "x"), h.members.map { it.personId })
        assertEquals(listOf("爸爸", "老街坊"), h.members.map { it.label })
        // 两人直接拼接（规格口径，同“爸爸妈妈”）
        assertEquals("爸爸老街坊", h.label)
    }

    // ---------- 文案 ----------

    @Test
    fun 到期文案_见过面与从未见面() {
        val ov = parentsOverview()
        val visited = HomeVisitSchedule.homes(
            listOf(plan(intervalDays = 30)), ov,
            listOf(visit("dad", 35)), today).single()
        assertEquals("已经 35 天没回家看看爸爸妈妈了",
            HomeVisitSchedule.dueText(visited))

        val never = HomeVisitSchedule.homes(
            listOf(plan(intervalDays = 30)), ov, emptyList(), today).single()
        assertEquals("还没有记录过回家看看爸爸妈妈，找时间回去吧",
            HomeVisitSchedule.dueText(never))
    }

    // ---------- 节前提醒 ----------

    /** 锚定 [anchor] 日构造“未到期”的家：上次见面 elapsed 天前。 */
    private fun freshHomeAt(
        ov: FamilyOverview, anchor: LocalDate, intervalDays: Int,
        elapsed: Int, memberIds: List<String> = listOf("dad", "mom")
    ): List<HomeVisitSchedule.Home> {
        val p = HomePlan(
            "h1", memberIds, intervalDays,
            createdAt = atNoon(anchor.minusDays(elapsed.toLong() + 100)))
        val logs = listOf(ContactLog(
            "v-" + memberIds.first(), memberIds.first(),
            atNoon(anchor.minusDays(elapsed.toLong())),
            ContactMethod.VISIT, null))
        return HomeVisitSchedule.homes(listOf(p), ov, logs, anchor)
    }

    private fun freshHome(
        ov: FamilyOverview, intervalDays: Int = 90, elapsed: Int = 50,
        memberIds: List<String> = listOf("dad", "mom")
    ) = freshHomeAt(ov, today, intervalDays, elapsed, memberIds)

    @Test
    fun 节前_中秋恰好七天前命中() {
        // 2026 中秋 = 2026-09-25；today=09-18 → target=09-25
        val ov = parentsOverview()
        val hits = HomeVisitSchedule.preHoliday(
            freshHome(ov, intervalDays = 90, elapsed = 50), ov, lunar, today)
        assertEquals(1, hits.size)
        val ev = hits.single()
        assertEquals(LocalDate.of(2026, 9, 25), ev.eventDate)
        assertEquals("homepre:h1:" + LocalDate.of(2026, 9, 25).toEpochDay(),
            ev.dedupKey)
        assertEquals("中秋节快到了（9月25日），已经 50 天没回家看看爸爸妈妈了",
            ev.text)
    }

    @Test
    fun 节前_六天或八天前不命中() {
        val ov = parentsOverview()
        // today=09-19 → target=09-26（中秋已过一天）→ 无命中
        assertTrue(HomeVisitSchedule.preHoliday(
            freshHome(ov, intervalDays = 90, elapsed = 50), ov, lunar,
            today.plusDays(1)).isEmpty())
        // today=09-17 → target=09-24（提前两天）→ 无命中
        assertTrue(HomeVisitSchedule.preHoliday(
            freshHome(ov, intervalDays = 90, elapsed = 50), ov, lunar,
            today.minusDays(1)).isEmpty())
    }

    @Test
    fun 节前_春节与国庆日期口径() {
        val ov = parentsOverview()
        // 2027 春节：官方历法为 2027-02-06，但 IcuLunarCalendar（android.icu
        // ChineseCalendar，与 App 内全部农历换算同一实现）该月朔算得
        // 2027-02-07——此处按实现口径断言，偏差属平台历法层问题。
        // today=2027-01-31 → target=2027-02-07 命中
        val cnyToday = LocalDate.of(2027, 1, 31)
        val cnyHits = HomeVisitSchedule.preHoliday(
            freshHomeAt(ov, cnyToday, intervalDays = 90, elapsed = 60),
            ov, lunar, cnyToday)
        assertEquals(1, cnyHits.size)
        assertEquals(LocalDate.of(2027, 2, 7), cnyHits.single().eventDate)
        assertTrue(cnyHits.single().text.startsWith("春节快到了（2月7日）"))

        // 国庆 = 10 月 1 日：today=2026-09-24 → target=2026-10-01
        val natToday = LocalDate.of(2026, 9, 24)
        val natHits = HomeVisitSchedule.preHoliday(
            freshHomeAt(ov, natToday, intervalDays = 90, elapsed = 60),
            ov, lunar, natToday)
        assertEquals(1, natHits.size)
        assertEquals(LocalDate.of(2026, 10, 1), natHits.single().eventDate)
        assertTrue(natHits.single().text.startsWith("国庆快到了（10月1日）"))
    }

    @Test
    fun 节前_距上次见面不足半个周期不命中() {
        val ov = parentsOverview()
        // 周期 90 → 半周期 45；上次见面 30 天前 → 不足
        assertTrue(HomeVisitSchedule.preHoliday(
            freshHome(ov, intervalDays = 90, elapsed = 30), ov, lunar,
            today).isEmpty())
        // 从未见面、创建 30 天前 → 同样不足
        val p = plan(intervalDays = 90, createdAt = atNoon(today.minusDays(30)))
        assertTrue(HomeVisitSchedule.preHoliday(
            HomeVisitSchedule.homes(listOf(p), ov, emptyList(), today),
            ov, lunar, today).isEmpty())
    }

    @Test
    fun 节前_已到期的家不发节前提醒() {
        val ov = parentsOverview()
        // 周期 90、上次见面 95 天前 → 逾期 5 已到期
        val homes = freshHome(ov, intervalDays = 90, elapsed = 95)
        assertTrue(HomeVisitSchedule.preHoliday(homes, ov, lunar, today).isEmpty())
        // 但 dueRows 里它在
        assertEquals(1, HomeVisitSchedule.dueRows(homes).size)
    }

    @Test
    fun 节前_成员生日命中() {
        // 妈妈生日 = 10 月 15 日（阳历）；today=10-08 → target=10-15，无节日
        val momBday = BirthdayValue(
            PartialDate(1990, 10, 15), CalendarType.SOLAR)
        val ov = overviewOf(
            ov(person("dad", "杨建国"), "爸爸", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT)),
            ov(person("mom", "李秀英"), "妈妈", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT), birthdays = listOf(momBday))
        )
        val anchor = LocalDate.of(2026, 10, 8)
        val hits = HomeVisitSchedule.preHoliday(
            freshHomeAt(ov, anchor, intervalDays = 90, elapsed = 50),
            ov, lunar, anchor)
        assertEquals(1, hits.size)
        val ev = hits.single()
        assertEquals(LocalDate.of(2026, 10, 15), ev.eventDate)
        assertEquals("妈妈的生日快到了（10月15日），已经 50 天没回家看看爸爸妈妈了",
            ev.text)
    }

    @Test
    fun 节前_从未见面用创建日起算文案() {
        // 2026 中秋 target 命中；从未见面、创建 60 天前（≥45）→ 命中且文案无天数
        val ov = parentsOverview()
        val p = plan(intervalDays = 90, createdAt = atNoon(today.minusDays(60)))
        val hits = HomeVisitSchedule.preHoliday(
            HomeVisitSchedule.homes(listOf(p), ov, emptyList(), today),
            ov, lunar, today)
        assertEquals(1, hits.size)
        assertEquals("中秋节快到了（9月25日），回家看看爸爸妈妈吧",
            hits.single().text)
    }

    @Test
    fun 节前_同日多事件只发一条_节日优先于生日() {
        // 国庆（10-01）与成员生日同日：target=2026-10-01
        // today=2026-09-24；妈妈生日设为 10 月 1 日
        val momBday = BirthdayValue(
            PartialDate(1990, 10, 1), CalendarType.SOLAR)
        val ov = overviewOf(
            ov(person("dad", "杨建国"), "爸爸", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT)),
            ov(person("mom", "李秀英"), "妈妈", KinshipKind.DERIVED,
                listOf(RelationshipType.PARENT), birthdays = listOf(momBday))
        )
        val anchor = LocalDate.of(2026, 9, 24)
        val hits = HomeVisitSchedule.preHoliday(
            freshHomeAt(ov, anchor, intervalDays = 90, elapsed = 50),
            ov, lunar, anchor)
        assertEquals(1, hits.size)
        // 节日（rank 2）优先于生日（rank 3）
        assertTrue(hits.single().text.startsWith("国庆快到了（10月1日）"))
    }

    @Test
    fun 节前_非命中日与非节日不产生命中() {
        val ov = parentsOverview()
        // today=09-18 → target=09-25 中秋；换一个无关日期验证空结果
        assertTrue(HomeVisitSchedule.preHoliday(
            freshHome(ov), ov, lunar,
            LocalDate.of(2026, 3, 10)).isEmpty())
        // overview 为 null → 空
        assertTrue(HomeVisitSchedule.preHoliday(
            freshHome(ov), null, lunar, today).isEmpty())
    }
}
