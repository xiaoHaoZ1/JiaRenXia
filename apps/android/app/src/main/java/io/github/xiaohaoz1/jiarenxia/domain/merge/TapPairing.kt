package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol

/**
 * 碰一碰夫妻配对（FAMILY_AFFAIRS 第 10 节）。
 *
 * 本机已录入“配偶”不等于两部手机已配对：配对是首次成功合并后，
 * 两部手机各自持久化的一段共享标识（pairingId）+ 对方“我”的来源人物 id
 * + 来源人物→本机人物映射。配对记录使：
 * - 已配对再碰：只交换识别与动画同步数据（纪念模式），不再传家庭资料；
 * - 同一来源人物在后续合并中复用既有映射（同一份资料反复处理不增人）；
 * - 断线/页面重建/重放后幂等，不产生重复；
 * - 一端已提交而另一端未提交时，可经确认恢复。
 *
 * pairingId 不是法律身份，也不含 NFC 标签 UID（UID 每次可换，不能当身份）。
 * 本机连接偏好（上次成功的角色）属于设备本地数据，不进备份/共享。
 */

/** 已建立的配偶配对。 */
data class SpousePairing(
    /** 32 位十六进制（16 字节），首次合并时双方 nonce 推导，两端一致。 */
    val pairingId: String,
    /** 对方机器上“我”的 personId（远程自报身份，配对身份的一部分）。 */
    val peerSelfId: String,
    /** 对方在本机对应的人物 id（本机配偶）。 */
    val localSpouseId: String,
    /** 对方姓名快照（展示用，允许随后改名——以 personId 为准）。 */
    val peerName: String,
    /** 来源人物 id → 本机人物 id 的持久映射（含新建的人物）。 */
    val personMap: Map<String, String>,
    /** 最近成功写入的会话 id（重放/诊断）。 */
    val lastSessionId: Int?,
    /** 最近一次应用的对方资料包 sha256（十六进制；重放/诊断）。 */
    val lastPayloadSha: String?,
    /** 本机上次成功使用的连接方式：true=主动感应(Reader)，false=等待感应(Card)。 */
    val lastRoleReader: Boolean?,
    val updatedAt: Long
)

/** 配对存储抽象（Room 实现见 data.tap.RoomSpousePairingStore）。 */
interface SpousePairingStore {
    /** 当前配对（未配对返回 null）。 */
    suspend fun current(): SpousePairing?
    /** 当前配对的来源→本机映射（未配对返回空）。 */
    suspend fun personMap(): Map<String, String> =
        current()?.personMap ?: emptyMap()
    /**
     * 合并提交成功后记录/刷新配对：personMap 全量合并（remote→local，
     * 已存在的键以新值为准）；sessionId/payloadSha 供重放识别。
     */
    suspend fun recordMerge(
        pairingId: String,
        peerSelfId: String,
        localSpouseId: String,
        peerName: String,
        mapping: Map<String, String>,
        sessionId: Int?,
        payloadSha: String?,
        isReader: Boolean
    )
    /** 只记住本次成功使用的本机角色（纪念/更新/彩排都允许记）。 */
    suspend fun rememberRole(isReader: Boolean)
    /** 未配对时的设备级角色偏好（仅本机使用，不随备份走）。 */
    suspend fun preferredRole(): Boolean?
    /** 清空配对（“清空所有数据”等场景）。 */
    suspend fun clear()
}

/**
 * 三种握手模式的协商结果。
 *
 * - [FirstMerge]：双方都未配对 → 结为夫妻（合并两家）。
 *   [confirmSpouse] 非空表示本机已录入同名配偶，需用户明确确认“对方就是
 *   已录入的配偶 X”——同名同性别只是候选，不能直接视为已配对。
 *   [pairingId] 为本次协商出的配对标识（双 nonce 推导）。
 * - [Memorial]：双方配对标识一致且对方本人身份与记录一致 → 纪念，
 *   不交换/不写家庭资料。只有一方想更新时也走纪念，并用
 *   [Memorial.ownWantsUpdate]/[Memorial.peerWantsUpdate] 提示——
 *   单方意愿不能升级为资料交换。
 * - [Update]：已配对且双方都选择了“更新家人资料” → 交换资料、各自预览、
 *   分别确认后才写入（补充式，不覆盖冲突、不删本机资料）。
 * - [Rehearsal]：任一方开预演 → 双方按预演走，不写家庭、不建立配对。
 * - [PairMismatch]：配对状态不对称/不一致——[kind] 区分单方丢失记录
 *   （RECOVER，可在确认后按携带方的配对标识恢复）与双方标识不同
 *   （DIFFERENT，确认后采用字典序较小者重新配对）。协商不静默退化为
 *   全量合并：必须由本机用户确认。
 * - [PeerChanged]：配对标识一致但对方自报的“我”与记录不符
 *   （可能恢复/换机异常）→ 拒绝并提示，不自动处理。
 * - [Relatives]：双方都选“互加亲人”→ 就绪即交换各自圈选的资料包，
 *   传完即可分开；“他是谁/选哪些人”与写入都由各自在本机独立完成。
 * - [ModeMismatch]：两台手机选的碰一碰用途不同 → 双方各自提示并拒绝。
 */
sealed class TapNegotiation {
    data object Rehearsal : TapNegotiation()
    /** 互加亲人：贴上即双向交换圈选资料包；不要求同性/异性或配对记录。 */
    data object Relatives : TapNegotiation()
    /** 用途不一致：一台选结为夫妻、一台选互加亲人。 */
    data object ModeMismatch : TapNegotiation()
    /**
     * 纪念模式（默认）：不交换家庭资料。
     * [ownWantsUpdate]/[peerWantsUpdate] 标记单方更新意愿——仅一方
     * 想更新时按纪念走，但可在文案里提示“双方都打开更新开关才交换”。
     */
    data class Memorial(
        val ownWantsUpdate: Boolean = false,
        val peerWantsUpdate: Boolean = false
    ) : TapNegotiation()
    data class Update(val pairingId: String) : TapNegotiation()
    data class FirstMerge(
        val pairingId: String,
        val confirmSpouse: String?
    ) : TapNegotiation()
    data class PairMismatch(val kind: Kind, val resolvablePairingId: String) :
        TapNegotiation() {
        enum class Kind { RECOVER, DIFFERENT }
    }
    data object PeerChanged : TapNegotiation()
}

/** 按双方 HELLO 与本机配对记录做模式协商（两端各自本地求值，结果一致）。 */
object TapNegotiator {

    /**
     * @param ownHello   本机 HELLO
     * @param peerHello  对方 HELLO
     * @param ownPairing 本机配对记录（可空）
     * @param ownSpouseName 本机已录入配偶名（旧记录候选确认用，可空）
     */
    fun decide(
        ownHello: TapProtocol.Hello,
        peerHello: TapProtocol.Hello,
        ownPairing: SpousePairing?,
        ownSpouseName: String?
    ): TapNegotiation {
        // 用途不一致最先判：夫妻侧做配对/性别检查不适用，亲属侧也不需要配对
        if (ownHello.kind != peerHello.kind) return TapNegotiation.ModeMismatch
        // 互加亲人已移除：对方宣布该用途时按用途不一致拒绝（协议常量保留以兼容 1.2.x 的 HELLO）
        if (ownHello.kind == TapProtocol.KIND_RELATIVES ||
            peerHello.kind == TapProtocol.KIND_RELATIVES) {
            return TapNegotiation.ModeMismatch
        }
        if (ownHello.rehearsal || peerHello.rehearsal) {
            return TapNegotiation.Rehearsal
        }
        val ownPid = ownPairing?.pairingId
        val peerPid = peerHello.pairingId?.let { TapProtocol.toHex(it) }
        return when {
            ownPid != null && peerPid != null && ownPid == peerPid -> {
                // 同一配对：核对对方本人身份仍是记录里的那个人
                if (ownPairing.peerSelfId.isNotEmpty() &&
                    peerHello.selfId.isNotEmpty() &&
                    ownPairing.peerSelfId != peerHello.selfId
                ) {
                    TapNegotiation.PeerChanged
                } else if (ownHello.wantUpdate && peerHello.wantUpdate) {
                    // 双方都明确选择更新才交换资料；单方意愿走纪念+提示
                    TapNegotiation.Update(ownPid)
                } else {
                    TapNegotiation.Memorial(
                        ownWantsUpdate = ownHello.wantUpdate,
                        peerWantsUpdate = peerHello.wantUpdate)
                }
            }
            ownPid == null && peerPid == null -> {
                val pid = TapProtocol.derivePairingId(
                    ownHello.sessionNonce, peerHello.sessionNonce)
                // 同名同性别旧配偶记录只作候选：需本机用户明确确认
                val confirm = ownSpouseName
                    ?.takeIf { it == peerHello.name }
                TapNegotiation.FirstMerge(pid, confirm)
            }
            else -> {
                // 配对不对称/不一致：先提示恢复，不静默退化
                val chosen = listOfNotNull(ownPid, peerPid).minOrNull()!!
                TapNegotiation.PairMismatch(
                    kind = if (ownPid == null || peerPid == null)
                        TapNegotiation.PairMismatch.Kind.RECOVER
                    else TapNegotiation.PairMismatch.Kind.DIFFERENT,
                    resolvablePairingId = chosen)
            }
        }
    }
}
