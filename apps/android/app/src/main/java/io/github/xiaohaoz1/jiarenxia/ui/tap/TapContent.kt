package io.github.xiaohaoz1.jiarenxia.ui.tap

import androidx.compose.foundation.layout.navigationBarsPadding
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
import io.github.xiaohaoz1.jiarenxia.ui.common.PersonPickerDialog
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.PageHeader
import io.github.xiaohaoz1.jiarenxia.ui.designsystem.JrxCard
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch

/**
 * 碰一碰页面：两台手机选相反的连接方式（主动感应/等待感应，与性别无关），
 * 贴上即自动协商——未配对结为夫妻合并两家；已配对纪念仪式；
 * 已配对+更新资料出预览分别确认；任一方预演则双方都不写。
 */
@Composable
fun TapScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    /** NFC 不可用/两方向失败时的备用路径：家人共享导入。 */
    onShare: () -> Unit = {},
    viewModel: TapViewModel = hiltViewModel()
) {
    val st by viewModel.ui.collectAsState()
    val ctx = LocalContext.current
    val activity = ctx as? ComponentActivity
    val adapter = remember(ctx) { NfcAdapter.getDefaultAdapter(ctx) }
    val hce = remember(ctx) {
        ctx.packageManager.hasSystemFeature(
            PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current

    // 每次回到前台都重新检查 NFC（覆盖从系统 NFC 设置页返回的场景）
    DisposableEffect(lifecycleOwner, adapter) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                viewModel.checkNfc(
                    adapter != null, adapter?.isEnabled == true, hce)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 页面期间屏幕常亮（仪式展示不能被息屏打断）
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // 合心展示：隐藏系统栏 + 最亮，离开 SHOW 即恢复
    val showing = st.stage == TapUiState.Stage.SHOW
    DisposableEffect(activity, view, showing) {
        val window = activity?.window
        val prevBrightness = window?.attributes?.screenBrightness
            ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (showing && window != null) {
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            window.attributes = window.attributes.also {
                it.screenBrightness = 1f
            }
        }
        onDispose {
            if (showing && window != null) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
                window.attributes = window.attributes.also {
                    it.screenBrightness = prevBrightness
                }
            }
        }
    }

    // 阶段提示震动：贴上连上震两下，交换完成长震一下
    LaunchedEffect(st.stage) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() != true) return@LaunchedEffect
        when (st.stage) {
            TapUiState.Stage.EXCHANGING -> vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 60, 120, 60), -1))
            TapUiState.Stage.SHOW -> vibrator.vibrate(
                VibrationEffect.createOneShot(
                    300, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> Unit
        }
    }

    // 卡片端：整个页面期间应答（含 SHOW：重贴的读卡端可以收尾）；
    // 读卡端：等待/确认/交换/预览/展示/错误页在前台时开读卡模式。
    // 读卡端在合心展示时也保持读卡模式：否则两机仍贴着时系统会把对方的
    // HCE 当普通标签分发，弹“无支持该卡片的应用”。
    // 连接方式由用户选择（与性别无关），等待页可“交换连接方式”。
    val reader = st.isReader
    // 选定连接方式后才开 NFC（卡片应答/读卡模式）；互加亲人的
    // 圈选页也不开——范围未定稿前贴上会按全量导出
    val nfcActive = st.roleReady && st.roleChosen &&
        st.stage != TapUiState.Stage.CHECKING &&
        st.stage != TapUiState.Stage.NO_NFC &&
        st.stage != TapUiState.Stage.NFC_OFF &&
        st.stage != TapUiState.Stage.REL_SCOPE &&
        st.stage != TapUiState.Stage.CHOOSE_ROLE
    DisposableEffect(nfcActive, reader, lifecycleOwner, activity, adapter) {
        if (!nfcActive || activity == null || adapter == null) {
            return@DisposableEffect onDispose {}
        }
        if (!reader) {
            // 不设首选服务：小米等机型感应到读卡器会把钱包/交通卡页面弹到
            // 前台，本应用一离开前台系统就撤销首选服务并解绑正在通信的
            // TapHostService，连接当场断开（真机日志 HostEmulationManager
            // “Unbinding from service …TapHostService”）；本应用 AID 唯一，
            // 不设首选也会路由到 TapHostService。卡片在整个页面期间保持应答，
            // 不随 pause 停用。
            viewModel.activateCard()
            return@DisposableEffect onDispose { viewModel.deactivateCard() }
        }
        var on = false
        fun start() {
            if (on) return
            on = true
            adapter.enableReaderMode(
                activity, { tag -> viewModel.onTagDiscovered(tag) },
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null
            )
        }
        fun stop() {
            if (!on) return
            on = false
            adapter.disableReaderMode(activity)
        }
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                // 已 RESUMED 时 addObserver 会立即补发 ON_RESUME
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }

    TapContent(
        st = st,
        onRehearsal = viewModel::setRehearsal,
        onRetry = viewModel::retry,
        onSwap = viewModel::swapHalf,
        onToggleRole = viewModel::toggleRole,
        onChooseRole = viewModel::chooseRole,
        onWantUpdate = viewModel::setWantUpdate,
        onConfirm = viewModel::confirmPending,
        onDecline = viewModel::declinePending,
        onToggleCandidate = viewModel::toggleCandidate,
        onConfirmUpdate = viewModel::confirmUpdate,
        onCancelUpdate = viewModel::cancelUpdate,
        onReplay = viewModel::replay,
        onShare = onShare,
        onDone = onDone,
        onBack = onBack,
        onToggleScope = viewModel::toggleScopePerson,
        onScopeBranch = viewModel::applyScopeBranch,
        onConfirmScope = viewModel::confirmScope,
        onScopeWarnProceed = viewModel::scopeWarnProceed,
        onScopeWarnBack = { viewModel.scopeWarnDismiss() },
        onBackToRole = viewModel::backToRole,
        onAnswerPeerIs = viewModel::answerPeerIs,
        onAnswerPeerNew = viewModel::answerPeerNew,
        onSkipRelatives = viewModel::skipRelatives,
        onToggleRelPick = viewModel::toggleRelPick,
        onConfirmRelPick = viewModel::confirmRelPick,
        onRelPickProceed = viewModel::relPickProceed,
        onRelPickBack = viewModel::relPickWarnDismiss,
        onRelToggleCandidate = viewModel::relToggleCandidate,
        onRelChooseConflict = viewModel::relChooseConflict,
        onRelPickAsLocal = viewModel::relPickAsLocal,
        onRelUnpickAsLocal = viewModel::relUnpickAsLocal,
        onConfirmRelImport = viewModel::confirmRelImport,
        onFinishRel = viewModel::finishRel,
        onBackToRelPick = viewModel::backToRelPick
    )
}

/** 无状态主体（截图测试直接喂状态）。 */
@Composable
fun TapContent(
    st: TapUiState,
    onRehearsal: (Boolean) -> Unit = {},
    onRetry: () -> Unit = {},
    onSwap: () -> Unit = {},
    onToggleRole: () -> Unit = {},
    onChooseRole: (Boolean) -> Unit = {},
    onWantUpdate: (Boolean) -> Unit = {},
    onConfirm: () -> Unit = {},
    onDecline: () -> Unit = {},
    onToggleCandidate: (String) -> Unit = {},
    onConfirmUpdate: () -> Unit = {},
    onCancelUpdate: () -> Unit = {},
    onReplay: () -> Unit = {},
    onShare: () -> Unit = {},
    onDone: () -> Unit = {},
    onBack: () -> Unit = {},
    // ---- 互加亲人 ----
    onToggleScope: (String) -> Unit = {},
    onScopeBranch: (io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch) -> Unit = {},
    onConfirmScope: () -> Unit = {},
    onScopeWarnProceed: () -> Unit = {},
    onScopeWarnBack: () -> Unit = {},
    onBackToRole: () -> Unit = {},
    onAnswerPeerIs: (String) -> Unit = {},
    onAnswerPeerNew: (String, io.github.xiaohaoz1.jiarenxia.core.model.RelationRole) -> Unit = { _, _ -> },
    onSkipRelatives: () -> Unit = {},
    onToggleRelPick: (String) -> Unit = {},
    onConfirmRelPick: () -> Unit = {},
    onRelPickProceed: () -> Unit = {},
    onRelPickBack: () -> Unit = {},
    onRelToggleCandidate: (String) -> Unit = {},
    onRelChooseConflict: (io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge.ConflictKey, Boolean) -> Unit = { _, _ -> },
    onRelPickAsLocal: (String, String) -> Unit = { _, _ -> },
    onRelUnpickAsLocal: (String) -> Unit = {},
    onConfirmRelImport: () -> Unit = {},
    onFinishRel: () -> Unit = {},
    onBackToRelPick: () -> Unit = {}
) {
    val ctx = LocalContext.current

    if (st.stage == TapUiState.Stage.SHOW) {
        // 全屏合心展示（无页头）；纪念模式走纪念文案
        HeartShow(
            selfName = st.selfName,
            peerName = st.peerName,
            leftHalf = st.leftHalf,
            labels = st.newFamily,
            moreCount = st.moreFamilyCount,
            rehearsal = st.sessionRehearsal,
            clockOffsetMs = st.clockOffsetMs,
            startAt = st.showStartAt ?: (
                System.currentTimeMillis() + st.clockOffsetMs +
                    TapViewModel.SHOW_DELAY_MS),
            memorialLine = st.memorialLine,
            weddingWords = st.weddingWords,
            anniversary = st.memorialAnniversary,
            dateHint = st.memorialHint,
            onReplay = onReplay,
            onSwap = onSwap,
            onDone = onDone,
            onRetry = onRetry,
            onBack = onBack
        )
        return
    }

    // 普通页面避开系统导航栏，防止底部备用入口被三键导航拦截。
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        PageHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("返回") }
                Text(
                    when {
                        st.tapKind == io.github.xiaohaoz1.jiarenxia.nfc
                            .TapProtocol.KIND_RELATIVES ||
                            st.stage == TapUiState.Stage.REL_IDENTIFY ||
                            st.stage == TapUiState.Stage.REL_PICK ||
                            st.stage == TapUiState.Stage.REL_PREVIEW ||
                            st.stage == TapUiState.Stage.REL_DONE ->
                            "碰一碰 · 互加亲人"
                        st.paired -> "碰一碰 · 纪念"
                        else -> "碰一碰结为夫妻"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f))
            }
        }
        if (st.stage == TapUiState.Stage.CHOOSE_ROLE) {
            // 角色选择页不进滚动列：整页纵向分布，避免内容挤在顶部
            ChooseRolePage(
                st.tapKind == io.github.xiaohaoz1.jiarenxia.nfc
                    .TapProtocol.KIND_RELATIVES,
                onChooseRole, onShare)
            return@Column
        }
        if (st.stage == TapUiState.Stage.REL_PREVIEW) {
            // 合并预览自带滚动容器，不能嵌进下方滚动列
            st.relPlan?.let { plan ->
                io.github.xiaohaoz1.jiarenxia.ui.share.MergePreviewContent(
                    sharerName = st.peerName,
                    plan = plan,
                    newTitles = st.relNewTitles,
                    manualNames = st.relManualNames,
                    localItems = st.relLocalItems,
                    localGenders = st.relLocalGenders,
                    newAliases = st.relNewAliases,
                    canReplace = false,
                    busy = st.relBusy,
                    onToggleCandidate = onRelToggleCandidate,
                    onPickAsLocal = onRelPickAsLocal,
                    onUnpickAsLocal = onRelUnpickAsLocal,
                    onChooseConflict = onRelChooseConflict,
                    onConfirm = onConfirmRelImport,
                    onFinish = onFinishRel,
                    onReplace = {},
                    onBack = onBackToRelPick)
            }
            return@Column
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            when (st.stage) {
                TapUiState.Stage.CHECKING -> JrxCard(
                    Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("正在检查 NFC…",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp))
                }
                TapUiState.Stage.NO_NFC -> JrxCard(
                    Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("这部手机不支持 NFC（或不支持卡片模拟）",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                    Text("可以改用“家人共享”传资料",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
                    Button(
                        onClick = onShare,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 4.dp, 16.dp, 16.dp)
                    ) { Text("改用家人共享") }
                }
                TapUiState.Stage.NFC_OFF -> JrxCard(
                    Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("手机的 NFC 没有打开",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
                    Button(
                        onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_NFC_SETTINGS))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 0.dp, 16.dp, 8.dp)
                    ) { Text("去打开 NFC") }
                    TextButton(
                        onClick = onShare,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 0.dp, 16.dp, 12.dp)
                    ) { Text("开不了？改用家人共享") }
                }
                TapUiState.Stage.CHOOSE_ROLE -> Unit // 上方单独整页渲染
                TapUiState.Stage.REL_SCOPE -> RelScopePage(
                    st, onToggleScope, onScopeBranch, onConfirmScope,
                    onScopeWarnProceed, onScopeWarnBack, onBackToRole)
                TapUiState.Stage.WAITING -> WaitingPage(
                    st, onToggleRole, onRehearsal, onWantUpdate, onShare)
                TapUiState.Stage.CONFIRMING -> ConfirmPage(
                    st, onConfirm, onDecline, onToggleCandidate)
                TapUiState.Stage.EXCHANGING -> ExchangingCard(st)
                TapUiState.Stage.UPDATE_PREVIEW -> UpdatePreviewPage(
                    st, onConfirmUpdate, onCancelUpdate, onToggleCandidate)
                TapUiState.Stage.REL_IDENTIFY -> RelIdentifyPage(
                    st, onAnswerPeerIs, onAnswerPeerNew, onSkipRelatives)
                TapUiState.Stage.REL_PICK -> RelPickPage(
                    st, onToggleRelPick, onConfirmRelPick,
                    onRelPickProceed, onRelPickBack)
                TapUiState.Stage.REL_PREVIEW -> Unit // 上方单独渲染（自带滚动）
                TapUiState.Stage.REL_DONE -> RelDonePage(st, onDone)
                TapUiState.Stage.ERROR -> JrxCard(
                    Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        st.error ?: "碰一碰失败",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    // 缺“我”（roleReady 之前）没法重试，只能先去建人
                    if (st.roleReady) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 0.dp, 16.dp, 8.dp)
                        ) { Text("重试") }
                        TextButton(
                            onClick = onShare,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 0.dp, 16.dp, 12.dp)
                        ) { Text("还是不行？改用家人共享") }
                    }
                }
                TapUiState.Stage.SHOW -> Unit // 上方已渲染全屏合心
            }
        }
    }
}

/** 初次选择连接方式：两部手机必须选相反的；整页纵向分布不留大白。 */
@Composable
private fun ColumnScope.ChooseRolePage(
    relatives: Boolean,
    onChooseRole: (Boolean) -> Unit,
    onShare: () -> Unit
) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上半：示意 + 说明（超大字号时可滚）；下半：两张角色卡与备用入口常驻底部
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TwoPhonesIllustration(
                leftReader = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .semantics { contentDescription = "两部手机靠近示意" }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "先选这部手机的连接方式",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "两部手机都从「" +
                    (if (relatives) "碰一碰互加亲人" else "碰一碰结为夫妻") +
                    "」进来，选相反的：一部“主动感应”，另一部“等待感应”。" +
                    "选好后把两部手机背靠背贴在一起。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        JrxCard(
            Modifier.fillMaxWidth(),
            onClick = { onChooseRole(true) }
        ) {
            Text(
                "这部手机主动感应",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "像刷卡机：用它去贴对方手机，主动读取对方资料",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        JrxCard(
            Modifier.fillMaxWidth(),
            onClick = { onChooseRole(false) }
        ) {
            Text(
                "这部手机等待感应",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "像一张卡：停在这个页面，让对方手机来贴它",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        TextButton(onClick = onShare) {
            Text("这部手机没有 NFC？改用家人共享",
                style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** 等待页：双机示意 + 半心呼吸 + 主文案 + 状态与操作。 */
@Composable
private fun WaitingPage(
    st: TapUiState,
    onToggleRole: () -> Unit,
    onRehearsal: (Boolean) -> Unit,
    onWantUpdate: (Boolean) -> Unit,
    onShare: () -> Unit
) {
    var helpOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 互加亲人用途：标题/说明按亲属交换讲，不出现配偶与纪念语义
        val relatives = st.tapKind == TapProtocol.KIND_RELATIVES
        val waitTitle = when {
            relatives -> "靠近一点，互加亲人"
            st.paired -> "和你，再心动一次"
            else -> "靠近一点，成为一家人"
        }
        TwoPhonesIllustration(
            leftReader = st.isReader,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .semantics {
                    contentDescription = "两部手机靠近示意"
                }
        )
        Spacer(Modifier.height(12.dp))
        Text(
            waitTitle,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                contentDescription = waitTitle
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                relatives ->
                    "两部手机都打开这个页面，背靠背贴在一起；贴上后交换" +
                        "各自圈选的家人资料，传完就可以分开，各自挑要加的人"
                st.paired ->
                    "两部手机都打开这个页面，背靠背贴在一起，播放纪念仪式"
                else ->
                    "两部手机都打开这个页面，背靠背贴在一起；" +
                        "贴上后交换家人资料、结为一家"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 断线重贴 / 对方取消 / 对方确认中等提示（TalkBack 可读）
        st.notice?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        // 配偶配对/配偶校验提示只属于结为夫妻用途
        if (!relatives) {
            if (st.paired) {
                Text(
                    "已和" + (st.selfSpouseName ?: "对方") +
                        "的手机配对；这次只放仪式，不会改家人资料",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                st.selfSpouseName?.let {
                    Text(
                        "你已录入配偶 " + it + "，只能与 " + it + " 碰一碰",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        JrxCard(Modifier.fillMaxWidth()) {
            // 底部状态行：本机连接方式 + 交换按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 12.dp, 16.dp, 0.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (st.isReader) "本机：主动感应中" else "本机：等待感应",
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        "另一部手机要停在相反方式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(
                onClick = onToggleRole,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp, 16.dp, 0.dp)
            ) { Text("交换连接方式", style = MaterialTheme.typography.titleMedium) }
            Text(
                "点了之后另一部手机也要切换到相反方式，再贴一次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 8.dp)
            )
            if (st.paired && !relatives) {
                // 已配对：次级入口“更新家人资料”（碰后出预览再确认）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 0.dp, 16.dp, 0.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("更新家人资料",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "碰一下交换彼此新增的家人；双方各自预览后才写入",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = st.wantUpdate,
                        onCheckedChange = onWantUpdate)
                }
            }
            // 预演模式只属于结为夫妻（互加亲人本来就要本机确认后才写入）
            if (!relatives) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 4.dp, 16.dp, 0.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("预演模式",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "流程和动画一样，但不写入家人、不建立配对；" +
                                "任一部手机开了预演，两部都按预演走",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = st.rehearsal,
                        onCheckedChange = onRehearsal)
                }
            }
            // 连接说明/技术细节按需展开
            TextButton(
                onClick = { helpOpen = !helpOpen },
                modifier = Modifier.padding(16.dp, 0.dp)
            ) { Text(if (helpOpen) "收起说明" else "贴不上？连接说明") }
            if (helpOpen) {
                Column(Modifier.padding(16.dp, 0.dp, 16.dp, 12.dp)) {
                    Text(
                        "两部手机都打开本页并解锁；确认一部是“主动感应”、" +
                            "另一部是“等待感应”（可点上面按钮交换）。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "等待感应的手机若弹出钱包/交通卡，保持贴着即可；" +
                            "建议领证前在钱包设置里关闭自动弹卡。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                    Text(
                        "两部手机需安装同一版本家人侠；实在贴不上可改用" +
                            "“家人共享”。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
            TextButton(
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 0.dp, 16.dp, 12.dp)
            ) { Text("改用家人共享（不用 NFC 也能合并两家）") }
        }
    }
}

/** 等待本机确认页：同名配偶候选 / 配对恢复 / 同一人候选勾选。 */
@Composable
private fun ConfirmPage(
    st: TapUiState,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onToggleCandidate: (String) -> Unit = {}
) {
    JrxCard(Modifier.fillMaxWidth().padding(16.dp)) {
        when (st.confirm) {
            TapUiState.Confirm.SPOUSE_CANDIDATE -> {
                Text(
                    "对方「" + st.confirmName + "」就是你已录入的配偶吗？",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                Text(
                    "确认后两部手机正式配对：这次碰面补齐对方家人资料，" +
                        "以后再碰只放纪念仪式、不重复导入。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 12.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 0.dp, 16.dp, 8.dp)
                ) { Text("是，就是我的配偶",
                    style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 0.dp, 16.dp, 16.dp)
                ) { Text("不是",
                    style = MaterialTheme.typography.titleMedium) }
            }
            TapUiState.Confirm.PAIR_MISMATCH -> {
                Text(
                    if (st.mismatchDifferentIds)
                        "两台手机的配对记录不一致"
                    else "一部手机记着你们是夫妻，另一部不记得了",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                Text(
                    "若对方确实是你的配偶，可以重新配对：会补齐对方家人资料；" +
                        "不是就取消。两边都要确认才继续。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 12.dp))
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 0.dp, 16.dp, 8.dp)
                ) { Text("是配偶，重新配对并合并",
                    style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 0.dp, 16.dp, 16.dp)
                ) { Text("取消",
                    style = MaterialTheme.typography.titleMedium) }
            }
            TapUiState.Confirm.FAMILY_CANDIDATES -> {
                Text(
                    "这几位是同一个人吗？",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                Text(
                    "对方家人的姓名和位置与已录入的家人一样。" +
                        "勾选的将合并成一个人；不勾的按新家人加入，" +
                        "以后还可手动整理。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
                CandidateList(st, onToggleCandidate)
                val blockingUnchecked = st.candidates.any {
                    it.blocking && it.remoteId !in st.candidatesSelected }
                if (blockingUnchecked) {
                    Text(
                        "「对方录入的配偶是你本人」未勾选时将取消本次合并，" +
                            "否则会在家人里多出一个你。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 0.dp))
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 12.dp, 16.dp, 8.dp)
                ) { Text("确认勾选并合并",
                    style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 0.dp, 16.dp, 16.dp)
                ) {
                    Text(
                        if (st.candidates.any { it.blocking }) "取消合并"
                        else "都不是同一个人，按新家人加入",
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            else -> Unit
        }
    }
}

/**
 * 同一人候选勾选列表（确认页/更新预览共用）。
 * 勾选的 remoteId 将并入“同一个人”映射。
 */
@Composable
private fun CandidateList(
    st: TapUiState,
    onToggle: (String) -> Unit
) {
    for (c in st.candidates) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(c.remoteId) }
                .padding(16.dp, 4.dp, 16.dp, 4.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            Checkbox(
                checked = c.remoteId in st.candidatesSelected,
                onCheckedChange = null // 行整体可点
            )
            Column(Modifier.padding(start = 8.dp)) {
                Text(
                    c.name + " — " + c.desc,
                    style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (c.blocking)
                        "不确认将无法合并（不然会多出一个“你”）"
                    else "勾上表示同一个人；不勾将作为新家人加入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (c.blocking) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 交换中卡片：按模式给文案；对方在确认时提示等待。 */
@Composable
private fun ExchangingCard(st: TapUiState) {
    JrxCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            when (st.sessionMode) {
                TapUiState.SessionMode.MEMORIAL -> "正在汇合两颗心…"
                TapUiState.SessionMode.RELATIVES ->
                    "正在互发圈选的家人资料…"
                else -> "正在交换家人资料…"
            },
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp)
        )
        if (st.peerName.isNotBlank()) {
            Text(
                "对方：" + st.peerName + "（" + st.peerFamilyCount +
                    " 位家人）",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp, 0.dp)
            )
        }
        Text(
            "请保持两部手机贴在一起",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 4.dp)
        )
        if (st.peerConfirming) {
            Text(
                "对方正在确认，稍等",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp)
            )
        }
        if (st.sessionRehearsal) {
            Text(
                "预演中，不会写入",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 16.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 资料更新预览页：列新增/补齐/冲突，确认后双方各自写入。 */
@Composable
private fun UpdatePreviewPage(
    st: TapUiState,
    onConfirmUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onToggleCandidate: (String) -> Unit = {}
) {
    JrxCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "家人资料更新预览",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        val allEmpty = st.updateNew == 0 && st.updateNewRels == 0 &&
            st.updateNewPhones == 0 && st.updateNewDates == 0
        if (allEmpty) {
            Text(
                "家人资料已是最新",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp))
        } else {
            Text(
                "将新增 " + st.updateNew + " 位家人、" +
                    st.updateNewRels + " 条关系、" +
                    st.updateNewPhones + " 个号码、" +
                    st.updateNewDates + " 个日期；已对应 " +
                    st.updateMatched + " 位家人",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp))
        }
        if (st.candidates.isNotEmpty()) {
            Text(
                "可能是同一个人（勾选后才并入）：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 0.dp))
            CandidateList(st, onToggleCandidate)
        }
        if (st.updateConflicts.isNotEmpty()) {
            Text(
                "两边不一致（本机保留原样）：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp, 4.dp, 16.dp, 0.dp))
            for (c in st.updateConflicts) {
                Text(
                    "· " + c,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp, 2.dp, 16.dp, 0.dp))
            }
        }
        Text(
            "只会补充新资料，不会改动或删除本机已有内容；两边分别确认后才写入。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 8.dp))
        Button(
            onClick = onConfirmUpdate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 0.dp, 16.dp, 8.dp)
        ) { Text("确认更新（本机）",
            style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            onClick = onCancelUpdate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 0.dp, 16.dp, 16.dp)
        ) { Text("取消", style = MaterialTheme.typography.titleMedium) }
    }
}

/** 互加亲人·圈选页：贴之前选“分享给对方的家人”（“我”必选）。 */
@Composable
private fun RelScopePage(
    st: TapUiState,
    onToggle: (String) -> Unit,
    onBranch: (GraphBranch) -> Unit,
    onConfirm: () -> Unit,
    onWarnProceed: () -> Unit,
    onWarnBack: () -> Unit,
    onBackToRole: () -> Unit
) {
    // 断开提醒：勾选的人经所选关系连不到“我”
    st.scopeWarn?.let { warn ->
        val names = st.scopeItems.filter { it.personId in warn }
            .joinToString("、") { it.name }
        AlertDialog(
            onDismissRequest = onWarnBack,
            title = { Text("有家人会连不进分享范围") },
            text = {
                Text(names + "：经勾选的关系连不到你（中间家人没有勾选）。" +
                    "对方收到时他们会是没有关联的单独人物。")
            },
            confirmButton = {
                TextButton(onClick = onWarnProceed) { Text("仍要分享") }
            },
            dismissButton = {
                TextButton(onClick = onWarnBack) { Text("返回补选") }
            })
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "分享给对方的家人",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "碰一下后对方只能从这些人里挑。默认全选，可按分支先圈。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        // 分支预设
        if (st.scopeBranches.size > 1) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                st.scopeBranches.forEach { b ->
                    FilterChip(
                        selected = st.scopeBranch == b,
                        onClick = { onBranch(b) },
                        label = { Text(b.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        JrxCard(Modifier.fillMaxWidth()) {
            st.scopeItems.forEach { item ->
                val isSelf = item.overview?.person?.isSelf == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSelf) {
                            onToggle(item.personId)
                        }
                        .padding(16.dp, 4.dp)
                        .semantics(mergeDescendants = true) {}
                ) {
                    Checkbox(
                        checked = item.personId in st.scopeSelected,
                        enabled = !isSelf,
                        onCheckedChange = null)
                    Text(
                        if (isSelf) item.label + "（必选）" else item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Text(
            "已选 " + st.scopeSelected.size + " 位家人",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
        Button(
            onClick = onConfirm,
            enabled = st.scopeSelected.size > 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) { Text("选好了，去贴", style = MaterialTheme.typography.titleMedium) }
        TextButton(
            onClick = onBackToRole,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) { Text("返回重选连接方式") }
    }
}

/**
 * 互加亲人·“他是谁”页：对方本人对上我家已有的人，或声明关系新建。
 */
@Composable
private fun RelIdentifyPage(
    st: TapUiState,
    onAnswerIs: (String) -> Unit,
    onAnswerNew: (String, RelationRole) -> Unit,
    onSkip: () -> Unit
) {
    var pickExisting by remember { mutableStateOf(false) }
    var newMode by remember { mutableStateOf(false) }
    var anchorPick by remember { mutableStateOf(false) }
    var anchorId by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf<RelationRole?>(null) }
    // 关系选项按对方本人生理性别过滤（他是男的就没有“女儿/妻子”）
    val roles = RelationRole.entries.filter { it.gender == st.relPeerSelfGender }

    if (pickExisting) {
        // 只给同性别、且不是“我”的本机人物（对方本人不可能是你自己）
        PersonPickerDialog(
            title = "「" + st.relPeerSelfName + "」是你家的哪位",
            items = st.relLocalItems.filter {
                st.relLocalGenders[it.personId] == st.relPeerSelfGender &&
                    it.personId != st.relLocalSelfId
            },
            selectedId = null,
            onPick = { onAnswerIs(it.personId) },
            onDismiss = { pickExisting = false })
    }
    if (anchorPick) {
        PersonPickerDialog(
            title = "他是谁的" + (role?.label ?: "亲人"),
            items = st.relLocalItems,
            selectedId = anchorId,
            onPick = { anchorId = it.personId },
            onDismiss = { anchorPick = false })
    }

    JrxCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "他是你家的谁？",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        Text(
            "对方「" + st.relPeerSelfName + "」把家人资料发给了你。" +
                "先说他对应你家里的谁，再挑要加进来的人。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 8.dp))
        st.relError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp))
        }
        Button(
            onClick = { pickExisting = true; newMode = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 4.dp, 16.dp, 0.dp)
        ) { Text("他是我家里已经有的人",
            style = MaterialTheme.typography.titleMedium) }
        OutlinedButton(
            onClick = { newMode = !newMode },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp, 16.dp, 0.dp)
        ) { Text("我家里还没有他",
            style = MaterialTheme.typography.titleMedium) }
        if (newMode) {
            Text(
                "他是我家里…的…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp))
            OutlinedButton(
                onClick = { anchorPick = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 4.dp, 16.dp, 0.dp)
            ) {
                Text(anchorId?.let { id ->
                    st.relLocalItems.firstOrNull { it.personId == id }
                        ?.label ?: "选择是我家的谁"
                } ?: "选择是我家的谁",
                    style = MaterialTheme.typography.titleMedium)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp, 4.dp, 16.dp, 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                roles.forEach { r ->
                    FilterChip(
                        selected = role == r,
                        onClick = { role = r },
                        label = { Text(r.label) })
                }
            }
            Button(
                onClick = {
                    val a = anchorId; val r = role
                    if (a != null && r != null) onAnswerNew(a, r)
                },
                enabled = anchorId != null && role != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp, 8.dp, 16.dp, 0.dp)
            ) { Text("就是他，继续",
                style = MaterialTheme.typography.titleMedium) }
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp, 16.dp, 12.dp)
        ) { Text("暂不添加") }
    }
}

/** 互加亲人·选人页：勾选要加进我家的对方人物（对方本人恒选）。 */
@Composable
private fun RelPickPage(
    st: TapUiState,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onProceed: () -> Unit,
    onPickBack: () -> Unit
) {
    st.relPickWarn?.let { warn ->
        val names = st.relPeerItems.filter { it.personId in warn }
            .joinToString("、") { it.name }
        AlertDialog(
            onDismissRequest = onPickBack,
            title = { Text("有人会连不进你家") },
            text = {
                Text(names + "：经对方的关系连不到对方本人（中间人没有勾选），" +
                    "加进来会是和你没有关联的单独人物。")
            },
            confirmButton = {
                TextButton(onClick = onProceed) { Text("仍然添加") }
            },
            dismissButton = {
                TextButton(onClick = onPickBack) { Text("返回补选") }
            })
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "把对方的家人加进来",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "「" + st.relPeerSelfName + "」带来 " + st.relPeerItems.size +
                " 人。勾选要加进你家的；没勾的不进你的手机。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
        JrxCard(Modifier.fillMaxWidth()) {
            st.relPeerItems.forEach { item ->
                val isSelf = item.personId == st.relPeerSelfId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSelf) {
                            onToggle(item.personId)
                        }
                        .padding(16.dp, 4.dp)
                        .semantics(mergeDescendants = true) {}
                ) {
                    Checkbox(
                        checked = item.personId in st.relPickSelected,
                        enabled = !isSelf,
                        onCheckedChange = null)
                    Text(
                        if (isSelf) item.label + "（必选）" else item.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        Button(
            onClick = onConfirm,
            enabled = st.relPickSelected.isNotEmpty() && !st.relBusy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) { Text("下一步：看合并预览",
            style = MaterialTheme.typography.titleMedium) }
    }
}

/** 互加亲人·完成页：本机确认即写完，不等对方。 */
@Composable
private fun RelDonePage(st: TapUiState, onDone: () -> Unit) {
    JrxCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "互加亲人完成",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
        Text(
            st.relDoneText.ifBlank { "已合并对方带来的资料" },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp))
        Text(
            "对方那边有同样的资料，他自己决定加谁——你们加的人可以不一样。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 12.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 0.dp, 16.dp, 16.dp)
        ) { Text("完成", style = MaterialTheme.typography.titleMedium) }
    }
}

/**
 * 两部手机靠近示意：两台圆角手机相向，内侧各半个心形轮廓随呼吸缩放。
 */
@Composable
private fun TwoPhonesIllustration(leftReader: Boolean, modifier: Modifier) {
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(900), RepeatMode.Reverse),
        label = "breathe")
    val phone = MaterialTheme.colorScheme.surfaceVariant
    val edge = MaterialTheme.colorScheme.outline
    val gold = Color(0xFFD8A23A)
    val heart = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val pw = w * 0.30f; val ph = h * 0.92f
        val cy = h / 2f
        val lx = w * 0.16f
        val rx = w - lx - pw
        // 两台手机轮廓
        drawRoundRect(phone, Offset(lx, cy - ph / 2f),
            androidx.compose.ui.geometry.Size(pw, ph),
            CornerRadius(pw * 0.14f))
        drawRoundRect(phone, Offset(rx, cy - ph / 2f),
            androidx.compose.ui.geometry.Size(pw, ph),
            CornerRadius(pw * 0.14f))
        drawRoundRect(edge, Offset(lx, cy - ph / 2f),
            androidx.compose.ui.geometry.Size(pw, ph),
            CornerRadius(pw * 0.14f), style = Stroke(2f))
        drawRoundRect(edge, Offset(rx, cy - ph / 2f),
            androidx.compose.ui.geometry.Size(pw, ph),
            CornerRadius(pw * 0.14f), style = Stroke(2f))
        // 内侧边缘各半个心（呼吸缩放）：左机右半边心、右机左半边心
        fun halfHeart(cx0: Float, flip: Boolean) {
            val hw = pw * 0.62f; val hh = ph * 0.34f
            val p = heartPath(hw, hh, cx0, cy)
            withTransform({
                scale(breath, breath, Offset(cx0, cy))
                if (flip) {
                    // 镜像：以 cx0 为轴翻转
                    scale(-1f, 1f, Offset(cx0, cy))
                }
            }) {
                drawPath(p, heart.copy(alpha = 0.9f),
                    style = Stroke(3f))
                drawPath(p, heart.copy(alpha = 0.18f))
            }
        }
        halfHeart(lx + pw, false)
        halfHeart(rx, true)
        // 读卡端标记：顶部小圆点波纹
        val readerX = if (leftReader) lx + pw / 2f else rx + pw / 2f
        drawCircle(gold, 5f, Offset(readerX, cy - ph / 2f - 14f))
        drawCircle(gold.copy(alpha = 0.4f), 9f * breath,
            Offset(readerX, cy - ph / 2f - 14f), style = Stroke(2f))
    }
}
