package io.github.xiaohaoz1.jiarenxia.ui.reminder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import io.github.xiaohaoz1.jiarenxia.core.kinship.CoreGroup
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.data.ContactRepository
import io.github.xiaohaoz1.jiarenxia.data.HomeIntervals
import io.github.xiaohaoz1.jiarenxia.data.HomePlan
import io.github.xiaohaoz1.jiarenxia.data.HomeVisitRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.HomeVisitSchedule
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
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

/** 回家看看设置页里的一行（一个“家”）。 */
data class HomeVisitRow(
    val planId: String,
    /** 家名（提醒文案同口径）。 */
    val label: String,
    /** 计划里存的全部成员 id（选人对话框初始勾选用）。 */
    val memberIds: List<String>,
    /** “家里有谁”行右侧值：成员称谓用“、”连接；无成员 “未选择”。 */
    val memberValue: String,
    val intervalDays: Int,
    val intervalLabel: String,
    /** “上次回家”行右侧值：“N 天前”/“今天”/“还没有记录”。 */
    val lastVisitText: String
)

/** 选人对话框候选：在世、非本人的全部家人（家庭页顺序）。 */
data class HomeVisitCandidate(
    val personId: String,
    /** 有称谓 “称谓（姓名）”，无称谓仅姓名。 */
    val text: String,
    /** 是否“父母”核心家人（第一个家新增时预勾选）。 */
    val isParent: Boolean
)

/** 回家看看设置页状态（截图用无状态 Content 直接构造）。 */
data class HomeVisitUi(
    val holidayNudge: Boolean = true,
    val homes: List<HomeVisitRow> = emptyList(),
    val candidates: List<HomeVisitCandidate> = emptyList()
)

/**
 * 回家看看设置（提醒设置 → 回家看看）。
 * 每个“家”一组：成员、周期、上次回家与删除；可再添加；节前提醒总开关。
 */
@HiltViewModel
class HomeVisitViewModel @Inject constructor(
    private val homeVisitRepository: HomeVisitRepository,
    private val contactRepository: ContactRepository,
    private val observeOverview: ObserveFamilyOverview,
    private val settings: SettingsRepository
) : ViewModel() {

    val ui: StateFlow<HomeVisitUi> = combine(
        homeVisitRepository.observeHomes(),
        contactRepository.observeAllLogs(),
        observeOverview(),
        settings.homeVisitHolidayNudge
    ) { plans, logs, overview, nudge ->
        val today = LocalDate.now()
        val states = HomeVisitSchedule.homes(plans, overview, logs, today)
            .associateBy { it.planId }
        val ordered = overview?.let { it.core + it.others }.orEmpty()
        fun labelOf(o: io.github.xiaohaoz1.jiarenxia.domain.PersonOverview): String {
            val k = o.result.kind
            return if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF)
                o.person.name else o.displayTitle
        }
        val homes = plans.map { plan ->
            val st = states[plan.id]
            val memberSet = plan.memberIds.toSet()
            val memberLabels = ordered.mapNotNull { o ->
                if (o.person.id in memberSet) labelOf(o) else null
            }
            HomeVisitRow(
                planId = plan.id,
                label = st?.label ?: "这个家",
                memberIds = plan.memberIds,
                memberValue = if (memberLabels.isEmpty()) "未选择"
                else memberLabels.joinToString("、"),
                intervalDays = plan.intervalDays,
                intervalLabel = HomeIntervals.label(plan.intervalDays),
                lastVisitText = st?.status?.daysSinceLast?.let {
                    if (it == 0) "今天" else it.toString() + " 天前"
                } ?: "还没有记录"
            )
        }
        val candidates = ordered
            .filter { ContactSchedule.eligible(it.person) }
            .map { o ->
                HomeVisitCandidate(
                    personId = o.person.id,
                    text = if (o.result.kind == KinshipKind.UNRELATED ||
                        o.result.kind == KinshipKind.SELF
                    ) o.person.name
                    else o.displayTitle + "（" + o.person.name + "）",
                    isParent = o.result.coreGroup == CoreGroup.PARENTS
                )
            }
        HomeVisitUi(holidayNudge = nudge, homes = homes, candidates = candidates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeVisitUi())

    fun setHolidayNudge(on: Boolean) {
        viewModelScope.launch { settings.setHomeVisitHolidayNudge(on) }
    }

    fun addHome(memberIds: List<String>, intervalDays: Int) {
        viewModelScope.launch { homeVisitRepository.addHome(memberIds, intervalDays) }
    }

    fun updateMembers(id: String, memberIds: List<String>) {
        viewModelScope.launch { homeVisitRepository.updateMembers(id, memberIds) }
    }

    fun updateInterval(id: String, intervalDays: Int) {
        viewModelScope.launch { homeVisitRepository.updateInterval(id, intervalDays) }
    }

    fun deleteHome(id: String) {
        viewModelScope.launch { homeVisitRepository.deleteHome(id) }
    }
}

/** 选人对话框目标：新增（下一步进周期）或编辑某个家（保存）。 */
private sealed interface MemberPick {
    data object Add : MemberPick
    data class Edit(val row: HomeVisitRow) : MemberPick
}

/** 周期对话框目标：新增（完成才 addHome）或编辑（选中即保存）。 */
private sealed interface IntervalPick {
    data class Add(val memberIds: List<String>) : IntervalPick
    data class Edit(val row: HomeVisitRow) : IntervalPick
}

@Composable
fun HomeVisitScreen(
    onBack: () -> Unit,
    viewModel: HomeVisitViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    HomeVisitContent(
        state = st,
        onBack = onBack,
        onHolidayNudge = viewModel::setHolidayNudge,
        onAddHome = viewModel::addHome,
        onUpdateMembers = viewModel::updateMembers,
        onUpdateInterval = viewModel::updateInterval,
        onDeleteHome = viewModel::deleteHome
    )
}

@Composable
fun HomeVisitContent(
    state: HomeVisitUi,
    onBack: () -> Unit,
    onHolidayNudge: (Boolean) -> Unit = {},
    onAddHome: (List<String>, Int) -> Unit = { _, _ -> },
    onUpdateMembers: (String, List<String>) -> Unit = { _, _ -> },
    onUpdateInterval: (String, Int) -> Unit = { _, _ -> },
    onDeleteHome: (String) -> Unit = {}
) {
    var memberPick by remember { mutableStateOf<MemberPick?>(null) }
    var intervalPick by remember { mutableStateOf<IntervalPick?>(null) }
    var deleteFor by remember { mutableStateOf<HomeVisitRow?>(null) }

    SettingsScaffold("回家看看", onBack) {
        state.homes.forEach { row ->
            SettingsGroup(title = row.label) {
                SettingsNavRow(
                    icon = Icons.Rounded.Groups,
                    title = "家里有谁",
                    value = row.memberValue,
                    onClick = {
                        memberPick = MemberPick.Edit(row)
                    }
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Schedule,
                    title = "多久回去一次",
                    value = row.intervalLabel,
                    onClick = { intervalPick = IntervalPick.Edit(row) }
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.History,
                    title = "上次回家",
                    value = row.lastVisitText,
                    arrow = false,
                    onClick = {}
                )
                RowDivider()
                SettingsNavRow(
                    icon = Icons.Rounded.Delete,
                    title = "不再提醒这个家",
                    danger = true,
                    arrow = false,
                    onClick = { deleteFor = row }
                )
            }
        }
        if (state.homes.isEmpty()) {
            SettingsHint("选好家里有谁、多久回去一次，到时提醒你回家看看。")
        }
        SettingsGroup(null) {
            SettingsNavRow(
                icon = Icons.Rounded.Add,
                title = if (state.homes.isEmpty()) "添加一个家" else "再添加一个家",
                onClick = { memberPick = MemberPick.Add }
            )
        }
        SettingsGroup("节前提醒") {
            SettingsSwitchRow(
                title = "节前提醒",
                subtitle = "春节、中秋、国庆和家里人生日前 7 天，离上次回家超过半个周期时提醒一次",
                checked = state.holidayNudge,
                onCheckedChange = onHolidayNudge
            )
        }
        SettingsHint("只有“见面”记录算回过家，家里任何一位见过面都算。提醒跟随「联系家人」的开关和时间。")
        Spacer(Modifier.height(16.dp))
    }

    // ---------- 选人对话框 ----------
    memberPick?.let { pick ->
        // 初始勾选：编辑 = 计划成员 ∩ 候选；新增且是第一个家 = 在世父母
        val candidateIds = state.candidates.mapTo(HashSet()) { it.personId }
        val initial = when (pick) {
            is MemberPick.Edit -> pick.row.memberIds.filterTo(HashSet()) {
                it in candidateIds
            }
            MemberPick.Add ->
                if (state.homes.isEmpty())
                    state.candidates.filter { it.isParent }
                        .mapTo(HashSet()) { it.personId }
                else emptySet()
        }
        var selected by remember(pick) { mutableStateOf(initial) }
        AlertDialog(
            onDismissRequest = { memberPick = null },
            title = { Text("家里有谁") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.candidates.forEach { c ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    selected = if (c.personId in selected)
                                        selected - c.personId else selected + c.personId
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = c.personId in selected,
                                onCheckedChange = null
                            )
                            Text(c.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected.isNotEmpty(),
                    onClick = {
                        // 按家庭页顺序保存（候选本身即家庭页顺序）
                        val ids = state.candidates.map { it.personId }
                            .filter { it in selected }
                        when (pick) {
                            MemberPick.Add -> {
                                memberPick = null
                                intervalPick = IntervalPick.Add(ids)
                            }
                            is MemberPick.Edit -> {
                                onUpdateMembers(pick.row.planId, ids)
                                memberPick = null
                            }
                        }
                    }
                ) {
                    Text(if (pick is MemberPick.Edit) "保存" else "下一步")
                }
            },
            dismissButton = {
                TextButton(onClick = { memberPick = null }) { Text("取消") }
            }
        )
    }

    // ---------- 周期对话框 ----------
    intervalPick?.let { pick ->
        var selected by remember(pick) {
            mutableStateOf(
                when (pick) {
                    is IntervalPick.Add -> HomeIntervals.QUARTER
                    is IntervalPick.Edit -> pick.row.intervalDays
                }
            )
        }
        AlertDialog(
            onDismissRequest = { intervalPick = null },
            title = { Text("多久回去一次") },
            text = {
                Column {
                    HomeIntervals.ALL.forEach { days ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    when (pick) {
                                        is IntervalPick.Edit -> {
                                            // 编辑模式：选中即保存并关闭
                                            onUpdateInterval(pick.row.planId, days)
                                            intervalPick = null
                                        }
                                        is IntervalPick.Add -> selected = days
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selected == days ||
                                    (pick is IntervalPick.Edit &&
                                        pick.row.intervalDays == days),
                                onClick = null
                            )
                            Text(
                                HomeIntervals.label(days),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (pick is IntervalPick.Add) {
                    TextButton(
                        onClick = {
                            onAddHome(pick.memberIds, selected)
                            intervalPick = null
                        }
                    ) { Text("完成") }
                }
            },
            dismissButton = {
                TextButton(onClick = { intervalPick = null }) { Text("取消") }
            }
        )
    }

    // ---------- 删除确认 ----------
    deleteFor?.let { row ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("不再提醒回家看看" + row.label + "？") },
            text = { Text("已经记下的见面记录会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteHome(row.planId)
                        deleteFor = null
                    }
                ) { Text("不再提醒") }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) { Text("取消") }
            }
        )
    }
}
