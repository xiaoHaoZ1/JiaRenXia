package io.github.xiaohaoz1.jiarenxia.ui.graph

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportDialogs
import io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportStep
import io.github.xiaohaoz1.jiarenxia.ui.backup.CreateBackupDocument
import io.github.xiaohaoz1.jiarenxia.ui.backup.backupFileName
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.ChipTone
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PersonAvatar
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.TitleChip

/** 画布当前视口缩放（语义属性，供测试断言阅读比例）。 */
internal val GraphViewportScaleKey =
    SemanticsPropertyKey<Float>("graphViewportScale")

/** 画布当前视口平移（供测试断言重建后视口一致）。 */
internal val GraphViewportPanXKey =
    SemanticsPropertyKey<Float>("graphViewportPanX")
internal val GraphViewportPanYKey =
    SemanticsPropertyKey<Float>("graphViewportPanY")

/** 芯片文案（与布局实测同一份，保证矩形一致）。 */
private fun chipLabel(c: PlacedChip) =
    GraphLayout.chipNoun(c.dir) + " " + c.count + " 位 ＋"

/**
 * 家庭关系图（视觉改版）：独立画布 + 明确工具区。
 * 顶部：返回/标题/查找/更多；下方一行显示当前范围与已展开人数；
 * 底部固定“回到我 / 展开家人 / 查看总览”。画布内只保留卡片、连线、
 * 家庭分组标签、折行标记与展开芯片，可拖动与双指缩放。
 */
@Composable
fun GraphScreen(
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    /** 菜单“分享这些家人”：以当前分支为预设打开选人页。 */
    onShareScope: (GraphBranch) -> Unit = {},
    /** 顶栏“添加家人”：直达添加入口页，回来图自动刷新。 */
    onAddFamily: () -> Unit = {},
    viewModel: GraphViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var forcedLandscape by rememberSaveable { mutableStateOf(false) }
    // 用户主动切过方向后，关闭横屏立即回竖屏（UNSPECIFIED 恢复依赖系统、有延迟）
    var userToggled by rememberSaveable { mutableStateOf(false) }

    // 手动横屏：不依赖系统自动旋转开关；离开关系图或关闭横屏后恢复默认
    LaunchedEffect(forcedLandscape, userToggled) {
        activity?.requestedOrientation =
            when {
                forcedLandscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                userToggled -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            // 旋转本身会重建 Activity（isChangingConfigurations），此时不重置
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    // 导出完成：分享调起 chooser；保存弹提示
    val exp by viewModel.exportState.collectAsStateWithLifecycle()
    LaunchedEffect(exp) {
        when (val e = exp) {
            is GraphExportState.ShareReady -> {
                context.startActivity(GraphExport.shareIntent(e.uri))
                viewModel.clearExportState()
            }
            GraphExportState.AlbumDone -> {
                android.widget.Toast.makeText(
                    context, "已保存到相册：家人侠",
                    android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearExportState()
            }
            GraphExportState.AlbumFailed -> {
                android.widget.Toast.makeText(
                    context, "保存失败，请重试",
                    android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearExportState()
            }
            else -> Unit
        }
    }

    // 备份：选保存位置（密码已在导出流程内暂存）；备份的始终是全部数据
    val exportBackupLauncher = rememberLauncherForActivityResult(
        CreateBackupDocument()
    ) { uri -> viewModel.backupExport.onTarget(uri, context) }
    val backupStep by viewModel.backupExport.step.collectAsStateWithLifecycle()
    LaunchedEffect(backupStep) {
        if (backupStep == BackupExportStep.PickExportTarget) {
            exportBackupLauncher.launch(backupFileName())
        }
    }
    val backupMsg by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(backupMsg) {
        backupMsg?.let {
            android.widget.Toast.makeText(
                context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }

    GraphContent(
        model = st.model,
        byId = st.byId,
        selfId = st.selfId,
        exporting = exp == GraphExportState.Generating,
        onExport = viewModel::exportImage,
        onBack = onBack,
        onOpenPerson = onOpenPerson,
        landscape = forcedLandscape,
        onToggleLandscape = {
            forcedLandscape = !forcedLandscape
            userToggled = true
        },
        branch = st.branch,
        availableBranches = st.availableBranches,
        familyTabs = GraphFamilyScope.tabs,
        familyRoots = st.familyRoots,
        scopeAnchorId = st.scopeAnchorId,
        focusedId = st.focusedId,
        hasExpanded = st.hasExpanded,
        connectedTotal = st.connectedTotal,
        expandGroups = st.expandGroups,
        scopeLabel = st.scopeLabel,
        cameraSeq = st.cameraSeq,
        cameraTarget = st.cameraTarget,
        cameraRestore = st.cameraRestore,
        onSelectBranch = viewModel::selectBranch,
        onExpand = viewModel::expand,
        onExpandAll = viewModel::expandAllNow,
        onCollapse = viewModel::collapseBranches,
        onLocate = viewModel::focusOn,
        onFocusFamily = viewModel::focusFamily,
        onBackup = viewModel.backupExport::start,
        onShareScope = onShareScope,
        onAddFamily = onAddFamily,
        showEdgeLabels = st.showEdgeLabels,
        onToggleEdgeLabels = viewModel::setEdgeLabelsEnabled
    )

    // 备份导出对话框（设置密码 / 进行中），与我的页共用；
    // 按分支筛选查看时提示备份范围仍是全部家人
    BackupExportDialogs(
        step = backupStep,
        onSubmitPassword = { viewModel.backupExport.submitPassword(it) },
        onDismiss = viewModel.backupExport::dismiss,
        notice = if (st.branch != GraphBranch.ALL)
            "备份的是全部家人数据，不只是当前显示的分支" else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphContent(
    model: GraphModel?,
    byId: Map<String, PersonOverview>,
    selfId: String?,
    exporting: Boolean = false,
    onExport: (String, GraphImageRenderer.Quality) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onOpenPerson: (String) -> Unit,
    landscape: Boolean = false,
    onToggleLandscape: (() -> Unit)? = null,
    branch: GraphBranch = GraphBranch.ALL,
    availableBranches: List<GraphBranch> = listOf(GraphBranch.ALL),
    focusedId: String? = null,
    /** 是否已有展开操作（决定“收起全部旁支”是否出现）。 */
    hasExpanded: Boolean = false,
    /** 当前范围内关联总人数（“已展开 N/M”分母）。 */
    connectedTotal: Int = 0,
    /** “展开家人”面板分组。 */
    expandGroups: List<GraphVisibility.Group> = emptyList(),
    /** 最近一次展开动作的分组标签。 */
    scopeLabel: String? = null,
    cameraSeq: Long = 0,
    cameraTarget: String? = null,
    /** 相机请求是否恢复 1.0 阅读比例（收起旁支=是）。 */
    cameraRestore: Boolean = false,
    onSelectBranch: (GraphBranch) -> Unit = {},
    /** 展开一组家庭块：（展开键，分组标签，定位目标）。 */
    onExpand: (Set<GraphVisibility.Expand>, String?, String?) -> Unit = { _, _, _ -> },
    onExpandAll: () -> Unit = {},
    /** 收起全部旁支（只收起显示，不删除资料）。 */
    onCollapse: () -> Unit = {},
    onLocate: (String) -> Unit = {},
    /** 面板“已展开·定位”。 */
    onFocusFamily: (String) -> Unit = {},
    /** 菜单“备份全部数据”：导出入口（流程与我的页一致）。 */
    onBackup: () -> Unit = {},
    /** 菜单“分享这些家人”：以当前分支为预设打开选人页。 */
    onShareScope: (GraphBranch) -> Unit = {},
    /** 是否在每条关系线上标注关系词（父子/夫妻…）。 */
    showEdgeLabels: Boolean = false,
    onToggleEdgeLabels: (Boolean) -> Unit = {},
    /** 六个家庭范围入口；空列表用于独立画布预览和旧版几何测试。 */
    familyTabs: List<GraphBranch> = emptyList(),
    familyRoots: List<String> = emptyList(),
    scopeAnchorId: String? = null,
    /** 顶栏“添加家人”入口；默认空实现便于独立预览。 */
    onAddFamily: () -> Unit = {}
) {
    var scale by rememberSaveable { mutableStateOf(1f) }
    var panX by rememberSaveable { mutableStateOf(0f) }
    var panY by rememberSaveable { mutableStateOf(0f) }
    var container by remember { mutableStateOf(IntSize.Zero) }
    // 以下定位/消费标记全部 rememberSaveable：界面重建后旧相机
    // 请求不得重放，也不允许初始化效果覆盖已恢复的视口
    var centeredKey by rememberSaveable { mutableStateOf("") }
    /** 上次定位时的几何指纹（容器宽x高|字号），同几何不重定位。 */
    var lastGeom by rememberSaveable { mutableStateOf("") }
    /** 已消费的中心焦点（区分“重建恢复”与“新焦点定位”）。 */
    var handledFocusId by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var expandSheet by remember { mutableStateOf(false) }
    var familySheet by remember(branch) { mutableStateOf(false) }
    var menuSheet by remember { mutableStateOf(false) }
    var legendOpen by remember { mutableStateOf(false) }
    var conflictsOpen by remember { mutableStateOf(false) }
    val parentConflicts = remember(model) { model?.parentGenerationConflicts.orEmpty() }
    var overview by rememberSaveable { mutableStateOf(false) }
    /** “回到我”请求计数：画布内 LaunchedEffect 消费。 */
    var homeReq by rememberSaveable { mutableIntStateOf(0) }
    /** 已消费的 homeReq：重建后不得重放回到我。 */
    var handledHome by rememberSaveable { mutableIntStateOf(0) }
    /** 总览中点选的人（回到阅读后短暂高亮）。 */
    var overviewPick by rememberSaveable { mutableStateOf<String?>(null) }
    var handledCam by rememberSaveable { mutableLongStateOf(0L) }
    var exportKind by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    // 横屏高度紧张：底栏操作挪进画布悬浮、分支选择收成单行横滑，把高度让给画布
    val landscape = LocalConfiguration.current.orientation ==
        Configuration.ORIENTATION_LANDSCAPE
    val familyMode = familyTabs.isNotEmpty()
    fun zoomBy(multiplier: Float) {
        val old = scale
        val next = (old * multiplier).coerceIn(0.15f, 2.5f)
        val x = container.width / 2f
        val y = container.height / 2f
        panX = x - (x - panX) * next / old
        panY = y - (y - panY) * next / old
        scale = next
        overview = false
        overviewPick = null
    }

    val scopeTitle = when {
        familyMode && branch == GraphBranch.ALL -> "我的家庭"
        familyMode -> scopeAnchorId?.let { id ->
            byId[id]?.let { it.displayTitle + " " + it.person.name + "的家庭" }
        } ?: GraphFamilyScope.labelOf(branch)
        focusedId != null ->
            "已定位 · " + (byId[focusedId]?.person?.name ?: "")
        scopeLabel != null -> scopeLabel
        branch != GraphBranch.ALL -> branch.label + "这一支"
        else -> "身边的家人"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("家庭关系图") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Rounded.Search, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("查找")
                    }
                    IconButton(onClick = onAddFamily) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = "添加家人")
                    }
                    IconButton(onClick = { menuSheet = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                    }
                }
            )
        },
        bottomBar = {
            // 横屏不渲染底栏：同样的操作改到画布内悬浮（见下方画布末尾）
            if (!landscape) {
                Column(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant)
                    // 三个胶囊按钮（≥48dp 触控高），回到我用浅主色强调
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BottomAction(
                            label = "回到我", primary = true,
                            modifier = Modifier.weight(1f)
                        ) {
                            overview = false
                            overviewPick = null
                            homeReq++
                        }
                        if (familyMode) {
                            BottomAction(label = "−", primary = false,
                                modifier = Modifier.weight(0.55f)) { zoomBy(0.8f) }
                            BottomAction(label = "＋", primary = false,
                                modifier = Modifier.weight(0.55f)) { zoomBy(1.25f) }
                        } else BottomAction(
                            label = "展开家人", primary = false,
                            modifier = Modifier.weight(1f)
                        ) { expandSheet = true }
                        BottomAction(
                            label = if (familyMode) "本支总览" else "查看总览", primary = false,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (overview) {
                                // 退出总览：回到本人家庭的阅读比例
                                overview = false
                                overviewPick = null
                                homeReq++
                            } else {
                                overview = true
                                overviewPick = null
                            }
                        }
                    }
                    Text(
                        if (familyMode) "称谓始终相对我 · 双指缩放拖动"
                        else "点卡片查看资料 · 双指缩放拖动",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (familyMode) {
                if (landscape) {
                    // 横屏：分支收成单行横滑，不再堆两行
                    Row(
                        Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        familyTabs.forEach { item ->
                            BranchChip(item, branch, item in availableBranches,
                                onSelect = {
                                    overview = false
                                    overviewPick = null
                                    onSelectBranch(item)
                                })
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        familyTabs.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { item ->
                                    BranchChip(item, branch, item in availableBranches,
                                        onSelect = {
                                            overview = false
                                            overviewPick = null
                                            onSelectBranch(item)
                                        },
                                        modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            // 第二行：查看范围 + 已展开人数（人物去重计数）
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    scopeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (familyMode) "${model?.nodes?.size ?: 0} 位家人"
                    else "已展开 " + (model?.nodes?.size ?: 0) + "/" +
                        connectedTotal + " 位",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (familyMode && branch != GraphBranch.ALL && familyRoots.size > 1) {
                val selectedRoot = cameraTarget?.takeIf { it in familyRoots }
                    ?: familyRoots.first()
                TextButton(onClick = { familySheet = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("定位家支 · " + (byId[selectedRoot]?.person?.name ?: "选择一家") + "  ⌄",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
            if (model != null && model.unconnectedCount > 0) {
                Text(
                    "另有" + model.unconnectedCount +
                        "位家人尚未与“我”关联，可在家庭页查看",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            // ---------- 独立画布 ----------
            if (parentConflicts.isNotEmpty()) {
                TextButton(
                    onClick = { conflictsOpen = true },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("有 " + parentConflicts.size + " 条亲子关系待核对 · 查看",
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .onSizeChanged { container = it }
            ) {
                if (model == null || model.nodes.isEmpty()) {
                    Text(
                        "添加家人后，这里会显示家庭关系图",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    // 路由结果：屏幕与 PNG 共用同一份折线/锚点/包围盒
                    val routing = remember(model) { GraphEdgeRouting.route(model) }
                    val bounds = routing.bounds
                    // 大字号：共享几何（卡片/通道/间距）按 fontBoost 同比放大，
                    // 内容坐标单位是基准 dp，渲染时乘 density 得 px
                    val fontBoost = maxOf(1f, density.fontScale)
                    val pxUnit = density.density * fontBoost
                    val contentW = bounds.width * pxUnit
                    val contentH = bounds.height * pxUnit
                    fun cx(n: GraphNode) =
                        (n.x - bounds.left + n.w / 2f) * pxUnit
                    fun cy(n: GraphNode) =
                        (n.y - bounds.top + n.h / 2f) * pxUnit

                    /**
                     * 初始视口：1.0 阅读比例。放得下则内容居中；放不下
                     * 居中本人家庭区域。判定用“节点包围盒”（卡片本体），
                     * 不含芯片/标签——芯片不能决定默认横向布局，也不能
                     * 为容纳左侧芯片把右侧父母推出屏外。
                     */
                    fun initialViewport(): GraphViewport.Viewport {
                        val self = model.nodes.firstOrNull {
                            it.personId == selfId }
                            ?: return GraphViewport.Viewport(1f, 0f, 0f)
                        val nb = routing.nodeBounds
                        return GraphViewport.initial(
                            nb.width * pxUnit, nb.height * pxUnit,
                            container.width.toFloat(), container.height.toFloat(),
                            cx(self), cy(self),
                            with(density) { 16.dp.toPx() }
                        )
                    }

                    /**
                     * 居中一块区域（回到我 / 展开定位 / 总览选点）。
                     * [restoreReading]=true 时明确恢复到 1.0 阅读比例；
                     * false（普通展开定位）保留当前缩放。
                     */
                    fun focusArea(
                        l: Float, t: Float, r: Float, b: Float,
                        restoreReading: Boolean
                    ) {
                        val vp = GraphViewport.focusRegion(
                            (l + r) / 2f, (t + b) / 2f,
                            container.width.toFloat(), container.height.toFloat(),
                            scale, preserveScale = !restoreReading
                        )
                        scale = vp.scale
                        panX = vp.panX; panY = vp.panY
                    }
                    fun focusBlockOf(pid: String, restoreReading: Boolean) {
                        // 聚焦范围 = 家庭块卡片 ∪ 贴附展开芯片（定位含相关入口）
                        val region = model.focusBlockOf(pid)
                        if (region != null) {
                            focusArea(
                                (region.left - bounds.left) * pxUnit,
                                (region.top - bounds.top) * pxUnit,
                                (region.right - bounds.left) * pxUnit,
                                (region.bottom - bounds.top) * pxUnit,
                                restoreReading)
                            return
                        }
                        val n = model.nodes.firstOrNull { it.personId == pid }
                            ?: return
                        focusArea(
                            (n.x - bounds.left) * pxUnit,
                            (n.y - bounds.top) * pxUnit,
                            (n.right - bounds.left) * pxUnit,
                            (n.bottom - bounds.top) * pxUnit,
                            restoreReading)
                    }

                    // 视口初始化/定位：键只含范围标识（分支+本人），
                    // 展开收起改变 model 但不触发重新居中——展开后的相机
                    // 由 cameraSeq 效果单独负责，避免两个效果互相覆盖。
                    // centeredKey/lastGeom/handledFocusId 均为 rememberSaveable：
                    // 界面重建后同内容同几何不重新定位，已恢复的视口不被覆盖。
                    val contentKey = branch.toString() + "|" + selfId
                    val geomKey = "" + container.width + "x" +
                        container.height + "|" + fontBoost
                    // 组合期快照：协程体内 container 读的是实时值，
                    // 若用实时值守门，携带旧 geomKey 的旧效果会在容器
                    // 就绪后晚跑、误判几何变化并重放定位。
                    val contReady =
                        container.width != 0 && container.height != 0
                    LaunchedEffect(contentKey, container, focusedId, fontBoost) {
                        if (!contReady)
                            return@LaunchedEffect
                        val geomSame = lastGeom == geomKey
                        if (focusedId != null) {
                            // 重建恢复：同内容、同几何且该焦点已定位过——
                            // 保留恢复的视口，不重复居中
                            if (centeredKey == contentKey && geomSame &&
                                handledFocusId == focusedId)
                                return@LaunchedEffect
                            val n = model.nodes.firstOrNull {
                                it.personId == focusedId } ?: return@LaunchedEffect
                            val vp = GraphViewport.centerOn(
                                cx(n), cy(n),
                                container.width.toFloat(),
                                container.height.toFloat(), scale)
                            scale = vp.scale
                            panX = vp.panX; panY = vp.panY
                            centeredKey = contentKey
                            lastGeom = geomKey
                            handledFocusId = focusedId
                            return@LaunchedEffect
                        }
                        handledFocusId = null
                        if (centeredKey != contentKey) {
                            if (model.nodes.none { it.personId == selfId })
                                return@LaunchedEffect
                            val vp = initialViewport()
                            scale = vp.scale
                            panX = vp.panX; panY = vp.panY
                            centeredKey = contentKey
                            lastGeom = geomKey
                        } else if (!geomSame) {
                            // 旋转/字号切换：重新测量后保持“我”的家庭在视野中央
                            //（几何随动，属普通定位——保留当前缩放）
                            selfId?.let { focusBlockOf(it, restoreReading = false) }
                            lastGeom = geomKey
                        }
                    }

                    // “回到我”：恢复到 1.0 阅读比例并居中本人所在家庭（保留展开范围）。
                    // 键不含 model——否则每次展开/收起都会重放这次居中，
                    // 把相机请求盖掉；几何变化由上面的初始化效果负责。
                    // handledHome 持久化：重建后已消费的请求不重放。
                    // container 进 key：容器未就绪时早退后必须重试，
                    // 不能留着未消费的请求等重建时重放。
                    LaunchedEffect(homeReq, container) {
                        if (homeReq == 0 || homeReq == handledHome)
                            return@LaunchedEffect
                        val sid = selfId ?: return@LaunchedEffect
                        if (!contReady)
                            return@LaunchedEffect
                        if (model.nodes.none { it.personId == sid })
                            return@LaunchedEffect
                        handledHome = homeReq
                        focusBlockOf(sid, restoreReading = true)
                    }

                    // 展开/定位/收起的相机请求：移动到目标家庭附近（seq 消费一次）。
                    // 目标恰是当前聚焦人时也执行块级定位——收起旁支后 model 变化
                    // 不会重启上面的定位效果，需要这里把本人家庭带回视野中央。
                    // cameraRestore：收起旁支=恢复 1.0；展开/查找=保留当前缩放。
                    // container 必须进 key：否则容器未就绪时的早退会让请求
                    // 保持未消费，重建后被旧 seq 重放（主审复现的根因）。
                    LaunchedEffect(cameraSeq, model, fontBoost, container) {
                        if (cameraSeq == handledCam) return@LaunchedEffect
                        val id = cameraTarget ?: return@LaunchedEffect
                        if (!contReady)
                            return@LaunchedEffect
                        if (model.nodes.none { it.personId == id })
                            return@LaunchedEffect
                        handledCam = cameraSeq
                        overview = false
                        focusBlockOf(id, restoreReading = cameraRestore)
                    }

                    // 总览：只缩放当前已展开范围，进入时计算一次 fit
                    LaunchedEffect(overview, model, container, fontBoost) {
                        if (!overview) return@LaunchedEffect
                        if (!contReady)
                            return@LaunchedEffect
                        val vp = GraphViewport.fitAll(
                            contentW, contentH,
                            container.width.toFloat(), container.height.toFloat(),
                            topInset = with(density) { 40.dp.toPx() },
                            padding = with(density) { 12.dp.toPx() }
                        )
                        scale = vp.scale
                        panX = vp.panX; panY = vp.panY
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("graphCanvas")
                            // 测试探针：暴露当前视口缩放/平移（阅读比例与
                            // 重建视口一致性的断言用）
                            .semantics {
                                this[GraphViewportScaleKey] = scale
                                this[GraphViewportPanXKey] = panX
                                this[GraphViewportPanYKey] = panY
                            }
                            // 缩放平移后的内容不得画进工具区/底栏
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { centroid, panDelta, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(0.12f, 2.5f)
                                    val realZoom = newScale / scale
                                    val pan = Offset(panX, panY)
                                    val np = centroid - (centroid - pan) * realZoom + panDelta
                                    panX = np.x; panY = np.y
                                    scale = newScale
                                }
                            }
                    ) {
                        Box(
                            // 缩放原点固定在左上角，与“屏幕 = 内容 * scale + pan”的约定一致
                            modifier = Modifier.graphicsLayer(
                                scaleX = scale, scaleY = scale,
                                translationX = panX, translationY = panY,
                                transformOrigin = TransformOrigin(0f, 0f)
                            )
                        ) {
                            val outline = MaterialTheme.colorScheme.outline
                            val faint = MaterialTheme.colorScheme.onSurfaceVariant
                            val spouseColor =
                                io.github.xiaohaoz1.jiarenxia.ui.theme
                                    .LocalJrxColors.current.spouseSide
                            // 各边关系词落点（布局单位，绘制时换算为内容 px）
                            val edgeLabelPts = remember(routing, showEdgeLabels) {
                                if (!showEdgeLabels) emptyList()
                                else GraphEdgeLabels.compute(routing)
                            }
                            val labelBg =
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            val canvasBackground = MaterialTheme.colorScheme.background
                            val labelFg =
                                MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
                            Canvas(
                                modifier = Modifier.size(
                                    (bounds.width * fontBoost).dp,
                                    (bounds.height * fontBoost).dp
                                )
                            ) {
                                fun ptX(x: Float) = (x - bounds.left) * pxUnit
                                fun ptY(y: Float) = (y - bounds.top) * pxUnit
                                val dash = PathEffect.dashPathEffect(
                                    floatArrayOf(12f, 8f))
                                // 折线边：只绘制路由结果
                                routing.paths.forEach { p ->
                                    val path = androidx.compose.ui.graphics.Path()
                                    p.points.forEachIndexed { i, q ->
                                        if (i == 0) path.moveTo(ptX(q.x), ptY(q.y))
                                        else path.lineTo(ptX(q.x), ptY(q.y))
                                    }
                                    when (p.style) {
                                        GraphEdgeRouting.RouteStyle.SPOUSE ->
                                            drawPath(path, spouseColor,
                                                style = androidx.compose.ui.graphics
                                                    .drawscope.Stroke(
                                                        3.dp.toPx(),
                                                        cap = StrokeCap.Round))
                                        GraphEdgeRouting.RouteStyle.SOLID ->
                                            drawPath(path, outline,
                                                style = androidx.compose.ui.graphics
                                                    .drawscope.Stroke(
                                                        2.dp.toPx(),
                                                        cap = StrokeCap.Round))
                                        GraphEdgeRouting.RouteStyle.DASHED ->
                                            drawPath(path, faint,
                                                style = androidx.compose.ui.graphics
                                                    .drawscope.Stroke(
                                                        1.dp.toPx(),
                                                        cap = StrokeCap.Round,
                                                        pathEffect = dash))
                                    }
                                }
                                // 跨线小桥：擦出下穿线的间隔，避免视觉上合成虚假的家庭接点。
                                routing.crossings.forEach { q ->
                                    val x = ptX(q.x); val y = ptY(q.y)
                                    val r = q.radius * pxUnit
                                    drawCircle(canvasBackground, r + 1.dp.toPx(), Offset(x, y))
                                    val bridge = androidx.compose.ui.graphics.Path().apply {
                                        moveTo(x - r, y)
                                        quadraticTo(x, y - r * 2, x + r, y)
                                    }
                                    val color = when (q.style) {
                                        GraphEdgeRouting.RouteStyle.SPOUSE -> spouseColor
                                        GraphEdgeRouting.RouteStyle.SOLID -> outline
                                        GraphEdgeRouting.RouteStyle.DASHED -> faint
                                    }
                                    drawPath(bridge, color, style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        if (q.style == GraphEdgeRouting.RouteStyle.SPOUSE) 3.dp.toPx() else 2.dp.toPx(),
                                        cap = StrokeCap.Round))
                                }
                                // 各边关系词（默认关闭，经更多菜单开启）
                                val labelPaint = android.graphics.Paint(
                                    android.graphics.Paint.ANTI_ALIAS_FLAG
                                ).apply {
                                    color = labelFg
                                    textSize = 10.dp.toPx() * fontBoost
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                                val fm = labelPaint.fontMetrics
                                val th = fm.descent - fm.ascent
                                val hPad = 4.dp.toPx() * fontBoost
                                val vPad = 1.dp.toPx()
                                val lGap = 3.dp.toPx()
                                edgeLabelPts.forEach { lp ->
                                    val lx = ptX(lp.x)
                                    val ly = ptY(lp.y) - th / 2 - lGap
                                    val tw = labelPaint.measureText(lp.text)
                                    drawRoundRect(
                                        color = labelBg,
                                        topLeft = Offset(
                                            lx - tw / 2 - hPad,
                                            ly + fm.ascent - vPad),
                                        size = Size(tw + hPad * 2, th + vPad * 2),
                                        cornerRadius = CornerRadius(4.dp.toPx())
                                    )
                                    drawContext.canvas.nativeCanvas.drawText(
                                        lp.text, lx,
                                        ly - (fm.ascent + fm.descent) / 2f,
                                        labelPaint)
                                }
                            }
                            // 家庭分组标签（“我的父母”等）：实测矩形内居中绘制
                            model.groupLabels.forEach { l ->
                                Text(
                                    l.text,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .offset(
                                            ((l.x - bounds.left) * fontBoost).dp,
                                            ((l.y - bounds.top) * fontBoost).dp)
                                        .width((l.w * fontBoost).dp)
                                )
                            }
                            // “同辈 · 接上行”折行标记：贴该行最左 block 左缘
                            model.laneMarks.forEach { m ->
                                Surface(
                                    color = MaterialTheme.colorScheme
                                        .surfaceContainerHigh,
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.offset(
                                        ((m.x - bounds.left) * fontBoost).dp,
                                        ((m.y - bounds.top) * fontBoost).dp)
                                ) {
                                    Text(
                                        m.text,
                                        style = MaterialTheme.typography
                                            .labelSmall,
                                        color = MaterialTheme.colorScheme
                                            .onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            // 人物卡片：按实测矩形渲染（字号放大时等比放大）
                            model.nodes.forEach { n ->
                                val ov = byId[n.personId] ?: return@forEach
                                GraphNodeItem(
                                    overview = ov,
                                    isSelf = n.personId == selfId,
                                    focused = n.personId == focusedId,
                                    picked = n.personId == overviewPick,
                                    w = n.w, h = n.h,
                                    fontBoost = fontBoost,
                                    onClick = {
                                        if (overview) {
                                            overview = false
                                            overviewPick = n.personId
                                            // 总览点人返回阅读：恢复 1.0
                                            focusBlockOf(n.personId,
                                                restoreReading = true)
                                        } else {
                                            onOpenPerson(n.personId)
                                        }
                                    },
                                    modifier = Modifier.offset(
                                        ((n.x - bounds.left) * fontBoost).dp,
                                        ((n.y - bounds.top) * fontBoost).dp
                                    )
                                )
                            }
                            // 展开芯片：路由级筛选后的完整实测矩形（≥48dp 触控高；
                            // 放不下或与连线冲突的芯片不上画布，入口在“展开家人”面板）
                            routing.chips.forEach { c ->
                                Surface(
                                    onClick = {
                                        onExpand(c.expands, null,
                                            c.reveals.firstOrNull())
                                    },
                                    color = MaterialTheme.colorScheme
                                        .surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme
                                        .primary,
                                    shape = RoundedCornerShape(14.dp * fontBoost),
                                    modifier = Modifier
                                        .offset(
                                            ((c.x - bounds.left) * fontBoost).dp,
                                            ((c.y - bounds.top) * fontBoost).dp)
                                        .size(
                                            (c.w * fontBoost).dp,
                                            (c.h * fontBoost).dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            chipLabel(c),
                                            style = MaterialTheme.typography
                                                .labelMedium,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // 总览提示条：不占卡片布局空间，位于画布上缘
                    if (overview) {
                        Surface(
                            color = MaterialTheme.colorScheme
                                .surfaceContainerHigh,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                "总览 · 点一个人放大查看",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                // 横屏：竖屏底栏的几个操作浮到画布下缘（不占布局高度）
                if (landscape) {
                    Row(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BottomAction(label = "回到我", primary = true) {
                            overview = false
                            overviewPick = null
                            homeReq++
                        }
                        if (familyMode) {
                            BottomAction(label = "−", primary = false) {
                                zoomBy(0.8f)
                            }
                            BottomAction(label = "＋", primary = false) {
                                zoomBy(1.25f)
                            }
                        } else BottomAction(
                            label = "展开家人", primary = false
                        ) { expandSheet = true }
                        BottomAction(
                            label = if (familyMode) "本支总览" else "查看总览",
                            primary = false
                        ) {
                            if (overview) {
                                overview = false
                                overviewPick = null
                                homeReq++
                            } else {
                                overview = true
                                overviewPick = null
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------- 底部面板：展开家人 ----------
    if (expandSheet) {
        ModalBottomSheet(onDismissRequest = { expandSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "展开家人",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        horizontal = 20.dp, vertical = 4.dp)
                )
                if (expandGroups.isEmpty()) {
                    Text(
                        "没有可展开的旁支",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = 20.dp, vertical = 16.dp)
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp)
                        .fillMaxWidth()
                ) {
                    items(expandGroups) { g ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    expandSheet = false
                                    if (g.expanded) onFocusFamily(g.focusId)
                                    else onExpand(g.expands, g.label, g.focusId)
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                g.label,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (g.expanded) "已展开 · 定位"
                                else "新增 " + g.addCount + " 位 ›",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (g.expanded)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (hasExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                expandSheet = false
                                onCollapse()
                                overview = false
                                overviewPick = null
                                homeReq++
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "收起全部旁支",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "只收起显示，不删资料",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (familySheet && familyMode && branch != GraphBranch.ALL) {
        ModalBottomSheet(onDismissRequest = { familySheet = false }) {
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                item {
                    Text("定位家支", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
                }
                items(familyRoots, key = { it }) { id ->
                    val person = byId[id]
                    SheetRow(title = (person?.person?.name ?: "家人") + " · " +
                        (person?.displayTitle ?: "")) {
                        familySheet = false
                        overview = false
                        overviewPick = null
                        onFocusFamily(id)
                    }
                }
            }
        }
    }

    // ---------- 底部面板：更多操作 ----------
    if (menuSheet) {
        ModalBottomSheet(onDismissRequest = { menuSheet = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "关系图选项",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(
                        horizontal = 20.dp, vertical = 4.dp)
                )
                // 查看范围：画布上方不再放筛选行，改在更多面板内呈现当前选择
                if (!familyMode && availableBranches.size > 1) {
                    Text(
                        "查看范围",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = 20.dp, vertical = 6.dp)
                    )
                    availableBranches.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    onSelectBranch(b)
                                    menuSheet = false
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                b.label,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                                color = if (branch == b)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (branch == b) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (onToggleLandscape != null) {
                    SheetRow(
                        title = if (landscape) "恢复竖屏" else "横屏查看",
                        action = {
                            menuSheet = false
                            onToggleLandscape()
                        }
                    )
                }
                SheetRow(
                    title = "关系线说明",
                    action = {
                        menuSheet = false
                        legendOpen = true
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "在线上显示称谓词",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showEdgeLabels,
                        onCheckedChange = onToggleEdgeLabels
                    )
                }
                if (model != null && model.collapsedCount > 0) {
                    SheetRow(
                        title = "展开全部家人（" +
                            model.collapsedCount + " 位未展开）",
                        action = {
                            menuSheet = false
                            onExpandAll()
                            // 全展开后进入总览：显示整棵树的已展开范围
                            overview = true
                            overviewPick = null
                        }
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant)
                SheetRow(title = "分享图片") {
                    menuSheet = false
                    exportKind = "share"
                }
                if (GraphExport.canSaveToAlbum()) {
                    SheetRow(title = "保存到相册") {
                        menuSheet = false
                        exportKind = "album"
                    }
                }
                SheetRow(title = if (familyMode) "选择家人分享" else "分享这些家人") {
                    menuSheet = false
                    onShareScope(branch)
                }
                SheetRow(title = "备份全部数据") {
                    menuSheet = false
                    onBackup()
                }
            }
        }
    }

    // ---------- 关系线说明 ----------
    if (legendOpen) {
        AlertDialog(
            onDismissRequest = { legendOpen = false },
            title = { Text("关系线说明") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("实线 — 已记录的夫妻、父母子女",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("虚线 — 直接记录的兄弟姐妹、堂表亲",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("线上的小拱桥表示交叉经过，两条线不相连。",
                        style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "能由父母连线表达的兄弟姐妹不再重复画线；" +
                            "共同父母不代表必然存在夫妻关系。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { legendOpen = false }) { Text("知道了") }
            }
        )
    }

    if (conflictsOpen) {
        AlertDialog(
            onDismissRequest = { conflictsOpen = false },
            title = { Text("核对亲子关系") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("以下记录与当前家谱辈分冲突，暂不画线。资料没有改动，请打开人物详情核对。")
                    parentConflicts.forEach { e ->
                        val parent = if (e.type == RelationshipType.PARENT) e.fromId else e.toId
                        val child = if (e.type == RelationshipType.PARENT) e.toId else e.fromId
                        val parentName = byId[parent]?.person?.name.orEmpty()
                        val childName = byId[child]?.person?.name.orEmpty()
                        TextButton(onClick = {
                            conflictsOpen = false
                            onOpenPerson(parent)
                        }) {
                            Text(parentName + "的子女：" + childName + "\n查看" + parentName + "的关系")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { conflictsOpen = false }) { Text("关闭") }
            }
        )
    }

    // 导出尺寸选择
    exportKind?.let { kind ->
        AlertDialog(
            onDismissRequest = { exportKind = null },
            title = { Text("导出图片") },
            text = {
                Column {
                    Surface(
                        onClick = {
                            exportKind = null
                            onExport(kind, GraphImageRenderer.Quality.STANDARD)
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("标准（适合分享）",
                            modifier = Modifier.padding(vertical = 14.dp,
                                horizontal = 8.dp))
                    }
                    Surface(
                        onClick = {
                            exportKind = null
                            onExport(kind, GraphImageRenderer.Quality.HD)
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("高清（打印用）",
                            modifier = Modifier.padding(vertical = 14.dp,
                                horizontal = 8.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { exportKind = null }) { Text("取消") }
            }
        )
    }
    if (exporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("正在生成图片…") },
            text = { CircularProgressIndicator() },
            confirmButton = {}
        )
    }
    // 查找：覆盖全部已关联人物（含当前未展开者），命中后自动展开路径定位；
    // 未关联人物单独提示，不伪造连接
    if (searchOpen) {
        var q by remember { mutableStateOf("") }
        var unrelated by remember { mutableStateOf<String?>(null) }
        val fr = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text("查找家人") },
            text = {
                Column {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it },
                        singleLine = true,
                        placeholder = { Text("姓名 / 称谓 / 拼音") },
                        modifier = Modifier.fillMaxWidth().focusRequester(fr)
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp)
                            .padding(top = 8.dp)
                    ) {
                        items(
                            io.github.xiaohaoz1.jiarenxia.domain.PersonSearch
                                .filter(byId.values.toList(), q)
                        ) { ov ->
                            val unrelatedP =
                                ov.result.kind == KinshipKind.UNRELATED
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        if (unrelatedP) {
                                            unrelated = ov.person.name
                                        } else {
                                            searchOpen = false
                                            onLocate(ov.person.id)
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    ov.person.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (unrelatedP)
                                        MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (unrelatedP) {
                                    TitleChip("未关联", ChipTone.MUTED)
                                } else {
                                    TitleChip(ov.displayTitle, ChipTone.CORE)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { searchOpen = false }) { Text("关闭") }
            }
        )
        unrelated?.let { name ->
            AlertDialog(
                onDismissRequest = { unrelated = null },
                title = { Text(name) },
                text = { Text("这位家人还没有和“我”建立关联，关系图中不会显示；可先在家庭页补充关系。") },
                confirmButton = {
                    TextButton(onClick = { unrelated = null }) {
                        Text("知道了")
                    }
                }
            )
        }
        LaunchedEffect(Unit) { fr.requestFocus() }
    }
}

/** 分支筛选 chip：竖屏三列分行（weight 等宽），横屏单行横滑（自然宽度）。 */
@Composable
private fun BranchChip(
    item: GraphBranch,
    branch: GraphBranch,
    available: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = branch == item,
        enabled = available,
        onClick = onSelect,
        label = {
            Text(GraphFamilyScope.labelOf(item),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center)
        },
        modifier = modifier
    )
}

/** 底部工具胶囊按钮：独立占位、触控高 48dp。 */
@Composable
private fun BottomAction(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = if (primary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 底部面板的一行操作。 */
@Composable
private fun SheetRow(title: String, action: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = action)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * 单个节点卡片：头像 28dp + 姓名 16sp（可换行）+ 称谓 13sp（完整、两行内）。
 * 卡片宽高由布局层按真实文字测量传入（w/h 为 fontScale=1 基准 dp），
 * 大字号经 fontBoost 同比放大几何而不是压缩文字。
 * “我”用主色浅底 + 描边 + “我”标记；其余统一白底。
 * 已故半透明。聚焦/总览选中用描边强调。
 */
@Composable
private fun GraphNodeItem(
    overview: PersonOverview,
    isSelf: Boolean,
    w: Float,
    h: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    picked: Boolean = false,
    /** 大字号：卡片随共享几何同比放大。 */
    fontBoost: Float = 1f
) {
    val p = overview.person
    val deceased = p.livingStatus == LivingStatus.DECEASED
    Card(
        // 合并语义：节点读成一条“姓名 + 称谓”
        modifier = modifier
            .size((w * fontBoost).dp, (h * fontBoost).dp)
            .semantics(mergeDescendants = true) {}
            .clickable(onClick = onClick)
            .alpha(if (deceased) 0.6f else 1f),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelf)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = when {
            focused -> BorderStroke(3.dp, MaterialTheme.colorScheme.tertiary)
            picked -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
            isSelf -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else -> null
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(10.dp * fontBoost))
                PersonAvatar(
                    p.avatarPath, p.name, 28.dp * fontBoost,
                    ring = null, deceased = deceased
                )
                Spacer(Modifier.height(6.dp * fontBoost))
                Text(
                    p.name,
                    fontSize = GraphGeom.NAME_SIZE.sp,
                    fontWeight = FontWeight.Medium,
                    // 显式行高与 GraphTextMeasure 测量一致：
                    // 每行恰好占 NAME_LH，中心对齐、不裁剪首末行
                    lineHeight = GraphGeom.NAME_LH.sp,
                    style = LocalTextStyle.current.copy(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.None)),
                    // 不截断：长姓名按真实折行完整显示，
                    // 卡片高度由布局层按同一份测量给足
                    softWrap = true,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp * fontBoost)
                )
                if (overview.displayTitle.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp * fontBoost))
                    Text(
                        overview.displayTitle,
                        fontSize = GraphGeom.TITLE_SIZE.sp,
                        lineHeight = GraphGeom.TITLE_LH.sp,
                        style = LocalTextStyle.current.copy(
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = true,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(
                            horizontal = 8.dp * fontBoost)
                    )
                }
            }
            if (isSelf) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(4.dp * fontBoost)
                ) {
                    Text(
                        "我",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(
                            horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
