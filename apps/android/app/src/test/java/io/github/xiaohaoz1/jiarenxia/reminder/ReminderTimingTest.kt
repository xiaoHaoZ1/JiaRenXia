package io.github.xiaohaoz1.jiarenxia.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** ReminderTiming：三类提醒各自时刻的调度延迟计算。 */
class ReminderTimingTest {

    private val nine = LocalTime.of(9, 0)

    // ---------- nextDelay（reconcile：有已过时刻立即补跑） ----------

    @Test
    fun reconcile_九点前_到今天的九点() {
        val now = LocalDateTime.of(2025, 5, 12, 7, 30)
        assertEquals(Duration.ofMinutes(90), ReminderTiming.nextDelay(now, setOf(nine)))
    }

    @Test
    fun reconcile_有时刻已过_立即补跑() {
        val now = LocalDateTime.of(2025, 5, 12, 10, 0)
        assertEquals(Duration.ZERO, ReminderTiming.nextDelay(now, setOf(nine)))
        // 其它时刻还没到也照样立即（补跑只发该类已过时刻的未发提醒）
        assertEquals(Duration.ZERO, ReminderTiming.nextDelay(now, setOf(nine, LocalTime.of(21, 0))))
    }

    @Test
    fun reconcile_全部在未来_到最近一个() {
        val now = LocalDateTime.of(2025, 5, 12, 8, 0)
        assertEquals(
            Duration.ofMinutes(30),
            ReminderTiming.nextDelay(now, setOf(nine, LocalTime.of(8, 30)))
        )
    }

    @Test
    fun reconcile_没有已开启提醒_不排() {
        assertNull(ReminderTiming.nextDelay(LocalDateTime.now(), emptySet()))
    }

    // ---------- nextRunDelay（跑完排下次：恒指向未来） ----------

    @Test
    fun 跑完_今天还有时刻_到最近一个() {
        val now = LocalDateTime.of(2025, 5, 12, 10, 0)
        assertEquals(
            Duration.ofHours(11),
            ReminderTiming.nextRunDelay(now, setOf(nine, LocalTime.of(21, 0)))
        )
    }

    @Test
    fun 跑完_今天时刻全过_到明天最早的() {
        val now = LocalDateTime.of(2025, 5, 12, 22, 0)
        assertEquals(
            Duration.ofHours(11),
            ReminderTiming.nextRunDelay(now, setOf(nine, LocalTime.of(21, 0)))
        )
    }

    @Test
    fun 临近午夜() {
        // 23:59 跑完 → 明天 09:00 ≈ 9小时1分
        val late = LocalDateTime.of(2025, 5, 12, 23, 59)
        assertEquals(
            Duration.ofMinutes(541),
            ReminderTiming.nextRunDelay(late, setOf(nine))
        )
        // 00:01 → 今天 09:00
        val early = LocalDateTime.of(2025, 5, 13, 0, 1)
        assertEquals(
            Duration.ofMinutes(539),
            ReminderTiming.nextRunDelay(early, setOf(nine))
        )
    }

    @Test
    fun 跑完_没有已开启提醒_不排() {
        assertNull(ReminderTiming.nextRunDelay(LocalDateTime.now(), emptySet()))
    }

    // ---------- 自定义提醒时间 ----------

    private val evening = LocalTime.of(20, 30)

    @Test
    fun 自定义时间_之前_到今天该时刻() {
        val now = LocalDateTime.of(2025, 5, 12, 19, 0)
        assertEquals(Duration.ofMinutes(90), ReminderTiming.nextDelay(now, setOf(evening)))
    }

    @Test
    fun 自定义时间_之后_立即补跑() {
        val now = LocalDateTime.of(2025, 5, 12, 21, 0)
        assertEquals(Duration.ZERO, ReminderTiming.nextDelay(now, setOf(evening)))
    }

    @Test
    fun 分钟数换算与格式化() {
        assertEquals(LocalTime.of(9, 0), ReminderTiming.timeOf(ReminderTiming.DEFAULT_MINUTE_OF_DAY))
        assertEquals(LocalTime.of(20, 30), ReminderTiming.timeOf(20 * 60 + 30))
        assertEquals(LocalTime.of(0, 0), ReminderTiming.timeOf(-5))
        assertEquals(LocalTime.of(23, 59), ReminderTiming.timeOf(5000))
        assertEquals("09:00", ReminderTiming.format(540))
        assertEquals("07:05", ReminderTiming.format(7 * 60 + 5))
        assertEquals("23:59", ReminderTiming.format(1439))
    }
}
