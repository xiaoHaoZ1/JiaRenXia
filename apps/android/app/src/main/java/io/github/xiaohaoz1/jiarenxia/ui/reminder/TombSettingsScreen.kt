package io.github.xiaohaoz1.jiarenxia.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombCalendar
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombMourner
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombPrefs
import io.github.xiaohaoz1.jiarenxia.ui.mine.RowDivider
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsGroup
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsHint
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsNavRow
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsScaffold
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsSwitchRow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 上坟设置页状态（截图用无状态 Content 直接构造）。 */
data class TombSettingsUi(
    val days: Set<TombDay> = TombPrefs.DEFAULT_DAYS,
    val zhongyuanDay: Int = 15,
    val excluded: Set<String> = emptySet(),
    val mourners: List<TombMourner> = emptyList()
)

/**
 * 上坟提醒设置（提醒设置 → 上坟的日子与家人）。
 * 上坟总开关关闭时本页照常可改（总开关只管发不发通知）。
 */
@HiltViewModel
class TombSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val observeOverview: ObserveFamilyOverview
) : ViewModel() {

    val ui: StateFlow<TombSettingsUi> = combine(
        settings.tombDays,
        settings.tombZhongyuanDay,
        settings.tombExcludedPersonIds
    ) { d, z, e -> Triple(d, z, e) }
        .combine(observeOverview()) { (d, z, e), ov ->
            TombSettingsUi(d, z, e, TombCalendar.mourners(ov))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TombSettingsUi())

    fun setDayEnabled(day: TombDay, on: Boolean) {
        viewModelScope.launch {
            val cur = settings.tombDays.first()
            settings.setTombDays(if (on) cur + day else cur - day)
        }
    }

    fun setZhongyuanDay(day: Int) {
        viewModelScope.launch { settings.setTombZhongyuanDay(day) }
    }

    fun setPersonTomb(on: Boolean, personId: String) {
        viewModelScope.launch {
            val cur = settings.tombExcludedPersonIds.first()
            settings.setTombExcludedPersonIds(
                if (on) cur - personId else cur + personId)
        }
    }
}

@Composable
fun TombSettingsScreen(
    onBack: () -> Unit,
    viewModel: TombSettingsViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    TombSettingsContent(
        state = st,
        onBack = onBack,
        onDayEnabled = viewModel::setDayEnabled,
        onZhongyuanDay = viewModel::setZhongyuanDay,
        onPersonTomb = viewModel::setPersonTomb
    )
}

@Composable
fun TombSettingsContent(
    state: TombSettingsUi,
    onBack: () -> Unit,
    onDayEnabled: (TombDay, Boolean) -> Unit = { _, _ -> },
    onZhongyuanDay: (Int) -> Unit = {},
    onPersonTomb: (Boolean, String) -> Unit = { _, _ -> }
) {
    var zhongyuanDialog by remember { mutableStateOf(false) }

    SettingsScaffold("上坟提醒", onBack) {
        SettingsGroup("哪些日子提醒") {
            DaySwitch("清明节", "提前 7 天和前一天提醒",
                TombDay.QINGMING in state.days) { onDayEnabled(TombDay.QINGMING, it) }
            RowDivider()
            DaySwitch(
                "中元节",
                "农历七月" + (if (state.zhongyuanDay == 14) "十四" else "十五") +
                    " · 提前 3 天和当天提醒",
                TombDay.ZHONGYUAN in state.days) { onDayEnabled(TombDay.ZHONGYUAN, it) }
            RowDivider()
            SettingsNavRow(
                icon = Icons.Rounded.Tune,
                title = "中元节按哪天",
                value = if (state.zhongyuanDay == 14) "七月十四" else "七月十五",
                onClick = { zhongyuanDialog = true }
            )
            RowDivider()
            DaySwitch("寒衣节", "农历十月初一 · 提前 3 天和当天提醒",
                TombDay.HANYI in state.days) { onDayEnabled(TombDay.HANYI, it) }
            RowDivider()
            DaySwitch("除夕", "前一天和当天提醒",
                TombDay.CHUXI in state.days) { onDayEnabled(TombDay.CHUXI, it) }
            RowDivider()
            DaySwitch("冬至", "提前 3 天和当天提醒",
                TombDay.DONGZHI in state.days) { onDayEnabled(TombDay.DONGZHI, it) }
            RowDivider()
            DaySwitch("忌日", "每位家人提前几天提醒，在其详情页设置",
                TombDay.DEATH_DAY in state.days) { onDayEnabled(TombDay.DEATH_DAY, it) }
            RowDivider()
            DaySwitch("做七与百日",
                "按忌日推算头七、三七、五七、七七和百日，需填写去世年份",
                TombDay.MOURNING in state.days) { onDayEnabled(TombDay.MOURNING, it) }
        }
        SettingsHint("节日日期每年自动推算，清明、冬至按节气。")
        SettingsGroup("给谁上坟") {
            if (state.mourners.isEmpty()) {
                SettingsHint(
                    "家里还没有记为已故的家人。在家人详情页把状态改为已故后，会出现在这里。"
                )
            } else {
                state.mourners.forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    SettingsSwitchRow(
                        title = m.label,
                        subtitle = if (m.title != null) m.name else null,
                        checked = m.personId !in state.excluded,
                        onCheckedChange = { on -> onPersonTomb(on, m.personId) }
                    )
                }
            }
        }
        if (state.mourners.isNotEmpty()) {
            SettingsHint("关掉的家人不会出现在清明、中元等节日提醒里；忌日和做七提醒不受影响。")
        }
        Spacer(Modifier.height(16.dp))
    }

    if (zhongyuanDialog) {
        AlertDialog(
            onDismissRequest = { zhongyuanDialog = false },
            title = { Text("中元节按哪天") },
            text = {
                Column {
                    ZhongyuanOption(
                        label = "七月十五（多数地方）",
                        selected = state.zhongyuanDay == 15,
                        onPick = { onZhongyuanDay(15); zhongyuanDialog = false }
                    )
                    ZhongyuanOption(
                        label = "七月十四（广东、客家等地）",
                        selected = state.zhongyuanDay == 14,
                        onPick = { onZhongyuanDay(14); zhongyuanDialog = false }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { zhongyuanDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 「哪些日子提醒」组里的开关行（无图标）。 */
@Composable
private fun DaySwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsSwitchRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

/** 中元节日期单选项：点中即保存并关闭弹窗。 */
@Composable
private fun ZhongyuanOption(
    label: String,
    selected: Boolean,
    onPick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onPick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
