package io.github.xiaohaoz1.jiarenxia.data.collab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.BuildConfig
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorage
import io.github.xiaohaoz1.jiarenxia.data.normalizePhone
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupCrypto
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupException
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupJson
import io.github.xiaohaoz1.jiarenxia.data.backup.ChainLink
import io.github.xiaohaoz1.jiarenxia.data.backup.CollabMeta
import io.github.xiaohaoz1.jiarenxia.data.backup.QuickSnapshotStore
import io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyCollabStateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyLocalOnlyEntity
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyPublicationEntity
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyReceiveDraftEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyRevisionPlan
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 协作流程错误（message 为可直接展示的中文文案）。 */
class CollabException(message: String) : Exception(message)

/**
 * 离线协作编排（FAMILY_AFFAIRS §5，协作改版）：
 *
 * - 汇总者：[exportConfirmed] 发布确认版（版本链推进 + 基准推进）、
 *   [prepareReceive]/[applyReceived] 接收成员补充包；
 * - 成员：[exportContribution] 发出补充包（自带基准快照与 id 映射）、
 *   [prepareReceive]/[applyReceived] 接收确认版；
 * - 三方比较/决议在 [FamilyRevisionPlan]（纯 JVM），写库唯一入口是
 *   [FamilyRepository.applyFamilyRevision]（单事务 + 名册哈希复核 +
 *   草稿 PENDING 复核 + 关系约束复核 + 私人外键复核）；
 * - 应用前经 [QuickSnapshotStore] 写“接收前副本”候选，事务提交成功才生效；
 * - 收到的加密包暂存 filesDir/collab_incoming（不存密码），草稿行存
 *   计划缓存与已做决策，退出后可续。
 */
@Singleton
class FamilyCollaborationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val repo: FamilyRepository,
    private val snapshots: QuickSnapshotStore,
    private val avatarStorage: AvatarStorage,
    private val ids: IdGenerator,
    private val clock: TimeSource
) {
    /** 接收暂存目录名（测试可改以隔离；生产固定 collab_incoming）。 */
    internal var incomingDirName: String = "collab_incoming"
    /** “我是谁”选择里的“都不包括我”持久化哨兵（界面/草稿共用字面量）。 */
    internal val SELF_NONE = "none"
    private val incomingDir get() = File(context.filesDir, incomingDirName)
    private val draftDao get() = db.familyReceiveDraftDao()
    private val stateDao get() = db.familyCollabDao()
    private val pubDao get() = db.familyPublicationDao()
    private val localOnlyDao get() = db.familyLocalOnlyDao()

    /** 草稿选择写锁：保存/续审刷写串行，防并发保存互踩序号。 */
    private val draftWriteMutex = kotlinx.coroutines.sync.Mutex()

    /** 测试钩子：保存通过状态/序号检查后、落库前挂起（交错复现用）。 */
    internal var saveGate: (suspend () -> Unit)? = null

    // ==================== 状态 ====================

    /** 协作状态总览（家庭页“共享与连接”/协作区展示用）。 */
    suspend fun status(): Status = withContext(Dispatchers.IO) {
        val st = stateDao.get()
        val pending = draftDao.getAll().firstOrNull { it.status == "PENDING" }
        Status(
            bound = st != null,
            role = st?.role,
            seriesId = st?.seriesId,
            revision = st?.acceptedRevision ?: 0,
            pendingDraftPackageId = pending?.packageId,
            pendingDraftMode = pending?.mode,
            localOnlyCount = localOnlyDao.getIds().size,
            hasPrereceive = snapshots.hasPrereceive()
        )
    }

    data class Status(
        val bound: Boolean,
        /** "coordinator" / "member"，未绑定为 null。 */
        val role: String?,
        val seriesId: String?,
        val revision: Int,
        val pendingDraftPackageId: String?,
        val pendingDraftMode: String?,
        val localOnlyCount: Int,
        /** 是否存在“上次接收前”的保护副本。 */
        val hasPrereceive: Boolean
    )

    /** 观察“仅本机保留”人物 id 集。 */
    fun observeLocalOnlyIds(): Flow<Set<String>> = repo.observeLocalOnlyIds()

    /** “仅本机保留”名单（人物 + 备注）。 */
    suspend fun localOnlyPersons(): List<Pair<PersonEntity, FamilyLocalOnlyEntity>> =
        withContext(Dispatchers.IO) {
            localOnlyDao.getAll().mapNotNull { lo ->
                db.personDao().getById(lo.personId)?.let { it to lo }
            }
        }

    /** 把本机人物标为“仅本机保留”（不进名册快照/确认版/默认分享）。 */
    suspend fun markLocalOnly(personId: String, note: String? = null) =
        withContext(Dispatchers.IO) {
            val roster = repo.canonicalRosterSnapshot()
            if (personId in roster.localOnlyIds) return@withContext
            val canon = roster.canonicalToLocal.entries
                .firstOrNull { it.value == personId }?.key
            val r = repo.applyFamilyRevision(CollabApply(
                expectedRosterHash = roster.hash,
                markLocalOnly = listOf(FamilyLocalOnlyEntity(
                    personId = personId, canonicalId = canon,
                    note = note ?: "手动设为仅本机保留", createdAt = clock.now()))
            ))
            if (r is FamilyResult.Failure) throw CollabException(r.error.message)
        }

    /** 解除“仅本机保留”（重新加入名册快照）。 */
    suspend fun unmarkLocalOnly(personId: String) = withContext(Dispatchers.IO) {
        val roster = repo.canonicalRosterSnapshot()
        if (personId !in roster.localOnlyIds) return@withContext
        val r = repo.applyFamilyRevision(CollabApply(
            expectedRosterHash = roster.hash,
            unmarkLocalOnly = listOf(personId)
        ))
        if (r is FamilyResult.Failure) throw CollabException(r.error.message)
    }

    // ==================== 发布 / 发出 ====================

    /**
     * 汇总者发布确认版：当前名册快照打包为 version N+1 的协作包写入 [out]，
     * 随后同一服务内推进协作状态（基准快照换成本版、版本索引登记）。
     * 返回写入的元数据（界面展示版本号）。
     */
    suspend fun exportConfirmed(
        out: OutputStream, password: CharArray
    ): CollabMeta = withContext(Dispatchers.IO) {
        val roster = repo.canonicalRosterSnapshot()
        if (roster.snap.persons.isEmpty()) {
            throw CollabException("家庭名册还是空的，先录入家人再发布")
        }
        val state = stateDao.get()
        if (state != null && state.role != "coordinator") {
            throw CollabException("这台设备登记为成员，确认版由汇总者发布")
        }
        val self = db.personDao().getSelf()
            ?: throw CollabException("先在家庭里建好「我」再发布")
        val coordinatorCanon = canonOf(roster, self.id)
        val pubs = pubDao.getAll().sortedBy { it.revision }
        // 同一共享快照重复发布复用当前版（审查 R7）：只重打包不推版号，
        // 也不新增发布记录；共享内容变了才推进
        val reused = state != null && state.acceptedHash == roster.hash &&
            pubs.isNotEmpty() &&
            pubs.last().revision == state.acceptedRevision
        val rev = if (reused) state.acceptedRevision
            else (state?.acceptedRevision ?: 0) + 1
        val seriesId = state?.seriesId ?: ids.newId()
        val packageId = ids.newId()
        val parentHash = if (reused) pubs.last().parentHash
            else state?.acceptedHash
        val chain = pubs.map {
            ChainLink(it.revision, it.snapshotHash, it.parentHash) } +
            (if (reused) emptyList()
            else listOf(ChainLink(rev, roster.hash, parentHash)))
        val meta = CollabMeta(
            mode = "confirmed", seriesId = seriesId,
            coordinatorPersonId = coordinatorCanon, packageId = packageId,
            revision = rev, parentRevision = rev - 1,
            parentHash = parentHash, snapshotHash = roster.hash,
            revisionChain = chain,
            baseRevision = null, baseHash = null, baseSnapshot = null,
            idMap = emptyMap(), scope = "full"
        )
        writeCollabZip(out, password, roster.snap, meta)
        if (!reused) {
            // 基准推进：accepted/base/版本索引在单个协作事务里落库
            val removed = computeRemoved(state, roster.snap)
            val newState = FamilyCollabStateEntity(
                id = 1, seriesId = seriesId, role = "coordinator",
                coordinatorPersonId = coordinatorCanon,
                acceptedRevision = rev, acceptedHash = roster.hash,
                baseSnapshotJson = FamilySnapshot.toJson(roster.snap)
                    .toString(),
                idMapJson = idMapJsonOf(roster.canonicalToLocal),
                removedIdsJson = removedIdsJsonOf(removed),
                updatedAt = clock.now()
            )
            val r = repo.applyFamilyRevision(CollabApply(
                expectedRosterHash = roster.hash,
                stateWrite = newState,
                publication = FamilyPublicationEntity(
                    revision = rev, packageId = packageId,
                    snapshotHash = roster.hash,
                    parentRevision = rev - 1, parentHash = parentHash,
                    createdAt = clock.now())
            ))
            if (r is FamilyResult.Failure) {
                throw CollabException(r.error.message)
            }
        }
        meta
    }

    /**
     * 成员发出补充包：本机名册规范快照 + 已接受基准（版本/哈希/快照）+
     * 规范→本机 id 映射；[out] 收到加密 ZIP。
     */
    suspend fun exportContribution(
        out: OutputStream, password: CharArray
    ): Unit = withContext(Dispatchers.IO) {
        val state = stateDao.get()
            ?: throw CollabException("还没加入协作系列：先接收一次确认版")
        if (state.role != "member") {
            throw CollabException("汇总者不需要给自己发补充包，直接发布确认版")
        }
        val roster = repo.canonicalRosterSnapshot()
        if (roster.snap.persons.isEmpty()) {
            throw CollabException("家庭名册是空的，没有可补充的内容")
        }
        val meta = CollabMeta(
            mode = "contribution", seriesId = state.seriesId,
            coordinatorPersonId = state.coordinatorPersonId,
            packageId = ids.newId(),
            revision = 0, parentRevision = null, parentHash = null,
            snapshotHash = null, revisionChain = emptyList(),
            baseRevision = state.acceptedRevision,
            baseHash = state.acceptedHash,
            baseSnapshot = state.baseSnapshotJson,
            idMap = roster.canonicalToLocal, scope = "full",
            sender = db.personDao().getSelf()?.name
        )
        writeCollabZip(out, password, roster.snap, meta)
    }

    // ==================== 接收 ====================

    /** 接收预览（决策页数据）。[resumed]=true 表示续上了已有草稿。 */
    data class ReceivePreview(
        val draft: FamilyReceiveDraftEntity,
        val meta: CollabMeta,
        val plan: FamilyRevisionPlan.Plan,
        val resumed: Boolean,
        /** 本机是否已有“我”（首次绑定没有 → 应用时要选“我是哪位”）。 */
        val hasLocalSelf: Boolean
    )

    /** 用于界面展示的轻量计划项（草稿缓存里重建）。 */
    data class CachedItem(
        val key: String, val state: String, val text: String,
        val personId: String,
        /** 身份候选：本机规范 id、姓名、命中理由（同名/共同亲属）。 */
        val candidates: List<Triple<String, String, String>> = emptyList(),
        /** 决策指纹：选择绑定的资料取值（R5 失效核对用）。 */
        val fingerprint: String? = null
    )

    /** 草稿持久化的全部接收选择（R9：随草稿恢复 + R5 失效核对）。 */
    data class DraftChoices(
        val decisions: Map<String, String> = emptyMap(),
        /** key → 决策做下时的计划项指纹。 */
        val fingerprints: Map<String, String> = emptyMap(),
        val selfCanonicalChoice: String? = null,
        val dropPending: Boolean = false,
        val localOnlyMarks: Set<String> = emptySet(),
        /** 放弃补充/仅本机勾选所见的待汇入项指纹集（换上下文失效）。 */
        val pendingScope: String? = null,
        /** 选择集单调递增序号（防晚完成的旧保存覆盖新选择）。 */
        val seq: Int = 0
    )

    /** 待处理草稿（无则 null）。 */
    suspend fun pendingDraft(): FamilyReceiveDraftEntity? =
        withContext(Dispatchers.IO) {
            draftDao.getAll().firstOrNull { it.status == "PENDING" }
        }

    /** 读草稿缓存的计划项（不重解密）。 */
    suspend fun draftPlanCache(packageId: String): List<CachedItem> =
        withContext(Dispatchers.IO) {
            val d = draftDao.get(packageId) ?: return@withContext emptyList()
            parsePlanCache(d.planCacheJson)
        }

    /** 草稿已做决策（key → wire）。 */
    suspend fun draftDecisions(packageId: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val d = draftDao.get(packageId) ?: return@withContext emptyMap()
            parseDecisions(d.decisionsJson)
        }

    /** 草稿缓存里的远端名册（规范 id → 姓名；选“我是谁”用）。 */
    suspend fun draftRoster(packageId: String): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            val d = draftDao.get(packageId) ?: return@withContext emptyList()
            parseRosterCache(d.planCacheJson)
        }

    /** 草稿缓存里的补充发送者名（“来自 X 的补充”；确认版为 null）。 */
    suspend fun draftSender(packageId: String): String? =
        withContext(Dispatchers.IO) {
            val d = draftDao.get(packageId) ?: return@withContext null
            d.planCacheJson?.let {
                runCatching {
                    JSONObject(it).optString("sender").takeIf { s -> s.isNotBlank() }
                }.getOrNull()
            }
        }

    /** 草稿缓存里的汇总者规范 id（“X 整理的家庭确认版”题名用）。 */
    suspend fun draftCoordinator(packageId: String): String? =
        withContext(Dispatchers.IO) {
            val d = draftDao.get(packageId) ?: return@withContext null
            d.planCacheJson?.let {
                runCatching {
                    JSONObject(it).optString("coordinator")
                        .takeIf { s -> s.isNotBlank() }
                }.getOrNull()
            }
        }

    /**
     * 本机相对已接受基准的差异项数（5.5 参与者副文案“尚有 N 项与基准不同”）。
     * 未绑定/无基准为 0。
     */
    suspend fun pendingDiffCount(): Int = withContext(Dispatchers.IO) {
        val state = stateDao.get() ?: return@withContext 0
        val base = state.baseSnapshotJson.let {
            runCatching { FamilySnapshot.parse(JSONObject(it)) }.getOrNull()
        } ?: return@withContext 0
        val roster = repo.canonicalRosterSnapshot()
        val p = FamilyRevisionPlan.plan(
            base, base, roster.snap,
            FamilyRevisionPlan.Ctx(
                selfCanonicalId = selfCanonOf(roster),
                removedIds = removedIdsOf(state),
                identities = emptyMap()))
        p.pendingLocal.size
    }

    /**
     * 持久化接收选择（随写随存，退出不丢；R9 的我是谁/仅本机/放弃
     * 补充一并入草稿）。决策附带计划项指纹——R5 失效核对用。
     *
     * 写序约定：只在草稿仍 PENDING 时写；[seq] 非 0 时必须严格大于
     * 已存序号，晚完成的旧保存被丢弃，不会覆盖较新选择。
     */
    suspend fun saveDecisions(
        packageId: String,
        decisions: Map<String, String>,
        selfCanonicalChoice: String? = null,
        dropPending: Boolean = false,
        localOnlyMarks: Set<String> = emptySet(),
        seq: Int = 0
    ) = withContext(Dispatchers.IO) {
        // 草稿写串行 + 条件落库：检查与写入之间不再给“应用落 APPLIED
        // 后又被整行写回 PENDING”留交错窗口（审查边界 1）
        draftWriteMutex.withLock {
            val d = draftDao.get(packageId) ?: return@withLock
            if (d.status != "PENDING") return@withLock
            val stored = parseChoices(d.decisionsJson)
            if (seq != 0 && seq <= stored.seq) return@withLock
            saveGate?.invoke()
            val cache = parseFullPlanCache(d.planCacheJson)
            val fp = cache?.items.orEmpty()
                .mapNotNull { it.fingerprint?.let { f -> it.key to f } }
                .toMap()
            // 身份裁决（add: 键）经 Same 并入重算后不再出现在缓存
            // 计划里：与续审/应用同一套两阶段核验单独确认——凭据仍
            // 对得上的裁决独立保留（调用方未提及也按已存值续存，
            // 传了以传入值为准），凭据失效的一律丢弃
            val addKeys = (decisions.keys + stored.decisions.keys)
                .filterTo(LinkedHashSet()) {
                    it.startsWith("add:") && it !in fp }
            val identV = if (addKeys.isEmpty() || cache?.remote == null) {
                null
            } else {
                val roster = repo.canonicalRosterSnapshot()
                validatedPlan(cache.base, cache.remote, roster.snap,
                    FamilyRevisionPlan.Ctx(
                        selfCanonicalId = selfCanonOf(roster),
                        removedIds = removedIdsOf(stateDao.get())),
                    stored)
            }
            val merged = LinkedHashMap<String, String>()
            decisions.forEach { (k, v) ->
                if (k in fp ||
                    (k in addKeys &&
                        identV?.decisions?.containsKey(k) == true)) {
                    merged[k] = v
                }
            }
            identV?.decisions?.forEach { (k, v) ->
                if (k in addKeys) merged.putIfAbsent(k, v)
            }
            // 只收当前计划里真实存在的项与仍有效的身份裁决；残留键
            // 不算有效确认。WHERE status='PENDING' 单语句条件更新：
            // 即使应用事务在检查之后已落 APPLIED，这条更新也命中
            // 不了，不回写状态
            draftDao.updateChoicesIfPending(
                packageId, decisionsJsonOf(DraftChoices(
                    decisions = merged,
                    fingerprints = merged.keys
                        .mapNotNull { k ->
                            (fp[k] ?: identV?.fingerprints?.get(k))
                                ?.let { k to it } }
                        .toMap(),
                    selfCanonicalChoice = selfCanonicalChoice,
                    dropPending = dropPending,
                    localOnlyMarks = localOnlyMarks,
                    pendingScope = cache?.let {
                        pendingScopeOfCached(it.items) },
                    seq = maxOf(seq, stored.seq))),
                clock.now())
        }
    }

    /** 草稿持久化的全部接收选择（R9 恢复用）。 */
    suspend fun draftChoices(packageId: String): DraftChoices =
        withContext(Dispatchers.IO) {
            val d = draftDao.get(packageId)
                ?: return@withContext DraftChoices()
            parseChoices(d.decisionsJson)
        }

    /** 续草稿时重算后的审阅内容（null = 草稿不存在或非待处理）。 */
    data class ResumedReview(
        val draft: FamilyReceiveDraftEntity,
        /** 按当前本机资料重算出的计划项（含指纹）。 */
        val items: List<CachedItem>,
        /** 待汇入项。 */
        val pendingLocal: List<CachedItem>,
        /** 仍有效的旧决策（指纹仍匹配）。 */
        val decisions: Map<String, String>,
        /** 因资料变化被作废的旧决策数。 */
        val invalidCount: Int,
        val selfCanonicalChoice: String?,
        val dropPending: Boolean,
        val localOnlyMarks: Set<String>,
        /** 远端名册（规范 id → 姓名；“我是谁”候选列表）。 */
        val remoteRoster: List<Pair<String, String>>,
        /** 计划里还有没有“我”（首次绑定提示）。 */
        val hasLocalSelf: Boolean,
        /** 本次预览的计划令牌；apply 提交须携回以证明看过此预览。 */
        val planToken: String? = null,
        /** 草稿已存选择的单调序号：界面续写从这里继续（跨进程重建不丢）。 */
        val choicesSeq: Int = 0
    )

    /**
     * 续草稿（R5/R9）：不重新解密包，用草稿缓存的基准/远端快照与
     * 当前本机名册重算计划；旧决策逐项按指纹核对——对不上的作废
     * 返回给界面重新确认，并同步刷写草稿缓存与决策。
     */
    suspend fun resumeReview(packageId: String): ResumedReview? =
        withContext(Dispatchers.IO) {
            val d = draftDao.get(packageId)
                ?: return@withContext null
            if (d.status != "PENDING") return@withContext null
            val cache = parseFullPlanCache(d.planCacheJson)
                ?: return@withContext null
            val remote = cache.remote ?: return@withContext null
            val roster = repo.canonicalRosterSnapshot()
            val state = stateDao.get()
            val stored = parseChoices(d.decisionsJson)
            // 旧格式草稿没有指纹：资料没变可用，变了全部要求重确认
            val legacyStale = stored.fingerprints.isEmpty() &&
                stored.decisions.isNotEmpty() &&
                d.rosterHash != roster.hash
            val ctx0 = FamilyRevisionPlan.Ctx(
                selfCanonicalId = selfCanonOf(roster),
                removedIds = removedIdsOf(state))
            val v = validatedPlan(cache.base, remote, roster.snap, ctx0,
                if (legacyStale) DraftChoices() else stored)
            val pendingIds = v.plan.pendingLocal
                .mapTo(HashSet()) { it.personId }
            // 放弃补充/仅本机勾选绑定保存时的待汇入上下文：其后新增
            // 或修改的待汇入项未经核对，旧全局选择不得覆盖。
            val scopeNow = pendingScope(v.plan)
            val scopeOk = v.plan.pendingLocal.isNotEmpty() &&
                stored.pendingScope != null &&
                stored.pendingScope == scopeNow
            val marks = if (scopeOk) stored.localOnlyMarks.filterTo(
                LinkedHashSet()) { it in pendingIds } else LinkedHashSet()
            val self = stored.selfCanonicalChoice
                ?.takeIf { it == SELF_NONE ||
                    it in remote.persons.map { p -> p.id } }
            // 刷写草稿：缓存换新计划（含新指纹），决策只留仍有效的。
            // 条件更新：交错下应用已落 APPLIED 则本次不写（不回写状态）
            val newChoices = DraftChoices(
                decisions = v.decisions,
                fingerprints = v.decisions.keys
                    .mapNotNull { k -> v.fingerprints[k]?.let { k to it } }
                    .toMap(),
                selfCanonicalChoice = self,
                dropPending = stored.dropPending && scopeOk,
                localOnlyMarks = marks,
                pendingScope = stored.pendingScope,
                seq = stored.seq)
            val written = draftWriteMutex.withLock {
                draftDao.updateReviewIfPending(
                    d.packageId,
                    planCacheJsonOf(v.plan, remote, cache.base,
                        cache.sender, cache.coordinator),
                    decisionsJsonOf(newChoices),
                    roster.hash, clock.now()) > 0
            }
            if (!written) return@withContext null   // 已被应用/丢弃
            val items = parsePlanCache(
                planCacheJsonOf(v.plan, remote, cache.base))
            val split = items.size - v.plan.pendingLocal.size
            ResumedReview(
                draft = d,
                items = items.subList(0, split),
                pendingLocal = items.subList(split, items.size),
                decisions = v.decisions,
                invalidCount = stored.decisions.size - v.decisions.size,
                selfCanonicalChoice = self,
                dropPending = stored.dropPending && scopeOk,
                localOnlyMarks = marks,
                remoteRoster = cache.roster,
                hasLocalSelf = selfCanonOf(roster) != null,
                planToken = reviewToken(cache.base, remote, roster.snap, ctx0),
                choicesSeq = stored.seq)
        }

    /** 当前预览令牌：调用方以此证明看过最新预览（apply 核验用）。 */
    suspend fun reviewToken(packageId: String): String? =
        withContext(Dispatchers.IO) {
            val d = draftDao.get(packageId) ?: return@withContext null
            if (d.status != "PENDING") return@withContext null
            val cache = parseFullPlanCache(d.planCacheJson)
                ?: return@withContext null
            val remote = cache.remote ?: return@withContext null
            val roster = repo.canonicalRosterSnapshot()
            val state = stateDao.get()
            reviewToken(cache.base, remote, roster.snap,
                FamilyRevisionPlan.Ctx(
                    selfCanonicalId = selfCanonOf(roster),
                    removedIds = removedIdsOf(state)))
        }

    /** 放弃草稿：删行与暂存文件。 */
    suspend fun discardDraft(packageId: String) = withContext(Dispatchers.IO) {
        draftDao.get(packageId)?.let {
            File(it.filePath).delete()
            draftDao.delete(packageId)
        }
    }

    /** 清理全部接收暂存文件（清空数据/整库恢复调用）。 */
    fun clearIncoming() {
        incomingDir.deleteRecursively()
    }

    /**
     * 接收第一步：解密校验 → 幂等/版本链检查 → 三方比较 → 暂存加密包 +
     * 建 PENDING 草稿（计划缓存入行）。
     */
    suspend fun prepareReceive(
        bytes: ByteArray, password: CharArray
    ): ReceivePreview = withContext(Dispatchers.IO) {
        val plan = parseIncoming(bytes, password)
        val meta = plan.collaboration
            ?: throw CollabException("这不是协作包，请走“接收家人分享”")
        val remote = snapOf(plan)
        val contentHash = contentHash(meta.packageId, remote)

        // 幂等：同 packageId 同内容重投 → 续草稿；已处理 → 提示；
        // 同 id 不同内容 → 拒绝
        draftDao.get(meta.packageId)?.let { d ->
            when {
                d.contentHash != contentHash ->
                    throw CollabException("这个标识的协作包内容与上次不一致")
                d.status == "APPLIED" ->
                    throw CollabException("这份协作包已经接收过了")
            }
        }

        // 单草稿规则（5.8）：已有另一份待核实草稿时必须先处理或明确放弃
        draftDao.getAll().firstOrNull {
            it.status == "PENDING" && it.packageId != meta.packageId
        }?.let {
            throw CollabException("还有一份资料待核实，请先处理或放弃它再接收新文件")
        }

        val state = stateDao.get()
        checkBinding(meta, state)
        val base = baseOf(meta, state)
        val roster = repo.canonicalRosterSnapshot()
        // 续草稿：旧决策先按当前资料核验（R5），有效的身份裁决并入 Ctx
        val old = draftDao.get(meta.packageId)
        val stored = old?.let { parseChoices(it.decisionsJson) }
            ?: DraftChoices()
        val v = validatedPlan(base, remote, roster.snap,
            FamilyRevisionPlan.Ctx(
                selfCanonicalId = selfCanonOf(roster),
                removedIds = removedIdsOf(state)),
            stored)
        val p = v.plan

        // 暂存加密包 + 写 PENDING 草稿（同包重投时覆盖刷新）
        incomingDir.mkdirs()
        val staged = File(incomingDir, meta.packageId + ".jrxb")
        staged.writeBytes(bytes)
        val now = clock.now()
        val draft = FamilyReceiveDraftEntity(
            packageId = meta.packageId, contentHash = contentHash,
            mode = meta.mode, seriesId = meta.seriesId,
            revision = meta.revision, rosterHash = roster.hash,
            baseRevision = meta.baseRevision, baseHash = meta.baseHash,
            // 失效的旧决策不回写（界面重新确认）
            decisionsJson = old?.let {
                decisionsJsonOf(parseChoices(it.decisionsJson).copy(
                    decisions = v.decisions,
                    fingerprints = v.decisions.keys.mapNotNull { k ->
                        v.fingerprints[k]?.let { k to it } }.toMap()))
            } ?: "{}",
            filePath = staged.absolutePath,
            status = "PENDING",
            planCacheJson = planCacheJsonOf(
                p, remote, base, meta.sender, meta.coordinatorPersonId),
            createdAt = old?.createdAt ?: now, updatedAt = now
        )
        draftDao.upsert(draft)
        ReceivePreview(
            draft = draft, meta = meta, plan = p,
            resumed = old != null,
            hasLocalSelf = selfCanonOf(roster) != null
        )
    }

    /**
     * 接收第二步（确认应用）：重新解密 → 哈希复核 → 三方比较 + 决议 →
     * 翻译为本机写入 → 保护副本候选 → 单事务应用 → 副本生效。
     *
     * [decisions]：决策 key → wire（身份项 "same:<规范id>"/"new"；
     * 其余 "keep"/"take"/"skip"）。
     * [decisions]/[localOnlyMarks]/[dropPending] 是“一份完整、明确的
     * 最新选择”：调用方给了就按给定值（空集/false 也是明确选择），
     * 没给（null）才回退草稿持久化值；不做 OR 或并集。
     * [selfCanonicalChoice]：首次绑定且本机无“我”时，选远端哪位规范人物
     * 当“我”（null=沿用草稿/不设）。
     * [reviewedToken]：调用方所见预览的计划令牌（resumeReview/
     * reviewToken 给出）。与当前预览不一致时，本次传入的选择不能视为
     * 已核对——逐项决策只放行与仍有效草稿决策同值的项，其余回到未
     * 确认；放弃补充/仅本机勾选按待汇入域重新核对。
     */
    suspend fun applyReceived(
        packageId: String,
        password: CharArray,
        decisions: Map<String, String>,
        localOnlyMarks: Set<String>? = null,
        selfCanonicalChoice: String? = null,
        /** true = “放弃这些补充，以确认版为准”：本机未汇入改动按收到的名册回写。 */
        dropPending: Boolean? = null,
        reviewedToken: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        val draft = draftDao.get(packageId)
            ?: throw CollabException("这份接收草稿不存在")
        if (draft.status != "PENDING") {
            throw CollabException("这份协作包已处理过了")
        }
        val staged = File(draft.filePath)
        if (!staged.isFile) throw CollabException("暂存的协作文件已丢失")
        val plan = parseIncoming(staged.readBytes(), password)
        val meta = plan.collaboration
            ?: throw CollabException("协作包内容已损坏")
        if (meta.packageId != packageId) {
            throw CollabException("协作包标识不一致")
        }
        val remote = snapOf(plan)
        if (contentHash(packageId, remote) != draft.contentHash) {
            throw CollabException("协作包内容与记录不一致")
        }

        // 决策绑定核验（R5）：草稿旧决策按当前资料逐项验指纹，
        // 调用方决策中有记录的同样必须仍匹配；不匹配的一律回到未确认
        val stored = parseChoices(draft.decisionsJson)
        var state = stateDao.get()
        checkBinding(meta, state)
        val base = baseOf(meta, state)
        val roster = repo.canonicalRosterSnapshot()
        // 旧格式草稿没有指纹：本机名册变了就整体重新确认
        val legacyStale = stored.fingerprints.isEmpty() &&
            stored.decisions.isNotEmpty() && draft.rosterHash != roster.hash
        val storedV = if (legacyStale) DraftChoices(
            selfCanonicalChoice = stored.selfCanonicalChoice,
            dropPending = stored.dropPending,
            localOnlyMarks = stored.localOnlyMarks) else stored
        val ctx0 = FamilyRevisionPlan.Ctx(
            selfCanonicalId = selfCanonOf(roster),
            removedIds = removedIdsOf(state))
        val v = validatedPlan(base, remote, roster.snap, ctx0, storedV)
        // 审阅令牌核对：令牌一致 = 调用方看过的正是当前预览；不一致
        // （或缺令牌）时传入决策须与仍有效的草稿决策同值才算数——
        // “同 key 再提交”不等于重新确认，缺指纹不能等于有效确认
        val tokenOk = reviewedToken != null &&
            reviewedToken == reviewToken(base, remote, roster.snap, ctx0)
        val legacyOk = storedV.fingerprints.isEmpty() && !legacyStale
        val effective = LinkedHashMap(v.decisions)
        decisions.forEach { (k, w) ->
            if (k !in v.fingerprints) return@forEach
            // 身份项指纹会随其他裁决并入而变动（候选集变化），其
            // 有效性以 v.decisions 的语义核验结果为准
            val credOk = if (k.startsWith("add:"))
                v.decisions.containsKey(k)
            else storedV.fingerprints[k] == v.fingerprints[k] || legacyOk
            val backed = tokenOk || (storedV.decisions[k] == w && credOk)
            if (backed) effective[k] = w   // 无凭据项丢弃 → 按未确认处理
        }
        val ctx = FamilyRevisionPlan.Ctx(
            selfCanonicalId = ctx0.selfCanonicalId,
            removedIds = ctx0.removedIds,
            identities = identityDecisions(effective))
        val p = if (ctx.identities == v.ctx.identities) v.plan
            else FamilyRevisionPlan.plan(base, remote, roster.snap, ctx)
        // 待汇入域核对：放弃补充/仅本机勾选绑保存时的待汇入指纹集，
        // 其后新增/修改的待汇入项未经核对，旧全局选择不得覆盖
        val scopeNow = pendingScope(p)
        val scopeOk = p.pendingLocal.isNotEmpty() &&
            stored.pendingScope != null && stored.pendingScope == scopeNow
        // 传入值是“最新完整选择”（null=未提供用草稿值）；危险方向
        // （放弃=true、勾选=非空）须令牌或域一致佐证，安全方向
        // （false、空集）永远生效
        // 危险方向（放弃补充/仅本机勾选）须有效审阅令牌或匹配的已
        // 确认域佐证；草稿缺域凭据不再放行——须先重新审阅取得令牌
        val trusted = tokenOk || scopeOk
        val effDrop = when {
            dropPending == null -> stored.dropPending && scopeOk
            !dropPending -> false
            else -> trusted
        }
        val pendingIds = p.pendingLocal.mapTo(HashSet()) { it.personId }
        val effMarks = when {
            localOnlyMarks == null ->
                if (scopeOk) stored.localOnlyMarks else emptySet()
            localOnlyMarks.isEmpty() -> emptySet()
            else -> if (trusted) localOnlyMarks else emptySet()
        }.filterTo(LinkedHashSet()) { it in pendingIds }
        val effSelf = (selfCanonicalChoice ?: stored.selfCanonicalChoice)
            ?.takeIf { it != SELF_NONE }
        // 阻止项自动按“保留本机”处理
        val fullDecisions = effective + p.decisions
            .filter { it.state == FamilyRevisionPlan.State.BLOCKED }
            .associate { it.key to FamilyRevisionPlan.Choice.KEEP_LOCAL.wire }
        val resolved = try {
            FamilyRevisionPlan.resolve(
                base, remote, roster.snap, ctx, p, fullDecisions,
                dropPending = effDrop)
        } catch (e: FamilyRevisionPlan.UnresolvedException) {
            throw CollabException("还有 " + e.missing.size + " 项没有确认")
        } catch (e: FamilyRevisionPlan.InconsistentException) {
            throw CollabException(e.message ?: "收到的内容与本机冲突")
        }

        // 头像：新增/改动的头像文件先落盘（不覆盖旧文件，内容定名）
        val avatarPaths = HashMap<String, String>()  // avatarHash → 本机相对路径
        val writtenAvatars = ArrayList<File>()
        val retiredAvatars = ArrayList<String>()
        var committed = false   // 数据库提交边界：前后的失败处理不同（R4）
        try {
            val localPersons = db.personDao().getAll().associateBy { it.id }
            val localRels = db.relationshipDao().getAll()
            val localOnlyRows = localOnlyDao.getAll()
            // 仅本机人物的规范 id → 本机行：名册快照不含仅本机人物，
            // 再纳入（重现/身份同）要靠这里落回本机行做原位恢复
            val localOnlyByCanon = localOnlyRows
                .mapNotNull { lo -> lo.canonicalId?.let { it to lo } }
                .toMap()
            val canonToLocal = HashMap(roster.canonicalToLocal)
            fun localId(canon: String) =
                canonToLocal[canon] ?: localOnlyByCanon[canon]?.personId ?: canon
            val now = clock.now()

            fun avatarFor(hash: String?): String? {
                if (hash == null) return null
                return avatarPaths.getOrPut(hash) {
                    val rel = "avatars/collab-$hash.jpg"
                    val f = avatarStorage.resolve(rel)
                    if (!f.isFile) {
                        // 内容定名：已存在的同名文件内容必然相同，不重写、
                        // 也不计入 writtenAvatars（失败清理不能删在用文件）
                        val bytes = plan.avatars[avatarFileName(hash)]
                            ?: throw CollabException("协作包缺少头像文件")
                        f.parentFile?.mkdirs()
                        f.writeBytes(bytes)
                        writtenAvatars += f
                    }
                    rel
                }
            }

            val addPersons = ArrayList<PersonEntity>()
            val updatePersons = ArrayList<PersonEntity>()
            val removePersons = ArrayList<String>()
            val upsertPhones = ArrayList<PhoneNumberEntity>()
            val deletePhones = ArrayList<Pair<String, String>>()
            val upsertDates = ArrayList<ImportantDateEntity>()
            val deleteDates = ArrayList<Pair<String, ImportantDateType>>()
            val insertRels = ArrayList<RelationshipEntity>()
            val deleteRelIds = ArrayList<String>()
            val newCanonMap = HashMap<String, String>()  // 新采用的规范id → 本机id
            val removedCanon = ArrayList<String>()

            for (op in resolved.ops) when (op) {
                is FamilyRevisionPlan.Op.AddPerson -> {
                    val revive = localOnlyByCanon[op.person.id]
                    if (revive != null) {
                        // 原位恢复（5.7）：规范 id 命中仅本机行 → 原位更新
                        // 并解除标记，不另建重复人物
                        canonToLocal[op.person.id] = revive.personId
                        val e = localPersons[revive.personId]
                            ?: throw CollabException(
                                "本机人物缺失：" + revive.personId)
                        // 完整快照语义（审查 R3）：远端头像字段为 null
                        // 是明确清除，不是“沿用本机”；同路径文件不退役
                        val path = avatarFor(op.person.avatarHash)
                        if (e.avatarPath != path) {
                            e.avatarPath?.let { retiredAvatars += it }
                        }
                        updatePersons += e.copy(
                            name = op.person.name,
                            gender = enumOr(Gender::class.java, op.person.gender),
                            livingStatus = enumOr(
                                LivingStatus::class.java, op.person.living),
                            note = op.person.note, avatarPath = path,
                            updatedAt = now)
                        // 仅本机期间本机条目没进名册比较：电话/日期以收到的
                        // 名册为准——先清本机条目，随后的 Put 项按远端重建
                        db.phoneNumberDao().getForPerson(revive.personId)
                            .forEach {
                                deletePhones += revive.personId to
                                    normalizePhone(it.number) }
                        db.importantDateDao().getAllDates()
                            .filter { it.personId == revive.personId }
                            .forEach { deleteDates += revive.personId to it.type }
                    } else {
                        val newLocal = ids.newId()
                        canonToLocal[op.person.id] = newLocal
                        newCanonMap[op.person.id] = newLocal
                        addPersons += PersonEntity(
                            id = newLocal, name = op.person.name,
                            gender = enumOr(Gender::class.java, op.person.gender),
                            avatarPath = avatarFor(op.person.avatarHash),
                            note = op.person.note,
                            livingStatus = enumOr(
                                LivingStatus::class.java, op.person.living),
                            isSelf = false, createdAt = now, updatedAt = now)
                    }
                }
                is FamilyRevisionPlan.Op.RemovePerson -> {
                    val l = localId(op.personId)
                    if (localPersons[l]?.isSelf == true) {
                        throw CollabException("不能把「我」从家庭名册移除")
                    }
                    removePersons += l
                    removedCanon += op.personId
                    localPersons[l]?.avatarPath?.let { retiredAvatars += it }
                }
                is FamilyRevisionPlan.Op.SetPerson -> {
                    val l = localId(op.personId)
                    val e = localPersons[l]
                        ?: throw CollabException("本机人物缺失：" + l)
                    val localSnapAvatar =
                        roster.snap.personMap[op.personId]?.avatarHash
                    val path = when {
                        op.avatarHash == localSnapAvatar -> e.avatarPath
                        else -> {
                            e.avatarPath?.let { retiredAvatars += it }
                            avatarFor(op.avatarHash)
                        }
                    }
                    updatePersons += e.copy(
                        name = op.name, gender = enumOr(
                            Gender::class.java, op.gender),
                        livingStatus = enumOr(
                            LivingStatus::class.java, op.living),
                        note = op.note, avatarPath = path, updatedAt = now)
                }
                is FamilyRevisionPlan.Op.PutPhone -> upsertPhones +=
                    PhoneNumberEntity(
                        id = ids.newId(), personId = localId(op.personId),
                        number = op.phone.number,
                        label = enumOr(PhoneLabel::class.java, op.phone.label),
                        status = enumOr(PhoneStatus::class.java, op.phone.status),
                        isPrimary = op.phone.primary, note = op.phone.note,
                        createdAt = now, updatedAt = now)
                is FamilyRevisionPlan.Op.DelPhone ->
                    deletePhones += localId(op.personId) to op.number
                is FamilyRevisionPlan.Op.PutDate -> upsertDates +=
                    ImportantDateEntity(
                        id = ids.newId(), personId = localId(op.personId),
                        type = enumOr(
                            ImportantDateType::class.java, op.date.type),
                        year = op.date.year, month = op.date.month,
                        day = op.date.day,
                        calendarType = enumOr(
                            CalendarType::class.java, op.date.calendar),
                        isLeapMonth = op.date.leap, note = op.date.note,
                        createdAt = now, updatedAt = now)
                is FamilyRevisionPlan.Op.DelDate -> deleteDates +=
                    localId(op.personId) to enumOr(
                        ImportantDateType::class.java, op.type)
                is FamilyRevisionPlan.Op.PutRel -> insertRels +=
                    relEntityOf(op.rel, ::localId, now)
                is FamilyRevisionPlan.Op.DelRel -> {
                    val target = op.rel
                    localRels.firstOrNull { lr ->
                        FamilySnapshot.normalizeRel(
                            lr.copy(
                                sourcePersonId = canonOfIds(
                                    canonToLocal, lr.sourcePersonId),
                                targetPersonId = canonOfIds(
                                    canonToLocal, lr.targetPersonId))
                        ) == target
                    }?.let { deleteRelIds += it.id }
                }
            }

            // 同键语义（审查 R1）：Put 按规范键原位更新（保留行 id 与
            // 其下私人提醒偏好）；删除只收远端不再包含的条目。仅本机
            // 再纳入时本机旧条目列入清空，与 Put 同键的绝不能删
            val putPhoneKeys = upsertPhones.mapTo(HashSet()) {
                it.personId to normalizePhone(it.number) }
            val effDeletePhones = deletePhones.filter { it !in putPhoneKeys }
            val putDateKeys = upsertDates.mapTo(HashSet()) {
                it.personId to it.type }
            val effDeleteDates = deleteDates.filter { it !in putDateKeys }

            // “仅本机保留”标记（未汇入保护；入参为规范 id，译为本机 id）
            val marks = effMarks.map { canon ->
                FamilyLocalOnlyEntity(
                    personId = localId(canon),
                    canonicalId = canon,
                    note = "保留为本机资料", createdAt = now)
            }

            // 身份 Same 裁决：远端规范 id 落到本机已有规范 id 的本机行上
            resolved.identityAdds.forEach { (remoteCanon, localCanon) ->
                canonToLocal[remoteCanon] = localId(localCanon)
            }

            // 再纳入恢复（5.7）：被本版名册重新包含、且用户明确接受
            // （含身份 Same 裁决与再现“重新加入”）的人物原位回名册，
            // 解除仅本机标记；明确拒绝的再现人物不动。
            val adoptedLocalIds = remote.persons.map { it.id }
                .filter { it !in resolved.excludedRemoteIds }
                .map { localId(it) }
                .toSet()
            val markIds = marks.mapTo(HashSet()) { it.personId }
            val unmarks = localOnlyRows
                .map { it.personId }
                .filter { it in adoptedLocalIds && it !in markIds }

            // self 指定（首次绑定）
            val setSelf = effSelf?.let { canon ->
                if (localPersons.values.any { it.isSelf }) {
                    throw CollabException("本机已有「我」，不能重复指定")
                }
                canonToLocal[canon]
                    ?: throw CollabException("指定的「我」不在收到的名册里")
            }

            // 状态写回
            val idMapAll = canonToLocal.toMap()
            state = stateDao.get()
            val newState = when (meta.mode) {
                "confirmed" -> {
                    val remoteIds = remote.persons.mapTo(HashSet()) { it.id }
                    // 移除记账：基准确认移除 ∪ 本次实际移除 ∪ 再现被拒，
                    // 减去已在新版名册里的（再现后被采用的销账）
                    val removed = ((removedIdsOf(state) + removedCanon)
                        .filter { it !in remoteIds } +
                        resolved.excludedRemoteIds).toSet()
                    FamilyCollabStateEntity(
                        id = 1, seriesId = meta.seriesId, role = "member",
                        coordinatorPersonId = meta.coordinatorPersonId,
                        acceptedRevision = meta.revision,
                        acceptedHash = meta.snapshotHash!!,
                        baseSnapshotJson =
                            FamilySnapshot.toJson(remote).toString(),
                        idMapJson = idMapJsonOf(idMapAll),
                        removedIdsJson = removedIdsJsonOf(removed),
                        updatedAt = now)
                }
                else -> {
                    val cur = state
                        ?: throw CollabException("协作状态缺失")
                    val removed = (removedIdsOf(cur) + removedCanon +
                        resolved.excludedRemoteIds).toSet()
                    cur.copy(
                        idMapJson = idMapJsonOf(idMapAll),
                        removedIdsJson = removedIdsJsonOf(removed),
                        updatedAt = now)
                }
            }

            // 保护副本候选（绑本次接收 packageId；提交成功才生效，
            // 标记丢失时以同库事务落库的 APPLIED 记录认领）
            if (!snapshots.savePrereceiveCandidate(packageId)) {
                throw CollabException("保存接收前副本失败，已取消接收")
            }
            val r = repo.applyFamilyRevision(CollabApply(
                expectedRosterHash = roster.hash,
                addPersons = addPersons,
                updatePersons = updatePersons,
                removePersons = removePersons,
                markLocalOnly = marks,
                unmarkLocalOnly = unmarks,
                upsertPhones = upsertPhones,
                deletePhones = effDeletePhones,
                upsertDates = upsertDates,
                deleteDates = effDeleteDates,
                insertRels = insertRels,
                deleteRelIds = deleteRelIds,
                setSelfId = setSelf,
                stateWrite = newState,
                draftWrite = draft.copy(
                    status = "APPLIED",
                    decisionsJson = decisionsJsonOf(
                        DraftChoices(decisions = fullDecisions)),
                    rosterHash = roster.hash,
                    updatedAt = now),
                expectPendingPackage = packageId
            ))
            if (r is FamilyResult.Failure) {
                snapshots.discardPrereceiveCandidate()
                writtenAvatars.forEach { it.delete() }
                throw CollabException(r.error.message)
            }
            committed = true
            // —— 数据库已提交（审查 R4）：以下文件步骤失败都不影响
            // 已生效的接收，不能再删已被引用的新头像、不能把已提交
            // 的接收伪装成可重试的普通失败 ——
            snapshots.commitPrereceive()   // 内部归位，不抛
            runCatching { staged.delete() }
            // 退役头像按数据库实际引用清理（审查 R3）：仅本机保留
            // 的人物、同内容共享文件、再纳入复用的文件都不在删除列
            runCatching {
                val referenced = db.personDao().getAll()
                    .mapNotNullTo(HashSet()) { it.avatarPath }
                retiredAvatars.filter { it !in referenced }
                    .forEach { avatarStorage.delete(it) }
            }
        } catch (e: CollabException) {
            if (!committed) writtenAvatars.forEach { it.delete() }
            throw e
        } catch (e: Exception) {
            if (!committed) {
                writtenAvatars.forEach { it.delete() }
                snapshots.discardPrereceiveCandidate()
            }
            throw e
        }
    }

    /** “上次接收前副本”的信息与恢复（透传 QuickSnapshotStore）。 */
    suspend fun prereceiveInfo() = snapshots.prereceiveInfo()
    suspend fun restorePrereceive() = snapshots.restorePrereceive()

    // ==================== 内部 ====================

    /** 解密 + 解析（任何失败转成中文 CollabException）。 */
    private fun parseIncoming(bytes: ByteArray, password: CharArray): RestorePlan =
        try {
            BackupJson.parsePlan(BackupCrypto.decrypt(bytes, password))
        } catch (e: BackupCrypto.BackupFailure) {
            throw CollabException(e.error.message)
        } catch (e: BackupException) {
            throw CollabException(e.error.message)
        } catch (e: Exception) {
            throw CollabException("协作包内容无效")
        }

    /** RestorePlan → 规范快照（头像哈希走 avatarHashes 字段）。 */
    private fun snapOf(plan: RestorePlan): FamilySnapshot.Snap =
        FamilySnapshot.fromEntities(
            persons = plan.persons, phones = plan.phones,
            rels = plan.relationships, dates = plan.importantDates,
            avatarHashOf = { plan.avatarHashes[it.id] }
        )

    /** 包内容哈希：packageId + 规范快照串（加密随机性不影呵幂等判定）。 */
    private fun contentHash(packageId: String, snap: FamilySnapshot.Snap) =
        FamilySnapshot.sha256Hex(
            (packageId + "|" + FamilySnapshot.canonicalJson(snap))
                .toByteArray(Charsets.UTF_8))

    /** 系列/角色/版本链检查（接收侧）。 */
    private suspend fun checkBinding(
        meta: CollabMeta, state: FamilyCollabStateEntity?) {
        when (meta.mode) {
            "confirmed" -> {
                if (state != null) {
                    if (state.seriesId != meta.seriesId) {
                        throw CollabException("这个确认版不属于本机加入的家庭系列")
                    }
                    if (meta.revision <= state.acceptedRevision) {
                        throw CollabException(
                            "这个版本不比已接收的新（当前第 " +
                                state.acceptedRevision + " 版）")
                    }
                    val linked = meta.revisionChain.any {
                        it.revision == state.acceptedRevision &&
                            it.hash == state.acceptedHash
                    }
                    if (state.acceptedRevision > 0 && !linked) {
                        throw CollabException("版本链接不上本机已接受的版本")
                    }
                }
            }
            "contribution" -> {
                if (state == null || state.role != "coordinator") {
                    throw CollabException("补充包只能由汇总者接收")
                }
                if (meta.seriesId != state.seriesId) {
                    throw CollabException("这份补充不属于本机管理的家庭系列")
                }
                // 基准序号已发布但摘要不同 → 明确矛盾，拒绝（审查 R6）
                val pub = meta.baseRevision?.let { rev ->
                    pubDao.getAll().firstOrNull { it.revision == rev } }
                if (pub != null && pub.snapshotHash != meta.baseHash) {
                    throw CollabException(
                        "补充基准与已发布版本不符，请让对方重新取得确认版")
                }
            }
            else -> throw CollabException("未知协作包类型")
        }
    }

    /**
     * 三方比较的基准快照（无可靠基准 → null，保守模式——缺项不推断删除）。
     * 补充包基准必须命中本机已发布索引（审查 R6）：序号未知或摘要
     * 不符都降级为无基准，不能把远端没有的内容当成删除。
     */
    private suspend fun baseOf(
        meta: CollabMeta, state: FamilyCollabStateEntity?
    ): FamilySnapshot.Snap? = when (meta.mode) {
        "confirmed" -> state?.baseSnapshotJson?.let {
            runCatching { FamilySnapshot.parse(JSONObject(it)) }.getOrNull()
        }
        else -> {
            val b = meta.baseSnapshot?.let {
                runCatching { FamilySnapshot.parse(JSONObject(it)) }
                    .getOrElse {
                        throw CollabException("补充包的基准快照损坏")
                    }
            } ?: return null
            // 完整性：声明的基准哈希必须与实际一致
            if (meta.baseHash == null ||
                FamilySnapshot.hash(b) != meta.baseHash) {
                throw CollabException("补充包基准与声明哈希不一致")
            }
            // 可信基准 = 命中本机已发布索引且摘要一致；否则保守降级
            val pub = meta.baseRevision?.let { rev ->
                pubDao.getAll().firstOrNull { it.revision == rev } }
            if (pub != null && pub.snapshotHash == meta.baseHash) b else null
        }
    }

    /** 本机“我”的规范 id（无名册快照人物时 null）。 */
    private suspend fun selfCanonOf(roster: CollabRoster): String? {
        val self = db.personDao().getSelf() ?: return null
        return canonOf(roster, self.id)
    }

    private fun canonOf(roster: CollabRoster, localId: String) =
        roster.canonicalToLocal.entries.firstOrNull { it.value == localId }
            ?.key ?: localId

    private fun canonOfIds(map: Map<String, String>, local: String) =
        map.entries.firstOrNull { it.value == local }?.key ?: local

    private fun removedIdsOf(state: FamilyCollabStateEntity?): Set<String> =
        state?.removedIdsJson?.let {
            runCatching {
                JSONArray(it).let { a ->
                    (0 until a.length()).map { i -> a.getString(i) }.toSet()
                }
            }.getOrDefault(emptySet())
        } ?: emptySet()

    /** 发布后 removedIds 重算：基准里有、新版没有的 = 确认移除；再现即销账。 */
    private fun computeRemoved(
        state: FamilyCollabStateEntity?, snap: FamilySnapshot.Snap
    ): Set<String> {
        val baseIds = state?.baseSnapshotJson?.let {
            runCatching {
                FamilySnapshot.parse(JSONObject(it)).persons
                    .mapTo(HashSet()) { p -> p.id }
            }.getOrDefault(emptySet())
        } ?: emptySet()
        val snapIds = snap.persons.mapTo(HashSet()) { it.id }
        return (removedIdsOf(state) + (baseIds - snapIds)) - snapIds
    }

    private fun idMapJsonOf(map: Map<String, String>) =
        JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }.toString()

    private fun removedIdsJsonOf(ids: Set<String>) =
        JSONArray().apply { ids.sorted().forEach { put(it) } }.toString()

    private fun decisionsJsonOf(c: DraftChoices) =
        JSONObject().apply {
            put("v", 2)
            put("d", JSONObject().apply {
                c.decisions.forEach { (k, v) -> put(k, v) } })
            put("f", JSONObject().apply {
                c.fingerprints.forEach { (k, v) -> put(k, v) } })
            c.selfCanonicalChoice?.let { put("self", it) }
            if (c.dropPending) put("drop", true)
            if (c.localOnlyMarks.isNotEmpty()) {
                put("lo", JSONArray().apply {
                    c.localOnlyMarks.sorted().forEach { put(it) } })
            }
            c.pendingScope?.let { put("ps", it) }
            if (c.seq != 0) put("seq", c.seq)
        }.toString()

    private fun jsonToMap(o: JSONObject): Map<String, String> =
        o.keys().asSequence().associateWith { o.getString(it) }

    /** 草稿选择 JSON 解析；兼容 v1 扁平 {key:wire} 旧格式（无指纹）。 */
    private fun parseChoices(json: String?): DraftChoices =
        if (json.isNullOrBlank()) DraftChoices() else runCatching {
            val o = JSONObject(json)
            if (o.optInt("v") == 2) {
                DraftChoices(
                    decisions = o.optJSONObject("d")
                        ?.let { jsonToMap(it) } ?: emptyMap(),
                    fingerprints = o.optJSONObject("f")
                        ?.let { jsonToMap(it) } ?: emptyMap(),
                    selfCanonicalChoice = o.optString("self")
                        .takeIf { it.isNotBlank() },
                    dropPending = o.optBoolean("drop"),
                    localOnlyMarks = o.optJSONArray("lo")?.let { a ->
                        (0 until a.length())
                            .mapTo(LinkedHashSet()) { a.getString(it) }
                    } ?: emptySet(),
                    pendingScope = o.optString("ps")
                        .takeIf { it.isNotBlank() },
                    seq = o.optInt("seq"))
            } else DraftChoices(decisions = jsonToMap(o))
        }.getOrDefault(DraftChoices())

    private fun parseDecisions(json: String): Map<String, String> =
        parseChoices(json).decisions

    /** 快照内容指纹（预览令牌用）。 */
    private fun snapFp(s: FamilySnapshot.Snap): String =
        FamilySnapshot.sha256Hex(
            FamilySnapshot.canonicalJson(s).toByteArray(Charsets.UTF_8))

    /**
     * 审阅令牌：基准 | 远端 | 本机名册 | 全部计划项指纹（排序）的摘要。
     * 任一侧资料变化 → 重算的计划/名册不同 → 令牌不同，基于旧预览
     * 的提交自然失效。
     */
    private fun reviewToken(
        base: FamilySnapshot.Snap?, remote: FamilySnapshot.Snap,
        local: FamilySnapshot.Snap, ctx0: FamilyRevisionPlan.Ctx
    ): String {
        val p = FamilyRevisionPlan.plan(base, remote, local, ctx0)
        val fps = (p.items + p.pendingLocal)
            .map { FamilyRevisionPlan.fingerprint(it) }.sorted()
        val parts = listOf(
            base?.let { snapFp(it) } ?: "-", snapFp(remote), snapFp(local))
        return FamilySnapshot.sha256Hex(
            (parts + fps).joinToString("|").toByteArray(Charsets.UTF_8))
    }

    /** 待汇入域：全部待汇入项指纹（排序）的摘要。 */
    private fun pendingScope(plan: FamilyRevisionPlan.Plan): String =
        FamilySnapshot.sha256Hex(plan.pendingLocal
            .map { FamilyRevisionPlan.fingerprint(it) }.sorted()
            .joinToString("|").toByteArray(Charsets.UTF_8))

    /** 计划缓存里的待汇入域（缓存项的 PENDING 态即待汇入）。 */
    private fun pendingScopeOfCached(items: List<CachedItem>): String =
        FamilySnapshot.sha256Hex(
            items.filter { it.state == "PENDING" }
                .mapNotNull { it.fingerprint }.sorted()
                .joinToString("|").toByteArray(Charsets.UTF_8))

    /** 两阶段计划核验结果。 */
    private class Validated(
        val plan: FamilyRevisionPlan.Plan,
        /** 仍有效的旧决策（指纹匹配）。 */
        val decisions: Map<String, String>,
        /** 当前计划项指纹（key → fingerprint）。 */
        val fingerprints: Map<String, String>,
        /** 并入有效身份裁决后的 Ctx。 */
        val ctx: FamilyRevisionPlan.Ctx
    )

    /**
     * 两阶段计划核验（R5）：第一阶段不带身份裁决算出候选集合，验证
     * 旧身份决策指纹；第二阶段并入仍有效的身份裁决重算，再按新指纹
     * 验证其余旧决策。返回最终计划与仍有效的决策集合。
     */
    private fun validatedPlan(
        base: FamilySnapshot.Snap?,
        remote: FamilySnapshot.Snap,
        local: FamilySnapshot.Snap,
        ctx0: FamilyRevisionPlan.Ctx,
        stored: DraftChoices
    ): Validated {
        fun fpsOf(p: FamilyRevisionPlan.Plan) =
            (p.items + p.pendingLocal)
                .associate { it.key to FamilyRevisionPlan.fingerprint(it) }
        fun fpMatches(fp: Map<String, String>, k: String) =
            stored.fingerprints[k]?.let { it == fp[k] } == true
        // 第一阶段：身份决策按“含候选”的计划项核验
        val p1 = FamilyRevisionPlan.plan(base, remote, local, ctx0)
        val fp1 = fpsOf(p1)
        // 身份项指纹含当时候选全集，其他身份裁决并入会让候选
        // 变动（“同名”→“同名且有共同亲属”），整项指纹比对会把
        // 后做的裁决永远作废——双端多人同名时无法收敛。语义核验：
        // 项仍是待裁决身份项，且所选本机人仍在候选列表里。
        fun identWireValid(key: String, wire: String): Boolean {
            val item = p1.items.firstOrNull { it.key == key }
                as? FamilyRevisionPlan.PersonAdd ?: return false
            if (item.state != FamilyRevisionPlan.State.IDENTITY) {
                return false
            }
            return when (val c = FamilyRevisionPlan.IdentChoice.parse(wire)) {
                FamilyRevisionPlan.IdentChoice.New -> true
                is FamilyRevisionPlan.IdentChoice.Same ->
                    item.candidates.any {
                        it.localId == c.localCanonicalId }
                null -> false
            }
        }
        val validIdent = identityDecisions(stored.decisions
            .filter { (k, w) -> k.startsWith("add:") &&
                identWireValid(k, w) })
        val ctx = FamilyRevisionPlan.Ctx(
            selfCanonicalId = ctx0.selfCanonicalId,
            removedIds = ctx0.removedIds, identities = validIdent)
        // 第二阶段：并入有效身份裁决重算，验证其余决策
        val p2 = FamilyRevisionPlan.plan(base, remote, local, ctx)
        val fp2 = fpsOf(p2)
        val valid = stored.decisions.filterKeys { k ->
            // 身份项可能已被 Same 裁决吸收进重算结果（不在 fp2 里），
            // 按语义核验；其余项按当前计划指纹核验
            (fp2.containsKey(k) && fpMatches(fp2, k)) ||
                (k.startsWith("add:") &&
                    identWireValid(k, stored.decisions.getValue(k)))
        }
        return Validated(p2, valid, fp1 + fp2, ctx)
    }

    /** 决策里的身份裁决提取（"add:<rid>" → same:<canon>/new）。 */
    private fun identityDecisions(
        decisions: Map<String, String>
    ): Map<String, FamilyRevisionPlan.IdentChoice> =
        decisions.mapNotNull { (k, v) ->
            if (!k.startsWith("add:")) return@mapNotNull null
            FamilyRevisionPlan.IdentChoice.parse(v)
                ?.let { k.removePrefix("add:") to it }
        }.toMap()

    private fun planCacheJsonOf(
        p: FamilyRevisionPlan.Plan, remote: FamilySnapshot.Snap,
        base: FamilySnapshot.Snap? = null,
        sender: String? = null, coordinator: String? = null
    ): String =
        JSONObject().apply {
            sender?.let { put("sender", it) }
            coordinator?.let { put("coordinator", it) }
            // 基准/远端快照原文：续草稿按当前本机资料重算计划（R5）
            put("remote", FamilySnapshot.toJson(remote))
            put("base", base?.let { FamilySnapshot.toJson(it) }
                ?: JSONObject.NULL)
            // 远端名册（规范 id → 姓名）：续草稿时“我是谁”选择列表用
            put("roster", JSONArray().apply {
                remote.persons.forEach { rp ->
                    put(JSONObject().apply {
                        put("i", rp.id); put("n", rp.name)
                    })
                }
            })
            put("items", JSONArray().apply {
                (p.items + p.pendingLocal).forEach { it ->
                    put(JSONObject().apply {
                        put("k", it.key); put("s", it.state.name)
                        put("t", it.text); put("p", it.personId)
                        put("f", FamilyRevisionPlan.fingerprint(it))
                        val cands = (it as? FamilyRevisionPlan.PersonAdd)
                            ?.candidates.orEmpty()
                        if (cands.isNotEmpty()) {
                            put("c", JSONArray().apply {
                                cands.forEach { c ->
                                    put(JSONObject().apply {
                                        put("id", c.localId)
                                        put("n", c.name)
                                        put("via", c.sharedVia)
                                    })
                                }
                            })
                        }
                    })
                }
            })
        }.toString()

    /** 计划缓存的完整解析（含基准/远端快照原文）。 */
    private class PlanCache(
        val items: List<CachedItem>,
        val roster: List<Pair<String, String>>,
        val sender: String?, val coordinator: String?,
        val remote: FamilySnapshot.Snap?,
        val base: FamilySnapshot.Snap?
    )

    private fun parseFullPlanCache(json: String?): PlanCache? =
        if (json.isNullOrBlank()) null else runCatching {
            val o = JSONObject(json)
            PlanCache(
                items = parsePlanCache(json),
                roster = parseRosterCache(json),
                sender = o.optString("sender").takeIf { it.isNotBlank() },
                coordinator = o.optString("coordinator")
                    .takeIf { it.isNotBlank() },
                remote = o.optJSONObject("remote")
                    ?.let { FamilySnapshot.parse(it) },
                base = if (o.isNull("base")) null
                    else o.optJSONObject("base")
                        ?.let { FamilySnapshot.parse(it) }
            )
        }.getOrNull()

    private fun parsePlanCache(json: String?): List<CachedItem> =
        if (json.isNullOrBlank()) emptyList() else runCatching {
            JSONObject(json).getJSONArray("items").let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    CachedItem(
                        key = o.getString("k"), state = o.getString("s"),
                        text = o.getString("t"), personId = o.getString("p"),
                        candidates = o.optJSONArray("c")?.let { ca ->
                            (0 until ca.length()).map { j ->
                                val c = ca.getJSONObject(j)
                                Triple(c.getString("id"), c.getString("n"),
                                    c.optString("via"))
                            }
                        } ?: emptyList(),
                        fingerprint = o.optString("f")
                            .takeIf { it.isNotBlank() }
                    )
                }
            }
        }.getOrDefault(emptyList())

    private fun parseRosterCache(json: String?): List<Pair<String, String>> =
        if (json.isNullOrBlank()) emptyList() else runCatching {
            JSONObject(json).optJSONArray("roster")?.let { a ->
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i)
                    o.getString("i") to o.getString("n")
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())

    // ---------- 规范空间 → 本机实体 ----------

    private fun <T : Enum<T>> enumOr(cls: Class<T>, name: String): T =
        java.lang.Enum.valueOf(cls, name)

    private fun relEntityOf(
        rel: FamilySnapshot.Rel, localId: (String) -> String, now: Long
    ): RelationshipEntity = when (rel.type) {
        RelationshipType.PARENT.name, RelationshipType.CHILD.name ->
            RelationshipEntity(
                id = ids.newId(), sourcePersonId = localId(rel.a),
                targetPersonId = localId(rel.b),
                type = RelationshipType.valueOf(rel.type),
                relativeAgeOrder = null, createdAt = now, updatedAt = now)
        else -> RelationshipEntity(
            id = ids.newId(), sourcePersonId = localId(rel.a),
            targetPersonId = localId(rel.b),
            type = RelationshipType.valueOf(rel.type),
            relativeAgeOrder = rel.order?.let {
                RelativeAgeOrder.valueOf(it) },
            createdAt = now, updatedAt = now)
    }

    /** 协作包内的头像文件名（内容定名，跨设备一致）。 */
    private fun avatarFileName(hash: String) = "$hash.jpg"

    /** 协作包写出：规范快照实体化 + 头像按内容定名打包。 */
    private fun writeCollabZip(
        out: OutputStream, password: CharArray,
        snap: FamilySnapshot.Snap, meta: CollabMeta
    ) {
        val now = clock.now()
        val persons = snap.persons.map { p ->
            PersonEntity(
                id = p.id, name = p.name,
                gender = enumOr(Gender::class.java, p.gender),
                avatarPath = p.avatarHash?.let { "avatars/" + avatarFileName(it) },
                note = p.note,
                livingStatus = enumOr(LivingStatus::class.java, p.living),
                isSelf = false, createdAt = now, updatedAt = now)
        }
        val phones = snap.persons.flatMap { p ->
            p.phones.map { ph ->
                PhoneNumberEntity(
                    id = "ph-" + p.id + "-" + ph.number,
                    personId = p.id, number = ph.number,
                    label = enumOr(PhoneLabel::class.java, ph.label),
                    status = enumOr(PhoneStatus::class.java, ph.status),
                    isPrimary = ph.primary, note = ph.note,
                    createdAt = now, updatedAt = now)
            }
        }
        val dates = snap.persons.flatMap { p ->
            p.dates.map { d ->
                ImportantDateEntity(
                    id = "dt-" + p.id + "-" + d.type, personId = p.id,
                    type = enumOr(ImportantDateType::class.java, d.type),
                    year = d.year, month = d.month, day = d.day,
                    calendarType = enumOr(CalendarType::class.java, d.calendar),
                    isLeapMonth = d.leap, note = d.note,
                    createdAt = now, updatedAt = now)
            }
        }
        val rels = snap.rels.mapIndexed { i, r ->
            relEntityOf(r, { it }, now).copy(id = "rel-$i")
        }
        val avatars = collectAvatars(snap)
        val zip = BackupJson.writeZip(
            persons = persons, phones = phones, relationships = rels,
            dates = dates, prefs = emptyList(), customTitles = emptyList(),
            tasks = emptyList(),
            contactLogs = emptyList(), contactPlans = emptyList(),
            settings = null, avatars = avatars,
            createdAt = now, appVersion = BuildConfig.VERSION_NAME,
            purpose = "share", sharedBy = meta.coordinatorPersonId,
            collaboration = meta,
            avatarHashes = snap.persons.mapNotNull { p ->
                p.avatarHash?.let { p.id to it }
            }.toMap()
        )
        out.use { it.write(BackupCrypto.encrypt(zip, password)) }
    }

    /** 按内容哈希收集头像字节：本机文件按内容 sha256 对上快照的 avatarHash。 */
    private fun collectAvatars(
        snap: FamilySnapshot.Snap
    ): Map<String, ByteArray> {
        val wanted = snap.persons.mapNotNull { it.avatarHash }.toSet()
        if (wanted.isEmpty()) return emptyMap()
        val dir = avatarStorage.resolve("avatars")
        if (!dir.isDirectory) return emptyMap()
        val found = HashMap<String, ByteArray>()
        dir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val h = runCatching {
                FamilySnapshot.sha256Hex(f.readBytes())
            }.getOrNull() ?: return@forEach
            if (h in wanted) found[avatarFileName(h)] = f.readBytes()
        }
        return found
    }
}
