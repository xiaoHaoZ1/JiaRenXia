package io.github.xiaohaoz1.jiarenxia.ui.share

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material.icons.rounded.ReplyAll
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.backup.CollabMeta
import io.github.xiaohaoz1.jiarenxia.data.collab.CollabException
import io.github.xiaohaoz1.jiarenxia.data.collab.FamilyCollaborationService
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyLocalOnlyEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyRevisionPlan
import io.github.xiaohaoz1.jiarenxia.ui.backup.ExportPasswordDialog
import io.github.xiaohaoz1.jiarenxia.ui.mine.RowDivider
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsGroup
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsHint
import io.github.xiaohaoz1.jiarenxia.ui.mine.SettingsScaffold
import io.github.xiaohaoz1.jiarenxia.ui.restore.PasswordDialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 协作页 ViewModel（FAMILY_AFFAIRS §5 界面侧）：
 *
 * - 共享与连接页：状态行（角色/版本/待收草稿/仅本机数）、发布确认版、
 *   发出补充包（产物经系统分享面板发出）；
 * - 协作接收页：消费 [AppEvents.pendingCollab]（新包）或恢复待处理草稿
 *   （进程重启续审），渲染计划缓存、随选随存决策、输密码应用。
 */
@HiltViewModel
class CollabViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val service: FamilyCollaborationService,
    private val db: AppDatabase,
    private val repo: FamilyRepository,
    private val appEvents: AppEvents
) : ViewModel() {

    // ---------- 发送侧（共享与连接页） ----------

    enum class SendKind { PUBLISH, CONTRIBUTE }

    sealed interface SendStep {
        data object Idle : SendStep
        data class AskPassword(val kind: SendKind) : SendStep
        data object Working : SendStep
        /** 文件已生成，界面接 ACTION_SEND。 */
        data class Ready(val file: File) : SendStep
    }

    val status = MutableStateFlow<FamilyCollaborationService.Status?>(null)
    val localOnlyPersons =
        MutableStateFlow<List<Pair<PersonEntity, FamilyLocalOnlyEntity>>>(
            emptyList())
    /** 本机相对已接受基准的差异项数（成员“发回补充”副文案）。 */
    val pendingDiff = MutableStateFlow(0)
    val sendStep = MutableStateFlow<SendStep>(SendStep.Idle)
    val message = MutableStateFlow<String?>(null)

    fun refresh() {
        viewModelScope.launch {
            status.value = service.status()
            localOnlyPersons.value = service.localOnlyPersons()
            pendingDiff.value = service.pendingDiffCount()
        }
    }

    fun publish() { sendStep.value = SendStep.AskPassword(SendKind.PUBLISH) }
    fun contribute() { sendStep.value = SendStep.AskPassword(SendKind.CONTRIBUTE) }
    fun dismissSend() { sendStep.value = SendStep.Idle }
    fun sendSent() { sendStep.value = SendStep.Idle }
    fun onMessageShown() { message.value = null }

    fun submitSendPassword(pw: CharArray) {
        val kind = (sendStep.value as? SendStep.AskPassword)?.kind ?: return
        // 对话框提交后会立即把它持有的数组清空，必须同步复制一份再交给协程
        val pwCopy = pw.copyOf()
        sendStep.value = SendStep.Working
        viewModelScope.launch {
            try {
                val dir = File(appContext.cacheDir, "collab")
                dir.deleteRecursively(); dir.mkdirs()
                val tmp = File(dir, "package.jrxb")
                val stamp = SimpleDateFormat("yyyyMMdd", Locale.US)
                    .format(Date())
                val file = when (kind) {
                    SendKind.PUBLISH -> {
                        val meta: CollabMeta
                        tmp.outputStream().use { out ->
                            meta = service.exportConfirmed(out, pwCopy)
                        }
                        val f = File(dir, "家人侠-确认版-第" + meta.revision +
                            "版-" + stamp + ".jrxb")
                        tmp.renameTo(f); f
                    }
                    SendKind.CONTRIBUTE -> {
                        tmp.outputStream().use { out ->
                            service.exportContribution(out, pwCopy)
                        }
                        val who = db.personDao().getSelf()?.name ?: "家人"
                        val f = File(dir, "家人侠-补充-" + who + "-" +
                            stamp + ".jrxb")
                        tmp.renameTo(f); f
                    }
                }
                sendStep.value = SendStep.Ready(file)
            } catch (e: CollabException) {
                message.value = e.message
                sendStep.value = SendStep.Idle
            } catch (e: Exception) {
                message.value = "生成协作包失败，请重试"
                sendStep.value = SendStep.Idle
            } finally {
                pwCopy.fill(' ')
            }
        }
    }

    fun unmark(personId: String) {
        viewModelScope.launch {
            runCatching { service.unmarkLocalOnly(personId) }
            refresh()
        }
    }

    // ---------- 接收审查 ----------

    /** “资料里没有我”占位选择值。 */
    private val SELF_NONE = "none"

    data class ReviewUi(
        val loading: Boolean = true,
        /** 无交接也无待处理草稿。 */
        val missing: Boolean = false,
        val packageId: String? = null,
        val mode: String = "",
        val revision: Int = 0,
        val coordinatorName: String? = null,
        /** 补充包发送者名（“来自 X 的补充”）。 */
        val senderName: String? = null,
        val applyItems: List<FamilyCollaborationService.CachedItem> =
            emptyList(),
        /** 需要用户裁决的项（CONFLICT/IDENTITY/REAPPEAR）。 */
        val decisionItems: List<FamilyCollaborationService.CachedItem> =
            emptyList(),
        /** 被硬规则拦下、自动保留本机的项（BLOCKED，仅展示）。 */
        val blockedItems: List<FamilyCollaborationService.CachedItem> =
            emptyList(),
        /** 本机相对基准的改动（PENDING，未汇入提示）。 */
        val pendingItems: List<FamilyCollaborationService.CachedItem> =
            emptyList(),
        /** pendingItems 人物（规范 id → 本机名）。 */
        val pendingNames: Map<String, String> = emptyMap(),
        val decisions: Map<String, String> = emptyMap(),
        val hasLocalSelf: Boolean = true,
        val remoteRoster: List<Pair<String, String>> = emptyList(),
        /** “我是谁”选择：规范 id / SELF_NONE / null 未表态。 */
        val selfChoice: String? = null,
        /** 标为“仅本机保留”的规范 id 集。 */
        val localOnlyMarks: Set<String> = emptySet(),
        /** true = “放弃这些补充，以确认版为准”（本机未汇入改动按远端回写）。 */
        val dropPending: Boolean = false,
        /** 续草稿时因资料变化被作废、需重新确认的旧选择数。 */
        val invalidCount: Int = 0,
        /** 当前预览的计划令牌：应用时携回，资料再变则提交不生效。 */
        val planToken: String? = null,
        /** 已绑定为成员 → 可把补充先发回汇总者（首次未绑定先应用再加入）。 */
        val canSendContribution: Boolean = false,
        val busy: Boolean = false,
        val done: Boolean = false,
        /** 续草稿场景没有内存密码 → 应用时要先输密码。 */
        val askPassword: Boolean = false,
        val error: String? = null
    ) {
        /** 首次收确认版本机无“我” → 必须选“我是谁”或明确“都不是”。 */
        val needSelfChoice get() =
            !hasLocalSelf && mode == "confirmed" && remoteRoster.isNotEmpty()

        val ready get() =
            decisionItems.all { decisions.containsKey(it.key) } &&
                (!needSelfChoice || selfChoice != null)
    }

    val review = MutableStateFlow(ReviewUi())
    private var reviewStarted = false
    private var reviewPassword: CharArray? = null

    /** 进入接收页：优先消费新交接，否则恢复待处理草稿。 */
    fun startReview() {
        if (reviewStarted) return
        reviewStarted = true
        viewModelScope.launch {
            val handoff = appEvents.pendingCollab
            appEvents.pendingCollab = null
            try {
                if (handoff != null) {
                    val p = service.prepareReceive(handoff.bytes, handoff.password)
                    reviewPassword = handoff.password
                    loadReview(p.draft.packageId)
                } else {
                    val d = service.pendingDraft()
                    if (d == null) {
                        review.value = ReviewUi(loading = false, missing = true)
                    } else {
                        loadReview(d.packageId)
                    }
                }
            } catch (e: CollabException) {
                review.value = ReviewUi(loading = false, error = e.message)
            }
        }
    }

    private suspend fun loadReview(packageId: String) {
        // 续草稿复核（R5/R9）：按当前本机资料重算计划，旧选择按指纹逐项
        // 核验，作废项回到未确认；我是谁/仅本机/放弃补充一并恢复
        val r = service.resumeReview(packageId)
        if (r == null) {
            review.value = ReviewUi(loading = false, missing = true)
            return
        }
        // 写序跨会话续起：界面/进程重建后 persistSeq 不归零，
        // 否则已存 seq=N 的草稿会拒掉重建后前 N 次保存（边界 1）
        persistSeq = r.choicesSeq
        review.value = uiFromResumed(packageId, r)
    }

    /** 重算后的审阅内容 → 页态（进入页与页内身份裁决刷新共用）。 */
    private suspend fun uiFromResumed(
        packageId: String,
        r: FamilyCollaborationService.ResumedReview
    ): ReviewUi {
        val items = r.items + r.pendingLocal
        val rosterNames = r.remoteRoster.toMap()
        // 汇总者名：草稿缓存的规范 id 优先（首次绑定还没有状态行）
        val coordinatorName =
            service.draftCoordinator(packageId)?.let { rosterNames[it] }
                ?: db.familyCollabDao().get()?.coordinatorPersonId
                    ?.let { rosterNames[it] }
        val senderName = service.draftSender(packageId)
        // 本机待汇入改动的人物名（规范 id → 本机 id → 本机名）；
        // 本机已删的人物再从远端名册兜底，都不在才回退到规范 id
        val canonToLocal = repo.canonicalRosterSnapshot().canonicalToLocal
        val pendingNames = r.pendingLocal.map { it.personId }.distinct()
            .associateWith { canon ->
                db.personDao().getById(canonToLocal[canon] ?: canon)?.name
                    ?: rosterNames[canon]
                    ?: canon
            }
        return ReviewUi(
            loading = false,
            packageId = packageId,
            mode = r.draft.mode,
            revision = r.draft.revision,
            coordinatorName = coordinatorName,
            senderName = senderName,
            applyItems = items.filter {
                it.state == FamilyRevisionPlan.State.APPLY.name },
            decisionItems = items.filter {
                it.state == FamilyRevisionPlan.State.CONFLICT.name ||
                    it.state == FamilyRevisionPlan.State.IDENTITY.name ||
                    it.state == FamilyRevisionPlan.State.REAPPEAR.name },
            blockedItems = items.filter {
                it.state == FamilyRevisionPlan.State.BLOCKED.name },
            pendingItems = r.pendingLocal,
            pendingNames = pendingNames,
            decisions = r.decisions,
            hasLocalSelf = r.hasLocalSelf,
            remoteRoster = r.remoteRoster,
            selfChoice = r.selfCanonicalChoice,
            localOnlyMarks = r.localOnlyMarks,
            dropPending = r.dropPending,
            invalidCount = r.invalidCount,
            planToken = r.planToken,
            canSendContribution =
                db.familyCollabDao().get()?.role == "member"
        )
    }

    /** 选择保存串行锁：较旧任务不得越过较新任务写库。 */
    private val persistMutex = Mutex()
    private var persistSeq = 0

    /**
     * 把界面当前全部接收选择写入草稿（saveDecisions 整体覆盖，必须传全）。
     * 串行执行且每次取最新状态：快速连续改动时，晚完成的旧任务写的
     * 也是最新选择，不可能覆盖出新值之外的旧组合。
     *
     * [replan]=true 时（身份裁决）保存后接着串行重算：Same 并入会
     * 让计划本身长出新的字段/关系分歧，按草稿最新选择刷新当前页，
     * 用户无需退出重进即可继续核对并接收。
     */
    private fun persistChoices(replan: Boolean = false) {
        if (review.value.packageId == null) return
        viewModelScope.launch {
            persistMutex.withLock {
                val saved = review.value
                val pkg = saved.packageId ?: return@withLock
                service.saveDecisions(pkg, saved.decisions,
                    selfCanonicalChoice = saved.selfChoice,
                    dropPending = saved.dropPending,
                    localOnlyMarks = saved.localOnlyMarks,
                    seq = ++persistSeq)
                if (!replan) return@withLock
                val r = service.resumeReview(pkg)
                if (r == null) {    // 草稿已被应用/放弃
                    review.value = ReviewUi(loading = false, missing = true)
                    return@withLock
                }
                persistSeq = maxOf(persistSeq, r.choicesSeq)
                // 保存快照之后的界面新选择（锁外窗口）不得被本次重算
                // 覆盖：与快照不一致的取值视为更新的选择，留待下一次保存
                val now = review.value
                review.value = uiFromResumed(pkg, r).copy(
                    decisions = r.decisions + now.decisions.filter { (k, v) ->
                        saved.decisions[k] != v },
                    selfChoice = if (now.selfChoice != saved.selfChoice)
                        now.selfChoice else r.selfCanonicalChoice,
                    dropPending = if (now.dropPending != saved.dropPending)
                        now.dropPending else r.dropPending,
                    localOnlyMarks =
                        if (now.localOnlyMarks != saved.localOnlyMarks)
                            now.localOnlyMarks else r.localOnlyMarks)
            }
        }
    }

    /** 决策持久化（随写随存）；身份裁决附带页内重算刷新。 */
    fun setDecision(key: String, wire: String) {
        if (review.value.packageId == null) return
        val next = review.value.decisions + (key to wire)
        review.update { it.copy(decisions = next) }
        persistChoices(
            replan = FamilyRevisionPlan.IdentChoice.parse(wire) != null)
    }

    fun setSelfChoice(canon: String) {
        review.update { it.copy(selfChoice = canon) }
        persistChoices()
    }

    fun toggleLocalOnly(canon: String) {
        review.update {
            it.copy(localOnlyMarks =
                if (canon in it.localOnlyMarks) it.localOnlyMarks - canon
                else it.localOnlyMarks + canon)
        }
        persistChoices()
    }

    /** “放弃这些补充，以确认版为准”开关。 */
    fun setDropPending(drop: Boolean) {
        review.update { it.copy(dropPending = drop) }
        persistChoices()
    }

    /**
     * “全部采用收到的”：分歧字段/电话/日期/关系一键选“采用收到的”。
     * 按 5.7 安全边界，身份裁决、再现项与“将从名册移除”（rm: 键）
     * 仍需逐项确认，不参与批量。
     */
    fun acceptAllShareable() {
        val ui = review.value
        val add = ui.decisionItems
            .filter {
                it.state == FamilyRevisionPlan.State.CONFLICT.name &&
                    !it.key.startsWith("rm:") }
            .associate {
                it.key to FamilyRevisionPlan.Choice.TAKE_REMOTE.wire }
        if (add.isEmpty()) return
        val next = ui.decisions + add
        review.update { it.copy(decisions = next) }
        persistChoices()
    }

    /** 应用：内存有密码直接用；续草稿场景先弹密码框。 */
    fun applyReview() {
        val pw = reviewPassword
        if (pw == null) {
            review.update { it.copy(askPassword = true) }
        } else {
            doApply(pw)
        }
    }

    fun applyWithPassword(pw: CharArray) {
        review.update { it.copy(askPassword = false) }
        doApply(pw)
    }

    private fun doApply(pw: CharArray) {
        val ui = review.value
        val pkg = ui.packageId ?: return
        review.update { it.copy(busy = true) }
        viewModelScope.launch {
            // 先等在途保存落库，再以“当前完整选择 + 预览令牌”提交
            persistMutex.withLock { }
            try {
                service.applyReceived(
                    packageId = pkg,
                    password = pw.copyOf(),
                    decisions = ui.decisions,
                    localOnlyMarks = ui.localOnlyMarks,
                    selfCanonicalChoice =
                        if (ui.selfChoice == SELF_NONE) null else ui.selfChoice,
                    dropPending = ui.dropPending,
                    reviewedToken = ui.planToken)
                review.update { it.copy(busy = false, done = true) }
            } catch (e: CollabException) {
                review.update { it.copy(busy = false, error = e.message) }
            } catch (e: Exception) {
                review.update { it.copy(busy = false, error = "接收失败，请重试") }
            }
        }
    }

    fun discardReview() {
        val pkg = review.value.packageId
        viewModelScope.launch {
            if (pkg != null) runCatching { service.discardDraft(pkg) }
            review.value = ReviewUi(loading = false, missing = true)
        }
    }

    override fun onCleared() {
        reviewPassword?.fill(' '); reviewPassword = null
        super.onCleared()
    }
}

/**
 * 发送侧对话框与分享动作（共享与连接页、接收页“先回补充”共用）：
 * 密码 → 生成 → ACTION_SEND（FileProvider）。
 */
@Composable
fun CollabSendFlow(
    sendStep: CollabViewModel.SendStep,
    onSubmitPassword: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    onSent: () -> Unit
) {
    val context = LocalContext.current
    when (sendStep) {
        is CollabViewModel.SendStep.AskPassword ->
            io.github.xiaohaoz1.jiarenxia.ui.backup.ExportPasswordDialog(
                title = if (sendStep.kind ==
                    CollabViewModel.SendKind.PUBLISH) "发布确认版"
                else "发回补充给汇总者",
                hint = "请通过电话等其他方式把密码告诉家人",
                onSubmit = onSubmitPassword,
                onDismiss = onDismiss
            )
        CollabViewModel.SendStep.Working ->
            AlertDialog(
                onDismissRequest = {},
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("正在生成协作包…")
                    }
                },
                confirmButton = {}
            )
        is CollabViewModel.SendStep.Ready -> {
            LaunchedEffect(sendStep.file) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.packageName + ".share-provider",
                    sendStep.file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "发给家人"))
                onSent()
            }
        }
        else -> {}
    }
}

/**
 * 协作接收审查页：新包（恢复流程识别转入）或待处理草稿。
 * 列表自上而下：自动更新 → 需要确认 → 已自动保留 → 本机改动 → 我是谁。
 */
@Composable
fun CollabReviewScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CollabViewModel = hiltViewModel()
) {
    val ui by viewModel.review.collectAsStateWithLifecycle()
    val sendStep by viewModel.sendStep.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.startReview() }
    LaunchedEffect(ui.done) {
        if (ui.done) {
            Toast.makeText(context, "已接收", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    CollabReviewContent(
        ui = ui,
        onBack = onBack,
        onDecision = viewModel::setDecision,
        onSelfChoice = viewModel::setSelfChoice,
        onToggleLocalOnly = viewModel::toggleLocalOnly,
        onDropPending = viewModel::setDropPending,
        onAcceptAll = viewModel::acceptAllShareable,
        onSendContribution = viewModel::contribute,
        onApply = viewModel::applyReview,
        onDiscard = viewModel::discardReview
    )

    // “先把补充发给汇总者”：同一套密码→生成→分享流程；分享返回不
    // 改状态（系统面板返回不证明对方收到，5.6）
    CollabSendFlow(
        sendStep = sendStep,
        onSubmitPassword = viewModel::submitSendPassword,
        onDismiss = viewModel::dismissSend,
        onSent = viewModel::sendSent
    )

    if (ui.askPassword) {
        PasswordDialog(
            title = "输入协作包密码",
            onSubmit = viewModel::applyWithPassword,
            onDismiss = {
                viewModel.review.update { it.copy(askPassword = false) }
            }
        )
    }
    ui.error?.let { err ->
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text("无法接收") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = onBack) { Text("返回") }
            }
        )
    }
    if (ui.busy) {
        AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("正在接收…")
                }
            },
            confirmButton = {}
        )
    }
}

/** 协作接收页内容（无状态，截图测试可用）。 */
@Composable
fun CollabReviewContent(
    ui: CollabViewModel.ReviewUi,
    onBack: () -> Unit,
    onDecision: (String, String) -> Unit = { _, _ -> },
    onSelfChoice: (String) -> Unit = {},
    onToggleLocalOnly: (String) -> Unit = {},
    onDropPending: (Boolean) -> Unit = {},
    onAcceptAll: () -> Unit = {},
    onSendContribution: () -> Unit = {},
    onApply: () -> Unit = {},
    onDiscard: () -> Unit = {},
    selfNoneValue: String = "none"
) {
    // 题名按 5.5：确认版“X 整理的家庭确认版”、补充“来自 X 的补充”
    val title = if (ui.mode == "confirmed")
        (ui.coordinatorName ?: "家人") + " 整理的家庭确认版" +
            " · 第 " + ui.revision + " 版"
    else "来自 " + (ui.senderName ?: "家人") + " 的补充"
    // 未决项数（需要确认的裁决 + 首次绑定的“我是谁”）：固定底栏禁用提示用
    val unresolved =
        ui.decisionItems.count { !ui.decisions.containsKey(it.key) } +
            if (ui.needSelfChoice && ui.selfChoice == null) 1 else 0
    SettingsScaffold(title, onBack, bottomBar = {
        if (!ui.loading && !ui.missing) {
            io.github.xiaohaoz1.jiarenxia.ui.common.FormActionBar(
                primaryText = if (ui.dropPending) "放弃本机改动并接收"
                    else "确认接收",
                onPrimary = onApply,
                primaryEnabled = ui.ready && !ui.busy,
                busy = ui.busy,
                secondaryText = "放弃这份协作包",
                onSecondary = onDiscard,
                secondaryEnabled = !ui.busy,
                missingText = if (unresolved > 0)
                    "还有 " + unresolved + " 项需要确认" else null
            )
        }
    }) {
        if (ui.loading) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) { CircularProgressIndicator() }
            return@SettingsScaffold
        }
        if (ui.missing) {
            SettingsHint("没有待处理的协作包。")
            return@SettingsScaffold
        }

        // 包头摘要：版本 + 数量概览（新增/纠正合并在自动更新与待确认里）
        SettingsGroup(null) {
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(
                    if (ui.mode == "confirmed")
                        "第 " + ui.revision + " 版 · " +
                            (ui.coordinatorName?.let { "汇总者 " + it } ?: "")
                    else "家人的补充与纠正",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    listOfNotNull(
                        "自动更新 " + ui.applyItems.size,
                        if (ui.decisionItems.isNotEmpty())
                            "待确认 " + ui.decisionItems.size else null,
                        if (ui.pendingItems.isNotEmpty())
                            "本机改动 " + ui.pendingItems.size else null
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "核对下面的改动后再应用；你的私人记录和设置保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 主选项（5.5）：“家庭资料以这份为准”一键采用可覆盖的
                // 共享字段；身份/再现/移除仍逐项确认
                if (ui.mode == "confirmed" && ui.decisionItems.any {
                        it.state == FamilyRevisionPlan.State.CONFLICT.name &&
                            !it.key.startsWith("rm:") }) {
                    TextButton(onClick = onAcceptAll) {
                        Text("家庭资料以这份为准")
                    }
                }
            }
        }

        // 续草稿复核：资料变化作废旧选择（R5）
        if (ui.invalidCount > 0) {
            SettingsHint(
                "有 " + ui.invalidCount +
                    " 项选择因资料变化需要重新确认。")
        }

        // 自动更新
        if (ui.applyItems.isNotEmpty()) {
            SettingsGroup("自动更新（" + ui.applyItems.size + "）") {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ui.applyItems.forEachIndexed { i, it ->
                        if (i > 0) RowDivider()
                        Text(it.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // 需要确认
        if (ui.decisionItems.isNotEmpty()) {
            SettingsGroup("需要确认（" + ui.decisionItems.size + "）") {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ui.decisionItems.forEachIndexed { i, item ->
                        if (i > 0) RowDivider()
                        CollabDecisionRow(item, ui.decisions[item.key], onDecision)
                    }
                }
            }
        }

        // 已自动保留（硬规则拦下）
        if (ui.blockedItems.isNotEmpty()) {
            SettingsGroup("已自动保留本机") {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ui.blockedItems.forEachIndexed { i, it ->
                        if (i > 0) RowDivider()
                        Text(it.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "这条不能采用收到的内容，已自动保留本机。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 本机改动未汇入
        if (ui.pendingItems.isNotEmpty()) {
            SettingsGroup("你的本机改动（" + ui.pendingItems.size + "）") {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ui.pendingItems.forEachIndexed { i, it ->
                        if (i > 0) RowDivider()
                        Text(it.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    // 确认版的未汇入保护（5.6）：可先把补充发回，或明确
                    // 放弃改动以确认版为准；退出核实走返回键保留草稿
                    if (ui.mode == "confirmed") {
                        if (!ui.dropPending && ui.canSendContribution) {
                            RowDivider()
                            TextButton(onClick = onSendContribution) {
                                Text("先把补充发给汇总者")
                            }
                        }
                        RowDivider()
                        ChoiceRadio(
                            selected = !ui.dropPending,
                            label = "保留这些改动（以后再发给汇总者）",
                            onClick = { onDropPending(false) })
                        ChoiceRadio(
                            selected = ui.dropPending,
                            label = "放弃这些改动，以确认版为准",
                            onClick = { onDropPending(true) })
                    }
                    if (!ui.dropPending) {
                        RowDivider()
                        ui.pendingItems.map { it.personId }.distinct()
                            .forEach { canon ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable(role = Role.Checkbox) {
                                            onToggleLocalOnly(canon)
                                        }
                                ) {
                                    Checkbox(
                                        checked = canon in ui.localOnlyMarks,
                                        onCheckedChange = null
                                    )
                                    Text(
                                        "「" + (ui.pendingNames[canon] ?: canon) +
                                            "」改为仅本机保留",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                    }
                }
            }
            SettingsHint(
                if (ui.dropPending)
                    "应用后这些改动会被收到的内容替换；本机新增的家人将移出名册" +
                        "（有私人记录的转为仅本机保留）。"
                else "勾选“仅本机保留”后，这个人不再出现在以后的共享与确认版里。")
        }

        // 我是谁（首次绑定）
        if (ui.needSelfChoice) {
            SettingsGroup("这份资料里的哪位是你？") {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ui.remoteRoster.forEachIndexed { i, (canon, name) ->
                        if (i > 0) RowDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable(role = Role.RadioButton) {
                                    onSelfChoice(canon)
                                }
                        ) {
                            RadioButton(
                                selected = ui.selfChoice == canon,
                                onClick = null
                            )
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    RowDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(role = Role.RadioButton) {
                                onSelfChoice(selfNoneValue)
                            }
                    ) {
                        RadioButton(
                            selected = ui.selfChoice == selfNoneValue,
                            onClick = null
                        )
                        Text(
                            "都不包括我",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 单条需要裁决的改动：文案 + 选项组。 */
@Composable
private fun CollabDecisionRow(
    item: FamilyCollaborationService.CachedItem,
    decided: String?,
    onDecision: (String, String) -> Unit
) {
    Column {
        Text(item.text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        when (item.state) {
            FamilyRevisionPlan.State.IDENTITY.name -> {
                item.candidates.forEach { (id, name, via) ->
                    ChoiceRadio(
                        selected = decided == "same:" + id,
                        label = "是我家的「" + name + "」（" + via + "）",
                        onClick = { onDecision(item.key, "same:" + id) }
                    )
                }
                ChoiceRadio(
                    selected = decided == "new",
                    label = "都不是，按新家人加入",
                    onClick = { onDecision(item.key, "new") }
                )
            }
            FamilyRevisionPlan.State.REAPPEAR.name -> {
                ChoiceRadio(
                    selected = decided == FamilyRevisionPlan.Choice.TAKE_REMOTE.wire,
                    label = "重新加入",
                    onClick = {
                        onDecision(item.key,
                            FamilyRevisionPlan.Choice.TAKE_REMOTE.wire)
                    }
                )
                ChoiceRadio(
                    selected = decided == FamilyRevisionPlan.Choice.KEEP_LOCAL.wire,
                    label = "不加入",
                    onClick = {
                        onDecision(item.key,
                            FamilyRevisionPlan.Choice.KEEP_LOCAL.wire)
                    }
                )
            }
            else -> {
                ChoiceRadio(
                    selected = decided == FamilyRevisionPlan.Choice.TAKE_REMOTE.wire,
                    label = "采用收到的",
                    onClick = {
                        onDecision(item.key,
                            FamilyRevisionPlan.Choice.TAKE_REMOTE.wire)
                    }
                )
                ChoiceRadio(
                    selected = decided == FamilyRevisionPlan.Choice.KEEP_LOCAL.wire,
                    label = "保留本机",
                    onClick = {
                        onDecision(item.key,
                            FamilyRevisionPlan.Choice.KEEP_LOCAL.wire)
                    }
                )
            }
        }
    }
}

@Composable
private fun ChoiceRadio(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
