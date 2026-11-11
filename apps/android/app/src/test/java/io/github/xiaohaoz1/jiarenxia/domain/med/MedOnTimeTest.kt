package io.github.xiaohaoz1.jiarenxia.domain.med

import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.core.model.MedicationPersonPrefs
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「按时吃」纯逻辑：槽位 / 下一次触发 / 送达文案 / 高亮判定。 */
class MedOnTimeTest {

    private val today = LocalDate.of(2026, 11, 11)
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun med(
        id: String = "m1",
        personId: String = "me",
        name: String = "二甲双胍",
        dose: String? = "1 片",
        usage: MedUsage = MedUsage.LONG_TERM,
        courseDays: Int? = null,
        start: LocalDate = today.minusDays(10),
        times: List<Int> = listOf(480),
        stopped: LocalDate? = null
    ) = Medication(
        id = id, personId = personId, name = name, dose = dose,
        spec = null, meal = null, usage = usage, courseDays = courseDays,
        startEpochDay = start.toEpochDay(), purpose = null, note = null,
        refillEpochDay = null,
        stoppedEpochDay = stopped?.toEpochDay(), times = times
    )

    private fun pref(personId: String, onTime: Boolean?) =
        MedicationPersonPrefs(personId, onTimeEnabled = onTime)

    private val allOn = MedOnTime.Settings(true, true)

    // ---------- slots ----------

    @Test
    fun `总开关或子开关关闭都不产出槽位`() {
        val m = med()
        assertTrue(MedOnTime.slots(
            listOf(m), emptyMap(), "me", setOf("me"),
            MedOnTime.Settings(false, true), today).isEmpty())
        assertTrue(MedOnTime.slots(
            listOf(m), emptyMap(), "me", setOf("me"),
            MedOnTime.Settings(true, false), today).isEmpty())
    }

    @Test
    fun `默认规则我开他人关且显式值优先`() {
        val self = med(id = "s", personId = "me", times = listOf(480))
        val other = med(id = "o", personId = "mom", times = listOf(600))
        val otherOn = med(id = "o2", personId = "dad", times = listOf(660))
        val prefs = mapOf(
            "dad" to pref("dad", true),
            "mom" to pref("mom", false)
        )
        val slots = MedOnTime.slots(
            listOf(self, other, otherOn), prefs, "me",
            setOf("me", "mom", "dad"), allOn, today)
        // me 默认开；mom 显式关；dad 显式开（按 personId 再按时刻排序）
        assertEquals(
            listOf("dad" to 660, "me" to 480),
            slots.map { it.personId to it.minute }
        )
        // “我”显式关 → 默认被覆盖
        val selfOff = MedOnTime.slots(
            listOf(self), mapOf("me" to pref("me", false)), "me",
            setOf("me"), allOn, today)
        assertTrue(selfOff.isEmpty())
    }

    @Test
    fun `已故与不在世名单不排`() {
        val m = med(personId = "me")
        assertTrue(MedOnTime.slots(
            listOf(m), emptyMap(), "me", emptySet(), allOn, today).isEmpty())
    }

    @Test
    fun `停用需要时吃无时刻未开始都不排`() {
        val stopped = med(id = "a", stopped = today.minusDays(1))
        val prn = med(id = "b", usage = MedUsage.AS_NEEDED, times = emptyList())
        val noTimes = med(id = "c", times = emptyList())
        val future = med(id = "d", start = today.plusDays(1))
        val slots = MedOnTime.slots(
            listOf(stopped, prn, noTimes, future), emptyMap(), "me",
            setOf("me"), allOn, today)
        assertTrue(slots.isEmpty())
    }

    @Test
    fun `短期最后一天照排次日不排待确认照排`() {
        val last = med(
            id = "a", usage = MedUsage.SHORT_COURSE, courseDays = 3,
            start = today.minusDays(2), times = listOf(480))
        val ended = med(
            id = "b", usage = MedUsage.SHORT_COURSE, courseDays = 3,
            start = today.minusDays(3), times = listOf(540))
        val pending = med(
            id = "c", usage = MedUsage.SHORT_COURSE, courseDays = null,
            start = today.minusDays(5), times = listOf(600))
        val slots = MedOnTime.slots(
            listOf(last, ended, pending), emptyMap(), "me",
            setOf("me"), allOn, today)
        // last：今天是第 3 天（最后一天）照排；ended：今天已是第 4 天不排；
        // pending：待确认照排
        assertEquals(
            listOf(480 to listOf("a"), 600 to listOf("c")),
            slots.map { it.minute to it.meds.map { m -> m.id } }
        )
    }

    @Test
    fun `同一时刻多药合并不同时刻分槽`() {
        val a = med(id = "a", name = "甲", times = listOf(480, 1200))
        val b = med(id = "b", name = "乙", dose = null, times = listOf(480))
        val slots = MedOnTime.slots(
            listOf(a, b), emptyMap(), "me", setOf("me"), allOn, today)
        assertEquals(2, slots.size)
        val at480 = slots.first { it.minute == 480 }
        assertEquals(listOf("a", "b"), at480.meds.map { it.id })
        assertEquals(listOf("a"), slots.first { it.minute == 1200 }.meds.map { it.id })
    }

    // ---------- nextTrigger ----------

    private fun slotOf(m: Medication, minute: Int = 480) =
        MedOnTime.Slot(m.personId, minute, listOf(m))

    @Test
    fun `今天时刻未过排今天已过排明天`() {
        val m = med()
        val slot = slotOf(m)
        val morning = ZonedDateTime.of(2026, 11, 11, 7, 0, 0, 0, zone)
        val evening = ZonedDateTime.of(2026, 11, 11, 21, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 11, 8, 0, 0, 0, zone),
            MedOnTime.nextTrigger(slot, morning))
        assertEquals(
            ZonedDateTime.of(2026, 11, 12, 8, 0, 0, 0, zone),
            MedOnTime.nextTrigger(slot, evening))
        // 正好到点也算未过
        val exact = ZonedDateTime.of(2026, 11, 11, 8, 0, 0, 0, zone)
        assertEquals(exact, MedOnTime.nextTrigger(slot, exact))
    }

    @Test
    fun `短期到期后找不到有效日返回空`() {
        // 今天是最后一天且时刻已过 → 明天起全部无效 → null
        val m = med(
            usage = MedUsage.SHORT_COURSE, courseDays = 1,
            start = today, times = listOf(480))
        val slot = slotOf(m)
        val night = ZonedDateTime.of(2026, 11, 11, 23, 0, 0, 0, zone)
        assertNull(MedOnTime.nextTrigger(slot, night))
    }

    @Test
    fun `待确认疗程一直有效`() {
        val m = med(
            usage = MedUsage.SHORT_COURSE, courseDays = null,
            start = today.minusDays(30), times = listOf(480))
        val slot = slotOf(m)
        val night = ZonedDateTime.of(2026, 11, 11, 23, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 11, 12, 8, 0, 0, 0, zone),
            MedOnTime.nextTrigger(slot, night))
    }

    // ---------- deliveryText ----------

    private fun at(minute: Int, dayOffset: Int = 0) =
        ZonedDateTime.of(
            2026, 11, 11 + dayOffset, minute / 60, minute % 60, 0, 0, zone
        ).toInstant()

    @Test
    fun `超过六十分钟不发整六十分钟仍发`() {
        val m = listOf(med())
        val sched = at(480)
        assertNull(MedOnTime.deliveryText(null, true, 480, m, sched,
            sched.plusSeconds(60 * 60 + 1)))
        // 整 60 分钟：仍发，走“的药”文案
        assertEquals("08:00 的药：二甲双胍 1 片",
            MedOnTime.deliveryText(null, true, 480, m, sched,
                sched.plusSeconds(60 * 60)))
    }

    @Test
    fun `五分钟整用该吃药了之后用的药`() {
        val m = listOf(med())
        val sched = at(480)
        assertEquals("08:00 该吃药了：二甲双胍 1 片",
            MedOnTime.deliveryText(null, true, 480, m, sched,
                sched.plusSeconds(5 * 60)))
        assertEquals("08:00 的药：二甲双胍 1 片",
            MedOnTime.deliveryText(null, true, 480, m, sched,
                sched.plusSeconds(5 * 60 + 1)))
        // 刚触发
        assertEquals("08:00 该吃药了：二甲双胍 1 片",
            MedOnTime.deliveryText(null, true, 480, m, sched, sched))
    }

    @Test
    fun `他人文案带称谓多药连接待确认加后缀`() {
        val m = listOf(
            med(id = "a", personId = "mom", name = "二甲双胍", dose = "1 片"),
            med(id = "b", personId = "mom", name = "阿司匹林",
                dose = null, usage = MedUsage.SHORT_COURSE,
                courseDays = null, times = listOf(480))
        )
        val sched = at(480)
        assertEquals(
            "08:00 妈妈该吃药了：二甲双胍 1 片、阿司匹林（疗程待确认）",
            MedOnTime.deliveryText("妈妈", false, 480, m, sched,
                sched.plusSeconds(60)))
        assertEquals(
            "08:00 妈妈的药：二甲双胍 1 片、阿司匹林（疗程待确认）",
            MedOnTime.deliveryText("妈妈", false, 480, m, sched,
                sched.plusSeconds(30 * 60)))
    }

    @Test
    fun `药名列表无用量只写药名`() {
        val m = listOf(med(dose = null))
        assertEquals("二甲双胍",
            MedOnTime.drugListText(m))
    }

    // ---------- highlightIndex ----------

    @Test
    fun `高亮取第一个不早于现在三十分钟前的行`() {
        val minutes = listOf(480, 720, 1200)   // 08:00 / 12:00 / 20:00
        // 现在 12:10 → 阈值 11:40 → 12:00 行高亮（下标 1）
        assertEquals(1, MedOnTime.highlightIndex(minutes, 12 * 60 + 10))
        // 现在 07:00 → 阈值 06:30 → 08:00 行高亮（下标 0）
        assertEquals(0, MedOnTime.highlightIndex(minutes, 7 * 60))
        // 现在 21:00 → 阈值 20:30 → 无行 → -1
        assertEquals(-1, MedOnTime.highlightIndex(minutes, 21 * 60))
        // 阈值边界：现在 08:29 → 阈值 07:59 → 08:00 行（下标 0）
        assertEquals(0, MedOnTime.highlightIndex(minutes, 8 * 60 + 29))
        // 现在 08:30 → 阈值 08:00 → 08:00 行仍命中（≥）
        assertEquals(0, MedOnTime.highlightIndex(minutes, 8 * 60 + 30))
    }
}
