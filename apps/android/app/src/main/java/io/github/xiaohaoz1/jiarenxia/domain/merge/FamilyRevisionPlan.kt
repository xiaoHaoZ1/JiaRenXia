package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot.DateEntry
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot.Person
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot.Phone
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot.Rel
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot.Snap

/**
 * 确认版/补充包的三方比较与落库计划（协作 §5.3，纯 JVM）。
 *
 * 输入全部在名册规范 id 空间：
 * - base：上次确认版的基准快照（null = 无可靠基准，进入保守模式——
 *   缺项绝不推断为删除）；
 * - remote：收到的内容（成员侧=新确认版 M；汇总者侧=对方补充 R）；
 * - local：本机当前名册。
 *
 * 每项差异的处置沿用规格表：
 * R=B → 保留本机；L=B 且 R≠B → 应用收到的；L=R → 已一致；
 * 双改不一致 → 分歧项（UNRESOLVED，必须显式选“保留我的/采用收到的”）；
 * 无可靠基准对应 → 保守新增或显式裁决，不自动删、不自动认人。
 *
 * 本机独有改动（L≠B 且未被 R 覆盖）计入 PENDING（待汇入补充），不写入；
 * 成员接收确认版时 PENDING 项做“未汇入”提示与保护选择。
 */
object FamilyRevisionPlan {

    /** 单项状态。 */
    enum class State {
        /** 远端侧更新（本机未改或双方同改）——应用。 */
        APPLY,
        /** 已一致，无操作。 */
        SAME,
        /** 本机改动、远端未覆盖——不写入，仅提示/保护。 */
        PENDING,
        /** 双改不一致——必须显式选择。 */
        CONFLICT,
        /** 远端新增人物疑似本机已有——必须显式裁决身份。 */
        IDENTITY,
        /** 曾被明确移除的规范人物再现——必须显式裁决是否重新加入。 */
        REAPPEAR,
        /** 硬性阻止项（移除“我”、把“我”标成已故）：只能保留本机。 */
        BLOCKED
    }

    /** 决策值。 */
    enum class Choice(val wire: String) {
        KEEP_LOCAL("keep"),
        TAKE_REMOTE("take"),
        /** 仅关系冲突：不采用收到的这条关系（算已处理）。 */
        SKIP("skip");

        companion object {
            fun of(wire: String?) = entries.firstOrNull { it.wire == wire }
        }
    }

    /** 身份裁决：远端规范 id → Same(本机规范 id) / New。 */
    sealed interface IdentChoice {
        data class Same(val localCanonicalId: String) : IdentChoice
        data object New : IdentChoice

        companion object {
            fun parse(wire: String?): IdentChoice? = when {
                wire == "new" -> New
                wire != null && wire.startsWith("same:") ->
                    Same(wire.removePrefix("same:"))
                else -> null
            }

            fun wire(c: IdentChoice): String = when (c) {
                is Same -> "same:" + c.localCanonicalId
                New -> "new"
            }
        }
    }

    // ---------- 差异项 ----------

    sealed interface Item {
        /** 稳定键：决策与草稿恢复按它对应。 */
        val key: String
        val state: State
        /** 预览用中文描述（含具体姓名/前后值）。 */
        val text: String
        /** 关联规范人物 id（排序/分组用）。 */
        val personId: String
    }

    /** 人物新增（含待裁决身份）。[person] 为远端侧快照人物。 */
    data class PersonAdd(
        override val key: String,
        val person: Person,
        override val state: State,
        override val text: String,
        /** 疑似同一人候选（本机侧规范 id + 姓名）。 */
        val candidates: List<Candidate> = emptyList()
    ) : Item {
        override val personId get() = person.id
    }

    data class Candidate(val localId: String, val name: String, val sharedVia: String)

    /** 人物移除（远端名册不再有此人）。 */
    data class PersonRemove(
        override val key: String,
        override val personId: String,
        val name: String,
        override val state: State,
        override val text: String,
        /** true = 是“我”，禁止采用（只能保留）。 */
        val blocked: Boolean = false,
        /** 数据层补齐：有私人外键 → 移除落“仅本机保留”而非物理删除。 */
        val privateRefs: Boolean = false
    ) : Item

    /** 人物标量字段差异（姓名/性别/在世状态/备注/照片）。 */
    data class FieldItem(
        override val key: String,
        override val personId: String,
        val personName: String,
        val field: String,
        val base: String?, val remote: String?, val local: String?,
        override val state: State,
        override val text: String,
        val blocked: Boolean = false
    ) : Item

    /** 电话条目差异（按规范号码对应）。 */
    data class PhoneItem(
        override val key: String,
        override val personId: String,
        val personName: String,
        val number: String,
        val base: Phone?, val remote: Phone?, val local: Phone?,
        override val state: State,
        override val text: String
    ) : Item

    /** 日期条目差异（按类型槽位对应）。 */
    data class DateItem(
        override val key: String,
        override val personId: String,
        val personName: String,
        val type: String,
        val base: DateEntry?, val remote: DateEntry?, val local: DateEntry?,
        override val state: State,
        override val text: String
    ) : Item

    /** 关系差异（按规范化元组对应）。 */
    data class RelItem(
        override val key: String,
        val rel: Rel,
        override val personId: String,
        val inBase: Boolean, val inRemote: Boolean, val inLocal: Boolean,
        /** 远端要加入但与本机约束矛盾时的说明（null=无矛盾）。 */
        val violation: String?,
        /** 采用时需一并移除的本机关系（矛盾的另一条），已是规范空间。 */
        val alsoRemoves: List<Rel>,
        override val state: State,
        override val text: String
    ) : Item

    // ---------- 计划 ----------

    data class Plan(
        /** 全部差异项（稳定排序）。 */
        val items: List<Item>,
        /** 本机相对基准的全部差异（待汇入补充提示用）。 */
        val pendingLocal: List<Item>
    ) {
        /** 待处理决策项（分歧/身份/再现/阻止）。 */
        val decisions: List<Item> get() = items.filter {
            it.state == State.CONFLICT || it.state == State.IDENTITY ||
                it.state == State.REAPPEAR || it.state == State.BLOCKED
        }

        /** 自动应用的远端更新项。 */
        val auto: List<Item> get() = items.filter { it.state == State.APPLY }

        /** 未处理完不能接收。 */
        fun ready(decisions: Map<String, String>): Boolean =
            this.decisions.all { decisions.containsKey(it.key) }
    }

    // ---------- 入口 ----------

    class Ctx(
        /** 本机“我”的规范 id（移除/标亡阻止用；无则为 null）。 */
        val selfCanonicalId: String?,
        /** 曾被明确移除的规范人物 id（旧包复活防护）。 */
        val removedIds: Set<String> = emptySet(),
        /** 远端新人物的身份裁决（远端规范 id → Same(本机规范id)/New）。 */
        val identities: Map<String, IdentChoice> = emptyMap()
    )

    /**
     * 计算三方差异计划。所有 id 均为规范空间；
     * 远端人物先按 [Ctx.identities] 的 SAME 裁决重映射到本机规范 id 再比较。
     */
    fun plan(base: Snap?, remote: Snap, local: Snap, ctx: Ctx): Plan {
        // 0. 身份裁决重映射：远端规范 id → 本机规范 id
        val remapped = FamilySnapshot.remap(remote,
            ctx.identities.mapNotNull { (rid, c) ->
                (c as? IdentChoice.Same)?.let { rid to it.localCanonicalId }
            }.toMap())
        val rMap = remapped.personMap
        val lMap = local.personMap
        val bMap = base?.persons.orEmpty().associateBy { it.id }
        val items = ArrayList<Item>()
        val pending = ArrayList<Item>()

        // 身份未决的远端新人物：其关系暂不列项（等裁决后重算）
        val undecidedNew = HashSet<String>()
        // 远端整体移除的人：其条目/关系差异由去留项覆盖
        val remoteRemoved = HashSet<String>()
        for ((bid, _) in bMap) {
            if (!rMap.containsKey(bid)) remoteRemoved += bid
        }

        // ---------- 1. 人物存在性 ----------
        for (rp in remapped.persons) {
            val bp = bMap[rp.id]
            val lp = lMap[rp.id]
            if (lp != null) continue  // 两端都有 → 字段级比较
            when {
                bp != null -> {
                    // 基准有、远端有、本机无：本机曾把它删了（待汇入的删除）
                    pending += PersonRemove(
                        key = "rm:" + rp.id, personId = rp.id, name = bp.name,
                        state = State.PENDING,
                        text = "本机已移除「" + bp.name + "」，收到的版本仍有此人"
                    )
                }
                rp.id in ctx.removedIds -> {
                    items += PersonAdd(
                        key = "add:" + rp.id, person = rp,
                        state = State.REAPPEAR,
                        text = "「" + rp.name + "」曾被确认移除，收到的版本里又出现了"
                    )
                }
                else -> {
                    val cands = candidates(rp, remapped, local, bMap)
                    if (cands.isEmpty()) {
                        items += PersonAdd(
                            key = "add:" + rp.id, person = rp, state = State.APPLY,
                            text = "新增家人「" + rp.name + "」"
                        )
                    } else {
                        // 已有裁决的人不再算“未决”（Same 裁决已在重映射
                        // 里并入本机人，走到这里的是 New）——否则裁决为
                        // 新家人后，它的远端关系永远进不了计划项，应用时
                        // 被静默丢弃
                        if (rp.id !in ctx.identities) undecidedNew += rp.id
                        items += PersonAdd(
                            key = "add:" + rp.id, person = rp,
                            state = State.IDENTITY,
                            text = "「" + rp.name + "」可能是你家已录入的人",
                            candidates = cands
                        )
                    }
                }
            }
        }
        for ((bid, bp) in bMap) {
            if (rMap.containsKey(bid)) continue  // 远端保留
            val lp = lMap[bid] ?: continue       // 本机也没有 → 一致移除
            val blocked = bid == ctx.selfCanonicalId
            items += PersonRemove(
                key = "rm:" + bid, personId = bid, name = bp.name,
                state = if (blocked) State.BLOCKED else State.CONFLICT,
                text = if (blocked)
                    "收到的版本要把「我」从名册移除——不能照收"
                else "将从家庭名册移除「" + lp.name + "」",
                blocked = blocked
            )
        }
        // 本机独有、基准与远端都没有的人：本机新增（待汇入）
        for (lp in local.persons) {
            if (rMap.containsKey(lp.id) || bMap.containsKey(lp.id)) continue
            pending += PersonAdd(
                key = "ladd:" + lp.id, person = lp, state = State.PENDING,
                text = "本机新增的家人「" + lp.name + "」尚未进入确认版"
            )
        }

        // ---------- 2. 人物字段（双端都有的人） ----------
        for (lp in local.persons) {
            val rp = rMap[lp.id] ?: continue
            val bp = bMap[lp.id]
            field(items, pending, lp.id, lp.name, "name", "姓名",
                bp?.name, rp.name, lp.name, blocked = false)
            field(items, pending, lp.id, lp.name, "gender", "性别",
                bp?.gender?.let(::genderText), genderText(rp.gender),
                genderText(lp.gender), blocked = false)
            field(items, pending, lp.id, lp.name, "living", "在世状态",
                bp?.living?.let(::livingText), livingText(rp.living),
                livingText(lp.living),
                blocked = lp.id == ctx.selfCanonicalId &&
                    rp.living == "DECEASED" && lp.living != "DECEASED")
            field(items, pending, lp.id, lp.name, "note", "备注",
                bp?.note ?: "（无）", rp.note ?: "（无）", lp.note ?: "（无）",
                blocked = false)
            field(items, pending, lp.id, lp.name, "avatar", "照片",
                avatarText(bp?.avatarHash), avatarText(rp.avatarHash),
                avatarText(lp.avatarHash), blocked = false)
        }

        // ---------- 3. 电话条目 ----------
        for (lp in local.persons) {
            val rp = rMap[lp.id]
            val bp = bMap[lp.id]
            if (rp == null && bp != null) continue  // 远端整人移除，去留项覆盖
            val keys = LinkedHashSet<String>()
            bp?.phones?.forEach { keys += it.number }
            rp?.phones?.forEach { keys += it.number }
            lp.phones.forEach { keys += it.number }
            // 纯新增项先攒着：远端新增 / 本机新增（基准都没有的号码）
            val remoteAdds = ArrayList<PhoneItem>()
            val localAdds = ArrayList<PhoneItem>()
            var sharedRemoved = false
            for (num in keys) {
                val b = bp?.phones?.firstOrNull { it.number == num }
                val r = rp?.phones?.firstOrNull { it.number == num }
                val l = lp.phones.firstOrNull { it.number == num }
                val state = classifyVal(b, r, l)
                if (state == State.SAME) {
                    if (b != null && r == null && l == null)
                        sharedRemoved = true  // 双端一致删了同一个基准号码
                    continue
                }
                val item = PhoneItem(
                    key = "ph:" + lp.id + ":" + num, personId = lp.id,
                    personName = lp.name, number = num,
                    base = b, remote = r, local = l, state = state,
                    text = phoneText(lp.name, num, r, l, state)
                )
                when {
                    state == State.APPLY && b == null -> remoteAdds += item
                    state == State.PENDING && b == null && r == null ->
                        localAdds += item
                    state == State.PENDING -> pending += item
                    else -> items += item
                }
            }
            // 双端都删了同一基准号码、又各自记了不同的新号：换号分歧，
            // 两端新号全部转待确认逐项裁决，否则 B、C 会无提示并存
            if (sharedRemoved && remoteAdds.isNotEmpty() &&
                localAdds.isNotEmpty()) {
                for (it in remoteAdds) items += it.copy(
                    state = State.CONFLICT,
                    text = it.personName + " 的电话：双方都把原号码换成了" +
                        "新的，收到的是 " + phoneDesc(it.remote))
                for (it in localAdds) items += it.copy(
                    state = State.CONFLICT,
                    text = it.personName + " 的电话：对方也把原号码换成了" +
                        "新的，你的新号是 " + phoneDesc(it.local))
            } else {
                for (it in remoteAdds) items += it
                for (it in localAdds) pending += it
            }
        }

        // ---------- 4. 日期条目 ----------
        for (lp in local.persons) {
            val rp = rMap[lp.id]
            val bp = bMap[lp.id]
            if (rp == null && bp != null) continue
            val keys = LinkedHashSet<String>()
            bp?.dates?.forEach { keys += it.type }
            rp?.dates?.forEach { keys += it.type }
            lp.dates.forEach { keys += it.type }
            for (t in keys) {
                val b = bp?.dates?.firstOrNull { it.type == t }
                val r = rp?.dates?.firstOrNull { it.type == t }
                val l = lp.dates.firstOrNull { it.type == t }
                val state = classifyVal(b, r, l)
                if (state == State.SAME) continue
                val item = DateItem(
                    key = "dt:" + lp.id + ":" + t, personId = lp.id,
                    personName = lp.name, type = t,
                    base = b, remote = r, local = l, state = state,
                    text = dateText(lp.name, t, r, l, state)
                )
                if (state == State.PENDING) pending += item else items += item
            }
        }

        // ---------- 5. 关系 ----------
        val bRels = base?.rels?.toSet().orEmpty()
        val rRels = remapped.rels.toSet()
        val lRels = local.rels.toSet()
        val allRels = (bRels + rRels + lRels).sortedWith(
            compareBy({ it.type }, { it.a }, { it.b }))
        for (rel in allRels) {
            if (rel.a in undecidedNew || rel.b in undecidedNew) continue
            if (rel.a in remoteRemoved || rel.b in remoteRemoved) continue
            val inB = rel in bRels
            val inR = rel in rRels
            val inL = rel in lRels
            val state = classifyVal(
                b = if (inB) rel else null,
                r = if (inR) rel else null,
                l = if (inL) rel else null
            )
            if (state == State.SAME) continue
            // 远端要加入的关系做约束检查（基于「远端删除已应用」的本机图）
            var violation: String? = null
            var alsoRemoves: List<Rel> = emptyList()
            var st = state
            if (inR && !inL) {
                val removed = allRels.filter { it in bRels && it !in rRels }.toSet()
                val check = checkRel(local.rels, local.personMap, rel, removed)
                violation = check.first
                alsoRemoves = check.second
                if (violation != null && st == State.APPLY) {
                    // 自动项变成显式决策：采用=同时移除矛盾项；或 SKIP
                    st = State.CONFLICT
                }
            }
            val item = RelItem(
                key = relKey(rel), rel = rel, personId = rel.a,
                inBase = inB, inRemote = inR, inLocal = inL,
                violation = violation, alsoRemoves = alsoRemoves,
                state = st,
                text = relItemText(local, remapped, rel, st, inR, violation)
            )
            if (st == State.PENDING) pending += item else items += item
        }

        return Plan(
            items = items.sortedBy { it.key },
            pendingLocal = pending.sortedBy { it.key }
        )
    }

    // ---------- 决策指纹（审查 R5） ----------

    /**
     * 决策所依据资料的指纹：同一计划项按“基准值|远端值|本机值|身份候选”
     * 摘要。草稿保存的决策记录当时指纹——任一侧资料变了指纹即不符，
     * 旧决策不能套用到新值上，必须重新确认。
     */
    fun fingerprint(item: Item): String = FamilySnapshot.sha256Hex(
        itemContext(item).toByteArray(Charsets.UTF_8))

    private fun itemContext(item: Item): String = when (item) {
        is PersonAdd -> "add|" + FamilySnapshot.canonicalJson(
            Snap(listOf(item.person), emptyList())) +
            "|" + item.candidates.joinToString(",") {
                it.localId + ":" + it.name + ":" + it.sharedVia }
        is PersonRemove -> "rm|" + item.personId + "|" + item.name +
            "|" + item.blocked + "|" + item.privateRefs
        is FieldItem -> "f|" + item.personId + "|" + item.field + "|" +
            item.base + "|" + item.remote + "|" + item.local
        is PhoneItem -> "ph|" + item.personId + "|" + item.number + "|" +
            phoneStr(item.base) + "|" + phoneStr(item.remote) + "|" +
            phoneStr(item.local)
        is DateItem -> "dt|" + item.personId + "|" + item.type + "|" +
            dateStr(item.base) + "|" + dateStr(item.remote) + "|" +
            dateStr(item.local)
        is RelItem -> "rel|" + relStr(item.rel) + "|" + item.inBase +
            "|" + item.inRemote + "|" + item.inLocal + "|" +
            item.violation + "|" +
            item.alsoRemoves.joinToString(",") { relStr(it) }
    }

    private fun phoneStr(p: Phone?): String = p?.let {
        it.number + "|" + it.label + "|" + it.status + "|" +
            it.primary + "|" + it.note } ?: "∅"

    private fun dateStr(d: DateEntry?): String = d?.let {
        it.type + "|" + it.year + "|" + it.month + "|" + it.day + "|" +
            it.calendar + "|" + it.leap + "|" + it.note } ?: "∅"

    private fun relStr(r: Rel): String =
        r.type + "|" + r.a + "|" + r.b + "|" + r.order

    // ---------- 分类 ----------

    /**
     * 三值分类：null 表示该侧不存在。
     * 都有 → 内容比较（l==r 一致；r==b 本机改动待汇入；l==b 应用远端；否则分歧）。
     */
    private fun <T> classifyVal(b: T?, r: T?, l: T?): State = when {
        b == null && r == null && l != null -> State.PENDING
        b == null && r != null && l == null -> State.APPLY
        b == null && r != null && l != null ->
            if (l == r) State.SAME else State.CONFLICT
        // 远端删除：本机仍等于基准才可自动删，本机已改则是一方删
        // 一方改的分歧，必须确认（审查 R2）
        b != null && r == null && l != null ->
            if (l == b) State.APPLY else State.CONFLICT
        b != null && r == null && l == null -> State.SAME    // 双删一致
        // 本机删除：远端仍等于基准是待汇入改动，远端已改则是删/改分歧
        b != null && r != null && l == null ->
            if (r == b) State.PENDING else State.CONFLICT
        b != null && r != null && l != null -> when {
            l == r -> State.SAME
            r == b -> State.PENDING
            l == b -> State.APPLY
            else -> State.CONFLICT
        }
        else -> State.SAME
    }

    /** 字段三分类并生成差异项。 */
    private fun field(
        items: MutableList<Item>, pending: MutableList<Item>,
        personId: String, personName: String, field: String, label: String,
        b: String?, r: String, l: String, blocked: Boolean
    ) {
        // b==null 时该人无基准记录，按“远端新增/双改”处理
        val state = if (b == null) {
            if (l == r) State.SAME else State.CONFLICT
        } else classifyVal(b, r, l)
        if (state == State.SAME) return
        val st = if (blocked && state == State.APPLY || blocked && state == State.CONFLICT)
            State.BLOCKED else state
        val text = when (st) {
            State.APPLY -> personName + " 的" + label + "：" + l + " → " + r
            State.BLOCKED -> personName + " 的" + label + "：收到的版本要改成「" +
                r + "」，不能自动覆盖"
            State.PENDING -> "你改过" + personName + " 的" + label +
                "（" + l + "），还没进确认版"
            else -> personName + " 的" + label + "：你的「" + l +
                "」与收到的「" + r + "」不一致"
        }
        val item = FieldItem(
            key = "f:" + personId + ":" + field, personId = personId,
            personName = personName, field = field,
            base = b, remote = r, local = l,
            state = st, text = text,
            blocked = st == State.BLOCKED
        )
        if (st == State.PENDING) pending += item else items += item
    }

    // ---------- 身份候选 ----------

    /**
     * 远端新人物的本机候选：同性别且（同名 或 至少 1 个共同规范邻居）。
     * 只在“本机新增（不在基准）”的人里找。
     */
    private fun candidates(
        rp: Person, remote: Snap, local: Snap,
        bMap: Map<String, Person>
    ): List<Candidate> {
        val rNbrs = neighborsOf(remote, rp.id)
        return local.persons.mapNotNull { lp ->
            if (lp.id in bMap) return@mapNotNull null
            if (lp.gender != rp.gender) return@mapNotNull null
            val shared = neighborsOf(local, lp.id) intersect rNbrs
            when {
                lp.name == rp.name -> Candidate(
                    lp.id, lp.name,
                    if (shared.isEmpty()) "同名" else "同名且有共同亲属")
                shared.isNotEmpty() -> Candidate(
                    lp.id, lp.name, "有共同亲属")
                else -> null
            }
        }
    }

    /** 某人的邻居规范 id 集合（按规范化关系无向取端点）。 */
    private fun neighborsOf(snap: Snap, pid: String): Set<String> =
        snap.rels.mapNotNullTo(HashSet()) { r ->
            when (pid) {
                r.a -> r.b
                r.b -> r.a
                else -> null
            }
        }

    // ---------- 关系约束（在本机图上模拟） ----------

    /**
     * 在 [rels] 描述的图上加入 [rel] 的可行性：
     * 返回 (违例说明, 采用时需同时移除的关系列表)。
     */
    internal fun checkRel(
        rels: List<Rel>, personMap: Map<String, Person>, rel: Rel,
        removed: Set<Rel>
    ): Pair<String?, List<Rel>> {
        val cur = rels.filter { it !in removed }
        if (rel in cur) return null to emptyList()
        if (rel.a == rel.b) return "两端是同一个人" to emptyList()
        val conflicting = cur.firstOrNull { e -> e.pair == rel.pair }
        if (conflicting != null && conflicting.type != rel.type) {
            return "两人间已记为" + relKindText(conflicting.type) to
                listOf(conflicting)
        }
        when (rel.type) {
            RelationshipType.PARENT.name -> {
                val parents = cur.filter {
                    it.type == RelationshipType.PARENT.name && it.b == rel.b
                }
                if (parents.size >= 2 &&
                    parents.none { it == rel }) {
                    return "已经记了两位父母" to emptyList()
                }
                val pGender = personMap[rel.a]?.gender
                if (pGender != null && parents.any {
                        it != rel && personMap[it.a]?.gender == pGender
                    }) {
                    return "已经记了" +
                        (if (pGender == Gender.MALE.name) "父亲" else "母亲") +
                        "，不能再加同性别的父母" to emptyList()
                }
                if (rel.b in ancestors(cur, rel.a)) {
                    return "会形成辈分循环" to emptyList()
                }
            }
            RelationshipType.SPOUSE.name -> {
                cur.filter {
                    it.type == RelationshipType.SPOUSE.name &&
                        (it.a == rel.a || it.b == rel.a ||
                            it.a == rel.b || it.b == rel.b)
                }.forEach { sp ->
                    if (sp.pair != rel.pair || sp.type != rel.type) {
                        return "其中一方已有配偶" to listOf(sp)
                    }
                }
            }
        }
        fun spec(r: Rel) = io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec(
            r.a, r.b, RelationshipType.valueOf(r.type), null)
        if (io.github.xiaohaoz1.jiarenxia.core.model.GenerationRules.conflicts(cur.map(::spec), spec(rel))) {
            return "与已记录的亲子、夫妻或兄弟姐妹关系辈分冲突" to emptyList()
        }
        return null to emptyList()
    }

    private fun ancestors(rels: List<Rel>, pid: String): Set<String> {
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(pid)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            rels.filter {
                it.type == RelationshipType.PARENT.name && it.b == n
            }.forEach { if (seen.add(it.a)) queue.add(it.a) }
        }
        return seen
    }

    // ---------- 解析为写操作 ----------

    /** 规范空间的写操作（数据层再译为本机 id）。 */
    sealed interface Op {
        data class AddPerson(val person: Person) : Op
        data class RemovePerson(val personId: String) : Op
        /** 字段整组改写为目标值（只含共享字段）。 */
        data class SetPerson(
            val personId: String, val name: String, val gender: String,
            val living: String, val note: String?, val avatarHash: String?
        ) : Op
        data class PutPhone(val personId: String, val phone: Phone) : Op
        data class DelPhone(val personId: String, val number: String) : Op
        data class PutDate(val personId: String, val date: DateEntry) : Op
        data class DelDate(val personId: String, val type: String) : Op
        data class PutRel(val rel: Rel) : Op
        data class DelRel(val rel: Rel) : Op
    }

    class UnresolvedException(val missing: List<String>) : Exception(
        "还有 " + missing.size + " 项没有确认")

    /** 决议后的关系图自相矛盾（如采用的关系仍与本机冲突）。 */
    class InconsistentException(msg: String) : Exception(msg)

    /** 最终决议结果。 */
    data class ResolvedPlan(
        val ops: List<Op>,
        /** 身份 Same 裁决新增的 远端规范id → 本机规范id 映射。 */
        val identityAdds: Map<String, String>,
        /** 远端新增人物获得的新规范 id 列表（=远端 id 原样即规范 id）。 */
        val adoptedRemoteIds: Set<String>,
        /** 再现项被判“不重新加入”的远端规范 id（仍在 removedIds 记账）。 */
        val excludedRemoteIds: Set<String> = emptySet()
    )

    /**
     * 决议 → 写操作序列（规范空间）。
     * [decisions]：key → Choice.wire；身份/再现项：key → IdentChoice.wire 或
     * "keep"（不重新加入）。决策不完整抛 [UnresolvedException]。
     * [dropPending]：“放弃这些补充，以确认版为准”——本机独有改动
     * （pendingLocal 项）按收到的名册回写：本机新增人物/条目/关系移除，
     * 本机做过的删除与字段改动恢复为远端值。
     */
    fun resolve(
        base: Snap?, remote: Snap, local: Snap, ctx: Ctx,
        plan: Plan,
        decisions: Map<String, String>,
        dropPending: Boolean = false
    ): ResolvedPlan {
        if (!plan.ready(decisions)) {
            throw UnresolvedException(
                plan.decisions.filter { !decisions.containsKey(it.key) }
                    .map { it.key })
        }
        val remapped = FamilySnapshot.remap(remote,
            ctx.identities.mapNotNull { (rid, c) ->
                (c as? IdentChoice.Same)?.let { rid to it.localCanonicalId }
            }.toMap())
        val lMap = local.personMap
        val ops = ArrayList<Op>()
        val identityAdds = ctx.identities.mapNotNull { (rid, c) ->
            (c as? IdentChoice.Same)?.let { rid to it.localCanonicalId }
        }.toMap()
        val adopted = HashSet<String>()
        val excludedPersons = HashSet<String>()
        val removedPersons = HashSet<String>()

        // 采用一个远端人物：人物行 + 全部电话/日期（条目比较只覆盖
        // 双端都有的人，新采用人物的条目要靠这里带上）
        fun adoptPerson(p: Person) {
            ops += Op.AddPerson(p)
            adopted += p.id
            p.phones.forEach { ops += Op.PutPhone(p.id, it) }
            p.dates.forEach { ops += Op.PutDate(p.id, it) }
        }

        // 人物新增（含裁决）
        for (it in plan.items.filterIsInstance<PersonAdd>()) {
            when (it.state) {
                State.APPLY -> adoptPerson(it.person)
                State.IDENTITY -> when (IdentChoice.parse(decisions[it.key])) {
                    is IdentChoice.New -> adoptPerson(it.person)
                    else -> {}  // Same 已在 identities 重映射里
                }
                State.REAPPEAR -> when (Choice.of(decisions[it.key])) {
                    Choice.TAKE_REMOTE -> adoptPerson(it.person)
                    else -> excludedPersons += it.person.id
                }
                else -> {}
            }
        }
        // 人物移除
        for (it in plan.items.filterIsInstance<PersonRemove>()) {
            val d = Choice.of(decisions[it.key])
            when {
                it.state == State.BLOCKED || d == Choice.KEEP_LOCAL -> {}
                else -> {
                    ops += Op.RemovePerson(it.personId)
                    removedPersons += it.personId
                }
            }
        }

        if (dropPending) {
            // “放弃这些补充，以确认版为准”：本机独有改动按远端回写。
            // 本机新增人物 → 移除（self 永不移除；有私人引用由数据层
            // 转“仅本机保留”而非物理删除）
            for (it in plan.pendingLocal.filterIsInstance<PersonAdd>()) {
                if (it.person.id == ctx.selfCanonicalId) continue
                ops += Op.RemovePerson(it.person.id)
                removedPersons += it.person.id
            }
            // 本机曾移除、远端仍在的人 → 按收到的名册恢复
            for (it in plan.pendingLocal.filterIsInstance<PersonRemove>()) {
                remapped.personMap[it.personId]?.let { adoptPerson(it) }
            }
        }

        // 字段：按人聚合成一条 SetPerson（放弃补充时含本机改动项）
        val fieldByPerson = (plan.items +
            if (dropPending) plan.pendingLocal else emptyList())
            .filterIsInstance<FieldItem>()
            .groupBy { it.personId }
        for ((pid, fields) in fieldByPerson) {
            if (pid in removedPersons || pid in excludedPersons) continue
            val l = lMap[pid] ?: continue
            val r = remapped.personMap[pid] ?: continue
            var name = l.name; var gender = l.gender; var living = l.living
            var note = l.note; var avatar = l.avatarHash
            var touched = false
            for (f in fields) {
                if (f.blocked) continue
                val take = f.state == State.APPLY ||
                    (f.state == State.CONFLICT &&
                        decisions[f.key] == Choice.TAKE_REMOTE.wire) ||
                    (dropPending && f.state == State.PENDING)
                if (!take) continue
                touched = true
                when (f.field) {
                    "name" -> name = r.name
                    "gender" -> gender = r.gender
                    "living" -> living = r.living
                    "note" -> note = r.note
                    "avatar" -> avatar = r.avatarHash
                }
            }
            if (touched) ops += Op.SetPerson(pid, name, gender, living, note, avatar)
        }

        // 电话 / 日期
        for (it in (plan.items +
            if (dropPending) plan.pendingLocal else emptyList())
            .filterIsInstance<PhoneItem>()) {
            if (it.personId in removedPersons || it.personId in excludedPersons) continue
            val take = it.state == State.APPLY ||
                (it.state == State.CONFLICT &&
                    decisions[it.key] == Choice.TAKE_REMOTE.wire) ||
                (dropPending && it.state == State.PENDING)
            if (!take) continue
            if (it.remote != null) ops += Op.PutPhone(it.personId, it.remote)
            else ops += Op.DelPhone(it.personId, it.number)
        }
        for (it in (plan.items +
            if (dropPending) plan.pendingLocal else emptyList())
            .filterIsInstance<DateItem>()) {
            if (it.personId in removedPersons || it.personId in excludedPersons) continue
            val take = it.state == State.APPLY ||
                (it.state == State.CONFLICT &&
                    decisions[it.key] == Choice.TAKE_REMOTE.wire) ||
                (dropPending && it.state == State.PENDING)
            if (!take) continue
            if (it.remote != null) ops += Op.PutDate(it.personId, it.remote)
            else ops += Op.DelDate(it.personId, it.type)
        }

        // 关系：先删后加
        val delRels = ArrayList<Rel>()
        val putRels = ArrayList<Rel>()
        for (it in (plan.items +
            if (dropPending) plan.pendingLocal else emptyList())
            .filterIsInstance<RelItem>()) {
            if (it.rel.a in removedPersons || it.rel.b in removedPersons) continue
            if (it.rel.a in excludedPersons || it.rel.b in excludedPersons) continue
            val d = Choice.of(decisions[it.key])
            val take = it.state == State.APPLY || d == Choice.TAKE_REMOTE ||
                (dropPending && it.state == State.PENDING)
            when {
                d == Choice.SKIP -> {}
                take && it.inRemote && !it.inLocal -> {
                    it.alsoRemoves.forEach { r -> delRels += r }
                    putRels += it.rel
                }
                take && !it.inRemote && it.inLocal -> delRels += it.rel
            }
        }
        // 新增人物附带的关系（两端都在远端的人之间的边，计划里已按 APPLY 列出）
        delRels.forEach { ops += Op.DelRel(it) }
        // 采用前做最终一致性校验：在本机图上按序删除+加入
        val cur = local.rels.filter { it !in delRels.toSet() }.toMutableList()
        for (r in putRels) {
            val (vio, also) = checkRel(cur, local.personMap, r, emptySet())
            if (vio != null && also.isEmpty()) {
                throw InconsistentException(
                    "关系「" + relText(local, remapped, r) + "」与本机冲突：" + vio)
            }
            cur.removeAll(also)
            if (r !in cur) cur += r
        }
        putRels.forEach { ops += Op.PutRel(it) }

        return ResolvedPlan(
            ops = ops,
            identityAdds = identityAdds,
            adoptedRemoteIds = adopted,
            excludedRemoteIds = excludedPersons
        )
    }

    // ---------- 展示辅助 ----------

    private fun relKey(rel: Rel) =
        "rel:" + rel.type + ":" + rel.a + ":" + rel.b

    private fun genderText(g: String) =
        if (g == Gender.MALE.name) "男" else "女"

    private fun livingText(s: String) =
        if (s == "DECEASED") "已故" else "在世"

    /** 照片展示值：区分 无/有(哈希变化算“换了照片”)。 */
    private fun avatarText(h: String?) =
        if (h == null) "无照片" else "照片·" + h.take(6)

    private fun relKindText(t: String) = when (t) {
        RelationshipType.PARENT.name -> "父母"
        RelationshipType.SPOUSE.name -> "夫妻"
        RelationshipType.SIBLING.name -> "兄弟姐妹"
        RelationshipType.COUSIN.name -> "堂表亲"
        else -> "其他关系"
    }

    private fun relText(local: Snap, remote: Snap, rel: Rel): String {
        val nameOf = { id: String ->
            remote.personMap[id]?.name ?: local.personMap[id]?.name ?: id }
        return when (rel.type) {
            RelationshipType.PARENT.name ->
                nameOf(rel.a) + " 是 " + nameOf(rel.b) + " 的父母"
            RelationshipType.SPOUSE.name ->
                nameOf(rel.a) + " 和 " + nameOf(rel.b) + " 是夫妻"
            RelationshipType.SIBLING.name ->
                nameOf(rel.a) + " 和 " + nameOf(rel.b) + " 是兄弟姐妹"
            RelationshipType.COUSIN.name ->
                nameOf(rel.a) + " 和 " + nameOf(rel.b) + " 是堂表亲"
            else -> nameOf(rel.a) + " 与 " + nameOf(rel.b)
        }
    }

    private fun relItemText(
        local: Snap, remote: Snap, rel: Rel, state: State,
        inRemote: Boolean, violation: String?
    ): String {
        val t = relText(local, remote, rel)
        return when {
            violation != null && state == State.CONFLICT ->
                "加入「" + t + "」与本机已有关系矛盾：" + violation
            state == State.PENDING -> "本机新增的关系「" + t + "」还没进确认版"
            !inRemote -> "收到的版本移除了关系「" + t + "」"
            else -> "新增关系「" + t + "」"
        }
    }

    private fun phoneDesc(p: Phone?): String = p?.let {
        it.number + when {
            it.status == "INACTIVE" -> "（已停用）"
            it.primary -> "（主号码）"
            else -> ""
        }
    } ?: "（无）"

    private fun phoneText(
        name: String, number: String, r: Phone?, l: Phone?, state: State
    ): String {
        fun desc(p: Phone?): String = phoneDesc(p)
        return when {
            state == State.PENDING -> "你改了 " + name + " 的电话 " + number +
                "，还没进确认版"
            r != null && l == null -> "新增 " + name + " 的电话 " + desc(r)
            r == null && l != null -> "移除 " + name + " 的电话 " + desc(l)
            else -> name + " 的电话 " + number + "：" + desc(l) +
                " → " + desc(r)
        }
    }

    private fun dateText(
        name: String, type: String, r: DateEntry?, l: DateEntry?, state: State
    ): String {
        val label = dateLabel(type)
        fun desc(d: DateEntry?): String = d?.let {
            (if (it.calendar == "LUNAR") "农历" else "") +
                (it.year?.let { y -> "${y}年" } ?: "") +
                (if (it.leap) "闰" else "") + it.month + "月" + it.day + "日"
        } ?: "（无）"
        return when {
            state == State.PENDING -> "你改了 " + name + " 的" + label +
                "，还没进确认版"
            r != null && l == null -> "新增 " + name + " 的" + label + " " + desc(r)
            r == null && l != null -> "移除 " + name + " 的" + label + " " + desc(l)
            else -> name + " 的" + label + "：" + desc(l) + " → " + desc(r)
        }
    }

    private fun dateLabel(t: String) = when (t) {
        ImportantDateType.BIRTHDAY_SOLAR.name -> "阳历生日"
        ImportantDateType.BIRTHDAY_LUNAR.name -> "农历生日"
        ImportantDateType.DEATH_ANNIVERSARY.name -> "忌日"
        ImportantDateType.WEDDING_ANNIVERSARY.name -> "结婚纪念日"
        ImportantDateType.LICENSE_ANNIVERSARY.name -> "领证纪念日"
        ImportantDateType.DATING_ANNIVERSARY.name -> "恋爱纪念日"
        else -> t
    }
}
