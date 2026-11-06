package io.github.xiaohaoz1.jiarenxia.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxStyle

/*
 * 「家书」改版（docs/DEVELOPMENT_PLAN.md 2.22）的纸面元素：双线、页标题、朱砂印、顶栏文字入口。
 */

/** 页标题放大的上限：系统字号 × App 字号超过它时标题停住，正文照常放大。 */
const val PAGE_TITLE_MAX_SCALE = 1.3f

/** 页头下的双线：两条 1dp 墨线、间隔 3dp、70% 不透明度。 */
@Composable
fun DoubleRule(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink))
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(ink))
    }
}

/**
 * 页标题：「家书」一级页 34sp、二级页（[secondary]）28sp，700；
 * 经典沿用改版前的 headlineSmall（22sp/600）。
 * 字号放大超过 [PAGE_TITLE_MAX_SCALE] 倍时「家书」标题按上限显示，避免大标题把内容挤出屏幕。
 */
@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
    secondary: Boolean = false
) {
    if (LocalJrxStyle.current == JrxStyle.JIASHU) {
        val scale = LocalDensity.current.fontScale
        val base = if (secondary) 28f else 34f
        // 实际显示 = base × min(fontScale, 上限)；fontSize 会再乘一次 fontScale，这里先除掉超出部分
        val size = if (scale > PAGE_TITLE_MAX_SCALE) base * PAGE_TITLE_MAX_SCALE / scale else base
        Text(
            text,
            fontSize = size.sp,
            lineHeight = (size * 1.25f).sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier.semantics { heading() }
        )
    } else {
        Text(
            text,
            style = MaterialTheme.typography.headlineSmall,
            modifier = modifier.semantics { heading() }
        )
    }
}

/** 朱砂方印：一两个字，旋转 -5°，如页头的“家”、底部的“侠”。 */
@Composable
fun SealMark(text: String, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    val jrx = LocalJrxColors.current
    Box(
        modifier = modifier.size(size).rotate(-5f)
            .clip(MaterialTheme.shapes.small)
            .background(jrx.seal),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = jrx.onSeal,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.56f).sp
        )
    }
}

/**
 * 顶栏文字入口（查找 / 添加 / 新建）：18dp 图标 + 14sp 墨青粗体字。
 * 不单用图标表意——不熟悉图标的家人也认得出。
 */
@Composable
fun TopTextAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}

/** 区块小标签（如「今日惦记」、设置分组名）：朱砂 12sp 粗体、字距加宽。 */
@Composable
fun SealLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = LocalJrxColors.current.seal,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 3.sp,
        modifier = modifier.semantics { heading() }
    )
}

/** 一级页页头的标题区：标题（可带右侧印章等）+ 可选摘要 + 双线。 */
@Composable
fun PageTitleBlock(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    titleTrailing: (@Composable () -> Unit)? = null
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PageTitle(title)
            if (titleTrailing != null) {
                Spacer(Modifier.width(10.dp))
                titleTrailing()
            }
        }
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        DoubleRule()
    }
}
