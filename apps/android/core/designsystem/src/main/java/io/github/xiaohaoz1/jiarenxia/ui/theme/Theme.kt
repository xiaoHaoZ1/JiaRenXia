package io.github.xiaohaoz1.jiarenxia.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 主题风格：经典「温馨暖色」与「家书」并存（用户可在 外观与字号 里切换）。
 * 风格只决定视觉语言（配色/字阶/圆角与个别组件结构），深浅明暗仍由 ThemeMode 管。
 */
enum class JrxStyle { CLASSIC, JIASHU }

// ---------------- 经典「温馨暖色」 ----------------

private val ClassicLightColors = lightColorScheme(
    primary = ClPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = ClPrimaryContainerLight,
    onPrimaryContainer = ClOnPrimaryContainerLight,
    secondary = ClSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = ClSecondaryContainerLight,
    onSecondaryContainer = ClOnSecondaryContainerLight,
    tertiary = ClTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = ClTertiaryContainerLight,
    onTertiaryContainer = ClOnTertiaryContainerLight,
    error = ClErrorLight,
    onError = Color.White,
    errorContainer = ClErrorContainerLight,
    background = ClBackgroundLight,
    onBackground = ClOnBackgroundLight,
    surface = ClBackgroundLight,
    onSurface = ClOnBackgroundLight,
    surfaceContainerLowest = ClSurfaceContainerLowestLight,
    surfaceContainerLow = ClSurfaceContainerLowLight,
    surfaceContainer = ClSurfaceContainerLight,
    surfaceContainerHigh = ClSurfaceContainerHighLight,
    surfaceContainerHighest = ClSurfaceContainerHighestLight,
    surfaceVariant = ClSurfaceVariantLight,
    onSurfaceVariant = ClOnSurfaceVariantLight,
    outline = ClOutlineLight,
    outlineVariant = ClOutlineVariantLight,
    surfaceTint = ClPrimaryLight
)

private val ClassicDarkColors = darkColorScheme(
    primary = ClPrimaryDark,
    onPrimary = ClOnPrimaryDark,
    primaryContainer = ClPrimaryContainerDark,
    onPrimaryContainer = ClOnPrimaryContainerLight,
    secondary = ClSecondaryDark,
    onSecondary = ClOnSecondaryDark,
    secondaryContainer = ClSecondaryContainerDark,
    onSecondaryContainer = ClOnSecondaryContainerLight,
    tertiary = ClTertiaryDark,
    onTertiary = ClOnTertiaryDark,
    tertiaryContainer = ClTertiaryContainerDark,
    onTertiaryContainer = ClOnTertiaryContainerLight,
    error = ClErrorDark,
    onError = Color(0xFF690005),
    errorContainer = ClErrorContainerDark,
    background = ClBackgroundDark,
    onBackground = ClOnBackgroundDark,
    surface = ClBackgroundDark,
    onSurface = ClOnBackgroundDark,
    surfaceContainerLowest = ClSurfaceContainerLowestDark,
    surfaceContainerLow = ClSurfaceContainerLowDark,
    surfaceContainer = ClSurfaceContainerDark,
    surfaceContainerHigh = ClSurfaceContainerHighDark,
    surfaceContainerHighest = ClSurfaceContainerHighestDark,
    surfaceVariant = ClSurfaceVariantDark,
    onSurfaceVariant = ClOnSurfaceVariantDark,
    outline = ClOutlineDark,
    outlineVariant = ClOutlineVariantDark,
    surfaceTint = ClPrimaryDark
)

/** 经典圆角：2026-10-09 改版统一收到 14dp 一档。 */
private val ClassicShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

// ---------------- 「家书」 ----------------

private val JiashuLightColors = lightColorScheme(
    primary = JsPrimaryLight,
    onPrimary = JsOnPrimaryLight,
    primaryContainer = JsPrimaryContainerLight,
    onPrimaryContainer = JsOnPrimaryContainerLight,
    secondary = JsSecondaryLight,
    onSecondary = JsOnPrimaryLight,
    secondaryContainer = JsSecondaryContainerLight,
    onSecondaryContainer = JsOnSecondaryContainerLight,
    tertiary = JsTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = JsTertiaryContainerLight,
    onTertiaryContainer = JsOnTertiaryContainerLight,
    error = JsErrorLight,
    onError = Color.White,
    errorContainer = JsErrorContainerLight,
    onErrorContainer = JsOnErrorContainerLight,
    background = JsPaperLight,
    onBackground = JsInkLight,
    surface = JsPaperLight,
    onSurface = JsInkLight,
    surfaceContainerLowest = JsSurfaceContainerLowestLight,
    surfaceContainerLow = JsSurfaceContainerLowLight,
    surfaceContainer = JsSurfaceContainerLight,
    surfaceContainerHigh = JsSurfaceContainerHighLight,
    surfaceContainerHighest = JsSurfaceContainerHighestLight,
    surfaceVariant = JsSurfaceVariantLight,
    onSurfaceVariant = JsOnSurfaceVariantLight,
    outline = JsOutlineLight,
    outlineVariant = JsOutlineVariantLight,
    surfaceTint = JsPrimaryLight
)

private val JiashuDarkColors = darkColorScheme(
    primary = JsPrimaryDark,
    onPrimary = JsOnPrimaryDark,
    primaryContainer = JsPrimaryContainerDark,
    onPrimaryContainer = JsOnPrimaryContainerDark,
    secondary = JsSecondaryDark,
    onSecondary = JsOnSecondaryDark,
    secondaryContainer = JsSecondaryContainerDark,
    onSecondaryContainer = JsOnSecondaryContainerDark,
    tertiary = JsTertiaryDark,
    onTertiary = JsOnTertiaryDark,
    tertiaryContainer = JsTertiaryContainerDark,
    onTertiaryContainer = JsOnTertiaryContainerDark,
    error = JsErrorDark,
    onError = Color(0xFF601410),
    errorContainer = JsErrorContainerDark,
    onErrorContainer = JsOnErrorContainerDark,
    background = JsPaperDark,
    onBackground = JsInkDark,
    surface = JsPaperDark,
    onSurface = JsInkDark,
    surfaceContainerLowest = JsSurfaceContainerLowestDark,
    surfaceContainerLow = JsSurfaceContainerLowDark,
    surfaceContainer = JsSurfaceContainerDark,
    surfaceContainerHigh = JsSurfaceContainerHighDark,
    surfaceContainerHighest = JsSurfaceContainerHighestDark,
    surfaceVariant = JsSurfaceVariantDark,
    onSurfaceVariant = JsOnSurfaceVariantDark,
    outline = JsOutlineDark,
    outlineVariant = JsOutlineVariantDark,
    surfaceTint = JsPrimaryDark
)

/** 「家书」圆角：收小为 4/6/8/12dp，按钮与印章都是小方角。 */
private val JiashuShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

// ---------------- 品牌扩展色（两套风格各自的取值） ----------------

/**
 * 品牌扩展色（随亮暗、风格切换）：朱砂/强调印色、亲属侧别、已故/语义色。
 *
 * [seal] 一系在经典风格里映射回经典主色（砖红）——调用处读 jrx.seal 时
 * 两种风格各自得到自己的强调色，无需在颜色上分支；结构性差异
 * （细框/印章等）另用 [LocalJrxStyle] 分支。
 */
data class JrxColors(
    /** 印章/喜事/“今天”强调色：家书为朱砂，经典映射为砖红主色。 */
    val seal: Color,
    /** [seal] 底上的字色。 */
    val onSeal: Color,
    /** 称谓细框描边（比 [seal] 浅一档）；经典不画细框，映射为主色占位。 */
    val sealLine: Color,
    /** 强调浅底：关系图里“我”的卡片底色（家书朱砂浅底，经典主色容器）。 */
    val sealContainer: Color,
    /** 父系（父亲及父系祖辈）。 */
    val paternal: Color,
    /** 母系（母亲及母系祖辈）。 */
    val maternal: Color,
    /** 配偶侧（配偶与配偶父母）。 */
    val spouseSide: Color,
    /** 已故标记色。 */
    val deceased: Color,
    /** 页头底色（与背景同色）。 */
    val pageHeaderBackground: Color,
    val success: Color,
    val warning: Color
)

val ClassicLightJrxColors = JrxColors(
    seal = ClPrimaryLight,
    onSeal = Color.White,
    sealLine = ClPrimaryLight,
    sealContainer = ClPrimaryContainerLight,
    paternal = ClPrimaryLight,
    maternal = ClTertiaryLight,
    spouseSide = Color(0xFF9A5B2E),
    deceased = Color(0xFF8A817D),
    pageHeaderBackground = ClBackgroundLight,
    success = Color(0xFF2F6B4F),
    warning = Color(0xFFB26A00)
)

val ClassicDarkJrxColors = JrxColors(
    seal = ClPrimaryDark,
    onSeal = ClOnPrimaryDark,
    sealLine = ClPrimaryDark,
    sealContainer = ClPrimaryContainerDark,
    paternal = ClPrimaryDark,
    maternal = Color(0xFFA6D2A2),
    spouseSide = Color(0xFFFFB688),
    deceased = Color(0xFFA39B97),
    pageHeaderBackground = ClBackgroundDark,
    success = Color(0xFF86C9A0),
    warning = Color(0xFFFFB95C)
)

val JiashuLightJrxColors = JrxColors(
    seal = JsSealLight,
    onSeal = JsOnSealLight,
    sealLine = JsSealLineLight,
    sealContainer = Color(0xFFF5E1DA),
    // 侧别三色保持改版前的色相（父系红、母系绿、配偶侧赭），不跟主色变成墨青
    paternal = JsSealLight,
    maternal = Color(0xFF57755B),
    spouseSide = Color(0xFF9A5B2E),
    deceased = Color(0xFF8E8578),
    pageHeaderBackground = JsPaperLight,
    success = Color(0xFF2F6B4F),
    warning = Color(0xFFA86A00)
)

val JiashuDarkJrxColors = JrxColors(
    seal = JsSealDark,
    onSeal = JsOnSealDark,
    sealLine = JsSealLineDark,
    sealContainer = Color(0xFF3A2420),
    paternal = JsSealDark,
    maternal = Color(0xFFA6D2A2),
    spouseSide = Color(0xFFFFB688),
    deceased = Color(0xFF8A847A),
    pageHeaderBackground = JsPaperDark,
    success = Color(0xFF86C9A0),
    warning = Color(0xFFFFB95C)
)

val LocalJrxColors = staticCompositionLocalOf { JiashuLightJrxColors }

/** 当前生效的主题风格；结构性差异（印章、细框、底栏版式）用它分支。 */
val LocalJrxStyle = staticCompositionLocalOf { JrxStyle.JIASHU }

/** 当前是否「家书」风格。 */
val isJiashuTheme: Boolean
    @Composable get() = LocalJrxStyle.current == JrxStyle.JIASHU

/**
 * 主题（明暗已判定）：固定配色，不使用动态取色。
 * [style] 决定视觉语言（配色/字阶/圆角），[dark] 决定该风格下的亮暗档。
 */
@Composable
fun JrxTheme(
    dark: Boolean,
    style: JrxStyle = JrxStyle.JIASHU,
    content: @Composable () -> Unit
) {
    val colorScheme = when (style) {
        JrxStyle.CLASSIC -> if (dark) ClassicDarkColors else ClassicLightColors
        JrxStyle.JIASHU -> if (dark) JiashuDarkColors else JiashuLightColors
    }
    val jrx = when (style) {
        JrxStyle.CLASSIC -> if (dark) ClassicDarkJrxColors else ClassicLightJrxColors
        JrxStyle.JIASHU -> if (dark) JiashuDarkJrxColors else JiashuLightJrxColors
    }
    CompositionLocalProvider(
        LocalJrxColors provides jrx,
        LocalJrxStyle provides style
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (style == JrxStyle.JIASHU) JiashuTypography else ClassicTypography,
            shapes = if (style == JrxStyle.JIASHU) JiashuShapes else ClassicShapes,
            content = content
        )
    }
}
