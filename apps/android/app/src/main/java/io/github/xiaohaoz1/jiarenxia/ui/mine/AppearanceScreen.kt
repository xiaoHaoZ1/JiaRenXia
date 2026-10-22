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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode

/** 外观与字号（我的 → 二级页）：深浅模式 + 应用内字体大小。 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: MineViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    AppearanceContent(
        themeMode = st.themeMode,
        fontScaleLevel = st.fontScaleLevel,
        onThemeMode = viewModel::setThemeMode,
        onFontScale = viewModel::setFontScaleLevel,
        onBack = onBack
    )
}

@Composable
fun AppearanceContent(
    themeMode: ThemeMode,
    fontScaleLevel: FontScaleLevel,
    onThemeMode: (ThemeMode) -> Unit,
    onFontScale: (FontScaleLevel) -> Unit,
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
        }

        SettingsGroup("字体大小") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FontScaleLevel.entries.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = fontScaleLevel == l,
                        onClick = { onFontScale(l) },
                        shape = SegmentedButtonDefaults.itemShape(i, FontScaleLevel.entries.size)
                    ) {
                        Text(
                            when (l) {
                                FontScaleLevel.STANDARD -> "标准"
                                FontScaleLevel.LARGE -> "大"
                                FontScaleLevel.EXTRA_LARGE -> "特大"
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // 这段文字本身随设置变化，改完立刻能看出效果
            Column(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text("示例：妈妈的生日还有 3 天", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "在手机系统字号之上再放大，最大约两倍。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SettingsHint("字号对全 App 生效，不影响系统其他应用。")
        Spacer(Modifier.height(16.dp))
    }
}
