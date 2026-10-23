package io.github.xiaohaoz1.jiarenxia.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 碰一碰卡片端服务（女方手机）。APDU 全部转给 [TapSession]；
 * 碰一碰页面未打开时一律返回 6A82。
 */
@AndroidEntryPoint
class TapHostService : HostApduService() {

    @Inject lateinit var session: TapSession

    override fun processCommandApdu(
        commandApdu: ByteArray, extras: Bundle?
    ): ByteArray = session.handleApdu(commandApdu)

    override fun onDeactivated(reason: Int) = session.deactivated()
}
