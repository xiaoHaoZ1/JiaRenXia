package com.jiarenxia.guardian

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.jiarenxia.guardian.ui.PrivacyScreen
import com.jiarenxia.guardian.ui.standaloneHomeOptions
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardConfig
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardState
import io.github.xiaohaoz1.jiarenxia.guardian.core.Phase
import io.github.xiaohaoz1.jiarenxia.guardian.sys.Permissions
import io.github.xiaohaoz1.jiarenxia.guardian.ui.GuardianHome
import io.github.xiaohaoz1.jiarenxia.guardian.ui.GuardianHomeCallbacks
import io.github.xiaohaoz1.jiarenxia.guardian.ui.GuardianHomeUi
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 守护侠 Roborazzi 截图：全部使用合成数据与静态状态，不触碰 ServiceLocator、
 * 不触发真实响铃/拨号/短信。产物在 build/outputs/roborazzi/g_*.png。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class GuardianShotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // 测试只提供主题，不垫 Surface：正式页面必须自己绘制背景，
    // 不能靠测试包装补齐页面缺失的背景（隐私页深色曾因此漏检）。
    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            JrxTheme(dark = dark) { content() }
        }
        composeRule.onRoot().captureRoboImage(
            File("build/outputs/roborazzi/g_$name.png").absolutePath
        )
    }

    /** 大字号（×1.3）截图：LocalDensity 覆盖模拟系统大字体，文件名加 _lg。 */
    private fun shotLarge(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(d.density, d.fontScale * 1.3f)
            ) {
                JrxTheme(dark = false) { content() }
            }
        }
        composeRule.onRoot().captureRoboImage(
            File("build/outputs/roborazzi/g_${name}_lg.png").absolutePath
        )
    }

    /** 滚到目标文字所在位置再截整屏：用于页底（按钮/隐私入口/版本号）截图。 */
    private fun shotScrolledTo(name: String, anchor: String, large: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            if (large) {
                val d = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(d.density, d.fontScale * 1.3f)
                ) {
                    JrxTheme(dark = false) { content() }
                }
            } else {
                JrxTheme(dark = false) { content() }
            }
        }
        // substring：版本号在 debug 构建渲染为“版本 1.0.0-debug”
        composeRule.onNodeWithText(anchor, substring = true).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File("build/outputs/roborazzi/g_$name.png").absolutePath
        )
    }

    private fun assertTexts(vararg texts: String) {
        texts.forEach {
            check(
                composeRule.onAllNodes(hasText(it, substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            ) { "assertTexts: 未找到包含文本的节点: " + it }
        }
    }

    private fun assertNoTexts(vararg texts: String) {
        texts.forEach {
            check(
                composeRule.onAllNodes(hasText(it, substring = true))
                    .fetchSemanticsNodes().isEmpty()
            ) { "assertNoTexts: 不应出现文本: " + it }
        }
    }

    // ---------- 合成数据 ----------

    private val demoNow = java.time.LocalDateTime.of(2026, 10, 18, 19, 0)
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun demoPerms() = listOf(
        Permissions.PermissionItem(
            key = "call_phone", title = "拨打电话",
            desc = "紧急时自动拨号", granted = true),
        Permissions.PermissionItem(
            key = "phone_state", title = "电话状态",
            desc = "接听电话也算使用", granted = true),
        Permissions.PermissionItem(
            key = "send_sms", title = "发送短信",
            desc = "每日平安短信", granted = true, optional = true),
        Permissions.PermissionItem(
            key = "notifications", title = "通知",
            desc = "响铃提醒与状态通知", granted = false),
        Permissions.PermissionItem(
            key = "battery", title = "忽略电池优化",
            desc = "防止后台被清理", granted = false),
        Permissions.PermissionItem(
            key = "usage_stats", title = "使用情况访问",
            desc = "漏报兜底", granted = false, optional = true),
    )

    private fun homeUi(enabled: Boolean, contact: Boolean) = GuardianHomeUi(
        config = GuardConfig(
            enabled = enabled,
            contactName = if (contact) "杨明" else "",
            contactNumber = if (contact) "138 0013 8000" else ""),
        state = GuardState(
            phase = Phase.MONITORING,
            lastActivityAt = demoNow - 3 * 3600_000L,
            deadlineAt = demoNow + 5 * 3600_000L,
            confirmEndAt = demoNow + 90_000L,
            nextDailySmsAt = demoNow + 14 * 3600_000L),
        // 守护侠无家庭数据：紧急联系人只有通讯录与手动输入两条路
        perms = demoPerms(),
        now = demoNow)

    @Composable
    private fun Home(enabled: Boolean, contact: Boolean) {
        GuardianHome(
            homeUi(enabled, contact),
            GuardianHomeCallbacks(),
            standaloneHomeOptions(onPrivacy = {})
        )
    }

    // ---------- 首页：未开启（浅色 / 深色 / 大字号） ----------

    @Test
    fun homeDisabled() = shot("home_disabled") {
        Home(enabled = false, contact = false)
    }.also {
        // 标题、状态副标题、提示卡两行、设置入口与页尾
        assertTexts(
            "守护侠", "未开启", "适合装在长辈的手机上",
            "装好后请先用测试模式走一遍，确认能响能拨。",
            "从通讯录选择", "手动输入", "请先设置紧急联系人",
            "开启守护", "隐私说明", "版本 1.0.0"
        )
        // 守护侠不提供家人候选入口与空家人列表
        assertNoTexts("从家人中选择", "家人资料里还没有其他家人", "还没填手机号",
            "如果家人侠里开着守护")
    }

    @Test
    fun homeDisabledDark() = shot("home_disabled_dark", dark = true) {
        Home(enabled = false, contact = false)
    }

    @Test
    fun homeDisabledLarge() = shotLarge("home_disabled") {
        Home(enabled = false, contact = false)
    }

    // ---------- 首页：已开启 ----------

    @Test
    fun homeEnabled() = shot("home_enabled") {
        Home(enabled = true, contact = true)
    }.also {
        assertTexts(
            "守护侠", "守护已开启", "就打电话通知", "查看守护记录",
            "运行状态", "隐私说明", "版本 1.0.0"
        )
        assertNoTexts("从家人中选择", "家人资料里还没有其他家人")
    }

    @Test
    fun homeEnabledDark() = shot("home_enabled_dark", dark = true) {
        Home(enabled = true, contact = true)
    }

    // ---------- 联系人选择（弹层只剩通讯录/手动输入） ----------

    @Test
    fun contactPicker() {
        composeRule.setContent {
            JrxTheme(dark = false) { Home(enabled = false, contact = true) }
        }
        composeRule.onNodeWithText("更换").performClick()
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            File("build/outputs/roborazzi/g_contact_picker.png").absolutePath
        )
        assertTexts("选择紧急联系人", "从通讯录选择", "手动输入")
        assertNoTexts("王秀兰", "家人资料里还没有其他家人", "还没填手机号")
    }

    // ---------- 页底（滚动到底部：开启/停止按钮、隐私入口、版本号） ----------

    @Test
    fun homeDisabledBottom() = shotScrolledTo("home_disabled_bottom", "版本") {
        Home(enabled = false, contact = false)
    }.also {
        assertTexts("开启守护", "请先设置紧急联系人", "隐私说明", "版本 1.0.0")
    }

    @Test
    fun homeEnabledBottom() = shotScrolledTo("home_enabled_bottom", "版本") {
        Home(enabled = true, contact = true)
    }.also {
        assertTexts("停止守护", "隐私说明", "版本 1.0.0")
    }

    @Test
    fun homeDisabledBottomLarge() =
        shotScrolledTo("home_disabled_bottom_lg", "版本", large = true) {
            Home(enabled = false, contact = false)
        }.also {
            assertTexts("隐私说明", "版本 1.0.0")
        }

    // ---------- 隐私说明 ----------

    @Test
    fun privacy() = shot("privacy") {
        PrivacyScreen(onBack = {})
    }.also {
        assertTexts(
            "隐私说明",
            "不联网、不注册账号，所有配置只保存在这台手机上",
            "拨打你设置的紧急联系人", "运营商按你的套餐收取",
            "系统选择器读取你选中的那一个号码",
            "不进入系统云备份和设备间迁移"
        )
    }

    @Test
    fun privacyDark() = shot("privacy_dark", dark = true) {
        PrivacyScreen(onBack = {})
    }
}
