package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import java.util.UUID

/**
 * 共享“和我结为夫妻”的合并逻辑（FAMILY_AFFAIRS 第 10 节，纯 JVM）。
 *
 * 输入双方快照（人物/关系/手机号/生日；不含头像与私密模块）与各自的“我”的 id；
 * 输出一个写入计划（MergePlan），由写入方在一个事务里执行。
 *
 * “同一个人”识别分四层：
 * 1. 持久映射 [knownMap]（来源人物 id → 本机人物 id，来自配对记录）——
 *    同一来源人物永远复用既有映射，这是重放/重连/页面重建幂等的根基；
 * 2. 来源回环 [Snapshot.origins]：对方人物标注的 oid 指向本机仍存在的
 *    人物且姓名+性别一致——这条人物本来就从本机出去，直接并入映射；
 *    oid 在但姓名/性别对不上则降为候选（可能是改名，也可能是误标）；
 * 3. 精确 id 复用：对方人物 id 已存在于本机且姓名+性别一致时视为同一人
 *    （随机 UUID 实质不会撞；撞的是上一次导入留下的同一人）；
 * 4. 结构匹配只产生“候选”：从每一对已映射/已候选的对出发做广度扩展，
 *    对已映射对的父母/子女/兄弟姐妹/配偶按 姓名+性别+关系位置 匹配、且
 *    要求两边候选都唯一的，记入 [MergePlan.candidates]——同名同性别
 *    同一个关系位置仍可能不是同一个人，只有调用方把来源 id 放进
 *    [approved]（用户明确确认）才真正落映射；未确认的候选按新人物处理。
 *    不按称谓推断血缘，也不自动合并/删除历史重复人物。
 *
 * 补充式更新：关系只新增；手机号按（人,号码）去重补充；重要日期只补本机
 * 缺失的类型（阳历/农历生日各占独立槽位），同类型不同值记为冲突
 * （[MergePlan.conflicts]）但不覆盖。
 */
object SpouseMerge {

    /** 一方快照（本机或对方）。 */
    data class Snapshot(
        val persons: List<PersonEntity>,
        val phones: List<PhoneNumberEntity>,
        val relationships: List<RelationshipEntity>,
        val dates: List<ImportantDateEntity>,
        /**
         * 来源标注：人物 id → 对方当初收到此人物时的来源 id（原为碰一碰资料包
         * 携带的 oid）。指向本机仍存在的同姓名同性别人物即“回环资料”——
         * 这条人物本来就从本机出去，直接复用映射，不再作为候选打扰用户。
         */
        val origins: Map<String, String> = emptyMap()
    )

    /** 一条无法自动处理的差异（预览展示；本机值始终保留）。 */
    data class PlanConflict(
        /** 本机人物姓名（展示用）。 */
        val localName: String,
        /** 字段名：name / 重要日期类型名。 */
        val field: String,
        /** 本机现有值描述。 */
        val localValue: String,
        /** 对方快照里的值描述。 */
        val remoteValue: String
    )

    /**
     * 疑似同一人的待确认候选（姓名+性别+关系位置一致、两边唯一）。
     * 确认：把 [remoteId] 加入 `approved` 重新调用 plan；
     * 不确认：保持两个独立人物（本机原记录原样保留，不自动合并）。
     */
    data class MatchCandidate(
        /** 对方快照里的人物 id。 */
        val remoteId: String,
        /** 疑似同一人的本机人物 id。 */
        val localId: String,
        /** 姓名（两边一致）。 */
        val name: String,
        /** 位置描述（如“李雪的父亲”），供确认页展示。 */
        val desc: String,
        /**
         * 结构性候选：不确认则无法完成合并——如“对方录入的配偶是你本人”，
         * 若保持独立会产生一个假的“你”并与错误的配偶关系共存，
         * 因此不确认 = 取消本次合并。
         */
        val blocking: Boolean = false
    )

    /** 合并写入计划。 */
    data class MergePlan(
        /** 对方 id → 本机 id（同一个人映射，含对方的“我”与已知映射种子）。 */
        val idMap: Map<String, String>,
        /** 新增人物（is_self = false；id 冲突者已重新生成）。 */
        val newPersons: List<PersonEntity>,
        /** 新增关系（端点已按 idMap 重写；含两人之间的 SPOUSE）。 */
        val newRelationships: List<RelationshipEntity>,
        /** 新增手机号（新人物的全部 + 映射人物缺少的号码）。 */
        val newPhones: List<PhoneNumberEntity>,
        /** 新增生日（新人物的全部 + 映射人物缺失生日的补齐）。 */
        val newDates: List<ImportantDateEntity>,
        /** 本次新识别为同一人的本机已录入者姓名（确认页预览）。 */
        val mergedNames: List<String>,
        /** 对方人物 → 本机人物的完整映射（含新增人物；持久化为来源映射）。 */
        val mapping: Map<String, String>,
        /** 无法自动处理的差异清单（姓名/日期不一致等；本机值保留）。 */
        val conflicts: List<PlanConflict>,
        /**
         * 待确认候选（来源→本机）。调用方须先展示给用户确认，
         * 确认后把 remoteId 放进 approved 重新生成计划再写入；
         * 本计划本身已按“未确认”生成（候选仍是新人物），直接写入安全。
         */
        val candidates: List<MatchCandidate> = emptyList()
    ) {
        /** 计划为空 = 同一资料重放/对方无新增（幂等短路）。 */
        fun empty(): Boolean = newPersons.isEmpty() &&
            newRelationships.isEmpty() && newPhones.isEmpty() &&
            newDates.isEmpty()
    }

    sealed class Result {
        data class Ok(val plan: MergePlan) : Result()
        data class Reject(val reason: String) : Result()
    }

    /**
     * [local] 本机快照、[remote] 对方快照、[myId] 本机“我”、
     * [peerSelfId] 对方“我”、[knownMap] 已持久化的来源→本机映射（可空）。
     * [approved] 本次会话用户明确确认过的来源人物 id：
     * - 含对方“我”的 id = 确认“对方就是我录入的配偶”；
     * - 含某候选 remoteId = 确认该候选是同一个人。
     * 每次调用生成一个新 plan（新增人物 id 即时生成）。
     */
    fun plan(
        local: Snapshot,
        remote: Snapshot,
        myId: String,
        peerSelfId: String,
        knownMap: Map<String, String> = emptyMap(),
        approved: Set<String> = emptySet()
    ): Result {
        val me = local.persons.firstOrNull { it.id == myId }
        val peer = remote.persons.firstOrNull { it.id == peerSelfId }
            ?: return Result.Reject("对方资料不完整")
        if (me == null) return Result.Reject("未找到本机的“我”")
        if (me.gender == peer.gender) {
            return Result.Reject("结为夫妻需要一男一女")
        }
        val localById = local.persons.associateBy { it.id }
        val remoteById = remote.persons.associateBy { it.id }

        // 已知映射种子：只收两端都仍然存在的对（本机人物被删则映射作废，
        // 对方人物按新人物处理）
        val seeded = LinkedHashMap<String, String>()
        for ((rid, lid) in knownMap) {
            if (rid in remoteById && lid in localById) seeded[rid] = lid
        }

        // ---------- 双方现有配偶都必须是对方（或没有） ----------
        // 配对过的同一来源人物允许改名（以持久映射为准）；否则按 姓名+性别。
        val mySpouse = spouseOf(local, myId)
        val peerSelfLocal = seeded[peerSelfId]
        if (mySpouse != null &&
            !(mySpouse.name == peer.name && mySpouse.gender == peer.gender) &&
            peerSelfLocal != mySpouse.id
        ) {
            return Result.Reject("你的家人里已经有配偶了")
        }
        if (peerSelfLocal != null && mySpouse != null &&
            peerSelfLocal != mySpouse.id
        ) {
            return Result.Reject("配偶与配对记录不一致")
        }
        val peerSpouse = spouseOf(remote, peerSelfId)
        if (peerSpouse != null &&
            !(peerSpouse.name == me.name && peerSpouse.gender == me.gender) &&
            seeded[peerSpouse.id] != myId &&
            remote.origins[peerSpouse.id] != myId
        ) {
            return Result.Reject("对方的家人里已经有配偶了")
        }

        // ---------- “同一个人”映射 ----------
        val idMap = LinkedHashMap<String, String>()
        idMap.putAll(seeded)
        /** 本次运行新识别出的对（区别于持久映射种子），用于预览与扩展。 */
        val freshMatched = LinkedHashMap<String, String>()
        fun matched(remoteId: String, localId: String, fresh: Boolean) {
            idMap[remoteId] = localId
            if (fresh && remoteId !in seeded) freshMatched[remoteId] = localId
        }
        /** 结构匹配产生的待确认候选（remoteId → 候选）。 */
        val candidates = LinkedHashMap<String, MatchCandidate>()
        if (mySpouse != null && peerSelfId !in idMap) {
            // 本机已录入的配偶 ↔ 对方的“我”：此时必然同名同性别
            // （不匹配上面已拒绝）。同名只是候选——须本机用户在确认页
            // 明确“对方就是已录入的配偶”后才复用该记录
            if (peerSelfId in approved) {
                matched(peerSelfId, mySpouse.id, true)
            } else {
                return Result.Reject("请先确认对方就是你录入的配偶")
            }
        }
        if (peerSpouse != null && peerSpouse.id !in idMap &&
            (remote.origins[peerSpouse.id] == myId ||
                (peerSpouse.name == me.name && peerSpouse.gender == me.gender))
        ) {
            // 对方录入的“我”：oid 回本机/双方已互认则自动并入；否则是阻塞
            // 候选——不确认就不能继续合并，否则会在本机造出一个假的“我”并
            // 挂错配偶关系
            if (peerSpouse.id in approved || peerSelfId in approved ||
                remote.origins[peerSpouse.id] == myId
            ) {
                matched(peerSpouse.id, myId, true)
            } else {
                candidates[peerSpouse.id] = MatchCandidate(
                    remoteId = peerSpouse.id, localId = myId,
                    name = me.name, desc = "对方录入的配偶",
                    blocking = true)
            }
        }

        // 来源回环：oid 指向本机仍存在的同姓名同性别人物 → 这条人物本来就
        // 从本机出去（上一次合并/更新导出过），直接并入映射不作候选；
        // oid 在但姓名/性别对不上 → 降为候选（可能改名或误标）。
        // 指向本机“我”的 oid 不在这里处理（配偶路径已覆盖）。
        for (p in remote.persons) {
            if (p.id in idMap || p.id in candidates || p.id == peerSelfId) {
                continue
            }
            val lid = remote.origins[p.id] ?: continue
            val lp = localById[lid] ?: continue
            if (lp.id == myId) continue
            if (lp.name == p.name && lp.gender == p.gender ||
                p.id in approved
            ) {
                // oid 对得上/用户已勾选 → 并入映射
                matched(p.id, lid, true)
            } else {
                candidates[p.id] = MatchCandidate(
                    remoteId = p.id, localId = lid, name = lp.name,
                    desc = "对方标注此人来自本机「" + p.name + "」")
            }
        }

        // 多层扩展：从每一对已映射/已候选的对出发，逐层提出同名同性别
        // 同关系位置的候选（两边唯一才提出）。候选也入队扩展——更深的
        // 亲属候选不依赖上层是否已确认。
        val queue = ArrayDeque<Pair<String, String>>()
        val enqueued = HashSet<String>()
        fun push(rid: String, lid: String) {
            if (enqueued.add(rid)) queue.add(rid to lid)
        }
        for ((r, l) in idMap) push(r, l)
        for (c in candidates.values) push(c.remoteId, c.localId)
        fun localTaken(lid: String) =
            lid in idMap.values || candidates.values.any { it.localId == lid }
        while (queue.isNotEmpty()) {
            val (rid, lid) = queue.removeFirst()
            val viaName = remoteById[rid]?.name ?: continue
            for (kind in RelKind.entries) {
                val remoteNbrs = neighbors(remote, rid, kind)
                    .filter {
                        it.id !in idMap && it.id !in candidates &&
                            it.id != peerSelfId
                    }
                if (remoteNbrs.isEmpty()) continue
                val localNbrs = neighbors(local, lid, kind)
                    .filter { it.id != myId && !localTaken(it.id) }
                if (localNbrs.isEmpty()) continue
                val remoteGroups = remoteNbrs.groupBy { it.name to it.gender }
                val localGroups = localNbrs.groupBy { it.name to it.gender }
                for ((key, rs) in remoteGroups) {
                    val ls = localGroups[key] ?: continue
                    // 同名同性别多人不猜——两边都唯一才成为候选
                    if (rs.size != 1 || ls.size != 1) continue
                    val (r, l) = rs[0].id to ls[0].id
                    if (r in approved) {
                        // 已确认候选：直接落映射
                        matched(r, l, true)
                    } else {
                        candidates[r] = MatchCandidate(
                            remoteId = r, localId = l, name = ls[0].name,
                            desc = viaName + "的" + kindLabel(kind, ls[0].gender))
                    }
                    push(r, l)
                }
            }
        }

        // ---------- id 冲突处理 ----------
        val localIds = localById.keys
        val remoteNewIds = HashMap<String, String>()
        for (p in remote.persons) {
            if (p.id in idMap) continue
            val existing = localById[p.id]
            when {
                existing == null -> remoteNewIds[p.id] = p.id
                // 同一 id 且姓名+性别一致：视为同一人（上次导入已按原 id
                // 落库）；映射进 idMap，不作为新人物
                existing.name == p.name && existing.gender == p.gender ->
                    matched(p.id, p.id, true)
                // 真正无关的 id 冲突：安全区分，重新生成本机 id
                else -> remoteNewIds[p.id] = newId("p")
            }
        }
        val rewrite = { id: String -> idMap[id] ?: remoteNewIds[id] ?: id }
        val localRelIds = local.relationships.map { it.id }.toHashSet()
        val localPhoneIds = local.phones.map { it.id }.toHashSet()
        val localDateIds = local.dates.map { it.id }.toHashSet()
        fun relId(id: String) = if (id in localRelIds) newId("r") else id
        fun phoneId(id: String) = if (id in localPhoneIds) newId("p") else id
        fun dateId(id: String) = if (id in localDateIds) newId("d") else id

        // ---------- 写入计划 ----------
        val newPersons = remote.persons
            .filter { it.id !in idMap }
            .map { it.copy(id = remoteNewIds[it.id]!!, isSelf = false) }

        val conflicts = mutableListOf<PlanConflict>()
        val existingKeys = local.relationships.map { pairKey(it) }.toHashSet()
        val newRels = mutableListOf<RelationshipEntity>()
        for (r in remote.relationships) {
            val nr = normalizeRel(
                r.copy(
                    id = relId(r.id),
                    sourcePersonId = rewrite(r.sourcePersonId),
                    targetPersonId = rewrite(r.targetPersonId)
                )
            )
            // 对称关系按无序对去重
            if (pairKey(nr) in existingKeys) continue
            existingKeys.add(pairKey(nr))
            newRels.add(nr)
        }
        // 两人之间的 SPOUSE（规范化存储：source < target）
        val peerLocal = rewrite(peerSelfId)
        val spouseKey = unorderedKey(
            myId, peerLocal, RelationshipType.SPOUSE)
        if (existingKeys.none {
                it.first == spouseKey.first &&
                    it.second == RelationshipType.SPOUSE
            }
        ) {
            val (s0, t0) = if (myId < peerLocal) myId to peerLocal
            else peerLocal to myId
            newRels.add(
                RelationshipEntity(
                    id = newId("r"), sourcePersonId = s0, targetPersonId = t0,
                    type = RelationshipType.SPOUSE,
                    relativeAgeOrder = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 关系约束（FAMILY_AFFAIRS 16.6）：
        // 把合并后的关系逐条在本机图上验约束；违规不写入，列入冲突清单
        val graph = RelationGraph(
            local.persons.associateBy { it.id } +
                newPersons.associateBy { it.id },
            local.relationships
        )
        val checkedRels = ArrayList<RelationshipEntity>(newRels.size)
        for (nr in newRels) {
            val v = graph.check(nr)
            if (v == null) {
                graph.add(nr); checkedRels += nr
            } else if (!v.identical) {
                conflicts += PlanConflict(
                    localName = "", field = "relation",
                    localValue = v.reason,
                    remoteValue = graph.describe(nr))
            }
            // identical = 完全相同 → 静默跳过（pairKey 去重已先挡一道）
        }
        newRels.clear(); newRels.addAll(checkedRels)

        // 手机号：新人物全部 + 映射人物本机缺少的号码
        val newPhones = mutableListOf<PhoneNumberEntity>()
        val localPhoneKey = local.phones.map { it.personId to it.number }.toSet()
        for (ph in remote.phones) {
            val pid = rewrite(ph.personId)
            val alreadyHas = pid to ph.number in
                (localPhoneKey + newPhones.map { it.personId to it.number })
            if (alreadyHas) continue
            newPhones.add(ph.copy(id = phoneId(ph.id), personId = pid))
        }

        // 重要日期：新人物全部带上；被映射人物只补本机缺少的类型，
        // UNIQUE(person_id,type)——同类型已有：一致跳过，不一致记冲突不覆盖
        val localDates = local.dates.associateBy { it.personId to it.type }
        val newDates = mutableListOf<ImportantDateEntity>()
        val seenDateKeys = HashSet<Pair<String,
            io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType>>()
        for (d in remote.dates) {
            val pid = rewrite(d.personId)
            val key = pid to d.type
            if (d.personId !in remoteNewIds) {
                val existing = localDates[key]
                if (existing != null) {
                    if (!sameDate(existing, d)) {
                        conflicts.add(PlanConflict(
                            localName = localById[pid]?.name ?: "",
                            field = d.type.name,
                            localValue = describeDate(existing),
                            remoteValue = describeDate(d)))
                    }
                    continue
                }
            }
            if (!seenDateKeys.add(key)) continue
            newDates.add(d.copy(id = dateId(d.id), personId = pid))
        }

        // 被映射人物姓名不一致也记为冲突（不自动改名）
        for ((rid, lid) in idMap) {
            val lp = localById[lid] ?: continue
            val rp = remoteById[rid] ?: continue
            if (lp.name != rp.name) {
                conflicts.add(PlanConflict(
                    localName = lp.name, field = "name",
                    localValue = lp.name, remoteValue = rp.name))
            }
        }

        // 本次新识别为同一人的本机人物姓名（确认预览）
        val mergedNames = freshMatched.values
            .mapNotNull { localById[it]?.name }
            .filter { it != me.name }

        // 完整来源→本机映射（新增人物也记录，供持久化与重放复用）
        val mapping = remote.persons.associate { it.id to rewrite(it.id) }

        return Result.Ok(
            MergePlan(
                idMap = idMap, newPersons = newPersons,
                newRelationships = newRels, newPhones = newPhones,
                newDates = newDates, mergedNames = mergedNames,
                mapping = mapping, conflicts = conflicts,
                candidates = candidates.values.toList()
            )
        )
    }

    /** 现有配偶（任意端点方向）。 */
    private fun spouseOf(s: Snapshot, selfId: String): PersonEntity? {
        val pair = s.relationships.firstOrNull {
            it.type == RelationshipType.SPOUSE &&
                (it.sourcePersonId == selfId || it.targetPersonId == selfId)
        } ?: return null
        val other = if (pair.sourcePersonId == selfId) {
            pair.targetPersonId
        } else {
            pair.sourcePersonId
        }
        return s.persons.firstOrNull { it.id == other }
    }

    /** 亲属方向：PARENT=此人的父母、CHILD=子女、SIBLING、SPOUSE、COUSIN。 */
    private enum class RelKind { PARENT, CHILD, SIBLING, SPOUSE, COUSIN }

    /** 候选位置称谓（确认页展示用）：按关系方向与性别。 */
    private fun kindLabel(kind: RelKind, g: Gender) = when (kind) {
        RelKind.PARENT -> if (g == Gender.MALE) "父亲" else "母亲"
        RelKind.CHILD -> if (g == Gender.MALE) "儿子" else "女儿"
        RelKind.SIBLING -> if (g == Gender.MALE) "兄弟" else "姐妹"
        RelKind.SPOUSE -> "配偶"
        RelKind.COUSIN -> "堂表亲"
    }

    /** 某人某方向的全部亲属人物。 */
    private fun neighbors(
        s: Snapshot, selfId: String, kind: RelKind
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
                // 申报堂表亲同样作为结构认人线索（两端无序）
                RelKind.COUSIN -> if (r.type == RelationshipType.COUSIN) {
                    when (selfId) {
                        r.sourcePersonId -> r.targetPersonId
                        r.targetPersonId -> r.sourcePersonId
                        else -> continue
                    }
                } else continue
            }
            s.persons.firstOrNull { it.id == other }?.let { out.add(it) }
        }
        return out
    }

    /** 同类型日期是否同值（年月日/历法等比较）。 */
    private fun sameDate(a: ImportantDateEntity, b: ImportantDateEntity) =
        a.year == b.year && a.month == b.month && a.day == b.day &&
            a.calendarType == b.calendarType && a.isLeapMonth == b.isLeapMonth

    private fun describeDate(d: ImportantDateEntity): String {
        val cal = if (d.calendarType ==
            io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR
        ) "农历" else "公历"
        val y = if (d.year != null) "${d.year}年" else ""
        val leap = if (d.isLeapMonth) "闰" else ""
        return "$cal $y${leap}${d.month}月${d.day}日"
    }

    /**
     * 对称关系规范化（DATABASE_SCHEMA 7.13/7.14）：SPOUSE/SIBLING/COUSIN
     * 存 sourcePersonId < targetPersonId；SIBLING/COUSIN 交换端点时
     * relativeAgeOrder 同步翻转。
     */
    fun normalizeRel(r: RelationshipEntity): RelationshipEntity {
        if (r.type != RelationshipType.SPOUSE &&
            r.type != RelationshipType.SIBLING &&
            r.type != RelationshipType.COUSIN
        ) return r
        if (r.sourcePersonId <= r.targetPersonId) return r
        return r.copy(
            sourcePersonId = r.targetPersonId,
            targetPersonId = r.sourcePersonId,
            relativeAgeOrder = if (r.type == RelationshipType.SIBLING ||
                r.type == RelationshipType.COUSIN)
                r.relativeAgeOrder?.inverse() else r.relativeAgeOrder
        )
    }

    /** 去重键：SPOUSE/SIBLING 当作无序对。 */
    private fun pairKey(r: RelationshipEntity) =
        unorderedKey(r.sourcePersonId, r.targetPersonId, r.type)

    private fun unorderedKey(a: String, b: String, t: RelationshipType) =
        (if (a < b) a to b else b to a) to t

    private fun newId(prefix: String) = prefix + "-" +
        UUID.randomUUID().toString()
}
