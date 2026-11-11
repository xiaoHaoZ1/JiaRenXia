package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.Pregnancy
import io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
import io.github.xiaohaoz1.jiarenxia.data.ChecklistProgress
import io.github.xiaohaoz1.jiarenxia.domain.pregnancy.PregnancyMath
import io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule
import io.github.xiaohaoz1.jiarenxia.ui.common.JrxDatePickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val MD_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
private val CN_FULL_DATE: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日")

/** “M月D日”（历史/产检行内日期）。 */
internal fun mdDate(d: LocalDate): String = d.format(MD_DATE)

/** “yyyy年M月d日”（孕期日期全文）。 */
internal fun cnFullDate(d: LocalDate): String = d.format(CN_FULL_DATE)

/**
 * 孕期卡片（FAMILY_AFFAIRS 26.7.2）：孕周、预产期、末次月经、
 * 下次产检与“记下次产检”入口；右上角菜单：改预产期 / 结束 / 删除。
 *
 * @param nextCheckup 该孕期未办未归档事项中到期日最早的一条（可为 null）
 * @param birthBag 该孕期待产包进度（无清单为 null；点击由调用方决定
 *   直接进清单页还是先按模板创建）
 */
@Composable
fun PregnancyCard(
    pregnancy: Pregnancy,
    nextCheckup: FamilyTask?,
    today: LocalDate,
    onEditDates: () -> Unit,
    onEnd: () -> Unit,
    onDelete: () -> Unit,
    onOpenTask: (String) -> Unit,
    onAddCheckup: () -> Unit,
    birthBag: ChecklistProgress? = null,
    onOpenBirthBag: () -> Unit = {}
) {
    JrxCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SectionTitle("孕期")
            Spacer(Modifier.weight(1f))
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "孕期操作"
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("改预产期") },
                        onClick = { menuOpen = false; onEditDates() }
                    )
                    DropdownMenuItem(
                        text = { Text("结束这条孕期记录") },
                        onClick = { menuOpen = false; onEnd() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除这条记录") },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }
        // 孕周
        Text(
            PregnancyMath.gestationalText(pregnancy.dueDate, today),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        // 预产期行
        Text(
            PregnancyMath.dueLineText(pregnancy.dueDate, today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 末次月经（有才显示）
        pregnancy.lmpDate?.let { lmp ->
            Spacer(Modifier.height(2.dp))
            Text(
                PregnancyMath.lmpLineText(lmp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(
            Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        // 下次产检
        if (nextCheckup != null) {
            val due = TaskSchedule.dueDate(nextCheckup)
            val days = TaskSchedule.daysUntil(due, today)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .clickable { onOpenTask(nextCheckup.id) }
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    "下次产检",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    mdDate(due) + " · " + when {
                        days < 0 -> "已逾期 " + (-days) + " 天"
                        days == 0 -> "今天"
                        else -> days.toString() + " 天后"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (days < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                "还没有记下次产检",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        // 待产包入口（下次产检之后、「记下次产检」之前）
        val bagText: Pair<String, androidx.compose.ui.graphics.Color> = when {
            birthBag == null -> "还没准备" to
                MaterialTheme.colorScheme.onSurfaceVariant
            birthBag.totalCount == 0 -> "还没有条目" to
                MaterialTheme.colorScheme.onSurfaceVariant
            birthBag.checkedCount == birthBag.totalCount -> "都备齐了" to
                MaterialTheme.colorScheme.primary
            else -> "已备 " + birthBag.checkedCount + " / " +
                birthBag.totalCount to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {}
                .clickable { onOpenBirthBag() }
                .padding(vertical = 6.dp)
        ) {
            Text(
                "待产包",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                bagText.first,
                style = MaterialTheme.typography.bodyMedium,
                color = bagText.second
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        TextButton(onClick = onAddCheckup) { Text("记下次产检") }
    }
}

/**
 * 孕期记录（历史）卡：BORN / ENDED 的孕期逐行列出，点击看详情。
 */
@Composable
fun PregnancyHistoryCard(
    pregnancies: List<Pregnancy>,
    onOpen: (Pregnancy) -> Unit
) {
    val ended = pregnancies.filter { it.status != PregnancyStatus.ACTIVE }
    if (ended.isEmpty()) return
    JrxCard(Modifier.fillMaxWidth()) {
        SectionTitle("孕期记录")
        ended.forEach { p ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clickable { onOpen(p) }
                    .padding(vertical = 6.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "预产期 " + cnFullDate(p.dueDate),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        when (p.status) {
                            PregnancyStatus.ENDED ->
                                "已结束" + (p.endedOn?.let { " · " + cnFullDate(it) } ?: "")
                            PregnancyStatus.BORN ->
                                "宝宝已出生" +
                                    (p.endedOn?.let { " · " + cnFullDate(it) } ?: "")
                            PregnancyStatus.ACTIVE -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 新建 / 改预产期对话框（FAMILY_AFFAIRS 26.7.3）：
 * “填预产期”与“填末次月经”互斥；末次月经经 +280 天推导预产期。
 *
 * @param mode 初始录入方式（新建默认 PRE_DUE；修改按原记录决定）
 * @param existing 改预产期时的原记录（新建为 null）
 * @param initialPicked 初始选中日期（截图/测试预填；null 时按 existing 推导）
 * @param onSave 保存回调：(推导后的预产期, 末次月经?)
 */
@Composable
fun PregnancyDateDialog(
    title: String,
    mode: PregnancyInputMode,
    existing: Pregnancy?,
    today: LocalDate,
    onSave: (due: LocalDate, lmp: LocalDate?) -> Unit,
    onDismiss: () -> Unit,
    initialPicked: LocalDate? = null
) {
    var inputMode by remember { mutableStateOf(mode) }
    // 选中的日期：切换录入方式时重置（预填由 existing 决定）
    var picked by remember {
        mutableStateOf(
            initialPicked ?: when (mode) {
                PregnancyInputMode.PRE_DUE -> existing?.dueDate
                PregnancyInputMode.LMP -> existing?.lmpDate
            }
        )
    }
    var showPicker by remember { mutableStateOf(false) }

    // 推导结果与合法性
    val derived: Pair<LocalDate, LocalDate?>? = picked?.let { d ->
        when (inputMode) {
            PregnancyInputMode.PRE_DUE -> d to existing?.lmpDate
            PregnancyInputMode.LMP ->
                PregnancyMath.dueFromLmp(d) to d
        }
    }
    val valid = derived != null && when (inputMode) {
        PregnancyInputMode.PRE_DUE ->
            PregnancyMath.isDueInValidRange(derived.first, today)
        PregnancyInputMode.LMP ->
            PregnancyMath.isLmpInValidRange(derived.second!!, today)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // 互斥选项
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = inputMode == PregnancyInputMode.PRE_DUE,
                        onClick = {
                            inputMode = PregnancyInputMode.PRE_DUE
                            picked = existing?.dueDate
                        }
                    )
                    Text(
                        "填预产期",
                        modifier = Modifier.clickable {
                            inputMode = PregnancyInputMode.PRE_DUE
                            picked = existing?.dueDate
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = inputMode == PregnancyInputMode.LMP,
                        onClick = {
                            inputMode = PregnancyInputMode.LMP
                            picked = existing?.lmpDate
                        }
                    )
                    Text(
                        "填末次月经",
                        modifier = Modifier.clickable {
                            inputMode = PregnancyInputMode.LMP
                            picked = existing?.lmpDate
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                // 日期字段（整行可点：primary 色值 + 日历图标）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) {}
                        .clickable { showPicker = true }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        if (inputMode == PregnancyInputMode.PRE_DUE)
                            "预产期" else "末次月经第一天",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        picked?.let { cnFullDate(it) } ?: "选择日期",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // 预览 / 错误
                if (derived != null) {
                    if (valid) {
                        Text(
                            when (inputMode) {
                                PregnancyInputMode.PRE_DUE ->
                                    "现在" + PregnancyMath.gestationalText(
                                        derived.first, today)
                                PregnancyInputMode.LMP ->
                                    "预产期 " + cnFullDate(derived.first) +
                                        " · 现在" + PregnancyMath
                                        .gestationalText(derived.first, today)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        Text(
                            when (inputMode) {
                                PregnancyInputMode.PRE_DUE ->
                                    "预产期要在 6 周前到 40 周后之间"
                                PregnancyInputMode.LMP ->
                                    "末次月经要在 46 周前到今天之间"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "孕周按预产期倒推 280 天计算，以医院产检为准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    derived?.let { (due, lmp) -> onSave(due, lmp) }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
    if (showPicker) {
        JrxDatePickerDialog(
            current = picked,
            onPick = { picked = it; showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/** 录入方式：直接填预产期 / 填末次月经（推导预产期）。 */
enum class PregnancyInputMode { PRE_DUE, LMP }

/** 初始录入方式：新建默认预产期；修改时有末次月经用末次月经。 */
internal fun initialPregnancyMode(existing: Pregnancy?): PregnancyInputMode =
    if (existing?.lmpDate != null) PregnancyInputMode.LMP
    else PregnancyInputMode.PRE_DUE

/** 结束孕期确认（FAMILY_AFFAIRS 26.7.4）。 */
@Composable
fun PregnancyEndDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("结束这条孕期记录？") },
        text = {
            Text(
                "结束后不再显示孕周。这次孕期还没完成的产检会停止提醒并归档，" +
                    "记录保留在「孕期记录」里。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("结束记录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 删除孕期记录确认（活动卡片与历史对话框共用，FAMILY_AFFAIRS 26.7.5）。 */
@Composable
fun PregnancyDeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除这条孕期记录？") },
        text = {
            Text("孕期记录和它的产检事项都会删除，不能恢复。")
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

/**
 * 孕期记录详情对话框（FAMILY_AFFAIRS 26.7.6）：
 * 预产期 / 末次月经 / 结束信息 + 该孕期全部产检事项（含已办与已归档）。
 *
 * @param tasks 由调用方订阅 observeTasksForPregnancy 得到
 * @param birthBag 该孕期待产包进度（有清单才显示「待产包」小节）
 */
@Composable
fun PregnancyHistoryDialog(
    pregnancy: Pregnancy,
    tasks: List<FamilyTask>,
    today: LocalDate,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    birthBag: ChecklistProgress? = null,
    onOpenBirthBag: (ChecklistProgress) -> Unit = {}
) {
    var showDelete by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("孕期记录") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "预产期 " + cnFullDate(pregnancy.dueDate),
                    style = MaterialTheme.typography.bodyMedium
                )
                pregnancy.lmpDate?.let {
                    Text(
                        "末次月经 " + cnFullDate(it),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                when (pregnancy.status) {
                    PregnancyStatus.ENDED -> Text(
                        "已结束于" +
                            (pregnancy.endedOn?.let { " " + cnFullDate(it) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    PregnancyStatus.BORN -> Text(
                        "宝宝出生于" +
                            (pregnancy.endedOn?.let { " " + cnFullDate(it) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    PregnancyStatus.ACTIVE -> {}
                }
                Spacer(Modifier.height(8.dp))
                Text("产检", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                if (tasks.isEmpty()) {
                    Text(
                        "没有产检记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    tasks.forEach { t ->
                        val due = TaskSchedule.dueDate(t)
                        val state = when {
                            t.done -> "已办"
                            t.archived -> "已归档"
                            else -> "未完成"
                        }
                        Text(
                            t.title + " · " + mdDate(due) + " · " + state,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                // 待产包小节（有清单才显示；点击关闭对话框跳清单页）
                birthBag?.let { bag ->
                    Spacer(Modifier.height(8.dp))
                    Text("待产包", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        onDismiss()
                        onOpenBirthBag(bag)
                    }) {
                        Text(
                            "查看待产包（已备 " + bag.checkedCount +
                                " / " + bag.totalCount + "）"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = { showDelete = true }) {
                Text("删除这条记录", color = MaterialTheme.colorScheme.error)
            }
        }
    )
    if (showDelete) {
        PregnancyDeleteDialog(
            onConfirm = { showDelete = false; onDelete() },
            onDismiss = { showDelete = false }
        )
    }
}
