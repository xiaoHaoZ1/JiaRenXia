package io.github.xiaohaoz1.jiarenxia.ui.checklist

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistItem
import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.data.ChecklistWithItemsModel
import io.github.xiaohaoz1.jiarenxia.domain.checklist.ChecklistTemplates
import io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule
import io.github.xiaohaoz1.jiarenxia.ui.common.JrxDatePickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.person.SectionTitle
import java.time.LocalDate
import kotlinx.coroutines.launch

/** 清单页回调集合（截图测试可传全空）。 */
data class ChecklistCallbacks(
    val onToggle: (ChecklistItem, Boolean) -> Unit = { _, _ -> },
    val onAddItem: (groupName: String, title: String, personId: String?) -> Unit =
        { _, _, _ -> },
    val onRenameItem: (item: ChecklistItem, title: String) -> Unit = { _, _ -> },
    val onDeleteItem: (ChecklistItem) -> Unit = {},
    /** 「打开事项」：跳事项编辑页。 */
    val onOpenTask: (taskId: String) -> Unit = {},
    /** 「定日期」/「再定一次日期」选定日期后回调。 */
    val onSetTaskDate: (item: ChecklistItem, date: LocalDate) -> Unit = { _, _ -> },
    val onDeleteChecklist: () -> Unit = {},
    val onNoticeShown: () -> Unit = {},
    val onErrorShown: () -> Unit = {}
)

/** 「添加」目标：组名 + 标题 + 归属人物（POSTNATAL 按卡继承）。 */
private data class AddTarget(
    val groupName: String,
    val cardTitle: String,
    val personId: String?
)

/** 清单页（FAMILY_AFFAIRS 26）：待产包 / 出生后要办的事的查看与办理。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    onDone: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: ChecklistViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    ChecklistContent(
        st = st,
        cb = ChecklistCallbacks(
            onToggle = { item, checked -> viewModel.setChecked(item.id, checked) },
            onAddItem = { g, t, p -> viewModel.addItem(g, t, p) },
            onRenameItem = { item, title -> viewModel.renameItem(item.id, title) },
            onDeleteItem = viewModel::deleteItem,
            onOpenTask = onOpenTask,
            onSetTaskDate = viewModel::setTaskDate,
            onDeleteChecklist = viewModel::deleteChecklist,
            onNoticeShown = viewModel::onNoticeShown,
            onErrorShown = viewModel::onErrorShown
        ),
        onDone = onDone
    )
}

/** 清单页无状态主体（截图测试直接喂状态）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistContent(
    st: ChecklistUiState,
    cb: ChecklistCallbacks,
    onDone: () -> Unit
) {
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(st.deleted) { if (st.deleted) onDone() }
    LaunchedEffect(st.notice) {
        st.notice?.let { snack.showSnackbar(it); cb.onNoticeShown() }
    }
    LaunchedEffect(st.error) {
        st.error?.let { snack.showSnackbar(it); cb.onErrorShown() }
    }

    var showDeleteChecklist by remember { mutableStateOf(false) }
    var addTarget by remember { mutableStateOf<AddTarget?>(null) }
    var renameTarget by remember { mutableStateOf<ChecklistItem?>(null) }
    var dateTarget by remember { mutableStateOf<ChecklistItem?>(null) }

    val model = st.model
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(model?.checklist?.title ?: "清单") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (model != null) {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "清单操作"
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("删除这张清单") },
                                    onClick = {
                                        menuOpen = false
                                        showDeleteChecklist = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        if (model == null) {
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                if (st.loading) {
                    Text(
                        "加载中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    Text(
                        "清单不存在",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        } else {
            ChecklistBody(
                st = st,
                padding = padding,
                onToggle = cb.onToggle,
                onAddClick = { addTarget = it },
                onRename = { renameTarget = it },
                onDeleteItem = cb.onDeleteItem,
                onOpenTask = cb.onOpenTask,
                onSetTaskDate = { dateTarget = it }
            )
        }
    }

    // 删除整张清单确认
    if (showDeleteChecklist) {
        ChecklistDeleteDialog(
            title = model?.checklist?.title ?: "清单",
            onConfirm = {
                showDeleteChecklist = false
                cb.onDeleteChecklist()
            },
            onDismiss = { showDeleteChecklist = false }
        )
    }
    // 添加条目（POSTNATAL 按卡继承 group/person）
    addTarget?.let { target ->
        ChecklistItemEditDialog(
            title = "添加到「" + target.cardTitle + "」",
            initial = "",
            confirmText = "保存",
            onConfirm = { title ->
                addTarget = null
                cb.onAddItem(target.groupName, title, target.personId)
            },
            onDismiss = { addTarget = null }
        )
    }
    // 改名
    renameTarget?.let { item ->
        ChecklistItemEditDialog(
            title = "改名",
            initial = item.title,
            confirmText = "保存",
            onConfirm = { title ->
                renameTarget = null
                cb.onRenameItem(item, title)
            },
            onDismiss = { renameTarget = null }
        )
    }
    // 「定日期」/「再定一次日期」
    dateTarget?.let { item ->
        JrxDatePickerDialog(
            current = st.tasks[item.taskId]?.let {
                io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule.dueDate(it)
            } ?: st.today,
            onPick = { date ->
                dateTarget = null
                cb.onSetTaskDate(item, date)
            },
            onDismiss = { dateTarget = null }
        )
    }
}

/** 清单正文：出处说明、进度、分组卡片（待产包）或人物卡（出生后要办的事）。 */
@Composable
private fun ChecklistBody(
    st: ChecklistUiState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onToggle: (ChecklistItem, Boolean) -> Unit,
    onAddClick: (AddTarget) -> Unit,
    onRename: (ChecklistItem) -> Unit,
    onDeleteItem: (ChecklistItem) -> Unit,
    onOpenTask: (String) -> Unit,
    onSetTaskDate: (ChecklistItem) -> Unit
) {
    val model = st.model ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val kind = model.checklist.kind
    val postnatal = kind == ChecklistKind.POSTNATAL

    Box(Modifier.fillMaxSize().padding(padding)) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // 出处说明
        if (kind == ChecklistKind.BIRTH_BAG) {
            Text(
                "条目参考" + ChecklistTemplates.BIRTH_BAG_PUBLISHER +
                    "发布的待产包清单，具体以分娩医院的要求为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(ChecklistTemplates.BIRTH_BAG_SOURCE_URL)
                        )
                    )
                } catch (e: ActivityNotFoundException) {
                    scope.launch {
                        snack.showSnackbar("没有可以打开链接的应用")
                    }
                }
            }) { Text("查看出处") }
        } else {
            Text(
                "很多地方可以在「新生儿出生一件事」里一次办好出生医学证明、" +
                    "预防接种证、户口登记、社保卡和医保参保，具体以当地为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(ChecklistTemplates.POSTNATAL_PLATFORM_URL)
                        )
                    )
                } catch (e: ActivityNotFoundException) {
                    scope.launch {
                        snack.showSnackbar("没有可以打开链接的应用")
                    }
                }
            }) { Text("打开国家政务服务平台") }
        }
        // 进度：待产包「已备 N / M / 都备齐了」，出生后「已办 N / M / 都办好了」
        val total = model.items.size
        val done = model.items.count { item ->
            item.taskId?.let { st.tasks[it]?.done } ?: item.checked
        }
        Text(
            when {
                total == 0 -> "还没有条目"
                done == total -> if (postnatal) "都办好了" else "都备齐了"
                else -> (if (postnatal) "已办 " else "已备 ") + done + " / " + total
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (total > 0 && done == total)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        if (postnatal) {
            val cards = buildPostnatalCards(
                model,
                personName = { st.personNames[it] },
                birthDate = { st.birthDates[it] }
            )
            cards.forEach { card ->
                JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    SectionTitle(card.title)
                    card.items.forEach { item ->
                        ChecklistItemRow(
                            item = item,
                            task = item.taskId?.let { st.tasks[it] },
                            today = st.today,
                            onToggle = onToggle,
                            onRename = onRename,
                            onDelete = onDeleteItem,
                            onOpenTask = onOpenTask,
                            onSetTaskDate = onSetTaskDate
                        )
                    }
                    TextButton(onClick = {
                        onAddClick(
                            AddTarget(card.groupName, card.title, card.personId)
                        )
                    }) { Text("添加") }
                }
            }
        } else {
            // 组顺序：模板组序在前，未知组按出现顺序殿后
            val templateGroups = ChecklistTemplates.groupsOf(kind)
            val byGroup = model.items.groupBy { it.groupName }
            val groups = templateGroups + byGroup.keys.filter { it !in templateGroups }
            groups.forEach { group ->
                val items = byGroup[group].orEmpty()
                JrxCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    SectionTitle(group)
                    if (items.isEmpty()) {
                        Text(
                            "这一组还没有东西",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        items.forEach { item ->
                            ChecklistItemRow(
                                item = item,
                                task = item.taskId?.let { st.tasks[it] },
                                today = st.today,
                                onToggle = onToggle,
                                onRename = onRename,
                                onDelete = onDeleteItem,
                                onOpenTask = onOpenTask,
                                onSetTaskDate = onSetTaskDate
                            )
                        }
                    }
                    TextButton(onClick = {
                        onAddClick(AddTarget(group, group, null))
                    }) { Text("添加") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    // 「查看出处」失败提示（挂在正文层，避免与页级 snackbar 冲突）
    SnackbarHost(snack)
    }
}

/**
 * 单条清单行：勾选框 + 标题/备注 + ⋮。
 * 有日期的条目（task 非空）以事项为准：勾选框显示事项已办且不可点，
 * 行点击打开事项编辑页，菜单为「打开事项/改名/删除」（已办加「再定一次日期」）。
 */
@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    task: FamilyTask?,
    today: LocalDate,
    onToggle: (ChecklistItem, Boolean) -> Unit,
    onRename: (ChecklistItem) -> Unit,
    onDelete: (ChecklistItem) -> Unit,
    onOpenTask: (String) -> Unit,
    onSetTaskDate: (ChecklistItem) -> Unit
) {
    val dated = task != null
    val done = task?.done ?: item.checked
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = done,
                enabled = !dated,
                role = Role.Checkbox,
                onValueChange = { onToggle(item, it) }
            )
            .padding(vertical = 4.dp)
    ) {
        Checkbox(checked = done, onCheckedChange = null, enabled = !dated)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
            )
            item.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (dated) {
                val due = TaskSchedule.dueDate(task)
                val days = TaskSchedule.daysUntil(due, today)
                val rel = when {
                    days == 0 -> "今天"
                    days > 0 -> days.toString() + " 天后"
                    else -> "已逾期 " + (-days) + " 天"
                }
                Text(
                    due.monthValue.toString() + "月" + due.dayOfMonth + "日 · " + rel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (days < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        var menuOpen by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "条目操作：" + item.title
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                if (dated) {
                    DropdownMenuItem(
                        text = { Text("打开事项") },
                        onClick = { menuOpen = false; onOpenTask(task.id) }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("定日期") },
                        onClick = { menuOpen = false; onSetTaskDate(item) }
                    )
                }
                if (dated && task.done) {
                    DropdownMenuItem(
                        text = { Text("再定一次日期") },
                        onClick = { menuOpen = false; onSetTaskDate(item) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("改名") },
                    onClick = { menuOpen = false; onRename(item) }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    onClick = { menuOpen = false; onDelete(item) }
                )
            }
        }
    }
}

/** 删除整张清单的确认框（截图测试直接渲染）。 */
@Composable
internal fun ChecklistDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除" + title + "清单？") },
        text = {
            Text("清单和勾选记录都会删除，不能恢复。之后可以在孕期卡上重新准备。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 添加 / 改名共用对话框：单行输入，≤30 字，trim 后为空不可保存。 */
@Composable
internal fun ChecklistItemEditDialog(
    title: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 30) text = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.trim().isNotEmpty(),
                onClick = { onConfirm(text.trim()) }
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
