package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebrationKind
import io.github.xiaohaoz1.jiarenxia.ui.common.JrxDatePickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 新生宝宝卡（FAMILY_AFFAIRS 26 第 3 批）：满月 / 百天行（可另设庆祝日）
 * + 「出生后要办的事」入口。位置：快捷操作之后、孕期卡/重要日期之前。
 */
@Composable
fun NewbornCard(
    card: NewbornCardState,
    today: LocalDate,
    onSetCelebration: (BabyCelebrationKind, LocalDate?) -> Unit,
    onOpenChecklist: (String) -> Unit,
    /** 「问一问」入口（§25 方案 C）：标题行右侧。 */
    onAsk: () -> Unit = {}
) {
    var pickKind by remember { mutableStateOf<BabyCelebrationKind?>(null) }
    JrxCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SectionTitle("新生宝宝")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onAsk) { Text("问一问") }
        }
        CelebrationRow(
            label = "满月",
            event = card.fullMonth,
            byBirth = card.fullMonthByBirth,
            overridden = card.fullMonthOverride,
            today = today,
            onClick = { pickKind = BabyCelebrationKind.FULL_MONTH }
        )
        CelebrationRow(
            label = "百天",
            event = card.hundredDays,
            byBirth = card.hundredByBirth,
            overridden = card.hundredOverride,
            today = today,
            onClick = { pickKind = BabyCelebrationKind.HUNDRED_DAYS }
        )
        if (card.postnatalChecklistId != null && card.postnatalTotal > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) {}
                    .clickable { onOpenChecklist(card.postnatalChecklistId) }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    "出生后要办的事",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "已办 " + card.postnatalDone + " / " + card.postnatalTotal,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(18.dp).height(18.dp)
                )
            }
        }
    }
    // 另设庆祝日：JrxDatePickerDialog + 「按出生日算」清除覆盖
    pickKind?.let { kind ->
        JrxDatePickerDialog(
            current = when (kind) {
                BabyCelebrationKind.FULL_MONTH -> card.fullMonth
                BabyCelebrationKind.HUNDRED_DAYS -> card.hundredDays
            },
            onPick = { date ->
                pickKind = null
                onSetCelebration(kind, date)
            },
            onDismiss = { pickKind = null },
            extraDismissButton = {
                TextButton(
                    onClick = {
                        pickKind = null
                        onSetCelebration(kind, null)
                    }
                ) { Text("按出生日算") }
            }
        )
    }
}

/** 满月/百天一行：左标签；右 “日期 · 还有 N 天 / 就是今天 / 已过 N 天”。 */
@Composable
private fun CelebrationRow(
    label: String,
    event: LocalDate,
    byBirth: LocalDate,
    overridden: Boolean,
    today: LocalDate,
    onClick: () -> Unit
) {
    val days = ChronoUnit.DAYS.between(today, event).toInt()
    val rel = when {
        days > 0 -> "还有 " + days + " 天"
        days == 0 -> "就是今天"
        else -> "已过 " + (-days) + " 天"
    }
    Column(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                cnFullDate(event) + " · " + rel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(18.dp).height(18.dp)
            )
        }
        if (overridden && event != byBirth) {
            Text(
                "按出生日算是 " + mdDate(byBirth),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
