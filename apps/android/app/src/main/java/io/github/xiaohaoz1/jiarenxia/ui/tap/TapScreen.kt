package io.github.xiaohaoz1.jiarenxia.ui.tap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.PersonAliasEntity
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonPickItem
import io.github.xiaohaoz1.jiarenxia.domain.PersonPickList
import io.github.xiaohaoz1.jiarenxia.domain.ShareImport
import io.github.xiaohaoz1.jiarenxia.domain.ceremony.WeddingWords
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyMerge
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpousePairing
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpousePairingStore
import io.github.xiaohaoz1.jiarenxia.domain.merge.TapMemorial
import io.github.xiaohaoz1.jiarenxia.domain.merge.TapNegotiation
import io.github.xiaohaoz1.jiarenxia.domain.merge.TapNegotiator
import io.github.xiaohaoz1.jiarenxia.domain.merge.TapReveal
import io.github.xiaohaoz1.jiarenxia.nfc.TapProtocol
import io.github.xiaohaoz1.jiarenxia.nfc.TapSession
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranch
import io.github.xiaohaoz1.jiarenxia.ui.graph.GraphBranches
import io.github.xiaohaoz1.jiarenxia.ui.share.ShareScope
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 碰一碰资料包导出（独立成小类：一期可被单测替换为计数实现）。 */
@Singleton
open class TapPayloadFactory @Inject constructor(
    private val backup: BackupManager,
    private val pairingStore: SpousePairingStore,
    private val db: AppDatabase
) {
    /**
     * 导出本机碰一碰资料包（已含协议头）。纪念模式不得调用。
     * 随包附带来源标注：本机人物 → 当初收到它时的来源 id（配对映射
     * 反转 + person_alias 回环）——对方借此把回环资料认回“本来就是
     * 我的人”，重复碰面不产生重复人物。
     * [scope] 为“互加亲人”的发送方圈选：非 null 时包内只含集合内人物。
     */
    open suspend fun pack(scope: Set<String>? = null): ByteArray {
        val origins = pairingStore.personMap()
            .entries.associate { (remote, local) -> local to remote } +
            db.personAliasDao().getAll()
                .associate { it.localPersonId to it.remotePersonId }
        return TapProtocol.pack(backup.exportTapPayload(origins, scope))
    }
}

/** 碰一碰页面状态（FAMILY_AFFAIRS 第 10 节，三模式版）。 */
data class TapUiState(
    val stage: Stage = Stage.CHECKING,
    /** 我是男方还是女方（称谓与半心左右按性别，与 NFC 角色无关）。 */
    val isMale: Boolean = true,
    /** 本机连接方式：true=主动感应(读卡)、false=等待感应(卡片)。与性别无关。 */
    val isReader: Boolean = true,
    /** 用户是否已明确选择过连接方式（未选择时先走 CHOOSE_ROLE 页）。 */
    val roleChosen: Boolean = false,
    /** “我”已读到、本机信息就绪；此前不开任何 NFC 模式。 */
    val roleReady: Boolean = false,
    /** 本机已与对方手机建立过配对（决定文案与默认模式）。 */
    val paired: Boolean = false,
    val selfName: String = "",
    val peerName: String = "",
    val peerFamilyCount: Int = 0,
    /** 本机家人数（HELLO 里报给对方）。 */
    val selfFamilyCount: Int = 0,
    val peerSpouseName: String? = null,
    /** 本机“我”已录入的配偶姓名（前置检查 + 等待页提示）。 */
    val selfSpouseName: String? = null,
    /** 等待页上的一行提示（断线重贴、对方取消等）。 */
    val notice: String? = null,
    val error: String? = null,
    /** 本机预演开关：流程与动画一致但不写入；任一方开启则双方都预演。 */
    val rehearsal: Boolean = false,
    /** 本次会话按预演走（己方或对方任一开预演）。 */
    val sessionRehearsal: Boolean = false,
    /** 本机想在已配对基础上交换家人资料（已配对页的次级入口）。 */
    val wantUpdate: Boolean = false,
    /** 本次协商出的模式（驱动各页文案）。 */
    val sessionMode: SessionMode = SessionMode.NONE,
    /** 等待本机用户确认的事项（同名配偶候选 / 配对恢复）。 */
    val confirm: Confirm = Confirm.NONE,
    /** 确认页上展示的候选配偶名。 */
    val confirmName: String = "",
    /** 配对不一致恢复时的文案差异。 */
    val mismatchDifferentIds: Boolean = false,
    /** 资料更新预览（UPDATE_PREVIEW 阶段）。 */
    val updateNew: Int = 0,
    val updateNewRels: Int = 0,
    val updateNewPhones: Int = 0,
    val updateNewDates: Int = 0,
    val updateMatched: Int = 0,
    val updateConflicts: List<String> = emptyList(),
    /** 疑似同一人的待确认候选（确认页与更新预览共用展示/勾选）。 */
    val candidates: List<SpouseMerge.MatchCandidate> = emptyList(),
    /** 已勾选的候选（来源人物 id 集合）。 */
    val candidatesSelected: Set<String> = emptySet(),
    /** 共享时钟偏移：sharedNow = 本机时钟 + offset（读卡端恒 0）。 */
    val clockOffsetMs: Long = 0,
    /** 合心过渡开始的共享时刻；未到点时显示微光轮廓。 */
    val showStartAt: Long? = null,
    /** 本机显示左半心（来宾看过去新郎在左；站反了用“换边”）。 */
    val leftHalf: Boolean = true,
    /** 飘进半颗心的新家人（最多 8 位，配偶在前）。 */
    val newFamily: List<FamilyLabel> = emptyList(),
    /** 超出 8 位的其余家人数（显示“等 N 位家人”）。 */
    val moreFamilyCount: Int = 0,
    /** 婚礼祝词（左词/右词）：协商时按读卡端 HELLO 解析（WeddingWords）。 */
    val weddingWords: Pair<String, String> = WeddingWords.DEFAULT,
    /** 纪念模式展示行（祝福语/相伴天数/周年）。 */
    val memorialLine: String? = null,
    /** 纪念日是当天（周年特效）。 */
    val memorialAnniversary: Boolean = false,
    /** 纪念页底部提示（日期核对/单方更新意愿说明；null 不显示）。 */
    val memorialHint: String? = null,
    /** 对方正在本机确认（等待对方操作提示）。 */
    val peerConfirming: Boolean = false,

    // ---------------- 互加亲人（FAMILY_AFFAIRS 10 扩充） ----------------
    /** 本机碰一碰用途（HELLO 的 kind 字段）：由入口路由带入。 */
    val tapKind: Byte = TapProtocol.KIND_SPOUSE,

    // —— 发送方圈选（贴之前）——
    /** 圈选页人物条目（本机全家，“姓名（称谓）”）。 */
    val scopeItems: List<PersonPickItem> = emptyList(),
    /** 圈选页勾选集合（必须含“我”）。 */
    val scopeSelected: Set<String> = emptySet(),
    /** 可选分支预设（全部/父系/母系/…）。 */
    val scopeBranches: List<GraphBranch> = listOf(GraphBranch.ALL),
    val scopeBranch: GraphBranch = GraphBranch.ALL,
    /** 圈选的断开提醒（非空时弹“仍要分享”对话框）。 */
    val scopeWarn: Set<String>? = null,
    // —— 接收方“他是谁”与选人（贴完分开后各自处理）——
    /** 对方本人姓名（“他是谁”页标题）。 */
    val relPeerSelfName: String = "",
    /** 对方本人人物 id（选人页该行锁定勾选）。 */
    val relPeerSelfId: String = "",
    /** 对方本人生理性别（“他不在我家”声明关系的选项过滤）。 */
    val relPeerSelfGender: Gender = Gender.MALE,
    /** 对方带来的人物条目（“对方本人”“对方的X”）。 */
    val relPeerItems: List<PersonPickItem> = emptyList(),
    /** 勾选要添加的对方人物（对方本人恒在）。 */
    val relPickSelected: Set<String> = emptySet(),
    /** 勾选的断开提醒（非空时弹“可能连不进你家”对话框）。 */
    val relPickWarn: Set<String>? = null,
    /** “他是谁”页的本机人物条目与性别表。 */
    val relLocalItems: List<PersonPickItem> = emptyList(),
    val relLocalGenders: Map<String, Gender> = emptyMap(),
    /** 本机“我”的 id（“他是谁”候选里排除——对方本人不可能是你）。 */
    val relLocalSelfId: String = "",
    /** “他是谁”页的提示语（声明关系被拒等）。 */
    val relError: String? = null,
    // —— 合并预览与完成 ——
    /** 最近一次重算出的合并计划。 */
    val relPlan: FamilyMerge.Plan? = null,
    /** 新人物 id → 称谓（本机“我”视角）。 */
    val relNewTitles: Map<String, String> = emptyMap(),
    /** 手动指认 remoteId → 本机人物名。 */
    val relManualNames: Map<String, String> = emptyMap(),
    /** 本次合并将新增保存的对应关系条数。 */
    val relNewAliases: Int = 0,
    /** 写入/计算进行中。 */
    val relBusy: Boolean = false,
    /** 完成页文案（REL_DONE 阶段显示）。 */
    val relDoneText: String = ""
) {
    enum class Stage {
        CHECKING,          // 检查 NFC
        NO_NFC,            // 不支持
        NFC_OFF,           // 未开启
        CHOOSE_ROLE,       // 初次选择连接方式（两台选相反）
        REL_SCOPE,         // 互加亲人：圈选分享给对方的家人
        WAITING,           // 等待贴上
        CONFIRMING,        // 等待本机用户确认（旧配偶候选/配对恢复/同一人候选）
        EXCHANGING,        // 已贴上、交换中
        UPDATE_PREVIEW,    // 资料更新预览（待本机确认写入）
        REL_IDENTIFY,      // 互加亲人：对方是我家谁（或“我家还没有他”）
        REL_PICK,          // 互加亲人：勾选要添加的对方家人
        REL_PREVIEW,       // 互加亲人：合并预览（本机确认写入）
        REL_DONE,          // 互加亲人：完成
        SHOW,              // 完成、合心展示（可直接分开）
        ERROR
    }

    /** 本次协商模式。 */
    enum class SessionMode { NONE, FIRST_MERGE, MEMORIAL, UPDATE, REHEARSAL, RELATIVES }

    /** 等待本机确认的事项。 */
    enum class Confirm { NONE, SPOUSE_CANDIDATE, PAIR_MISMATCH, FAMILY_CANDIDATES }

    /** 飘进半颗心的一位新家人（新称谓 + 姓名）。 */
    data class FamilyLabel(val title: String, val name: String)
}

/**
 * 碰一碰（婚礼仪式）：两部手机停在页面并贴上即自动协商——
 * 未配对：结为夫妻合并两家；已配对：纪念仪式不写资料；
 * 已配对+更新：交换资料各自预览确认后写入；任一方开预演则双方都预演。
 * NFC 连接方式与性别无关，两台手机选相反方式。
 */
@HiltViewModel
class TapViewModel @Inject constructor(
    private val backup: BackupManager,
    private val observeOverview: ObserveFamilyOverview,
    private val session: TapSession,
    private val engine: KinshipEngine,
    private val lunar: LunarCalendar,
    private val settings: SettingsRepository,
    private val pairingStore: SpousePairingStore,
    private val payloadFactory: TapPayloadFactory,
    private val db: AppDatabase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * 碰一碰用途由入口路由参数 kind 决定（“我的”页两个入口直达），
     * 页内不再选择；缺省/非法值按结为夫妻处理。
     */
    private val entryKind: Byte =
        when (savedStateHandle.get<Int>("kind")?.toByte()) {
            // 互加亲人已移除：未知/旧用途一律按结为夫妻处理
            else -> TapProtocol.KIND_SPOUSE
        }

    private val _ui = MutableStateFlow(TapUiState())
    val ui: StateFlow<TapUiState> = _ui

    private var myId: String? = null
    private var mySpouseId: String? = null
    private var myDates: List<TapProtocol.DateWire> = emptyList()
    /** 本机设置的婚礼祝词（“左|右”），HELLO 带给对方。 */
    private var myWords: String = WeddingWords.join(WeddingWords.DEFAULT)
    /** 本机配对记录（进入页面时载入）。 */
    private var pairing: SpousePairing? = null
    /** 已含协议头（pack 结果）；只在需要交换资料的模式下生成。 */
    @Volatile private var ownPayload: ByteArray? = null
    /** 对方经 DONE 上报的共享合心开始时刻（读卡端时钟域）。 */
    @Volatile private var peerStartAt: Long? = null
    /** 同一时刻只允许一个读卡协程（重复贴上不叠加）。 */
    private val readerBusy = AtomicBoolean(false)
    /** 资料包只导出一次（重复进入/重试不叠加）。 */
    private val payloadBusy = AtomicBoolean(false)
    /** 本次页面会话 nonce（首次合并时双方共同推导配对标识）。 */
    private val sessionNonce = ByteArray(TapProtocol.PAIRING_ID_BYTES)
        .also { java.util.Random().nextBytes(it) }
    /** 本次协商出的配对标识（合并/恢复写入用）。 */
    @Volatile private var negotiatedPairingId: String? = null
    /** 卡片端：等双方确认后要提交的更新计划。 */
    @Volatile private var pendingCommitPlan: SpouseMerge.MergePlan? = null
    /** 更新预览时记住的对方“我”与资料包指纹（提交配对记录用）。 */
    @Volatile private var pendingPeerSelf = ""
    @Volatile private var pendingPayloadSha: String? = null
    /** 更新预览时暂存的对方快照（勾选候选后重算计划用）。 */
    @Volatile private var pendingRemote: SpouseMerge.Snapshot? = null
    /** 待提交计划的合心名单（提交成功进 SHOW 时才上屏）。 */
    @Volatile private var pendingLabels: List<TapUiState.FamilyLabel> =
        emptyList()
    @Volatile private var committed = false
    /** 本次会话经用户明确确认的来源人物 id（含对方“我”与勾选候选）。 */
    private val approvedIds = HashSet<String>()
    /** 同一人候选提示本会话已出过（确认/拒绝一次后重入不再追问）。 */
    @Volatile private var candidatesDecided = false
    /**
     * 候选确认期间暂存的已收资料与合心时刻（卡片端用：本机确认时
     * 对方已收尾就直接提交，对方仍在确认则等其第二次 PUT 再提交——
     * 保持“两端都确认才写”的门）。
     */
    @Volatile private var stashPayload: ByteArray? = null
    @Volatile private var stashStartAt: Long? = null
    /** 同名配偶确认时记住的对方“我”id（确认即批准该映射）。 */
    @Volatile private var pendingPeerSelfId = ""
    // ---------------- 互加亲人 ----------------
    /** 最新家庭总览（圈选页分支预设与人物条目用）。 */
    private var latestOverview: io.github.xiaohaoz1.jiarenxia.domain
        .FamilyOverview? = null
    /** 圈选确定的分享范围（必含“我”）；互加亲人模式下打包用。 */
    @Volatile private var relScope: Set<String>? = null
    /** 对方资料包（REL_IDENTIFY 起的输入）。 */
    private var relRemotePlan: RestorePlan? = null
    /** 对方资料里的“我”（sharedBy）。 */
    private var relRemoteSelfId = ""
    /** 已保存的对方→本机映射（person_alias + 包内 oid 回环补充）。 */
    private var relKnownMap: Map<String, String> = emptyMap()
    private var relLocalSide: FamilyMerge.Side? = null
    /** “他是谁”答案：对方本人 → 本机已有人的指认。 */
    private var relPeerAnchor: Map<String, String> = emptyMap()
    /** “他不在我家”声明（remoteAnchor，与 relPeerAnchor 二选一）。 */
    private var relRemoteAnchor: FamilyMerge.RemoteAnchor? = null
    /** “是我家的…”手动指认（预览页内）。 */
    private val relManual = LinkedHashMap<String, String>()
    /** 已勾选的“可能是同一人”候选。 */
    private val relApproved = LinkedHashSet<String>()
    /** 选“用对方的”的字段冲突。 */
    private val relTakeRemote = LinkedHashSet<FamilyMerge.ConflictKey>()

    /** 读卡端上报的最新状态（卡片端观察“对方已确认”）。 */
    @Volatile private var peerStatusSeen: Byte = TapProtocol.STATUS_WAIT
    /**
     * 卡片端：对方已发来 DONE（= 对方已提交成功）。卡片端的写入以此为
     * 触发——读卡端的 CONFIRM 在资料就绪时就上报、早于候选决策，不能当
     * “对方同意写入”的证据；DONE 只在对方提交完成后发出，是安全的序。
     */
    @Volatile private var peerDoneArrived = false

    /** 本机上报给对方的协议状态（页面状态 → 线上状态的唯一映射）。 */
    @Volatile private var wireStatus: Byte = TapProtocol.STATUS_WAIT
    /** STATUS_REJECT 时的附加码。 */
    @Volatile private var wireCode: Byte = 0

    // NFC 检查结果由页面在每次 ON_RESUME 时上报
    private var nfcChecked = false
    private var nfcSupported = false
    private var nfcEnabled = false
    private var nfcHce = true

    companion object {
        /** 交换完成到合心过渡开始的延时（共享时钟域）。 */
        const val SHOW_DELAY_MS = 1500L
    }

    /** 共享时钟：读卡端 = 本机时钟，卡片端 = 本机 + 对读卡端的偏移。 */
    private fun sharedNow() =
        System.currentTimeMillis() + _ui.value.clockOffsetMs

    init {
        // 用途由入口固定（路由参数），互加亲人要提前备好圈选数据
        chooseKind(entryKind)
        // 圈选页与“他不在我家”锚点选择都用最新总览（跟随库变化）
        viewModelScope.launch {
            observeOverview().collect { latestOverview = it }
        }
        viewModelScope.launch {
            val ov = observeOverview().first()
            val self = ov?.self?.person
            if (self == null) {
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.ERROR,
                        error = "请先创建“我”")
                }
                return@launch
            }
            myId = self.id
            pairing = pairingStore.current()
            // 本机配偶：HELLO 上报 + 等待页提示 + 前置检查 + 同名候选确认
            val snap = backup.localMergeSnapshot()
            val spouseRel = snap.relationships.firstOrNull {
                it.type == RelationshipType.SPOUSE &&
                    (it.sourcePersonId == self.id ||
                        it.targetPersonId == self.id)
            }
            val spousePerson = spouseRel?.let { r ->
                val other = if (r.sourcePersonId == self.id)
                    r.targetPersonId else r.sourcePersonId
                snap.persons.firstOrNull { it.id == other }
            }
            mySpouseId = spousePerson?.id
            // 夫妻共享纪念日：本机“我”或配偶记录上的结婚/领证/恋爱
            // （一对夫妻每种类型共用一条；谁录的都能找到）
            myDates = snap.dates.filter {
                it.type.isCoupleShared &&
                    (it.personId == self.id || it.personId == mySpouseId)
            }.map {
                TapProtocol.DateWire(
                    kind = when (it.type) {
                        ImportantDateType.LICENSE_ANNIVERSARY ->
                            TapProtocol.DATE_LICENSE
                        ImportantDateType.DATING_ANNIVERSARY ->
                            TapProtocol.DATE_DATING
                        else -> TapProtocol.DATE_WEDDING
                    },
                    year = it.year ?: 0, month = it.month, day = it.day,
                    lunar = it.calendarType == CalendarType.LUNAR,
                    leapMonth = it.isLeapMonth)
            }
            // 婚礼祝词：本机设置经 HELLO 带给对方（不一致时读卡端为准）
            myWords = settings.weddingWords.first()
            // 角色记忆：优先该配对上次成功的角色，否则设备级偏好；
            // 都没有则初次必须明确选择（两台手机选相反方式）
            val remembered = pairing?.lastRoleReader
                ?: pairingStore.preferredRole()
            _ui.update {
                it.copy(
                    isMale = self.gender == Gender.MALE,
                    selfName = self.name,
                    selfFamilyCount = ov.byId.size,
                    selfSpouseName = spousePerson?.name,
                    leftHalf = self.gender == Gender.MALE,
                    paired = pairing != null,
                    // 用户已先手选过的选择不被加载结果覆盖
                    roleChosen = it.roleChosen || remembered != null,
                    isReader = if (it.roleChosen) it.isReader
                        else remembered ?: true,
                    roleReady = true
                )
            }
            refreshStage()
        }
    }

    /** NFC 检查结果（页面每次 ON_RESUME 调用；hce = 支持卡模拟）。 */
    fun checkNfc(supported: Boolean, enabled: Boolean, hce: Boolean = true) {
        nfcChecked = true
        nfcSupported = supported
        nfcEnabled = enabled
        nfcHce = hce
        refreshStage()
    }

    /** 由最近一次 NFC 检查结果重算 stage；checkNfc 调用过一次后才生效。 */
    private fun refreshStage() {
        if (!nfcChecked) return
        _ui.update { st ->
            when {
                // 缺“我”的错误页不动；合心展示不被 NFC 状态打断
                st.stage == TapUiState.Stage.ERROR && !st.roleReady -> st
                st.stage == TapUiState.Stage.SHOW -> st
                st.stage == TapUiState.Stage.EXCHANGING ||
                    st.stage == TapUiState.Stage.CONFIRMING ||
                    st.stage == TapUiState.Stage.UPDATE_PREVIEW ||
                    // 互加亲人传输后的本地决策页不随 NFC 状态回退
                    // （贴完即可分开，各自慢慢挑人）
                    st.stage == TapUiState.Stage.REL_IDENTIFY ||
                    st.stage == TapUiState.Stage.REL_PICK ||
                    st.stage == TapUiState.Stage.REL_PREVIEW ||
                    st.stage == TapUiState.Stage.REL_DONE -> st
                !nfcSupported ||
                    (st.roleReady && !st.isReader && !nfcHce) ->
                    st.copy(stage = TapUiState.Stage.NO_NFC)
                !nfcEnabled -> st.copy(stage = TapUiState.Stage.NFC_OFF)
                else -> st.copy(
                    stage = when {
                        !st.roleReady -> TapUiState.Stage.CHECKING
                        !st.roleChosen -> TapUiState.Stage.CHOOSE_ROLE
                        // 互加亲人贴之前必须先圈定分享范围
                        st.tapKind == TapProtocol.KIND_RELATIVES &&
                            relScope == null ->
                            TapUiState.Stage.REL_SCOPE
                        else -> TapUiState.Stage.WAITING
                    }
                )
            }
        }
    }

    /** 初次明确选择本机连接方式（两台手机要选相反的）。 */
    fun chooseRole(reader: Boolean) {
        _ui.update {
            it.copy(isReader = reader, roleChosen = true, notice = null)
        }
        refreshStage()
    }

    /** 需要本机资料包的模式（纪念模式之外都要生成并交换）。 */
    private fun needsPayload(mode: TapUiState.SessionMode) =
        mode == TapUiState.SessionMode.FIRST_MERGE ||
            mode == TapUiState.SessionMode.UPDATE ||
            mode == TapUiState.SessionMode.REHEARSAL ||
            mode == TapUiState.SessionMode.RELATIVES

    /** 按需生成资料包（纪念模式永不调用；导出失败进错误页）。 */
    private fun ensurePayload() {
        // 已生成过（重贴/重试/重连）直接推进线上状态，不重复导出
        if (ownPayload != null) {
            payloadReady()
            return
        }
        if (!payloadBusy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                // 互加亲人带圈选范围；其余模式整包导出
                ownPayload = payloadFactory.pack(
                    if (_ui.value.sessionMode == TapUiState.SessionMode.RELATIVES)
                        relScope else null)
                payloadReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                payloadBusy.set(false)
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.ERROR,
                        error = "资料导出失败")
                }
            }
        }
    }

    /** 资料包就绪后按当前模式推进线上状态（双方确认中的不抢占）。 */
    private fun payloadReady() {
        val st = _ui.value
        if (st.stage == TapUiState.Stage.EXCHANGING) {
            wireStatus = when (st.sessionMode) {
                // 资料就绪即可交换的模式（各自本地处理，不等双方确认）
                TapUiState.SessionMode.UPDATE,
                TapUiState.SessionMode.RELATIVES -> TapProtocol.STATUS_READY
                else -> TapProtocol.STATUS_CONFIRM
            }
        }
    }

    private fun ownHello() = TapProtocol.Hello(
        sessionId = 0,
        name = _ui.value.selfName,
        gender = if (_ui.value.isMale) 0 else 1,
        familyCount = _ui.value.selfFamilyCount,
        spouseName = _ui.value.selfSpouseName,
        rehearsal = _ui.value.rehearsal,
        selfId = myId ?: "",
        pairingId = pairing?.pairingId?.let { TapProtocol.fromHex(it) },
        sessionNonce = sessionNonce,
        wantUpdate = _ui.value.wantUpdate,
        dates = myDates,
        weddingWords = myWords,
        kind = _ui.value.tapKind
    )

    // ---------------- 卡片端（等待感应） ----------------

    /**
     * 整个页面生命周期内只有一张 TapCard：pause/resume 之间 deactivate/
     * activate 复用它，会话在重贴后可以续传、不会因为页面特效重建而丢。
     */
    private val card by lazy {
        TapProtocol.TapCard(
            isActive = { true },
            clock = { System.currentTimeMillis() },
            ownHello = { ownHello() },
            ownPayload = { ownPayload ?: ByteArray(0) },
            ownStatus = { wireStatus },
            onPeerHello = { h ->
                viewModelScope.launch { onPeerHello(h) }
            },
            onPeerCancel = {
                viewModelScope.launch {
                    val st = _ui.value
                    if (st.stage != TapUiState.Stage.SHOW &&
                        st.stage != TapUiState.Stage.ERROR &&
                        // 互加亲人传输已完成：对方离开不影响本地决策页
                        st.stage != TapUiState.Stage.REL_IDENTIFY &&
                        st.stage != TapUiState.Stage.REL_PICK &&
                        st.stage != TapUiState.Stage.REL_PREVIEW &&
                        st.stage != TapUiState.Stage.REL_DONE
                    ) {
                        wireStatus = TapProtocol.STATUS_WAIT
                        _ui.update {
                            it.copy(
                                stage = TapUiState.Stage.WAITING,
                                notice = "对方取消了",
                                peerConfirming = false)
                        }
                    }
                }
            },
            onPeerReject = { code ->
                viewModelScope.launch {
                    val st = _ui.value
                    if (st.stage != TapUiState.Stage.SHOW &&
                        st.stage != TapUiState.Stage.ERROR &&
                        st.stage != TapUiState.Stage.REL_IDENTIFY &&
                        st.stage != TapUiState.Stage.REL_PICK &&
                        st.stage != TapUiState.Stage.REL_PREVIEW &&
                        st.stage != TapUiState.Stage.REL_DONE
                    ) {
                        wireStatus = TapProtocol.STATUS_WAIT
                        _ui.update {
                            it.copy(
                                stage = TapUiState.Stage.WAITING,
                                notice = peerRejectNotice(code),
                                peerConfirming = false)
                        }
                    }
                }
            },
            onPeerStatus = { status, _ ->
                peerStatusSeen = status
                viewModelScope.launch {
                    _ui.update {
                        it.copy(peerConfirming =
                            status == TapProtocol.STATUS_NEED_CONFIRM)
                    }
                    maybeCardCommit()
                }
            },
            onLinkLost = {
                viewModelScope.launch {
                    // 会话保留在卡片里：重贴凭会话 id 续传
                    val st = _ui.value
                    if (st.stage == TapUiState.Stage.EXCHANGING) {
                        _ui.update {
                            it.copy(
                                stage = TapUiState.Stage.WAITING,
                                notice = "连接断开了，再贴一下继续")
                        }
                    }
                }
            },
            onPeerPayload = { bytes ->
                viewModelScope.launch { onCardPeerPayload(bytes) }
            },
            onPeerDone = { startAt ->
                peerStartAt = startAt
                peerDoneArrived = true
                viewModelScope.launch {
                    val st = _ui.value
                    when {
                        // 纪念模式没有资料包：DONE 就是展示信号
                        st.sessionMode == TapUiState.SessionMode.MEMORIAL ->
                            enterShow(startAt)
                        // 预演不写库：DONE 即展示信号
                        st.sessionRehearsal &&
                            st.stage == TapUiState.Stage.EXCHANGING ->
                            enterShow(startAt)
                        st.stage == TapUiState.Stage.SHOW &&
                            startAt != null -> {
                            val cur = st.showStartAt
                            if (cur == null || cur > sharedNow()) {
                                _ui.update {
                                    it.copy(showStartAt = startAt)
                                }
                            }
                        }
                    }
                    // DONE 到达说明对方已收尾；有待提交计划趁机再尝试一次
                    maybeCardCommit()
                }
            }
        ).also { c -> c.codeProvider = { wireCode } }
    }

    /** 页面可见时挂入卡片状态机。 */
    fun activateCard() {
        session.activate(card)
    }

    /** 页面离开。 */
    fun deactivateCard() = session.deactivate()

    /** 收到对方 HELLO（卡片端）：前置检查 → 模式协商 → 分支。 */
    private suspend fun onPeerHello(h: TapProtocol.Hello) {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.WAITING &&
            st.stage != TapUiState.Stage.EXCHANGING
        ) return
        peerStatusSeen = TapProtocol.STATUS_WAIT
        peerDoneArrived = false
        committed = false
        pendingCommitPlan = null
        negotiatedPairingId = null
        // 新一轮会话：候选批准与暂存全部作废
        approvedIds.clear()
        candidatesDecided = false
        stashPayload = null
        pendingPeerSelf = ""
        pendingPeerSelfId = ""
        pendingRemote = null
        pendingLabels = emptyList()
        // 用途不一致（一方结为夫妻、一方互加亲人）：前置检查一律跳过，
        // 直接按模式不一致拒绝——性别/配偶检查只属于结为夫妻
        val reason = if (h.kind == _ui.value.tapKind &&
            _ui.value.tapKind == TapProtocol.KIND_SPOUSE) rejectReason(h)
            else null
        if (reason != null) {
            wireStatus = TapProtocol.STATUS_REJECT
            wireCode = rejectCode(h)
            _ui.update {
                it.copy(stage = TapUiState.Stage.ERROR,
                    error = reason, peerName = h.name,
                    peerFamilyCount = h.familyCount,
                    peerSpouseName = h.spouseName)
            }
            return
        }
        // 模式协商（双方都各自本地求值，结果一致）
        val decision = TapNegotiator.decide(
            ownHello(), h, pairing, _ui.value.selfSpouseName)
        applyDecision(decision, h)
    }

    /** 协商结果 → 页面分支（卡片端与读卡端共用）。 */
    private fun applyDecision(
        d: TapNegotiation, h: TapProtocol.Hello
    ) {
        card.exchangeBeforeConfirm = d is TapNegotiation.Update ||
            d is TapNegotiation.Relatives
        // 婚礼祝词：两端不一致以读卡端为准（两机各持有双方 HELLO，结果一致）
        _ui.update {
            it.copy(weddingWords = WeddingWords.resolve(
                it.isReader, myWords, h.weddingWords))
        }
        when (d) {
            // 互加亲人：就绪即交换，收到对方包后各自本地决定加谁
            // （“贴一下传完就能分开”，不等对方确认）
            TapNegotiation.Relatives -> {
                negotiatedPairingId = null
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.EXCHANGING,
                        sessionMode = TapUiState.SessionMode.RELATIVES,
                        sessionRehearsal = false,
                        peerName = h.name,
                        peerFamilyCount = h.familyCount,
                        peerSpouseName = h.spouseName,
                        notice = null,
                        clockOffsetMs = if (_ui.value.isReader) 0L
                            else if (h.clockMillis > 0)
                                h.clockMillis - System.currentTimeMillis()
                            else 0L)
                }
                ensurePayload()
            }
            // 两台手机选的用途不一样：互不交换，提示选同一种
            TapNegotiation.ModeMismatch -> {
                wireStatus = TapProtocol.STATUS_REJECT
                wireCode = TapProtocol.REJECT_MODE_MISMATCH
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.ERROR,
                        error = "对方选的是「互加亲人」。新版只保留结为夫妻，" +
                            "请两部手机都进入「碰一碰结为夫妻」。",
                        peerName = h.name,
                        peerFamilyCount = h.familyCount)
                }
            }
            TapNegotiation.Rehearsal -> {
                negotiatedPairingId = null
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.EXCHANGING,
                        sessionMode = TapUiState.SessionMode.REHEARSAL,
                        sessionRehearsal = true,
                        peerName = h.name,
                        peerFamilyCount = h.familyCount,
                        peerSpouseName = h.spouseName,
                        notice = null,
                        clockOffsetMs = if (_ui.value.isReader) 0L
                            else if (h.clockMillis > 0)
                                h.clockMillis - System.currentTimeMillis()
                            else 0L)
                }
                ensurePayload()
            }
            is TapNegotiation.Memorial -> {
                // 纪念模式：不生成/不交换资料包，确认双方就绪即 DONE。
                // 只有一方想更新时也走纪念——在纪念页说明，不静默交换资料
                wireStatus = TapProtocol.STATUS_CONFIRM
                val updateNote = when {
                    d.ownWantsUpdate && !d.peerWantsUpdate ->
                        "对方没有打开「更新家人资料」，这次只放纪念仪式；" +
                            "想交换更新请对方也打开"
                    d.peerWantsUpdate && !d.ownWantsUpdate ->
                        "对方想更新家人资料，你没有打开更新；" +
                            "这次只放纪念仪式"
                    else -> null
                }
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.EXCHANGING,
                        sessionMode = TapUiState.SessionMode.MEMORIAL,
                        sessionRehearsal = false,
                        peerName = h.name,
                        peerFamilyCount = h.familyCount,
                        peerSpouseName = h.spouseName,
                        notice = null,
                        memorialLine = memorialText(h),
                        memorialAnniversary = memorialAnniv(h),
                        memorialHint = memorialHint(h, updateNote),
                        clockOffsetMs = if (_ui.value.isReader) 0L
                            else if (h.clockMillis > 0)
                                h.clockMillis - System.currentTimeMillis()
                            else 0L)
                }
            }
            is TapNegotiation.Update -> {
                negotiatedPairingId = d.pairingId
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.EXCHANGING,
                        sessionMode = TapUiState.SessionMode.UPDATE,
                        sessionRehearsal = false,
                        peerName = h.name,
                        peerFamilyCount = h.familyCount,
                        peerSpouseName = h.spouseName,
                        notice = null,
                        clockOffsetMs = if (_ui.value.isReader) 0L
                            else if (h.clockMillis > 0)
                                h.clockMillis - System.currentTimeMillis()
                            else 0L)
                }
                ensurePayload()
            }
            is TapNegotiation.FirstMerge -> {
                negotiatedPairingId = d.pairingId
                pendingPeerSelfId = h.selfId
                if (d.confirmSpouse != null) {
                    // 旧配偶候选：明确确认“对方就是已录入的配偶”
                    wireStatus = TapProtocol.STATUS_NEED_CONFIRM
                    _ui.update {
                        it.copy(
                            stage = TapUiState.Stage.CONFIRMING,
                            sessionMode = TapUiState.SessionMode.FIRST_MERGE,
                            confirm = TapUiState.Confirm.SPOUSE_CANDIDATE,
                            confirmName = d.confirmSpouse,
                            sessionRehearsal = false,
                            peerName = h.name,
                            peerFamilyCount = h.familyCount,
                            peerSpouseName = h.spouseName,
                            notice = null,
                            clockOffsetMs = if (_ui.value.isReader) 0L
                                else if (h.clockMillis > 0)
                                    h.clockMillis - System.currentTimeMillis()
                                else 0L)
                    }
                } else {
                    _ui.update {
                        it.copy(
                            stage = TapUiState.Stage.EXCHANGING,
                            sessionMode = TapUiState.SessionMode.FIRST_MERGE,
                            sessionRehearsal = false,
                            peerName = h.name,
                            peerFamilyCount = h.familyCount,
                            peerSpouseName = h.spouseName,
                            notice = null,
                            clockOffsetMs = if (_ui.value.isReader) 0L
                                else if (h.clockMillis > 0)
                                    h.clockMillis - System.currentTimeMillis()
                                else 0L)
                    }
                    ensurePayload()
                }
            }
            is TapNegotiation.PairMismatch -> {
                negotiatedPairingId = d.resolvablePairingId
                pendingPeerSelfId = h.selfId
                wireStatus = TapProtocol.STATUS_NEED_CONFIRM
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.CONFIRMING,
                        sessionMode = TapUiState.SessionMode.FIRST_MERGE,
                        confirm = TapUiState.Confirm.PAIR_MISMATCH,
                        mismatchDifferentIds =
                            d.kind ==
                                TapNegotiation.PairMismatch.Kind.DIFFERENT,
                        sessionRehearsal = false,
                        peerName = h.name,
                        peerFamilyCount = h.familyCount,
                        peerSpouseName = h.spouseName,
                        notice = null)
                }
            }
            TapNegotiation.PeerChanged -> {
                wireStatus = TapProtocol.STATUS_REJECT
                wireCode = TapProtocol.REJECT_PAIR_MISMATCH
                _ui.update {
                    it.copy(
                        stage = TapUiState.Stage.ERROR,
                        error = "对方手机的身份与配对记录不一致，" +
                            "请确认是配偶本人的手机")
                }
            }
        }
    }

    // ---------------- 读卡端（主动感应） ----------------

    /** 读到卡片（ReaderMode onTagDiscovered）后驱动协议。 */
    fun onTagDiscovered(tag: android.nfc.Tag) {
        val dep = android.nfc.tech.IsoDep.get(tag)
            ?: return onNonIsoDepTag()
        startReader(
            open = { dep.timeout = 3000; dep.connect() },
            link = TapProtocol.ApduTransport { cmd -> dep.transceive(cmd) },
            close = { runCatching { dep.close() } }
        )
    }

    /**
     * 读到的是非 ISO-DEP 卡（门禁卡/公交卡）：对方手机当卡片端时把它
     * 钱包里的默认卡交出来了（如 OPPO ColorOS eSE 门禁卡）——提示交换角色。
     */
    internal fun onNonIsoDepTag() {
        _ui.update { st ->
            if (st.stage != TapUiState.Stage.WAITING) st
            else st.copy(
                notice = "感应到的是门禁卡或公交卡，不是对方的碰一碰：" +
                    "对方手机可能把钱包里的卡设成了默认。" +
                    "请两部手机都点“交换连接方式”再试"
            )
        }
    }

    /**
     * 读卡协程（可注入内存链路单测）。仅在 WAITING 且没有在跑的协程时
     * 启动，否则返回 null。断线/对方未开页面 → 回等待页提示重贴；
     * 协议错误 → 错误页。
     */
    internal fun startReader(
        open: () -> Unit,
        link: TapProtocol.ApduTransport,
        close: () -> Unit
    ): Job? {
        if (_ui.value.stage != TapUiState.Stage.WAITING) return null
        if (!readerBusy.compareAndSet(false, true)) return null
        // 新一轮读卡会话：上一轮残留的候选批准与暂存作废（重贴安全）
        approvedIds.clear()
        candidatesDecided = false
        stashPayload = null
        stashStartAt = null
        pendingCommitPlan = null
        pendingRemote = null
        pendingLabels = emptyList()
        committed = false
        peerStartAt = null
        peerStatusSeen = TapProtocol.STATUS_WAIT
        peerDoneArrived = false
        return viewModelScope.launch(Dispatchers.IO) {
            var connected = false
            var exchanged = false
            try {
                open()
                val reader = TapProtocol.TapReader(
                    link, ownHello(), { ownPayload ?: ByteArray(0) })
                val hello = reader.connect()
                connected = true
                // 性别/配偶前置检查只属于结为夫妻；用途不一致交由协商拒绝
                val reason = if (hello.kind == _ui.value.tapKind &&
                    _ui.value.tapKind == TapProtocol.KIND_SPOUSE)
                        rejectReason(hello) else null
                if (reason != null) {
                    // 告知对方拒绝原因，双方看到同一条原因
                    runCatching {
                        reader.status(
                            TapProtocol.STATUS_REJECT, rejectCode(hello))
                    }
                    _ui.update {
                        it.copy(
                            stage = TapUiState.Stage.ERROR,
                            error = reason,
                            peerName = hello.name,
                            peerFamilyCount = hello.familyCount,
                            peerSpouseName = hello.spouseName
                        )
                    }
                    return@launch
                }
                // 模式协商（两端各自本地求值，结果一致）
                val decision = TapNegotiator.decide(
                    ownHello(), hello, pairing, _ui.value.selfSpouseName)
                applyDecision(decision, hello)
                // 协商出拒绝类结果时直接走错误页
                if (_ui.value.stage == TapUiState.Stage.ERROR) {
                    runCatching {
                        reader.status(TapProtocol.STATUS_REJECT, wireCode)
                    }
                    return@launch
                }
                // 主循环：轮询对方状态直到本次会话收尾
                while (true) {
                    val st = _ui.value
                    when (st.stage) {
                        TapUiState.Stage.WAITING -> {
                            // 本机取消/NFC 关闭/对方已离开：告知对方作废
                            runCatching {
                                reader.status(
                                    TapProtocol.STATUS_CANCEL)
                            }
                            return@launch
                        }
                        TapUiState.Stage.CONFIRMING -> {
                            // 本机正在等用户确认
                            val (peer, pcode) = reader.status(
                                TapProtocol.STATUS_NEED_CONFIRM)
                            if (peer == TapProtocol.STATUS_CANCEL ||
                                peer == TapProtocol.STATUS_REJECT
                            ) {
                                backToWaiting(peerEndNotice(peer, pcode))
                                return@launch
                            }
                            _ui.update {
                                it.copy(peerConfirming =
                                    peer == TapProtocol.STATUS_NEED_CONFIRM)
                            }
                            delay(200)
                            continue
                        }
                        TapUiState.Stage.UPDATE_PREVIEW -> {
                            val (peer, pcode) = reader.status(
                                TapProtocol.STATUS_NEED_CONFIRM)
                            if (peer == TapProtocol.STATUS_CANCEL ||
                                peer == TapProtocol.STATUS_REJECT
                            ) {
                                backToWaiting(peerEndNotice(peer, pcode))
                                return@launch
                            }
                            delay(200)
                            continue
                        }
                        // 互加亲人：资料交换完成后进入本地决策页，向对方发
                        // DONE 收尾即结束会话（之后互不等待，各自慢慢挑）
                        TapUiState.Stage.REL_IDENTIFY,
                        TapUiState.Stage.REL_PICK,
                        TapUiState.Stage.REL_PREVIEW,
                        TapUiState.Stage.REL_DONE -> {
                            runCatching { reader.done(sharedNow()) }
                            return@launch
                        }
                        TapUiState.Stage.EXCHANGING -> {
                            val own = wireStatus
                            val (peer, pcode) = reader.status(own)
                            if (peer == TapProtocol.STATUS_CANCEL ||
                                peer == TapProtocol.STATUS_REJECT
                            ) {
                                backToWaiting(peerEndNotice(peer, pcode))
                                return@launch
                            }
                            _ui.update {
                                it.copy(peerConfirming =
                                    peer == TapProtocol.STATUS_NEED_CONFIRM)
                            }
                            when (st.sessionMode) {
                                TapUiState.SessionMode.MEMORIAL -> {
                                    if (own == TapProtocol.STATUS_CONFIRM &&
                                        peer == TapProtocol.STATUS_CONFIRM
                                    ) {
                                        val startAt =
                                            sharedNow() + SHOW_DELAY_MS
                                        runCatching { reader.done(startAt) }
                                        enterShow(startAt)
                                        return@launch
                                    }
                                }
                                TapUiState.SessionMode.UPDATE -> {
                                    if (!exchanged) {
                                        if (exchangeOk(own, peer, true)) {
                                            // 就绪即交换，各自出预览
                                            val plain = reader.recvPayload()
                                            reader.sendPayload()
                                            exchanged = true
                                            prepareUpdatePreview(plain)
                                        }
                                    } else if (
                                        own == TapProtocol.STATUS_CONFIRM &&
                                        peer == TapProtocol.STATUS_CONFIRM &&
                                        !committed
                                    ) {
                                        // 双方都确认更新 → 各自写入
                                        commitPendingAndShow(reader)
                                        return@launch
                                    }
                                }
                                TapUiState.SessionMode.RELATIVES -> {
                                    // 就绪即交换；解析后进入本地决策页，
                                    // 下一轮循环发 DONE 收尾
                                    if (!exchanged && exchangeOk(own, peer, true)) {
                                        val plain = reader.recvPayload()
                                        reader.sendPayload()
                                        exchanged = true
                                        enterRelatives(plain)
                                    }
                                }
                                else -> {
                                    // 合并/预演：双方都“已确认”才交换资料
                                    if (!exchanged) {
                                        if (exchangeOk(own, peer, false)) {
                                            val plain = reader.recvPayload()
                                            reader.sendPayload()
                                            exchanged = true
                                            completeExchange(plain, null)
                                            when (_ui.value.stage) {
                                                // 出了同一人候选或等待对方
                                                // 确认：留在循环里继续对齐
                                                TapUiState.Stage.CONFIRMING,
                                                TapUiState.Stage.EXCHANGING -> {
                                                    delay(200)
                                                    continue
                                                }
                                                // 计划被拒/解析失败：告知对方后退出
                                                TapUiState.Stage.ERROR -> {
                                                    runCatching {
                                                        reader.status(
                                                            wireStatus,
                                                            wireCode)
                                                    }
                                                    return@launch
                                                }
                                                else -> return@launch
                                            }
                                        }
                                    } else if (
                                        own == TapProtocol.STATUS_CONFIRM &&
                                        peer == TapProtocol.STATUS_CONFIRM &&
                                        !committed
                                    ) {
                                        // 双方都确认：候选确认过的用暂存
                                        // 资料按已批准集合重算计划
                                        val stash = stashPayload
                                        if (stash != null) {
                                            stashPayload = null
                                            completeExchange(stash, null)
                                            if (_ui.value.stage ==
                                                TapUiState.Stage.ERROR
                                            ) {
                                                runCatching {
                                                    reader.status(
                                                        wireStatus, wireCode)
                                                }
                                                return@launch
                                            }
                                        }
                                        commitPendingAndShow(reader)
                                        return@launch
                                    }
                                }
                            }
                            delay(200)
                        }
                        else -> {
                            // 离开交换相关阶段：把当前线上状态发给对方
                            // （REJECT/CANCEL），再退出协程
                            runCatching {
                                reader.status(wireStatus, wireCode)
                            }
                            return@launch
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                backToWaiting("连接断开了，再贴一下继续")
            } catch (e: SecurityException) {
                backToWaiting("连接断开了，再贴一下继续")
            } catch (e: TapProtocol.TapFail) {
                when {
                    e.sw == 0x6A82 && !connected -> backToWaiting(
                        "没读到对方的碰一碰：请确认对方也打开了碰一碰页面；" +
                            "仍不行就两部手机都点“交换连接方式”再试")
                    e.sw == 0x6A82 -> backToWaiting(
                        "对方取消了或离开了碰一碰页面，再贴一下重新开始")
                    else -> toError("碰一碰失败：" + (e.message ?: "未知错误"))
                }
            } catch (e: Exception) {
                toError("碰一碰失败：" + (e.message ?: "未知错误"))
            } finally {
                close()
                readerBusy.set(false)
            }
        }
    }

    /** 双方状态是否允许交换资料（update 模式就绪即可，其余需双方都确认）。 */
    private fun exchangeOk(own: Byte, peer: Byte, updateMode: Boolean):
        Boolean {
        fun ok(s: Byte) = s == TapProtocol.STATUS_READY ||
            s == TapProtocol.STATUS_CONFIRM
        if (!ok(own) || !ok(peer)) return false
        return updateMode ||
            (own == TapProtocol.STATUS_CONFIRM &&
                peer == TapProtocol.STATUS_CONFIRM)
    }

    /** 对方结束时本机提示：CANCEL 泛化，REJECT 按附加码给可操作文案。 */
    private fun peerEndNotice(peer: Byte, code: Byte) =
        if (peer == TapProtocol.STATUS_REJECT) peerRejectNotice(code)
        else "对方取消了"

    /** 只在等待/交换/确认阶段回等待页（不覆盖 NFC 关闭、合心、错误）。 */
    private fun backToWaiting(notice: String) {
        wireStatus = TapProtocol.STATUS_WAIT
        _ui.update { st ->
            if (st.stage != TapUiState.Stage.WAITING &&
                st.stage != TapUiState.Stage.EXCHANGING &&
                st.stage != TapUiState.Stage.CONFIRMING &&
                st.stage != TapUiState.Stage.UPDATE_PREVIEW
            ) st
            else st.copy(stage = TapUiState.Stage.WAITING,
                notice = notice, peerConfirming = false)
        }
    }

    private fun toError(msg: String) {
        wireStatus = TapProtocol.STATUS_WAIT
        _ui.update { it.copy(stage = TapUiState.Stage.ERROR, error = msg) }
    }

    /**
     * 碰上即前置检查（HELLO 已带对方姓名/性别/配偶）：
     * 同性 → 拒绝；本机已有非对方的配偶、对方已有非我的配偶 → 拒绝。
     * 已配对时以配对记录为准（对方改名不拦）。
     */
    private fun rejectReason(h: TapProtocol.Hello): String? {
        val p = pairing
        if (p != null) {
            val peerPid = h.pairingId?.let { TapProtocol.toHex(it) }
            if (peerPid == p.pairingId) {
                // 同一对配偶：只查性别（姓名允许随后改过）
                return if ((h.gender == 0.toByte()) == _ui.value.isMale)
                    "碰一碰结为夫妻需要一男一女" else null
            }
        }
        return when {
            (h.gender == 0.toByte()) == _ui.value.isMale ->
                "碰一碰结为夫妻需要一男一女"
            _ui.value.selfSpouseName != null &&
                _ui.value.selfSpouseName != h.name ->
                "你已录入配偶" + _ui.value.selfSpouseName +
                    "，只能与" + _ui.value.selfSpouseName + "碰一碰"
            h.spouseName != null && h.spouseName != _ui.value.selfName ->
                "对方已录入配偶" + h.spouseName
            else -> null
        }
    }

    /** 前置检查对应的拒绝码（发给对方显示）。 */
    private fun rejectCode(h: TapProtocol.Hello): Byte = when {
        (h.gender == 0.toByte()) == _ui.value.isMale ->
            TapProtocol.REJECT_GENDER
        _ui.value.selfSpouseName != null &&
            _ui.value.selfSpouseName != h.name ->
            TapProtocol.REJECT_SELF_SPOUSE
        h.spouseName != null && h.spouseName != _ui.value.selfName ->
            TapProtocol.REJECT_PEER_SPOUSE
        else -> TapProtocol.REJECT_NOT_SPOUSE
    }

    /** 对方拒绝时本机展示的提示（按附加码给可操作文案）。 */
    private fun peerRejectNotice(code: Byte): String = when (code) {
        TapProtocol.REJECT_GENDER -> "对方拒绝了：碰一碰结为夫妻需要一男一女"
        TapProtocol.REJECT_SELF_SPOUSE -> "对方拒绝了：对方家人里已录入别的配偶"
        TapProtocol.REJECT_PEER_SPOUSE -> "对方拒绝了：对方说你已录入别的配偶"
        TapProtocol.REJECT_NOT_SPOUSE -> "对方说：你不是对方已录入的配偶"
        TapProtocol.REJECT_PAIR_MISMATCH ->
            "配对信息不一致，请确认是配偶本人的手机"
        TapProtocol.REJECT_MODE_MISMATCH ->
            "对方拒绝了：对方选的是「互加亲人」。" +
                "新版只保留结为夫妻，请两部手机都进入「碰一碰结为夫妻」。"
        else -> "对方拒绝了这次碰一碰"
    }

    // ---------------- 用户操作 ----------------

    /**
     * 应用碰一碰用途（结为夫妻 / 互加亲人）。生产路径只在 init 由
     * 路由参数调用一次；测试可用来切换用途。两台手机须选同一种。
     */
    fun chooseKind(kind: Byte) {
        _ui.update {
            it.copy(tapKind = kind, notice = null)
        }
        if (kind == TapProtocol.KIND_RELATIVES) prepareScope()
        refreshStage()
    }

    /** 圈选页初始化：本机人物条目 + 分支预设（默认全选）。 */
    private fun prepareScope() {
        viewModelScope.launch {
            // 持续收集的总览可能尚未到达（快速选用途时）→ 主动取一次
            val ov = latestOverview ?: observeOverview().first()
                ?: return@launch
            _ui.update { st ->
                if (st.scopeItems.isNotEmpty()) st else st.copy(
                    scopeItems = PersonPickList.build(ov),
                    scopeBranches = GraphBranches.available(ov),
                    scopeBranch = GraphBranch.ALL,
                    scopeSelected = ShareScope.preset(ov, GraphBranch.ALL))
            }
        }
    }

    /** 圈选页：勾选/取消一位家人（“我”必选、不可取消）。 */
    fun toggleScopePerson(id: String) {
        val mid = myId ?: return
        if (id == mid) return
        _ui.update { st ->
            if (st.stage != TapUiState.Stage.REL_SCOPE) st else st.copy(
                scopeSelected = if (id in st.scopeSelected)
                    st.scopeSelected - id else st.scopeSelected + id,
                scopeWarn = null)
        }
    }

    /** 圈选页：按分支预设批量选择（“我”恒在集合内）。 */
    fun applyScopeBranch(branch: GraphBranch) {
        val mid = myId ?: return
        viewModelScope.launch {
            val ov = latestOverview ?: observeOverview().first()
                ?: return@launch
            _ui.update { st ->
                if (st.stage != TapUiState.Stage.REL_SCOPE) st else st.copy(
                    scopeBranch = branch,
                    scopeSelected = ShareScope.preset(ov, branch) + mid,
                    scopeWarn = null)
            }
        }
    }

    /**
     * 圈选页“选好了，去贴”：勾选但与“我”断开的人先弹提醒；
     * 确认后定稿分享范围（打包时按它过滤）。
     */
    fun confirmScope() {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.REL_SCOPE) return
        val mid = myId ?: return
        viewModelScope.launch {
            val sel = st.scopeSelected + mid
            val ov = latestOverview ?: observeOverview().first()
            if (ov != null && st.scopeWarn == null) {
                val relPairs = ov.graph.relationships
                    .map { it.sourcePersonId to it.targetPersonId }
                val disc = ShareScope.disconnected(sel, relPairs, mid)
                if (disc.isNotEmpty()) {
                    _ui.update {
                        it.copy(scopeSelected = sel, scopeWarn = disc)
                    }
                    return@launch
                }
            }
            relScope = sel
            _ui.update { it.copy(scopeSelected = sel, scopeWarn = null) }
            refreshStage()
        }
    }

    /** 圈选断开提醒“仍要分享”。 */
    fun scopeWarnProceed() {
        val st = _ui.value
        val mid = myId ?: return
        relScope = st.scopeSelected + mid
        _ui.update { it.copy(scopeWarn = null) }
        refreshStage()
    }

    /** 圈选断开提醒“返回补选”。 */
    fun scopeWarnDismiss() = _ui.update { it.copy(scopeWarn = null) }

    /** 圈选页“返回”：清掉已定稿的范围，回到连接方式选择。 */
    fun backToRole() {
        relScope = null
        _ui.update {
            it.copy(roleChosen = false, scopeWarn = null, notice = null)
        }
        refreshStage()
    }

    /**
     * 互加亲人：收到对方资料包——解析 → “他是谁”。
     * 对方本人被已保存映射 / 包内 oid 回环 / 同 id 认出时跳过识人，
     * 直接进选人页。传完即与对方断开无关，之后全在本机完成。
     */
    private suspend fun enterRelatives(deflated: ByteArray) {
        val mid = myId ?: return
        try {
            val zip = backup.inflatePayload(deflated)
            val plan = BackupJson.parsePlan(zip)
            val peerSelfId = plan.sharedBy
                ?: plan.persons.firstOrNull { it.isSelf }?.id
                ?: throw IllegalStateException("对方资料里没有“我”")
            val rs = plan.persons.firstOrNull { it.id == peerSelfId }
                ?: throw IllegalStateException("对方资料里没有“我”")
            relRemotePlan = plan
            relRemoteSelfId = peerSelfId
            relPeerAnchor = emptyMap()
            relRemoteAnchor = null
            relManual.clear(); relApproved.clear(); relTakeRemote.clear()
            val local = backup.localFamilySide()
            relLocalSide = local
            val localById = local.persons.associateBy { it.id }
            // 已保存的对方→本机映射；包内 oid 回环补充（对方包里
            // “本来就是我的人”经 re-id 后仍可认出）
            val known = HashMap<String, String>()
            db.personAliasDao().getAll().forEach {
                if (it.localPersonId in localById)
                    known[it.remotePersonId] = it.localPersonId
            }
            for ((rid, oid) in plan.personOrigins) {
                val lp = localById[oid] ?: continue
                val rp = plan.persons.firstOrNull { it.id == rid } ?: continue
                if (rp.gender == lp.gender && rid !in known) known[rid] = oid
            }
            relKnownMap = known
            // 对方带来的人（“对方本人”“对方的X”）与本机条目（“他是谁”用）
            val (peerItems, lPair) =
                kotlinx.coroutines.withContext(Dispatchers.Default) {
                    ShareImport.candidates(plan, engine, lunar, "对方") to
                        localPickItems(local, mid)
                }
            val (lItems, lGenders) = lPair
            val auto = known[peerSelfId]
                ?.takeIf { localById[it]?.gender == rs.gender }
                ?: peerSelfId.takeIf { localById[it]?.gender == rs.gender }
            _ui.update {
                it.copy(
                    relPeerSelfName = rs.name,
                    relPeerSelfId = peerSelfId,
                    relPeerSelfGender = rs.gender,
                    relPeerItems = peerItems,
                    relPickSelected =
                        plan.persons.mapTo(HashSet()) { p -> p.id },
                    relLocalItems = lItems,
                    relLocalGenders = lGenders,
                    relLocalSelfId = mid,
                    relError = null)
            }
            if (auto != null) {
                relPeerAnchor = mapOf(peerSelfId to auto)
                _ui.update { it.copy(stage = TapUiState.Stage.REL_PICK) }
            } else {
                _ui.update { it.copy(stage = TapUiState.Stage.REL_IDENTIFY) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(stage = TapUiState.Stage.ERROR,
                    error = "对方资料无法解析")
            }
        }
    }

    /** “他是谁”答一：对方本人就是我家的某个人（只能选同性别）。 */
    fun answerPeerIs(localId: String) {
        if (_ui.value.stage != TapUiState.Stage.REL_IDENTIFY) return
        relPeerAnchor = mapOf(relRemoteSelfId to localId)
        relRemoteAnchor = null
        _ui.update {
            it.copy(stage = TapUiState.Stage.REL_PICK, relError = null)
        }
    }

    /** “他是谁”答二：我家还没有他——声明他是本机某人的某种关系。 */
    fun answerPeerNew(anchorLocalId: String, role: RelationRole) {
        if (_ui.value.stage != TapUiState.Stage.REL_IDENTIFY) return
        relPeerAnchor = emptyMap()
        relRemoteAnchor = FamilyMerge.RemoteAnchor(
            relRemoteSelfId, anchorLocalId, role)
        _ui.update {
            it.copy(stage = TapUiState.Stage.REL_PICK, relError = null)
        }
    }

    /** 识人页“暂不添加”：丢弃对方包回等待页（下次碰面重新交换）。 */
    fun skipRelatives() {
        relRemotePlan = null
        relPeerAnchor = emptyMap()
        relRemoteAnchor = null
        wireStatus = TapProtocol.STATUS_WAIT
        _ui.update {
            it.copy(stage = TapUiState.Stage.WAITING,
                notice = "已收到对方资料，本次没有添加", relError = null)
        }
    }

    /** 选人页勾选开关（对方本人恒选，不可取消）。 */
    fun toggleRelPick(remoteId: String) {
        if (remoteId == relRemoteSelfId) return
        _ui.update { st ->
            if (st.stage != TapUiState.Stage.REL_PICK) st else st.copy(
                relPickSelected = if (remoteId in st.relPickSelected)
                    st.relPickSelected - remoteId
                else st.relPickSelected + remoteId,
                relPickWarn = null)
        }
    }

    /**
     * 选人页“下一步”：勾选但从对方本人断开的人先提醒
     * （中间人物没选，他们会作为无关联的人进来）。
     */
    fun confirmRelPick() {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.REL_PICK) return
        val plan = relRemotePlan ?: return
        val sel = st.relPickSelected + relRemoteSelfId
        val relPairs = plan.relationships
            .map { it.sourcePersonId to it.targetPersonId }
        val disc = ShareScope.disconnected(sel, relPairs, relRemoteSelfId)
        if (disc.isNotEmpty() && st.relPickWarn == null) {
            _ui.update { it.copy(relPickWarn = disc) }
            return
        }
        viewModelScope.launch { recomputeRelMerge() }
    }

    /** 选人断开提醒“仍然添加”。 */
    fun relPickProceed() {
        _ui.update { it.copy(relPickWarn = null) }
        viewModelScope.launch { recomputeRelMerge() }
    }

    /** 选人断开提醒“返回补选”。 */
    fun relPickWarnDismiss() =
        _ui.update { it.copy(relPickWarn = null) }

    /** 勾选的对方人物 → 合并引擎的对方一侧（对方本人恒在集合内）。 */
    private fun relRemoteSide(): FamilyMerge.Side? {
        val plan = relRemotePlan ?: return null
        val sel = _ui.value.relPickSelected + relRemoteSelfId
        val side = backup.familySideOf(plan)
        val dates = side.dates.filter { it.personId in sel }
        val dateIds = dates.mapTo(HashSet()) { it.id }
        val prefs = side.prefs.filter { it.importantDateId in dateIds }
        val prefIds = prefs.mapTo(HashSet()) { it.id }
        return side.copy(
            persons = side.persons.filter { it.id in sel },
            phones = side.phones.filter { it.personId in sel },
            relationships = side.relationships.filter {
                it.sourcePersonId in sel && it.targetPersonId in sel },
            dates = dates,
            prefs = prefs,
            offsets = side.offsets.filter {
                it.reminderPreferenceId in prefIds })
    }

    /** 按当前识人结果与勾选重算合并计划（结构同 ShareImport）。 */
    private suspend fun recomputeRelMerge() {
        val local = relLocalSide ?: return
        val remote = relRemoteSide() ?: return
        val mid = myId ?: return
        _ui.update { it.copy(relBusy = true) }
        try {
            val result = kotlinx.coroutines.withContext(Dispatchers.Default) {
                FamilyMerge.plan(FamilyMerge.Request(
                    local = local, remote = remote,
                    localSelfId = mid,
                    anchors = relPeerAnchor + relManual,
                    knownMap = relKnownMap,
                    approved = relApproved.toSet(),
                    takeRemote = relTakeRemote.toSet(),
                    remoteAnchor = relRemoteAnchor))
            }
            when (result) {
                is FamilyMerge.Result.Ok -> {
                    val titles = kotlinx.coroutines.withContext(
                        Dispatchers.Default
                    ) { relNewPersonTitles(local, result.plan, mid) }
                    _ui.update {
                        it.copy(
                            stage = TapUiState.Stage.REL_PREVIEW,
                            relPlan = result.plan,
                            relNewTitles = titles,
                            relManualNames = relManual.mapValues { (_, l) ->
                                local.persons.firstOrNull { p -> p.id == l }
                                    ?.name ?: "" },
                            relNewAliases = result.plan.mapping
                                .count { (r, l) ->
                                    r != l && relKnownMap[r] != l },
                            relError = null, relBusy = false)
                    }
                }
                // 声明的关系与库矛盾 → 退回识人页提示原因重选
                is FamilyMerge.Result.Reject -> _ui.update {
                    it.copy(stage = TapUiState.Stage.REL_IDENTIFY,
                        relPlan = null,
                        relError = result.reason, relBusy = false)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(relError = "合并失败，请重试", relBusy = false)
            }
        }
    }

    /** 新增家人称谓：合并后的内存图（本机+新增人物/关系/日期）以我观察。 */
    private suspend fun relNewPersonTitles(
        local: FamilyMerge.Side, plan: FamilyMerge.Plan, mid: String
    ): Map<String, String> {
        if (plan.newPersons.isEmpty()) return emptyMap()
        val graph = ShareImport.graphOf(
            local.persons + plan.newPersons,
            local.relationships + plan.newRelationships,
            local.dates + plan.newDates, lunar)
        val results = engine.resolveAll(graph, mid)
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

    /** 本机“我”视角的人物条目与性别表（“他是谁”/“是我家的…”共用）。 */
    private fun localPickItems(
        side: FamilyMerge.Side, selfId: String
    ): Pair<List<PersonPickItem>, Map<String, Gender>> {
        val graph = ShareImport.graphOf(
            side.persons, side.relationships, side.dates, lunar)
        val results = engine.resolveAll(graph, selfId)
        val items = side.persons.map { p ->
            val r = results[p.id]
            val title = when {
                p.id == selfId -> "我"
                r == null || r.kind == KinshipKind.UNRELATED -> null
                else -> r.displayTitle
            }
            val inside = listOfNotNull(title,
                if (p.livingStatus == LivingStatus.DECEASED) "已故" else null)
                .joinToString(" · ")
            PersonPickItem(p.id, p.name,
                p.name + if (inside.isNotEmpty()) "（" + inside + "）" else "")
        }
        return items to side.persons.associate { it.id to it.gender }
    }

    /** 预览页“可能是同一人”勾选开关。 */
    fun relToggleCandidate(remoteId: String) {
        if (!relApproved.remove(remoteId)) relApproved.add(remoteId)
        viewModelScope.launch { recomputeRelMerge() }
    }

    /** 预览页“不一致”单选（true=用对方的）。 */
    fun relChooseConflict(key: FamilyMerge.ConflictKey, remote: Boolean) {
        if (remote) relTakeRemote.add(key) else relTakeRemote.remove(key)
        viewModelScope.launch { recomputeRelMerge() }
    }

    /** 预览页新增家人“是我家的…”手动指认为本机人物。 */
    fun relPickAsLocal(remoteId: String, localId: String) {
        relManual[remoteId] = localId
        viewModelScope.launch { recomputeRelMerge() }
    }

    /** 撤销“是我家的…”手动指认。 */
    fun relUnpickAsLocal(remoteId: String) {
        relManual.remove(remoteId)
        viewModelScope.launch { recomputeRelMerge() }
    }

    /**
     * 预览页“确认合并”：单事务写库 + person_alias 保存对方→本机映射
     * （下次碰面这些人物自动认出，不再追问）。
     */
    fun confirmRelImport() {
        val plan = _ui.value.relPlan ?: return
        // 纯认人（无库内改动但有新映射）也允许确认
        if (_ui.value.relBusy ||
            !(plan.hasWrites() || _ui.value.relNewAliases > 0)) return
        _ui.update { it.copy(relBusy = true) }
        viewModelScope.launch {
            try {
                backup.importFamilyMerge(plan, emptyMap()) {
                    val rows = plan.mapping
                        .filter { (r, l) -> r != l }
                        .map { (r, l) -> PersonAliasEntity(
                            r, l, System.currentTimeMillis()) }
                    if (rows.isNotEmpty()) db.personAliasDao().upsertAll(rows)
                }
                _ui.update {
                    it.copy(stage = TapUiState.Stage.REL_DONE,
                        relBusy = false,
                        relDoneText = if (plan.newPersons.isNotEmpty())
                            "已把对方带来的 " + plan.newPersons.size +
                                " 位家人加进你的家庭"
                        else "已合并对方带来的资料")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(relBusy = false, relError = "合并失败，请重试")
                }
            }
        }
    }

    /** 预览页“没有新内容”的“完成”。 */
    fun finishRel() {
        _ui.update {
            it.copy(stage = TapUiState.Stage.REL_DONE,
                relDoneText = "没有新内容")
        }
    }

    /** 预览页“返回”：回选人页（识人结果与勾选保留）。 */
    fun backToRelPick() {
        _ui.update {
            it.copy(stage = TapUiState.Stage.REL_PICK,
                relPlan = null, relError = null)
        }
    }

    /** 预演开关（等待/错误页可切；任一方开则双方按预演走）。 */
    fun setRehearsal(on: Boolean) {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.WAITING &&
            st.stage != TapUiState.Stage.ERROR
        ) return
        _ui.update { it.copy(rehearsal = on) }
    }

    /** 已配对页“更新家人资料”开关（下次碰一碰按更新模式协商）。 */
    fun setWantUpdate(on: Boolean) {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.WAITING) return
        _ui.update { it.copy(wantUpdate = on) }
    }

    /** 换边：本机显示另一半心（站反了用）。 */
    fun swapHalf() {
        _ui.update { it.copy(leftHalf = !it.leftHalf) }
    }

    /** 交换连接方式（等待/错误页可切；两部手机都要点）。 */
    fun toggleRole() {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.WAITING &&
            st.stage != TapUiState.Stage.ERROR
        ) return
        _ui.update {
            it.copy(isReader = !it.isReader, roleChosen = true,
                notice = null)
        }
    }

    /**
     * 确认页“是”：同名配偶候选/配对恢复/同一人候选确认。
     * - 同名配偶：批准“对方的我是我录入的配偶”这条映射；
     * - 同一人候选：勾选者并入映射后提交暂存的合并计划；
     * - 配对恢复：确认是配偶后继续交换。
     * 确认后进入交换（等对方也确认）。
     */
    fun confirmPending() {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.CONFIRMING) return
        when (st.confirm) {
            TapUiState.Confirm.SPOUSE_CANDIDATE,
            TapUiState.Confirm.PAIR_MISMATCH ->
                // 批准对方“我”→本机配偶的映射（恢复配对同理）
                if (pendingPeerSelfId.isNotEmpty())
                    approvedIds.add(pendingPeerSelfId)
            TapUiState.Confirm.FAMILY_CANDIDATES -> {
                // 未勾选的阻塞候选：不能安全合并 → 按取消处理
                if (st.candidates.any {
                        it.blocking && it.remoteId !in st.candidatesSelected
                    }) {
                    declinePending()
                    return
                }
                approvedIds.addAll(st.candidatesSelected)
            }
            else -> Unit
        }
        proceedAfterConfirm()
    }

    /** 确认页“不是/取消”：拒绝（不同名配偶）或取消（配对恢复/候选）。 */
    fun declinePending() {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.CONFIRMING) return
        when (st.confirm) {
            TapUiState.Confirm.SPOUSE_CANDIDATE -> {
                wireStatus = TapProtocol.STATUS_REJECT
                wireCode = TapProtocol.REJECT_NOT_SPOUSE
                _ui.update {
                    it.copy(stage = TapUiState.Stage.ERROR,
                        error = "对方不是你已录入的配偶，未做合并",
                        confirm = TapUiState.Confirm.NONE)
                }
            }
            TapUiState.Confirm.FAMILY_CANDIDATES -> {
                if (st.candidates.any { it.blocking }) {
                    // 阻塞候选不确认 = 本次不能合并（否则会造出假的“我”）
                    wireStatus = TapProtocol.STATUS_CANCEL
                    _ui.update {
                        it.copy(stage = TapUiState.Stage.WAITING,
                            confirm = TapUiState.Confirm.NONE,
                            candidates = emptyList(),
                            candidatesSelected = emptySet(),
                            notice = "已取消：对方录入的配偶未经确认，不能合并",
                            sessionMode = TapUiState.SessionMode.NONE)
                    }
                } else {
                    // 非阻塞候选：都不认——候选保持独立人物，仍完成合并
                    proceedAfterConfirm()
                }
            }
            else -> {
                // 保持 CANCEL：对方下一次轮询能看到并退回等待页（会话作废）
                wireStatus = TapProtocol.STATUS_CANCEL
                _ui.update {
                    it.copy(stage = TapUiState.Stage.WAITING,
                        confirm = TapUiState.Confirm.NONE,
                        notice = "已取消；配对记录不一致时不会再合并",
                        sessionMode = TapUiState.SessionMode.NONE)
                }
            }
        }
    }

    /** 候选勾选开关（确认页/更新预览共用）。 */
    fun toggleCandidate(remoteId: String) {
        _ui.update { st ->
            st.copy(candidatesSelected =
                if (remoteId in st.candidatesSelected)
                    st.candidatesSelected - remoteId
                else st.candidatesSelected + remoteId)
        }
    }

    /**
     * 确认完成后的推进：回到交换态并把线上状态推进到“已确认”；
     * 真正的写入仍由协议门控（双方都 CONFIRM 才交换/提交）——
     * 一端确认而另一端取消/断开时，本机不落半截数据。
     */
    private fun proceedAfterConfirm() {
        wireStatus = TapProtocol.STATUS_WAIT
        _ui.update {
            it.copy(stage = TapUiState.Stage.EXCHANGING,
                confirm = TapUiState.Confirm.NONE,
                candidates = emptyList(),
                candidatesSelected = emptySet())
        }
        // 资料包就绪后再到“已确认”，由 payloadReady 推进
        ensurePayload()
        // 卡片端：按已批准集合重算暂存计划——真正写入仍由
        // maybeCardCommit 的“对方也已确认”门控制
        val stash = stashPayload
        if (stash != null && !_ui.value.isReader) {
            stashPayload = null
            viewModelScope.launch { completeExchange(stash, stashStartAt) }
        }
    }

    /** 资料更新预览“确认更新（本机）”：等对方也确认后各自写入。 */
    fun confirmUpdate() {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.UPDATE_PREVIEW) return
        // 阻塞候选未勾选：不能安全写入（会产生假的“我”），按取消处理
        if (st.candidates.any {
                it.blocking && it.remoteId !in st.candidatesSelected }) {
            cancelUpdate()
            return
        }
        approvedIds.addAll(st.candidatesSelected)
        val remote = pendingRemote
        val mid = myId
        // 勾选过候选：按批准集合重算计划再提交（候选并入映射、不再新建）
        if (remote != null && mid != null &&
            st.candidatesSelected.isNotEmpty()
        ) {
            viewModelScope.launch {
                val r = SpouseMerge.plan(
                    backup.localMergeSnapshot(), remote, mid,
                    pendingPeerSelf,
                    knownMap = pairing?.personMap.orEmpty(),
                    approved = approvedIds)
                if (r is SpouseMerge.Result.Ok) pendingCommitPlan = r.plan
                wireStatus = TapProtocol.STATUS_CONFIRM
                _ui.update {
                    it.copy(stage = TapUiState.Stage.EXCHANGING,
                        notice = null, candidates = emptyList(),
                        candidatesSelected = emptySet())
                }
                maybeCardCommit()
            }
        } else {
            wireStatus = TapProtocol.STATUS_CONFIRM
            _ui.update {
                it.copy(stage = TapUiState.Stage.EXCHANGING,
                    notice = null, candidates = emptyList(),
                    candidatesSelected = emptySet())
            }
            maybeCardCommit()
        }
    }

    /** 资料更新预览“取消”：不写入，对方同步回到等待页。 */
    fun cancelUpdate() {
        val st = _ui.value
        if (st.stage != TapUiState.Stage.UPDATE_PREVIEW) return
        // 保持 CANCEL：对方下一次轮询能看到并退回等待页
        wireStatus = TapProtocol.STATUS_CANCEL
        pendingRemote = null
        pendingCommitPlan = null
        _ui.update {
            it.copy(stage = TapUiState.Stage.WAITING,
                notice = "已取消更新",
                sessionMode = TapUiState.SessionMode.NONE,
                candidates = emptyList(),
                candidatesSelected = emptySet())
        }
    }

    /**
     * 读卡端收尾：双方都已确认后调用——提交待写计划（无计划=预演），
     * 成功后 DONE + 合心展示；本地提交失败先告知对方再进错误页，
     * 绝不在失败时显示成功。
     */
    private suspend fun commitPendingAndShow(reader: TapProtocol.TapReader) {
        committed = true
        val plan = pendingCommitPlan
        pendingCommitPlan = null
        if (plan != null) {
            commitPlan(plan,
                isUpdate = _ui.value.sessionMode ==
                    TapUiState.SessionMode.UPDATE,
                peerSelfId = pendingPeerSelf,
                payloadSha = pendingPayloadSha)
                ?: run {
                    runCatching {
                        reader.status(TapProtocol.STATUS_REJECT,
                            TapProtocol.REJECT_PAIR_MISMATCH)
                    }
                    return
                }
        }
        applyPendingLabels()
        val startAt = sharedNow() + SHOW_DELAY_MS
        runCatching { reader.done(startAt) }
        enterShow(startAt)
    }

    /** 提交成功进 SHOW 前，把备好的合心名单上屏。 */
    private fun applyPendingLabels() {
        if (pendingLabels.isEmpty()) return
        _ui.update {
            it.copy(
                newFamily = pendingLabels.take(8),
                moreFamilyCount =
                    (pendingLabels.size - 8).coerceAtLeast(0))
        }
    }

    /**
     * 卡片端：本机已确认且已收到对方 DONE（=对方提交完成）时提交待写
     * 计划（合并/更新共用此门）。读卡端先写、卡片端后写：读卡端提交
     * 失败会发 REJECT 而非 DONE，本机不落半截；本机失败则进错误页，
     * 下次碰面按“配对不一致”恢复。不在本地提交失败时显示成功。
     */
    private fun maybeCardCommit() {
        val plan = pendingCommitPlan ?: return
        if (committed || _ui.value.isReader || !peerDoneArrived) return
        if (wireStatus != TapProtocol.STATUS_CONFIRM) return
        committed = true
        pendingCommitPlan = null
        viewModelScope.launch {
            if (commitPlan(plan,
                    isUpdate = _ui.value.sessionMode ==
                        TapUiState.SessionMode.UPDATE,
                    peerSelfId = pendingPeerSelf,
                    payloadSha = pendingPayloadSha) == null
            ) {
                wireStatus = TapProtocol.STATUS_REJECT
                wireCode = TapProtocol.REJECT_PAIR_MISMATCH
                return@launch
            }
            applyPendingLabels()
            // DONE 可能先于或晚于提交；合心时刻取对方上报值
            enterShow(peerStartAt)
        }
    }

    /** 错误页“重试”与展示后“再来一次”：清空会话状态后重走 NFC 检查。 */
    fun retry() {
        if (_ui.value.roleReady && !_ui.value.isReader) card.reset()
        peerStartAt = null
        peerDoneArrived = false
        pendingCommitPlan = null
        committed = false
        negotiatedPairingId = null
        approvedIds.clear()
        candidatesDecided = false
        stashPayload = null
        pendingPeerSelf = ""
        pendingPeerSelfId = ""
        pendingRemote = null
        pendingLabels = emptyList()
        peerStatusSeen = TapProtocol.STATUS_WAIT
        wireStatus = TapProtocol.STATUS_WAIT
        wireCode = 0
        // 互加亲人：范围与本机决策一并清掉（重走时会回到用途选择）
        relScope = null
        relRemotePlan = null
        relRemoteSelfId = ""
        relKnownMap = emptyMap()
        relLocalSide = null
        relPeerAnchor = emptyMap()
        relRemoteAnchor = null
        relManual.clear(); relApproved.clear(); relTakeRemote.clear()
        _ui.update {
            it.copy(
                stage = TapUiState.Stage.CHECKING,
                error = null, notice = null,
                peerName = "", peerFamilyCount = 0, peerSpouseName = null,
                sessionRehearsal = false, sessionMode = TapUiState.SessionMode.NONE,
                confirm = TapUiState.Confirm.NONE,
                clockOffsetMs = 0, showStartAt = null,
                newFamily = emptyList(), moreFamilyCount = 0,
                memorialLine = null, memorialAnniversary = false,
                memorialHint = null, peerConfirming = false,
                candidates = emptyList(), candidatesSelected = emptySet(),
                leftHalf = it.isMale,
                tapKind = entryKind,
                scopeWarn = null, relPeerSelfName = "",
                relPeerItems = emptyList(), relPickSelected = emptySet(),
                relPickWarn = null, relError = null, relPlan = null,
                relNewTitles = emptyMap(), relManualNames = emptyMap(),
                relNewAliases = 0, relBusy = false, relDoneText = "")
        }
        refreshStage()
    }

    /** 重播动画（只重演仪式，不重新导入任何数据）。 */
    fun replay() {
        if (_ui.value.stage != TapUiState.Stage.SHOW) return
        _ui.update {
            it.copy(showStartAt = sharedNow() + SHOW_DELAY_MS)
        }
    }

    /** 进入合心展示；成功后记住本机这次使用的连接方式。 */
    private fun enterShow(startAt: Long?) {
        _ui.update {
            it.copy(
                stage = TapUiState.Stage.SHOW,
                showStartAt = startAt ?: peerStartAt
                    ?: (sharedNow() + SHOW_DELAY_MS))
        }
        viewModelScope.launch {
            pairingStore.rememberRole(_ui.value.isReader)
        }
    }

    /** 纪念模式文案（双方 HELLO 的同类型纪念日一致才有数字）。 */
    private fun memorialText(h: TapProtocol.Hello): String {
        val d = TapMemorial.display(myDates, h.dates,
            LocalDate.now(), lunar)
        return TapMemorial.text(d)
    }

    private fun memorialAnniv(h: TapProtocol.Hello) =
        TapMemorial.display(myDates, h.dates, LocalDate.now(), lunar)
            .isAnniversary

    /** 纪念页底部提示：日期核对 + 单方更新意愿说明（可叠加）。 */
    private fun memorialHint(h: TapProtocol.Hello, updateNote: String?) =
        listOfNotNull(
            if (TapMemorial.display(
                    myDates, h.dates, LocalDate.now(), lunar)
                    .dateCheckHint)
                "两边记的纪念日不一致，回家在家人详情里核对" else null,
            updateNote
        ).joinToString("\n").ifBlank { null }

    // ---------------- 资料包处理与提交 ----------------

    /** 卡片端收到对方完整资料包（PUT_DATA 收齐回调）。 */
    private suspend fun onCardPeerPayload(bytes: ByteArray) {
        val st = _ui.value
        when (st.sessionMode) {
            TapUiState.SessionMode.UPDATE -> prepareUpdatePreview(bytes)
            // 互加亲人：对方包收齐即进本地决策（DONE 随后到，无需等它）
            TapUiState.SessionMode.RELATIVES -> enterRelatives(bytes)
            else -> completeExchange(bytes, startAt = null)
        }
    }

    /** 资料更新：双方资料已交换，各自算计划并出预览（不写库）。 */
    private suspend fun prepareUpdatePreview(deflated: ByteArray) {
        val mid = myId ?: return
        try {
            val zip = backup.inflatePayload(deflated)
            val plan = BackupJson.parsePlan(zip)
            val peerSelf = plan.sharedBy ?: plan.persons.first { it.isSelf }.id
            val local = backup.localMergeSnapshot()
            val remote = backup.mergeSnapshotOf(plan)
            when (val r = SpouseMerge.plan(
                local, remote, mid, peerSelf,
                knownMap = pairing?.personMap.orEmpty(),
                approved = approvedIds)) {
                is SpouseMerge.Result.Reject -> {
                    wireStatus = TapProtocol.STATUS_REJECT
                    wireCode = TapProtocol.REJECT_PAIR_MISMATCH
                    _ui.update {
                        it.copy(stage = TapUiState.Stage.ERROR,
                            error = r.reason)
                    }
                }
                is SpouseMerge.Result.Ok -> {
                    pendingCommitPlan = r.plan
                    pendingPeerSelf = peerSelf
                    pendingPayloadSha = TapProtocol.toHex(
                        TapProtocol.sha256(deflated))
                    pendingRemote = remote
                    wireStatus = TapProtocol.STATUS_NEED_CONFIRM
                    _ui.update {
                        it.copy(
                            stage = TapUiState.Stage.UPDATE_PREVIEW,
                            updateNew = r.plan.newPersons.size,
                            updateNewRels = r.plan.newRelationships.size,
                            updateNewPhones = r.plan.newPhones.size,
                            updateNewDates = r.plan.newDates.size,
                            updateMatched = r.plan.idMap.size,
                            updateConflicts = r.plan.conflicts.map { c ->
                                conflictText(c)
                            },
                            candidates = r.plan.candidates,
                            candidatesSelected = emptySet())
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(stage = TapUiState.Stage.ERROR,
                    error = "对方资料无法解析")
            }
        }
    }

    private fun conflictText(c: SpouseMerge.PlanConflict) =
        if (c.field == "name") {
            c.localName + "：对方记录的名字是「" + c.remoteValue +
                "」，本机保留「" + c.localValue + "」"
        } else {
            c.localName + " 的" + dateTypeText(c.field) +
                "不一致：本机 " + c.localValue + "，对方 " + c.remoteValue
        }

    private fun dateTypeText(t: String) = when (t) {
        "BIRTHDAY" -> "生日"
        "DEATH_ANNIVERSARY" -> "忌日"
        "WEDDING_ANNIVERSARY" -> "结婚纪念日"
        else -> t
    }

    /**
     * 收到对方资料包：inflate → parsePlan 校验 → 合并计划 →
     * （非预演则）写入 → 合心展示。已在 SHOW 时忽略（续传重复完成）。
     * 重放同一资料：计划为空 → 不写库，直接展示。
     */
    private suspend fun completeExchange(deflated: ByteArray, startAt: Long?) {
        if (_ui.value.stage == TapUiState.Stage.SHOW) return
        val mid = myId ?: return
        try {
            val zip = backup.inflatePayload(deflated)
            val plan = BackupJson.parsePlan(zip)
            val peerSelf = plan.sharedBy ?: plan.persons.first { it.isSelf }.id
            val local = backup.localMergeSnapshot()
            val remote = backup.mergeSnapshotOf(plan)
            when (val r = SpouseMerge.plan(
                local, remote, mid, peerSelf,
                knownMap = pairing?.personMap.orEmpty(),
                approved = approvedIds)) {
                is SpouseMerge.Result.Reject -> {
                    wireStatus = TapProtocol.STATUS_REJECT
                    wireCode = TapProtocol.REJECT_PAIR_MISMATCH
                    _ui.update {
                        it.copy(stage = TapUiState.Stage.ERROR,
                            error = r.reason)
                    }
                }
                is SpouseMerge.Result.Ok -> {
                    // 同名同性别同位置的“疑似同一人”只是候选：
                    // 先给用户勾选确认（预演不写库，跳过询问）；
                    // 确认后由协议第二遍提交时重算计划
                    val undecided = r.plan.candidates.filter {
                        it.remoteId !in approvedIds }
                    if (undecided.isNotEmpty() && !candidatesDecided &&
                        !_ui.value.sessionRehearsal
                    ) {
                        candidatesDecided = true
                        stashPayload = deflated
                        stashStartAt = startAt
                        wireStatus = TapProtocol.STATUS_NEED_CONFIRM
                        _ui.update {
                            it.copy(
                                stage = TapUiState.Stage.CONFIRMING,
                                confirm =
                                    TapUiState.Confirm.FAMILY_CANDIDATES,
                                candidates = undecided,
                                candidatesSelected =
                                    undecided.map { c -> c.remoteId }.toSet())
                        }
                        return
                    }
                    // 计划就绪：写入挂到“双方都确认”门（读卡端主循环 /
                    // 卡片端 maybeCardCommit）——一端取消/断开时另一端不落
                    // 半截数据；合心名单先备好，提交成功进 SHOW 时才上屏
                    if (_ui.value.sessionMode != TapUiState.SessionMode.UPDATE) {
                        pendingLabels = TapReveal.labels(
                            local, r.plan, mid, engine, lunar
                        ).map { TapUiState.FamilyLabel(it.title, it.name) }
                    }
                    if (!_ui.value.sessionRehearsal) {
                        pendingCommitPlan = r.plan
                        pendingPeerSelf = peerSelf
                        pendingPayloadSha = TapProtocol.toHex(
                            TapProtocol.sha256(deflated))
                    }
                    wireStatus = TapProtocol.STATUS_CONFIRM
                    if (!_ui.value.isReader) maybeCardCommit()
                    // 读卡端：主循环下一轮看到双方 CONFIRM 即提交
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(stage = TapUiState.Stage.ERROR,
                    error = "对方资料无法解析")
            }
        }
    }

    /**
     * 写入合并计划 + 配对/映射记录，两者在同一个 Room 事务内提交：
     * 任一步失败整体回滚，不会留下“家人已进库但配对没记上”的半截状态；
     * 取消异常照常向上传播。返回 null 表示失败（UI 已转错误页）。
     */
    private suspend fun commitPlan(
        plan: SpouseMerge.MergePlan,
        isUpdate: Boolean,
        peerSelfId: String = "",
        payloadSha: String? = null
    ): Unit? {
        val pid = negotiatedPairingId ?: pairing?.pairingId
        val peerSid = if (peerSelfId.isNotEmpty()) peerSelfId
            else pairing?.peerSelfId.orEmpty()
        // 对方“我”在本机对应的人物：可能在 idMap（旧配偶记录复用），
        // 也可能在 mapping（本次新建的人物）——两者都覆盖
        val spouseLocal = plan.mapping[peerSid] ?: mySpouseId
        val recordPairing = pid != null && peerSid.isNotEmpty() &&
            spouseLocal != null
        try {
            // 同一资料重放：计划为空时 insert 全空，事务内仅刷新配对记录
            backup.importMerge(plan) {
                if (recordPairing) {
                    pairingStore.recordMerge(
                        pairingId = pid.orEmpty(),
                        peerSelfId = peerSid,
                        localSpouseId = spouseLocal.orEmpty(),
                        peerName = _ui.value.peerName,
                        mapping = plan.mapping,
                        sessionId = card.session.takeIf { it != 0 },
                        payloadSha = payloadSha,
                        isReader = _ui.value.isReader)
                }
            }
            if (recordPairing) {
                pairing = pairingStore.current()
                _ui.update { it.copy(paired = true) }
            }
            return Unit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update {
                it.copy(stage = TapUiState.Stage.ERROR,
                    error = "合并失败：" + (e.message ?: "未知错误"))
            }
            return null
        }
    }

    override fun onCleared() {
        session.deactivate()
    }

}
