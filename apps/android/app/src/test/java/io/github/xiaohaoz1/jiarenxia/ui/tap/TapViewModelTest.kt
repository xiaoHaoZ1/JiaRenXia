package io.github.xiaohaoz1.jiarenxia.ui.tap

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
import io.github.xiaohaoz1.jiarenxia.ui.tap.TapUiState.Stage
import java.io.IOException
import kotlin.math.abs
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 碰一碰 ViewModel（婚礼仪式版）：两部“手机”各用一套内存库 + TapSession，
 * 男方经 startReader 注入的内存链路直接喂给女方会话（等价于真机 IsoDep）。
 * 全流程无任何用户确认：贴上即交换并写入（彩排不写入）→ 合心展示。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TapViewModelTest : RepoTestBase() {

    private val phones = mutableListOf<TapPhone>()

    @Before
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDownPhones() {
        // 取消各手机当前/历史 ViewModel 的协程，避免残留任务在
        // 下个测试 setMain/resetMain 的瞬间仍在读 Dispatchers.Main。
        // cancel 本身不等待协程结束——必须 cancelAndJoin，否则正在取消的
        // 协程与 resetMain 并发读写 Dispatchers.Main（IllegalStateException）。
        runBlocking {
            phones.forEach { p ->
                p.vms.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
                p.db.close()
            }
        }
        Dispatchers.resetMain()
    }

    private suspend fun phone(
        selfName: String,
        gender: Gender,
        fatherName: String,
        motherName: String,
        withSelf: Boolean = true
    ): TapPhone = buildTapPhone(
        selfName, gender, fatherName, motherName, withSelf
    ).also { phones.add(it) }

    private suspend fun joinReal(job: Job?) {
        assertNotNull(job)
        withContext(Dispatchers.IO) { withTimeout(15_000) { job!!.join() } }
    }

    /** 等到“我”读完 → NFC 就绪 → 选择连接方式（两台选相反）。 */
    private suspend fun ready(p: TapPhone, reader: Boolean) {
        awaitTap(p.vm) { it.roleReady }
        p.vm.checkNfc(supported = true, enabled = true)
        assertEquals(Stage.CHOOSE_ROLE, p.vm.ui.value.stage)
        p.vm.chooseRole(reader)
        assertEquals(Stage.WAITING, p.vm.ui.value.stage)
    }

    /** 同一部手机换 ViewModel（配对/角色记忆从存储重新加载）+ 新计数导出器。 */
    private fun reload(p: TapPhone): Pair<TapViewModel, CountingPayloadFactory> =
        rebuildVm(p)

    /** 两部手机走一次完整碰一碰（reader 为读卡端）。 */
    private suspend fun tap(
        reader: TapViewModel, card: TapPhone
    ) {
        card.vm.activateCard()
        val job = reader.startReader(
            open = {},
            link = TapProtocol.ApduTransport { card.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        joinReal(job)
    }

    private fun maleHello() =
        TapProtocol.Hello(sessionId = 0, name = "杨明", gender = 0,
            familyCount = 3, spouseName = null)

    @Test
    fun 贴上即自动交换写入并展示() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)

        female.vm.activateCard()
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        joinReal(job)

        // 没有任何用户动作，双方直接进入合心展示
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        awaitTap(female.vm) { it.stage == Stage.SHOW }
        // DONE 携带的共享开始时刻最终校准女方起点
        awaitTap(female.vm) {
            it.showStartAt != null &&
                it.showStartAt == male.vm.ui.value.showStartAt
        }
        assertTrue(abs(female.vm.ui.value.clockOffsetMs) < 100)

        // 直接写入：双方各 6 人 + SPOUSE 边
        assertEquals(6, male.db.personDao().getAll().size)
        assertEquals(6, female.db.personDao().getAll().size)
        val maleSelf = male.db.personDao().getSelf()!!.id
        val femaleSelf = female.db.personDao().getSelf()!!.id
        assertTrue(male.db.relationshipDao().getAll().any {
            it.type == RelationshipType.SPOUSE &&
                (it.sourcePersonId == maleSelf || it.targetPersonId == maleSelf)
        })
        assertTrue(female.db.relationshipDao().getAll().any {
            it.type == RelationshipType.SPOUSE &&
                (it.sourcePersonId == femaleSelf ||
                    it.targetPersonId == femaleSelf)
        })

        // 新家人称谓从“我”的视角推导，配偶在前
        assertEquals(
            TapUiState.FamilyLabel("妻子", "李雪"),
            male.vm.ui.value.newFamily[0])
        assertEquals(
            TapUiState.FamilyLabel("丈夫", "杨明"),
            female.vm.ui.value.newFamily[0])

        // 双方各自记下同一配对身份（含来源人物映射与角色记忆）
        val pidM = male.pairing.rec!!.pairingId
        val pidF = female.pairing.rec!!.pairingId
        assertEquals(pidM, pidF)
        assertTrue(pidM.isNotEmpty())
        assertTrue(male.pairing.rec!!.personMap.isNotEmpty())
        assertEquals(true, male.pairing.rec!!.lastRoleReader)
        assertEquals(false, female.pairing.rec!!.lastRoleReader)
    }

    @Test
    fun 彩排不写入但流程动画一致() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        // 只有女方开彩排：任一方开则两部都按彩排走
        female.vm.setRehearsal(true)
        assertTrue(female.vm.ui.value.rehearsal)

        female.vm.activateCard()
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        joinReal(job)
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        awaitTap(female.vm) { it.stage == Stage.SHOW }
        assertTrue(male.vm.ui.value.sessionRehearsal)
        assertTrue(female.vm.ui.value.sessionRehearsal)
        // 彩排不写入：人数不变、无 SPOUSE 边
        assertEquals(3, male.db.personDao().getAll().size)
        assertEquals(3, female.db.personDao().getAll().size)
        assertTrue(male.db.relationshipDao().getAll().none {
            it.type == RelationshipType.SPOUSE
        })
    }

    @Test
    fun 卡片在activate与deactivate之间保会话() = runTest {
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(female, false)
        female.vm.activateCard()
        val h1 = TapProtocol.Hello.decode(
            female.session.handleApdu(TapProtocol.selectApdu())
                .dropLast(2).toByteArray())!!
        female.vm.deactivateCard()
        assertArrayEquals(TapProtocol.SW_NOT_FOUND,
            female.session.handleApdu(TapProtocol.selectApdu()))
        female.vm.activateCard()
        val h2 = TapProtocol.Hello.decode(
            female.session.handleApdu(TapProtocol.selectApdu())
                .dropLast(2).toByteArray())!!
        assertEquals(h1.sessionId, h2.sessionId)
    }

    @Test
    fun 女方通过前置检查且资料就绪才放读写() = runTest {
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(female, false)
        female.vm.activateCard()
        female.session.handleApdu(TapProtocol.selectApdu())
        // 只有 SELECT：STATUS 仍报等待
        var resp = female.session.handleApdu(
            TapProtocol.apdu(TapProtocol.INS_STATUS,
                byteArrayOf(TapProtocol.STATUS_CONFIRM)))
        assertEquals(TapProtocol.STATUS_WAIT, resp[0])
        // 通过前置检查的 HELLO → 进交换；轮询到就绪时 GET_DATA 立刻有数据
        female.session.handleApdu(
            TapProtocol.apdu(TapProtocol.INS_PUT_HELLO, maleHello().encode()))
        awaitTap(female.vm) { it.stage == Stage.EXCHANGING }
        withContext(Dispatchers.IO) {
            withTimeout(5_000) {
                while (true) {
                    resp = female.session.handleApdu(
                        TapProtocol.apdu(TapProtocol.INS_STATUS,
                            byteArrayOf(TapProtocol.STATUS_CONFIRM)))
                    assertEquals(0x9000, TapProtocol.sw(resp))
                    if (resp[0] == TapProtocol.STATUS_CONFIRM) {
                        val g = female.session.handleApdu(
                            TapProtocol.apdu(TapProtocol.INS_GET_DATA,
                                TapProtocol.int4(0)))
                        assertEquals(0x9000, TapProtocol.sw(g))
                        assertTrue(g.size > 2)
                        break
                    }
                    delay(20)
                }
            }
        }
    }

    @Test
    fun 前置检查不通过则不交换() = runTest {
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(female, false)
        female.vm.activateCard()
        female.session.handleApdu(TapProtocol.selectApdu())
        // 同性（女方收到女方 HELLO）→ 直接提示原因
        female.session.handleApdu(
            TapProtocol.apdu(TapProtocol.INS_PUT_HELLO,
                TapProtocol.Hello(0, "王芳", 1, 2, null).encode()))
        awaitTap(female.vm) { it.stage == Stage.ERROR }
        assertEquals("碰一碰结为夫妻需要一男一女",
            female.vm.ui.value.error)
        // 卡片上报 REJECT+原因码（对方据此提示），GET_DATA 仍 6985
        val resp = female.session.handleApdu(
            TapProtocol.apdu(TapProtocol.INS_STATUS,
                byteArrayOf(TapProtocol.STATUS_CONFIRM)))
        assertEquals(TapProtocol.STATUS_REJECT, resp[0])
        assertEquals(TapProtocol.REJECT_GENDER, resp[1])
        val g = female.session.handleApdu(
            TapProtocol.apdu(TapProtocol.INS_GET_DATA, TapProtocol.int4(0)))
        assertEquals(0x6985, TapProtocol.sw(g))
    }

    @Test
    fun 对方取消后回到等待页() = runTest {
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(female, false)
        female.vm.activateCard()
        female.session.handleApdu(TapProtocol.selectApdu())
        female.session.handleApdu(
            TapProtocol.apdu(TapProtocol.INS_PUT_HELLO, maleHello().encode()))
        awaitTap(female.vm) { it.stage == Stage.EXCHANGING }

        val resp = female.session.handleApdu(
            TapProtocol.apdu(TapProtocol.INS_STATUS,
                byteArrayOf(TapProtocol.STATUS_CANCEL)))
        assertArrayEquals(TapProtocol.SW_ABORT, resp)
        awaitTap(female.vm) {
            it.stage == Stage.WAITING && it.notice == "对方取消了"
        }
        // 会话已作废：后续非 SELECT 一律 6A82
        assertArrayEquals(TapProtocol.SW_NOT_FOUND,
            female.session.handleApdu(
                TapProtocol.apdu(TapProtocol.INS_STATUS, byteArrayOf(0))))
    }

    @Test
    fun 男方链路断开后回等待页且可重贴() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        female.vm.activateCard()

        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { cmd ->
                // 第一次轮询状态就丢链（对应真机 TagLostException）
                if (cmd.size >= 2 && cmd[1] == TapProtocol.INS_STATUS) {
                    throw IOException("Tag was lost")
                }
                female.session.handleApdu(cmd)
            },
            close = {})
        joinReal(job)
        awaitTap(male.vm) {
            it.stage == Stage.WAITING && it.notice != null
        }
        assertTrue(male.vm.ui.value.notice!!.contains("再贴"))
        assertNull(male.vm.ui.value.error)

        // 再贴一次：干净链路走完全程
        val job2 = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job2)
        joinReal(job2)
        awaitTap(male.vm) { it.stage == Stage.SHOW }
    }

    @Test
    fun 对方没开碰一碰页面时提示而不是报错() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        // 女方没有 activateCard：会话无卡片，一律 6A82
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        joinReal(job)
        awaitTap(male.vm) { it.notice != null }
        assertTrue(
            male.vm.ui.value.notice!!.contains("没读到对方的碰一碰"))
        assertEquals(Stage.WAITING, male.vm.ui.value.stage)
        assertNull(male.vm.ui.value.error)
    }

    @Test
    fun 交换读卡角色后身份不变流程照旧() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        // 两部手机都交换角色：男方当卡片、女方读卡（性别与半心左右不变）
        male.vm.toggleRole()
        female.vm.toggleRole()
        assertTrue(!male.vm.ui.value.isReader)
        assertTrue(female.vm.ui.value.isReader)

        male.vm.activateCard()
        val job = female.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { male.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        joinReal(job)
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        awaitTap(female.vm) { it.stage == Stage.SHOW }

        assertEquals(6, male.db.personDao().getAll().size)
        assertEquals(6, female.db.personDao().getAll().size)
        val maleSelf = male.db.personDao().getSelf()!!.id
        assertTrue(male.db.relationshipDao().getAll().any {
            it.type == RelationshipType.SPOUSE &&
                (it.sourcePersonId == maleSelf || it.targetPersonId == maleSelf)
        })
        // 半心左右仍按性别：男左女右；称谓按真实性别（卡片端 HELLO 带的是真实性别）
        assertTrue(male.vm.ui.value.leftHalf)
        assertTrue(!female.vm.ui.value.leftHalf)
        assertEquals(
            TapUiState.FamilyLabel("妻子", "李雪"),
            male.vm.ui.value.newFamily[0])
        assertEquals(
            TapUiState.FamilyLabel("丈夫", "杨明"),
            female.vm.ui.value.newFamily[0])
    }

    @Test
    fun 读到门禁卡时提示交换角色() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        ready(male, true)
        male.vm.onNonIsoDepTag()
        assertTrue(
            male.vm.ui.value.notice!!.contains("交换连接方式"))
        // 非等待页忽略（notice 保持原样，不被覆盖）
        val old = male.vm.ui.value.notice
        male.vm.checkNfc(supported = true, enabled = false)
        assertEquals(Stage.NFC_OFF, male.vm.ui.value.stage)
        male.vm.onNonIsoDepTag()
        assertEquals(old, male.vm.ui.value.notice)
    }

    @Test
    fun 交换读卡角色只在等待或错误页生效() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        female.vm.activateCard()
        // 交换中不可切角色
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        awaitTap(male.vm) { it.stage == Stage.EXCHANGING }
        male.vm.toggleRole()
        assertTrue(male.vm.ui.value.isReader)
        joinReal(job)
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        // 展示中也不可切
        male.vm.toggleRole()
        assertTrue(male.vm.ui.value.isReader)
    }

    @Test
    fun NFC状态检查() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        awaitTap(male.vm) { it.roleReady }
        male.vm.checkNfc(supported = false, enabled = false)
        assertEquals(Stage.NO_NFC, male.vm.ui.value.stage)
        male.vm.checkNfc(supported = true, enabled = false)
        assertEquals(Stage.NFC_OFF, male.vm.ui.value.stage)
        male.vm.checkNfc(supported = true, enabled = true)
        // 初次必须明确选择连接方式（角色不再由性别推导；用途由入口固定）
        assertEquals(Stage.CHOOSE_ROLE, male.vm.ui.value.stage)
        male.vm.chooseRole(true)
        assertEquals(Stage.WAITING, male.vm.ui.value.stage)

        // 不支持 HCE 的手机仍能当读卡端：选等待感应才报不支持
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        awaitTap(female.vm) { it.roleReady }
        female.vm.checkNfc(supported = true, enabled = true, hce = false)
        assertEquals(Stage.CHOOSE_ROLE, female.vm.ui.value.stage)
        female.vm.chooseRole(false)
        assertEquals(Stage.NO_NFC, female.vm.ui.value.stage)
        female.vm.chooseRole(true)
        assertEquals(Stage.WAITING, female.vm.ui.value.stage)

        // 没有“我”：错误页且 NFC 检查不会把它踢走
        val empty = phone("我", Gender.MALE, "父", "母", withSelf = false)
        awaitTap(empty.vm) { it.stage == Stage.ERROR }
        assertEquals("请先创建“我”", empty.vm.ui.value.error)
        empty.vm.checkNfc(supported = true, enabled = true)
        assertEquals(Stage.ERROR, empty.vm.ui.value.stage)
        empty.vm.checkNfc(supported = true, enabled = false)
        assertEquals(Stage.ERROR, empty.vm.ui.value.stage)
    }

    @Test
    fun 女方链路断开回等待页且会话保留() = runTest {
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(female, false)
        female.vm.activateCard()
        val h1 = TapProtocol.Hello.decode(
            female.session.handleApdu(TapProtocol.selectApdu())
                .dropLast(2).toByteArray())!!
        female.session.handleApdu(
            TapProtocol.apdu(TapProtocol.INS_PUT_HELLO, maleHello().encode()))
        awaitTap(female.vm) { it.stage == Stage.EXCHANGING }

        // HCE 链路断开（onDeactivated）：回等待页提示再贴，会话保留
        female.session.deactivated()
        awaitTap(female.vm) {
            it.stage == Stage.WAITING &&
                it.notice == "连接断开了，再贴一下继续"
        }
        val h2 = TapProtocol.Hello.decode(
            female.session.handleApdu(TapProtocol.selectApdu())
                .dropLast(2).toByteArray())!!
        assertEquals(h1.sessionId, h2.sessionId)
    }

    @Test
    fun 中途分开再贴凭会话续传() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        female.vm.activateCard()

        // 第一笔 PUT_DATA（写男方资料）就丢链
        var dropped = false
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { cmd ->
                if (!dropped && cmd.size >= 2 &&
                    cmd[1] == TapProtocol.INS_PUT_DATA
                ) {
                    dropped = true
                    throw IOException("Tag was lost")
                }
                female.session.handleApdu(cmd)
            },
            close = {})
        joinReal(job)
        awaitTap(male.vm) {
            it.stage == Stage.WAITING &&
                it.notice?.contains("再贴") == true
        }
        // 女方这边也被断开（HCE onDeactivated）→ 回等待页
        female.session.deactivated()
        awaitTap(female.vm) { it.stage == Stage.WAITING }

        // 重贴：凭会话续传走完全程
        val job2 = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job2)
        joinReal(job2)
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        awaitTap(female.vm) { it.stage == Stage.SHOW }
        assertEquals(6, male.db.personDao().getAll().size)
        assertEquals(6, female.db.personDao().getAll().size)
    }

    @Test
    fun 对方协议版本不同则提示升级() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        ready(male, true)
        // 链路总是回 v1 HELLO + 9000
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport {
                byteArrayOf(1) + TapProtocol.int4(9) + byteArrayOf(0) +
                    TapProtocol.SW_OK
            },
            close = {})
        joinReal(job)
        awaitTap(male.vm) { it.stage == Stage.ERROR }
        assertTrue(male.vm.ui.value.error!!.contains("版本不同"))
    }

    // ---------------- v3：配对 / 纪念 / 确认 / 更新 ----------------

    /** 两部手机先完成一次合并（双方记下配对），再换 ViewModel 重进页面。 */
    private suspend fun marry(
        male: TapPhone, female: TapPhone
    ): Pair<String, String> {
        ready(male, true); ready(female, false)
        tap(male.vm, female)
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        awaitTap(female.vm) { it.stage == Stage.SHOW }
        return male.pairing.rec!!.pairingId to female.pairing.rec!!.pairingId
    }

    /** 已配对后的第二次碰一碰：新 ViewModel（等效页面重建）。 */
    private suspend fun readyPaired(p: TapPhone):
        Pair<TapViewModel, CountingPayloadFactory> {
        val (vm, fac) = reload(p)
        awaitTap(vm) { it.roleReady }
        assertTrue(vm.ui.value.paired)
        // 角色记忆生效 + 用途由入口固定：已配对回访直接到等待页
        vm.checkNfc(supported = true, enabled = true)
        assertEquals(Stage.WAITING, vm.ui.value.stage)
        return vm to fac
    }

    @Test
    fun 已配对再碰为纪念模式且不交换资料包() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        val (pidM, pidF) = marry(male, female)
        assertEquals(pidM, pidF)

        val (mvm, mfac) = readyPaired(male)
        val (fvm, ffac) = readyPaired(female)
        // 角色记忆：男仍读卡、女仍卡片
        assertTrue(mvm.ui.value.isReader)
        assertTrue(!fvm.ui.value.isReader)

        // 记录链路上的 APDU 指令：纪念模式必须没有资料读写
        val insSeen = mutableListOf<Int>()
        fvm.activateCard()
        val job = mvm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { cmd ->
                if (cmd.size >= 2) insSeen.add(cmd[1].toInt() and 0xFF)
                female.session.handleApdu(cmd)
            },
            close = {})
        assertNotNull(job)
        joinReal(job)

        awaitTap(mvm) { it.stage == Stage.SHOW }
        awaitTap(fvm) { it.stage == Stage.SHOW }
        assertEquals(TapUiState.SessionMode.MEMORIAL,
            mvm.ui.value.sessionMode)
        assertNotNull(mvm.ui.value.memorialLine)
        assertTrue(mvm.ui.value.memorialLine!!.isNotEmpty())
        // 纪念模式不导出、不传输家庭资料包
        assertEquals(0, mfac.calls)
        assertEquals(0, ffac.calls)
        assertTrue(insSeen.none {
            it == TapProtocol.INS_GET_DATA.toInt() ||
                it == TapProtocol.INS_PUT_DATA.toInt()
        })
        // 不写家庭资料：人数不变、配对不变
        assertEquals(6, male.db.personDao().getAll().size)
        assertEquals(6, female.db.personDao().getAll().size)
        assertEquals(pidM, male.pairing.rec!!.pairingId)
    }

    @Test
    fun 预演不建立配对且不写入() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        female.vm.setRehearsal(true)
        tap(male.vm, female)
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        awaitTap(female.vm) { it.stage == Stage.SHOW }
        assertTrue(male.vm.ui.value.sessionRehearsal)
        // 预演：不写资料也不建立正式配对
        assertEquals(3, male.db.personDao().getAll().size)
        assertNull(male.pairing.rec)
        assertNull(female.pairing.rec)
    }

    @Test
    fun 同一资料重放幂等不重复写入() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        marry(male, female)
        // 模拟“一端已提交、另一端重放同一资料包”：女方配对记录保持，
        // 直接把女方资料再喂给男方的合并计划——新增必须为空
        val snap = withContext(Dispatchers.IO) {
            male.backup.localMergeSnapshot()
        }
        val femalePlan = BackupJsonForTest(female, male)
        val r = io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge.plan(
            snap, femalePlan.remote, male.db.personDao().getSelf()!!.id,
            femalePlan.peerSelfId,
            knownMap = male.pairing.rec!!.personMap)
        val ok = r as io.github.xiaohaoz1.jiarenxia.domain.merge
            .SpouseMerge.Result.Ok
        assertTrue("重放应为空计划", ok.plan.empty())
    }

    @Test
    fun 旧配偶候选确认后才合并且复用配偶记录() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        // 女方旧数据：已录入配偶“杨明”，但两台手机从未配对
        val femaleSelf = female.db.personDao().getSelf()!!.id
        (female.repo.createRelative(
            io.github.xiaohaoz1.jiarenxia.data.PersonDraft(
                "杨明", Gender.MALE),
            femaleSelf,
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.HUSBAND)
            as io.github.xiaohaoz1.jiarenxia.data.FamilyResult.Success)
        ready(male, true); ready(female, false)
        assertEquals("杨明", female.vm.ui.value.selfSpouseName)

        female.vm.activateCard()
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        // 女方（持有旧配偶记录的一方）停在明确确认页；男方继续等
        awaitTap(female.vm) { it.stage == Stage.CONFIRMING }
        assertEquals(TapUiState.Confirm.SPOUSE_CANDIDATE,
            female.vm.ui.value.confirm)
        assertEquals("杨明", female.vm.ui.value.confirmName)
        awaitTap(male.vm) { it.peerConfirming }

        female.vm.confirmPending()
        // 交换后男方这边出现阻塞候选：对方录入的“杨明”疑似就是本机的我
        awaitTap(male.vm) { it.stage == Stage.CONFIRMING }
        assertEquals(TapUiState.Confirm.FAMILY_CANDIDATES,
            male.vm.ui.value.confirm)
        assertTrue(male.vm.ui.value.candidates.any { it.blocking })
        male.vm.confirmPending()
        joinReal(job)
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        awaitTap(female.vm) { it.stage == Stage.SHOW }
        // 男方本人映射到女方已录入的“杨明”记录，不产生重复人物
        assertEquals(6, female.db.personDao().getAll().size)
        assertTrue(female.db.personDao().getAll()
            .count { it.name == "杨明" } == 1)
        assertNotNull(female.pairing.rec)
        assertNotNull(male.pairing.rec)
        assertEquals(male.pairing.rec!!.pairingId,
            female.pairing.rec!!.pairingId)
    }

    @Test
    fun 确认页否认则不合并() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        // 女方录入的配偶叫“杨明”——但碰上来的人未必是他
        val femaleSelf = female.db.personDao().getSelf()!!.id
        (female.repo.createRelative(
            io.github.xiaohaoz1.jiarenxia.data.PersonDraft(
                "杨明", Gender.MALE),
            femaleSelf,
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.HUSBAND)
            as io.github.xiaohaoz1.jiarenxia.data.FamilyResult.Success)
        ready(male, true); ready(female, false)

        female.vm.activateCard()
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        awaitTap(female.vm) { it.stage == Stage.CONFIRMING }
        female.vm.declinePending()
        joinReal(job)
        // 女方错误页；男方收到具体拒绝原因
        assertEquals(Stage.ERROR, female.vm.ui.value.stage)
        awaitTap(male.vm) {
            it.stage == Stage.WAITING && it.notice != null
        }
        assertTrue(male.vm.ui.value.notice!!.contains("不是对方已录入的配偶"))
        // 双方都没写入
        assertEquals(3, male.db.personDao().getAll().size)
        assertEquals(4, female.db.personDao().getAll().size)
        assertNull(male.pairing.rec)
        assertNull(female.pairing.rec)
    }

    @Test
    fun 配对记录不对称时先确认恢复再合并() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        val (pid, _) = marry(male, female)
        // 一端丢配对记录（数据还在）：不对称，不能静默重跑全量合并
        male.pairing.clear()

        val (mvm, _) = reload(male)
        val (fvm, _) = reload(female)
        awaitTap(mvm) { it.roleReady }
        awaitTap(fvm) { it.roleReady }
        assertTrue(!mvm.ui.value.paired)
        assertTrue(fvm.ui.value.paired)
        // 男方无配对记忆 → 需重新选角色；女方走记忆角色
        mvm.checkNfc(true, true); fvm.checkNfc(true, true)
        assertEquals(Stage.CHOOSE_ROLE, mvm.ui.value.stage)
        assertEquals(Stage.WAITING, fvm.ui.value.stage)
        mvm.chooseRole(true)

        fvm.activateCard()
        val job = mvm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        // 双方都停在“配对不一致”确认页，不会静默退化为全量合并
        awaitTap(mvm) { it.stage == Stage.CONFIRMING }
        awaitTap(fvm) { it.stage == Stage.CONFIRMING }
        assertEquals(TapUiState.Confirm.PAIR_MISMATCH,
            mvm.ui.value.confirm)
        mvm.confirmPending()
        fvm.confirmPending()
        // 交换后双方各自出同一人候选（两家结构相同），都确认才写入
        awaitTap(mvm) {
            it.stage == Stage.CONFIRMING &&
                it.confirm == TapUiState.Confirm.FAMILY_CANDIDATES
        }
        awaitTap(fvm) {
            it.stage == Stage.CONFIRMING &&
                it.confirm == TapUiState.Confirm.FAMILY_CANDIDATES
        }
        mvm.confirmPending()
        fvm.confirmPending()
        joinReal(job)
        awaitTap(mvm) { it.stage == Stage.SHOW }
        awaitTap(fvm) { it.stage == Stage.SHOW }
        // 恢复为同一配对；本机数据不变（幂等，无重复人物）
        assertEquals(pid, male.pairing.rec!!.pairingId)
        assertEquals(6, male.db.personDao().getAll().size)
        assertEquals(6, female.db.personDao().getAll().size)
    }

    @Test
    fun 更新模式先交换出预览双方确认后才写入() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        marry(male, female)
        // 女方之后新增一位家人（如弟弟），下次碰面按“更新资料”补充
        val fSelf = female.db.personDao().getSelf()!!.id
        (female.repo.createRelative(
            io.github.xiaohaoz1.jiarenxia.data.PersonDraft(
                "李雷", Gender.MALE),
            fSelf,
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
                .YOUNGER_BROTHER)
            as io.github.xiaohaoz1.jiarenxia.data.FamilyResult.Success)
        assertEquals(7, female.db.personDao().getAll().size)
        assertEquals(6, male.db.personDao().getAll().size)

        val (mvm, _) = readyPaired(male)
        val (fvm, _) = readyPaired(female)
        // 双方都明确打开“更新家人资料”才进入更新交换
        mvm.setWantUpdate(true)
        fvm.setWantUpdate(true)

        fvm.activateCard()
        val job = mvm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        // 资料先交换但暂不写库：双方都出预览
        awaitTap(mvm) { it.stage == Stage.UPDATE_PREVIEW }
        awaitTap(fvm) { it.stage == Stage.UPDATE_PREVIEW }
        assertEquals(6, male.db.personDao().getAll().size)
        assertTrue(mvm.ui.value.updateNew >= 1)
        // 男方确认、女方确认 → 各自提交补充式更新
        mvm.confirmUpdate()
        fvm.confirmUpdate()
        joinReal(job)
        awaitTap(mvm) { it.stage == Stage.SHOW }
        awaitTap(fvm) { it.stage == Stage.SHOW }
        // 男方补进“李雷”+姐弟关系；女方数据只增不减
        assertEquals(7, male.db.personDao().getAll().size)
        assertTrue(male.db.personDao().getAll().any { it.name == "李雷" })
        assertEquals(7, female.db.personDao().getAll().size)
        // 配对映射扩展：李雷的来源 id 记入映射
        assertTrue(male.pairing.rec!!.personMap.isNotEmpty())
    }

    @Test
    fun 单方想更新仍走纪念且不交换资料包() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        marry(male, female)
        val (mvm, mfac) = readyPaired(male)
        val (fvm, ffac) = readyPaired(female)
        // 只有女方打开“更新家人资料”：协商结果仍是纪念（意愿不对称）
        fvm.setWantUpdate(true)

        val insSeen = mutableListOf<Int>()
        fvm.activateCard()
        val job = mvm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { cmd ->
                if (cmd.size >= 2) insSeen.add(cmd[1].toInt() and 0xFF)
                female.session.handleApdu(cmd)
            },
            close = {})
        assertNotNull(job)
        joinReal(job)
        awaitTap(mvm) { it.stage == Stage.SHOW }
        awaitTap(fvm) { it.stage == Stage.SHOW }
        assertEquals(TapUiState.SessionMode.MEMORIAL,
            mvm.ui.value.sessionMode)
        assertEquals(TapUiState.SessionMode.MEMORIAL,
            fvm.ui.value.sessionMode)
        // 想更新的一方收到说明：这次只放纪念仪式
        assertNotNull(fvm.ui.value.memorialHint)
        assertTrue(fvm.ui.value.memorialHint!!.contains("更新"))
        // 不导出、不传输家庭资料包
        assertEquals(0, mfac.calls)
        assertEquals(0, ffac.calls)
        assertTrue(insSeen.none {
            it == TapProtocol.INS_GET_DATA.toInt() ||
                it == TapProtocol.INS_PUT_DATA.toInt()
        })
        assertEquals(6, male.db.personDao().getAll().size)
        assertEquals(6, female.db.personDao().getAll().size)
    }

    @Test
    fun 阻塞候选取消则双方都不写入() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        // 女方旧数据里有同名配偶“杨明”→ 男方会拿到阻塞候选
        val femaleSelf = female.db.personDao().getSelf()!!.id
        (female.repo.createRelative(
            io.github.xiaohaoz1.jiarenxia.data.PersonDraft(
                "杨明", Gender.MALE),
            femaleSelf,
            io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.HUSBAND)
            as io.github.xiaohaoz1.jiarenxia.data.FamilyResult.Success)
        ready(male, true); ready(female, false)

        female.vm.activateCard()
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        awaitTap(female.vm) { it.stage == Stage.CONFIRMING }
        female.vm.confirmPending()
        // 男方停在候选确认页（阻塞候选默认勾选，这里选“取消”）
        awaitTap(male.vm) {
            it.stage == Stage.CONFIRMING &&
                it.confirm == TapUiState.Confirm.FAMILY_CANDIDATES
        }
        assertTrue(male.vm.ui.value.candidates.any { it.blocking })
        male.vm.declinePending()
        joinReal(job)
        // 男方回到等待页；女方收到取消也回等待页
        assertEquals(Stage.WAITING, male.vm.ui.value.stage)
        awaitTap(female.vm) {
            it.stage == Stage.WAITING && it.notice != null
        }
        // 双方都没写半截数据
        assertEquals(3, male.db.personDao().getAll().size)
        assertEquals(4, female.db.personDao().getAll().size)
        assertNull(male.pairing.rec)
        assertNull(female.pairing.rec)
    }

    @Test
    fun 本地提交失败显示错误且不写半截() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        // 读卡端提交失败：事务整体回滚，家人与配对都不落
        male.pairing.failOnRecord = true

        tap(male.vm, female)
        // 读卡端：提交失败进错误页（绝不显示成功页）
        awaitTap(male.vm) { it.stage == Stage.ERROR }
        assertTrue(male.vm.ui.value.error!!.contains("合并失败"))
        assertEquals(3, male.db.personDao().getAll().size)
        assertNull(male.pairing.rec)
        // 卡片端：DONE 永不到达（对方先发了 REJECT）→ 不写库，回等待页
        awaitTap(female.vm) {
            it.stage == Stage.WAITING && it.notice != null
        }
        assertEquals(3, female.db.personDao().getAll().size)
        assertNull(female.pairing.rec)
    }

    @Test
    fun 卡片端提交失败下次碰面按配对不一致恢复() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        ready(male, true); ready(female, false)
        // 只有卡片端提交失败：读卡端先写成功，卡片端收到 DONE 才写、失败
        female.pairing.failOnRecord = true

        tap(male.vm, female)
        // 读卡端已写入并合心展示（本条合并对它成立）
        awaitTap(male.vm) { it.stage == Stage.SHOW }
        assertEquals(6, male.db.personDao().getAll().size)
        assertNotNull(male.pairing.rec)
        // 卡片端：DONE 到达后本机提交失败 → 错误页，不显示成功
        awaitTap(female.vm) { it.stage == Stage.ERROR }
        assertEquals(3, female.db.personDao().getAll().size)
        assertNull(female.pairing.rec)
        val pid = male.pairing.rec!!.pairingId

        // 重贴：配对不对称 → 双方确认恢复后补写
        val mvm = reload(male).first
        val fvm = reload(female).first
        awaitTap(mvm) { it.roleReady }
        awaitTap(fvm) { it.roleReady }
        assertTrue(mvm.ui.value.paired)
        assertTrue(!fvm.ui.value.paired)
        mvm.checkNfc(true, true); fvm.checkNfc(true, true)
        // 女方无配对记忆 → 重新选角色（做卡片）；男方走记忆角色
        fvm.chooseRole(false)

        fvm.activateCard()
        val job = mvm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        awaitTap(mvm) { it.stage == Stage.CONFIRMING }
        awaitTap(fvm) { it.stage == Stage.CONFIRMING }
        mvm.confirmPending()
        fvm.confirmPending()
        joinReal(job)
        awaitTap(mvm) { it.stage == Stage.SHOW }
        awaitTap(fvm) { it.stage == Stage.SHOW }
        // 女方补齐三家；男方幂等不再新增；两端恢复同一配对标识
        assertEquals(6, female.db.personDao().getAll().size)
        assertEquals(6, male.db.personDao().getAll().size)
        assertEquals(pid, male.pairing.rec!!.pairingId)
        assertEquals(pid, female.pairing.rec!!.pairingId)
    }

    private class FemaleRemote(
        val remote: io.github.xiaohaoz1.jiarenxia.domain.merge
            .SpouseMerge.Snapshot,
        val peerSelfId: String
    )

    private suspend fun BackupJsonForTest(
        female: TapPhone, to: TapPhone
    ): FemaleRemote {
        val payload = female.backup.exportTapPayload()
        val zip = to.backup.inflatePayload(payload)
        val plan = io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson
            .parsePlan(zip)
        return FemaleRemote(
            to.backup.mergeSnapshotOf(plan),
            plan.sharedBy ?: plan.persons.first { it.isSelf }.id)
    }
}
