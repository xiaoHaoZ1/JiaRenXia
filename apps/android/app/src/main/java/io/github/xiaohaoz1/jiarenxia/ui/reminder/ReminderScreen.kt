package io.github.xiaohaoz1.jiarenxia.ui.reminder

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import io.github.xiaohaoz1.jiarenxia.domain.DateRow
import io.github.xiaohaoz1.jiarenxia.reminder.BirthdayRow
import io.github.xiaohaoz1.jiarenxia.reminder.TaskTemplates
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.CountdownBadge
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.EmptyState
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * 提醒段副标题：“未来 30 天有 N 个生日、M 件事”（事项含逾期）。
 * 放在文件级以便 ReminderContent 截图测试与页头共用同一文案。
 */
internal fun reminderSubtitle(st: ReminderUiState): String {
    val bd = st.today.size + st.upcoming.size
    val an = st.dateRows.size + st.dateRowsToday.size
    val tk = st.tasksToday.size + st.tasksNext30.size + st.tasksLater.size
    val parts = mutableListOf<String>()
    if (bd > 0) parts += bd.toString() + " 个生日"
    if (an > 0) parts += an.toString() + " 个纪念日"
    if (tk > 0) parts += tk.toString() + " 件事"
    if (st.contactDue.isNotEmpty()) {
        parts += st.contactDue.size.toString() + " 位该联系了"
    }
    return if (parts.isEmpty()) "未来 30 天没有生日和事项"
    else "未来 30 天有 " + parts.joinToString("、")
}

/**
 * 家事页（原提醒页）：渐变页头 + 横幅 + 今天生日卡 + 未来 30 天时间线
 * + 家庭事项（今天要办 / 未来 30 天 / 以后 / 已办）+ “新建事项” FAB。
 * 2026-10-20 起（A6）人情/保障彻底移除，本页只显示提醒内容；
 * 原关联保单的缴费事项保留为普通家庭事项，照常管理与提醒。
 */
@Composable
fun ReminderScreen(
    onOpenPerson: (String) -> Unit,
    onOpenTask: (String) -> Unit = {},
    onNewTask: (String) -> Unit = {},
    /** 顶栏铃铛：三类提醒的开关与时刻（2026-10-09 从「我的」搬来）。 */
    onReminderSettings: () -> Unit = {},
    viewModel: ReminderViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 回到前台时重新检查系统通知开关
    var notifEnabled by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    // 打完电话回来的“联系上了吗”提示（10 分钟窗口）
    io.github.xiaohaoz1.jiarenxia.ui.common.PostCallPromptEffect(
        viewModel.appEvents, viewModel::logCallContact)
    val snack by viewModel.snack.collectAsStateWithLifecycle()
    LaunchedEffect(snack) {
        val (msg, undoable) = snack ?: return@LaunchedEffect
        val res = snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = if (undoable) "撤销" else null,
            duration = SnackbarDuration.Short
        )
        if (res == SnackbarResult.ActionPerformed) viewModel.undoDone()
        viewModel.snackConsumed()
    }

    var templatePickerOpen by remember { mutableStateOf(false) }
    if (templatePickerOpen) {
        TemplatePickerDialog(
            onPick = { templatePickerOpen = false; onNewTask(it.name) },
            onDismiss = { templatePickerOpen = false }
        )
    }

    val scope = rememberCoroutineScope()

    // 导出表格（只导家事提醒）：CreateDocument 保存，初始目录在“下载”
    // 提醒导出的范围参数（起止日期 + 是否含逾期）
    var pendingRange by remember {
        mutableStateOf<Triple<java.time.LocalDate, java.time.LocalDate, Boolean>?>(null)
    }
    // 提醒导出先弹范围对话框
    var exportRangeOpen by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            // count：0 行也写出表头，提示文案区分空载
            val out = pendingRange?.let { (from, to, overdue) ->
                viewModel.buildRemindersCsv(from, to, overdue)
            }
            out?.let { result ->
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(result.csv.toByteArray(Charsets.UTF_8))
                }
                snackbarHostState.showSnackbar(
                    if (result.rowCount == 0) "所选范围内没有内容，已导出表头"
                    else "已导出表格（" + result.rowCount + " 条）"
                )
            }
        }
    }
    if (exportRangeOpen) {
        ReminderExportDialog(
            onConfirm = { from, to, overdue ->
                exportRangeOpen = false
                pendingRange = Triple(from, to, overdue)
                exportLauncher.launch("家事提醒.csv")
            },
            onDismiss = { exportRangeOpen = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        // 外层 MainShell 已管水平向、PageHeader 管状态栏；
        // material3 1.4 默认全量 insets 会再叠一层状态栏留白，这里关掉
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AffairsHeader(
                subtitle = reminderSubtitle(st),
                onExport = { exportRangeOpen = true },
                onReminderSettings = onReminderSettings
            )
            Box(Modifier.fillMaxSize()) {
                ReminderContent(
                    st = st,
                    notifEnabled = notifEnabled,
                    onOpenPerson = onOpenPerson,
                    onDial = { number ->
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))
                        )
                    },
                    onLogContact = viewModel::logContactNow,
                    onPersonDial = { person, label, number ->
                        viewModel.onDialMade(person, label)
                        context.startActivity(
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))
                        )
                    },
                    onNotifSettings = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(
                                        Settings.EXTRA_APP_PACKAGE,
                                        context.packageName
                                    )
                                )
                        }
                    },
                    onOpenTask = onOpenTask,
                    onMarkDone = viewModel::markDone
                )
                ExtendedFloatingActionButton(
                    onClick = { templatePickerOpen = true },
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp)
                        .semantics { contentDescription = "新建事项" }
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("新建事项")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ReminderContent(
    st: ReminderUiState,
    notifEnabled: Boolean,
    onOpenPerson: (String) -> Unit,
    onDial: (String) -> Unit,
    /** 拨给家人（人、称谓/姓名、号码）：拨前记待提示，回来弹“联系上了吗”。 */
    onPersonDial: (io.github.xiaohaoz1.jiarenxia.core.model.Person,
        String, String) -> Unit = { _, _, n -> onDial(n) },
    onLogContact: (String,
        io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod,
        String?) -> Unit = { _, _, _ -> },
    onNotifSettings: () -> Unit,
    onOpenTask: (String) -> Unit = {},
    onMarkDone: (TaskRow) -> Unit = {}
) {
    /** “以后的事项”“已办”折叠状态（默认收起，横竖屏/进程存活期间保留）。 */
    var expandedSections by rememberSaveable { mutableStateOf(setOf<String>()) }
    // “记一次联系”面板目标（联系提醒）
    var contactLogFor by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    LazyColumn(Modifier.fillMaxSize()) {
        // 横幅
        if (!st.remindersEnabled) {
            item {
                WarningBanner(Icons.Rounded.NotificationsOff, "生日提醒已关闭，点右上角铃铛可开启")
            }
        } else if (!notifEnabled) {
            item {
                WarningBanner(Icons.Rounded.Info, "通知未开启，无法收到生日提醒") {
                    TextButton(onClick = onNotifSettings) { Text("去设置") }
                }
            }
        }
        // 全部时间线内容为空时只显示一套空态，避免再叠加事项空态。
        val emptyTimeline = !st.loading && st.today.isEmpty() && st.upcoming.isEmpty() &&
            st.dateRowsToday.isEmpty() && st.dateRows.isEmpty() && st.contactDue.isEmpty() &&
            st.tasksToday.isEmpty() && st.tasksNext30.isEmpty() &&
            st.tasksLater.isEmpty() && st.tasksDone.isEmpty()
        if (emptyTimeline) {
            item {
                EmptyState(
                    title = "未来 30 天内没有生日和事项",
                    message = "填写生日或点“新建事项”后，这里会按时间排好"
                )
            }
        }
        // ---------- 合并时间线：今天（逾期→生日→今天事项）+ 未来 30 天 ----------
        val overdueTasks = st.tasksToday.filter { it.daysUntil < 0 }
        val dueTodayTasks = st.tasksToday.filter { it.daysUntil == 0 }
        if (overdueTasks.isNotEmpty() || st.today.isNotEmpty() ||
            dueTodayTasks.isNotEmpty() || st.contactDue.isNotEmpty() ||
            st.dateRowsToday.isNotEmpty()
        ) {
            item {
                Text(
                    "今天", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            item {
                JrxCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    var index = 0
                    fun divider(): Boolean = index++ > 0
                    overdueTasks.forEach { row ->
                        CardRow(divider(), 0.dp) { TaskLine(row, onOpenTask, onMarkDone) }
                    }
                    // 联系提醒：该联系的放“今天”（记一次打开方式+备注面板）
                    st.contactDue.forEach { row ->
                        CardRow(divider(), 0.dp) {
                            ContactLine(row, onOpenPerson) { contactLogFor = row.personId }
                        }
                    }
                    st.today.forEach { row ->
                        CardRow(divider(), 0.dp) {
                            TodayBirthdayLine(
                                row = row,
                                number = st.defaultNumbers[row.person.id],
                                onOpenPerson = onOpenPerson,
                                onDial = { n ->
                                    onPersonDial(
                                        row.person,
                                        row.title?.takeUnless { it.isBlank() }
                                            ?: row.person.name,
                                        n)
                                }
                            )
                        }
                    }
                    st.dateRowsToday.forEach { row ->
                        CardRow(divider(), 0.dp) { DateLine(row, onOpenPerson) }
                    }
                    dueTodayTasks.forEach { row ->
                        CardRow(divider(), 0.dp) { TaskLine(row, onOpenTask, onMarkDone) }
                    }
                }
            }
        }
        // 未来 30 天：生日 + 事项按日期合并排序，同一天先生日后事项
        val next30 = (st.upcoming.map { TimelineItem.Bday(it) } +
            st.dateRows.map { TimelineItem.DateItem(it) } +
            st.tasksNext30.map { TimelineItem.Task(it) })
            .sortedWith(compareBy({ it.date }, { it is TimelineItem.Task }))
        if (next30.isNotEmpty()) {
            item {
                Text(
                    "未来 30 天（" + next30.size + "）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            item {
                JrxCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    next30.forEachIndexed { i, entry ->
                        CardRow(i > 0, DateLineIndent) {
                            DatedRow(
                                date = entry.date,
                                showDate = i == 0 || next30[i - 1].date != entry.date
                            ) {
                                when (entry) {
                                    is TimelineItem.Bday ->
                                        UpcomingBirthdayLine(entry.row, onOpenPerson)
                                    is TimelineItem.DateItem -> DateLine(entry.row, onOpenPerson)
                                    is TimelineItem.Task ->
                                        TaskLine(entry.row, onOpenTask, onMarkDone)
                                }
                            }
                        }
                    }
                }
            }
        }
        // ---------- 事项关闭横幅 / 折叠分组 ----------
        if (!st.taskRemindersEnabled) {
            item {
                WarningBanner(Icons.Rounded.NotificationsOff, "家庭事项提醒已关闭，点右上角铃铛可开启")
            }
        }
        val anyTasks = st.tasksToday.isNotEmpty() || st.tasksNext30.isNotEmpty() ||
            st.tasksLater.isNotEmpty() || st.tasksDone.isNotEmpty()
        if (anyTasks || st.loading.not()) {
            if (st.tasksLater.isNotEmpty()) {
                item {
                    SectionToggleHeader(
                        "以后的事项（" + st.tasksLater.size + "）",
                        expanded = "later" in expandedSections
                    ) { expandedSections = expandedSections.toggleKey("later") }
                }
                if ("later" in expandedSections) {
                    item { TaskDatedCard(st.tasksLater, onOpenTask, onMarkDone) }
                }
            }
            if (st.tasksDone.isNotEmpty()) {
                item {
                    SectionToggleHeader(
                        "已办（" + st.tasksDone.size + "）",
                        expanded = "done" in expandedSections
                    ) { expandedSections = expandedSections.toggleKey("done") }
                }
                if ("done" in expandedSections) {
                    item { TaskDatedCard(st.tasksDone, onOpenTask, onMarkDone) }
                }
            }
            if (!anyTasks && !emptyTimeline) {
                item {
                    EmptyState(
                        title = "还没有家庭事项",
                        message = "点右下角“新建事项”，记录缴费、认证、证件到期等家里要办的事"
                    )
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
    }
    // “记一次联系”面板
    contactLogFor?.let { pid ->
        var method by remember {
            mutableStateOf(io.github.xiaohaoz1.jiarenxia.core.model
                .ContactMethod.CALL)
        }
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { contactLogFor = null },
            title = { Text("记一次联系") },
            text = {
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
                            .entries.forEach { m ->
                                FilterChip(
                                    selected = method == m,
                                    onClick = { method = m },
                                    label = { Text(m.label) }
                                )
                            }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(100) },
                        label = { Text("备注（可空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onLogContact(pid, method, noteText.ifBlank { null })
                    contactLogFor = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { contactLogFor = null }) { Text("取消") }
            }
        )
    }

}

/** 警示横幅：图标 + 文案 + 可选操作。 */
@Composable
private fun WarningBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    action: (@Composable () -> Unit)? = null
) {
    val warning = LocalJrxColors.current.warning
    JrxCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = warning.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = warning,
                        modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

/** 带日期列的行：分隔线与竖线从日期列右侧开始。 */
private val DateLineIndent = 54.dp

/** 事项类型图标（新建事项模板与事项行共用）。 */
private val taskKindIcons: Map<TaskKind, ImageVector> = mapOf(
    TaskKind.INSURANCE_PREMIUM to Icons.Rounded.Payments,
    TaskKind.MEDICAL_INSURANCE to Icons.Rounded.HealthAndSafety,
    TaskKind.PENSION_CERT to Icons.Rounded.HowToReg,
    TaskKind.ID_EXPIRY to Icons.Rounded.Badge,
    TaskKind.CHECKUP to Icons.Rounded.MonitorHeart,
    TaskKind.CUSTOM to Icons.Rounded.Create
)

/** 卡片内的一行：除首行外上方加发丝分隔线，分隔线从 [indent] 处开始。 */
@Composable
private fun CardRow(divider: Boolean, indent: Dp, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (divider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = indent),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
        content()
    }
}

/**
 * 未来 30 天的日期列 + 竖线 + 圆点（距行顶 18dp）。
 * 同一天的第二条起 [showDate] 为 false，日期列留空，竖线与圆点照常。
 */
@Composable
private fun DatedRow(date: LocalDate, showDate: Boolean, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier.widthIn(min = 46.dp).padding(top = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (showDate) {
                Text(
                    "%02d/%02d".format(date.monthValue, date.dayOfMonth),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    weekdayLabel(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(Modifier.width(8.dp).fillMaxHeight()) {
            Box(
                Modifier.align(Alignment.TopCenter).width(1.dp).fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(8.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
            )
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/** 周几的中文名，如“周日”。 */
private fun weekdayLabel(d: LocalDate): String =
    "周" + arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]

/** “10月2日”。 */
private fun monthDay(d: LocalDate): String = d.monthValue.toString() + "月" + d.dayOfMonth + "日"

/**
 * 卡内统一行形态：左 22dp 图标 + 主文案 + 灰色副文案（· 分隔）+ 右侧尾部控件。
 * 整行可点；尾部控件（勾选、拨号等）自己拦截点击。
 */
@Composable
private fun AffairLine(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleAlpha: Float = 1f,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = titleAlpha)
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor
                )
            }
        }
        trailing()
    }
}

/** 「今天」的生日行：“今天是…的生日” + 日期；有电话时带拨号。 */
@Composable
private fun TodayBirthdayLine(
    row: BirthdayRow,
    number: String?,
    onOpenPerson: (String) -> Unit,
    onDial: (String) -> Unit
) {
    AffairLine(
        icon = Icons.Rounded.Cake,
        iconTint = MaterialTheme.colorScheme.primary,
        title = (if (row.title == null) "今天是你的生日"
        else "今天是" + row.title + row.person.name + "的生日") +
            // 阳历与农历生日落在同一天时说明
            if (row.merged) "（阳历和农历同一天）" else "",
        // 农历生日亮原文（“农历八月十五”）+ 当天公历日期
        subtitle = listOfNotNull(row.lunarText, monthDay(row.nextSolar))
            .joinToString(" · "),
        onClick = { onOpenPerson(row.person.id) },
        trailing = {
            CountdownBadge(row.daysUntil)
            if (number != null) {
                IconButton(onClick = { onDial(number) }) {
                    Icon(Icons.Rounded.Call, contentDescription = "拨打")
                }
            }
        }
    )
}

/** 「未来 30 天」的生日行：姓名 + 称谓/日期/提醒状态，尾部为倒计时。 */
@Composable
private fun UpcomingBirthdayLine(row: BirthdayRow, onOpenPerson: (String) -> Unit) {
    AffairLine(
        icon = Icons.Rounded.Cake,
        iconTint = MaterialTheme.colorScheme.primary,
        title = row.person.name,
        subtitle = listOfNotNull(
            row.title,
            if (row.merged) "阳历·农历同日" else row.lunarText,
            if (!row.reminderEnabled) "提醒已关闭" else null
        ).joinToString(" · "),
        onClick = { onOpenPerson(row.person.id) },
        trailing = { CountdownBadge(row.daysUntil) }
    )
}

/** 事项副文案：关联人 · 周期期数 · 金额 · 已办日期，用 · 串起来。 */
private fun taskSubtitle(row: TaskRow): String {
    val t = row.task
    val parts = mutableListOf<String>()
    row.personLabel?.let { parts += it }
    if (!t.done && t.repeat != TaskRepeat.NONE) {
        parts += (if (t.repeat == TaskRepeat.YEARLY) "每年" else "每月") +
            "·第" + (t.periodIndex + 1) + "期"
    }
    t.amountFen?.let { parts += formatFen(it) }
    t.lastDoneDate?.takeIf { t.done }?.let {
        parts += "已办于 " + it.monthValue + "月" + it.dayOfMonth + "日"
    }
    return parts.joinToString(" · ")
}

/** 事项行：类型图标 + 标题 + 副文案；未办的带逾期/倒计时徽标与“标记已办”。 */
@Composable
private fun TaskLine(
    row: TaskRow,
    onOpenTask: (String) -> Unit,
    onMarkDone: (TaskRow) -> Unit
) {
    val done = row.task.done
    AffairLine(
        icon = taskKindIcons.getValue(row.task.kind),
        iconTint = if (done) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.primary,
        title = row.task.title,
        subtitle = taskSubtitle(row),
        onClick = { onOpenTask(row.task.id) },
        titleAlpha = if (done) 0.55f else 1f,
        trailing = {
            if (!done) {
                OverdueBadge(row.daysUntil)
                IconButton(onClick = { onMarkDone(row) }) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "标记已办",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

/** 联系提醒行：“该联系 X 了” + 距上次联系；尾部“记一次”。 */
@Composable
private fun ContactLine(
    row: ContactDueRow,
    onOpenPerson: (String) -> Unit,
    onLogContact: () -> Unit
) {
    AffairLine(
        icon = Icons.Rounded.Call,
        iconTint = MaterialTheme.colorScheme.primary,
        title = "该联系" + row.label + "了",
        subtitle = if (row.daysSinceLast == null) "还没有联系记录"
        else "已 " + row.daysSinceLast + " 天",
        onClick = { onOpenPerson(row.personId) },
        subtitleColor = MaterialTheme.colorScheme.error,
        trailing = { TextButton(onClick = onLogContact) { Text("记一次") } }
    )
}

/** 纪念日/忌日行：心形（结婚纪念日）或素色花 + 名称 + 年数/农历。 */
@Composable
private fun DateLine(row: DateRow, onOpenPerson: (String) -> Unit) {
    val isWedding = row.kind.isCoupleShared
    AffairLine(
        icon = if (isWedding) Icons.Rounded.Favorite else Icons.Rounded.LocalFlorist,
        iconTint = if (isWedding) Color(0xFFD81B60)
        else MaterialTheme.colorScheme.onSurfaceVariant,
        title = row.label,
        subtitle = listOfNotNull(row.yearLabel, row.lunarText).joinToString(" · "),
        onClick = { onOpenPerson(row.personId) }
    )
}

/** 以后的事项 / 已办：一张卡，每行带日期列。 */
@Composable
private fun TaskDatedCard(
    rows: List<TaskRow>,
    onOpenTask: (String) -> Unit,
    onMarkDone: (TaskRow) -> Unit
) {
    JrxCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        rows.forEachIndexed { i, row ->
            CardRow(i > 0, DateLineIndent) {
                DatedRow(row.dueDate, showDate = i == 0 || rows[i - 1].dueDate != row.dueDate) {
                    TaskLine(row, onOpenTask, onMarkDone)
                }
            }
        }
    }
}

private fun Set<String>.toggleKey(key: String): Set<String> =
    if (key in this) this - key else this + key

@Composable
private fun SectionToggleHeader(text: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** 倒计时徽标：逾期红色“已逾期 N 天”，其余沿用生日倒计时样式。 */
@Composable
private fun OverdueBadge(daysUntil: Int) {
    if (daysUntil >= 0) {
        CountdownBadge(daysUntil)
        return
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            "已逾期 " + (-daysUntil) + " 天",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 分转“元”显示：整元不带小数（380 元），非整保留两位。 */
internal fun formatFen(fen: Long): String =
    if (fen % 100 == 0L) (fen / 100).toString() + " 元"
    else String.format(java.util.Locale.CHINA, "%.2f 元", fen / 100.0)

/** 新建事项模板选择：六个模板 + 图标 + 说明（FAMILY_AFFAIRS 5.1）。 */
@Composable
internal fun TemplatePickerDialog(onPick: (TaskKind) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建事项") },
        text = {
            Column {
                TaskTemplates.ALL.forEach { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPick(t.kind) }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(
                            taskKindIcons.getValue(t.kind), contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(t.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (t.hint.isNotEmpty()) t.hint
                                else when (t.repeat) {
                                    TaskRepeat.YEARLY ->
                                        "每年一次，" + offsetsLabel(t.offsets)
                                    TaskRepeat.MONTHLY ->
                                        "每月一次，" + offsetsLabel(t.offsets)
                                    TaskRepeat.NONE ->
                                        "只提醒一次，" + offsetsLabel(t.offsets) + "；办完即归档"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 未来 30 天合并时间线条目：生日行与事项行混排，按日期排序（同天生日在前）。 */
private sealed interface TimelineItem {
    val date: java.time.LocalDate

    class Bday(val row: BirthdayRow) : TimelineItem {
        override val date get() = row.nextSolar
    }

    class DateItem(val row: DateRow) :
        TimelineItem {
        override val date get() = row.occurrence
    }

    class Task(val row: TaskRow) : TimelineItem {
        override val date get() = row.dueDate
    }
}

/** 提前天数文案：“当天、提前 3、15 天提醒”——0 写作“当天”，其余按天数从小到大。 */
private fun offsetsLabel(offsets: Set<Int>): String {
    val days = offsets.sorted()
    val parts = mutableListOf<String>()
    val ahead = days.filter { it > 0 }
    if (0 in days) parts += "当天"
    if (ahead.isNotEmpty()) parts += "提前 " + ahead.joinToString("、") + " 天"
    return parts.joinToString("、") + "提醒"
}

