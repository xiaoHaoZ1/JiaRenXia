package io.github.xiaohaoz1.jiarenxia.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.EventAvailable
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
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderTiming
import io.github.xiaohaoz1.jiarenxia.ui.common.rememberNotificationPermissionRequester
import io.github.xiaohaoz1.jiarenxia.ui.mine.RowDivider
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsGroup
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsHint
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsScaffold
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsSwitchRow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 三类提醒的开关与每日时刻。 */
data class ReminderSettingsUi(
    val birthdayEnabled: Boolean = true,
    val taskEnabled: Boolean = true,
    val contactEnabled: Boolean = true,
    val birthdayMinute: Int = ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    val taskMinute: Int = ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    val contactMinute: Int = ReminderTiming.DEFAULT_MINUTE_OF_DAY
)

/**
 * 提醒设置（家事页顶栏铃铛）。
 *
 * 2026-10-09 改版从「我的」搬到这里：用户想改「生日提醒几点」时想的是「提醒」，
 * 不是「我的 → 设置」。调度仍由 ReminderSync 随设置变化自行重排。
 */
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    val ui: StateFlow<ReminderSettingsUi> = combine(
        settings.remindersEnabled,
        settings.taskRemindersEnabled,
        settings.contactRemindersEnabled,
        settings.reminderMinuteOfDay,
        settings.taskReminderMinuteOfDay
    ) { b, t, c, bm, tm -> ReminderSettingsUi(b, t, c, bm, tm) }
        .combine(settings.contactReminderMinuteOfDay) { st, cm -> st.copy(contactMinute = cm) }
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
}

@Composable
fun ReminderSettingsScreen(
    onBack: () -> Unit,
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
        onBirthdayMinute = viewModel::setBirthdayMinute,
        onTaskMinute = viewModel::setTaskMinute,
        onContactMinute = viewModel::setContactMinute
    )
}

@Composable
fun ReminderSettingsContent(
    state: ReminderSettingsUi,
    onBack: () -> Unit,
    onBirthdayEnabled: (Boolean) -> Unit = {},
    onTaskEnabled: (Boolean) -> Unit = {},
    onContactEnabled: (Boolean) -> Unit = {},
    onBirthdayMinute: (Int) -> Unit = {},
    onTaskMinute: (Int) -> Unit = {},
    onContactMinute: (Int) -> Unit = {}
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
        }
        SettingsHint("三类各管各的：关掉一类不影响其他两类。逾期的事项只置顶标红，不再反复发通知。")
        Spacer(Modifier.height(16.dp))
    }

    timeTarget?.let { target ->
        val title = when (target) {
            ReminderTimeTarget.BIRTHDAY -> "生日与纪念日提醒"
            ReminderTimeTarget.TASK -> "家庭事项提醒"
            ReminderTimeTarget.CONTACT -> "联系提醒"
        }
        val minute = when (target) {
            ReminderTimeTarget.BIRTHDAY -> state.birthdayMinute
            ReminderTimeTarget.TASK -> state.taskMinute
            ReminderTimeTarget.CONTACT -> state.contactMinute
        }
        ReminderTimeDialog(
            title = title,
            initialMinuteOfDay = minute,
            onConfirm = {
                when (target) {
                    ReminderTimeTarget.BIRTHDAY -> onBirthdayMinute(it)
                    ReminderTimeTarget.TASK -> onTaskMinute(it)
                    ReminderTimeTarget.CONTACT -> onContactMinute(it)
                }
                timeTarget = null
            },
            onDismiss = { timeTarget = null }
        )
    }
}

/** 正在改时间的那一类提醒。 */
private enum class ReminderTimeTarget { BIRTHDAY, TASK, CONTACT }

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
