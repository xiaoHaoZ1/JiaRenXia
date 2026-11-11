package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import java.time.LocalDate

/**
 * 人物页「用药」卡（FAMILY_AFFAIRS 23）：未停用的药逐行摘要 +
 * 「以前吃过」折叠历史 + 「记一种药」/「给医生看」入口 + 按人提醒开关。
 * 只在世人物显示；已停用记录默认收起。
 */
@Composable
fun MedicationCard(
    medications: List<Medication>,
    today: LocalDate,
    courseRefillEnabled: Boolean,
    onSetCourseRefill: (Boolean) -> Unit,
    onOpenEdit: (String?) -> Unit,
    onOpenDoctor: () -> Unit
) {
    val active = medications.filter { it.stoppedEpochDay == null }
    val stopped = medications.filter { it.stoppedEpochDay != null }
    var historyOpen by remember { mutableStateOf(false) }
    JrxCard(Modifier.fillMaxWidth()) {
        SectionTitle("用药")
        if (medications.isEmpty()) {
            Text(
                "还没有记录在用的药",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (active.isEmpty()) {
                Text(
                    "还没有记录在用的药",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            active.forEach { med ->
                MedRow(med = med, today = today, onClick = { onOpenEdit(med.id) })
            }
            if (stopped.isNotEmpty()) {
                TextButton(onClick = { historyOpen = !historyOpen }) {
                    Text(if (historyOpen) "收起" else "以前吃过（${stopped.size}）")
                }
                if (historyOpen) {
                    stopped.forEach { med ->
                        val start = LocalDate.ofEpochDay(med.startEpochDay)
                        val stop = LocalDate.ofEpochDay(med.stoppedEpochDay!!)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .clickable { onOpenEdit(med.id) }
                                .heightIn(min = 48.dp)
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                "${med.name} · " +
                                    MedSchedule.shortDate(start) + "开始 · " +
                                    MedSchedule.shortDate(stop) + "记录停用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        // 底部入口行：「记一种药」始终可点（首条也要能录）；
        // 「给医生看」仅在有未停用记录时出现
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = { onOpenEdit(null) }) {
                Text("记一种药")
            }
            Spacer(Modifier.weight(1f))
            if (active.isNotEmpty()) {
                TextButton(onClick = onOpenDoctor) {
                    Text("给医生看")
                }
            }
        }
        if (medications.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "疗程到期与续药提醒",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = courseRefillEnabled,
                    onCheckedChange = onSetCourseRefill
                )
            }
        }
    }
}

/** 一条未停用用药：药名 + 主摘要（待确认/到期用 warning 色）+ 第二行。 */
@Composable
private fun MedRow(med: Medication, today: LocalDate, onClick: () -> Unit) {
    val summary = MedSchedule.summaryOf(med, today)
    val warn = summary.coursePending ||
        summary.status == MedSchedule.Status.COURSE_ENDED
    Column(
        modifier = Modifier.fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp)
    ) {
        Text(med.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            summary.mainLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (warn) LocalJrxColors.current.warning
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        summary.detailLine?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
