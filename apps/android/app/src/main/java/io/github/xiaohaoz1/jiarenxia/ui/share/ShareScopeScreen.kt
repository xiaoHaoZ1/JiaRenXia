package io.github.xiaohaoz1.jiarenxia.ui.share

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem
import io.github.xiaohaoz1.jiarenxia.domain.PersonPickList
import io.github.xiaohaoz1.jiarenxia.domain.PersonSearch
import io.github.xiaohaoz1.jiarenxia.ui.backup.ExportPasswordDialog
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranches
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** “选择要分享的家人”页内步骤。 */
sealed interface ShareScopeStep {
    /** 勾选人物中。 */
    data object Picking : ShareScopeStep

    /** 断开提醒：勾选结果里有人与“我”之间缺少中间人物（ids）。 */
    data class Disconnected(val ids: Set<String>) : ShareScopeStep

    /** 设置共享密码（两次输入）。 */
    data object SetPassword : ShareScopeStep

    /** 正在生成共享文件。 */
    data object Working : ShareScopeStep

    /** 文件就绪 → 界面发 ACTION_SEND。 */
    data class Ready(val file: File) : ShareScopeStep
}

data class ShareScopeUiState(
    val loading: Boolean = true,
    /** 当前分支筛选（决定点“全部/父系…”时的预选集合）。 */
    val branch: GraphBranch,
    /** 有成员的分支（枚举序，ALL 恒在首位）。 */
    val availableBranches: List<GraphBranch> = listOf(GraphBranch.ALL),
    /** 全部人物条目（“姓名（称谓）”，排序同人物选择器）。 */
    val items: List<PersonPickItem> = emptyList(),
    /** 已勾选的人物 id；“我”恒在集合内。 */
    val selected: Set<String> = emptySet(),
    val selfId: String? = null,
    val selfName: String = "",
    val step: ShareScopeStep = ShareScopeStep.Picking,
    /** 一次性提示（生成失败等），界面消费后调 onMessageShown。 */
    val message: String? = null
)

/**
 * “选择要分享的家人”ViewModel：分支预选 + 逐项勾选 + 断开检查，
 * 生成共享文件。共享文件只含人物档案（人物/手机号/关系/生日/头像），
 * 不附带事项/人情/保单，人物与日期上的备注一律剔除
 * （BackupManager.exportShare）。
 */
@HiltViewModel
class ShareScopeViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val context: android.content.Context,
    observeOverview: ObserveFamilyOverview,
    private val backup: BackupManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** 路由参数 share/scope?branch=：预设分支名，非法值回退“全部”。 */
    private val initialBranch: GraphBranch =
        runCatching {
            GraphBranch.valueOf(savedStateHandle.get<String>("branch") ?: "")
        }.getOrDefault(GraphBranch.ALL)

    private val _ui = MutableStateFlow(ShareScopeUiState(branch = initialBranch))
    val ui: StateFlow<ShareScopeUiState> = _ui

    /** 最近一次总览（断开检查的关系边与分支重选用）。 */
    private var overview: FamilyOverview? = null
    private var relPairs: List<Pair<String, String>> = emptyList()

    /** 仅在提交后短暂持有的共享密码；用完即清空（同备份流程）。 */
    private var pendingPassword: CharArray? = null

    init {
        viewModelScope.launch {
            observeOverview().collect { ov ->
                overview = ov
                relPairs = ov?.graph?.relationships
                    ?.map { it.sourcePersonId to it.targetPersonId }
                    .orEmpty()
                _ui.update { st ->
                    if (ov == null) {
                        st.copy(loading = false, items = emptyList(),
                            selected = emptySet(), selfId = null, selfName = "")
                    } else {
                        val selfId = ov.self.person.id
                        // 首次出现总览按路由分支预选；之后保留勾选
                        // （剔除已被删的人物），“我”恒选中
                        val selected =
                            if (st.selfId == null) ShareScope.preset(ov, st.branch)
                            else st.selected.filter { it in ov.byId }.toSet()
                        st.copy(
                            loading = false,
                            items = PersonPickList.build(ov),
                            availableBranches = GraphBranches.available(ov),
                            selected = selected + selfId,
                            selfId = selfId,
                            selfName = ov.self.person.name
                        )
                    }
                }
            }
        }
    }

    /** 点选分支筛选：按该分支重新预选。 */
    fun selectBranch(branch: GraphBranch) {
        val ov = overview ?: return
        _ui.update {
            it.copy(
                branch = branch,
                selected = ShareScope.preset(ov, branch)
            )
        }
    }

    /** 勾选/取消一人；“我”始终勾选、不可取消。 */
    fun toggle(personId: String) {
        _ui.update { st ->
            if (personId == st.selfId) return@update st
            st.copy(
                selected = if (personId in st.selected) st.selected - personId
                else st.selected + personId
            )
        }
    }

    /**
     * 下一步：先算断开检查——勾选集合里有人与“我”不通（中间人物被取消）
     * 时先给出提醒，可返回补选；否则直接进密码步骤。
     */
    fun next() {
        val st = _ui.value
        val selfId = st.selfId ?: return
        val gone = ShareScope.disconnected(st.selected, relPairs, selfId)
        _ui.update {
            it.copy(
                step = if (gone.isEmpty()) ShareScopeStep.SetPassword
                else ShareScopeStep.Disconnected(gone)
            )
        }
    }

    /** 断开提醒“返回补选”。 */
    fun backToPicking() {
        _ui.update { it.copy(step = ShareScopeStep.Picking) }
    }

    /** 断开提醒“仍然分享”。 */
    fun shareAnyway() {
        _ui.update { it.copy(step = ShareScopeStep.SetPassword) }
    }

    /** 密码对话框取消：清空暂存密码并回到勾选。 */
    fun dismissPassword() {
        clearPending()
        _ui.update { it.copy(step = ShareScopeStep.Picking) }
    }

    /**
     * 密码确定后生成共享文件：勾选了全部人物时文件名
     * 不带“部分”，否则带“部分”标记；内容只含所选人物。
     */
    fun submitPassword(pw: CharArray) {
        val st = _ui.value
        val selfId = st.selfId ?: return
        pendingPassword = pw.copyOf()
        _ui.update { it.copy(step = ShareScopeStep.Working) }
        viewModelScope.launch {
            val pw2 = pendingPassword
            try {
                val dir = File(context.cacheDir, "share")
                dir.deleteRecursively(); dir.mkdirs()
                val stamp = java.text.SimpleDateFormat(
                    "yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                val shareAll =
                    st.selected.containsAll(
                        st.items.map { it.personId }.toSet())
                val file = File(
                    dir, "家人侠-家人共享-" + st.selfName +
                        (if (shareAll) "-" else "-部分-") + stamp + ".jrxb")
                pw2?.let { p ->
                    file.outputStream().use { out ->
                        backup.exportShare(
                            out, p, sharedBy = selfId,
                            personIds = st.selected
                        )
                    }
                }
                _ui.update { it.copy(step = ShareScopeStep.Ready(file)) }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(step = ShareScopeStep.Picking,
                        message = "生成共享文件失败，请重试")
                }
            } finally {
                pw2?.fill(' '); pendingPassword = null
            }
        }
    }

    /** 共享 intent 发出后复位到勾选页（可继续调整再分享）。 */
    fun shareSent() {
        _ui.update { it.copy(step = ShareScopeStep.Picking) }
    }

    fun onMessageShown() {
        _ui.update { it.copy(message = null) }
    }

    private fun clearPending() {
        pendingPassword?.fill(' '); pendingPassword = null
    }
}

/**
 * “选择要分享的家人”（FAMILY_AFFAIRS 9）：共享与连接页与关系图的
 * 分享入口都落到这里——分支预选 → 逐项勾选 → 断开提醒 →
 * 共享密码 → 生成文件 → 系统分享。共享文件只含家人档案，
 * 事项/人情/保单/备注一律不附带。
 */
@Composable
fun ShareScopeScreen(
    onBack: () -> Unit,
    viewModel: ShareScopeViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(st.message) {
        st.message?.let {
            android.widget.Toast.makeText(
                context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.onMessageShown()
        }
    }

    ShareScopeContent(
        state = st,
        onBack = onBack,
        onSelectBranch = viewModel::selectBranch,
        onToggle = viewModel::toggle,
        onNext = viewModel::next
    )

    when (val step = st.step) {
        is ShareScopeStep.Disconnected -> {
            val labels = st.items.filter { it.personId in step.ids }
                .map { it.label }
            AlertDialog(
                onDismissRequest = viewModel::backToPicking,
                title = { Text("这些人可能算不出称谓") },
                text = {
                    Column {
                        labels.forEach { Text(it) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "这些人和你之间缺少中间的家人，对方导入后可能算不出称谓",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::shareAnyway) { Text("仍然分享") }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::backToPicking) { Text("返回补选") }
                }
            )
        }
        ShareScopeStep.SetPassword -> ExportPasswordDialog(
            title = "设置共享密码",
            hint = "请通过电话等其他方式把密码告诉家人",
            onSubmit = viewModel::submitPassword,
            onDismiss = viewModel::dismissPassword
        )
        ShareScopeStep.Working -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("正在生成共享文件…")
                }
            },
            confirmButton = {}
        )
        is ShareScopeStep.Ready -> {
            LaunchedEffect(step.file) {
                val uri = FileProvider.getUriForFile(
                    context,
                    context.packageName + ".share-provider",
                    step.file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "分享给家人"))
                viewModel.shareSent()
            }
        }
        ShareScopeStep.Picking -> {}
    }
}

/** 纯展示：分支筛选行 + 可搜索勾选列表 + 底部摘要。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareScopeContent(
    state: ShareScopeUiState,
    onBack: () -> Unit,
    onSelectBranch: (GraphBranch) -> Unit = {},
    onToggle: (String) -> Unit = {},
    onNext: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(state.items, query) {
        if (query.isBlank()) state.items
        else PersonSearch.filter(
            state.items.mapNotNull { it.overview }, query
        ).map { o -> state.items.first { it.personId == o.person.id } }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择要分享的家人") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        bottomBar = {
            androidx.compose.material3.Surface {
            // 全面屏手势条/导航键在底部：与其他页面的底部操作区一致，让出导航栏
            Column(
                Modifier.navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "将分享 " + state.selected.size + " 位家人：" +
                        "包含手机号、生日、头像",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onNext,
                    enabled = !state.loading && state.selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("下一步") }
            }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.availableBranches.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.availableBranches.size) { i ->
                        val b = state.availableBranches[i]
                        FilterChip(
                            selected = state.branch == b,
                            onClick = { onSelectBranch(b) },
                            label = { Text(b.label) }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索姓名、称谓或拼音") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(
                    count = filtered.size,
                    key = { filtered[it].personId }
                ) { i ->
                    val item = filtered[i]
                    val isSelf = item.personId == state.selfId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = item.personId in state.selected,
                                enabled = !isSelf,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(item.personId) }
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Checkbox(
                            checked = item.personId in state.selected,
                            onCheckedChange = null,
                            enabled = !isSelf
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(item.label)
                    }
                }
            }
        }
    }
}
