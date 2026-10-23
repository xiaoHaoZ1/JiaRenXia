package io.github.xiaohaoz1.jiarenxia.ui.tap

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
import io.github.xiaohaoz1.jiarenxia.nfc.TapSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNfcAdapter

/**
 * 碰一碰页面生命周期回归（0.9.1 修复）：
 * - 女方不设 CardEmulation 首选服务——小米等机型钱包弹前台时系统会解绑
 *   正在通信的 HCE 服务；卡片在整个页面期间应答，pause 不停用。
 * - 男方读卡模式照旧随 ON_RESUME/ON_PAUSE 启停。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class TapScreenLifecycleTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context get() =
        ApplicationProvider.getApplicationContext()

    private val phones = mutableListOf<TapPhone>()

    @Before
    fun nfcOn() {
        // viewModelScope 走 Main：换成 UnconfinedTestDispatcher 即时驱动
        Dispatchers.setMain(UnconfinedTestDispatcher())
        shadowOf(ctx.packageManager).setSystemFeature(
            PackageManager.FEATURE_NFC, true)
        shadowOf(ctx.packageManager).setSystemFeature(
            PackageManager.FEATURE_NFC_HOST_CARD_EMULATION, true)
        ShadowNfcAdapter.setNfcHardwareExists(true)
        shadowOf(NfcAdapter.getDefaultAdapter(ctx)).setEnabled(true)
    }

    @After
    fun tearDown() {
        phones.forEach { it.db.close() }
        ShadowNfcAdapter.reset()
        Dispatchers.resetMain()
    }

    private fun phone(
        name: String, gender: Gender, father: String, mother: String
    ): TapPhone = runBlocking {
        buildTapPhone(name, gender, father, mother)
    }.also { phones.add(it) }

    /** 等页面进入等待页（checkNfc 在 ON_RESUME 自动触发）。 */
    private fun awaitWaiting(p: TapPhone) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            p.vm.ui.value.roleReady &&
                p.vm.ui.value.stage == TapUiState.Stage.WAITING
        }
    }

    private fun selectHello(session: TapSession): Pair<Int, ByteArray> {
        val r = session.handleApdu(TapProtocol.selectApdu())
        assertEquals("SELECT 应回 9000", 0x9000, TapProtocol.sw(r))
        val hello = TapProtocol.Hello.decode(
            r.copyOfRange(0, r.size - 2))
        assertNotNull(hello)
        return hello!!.sessionId to r
    }

    @Test
    fun 女方暂停时卡片仍应答直到离开页面() {
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        female.vm.chooseRole(false)
        composeRule.setContent {
            TapScreen(onBack = {}, onDone = {}, viewModel = female.vm)
        }
        awaitWaiting(female)
        // 页面挂上了卡片（整个页面期间激活，不靠 lifecycle 启停）
        composeRule.waitUntil(timeoutMillis = 5_000) { female.session.active }
        val (s1, _) = selectHello(female.session)

        // 模拟被钱包/交通卡页面盖到前台：本应用 pause
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.waitForIdle()
        val (s2, _) = selectHello(female.session)
        assertEquals("pause 不应重置会话", s1, s2)

        // 离开页面（销毁）→ 卡片停用，SELECT 回 6A82
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        composeRule.waitForIdle()
        assertArrayEquals(
            TapProtocol.SW_NOT_FOUND,
            female.session.handleApdu(TapProtocol.selectApdu()))
        assertFalse(female.session.active)
    }

    @Test
    fun 男方读卡模式随前台启停() {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        male.vm.chooseRole(true)
        composeRule.setContent {
            TapScreen(onBack = {}, onDone = {}, viewModel = male.vm)
        }
        awaitWaiting(male)
        val adapter = NfcAdapter.getDefaultAdapter(ctx)
        // RESUMED：读卡模式已开启
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(adapter).isInReaderMode
        }
        // pause → 读卡模式关闭
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !shadowOf(adapter).isInReaderMode
        }
        // resume → 读卡模式重新开启
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(adapter).isInReaderMode
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        composeRule.waitForIdle()
        assertFalse(shadowOf(adapter).isInReaderMode)
    }

    @Test
    fun 交换读卡角色后男方当卡片端() {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        // toggleRole 只在 WAITING/ERROR 生效：就绪 → 先选角色 → 交换
        runBlocking { awaitTap(male.vm) { it.roleReady } }
        male.vm.checkNfc(supported = true, enabled = true, hce = true)
        assertEquals(TapUiState.Stage.CHOOSE_ROLE, male.vm.ui.value.stage)
        male.vm.chooseRole(true)
        assertEquals(TapUiState.Stage.WAITING, male.vm.ui.value.stage)
        male.vm.toggleRole()
        assertFalse(male.vm.ui.value.isReader)

        composeRule.setContent {
            TapScreen(onBack = {}, onDone = {}, viewModel = male.vm)
        }
        // 男方手机走了卡片分支：会话激活且不开读卡模式
        composeRule.waitUntil(timeoutMillis = 5_000) { male.session.active }
        val adapter = NfcAdapter.getDefaultAdapter(ctx)
        composeRule.waitForIdle()
        assertFalse(shadowOf(adapter).isInReaderMode)
    }

    @Test
    fun 读卡端展示期间保持读卡模式() {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        male.vm.chooseRole(true)
        composeRule.setContent {
            TapScreen(onBack = {}, onDone = {}, viewModel = male.vm)
        }
        awaitWaiting(male)
        runBlocking { awaitTap(female.vm) { it.roleReady } }
        female.vm.checkNfc(supported = true, enabled = true, hce = true)
        female.vm.chooseRole(false)
        female.vm.activateCard()

        val adapter = NfcAdapter.getDefaultAdapter(ctx)
        // 内存链路走完交换 → SHOW：读卡模式仍保持（0.9.4：
        // 否则两机仍贴着时系统把对方 HCE 当普通标签分发）
        val job = male.vm.startReader(
            open = {},
            link = TapProtocol.ApduTransport { female.session.handleApdu(it) },
            close = {})
        assertNotNull(job)
        runBlocking {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(15_000) { job!!.join() }
            }
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            male.vm.ui.value.stage == TapUiState.Stage.SHOW
        }
        // 不能用 waitForIdle：合心动画按帧循环，页面永远 busy
        assertTrue("SHOW 期间读卡模式应保持", shadowOf(adapter).isInReaderMode)

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.DESTROYED)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !shadowOf(adapter).isInReaderMode
        }
    }
}
