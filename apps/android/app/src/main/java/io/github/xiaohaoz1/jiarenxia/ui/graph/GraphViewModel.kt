package io.github.xiaohaoz1.jiarenxia.ui.graph

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class GraphUiState(
    val loading: Boolean = true,
    val model: GraphModel? = null,
    val byId: Map<String, PersonOverview> = emptyMap(),
    val selfId: String? = null,
    /** 当前分支筛选。 */
    val branch: GraphBranch = GraphBranch.ALL,
    /** 有成员的分支（枚举序，ALL 恒在首位）。 */
    val availableBranches: List<GraphBranch> = listOf(GraphBranch.ALL),
    /** 只控制成员范围，不改变“我”的身份或称谓观察者。 */
    val scopeAnchorId: String? = null,
    /** 其他分支可定位的家庭；我的家庭为空。 */
    val familyRoots: List<String> = emptyList(),
    /** 待居中高亮的节点（查找/深链进入）；分支切换时清除。 */
    val focusedId: String? = null,
    /** 是否展开过旁支（决定“收起全部旁支”是否出现）。 */
    val hasExpanded: Boolean = false,
    /** 是否在关系线上标注关系词（本机偏好，默认关闭）。 */
    val showEdgeLabels: Boolean = false,
    /** 当前范围内关联总人数（顶部“已展开 N/M”分母）。 */
    val connectedTotal: Int = 0,
    /** “展开家人”底部面板分组。 */
    val expandGroups: List<GraphVisibility.Group> = emptyList(),
    /** 最近一次展开动作的分组标签（范围行显示“查看范围”）。 */
    val scopeLabel: String? = null,
    /** 相机请求：展开后移动到新增家庭附近（seq 递增，界面消费一次）。 */
    val cameraSeq: Long = 0,
    /** 相机目标人物 id（展开锚点 / 面板定位目标）。 */
    val cameraTarget: String? = null,
    /** 本次相机请求是否恢复 1.0 阅读比例（收起旁支=是；展开/查找=否）。 */
    val cameraRestore: Boolean = false
)

/** 范围行 + 相机请求的合成（combine 内部使用）。 */
private data class CameraCmd(
    val scopeLabel: String?,
    val seq: Long,
    val target: String?,
    val restore: Boolean
)

/** 导出图片的状态：null 空闲 / Generating / 结果。 */
sealed interface GraphExportState {
    data object Generating : GraphExportState
    /** 分享：拿到 content Uri；保存：成功/失败。 */
    data class ShareReady(val uri: android.net.Uri) : GraphExportState
    data object AlbumDone : GraphExportState
    data object AlbumFailed : GraphExportState
}

/** 关系图 ViewModel：称谓推导 + 布局都在 Dispatchers.Default 上计算。 */
@HiltViewModel
class GraphViewModel @Inject constructor(
    observeOverview: ObserveFamilyOverview,
    private val engine: KinshipEngine,
    backup: io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager,
    private val settings: io.github.xiaohaoz1.jiarenxia.data.settings
        .SettingsRepository,
    private val repository: io.github.xiaohaoz1.jiarenxia.data.FamilyRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val context: android.content.Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** 一次性提示（备份结果等），界面消费后调 onMessageShown 清空。 */
    val message = MutableStateFlow<String?>(null)
    fun onMessageShown() { message.value = null }

    /**
     * 备份导出流程（与我的页共用 ui/backup/BackupExportFlow）。
     * 备份的始终是全部数据，与当前分支筛选无关。
     */
    val backupExport = io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportFlow(
        scope = viewModelScope,
        backup = backup,
        settings = settings,
        onMessage = { message.value = it }
    )

    /** 图片导出（V0.9）：Generating → ShareReady/AlbumDone。 */
    val exportState = MutableStateFlow<GraphExportState?>(null)

    /** 导出当前（已筛选）模型为 PNG。kind=share/save。 */
    fun exportImage(kind: String, quality: GraphImageRenderer.Quality) {
        val st = ui.value
        val model = st.model ?: return
        viewModelScope.launch(Dispatchers.Default) {
            exportState.value = GraphExportState.Generating
            try {
                val today = java.time.LocalDate.now()
                val infos = st.byId.mapValues { (_, o) ->
                    GraphImageRenderer.NodeInfo(
                        name = o.person.name,
                        title = if (o.person.isSelf) "我"
                            else o.displayTitle,
                        deceased = o.person.livingStatus !=
                            io.github.xiaohaoz1.jiarenxia.core.model
                                .LivingStatus.ALIVE
                    )
                }
                // 导出当前可见图：附线型图例与收起人数说明
                val footNote = GraphImageRenderer.LINE_LEGEND +
                    if (model.collapsedCount > 0)
                        "    另有 " + model.collapsedCount + " 位家人未展开"
                    else ""
                val bmp = GraphImageRenderer.render(
                    model, infos, st.selfId, quality,
                    GraphImageRenderer.titleLine(
                        st.byId[st.selfId]?.person?.name ?: "我", today),
                    edgeLabels = st.showEdgeLabels,
                    footNote = footNote,
                    textScale = context.resources.configuration.fontScale
                        .coerceAtLeast(1f))
                val name = GraphExport.fileName(today)
                if (kind == "album") {
                    val uri = withContext(
                        Dispatchers.IO
                    ) { GraphExport.saveToAlbum(context, bmp, name) }
                    exportState.value =
                        if (uri != null) GraphExportState.AlbumDone
                        else GraphExportState.AlbumFailed
                } else {
                    val uri = withContext(
                        Dispatchers.IO
                    ) { GraphExport.writeCache(context, bmp, name) }
                    exportState.value = GraphExportState.ShareReady(uri)
                }
            } catch (e: Exception) {
                exportState.value = GraphExportState.AlbumFailed
            }
        }
    }

    fun clearExportState() {
        exportState.value = null
    }

    private val _branch = MutableStateFlow(GraphBranch.ALL)
    // 路由参数 graph?focus={focus}（人物详情“在关系图中查看”）
    private val _focusedId = MutableStateFlow(
        savedStateHandle.get<String>("focus")?.takeIf { it.isNotBlank() }
    )
    /** 图谱可见层状态：已点展开的（锚点+方向）键；与分享选人完全隔离。 */
    private val _expanded =
        MutableStateFlow<Set<GraphVisibility.Expand>>(emptySet())
    private val _expandAll = MutableStateFlow(false)
    /** 最近一次展开的分组标签（范围行）与相机目标。 */
    private val _scopeLabel = MutableStateFlow<String?>(null)
    private val _cameraSeq = MutableStateFlow(0L)
    private val _cameraTarget = MutableStateFlow<String?>(null)
    /** 相机请求是否恢复 1.0 阅读比例；见 [GraphUiState.cameraRestore]。 */
    private val _cameraRestore = MutableStateFlow(false)
    /** 最近一次构建输入（查找定位展开路径用）。 */
    @Volatile
    private var lastInputs: Triple<FamilyOverview, Map<String, Int>, Map<String, Int>>? =
        null

    val ui: StateFlow<GraphUiState> =
        combine(
            combine(
                combine(observeOverview(), _branch, _focusedId) { o, b, f ->
                    Triple(o, b, f)
                },
                combine(
                    _scopeLabel, _cameraSeq, _cameraTarget, _cameraRestore
                ) { sl, cs, ct, cr ->
                    CameraCmd(sl, cs, ct, cr)
                }
            ) { a, b -> a to b },
            combine(
                combine(_expanded, _expandAll, settings.edgeLabelsEnabled) { e, a, l ->
                    Triple(e, a, l)
                },
                repository.observeLocalOnlyIds()
            ) { t, lo -> t to lo }
        ) { (first, second), (triple, localOnly) ->
            val (overview, branch, focusedId) = first
            val (scopeLabel, cameraSeq, cameraTarget, cameraRestore) = second
            val (expanded, expandAll, edgeLabels) = triple
            buildState(overview, branch, focusedId, expanded, expandAll,
                localOnly).copy(
                showEdgeLabels = edgeLabels,
                scopeLabel = scopeLabel,
                cameraSeq = cameraSeq,
                cameraTarget = cameraTarget,
                cameraRestore = cameraRestore
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GraphUiState())

    /** “关系词”开关：本机偏好，屏幕与导出图共用。 */
    fun setEdgeLabelsEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setEdgeLabelsEnabled(enabled) }
    }

    private fun buildState(
        overview: FamilyOverview?, branch: GraphBranch, focusedId: String?,
        expanded: Set<GraphVisibility.Expand>, expandAll: Boolean,
        localOnlyIds: Set<String> = emptySet()
    ): GraphUiState {
        if (overview == null) return GraphUiState(loading = false)
        // “仅本机保留”人物不进入默认关系图（协作 §5.7）：
        // 人物与全部相关边一并隐藏；普通部分分享仍可明确选这些人
        val source = if (localOnlyIds.isEmpty()) overview else overview.copy(
            graph = overview.graph.copy(
                persons = overview.graph.persons
                    .filter { it.id !in localOnlyIds },
                relationships = overview.graph.relationships.filter {
                    it.sourcePersonId !in localOnlyIds &&
                        it.targetPersonId !in localOnlyIds },
                birthdays = overview.graph.birthdays - localOnlyIds,
                birthdayValues = overview.graph.birthdayValues - localOnlyIds
            ),
            byId = overview.byId - localOnlyIds,
            core = overview.core.filter { it.person.id !in localOnlyIds },
            others = overview.others.filter { it.person.id !in localOnlyIds }
        )
        val ov = GraphFamilyTitles.resolve(source)
        val allGens = engine.generations(ov.graph, ov.self.person.id)
        // 范围锚点与称谓观察者分离：overview 始终由本人推导，不重算“父亲视角”的称谓。
        val activeBranch = if (focusedId != null && focusedId !in
            GraphFamilyScope.visibleIds(ov, branch)) {
            GraphFamilyScope.available(ov).firstOrNull {
                focusedId in GraphFamilyScope.visibleIds(ov, it)
            } ?: branch
        } else branch
        val visible = GraphFamilyScope.visibleIds(ov, activeBranch)
        val gens = allGens.filterKeys { it in visible }
        lastInputs = Triple(ov, allGens, gens)
        // unconnectedCount 始终指“与‘我’无路径的人”，按未筛选的世代表计算
        val model = GraphFamilyLayout.build(
            ov, gens,
            // 真实字体测量：卡片宽高按系统字体管线实测，
            // 屏幕绘制与导出共用同一份几何
            sizer = GraphTextMeasure.sizer
        ).copy(
            unconnectedCount = ov.graph.persons.count { it.id !in allGens.keys }
        )
        // 焦点只在可见集内有效（查找路径展开由 focusOn 先处理）
        val focused = focusedId?.takeIf { it in gens }
        return GraphUiState(
            loading = false, model = model, byId = ov.byId,
            selfId = ov.self.person.id, branch = activeBranch,
            availableBranches = GraphFamilyScope.available(ov),
            scopeAnchorId = GraphFamilyScope.anchorOf(ov, activeBranch),
            familyRoots = GraphFamilyScope.familyRoots(ov, activeBranch)
                .filter { it in gens },
            focusedId = focused,
            hasExpanded = false,
            connectedTotal = gens.size,
            expandGroups = emptyList()
        )
    }

    /** 切换分支；清除焦点、展开状态与范围标签（新范围从默认核心开始）。 */
    fun selectBranch(branch: GraphBranch) {
        val ov = lastInputs?.first
        if (ov != null && GraphFamilyScope.anchorOf(ov, branch) == null) return
        _focusedId.value = null
        _expanded.value = emptySet()
        _expandAll.value = false
        _scopeLabel.value = null
        _branch.value = branch
    }

    /**
     * 展开一组家庭块（画布芯片或“展开家人”面板行）：
     * 只影响本图可见层；展开后相机移到 [focusId] 附近。
     */
    fun expand(
        expands: Set<GraphVisibility.Expand>,
        scopeLabel: String? = null,
        focusId: String? = null
    ) {
        _expanded.value = _expanded.value + expands
        if (scopeLabel != null) _scopeLabel.value = scopeLabel
        if (focusId != null) {
            // 先写目标再递增序号：combine 不会发出“新序号+旧目标”的中间态
            // 展开定位保留当前缩放（不恢复 1.0），避免甩出视野
            _cameraRestore.value = false
            _cameraTarget.value = focusId
            _cameraSeq.value += 1
        }
    }

    /** 面板“已展开·定位”：不改动展开集，只移动相机（保留缩放）。 */
    fun focusFamily(personId: String) {
        if (ui.value.branch == GraphBranch.ALL || personId !in ui.value.familyRoots) return
        _cameraRestore.value = true
        _cameraTarget.value = personId
        _cameraSeq.value += 1
    }

    /** 展开全部旁支（更多菜单）。 */
    fun expandAllNow() {
        _expandAll.value = true
        _scopeLabel.value = "全部家人"
    }

    /**
     * 收起全部旁支回到默认核心；相机回到本人家庭并恢复 1.0
     * 阅读比例（回到“默认阅读”语义），当前聚焦人物保持可见。
     */
    fun collapseBranches() {
        _expanded.value = emptySet()
        _expandAll.value = false
        _scopeLabel.value = null
        lastInputs?.first?.self?.person?.id?.let { sid ->
            _cameraRestore.value = true
            _cameraTarget.value = sid
            _cameraSeq.value += 1
        }
    }

    /**
     * 定位到某人：不在当前范围先切回 ALL，再沿连接路径展开并居中。
     * 已有展开状态保留（只追加必要路径）。
     */
    fun focusOn(personId: String) {
        val (ov, allGens, gens0) = lastInputs
            ?: run { _focusedId.value = personId; return }
        var gens = gens0
        if (personId !in gens.keys) {
            val target = GraphFamilyScope.available(ov).firstOrNull {
                personId in GraphFamilyScope.visibleIds(ov, it)
            }
            if (target == null) {
                message.value = "这位家人不在当前家庭图范围内，可在家庭页查看资料"
                return
            }
            _branch.value = target
            gens = allGens.filterKeys { it in GraphFamilyScope.visibleIds(ov, target) }
        }
        _focusedId.value = personId
        // 先写目标再递增序号：避免“新序号+旧目标”中间态被相机消费
        // 查找定位属普通聚焦，保留当前缩放
        _cameraRestore.value = false
        _cameraTarget.value = personId
        _cameraSeq.value += 1
    }
}
