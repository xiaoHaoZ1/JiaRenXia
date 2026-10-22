package io.github.xiaohaoz1.jiarenxia.ui.welcome

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 新手引导（单页版）的交互回归：标题与三条要点可见，
 * 点“开始使用”完成并写 introSeen。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IntroContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun 单页要点齐全() {
        composeRule.setContent { JiaRenXiaTheme { IntroContent(onDone = {}) } }
        composeRule.onNodeWithText("家人、日子、家事").assertIsDisplayed()
        // 小视口/大字号下要点可滚动，逐条滚到可见再断言
        listOf("记家人、查关系", "记日子、办家事", "只在这台手机里").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        // “开始使用”固定底部，不需要滚动
        composeRule.onNodeWithText("开始使用").assertIsDisplayed()
    }

    @Test
    fun 开始使用完成引导() {
        var done = false
        composeRule.setContent { JiaRenXiaTheme { IntroContent(onDone = { done = true }) } }
        composeRule.onNodeWithText("开始使用").performClick()
        assertTrue(done)
    }
}
