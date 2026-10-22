package io.github.xiaohaoz1.jiarenxia.guardian

import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardState
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardianFormat
import io.github.xiaohaoz1.jiarenxia.guardian.core.Phase
import io.github.xiaohaoz1.jiarenxia.guardian.core.TimeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** 守护 UI 纯函数：相对时间 / 倒计时 / 号码校验 / 权限进度。 */
class GuardianFormatTest {

    private val z = ZoneId.of("Asia/Shanghai")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, z).toInstant().toEpochMilli()

    @Test
    fun `relative day time`() {
        val now = at(2026, 10, 2, 20, 0)
        assertEquals("今天 19:05", GuardianFormat.formatRelativeDayTime(at(2026, 10, 2, 19, 5), now, z))
        assertEquals("昨天 08:12", GuardianFormat.formatRelativeDayTime(at(2026, 10, 1, 8, 12), now, z))
        assertEquals("9月28日 08:12", GuardianFormat.formatRelativeDayTime(at(2026, 9, 28, 8, 12), now, z))
    }

    @Test
    fun `ago`() {
        val now = at(2026, 10, 2, 20, 0)
        assertEquals("刚刚", GuardianFormat.formatAgo(now - 30_000, now))
        assertEquals("25 分钟前", GuardianFormat.formatAgo(at(2026, 10, 2, 19, 35), now))
        assertEquals("3 小时前", GuardianFormat.formatAgo(at(2026, 10, 2, 17, 0), now))
        assertEquals("2 天前", GuardianFormat.formatAgo(at(2026, 9, 30, 19, 0), now))
        // 时钟回拨不输出负数
        assertEquals("刚刚", GuardianFormat.formatAgo(at(2026, 10, 2, 21, 0), now))
    }

    @Test
    fun `countdown`() {
        assertEquals("4小时55分", GuardianFormat.formatCountdown((4 * 3600 + 55 * 60) * 1000L))
        assertEquals("3分20秒", GuardianFormat.formatCountdown(200_000L))
        assertEquals("40秒", GuardianFormat.formatCountdown(40_000L))
        assertEquals("0秒", GuardianFormat.formatCountdown(-5_000L))
    }

    @Test
    fun `contact number validator`() {
        assertTrue(GuardianFormat.isValidContactNumber("13800138000"))
        assertTrue(GuardianFormat.isValidContactNumber("+86 138-0013-8000"))
        assertTrue(GuardianFormat.isValidContactNumber("12345"))
        assertFalse(GuardianFormat.isValidContactNumber(""))
        assertFalse(GuardianFormat.isValidContactNumber("1234"))
        assertFalse(GuardianFormat.isValidContactNumber("abc"))
        assertFalse(GuardianFormat.isValidContactNumber("1380@123"))
    }

    @Test
    fun `required permission progress`() {
        // (granted, optional)
        val items = listOf(
            true to false, false to false, true to false, false to true
        )
        assertEquals(2 to 3, GuardianFormat.requiredProgress(items))
        assertEquals(0 to 0, GuardianFormat.requiredProgress(listOf(true to true)))
    }

    @Test
    fun `status text by phase`() {
        val at = at(2026, 10, 2, 8, 30)
        assertEquals(
            "守护中",
            GuardianFormat.statusText(GuardState(phase = Phase.MONITORING)),
        )
        assertEquals(
            "守护中｜上次使用 " + TimeRules.formatClock(at),
            GuardianFormat.statusText(
                GuardState(phase = Phase.MONITORING, lastActivityAt = at)
            ),
        )
        assertEquals(
            "守护中｜正在使用",
            GuardianFormat.statusText(
                GuardState(phase = Phase.MONITORING, lastActivityAt = at, inUse = true)
            ),
        )
        assertEquals(
            "正在等待确认",
            GuardianFormat.statusText(GuardState(phase = Phase.CONFIRMING)),
        )
        assertEquals(
            "已拨打紧急电话",
            GuardianFormat.statusText(GuardState(phase = Phase.ALERTED)),
        )
        assertEquals(
            "已于 " + TimeRules.formatClock(at) + " 拨打紧急电话",
            GuardianFormat.statusText(GuardState(phase = Phase.ALERTED, alertedAt = at)),
        )
    }
}
