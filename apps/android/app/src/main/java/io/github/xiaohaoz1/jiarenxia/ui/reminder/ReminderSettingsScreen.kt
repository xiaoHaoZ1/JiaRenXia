package io.github.xiaohaoz1.jiarenxia.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.HomeIntervals
import io.github.xiaohaoz1.jiarenxia.data.HomeVisitRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.HomeVisitSchedule
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombCalendar
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderTiming
import io.github.xiaohaoz1.jiarenxia.ui.common.rememberNotificationPermissionRequester
import io.github.xiaohaoz1.jiarenxia.ui.mine.RowDivider
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsGroup
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsHint
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsNavRow
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsScaffold
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsSwitchRow
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 五类提醒的开关与每日时刻。 */
data class ReminderSettingsUi(
    val birthdayEnabled: Boolean = true,
    val taskEnabled: Boolean = true,
    val contactEnabled: Boolean = true,
    val tombEnabled: Boolean = true,
    val medEnabled: Boolean = true,
    val birthdayMinute: Int = ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    val taskMinute: Int = ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    val contactMinute: Int = ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    val tombMinute: Int = ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    val medMinute: Int = ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    /** 「上坟的日子与家人」行副标题摘要（已开启日子 + 家人数）。 */
    val tombSummary: String = "未选择日子",
    /** 「回家看看」行副标题摘要（每个家 “家名 周期”，“；”连接；无家 “未设置”）。 */
    val homeSummary: String = "未设置"
)

/**
 * 提醒设置（家事页顶栏铃铛）。
 *
 * 2026-10-09 改版从「我的」搬到这里：用户想改「生日提醒几点」时想的是「提醒」，
 * 不是「我的 → 设置」。调度仍由 ReminderSync 随设置变化自行重排。
 */
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val observeOverview: ObserveFamilyOverview,
    private val homeVisitRepository: HomeVisitRepository
) : ViewModel() {

    init {
        // 进过提醒设置页即算看过家事页的一次性引导条
        viewModelScope.launch { settings.setReminderGuideSeen(true) }
    }

    val ui: StateFlow<ReminderSettingsUi> = combine(
        settings.remindersEnabled,
        settings.taskRemindersEnabled,
        settings.contactRemindersEnabled,
        settings.reminderMinuteOfDay,
        settings.taskReminderMinuteOfDay
    ) { b, t, c, bm, tm ->
        ReminderSettingsUi(
            birthdayEnabled = b, taskEnabled = t, contactEnabled = c,
            birthdayMinute = bm, taskMinute = tm
        )
    }
        .combine(settings.contactReminderMinuteOfDay) { st, cm -> st.copy(contactMinute = cm) }
        .combine(settings.tombRemindersEnabled) { st, te -> st.copy(tombEnabled = te) }
        .combine(settings.tombReminderMinuteOfDay) { st, tm -> st.copy(tombMinute = tm) }
        .combine(settings.medRemindersEnabled) { st, me -> st.copy(medEnabled = me) }
        .combine(settings.medReminderMinuteOfDay) { st, mm -> st.copy(medMinute = mm) }
        .combine(
            combine(settings.tombDays, observeOverview()) { days, ov ->
                days to TombCalendar.mourners(ov).size
            }
        ) { st, (days, count) ->
            st.copy(tombSummary = tombDaysSummary(days, count))
        }
        .combine(
            homeVisitRepository.observeHomes()
                .combine(observeOverview()) { plans, ov -> plans to ov }
        ) { st, (plans, ov) ->
            // 摘要：每个家 “家名 周期label”，无家 “未设置”
            val states = HomeVisitSchedule.homes(
                plans, ov, emptyList(), LocalDate.now()).associateBy { it.planId }
            val summary = plans.joinToString("；") { p ->
                (states[p.id]?.label ?: "这个家") + " " + HomeIntervals.label(p.intervalDays)
            }.ifEmpty { "未设置" }
            st.copy(homeSummary = summary)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderSettingsUi())

    fun setBirthdayEnabled(on: Boolean) {
        viewModelScope.launch { settings.setRemindersEnabled(on) }
    }

    fun setTaskEnabled(on: Boolean) {
        viewModelScope.launch { settings.setTaskRemindersEnabled(on) }
    }

    fun setContactEnabled(on: Boolean) {
        viewModelScope.launch { settings.setContactRemindersEnabled(on) }
    }

    fun setBirthdayMinute(m: Int) {
        viewModelScope.launch { settings.setReminderMinuteOfDay(m) }
    }

    fun setTaskMinute(m: Int) {
        viewModelScope.launch { settings.setTaskReminderMinuteOfDay(m) }
    }

    fun setContactMinute(m: Int) {
        viewModelScope.launch { settings.setContactReminderMinuteOfDay(m) }
    }

    fun setTombEnabled(on: Boolean) {
        viewModelScope.launch { settings.setTombRemindersEnabled(on) }
    }

    fun setTombMinute(m: Int) {
        viewModelScope.launch { settings.setTombReminderMinuteOfDay(m) }
    }

    fun setMedEnabled(on: Boolean) {
        viewModelScope.launch { settings.setMedRemindersEnabled(on) }
    }

    fun setMedMinute(m: Int) {
        viewModelScope.launch { settings.setMedReminderMinuteOfDay(m) }
    }
}

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
    onOpenTombSettings: () -> Unit = {},
    onOpenHomeSettings: () -> Unit = {},
    viewModel: ReminderSettingsViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val requester = rememberNotificationPermissionRequester()
    ReminderSettingsContent(
        state = st,
        onBack = onBack,
        onBirthdayEnabled = { on ->
            viewModel.setBirthdayEnabled(on)
            if (on) requester.request()
        },
        onTaskEnabled = viewModel::setTaskEnabled,
        onContactEnabled = viewModel::setContactEnabled,
        onTombEnabled = { on ->
            viewModel.setTombEnabled(on)
            if (on) requester.request()
        },
        onMedEnabled = { on ->
            viewModel.setMedEnabled(on)
            if (on) requester.request()
        },
        onBirthdayMinute = viewModel::setBirthdayMinute,
        onTaskMinute = viewModel::setTaskMinute,
        onContactMinute = viewModel::setContactMinute,
        onTombMinute = viewModel::setTombMinute,
        onMedMinute = viewModel::setMedMinute,
        onOpenTombSettings = onOpenTombSettings,
        onOpenHomeSettings = onOpenHomeSettings
    )
}

@Composable
fun ReminderSettingsContent(
    state: ReminderSettingsUi,
    onBack: () -> Unit,
    onBirthdayEnabled: (Boolean) -> Unit = {},
    onTaskEnabled: (Boolean) -> Unit = {},
    onContactEnabled: (Boolean) -> Unit = {},
    onTombEnabled: (Boolean) -> Unit = {},
    onMedEnabled: (Boolean) -> Unit = {},
    onBirthdayMinute: (Int) -> Unit = {},
    onTaskMinute: (Int) -> Unit = {},
    onContactMinute: (Int) -> Unit = {},
    onTombMinute: (Int) -> Unit = {},
    onMedMinute: (Int) -> Unit = {},
    onOpenTombSettings: () -> Unit = {},
    onOpenHomeSettings: () -> Unit = {}
) {
    var timeTarget by remember { mutableStateOf<ReminderTimeTarget?>(null) }

    SettingsScaffold("提醒设置", onBack) {
        SettingsGroup("每天什么时候提醒") {
            SettingsSwitchRow(
                title = "生日与纪念日",
                icon = Icons.Rounded.NotificationsActive,
                checked = state.birthdayEnabled,
                onCheckedChange = onBirthdayEnabled,
                trailing = {
                    ReminderTimeText(state.birthdayEnabled, state.birthdayMinute) {
                        timeTarget = ReminderTimeTarget.BIRTHDAY
                    }
                }
            )
            RowDivider()
            SettingsSwitchRow(
                title = "家庭事项",
                icon = Icons.Rounded.EventAvailable,
                checked = state.taskEnabled,
                onCheckedChange = onTaskEnabled,
                trailing = {
                    ReminderTimeText(state.taskEnabled, state.taskMinute) {
                        timeTarget = ReminderTimeTarget.TASK
                    }
                }
            )
            RowDivider()
            SettingsSwitchRow(
                title = "联系家人",
                icon = Icons.Rounded.Call,
                checked = state.contactEnabled,
                onCheckedChange = onContactEnabled,
                trailing = {
                    ReminderTimeText(state.contactEnabled, state.contactMinute) {
                        timeTarget = ReminderTimeTarget.CONTACT
                    }
                }
            )
            RowDivider()
            SettingsSwitchRow(
                title = "上坟提醒",
                icon = Icons.Rounded.LocalFlorist,
                checked = state.tombEnabled,
                onCheckedChange = onTombEnabled,
                trailing = {
                    ReminderTimeText(state.tombEnabled, state.tombMinute) {
                        timeTarget = ReminderTimeTarget.TOMB
                    }
                }
            )
            RowDivider()
            SettingsSwitchRow(
                title = "用药提醒",
                subtitle = "疗程到期与续药",
                icon = Icons.Rounded.Medication,
                checked = state.medEnabled,
                onCheckedChange = onMedEnabled,
                trailing = {
                    ReminderTimeText(state.medEnabled, state.medMinute) {
                        timeTarget = ReminderTimeTarget.MED
                    }
                }
            )
        }
        SettingsHint("五类各管各的：关掉一类不影响其他四类。逾期的事项只置顶标红，不再反复发通知。")
        SettingsGroup("上坟提醒") {
            SettingsNavRow(
                icon = Icons.Rounded.LocalFlorist,
                title = "上坟的日子与家人",
                subtitle = state.tombSummary,
                onClick = onOpenTombSettings
            )
        }
        SettingsGroup("回家看看") {
            SettingsNavRow(
                icon = Icons.Rounded.Home,
                title = "回家看看",
                subtitle = state.homeSummary,
                onClick = onOpenHomeSettings
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    timeTarget?.let { target ->
        val title = when (target) {
            ReminderTimeTarget.BIRTHDAY -> "生日与纪念日提醒"
            ReminderTimeTarget.TASK -> "家庭事项提醒"
            ReminderTimeTarget.CONTACT -> "联系提醒"
            ReminderTimeTarget.TOMB -> "上坟提醒"
            ReminderTimeTarget.MED -> "用药提醒"
        }
        val minute = when (target) {
            ReminderTimeTarget.BIRTHDAY -> state.birthdayMinute
            ReminderTimeTarget.TASK -> state.taskMinute
            ReminderTimeTarget.CONTACT -> state.contactMinute
            ReminderTimeTarget.TOMB -> state.tombMinute
            ReminderTimeTarget.MED -> state.medMinute
        }
        ReminderTimeDialog(
            title = title,
            initialMinuteOfDay = minute,
            onConfirm = {
                when (target) {
                    ReminderTimeTarget.BIRTHDAY -> onBirthdayMinute(it)
                    ReminderTimeTarget.TASK -> onTaskMinute(it)
                    ReminderTimeTarget.CONTACT -> onContactMinute(it)
                    ReminderTimeTarget.TOMB -> onTombMinute(it)
                    ReminderTimeTarget.MED -> onMedMinute(it)
                }
                timeTarget = null
            },
            onDismiss = { timeTarget = null }
        )
    }
}

/** 正在改时间的那一类提醒。 */
private enum class ReminderTimeTarget { BIRTHDAY, TASK, CONTACT, TOMB, MED }

/** 「上坟的日子与家人」行副标题：已开启日子短名（固定顺序）+ “ · N 位家人”。 */
internal fun tombDaysSummary(days: Set<TombDay>, personCount: Int): String {
    val names = TombDay.entries.mapNotNull { d ->
        TOMB_DAY_SHORT[d]?.takeIf { d in days }
    }
    if (names.isEmpty()) return "未选择日子"
    return names.joinToString("、") + " · " + personCount + " 位家人"
}

private val TOMB_DAY_SHORT = mapOf(
    TombDay.QINGMING to "清明",
    TombDay.ZHONGYUAN to "中元",
    TombDay.HANYI to "寒衣",
    TombDay.CHUXI to "除夕",
    TombDay.DONGZHI to "冬至",
    TombDay.DEATH_DAY to "忌日",
    TombDay.MOURNING to "做七"
)

/** 行尾的提醒时刻文本：点开选择器；对应开关关闭时置灰不可点。 */
@Composable
private fun ReminderTimeText(enabled: Boolean, minuteOfDay: Int, onClick: () -> Unit) {
    Text(
        ReminderTiming.format(minuteOfDay),
        style = MaterialTheme.typography.titleMedium,
        color = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/** 选择某类提醒的每日提醒时间（24 小时制）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    title: String,
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = ReminderTiming.timeOf(initialMinuteOfDay)
    val state = rememberTimePickerState(
        initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "每天这个时间检查并发出该类提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
