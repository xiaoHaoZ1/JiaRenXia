package io.github.xiaohaoz1.jiarenxia.ui.common

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * 日期选择器的日历日 ↔ 毫秒换算：固定 UTC 语义，
 * 设备默认时区（北京/UTC/负时区）不影响预选与回填。
 */
class JrxDatePickerTest {

    private val zones = listOf(
        "Asia/Shanghai",   // UTC+8：本地零点会落到前一日 UTC，正是原 bug
        "UTC",
        "America/New_York" // 负时区
    )

    @After
    fun resetZone() {
        TimeZone.setDefault(null)
    }

    @Test
    fun 毫秒值恒为UTC零点() {
        zones.forEach { z ->
            TimeZone.setDefault(TimeZone.getTimeZone(z))
            val d = LocalDate.of(2026, 10, 4)
            assertEquals(z, d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                d.toPickerMillis())
            // Material3 以 UTC 解毫秒：选中日历日必须等于原日期
            assertEquals(z, d,
                Instant.ofEpochMilli(d.toPickerMillis())
                    .atZone(ZoneOffset.UTC).toLocalDate())
        }
    }

    @Test
    fun 往返一致_任何时区() {
        val dates = listOf(
            LocalDate.of(2026, 10, 4),   // 普通日期
            LocalDate.of(2026, 1, 31),   // 月末
            LocalDate.of(2025, 12, 31),  // 年末
            LocalDate.of(2024, 2, 29),   // 闰日
            LocalDate.of(2027, 3, 1)
        )
        zones.forEach { z ->
            TimeZone.setDefault(TimeZone.getTimeZone(z))
            dates.forEach { d ->
                assertEquals("$z / $d", d, d.toPickerMillis().toPickerLocalDate())
            }
        }
    }

    @Test
    fun 打开直接确认不改日期() {
        // 预选 current，未改动即确认 → 回填同一天（选不选同一天就保存同一天）
        zones.forEach { z ->
            TimeZone.setDefault(TimeZone.getTimeZone(z))
            val current = LocalDate.of(2026, 10, 4)
            val confirmed = current.toPickerMillis().toPickerLocalDate()
            assertEquals(z, current, confirmed)
        }
    }
}
