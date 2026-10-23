package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 纪念模式日期规则：相伴天数（当天为第 1 天）、完整周年、
 * 无年份/缺失/冲突/未来不编造数字；农历日期经 lunar 换算。
 * HELLO v4 起每种夫妻纪念日（恋爱/领证/结婚）各算各的时长。
 * （IcuLunarCalendar 用 android.icu → 需要 Robolectric）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TapMemorialTest {

    private val lunar = IcuLunarCalendar()
    private val today = LocalDate.of(2026, 10, 4)

    private fun wedding(year: Int, month: Int, day: Int) =
        TapProtocol.DateWire(TapProtocol.DATE_WEDDING,
            year, month, day, lunar = false, leapMonth = false)

    private fun dating(year: Int, month: Int, day: Int) =
        TapProtocol.DateWire(TapProtocol.DATE_DATING,
            year, month, day, lunar = false, leapMonth = false)

    private fun license(year: Int, month: Int, day: Int) =
        TapProtocol.DateWire(TapProtocol.DATE_LICENSE,
            year, month, day, lunar = false, leapMonth = false)

    private fun lunarW(year: Int, month: Int, day: Int, leap: Boolean = false) =
        TapProtocol.DateWire(TapProtocol.DATE_WEDDING,
            year, month, day, lunar = true, leapMonth = leap)

    private fun display(
        own: TapProtocol.DateWire?,
        peer: TapProtocol.DateWire?,
        at: LocalDate = today
    ) = TapMemorial.display(
        listOfNotNull(own), listOfNotNull(peer), at, lunar)

    @Test
    fun `两边都没日期_普通祝福无提示`() {
        val d = display(null, null)
        assertEquals(TapMemorial.Line.Blessing, d.line)
        assertFalse(d.dateCheckHint)
        assertFalse(d.isAnniversary)
        assertTrue(d.facts.isEmpty())
    }

    @Test
    fun `阳历日期一致_普通日子显示相伴天数`() {
        val w = wedding(2020, 5, 20)
        val d = display(w, w)
        val expect = ChronoUnit.DAYS.between(
            LocalDate.of(2020, 5, 20), today).toInt() + 1
        assertEquals(TapMemorial.Line.Days(expect), d.line)
        assertFalse(d.dateCheckHint)
        assertFalse(d.isAnniversary)
        // 时长行同时给出“结婚 X 年”
        assertEquals(listOf("结婚 6 年 4 个月"), d.facts)
    }

    @Test
    fun `结婚当天是第1天_显示新婚文案`() {
        val w = wedding(today.year, today.monthValue, today.dayOfMonth)
        val d = display(w, w)
        assertEquals(TapMemorial.Line.Newlywed, d.line)
        assertFalse(d.dateCheckHint)
    }

    @Test
    fun `周年当天显示完整周年数`() {
        val w = wedding(2016, 10, 4)
        val d = display(w, w)
        assertEquals(TapMemorial.Line.Anniversary(10), d.line)
        assertTrue(d.isAnniversary)
    }

    @Test
    fun `周年前一天仍显示相伴天数`() {
        val w = wedding(2016, 10, 4)
        val d = display(w, w, LocalDate.of(2026, 10, 3))
        val expect = ChronoUnit.DAYS.between(
            LocalDate.of(2016, 10, 4), LocalDate.of(2026, 10, 3)).toInt() + 1
        assertEquals(TapMemorial.Line.Days(expect), d.line)
        assertFalse(d.isAnniversary)
    }

    @Test
    fun `无年份月日_纪念日当天专属祝福`() {
        val w = wedding(0, 10, 4)
        val d = display(w, w)
        assertEquals(TapMemorial.Line.AnniversaryDay, d.line)
        assertTrue(d.isAnniversary)
        // 非纪念日当天：普通祝福，不编造数字
        val d2 = display(w, w, LocalDate.of(2026, 10, 5))
        assertEquals(TapMemorial.Line.Blessing, d2.line)
        assertFalse(d2.dateCheckHint)
        assertTrue(d2.facts.isEmpty())
    }

    @Test
    fun `只有一边有日期_普通祝福加核对提示`() {
        val w = wedding(2020, 5, 20)
        val d = display(w, null)
        assertEquals(TapMemorial.Line.Blessing, d.line)
        assertTrue(d.dateCheckHint)
        // 对称：另一边缺失也一样
        val d2 = display(null, w)
        assertEquals(TapMemorial.Line.Blessing, d2.line)
        assertTrue(d2.dateCheckHint)
    }

    @Test
    fun `两边日期不一致_不编造数字且提示核对`() {
        val d = display(wedding(2020, 5, 20), wedding(2020, 5, 21))
        assertEquals(TapMemorial.Line.Blessing, d.line)
        assertTrue(d.dateCheckHint)
    }

    @Test
    fun `结婚日期在未来_普通祝福加核对提示`() {
        val w = wedding(2030, 1, 1)
        val d = display(w, w)
        assertEquals(TapMemorial.Line.Blessing, d.line)
        assertTrue(d.dateCheckHint)
    }

    @Test
    fun `农历日期一致_按公历发生日算相伴天数与周年`() {
        // 农历 2016 年八月十五（中秋）
        val w = lunarW(2016, 8, 15)
        val weddingSolar = lunar.toSolar(2016, 8, 15, false)!!
        val d = display(w, w)
        val expect = ChronoUnit.DAYS.between(weddingSolar, today).toInt() + 1
        assertEquals(TapMemorial.Line.Days(expect), d.line)
    }

    @Test
    fun `三种纪念日都一致_按相识领证结婚顺序列时长`() {
        val own = listOf(
            dating(2018, 7, 1), license(2020, 10, 1), wedding(2021, 10, 4))
        val d = TapMemorial.display(own, own, today, lunar)
        // 结婚主行仍在
        assertEquals(TapMemorial.Line.Anniversary(5), d.line)
        assertEquals(
            listOf("相恋 8 年 3 个月", "领证 6 年", "结婚 5 年"),
            d.facts)
        assertFalse(d.dateCheckHint)
        // 完整文案：主行换行接时长行
        assertEquals(
            "结婚 5 周年\n相恋 8 年 3 个月 · 领证 6 年 · 结婚 5 年",
            TapMemorial.text(d))
    }

    @Test
    fun `只录了恋爱纪念日_也能显示时长`() {
        val own = listOf(dating(2018, 7, 1))
        val d = TapMemorial.display(own, own, today, lunar)
        assertEquals(TapMemorial.Line.Blessing, d.line)
        assertEquals(listOf("相恋 8 年 3 个月"), d.facts)
        assertFalse(d.dateCheckHint)
    }

    @Test
    fun `恋爱日期一边缺失_结婚时长照显示但提示核对`() {
        val own = listOf(dating(2018, 7, 1), wedding(2021, 10, 4))
        val peer = listOf(wedding(2021, 10, 4))
        val d = TapMemorial.display(own, peer, today, lunar)
        // 结婚一致：主行与结婚时长正常
        assertEquals(TapMemorial.Line.Anniversary(5), d.line)
        assertEquals(listOf("结婚 5 年"), d.facts)
        // 恋爱一边缺失：提示核对
        assertTrue(d.dateCheckHint)
    }

    @Test
    fun `领证日期不一致_提示核对且不出领证时长`() {
        val own = listOf(license(2020, 10, 1), wedding(2021, 10, 4))
        val peer = listOf(license(2020, 10, 2), wedding(2021, 10, 4))
        val d = TapMemorial.display(own, peer, today, lunar)
        assertEquals(listOf("结婚 5 年"), d.facts)
        assertTrue(d.dateCheckHint)
    }

    @Test
    fun `恋爱日期在未来_不出时长且提示核对`() {
        val own = listOf(dating(2030, 1, 1))
        val d = TapMemorial.display(own, own, today, lunar)
        assertTrue(d.facts.isEmpty())
        assertTrue(d.dateCheckHint)
    }

    @Test
    fun `恋爱只有月日_不出时长也不提示`() {
        val own = listOf(dating(0, 7, 1))
        val d = TapMemorial.display(own, own, today, lunar)
        assertTrue(d.facts.isEmpty())
        assertFalse(d.dateCheckHint)
    }

    @Test
    fun `文案映射`() {
        assertEquals("愿你们白头偕老，岁岁常欢",
            TapMemorial.text(TapMemorial.Line.Blessing))
        assertEquals("喜结连理，新婚快乐",
            TapMemorial.text(TapMemorial.Line.Newlywed))
        assertEquals("今天是你们的结婚纪念日",
            TapMemorial.text(TapMemorial.Line.AnniversaryDay))
        assertEquals("与你相伴第 3650 天",
            TapMemorial.text(TapMemorial.Line.Days(3650)))
        assertEquals("结婚 10 周年",
            TapMemorial.text(TapMemorial.Line.Anniversary(10)))
    }
}
