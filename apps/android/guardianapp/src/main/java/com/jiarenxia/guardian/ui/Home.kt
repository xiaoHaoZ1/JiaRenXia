package com.jiarenxia.guardian.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jiarenxia.guardian.BuildConfig
import io.github.xiaohaoz1.jiarenxia.guardian.ui.GuardianHomeOptions
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors

/**
 * 守护侠首页定制：标题“守护侠”（守护侠没有家庭数据，联系人只有通讯录/手动输入），
 * 页头下挂测试模式提示卡，设置页末尾加隐私说明入口与版本号。
 * MainActivity 与截图测试共用此工厂。
 */
fun standaloneHomeOptions(onPrivacy: () -> Unit) = GuardianHomeOptions(
    title = "守护侠",
    promptCard = { GuardianPromptCard(Modifier.padding(vertical = 6.dp)) },
    footer = { StandaloneFooter(onPrivacy = onPrivacy) }
)

/** 页头下的醒目提示卡：先走测试模式验证响铃拨号（守护已移出家人侠，C3）。 */
@Composable
fun GuardianPromptCard(modifier: Modifier = Modifier) {
    val warning = LocalJrxColors.current.warning
    Surface(
        color = warning.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, warning.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Shield, contentDescription = null,
                    tint = warning, modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "装好后请先用测试模式走一遍，确认能响能拨。",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = warning
                )
            }
        }
    }
}

/** 设置页页尾：隐私说明入口 + 版本号。 */
@Composable
fun StandaloneFooter(onPrivacy: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPrivacy)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    "隐私说明",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "版本 " + BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}
