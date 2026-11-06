package io.github.xiaohaoz1.jiarenxia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode

/** 主题是否实际处于深色（含跟随系统）。 */
@Composable
fun isJrxDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * 应用主题：固定配色，不使用动态取色。
 * [mode] 管深浅（浅色/深色/跟随系统），[style] 管视觉语言（经典/家书，默认家书）。
 */
@Composable
fun JiaRenXiaTheme(
    mode: ThemeMode = ThemeMode.LIGHT,
    style: JrxStyle = JrxStyle.JIASHU,
    content: @Composable () -> Unit
) {
    JrxTheme(dark = isJrxDarkTheme(mode), style = style, content = content)
}
