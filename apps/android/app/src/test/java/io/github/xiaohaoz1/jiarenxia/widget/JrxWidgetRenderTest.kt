package io.github.xiaohaoz1.jiarenxia.widget

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode
import io.github.xiaohaoz1.jiarenxia.domain.anniversary.CoupleCounter.CoupleLine
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 家事时间线插件渲染验收：每个状态先校验 Glance 节点树，
 * 再把同一份内容翻译为 RemoteViews 生成深浅色 Roborazzi 截图。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
class JrxWidgetRenderTest {

    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight-xxhdpi")
    fun readyLight() = verifyAndCapture("ready_light", readyState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun readyDark() = verifyAndCapture("ready_dark", readyState())

    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight-xxhdpi")
    fun lockedLight() = verifyAndCapture("locked_light", WidgetState.Locked)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun lockedDark() = verifyAndCapture("locked_dark", WidgetState.Locked)

    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight-xxhdpi")
    fun emptyLight() = verifyAndCapture("empty_light", WidgetState.Empty)

    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun emptyDark() = verifyAndCapture("empty_dark", WidgetState.Empty)

    // 强制配色：系统是深色但插件强制浅色（及反向），两档 ColorProvider 应收拢到同一套。
    @Test
    @Config(qualifiers = "w360dp-h800dp-night-xxhdpi")
    fun forcedLightOnNightSystem() = verifyAndCapture(
        "ready_forced_light", readyState(), WidgetThemeMode.LIGHT)

    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight-xxhdpi")
    fun forcedDarkOnLightSystem() = verifyAndCapture(
        "ready_forced_dark", readyState(), WidgetThemeMode.DARK)

    /** 调色板断言：强制模式下 day/night 两档解出同一个颜色。 */
    @Test
    @Config(qualifiers = "w360dp-h800dp-notnight-xxhdpi")
    fun paletteRespectsForcedMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val light = WidgetColors(WidgetThemeMode.LIGHT)
        val dark = WidgetColors(WidgetThemeMode.DARK)
        // 浅色环境下：强制浅色是暖白，强制深色是深棕
        assertEquals(
            Color(0xFFFBF8F5), light.surface.getColor(context))
        assertEquals(
            Color(0xFF26201C), dark.surface.getColor(context))
        // 强制模式下正文色也不随环境翻转
        assertEquals(
            Color(0xFF1F1A17), light.text.getColor(context))
        assertEquals(
            Color(0xFFF4EDEA), dark.text.getColor(context))
    }

    private fun verifyAndCapture(
        name: String,
        state: WidgetState,
        themeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                JrxWidgetContent(context, state, FIXED_TODAY, themeMode)
            }

            onNode(hasTextEqualTo("10月26日 周一")).assertHasText("10月26日 周一")
            onNode(hasTextEqualTo("家事时间线")).assertHasText("家事时间线")
            when (state) {
                is WidgetState.Ready -> {
                    onAllNodes(hasTextEqualTo("逾期")).assertCountEquals(1)
                    // 一个组头 + 两个今日事项的实底胶囊。
                    onAllNodes(hasTextEqualTo("今天")).assertCountEquals(3)
                    onAllNodes(hasTextEqualTo("明天")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("后天")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("恋")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("生")).assertCountEquals(2)
                    onAllNodes(hasTextEqualTo("念")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("忌")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("联")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("事")).assertCountEquals(2)
                    onAllNodes(hasTextEqualTo("逾期2天")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("10月27日")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("10月28日")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("7天后")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("11月2日")).assertCountEquals(1)
                    onAllNodes(hasTextEqualTo("11月15日")).assertCountEquals(2)
                }
                WidgetState.Locked ->
                    onNode(hasTextEqualTo("解锁手机后显示家事")).assertHasText("解锁手机后显示家事")
                WidgetState.Empty -> {
                    onNode(hasTextEqualTo("近期没有家事安排")).assertHasText("近期没有家事安排")
                    onNode(hasTextEqualTo("点按查看家事页")).assertHasText("点按查看家事页")
                }
                WidgetState.Error -> error("本测试不覆盖 Error 状态")
            }
        }
        captureRemoteViews(name, state, WIDGET_HEIGHT_DP, themeMode)
        if (state is WidgetState.Ready) {
            captureRemoteViews("${name}_tall", state, WIDGET_TALL_HEIGHT_DP, themeMode)
        }
    }

    private fun captureRemoteViews(
        name: String,
        state: WidgetState,
        heightDp: Int,
        themeMode: WidgetThemeMode,
    ) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val remoteViews = runBlocking {
            FixtureWidget(state, themeMode).compose(
                context = activity,
                size = DpSize(WIDGET_WIDTH_DP.dp, heightDp.dp),
            )
        }
        val root = FrameLayout(activity)
        val density = activity.resources.displayMetrics.density
        val width = (WIDGET_WIDTH_DP * density).toInt()
        val height = (heightDp * density).toInt()
        activity.setContentView(root, ViewGroup.LayoutParams(width, height))
        root.addView(remoteViews.apply(activity, root))
        shadowOf(Looper.getMainLooper()).idle()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)

        val output = File("build/outputs/roborazzi/widget_$name.png").absoluteFile
        output.parentFile?.mkdirs()
        root.captureRoboImage(output.absolutePath)
        controller.pause().stop().destroy()
    }

    private class FixtureWidget(
        private val state: WidgetState,
        private val themeMode: WidgetThemeMode,
    ) : GlanceAppWidget() {
        override suspend fun provideGlance(context: Context, id: GlanceId) {
            provideContent {
                JrxWidgetContent(
                    context = context,
                    state = state,
                    today = FIXED_TODAY,
                    themeMode = themeMode,
                    staticTimelineForTest = true,
                )
            }
        }
    }

    companion object {
        private val FIXED_TODAY: LocalDate = LocalDate.of(2026, 10, 26)
        private const val WIDGET_WIDTH_DP = 328
        private const val WIDGET_HEIGHT_DP = 300
        private const val WIDGET_TALL_HEIGHT_DP = 530

        private fun readyState(): WidgetState.Ready = WidgetState.Ready(
            couple = CoupleLine(
                partnerId = "partner",
                title = "我和老婆相恋 3650 天",
                note = "结婚 8 年 · 领证 3000 天",
            ),
            items = listOf(
                WidgetItem(
                    key = "t:overdue",
                    kind = WidgetItemKind.TASK,
                    title = "更换驾驶证",
                    note = "我",
                    daysUntil = -2,
                    taskId = "overdue",
                ),
                WidgetItem(
                    key = "b:mother",
                    kind = WidgetItemKind.BIRTHDAY,
                    title = "妈妈生日",
                    note = "农历九月十六",
                    daysUntil = 0,
                    personId = "mother",
                ),
                WidgetItem(
                    key = "c:father",
                    kind = WidgetItemKind.CONTACT,
                    title = "该联系爸爸了",
                    note = "已经 14 天没联系",
                    daysUntil = 0,
                    personId = "father",
                ),
                WidgetItem(
                    key = "d:anniversary",
                    kind = WidgetItemKind.ANNIVERSARY,
                    title = "结婚纪念日",
                    note = "第 8 年",
                    daysUntil = 1,
                    personId = "partner",
                ),
                WidgetItem(
                    key = "d:memorial",
                    kind = WidgetItemKind.MEMORIAL,
                    title = "爷爷忌日",
                    note = "农历九月十八",
                    daysUntil = 2,
                    personId = "grandfather",
                ),
                WidgetItem(
                    key = "t:insurance",
                    kind = WidgetItemKind.TASK,
                    title = "续家庭保险",
                    note = "妈妈",
                    daysUntil = 7,
                    taskId = "insurance",
                ),
                WidgetItem(
                    key = "b:sister",
                    kind = WidgetItemKind.BIRTHDAY,
                    title = "姐姐生日",
                    note = null,
                    daysUntil = 20,
                    personId = "sister",
                ),
            ),
        )
    }
}
