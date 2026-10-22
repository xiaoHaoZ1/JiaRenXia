package io.github.xiaohaoz1.jiarenxia.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 金额解析与回显（MoneyText）：十进制精确、空/非法/合法三分、
 * 边界与“显示→再保存”往返。
 */
class MoneyTextTest {

    private fun fen(text: String): Long =
        (MoneyText.parse(text) as? MoneyText.Result.Fen)?.value
            ?: error("expected Fen for \"$text\"")

    @Test
    fun 精确转换_不靠Double() {
        // 这些值经 Double*100 会丢精度（409.999…/28.999…/112.999…/200.999…）
        assertEquals(29L, fen("0.29"))
        assertEquals(113L, fen("1.13"))
        assertEquals(201L, fen("2.01"))
        assertEquals(410L, fen("4.10"))
        assertEquals(410L, fen("4.1"))
        assertEquals(0L, fen("0"))
        assertEquals(0L, fen("0.00"))
        assertEquals(68000L, fen("680"))
        assertEquals(5L, fen("0.05"))
        assertEquals(50L, fen(".5"))
        assertEquals(500L, fen("5."))
    }

    @Test
    fun 空与非法分开() {
        assertEquals(MoneyText.Result.Empty, MoneyText.parse(""))
        assertEquals(MoneyText.Result.Empty, MoneyText.parse("   "))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("abc"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("1.2.3"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("."))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("-5"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("12-3"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("1,000"))
    }

    @Test
    fun 小数位与上下限() {
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("0.001"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("4.999"))
        // 上限 999,999,999.99 元 = 99,999,999,999 分
        assertEquals(MoneyText.Result.Fen(99_999_999_999L),
            MoneyText.parse("999999999.99"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("1000000000"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("9999999999"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parse("99999999999999999999"))
    }

    @Test
    fun 万元输入() {
        assertEquals(MoneyText.Result.Empty, MoneyText.parseWan(" "))
        assertEquals(MoneyText.Result.Fen(50_000_000L), MoneyText.parseWan("50"))
        assertEquals(MoneyText.Result.Fen(500_000L), MoneyText.parseWan("0.5"))
        assertEquals(MoneyText.Result.Fen(505_000L), MoneyText.parseWan("0.505"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parseWan("abc"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parseWan("-1"))
        // 万元输入超过 6 位小数（小于 1 分的精度）拒绝而非截断
        assertEquals(MoneyText.Result.Invalid, MoneyText.parseWan("0.0000001"))
        // 上限：99999 万元 = 999,990,000 元合法；100000 万元超限
        assertEquals(MoneyText.Result.Fen(99_999_999_999L),
            MoneyText.parseWan("99999.999999"))
        assertEquals(MoneyText.Result.Invalid, MoneyText.parseWan("100000"))
    }

    @Test
    fun 回显再保存保持原值() {
        listOf(0L, 5L, 29L, 113L, 201L, 410L, 68000L, 99_999_999_999L).forEach { f ->
            assertEquals(f, fen(MoneyText.formatYuanInput(f)))
        }
        assertEquals("680", MoneyText.formatYuanInput(68000))
        assertEquals("4.10", MoneyText.formatYuanInput(410))
        assertEquals("0.05", MoneyText.formatYuanInput(5))
        assertEquals("0.29", MoneyText.formatYuanInput(29))
        // 万元回显：整万不带小数，非整万按分精确
        listOf(0L, 50_000_000L, 500_000L, 505_000L, 123_456L).forEach { f ->
            assertEquals(MoneyText.Result.Fen(f), MoneyText.parseWan(MoneyText.formatWanInput(f)))
        }
        assertEquals("50", MoneyText.formatWanInput(50_000_000L))
        assertEquals("0.5", MoneyText.formatWanInput(500_000L))
        assertEquals("0.505", MoneyText.formatWanInput(505_000L))
    }
}
