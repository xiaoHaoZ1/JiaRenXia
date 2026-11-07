package io.github.xiaohaoz1.jiarenxia.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 碰一碰 AID 解析：BuildConfig.TAP_AID（十六进制）→ APDU 字节。
 * debug/release 单测构建 TAP_AID=F04A52580001（与原硬编码一致）；
 * demo 变体构建为 F04A52580002（见 app/build.gradle.kts）。
 */
class TapProtocolAidTest {

    @Test
    fun `生产AID解析为既有字节`() {
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x4A, 0x52, 0x58, 0x00, 0x01),
            TapProtocol.aidFromHex("F04A52580001")
        )
    }

    @Test
    fun `演示AID解析`() {
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x4A, 0x52, 0x58, 0x00, 0x02),
            TapProtocol.aidFromHex("F04A52580002")
        )
    }

    @Test
    fun `当前构建AID与默认值一致`() {
        // 单测跑在 debug 变体：TAP_AID 必须仍是 F04A52580001，
        // 保证 TapManifestTest 等既有断言不受影响
        assertArrayEquals(TapProtocol.aidFromHex("F04A52580001"), TapProtocol.AID)
        assertEquals(6, TapProtocol.AID.size)
    }
}
