package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 结婚纪念日对话框的真机回归：选年份 → 保存后年份要保存下来
 * （之前新建时“不知道年份”默认勾选，年份框被禁用，输入无效）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportantDateDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setDialog(saved: (BirthdayValue?) -> Unit) {
        composeRule.setContent {
            MaterialTheme {
                ImportantDateDialog(
                    title = "结婚纪念日",
                    initial = null,
                    lunar = null,
                    onSave = saved,
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun 新建时可选年份并保存() {
        var saved: BirthdayValue? = null
        setDialog { saved = it }
        // 年份两段选择：点开占位 → 选年代 → 选年份。
        // Robolectric 里对话框网格不测布局（bounds 为 0），
        // performClick 不触发动作，直接调用语义 OnClick
        composeRule.onNodeWithText("年份").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1950年代").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action?.invoke()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1958").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action?.invoke()
        composeRule.waitForIdle()
        // 占位“年份”被选中值替换（对话框已关闭）
        composeRule.onNodeWithText("年份").assertDoesNotExist()
        composeRule.onNodeWithText("保存").performClick()
        assertEquals(1958, saved?.date?.year)
    }

    @Test
    fun 不选年份保存视为未填年份() {
        var saved: BirthdayValue? = null
        setDialog { saved = it }
        composeRule.onNodeWithText("保存").performClick()
        assertEquals(null, saved?.date?.year)
    }

    @Test
    fun 勾选不知道年份后保存为未填() {
        var saved: BirthdayValue? = null
        setDialog { saved = it }
        composeRule.onNodeWithText("年份").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1950年代").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action?.invoke()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1958").fetchSemanticsNode()
            .config[SemanticsActions.OnClick].action?.invoke()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("不知道年份").performClick()
        composeRule.onNodeWithText("保存").performClick()
        assertEquals(null, saved?.date?.year)
    }
}
