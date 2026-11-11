package io.github.xiaohaoz1.jiarenxia.ui.person

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import io.github.xiaohaoz1.jiarenxia.core.model.Medication
import io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
import io.github.xiaohaoz1.jiarenxia.reminder.MedAlarmScheduler
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import java.time.LocalDate

/** 「按时吃提醒」行下方的系统状态提示（只显示一条，通知优先）。 */
enum class MedOnTimeHint { NONE, NOTIFICATIONS_OFF, EXACT_ALARM_MISSING }

/**
 * 计算按时吃提醒的系统提示：通知不可用（系统通知关或「按时吃药」渠道关）
 * 优先于 Android 12+ 缺精确闹钟权限；渠道尚未创建视为可用。
 */
fun medOnTimeHint(context: Context): MedOnTimeHint {
    val nm = NotificationManagerCompat.from(context)
    val notifOn = nm.areNotificationsEnabled() &&
        (nm.getNotificationChannel(MedAlarmScheduler.MED_ON_TIME_CHANNEL)
            ?.importance != NotificationManager.IMPORTANCE_NONE)
    if (!notifOn) return MedOnTimeHint.NOTIFICATIONS_OFF
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(android.app.AlarmManager::class.java)
        if (am != null && !am.canScheduleExactAlarms()) {
            return MedOnTimeHint.EXACT_ALARM_MISSING
        }
    }
    return MedOnTimeHint.NONE
}

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
    onOpenDoctor: () -> Unit,
    /** 「按时吃提醒」该人开关的生效值（显式值优先；null 时“我”开他人关）。 */
    onTimeEnabled: Boolean = false,
    /** 用药总开关 && 「按时吃提醒」子开关（两个都开时按人开关才可改）。 */
    onTimeSwitchEnabled: Boolean = true,
    /** 系统状态提示（通知关 / 缺精确闹钟权限），由调用方按系统实况给出。 */
    onTimeHint: MedOnTimeHint = MedOnTimeHint.NONE,
    onSetOnTime: (Boolean) -> Unit = {}
) {
    val active = medications.filter { it.stoppedEpochDay == null }
    val stopped = medications.filter { it.stoppedEpochDay != null }
    val hasTimed = medications.any { it.times.isNotEmpty() }
    val hasActiveTimed = active.any { it.times.isNotEmpty() }
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
        if (hasTimed) {
            // 「按时吃提醒」按人开关：生效值显示，两个用药开关都开才可改
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .alpha(if (onTimeSwitchEnabled) 1f else 0.5f)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "按时吃提醒",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (onTimeSwitchEnabled)
                            "部分手机需要在系统设置里允许家人侠自启动、" +
                                "不限制后台，提醒才更准时"
                        else "「提醒」里的用药提醒或按时吃提醒已关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = onTimeEnabled,
                    onCheckedChange = onSetOnTime,
                    enabled = onTimeSwitchEnabled
                )
            }
            // 生效且有在用带时刻药时，按需提示系统状态（通知优先于精确闹钟）
            if (onTimeEnabled && onTimeSwitchEnabled && hasActiveTimed) {
                val context = LocalContext.current
                when (onTimeHint) {
                    MedOnTimeHint.NOTIFICATIONS_OFF -> OnTimeHintRow(
                        text = "通知未开启，提醒不会响",
                        action = "去开启"
                    ) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(
                                        Settings.EXTRA_APP_PACKAGE,
                                        context.packageName
                                    )
                            )
                        }
                    }
                    MedOnTimeHint.EXACT_ALARM_MISSING -> OnTimeHintRow(
                        text = "提醒可能延迟",
                        action = "允许准时提醒"
                    ) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(
                                        Uri.parse("package:" + context.packageName)
                                    )
                            )
                        }
                    }
                    MedOnTimeHint.NONE -> Unit
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

/** 按时吃提醒的系统提示行：warning 小字 + 右侧操作按钮。 */
@Composable
private fun OnTimeHintRow(text: String, action: String, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = LocalJrxColors.current.warning,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAction) {
            Text(action, style = MaterialTheme.typography.bodySmall)
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
