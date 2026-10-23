package io.github.xiaohaoz1.jiarenxia.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 碰一碰 APDU 协议（内存通道，FAMILY_AFFAIRS 第 10 节）。 */
class TapProtocolTest {

    private class Fixture {
        private val helloCard = TapProtocol.Hello(0, "李雪", 1, 4, null)
        private val helloReader = TapProtocol.Hello(0, "杨明", 0, 5, null)
        var active = true
        var cardStatus = TapProtocol.STATUS_WAIT
        var cardPayload = TapProtocol.pack(byteArrayOf(9, 9, 9))
        var gotHello: TapProtocol.Hello? = null
        var gotPayload: ByteArray? = null
        var now = 0L
        var newSessions = 0
        var peerCancels = 0
        val card = TapProtocol.TapCard(
            isActive = { active }, clock = { now },
            ownHello = { helloCard }, ownPayload = { cardPayload },
            ownStatus = { cardStatus },
            onPeerHello = { gotHello = it },
            onPeerPayload = { gotPayload = it },
            onNewSession = { newSessions++ },
            onPeerCancel = { peerCancels++ })
        val link = TapProtocol.ApduTransport { cmd -> card.handle(cmd) }
        fun reader(payload: ByteArray) =
            TapProtocol.TapReader(link, helloReader, {
                TapProtocol.pack(payload) })
    }

    @Test
    fun 正常流程() {
        val f = Fixture()
        val r = f.reader(byteArrayOf(1, 2, 3))
        val h = r.connect()
        assertEquals("李雪", h.name)
        assertEquals(4, h.familyCount)
        // 双方都确认后交换
        f.cardStatus = TapProtocol.STATUS_CONFIRM
        assertEquals(
            TapProtocol.STATUS_CONFIRM,
            r.status(TapProtocol.STATUS_CONFIRM).first)
        r.sendPayload()
        val plain = r.recvPayload()
        assertArrayEquals(byteArrayOf(9, 9, 9), plain)
        r.done(1234L)
        assertArrayEquals(byteArrayOf(1, 2, 3), f.gotPayload)
        assertEquals("杨明", f.gotHello?.name)
    }

    @Test
    fun HELLOv4编解码往返() {
        val h = TapProtocol.Hello(
            sessionId = 0x11223344, name = "李雪", gender = 1,
            familyCount = 300, spouseName = "张伟",
            rehearsal = true, clockMillis = 1_700_000_000_123L,
            selfId = "p-abc123", pairingId = ByteArray(16) { it.toByte() },
            sessionNonce = ByteArray(16) { (it + 40).toByte() },
            wantUpdate = true,
            dates = listOf(
                TapProtocol.DateWire(
                    kind = TapProtocol.DATE_WEDDING,
                    year = 2020, month = 10, day = 2,
                    lunar = false, leapMonth = false),
                TapProtocol.DateWire(
                    kind = TapProtocol.DATE_DATING,
                    year = 2017, month = 5, day = 20,
                    lunar = false, leapMonth = false)),
            weddingWords = "永结|同心")
        val d = TapProtocol.Hello.decode(h.encode())!!
        assertEquals(h.sessionId, d.sessionId)
        assertEquals("李雪", d.name)
        assertEquals(1.toByte(), d.gender)
        assertEquals(300, d.familyCount)
        assertEquals("张伟", d.spouseName)
        assertTrue(d.rehearsal)
        assertTrue(d.wantUpdate)
        assertEquals(1_700_000_000_123L, d.clockMillis)
        assertEquals("p-abc123", d.selfId)
        assertArrayEquals(h.pairingId, d.pairingId)
        assertArrayEquals(h.sessionNonce, d.sessionNonce)
        assertEquals(2, d.dates.size)
        val w = d.dates.first { it.kind == TapProtocol.DATE_WEDDING }
        assertEquals(2020, w.year)
        assertEquals(10, w.month)
        assertEquals(2, w.day)
        val dt = d.dates.first { it.kind == TapProtocol.DATE_DATING }
        assertEquals(2017, dt.year)
        assertEquals(5, dt.month)
        assertEquals(20, dt.day)
        assertEquals("永结|同心", d.weddingWords)
        // 无配偶/无彩排/无配对/无纪念日的往返
        val d2 = TapProtocol.Hello.decode(
            TapProtocol.Hello(1, "杨明", 0, 3, null).encode())!!
        assertNull(d2.spouseName)
        assertTrue(!d2.rehearsal)
        assertTrue(!d2.wantUpdate)
        assertNull(d2.pairingId)
        assertTrue(d2.dates.isEmpty())
        assertEquals(16, d2.sessionNonce.size)
        assertEquals("", d2.weddingWords)
        // 旧实现编码的 HELLO（尾部没有祝词字段）解码 → 空串按默认处理
        val base = TapProtocol.Hello(1, "杨明", 0, 3, null).encode()
        assertEquals("", TapProtocol.Hello.decode(
            base.copyOf(base.size - 1))!!.weddingWords)
        // 农历纪念日标志 + 领证类型
        val d3 = TapProtocol.Hello.decode(
            TapProtocol.Hello(1, "杨明", 0, 3, null,
                dates = listOf(
                    TapProtocol.DateWire(
                        kind = TapProtocol.DATE_LICENSE,
                        year = 0, month = 8, day = 15,
                        lunar = true, leapMonth = true))).encode())!!
        assertEquals(1, d3.dates.size)
        assertEquals(TapProtocol.DATE_LICENSE, d3.dates[0].kind)
        assertTrue(d3.dates[0].lunar)
        assertTrue(d3.dates[0].leapMonth)
        assertEquals(0, d3.dates[0].year)
        // 版本不符（v1/v2/v3 编码）直接判 null
        assertNull(TapProtocol.Hello.decode(
            byteArrayOf(1) + h.encode().copyOfRange(1, h.encode().size)))
        assertNull(TapProtocol.Hello.decode(
            byteArrayOf(3) + h.encode().copyOfRange(1, h.encode().size)))
    }

    @Test
    fun HELLOv4带用途字段且旧版判null() {
        // 互加亲人 kind 往返
        val rel = TapProtocol.Hello(0, "李雷", 0, 3, null,
            kind = TapProtocol.KIND_RELATIVES)
        assertEquals(
            TapProtocol.HELLO_VERSION.toByte(), rel.encode()[0])
        val d = TapProtocol.Hello.decode(rel.encode())!!
        assertEquals(TapProtocol.KIND_RELATIVES, d.kind)
        // 缺省用途 = 结为夫妻
        val d2 = TapProtocol.Hello.decode(
            TapProtocol.Hello(0, "李雪", 1, 3, null).encode())!!
        assertEquals(TapProtocol.KIND_SPOUSE, d2.kind)
        // v3 编码判 null（v3 设备不认识互加亲人，不能放行当夫妻合并）
        assertNull(TapProtocol.Hello.decode(
            byteArrayOf(3) +
                rel.encode().copyOfRange(1, rel.encode().size)))
    }

    @Test
    fun 配对标识由双方nonce推导且两端一致() {
        val a = ByteArray(16) { it.toByte() }
        val b = ByteArray(16) { (it + 100).toByte() }
        val ab = TapProtocol.derivePairingId(a, b)
        val ba = TapProtocol.derivePairingId(b, a)
        // 顺序无关：两部手机各自推导结果一致
        assertEquals(ab, ba)
        assertEquals(32, ab.length) // 16 字节 hex
        // 换一方 nonce 则配对标识变化
        val c = ByteArray(16) { (it + 7).toByte() }
        assertTrue(ab != TapProtocol.derivePairingId(a, c))
    }

    @Test
    fun 旧版HELLO应答读卡端报版本不同() {
        val f = Fixture()
        // v1 应答：首字节 1 + 任意内容 + 9000
        val v1Link = TapProtocol.ApduTransport {
            byteArrayOf(1) + TapProtocol.int4(7) + byteArrayOf(3) +
                "旧".toByteArray(Charsets.UTF_8) + TapProtocol.SW_OK
        }
        try {
            TapProtocol.TapReader(
                v1Link, TapProtocol.Hello(0, "杨明", 0, 3, null),
                { TapProtocol.pack(byteArrayOf(1)) }).connect()
            throw AssertionError("应抛 TapFail")
        } catch (e: TapProtocol.TapFail) {
            assertTrue(e.message!!.contains("版本不同"))
        }
    }

    @Test
    fun DONE携带共享开始时刻给卡片端() {
        var got: Long? = -1L
        var calls = 0
        val card = TapProtocol.TapCard(
            isActive = { true }, clock = { 0L },
            ownHello = { TapProtocol.Hello(0, "李雪", 1, 4, null) },
            ownPayload = { byteArrayOf(1) },
            ownStatus = { TapProtocol.STATUS_WAIT },
            onPeerHello = {}, onPeerPayload = {},
            onPeerDone = { got = it; calls++ })
        card.handle(TapProtocol.selectApdu()) // 开会话
        val resp = card.handle(TapProtocol.apdu(
            TapProtocol.INS_DONE, TapProtocol.int8(1_700_000_002_500L)))
        assertArrayEquals(TapProtocol.SW_OK, resp)
        assertEquals(1_700_000_002_500L, got)
        assertEquals(1, calls)
        // 数据不足 8B 时回调 null
        card.handle(TapProtocol.apdu(
            TapProtocol.INS_DONE, byteArrayOf(1, 2)))
        assertNull(got)
    }

    @Test
    fun 链路断开回调且会话保留() {
        var lost = 0
        val card = TapProtocol.TapCard(
            isActive = { true }, clock = { 0L },
            ownHello = { TapProtocol.Hello(0, "李雪", 1, 4, null) },
            ownPayload = { byteArrayOf(1) },
            ownStatus = { TapProtocol.STATUS_WAIT },
            onPeerHello = {}, onPeerPayload = {},
            onLinkLost = { lost++ })
        card.handle(TapProtocol.selectApdu())
        val s1 = card.session
        card.onDeactivated()
        assertEquals(1, lost)
        // 会话保留：下一次 SELECT 仍是同一会话 id
        val resp = card.handle(TapProtocol.selectApdu())
        assertEquals(0x9000, TapProtocol.sw(resp))
        assertEquals(s1, card.session)
    }

    @Test
    fun 任一方取消则中止() {
        val f = Fixture()
        val r = f.reader(byteArrayOf(1))
        r.connect()
        f.cardStatus = TapProtocol.STATUS_CANCEL
        assertEquals(
            TapProtocol.STATUS_CANCEL,
            r.status(TapProtocol.STATUS_CONFIRM).first)
        assertNull(f.gotPayload)
    }

    @Test
    fun 确认前断开不交换() {
        val f = Fixture()
        val r = f.reader(byteArrayOf(1))
        r.connect()
        assertEquals(TapProtocol.STATUS_WAIT,
            r.status(TapProtocol.STATUS_WAIT).first)
        assertNull(f.gotPayload)
    }

    @Test
    fun 中途断开可续传() {
        val f = Fixture()
        val data = ByteArray(600) { it.toByte() }
        val r = f.reader(data)
        r.connect()
        f.cardStatus = TapProtocol.STATUS_CONFIRM
        r.status(TapProtocol.STATUS_CONFIRM)
        // 发一块后“断开”：新建 reader（同会话续传，顺序写入偏移续上）
        f.card.handle(
            TapProtocol.apdu(
                TapProtocol.INS_PUT_DATA,
                TapProtocol.int4(0) + TapProtocol.pack(data).copyOf(36)))
        val r2 = f.reader(data)
        r2.connect() // 同会话：session 不变
        r2.status(TapProtocol.STATUS_CONFIRM) // 同步卡片端已收长度 → 续传
        r2.sendPayload()
        assertArrayEquals(data, f.gotPayload)
        // 读卡端收包也从中断处继续
        val r3 = f.reader(data)
        r3.connect()
        f.cardStatus = TapProtocol.STATUS_CONFIRM
        r3.status(TapProtocol.STATUS_CONFIRM)
        assertArrayEquals(byteArrayOf(9, 9, 9), r3.recvPayload())
    }

    @Test
    fun 未确认时GET_DATA返回6985() {
        val f = Fixture()
        val r = f.reader(byteArrayOf(1))
        r.connect()
        // 双方都没确认
        var resp = f.card.handle(TapProtocol.apdu(TapProtocol.INS_GET_DATA,
            TapProtocol.int4(0)))
        assertEquals(
            0x69.toByte(), resp[resp.size - 2])
        assertEquals(0x85.toByte(), resp[resp.size - 1])
        // 只有一方确认也一样
        f.cardStatus = TapProtocol.STATUS_CONFIRM
        resp = f.card.handle(TapProtocol.apdu(TapProtocol.INS_GET_DATA,
            TapProtocol.int4(0)))
        assertEquals(0x69.toByte(), resp[resp.size - 2])
        // 双方都确认后才能读
        r.status(TapProtocol.STATUS_CONFIRM)
        resp = f.card.handle(TapProtocol.apdu(TapProtocol.INS_GET_DATA,
            TapProtocol.int4(0)))
        assertEquals(0x90.toByte(), resp[resp.size - 2])
    }

    @Test
    fun 未确认时PUT_DATA返回6985() {
        val f = Fixture()
        f.reader(byteArrayOf(1)).connect()
        val resp = f.card.handle(TapProtocol.apdu(TapProtocol.INS_PUT_DATA,
            TapProtocol.int4(0) + byteArrayOf(1, 2)))
        assertEquals(0x69.toByte(), resp[resp.size - 2])
        assertNull(f.gotPayload)
    }

    @Test
    fun 恶意大偏移拒绝且不分配() {
        val f = Fixture()
        val r = f.reader(byteArrayOf(1))
        r.connect()
        f.cardStatus = TapProtocol.STATUS_CONFIRM
        r.status(TapProtocol.STATUS_CONFIRM)
        // 偏移接近 2^31
        var resp = f.card.handle(TapProtocol.apdu(TapProtocol.INS_PUT_DATA,
            TapProtocol.int4(Int.MAX_VALUE - 8) + byteArrayOf(1)))
        assertEquals(0x6A.toByte(), resp[resp.size - 2])
        // 非零起点（顺序写入，首块必须 off=0）
        resp = f.card.handle(TapProtocol.apdu(TapProtocol.INS_PUT_DATA,
            TapProtocol.int4(10) + byteArrayOf(1)))
        assertEquals(0x6A.toByte(), resp[resp.size - 2])
        // 声明总长超限
        val big = TapProtocol.int4(TapProtocol.MAX_PAYLOAD + 1) +
            ByteArray(32) + byteArrayOf(1)
        resp = f.card.handle(TapProtocol.apdu(TapProtocol.INS_PUT_DATA,
            TapProtocol.int4(0) + big))
        assertEquals(0x6F.toByte(), resp[resp.size - 2]) // SW_ABORT
    }

    @Test
    fun 校验失败中止() {
        val f = Fixture()
        f.cardPayload = TapProtocol.pack(byteArrayOf(1, 2, 3)).also {
            it[10] = (it[10] + 1).toByte() // 破坏 sha256
        }
        val r = f.reader(byteArrayOf(1))
        r.connect()
        f.cardStatus = TapProtocol.STATUS_CONFIRM
        r.status(TapProtocol.STATUS_CONFIRM)
        try {
            r.recvPayload()
            throw AssertionError("应抛 TapFail")
        } catch (e: TapProtocol.TapFail) {
            assertTrue(e.message!!.contains("校验"))
        }
    }

    @Test
    fun 会话超时作废() {
        val f = Fixture()
        val r = f.reader(byteArrayOf(1))
        val h1 = r.connect()
        f.now = TapProtocol.SESSION_TTL_MS + 1
        val h2 = r.connect()
        assertTrue(h1.sessionId != h2.sessionId)
    }

    @Test
    fun 未激活返回6A82() {
        val f = Fixture().also { it.active = false }
        val r = f.reader(byteArrayOf(1))
        try {
            r.connect()
            throw AssertionError("应抛 TapFail")
        } catch (e: TapProtocol.TapFail) {
        }
        // 原始响应确认是 6A82
        val resp = f.link.transceive(TapProtocol.selectApdu())
        assertArrayEquals(TapProtocol.SW_NOT_FOUND, resp)
    }

    @Test
    fun 未激活时connect带上状态字6A82() {
        val f = Fixture().also { it.active = false }
        try {
            f.reader(byteArrayOf(1)).connect()
            throw AssertionError("应抛 TapFail")
        } catch (e: TapProtocol.TapFail) {
            assertEquals(0x6A82, e.sw)
        }
    }

    @Test
    fun reset后非SELECT一律6A82且下次SELECT换新会话() {
        val f = Fixture()
        val r = f.reader(byteArrayOf(1))
        val h1 = r.connect()
        f.card.reset()
        val resp = f.card.handle(
            TapProtocol.apdu(TapProtocol.INS_STATUS, byteArrayOf(0)))
        assertArrayEquals(TapProtocol.SW_NOT_FOUND, resp)
        val h2 = r.connect()
        assertTrue(h1.sessionId != h2.sessionId)
    }

    @Test
    fun 新会话回调只在开启新会话时触发() {
        val f = Fixture()
        assertEquals(0, f.newSessions)
        f.card.handle(TapProtocol.selectApdu())
        assertEquals(1, f.newSessions)
        // 同会话重复 SELECT 不触发
        f.card.handle(TapProtocol.selectApdu())
        assertEquals(1, f.newSessions)
        // 会话超时后 → 新会话
        f.now = TapProtocol.SESSION_TTL_MS + 1
        f.card.handle(TapProtocol.selectApdu())
        assertEquals(2, f.newSessions)
        // reset 后 → 新会话
        f.card.reset()
        f.card.handle(TapProtocol.selectApdu())
        assertEquals(3, f.newSessions)
    }

    @Test
    fun 对端取消回6F00且会话作废() {
        val f = Fixture()
        val r = f.reader(byteArrayOf(1))
        r.connect()
        // 读卡端上报取消
        val resp = f.card.handle(
            TapProtocol.apdu(
                TapProtocol.INS_STATUS, byteArrayOf(TapProtocol.STATUS_CANCEL)))
        assertArrayEquals(TapProtocol.SW_ABORT, resp)
        assertEquals(1, f.peerCancels)
        // 会话已作废：后续非 SELECT 一律 6A82
        val next = f.card.handle(
            TapProtocol.apdu(TapProtocol.INS_STATUS, byteArrayOf(0)))
        assertArrayEquals(TapProtocol.SW_NOT_FOUND, next)
    }
}
