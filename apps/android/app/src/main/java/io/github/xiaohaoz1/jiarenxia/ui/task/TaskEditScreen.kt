package io.github.xiaohaoz1.jiarenxia.ui.task

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import io.github.xiaohaoz1.jiarenxia.core.model.TASK_OFFSETS
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderTiming
import io.github.xiaohaoz1.jiarenxia.reminder.TaskTemplates
import io.github.xiaohaoz1.jiarenxia.ui.common.JrxDatePickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 编辑页回调集合（截图测试可传全空）。 */
data class TaskEditCallbacks(
    val onKind: (TaskKind) -> Unit = {},
    val onPerson: (String?) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onDate: (LocalDate) -> Unit = {},
    val onRepeat: (TaskRepeat) -> Unit = {},
    val onOffset: (Int) -> Unit = {},
    /** null = 跟随家事提醒时刻设置。 */
    val onRemindMinutes: (Int?) -> Unit = {},
    val onAmount: (String) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onEnabled: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDismissError: () -> Unit = {}
)

/** 事项编辑页（FAMILY_AFFAIRS 5.2）：模板、关联人、标题、日期、重复、提前提醒、金额、备注、开关。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditScreen(
    onDone: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    TaskEditContent(
        st = st,
        cb = TaskEditCallbacks(
            onKind = viewModel::setKind,
            onPerson = viewModel::setPerson,
            onTitle = viewModel::setTitle,
            onDate = viewModel::setDate,
            onRepeat = viewModel::setRepeat,
            onOffset = viewModel::toggleOffset,
            onRemindMinutes = viewModel::setRemindMinutes,
            onAmount = viewModel::setAmount,
            onNote = viewModel::setNote,
            onEnabled = viewModel::setEnabled,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismissError = viewModel::dismissError
        ),
        onDone = onDone
    )
}

/** 事项编辑页无状态主体（截图测试直接喂状态）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditContent(
    st: TaskEditUiState,
    cb: TaskEditCallbacks,
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

    var templateDialog by remember { mutableStateOf(false) }
    var personDialog by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }

    // 固定底栏缺项定位：标题卡 / 提前提醒卡
    val titleBring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val offsetBring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val missing = when {
        st.title.isBlank() -> "还差：标题" to titleBring
        st.offsets.isEmpty() -> "还差：至少一项提前提醒" to offsetBring
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (st.taskId == null) "新建事项" else "编辑事项") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        bottomBar = {
            io.github.xiaohaoz1.jiarenxia.ui.common.FormActionBar(
                primaryText = "保存",
                onPrimary = cb.onSave,
                primaryEnabled = missing == null,
                busy = st.saving,
                missingText = missing?.first,
                onMissingClick = missing?.let { (_, req) ->
                    { scope.launch { req.bringIntoView() } }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // 模板（可改）
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                EditRow("模板", TaskTemplates.of(st.kind).displayName) {
                    templateDialog = true
                }
                if (st.templateHint.isNotEmpty()) {
                    Text(
                        st.templateHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            // 关联人（可空 = 全家）
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                EditRow("关联人", st.personLabel()) { personDialog = true }
            }
            // 标题
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)
                .bringIntoViewRequester(titleBring)) {
                OutlinedTextField(
                    value = st.title,
                    onValueChange = cb.onTitle,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 日期
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                EditRow(
                    "日期",
                    st.anchorDate.year.toString() + "年" + st.anchorDate.monthValue + "月" +
                        st.anchorDate.dayOfMonth + "日"
                ) { dateDialog = true }
            }
            // 重复
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text("重复", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(TaskRepeat.NONE to "不重复", TaskRepeat.MONTHLY to "每月",
                        TaskRepeat.YEARLY to "每年").forEachIndexed { i, (r, label) ->
                        SegmentedButton(
                            selected = st.repeat == r,
                            onClick = { cb.onRepeat(r) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = i, count = 3
                            )
                        ) { Text(label) }
                    }
                }
            }
            // 提前提醒（至少一项）
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)
                .bringIntoViewRequester(offsetBring)) {
                Text("提前提醒", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                // FlowRow：胶囊按内容宽度排列、自动换行到下一行，文字不折行
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TASK_OFFSETS.sorted().forEach { d ->
                        FilterChip(
                            selected = d in st.offsets,
                            onClick = { cb.onOffset(d) },
                            label = {
                                Text(
                                    if (d == 0) "当天" else "提前 " + d + " 天",
                                    maxLines = 1, softWrap = false
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 一天内提醒时刻：null 跟随「家事提醒时刻」全局设置
                EditRow(
                    "提醒时刻",
                    st.remindMinutes?.let { ReminderTiming.format(it) }
                        ?: "跟随默认（家事提醒时刻）"
                ) { timeDialog = true }
            }
            // 金额 + 备注 + 开关
            JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                OutlinedTextField(
                    value = st.amountText,
                    onValueChange = cb.onAmount,
                    label = { Text("金额（元，可空）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = st.note,
                    onValueChange = cb.onNote,
                    label = { Text("备注（可空，200 字内）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("提醒开关", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall)
                    Switch(checked = st.enabled, onCheckedChange = cb.onEnabled)
                }
            }
            // 已有事项：上次办理 + 删除
            if (st.taskId != null) {
                st.lastDoneDate?.let { last ->
                    Text(
                        "上次办理：" + last.year + "年" + last.monthValue + "月" +
                            last.dayOfMonth + "日",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                TextButton(
                    onClick = { deleteConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("删除事项", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (templateDialog) {
        SelectDialog(
            title = "选择模板",
            options = TaskTemplates.ALL.map { it.kind.name to it.displayName },
            selected = st.kind.name,
            onPick = { cb.onKind(TaskKind.valueOf(it)); templateDialog = false },
            onDismiss = { templateDialog = false }
        )
    }
    if (personDialog) {
        io.github.xiaohaoz1.jiarenxia.ui.common.PersonPickerDialog(
            title = "关联人",
            items = st.persons,
            selectedId = st.personId,
            onPick = { cb.onPerson(it.personId); personDialog = false },
            onDismiss = { personDialog = false },
            leadingContent = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = st.personId == null,
                            onClick = {
                                cb.onPerson(null); personDialog = false
                            }
                        )
                        .padding(vertical = 6.dp)
                ) {
                    Icon(
                        if (st.personId == null) Icons.Rounded.CheckCircle
                        else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (st.personId == null)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("全家（不关联）")
                }
            }
        )
    }
    if (dateDialog) {
        JrxDatePickerDialog(
            current = st.anchorDate,
            onPick = { cb.onDate(it) },
            onDismiss = { dateDialog = false }
        )
    }
    if (timeDialog) {
        TaskTimeDialog(
            current = st.remindMinutes,
            onPick = { cb.onRemindMinutes(it); timeDialog = false },
            onFollowDefault = { cb.onRemindMinutes(null); timeDialog = false },
            onDismiss = { timeDialog = false }
        )
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除这个事项？") },
            text = {
                Text("删除后不再提醒，已办记录一并清除。")
            },
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
}

/** 编辑页通用行：标题 + 当前值 + 下拉箭头，整行可点。 */
@Composable
private fun EditRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(64.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Rounded.KeyboardArrowDown, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 事项的一天内提醒时刻选择：确定写入分钟数，「跟随默认」回到家事提醒时刻设置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTimeDialog(
    current: Int?,
    onPick: (Int) -> Unit,
    onFollowDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    val initial = ReminderTiming.timeOf(current ?: ReminderTiming.DEFAULT_MINUTE_OF_DAY)
    val state = rememberTimePickerState(
        initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒时刻") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "该事项在每个提醒日的这个时间提醒",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("确定") }
        },
        dismissButton = {
            Row {
                // 已有自定义时刻时才需要「跟随默认」复位
                if (current != null) {
                    TextButton(onClick = onFollowDefault) { Text("跟随默认") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 单选对话框（模板 / 关联人）。 */
@Composable
private fun SelectDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = key == selected) { onPick(key) }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(
                            if (key == selected) Icons.Rounded.CheckCircle
                            else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (key == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

