package io.github.xiaohaoz1.jiarenxia.ui.person

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.MissingInformation
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import io.github.xiaohaoz1.jiarenxia.ui.common.CommonChildrenDialog
import io.github.xiaohaoz1.jiarenxia.ui.common.ConfirmDialog
import io.github.xiaohaoz1.jiarenxia.ui.common.PostCallPromptEffect
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat
import io.github.xiaohaoz1.jiarenxia.ui.common.rememberNotificationPermissionRequester
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.ChipTone
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.CountdownBadge
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.DateTile
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.IconInfoRow
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PersonAvatar
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.QuickAction
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.TitleChip
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.ringColorOf
import io.github.xiaohaoz1.jiarenxia.ui.theme.LocalJrxColors
import java.time.LocalDate

private fun labelName(l: PhoneLabel) = when (l) {
    PhoneLabel.MOBILE -> "手机"
    PhoneLabel.WORK -> "工作"
    PhoneLabel.HOME -> "家庭"
    PhoneLabel.BACKUP -> "备用"
    PhoneLabel.OTHER -> "其他"
}

/**
 * 人物详情页（改版）：渐变页头（头像/称谓/提示）+ 快捷操作 +
 * 生日/手机号/关系/备注/基本信息 卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onRelate: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenGraph: (String) -> Unit = {},
    onOpenAffairs: () -> Unit = {},
    onAddTask: (String) -> Unit = {},
    onOpenTaskDetail: (String) -> Unit = {},
    viewModel: PersonDetailViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // 打完电话回来的“联系上了吗”提示（10 分钟窗口）
    PostCallPromptEffect(viewModel.appEvents, viewModel::logCallContact)
    val context = LocalContext.current

    LaunchedEffect(st.error) { st.error?.let { snackbar.showSnackbar(it); viewModel.onErrorShown() } }
    LaunchedEffect(st.deleted) { if (st.deleted) onBack() }

    PersonDetailContent(
        st = st,
        today = java.time.LocalDate.now(),
        lunar = viewModel.lunar,
        snackbar = snackbar,
        onBack = onBack,
        onEdit = onEdit,
        onRelate = onRelate,
        onOpenPerson = onOpenPerson,
        onOpenGraph = onOpenGraph,
        onOpenAffairs = onOpenAffairs,
        onAddTask = onAddTask,
        onOpenTaskDetail = onOpenTaskDetail,
        onDial = { number ->
            st.detail?.person?.let { p ->
                // “联系上了吗”提示用的称呼：称谓优先，本人与无称谓回退姓名
                val o = st.overview
                val label = if (o == null || p.isSelf) p.name
                    else o.displayTitle.ifEmpty { p.name }
                viewModel.onDialMade(p, label)
            }
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "未找到拨号应用", Toast.LENGTH_SHORT).show()
            }
        },
        onSetReminder = viewModel::setBirthdayReminder,
        onSetReminderOffsets = viewModel::setBirthdayReminderOffsets,
        onSetReminderFor = viewModel::setReminderEnabledFor,
        onSetOffsetsFor = viewModel::setReminderOffsetsFor,
        onSetCouple = viewModel::setCoupleAnniversary,
        onLinkSpouseWedding = viewModel::linkSpouseAndWedding,
        onToggleCommonChild = viewModel::toggleCommonChild,
        onConfirmCommonChildren = { viewModel.confirmCommonChildren() },
        onDismissCommonChildren = { viewModel.dismissCommonChildren() },
        onParentCompletion = viewModel::askParentCompletion,
        onSetContactPlan = viewModel::setContactPlan,
        onLogContact = viewModel::logContact,
        onDeleteContactLog = viewModel::deleteContactLog,
        onSetDeath = viewModel::setDeathAnniversary,
        onSetCustomTitle = viewModel::setCustomTitle,
        onDeletePerson = { viewModel.deletePerson() },
        onSetSiblingOrder = viewModel::setSiblingOrder,
        onRemoveRelation = viewModel::removeRelationship
    )
}

/**
 * 详情页无状态主体：渐变页头 + 快捷操作 + 卡片组。
 * today 由调用方注入，便于截图测试固定日期。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonDetailContent(
    st: PersonDetailUiState,
    today: LocalDate,
    lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onRelate: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenGraph: (String) -> Unit = {},
    onOpenAffairs: () -> Unit = {},
    onAddTask: (String) -> Unit = {},
    onOpenTaskDetail: (String) -> Unit = {},
    onDial: (String) -> Unit,
    /** 生日提醒开关：第一个参数为历法槽位（阳历/农历）。 */
    onSetReminder: (
        io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,
        Boolean) -> Unit,
    onSetReminderFor: (
        io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        Boolean) -> Unit = { _, _ -> },
    onSetOffsetsFor: (
        io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        Set<Int>) -> Unit = { _, _ -> },
    /** 夫妻共享纪念日（结婚/领证/恋爱）保存：null 表示清除。 */
    onSetCouple: (
        io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?) -> Unit =
        { _, _ -> },
    onLinkSpouseWedding: (
        String,
        io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue) -> Unit =
        { _, _ -> },
    /** “共同子女”询问：勾选 / 确定 / 都不是。 */
    onToggleCommonChild: (String) -> Unit = {},
    onConfirmCommonChildren: () -> Unit = {},
    onDismissCommonChildren: () -> Unit = {},
    onParentCompletion: () -> Unit = {},
    onSetContactPlan: (Int?) -> Unit = {},
    onLogContact: (
        io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod,
        String?) -> Unit = { _, _ -> },
    onDeleteContactLog: (String) -> Unit = {},
    onSetDeath: (
        io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue?) -> Unit = {},
    /** 生日提醒偏移：第一个参数为历法槽位。 */
    onSetReminderOffsets: (
        io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,
        Set<Int>) -> Unit,
    onSetCustomTitle: (String?) -> Unit,
    onDeletePerson: () -> Unit,
    onSetSiblingOrder: (String, String, RelativeAgeOrder?) -> Unit,
    onRemoveRelation: (String) -> Unit
) {
    val context = LocalContext.current
    val permissionRequester = rememberNotificationPermissionRequester()
    var showPhoneSheet by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var ageDialogFor by remember { mutableStateOf<RelationRow?>(null) }
    var deleteRelFor by remember { mutableStateOf<RelationRow?>(null) }

    val dial = onDial

    // 顶栏透明但内容滑到下方时变为实色，避免文字压在返回键上
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // 记为夫妻成功后的“共同子女”询问
    st.commonChildrenAsk?.let { ask ->
        CommonChildrenDialog(
            ask = ask,
            onToggle = onToggleCommonChild,
            onConfirm = onConfirmCommonChildren,
            onNone = onDismissCommonChildren
        )
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val p = st.detail?.person
                    if (p != null && !p.isSelf) {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "更多操作：" + p.name
                                )
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                // 仅与“我”有路径的人才能在关系图中定位
                                if (st.overview?.result?.kind.let {
                                        it != null && it != KinshipKind.UNRELATED
                                    }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("在关系图中查看") },
                                        onClick = {
                                            menuOpen = false
                                            onOpenGraph(p.id)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    onClick = { menuOpen = false; showDelete = true }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val detail = st.detail
        val ov = st.overview
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (st.loading) "" else "人物不存在")
            }
            return@Scaffold
        }
        val person = detail.person
        val result = ov?.result
        val deceased = person.livingStatus == LivingStatus.DECEASED
        val activePhones = detail.phones.filter { it.status == PhoneStatus.ACTIVE }

        // 页头叠在顶栏之下（顶栏透明），所以滚动内容从屏幕顶开始
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            var customDialog by remember { mutableStateOf(false) }
            // 渐变页头：透明顶栏盖在上面
            PageHeader(
                contentPadding = PaddingValues(top = 64.dp, bottom = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PersonAvatar(
                        person.avatarPath, person.name, 104.dp,
                        ring = ov?.let { ringColorOf(it, st.familyById) },
                        deceased = deceased
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        person.name, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(6.dp))
                    // 年龄 / 享年（生日无年份不显示）
                    st.ageText?.let {
                        Text(
                            it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (result != null) {
                        val customTitle = ov?.customTitle
                        // 称谓胶囊：非“我”时点击可自定义称呼（25.2，仅覆盖显示）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val shown = if (person.isSelf) "我" else (customTitle ?: result.displayTitle)
                            Box(Modifier.clickable(enabled = !person.isSelf) { customDialog = true }) {
                                TitleChip(shown, ChipTone.CORE)
                            }
                            if (!person.isSelf) {
                                Spacer(Modifier.width(4.dp))
                                // 触摸热区 ≥48dp（显示仍为 16dp 小铅笔）
                                Icon(
                                    Icons.Rounded.Edit, contentDescription = "修改称呼",
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .size(16.dp)
                                        .clickable { customDialog = true },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // 自定义称呼生效时，附标准称谓/结构关系作对照
                        if (customTitle != null && !person.isSelf) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (result.standardTitle != null)
                                    "标准称谓：" + result.standardTitle
                                else "关系：" + result.structuralDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (!person.isSelf && result.structuralDescription != result.displayTitle) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "关系：" + result.structuralDescription,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 信息不足提示胶囊
                        val hints = buildList {
                            if (MissingInformation.SIBLING_AGE_ORDER in result.missingInformation)
                                add("补充长幼信息后，可以确定具体称谓")
                            if (MissingInformation.BIRTH_DATES in result.missingInformation)
                                add("补充双方完整的出生日期后，可以确定具体称谓")
                            if (result.kind == KinshipKind.UNRELATED)
                                add(
                                    // 只记称呼人物显示“关系待补充”
                                    if (ov?.titleOnly == true) "关系待补充"
                                    else "还没有和“我”建立关系"
                                )
                        }
                        if (hints.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            hints.forEach {
                                TitleChip(it, ChipTone.MUTED)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                    if (deceased) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "已故", style = MaterialTheme.typography.labelMedium,
                            color = LocalJrxColors.current.deceased
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                // 快捷操作
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickAction(
                        icon = Icons.Rounded.Call,
                        label = "打电话",
                        enabled = !deceased && activePhones.isNotEmpty(),
                        onClick = {
                            if (activePhones.size == 1) dial(activePhones[0].number)
                            else showPhoneSheet = true
                        }
                    )
                    QuickAction(
                        icon = Icons.Rounded.Edit,
                        label = "编辑",
                        onClick = { onEdit(person.id) }
                    )
                    QuickAction(
                        icon = Icons.Rounded.GroupAdd,
                        label = "添加关系",
                        onClick = { onRelate(person.id) }
                    )
                }

                // 重要日期卡片（生日 / 结婚纪念日 / 忌日）
                var coupleDialog by remember {
                    mutableStateOf<io.github.xiaohaoz1.jiarenxia.core.model
                        .ImportantDateType?>(null)
                }
                var deathDialog by remember { mutableStateOf(false) }
                JrxCard(Modifier.fillMaxWidth()) {
                    SectionTitle("重要日期")
                    if (detail.birthdays.isNotEmpty()) {
                        // 阳历/农历两个槽位各自独立展示与提醒
                        io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.entries
                            .forEachIndexed { slotIndex, cal ->
                            val bd = detail.birthdays[cal] ?: return@forEachIndexed
                            val b = bd.value
                            if (slotIndex > 0 || detail.birthdays.size > 1) {
                                HorizontalDivider(
                                    Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                            // today / lunar 由参数注入
                            val next = BirthdayMath.nextOccurrence(b, today, lunar)
                            val until = BirthdayMath.daysUntil(b, today, lunar)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (next != null) {
                                    DateTile(
                                        next.monthValue, next.dayOfMonth,
                                        highlight = !deceased && until == 0
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        (if (cal ==
                                            io.github.xiaohaoz1.jiarenxia.core.model
                                                .CalendarType.SOLAR) "阳历生日　" else "") +
                                            LunarFormat.formatBirthday(b),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    // 农历生日附公历提示
                                    if (cal == io.github.xiaohaoz1.jiarenxia.core.model
                                        .CalendarType.LUNAR
                                    ) {
                                        LunarFormat.nextSolarHint(b, today, lunar)?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (!deceased && until != null) {
                                    CountdownBadge(until)
                                }
                            }
                            HorizontalDivider(
                                Modifier.padding(vertical = 10.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            ReminderPanel(
                                label = (if (cal ==
                                    io.github.xiaohaoz1.jiarenxia.core.model
                                        .CalendarType.SOLAR) "阳历" else "农历") +
                                    "生日提醒",
                                checked = bd.reminder?.enabled ?: false,
                                onToggle = {
                                    onSetReminder(cal, it)
                                    if (it) permissionRequester.request()
                                },
                                enabled = !deceased,
                                note = if (deceased) "已故人物不发送生日提醒"
                                    else null,
                                offsets = if (!deceased)
                                    bd.reminder?.reminderOffsets else null,
                                onOffsets = { onSetReminderOffsets(cal, it) }
                            )
                        }
                    } else {
                        Text(
                            "还没有填写生日",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { onEdit(person.id) }) { Text("去填写") }
                    }

                    // ---- 夫妻共享纪念日：恋爱/领证/结婚，各共一条 ----
                    val hasSpouse = detail.relationships.any {
                        it.type == io.github.xiaohaoz1.jiarenxia.core.model
                            .RelationshipType.SPOUSE
                    }
                    val candidate = st.weddingCandidateName
                    val coupleTypes = listOf(
                        io.github.xiaohaoz1.jiarenxia.core.model
                            .ImportantDateType.DATING_ANNIVERSARY,
                        io.github.xiaohaoz1.jiarenxia.core.model
                            .ImportantDateType.LICENSE_ANNIVERSARY,
                        io.github.xiaohaoz1.jiarenxia.core.model
                            .ImportantDateType.WEDDING_ANNIVERSARY
                    )
                    val anyCoupleEntry = coupleTypes.any {
                        st.coupleDates[it] != null
                    }
                    // 这组纪念日只属于“我”：有配偶才显示录入入口；
                    // 无配偶时仅残留记录与“记为夫妻”候选保留入口，
                    // 其他人的页面一律不显示
                    if (person.isSelf &&
                        (hasSpouse || anyCoupleEntry || candidate != null)
                    ) {
                        HorizontalDivider(
                            Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        for (type in coupleTypes) {
                            val entry = st.coupleDates[type]
                            // 无配偶：仅已有记录或结婚候选显示对应入口
                            if (!hasSpouse && entry == null &&
                                !(candidate != null && type ==
                                    io.github.xiaohaoz1.jiarenxia.core.model
                                        .ImportantDateType
                                        .WEDDING_ANNIVERSARY)
                            ) continue
                            val typeName = io.github.xiaohaoz1.jiarenxia
                                .domain.anniversary.AnniversaryDurations
                                .typeName(type)
                            if (entry != null) {
                                val duration = io.github.xiaohaoz1.jiarenxia
                                    .domain.anniversary.AnniversaryDurations
                                    .startDate(entry.date, lunar)?.let { d ->
                                        io.github.xiaohaoz1.jiarenxia
                                            .domain.anniversary
                                            .AnniversaryDurations
                                            .fact(type, d, today)
                                    }
                                DateRowDetail(
                                    label = typeName + "（与" +
                                        (entry.partnerNameFor(
                                            person.id) ?: "配偶") + "）",
                                    value = entry.date,
                                    enabled = entry.reminderEnabled,
                                    offsets = entry.reminderOffsets,
                                    today = today, lunar = lunar,
                                    yearLabel = duration,
                                    onToggle = {
                                        onSetReminderFor(type, it)
                                        if (it) permissionRequester.request()
                                    },
                                    onOffsets = {
                                        onSetOffsetsFor(type, it)
                                    },
                                    onEdit = {
                                        coupleDialog = type
                                    }
                                )
                            } else {
                                AddDateRow(
                                    label = if (candidate != null && type ==
                                        io.github.xiaohaoz1.jiarenxia.core
                                            .model.ImportantDateType
                                            .WEDDING_ANNIVERSARY
                                    ) "添加" + typeName + "（与" +
                                        candidate + "）"
                                    else "添加" + typeName,
                                    onClick = { coupleDialog = type }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    // ---- 忌日：仅已故 ----
                    if (deceased) {
                        HorizontalDivider(
                            Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        val death = st.deathDate
                        if (death != null) {
                            DateRowDetail(
                                label = "忌日",
                                value = death.date,
                                enabled = death.reminderEnabled,
                                offsets = death.reminderOffsets,
                                today = today, lunar = lunar,
                                yearLabel = weddingYearLabel(
                                    death.date, today, lunar, false),
                                onToggle = {
                                    onSetReminderFor(
                                        io.github.xiaohaoz1.jiarenxia.core
                                            .model.ImportantDateType
                                            .DEATH_ANNIVERSARY, it)
                                    if (it) permissionRequester.request()
                                },
                                onOffsets = {
                                    onSetOffsetsFor(
                                        io.github.xiaohaoz1.jiarenxia.core
                                            .model.ImportantDateType
                                            .DEATH_ANNIVERSARY, it)
                                },
                                onEdit = { deathDialog = true }
                            )
                        } else {
                            AddDateRow(
                                label = "添加忌日",
                                onClick = { deathDialog = true }
                            )
                        }
                    }
                }
                coupleDialog?.let { dlgType ->
                    val candId = st.weddingCandidateId
                    val candName = st.weddingCandidateName
                    val isWedding = dlgType ==
                        io.github.xiaohaoz1.jiarenxia.core.model
                            .ImportantDateType.WEDDING_ANNIVERSARY
                    val needLink = isWedding && !detail.relationships.any {
                        it.type == io.github.xiaohaoz1.jiarenxia.core.model
                            .RelationshipType.SPOUSE
                    } && candId != null
                    val typeName = io.github.xiaohaoz1.jiarenxia.domain
                        .anniversary.AnniversaryDurations.typeName(dlgType)
                    ImportantDateDialog(
                        title = typeName,
                        initial = st.coupleDates[dlgType]?.date,
                        yearLabel = if (isWedding) "结婚年份"
                            else if (dlgType ==
                                io.github.xiaohaoz1.jiarenxia.core.model
                                    .ImportantDateType.LICENSE_ANNIVERSARY
                            ) "领证年份" else "相恋年份",
                        note = if (needLink)
                            "将同时把" + person.name + "和" + candName +
                                "记为夫妻"
                        else null,
                        lunar = lunar,
                        onSave = { v ->
                            if (v != null && needLink) {
                                onLinkSpouseWedding(candId, v)
                            } else onSetCouple(dlgType, v)
                            coupleDialog = null
                        },
                        onDismiss = { coupleDialog = null }
                    )
                }
                if (deathDialog) {
                    ImportantDateDialog(
                        title = "忌日",
                        initial = st.deathDate?.date,
                        yearLabel = "去世年份",
                        lunar = lunar,
                        onSave = { onSetDeath(it); deathDialog = false },
                        onDismiss = { deathDialog = false }
                    )
                }
                if (customDialog && ov != null && !person.isSelf) {
                    CustomTitleDialog(
                        personName = person.name,
                        current = ov.customTitle,
                        onDismiss = { customDialog = false },
                        onSave = { onSetCustomTitle(it); customDialog = false }
                    )
                }
                Spacer(Modifier.height(12.dp))
                // “我的事/TA 的事”卡片：此人名下待办（最多 3 条）+ 添加；
                // 全家级事项不在这里，在提醒页时间线（“查看全部”也跳到那里）
                JrxCard(Modifier.fillMaxWidth()) {
                    SectionTitle(if (person.isSelf) "我的事" else "TA 的事")
                    val undone = st.tasks.filter { !it.done }
                        .sortedBy {
                            io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule.dueDate(it)
                        }
                    if (undone.isEmpty()) {
                        Text(
                            "名下没有要办的事",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    undone.take(3).forEach { task ->
                        val due = io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule.dueDate(task)
                        val days = io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule
                            .daysUntil(due, today)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .semantics(mergeDescendants = true) {}
                                .clickable { onOpenTaskDetail(task.id) }
                                .padding(vertical = 6.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.title,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    due.monthValue.toString() + "月" + due.dayOfMonth + "日" +
                                        when {
                                            days < 0 -> " · 已逾期 " + (-days) + " 天"
                                            days == 0 -> " · 今天"
                                            else -> " · " + days + " 天后"
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (days < 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // 周期事项亮出“每年·第N期”与上期办结日——
                                // 这是区别于日历事件的核心信息
                                if (task.repeat != TaskRepeat.NONE) {
                                    var periodLine =
                                        (if (task.repeat == TaskRepeat.YEARLY) "每年" else "每月") +
                                            " · 第" + (task.periodIndex + 1) + "期"
                                    task.lastDoneDate?.let {
                                        periodLine += " · 上期" + it.monthValue + "月" +
                                            it.dayOfMonth + "日已办"
                                    }
                                    Text(
                                        periodLine,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { onAddTask(person.id) }) {
                            Text("添加事项")
                        }
                        Spacer(Modifier.weight(1f))
                        if (undone.size > 3) {
                            TextButton(onClick = onOpenAffairs) {
                                Text("查看全部（" + undone.size + "）")
                            }
                        }
                    }
                }
                // 人情/保障摘要卡已随模块退出移除（A6）
                Spacer(Modifier.height(12.dp))

                // 联系卡片：最近联系 + 记一次 + 周期 + 最近 5 条（V0.9 联系提醒）；
                // “我”与已故家人不显示（ContactSchedule.eligible）
                if (io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
                        .eligible(person)
                ) {
                    JrxCard(Modifier.fillMaxWidth()) {
                        SectionTitle("联系")
                        val lastLog = st.contactLogs.firstOrNull()
                        if (lastLog == null) {
                            Text(
                                "还没有联系记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val daysAgo = java.time.temporal.ChronoUnit.DAYS
                                .between(
                                    java.time.Instant
                                        .ofEpochMilli(lastLog.atMillis)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDate(),
                                    today)
                            Text(
                                "最近联系：" +
                                    (if (daysAgo <= 0) "今天"
                                    else daysAgo.toString() + " 天前") +
                                    "（" + lastLog.method.label + "）",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        // 记一次联系：方式胶囊 + 备注（≤100 字）
                        var logSheet by remember { mutableStateOf(false) }
                        androidx.compose.material3.FilledTonalButton(
                            onClick = { logSheet = true },
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = 8.dp)
                        ) { Text("记一次联系") }
                        // 联系周期
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "联系周期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val options = listOf<Pair<Int?, String>>(
                                null to "不提醒"
                            ) + io.github.xiaohaoz1.jiarenxia.data
                                .ContactIntervals.ALL.map {
                                    it to io.github.xiaohaoz1.jiarenxia.data
                                        .ContactIntervals.label(it)
                                }
                            options.forEach { (days, label) ->
                                FilterChip(
                                    selected = st.contactPlanDays == days,
                                    onClick = { onSetContactPlan(days) },
                                    label = { Text(label) }
                                )
                            }
                        }
                        // 最近 5 条记录（可删除）
                        st.contactLogs.take(5).forEach { log ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                val d = java.time.Instant
                                    .ofEpochMilli(log.atMillis)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        log.method.label + " · " +
                                            d.monthValue + "月" +
                                            d.dayOfMonth + "日" +
                                            (log.note?.let { " · " + it } ?: ""),
                                        style = MaterialTheme.typography
                                            .bodyMedium
                                    )
                                }
                                IconButton(onClick = {
                                    onDeleteContactLog(log.id)
                                }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "删除这条联系记录",
                                        tint = MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (logSheet) {
                            var method by remember {
                                mutableStateOf(
                                    io.github.xiaohaoz1.jiarenxia.core.model
                                        .ContactMethod.CALL)
                            }
                            var noteText by remember { mutableStateOf("") }
                            AlertDialog(
                                onDismissRequest = { logSheet = false },
                                title = { Text("记一次联系") },
                                text = {
                                    Column {
                                        FlowRow(
                                            horizontalArrangement =
                                                Arrangement.spacedBy(8.dp)
                                        ) {
                                            io.github.xiaohaoz1.jiarenxia.core
                                                .model.ContactMethod.entries
                                                .forEach { m ->
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
                                            onValueChange = {
                                                noteText = it.take(100)
                                            },
                                            label = { Text("备注（可空）") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        onLogContact(
                                            method,
                                            noteText.ifBlank { null })
                                        logSheet = false
                                    }) { Text("保存") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { logSheet = false }) {
                                        Text("取消")
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 手机号卡片
                JrxCard(Modifier.fillMaxWidth()) {
                    SectionTitle("手机号")
                    if (detail.phones.isEmpty()) {
                        Text(
                            "还没有手机号",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { onEdit(person.id) }) { Text("去添加") }
                    }
                    detail.phones.forEachIndexed { i, ph ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        ph.number,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (ph.status == PhoneStatus.INACTIVE)
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (ph.isPrimary) {
                                        Spacer(Modifier.width(6.dp))
                                        TitleChip("主号码", ChipTone.CORE)
                                    }
                                    if (ph.status == PhoneStatus.INACTIVE) {
                                        Spacer(Modifier.width(6.dp))
                                        TitleChip("已停用", ChipTone.MUTED)
                                    }
                                }
                                Text(
                                    labelName(ph.label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!deceased && ph.status == PhoneStatus.ACTIVE) {
                                IconButton(onClick = { dial(ph.number) }) {
                                    Icon(
                                        Icons.Rounded.Call,
                                        contentDescription = "给" + person.name + "打电话",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (i != detail.phones.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 关系卡片
                JrxCard(Modifier.fillMaxWidth()) {
                    SectionTitle("关系")
                    st.parentCompletion?.let { ask ->
                        io.github.xiaohaoz1.jiarenxia.ui.common.ParentCompletionSuggestion(ask, onParentCompletion)
                    }
                    st.relationRows.forEach { row ->
                        RelationRowItem(
                            row = row,
                            onClick = { onOpenPerson(row.other.id) },
                            onSetAge = { ageDialogFor = row },
                            onDelete = { deleteRelFor = row }
                        )
                    }
                    TextButton(onClick = { onRelate(person.id) }) { Text("添加关系") }
                }
                Spacer(Modifier.height(12.dp))

                // 备注卡片
                if (!person.note.isNullOrBlank()) {
                    JrxCard(Modifier.fillMaxWidth()) {
                        SectionTitle("备注")
                        Text(person.note!!, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 基本信息卡片
                JrxCard(Modifier.fillMaxWidth()) {
                    SectionTitle("基本信息")
                    IconInfoRow(
                        Icons.Rounded.Person, "性别",
                        if (person.gender == Gender.MALE) "男" else "女"
                    )
                    // 排行（未记录不显示该行；只影响同代排序，不参与称谓）
                    person.siblingRank?.let {
                        IconInfoRow(
                            Icons.Rounded.FormatListNumbered, "排行",
                            siblingRankWord(it) + "（第 " + it + " 个）"
                        )
                    }
                    // 在世不标“状态”，只有已故才显示这一行
                    if (deceased) {
                        IconInfoRow(
                            Icons.Rounded.FavoriteBorder, "状态", "已故"
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // 多号码选择
    if (showPhoneSheet) {
        val activePhones = st.detail?.phones.orEmpty().filter { it.status == PhoneStatus.ACTIVE }
        ModalBottomSheet(onDismissRequest = { showPhoneSheet = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text(
                    "拨打电话", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                activePhones.forEach { ph ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { showPhoneSheet = false; dial(ph.number) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(ph.number, style = MaterialTheme.typography.titleMedium)
                            Text(
                                labelName(ph.label) + if (ph.isPrimary) " · 主号码" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // 修改长幼对话框
    ageDialogFor?.let { row ->
        val thisName = st.detail?.person?.name ?: ""
        AlertDialog(
            onDismissRequest = { ageDialogFor = null },
            title = { Text(row.other.name + " 比 " + thisName + "：") },
            text = {
                Column {
                    listOf(
                        RelativeAgeOrder.OLDER to "年长",
                        RelativeAgeOrder.YOUNGER to "年幼",
                        RelativeAgeOrder.SAME to "相同",
                        null to "未记录"
                    ).forEach { (order, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSetSiblingOrder(row.relationship.id, row.other.id, order)
                                ageDialogFor = null
                            }
                        ) {
                            RadioButton(selected = false, onClick = null)
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { ageDialogFor = null }) { Text("取消") } }
        )
    }

    // 删除关系确认
    deleteRelFor?.let {
        ConfirmDialog(
            text = "删除这条关系？删除后，可能有家人不再与“我”关联，他们仍会保留在“其他家人”中。",
            confirmText = "删除",
            onConfirm = {
                onRemoveRelation(it.relationship.id); deleteRelFor = null
            },
            onDismiss = { deleteRelFor = null }
        )
    }

    // 删除人物确认
    if (showDelete) {
        val n = st.detail?.relationships?.size ?: 0
        // 顺带删除清单：事项（只显示数量大于 0 的项）
        val extras = buildList {
            val undone = st.tasks.size
            if (undone > 0) add(undone.toString() + " 条事项")
        }
        val extraText = if (extras.isEmpty()) "" else "同时删除 " +
            extras.joinToString("、") + "，"
        ConfirmDialog(
            text = "删除「" + (st.detail?.person?.name ?: "") + "」？" + extraText +
                "将同时删除与TA相关的" + n + "条关系，此操作不可撤销。",
            confirmText = "删除",
            onConfirm = { showDelete = false; onDeletePerson() },
            onDismiss = { showDelete = false }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(8.dp))
}

/** 关系行：头像 + 称谓胶囊 + 姓名 + 箭头 + 行内菜单。 */
@Composable
private fun RelationRowItem(
    row: RelationRow,
    onClick: () -> Unit,
    onSetAge: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PersonAvatar(
            row.other.avatarPath, row.other.name, 36.dp,
            deceased = row.other.livingStatus == LivingStatus.DECEASED
        )
        Spacer(Modifier.width(10.dp))
        TitleChip(row.label, ChipTone.OTHER)
        Spacer(Modifier.width(8.dp))
        Text(
            row.other.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Rounded.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "关系操作")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (row.relationship.type == RelationshipType.SIBLING ||
                    row.relationship.type == RelationshipType.COUSIN
                ) {
                    DropdownMenuItem(text = { Text("修改长幼") }, onClick = {
                        menuOpen = false; onSetAge()
                    })
                }
                DropdownMenuItem(text = { Text("删除关系") }, onClick = {
                    menuOpen = false; onDelete()
                })
            }
        }
    }
}

/** 自定义称呼对话框（DATABASE_SCHEMA 25.2）：1–12 字符，仅覆盖显示。 */
@Composable
internal fun CustomTitleDialog(
    personName: String,
    current: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit
) {
    var text by remember { mutableStateOf(current ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义称呼") },
        text = { CustomTitleDialogBody(personName, text, { text = it }) },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank() && text.trim().length in 1..12
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text("恢复默认", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

/** 对话框正文（抽出以便截图测试无需打开弹层窗口）。 */
@Composable
internal fun CustomTitleDialogBody(
    personName: String, text: String, onText: (String) -> Unit
) {
    Column {
        Text(
            "给" + personName + "起一个你家里的叫法。",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 12) onText(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text("例如：大伯、外公、老舅。只改变显示的称呼，不影响关系推导。")
            }
        )
    }
}

/** “第 N 年”/“N 周年”：下一次发生的年份减基准年份。 */
@Composable
private fun weddingYearLabel(
    value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue,
    today: LocalDate,
    lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    wedding: Boolean
): String? {
    val base = value.date.year ?: return null
    val occ = BirthdayMath.nextOccurrence(value, today, lunar) ?: return null
    val n = occ.year - base
    return if (n <= 0) null
    else if (wedding) "第 " + n + " 年" else n.toString() + " 周年"
}

/**
 * 重要日期行（结婚纪念日 / 忌日）：日期 + 农历附公历 + 年数 +
 * 提醒开关与提前天数胶囊；点击行进入编辑。
 */
@Composable
private fun DateRowDetail(
    label: String,
    value: io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue,
    enabled: Boolean,
    offsets: Set<Int>,
    today: LocalDate,
    lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    yearLabel: String?,
    onToggle: (Boolean) -> Unit,
    onOffsets: (Set<Int>) -> Unit,
    onEdit: () -> Unit
) {
    val next = BirthdayMath.nextOccurrence(value, today, lunar)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (next != null) {
            DateTile(next.monthValue, next.dayOfMonth,
                highlight = false)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                LunarFormat.formatBirthday(value),
                style = MaterialTheme.typography.bodyMedium
            )
            if (value.calendar ==
                io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR
            ) {
                LunarFormat.nextSolarHint(value, today, lunar)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            yearLabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        TextButton(onClick = onEdit) { Text("修改") }
    }
    Spacer(Modifier.height(8.dp))
    ReminderPanel(
        label = "提醒",
        checked = enabled,
        onToggle = onToggle,
        offsets = offsets,
        onOffsets = onOffsets
    )
    Spacer(Modifier.height(4.dp))
}

/** 提醒设置面板：铃铛 + 开关行 + 可选的时机胶囊，收进浅色内嵌面板。 */
@Composable
private fun ReminderPanel(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true,
    note: String? = null,
    offsets: Set<Int>? = null,
    onOffsets: (Set<Int>) -> Unit = {}
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                // 整行 toggleable，把标签与开关关联给 TalkBack
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = checked, role = Role.Switch, enabled = enabled,
                    onValueChange = onToggle
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (checked) Icons.Rounded.NotificationsActive
                        else Icons.Rounded.NotificationsNone,
                    contentDescription = null,
                    tint = if (checked && enabled)
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                MiniSwitch(checked = checked, enabled = enabled)
            }
            if (checked && offsets != null) {
                Spacer(Modifier.height(8.dp))
                ReminderOffsetRow(offsets, onOffsets)
            }
        }
    }
}

/** 提醒时机：当天 / 提前 1 / 3 / 7 天，至少保留一项。 */
@Composable
private fun ReminderOffsetRow(
    offsets: Set<Int>,
    onOffsets: (Set<Int>) -> Unit
) {
    val context = LocalContext.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        mapOf(0 to "当天", 1 to "提前1天", 3 to "提前3天", 7 to "提前7天")
            .forEach { (d, label) ->
                ReminderChip(
                    label = label,
                    selected = d in offsets,
                    onClick = {
                        val next =
                            if (d in offsets) offsets - d else offsets + d
                        if (next.isEmpty()) {
                            Toast.makeText(
                                context, "至少保留一个提醒时间",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else onOffsets(next)
                    }
                )
            }
    }
}

/** 紧凑的时机胶囊：选中暖色填充，未选中细描边。 */
@Composable
private fun ReminderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) cs.secondaryContainer else Color.Transparent,
        contentColor = if (selected) cs.onSecondaryContainer
            else cs.onSurfaceVariant,
        border = BorderStroke(
            1.dp, if (selected) Color.Transparent else cs.outlineVariant)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** 细版开关：只负责绘制，点击与无障碍由外层 toggleable 行处理。 */
@Composable
private fun MiniSwitch(checked: Boolean, enabled: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val thumbX by animateDpAsState(
        targetValue = if (checked) 18.dp else 4.dp, label = "miniSwitch")
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .size(width = 37.dp, height = 21.dp)
            .clip(CircleShape)
            .background(if (checked) cs.primary else cs.surfaceContainerHighest)
            .border(
                1.5.dp, if (checked) cs.primary else cs.outline, CircleShape)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbX)
                .size(15.dp)
                .clip(CircleShape)
                .background(if (checked) cs.onPrimary else cs.outline)
        )
    }
}

/** 纪念日空位入口：浅色描边面板里的加号行。 */
@Composable
private fun AddDateRow(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Add, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
