package io.github.xiaohaoz1.jiarenxia.nfc

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 碰一碰会话单例（FAMILY_AFFAIRS 第 10 节）。
 * 卡片端只在碰一碰页面打开期间响应：[activate] 开启、[deactivate] 关闭，
 * 其余时间 TapHostService 一律回 6A82。
 */
@Singleton
class TapSession @Inject constructor() {

    @Volatile var active = false; private set
    @Volatile var card: TapProtocol.TapCard? = null; private set

    /** 页面打开时挂入卡片状态机（女方手机）。 */
    fun activate(card: TapProtocol.TapCard) {
        this.card = card
        active = true
    }

    fun deactivate() {
        active = false
        card = null
    }

    /** 供 TapHostService 转发 APDU。 */
    fun handleApdu(cmd: ByteArray): ByteArray =
        card?.handle(cmd) ?: TapProtocol.SW_NOT_FOUND

    /** HCE 链路断开（onDeactivated）转发给卡片；会话保留，重贴续传。 */
    fun deactivated() {
        card?.onDeactivated()
    }
}
