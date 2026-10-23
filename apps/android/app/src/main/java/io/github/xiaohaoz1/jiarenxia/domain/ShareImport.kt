package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.data.db.toBirthdayValue
import io.github.xiaohaoz1.jiarenxia.data.db.toModel
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge
import io.github.xiaohaoz1.jiarenxia.domain.pinyin.PinyinIndex
import java.util.UUID

/**
 * 家人共享导入的纯逻辑（FAMILY_AFFAIRS 9.4 / 16.3）：
 * 选“我是谁”的候选标签、父母补全判定、“资料里没有我”增补与视角预览。
 */
object ShareImport {

    /** 文件内容 → 称谓引擎用的 FamilyGraph（观察者可换）。 */
    fun graphOf(plan: RestorePlan, lunar: LunarCalendar): FamilyGraph =
        graphOf(plan.persons, plan.relationships, plan.importantDates, lunar)

    /** 实体表 → 称谓引擎用的 FamilyGraph（碰一碰合并后的内存图也走这里）。 */
    fun graphOf(
        persons: List<PersonEntity>,
        relationships: List<RelationshipEntity>,
        dates: List<ImportantDateEntity>,
        lunar: LunarCalendar
    ): FamilyGraph {
        // 引擎只认生日行（阳历/农历两类），忌日/纪念日不参与长幼判断
        val byPerson = dates.filter { it.type.isBirthday }.groupBy { it.personId }
        return FamilyGraph(
            persons = persons.map { it.toModel() },
            relationships = relationships.map { it.toModel() },
            birthdayValues = byPerson.mapValues { (_, list) ->
                list.sortedBy { it.calendarType }.map { it.toBirthdayValue() }
            },
            birthdays = byPerson.mapNotNull { (pid, list) ->
                // 双生日冲突不猜，未知不参与比较
                BirthdayMath.resolveBirthDate(
                    list.map { it.toBirthdayValue() }, lunar
                ).date?.let {
                    pid to PartialDate(it.year, it.monthValue, it.dayOfMonth)
                }
            }.toMap()
        )
    }

    /**
     * “我是谁”候选条目（标签从分享者视角说清关系）：
     * 分享者本人 → “姓名（分享者本人）”；亲属 → “姓名（分享者的X）”；
     * 无关联 → 仅姓名。排序 = FamilyOrdering 分组 + 组内拼音（同 PersonPickList）。
     * [sharerWord]：碰一碰互加亲人传 "对方"（“对方本人”“对方的母亲”）。
     */
    fun candidates(
        plan: RestorePlan,
        engine: KinshipEngine,
        lunar: LunarCalendar,
        sharerWord: String = "分享者"
    ): List<PersonPickItem> {
        val sharer = plan.sharedBy ?: return plan.persons.map {
            PersonPickItem(it.id, it.name, it.name)
        }
        val graph = graphOf(plan, lunar)
        val results = engine.resolveAll(graph, sharer)
        return plan.persons.map { p ->
            val r = results[p.id]
            val deceased = p.livingStatus ==
                io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.DECEASED
            // 已故并入括号：“X（分享者的母亲 · 已故）”；无称谓者“X（已故）”
            val inside = when {
                p.id == sharer -> "${sharerWord}本人"
                r == null || r.kind == KinshipKind.UNRELATED -> ""
                else -> "${sharerWord}的" + r.displayTitle
            }.let { t ->
                listOfNotNull(t.ifEmpty { null },
                    if (deceased) "已故" else null).joinToString(" · ")
            }
            val label = p.name + if (inside.isNotEmpty())
                "（" + inside + "）" else ""
            PersonPickItem(
                personId = p.id, name = p.name, label = label,
                overview = if (r != null) PersonOverview(
                    person = p.toModel(), result = r
                ) else null
            )
        }.sortedWith { a, b ->
            // 分享者本人固定排第一，之后按 分组 + 拼音
            val selfA = a.personId == sharer; val selfB = b.personId == sharer
            if (selfA != selfB) return@sortedWith if (selfA) -1 else 1
            val ga = a.overview?.result?.coreGroup
                ?.let { FamilyOrdering.groupRankOf(it) } ?: 99
            val gb = b.overview?.result?.coreGroup
                ?.let { FamilyOrdering.groupRankOf(it) } ?: 99
            if (ga != gb) ga - gb
            else PinyinIndex.of(a.name).full.compareTo(PinyinIndex.of(b.name).full)
        }
    }

    /**
     * 父母补全（FAMILY_AFFAIRS 9.4）：所选“我”没有 PARENT 记录、
     * 且某个兄弟姐妹已记录父母时返回 该兄弟姐妹 id → 其父母 id 列表。
     * 返回 null 表示无需询问。
     */
    fun adoptableParents(
        plan: RestorePlan, selfId: String
    ): Pair<String, List<String>>? {
        val rels = plan.relationships
        val myParents = rels.filter {
            it.type == RelationshipType.PARENT && it.targetPersonId == selfId
        }
        if (myParents.isNotEmpty()) return null
        val siblings = rels.filter {
            it.type == RelationshipType.SIBLING &&
                (it.sourcePersonId == selfId || it.targetPersonId == selfId)
        }.map {
            if (it.sourcePersonId == selfId) it.targetPersonId else it.sourcePersonId
        }
        val sib = siblings.firstOrNull { s ->
            rels.any { it.type == RelationshipType.PARENT && it.targetPersonId == s }
        } ?: return null
        val parents = rels.filter {
            it.type == RelationshipType.PARENT && it.targetPersonId == sib
        }.map { it.sourcePersonId }
        return sib to parents
    }

    /**
     * “我不是 X”（FAMILY_AFFAIRS 16.2）：备份改按家人共享处理。
     * 只保留与共享文件相同的内容——人物/手机号/关系/重要日期/提醒偏好/头像；
     * 剥掉事项、联系记录与计划、自定义称呼与设置
     * （这些是对方的私密数据）。sharedBy 记为备份里的“我”。
     */
    fun fromBackup(plan: RestorePlan): RestorePlan = plan.copy(
        purpose = "share",
        sharedBy = plan.persons.firstOrNull { it.isSelf }?.id,
        customTitles = emptyList(),
        tasks = emptyList(),
        contactLogs = emptyList(),
        contactPlans = emptyList(),
        settings = null,
        summary = plan.summary.copy(tasks = 0)
    )

    /**
     * “资料里没有我”的关系约束校验（FAMILY_AFFAIRS 16.3/16.6）：
     * 对方已有两位父母时不能选父亲/母亲；已有配偶时不能选丈夫/妻子。
     * 返回错误文案；null = 可建。
     */
    fun newSelfError(plan: RestorePlan, anchorId: String, role: RelationRole): String? {
        val anchor = plan.persons.firstOrNull { it.id == anchorId } ?: return null
        when (role) {
            RelationRole.FATHER, RelationRole.MOTHER ->
                if (plan.relationships.count {
                        it.type == RelationshipType.PARENT &&
                            it.targetPersonId == anchorId
                    } >= 2) {
                    return anchor.name + "已经记了两位父母"
                }
            RelationRole.HUSBAND, RelationRole.WIFE ->
                if (plan.relationships.any {
                        it.type == RelationshipType.SPOUSE &&
                            (it.sourcePersonId == anchorId ||
                                it.targetPersonId == anchorId)
                    }) {
                    return anchor.name + "已经有配偶了"
                }
            else -> {}
        }
        return null
    }

    /**
     * “资料里没有我”：在 plan 末尾追加一位新人物（非“我”，性别由 role 决定）
     * 与一条 [newId]↔[anchorId] 的关系（经 SpouseMerge.normalizeRel 规范化）。
     * 只建这一条关系，不按称呼推断血缘；导入时照旧用 importShare(augmented, newId, …)。
     */
    fun withNewSelf(
        plan: RestorePlan,
        name: String,
        anchorId: String,
        role: RelationRole,
        newId: String,
        now: Long
    ): RestorePlan {
        val spec = role.toRelationshipSpec(newId, anchorId)
        val rel = SpouseMerge.normalizeRel(
            RelationshipEntity(
                id = UUID.randomUUID().toString(),
                sourcePersonId = spec.sourcePersonId,
                targetPersonId = spec.targetPersonId,
                type = spec.type,
                relativeAgeOrder = spec.relativeAgeOrder,
                createdAt = now, updatedAt = now
            )
        )
        return plan.copy(
            persons = plan.persons + PersonEntity(
                id = newId, name = name.trim(), gender = role.gender,
                avatarPath = null, note = null,
                livingStatus = LivingStatus.ALIVE,
                isSelf = false, createdAt = now, updatedAt = now
            ),
            relationships = plan.relationships + rel,
            summary = plan.summary.copy(
                persons = plan.summary.persons + 1,
                relationships = plan.summary.relationships + 1
            )
        )
    }

    /**
     * 共同父母询问（FAMILY_AFFAIRS 16.3）：新增“我”是 [anchorId] 的子女
     * （SON/DAUGHTER）且对方有配偶时返回配偶 id，否则 null。
     */
    fun coParentFor(
        plan: RestorePlan, anchorId: String, role: RelationRole
    ): String? {
        if (role != RelationRole.SON && role != RelationRole.DAUGHTER) return null
        val r = plan.relationships.firstOrNull {
            it.type == RelationshipType.SPOUSE &&
                (it.sourcePersonId == anchorId || it.targetPersonId == anchorId)
        } ?: return null
        return if (r.sourcePersonId == anchorId) r.targetPersonId else r.sourcePersonId
    }

    /**
     * 仅供“从你的角度看”预览：把确认补上的父母（PARENT: pid→selfId）追加进
     * plan 关系列表（已存在的跳过），让预览里的称谓与导入结果一致。
     */
    fun withAdoptedParents(
        plan: RestorePlan, selfId: String, parentIds: List<String>
    ): RestorePlan {
        if (parentIds.isEmpty()) return plan
        val existing = plan.relationships.filter {
            it.type == RelationshipType.PARENT && it.targetPersonId == selfId
        }.map { it.sourcePersonId }.toSet()
        val now = System.currentTimeMillis()
        val add = parentIds.filter { it !in existing }.map { pid ->
            RelationshipEntity(
                id = UUID.randomUUID().toString(),
                sourcePersonId = pid, targetPersonId = selfId,
                type = RelationshipType.PARENT,
                relativeAgeOrder = null, createdAt = now, updatedAt = now
            )
        }
        return plan.copy(relationships = plan.relationships + add)
    }

    /** “从你的角度看”预览的一行。 */
    data class PreviewLine(val personId: String, val name: String, val title: String)

    /** 预览结果：lines = 前 [limit] 位家人（核心家人在前）；unresolved = 算不出称谓的人数。 */
    data class PerspectivePreview(val lines: List<PreviewLine>, val unresolved: Int)

    /**
     * “从你的角度看”预览（FAMILY_AFFAIRS 16.3）：以 [selfId] 为观察者
     * 推导称谓，排序同家庭页（核心分组 + 其余按路径/姓名），
     * 排除本人与 UNRELATED（计入 unresolved）；已故者称谓后加“（已故）”。
     */
    fun perspectivePreview(
        plan: RestorePlan,
        selfId: String,
        engine: KinshipEngine,
        lunar: LunarCalendar,
        limit: Int = 8
    ): PerspectivePreview {
        val graph = graphOf(plan, lunar)
        val results = engine.resolveAll(graph, selfId)
        val byId = plan.persons.mapNotNull { p ->
            val r = results[p.id] ?: return@mapNotNull null
            p.id to PersonOverview(person = p.toModel(), result = r)
        }.toMap()
        val rest = byId.values.filter { it.person.id != selfId }
        val unresolved = rest.count { it.result.kind == KinshipKind.UNRELATED }
        val related = rest.filter { it.result.kind != KinshipKind.UNRELATED }
        val ordered =
            FamilyOrdering.core(related.filter { it.result.coreGroup != null }, byId) +
                FamilyOrdering.others(related.filter { it.result.coreGroup == null })
        val lines = ordered.take(limit).map { o ->
            PreviewLine(
                personId = o.person.id,
                name = o.person.name,
                title = o.displayTitle +
                    if (o.person.livingStatus == LivingStatus.DECEASED) "（已故）" else ""
            )
        }
        return PerspectivePreview(lines, unresolved)
    }

    /**
     * “和我结为夫妻”可行性预判（FAMILY_AFFAIRS 16.3，0.9.4 缺陷修复：
     * 合并计划以本机“我”[localSelfId] 计算，不再误用文件里所选人物的 id）。
     * 点“和我结为夫妻”并确认预览 = 用户明确确认“对方就是我的配偶”，
     * 故 approved 恒含 [peerSelfId]（否则“我已录入配偶、对方文件里没有我”
     * 会被 plan 以“请先确认对方就是你录入的配偶”拒绝，入口永不出现）。
     * [selectedId] 为“你是谁”里选中的文件人物：正是对方录入的配偶记录时
     * 作为种子映射传入（即使对方给我记的名字不同，如“老公”，也能认回我）。
     * 仍有未确认的阻塞候选时不提供此入口（返回 null）。
     */
    fun spouseMergePlan(
        local: SpouseMerge.Snapshot,
        remote: SpouseMerge.Snapshot,
        peerSelfId: String,
        localSelfId: String,
        selectedId: String?
    ): SpouseMerge.MergePlan? {
        val peerSpouseId = remote.relationships.firstOrNull {
            it.type == RelationshipType.SPOUSE &&
                (it.sourcePersonId == peerSelfId || it.targetPersonId == peerSelfId)
        }?.let {
            if (it.sourcePersonId == peerSelfId) it.targetPersonId else it.sourcePersonId
        }
        val knownMap =
            if (selectedId != null && selectedId == peerSpouseId)
                mapOf(selectedId to localSelfId)
            else emptyMap()
        val r = SpouseMerge.plan(
            local, remote, localSelfId, peerSelfId,
            knownMap = knownMap, approved = setOf(peerSelfId))
        val p = (r as? SpouseMerge.Result.Ok)?.plan ?: return null
        if (p.candidates.any { it.blocking }) return null
        return p
    }
}
