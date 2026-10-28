package io.github.xiaohaoz1.jiarenxia.ui.share

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import android.widget.Toast
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan
import io.github.xiaohaoz1.jiarenxia.data.db.PersonAliasEntity
import io.github.xiaohaoz1.jiarenxia.data.db.toBirthdayValue
import io.github.xiaohaoz1.jiarenxia.data.db.toModel
import io.github.xiaohaoz1.jiarenxia.domain.ShareImport
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
import io.github.xiaohaoz1.jiarenxia.ui.common.PersonPickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 共享导入 UI 状态。 */
data class ShareImportUiState(
    /** 待输入密码（来源是 uri）；或已解密待确认。 */
    val stage: Stage = Stage.PASSWORD,
    /** 文件原始 plan（页头“来自 X 的家人共享”与候选列表用）。 */
    val plan: RestorePlan? = null,
    /** “资料里没有我”增补后的导入用 plan（null = 直接用 plan）。 */
    val importPlan: RestorePlan? = null,
    /** 选人列表（PersonPickItem：姓名（分享者的XX）/（分享者本人））。 */
    val candidates: List<io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem> =
        emptyList(),
    val selectedId: String? = null,
    /** 本机已有数据 → 预览确认后再确认替换。 */
    val hasExisting: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
    /** 完成后的提示语（以谁的视角）。 */
    val doneName: String = "",
    /** “同父同母”补全询问：兄弟姐妹 id、父母 id、兄弟姐妹姓名。 */
    val adoptSibling: String? = null,
    val adoptParentIds: List<String> = emptyList(),
    val adoptSiblingName: String = "",
    /** “资料里没有我”且对方有配偶 → 问“X 也是你的父亲/母亲吗”。 */
    val coParentId: String? = null,
    val coParentName: String = "",
    /** 父亲/母亲（按配偶性别）。 */
    val coParentWord: String = "",
    /** “添加我自己”对话框开关。 */
    val newSelfOpen: Boolean = false,
    /** “以 X 的身份导入？”预览。 */
    val preview: ShareImport.PerspectivePreview? = null,
    val previewName: String = "",
    /** 已有数据 → 预览确认后再确认替换。 */
    val replaceAsk: Boolean = false,
    /** “和我结为夫妻”备选：合并计划可用时非空，预览中显示。 */
    val mergeEligible: Boolean = false,
    val mergePreviewNew: Int = 0,
    val mergePreviewMerged: List<String> = emptyList(),
    val mergeAsk: Boolean = false,
    /** 写入/计算进行中（禁用按钮防连点）。 */
    val busy: Boolean = false,
    /** 本机“我”的 id（合并入口前置：已有数据且有“我”）。 */
    val localSelfId: String? = null,
    /** “认出一位两边都有的家人”对话框开关（无交集的“资料里没有我”）。 */
    val anchorPickOpen: Boolean = false,
    /** 本机人物条目（姓名+称谓标签；共同家人指认与“是我家的…”共用）。 */
    val mergeLocalItems: List<io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem> =
        emptyList(),
    /** 本机人物 id → 性别（选择器按同性别过滤）。 */
    val mergeLocalGenders: Map<String, Gender> = emptyMap(),
    // ---- Stage.MERGE（合并预览，FAMILY_AFFAIRS 16.7）----
    /** 最近一次重算出的合并计划。 */
    val mergePlan: io.github.xiaohaoz1.jiarenxia.domain.merge
        .FamilyMerge.Plan? = null,
    /** 新人物 remoteId → 称谓（本机“我”视角；算不出为“暂无称谓”）。 */
    val mergeNewTitles: Map<String, String> = emptyMap(),
    /** 手动指认 remoteId → 本机人物名（“已认作 X（撤销）”）。 */
    val mergeManualNames: Map<String, String> = emptyMap(),
    /** 合并底部是否提供“整体替换…”（只有从选人进入时才显示）。 */
    val mergeCanReplace: Boolean = false,
    /** 本次合并将新增保存的对应关系条数（纯认人时也有写入意义）。 */
    val mergeNewAliases: Int = 0
) {
    enum class Stage { PASSWORD, CONFIRM, MERGE }
}

/** 家人共享导入（FAMILY_AFFAIRS 9.4 / 16.3）：密码 → 摘要 → 选“我是谁” → 写入。 */
@HiltViewModel
class ShareImportViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val context: android.content.Context,
    private val backup: BackupManager,
    private val db: AppDatabase,
    private val appEvents: AppEvents,
    private val engine: KinshipEngine,
    private val lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val uriString: String? = savedStateHandle["uri"]
    private val uri: Uri? = uriString?.let(Uri::parse)

    private val _ui = MutableStateFlow(ShareImportUiState())
    val ui = _ui

    init {
        viewModelScope.launch {
            // 两种来源：外部 intent（uri）或恢复流程传来的已解密 plan
            val plan = appEvents.pendingSharePlan
            if (plan != null) {
                appEvents.pendingSharePlan = null
                present(plan)
            } else if (uri == null) {
                _ui.update { it.copy(error = "没有可导入的文件") }
            }
            val persons = db.personDao().getAll()
            _ui.update {
                it.copy(hasExisting = persons.isNotEmpty(),
                    localSelfId = persons.firstOrNull { p -> p.isSelf }?.id)
            }
        }
    }

    /** 导入用 plan：增补“新的我”后的版本，未增补则同文件 plan。 */
    private fun importPlan(): RestorePlan? = _ui.value.importPlan ?: _ui.value.plan

    private suspend fun present(plan: RestorePlan) {
        val localSelf = db.personDao().getSelf()
        // 文件里有与本机“我”同 id 且同性别的人 → 预选（FAMILY_AFFAIRS 16.4）
        val preselect = localSelf?.let { ls ->
            plan.persons.firstOrNull { it.id == ls.id && it.gender == ls.gender }?.id
        }
        _ui.update {
            it.copy(
                stage = ShareImportUiState.Stage.CONFIRM, plan = plan,
                candidates = ShareImport.candidates(plan, engine, lunar),
                selectedId = preselect
            )
        }
        refreshMerge()
    }

    /** 输入密码解密。旧协作包也按普通分享处理（collaboration 元数据解析时忽略）。 */
    fun open(password: CharArray) {
        val u = uri ?: return
        viewModelScope.launch {
            val raw = runCatching {
                uriContextOpen(u)?.use { it.readBytes() }
            }.getOrNull()
            if (raw == null) {
                _ui.update { it.copy(error = "打不开这个文件") }
                return@launch
            }
            backup.inspect(raw.inputStream(), password)
                .onSuccess { plan ->
                    when {
                        plan.purpose != "share" ->
                            _ui.update { it.copy(error = "这是备份文件，请到“从备份恢复”导入") }
                        else -> present(plan)
                    }
                }
                .onFailure {
                    _ui.update { it.copy(error = "密码不对或文件已损坏") }
                }
        }
    }

    private fun uriContextOpen(u: Uri): java.io.InputStream? =
        context.contentResolver.openInputStream(u)

    fun select(id: String) {
        // 改选列表里的人 → 清掉“资料里没有我”增补的临时人物，避免两路并存
        _ui.update { it.copy(selectedId = id, importPlan = null) }
        newSelfAnchor = null
        newSelfRole = null
        resetPipeline()
        viewModelScope.launch { refreshMerge() }
    }

    /**
     * “和我结为夫妻”可行性（以本机“我”计算，0.9.4 缺陷修复：
     * 旧实现误传文件里所选人物的 id，入口实际从不出现）。
     * 本机没有“我”时不提供此入口。
     */
    private suspend fun refreshMerge() {
        val plan = _ui.value.plan ?: return
        val peer = plan.sharedBy ?: return
        val localSelf = db.personDao().getSelf()
        val p = if (localSelf == null) null else ShareImport.spouseMergePlan(
            backup.localMergeSnapshot(), backup.mergeSnapshotOf(plan),
            peerSelfId = peer, localSelfId = localSelf.id,
            selectedId = _ui.value.selectedId
        )
        spouseMergePlan = p
        _ui.update {
            it.copy(
                mergeEligible = p != null,
                mergePreviewNew = p?.newPersons?.size ?: 0,
                mergePreviewMerged = p?.mergedNames ?: emptyList()
            )
        }
    }

    /** 预计算的配偶合并计划（null = 不满足前置）。 */
    private var spouseMergePlan:
        io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge.MergePlan? = null

    /** 点“和我结为夫妻”：先弹合并预览。 */
    fun askMerge() = _ui.update { it.copy(mergeAsk = true) }

    fun dismissMerge() = _ui.update { it.copy(mergeAsk = false) }

    /** 预览确认 → 合并写入（不动本机的“我”）。 */
    fun confirmMerge() {
        val p = spouseMergePlan ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(mergeAsk = false, busy = true) }
        viewModelScope.launch {
            try {
                backup.importMerge(p)
                val name = _ui.value.plan?.persons
                    ?.firstOrNull { it.id == _ui.value.selectedId }?.name ?: ""
                _ui.update { it.copy(done = true, doneName = name, busy = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = "导入失败，请重试") }
            }
        }
    }

    // ---------- “资料里没有我”（添加我自己） ----------

    private var newSelfAnchor: String? = null
    private var newSelfRole: RelationRole? = null
    /** 补全流程的已答状态（选人变化时重置）。 */
    private var adoptHandled = false
    private var coParentHandled = false
    private var adoptIds: List<String> = emptyList()

    /**
     * “资料里没有我”（已有数据 + 本机有“我”时走合并，FAMILY_AFFAIRS 16.3）：
     * - 文件与本机已有交集（同 id 同性别或命中 person_alias）→ 直接进
     *   合并阶段（anchors 为空，靠自动认人）；
     * - 无交集 → “认出一位两边都有的家人”对话框（指认或桥接）；
     * - 不能合并（无“我”/空库）→ 原来的“添加我自己”表单。
     */
    fun openNewSelf() {
        if (!canMerge()) {
            _ui.update { it.copy(newSelfOpen = true) }
            return
        }
        viewModelScope.launch {
            val plan = _ui.value.plan ?: return@launch
            val selfId = _ui.value.localSelfId ?: return@launch
            val known = db.personAliasDao().getAll()
            val localPersons = db.personDao().getAll()
            val localIds = localPersons.mapTo(HashSet()) { it.id }
            val localById = localPersons.associateBy { it.id }
            // 交集 = 任一对方人物与本机同 id 同性别，或命中已保存的对应关系
            val overlap = plan.persons.any { rp ->
                val lp = localById[rp.id]
                (lp != null && lp.gender == rp.gender) ||
                    known.any { it.remotePersonId == rp.id &&
                        it.localPersonId in localIds }
            }
            if (overlap) {
                launchMerge(anchors = emptyMap(), selectedRemoteId = null)
            } else {
                val (items, genders) = withContext(Dispatchers.Default) {
                    localPickItems(
                        backup.localFamilySide(), selfId)
                }
                _ui.update {
                    it.copy(anchorPickOpen = true,
                        mergeLocalItems = items,
                        mergeLocalGenders = genders)
                }
            }
        }
    }
    fun dismissNewSelf() = _ui.update { it.copy(newSelfOpen = false) }

    /** “添加我自己”完成：在原 plan 之上增补新人物并选中（原 plan 保留供页头/候选列表）。 */
    fun confirmNewSelf(name: String, anchorId: String, role: RelationRole) {
        val plan = _ui.value.plan ?: return
        if (ShareImport.newSelfError(plan, anchorId, role) != null) return
        val newId = UUID.randomUUID().toString()
        newSelfAnchor = anchorId
        newSelfRole = role
        resetPipeline()
        _ui.update {
            it.copy(
                importPlan = ShareImport.withNewSelf(
                    plan, name, anchorId, role, newId,
                    System.currentTimeMillis()
                ),
                selectedId = newId,
                newSelfOpen = false
            )
        }
        viewModelScope.launch { refreshMerge() }
    }

    private fun resetPipeline() {
        adoptHandled = false
        coParentHandled = false
        adoptIds = emptyList()
    }

    // ---------- 导入流水线：补全询问 → 视角预览 →（已有数据）替换确认 → 写入 ----------

    /** 点“导入”：先看是否要补全父母（同父同母询问）。 */
    fun confirmImport() {
        val plan = importPlan() ?: return
        val id = _ui.value.selectedId ?: return
        if (_ui.value.busy) return
        if (!adoptHandled) {
            val adopt = ShareImport.adoptableParents(plan, id)
            if (adopt != null) {
                adoptHandled = true
                val sibName = plan.persons
                    .firstOrNull { it.id == adopt.first }?.name ?: ""
                _ui.update {
                    it.copy(adoptSibling = adopt.first,
                        adoptParentIds = adopt.second,
                        adoptSiblingName = sibName)
                }
                return
            }
        }
        askCoParentOrPreview()
    }

    /** 同父同母选择：是 → 沿用 adoptParentIds；否/不确定 → 不补。 */
    fun answerAdopt(sameParents: Boolean) {
        if (sameParents) adoptIds = _ui.value.adoptParentIds
        _ui.update {
            it.copy(adoptSibling = null, adoptParentIds = emptyList(),
                adoptSiblingName = "")
        }
        askCoParentOrPreview()
    }

    /** “资料里没有我”且选的是子女关系 → 对方配偶也可能是自己的父母。 */
    private fun askCoParentOrPreview() {
        val anchor = newSelfAnchor
        val role = newSelfRole
        if (!coParentHandled && anchor != null && role != null) {
            val plan = importPlan() ?: return
            val sp = ShareImport.coParentFor(plan, anchor, role)
            if (sp != null) {
                coParentHandled = true
                val spPerson = plan.persons.firstOrNull { it.id == sp }
                _ui.update {
                    it.copy(
                        coParentId = sp,
                        coParentName = spPerson?.name ?: "",
                        coParentWord =
                            if (spPerson?.gender == Gender.MALE) "父亲" else "母亲"
                    )
                }
                return
            }
        }
        showPreview()
    }

    /** “X 也是你的父亲/母亲吗”：是 → 记入补全父母；不是/不确定 → 不补。 */
    fun answerCoParent(yes: Boolean) {
        val sp = _ui.value.coParentId
        if (yes && sp != null) adoptIds = adoptIds + sp
        _ui.update { it.copy(coParentId = null) }
        showPreview()
    }

    /** “以 X 的身份导入？”预览：用加上补全父母后的 plan 计算称谓。 */
    private fun showPreview() {
        val plan = importPlan() ?: return
        val id = _ui.value.selectedId ?: return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val name = plan.persons.firstOrNull { it.id == id }?.name ?: ""
                val pv = withContext(Dispatchers.Default) {
                    val augmented =
                        ShareImport.withAdoptedParents(plan, id, adoptIds)
                    ShareImport.perspectivePreview(
                        augmented, id, engine, lunar
                    )
                }
                _ui.update { it.copy(preview = pv, previewName = name) }
            } catch (e: Exception) {
                _ui.update { it.copy(error = "无法生成预览，请重试") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    /** 预览“确认导入”：已有数据时再确认替换，否则直接写入。 */
    fun confirmPreview() {
        _ui.update { it.copy(preview = null) }
        if (_ui.value.hasExisting) _ui.update { it.copy(replaceAsk = true) }
        else doImport()
    }

    /** 预览“重新选择”：回候选列表，补全流程状态一并重置。 */
    fun cancelPreview() {
        _ui.update { it.copy(preview = null) }
        resetPipeline()
    }

    /** “替换现有数据？”：替换 → 写入；先去备份/取消 → 停下。 */
    fun answerReplace(ok: Boolean) {
        _ui.update { it.copy(replaceAsk = false) }
        if (ok) doImport()
    }

    private fun doImport() {
        val plan = importPlan() ?: return
        val id = _ui.value.selectedId ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                backup.importShare(plan, id, adoptIds)
                val name = plan.persons.first { it.id == id }.name
                _ui.update { it.copy(done = true, doneName = name, busy = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = "导入失败，请重试") }
            }
        }
    }

    // ---------- “合并到我的家庭”（Stage.MERGE，FAMILY_AFFAIRS 16.3/16.7） ----------

    /** 合并入口前置：已有数据且本机有“我”（无“我”只走整体替换）。 */
    private fun canMerge() =
        _ui.value.hasExisting && _ui.value.localSelfId != null

    private var mergeLocalSide: FamilyMerge.Side? = null
    private var mergeKnownMap: Map<String, String> = emptyMap()
    /** 起点指认（选人 / 共同家人对话框）。 */
    private var mergeBaseAnchors: Map<String, String> = emptyMap()
    /** “是我家的…”手动指认（可撤销）。 */
    private val mergeManual = LinkedHashMap<String, String>()
    private val mergeApproved = LinkedHashSet<String>()
    private val mergeTakeRemote = LinkedHashSet<FamilyMerge.ConflictKey>()
    private var mergeBridge: FamilyMerge.Bridge? = null
    private var mergeSelfId: String = ""
    /** 选人进入时记录所选，用于底部“整体替换…”。 */
    private var mergeSelectedRemoteId: String? = null

    /** 主按钮“合并到我的家庭”：所选文件人物锚定到本机“我”后进合并预览。 */
    fun enterMerge() {
        if (!canMerge()) return
        val selfId = _ui.value.localSelfId ?: return
        val sel = _ui.value.selectedId ?: return
        viewModelScope.launch {
            launchMerge(anchors = mapOf(sel to selfId), selectedRemoteId = sel)
        }
    }

    /** “认出一位两边都有的家人”A 路径：资料里的人 = 我家的人 → 起点指认。 */
    fun confirmAnchorPick(remoteId: String, localId: String) {
        viewModelScope.launch {
            launchMerge(anchors = mapOf(remoteId to localId),
                selectedRemoteId = null)
        }
    }

    /** “认出一位两边都有的家人”B 路径：没有共同家人 → 以桥接关系进合并。 */
    fun confirmBridge(anchorId: String, role: RelationRole) {
        viewModelScope.launch {
            launchMerge(anchors = emptyMap(),
                bridge = FamilyMerge.Bridge(anchorId, role),
                selectedRemoteId = null)
        }
    }

    fun dismissAnchorPick() = _ui.update { it.copy(anchorPickOpen = false) }

    /** 进入合并阶段：取本机快照与已保存的对应关系，然后重算。 */
    private suspend fun launchMerge(
        anchors: Map<String, String>,
        bridge: FamilyMerge.Bridge? = null,
        selectedRemoteId: String?
    ) {
        if (_ui.value.plan == null) return
        val selfId = _ui.value.localSelfId ?: return
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, anchorPickOpen = false) }
        try {
            mergeLocalSide = backup.localFamilySide()
            mergeKnownMap = db.personAliasDao().getAll()
                .associate { it.remotePersonId to it.localPersonId }
            mergeBaseAnchors = anchors
            mergeManual.clear()
            mergeApproved.clear()
            mergeTakeRemote.clear()
            mergeBridge = bridge
            mergeSelfId = selfId
            mergeSelectedRemoteId = selectedRemoteId
            if (_ui.value.mergeLocalItems.isEmpty()) {
                val (items, genders) = withContext(Dispatchers.Default) {
                    localPickItems(mergeLocalSide!!, selfId)
                }
                _ui.update {
                    it.copy(mergeLocalItems = items,
                        mergeLocalGenders = genders)
                }
            }
            _ui.update {
                it.copy(stage = ShareImportUiState.Stage.MERGE,
                    mergeCanReplace = selectedRemoteId != null)
            }
            recomputeMerge()
        } catch (e: Exception) {
            _ui.update { it.copy(busy = false, error = "合并失败，请重试") }
        }
    }

    /**
     * 按当前勾选/冲突选择/手动指认在 Dispatchers.Default 重算合并计划；
     * Reject（如桥接违规）→ 回选人阶段并 snackbar 提示原因。
     */
    private suspend fun recomputeMerge() {
        val file = _ui.value.plan ?: return
        val local = mergeLocalSide ?: return
        _ui.update { it.copy(busy = true) }
        try {
            val result = withContext(Dispatchers.Default) {
                FamilyMerge.plan(FamilyMerge.Request(
                    local = local, remote = backup.familySideOf(file),
                    localSelfId = mergeSelfId,
                    anchors = mergeBaseAnchors + mergeManual,
                    knownMap = mergeKnownMap,
                    approved = mergeApproved.toSet(),
                    takeRemote = mergeTakeRemote.toSet(),
                    bridge = mergeBridge))
            }
            when (result) {
                is FamilyMerge.Result.Ok -> {
                    val titles = withContext(Dispatchers.Default) {
                        newPersonTitles(result.plan)
                    }
                    _ui.update {
                        it.copy(
                            mergePlan = result.plan,
                            mergeNewTitles = titles,
                            mergeManualNames = mergeManual.mapValues { (_, l) ->
                                local.persons.firstOrNull { p -> p.id == l }
                                    ?.name ?: ""
                            },
                            // 将新保存的对应关系条数：认回了但 person_alias 还没有的
                            mergeNewAliases = result.plan.mapping.count { (r, l) ->
                                r != l && mergeKnownMap[r] != l
                            },
                            busy = false)
                    }
                }
                is FamilyMerge.Result.Reject -> _ui.update {
                    it.copy(stage = ShareImportUiState.Stage.CONFIRM,
                        mergePlan = null, busy = false,
                        error = result.reason)
                }
            }
        } catch (e: Exception) {
            _ui.update { it.copy(busy = false, error = "合并失败，请重试") }
        }
    }

    /** 本机“我”视角的人物条目与性别表（共同家人指认 / “是我家的…”共用）。 */
    private suspend fun localPickItems(
        side: FamilyMerge.Side, selfId: String
    ): Pair<List<io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem>,
            Map<String, Gender>> {
        val graph = ShareImport.graphOf(
            side.persons, side.relationships, side.dates, lunar)
        val results = engine.resolveAll(graph, selfId)
        val items = side.persons.map { p ->
            val r = results[p.id]
            val title = when {
                p.id == selfId -> "我"
                r == null || r.kind == KinshipKind.UNRELATED -> null
                else -> r.standardTitle
            }
            val inside = listOfNotNull(title,
                if (p.livingStatus == LivingStatus.DECEASED) "已故" else null)
                .joinToString(" · ")
            io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem(
                p.id, p.name,
                p.name + if (inside.isNotEmpty()) "（" + inside + "）" else "")
        }
        return items to side.persons.associate { it.id to it.gender }
    }

    /** 新增家人的称谓：合并后的内存图（本机+新增人物/关系/日期）以本机“我”观察。 */
    private suspend fun newPersonTitles(
        plan: FamilyMerge.Plan
    ): Map<String, String> {
        if (plan.newPersons.isEmpty()) return emptyMap()
        val local = mergeLocalSide ?: return emptyMap()
        val graph = ShareImport.graphOf(
            local.persons + plan.newPersons,
            local.relationships + plan.newRelationships,
            local.dates + plan.newDates,
            lunar)
        val results = engine.resolveAll(graph, mergeSelfId)
        return plan.newPersons.associate { np ->
            val r = results[np.id]
            val base = when {
                r == null || r.kind == KinshipKind.UNRELATED -> "暂无称谓"
                else -> r.standardTitle
            }
            np.id to (base + if (np.livingStatus == LivingStatus.DECEASED)
                "（已故）" else "")
        }
    }

    /** “可能是同一人”复选框：勾选 → 并入映射；取消 → 回到候选/新人物。 */
    fun toggleCandidate(remoteId: String) {
        if (!mergeApproved.remove(remoteId)) mergeApproved.add(remoteId)
        viewModelScope.launch { recomputeMerge() }
    }

    /** “不一致”单选：true=用对方的，false=保留我的。 */
    fun chooseConflict(key: FamilyMerge.ConflictKey, remote: Boolean) {
        if (remote) mergeTakeRemote.add(key) else mergeTakeRemote.remove(key)
        viewModelScope.launch { recomputeMerge() }
    }

    /** 新增家人“是我家的…”：手动指认为本机某人物（下一次重算并入映射）。 */
    fun pickAsLocal(remoteId: String, localId: String) {
        mergeManual[remoteId] = localId
        viewModelScope.launch { recomputeMerge() }
    }

    /** 撤销“是我家的…”手动指认。 */
    fun unpickAsLocal(remoteId: String) {
        mergeManual.remove(remoteId)
        viewModelScope.launch { recomputeMerge() }
    }

    /** “确认合并”：单事务写库 + 保存 person_alias 对应关系。 */
    fun confirmMergeImport() {
        val plan = _ui.value.mergePlan ?: return
        // 无库内改动但有新对应关系时也允许确认（认回本身要持久化）
        if (_ui.value.busy ||
            !(plan.hasWrites() || _ui.value.mergeNewAliases > 0)) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                backup.importFamilyMerge(
                    plan, _ui.value.plan?.avatars ?: emptyMap()) {
                    // 对应关系只存 remote != local 的项（同 id 本就是同一人）
                    val rows = plan.mapping
                        .filter { (r, l) -> r != l }
                        .map { (r, l) -> PersonAliasEntity(
                            r, l, System.currentTimeMillis()) }
                    if (rows.isNotEmpty()) db.personAliasDao().upsertAll(rows)
                }
                // 合并完成提示弹到主线程（协程可能从 Default/IO 上恢复执行）
                val toastText = if (plan.newPersons.isNotEmpty())
                    "已合并：新增 " + plan.newPersons.size + " 位家人"
                else "已合并"
                androidx.core.content.ContextCompat
                    .getMainExecutor(context).execute {
                        Toast.makeText(context, toastText,
                            Toast.LENGTH_SHORT).show()
                    }
                _ui.update { it.copy(done = true, doneName = "", busy = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, error = "合并失败，请重试") }
            }
        }
    }

    /** “没有新内容”的“完成”：不写库，直接结束。 */
    fun finishMerge() {
        _ui.update { it.copy(done = true, doneName = "") }
    }

    /** 合并预览“返回” → 回选人阶段（选择状态保留）。 */
    fun exitMerge() {
        _ui.update {
            it.copy(stage = ShareImportUiState.Stage.CONFIRM, mergePlan = null)
        }
    }

    /** 合并预览底部“整体替换…”：回到选人阶段并走原替换流水线。 */
    fun mergeReplace() {
        exitMerge()
        confirmImport()
    }

    /** snackbar 已显示 → 清空（避免相同文案不再弹出）。 */
    fun onErrorShown() = _ui.update { it.copy(error = null) }
}


/** 家人共享导入页（路由 share/import?uri=）。 */
@Composable
fun ShareImportScreen(
    onDone: (String) -> Unit,
    viewModel: ShareImportViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    ShareImportContent(st = st, onOpen = viewModel::open,
        onSelect = viewModel::select, onConfirm = viewModel::confirmImport,
        onEnterMerge = viewModel::enterMerge,
        onAskMerge = viewModel::askMerge,
        onConfirmMerge = viewModel::confirmMerge,
        onCancelMerge = { viewModel.dismissMerge() },
        onAnswerAdopt = viewModel::answerAdopt,
        onNewSelfClick = viewModel::openNewSelf,
        onDismissNewSelf = viewModel::dismissNewSelf,
        onSubmitNewSelf = viewModel::confirmNewSelf,
        onAnswerCoParent = viewModel::answerCoParent,
        onConfirmPreview = viewModel::confirmPreview,
        onCancelPreview = viewModel::cancelPreview,
        onReplaceAnswer = viewModel::answerReplace,
        onAnchorPick = viewModel::confirmAnchorPick,
        onBridge = viewModel::confirmBridge,
        onDismissAnchorPick = viewModel::dismissAnchorPick,
        onToggleCandidate = viewModel::toggleCandidate,
        onChooseConflict = viewModel::chooseConflict,
        onPickAsLocal = viewModel::pickAsLocal,
        onUnpickAsLocal = viewModel::unpickAsLocal,
        onConfirmMergeImport = viewModel::confirmMergeImport,
        onFinishMerge = viewModel::finishMerge,
        onExitMerge = viewModel::exitMerge,
        onMergeReplace = viewModel::mergeReplace,
        onErrorShown = viewModel::onErrorShown,
        onDone = onDone)
}

/** 无状态主体（截图测试直接喂状态）。 */
@Composable
fun ShareImportContent(
    st: ShareImportUiState,
    onOpen: (CharArray) -> Unit,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    /** “合并到我的家庭”（选人 → 合并预览；已有数据 + 本机有“我”时）。 */
    onEnterMerge: () -> Unit = {},
    onAskMerge: () -> Unit = {},
    onConfirmMerge: () -> Unit = {},
    onCancelMerge: () -> Unit = {},
    onAnswerAdopt: (Boolean) -> Unit = {},
    /** “资料里没有我（添加我自己）”入口与表单提交。 */
    onNewSelfClick: () -> Unit = {},
    onDismissNewSelf: () -> Unit = {},
    onSubmitNewSelf: (String, String, RelationRole) -> Unit = { _, _, _ -> },
    /** “X 也是你的父亲/母亲吗”（新增“我”为子女关系时）。 */
    onAnswerCoParent: (Boolean) -> Unit = {},
    /** “以 X 的身份导入？”预览：确认 / 重新选择。 */
    onConfirmPreview: () -> Unit = {},
    onCancelPreview: () -> Unit = {},
    /** 已有数据的替换确认：true=替换，false=先去备份。 */
    onReplaceAnswer: (Boolean) -> Unit = {},
    // ---- 合并阶段（Stage.MERGE）----
    /** “认出一位两边都有的家人”：指认（对方 id, 本机 id）。 */
    onAnchorPick: (String, String) -> Unit = { _, _ -> },
    /** “没有共同的家人”：桥接（对方人物 id, 我是它的角色）。 */
    onBridge: (String, RelationRole) -> Unit = { _, _ -> },
    onDismissAnchorPick: () -> Unit = {},
    /** “可能是同一人”复选框。 */
    onToggleCandidate: (String) -> Unit = {},
    /** “不一致”单选：true=用对方的。 */
    onChooseConflict: (FamilyMerge.ConflictKey, Boolean) -> Unit = { _, _ -> },
    /** 新增家人“是我家的…”（remoteId → localId）与撤销。 */
    onPickAsLocal: (String, String) -> Unit = { _, _ -> },
    onUnpickAsLocal: (String) -> Unit = {},
    onConfirmMergeImport: () -> Unit = {},
    /** “没有新内容”的“完成”。 */
    onFinishMerge: () -> Unit = {},
    /** 合并预览“返回”/“整体替换…”。 */
    onExitMerge: () -> Unit = {},
    onMergeReplace: () -> Unit = {},
    /** snackbar 已显示 → 清空 error（相同文案还能再弹）。 */
    onErrorShown: () -> Unit = {},
    onDone: (String) -> Unit
) {
    val snack = remember { SnackbarHostState() }
    var password by remember { mutableStateOf(CharArray(0)) }

    if (st.done) onDone(st.doneName)

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        // 状态栏留白由 PageHeader 负责；material3 1.4 默认全量
        // insets 会在页头上方再叠一层，这里只留水平向
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PageHeader {
                Text("家人共享", style = MaterialTheme.typography.headlineSmall)
            }
            when (st.stage) {
                ShareImportUiState.Stage.PASSWORD -> {
                    io.github.xiaohaoz1.jiarenxia.ui.common.DisableAutofill()
                    JrxCard(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("这份文件来自家人的手机，需要密码才能打开。",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = String(password),
                            onValueChange = { password = it.toCharArray() },
                            label = { Text("密码") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onOpen(password.copyOf()) },
                            enabled = password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("打开") }
                    }
                }
                ShareImportUiState.Stage.CONFIRM -> {
                    val plan = st.plan
                    if (plan != null) {
                        JrxCard(Modifier.fillMaxWidth().padding(16.dp)) {
                            val sharer = plan.persons
                                .firstOrNull { it.id == plan.sharedBy }?.name ?: "家人"
                            val modules = buildList {
                                if (plan.tasks.isNotEmpty()) add("家庭事项")
                            }
                            Text(
                                "来自 " + sharer + " 的家人共享：" +
                                    plan.persons.size + " 位家人" +
                                    if (modules.isNotEmpty())
                                        "，含" + modules.joinToString("、")
                                    else "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            "你是谁？（请选你自己）",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(16.dp, 8.dp)
                        )
                        // 超过 10 人提供搜索（姓名/称谓/拼音，同人物选择器）
                        var q by remember { mutableStateOf("") }
                        if (st.candidates.size > 10) {
                            OutlinedTextField(
                                value = q, onValueChange = { q = it },
                                placeholder = { Text("搜索姓名、称谓或拼音") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        val shown = if (q.isBlank()) st.candidates else
                            io.github.xiaohaoz1.jiarenxia.domain.PersonSearch
                                .filter(
                                    st.candidates.mapNotNull { it.overview }, q
                                ).map { o -> st.candidates.first {
                                    it.personId == o.person.id } }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()
                            .padding(horizontal = 16.dp)) {
                            items(shown, key = { it.personId }) { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onSelect(item.personId) }
                                ) {
                                    RadioButton(
                                        selected = st.selectedId == item.personId,
                                        onClick = { onSelect(item.personId) }
                                    )
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                            // 最后一项：文件里没有我 → 增补一个“我”（16.3）
                            item(key = "new-self") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { onNewSelfClick() }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = st.importPlan != null,
                                        onClick = { onNewSelfClick() }
                                    )
                                    Text(
                                        "资料里没有我（添加我自己）",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        // 已有数据且有“我”：主按钮合并、次按钮整体替换（16.3）
                        val canMerge =
                            st.hasExisting && st.localSelfId != null
                        if (canMerge) {
                            Button(
                                onClick = onEnterMerge,
                                enabled = st.selectedId != null && !st.busy,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) { Text("合并到我的家庭") }
                            TextButton(
                                onClick = onConfirm,
                                enabled = st.selectedId != null && !st.busy,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) { Text("整体替换我的资料…") }
                        } else {
                            Button(
                                onClick = onConfirm,
                                enabled = st.selectedId != null && !st.busy,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(16.dp)
                            ) { Text("导入") }
                        }
                        // 备选路径：满足结为夫妻前置条件时显示（FAMILY_AFFAIRS 10/16.3）
                        if (st.mergeEligible) {
                            TextButton(
                                onClick = onAskMerge,
                                enabled = !st.busy,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) { Text("和我结为夫妻（合并两家）") }
                        }
                    }
                }
                ShareImportUiState.Stage.MERGE -> {
                    val mp = st.mergePlan
                    val file = st.plan
                    if (mp != null && file != null) {
                        val sharer = file.persons
                            .firstOrNull { it.id == file.sharedBy }?.name
                            ?: "家人"
                        MergePreviewContent(
                            sharerName = sharer,
                            plan = mp,
                            newTitles = st.mergeNewTitles,
                            manualNames = st.mergeManualNames,
                            localItems = st.mergeLocalItems,
                            localGenders = st.mergeLocalGenders,
                            newAliases = st.mergeNewAliases,
                            canReplace = st.mergeCanReplace,
                            busy = st.busy,
                            onToggleCandidate = onToggleCandidate,
                            onPickAsLocal = onPickAsLocal,
                            onUnpickAsLocal = onUnpickAsLocal,
                            onChooseConflict = onChooseConflict,
                            onConfirm = onConfirmMergeImport,
                            onFinish = onFinishMerge,
                            onReplace = onMergeReplace,
                            onBack = onExitMerge
                        )
                    }
                }
            }
        }
    }

    // “认出一位两边都有的家人”（无交集的“资料里没有我”，16.3）
    if (st.anchorPickOpen) {
        val file = st.plan
        if (file != null) {
            CommonAnchorDialog(
                candidates = st.candidates,
                fileGenders = file.persons.associate { it.id to it.gender },
                localItems = st.mergeLocalItems,
                localGenders = st.mergeLocalGenders,
                onPick = onAnchorPick,
                onBridge = onBridge,
                onDismiss = onDismissAnchorPick
            )
        }
    }

    // “和我结为夫妻”合并预览
    if (st.mergeAsk) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("合并两家") },
            text = {
                Column {
                    Text("新增 " + st.mergePreviewNew + " 位家人")
                    if (st.mergePreviewMerged.isNotEmpty()) {
                        Text(
                            "将与已录入的 " +
                                st.mergePreviewMerged.joinToString("、") + " 合并",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmMerge) { Text("确认合并") }
            },
            dismissButton = {
                TextButton(onClick = onCancelMerge) { Text("取消") }
            }
        )
    }
    // “同父同母”补全询问（选定“我”后多这一步）
    if (st.adoptSibling != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("补全父母") },
            text = {
                Text(
                    "你和" + st.adoptSiblingName +
                        "是同父同母的亲兄弟姐妹吗？"
                )
            },
            confirmButton = {
                TextButton(onClick = { onAnswerAdopt(true) }) { Text("是") }
            },
            dismissButton = {
                TextButton(onClick = { onAnswerAdopt(false) }) {
                    Text("不是 / 不确定")
                }
            }
        )
    }
    // “资料里没有我”选了子女关系：对方配偶也可能是自己的父亲/母亲（16.3）
    if (st.coParentId != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("补全父母") },
            text = {
                Text(st.coParentName + "也是你的" + st.coParentWord + "吗？")
            },
            confirmButton = {
                TextButton(onClick = { onAnswerCoParent(true) }) { Text("是") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onAnswerCoParent(false) }) {
                        Text("不是")
                    }
                    TextButton(onClick = { onAnswerCoParent(false) }) {
                        Text("不确定")
                    }
                }
            }
        )
    }
    // “以 X 的身份导入？”——从你的角度看预览（16.3）
    st.preview?.let { pv ->
        AlertDialog(
            onDismissRequest = onCancelPreview,
            title = { Text("以“" + st.previewName + "”的身份导入？") },
            text = { PerspectivePreviewBody(pv) },
            confirmButton = {
                TextButton(onClick = onConfirmPreview) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = onCancelPreview) { Text("重新选择") }
            }
        )
    }
    // 已有数据 → 预览确认后再确认替换
    if (st.replaceAsk) {
        AlertDialog(
            onDismissRequest = { onReplaceAnswer(false) },
            title = { Text("替换现有数据？") },
            text = {
                Text("将替换手机上现有的全部数据（包括家庭事项），建议先备份")
            },
            confirmButton = {
                TextButton(onClick = { onReplaceAnswer(true) }) {
                    Text("替换")
                }
            },
            dismissButton = {
                TextButton(onClick = { onReplaceAnswer(false) }) { Text("先去备份") }
            }
        )
    }
    // “资料里没有我”：姓名 + “我是 [资料里的家人] 的 [角色]”（16.3）
    if (st.newSelfOpen) {
        val plan = st.plan
        var nsName by remember { mutableStateOf("") }
        var nsAnchor by remember {
            mutableStateOf<io.github.xiaohaoz1.jiarenxia.domain
                .PersonPickItem?>(null)
        }
        var nsRole by remember { mutableStateOf<RelationRole?>(null) }
        var anchorPick by remember { mutableStateOf(false) }
        val nsError = if (plan != null && nsAnchor != null && nsRole != null)
            ShareImport.newSelfError(plan, nsAnchor!!.personId, nsRole!!)
        else null
        AlertDialog(
            onDismissRequest = onDismissNewSelf,
            title = { Text("添加我自己") },
            text = {
                NewSelfForm(
                    name = nsName, onName = { nsName = it },
                    anchorLabel = nsAnchor?.label,
                    onPickAnchor = { anchorPick = true },
                    role = nsRole, onRole = { nsRole = it },
                    error = nsError
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nsName.isNotBlank() && nsAnchor != null &&
                        nsRole != null && nsError == null,
                    onClick = {
                        onSubmitNewSelf(nsName, nsAnchor!!.personId, nsRole!!)
                    }
                ) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = onDismissNewSelf) { Text("取消") }
            }
        )
        if (anchorPick) {
            PersonPickerDialog(
                title = "选择资料里的家人",
                items = st.candidates,
                selectedId = nsAnchor?.personId,
                onPick = { nsAnchor = it },
                onDismiss = { anchorPick = false }
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(st.error) {
        st.error?.let { snack.showSnackbar(it); onErrorShown() }
    }
}

/**
 * “添加我自己”表单正文（抽出供截图）：姓名 + “我是 [选择资料里的家人] 的
 * [12 种关系]”，性别由所选关系决定，不推断血缘；[error] 为关系约束提示。
 * [showName]=false 时隐藏姓名框（“没有共同的家人”桥接用，本机“我”已有名字）。
 */
@Composable
internal fun NewSelfForm(
    name: String,
    onName: (String) -> Unit,
    anchorLabel: String?,
    onPickAnchor: () -> Unit,
    role: RelationRole?,
    onRole: (RelationRole) -> Unit,
    error: String?,
    showName: Boolean = true
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (showName) {
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text("姓名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("我是")
            TextButton(onClick = onPickAnchor) {
                Text(
                    anchorLabel ?: "选择资料里的家人",
                    color = if (anchorLabel == null)
                        MaterialTheme.colorScheme.primary
                    else androidx.compose.ui.graphics.Color.Unspecified
                )
            }
            Text("的")
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RelationRole.entries.forEach { r ->
                FilterChip(
                    selected = role == r,
                    onClick = { onRole(r) },
                    label = { Text(r.label, maxLines = 1, softWrap = false) }
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** “以 X 的身份导入”预览正文（抽出供截图；特大字号可滚动）。 */
@Composable
internal fun PerspectivePreviewBody(
    preview: ShareImport.PerspectivePreview
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("从你的角度看：", style = MaterialTheme.typography.bodyMedium)
        preview.lines.forEach {
            Text(
                it.name + " · " + it.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        if (preview.unresolved > 0) {
            Text(
                "另有 " + preview.unresolved + " 位暂时算不出称谓",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
