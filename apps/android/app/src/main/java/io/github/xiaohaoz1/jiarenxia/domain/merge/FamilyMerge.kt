package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.ReminderPreference
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderOffsetEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderPreferenceEntity
import io.github.xiaohaoz1.jiarenxia.data.normalizePhone
import java.util.UUID

/**
 * 通用家庭合并引擎（FAMILY_AFFAIRS 16.5/16.6，纯 JVM）：
 * 同源更新与首次对齐共用——在 SpouseMerge 规则上推广，去掉“一男一女”、
 * 配偶相容检查和自动添加的夫妻关系。
 *
 * “同一个人”识别优先级：用户指认 [Request.anchors] > 已保存映射
 * [Request.knownMap] > 同 id > 结构候选（用户勾选 [Request.approved] 才
 * 落映射）。其余规则：只补不删；冲突逐条选择（默认保留本机）；
 * 事项/人情/保单/联系/自定义称呼天然不参与（不在 [Side] 里）。
 */
object FamilyMerge {

    /** 一方快照（本机或对方）。prefs/offsets 为展开的提醒偏好与提前天数。 */
    data class Side(
        val persons: List<PersonEntity>,
        val phones: List<PhoneNumberEntity>,
        val relationships: List<RelationshipEntity>,
        val dates: List<ImportantDateEntity>,
        val prefs: List<ReminderPreferenceEntity> = emptyList(),
        val offsets: List<ReminderOffsetEntity> = emptyList()
    )

    /** 字段冲突键：本机人物 + 字段（"name" / "livingStatus" / 日期类型名）。 */
    data class ConflictKey(val localPersonId: String, val field: String)

    /** “我是谁”桥：本机“我”是对方人物 remoteId 的 role（16.3 增补“我”）。 */
    data class Bridge(val remoteId: String, val role: RelationRole)

    /**
     * “他不在我家”锚（碰一碰互加亲人）：对方人物 remoteId 是本机人物
     * localAnchorId 的 role——对方本人在本机还不存在时，按声明关系新建
     * 并接入本机关系图；同时作为认人起点参与结构候选比对（对方带来的
     * 亲属可与本机已有人物互相认出）。
     */
    data class RemoteAnchor(
        val remoteId: String,
        val localAnchorId: String,
        val role: RelationRole
    )

    data class Request(
        val local: Side,
        val remote: Side,
        /** 本机“我”的 id（在世状态冲突对其豁免；bridge 的锚点）。 */
        val localSelfId: String,
        /** 用户指认：对方 id → 本机 id（须同性别）。 */
        val anchors: Map<String, String> = emptyMap(),
        /** 已保存的对应关系：对方 id → 本机 id。 */
        val knownMap: Map<String, String> = emptyMap(),
        /** 勾选确认的候选（对方 id）。 */
        val approved: Set<String> = emptySet(),
        /** 用户选择“用对方的”的字段冲突。 */
        val takeRemote: Set<ConflictKey> = emptySet(),
        val bridge: Bridge? = null,
        /** “他不在我家”锚（互加亲人）；remoteId 已被 anchors/knownMap 认出时忽略。 */
        val remoteAnchor: RemoteAnchor? = null,
        val now: Long = System.currentTimeMillis(),
        val newId: () -> String = { UUID.randomUUID().toString() }
    )

    /** 疑似同一人的待确认候选（结构匹配、两边唯一）。 */
    data class Candidate(
        val remoteId: String,
        val localId: String,
        val remoteName: String,
        val localName: String,
        /** 位置描述（如“李雪的父亲”）。 */
        val desc: String,
        /** 两边姓名不同（如一边写“妈妈”、一边写真名）。 */
        val nameDiffers: Boolean,
        /** 不确认、按新人物处理会造成关系矛盾。 */
        val conflictIfDeclined: Boolean
    )

    /** 字段冲突：预览逐条展示，默认保留本机，改选“用对方的”才写。 */
    data class FieldConflict(
        val key: ConflictKey,
        val personName: String,
        val fieldLabel: String,
        val localValue: String,
        val remoteValue: String,
        val takeRemote: Boolean
    )

    /** 违反关系约束的关系（不写入；端点为本机空间 id）。 */
    data class RelationConflict(
        val aId: String,
        val bId: String,
        /** 对方记的关系描述。 */
        val desc: String,
        /** 违反的约束。 */
        val reason: String
    )

    /** 待补充的头像（remoteFileName 为文件内 avatars/<名> 的文件名）。 */
    data class AvatarAdd(
        val localPersonId: String,
        val remoteFileName: String,
        val isNewPerson: Boolean
    )

    /** 合并写入计划（端点全部为本机空间 id，可直接落库）。 */
    data class Plan(
        /** 对方 id → 本机 id（同一个人映射）。 */
        val idMap: Map<String, String>,
        val newPersons: List<PersonEntity>,
        val newRelationships: List<RelationshipEntity>,
        val newPhones: List<PhoneNumberEntity>,
        val newDates: List<ImportantDateEntity>,
        val newPrefs: List<ReminderPreferenceEntity>,
        val newOffsets: List<ReminderOffsetEntity>,
        /** takeRemote 的人物字段改写（同名合并为一条，updatedAt=now）。 */
        val personUpdates: List<PersonEntity>,
        /** takeRemote 的日期改写（沿用本机行 id 与提醒偏好）。 */
        val dateUpdates: List<ImportantDateEntity>,
        val avatarAdds: List<AvatarAdd>,
        val candidates: List<Candidate>,
        val conflicts: List<FieldConflict>,
        val relationConflicts: List<RelationConflict>,
        /** 全部对方人物 → 本机空间 id（含新人物）。 */
        val mapping: Map<String, String>
    ) {
        fun hasWrites(): Boolean =
            newPersons.isNotEmpty() || newRelationships.isNotEmpty() ||
                newPhones.isNotEmpty() || newDates.isNotEmpty() ||
                newPrefs.isNotEmpty() || newOffsets.isNotEmpty() ||
                personUpdates.isNotEmpty() || dateUpdates.isNotEmpty() ||
                avatarAdds.isNotEmpty()

        /** “没有新内容”（幂等重放或完全重复的文件）。 */
        fun nothingToShow(): Boolean = !hasWrites() &&
            candidates.isEmpty() && conflicts.isEmpty() &&
            relationConflicts.isEmpty()
    }

    sealed class Result {
        data class Ok(val plan: Plan) : Result()
        data class Reject(val reason: String) : Result()
    }

    /** 亲属方向（结构候选的比较位置）。 */
    private enum class RelKind { PARENT, CHILD, SIBLING, SPOUSE }

    fun plan(req: Request): Result {
        var localById = req.local.persons.associateBy { it.id }
        var localRels = req.local.relationships
        // remoteAnchor 时扩入新人物与声明关系的本机一侧（候选比对/约束校验用）
        var local = req.local
        val remoteById = req.remote.persons.associateBy { it.id }

        // ---------- 1. 认人（一个本机人物只能被认一次，后来者降级） ----------
        val idMap = LinkedHashMap<String, String>()
        val localTaken = HashSet<String>()

        // anchors：用户明确指认，两端须存在且同性别；同一本机人不能被指两次
        for ((rid, lid) in req.anchors) {
            val rp = remoteById[rid]
            val lp = localById[lid]
                ?: return Result.Reject("指认的人物在资料里不存在")
            if (rp == null) return Result.Reject("指认的人物在资料里不存在")
            if (rp.gender != lp.gender) {
                return Result.Reject(
                    "「" + rp.name + "」和「" + lp.name +
                        "」性别不同，不能是同一个人")
            }
            if (!localTaken.add(lid)) {
                return Result.Reject("「" + lp.name + "」被指认了两次")
            }
            idMap[rid] = lid
        }
        // knownMap：此前确认过的对应关系，两端都在且同性别才生效
        for ((rid, lid) in req.knownMap) {
            if (rid in idMap) continue
            val rp = remoteById[rid] ?: continue
            val lp = localById[lid] ?: continue
            if (rp.gender != lp.gender || lid in localTaken) continue
            idMap[rid] = lid; localTaken.add(lid)
        }
        // 同 id：对方人物 id 已在本机且同性别 → 同一人
        // （性别不同或本机人已被占用 → 按新人物处理并换新 id）
        for (p in req.remote.persons) {
            if (p.id in idMap) continue
            val lp = localById[p.id] ?: continue
            if (lp.gender != p.gender || lp.id in localTaken) continue
            idMap[p.id] = lp.id; localTaken.add(lp.id)
        }

        // ---------- 1.5 “他不在我家”锚（互加亲人）：对方本人按声明关系新建，
        // 放入 idMap 作认人起点——对方带来的亲属随即可与本机人物比对出候选。
        // remoteId 已被 anchors/knownMap/同 id 认出时跳过（它本就是认人结果）。
        var anchorNew: PersonEntity? = null
        var anchorRel: RelationshipEntity? = null
        val ra = req.remoteAnchor
        if (ra != null && ra.remoteId !in idMap) {
            val rp = remoteById[ra.remoteId]
                ?: return Result.Reject("指认的人物在资料里不存在")
            val lp = localById[ra.localAnchorId]
                ?: return Result.Reject("指认的人物在本机不存在")
            val np = PersonEntity(
                id = req.newId(), name = rp.name, gender = rp.gender,
                avatarPath = null, note = null,
                livingStatus = rp.livingStatus, isSelf = false,
                siblingRank = rp.siblingRank,
                createdAt = req.now, updatedAt = req.now)
            val spec = ra.role.toRelationshipSpec(np.id, lp.id)
            val rel = SpouseMerge.normalizeRel(
                RelationshipEntity(
                    id = req.newId(),
                    sourcePersonId = spec.sourcePersonId,
                    targetPersonId = spec.targetPersonId,
                    type = spec.type,
                    relativeAgeOrder = spec.relativeAgeOrder,
                    createdAt = req.now, updatedAt = req.now))
            // 声明的关系与库矛盾 → 整个合并不接受（同 bridge 的处理）
            RelationGraph(localById + (np.id to np), localRels)
                .check(rel)?.let { return Result.Reject(it.reason) }
            idMap[ra.remoteId] = np.id; localTaken.add(np.id)
            anchorNew = np; anchorRel = rel
            localById = localById + (np.id to np)
            localRels = localRels + rel
            local = local.copy(persons = local.persons + np, relationships = localRels)
        }

        // ---------- 2. 结构候选：从已认对出发逐层比较 ----------
        // （候选本身也入队，更深的亲属候选不依赖上层是否已确认，同 SpouseMerge）
        val candidates = LinkedHashMap<String, Candidate>()
        val candidateLocalIds = HashSet<String>()
        val queue = ArrayDeque<Pair<String, String>>()
        val enqueued = HashSet<String>()
        fun push(rid: String, lid: String) {
            if (enqueued.add(rid)) queue.add(rid to lid)
        }
        for ((r, l) in idMap) push(r, l)

        // neighbors 用 id→人物 索引（O(P·R) 级别，勾选重算不再是平方扫描）
        fun remoteNbrs(rid: String, kind: RelKind) =
            neighbors(req.remote, remoteById, rid, kind)
        fun localNbrs(lid: String, kind: RelKind) =
            neighbors(local, localById, lid, kind)

        fun propose(r: PersonEntity, l: PersonEntity, viaName: String, kind: RelKind) {
            if (r.id in req.approved) {
                // 用户已勾选该候选 → 直接落映射
                if (l.id !in localTaken) {
                    idMap[r.id] = l.id; localTaken.add(l.id)
                    push(r.id, l.id)
                }
            } else {
                candidates[r.id] = Candidate(
                    remoteId = r.id, localId = l.id,
                    remoteName = r.name, localName = l.name,
                    desc = viaName + "的" + kindLabel(kind, r.gender),
                    nameDiffers = r.name != l.name,
                    conflictIfDeclined = false
                )
                candidateLocalIds += l.id
                push(r.id, l.id)
            }
        }

        while (queue.isNotEmpty()) {
            val (rid, lid) = queue.removeFirst()
            // 位置描述给本机用户看：用本机那位已认人物的名字
            val viaName = localById[lid]?.name ?: continue
            for (kind in RelKind.entries) {
                val rn = remoteNbrs(rid, kind)
                    .filter { it.id !in idMap && it.id !in candidates }
                if (rn.isEmpty()) continue
                val ln = localNbrs(lid, kind)
                    .filter {
                        it.id !in localTaken && it.id !in candidateLocalIds
                    }
                if (ln.isEmpty()) continue
                val remoteGroups = rn.groupBy { it.gender }
                for ((g, rs) in remoteGroups) {
                    val ls = ln.filter { it.gender == g }
                    if (ls.isEmpty()) continue
                    if (rs.size == 1 && ls.size == 1) {
                        // 同位置同性别两边唯一 → 候选（异名也列出）
                        propose(rs[0], ls[0], viaName, kind)
                    } else {
                        // 多人：只把两边都恰好出现一次的同名配成候选，
                        // 不做异名配对
                        val lByName = ls.groupBy { it.name }
                        val counted = rs.groupBy { it.name }
                        for ((name, same) in counted) {
                            if (same.size == 1 &&
                                lByName[name]?.size == 1
                            ) {
                                propose(same[0], lByName.getValue(name)[0],
                                    viaName, kind)
                            }
                        }
                    }
                }
            }
        }

        // ---------- 3. id 冲突：保留对方 id，与本机相撞才换 ----------
        val remoteNewIds = HashMap<String, String>()
        for (p in req.remote.persons) {
            if (p.id in idMap) continue
            remoteNewIds[p.id] = if (p.id in localById) req.newId() else p.id
        }
        fun rewrite(rid: String) = idMap[rid] ?: remoteNewIds[rid] ?: rid
        val localRelIds = local.relationships.map { it.id }.toHashSet()
        val localPhoneIds = local.phones.map { it.id }.toHashSet()
        val localDateIds = local.dates.map { it.id }.toHashSet()
        val localPrefIds = local.prefs.map { it.id }.toHashSet()
        val localOffsetIds = local.offsets.map { it.id }.toHashSet()
        fun relId(id: String) = if (id in localRelIds) req.newId() else id
        fun phoneId(id: String) = if (id in localPhoneIds) req.newId() else id
        fun dateId(id: String) = if (id in localDateIds) req.newId() else id
        fun prefId(id: String) = if (id in localPrefIds) req.newId() else id
        fun offsetId(id: String) = if (id in localOffsetIds) req.newId() else id

        // ---------- 4. 新人物（不是“我”；头像经 AvatarAdd 由数据层补） ----------
        // anchorNew：“他不在我家”声明新建的对方本人（已在 idMap，不入 remoteNewIds）
        val newPersons = req.remote.persons
            .filter { it.id in remoteNewIds }
            .map {
                it.copy(id = remoteNewIds[it.id]!!, isSelf = false,
                    avatarPath = null)
            } + listOfNotNull(anchorNew)
        val newPersonsById = newPersons.associateBy { it.id }
        val mapping = req.remote.persons.associate { it.id to rewrite(it.id) }

        // ---------- 5. 字段冲突（只对已认出的人；本机“我”跳过在世状态） ----------
        val conflicts = ArrayList<FieldConflict>()
        val personUpdates = LinkedHashMap<String, PersonEntity>()
        for ((rid, lid) in idMap) {
            val lp = localById[lid] ?: continue
            val rp = remoteById[rid] ?: continue
            if (lp.name != rp.name) {
                val key = ConflictKey(lid, "name")
                conflicts += FieldConflict(
                    key = key, personName = lp.name, fieldLabel = "姓名",
                    localValue = lp.name, remoteValue = rp.name,
                    takeRemote = key in req.takeRemote)
                if (key in req.takeRemote) {
                    personUpdates[lid] =
                        (personUpdates[lid] ?: lp).copy(name = rp.name)
                }
            }
            if (lp.livingStatus != rp.livingStatus && !lp.isSelf) {
                val key = ConflictKey(lid, "livingStatus")
                conflicts += FieldConflict(
                    key = key, personName = lp.name, fieldLabel = "在世状态",
                    localValue = livingWord(lp.livingStatus),
                    remoteValue = livingWord(rp.livingStatus),
                    takeRemote = key in req.takeRemote)
                if (key in req.takeRemote) {
                    personUpdates[lid] = (personUpdates[lid] ?: lp)
                        .copy(livingStatus = rp.livingStatus)
                }
            }
            if (lp.siblingRank != rp.siblingRank) {
                val key = ConflictKey(lid, "siblingRank")
                conflicts += FieldConflict(
                    key = key, personName = lp.name, fieldLabel = "排行",
                    localValue = rankWord(lp.siblingRank),
                    remoteValue = rankWord(rp.siblingRank),
                    takeRemote = key in req.takeRemote)
                if (key in req.takeRemote) {
                    personUpdates[lid] = (personUpdates[lid] ?: lp)
                        .copy(siblingRank = rp.siblingRank)
                }
            }
        }
        val personUpdateList = personUpdates.values
            .map { it.copy(updatedAt = req.now) }
        // 合并后某人的有效在世状态（忌日规则用）
        fun effectiveLiving(lid: String): LivingStatus {
            val lp = localById[lid] ?: return LivingStatus.ALIVE
            return if (ConflictKey(lid, "livingStatus") in req.takeRemote) {
                idMap.entries.firstOrNull { it.value == lid }
                    ?.let { remoteById[it.key]?.livingStatus } ?: lp.livingStatus
            } else lp.livingStatus
        }

        // ---------- 6. 关系：逐条校验约束，违规不写入 ----------
        // localRels 已含声明关系：对方包里相同的关系在此被判 identical 静默跳过
        val graph = RelationGraph(
            localById + newPersonsById, localRels)
        val newRelationships = ArrayList<RelationshipEntity>()
        anchorRel?.let { newRelationships += it }
        val relationConflicts = ArrayList<RelationConflict>()
        for (r in req.remote.relationships) {
            val nr = SpouseMerge.normalizeRel(
                r.copy(
                    id = relId(r.id),
                    sourcePersonId = rewrite(r.sourcePersonId),
                    targetPersonId = rewrite(r.targetPersonId)
                )
            )
            val v = graph.check(nr)
            if (v == null) {
                graph.add(nr); newRelationships += nr
            } else if (!v.identical) {
                relationConflicts += RelationConflict(
                    aId = nr.sourcePersonId, bId = nr.targetPersonId,
                    desc = graph.describe(nr), reason = v.reason)
            }
            // identical = 与本机完全相同 → 静默跳过
        }
        // bridge（“资料里没有我”）：本机“我”是对方人物的 role
        if (req.bridge != null) {
            val b = req.bridge
            val spec = b.role.toRelationshipSpec(
                req.localSelfId, rewrite(b.remoteId))
            val nr = SpouseMerge.normalizeRel(
                RelationshipEntity(
                    id = req.newId(),
                    sourcePersonId = spec.sourcePersonId,
                    targetPersonId = spec.targetPersonId,
                    type = spec.type,
                    relativeAgeOrder = spec.relativeAgeOrder,
                    createdAt = req.now, updatedAt = req.now
                )
            )
            val v = graph.check(nr)
            if (v != null) {
                // 桥本身违规 = 用户选的关系与库矛盾 → 整个合并不接受
                return Result.Reject(v.reason)
            }
            graph.add(nr); newRelationships += nr
        }

        // ---------- 7. conflictIfDeclined：候选的新 id 出现在矛盾端点 ----------
        val declinedEnds = relationConflicts
            .flatMap { listOf(it.aId, it.bId) }.toSet()
        val candidateList = candidates.values.map {
            it.copy(conflictIfDeclined =
                it.remoteId in remoteNewIds &&
                    remoteNewIds[it.remoteId] in declinedEnds)
        }

        // ---------- 8. 手机号：（人,号码）去重补充；有主号码的人补上的不再为主 ----------
        val localPhoneKeys = local.phones
            .map { it.personId to normalizePhone(it.number) }.toMutableSet()
        val localPrimary = local.phones
            .filter { it.isPrimary }.map { it.personId }.toHashSet()
        val newPhones = ArrayList<PhoneNumberEntity>()
        val addedPrimary = HashSet<String>()
        for (ph in req.remote.phones) {
            val pid = rewrite(ph.personId)
            val key = pid to normalizePhone(ph.number)
            if (key in localPhoneKeys) continue
            localPhoneKeys += key
            val isNewPerson = ph.personId !in idMap
            val primary = if (isNewPerson) ph.isPrimary
            else ph.isPrimary && pid !in localPrimary && pid !in addedPrimary
            if (primary) addedPrimary += pid
            newPhones += ph.copy(
                id = phoneId(ph.id), personId = pid, isPrimary = primary)
        }

        // ---------- 9. 日期：新人物全带；已认出的补缺失类型/记冲突 ----------
        val localDatesByKey = local.dates
            .associateBy { it.personId to it.type }
        val newDates = ArrayList<ImportantDateEntity>()
        val dateUpdates = LinkedHashMap<String, ImportantDateEntity>()
        val addedDateRemoteIds = LinkedHashMap<String, ImportantDateEntity>()
        val seenDateKeys = HashSet<Pair<String, ImportantDateType>>()
        // （较小者 id, 较大者 id, 类型）：每种共享纪念日按夫妻各一条
        val seenWeddingPairs = HashSet<Triple<String, String, ImportantDateType>>()
        for (d in req.remote.dates) {
            val pid = rewrite(d.personId)
            val isNewPerson = d.personId !in idMap
            when (d.type) {
                // 忌日只加给合并后已故的人
                ImportantDateType.DEATH_ANNIVERSARY -> {
                    val deceased = if (isNewPerson)
                        (remoteById[d.personId]?.livingStatus ==
                            LivingStatus.DECEASED)
                    else effectiveLiving(pid) == LivingStatus.DECEASED
                    if (!deceased) continue
                }
                // 夫妻共享纪念日（结婚/领证/恋爱）按夫妻算一条：
                // 只认“文件里此人的配偶”与合并后本机配偶是同一人的那段婚姻
                // （对方记的别段婚姻在本机被判矛盾时，不能把它的纪念日挂到
                // 本机这对夫妻上）
                ImportantDateType.WEDDING_ANNIVERSARY,
                ImportantDateType.LICENSE_ANNIVERSARY,
                ImportantDateType.DATING_ANNIVERSARY -> {
                    val remoteSpouse = neighbors(
                        req.remote, remoteById, d.personId,
                        RelKind.SPOUSE).firstOrNull()
                    val spouse = if (remoteSpouse != null &&
                        rewrite(remoteSpouse.id) == graph.spouseOf(pid)
                    ) graph.spouseOf(pid) else null
                    if (spouse == null) continue
                    val pair = if (pid < spouse) Triple(pid, spouse, d.type)
                    else Triple(spouse, pid, d.type)
                    if (!seenWeddingPairs.add(pair)) continue
                    // 夫妻任一方本机已有 → 比较（冲突记在本机持有行的那个人身上）
                    val existing = localDatesByKey[pid to d.type]
                        ?: localDatesByKey[spouse to d.type]
                    if (existing != null) {
                        handleDateConflict(
                            existing, d, localById, req,
                            conflicts, dateUpdates)
                        continue
                    }
                }
                else -> {}
            }
            if (!isNewPerson) {
                val existing = localDatesByKey[pid to d.type]
                if (existing != null) {
                    handleDateConflict(
                        existing, d, localById, req,
                        conflicts, dateUpdates)
                    continue
                }
            }
            if (!seenDateKeys.add(pid to d.type)) continue
            val nd = d.copy(id = dateId(d.id), personId = pid)
            newDates += nd
            addedDateRemoteIds[d.id] = nd
        }

        // ---------- 10. 提醒偏好：文件带的复制；没有的按默认补建 ----------
        val remotePrefForDate = req.remote.prefs
            .associateBy { it.importantDateId }
        val remoteOffsetsByPref = req.remote.offsets
            .groupBy { it.reminderPreferenceId }
        val newPrefs = ArrayList<ReminderPreferenceEntity>()
        val newOffsets = ArrayList<ReminderOffsetEntity>()
        for ((remoteDateId, nd) in addedDateRemoteIds) {
            val rp = remotePrefForDate[remoteDateId]
            if (rp != null) {
                val np = rp.copy(
                    id = prefId(rp.id), importantDateId = nd.id)
                newPrefs += np
                remoteOffsetsByPref[rp.id].orEmpty().forEach {
                    newOffsets += it.copy(
                        id = offsetId(it.id),
                        reminderPreferenceId = np.id)
                }
            } else {
                // 文件没带偏好 → 与手工录入一致：默认开启 + 类型默认提前天数
                val np = ReminderPreferenceEntity(
                    id = req.newId(), importantDateId = nd.id,
                    enabled = true, createdAt = req.now, updatedAt = req.now)
                newPrefs += np
                ReminderPreference.defaultOffsetsFor(nd.type).forEach { d ->
                    newOffsets += ReminderOffsetEntity(
                        id = req.newId(), reminderPreferenceId = np.id,
                        daysBefore = d)
                }
            }
        }

        // ---------- 11. 头像：新人物带上；已认出且本机没头像的补上 ----------
        val avatarAdds = ArrayList<AvatarAdd>()
        for (p in req.remote.persons) {
            val path = p.avatarPath ?: continue
            val fileName = path.substringAfterLast('/')
            if (p.id in idMap) {
                val lp = localById[idMap[p.id]] ?: continue
                // 本机已有头像保留，绝不覆盖
                if (lp.avatarPath == null) {
                    avatarAdds += AvatarAdd(
                        localPersonId = lp.id,
                        remoteFileName = fileName, isNewPerson = false)
                }
            } else {
                avatarAdds += AvatarAdd(
                    localPersonId = remoteNewIds[p.id]!!,
                    remoteFileName = fileName, isNewPerson = true)
            }
        }

        return Result.Ok(
            Plan(
                idMap = idMap, newPersons = newPersons,
                newRelationships = newRelationships, newPhones = newPhones,
                newDates = newDates, newPrefs = newPrefs,
                newOffsets = newOffsets, personUpdates = personUpdateList,
                dateUpdates = dateUpdates.values.toList(),
                avatarAdds = avatarAdds, candidates = candidateList,
                conflicts = conflicts, relationConflicts = relationConflicts,
                mapping = mapping
            )
        )
    }

    /** 日期冲突处理：同值跳过；不同值记冲突，takeRemote 时写 dateUpdates。 */
    private fun handleDateConflict(
        existing: ImportantDateEntity,
        remote: ImportantDateEntity,
        localById: Map<String, PersonEntity>,
        req: Request,
        conflicts: MutableList<FieldConflict>,
        dateUpdates: MutableMap<String, ImportantDateEntity>
    ) {
        if (sameDate(existing, remote)) return
        val key = ConflictKey(existing.personId, existing.type.name)
        conflicts += FieldConflict(
            key = key,
            personName = localById[existing.personId]?.name ?: "",
            fieldLabel = dateLabel(existing.type),
            localValue = describeDate(existing),
            remoteValue = describeDate(remote),
            takeRemote = key in req.takeRemote)
        if (key in req.takeRemote) {
            // 沿用本机行 id 与提醒偏好，只换日期值
            dateUpdates[existing.id] = existing.copy(
                year = remote.year, month = remote.month, day = remote.day,
                calendarType = remote.calendarType,
                isLeapMonth = remote.isLeapMonth,
                updatedAt = req.now)
        }
    }

    /** 候选位置称谓（按关系方向与候选人性别）。 */
    private fun kindLabel(kind: RelKind, g: Gender) = when (kind) {
        RelKind.PARENT -> if (g == Gender.MALE) "父亲" else "母亲"
        RelKind.CHILD -> if (g == Gender.MALE) "儿子" else "女儿"
        RelKind.SIBLING -> if (g == Gender.MALE) "兄弟" else "姐妹"
        RelKind.SPOUSE -> if (g == Gender.MALE) "丈夫" else "妻子"
    }

    /** 某人某方向的全部亲属人物（id→人物 经 [byId] 查询，不做线性扫表）。 */
    private fun neighbors(
        s: Side, byId: Map<String, PersonEntity>, selfId: String, kind: RelKind
    ): List<PersonEntity> {
        val out = ArrayList<PersonEntity>()
        for (r in s.relationships) {
            val other = when (kind) {
                RelKind.PARENT -> if (r.type == RelationshipType.PARENT &&
                    r.targetPersonId == selfId) r.sourcePersonId else continue
                RelKind.CHILD -> if (r.type == RelationshipType.PARENT &&
                    r.sourcePersonId == selfId) r.targetPersonId else continue
                RelKind.SIBLING -> if (r.type == RelationshipType.SIBLING) {
                    when (selfId) {
                        r.sourcePersonId -> r.targetPersonId
                        r.targetPersonId -> r.sourcePersonId
                        else -> continue
                    }
                } else continue
                RelKind.SPOUSE -> if (r.type == RelationshipType.SPOUSE) {
                    when (selfId) {
                        r.sourcePersonId -> r.targetPersonId
                        r.targetPersonId -> r.sourcePersonId
                        else -> continue
                    }
                } else continue
            }
            byId[other]?.let { out.add(it) }
        }
        return out
    }

    private fun livingWord(s: LivingStatus) =
        if (s == LivingStatus.DECEASED) "已故" else "在世"

    private fun rankWord(r: Int?) = r?.let { "第 $it" } ?: "未记录"

    private fun dateLabel(t: ImportantDateType) = when (t) {
        ImportantDateType.BIRTHDAY_SOLAR -> "阳历生日"
        ImportantDateType.BIRTHDAY_LUNAR -> "农历生日"
        ImportantDateType.DEATH_ANNIVERSARY -> "忌日"
        ImportantDateType.WEDDING_ANNIVERSARY -> "结婚纪念日"
        ImportantDateType.LICENSE_ANNIVERSARY -> "领证纪念日"
        ImportantDateType.DATING_ANNIVERSARY -> "恋爱纪念日"
    }

    private fun sameDate(a: ImportantDateEntity, b: ImportantDateEntity) =
        a.year == b.year && a.month == b.month && a.day == b.day &&
            a.calendarType == b.calendarType && a.isLeapMonth == b.isLeapMonth

    private fun describeDate(d: ImportantDateEntity): String {
        val cal = if (d.calendarType == CalendarType.LUNAR) "农历" else "公历"
        val y = if (d.year != null) "${d.year}年" else ""
        val leap = if (d.isLeapMonth) "闰" else ""
        return "$cal $y${leap}${d.month}月${d.day}日"
    }
}
