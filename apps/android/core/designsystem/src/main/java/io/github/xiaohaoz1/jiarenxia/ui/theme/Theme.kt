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

private val LightColors = lightColorScheme(
    primary = JrxPrimaryLight,
    onPrimary = JrxOnPrimaryLight,
    primaryContainer = JrxPrimaryContainerLight,
    onPrimaryContainer = JrxOnPrimaryContainerLight,
    secondary = JrxSecondaryLight,
    onSecondary = JrxOnPrimaryLight,
    secondaryContainer = JrxSecondaryContainerLight,
    onSecondaryContainer = JrxOnSecondaryContainerLight,
    tertiary = JrxTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = JrxTertiaryContainerLight,
    onTertiaryContainer = JrxOnTertiaryContainerLight,
    error = JrxErrorLight,
    onError = Color.White,
    errorContainer = JrxErrorContainerLight,
    onErrorContainer = JrxOnErrorContainerLight,
    background = JrxPaperLight,
    onBackground = JrxInkLight,
    surface = JrxPaperLight,
    onSurface = JrxInkLight,
    surfaceContainerLowest = JrxSurfaceContainerLowestLight,
    surfaceContainerLow = JrxSurfaceContainerLowLight,
    surfaceContainer = JrxSurfaceContainerLight,
    surfaceContainerHigh = JrxSurfaceContainerHighLight,
    surfaceContainerHighest = JrxSurfaceContainerHighestLight,
    surfaceVariant = JrxSurfaceVariantLight,
    onSurfaceVariant = JrxOnSurfaceVariantLight,
    outline = JrxOutlineLight,
    outlineVariant = JrxOutlineVariantLight,
    surfaceTint = JrxPrimaryLight
)

private val DarkColors = darkColorScheme(
    primary = JrxPrimaryDark,
    onPrimary = JrxOnPrimaryDark,
    primaryContainer = JrxPrimaryContainerDark,
    onPrimaryContainer = JrxOnPrimaryContainerDark,
    secondary = JrxSecondaryDark,
    onSecondary = JrxOnSecondaryDark,
    secondaryContainer = JrxSecondaryContainerDark,
    onSecondaryContainer = JrxOnSecondaryContainerDark,
    tertiary = JrxTertiaryDark,
    onTertiary = JrxOnTertiaryDark,
    tertiaryContainer = JrxTertiaryContainerDark,
    onTertiaryContainer = JrxOnTertiaryContainerDark,
    error = JrxErrorDark,
    onError = Color(0xFF601410),
    errorContainer = JrxErrorContainerDark,
    onErrorContainer = JrxOnErrorContainerDark,
    background = JrxPaperDark,
    onBackground = JrxInkDark,
    surface = JrxPaperDark,
    onSurface = JrxInkDark,
    surfaceContainerLowest = JrxSurfaceContainerLowestDark,
    surfaceContainerLow = JrxSurfaceContainerLowDark,
    surfaceContainer = JrxSurfaceContainerDark,
    surfaceContainerHigh = JrxSurfaceContainerHighDark,
    surfaceContainerHighest = JrxSurfaceContainerHighestDark,
    surfaceVariant = JrxSurfaceVariantDark,
    onSurfaceVariant = JrxOnSurfaceVariantDark,
    outline = JrxOutlineDark,
    outlineVariant = JrxOutlineVariantDark,
    surfaceTint = JrxPrimaryDark
)

/** 圆角：「家书」改版收小为 4/6/8/12dp（原 10/14/14/20），按钮与印章都是小方角。 */
private val AppShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

/**
 * 品牌扩展色（随亮暗切换）：朱砂、亲属侧别、已故/语义色。
 */
data class JrxColors(
    /** 朱砂：印章、今天、生日倒计时、逾期、核心称谓框。 */
    val seal: Color,
    /** 朱砂底上的字色。 */
    val onSeal: Color,
    /** 称谓细框描边（比 [seal] 浅一档）。 */
    val sealLine: Color,
    /** 朱砂浅底：关系图里“我”的卡片底色。 */
    val sealContainer: Color,
    /**
     * 父系（父亲及父系祖辈）。侧别三色保持改版前的色相（父系红、母系绿、配偶侧赭），
     * 不跟主色变成墨青：关系图版式不改，读图习惯也不改。
     */
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

val LightJrxColors = JrxColors(
    seal = JrxSealLight,
    onSeal = JrxOnSealLight,
    sealLine = JrxSealLineLight,
    sealContainer = Color(0xFFF5E1DA),
    paternal = JrxSealLight,
    maternal = Color(0xFF57755B),
    spouseSide = Color(0xFF9A5B2E),
    deceased = Color(0xFF8E8578),
    pageHeaderBackground = JrxPaperLight,
    success = Color(0xFF2F6B4F),
    warning = Color(0xFFA86A00)
)

val DarkJrxColors = JrxColors(
    seal = JrxSealDark,
    onSeal = JrxOnSealDark,
    sealLine = JrxSealLineDark,
    sealContainer = Color(0xFF3A2420),
    paternal = JrxSealDark,
    maternal = Color(0xFFA6D2A2),
    spouseSide = Color(0xFFFFB688),
    deceased = Color(0xFF8A847A),
    pageHeaderBackground = JrxPaperDark,
    success = Color(0xFF86C9A0),
    warning = Color(0xFFFFB95C)
)

val LocalJrxColors = staticCompositionLocalOf { LightJrxColors }

/**
 * 主题（明暗已判定）：「家书」固定配色，不使用动态取色。
 */
@Composable
fun JrxTheme(
    dark: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalJrxColors provides if (dark) DarkJrxColors else LightJrxColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
