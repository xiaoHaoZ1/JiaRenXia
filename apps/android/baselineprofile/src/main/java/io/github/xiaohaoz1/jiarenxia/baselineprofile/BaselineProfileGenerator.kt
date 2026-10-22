package io.github.xiaohaoz1.jiarenxia.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成用例（连真机，跑在 nonMinifiedRelease 变体上）：
 *
 *   gradlew.bat :app:generateReleaseBaselineProfile
 *
 * 成功后产物自动写入 app/src/main/generated/baselineProfiles/，
 * 随全部变体（含 release）打包，安装时 ART 按它预编译热点路径。
 *
 * 已知问题：小米/HyperOS 拦截系统发出的 SAVE_PROFILE 广播，本用例
 * 会在走查完成后报 "save profile broadcast was not received"。
 * 手动备选（产出等价文件）：
 *   1. 装 nonMinifiedRelease 包，adb 手动走一遍下述走查路径（input tap/swipe）
 *   2. adb shell pm dump-profiles --dump-classes-and-methods com.jiarenxia.app
 *   3. 把它写到 /data/misc/profman/<pkg>-primary.prof.txt 的内容
 *      复制进 app/src/main/generated/baselineProfiles/baseline-prof.txt
 *
 * 走查路径 = 用户高频动作：冷启动 → 家庭列表滑动 → 三个底部 tab 轮换 →
 * 打开第一个人物详情 → 返回 → 我的页滑动。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val uiDevice: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun generate() = rule.collect(
        packageName = "com.jiarenxia.app",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // 家庭页：滑动列表（LazyColumn 重组 + 头像解码路径）
        scrollList()

        // 三个底部 tab 轮换一轮（各屏首组合）
        listOf("家事", "我的", "家庭").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.wait(Until.hasObject(By.text(tab)), 3000)
        }

        // 打开家庭列表里第一个可点卡片（人物详情是重组最重的页），返回
        runCatching {
            val list = UiScrollable(UiSelector().scrollable(true))
            if (list.exists()) {
                val first = list.getChild(UiSelector().clickable(true))
                if (first != null && first.exists()) {
                    first.click()
                    uiDevice.waitForIdle(2000)
                    uiDevice.pressBack()
                }
            }
        }

        // 我的页再滑动一次
        device.findObject(By.text("我的"))?.click()
        device.wait(Until.hasObject(By.text("我的")), 3000)
        scrollList()
        pressHome()
    }

    private fun scrollList() {
        runCatching {
            val list = UiScrollable(UiSelector().scrollable(true))
            if (list.exists()) {
                list.flingForward()
                uiDevice.waitForIdle(1000)
                list.flingBackward()
                uiDevice.waitForIdle(1000)
            }
        }
    }
}
