package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.data.settings.MAX_FONT_SCALE_FACTOR
import io.github.xiaohaoz1.jiarenxia.data.settings.MIN_FONT_SCALE_FACTOR
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxStyle
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
        themeStyle = st.themeStyle,
        widgetThemeMode = st.widgetThemeMode,
        fontScaleFactor = st.fontScaleFactor,
        elderMode = st.elderMode,
        onThemeMode = viewModel::setThemeMode,
        onThemeStyle = viewModel::setThemeStyle,
        onWidgetThemeMode = viewModel::setWidgetThemeMode,
        onFontScale = viewModel::setFontScaleFactor,
        onElderMode = viewModel::setElderMode,
        onBack = onBack
    )
}

@Composable
fun AppearanceContent(
    themeMode: ThemeMode,
    themeStyle: io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle,
    widgetThemeMode: WidgetThemeMode,
    fontScaleFactor: Float,
    elderMode: Boolean,
    onThemeMode: (ThemeMode) -> Unit,
    onThemeStyle: (io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle) -> Unit,
    onWidgetThemeMode: (WidgetThemeMode) -> Unit,
    onFontScale: (Float) -> Unit,
    onElderMode: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val jiashu = LocalJrxStyle.current == JrxStyle.JIASHU
    var showElderConfirm by remember { mutableStateOf(false) }
    SettingsScaffold("外观与字号", onBack) {
        SettingsGroup("主题") {
            StyleOptions(
                labels = JrxStyle.entries.map {
                    when (it) { JrxStyle.JIASHU -> "家书"; JrxStyle.CLASSIC -> "经典" }
                },
                selected = JrxStyle.entries.indexOf(themeStyle),
                onSelect = { onThemeStyle(JrxStyle.entries[it]) }
            )
            SettingsHint("「家书」是纸墨风的改版样式；「经典」是原来的暖白样式。切换即全 App 生效。")
        }

        SettingsGroup("深浅模式") {
            StyleOptions(
                labels = ThemeMode.entries.map {
                    when (it) {
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                        ThemeMode.SYSTEM -> "跟随系统"
                    }
                },
                selected = ThemeMode.entries.indexOf(themeMode),
                onSelect = { onThemeMode(ThemeMode.entries[it]) }
            )
            SettingsHint("深浅模式管 App 内界面。")
        }

        SettingsGroup("桌面插件配色") {
            StyleOptions(
                labels = WidgetThemeMode.entries.map {
                    when (it) {
                        WidgetThemeMode.LIGHT -> "浅色"
                        WidgetThemeMode.DARK -> "深色"
                        WidgetThemeMode.SYSTEM -> "跟随系统"
                    }
                },
                selected = WidgetThemeMode.entries.indexOf(widgetThemeMode),
                onSelect = { onWidgetThemeMode(WidgetThemeMode.entries[it]) }
            )
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
                    .roundToInt() - 1,
                // 「家书」滑杆：墨色轨道 + 朱砂滑钮
                colors = if (jiashu) SliderDefaults.colors(
                    thumbColor = LocalJrxColors.current.seal,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                ) else SliderDefaults.colors()
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

        SettingsGroup("长辈模式") {
            SettingsNavRow(
                icon = Icons.Rounded.Elderly,
                title = "长辈模式",
                subtitle = "只看家人、打电话和提醒，字更大，不能改资料",
                arrow = false,
                onClick = { showElderConfirm = true }
            )
        }
        if (showElderConfirm) {
            AlertDialog(
                onDismissRequest = { showElderConfirm = false },
                title = { Text("开启长辈模式？") },
                text = { ElderModeConfirmDialogBody() },
                confirmButton = {
                    TextButton(onClick = {
                        showElderConfirm = false
                        onElderMode(true)
                    }) { Text("开启") }
                },
                dismissButton = {
                    TextButton(onClick = { showElderConfirm = false }) {
                        Text("取消")
                    }
                }
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** 长辈模式开启确认框的正文（截图测试直接渲染同款内容）。 */
@Composable
internal fun ElderModeConfirmDialogBody() {
    Text(
        "适合装在家里长辈的手机上：只保留看家人、打电话和最近的日子，" +
            "字号放大，资料不能修改。\n\n" +
            "要退出时，在长辈模式页面最下方长按「退出长辈模式」3 秒。"
    )
}

/**
 * 单选选项：「家书」用文字选项，选中项粗体加朱砂下划线；
 * 「经典」保持分段按钮。
 */
@Composable
private fun StyleOptions(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    if (LocalJrxStyle.current == JrxStyle.JIASHU) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button) { onSelect(i) }
                        .padding(4.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier.height(2.dp).width(30.dp).background(
                            if (on) LocalJrxColors.current.seal
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, labels.size)
                ) { Text(label) }
            }
        }
    }
}
