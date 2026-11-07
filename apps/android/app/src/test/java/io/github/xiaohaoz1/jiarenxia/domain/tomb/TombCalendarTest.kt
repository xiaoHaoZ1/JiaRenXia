package io.github.xiaohaoz1.jiarenxia.domain.tomb

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.kinship.MissingInformation
import io.github.xiaohaoz1.jiarenxia.core.kinship.PathStep
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 上坟日推算（节日公式 / 农历换算 / 做七 / 名单 / 文案）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TombCalendarTest {

    private val lunar = IcuLunarCalendar()
    private val prefs = TombPrefs()

    // ---------- 清明 / 冬至（寿星公式 2000–2099） ----------

    @Test
    fun 清明各年() {
        val expected = mapOf(
            2020 to "2020-04-04", 2021 to "2021-04-04", 2022 to "2022-04-05",
            2023 to "2023-04-05", 2024 to "2024-04-04", 2025 to "2025-04-04",
            2026 to "2026-04-05", 2027 to "2027-04-05", 2028 to "2028-04-04"
        )
        expected.forEach { (y, s) ->
            assertEquals(LocalDate.parse(s), TombCalendar.qingming(y))
        }
        assertNull(TombCalendar.qingming(1999))
        assertNull(TombCalendar.qingming(2100))
    }

    @Test
    fun 冬至各年_2021例外() {
        val expected = mapOf(
            2020 to "2020-12-21", 2021 to "2021-12-21", 2022 to "2022-12-22",
            2023 to "2023-12-22", 2024 to "2024-12-21", 2025 to "2025-12-21",
            2026 to "2026-12-22"
        )
        expected.forEach { (y, s) ->
            assertEquals(LocalDate.parse(s), TombCalendar.dongzhi(y))
        }
    }

    // ---------- 农历节日 ----------

    @Test
    fun 中元_十五与十四() {
        assertEquals(LocalDate.of(2024, 8, 18), TombCalendar.zhongyuan(2024, 15, lunar))
        assertEquals(LocalDate.of(2025, 9, 6), TombCalendar.zhongyuan(2025, 15, lunar))
        assertEquals(LocalDate.of(2025, 9, 5), TombCalendar.zhongyuan(2025, 14, lunar))
    }

    @Test
    fun 寒衣() {
        assertEquals(LocalDate.of(2024, 11, 1), TombCalendar.hanyi(2024, lunar))
        assertEquals(LocalDate.of(2025, 11, 20), TombCalendar.hanyi(2025, lunar))
    }

    @Test
    fun 除夕() {
        assertEquals(LocalDate.of(2025, 1, 28), TombCalendar.chuxi(2025, lunar))
        assertEquals(LocalDate.of(2026, 2, 16), TombCalendar.chuxi(2026, lunar))
    }

    // ---------- 做七 / 百日 ----------

    private fun person(
        id: String, name: String = id,
        deceased: Boolean = true, self: Boolean = false
    ) = Person(
        id = id, name = name, gender = Gender.MALE,
        livingStatus = if (deceased) LivingStatus.DECEASED else LivingStatus.ALIVE,
        isSelf = self
    )

    private fun deathEntry(
        p: Person, year: Int?, month: Int, day: Int,
        calendar: CalendarType = CalendarType.SOLAR,
        reminderEnabled: Boolean = true
    ) = DateEntry(
        type = ImportantDateType.DEATH_ANNIVERSARY, person = p,
        date = BirthdayValue(PartialDate(year, month, day), calendar),
        reminderEnabled = reminderEnabled
    )

    private fun mournersOf(vararg m: TombMourner) = m.toList()

    @Test
    fun 做七各阶段_阳历去世日() {
        val gp = person("gp", "爷爷")
        val evs = TombCalendar.events(
            today = LocalDate.of(2026, 3, 1), windowDays = 120,
            lunar = lunar,
            mourners = mournersOf(TombMourner("gp", "爷爷", "杨福生")),
            deathEntries = listOf(deathEntry(gp, 2026, 3, 1)),
            prefs = prefs
        ).filter { it.day == TombDay.MOURNING }
        val byDay = evs.associate { it.mourningDayNumber to it.date }
        assertEquals(
            mapOf(
                7 to LocalDate.of(2026, 3, 7),
                21 to LocalDate.of(2026, 3, 21),
                35 to LocalDate.of(2026, 4, 4),
                49 to LocalDate.of(2026, 4, 18),
                100 to LocalDate.of(2026, 6, 8)
            ),
            byDay
        )
        assertEquals("爷爷的三七", evs.single { it.mourningDayNumber == 21 }.title)
        assertEquals(listOf("gp"), evs.single { it.mourningDayNumber == 7 }.personIds)
    }

    @Test
    fun 做七_去世日无年份不产出() {
        val gp = person("gp", "爷爷")
        val evs = TombCalendar.events(
            today = LocalDate.of(2026, 3, 1), windowDays = 120,
            lunar = lunar,
            mourners = mournersOf(TombMourner("gp", "爷爷", "杨福生")),
            deathEntries = listOf(deathEntry(gp, null, 3, 1)),
            prefs = prefs
        )
        assertTrue(evs.none { it.day == TombDay.MOURNING })
    }

    @Test
    fun 做七_农历去世日先换算() {
        val gp = person("gp", "爷爷")
        // 农历 2026 正月十五 → 公历换算后再推做七
        val diedSolar = lunar.toSolar(2026, 1, 15, false)!!
        val evs = TombCalendar.events(
            today = diedSolar, windowDays = 120,
            lunar = lunar,
            mourners = mournersOf(TombMourner("gp", "爷爷", "杨福生")),
            deathEntries = listOf(
                deathEntry(gp, 2026, 1, 15, CalendarType.LUNAR)
            ),
            prefs = prefs
        ).filter { it.day == TombDay.MOURNING }
        val touqi = evs.single { it.mourningDayNumber == 7 }
        assertEquals(diedSolar.plusDays(6), touqi.date)
    }

    @Test
    fun 做七_未启用MOURNING不产出() {
        val gp = person("gp", "爷爷")
        val evs = TombCalendar.events(
            today = LocalDate.of(2026, 3, 1), windowDays = 120,
            lunar = lunar,
            mourners = mournersOf(TombMourner("gp", "爷爷", "杨福生")),
            deathEntries = listOf(deathEntry(gp, 2026, 3, 1)),
            prefs = TombPrefs(days = TombPrefs.DEFAULT_DAYS - TombDay.MOURNING)
        )
        assertTrue(evs.none { it.day == TombDay.MOURNING })
    }

    // ---------- 节日事件与名单 ----------

    @Test
    fun 节日_清明命中窗口() {
        val mourners = mournersOf(TombMourner("gp", "爷爷", "杨福生"))
        // 2026 清明 4/5：03-29（提前 7 天）与 04-04（提前 1 天）命中；04-05 当天不命中
        val at7 = TombCalendar.events(
            LocalDate.of(2026, 3, 29), 30, lunar, mourners, emptyList(), prefs)
        val q = at7.single { it.day == TombDay.QINGMING }
        assertEquals(LocalDate.of(2026, 4, 5), q.date)
        assertEquals(7, TombCalendar.dueDaysBefore(q, LocalDate.of(2026, 3, 29)))
        val at1 = TombCalendar.events(
            LocalDate.of(2026, 4, 4), 30, lunar, mourners, emptyList(), prefs)
        assertEquals(
            1, TombCalendar.dueDaysBefore(
                at1.single { it.day == TombDay.QINGMING },
                LocalDate.of(2026, 4, 4)))
        // 04-05 当天：事件仍在时间线里，但不命中任何提前天数 → 不发通知
        val onDay = TombCalendar.events(
            LocalDate.of(2026, 4, 5), 30, lunar, mourners, emptyList(), prefs)
        val qDay = onDay.first {
            it.day == TombDay.QINGMING && it.date == LocalDate.of(2026, 4, 5)
        }
        assertNull(TombCalendar.dueDaysBefore(qDay, LocalDate.of(2026, 4, 5)))
    }

    @Test
    fun 节日_未启用的日子不出() {
        val mourners = mournersOf(TombMourner("gp", "爷爷", "杨福生"))
        val p = TombPrefs(days = TombPrefs.DEFAULT_DAYS - TombDay.QINGMING)
        // 宽窗口覆盖 2026 全年：中元仍在（默认开启），清明被关掉
        val evs = TombCalendar.events(
            LocalDate.of(2026, 1, 1), 400, lunar, mourners, emptyList(), p)
        assertTrue(evs.none { it.day == TombDay.QINGMING })
        assertTrue(evs.any { it.day == TombDay.ZHONGYUAN })
    }

    @Test
    fun 节日_名单空或全被排除时不产出() {
        // 窗口覆盖 2025 中元（9/6）：名单为空 → 完全没有事件
        val none = TombCalendar.events(
            LocalDate.of(2025, 9, 1), 30, lunar, emptyList(), emptyList(), prefs)
        assertTrue(none.isEmpty())
        // 全部排除 → 同样没有节日事件
        val mourners = mournersOf(
            TombMourner("gp", "爷爷", "杨福生"),
            TombMourner("nm", "奶奶", "李秀兰"))
        val allExcluded = TombPrefs(excludedIds = setOf("gp", "nm"))
        val evs = TombCalendar.events(
            LocalDate.of(2025, 9, 1), 30, lunar, mourners, emptyList(), allExcluded)
        assertTrue(evs.none { it.day != TombDay.MOURNING })
        // 排除一人后名单只剩另一人
        val one = TombPrefs(excludedIds = setOf("nm"))
        val evs2 = TombCalendar.events(
            LocalDate.of(2025, 9, 1), 30, lunar, mourners, emptyList(), one)
        val zy = evs2.single { it.day == TombDay.ZHONGYUAN }
        assertEquals(listOf("gp"), zy.personIds)
        assertEquals(listOf("爷爷"), zy.names)
    }

    @Test
    fun 节日_中元十四变体文案() {
        val mourners = mournersOf(
            TombMourner("gp", "爷爷", "杨福生"),
            TombMourner("nm", "奶奶", "李秀兰"))
        // 2025 中元（十五）= 9/6；窗口覆盖，取事件本体验证 lunarText
        val evs = TombCalendar.events(
            LocalDate.of(2025, 9, 1), 30, lunar, mourners, emptyList(),
            TombPrefs(zhongyuanDay = 14))
        val zy = evs.single { it.day == TombDay.ZHONGYUAN }
        assertEquals(LocalDate.of(2025, 9, 5), zy.date)
        assertEquals("农历七月十四", zy.lunarText)
        val evs15 = TombCalendar.events(
            LocalDate.of(2025, 9, 1), 30, lunar, mourners, emptyList(),
            TombPrefs(zhongyuanDay = 15))
        val zy15 = evs15.single { it.day == TombDay.ZHONGYUAN }
        assertEquals(LocalDate.of(2025, 9, 6), zy15.date)
        assertEquals("农历七月十五", zy15.lunarText)
    }

    // ---------- 名单（mourners） ----------

    private fun kinshipResult(
        targetId: String, title: String?, kind: KinshipKind,
        corePath: Boolean
    ) = KinshipResult(
        observerId = "me", targetId = targetId,
        relationshipPath = if (corePath)
            listOf(PathStep(RelationshipType.PARENT, "me")) else emptyList(),
        structuralDescription = "无直接关系",
        standardTitle = title,
        confidence = Confidence.EXACT,
        missingInformation = if (kind == KinshipKind.UNRELATED)
            setOf(MissingInformation.NO_PATH) else emptySet(),
        kind = kind
    )

    private fun overviewOf(
        vararg persons: Pair<Person, KinshipResult>
    ): FamilyOverview {
        val self = Person("me", "我", Gender.MALE, isSelf = true)
        val selfRes = KinshipResult(
            "me", "me", emptyList(), "本人", null, null,
            Confidence.EXACT, emptySet(), KinshipKind.SELF)
        val selfOv = PersonOverview(self, selfRes)
        val ovs = persons.map { (p, r) -> PersonOverview(p, r) }
        return FamilyOverview(
            self = selfOv,
            graph = FamilyGraph(
                listOf(self) + persons.map { it.first }, emptyList()),
            byId = (listOf(selfOv) + ovs).associateBy { it.person.id },
            core = ovs.filter { it.result.coreGroup != null },
            others = ovs.filter { it.result.coreGroup == null }
        )
    }

    @Test
    fun 名单_排除在世本人与无称谓用姓名() {
        val deceased = person("gp", "杨福生")
        val living = person("dad", "杨建国", deceased = false)
        val unrelated = person("x", "老街坊")
        val ov = overviewOf(
            deceased to kinshipResult("gp", "爷爷", KinshipKind.DERIVED, true),
            living to kinshipResult("dad", "爸爸", KinshipKind.DERIVED, true),
            unrelated to kinshipResult(
                "x", null, KinshipKind.UNRELATED, false)
        )
        val list = TombCalendar.mourners(ov)
        // 在世者与本人不进名单；UNRELATED 排最后、标签用姓名
        assertEquals(listOf("gp", "x"), list.map { it.personId })
        assertEquals("爷爷", list[0].label)
        assertEquals("老街坊", list[1].label)
        assertNull(list[1].title)
    }

    // ---------- 文案 ----------

    @Test
    fun 名单串_三人以内全列_以上截前三() {
        assertEquals("爷爷、奶奶、姥爷",
            TombCalendar.rosterText(listOf("爷爷", "奶奶", "姥爷")))
        assertEquals("爷爷、奶奶、姥爷等 5 位家人",
            TombCalendar.rosterText(
                listOf("爷爷", "奶奶", "姥爷", "大伯", "小姨")))
    }

    @Test
    fun 节日通知文案() {
        val ev = TombEvent(
            day = TombDay.QINGMING, date = LocalDate.of(2026, 4, 5),
            title = "清明节", lunarText = null,
            personIds = listOf("a", "b", "c", "d", "e"),
            names = listOf("爷爷", "奶奶", "姥爷", "大伯", "小姨"))
        // 清明提前 7 天：2026-03-29
        assertEquals(
            "7天后（4月5日）是清明节，记得给爷爷、奶奶、姥爷等 5 位家人上坟",
            TombCalendar.festivalText(ev, 7))
        val zy = ev.copy(
            day = TombDay.ZHONGYUAN, title = "中元节",
            date = LocalDate.of(2025, 9, 6), lunarText = "农历七月十五",
            personIds = listOf("a", "b"), names = listOf("爷爷", "奶奶"))
        assertEquals(
            "今天是中元节（农历七月十五），记得给爷爷、奶奶上坟",
            TombCalendar.festivalText(zy, 0))
    }

    @Test
    fun 做七通知与副标题文案() {
        val ev = TombEvent(
            day = TombDay.MOURNING, date = LocalDate.of(2026, 3, 21),
            title = "爷爷的三七", lunarText = null,
            personIds = listOf("gp"), names = listOf("爷爷"),
            mourningDayNumber = 21)
        assertEquals("明天是爷爷的三七", TombCalendar.mourningText(ev, 1))
        assertEquals("去世第 21 天", TombCalendar.timelineSubtitle(ev))
        val zy = TombEvent(
            day = TombDay.ZHONGYUAN, date = LocalDate.of(2025, 9, 6),
            title = "中元节", lunarText = "农历七月十五",
            personIds = listOf("a", "b"), names = listOf("爷爷", "奶奶"))
        assertEquals(
            "农历七月十五 · 给爷爷、奶奶上坟",
            TombCalendar.timelineSubtitle(zy))
    }

    @Test
    fun 提前天数表() {
        assertEquals(setOf(7, 1), TombCalendar.advanceDays(TombDay.QINGMING))
        assertEquals(setOf(3, 0), TombCalendar.advanceDays(TombDay.ZHONGYUAN))
        assertEquals(setOf(3, 0), TombCalendar.advanceDays(TombDay.HANYI))
        assertEquals(setOf(3, 0), TombCalendar.advanceDays(TombDay.DONGZHI))
        assertEquals(setOf(1, 0), TombCalendar.advanceDays(TombDay.CHUXI))
        assertEquals(setOf(1, 0), TombCalendar.advanceDays(TombDay.MOURNING))
        assertTrue(TombCalendar.advanceDays(TombDay.DEATH_DAY).isEmpty())
    }
}
