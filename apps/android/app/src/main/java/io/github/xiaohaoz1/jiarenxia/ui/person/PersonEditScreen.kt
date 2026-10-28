package io.github.xiaohaoz1.jiarenxia.ui.person

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContactPhone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Woman
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.domain.RelativePlan
import io.github.xiaohaoz1.jiarenxia.ui.common.CommonChildrenDialog
import io.github.xiaohaoz1.jiarenxia.ui.common.ConfirmDialog
import io.github.xiaohaoz1.jiarenxia.ui.common.rememberNotificationPermissionRequester
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.ChipTone
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PersonAvatar
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PickedAvatar
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.TitleChip
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.roleIcon

/** 表单回调集合：把 ViewModel 方法打包传给无状态 Content，便于预览与截图测试。 */
class PersonEditCallbacks(
    val onName: (String) -> Unit = {},
    val onGender: (Gender) -> Unit = {},
    val onDeceased: (Boolean) -> Unit = {},
    /** 阳历生日表单整体替换。 */
    val onSolarBirthday: (BirthdayFormState) -> Unit = {},
    /** 农历生日表单整体替换。 */
    val onLunarBirthday: (BirthdayFormState) -> Unit = {},
    /** 用户主动触发：把指定历法槽位的完整生日换算填入另一槽位。 */
    val onConvert: (io.github.xiaohaoz1.jiarenxia.core.model.CalendarType) -> Unit = {},
    val onNote: (String) -> Unit = {},
    /** 兄弟姐妹排行输入（原文串，ViewModel 负责过滤）。 */
    val onSiblingRank: (String) -> Unit = {},
    /** “手动填写”：优先聚焦已有空行，没有才追加。 */
    val onAddPhone: () -> Unit = {},
    /** 卡级“从联系人选择”：去重后填空行或追加。 */
    val onImportContact: (io.github.xiaohaoz1.jiarenxia.ui.common.PickedContact) -> Unit = {},
    /** 行级“从联系人选择”：只作用当前行（rowKey 定位）。 */
    val onPickPhoneForRow: (String,
        io.github.xiaohaoz1.jiarenxia.ui.common.PickedContact) -> Unit = { _, _ -> },
    val onUpdatePhone: (String, PhoneRowState) -> Unit = { _, _ -> },
    val onRemovePhone: (String) -> Unit = {},
    val onSetPrimary: (String) -> Unit = {},
    /** 定位效果已渲染，清除 focusPhoneKey/focusTarget。 */
    val onFocusHandled: () -> Unit = {},
    /** 缺项提示点击：定位第一个缺项所在区块。 */
    val onMissingClick: () -> Unit = {},
    val onAnchor: (String) -> Unit = {},
    val onRole: (RelationRole) -> Unit = {},
    val onRemoveAvatar: () -> Unit = {},
    val onPickAvatar: (android.net.Uri) -> Unit = {},
    val onSave: () -> Unit = {},
    /** “保存并继续添加”：落库后清空个人信息、保留分支上下文。 */
    val onSaveAndContinue: () -> Unit = {},
    val lineage: LineageCallbacks = LineageCallbacks()
)

/**
 * 人物表单页（改版）：创建“我” / 新增亲属 / 编辑 共用同一表单风格。
 * SELF：渐变页头 + 底部主按钮；NEW/EDIT：顶栏 + “保存”。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonEditScreen(
    mode: PersonEditMode,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: PersonEditViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(mode) { viewModel.init(mode) }
    LaunchedEffect(st.error) { st.error?.let { snackbar.showSnackbar(it); viewModel.onErrorShown() } }
    LaunchedEffect(st.savedNotice) {
        st.savedNotice?.let { snackbar.showSnackbar(it); viewModel.onSavedNoticeShown() }
    }

    val callbacks = remember(viewModel) {
        PersonEditCallbacks(
            onName = viewModel::onName, onGender = viewModel::onGender,
            onDeceased = viewModel::onDeceased,
            onSolarBirthday = viewModel::onSolarBirthday,
            onLunarBirthday = viewModel::onLunarBirthday,
            onConvert = viewModel::onConvert,
            onNote = viewModel::onNote,
            onSiblingRank = viewModel::onSiblingRank,
            onAddPhone = viewModel::addPhone,
            onImportContact = { c -> viewModel.addPhoneFromContact(c.name, c.number) },
            onPickPhoneForRow = { key, c -> viewModel.pickPhoneForRow(key, c.name, c.number) },
            onUpdatePhone = viewModel::updatePhone,
            onRemovePhone = viewModel::removePhone, onSetPrimary = viewModel::setPrimary,
            onFocusHandled = viewModel::onFocusHandled,
            onMissingClick = viewModel::onMissingClick,
            onAnchor = viewModel::onAnchor, onRole = viewModel::onRole,
            onRemoveAvatar = viewModel::onRemoveAvatar, onPickAvatar = viewModel::onAvatar,
            onSave = { viewModel.save() },
            onSaveAndContinue = { viewModel.save(continueAdd = true) },
            lineage = LineageCallbacks(
                onLineage = viewModel::onLineage,
                onSharedParents = viewModel::onSharedParents,
                onPickFather = viewModel::onPickFather,
                onFatherUnsure = viewModel::onFatherUnsure,
                onLinkFather = viewModel::onLinkFather,
                onPickMother = viewModel::onPickMother,
                onMotherUnsure = viewModel::onMotherUnsure,
                onLinkMother = viewModel::onLinkMother,
                onMiddle = viewModel::onMiddle,
                onMiddleNone = viewModel::onMiddleNone,
                onTitleOnlyText = viewModel::onTitleOnlyText
            )
        )
    }

    PersonEditContent(
        st = st,
        missingText = viewModel.missingRequirements()
            .takeIf { it.isNotEmpty() }
            ?.let { "还差：" + it.joinToString("、") },
        snackbar = snackbar,
        onDone = onDone, onBack = onBack, cb = callbacks,
        lunar = viewModel.lunar,
        onConfirmSpouse = { viewModel.confirmSpouse() },
        onDismissSpouseAsk = { viewModel.dismissSpouseAsk() },
        onToggleCommonChild = viewModel::toggleCommonChild,
        onConfirmCommonChildren = { viewModel.confirmCommonChildren() },
        onDismissCommonChildren = { viewModel.dismissCommonChildren() }
    )
}

/** 无状态表单主体：所有编辑动作通过 [PersonEditCallbacks] 回调。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonEditContent(
    st: PersonEditUiState,
    /** 固定底栏缺项提示（如“还差：姓名、性别”），null 不缺。 */
    missingText: String? = null,
    snackbar: SnackbarHostState,
    onDone: () -> Unit,
    onBack: () -> Unit,
    cb: PersonEditCallbacks,
    lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar? = null,
    onConfirmSpouse: () -> Unit = {},
    onDismissSpouseAsk: () -> Unit = {},
    onToggleCommonChild: (String) -> Unit = {},
    onConfirmCommonChildren: () -> Unit = {},
    onDismissCommonChildren: () -> Unit = {}
) {
    var showDiscard by remember { mutableStateOf(false) }
    val permissionRequester = rememberNotificationPermissionRequester()
    // 保存成功且填写了生日：先走通知权限说明，再回到上一页
    LaunchedEffect(st.done) {
        if (st.done) {
            if (st.solarBirthday.on || st.lunarBirthday.on)
                permissionRequester.request { onDone() } else onDone()
        }
    }
    // “第二位父母”记为夫妻询问（新增父母成功且无配偶候选冲突时）
    st.spouseAsk?.let { ask ->
        AlertDialog(
            onDismissRequest = onDismissSpouseAsk,
            title = { Text("记为夫妻吗？") },
            text = {
                Text(ask.newPersonName + "和" + ask.candidateName +
                    "是夫妻吗？")
            },
            confirmButton = {
                TextButton(onClick = onConfirmSpouse) {
                    Text("是")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSpouseAsk) {
                    Text("不是")
                }
            }
        )
    }
    // 结为夫妻后的“共同子女”询问（新建配偶成功且有候选时）
    st.commonChildrenAsk?.let { ask ->
        CommonChildrenDialog(
            ask = ask,
            onToggle = onToggleCommonChild,
            onConfirm = onConfirmCommonChildren,
            onNone = onDismissCommonChildren
        )
    }

    // 创建“我”是首个页面：不拦截系统返回（返回即离开应用），也不显示返回键
    val isSelfMode = st.mode == PersonEditMode.SELF
    val tryBack = { if (st.dirty) showDiscard = true else onBack() }
    BackHandler(enabled = st.dirty && !isSelfMode) { showDiscard = true }

    if (showDiscard) {
        ConfirmDialog(
            text = "放弃未保存的修改？",
            confirmText = "放弃",
            onConfirm = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false }
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { cb.onPickAvatar(it) } }

    // 定位请求：每个字段绑到自己的控件与提示区域（姓名框可唤起键盘）；
    // 电话行由各行的 requester 处理
    val nameBring = remember { BringIntoViewRequester() }
    val nameFocus = remember { FocusRequester() }
    val genderBring = remember { BringIntoViewRequester() }
    val anchorBring = remember { BringIntoViewRequester() }
    val roleBring = remember { BringIntoViewRequester() }
    val lineageBring = remember { BringIntoViewRequester() }
    LaunchedEffect(st.focusTarget) {
        when (st.focusTarget) {
            EditFocusTarget.NAME -> {
                nameBring.bringIntoView()
                nameFocus.requestFocus()
            }
            EditFocusTarget.GENDER -> genderBring.bringIntoView()
            EditFocusTarget.ANCHOR -> anchorBring.bringIntoView()
            EditFocusTarget.ROLE -> roleBring.bringIntoView()
            EditFocusTarget.LINEAGE -> lineageBring.bringIntoView()
            null -> {}
        }
        if (st.focusTarget != null) cb.onFocusHandled()
    }

    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            if (st.mode != PersonEditMode.SELF) {
                TopAppBar(
                    title = {
                        Text(if (st.mode == PersonEditMode.NEW) "添加家人" else "编辑资料")
                    },
                    navigationIcon = {
                        IconButton(onClick = tryBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        },
        bottomBar = {
            // “允许尝试提交”≠“校验通过”：加载完成即可点击，点击后由 ViewModel
            // 校验——缺填就地标错并定位第一处，通过才写库；
            // busy 在保存中禁用，防重复提交。缺项提示仍可点击定位，但不是唯一入口。
            io.github.xiaohaoz1.jiarenxia.ui.common.FormActionBar(
                primaryText = if (st.mode == PersonEditMode.SELF) "下一步" else "保存",
                onPrimary = { cb.onSave() },
                primaryEnabled = st.loaded && !st.done,
                busy = st.saving,
                secondaryText = if (st.mode == PersonEditMode.NEW) "保存并继续添加" else null,
                onSecondary = if (st.mode == PersonEditMode.NEW) cb.onSaveAndContinue else null,
                secondaryEnabled = st.loaded && !st.done,
                missingText = if (st.saving) null else missingText,
                onMissingClick = cb.onMissingClick
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // SELF 页头：渐变 + 插画 + 标题
            if (st.mode == PersonEditMode.SELF) {
                PageHeader {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("创建“我”", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "“我”是整个家庭关系的中心",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                // 头像：居中 + 相机角标
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box {
                        val avatarUri = st.avatarUri
                        if (avatarUri != null) {
                            PickedAvatar(avatarUri, 96.dp)
                        } else {
                            PersonAvatar(st.avatarPath, st.name.ifBlank { "?" }, 96.dp)
                        }
                        Surface(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PhotoCamera, contentDescription = "选择头像",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                if (st.avatarPath != null || st.avatarUri != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { cb.onRemoveAvatar() }) { Text("移除头像") }
                    }
                }

                // 基本信息在关联卡之前：先写姓名，再选关系
                JrxCard(Modifier.fillMaxWidth()) {
                    FormSectionTitle("基本信息")
                    OutlinedTextField(
                        value = st.name,
                        onValueChange = cb.onName,
                        label = { Text("姓名*") },
                        isError = PersonEditField.NAME in st.fieldErrors,
                        supportingText = {
                            if (PersonEditField.NAME in st.fieldErrors)
                                Text("请填写姓名")
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                            .bringIntoViewRequester(nameBring)
                            .focusRequester(nameFocus),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    // 性别区块整体定位：标题 + 选项 + 错误提示一起滚入视野
                    Column(Modifier.fillMaxWidth().bringIntoViewRequester(genderBring)) {
                        Text("性别*", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        val genderError = PersonEditField.GENDER in st.fieldErrors
                        SingleChoiceSegmentedButtonRow(
                            Modifier.fillMaxWidth().then(
                                if (genderError) Modifier.border(
                                    1.dp, MaterialTheme.colorScheme.error,
                                    MaterialTheme.shapes.medium
                                ) else Modifier
                            )
                        ) {
                            listOf(Gender.MALE to "男", Gender.FEMALE to "女")
                                .forEachIndexed { i, (g, label) ->
                                    SegmentedButton(
                                        selected = st.gender == g,
                                        onClick = { cb.onGender(g) },
                                        shape = SegmentedButtonDefaults
                                            .itemShape(index = i, count = 2)
                                    ) { Text(label) }
                                }
                        }
                        if (genderError) FieldErrorText("请选择性别")
                    }
                    // 兄弟姐妹排行（可不填）：只影响同代排序，不参与称谓
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = st.siblingRank,
                            onValueChange = cb.onSiblingRank,
                            label = { Text("在兄弟姐妹中排第几（可不填）") },
                            shape = MaterialTheme.shapes.medium,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        st.siblingRank.toIntOrNull()?.takeIf { it >= 1 }?.let {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                siblingRankWord(it),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // 已故开关（默认在世不显示；创建“我”与编辑“我”都不显示此行）
                    if (st.mode != PersonEditMode.SELF && !st.isSelf) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            // 整行 toggleable：已故标签与开关语义关联
                            modifier = Modifier.padding(top = 8.dp).toggleable(
                                value = st.deceased, role = Role.Switch,
                                onValueChange = cb.onDeceased
                            )
                        ) {
                            Text("已故", modifier = Modifier.weight(1f))
                            Switch(checked = st.deceased, onCheckedChange = null)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // NEW 模式：关联卡片（紧随基本信息之后）
                if (st.mode == PersonEditMode.NEW) {
                    AnchorCard(st, cb, anchorBring, roleBring, lineageBring)
                    Spacer(Modifier.height(12.dp))
                }

                // 生日：阳历 / 农历两个独立槽位
                JrxCard(Modifier.fillMaxWidth()) {
                    FormSectionTitle("生日")
                    BirthdaySlot(
                        label = "阳历生日",
                        calendar = io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.SOLAR,
                        form = st.solarBirthday,
                        onChange = cb.onSolarBirthday,
                        lunar = lunar
                    )
                    // 阳历完整且农历槽未填 → 提供用户主动换算（打开页面不自动保存）
                    if (st.solarBirthday.complete() && !st.lunarBirthday.on) {
                        TextButton(onClick = {
                            cb.onConvert(io.github.xiaohaoz1.jiarenxia.core.model
                                .CalendarType.SOLAR)
                        }) { Text("换算为农历生日填入") }
                    }
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    BirthdaySlot(
                        label = "农历生日",
                        calendar = io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR,
                        form = st.lunarBirthday,
                        onChange = cb.onLunarBirthday,
                        lunar = lunar
                    )
                    if (st.lunarBirthday.complete() && !st.solarBirthday.on) {
                        TextButton(onClick = {
                            cb.onConvert(io.github.xiaohaoz1.jiarenxia.core.model
                                .CalendarType.LUNAR)
                        }) { Text("换算为阳历生日填入") }
                    }
                    if (st.birthdayConflict) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "两份生日换算出的公历日期不一致，请核对",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 电话：可稍后补充；手动填写复用空行，联系人选择去重
                JrxCard(Modifier.fillMaxWidth()) {
                    FormSectionTitle("电话（可稍后补充）")
                    st.phones.forEach { row ->
                        PhoneRow(
                            row = row,
                            focused = st.focusPhoneKey == row.rowKey,
                            focusKeyboard = st.focusPhoneKeyboard,
                            onFocused = cb.onFocusHandled,
                            cb = cb
                        )
                    }
                    // 两条明确入口：窄屏允许换行
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = cb.onAddPhone) {
                            Icon(Icons.Rounded.Add, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("手动填写")
                        }
                        // 从联系人选择（ACTION_PICK，无需通讯录权限）
                        val ctx = LocalContext.current
                        val importLauncher = rememberLauncherForActivityResult(
                            io.github.xiaohaoz1.jiarenxia.ui.common.PickPhoneContact()
                        ) { picked -> picked?.let(cb.onImportContact) }
                        OutlinedButton(onClick = {
                            try {
                                importLauncher.launch(Unit)
                            } catch (e: android.content.ActivityNotFoundException) {
                                Toast.makeText(ctx, "未找到通讯录应用", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Rounded.ContactPhone, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("从联系人选择")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                // 备注
                JrxCard(Modifier.fillMaxWidth()) {
                    FormSectionTitle("备注")
                    OutlinedTextField(
                        value = st.note,
                        onValueChange = cb.onNote,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 排行的口语叫法：1–10 用「老大…老十」，更大用「第 N」。 */
internal fun siblingRankWord(n: Int): String {
    val words = listOf("老大", "老二", "老三", "老四", "老五",
        "老六", "老七", "老八", "老九", "老十")
    return words.getOrNull(n - 1) ?: "第 " + n
}

/** 卡片内小节标题。 */
@Composable
private fun FormSectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(10.dp))
}

/** 字段就地错误文案：贴着对应控件显示，文字提示与错误色同时存在。 */
@Composable
private fun FieldErrorText(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** 非文本控件的错误外框：文字提示之外再给选择区域一个明确的错误边界。 */
@Composable
private fun errorFrameModifier(show: Boolean): Modifier =
    if (show) Modifier
        .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.medium)
        .padding(8.dp)
    else Modifier

/** 新增模式：关联卡片（关联人 + 角色选项 + 兄妹血缘细分；各区块独立定位）。 */
@Composable
private fun AnchorCard(
    st: PersonEditUiState,
    cb: PersonEditCallbacks,
    /** 关联人行定位 requester。 */
    anchorBring: BringIntoViewRequester,
    /** 称谓选项定位 requester。 */
    roleBring: BringIntoViewRequester,
    /** 血缘区块定位 requester。 */
    lineageBring: BringIntoViewRequester
) {
    val anchorError = PersonEditField.ANCHOR in st.fieldErrors
    val roleError = PersonEditField.ROLE in st.fieldErrors
    val lineageError = PersonEditField.LINEAGE in st.fieldErrors
    JrxCard(Modifier.fillMaxWidth()) {
        FormSectionTitle("关联")
        // 关联人行：头像 + 姓名 + 称谓 + 错误提示，整体定位
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(anchorBring)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .then(errorFrameModifier(anchorError))
            ) {
            val anchor = st.anchorCandidates.firstOrNull { it.id == st.anchorId }
            if (st.anchorId != null && st.anchorId.isNotBlank()) {
                PersonAvatar(anchor?.avatarPath, st.anchorName, 40.dp)
                Spacer(Modifier.width(10.dp))
                Text(st.anchorName, style = MaterialTheme.typography.titleMedium)
                if (st.anchorTitle.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    TitleChip(st.anchorTitle, ChipTone.OTHER)
                }
            } else {
                Text("未选择关联人", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (st.anchorSelectable) {
                Spacer(Modifier.weight(1f))
                var open by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { open = true }) {
                        Text(if (st.anchorId.isNullOrBlank()) "选择关联人" else "更换")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        st.anchorCandidates.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = {
                                cb.onAnchor(p.id); open = false
                            })
                        }
                    }
                }
            }
        }
            if (anchorError) FieldErrorText("请选择关联人")
        }
        // 角色
        if (!st.anchorId.isNullOrBlank()) {
            // 称谓按“我”的视角时（关联人是父/母），主语也用“我”
            val subject = if (st.anchorAsParent != null)
                st.selfName.ifBlank { "我" } else st.anchorName
            // 称谓区块整体定位：引导语 + 选项 + 错误提示一起滚入视野
            Column(Modifier.fillMaxWidth().bringIntoViewRequester(roleBring)) {
                Spacer(Modifier.height(8.dp))
                Text("TA 是" + subject + "的：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth()
                        .then(errorFrameModifier(roleError)),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    st.roleOptions.forEach { r ->
                        FilterChip(
                            selected = st.role == r,
                            onClick = { cb.onRole(r) },
                            leadingIcon = {
                                Icon(roleIcon(r), contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                            },
                            // 用户视角称谓：关联人是爸/妈时兄妹显示为伯叔姑/舅姨
                            label = { Text(RelativePlan.roleLabel(r, st.anchorAsParent)) }
                        )
                    }
                }
                if (roleError) FieldErrorText("请选择关系")
                // 句子预览：姓名填了就代入，明确是谁与谁的关系（与“添加关系”同一句式）
                if (st.role != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            "「" + st.name.ifBlank { "TA" } + "」是「" + subject +
                                "」的" + RelativePlan.roleLabel(st.role, st.anchorAsParent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 10.dp
                            )
                        )
                    }
                }
            }
            // 兄妹称谓的血缘细分：亲/堂表/只记称呼（缺项定位目标，
            // 提示在区块内顶部，保证滚动定位后与控件一起可见）
            if (st.role in RelativePlan.SIBLING_ROLES) {
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier.fillMaxWidth()
                        .bringIntoViewRequester(lineageBring)
                        .then(errorFrameModifier(lineageError))
                ) {
                    if (lineageError) FieldErrorText("请完善血缘或称呼信息")
                    LineageSection(
                        st = LineageUiState(
                            lineage = st.lineage,
                            sharedParents = st.sharedParents,
                            anchorName = st.anchorName,
                            fatherCandidates = st.fatherCandidates,
                            motherCandidates = st.motherCandidates,
                            pickedFatherId = st.pickedFatherId,
                            pickedMotherId = st.pickedMotherId,
                            fatherUnsure = st.fatherUnsure,
                            motherUnsure = st.motherUnsure,
                            linkFather = st.linkFather,
                            linkMother = st.linkMother,
                            middleCandidates = st.middleCandidates,
                            middleId = st.middleId,
                            middleNone = st.middleNone,
                            titleOnlyText = st.titleOnlyText,
                            subjectGender = st.gender,
                            // 称呼文本是当前拦截项时才给输入框标错
                            titleError = lineageError && st.titleOnlyText.isBlank()
                        ),
                        cb = cb.lineage
                    )
                }
            }
        }
    }
}

/**
 * 单槽生日表单：开关 + 年份（可“不知道”）+ 月 + 日；
 * 农历槽用中文月/日名并附“闰月”开关。所有变更整体回传 [onChange]。
 */
@Composable
private fun BirthdaySlot(
    label: String,
    calendar: io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,
    form: BirthdayFormState,
    onChange: (BirthdayFormState) -> Unit,
    lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(
            value = form.on, role = Role.Switch,
            onValueChange = { onChange(form.copy(on = it)) }
        )
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = form.on, onCheckedChange = null)
    }
    if (!form.on) return
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 出生年份下限放宽到 1900：长辈可能生于 1949 年前
        YearDropdown(
            value = form.yearText.toIntOrNull(),
            enabled = !form.yearUnknown,
            from = 1900,
            placeholder = "出生年份",
            onPick = { onChange(form.copy(yearText = it.toString())) }
        )
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable {
                onChange(form.copy(yearUnknown = !form.yearUnknown))
            }
        ) {
            Checkbox(
                checked = form.yearUnknown,
                onCheckedChange = { onChange(form.copy(yearUnknown = it)) }
            )
            Text("不知道出生年份", style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(8.dp))
    val lunarMode = calendar == io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR
    val knownYear = if (form.yearUnknown) null else form.yearText.toIntOrNull()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lunarMode) {
            // 农历月名下拉
            LunarMonthDropdown(form.month) { onChange(form.copy(month = it)) }
            // 闰月：仅年份未知或该年确实闰此月时可勾
            val leapOk = knownYear == null || lunar?.leapMonthOf(knownYear) == form.month
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = leapOk) {
                    onChange(form.copy(leapMonth = !form.leapMonth))
                }
            ) {
                Checkbox(
                    checked = form.leapMonth,
                    onCheckedChange = { onChange(form.copy(leapMonth = it)) },
                    enabled = leapOk
                )
                Text("闰月", style = MaterialTheme.typography.bodySmall)
            }
            // 农历日：初一..三十；年份已知时按实际天数
            val maxD = if (knownYear == null || lunar == null) 30
                else lunar.daysInMonth(knownYear, form.month, form.leapMonth) ?: 30
            LunarDayDropdown(form.day, 1..maxD) { onChange(form.copy(day = it)) }
        } else {
            NumberDropdown(
                label = "月", value = form.month, range = 1..12,
                onPick = { onChange(form.copy(month = it)) }
            )
            NumberDropdown(
                label = "日", value = form.day, range = 1..dayMax(form),
                onPick = { onChange(form.copy(day = it)) }
            )
        }
    }
}

private fun dayMax(f: BirthdayFormState): Int {
    val y = f.yearText.toIntOrNull()
    return if (f.yearUnknown || y == null) java.time.Month.of(f.month).maxLength()
    else java.time.YearMonth.of(y, f.month).lengthOfMonth()
}

/** 农历月下拉：正月..腊月。 */
@Composable
private fun LunarMonthDropdown(value: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat.monthName(value),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (1..12).forEach { v ->
                DropdownMenuItem(
                    text = {
                        Text(io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat.monthName(v))
                    },
                    onClick = { onPick(v); open = false }
                )
            }
        }
    }
}

/** 农历日下拉：初一..三十。 */
@Composable
private fun LunarDayDropdown(value: Int, range: IntRange, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat.dayName(value),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            range.forEach { v ->
                DropdownMenuItem(
                    text = {
                        Text(io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarFormat.dayName(v))
                    },
                    onClick = { onPick(v); open = false }
                )
            }
        }
    }
}

/** 数字下拉（月 / 日）：圆角色块按钮。 */
@Composable
private fun NumberDropdown(label: String, value: Int, range: IntRange, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                label + "：" + value,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            range.forEach { v ->
                DropdownMenuItem(text = { Text(v.toString()) }, onClick = { onPick(v); open = false })
            }
        }
    }
}

/**
 * 单条电话表单行：号码框 + 「从联系人选择」「删除这条电话」文字操作；
 * 标签/在用/主号码/号码备注收在「更多号码设置」展开区（展开与否不影响保存）。
 * [focused] 为真时滚入视野（[focusKeyboard] 决定顺带聚焦号码框）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhoneRow(
    row: PhoneRowState,
    focused: Boolean,
    focusKeyboard: Boolean,
    onFocused: () -> Unit,
    cb: PersonEditCallbacks
) {
    val labels = listOf(
        PhoneLabel.MOBILE to "手机", PhoneLabel.WORK to "工作",
        PhoneLabel.HOME to "家庭", PhoneLabel.BACKUP to "备用", PhoneLabel.OTHER to "其他"
    )
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(focused) {
        if (focused) {
            bring.bringIntoView()
            if (focusKeyboard) focus.requestFocus()
            onFocused()
        }
    }
    // “更多号码设置”展开与否是本行 UI 状态，不算表单改动；按 rowKey 记忆
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(row.rowKey) {
        mutableStateOf(false)
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .bringIntoViewRequester(bring)
    ) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(
                value = row.number,
                onValueChange = { cb.onUpdatePhone(row.rowKey, row.copy(number = it)) },
                label = { Text("号码") },
                isError = row.error != null,
                supportingText = {
                    when {
                        row.error != null -> Text(row.error)
                        row.notice != null -> Text(
                            row.notice,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true
            )
            // 停用号码的可见摘要：收起“更多号码设置”也不能藏掉状态
            if (row.status == PhoneStatus.INACTIVE) {
                Text(
                    "已停用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // 大字号下按钮放不下就整体换行，不允许逐字折行
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Center
            ) {
                // 从联系人选择（ACTION_PICK，无需 READ_CONTACTS），只作用本行
                val rowCtx = LocalContext.current
                val pickLauncher = rememberLauncherForActivityResult(
                    io.github.xiaohaoz1.jiarenxia.ui.common.PickPhoneContact()
                ) { picked ->
                    picked?.let { cb.onPickPhoneForRow(row.rowKey, it) }
                }
                TextButton(onClick = {
                    try {
                        pickLauncher.launch(Unit)
                    } catch (e: android.content.ActivityNotFoundException) {
                        Toast.makeText(rowCtx, "未找到通讯录应用", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("从联系人选择", maxLines = 1, softWrap = false) }
                TextButton(onClick = { cb.onRemovePhone(row.rowKey) }) {
                    Text("删除这条电话", color = MaterialTheme.colorScheme.error,
                        maxLines = 1, softWrap = false)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起设置" else "更多号码设置",
                        maxLines = 1, softWrap = false)
                }
            }
            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    var open by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { open = true }) {
                            Text(labels.first { it.first == row.label }.second)
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            labels.forEach { (l, name) ->
                                DropdownMenuItem(text = { Text(name) }, onClick = {
                                    cb.onUpdatePhone(row.rowKey, row.copy(label = l))
                                    open = false
                                })
                            }
                        }
                    }
                    // 在用/已停用：停用自动清主号码
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // 整行 toggleable：在用/停用标签与开关语义关联
                        modifier = Modifier.toggleable(
                            value = row.status == PhoneStatus.ACTIVE, role = Role.Switch
                        ) { on ->
                            cb.onUpdatePhone(row.rowKey, row.copy(
                                status = if (on) PhoneStatus.ACTIVE else PhoneStatus.INACTIVE,
                                isPrimary = if (on) row.isPrimary else false
                            ))
                        }
                    ) {
                        Text(if (row.status == PhoneStatus.ACTIVE) "在用" else "已停用",
                            style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = row.status == PhoneStatus.ACTIVE,
                            onCheckedChange = null
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            enabled = row.status == PhoneStatus.ACTIVE
                        ) { cb.onSetPrimary(row.rowKey) }
                    ) {
                        RadioButton(
                            selected = row.isPrimary,
                            onClick = { cb.onSetPrimary(row.rowKey) },
                            enabled = row.status == PhoneStatus.ACTIVE
                        )
                        Text("主号码", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = row.note ?: "",
                    onValueChange = {
                        cb.onUpdatePhone(row.rowKey,
                            row.copy(note = it.ifBlank { null }))
                    },
                    label = { Text("号码备注（可空）") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    singleLine = true
                )
            }
        }
    }
}
