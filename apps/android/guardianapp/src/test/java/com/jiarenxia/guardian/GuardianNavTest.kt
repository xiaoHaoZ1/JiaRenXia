package com.jiarenxia.guardian

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 宿主级导航验证：直接启动正式 MainActivity（不是测试专用 Activity），
 * 走真实的页底隐私入口点击与两种返回路径（顶栏返回、系统返回键）。
 * 不触发响铃/拨号/短信；守护状态为全新安装的默认值（未开启）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class GuardianNavTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun privacyEntryClickAndBothWaysBack() {
        // 首页（默认未开启守护）
        composeRule.onNodeWithText("守护是什么", substring = true).assertIsDisplayed()

        // 页底隐私入口 → 隐私说明页
        composeRule.onNodeWithText("隐私说明").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("守护侠不联网", substring = true).assertIsDisplayed()

        // 顶栏返回按钮 → 回首页
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("守护是什么", substring = true).assertIsDisplayed()

        // 再走一遍：系统返回键（BackHandler）也回首页
        composeRule.onNodeWithText("隐私说明").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("守护侠不联网", substring = true).assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("守护是什么", substring = true).assertIsDisplayed()
    }
}
