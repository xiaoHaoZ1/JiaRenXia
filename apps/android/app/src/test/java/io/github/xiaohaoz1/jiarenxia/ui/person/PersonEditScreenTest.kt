package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.lifecycle.SavedStateHandle
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 人物表单的 UI 级回归：经真实保存按钮触发校验（不走 vm.save() 捷径）——
 * 缺填时按钮可点击，点击后就地报错并自动定位第一个错误。
 * h700dp 矮屏让表单溢出，验证滚动定位真的发生。
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h700dp-xxhdpi")
class PersonEditScreenTest : RepoTestBase() {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun vm(
        anchorId: String,
        roles: String,
        personId: String = ""
    ) = PersonEditViewModel(
        SavedStateHandle(
            mapOf(
                "personId" to personId, "anchorId" to anchorId,
                "roles" to roles, "anchors" to ""
            )
        ),
        repo,
        ObserveFamilyOverview(repo, KinshipEngine()),
        IcuLunarCalendar()
    )

    /**
     * 等 ViewModel 状态就绪：viewModelScope 跑在 Main 上，
     * 反复泵主 Looper 直到条件满足（比 compose idle 轮询确定）。
     */
    private fun awaitState(timeoutMs: Long = 10_000, cond: () -> Boolean) {
        val main = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("awaitState 超时")
            }
            main.idle()
            Thread.sleep(10)
        }
        composeRule.waitForIdle()
    }

    private fun setForm(vm: PersonEditViewModel, mode: PersonEditMode) {
        composeRule.setContent {
            JiaRenXiaTheme {
                PersonEditScreen(mode = mode, onDone = {}, onBack = {}, viewModel = vm)
            }
        }
        awaitState { vm.ui.value.loaded }
    }

    /** 滚到表单底部（点保存前用户可能在任意位置）。 */
    private fun scrollToBottom() {
        repeat(4) {
            composeRule.onRoot().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun 缺填时保存按钮可点击_点击后标错并定位姓名() {
        val me = runBlocking { createSelf() }
        val vm = vm(me, "SISTER,BROTHER")
        setForm(vm, PersonEditMode.NEW)

        // 缺填不是“禁用保存”的理由：允许尝试提交，由校验拒绝并标错
        composeRule.onNodeWithText("保存").assertIsEnabled()
        // 用户先滚到页面底部（比如看完备注）再点保存
        scrollToBottom()

        composeRule.onNodeWithText("保存").performClick()
        awaitState { vm.ui.value.saveAttempted }

        // 从底部自动滚回顶部：姓名错误提示重新进入视野
        composeRule.onNodeWithText("请填写姓名").assertIsDisplayed()
        // 其余缺项全部标出（此刻可能在屏幕下方，只断言存在）
        composeRule.onNodeWithText("请选择性别").assertExists()
        composeRule.onNodeWithText("请选择关系").assertExists()
        // 姓名框获得焦点（真实设备上唤起键盘）
        composeRule.onAllNodes(hasSetTextAction()).onFirst().assertIsFocused()
        // 未写库
        runBlocking { assertEquals(1, db.personDao().getAll().size) }
    }

    @Test
    fun 底部缺项提示仍可点击定位() {
        val me = runBlocking { createSelf() }
        val vm = vm(me, "SISTER,BROTHER")
        setForm(vm, PersonEditMode.NEW)

        composeRule.onNodeWithText("还差：姓名、性别、称谓").performClick()
        awaitState { vm.ui.value.saveAttempted }
        composeRule.onNodeWithText("请填写姓名").assertIsDisplayed()
    }

    @Test
    fun 血缘未确认时保存定位到血缘区块() {
        val me = runBlocking { createSelf() }
        val vm = vm(me, "SISTER,BROTHER")
        setForm(vm, PersonEditMode.NEW)
        vm.onName("杨帆")
        vm.onGender(io.github.xiaohaoz1.jiarenxia.core.model.Gender.FEMALE)
        vm.onRole(io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.SISTER)
        awaitState { vm.ui.value.titleOnlyText.isNotBlank() }

        // 只差血缘细分：错误文本出现前不在视野（700dp 矮屏下它在页面中下段）
        composeRule.onNodeWithText("保存").performClick()
        awaitState { vm.ui.value.saveAttempted }
        composeRule.onNodeWithText("请完善血缘或称呼信息").assertIsDisplayed()
    }

    @Test
    fun 创建我_下一步可点击校验() {
        val vm = vm("", "")
        setForm(vm, PersonEditMode.SELF)

        composeRule.onNodeWithText("下一步").assertIsEnabled().performClick()
        awaitState { vm.ui.value.saveAttempted }
        composeRule.onNodeWithText("请填写姓名").assertIsDisplayed()
        composeRule.onNodeWithText("请选择性别").assertExists()
        runBlocking { assertTrue(db.personDao().getAll().isEmpty()) }
    }

    @Test
    fun 保存并继续添加_缺填时同样校验标错() {
        val me = runBlocking { createSelf() }
        val vm = vm(me, "FATHER")
        setForm(vm, PersonEditMode.NEW)

        composeRule.onNodeWithText("保存并继续添加")
            .assertIsEnabled().performClick()
        awaitState { vm.ui.value.saveAttempted }
        composeRule.onNodeWithText("请填写姓名").assertIsDisplayed()
        // 校验拒绝：不写库、不进入下一位
        runBlocking { assertEquals(1, db.personDao().getAll().size) }
        assertTrue(vm.ui.value.savedNotice == null)
    }

    @Test
    fun 修正后再次保存可成功() {
        val me = runBlocking { createSelf() }
        val vm = vm(me, "FATHER")
        setForm(vm, PersonEditMode.NEW)
        vm.onName("杨建国")

        composeRule.onNodeWithText("保存").performClick()
        awaitState { vm.ui.value.done }
        runBlocking { assertEquals(2, db.personDao().getAll().size) }
    }
}
