package io.github.xiaohaoz1.jiarenxia.domain.pregnancy

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 孕期计算与文案（FAMILY_AFFAIRS 26）。 */
class PregnancyMathTest {

    private val today = LocalDate.of(2026, 10, 2)

    @Test
    fun `末次月经加280天得预产期`() {
        val lmp = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 10, 22), PregnancyMath.dueFromLmp(lmp))
    }

    @Test
    fun `孕天数与周数`() {
        // 预产期 = today + 54 天 → 孕 280−54 = 226 天 = 32 周 2 天
        val due = today.plusDays(54)
        assertEquals(226, PregnancyMath.gestationalDays(due, today))
        assertEquals(32, PregnancyMath.gestationalWeeks(due, today))
        assertEquals(2, PregnancyMath.dayInWeek(due, today))
        // 整周边界：孕天数 224 = 32 周 0 天
        val dueW = today.plusDays(56)
        assertEquals(224, PregnancyMath.gestationalDays(dueW, today))
        assertEquals(32, PregnancyMath.gestationalWeeks(dueW, today))
        assertEquals(0, PregnancyMath.dayInWeek(dueW, today))
        // 第 0 天：预产期 = today + 280
        val due0 = today.plusDays(280)
        assertEquals(0, PregnancyMath.gestationalDays(due0, today))
        assertEquals(0, PregnancyMath.gestationalWeeks(due0, today))
    }

    @Test
    fun `孕天数下限夹0`() {
        // 预产期在 280 天之后 → 孕天数不为负（下限 0）
        val due = today.plusDays(300)
        assertEquals(0, PregnancyMath.gestationalDays(due, today))
        // 边界：恰好 today + 280 → 0 天
        assertEquals(0, PregnancyMath.gestationalDays(today.plusDays(280), today))
    }

    @Test
    fun `距预产期天数`() {
        assertEquals(54, PregnancyMath.daysToDue(today.plusDays(54), today))
        assertEquals(0, PregnancyMath.daysToDue(today, today))
        assertEquals(-3, PregnancyMath.daysToDue(today.minusDays(3), today))
    }

    @Test
    fun `预产期校验上下界`() {
        // [today−42, today+280] 含边界
        assertTrue(PregnancyMath.isDueInValidRange(today.minusDays(42), today))
        assertTrue(PregnancyMath.isDueInValidRange(today.plusDays(280), today))
        assertFalse(PregnancyMath.isDueInValidRange(today.minusDays(43), today))
        assertFalse(PregnancyMath.isDueInValidRange(today.plusDays(281), today))
    }

    @Test
    fun `末次月经校验上下界`() {
        // [today−322, today] 含边界（对应预产期 [today−42, today+280]）
        assertTrue(PregnancyMath.isLmpInValidRange(today.minusDays(322), today))
        assertTrue(PregnancyMath.isLmpInValidRange(today, today))
        assertFalse(PregnancyMath.isLmpInValidRange(today.minusDays(323), today))
        assertFalse(PregnancyMath.isLmpInValidRange(today.plusDays(1), today))
    }

    @Test
    fun `孕周文案`() {
        // 孕 226 天 = 32 周 2 天
        val due = today.plusDays(54)
        assertEquals("孕 32 周 2 天", PregnancyMath.gestationalText(due, today))
    }

    @Test
    fun `预产期行文案三种变体`() {
        val due = LocalDate.of(2027, 1, 20)
        // 今天 = 2026-10-02；距预产期 110 天
        assertEquals(
            "预产期 2027年1月20日 · 还有 110 天",
            PregnancyMath.dueLineText(due, today)
        )
        assertEquals(
            "预产期 2027年1月20日 · 就是今天",
            PregnancyMath.dueLineText(due, due)
        )
        assertEquals(
            "预产期 2027年1月20日 · 已过 3 天",
            PregnancyMath.dueLineText(due, due.plusDays(3))
        )
    }

    @Test
    fun `家庭页短状态`() {
        val due = today.plusDays(54)
        assertEquals("孕 32 周 2 天", PregnancyMath.familyShortText(due, today))
        assertEquals("今天预产期", PregnancyMath.familyShortText(due, due))
        assertEquals(
            "已过预产期 3 天",
            PregnancyMath.familyShortText(due, due.plusDays(3))
        )
    }

    @Test
    fun `末次月经行文案`() {
        assertEquals(
            "末次月经 2026年4月15日",
            PregnancyMath.lmpLineText(LocalDate.of(2026, 4, 15))
        )
    }
}
