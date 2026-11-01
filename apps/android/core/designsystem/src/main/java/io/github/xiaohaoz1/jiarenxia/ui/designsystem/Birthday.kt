package io.github.xiaohaoz1.jiarenxia.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors

/**
 * 日历方块：52×58dp，上带“N月”，下显日号。
 * highlight 时顶带用朱砂（今天/重点生日）。
 */
@Composable
fun DateTile(month: Int, day: Int, highlight: Boolean = false) {
    val jrx = LocalJrxColors.current
    val bandBg = if (highlight) jrx.seal
    else MaterialTheme.colorScheme.secondaryContainer
    val bandFg = if (highlight) jrx.onSeal
    else MaterialTheme.colorScheme.onSecondaryContainer
    Column(
        modifier = Modifier.width(52.dp).height(58.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(20.dp).background(bandBg),
            contentAlignment = Alignment.Center
        ) {
            Text(month.toString() + "月", style = MaterialTheme.typography.labelSmall, color = bandFg)
        }
        Box(modifier = Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) {
            Text(day.toString(), style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** 生日倒计时印：0→今天（朱砂实心），1→明天、其他→N天后（朱砂细框）。 */
@Composable
fun CountdownBadge(days: Int) {
    val jrx = LocalJrxColors.current
    val (text, filled) = when (days) {
        0 -> "今天" to true
        1 -> "明天" to false
        else -> days.toString() + "天后" to false
    }
    Surface(
        color = if (filled) jrx.seal else Color.Transparent,
        shape = RoundedCornerShape(2.dp),
        border = if (filled) null else BorderStroke(1.dp, jrx.sealLine)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) jrx.onSeal else jrx.seal,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
