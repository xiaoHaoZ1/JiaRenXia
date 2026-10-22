package io.github.xiaohaoz1.jiarenxia.guardian

import io.github.xiaohaoz1.jiarenxia.guardian.core.SmsText
import io.github.xiaohaoz1.jiarenxia.guardian.core.maskNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 号码脱敏与短信文案（纯 JVM）。 */
class SmsTextMaskTest {

    @Test
    fun `maskNumber keeps first3 last4`() {
        assertEquals("138****8000", maskNumber("13800138000"))
        assertEquals("131****5678", maskNumber("13112345678"))
    }

    @Test
    fun `maskNumber short numbers`() {
        assertEquals("**11", maskNumber("0011".take(4))) // 4位→后2
        assertEquals("12", maskNumber("12"))
        assertEquals("", maskNumber(""))
        assertEquals("***78", maskNumber("12378"))
    }

    @Test
    fun `config toString masks number`() {
        val c = io.github.xiaohaoz1.jiarenxia.guardian.core.GuardConfig(
            contactName = "家人", contactNumber = "13800138000"
        )
        assertTrue(c.toString().contains("138****8000"))
        assertTrue(!c.toString().contains("13800138000"))
    }

    @Test
    fun `daily sms text`() {
        val t = SmsText.daily(
            lastActivityAt = 1_700_000_000_000L,
            now = 1_700_003_600_000L,  // +1h
            batteryPct = 87, charging = true, inUse = false
        )
        assertTrue(t.startsWith("【家人侠·守护】"))
        assertTrue(t.contains("1小时前"))
        assertTrue(t.contains("电量87%，充电中"))
        assertTrue(t.contains("请检查手机是否关机"))
    }

    @Test
    fun `daily sms without record`() {
        val t = SmsText.daily(0, 1_700_003_600_000L, 55, false, inUse = false)
        assertTrue(t.contains("无记录"))
        assertTrue(t.contains("未知"))
        assertTrue(t.contains("电量55%"))
    }

    @Test
    fun `daily sms while in use says in use`() {
        val t = SmsText.daily(
            lastActivityAt = 1_700_000_000_000L,
            now = 1_700_003_600_000L,
            batteryPct = 87, charging = false, inUse = true,
        )
        assertTrue(t.contains("最后一次使用手机：正在使用。"))
        assertFalse(t.contains("小时前"))
    }

    @Test
    fun `emergency sms text`() {
        val t = SmsText.emergency(1_700_000_000_000L)
        assertTrue(t.startsWith("【家人侠·守护】紧急"))
        assertTrue(t.contains("自动拨打电话失败"))
        assertTrue(t.contains("尽快电话联系确认"))
    }
}
