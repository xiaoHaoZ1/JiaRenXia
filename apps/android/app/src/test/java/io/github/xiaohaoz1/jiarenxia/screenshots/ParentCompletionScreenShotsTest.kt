package io.github.xiaohaoz1.jiarenxia.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.CommonChildCandidate
import io.github.xiaohaoz1.jiarenxia.data.CommonChildrenAsk
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.ui.common.CommonChildrenDialog
import io.github.xiaohaoz1.jiarenxia.ui.theme.JiaRenXiaTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

/** 以合成名字验收补全父母的明确问句、按钮和大字号渲染。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ParentCompletionScreenShotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(label: String, dark: Boolean = false, large: Boolean = false) {
        val ask = CommonChildrenAsk("a", if (label == "父亲") "林青" else "周远", "b", if (label == "父亲") "周远" else "林青",
            listOf(CommonChildCandidate(Person("child", "周安", Gender.MALE), "mom", "儿子")),
            parentLabel = label)
        var confirmed = 0
        var dismissed = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                JiaRenXiaTheme(mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    Surface {
                        CommonChildrenDialog(ask, {}, { confirmed++ }, { dismissed++ })
                    }
                }
            }
        }
        compose.onNodeWithText("${ask.spouseBName}也是周安的${label}吗？").assertExists()
        compose.onRoot().captureRoboImage(File("build/outputs/roborazzi/parent_completion_${label}_${dark}_${large}.png").absolutePath)
        compose.onNodeWithText("暂不添加").performClick()
        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
        compose.onNodeWithText("确定").performClick()
        assertEquals(1, confirmed)
    }

    @Test fun father() = shot("父亲")
    @Test fun motherDarkLarge() = shot("母亲", dark = true, large = true)

    @Test fun existingChildDetail() {
        val child = Person("child", "周安", Gender.MALE)
        val ask = CommonChildrenAsk("mom", "林青", "dad", "周远",
            listOf(CommonChildCandidate(child, "mom", "儿子")), parentLabel = "父亲")
        var clicked = false
        compose.setContent {
            JiaRenXiaTheme(mode = ThemeMode.LIGHT) {
                io.github.xiaohaoz1.jiarenxia.ui.person.PersonDetailContent(
                    st = io.github.xiaohaoz1.jiarenxia.ui.person.PersonDetailUiState(
                        loading = false, detail = io.github.xiaohaoz1.jiarenxia.data.PersonDetail(child, emptyList(), emptyMap(), emptyList()),
                        parentCompletion = ask),
                    today = java.time.LocalDate.of(2026, 10, 17),
                    lunar = io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar(),
                    snackbar = androidx.compose.material3.SnackbarHostState(),
                    onBack = {}, onEdit = {}, onRelate = {}, onOpenPerson = {}, onDial = {},
                    onSetReminder = { _, _ -> }, onSetReminderOffsets = { _, _ -> },
                    onSetCustomTitle = {}, onDeletePerson = {}, onSetSiblingOrder = { _, _, _ -> },
                    onRemoveRelation = {}, onParentCompletion = { clicked = true })
            }
        }
        compose.onNodeWithText("确认父亲 · 周远").performScrollTo()
        compose.onRoot().captureRoboImage(File("build/outputs/roborazzi/parent_completion_detail.png").absolutePath)
        compose.onNodeWithText("确认父亲 · 周远").performClick()
        assertEquals(true, clicked)
    }
}
