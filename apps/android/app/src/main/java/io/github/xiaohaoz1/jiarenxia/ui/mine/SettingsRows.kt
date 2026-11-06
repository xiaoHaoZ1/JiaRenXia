package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.DoubleRule
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.SealLabel
import io.github.xiaohaoz1.jiarenxia.ui.theme.JrxStyle
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxStyle

/**
 * 设置类页面的共用行与骨架（2026-10-09 界面改版）。
 *
 * 规则：一级页只放入口，开关与详细设置都在二级页；行内不再用 40dp 彩色图标圆，
 * 图标用 22dp 线性主色图标，层次靠描边和留白。
 */

/** 二级设置页骨架：返回 + 标题 + 可滚动内容（内容自带 16dp 左右留白）。
 *  「家书」下标题单独一行（28sp 粗体 + 双线），返回键在顶行。
 *  [bottomBar] 提供时钉在页面底部不随内容滚动（如固定操作栏）。 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val jiashu = LocalJrxStyle.current == JrxStyle.JIASHU
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                if (!jiashu) {
                    Spacer(Modifier.width(2.dp))
                    Text(
                        title, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            }
            if (jiashu) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(8.dp))
                    DoubleRule()
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                content = content
            )
            bottomBar?.invoke()
        }
    }
}

/**
 * 分组：经典 = 灰色小标题 + 一张卡片；家书 = 朱砂小标签 + 裸行（发丝线分隔）。
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val jiashu = LocalJrxStyle.current == JrxStyle.JIASHU
    if (jiashu) {
        if (title != null) {
            SealLabel(title, Modifier.padding(top = 18.dp, bottom = 8.dp))
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Column(Modifier.fillMaxWidth(), content = content)
    } else {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
                    .semantics { heading() }
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
        JrxCard(Modifier.fillMaxWidth(), content = content)
    }
}

/** 行间分隔线：经典是卡片内缩进分隔；家书是行下发丝线（行的留白已够）。 */
@Composable
fun RowDivider() {
    val jiashu = LocalJrxStyle.current == JrxStyle.JIASHU
    HorizontalDivider(
        if (jiashu) Modifier else Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/**
 * 入口行：图标 + 标题（可带副标题）+ 右侧值 + 箭头。
 * [arrow] 为 false 时不画「>」——箭头表示进子页，原地动作（如检查更新）不该有。
 * [danger] 为 true 时标题与图标用主色强调（清空数据等破坏性操作）。
 */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    /** 右侧小角标（如「新版」），errorContainer 底色醒目展示。 */
    badge: String? = null,
    arrow: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (danger) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    // 「家书」行不带图标、行距更开（发丝线分隔名单感）
    val jiashu = LocalJrxStyle.current == JrxStyle.JIASHU
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = if (jiashu) 14.dp else 4.dp)
    ) {
        if (!jiashu) {
            Icon(icon, contentDescription = null, tint = tint,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyLarge,
                color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (badge != null) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        if (value != null) {
            Text(
                value, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
        }
        if (arrow) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 开关行：标题（可带副说明）+ 右侧开关；整行可切换，TalkBack 读作开关。 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    /** 开关右侧之前的附加内容（如提醒时刻）。 */
    trailing: (@Composable () -> Unit)? = null
) {
    val jiashu = LocalJrxStyle.current == JrxStyle.JIASHU
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = if (jiashu) 14.dp else 4.dp)
    ) {
        if (icon != null && !jiashu) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** 页面底部或分组下方的说明小字。 */
@Composable
fun SettingsHint(text: String, center: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start
    ) {
        Text(
            text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
