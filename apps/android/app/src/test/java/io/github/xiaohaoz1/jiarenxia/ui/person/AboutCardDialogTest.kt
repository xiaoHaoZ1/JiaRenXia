package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.xiaohaoz1.jiarenxia.core.model.FactCategory
import io.github.xiaohaoz1.jiarenxia.core.model.PersonFact
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 「关于TA」弹框交互：正文可滚动、编辑尺码时尺码项固定。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AboutCardDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setCard(facts: List<PersonFact>) {
        composeRule.setContent {
            JiaRenXiaTheme(mode = ThemeMode.LIGHT) {
                AboutCard(
                    facts = facts,
                    deceased = false,
                    today = LocalDate.of(2026, 10, 2),
                    onAdd = { _, _, _ -> },
                    onUpdate = { _, _, _ -> },
                    onDelete = {}
                )
            }
        }
    }

    /** 大字号加键盘时正文会超过弹框高度，必须能滚动而不是被裁掉。 */
    @Test
    fun 添加弹框正文可以滚动() {
        setCard(emptyList())
        composeRule.onNodeWithText("尺码").performClick()
        composeRule.onNode(hasScrollAction() and hasAnyDescendant(hasText("哪一项")))
            .assertExists()
    }

    /** 编辑尺码时不能切换尺码项（避免改出两条同名项），输入框标签显示该项名称。 */
    @Test
    fun 编辑尺码时尺码项固定() {
        setCard(
            listOf(
                PersonFact(
                    "f1", "mom", FactCategory.SIZE, "鞋码", "37",
                    LocalDate.of(2026, 9, 1), 1, 1
                )
            )
        )
        composeRule.onNodeWithText("鞋码 37").performClick()
        composeRule.onNodeWithText("编辑「尺码」").assertExists()
        composeRule.onAllNodesWithText("哪一项").assertCountEquals(0)
        composeRule.onAllNodesWithText("上衣").assertCountEquals(0)
        composeRule.onNodeWithText("鞋码").assertExists()
    }
}
