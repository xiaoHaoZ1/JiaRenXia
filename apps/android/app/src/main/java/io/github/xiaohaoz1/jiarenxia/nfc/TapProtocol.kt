package io.github.xiaohaoz1.jiarenxia.nfc

import java.security.MessageDigest
import java.util.Random

/**
 * 碰一碰 APDU 协议（FAMILY_AFFAIRS 第 10 节）。纯 JVM，可在内存通道上单测。
 *
 * NFC 角色与性别无关：一方“主动感应”（ReaderMode），另一方“等待感应”（HCE），
 * 两台手机选相反方式；半心左右与称谓仍按性别。
 * AID：F04A52580001（category other）。全部短 APDU，数据块 <= 240 字节。
 *
 * 指令（CLA = 0x80）：
 * - SELECT AID -> HELLO v3（版本/会话id/姓名/性别/家人数/标志位/时钟/本人id/
 *   配偶/配对标识/会话 nonce/结婚纪念日）
 * - 0x10 PUT_HELLO：读卡端回报自己的 HELLO
 * - 0x20 STATUS：data=[读卡端状态, 附加码] -> 返回 [卡片端状态, 附加码, 已收字节数4B]
 *   状态：0 等待 / 1 就绪（身份检查与资料包就绪；资料更新模式下此时即可读写）/
 *   2 已确认（自动模式直接到此；需确认的模式须双方都到此才交换与写入）/
 *   3 取消 / 4 拒绝（附加码 = 原因）
 * - 0x30 PUT_DATA：data = 4B 偏移 + 数据块（写入自己的资料包）
 * - 0x40 GET_DATA：data = 4B 偏移 -> 数据块（读出对方的资料包）
 * - 0x50 DONE：data = 8B 共享合心开始时刻（读卡端时钟域）
 *
 * 读卡端顺序：双方就绪后先 GET_DATA 读对方资料、再 PUT_DATA 写自己的——卡片端
 * 收齐即双向完成，随后 DONE 携带共享开始时刻。纪念模式不交换资料包：
 * 双方确认后直接 DONE。
 *
 * 取消语义：读卡端在 STATUS 里上报 STATUS_CANCEL 时，卡片端作废当前会话
 * （reset）并回 6F00；此后一切非 SELECT 指令都回 6A82，下一次 SELECT
 * 开启全新会话。任一方离开页面/取消都靠这条链让另一方回到等待页。
 *
 * 安全：PUT_DATA / GET_DATA 的放行由 [TapCard.exchangeAllowed] 决定——
 * 双方都就绪（自动模式）或都已确认（需确认的模式），资料更新模式在双方就绪时
 * 即可交换以便各自生成预览。资料包 = 4B 总长度 + 32B SHA-256 + Deflate
 * (backup.json zip)，接收端 `parsePlan` 全量校验；PUT_DATA 只允许顺序写入
 * （偏移必须等于已收长度），重贴后可续传。会话超过 2 分钟作废。卡片端只在
 * 碰一碰页面打开期间响应。
 *
 * HELLO v4 = 版本 4 | 会话 id 4B | 姓名（1B 长度 + UTF-8）| 性别 1B |
 * 家人数 2B | 标志位 1B（bit0 彩排、bit1 资料更新意愿）| 时钟 8B 大端 |
 * 本人 id（1B 长度 + UTF-8，本机“我”的 personId）| 配偶标志 | 配偶名 |
 * 配对标志 | 配对标识 16B | 会话 nonce 16B | 纪念日条数 1B |
 * 每条 = 类型 1B + 日期 6B（结婚/领证/恋爱各至多一条）|
 * 祝词 UTF-8（1B 长度 + 字节，可省略按默认“喜结|连理”）|
 * 碰一碰用途 1B（0 结为夫妻 / 1 互加亲人）。
 * 两部手机须同为 v4；旧版应答由读卡端直接报“版本不同”。互加亲人
 * 放到新字段而不占标志位：旧版设备会把它静默当夫妻碰一碰处理（异性
 * 亲属会被错误合并），版本隔离更稳妥。
 *
 * 配对标识：首次合并成功时由双方会话 nonce 共同推导（sha256 取前 16B），
 * 两部手机得到相同值并各自持久化；此后的 HELLO 携带它即可互认“同一对夫妻”。
 * 它只用于去重与纪念模式识别，不是法律身份或安全认证。
 */
object TapProtocol {

    val AID = byteArrayOf(
        0xF0.toByte(), 0x4A, 0x52, 0x58, 0x00, 0x01)

    const val CLA: Byte = 0x80.toByte()
    const val INS_PUT_HELLO: Byte = 0x10
    const val INS_STATUS: Byte = 0x20
    const val INS_PUT_DATA: Byte = 0x30
    const val INS_GET_DATA: Byte = 0x40
    const val INS_DONE: Byte = 0x50

    const val STATUS_WAIT: Byte = 0
    /** 1 = 就绪（身份检查通过且资料包已生成；UPDATE 模式下此时即可读写）。 */
    const val STATUS_READY: Byte = 1
    /** 2 = 已确认（自动模式直接到此；确认模式须双方都到此）。 */
    const val STATUS_CONFIRM: Byte = 2
    const val STATUS_CANCEL: Byte = 3
    /** 4 = 本机前置检查不通过/用户拒绝；附加码见 REJECT_*。 */
    const val STATUS_REJECT: Byte = 4
    /** 5 = 本机正在等用户确认（对方页显示“等待对方确认”）。 */
    const val STATUS_NEED_CONFIRM: Byte = 5

    /** STATUS_REJECT 附加码：同性。 */
    const val REJECT_GENDER: Byte = 1
    /** 本机已有其他配偶。 */
    const val REJECT_SELF_SPOUSE: Byte = 2
    /** 对方已有其他配偶。 */
    const val REJECT_PEER_SPOUSE: Byte = 3
    /** 配对确认被本机用户拒绝（对方不是已录入的配偶）。 */
    const val REJECT_NOT_SPOUSE: Byte = 4
    /** 配对记录不一致且用户选择不恢复。 */
    const val REJECT_PAIR_MISMATCH: Byte = 5
    /** 两台手机选的碰一碰用途不同（结为夫妻 / 互加亲人）。 */
    const val REJECT_MODE_MISMATCH: Byte = 6

    /** HELLO 编码版本；两部手机须一致（v4 起含多条目纪念日、祝词与用途字段）。 */
    const val HELLO_VERSION = 4

    /** 碰一碰用途：结为夫妻（默认）。 */
    const val KIND_SPOUSE: Byte = 0
    /** 碰一碰用途：互加亲人（不限性别、不做配偶检查）。 */
    const val KIND_RELATIVES: Byte = 1

    const val MAX_BLOCK = 240
    /** 资料包总长度上限（4B 头 + 32B 哈希 + 数据），防恶意大偏移。 */
    const val MAX_PAYLOAD = 2 * 1024 * 1024
    const val SESSION_TTL_MS = 120_000L

    /** 配对标识长度（字节）。 */
    const val PAIRING_ID_BYTES = 16

    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    /** 条件不满足（双方未都到可交换状态就读写数据）。 */
    val SW_NOT_ALLOWED = byteArrayOf(0x69.toByte(), 0x85.toByte())
    val SW_WRONG = byteArrayOf(0x6A.toByte(), 0x84.toByte())
    val SW_ABORT = byteArrayOf(0x6F.toByte(), 0x00)

    fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    /** APDU 响应的状态字（末尾两字节，无符号 16 位）；不足两字节时返回 0。 */
    fun sw(r: ByteArray): Int =
        if (r.size < 2) 0
        else ((r[r.size - 2].toInt() and 0xFF) shl 8) or
            (r[r.size - 1].toInt() and 0xFF)

    /** 新婚夫妇的配对标识：由双方会话 nonce 共同推导（字典序小的在前）。 */
    fun derivePairingId(nonceA: ByteArray, nonceB: ByteArray): String {
        val ord = { x: ByteArray, y: ByteArray ->
            var i = 0
            var less = false
            while (i < minOf(x.size, y.size)) {
                val d = (x[i].toInt() and 0xFF) - (y[i].toInt() and 0xFF)
                if (d != 0) { less = d < 0; break }
                i++
            }
            if (i == minOf(x.size, y.size)) x.size <= y.size else less
        }
        val (a, b) = if (ord(nonceA, nonceB)) nonceA to nonceB
            else nonceB to nonceA
        return toHex(sha256(a + b).copyOf(PAIRING_ID_BYTES))
    }

    fun toHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(
            "0123456789abcdef"[(x.toInt() shr 4) and 0xF]).append(
            "0123456789abcdef"[x.toInt() and 0xF])
        return sb.toString()
    }

    fun fromHex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        val out = ByteArray(s.length / 2)
        var i = 0
        while (i < s.length) {
            val hi = Character.digit(s[i], 16)
            val lo = Character.digit(s[i + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i / 2] = ((hi shl 4) or lo).toByte()
            i += 2
        }
        return out
    }

    /** 纪念日载荷类型：结婚。 */
    const val DATE_WEDDING: Int = 0
    /** 纪念日载荷类型：领证。 */
    const val DATE_LICENSE: Int = 1
    /** 纪念日载荷类型：恋爱。 */
    const val DATE_DATING: Int = 2

    /**
     * 线上传输的一条夫妻共享纪念日（HELLO v4 纪念模式用于比对双方记录）：
     * year=0 表示只知月日；lunar 1 农历 / 0 公历。
     */
    data class DateWire(
        /** [DATE_WEDDING] / [DATE_LICENSE] / [DATE_DATING]。 */
        val kind: Int,
        val year: Int,          // 0 = 未知
        val month: Int,
        val day: Int,
        val lunar: Boolean,
        val leapMonth: Boolean
    ) {
        /** 与 [other] 完全一致的日期记录才算一致（类型也须相同）。 */
        fun sameAs(other: DateWire) =
            kind == other.kind && year == other.year &&
                month == other.month && day == other.day &&
                lunar == other.lunar && leapMonth == other.leapMonth
    }

    /**
     * HELLO v4：版本4|会话id4|姓名|性别|家人数2B|标志位1B（bit0 彩排、
     * bit1 资料更新意愿）|时钟8B（发送方 System.currentTimeMillis，卡片端据此
     * 对齐读卡端时钟作为共享时钟）|本人id|配偶标志|配偶名|配对标志|配对标识16B|
     * 会话 nonce16B|纪念日条数1B|每条类型1B+日期6B|祝词UTF8（1B 长度 + 字节，
     * 可省略，缺省=“喜结|连理”，两端不一致以读卡端为准）|用途1B。
     */
    data class Hello(
        val sessionId: Int,
        val name: String,
        val gender: Byte,            // 0 男 1 女
        val familyCount: Int,
        val spouseName: String?,
        /** 本机开了彩排/预演模式：任一方开启则双方都按预演走（不写入）。 */
        val rehearsal: Boolean = false,
        /** 发送方时钟（毫秒）；卡片端用它对齐读卡端时钟作为共享时钟。 */
        val clockMillis: Long = 0L,
        /** 本机“我”的 personId（安装内稳定身份；不是 NFC 标签 UID）。 */
        val selfId: String = "",
        /** 已建立的配对标识（16B）；未配对为 null。 */
        val pairingId: ByteArray? = null,
        /** 本次页面会话随机数；首次合并时双方共同推导配对标识。 */
        val sessionNonce: ByteArray = Random16.next(),
        /** 本机想在已配对基础上交换家人资料（更新模式意愿）。 */
        val wantUpdate: Boolean = false,
        /**
         * 本机记录的夫妻共享纪念日（结婚/领证/恋爱各至多一条）；
         * 双方同类型记录一致才显示对应时长。
         */
        val dates: List<DateWire> = emptyList(),
        /**
         * 婚礼祝词（“左|右”，WeddingWords.join）；空串视为默认
         * “喜结|连理”。两端不一致时以读卡端为准。尾部可选字段。
         */
        val weddingWords: String = "",
        /** 碰一碰用途：KIND_SPOUSE 结为夫妻 / KIND_RELATIVES 互加亲人。 */
        val kind: Byte = KIND_SPOUSE
    ) {
        fun encode(): ByteArray {
            val n = truncUtf8(name, 255)
            val s = spouseName?.let { truncUtf8(it, 255) } ?: ByteArray(0)
            val sid = truncUtf8(selfId, 255)
            val out = java.io.ByteArrayOutputStream()
            out.write(HELLO_VERSION)
            out.write(int4(sessionId))
            out.write(n.size); out.write(n)
            out.write(gender.toInt())
            // 家人数两字节，支持 >255 人的家庭
            out.write((familyCount shr 8) and 0xFF)
            out.write(familyCount and 0xFF)
            out.write((if (rehearsal) 1 else 0) or (if (wantUpdate) 2 else 0))
            out.write(int8(clockMillis))
            out.write(sid.size); out.write(sid)
            out.write(if (spouseName != null) 1 else 0)
            if (spouseName != null) {
                out.write(s.size); out.write(s)
            }
            out.write(if (pairingId != null) 1 else 0)
            pairingId?.let { out.write(it.copyOf(PAIRING_ID_BYTES)) }
            out.write(sessionNonce.copyOf(PAIRING_ID_BYTES))
            out.write(dates.size)
            for (w in dates) {
                out.write(w.kind)
                out.write((w.year shr 8) and 0xFF)
                out.write(w.year and 0xFF)
                out.write(w.month)
                out.write(w.day)
                out.write(if (w.lunar) 1 else 0)
                out.write(if (w.leapMonth) 1 else 0)
            }
            val ww = truncUtf8(weddingWords, 255)
            out.write(ww.size); out.write(ww)
            out.write(kind.toInt())
            return out.toByteArray()
        }
        companion object {
            fun decode(b: ByteArray): Hello? = runCatching {
                var i = 0
                if (b[i++].toInt() != HELLO_VERSION) return null
                val sid = readInt4(b, i); i += 4
                val nl = b[i++].toInt() and 0xFF
                val name = String(b, i, nl, Charsets.UTF_8); i += nl
                val gender = b[i++]
                val count = ((b[i].toInt() and 0xFF) shl 8) or
                    (b[i + 1].toInt() and 0xFF); i += 2
                val flags = b[i++].toInt() and 0xFF
                val clockMillis = readInt8(b, i); i += 8
                val sl = b[i++].toInt() and 0xFF
                val selfId = String(b, i, sl, Charsets.UTF_8); i += sl
                val hasSpouse = b[i++].toInt() == 1
                var spouse: String? = null
                if (hasSpouse) {
                    val spl = b[i++].toInt() and 0xFF
                    spouse = String(b, i, spl, Charsets.UTF_8); i += spl
                }
                var pairingId: ByteArray? = null
                if (i < b.size && b[i++].toInt() == 1) {
                    pairingId = b.copyOfRange(i, i + PAIRING_ID_BYTES)
                    i += PAIRING_ID_BYTES
                }
                val nonce = if (i + PAIRING_ID_BYTES <= b.size) {
                    b.copyOfRange(i, i + PAIRING_ID_BYTES)
                        .also { i += PAIRING_ID_BYTES }
                } else ByteArray(0)
                val dates = ArrayList<DateWire>()
                if (i < b.size) {
                    val n = b[i++].toInt() and 0xFF
                    var k = 0
                    while (k < n && i + 7 <= b.size) {
                        dates += DateWire(
                            kind = b[i].toInt() and 0xFF,
                            year = ((b[i + 1].toInt() and 0xFF) shl 8) or
                                (b[i + 2].toInt() and 0xFF),
                            month = b[i + 3].toInt() and 0xFF,
                            day = b[i + 4].toInt() and 0xFF,
                            lunar = b[i + 5].toInt() != 0,
                            leapMonth = b[i + 6].toInt() != 0)
                        i += 7
                        k++
                    }
                }
                // 尾部可选：婚礼祝词 UTF（长度 1B + 字节）
                var weddingWords = ""
                if (i < b.size) {
                    val wl = b[i++].toInt() and 0xFF
                    if (i + wl <= b.size) {
                        weddingWords = String(b, i, wl, Charsets.UTF_8)
                        i += wl
                    }
                }
                val kind = if (i < b.size) b[i] else KIND_SPOUSE
                Hello(sid, name, gender, count, spouse,
                    rehearsal = (flags and 1) != 0,
                    clockMillis = clockMillis,
                    selfId = selfId,
                    pairingId = pairingId,
                    sessionNonce = nonce,
                    wantUpdate = (flags and 2) != 0,
                    dates = dates,
                    weddingWords = weddingWords,
                    kind = kind)
            }.getOrNull()
        }
    }

    private object Random16 {
        fun next(): ByteArray =
            ByteArray(PAIRING_ID_BYTES).also { Random().nextBytes(it) }
    }

    /** UTF-8 截断到 maxBytes，不截在多字节字符中间。 */
    fun truncUtf8(s: String, maxBytes: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (c in s) {
            val b = c.toString().toByteArray(Charsets.UTF_8)
            if (out.size() + b.size > maxBytes) break
            out.write(b)
        }
        return out.toByteArray()
    }

    fun int4(v: Int) = byteArrayOf(
        (v shr 24).toByte(), (v shr 16).toByte(),
        (v shr 8).toByte(), v.toByte())

    fun readInt4(b: ByteArray, off: Int) =
        ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    fun int8(v: Long) = byteArrayOf(
        (v shr 56).toByte(), (v shr 48).toByte(),
        (v shr 40).toByte(), (v shr 32).toByte(),
        (v shr 24).toByte(), (v shr 16).toByte(),
        (v shr 8).toByte(), v.toByte())

    fun readInt8(b: ByteArray, off: Int) =
        ((b[off].toLong() and 0xFF) shl 56) or
            ((b[off + 1].toLong() and 0xFF) shl 48) or
            ((b[off + 2].toLong() and 0xFF) shl 40) or
            ((b[off + 3].toLong() and 0xFF) shl 32) or
            ((b[off + 4].toLong() and 0xFF) shl 24) or
            ((b[off + 5].toLong() and 0xFF) shl 16) or
            ((b[off + 6].toLong() and 0xFF) shl 8) or (b[off + 7].toLong() and 0xFF)

    fun apdu(ins: Byte, data: ByteArray = byteArrayOf()): ByteArray =
        byteArrayOf(CLA, ins, 0, 0, data.size.toByte()) + data

    fun selectApdu(): ByteArray = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()) + AID

    /** 资料包加头：4B 总长度 + 32B sha256 + 数据。 */
    fun pack(data: ByteArray): ByteArray =
        int4(data.size) + sha256(data) + data

    fun unpack(buf: ByteArray, size: Int): ByteArray? {
        if (size < 36 || buf.size < size) return null
        val total = readInt4(buf, 0)
        if (total < 0 || total > MAX_PAYLOAD) return null
        if (size < 36 + total) return null
        val data = buf.copyOfRange(36, 36 + total)
        if (!sha256(data).contentEquals(buf.copyOfRange(4, 36))) return null
        return data
    }

    // ---------------- 卡片端（等待感应的手机，HCE） ----------------

    class TapCard(
        private val isActive: () -> Boolean,
        private val clock: () -> Long,
        private val ownHello: () -> Hello,
        private val ownPayload: () -> ByteArray,   // 已含头（pack 结果）
        private val ownStatus: () -> Byte,
        private val onPeerHello: (Hello) -> Unit,
        private val onPeerPayload: (ByteArray) -> Unit,
        /** SELECT 开启新会话时回调（会话级状态只属于一个会话，应在外层清掉）。 */
        private val onNewSession: () -> Unit = {},
        /** 读卡端上报取消时回调（会话已被 reset，下一条指令回 6A82）。 */
        private val onPeerCancel: () -> Unit = {},
        /** 读卡端上报拒绝时回调（会话作废，参数为拒绝码）。 */
        private val onPeerReject: (Byte) -> Unit = {},
        /** 读卡端每次 STATUS 上报的状态+附加码变化时回调。 */
        private val onPeerStatus: (status: Byte, code: Byte) -> Unit =
            { _, _ -> },
        /** INS_DONE 到达：参数为共享合心开始时刻（<8B 数据时为 null）。 */
        private val onPeerDone: (Long?) -> Unit = {},
        /** HCE 链路断开（onDeactivated）时回调；会话保留，重贴可续传。 */
        private val onLinkLost: () -> Unit = {}
    ) {
        /** 当前会话（断开重贴续传用）。 */
        var session: Int = 0; private set
        private var bornAt = 0L
        private var peerHello: Hello? = null
        /** 读卡端最近一次 STATUS 上报的状态。 */
        var peerStatus: Byte = STATUS_WAIT; private set
        /** 读卡端最近一次 STATUS 上报的附加码（REJECT 原因）。 */
        var peerCode: Byte = 0; private set
        private var peerBuf = ByteArray(0)
        private var peerDone = false

        /**
         * 资料更新模式：双方“就绪”即可交换（各自据此生成预览），
         * 写入仍须双方都“已确认”。由外层在协商后设置。
         */
        @Volatile var exchangeBeforeConfirm = false

        /** 本机状态是否达到可交换门槛。 */
        private fun statusAllowsExchange(s: Byte) =
            s == STATUS_READY || s == STATUS_CONFIRM

        /** 双方状态都允许读写资料。 */
        fun exchangeAllowed(): Boolean {
            val own = ownStatus()
            val peer = peerStatus
            if (!statusAllowsExchange(own) || !statusAllowsExchange(peer)) {
                return false
            }
            // 预演/纪念/首次合并等自动模式直接拿“就绪”放行；资料更新模式
            // 就绪即放行（先换资料出预览），写入以双方“已确认”为准。
            if (exchangeBeforeConfirm) return true
            return own == STATUS_CONFIRM && peer == STATUS_CONFIRM
        }

        /** HCE 链路断开（HostApduService.onDeactivated）时调用；会话保留，重贴可续传。 */
        fun onDeactivated() = onLinkLost()

        /**
         * 作废当前会话：此后一切非 SELECT 指令回 6A82，
         * 下一次 SELECT 开启全新会话（sessionId 更换、对端状态清空）。
         */
        fun reset() {
            session = 0
            bornAt = 0
            peerHello = null
            peerStatus = STATUS_WAIT
            peerCode = 0
            peerBuf = ByteArray(0)
            peerDone = false
        }

        /** 处理一条 APDU；未激活/非本 AID 一律 6A82。 */
        fun handle(cmd: ByteArray): ByteArray {
            if (!isActive()) return SW_NOT_FOUND
            // SELECT AID
            if (cmd.size >= 5 + AID.size &&
                cmd[0] == 0x00.toByte() && cmd[1] == 0xA4.toByte() &&
                cmd.copyOfRange(5, 5 + AID.size).contentEquals(AID)
            ) {
                if (session == 0 || clock() - bornAt > SESSION_TTL_MS) {
                    reset()
                    session = Random().nextInt()
                    bornAt = clock()
                    onNewSession()
                }
                return ownHello().copy(
                    sessionId = session,
                    clockMillis = clock()).encode() + SW_OK
            }
            if (cmd.size < 5 || cmd[0] != CLA) return SW_NOT_FOUND
            if (session == 0) return SW_NOT_FOUND
            val ins = cmd[1]
            val data = cmd.copyOfRange(5, cmd.size)
            return when (ins) {
                INS_PUT_HELLO -> {
                    peerHello = Hello.decode(data)
                    if (peerHello == null) SW_WRONG
                    else {
                        onPeerHello(peerHello!!)
                        SW_OK
                    }
                }
                INS_STATUS -> {
                    if (data.isNotEmpty()) {
                        peerStatus = data[0]
                        peerCode = if (data.size > 1) data[1] else 0
                    }
                    if (peerStatus == STATUS_CANCEL ||
                        peerStatus == STATUS_REJECT
                    ) {
                        // 读卡端取消/拒绝：作废会话并通知外层，后续指令一律 6A82
                        // （先留住原状态/码再 reset——reset 会把 peerStatus 清零）
                        val was = peerStatus
                        val code = peerCode
                        reset()
                        if (was == STATUS_CANCEL) onPeerCancel()
                        else onPeerReject(code)
                        return SW_ABORT
                    }
                    onPeerStatus(peerStatus, peerCode)
                    // 响应 = [己方状态, 己方附加码, 已收字节数4B]（供读卡端续传）
                    val own = ownStatus()
                    byteArrayOf(own, ownCode()) +
                        int4(peerBuf.size) + SW_OK
                }
                INS_PUT_DATA -> {
                    if (!exchangeAllowed()) return SW_NOT_ALLOWED
                    if (data.size < 4) return SW_WRONG
                    val off = readInt4(data, 0)
                    if (off < 0 || off > MAX_PAYLOAD) return SW_WRONG
                    // 只允许顺序写入：偏移必须等于当前已收长度
                    if (off != peerBuf.size) return SW_WRONG
                    val chunk = data.copyOfRange(4, data.size)
                    if (peerBuf.size + chunk.size > MAX_PAYLOAD + 36) {
                        return SW_ABORT
                    }
                    peerBuf = peerBuf + chunk
                    // 头齐了就知道总长，尝试完整校验
                    val total = if (peerBuf.size >= 4) {
                        val t = readInt4(peerBuf, 0)
                        if (t < 0 || t > MAX_PAYLOAD) return SW_ABORT
                        36 + t
                    } else Int.MAX_VALUE
                    if (peerBuf.size >= total) {
                        val plain = unpack(peerBuf, total) ?: return SW_ABORT
                        onPeerPayload(plain)
                        peerDone = true
                    }
                    SW_OK
                }
                INS_GET_DATA -> {
                    if (!exchangeAllowed()) return SW_NOT_ALLOWED
                    if (data.size < 4) return SW_WRONG
                    val off = readInt4(data, 0)
                    val full = ownPayload()
                    if (off < 0 || off >= full.size) SW_WRONG
                    else full.copyOfRange(
                        off, minOf(off + MAX_BLOCK, full.size)) + SW_OK
                }
                INS_DONE -> {
                    // 8B 共享合心开始时刻（读卡端时钟域）
                    onPeerDone(
                        if (data.size >= 8) readInt8(data, 0) else null)
                    SW_OK
                }
                else -> SW_NOT_FOUND
            }
        }

        /** 本机附加码（STATUS_REJECT 原因等），由外层按 UI 状态给出。 */
        private fun ownCode(): Byte = codeProvider()

        /** 外层注入的附加码（默认 0）。 */
        var codeProvider: () -> Byte = { 0 }
    }

    // ---------------- 读卡端（主动感应的手机，ReaderMode） ----------------

    /** APDU 通道（测试内存连接，真机 IsoDep.transceive）。 */
    fun interface ApduTransport {
        fun transceive(cmd: ByteArray): ByteArray
    }

    /** 读卡端驱动：全部同步调用，由调用方在 IO 线程跑。 */
    class TapReader(
        private val link: ApduTransport,
        private val ownHello: Hello,
        /** 已含头（pack 结果）；供应式，就绪时才生成。 */
        private val ownPayload: () -> ByteArray,
        private val clock: () -> Long = { System.currentTimeMillis() }
    ) {
        var peerHello: Hello? = null; private set
        var peerPayload: ByteArray? = null; private set
        private var sent = 0
        private var got = 0
        /** 接收缓冲区随对象保留，断开重贴后从 [got] 续传。 */
        private val recvBuf = java.io.ByteArrayOutputStream()

        /** 阶段：SELECT + PUT_HELLO，返回对方 HELLO。 */
        fun connect(): Hello {
            val r = link.transceive(selectApdu())
            // 先查状态字：6A82 = 对方没开碰一碰页面，不应误报解析失败
            if (!ok(r)) throw TapFail("对方未应答碰一碰", sw(r))
            // 协议版本不一致（旧版 HELLO）：提示两部手机都更新
            if (r.isNotEmpty() && r[0] != HELLO_VERSION.toByte()) {
                throw TapFail("对方的家人侠版本不同，请两部手机都更新到最新版")
            }
            val h = Hello.decode(r.copyOfRange(0, r.size - 2))
                ?: throw TapFail("HELLO 解析失败")
            peerHello = h
            val pr = link.transceive(apdu(
                INS_PUT_HELLO,
                ownHello.copy(clockMillis = clock()).encode()))
            if (!ok(pr)) throw TapFail("PUT_HELLO 被拒", sw(pr))
            return h
        }

        /**
         * 轮询：上报己方状态与附加码，返回对方 [status, code]。
         * 响应里还带卡片端已收字节数，用于续传起点对齐。
         */
        fun status(own: Byte, code: Byte = 0): Pair<Byte, Byte> {
            val r = link.transceive(apdu(INS_STATUS, byteArrayOf(own, code)))
            if (!ok(r)) throw TapFail("STATUS 失败", sw(r))
            // 卡片端已收字节数 → 续传起点（重贴后从对方已收长度继续，
            // 也可能比本机以为的少——丢块时回退）
            if (r.size >= 6) sent = readInt4(r, 2)
            return r[0] to (if (r.size > 1) r[1] else 0)
        }

        /** 写入自己的资料包（断点续传：从 [sent] 继续）。 */
        fun sendPayload() {
            val full = ownPayload()
            if (full.isEmpty()) throw TapFail("资料包未生成")
            while (sent < full.size) {
                val end = minOf(sent + MAX_BLOCK, full.size)
                val chunk = full.copyOfRange(sent, end)
                val r = link.transceive(apdu(INS_PUT_DATA, int4(sent) + chunk))
                if (!ok(r)) throw TapFail("PUT_DATA 被拒", sw(r))
                sent = end
            }
        }

        /** 读取对方资料包（断点续传：从 [got] 继续），完成后返回原始数据。 */
        fun recvPayload(): ByteArray {
            while (true) {
                val r = link.transceive(apdu(INS_GET_DATA, int4(got)))
                if (!ok(r)) throw TapFail("GET_DATA 失败", sw(r))
                val chunk = r.copyOfRange(0, r.size - 2)
                recvBuf.write(chunk); got += chunk.size
                val all = recvBuf.toByteArray()
                if (all.size >= 4) {
                    val declared = readInt4(all, 0)
                    if (declared < 0 || declared > MAX_PAYLOAD) {
                        throw TapFail("资料包长度非法")
                    }
                    if (all.size >= 36 + declared) {
                        val plain = unpack(all, 36 + declared)
                            ?: throw TapFail("资料包校验失败")
                        peerPayload = plain
                        return plain
                    }
                }
                if (chunk.isEmpty()) throw TapFail("GET_DATA 无进度")
            }
        }

        /** 完成：携带共享合心开始时刻（读卡端时钟域）。 */
        fun done(startAt: Long) {
            link.transceive(apdu(INS_DONE, int8(startAt)))
        }

        private fun ok(r: ByteArray) =
            r.size >= 2 && r[r.size - 2] == SW_OK[0] && r[r.size - 1] == SW_OK[1]
    }

    /** 协议层失败；[sw] 为对方返回的状态字（如 0x6A82 = 页面未打开），0 表示无。 */
    class TapFail(msg: String, val sw: Int = 0) : Exception(msg)
}
