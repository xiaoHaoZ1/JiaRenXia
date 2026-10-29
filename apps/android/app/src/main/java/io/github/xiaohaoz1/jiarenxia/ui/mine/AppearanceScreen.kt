package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.data.settings.MAX_FONT_SCALE_FACTOR
import io.github.xiaohaoz1.jiarenxia.data.settings.MIN_FONT_SCALE_FACTOR
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode
import kotlin.math.roundToInt

/** 外观与字号（我的 → 二级页）：深浅模式 + 桌面插件配色 + 应用内字体大小。 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: MineViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    AppearanceContent(
        themeMode = st.themeMode,
        widgetThemeMode = st.widgetThemeMode,
        fontScaleFactor = st.fontScaleFactor,
        onThemeMode = viewModel::setThemeMode,
        onWidgetThemeMode = viewModel::setWidgetThemeMode,
        onFontScale = viewModel::setFontScaleFactor,
        onBack = onBack
    )
}

@Composable
fun AppearanceContent(
    themeMode: ThemeMode,
    widgetThemeMode: WidgetThemeMode,
    fontScaleFactor: Float,
    onThemeMode: (ThemeMode) -> Unit,
    onWidgetThemeMode: (WidgetThemeMode) -> Unit,
    onFontScale: (Float) -> Unit,
    onBack: () -> Unit
) {
    SettingsScaffold("外观与字号", onBack) {
        SettingsGroup("深浅模式") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = themeMode == m,
                        onClick = { onThemeMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)
                    ) {
                        Text(
                            when (m) {
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                                ThemeMode.SYSTEM -> "跟随系统"
                            }
                        )
                    }
                }
            }
            SettingsHint("深浅模式管 App 内界面。")
        }

        SettingsGroup("桌面插件配色") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WidgetThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = widgetThemeMode == m,
                        onClick = { onWidgetThemeMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, WidgetThemeMode.entries.size)
                    ) {
                        Text(
                            when (m) {
                                WidgetThemeMode.LIGHT -> "浅色"
                                WidgetThemeMode.DARK -> "深色"
                                WidgetThemeMode.SYSTEM -> "跟随系统"
                            }
                        )
                    }
                }
            }
            SettingsHint("只管桌面「家事时间线」插件：系统深色时也可以让插件保持浅色。")
        }

        SettingsGroup("字体大小") {
            // 5% 步进：拖动即时改示例与百分比，松手写设置、全 App 生效
            var slider by remember(fontScaleFactor) {
                mutableFloatStateOf(fontScaleFactor)
            }
            Text(
                "字号 " + (slider * 100).roundToInt() + "%",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { onFontScale(slider) },
                valueRange = MIN_FONT_SCALE_FACTOR..MAX_FONT_SCALE_FACTOR,
                steps = ((MAX_FONT_SCALE_FACTOR - MIN_FONT_SCALE_FACTOR) * 20)
                    .roundToInt() - 1
            )
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(
                    "示例：妈妈的生日还有 3 天",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * slider)
                )
                Text(
                    "在手机系统字号之上再调整，85%–160%。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SettingsHint("字号对全 App 生效，不影响系统其他应用。")
        Spacer(Modifier.height(16.dp))
    }
}
