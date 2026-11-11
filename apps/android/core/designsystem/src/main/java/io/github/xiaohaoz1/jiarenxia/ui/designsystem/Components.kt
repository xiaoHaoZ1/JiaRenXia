package io.github.xiaohaoz1.jiarenxia.ui.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxStyle

/** 「家书」主按钮用小方角（shapes.small）；经典沿用传入的 [classic]（默认 Button 胶囊）。 */
@Composable
fun jrxActionShape(classic: Shape? = null): Shape =
    if (LocalJrxStyle.current == JrxStyle.JIASHU) MaterialTheme.shapes.small
    else classic ?: ButtonDefaults.shape

/**
 * 卡片：实底 + 发丝描边，16dp 内边距。圆角与底色随风格 token——
 * 经典纯白卡 14dp，「家书」浅纸卡 6dp（2.22：一级页逐步改成无卡片的行，二级页继续用它）。
 */
@Composable
fun JrxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** 卡片底色覆盖（默认 surfaceContainerLow）；推荐卡等强调场景用。 */
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(
        containerColor = containerColor
            ?: MaterialTheme.colorScheme.surfaceContainerLow
    )
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val none = CardDefaults.cardElevation(defaultElevation = 0.dp)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            elevation = none,
            border = border
        ) { Column(Modifier.padding(16.dp), content = content) }
    } else {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.medium,
            colors = colors,
            elevation = none,
            border = border
        ) { Column(Modifier.padding(16.dp), content = content) }
    }
}

/**
 * 区块标题：左标题 + 可选淡色计数（「家书」“至亲 8 位”样式，[countLabel]）+
 * 可选右侧文字按钮。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    countLabel: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (LocalJrxStyle.current == JrxStyle.JIASHU) {
            // 区块标题标记为 heading，TalkBack 可按标题导航；「家书」20sp/700
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            if (countLabel != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    countLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.weight(1f))
        } else {
            // 经典 16sp/600
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
        }
        if (actionLabel != null) {
            TextButton(onClick = { onAction?.invoke() }) { Text(actionLabel) }
        }
    }
}

/** 称谓/标签细框色调。 */
enum class ChipTone { CORE, OTHER, MUTED }

/**
 * 称谓/状态小标签。「家书」= 细框印（透明底 + 1dp 描边 + 2dp 圆角）；
 * 经典 = 彩色小胶囊（实底无描边）。CORE 是核心称谓/主号码，OTHER 次要，MUTED 最淡。
 */
@Composable
fun TitleChip(text: String, tone: ChipTone = ChipTone.CORE) {
    if (LocalJrxStyle.current == JrxStyle.JIASHU) {
        val jrx = LocalJrxColors.current
        val (line, fg) = when (tone) {
            ChipTone.CORE -> jrx.sealLine to jrx.seal
            ChipTone.OTHER -> MaterialTheme.colorScheme.outline to
                MaterialTheme.colorScheme.onSurfaceVariant
            ChipTone.MUTED -> MaterialTheme.colorScheme.outlineVariant to
                MaterialTheme.colorScheme.outline
        }
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(1.dp, line)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    } else {
        val (bg, fg) = when (tone) {
            ChipTone.CORE -> MaterialTheme.colorScheme.primaryContainer to
                MaterialTheme.colorScheme.onPrimaryContainer
            ChipTone.OTHER -> MaterialTheme.colorScheme.secondaryContainer to
                MaterialTheme.colorScheme.onSecondaryContainer
            ChipTone.MUTED -> MaterialTheme.colorScheme.surfaceVariant to
                MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(color = bg, shape = MaterialTheme.shapes.small) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

/** 图标 + 标签 + 值 的信息行。 */
@Composable
fun IconInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 圆形浅色快捷操作按钮（图标 + 下方文字）。 */
@Composable
fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (enabled) 1f else 0.38f)
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 空状态：可选插画 + 标题 + 说明 + 可选按钮；文字多行居中并保持稳定左右留白。 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    illustration: Painter? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (illustration != null) {
            Image(painter = illustration, contentDescription = null, modifier = Modifier.size(140.dp))
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(shape = jrxActionShape(), onClick = onAction) { Text(actionLabel) }
        }
    }
}
