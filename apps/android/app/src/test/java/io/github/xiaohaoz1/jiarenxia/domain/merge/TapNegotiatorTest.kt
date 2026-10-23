package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 碰一碰模式协商的用途分支（互加亲人 / 用途不一致）。 */
class TapNegotiatorTest {

    private fun hello(kind: Byte) = TapProtocol.Hello(
        sessionId = 0, name = "对方", gender = 0,
        familyCount = 3, spouseName = null, kind = kind)

    @Test
    fun 双方都选互加亲人按用途不一致拒绝() {
        // 互加亲人已移除：旧版手机宣布该用途时按 ModeMismatch 拒绝，不崩溃
        val d = TapNegotiator.decide(
            hello(TapProtocol.KIND_RELATIVES),
            hello(TapProtocol.KIND_RELATIVES),
            ownPairing = null, ownSpouseName = null)
        assertEquals(TapNegotiation.ModeMismatch, d)
    }

    @Test
    fun 用途不一致最先判且与先后无关() {
        val d1 = TapNegotiator.decide(
            hello(TapProtocol.KIND_SPOUSE),
            hello(TapProtocol.KIND_RELATIVES),
            ownPairing = null, ownSpouseName = null)
        val d2 = TapNegotiator.decide(
            hello(TapProtocol.KIND_RELATIVES),
            hello(TapProtocol.KIND_SPOUSE),
            ownPairing = null, ownSpouseName = null)
        assertEquals(TapNegotiation.ModeMismatch, d1)
        assertEquals(TapNegotiation.ModeMismatch, d2)
    }

    @Test
    fun 预演模式仍生效() {
        // 结为夫妻时预演仍生效（回归保护）
        val sp = hello(TapProtocol.KIND_SPOUSE).copy(rehearsal = true)
        val d2 = TapNegotiator.decide(
            sp, hello(TapProtocol.KIND_SPOUSE),
            ownPairing = null, ownSpouseName = null)
        assertTrue(d2 is TapNegotiation.Rehearsal)
    }
}
