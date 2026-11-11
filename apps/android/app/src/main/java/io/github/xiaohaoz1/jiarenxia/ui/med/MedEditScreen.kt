package io.github.xiaohaoz1.jiarenxia.ui.med

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.MedMeal
import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
import io.github.xiaohaoz1.jiarenxia.ui.common.FormActionBar
import io.github.xiaohaoz1.jiarenxia.ui.common.JrxDatePickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.person.cnFullDate
import io.github.xiaohaoz1.jiarenxia.ui.person.mdDate
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import java.time.LocalDate

/** 编辑页回调集合（截图测试可传全空）。 */
data class MedEditCallbacks(
    val onName: (String) -> Unit = {},
    val onPurpose: (String) -> Unit = {},
    val onDose: (String) -> Unit = {},
    val onSpec: (String) -> Unit = {},
    val onUsage: (MedUsage?) -> Unit = {},
    val onCourseDaysText: (String) -> Unit = {},
    val onCoursePending: (Boolean) -> Unit = {},
    val onRefillDate: (LocalDate?) -> Unit = {},
    val onStartDate: (LocalDate) -> Unit = {},
    val onAddTime: (Int) -> Unit = {},
    val onRemoveTime: (Int) -> Unit = {},
    val onMeal: (MedMeal?) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onMarkStopped: () -> Unit = {},
    val onClearStopped: () -> Unit = {},
    val onCourseEndedHint: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onConfirmLongTerm: () -> Unit = {},
    val onDismissLongTerm: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismissError: () -> Unit = {}
)

/** 「拿药时问清楚」卡的固定五条。 */
private val MED_ASKS = listOf(
    "吃多久？能不能长期吃？",
    "饭前吃还是饭后吃？",
    "和正在吃的其他药冲不冲突？",
    "吃药期间能不能喝酒？",
    "什么情况要复诊？"
)

/**
 * 用药编辑页（FAMILY_AFFAIRS 23）：新建 / 编辑共用；
 * 顶栏返回 + 标题；编辑态 ⋮「删除」；底部固定「保存」。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedEditScreen(
    onDone: () -> Unit,
    viewModel: MedEditViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    MedEditContent(
        st = st,
        today = LocalDate.now(),
        cb = MedEditCallbacks(
            onName = viewModel::setName,
            onPurpose = viewModel::setPurpose,
            onDose = viewModel::setDose,
            onSpec = viewModel::setSpec,
            onUsage = viewModel::setUsage,
            onCourseDaysText = viewModel::setCourseDaysText,
            onCoursePending = viewModel::setCoursePending,
            onRefillDate = viewModel::setRefillDate,
            onStartDate = viewModel::setStartDate,
            onAddTime = viewModel::addTime,
            onRemoveTime = viewModel::removeTime,
            onMeal = viewModel::setMeal,
            onNote = viewModel::setNote,
            onMarkStopped = viewModel::markStopped,
            onClearStopped = viewModel::clearStopped,
            onCourseEndedHint = viewModel::showCourseEndedHint,
            onSave = viewModel::save,
            onConfirmLongTerm = viewModel::confirmLongTermSave,
            onDismissLongTerm = viewModel::dismissLongTermConfirm,
            onDelete = viewModel::delete,
            onDismissError = viewModel::dismissError
        ),
        onDone = onDone
    )
}

/** 用药编辑页无状态主体（截图测试直接喂状态）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedEditContent(
    st: MedEditUiState,
    today: LocalDate,
    cb: MedEditCallbacks,
    onDone: () -> Unit
) {
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(st.saved, st.deleted) {
        if (st.saved || st.deleted) onDone()
    }
    LaunchedEffect(st.error) {
        st.error?.let {
            snack.showSnackbar(it)
            cb.onDismissError()
        }
    }

    var startDialog by remember { mutableStateOf(false) }
    var refillDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 「拿药时问清楚」：新建默认展开，编辑默认收起
    var askOpen by remember(st.medId) { mutableStateOf(st.medId == null) }

    val missing = when {
        st.name.isBlank() -> "还差：药名"
        st.usage == null -> "还差：怎么吃"
        st.usage == MedUsage.SHORT_COURSE && !st.coursePending &&
            (st.courseDaysText.trim().toIntOrNull() == null ||
                st.courseDaysText.trim().toInt() !in 1..365) -> "还差：疗程天数"
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (st.medId == null) "记一种药" else "编辑用药") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (st.medId != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = {
                                    menuOpen = false
                                    deleteConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            FormActionBar(
                primaryText = "保存",
                onPrimary = cb.onSave,
                primaryEnabled = st.canSave,
                busy = st.saving,
                missingText = missing
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 已有记录的状态卡（规格 §6.2）
            st.existing?.let { med ->
                MedStatusCard(
                    med = med, today = today,
                    courseEndedHint = st.courseEndedHint,
                    onMarkStopped = cb.onMarkStopped,
                    onClearStopped = cb.onClearStopped,
                    onCourseEndedHint = cb.onCourseEndedHint
                )
                Spacer(Modifier.height(6.dp))
            }

            // 「拿药时问清楚」可折叠卡
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { askOpen = !askOpen }
                ) {
                    Text(
                        "拿药时问清楚",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (askOpen) Icons.Rounded.KeyboardArrowDown
                        else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (askOpen) {
                    Spacer(Modifier.height(6.dp))
                    MED_ASKS.forEach { q ->
                        Text(
                            q,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "问到的答案填在下面：吃多久填进“怎么吃”，其余写进医嘱备注。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 药名（必填）
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                OutlinedTextField(
                    value = st.name,
                    onValueChange = cb.onName,
                    label = { Text("药名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 治什么
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                OutlinedTextField(
                    value = st.purpose,
                    onValueChange = cb.onPurpose,
                    label = { Text("治什么（可不填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 每次用量
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                OutlinedTextField(
                    value = st.dose,
                    onValueChange = cb.onDose,
                    label = { Text("每次用量（可不填，如 1 片）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 药品规格
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                OutlinedTextField(
                    value = st.spec,
                    onValueChange = cb.onSpec,
                    label = { Text("药品规格（可不填，如 500mg/片）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 怎么吃（三段；新建不预选）
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("怎么吃", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        MedUsage.SHORT_COURSE to "短期疗程",
                        MedUsage.LONG_TERM to "长期吃",
                        MedUsage.AS_NEEDED to "需要时吃"
                    ).forEachIndexed { i, (u, label) ->
                        SegmentedButton(
                            selected = st.usage == u,
                            onClick = { cb.onUsage(u) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = i, count = 3
                            )
                        ) { Text(label) }
                    }
                }
                when (st.usage) {
                    MedUsage.SHORT_COURSE -> {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = st.courseDaysText,
                            onValueChange = cb.onCourseDaysText,
                            label = { Text("记录的疗程天数") },
                            singleLine = true,
                            enabled = !st.coursePending,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = st.coursePending,
                                onCheckedChange = cb.onCoursePending
                            )
                            Text(
                                "疗程待确认（还没问清楚吃几天）",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (st.coursePending) {
                            Text(
                                "疗程待确认，请向医生或药师核对",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalJrxColors.current.warning
                            )
                        }
                    }
                    MedUsage.LONG_TERM -> {
                        Spacer(Modifier.height(4.dp))
                        MedDateRow(
                            label = "下次拿药日（可不填）",
                            value = st.refillDate?.let { mdDate(it) } ?: "不填",
                            icon = Icons.Rounded.CalendarMonth,
                            onClick = { refillDialog = true }
                        )
                    }
                    else -> Unit
                }
            }
            // 开始日期
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                MedDateRow(
                    label = "开始日期",
                    value = cnFullDate(st.startDate),
                    icon = Icons.Rounded.CalendarMonth,
                    onClick = { startDialog = true }
                )
            }
            // 每天几点吃（需要时吃不显示）
            if (st.usage != null && st.usage != MedUsage.AS_NEEDED) {
                JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("每天几点吃（可不填）",
                        style = MaterialTheme.typography.titleSmall)
                    if (st.times.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            st.times.forEach { minute ->
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = {
                                        Text("%02d:%02d".format(
                                            minute / 60, minute % 60))
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "删除时刻",
                                            modifier = Modifier.clickable {
                                                cb.onRemoveTime(minute)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { timeDialog = true },
                        enabled = st.times.size < MED_TIMES_MAX
                    ) { Text("添加时刻") }
                }
            }
            // 饭前饭后
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("饭前饭后", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        null to "不填", MedMeal.BEFORE to "饭前",
                        MedMeal.AFTER to "饭后", MedMeal.WITH to "随餐"
                    ).forEachIndexed { i, (m, label) ->
                        SegmentedButton(
                            selected = st.meal == m,
                            onClick = { cb.onMeal(m) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = i, count = 4
                            )
                        ) { Text(label) }
                    }
                }
            }
            // 医嘱备注
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                OutlinedTextField(
                    value = st.note,
                    onValueChange = cb.onNote,
                    label = { Text("医嘱备注（可不填）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "${st.note.length}/$MED_NOTE_MAX",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (startDialog) {
        JrxDatePickerDialog(
            current = st.startDate,
            onPick = { cb.onStartDate(it); startDialog = false },
            onDismiss = { startDialog = false }
        )
    }
    if (refillDialog) {
        JrxDatePickerDialog(
            current = st.refillDate,
            onPick = { cb.onRefillDate(it); refillDialog = false },
            onDismiss = { refillDialog = false },
            extraDismissButton = {
                if (st.refillDate != null) {
                    TextButton(onClick = {
                        cb.onRefillDate(null); refillDialog = false
                    }) { Text("清除") }
                }
            }
        )
    }
    if (timeDialog) {
        MedTimeDialog(
            onPick = { cb.onAddTime(it); timeDialog = false },
            onDismiss = { timeDialog = false }
        )
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除这条用药记录？") },
            text = { Text("删除后不能恢复。") },
            confirmButton = {
                TextButton(onClick = { deleteConfirm = false; cb.onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text("取消") }
            }
        )
    }
    if (st.confirmLongTerm) {
        AlertDialog(
            onDismissRequest = cb.onDismissLongTerm,
            title = { Text("请确认这是医生交代的长期用药") },
            text = {
                Text("长期吃的药会按“下次拿药日”提醒拿药。如果医生只开了几天，请选“短期疗程”。")
            },
            confirmButton = {
                TextButton(onClick = cb.onConfirmLongTerm) { Text("是医生交代的") }
            },
            dismissButton = {
                TextButton(onClick = cb.onDismissLongTerm) { Text("再想想") }
            }
        )
    }
}

/** 已有记录的状态卡：COURSE_ENDED / ACTIVE / STOPPED 三态（规格 §6.2）。 */
@Composable
private fun MedStatusCard(
    med: io.github.xiaohaoz1.jiarenxia.core.model.Medication,
    today: LocalDate,
    courseEndedHint: Boolean,
    onMarkStopped: () -> Unit,
    onClearStopped: () -> Unit,
    onCourseEndedHint: () -> Unit
) {
    val status = MedSchedule.statusOf(med, today)
    JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        when (status) {
            MedSchedule.Status.COURSE_ENDED -> {
                Text(
                    "记录疗程已到期，实际停用待确认",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalJrxColors.current.warning
                )
                Row {
                    TextButton(onClick = onMarkStopped) { Text("记录已停用") }
                    TextButton(onClick = onCourseEndedHint) { Text("医生让继续吃") }
                }
                if (courseEndedHint) {
                    Text(
                        "改一下下面的疗程天数，或改成长期吃，再保存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            MedSchedule.Status.ACTIVE -> {
                TextButton(onClick = onMarkStopped) { Text("记录已停用") }
            }
            MedSchedule.Status.STOPPED -> {
                val stop = med.stoppedEpochDay?.let { LocalDate.ofEpochDay(it) }
                Text(
                    (stop?.let { mdDate(it) } ?: "") + "记录停用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearStopped) { Text("重新开始吃") }
            }
        }
    }
}

/** 日期/时刻行：左标签 + 右值（primary）+ 18dp 图标，整行 48dp 可点。 */
@Composable
private fun MedDateRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp).height(18.dp)
        )
    }
}

/** 服用时刻选择：TimePicker（同事项提醒时刻同款）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedTimeDialog(
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = 8, initialMinute = 0, is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加时刻") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
