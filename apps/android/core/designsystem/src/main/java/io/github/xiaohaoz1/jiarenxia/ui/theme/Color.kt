package io.github.xiaohaoz1.jiarenxia.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 两套配色并存（主题切换）：Cl* = 经典「温馨暖色」，Js* = 「家书」（2.22）。
 * 色值定义只在本文件与 Theme.kt 组装 scheme 时使用，界面一律走
 * MaterialTheme.colorScheme / LocalJrxColors，不直接引用这些常量。
 */

/**
 * 经典「温馨暖色」— 浅色方案（2026-10-09 改版基调）。
 * 保留品牌砖红 #B4473C 作为唯一强调色，其余去饱和——
 * 暖白底 + 纯白卡 + 发丝描边，对比交给描边与留白。
 */
val ClPrimaryLight = Color(0xFFB4473C)
val ClPrimaryContainerLight = Color(0xFFFBEAE7)
val ClOnPrimaryContainerLight = Color(0xFF7E2A22)
val ClSecondaryLight = Color(0xFF7D5A44)
val ClSecondaryContainerLight = Color(0xFFF6EDE4)
val ClOnSecondaryContainerLight = Color(0xFF53433E)
val ClTertiaryLight = Color(0xFF57755B)
val ClTertiaryContainerLight = Color(0xFFF1F6F2)
val ClOnTertiaryContainerLight = Color(0xFF1C3B2B)
val ClBackgroundLight = Color(0xFFFBF8F5)
val ClOnBackgroundLight = Color(0xFF1F1A17)
/** 经典卡片：纯白（JrxCard 用 surfaceContainerLow）。 */
val ClSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val ClSurfaceContainerLowLight = Color(0xFFFFFFFF)
val ClSurfaceContainerLight = Color(0xFFF7F2EE)
val ClSurfaceContainerHighLight = Color(0xFFF2EBE6)
val ClSurfaceContainerHighestLight = Color(0xFFECE4DE)
val ClSurfaceVariantLight = Color(0xFFF5EFEA)
val ClOnSurfaceVariantLight = Color(0xFF7A6E68)
val ClOutlineLight = Color(0xFFA69A94)
/** 经典发丝描边：卡片与分隔线统一用它。 */
val ClOutlineVariantLight = Color(0xFFEFE7E1)
val ClErrorLight = Color(0xFFBA1A1A)
val ClErrorContainerLight = Color(0xFFFBEAE7)

/** 经典深色：暖棕底（非中性灰），与浅色同样去饱和、靠描边分层。 */
val ClPrimaryDark = Color(0xFFFFB4A7)
val ClOnPrimaryDark = Color(0xFF690F09)
val ClPrimaryContainerDark = Color(0xFF3A2420)
val ClSecondaryDark = Color(0xFFEDBFA2)
val ClOnSecondaryDark = Color(0xFF452A19)
val ClSecondaryContainerDark = Color(0xFF2E2420)
val ClTertiaryDark = Color(0xFFBDD3B9)
val ClOnTertiaryDark = Color(0xFF283C27)
val ClTertiaryContainerDark = Color(0xFF24322A)
val ClBackgroundDark = Color(0xFF17120F)
val ClOnBackgroundDark = Color(0xFFF1DFD9)
val ClSurfaceContainerLowestDark = Color(0xFF120E0C)
/** 经典卡片（深色）。 */
val ClSurfaceContainerLowDark = Color(0xFF221A16)
val ClSurfaceContainerDark = Color(0xFF281F1B)
val ClSurfaceContainerHighDark = Color(0xFF322722)
val ClSurfaceContainerHighestDark = Color(0xFF3C302B)
val ClSurfaceVariantDark = Color(0xFF3A2E29)
val ClOnSurfaceVariantDark = Color(0xFFBCAAA3)
val ClOutlineDark = Color(0xFF8C7B74)
/** 经典发丝描边（深色）。 */
val ClOutlineVariantDark = Color(0xFF342A25)
val ClErrorDark = Color(0xFFFFB4AB)
val ClErrorContainerDark = Color(0xFF3A2420)

/**
 * 「家书」配色 — 浅色方案（默认），见 docs/DEVELOPMENT_PLAN.md 2.22。
 *
 * 纸色底 + 墨色字；墨青（primary）管可点的操作，朱砂（[JrxColors.seal]）管喜事、
 * 今天与印章，藤黄（tertiary）管纪念日。层次靠发丝线与留白，不靠白卡堆叠。
 */
val JsPaperLight = Color(0xFFF3EDE2)
val JsInkLight = Color(0xFF1F2A29)
val JsPrimaryLight = Color(0xFF0C4247)
val JsOnPrimaryLight = Color(0xFFF8F4EC)
val JsPrimaryContainerLight = Color(0xFFDCE7E4)
val JsOnPrimaryContainerLight = Color(0xFF0C4247)
val JsSecondaryLight = Color(0xFF6B6357)
val JsSecondaryContainerLight = Color(0xFFE6DDCC)
val JsOnSecondaryContainerLight = Color(0xFF1F2A29)
val JsTertiaryLight = Color(0xFF8C6A10)
val JsTertiaryContainerLight = Color(0xFFF1E6C8)
val JsOnTertiaryContainerLight = Color(0xFF4A3800)
val JsSurfaceContainerLowestLight = Color(0xFFFBF8F2)
/** 卡片（JrxCard 用 surfaceContainerLow）：比纸色浅一档。 */
val JsSurfaceContainerLowLight = Color(0xFFF8F4EC)
val JsSurfaceContainerLight = Color(0xFFEEE7DA)
/** 弹框与日期/时间选择器：与卡片同一张浅纸。 */
val JsSurfaceContainerHighLight = Color(0xFFF8F4EC)
val JsSurfaceContainerHighestLight = Color(0xFFE6DDCC)
val JsSurfaceVariantLight = Color(0xFFE6DDCC)
/** 次要文字：压深一档，在纸色上保证 4.5:1。 */
val JsOnSurfaceVariantLight = Color(0xFF6B6357)
val JsOutlineLight = Color(0xFF8E8578)
/** 发丝线：行间分隔与卡片细框统一用它。 */
val JsOutlineVariantLight = Color(0xFFD9CFBE)
val JsErrorLight = Color(0xFFB3261E)
val JsErrorContainerLight = Color(0xFFF6DAD5)
val JsOnErrorContainerLight = Color(0xFF7A1E14)
val JsSealLight = Color(0xFFB23E2D)
val JsOnSealLight = Color(0xFFF8EDE0)
val JsSealLineLight = Color(0xFFC9705F)

/** 「家书」深色：墨色纸底、米白字；朱砂与墨青各调亮一档，版式与浅色一致。 */
val JsPaperDark = Color(0xFF191C1B)
val JsInkDark = Color(0xFFECE4D6)
val JsPrimaryDark = Color(0xFF8CC4BF)
val JsOnPrimaryDark = Color(0xFF0B2A2D)
val JsPrimaryContainerDark = Color(0xFF1F3A3C)
val JsOnPrimaryContainerDark = Color(0xFFCDE6E2)
val JsSecondaryDark = Color(0xFFA39B8D)
val JsOnSecondaryDark = Color(0xFF191C1B)
val JsSecondaryContainerDark = Color(0xFF2A2E2C)
val JsOnSecondaryContainerDark = Color(0xFFECE4D6)
val JsTertiaryDark = Color(0xFFD8B04A)
val JsOnTertiaryDark = Color(0xFF2E2400)
val JsTertiaryContainerDark = Color(0xFF3A3218)
val JsOnTertiaryContainerDark = Color(0xFFF1E2B5)
val JsSurfaceContainerLowestDark = Color(0xFF141716)
/** 卡片（深色）。 */
val JsSurfaceContainerLowDark = Color(0xFF1E2120)
val JsSurfaceContainerDark = Color(0xFF232726)
val JsSurfaceContainerHighDark = Color(0xFF262A29)
val JsSurfaceContainerHighestDark = Color(0xFF2E3331)
val JsSurfaceVariantDark = Color(0xFF2A2E2C)
val JsOnSurfaceVariantDark = Color(0xFFA39B8D)
val JsOutlineDark = Color(0xFF6F6A61)
/** 发丝线（深色）。 */
val JsOutlineVariantDark = Color(0xFF343937)
val JsErrorDark = Color(0xFFF2B8B5)
val JsErrorContainerDark = Color(0xFF4A1F1A)
val JsOnErrorContainerDark = Color(0xFFF6DAD5)
val JsSealDark = Color(0xFFE07B63)
val JsOnSealDark = Color(0xFF191C1B)
val JsSealLineDark = Color(0xFFB9614F)
