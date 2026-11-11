package io.github.xiaohaoz1.jiarenxia.domain.med

import io.github.xiaohaoz1.jiarenxia.core.model.MedMeal
import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用药状态/摘要/到期集合/长期确认判定（FAMILY_AFFAIRS 23，纯 JVM）。 */
class MedScheduleTest {

    private val today = LocalDate.of(2026, 10, 2)

    private fun med(
        usage: MedUsage = MedUsage.SHORT_COURSE,
        courseDays: Int? = 7,
        start: LocalDate = today.minusDays(2),   // 第 3 天
        refill: LocalDate? = null,
        stopped: LocalDate? = null,
        dose: String? = null,
        spec: String? = null,
        meal: MedMeal? = null,
        times: List<Int> = emptyList(),
        name: String = "阿莫西林"
    ) = Medication(
        id = "m1", personId = "p1", name = name,
        dose = dose, spec = spec, meal = meal, usage = usage,
        courseDays = courseDays, startEpochDay = start.toEpochDay(),
        purpose = null, note = null,
        refillEpochDay = refill?.toEpochDay(),
        stoppedEpochDay = stopped?.toEpochDay(),
        times = times
    )

    // ---------- statusOf ----------

    @Test
    fun 已停用优先于其他状态() {
        val m = med(stopped = today, courseDays = 1, start = today.minusDays(10))
        assertEquals(MedSchedule.Status.STOPPED, MedSchedule.statusOf(m, today))
    }

    @Test
    fun 短期疗程最后一天之后为到期() {
        val m = med(courseDays = 3, start = today.minusDays(3))  // 最后一天昨天
        assertEquals(MedSchedule.Status.COURSE_ENDED,
            MedSchedule.statusOf(m, today))
        // 最后一天当天仍 ACTIVE
        val m2 = med(courseDays = 3, start = today.minusDays(2))
        assertEquals(MedSchedule.Status.ACTIVE, MedSchedule.statusOf(m2, today))
    }

    @Test
    fun 短期待确认与其他用法恒为进行中() {
        assertEquals(MedSchedule.Status.ACTIVE,
            MedSchedule.statusOf(med(courseDays = null), today))
        assertEquals(MedSchedule.Status.ACTIVE,
            MedSchedule.statusOf(med(usage = MedUsage.LONG_TERM, courseDays = null), today))
        assertEquals(MedSchedule.Status.ACTIVE,
            MedSchedule.statusOf(med(usage = MedUsage.AS_NEEDED, courseDays = null), today))
    }

    // ---------- dayNumber ----------

    @Test
    fun 天数按开始日期算_未开始为null() {
        assertEquals(3, MedSchedule.dayNumber(med(), today))
        assertNull(MedSchedule.dayNumber(med(start = today.plusDays(1)), today))
    }

    // ---------- summaryOf 主摘要 ----------

    @Test
    fun 短期有天数进行中() {
        val s = MedSchedule.summaryOf(med(), today)
        assertEquals("短期疗程 · 记录疗程 7 天，按开始日期计算今天是第 3 天",
            s.mainLine)
        assertFalse(s.coursePending)
    }

    @Test
    fun 短期有天数未开始() {
        val s = MedSchedule.summaryOf(
            med(courseDays = 7, start = LocalDate.of(2026, 10, 25)), today)
        assertEquals("短期疗程 · 记录疗程 7 天，10月25日开始", s.mainLine)
    }

    @Test
    fun 短期待确认() {
        val s = MedSchedule.summaryOf(med(courseDays = null), today)
        assertEquals("短期疗程 · 疗程待确认，请向医生或药师核对", s.mainLine)
        assertTrue(s.coursePending)
    }

    @Test
    fun 记录疗程已到期() {
        val s = MedSchedule.summaryOf(
            med(courseDays = 7, start = today.minusDays(10)), today)
        assertEquals("记录疗程已到期（7 天），实际停用待确认", s.mainLine)
    }

    @Test
    fun 长期吃有无拿药日() {
        assertEquals("长期吃", MedSchedule.summaryOf(
            med(usage = MedUsage.LONG_TERM, courseDays = null), today).mainLine)
        assertEquals("长期吃 · 下次拿药 1月5日", MedSchedule.summaryOf(
            med(usage = MedUsage.LONG_TERM, courseDays = null,
                refill = LocalDate.of(2027, 1, 5)), today).mainLine)
    }

    @Test
    fun 需要时吃() {
        assertEquals("需要时吃", MedSchedule.summaryOf(
            med(usage = MedUsage.AS_NEEDED, courseDays = null), today).mainLine)
    }

    @Test
    fun 已停用摘要() {
        val s = MedSchedule.summaryOf(med(stopped = today), today)
        assertEquals("记录已停用", s.mainLine)
    }

    // ---------- detailLine ----------

    @Test
    fun 第二行按序拼接() {
        val s = MedSchedule.summaryOf(
            med(dose = "1 片", spec = "500mg/片",
                times = listOf(1200, 480), meal = MedMeal.AFTER), today)
        assertEquals("每次 1 片 · 500mg/片 · 08:00、20:00 · 饭后", s.detailLine)
    }

    @Test
    fun 第二行无内容为null() {
        assertNull(MedSchedule.summaryOf(med(), today).detailLine)
    }

    // ---------- duesOn ----------

    @Test
    fun 疗程最后一天产生到期() {
        val last = today
        val m = med(courseDays = 3, start = last.minusDays(2))
        val dues = MedSchedule.duesOn(m, last)
        assertEquals(1, dues.size)
        assertEquals(MedSchedule.DueKind.COURSE_END, dues[0].kind)
        assertEquals(last.toEpochDay(), dues[0].epochDay)
        // 前一天 / 后一天都不发
        assertTrue(MedSchedule.duesOn(m, last.minusDays(1)).isEmpty())
        assertTrue(MedSchedule.duesOn(m, last.plusDays(1)).isEmpty())
    }

    @Test
    fun 续药只在拿药日前三天与当天() {
        val refill = today.plusDays(3)
        val m = med(usage = MedUsage.LONG_TERM, courseDays = null, refill = refill)
        val dues = MedSchedule.duesOn(m, today)
        assertEquals(1, dues.size)
        assertEquals(MedSchedule.DueKind.REFILL, dues[0].kind)
        assertEquals(3, dues[0].daysBefore)
        // 当天（daysBefore=0）
        val dues0 = MedSchedule.duesOn(m, refill)
        assertEquals(1, dues0.size)
        assertEquals(0, dues0[0].daysBefore)
        // 其他日子不发
        assertTrue(MedSchedule.duesOn(m, today.plusDays(1)).isEmpty())
        assertTrue(MedSchedule.duesOn(m, today.plusDays(2)).isEmpty())
        assertTrue(MedSchedule.duesOn(m, today.plusDays(4)).isEmpty())
    }

    @Test
    fun 短期与需要时吃永不产生续药() {
        val refill = today.plusDays(3)
        val short = med(usage = MedUsage.SHORT_COURSE, courseDays = null,
            refill = refill)
        assertTrue(MedSchedule.duesOn(short, today).none {
            it.kind == MedSchedule.DueKind.REFILL })
        val asNeeded = med(usage = MedUsage.AS_NEEDED, courseDays = null,
            refill = refill)
        assertTrue(MedSchedule.duesOn(asNeeded, today).isEmpty())
        // 短期即使填了拿药日字段也不续药（非 LONG_TERM）
        val shortDays = med(courseDays = 30, refill = refill)
        assertTrue(MedSchedule.duesOn(shortDays, today).none {
            it.kind == MedSchedule.DueKind.REFILL })
    }

    @Test
    fun 已停用不产生任何提醒() {
        val m = med(courseDays = 1, start = today, stopped = today)
        assertTrue(MedSchedule.duesOn(m, today).isEmpty())
    }

    // ---------- needsLongTermConfirm ----------

    @Test
    fun 长期确认判定() {
        // 新建选长期 → 弹
        assertTrue(MedSchedule.needsLongTermConfirm(null, MedUsage.LONG_TERM))
        // 短期/需要时吃改成长期 → 弹
        assertTrue(MedSchedule.needsLongTermConfirm(
            MedUsage.SHORT_COURSE, MedUsage.LONG_TERM))
        assertTrue(MedSchedule.needsLongTermConfirm(
            MedUsage.AS_NEEDED, MedUsage.LONG_TERM))
        // 长期保持长期 → 不弹
        assertFalse(MedSchedule.needsLongTermConfirm(
            MedUsage.LONG_TERM, MedUsage.LONG_TERM))
        // 其他用法互切 → 不弹
        assertFalse(MedSchedule.needsLongTermConfirm(
            MedUsage.SHORT_COURSE, MedUsage.AS_NEEDED))
        assertFalse(MedSchedule.needsLongTermConfirm(
            null, MedUsage.SHORT_COURSE))
        assertFalse(MedSchedule.needsLongTermConfirm(null, null))
    }
}
