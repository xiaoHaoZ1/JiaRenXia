package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 碰一碰合并逻辑（FAMILY_AFFAIRS 第 10 节）。 */
class SpouseMergeTest {

    private fun p(id: String, name: String, g: Gender, self: Boolean = false) =
        PersonEntity(
            id = id, name = name, gender = g, avatarPath = null,
            note = null, livingStatus = LivingStatus.ALIVE,
            isSelf = self, createdAt = 0, updatedAt = 0)

    private fun r(
        src: String, tgt: String, t: RelationshipType,
        id: String = "r-$src-$tgt-${t.name}"
    ) = RelationshipEntity(
        id = id, sourcePersonId = src, targetPersonId = tgt,
        type = t, relativeAgeOrder = null, createdAt = 0, updatedAt = 0)

    private fun snap(
        persons: List<PersonEntity>,
        rels: List<RelationshipEntity> = emptyList(),
        dates: List<io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity> =
            emptyList()
    ) = SpouseMerge.Snapshot(persons, emptyList(), rels, dates)

    // ---------- 前置条件 ----------

    @Test
    fun 同性别拒绝() {
        val a = snap(listOf(p("a", "杨明", Gender.MALE, true)))
        val b = snap(listOf(p("b", "李强", Gender.MALE, true)))
        val r = SpouseMerge.plan(a, b, "a", "b")
        assertTrue(r is SpouseMerge.Result.Reject)
        assertEquals("结为夫妻需要一男一女",
            (r as SpouseMerge.Result.Reject).reason)
    }

    @Test
    fun 已有其他配偶拒绝() {
        val a = snap(
            listOf(p("a", "杨明", Gender.MALE, true), p("w", "张芳", Gender.FEMALE)),
            listOf(r("a", "w", RelationshipType.SPOUSE)))
        val b = snap(listOf(p("b", "李雪", Gender.FEMALE, true)))
        val r = SpouseMerge.plan(a, b, "a", "b")
        assertEquals("你的家人里已经有配偶了",
            (r as SpouseMerge.Result.Reject).reason)
    }

    @Test
    fun 已有配偶是对方本人_允许() {
        val a = snap(
            listOf(p("a", "杨明", Gender.MALE, true), p("w", "李雪", Gender.FEMALE)),
            listOf(r("a", "w", RelationshipType.SPOUSE)))
        val b = snap(listOf(p("b", "李雪", Gender.FEMALE, true)))
        // 同名同性别配偶只是候选：未经确认直接拒绝
        val rejected = SpouseMerge.plan(a, b, "a", "b")
        assertTrue(rejected is SpouseMerge.Result.Reject)
        // 用户确认“对方就是我录入的配偶”（approved 含对方“我”）才复用
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b")) as SpouseMerge.Result.Ok
        // 对方“我”映射到本机配偶记录，不产生新人也不重复 SPOUSE
        assertEquals("w", r.plan.idMap["b"])
        assertTrue(r.plan.newPersons.isEmpty())
        assertTrue(r.plan.newRelationships.isEmpty())
    }

    // ---------- 一层映射 ----------

    @Test
    fun 已录入配偶父母_一层映射() {
        // 本机：我 + 配偶李雪 + 岳父李国强
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("w", "李雪", Gender.FEMALE),
                p("wf", "李国强", Gender.MALE)),
            listOf(
                r("a", "w", RelationshipType.SPOUSE),
                r("wf", "w", RelationshipType.PARENT)))
        // 对方：我李雪 + 父亲李国强
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("bf", "李国强", Gender.MALE)),
            listOf(r("bf", "b", RelationshipType.PARENT)))
        // 确认对方是配偶后，岳父仍只是候选：未确认前不落映射
        val first = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b")) as SpouseMerge.Result.Ok
        assertEquals("w", first.plan.idMap["b"])
        assertTrue("bf" !in first.plan.idMap)
        assertEquals(
            listOf("bf"),
            first.plan.candidates.map { it.remoteId })
        // 候选被用户勾选确认后才复用本机记录
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b", "bf")) as SpouseMerge.Result.Ok
        assertEquals("w", r.plan.idMap["b"])
        assertEquals("wf", r.plan.idMap["bf"])
        assertTrue(r.plan.newPersons.isEmpty())
        assertTrue(r.plan.newRelationships.isEmpty())
        assertEquals(listOf("李雪", "李国强"), r.plan.mergedNames)
    }

    @Test
    fun 候选未确认则保持独立人物() {
        // 同上结构，但用户没有勾选岳父候选：李国强作为新人物并入，
        // 本机原“李国强”记录原样保留（不自动合并、不删除）
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("w", "李雪", Gender.FEMALE),
                p("wf", "李国强", Gender.MALE)),
            listOf(
                r("a", "w", RelationshipType.SPOUSE),
                r("wf", "w", RelationshipType.PARENT)))
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("bf", "李国强", Gender.MALE)),
            listOf(r("bf", "b", RelationshipType.PARENT)))
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b")) as SpouseMerge.Result.Ok
        assertTrue("bf" !in r.plan.idMap)
        val newDad = r.plan.newPersons.single { it.name == "李国强" }
        assertTrue(newDad.id != "wf")
        assertEquals(1, r.plan.candidates.size)
        assertTrue(!r.plan.candidates[0].blocking)
    }

    @Test
    fun 未确认候选_额外父母关系不写入且记冲突() {
        // 同上结构，但不勾选岳父候选：新“李国强”→ 李雪的父子关系会让
        // 李雪多一位父亲（16.6 同性别父母最多一位）——不写入，记入冲突
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("w", "李雪", Gender.FEMALE),
                p("wf", "李国强", Gender.MALE)),
            listOf(
                r("a", "w", RelationshipType.SPOUSE),
                r("wf", "w", RelationshipType.PARENT)))
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("bf", "李国强", Gender.MALE)),
            listOf(r("bf", "b", RelationshipType.PARENT)))
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b")) as SpouseMerge.Result.Ok
        // 新“李国强”→w 的父子关系不写入（w 已有父亲 wf）
        assertTrue(r.plan.newRelationships.none {
            it.type == RelationshipType.PARENT &&
                it.targetPersonId == "w" })
        val c = r.plan.conflicts.single { it.field == "relation" }
        assertEquals("李雪已经记了父亲", c.localValue)
    }

    // ---------- 全新家人 ----------

    @Test
    fun 两家完全不相交_全部新增() {
        val a = snap(listOf(p("a", "杨明", Gender.MALE, true)))
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("bf", "李国强", Gender.MALE),
                p("bm", "陈美华", Gender.FEMALE)),
            listOf(
                r("bf", "b", RelationshipType.PARENT),
                r("bm", "b", RelationshipType.PARENT),
                r("bf", "bm", RelationshipType.SPOUSE)))
        val r = SpouseMerge.plan(a, b, "a", "b") as SpouseMerge.Result.Ok
        // 3 个新人物（is_self 全为 false）+ 2 条父母边 + 1 条对方父母配偶 + 1 条新 SPOUSE
        assertEquals(3, r.plan.newPersons.size)
        assertTrue(r.plan.newPersons.all { !it.isSelf })
        assertEquals(4, r.plan.newRelationships.size)
        assertTrue(r.plan.newRelationships.any {
            it.type == RelationshipType.SPOUSE &&
                it.sourcePersonId == "a" && it.targetPersonId == "b"
        })
    }

    // ---------- id 冲突 ----------

    @Test
    fun id冲突时重新生成并改写引用() {
        val a = snap(listOf(p("a", "杨明", Gender.MALE, true),
            p("p-x", "本地人", Gender.MALE)))
        val b = snap(
            listOf(p("b", "李雪", Gender.FEMALE, true),
                p("p-x", "李国强", Gender.MALE)),
            listOf(r("p-x", "b", RelationshipType.PARENT)))
        val r = SpouseMerge.plan(a, b, "a", "b") as SpouseMerge.Result.Ok
        val newDad = r.plan.newPersons.first { it.name == "李国强" }
        assertTrue(newDad.id != "p-x")
        val rel = r.plan.newRelationships.first {
            it.type == RelationshipType.PARENT
        }
        assertEquals(newDad.id, rel.sourcePersonId)
        assertEquals("b", rel.targetPersonId)
    }

    // ---------- 重复关系跳过 ----------

    @Test
    fun 重复关系跳过() {
        // 本机：我 + 配偶李雪 + 孩子杨小帆（李雪 PARENT 小帆）
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("w", "李雪", Gender.FEMALE),
                p("k", "杨小帆", Gender.MALE)),
            listOf(
                r("a", "w", RelationshipType.SPOUSE),
                r("w", "k", RelationshipType.PARENT)))
        // 对方：我李雪 + 孩子杨小帆；边 b→k 重写后与本机重复
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("k2", "杨小帆", Gender.MALE)),
            listOf(r("b", "k2", RelationshipType.PARENT)))
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b", "k2")) as SpouseMerge.Result.Ok
        // 孩子一层映射；重复 PARENT 边跳过；SPOUSE 不重复
        assertEquals("w", r.plan.idMap["b"])
        assertEquals("k", r.plan.idMap["k2"])
        assertTrue(r.plan.newRelationships.isEmpty())
        assertTrue(r.plan.newPersons.isEmpty())
    }

    // ---------- 对称映射：对方已录入“我” ----------

    @Test
    fun 对方已录入我_不产生第二个我() {
        // 本机：我杨明（男）+ 父母
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("af", "杨建国", Gender.MALE),
                p("am", "王秀兰", Gender.FEMALE)),
            listOf(
                r("af", "a", RelationshipType.PARENT),
                r("am", "a", RelationshipType.PARENT)))
        // 对方：我李雪 + 她录入的丈夫“杨明”（含杨明的父母）
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("b-hub", "杨明", Gender.MALE),
                p("b-af", "杨建国", Gender.MALE),
                p("b-am", "王秀兰", Gender.FEMALE)),
            listOf(
                r("b-hub", "b", RelationshipType.SPOUSE),
                r("b-af", "b-hub", RelationshipType.PARENT),
                r("b-am", "b-hub", RelationshipType.PARENT)))
        // 对方录入的“杨明”是阻塞候选：不确认就会产生假的“我”；
        // 用户确认其与祖父母候选后才落映射
        val blocked = SpouseMerge.plan(a, b, "a", "b")
            as SpouseMerge.Result.Ok
        assertTrue(blocked.plan.candidates.any {
            it.remoteId == "b-hub" && it.blocking })
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b-hub", "b-af", "b-am"))
                as SpouseMerge.Result.Ok
        // 对方的杨明映射为本机的我；其父母映射为本机父母；不产生新人物
        assertEquals("a", r.plan.idMap["b-hub"])
        assertEquals("af", r.plan.idMap["b-af"])
        assertEquals("am", r.plan.idMap["b-am"])
        assertTrue(r.plan.newPersons.none { it.name == "杨明" })
        // 只有一条 SPOUSE（a ↔ b）
        assertEquals(1, r.plan.newRelationships.count {
            it.type == RelationshipType.SPOUSE
        })
    }

    @Test
    fun 双方互相录过_不重复() {
        val a = snap(
            listOf(p("a", "杨明", Gender.MALE, true), p("w", "李雪", Gender.FEMALE)),
            listOf(r("a", "w", RelationshipType.SPOUSE)))
        val b = snap(
            listOf(p("b", "李雪", Gender.FEMALE, true), p("b-hub", "杨明",
                Gender.MALE)),
            listOf(r("b-hub", "b", RelationshipType.SPOUSE)))
        // 本机配偶确认（approved 含对方“我”）即双方互认：
        // 对方录入的“我”随之自动并入
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b")) as SpouseMerge.Result.Ok
        assertEquals("w", r.plan.idMap["b"])
        assertEquals("a", r.plan.idMap["b-hub"])
        assertTrue(r.plan.newPersons.isEmpty())
        assertTrue(r.plan.newRelationships.isEmpty())
    }

    // ---------- 对称关系规范化与无序去重 ----------

    @Test
    fun 反向重复关系跳过且长幼翻转() {
        // 本机：我 + 配偶李雪 + 小帆（李雪的弟弟，YOUNGER，k<w 存 k→w）
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("w", "李雪", Gender.FEMALE),
                p("k", "杨小帆", Gender.MALE)),
            listOf(
                r("a", "w", RelationshipType.SPOUSE),
                // 规范化：k < w，relativeAgeOrder 表示 source 对 target 的长幼
                r("k", "w", RelationshipType.SIBLING,
                    id = "r-sib").copy(
                    relativeAgeOrder = io.github.xiaohaoz1.jiarenxia.core.model
                        .RelativeAgeOrder.YOUNGER)))
        // 对方同一条兄弟边但端点相反（w→k2, YOUNGER 视角相反端点）
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("k2", "杨小帆", Gender.MALE)),
            listOf(
                r("b", "k2", RelationshipType.SIBLING).copy(
                    relativeAgeOrder = io.github.xiaohaoz1.jiarenxia.core.model
                        .RelativeAgeOrder.OLDER)))
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b", "k2")) as SpouseMerge.Result.Ok
        assertEquals("k", r.plan.idMap["k2"])
        // 反向重复被跳过
        assertTrue(r.plan.newRelationships.none {
            it.type == RelationshipType.SIBLING
        })
    }

    @Test
    fun 反向重复堂表亲跳过且长幼翻转() {
        // 本机：我 + 配偶李雪 + 小帆（李雪的堂弟，k<w 存 k→w，COUSIN YOUNGER）
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("w", "李雪", Gender.FEMALE),
                p("k", "杨小帆", Gender.MALE)),
            listOf(
                r("a", "w", RelationshipType.SPOUSE),
                r("k", "w", RelationshipType.COUSIN,
                    id = "r-cousin").copy(
                    relativeAgeOrder = io.github.xiaohaoz1.jiarenxia.core.model
                        .RelativeAgeOrder.YOUNGER)))
        // 对方同一条堂表亲记录但端点相反（w→k2，OLDER 等价于 k 年幼）
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("k2", "杨小帆", Gender.MALE)),
            listOf(
                r("b", "k2", RelationshipType.COUSIN).copy(
                    relativeAgeOrder = io.github.xiaohaoz1.jiarenxia.core.model
                        .RelativeAgeOrder.OLDER)))
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b", "k2")) as SpouseMerge.Result.Ok
        assertEquals("k", r.plan.idMap["k2"])
        // 反向重复的申报堂表亲被跳过
        assertTrue(r.plan.newRelationships.none {
            it.type == RelationshipType.COUSIN
        })
    }

    @Test
    fun 申报堂表亲写入时端点规范化并翻转长幼() {
        // 对方带来一条 COUSIN，映射后两端字典序反序 → 规范化翻转长幼
        val a = snap(listOf(p("a", "杨明", Gender.MALE, true)))
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("y", "远亲", Gender.MALE),
                p("z", "近亲", Gender.MALE)),
            listOf(
                // z 比 y 年长；规范化后 y→z 应为 YOUNGER
                r("z", "y", RelationshipType.COUSIN).copy(
                    relativeAgeOrder = io.github.xiaohaoz1.jiarenxia.core.model
                        .RelativeAgeOrder.OLDER)))
        val r = SpouseMerge.plan(a, b, "a", "b") as SpouseMerge.Result.Ok
        val cousin = r.plan.newRelationships.single {
            it.type == RelationshipType.COUSIN
        }
        // 新人物 id 由合并侧分配，只需校验 source<target 且长幼已按新端点翻转
        assertTrue(cousin.sourcePersonId < cousin.targetPersonId)
        val src = r.plan.newPersons.first { it.id == cousin.sourcePersonId }
        val tgt = r.plan.newPersons.first { it.id == cousin.targetPersonId }
        // 源记录 z（年长）→ y；若新 id 仍 z>y 则翻转为 YOUNGER（y 比 z 年幼）
        val expect = if (src.name == "近亲") {
            io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder.OLDER
        } else {
            io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder.YOUNGER
        }
        assertEquals(expect, cousin.relativeAgeOrder)
    }

    @Test
    fun 新SPOUSE按规范顺序写入() {
        // 本机 id 字典序大于对方 id → 规范化后 source<target
        val a = snap(listOf(p("z-me", "杨明", Gender.MALE, true)))
        val b = snap(listOf(p("a-peer", "李雪", Gender.FEMALE, true)))
        val r = SpouseMerge.plan(a, b, "z-me", "a-peer") as SpouseMerge.Result.Ok
        val sp = r.plan.newRelationships.single {
            it.type == RelationshipType.SPOUSE
        }
        assertTrue(sp.sourcePersonId < sp.targetPersonId)
    }

    // ---------- 日期按 (personId,type) 去重 ----------

    @Test
    fun 映射人物的同类型日期不重复() {
        val a = snap(
            listOf(p("a", "杨明", Gender.MALE, true), p("w", "李雪",
                Gender.FEMALE)),
            listOf(r("a", "w", RelationshipType.SPOUSE)))
        // 本机“李雪”已有生日
        val a2 = a.copy(dates = listOf(
            io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity(
                id = "d1", personId = "w",
                type = io.github.xiaohaoz1.jiarenxia.core.model
                    .ImportantDateType.BIRTHDAY_SOLAR,
                year = 1995, month = 3, day = 3,
                calendarType = io.github.xiaohaoz1.jiarenxia.core.model
                    .CalendarType.SOLAR,
                note = null, createdAt = 0, updatedAt = 0)))
        // 对方李雪也有生日 + 一条纪念日（本机没有 → 补齐）
        val b = snap(
            listOf(p("b", "李雪", Gender.FEMALE, true)),
            emptyList())
        val b2 = b.copy(dates = listOf(
            io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity(
                id = "d2", personId = "b",
                type = io.github.xiaohaoz1.jiarenxia.core.model
                    .ImportantDateType.BIRTHDAY_SOLAR,
                year = 1995, month = 3, day = 3,
                calendarType = io.github.xiaohaoz1.jiarenxia.core.model
                    .CalendarType.SOLAR,
                note = null, createdAt = 0, updatedAt = 0)))
        val r = SpouseMerge.plan(a2, b2, "a", "b",
            approved = setOf("b")) as SpouseMerge.Result.Ok
        // 同类型（BIRTHDAY）已有 → 不重复插入
        assertTrue(r.plan.newDates.isEmpty())
    }

    // ---------- 纪念日/忌日并入（V0.8） ----------

    @Test
    fun 带纪念日和忌日的合并() {
        fun d(id: String, pid: String, t: io.github.xiaohaoz1.jiarenxia.core
            .model.ImportantDateType) =
            io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity(
                id = id, personId = pid, type = t,
                year = 1984, month = 10, day = 18,
                calendarType = io.github.xiaohaoz1.jiarenxia.core.model
                    .CalendarType.SOLAR,
                isLeapMonth = false, note = null,
                createdAt = 0, updatedAt = 0)
        // 本机：我 + 配偶 + 爷爷（已故，有忌日）
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("w", "李雪", Gender.FEMALE),
                p("gp", "杨福生", Gender.MALE)),
            listOf(r("a", "w", RelationshipType.SPOUSE)),
            dates = listOf(d("ad", "gp", io.github.xiaohaoz1.jiarenxia.core.model
                .ImportantDateType.DEATH_ANNIVERSARY)))
        // 对方：她 + 她的忌日 + 结婚纪念日（挂她名下）
        val b = snap(
            listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("b-mom", "陈美华", Gender.FEMALE)),
            listOf(r("b-mom", "b", RelationshipType.PARENT)),
            dates = listOf(
                d("bd1", "b", io.github.xiaohaoz1.jiarenxia.core.model
                    .ImportantDateType.WEDDING_ANNIVERSARY),
                d("bd2", "b-mom", io.github.xiaohaoz1.jiarenxia.core.model
                    .ImportantDateType.DEATH_ANNIVERSARY)))
        val res = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b")) as SpouseMerge.Result.Ok
        // 李雪被映射 → 她名下的纪念日类型本机没有 → 并入；岳母是新人物忌日也并入
        assertEquals(
            setOf(
                io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
                    .WEDDING_ANNIVERSARY,
                io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
                    .DEATH_ANNIVERSARY),
            res.plan.newDates.map { it.type }.toSet())
        // 本机爷爷的忌日不受影响
        assertTrue(res.plan.newDates.all {
            it.personId != "gp" ||
                it.type != io.github.xiaohaoz1.jiarenxia.core.model
                    .ImportantDateType.DEATH_ANNIVERSARY
        })
    }

    // ---------- 来源回环（oid）：合并后再更新不产生重复人物 ----------

    @Test
    fun oid回环自动认回不产候选() {
        // 本机：我杨明 + 父亲杨建国 + 母亲王秀兰
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("af", "杨建国", Gender.MALE),
                p("am", "王秀兰", Gender.FEMALE)),
            listOf(
                r("af", "a", RelationshipType.PARENT),
                r("am", "a", RelationshipType.PARENT)))
        // 对方库里：她 + 上次从我这里合并过去的“我”与父母副本
        // （对方本地 id 不同，但 oid 指回本机 a/af/am）
        val b = SpouseMerge.Snapshot(
            persons = listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("m", "杨明", Gender.MALE),
                p("x1", "杨建国", Gender.MALE),
                p("x2", "王秀兰", Gender.FEMALE)),
            phones = emptyList(),
            relationships = listOf(
                r("b", "m", RelationshipType.SPOUSE),
                r("x1", "m", RelationshipType.PARENT),
                r("x2", "m", RelationshipType.PARENT)),
            dates = emptyList(),
            origins = mapOf("m" to "a", "x1" to "af", "x2" to "am"))
        val r = SpouseMerge.plan(a, b, "a", "b") as SpouseMerge.Result.Ok
        // 回环人物直接并入映射，不产候选、不建新人
        assertEquals("a", r.plan.idMap["m"])
        assertEquals("af", r.plan.idMap["x1"])
        assertEquals("am", r.plan.idMap["x2"])
        assertTrue(r.plan.candidates.isEmpty())
        assertTrue(r.plan.newPersons.none { it.id in setOf("a", "af", "am") })
    }

    @Test
    fun oid姓名性别不符则降为候选() {
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("af", "杨建国", Gender.MALE)),
            listOf(r("af", "a", RelationshipType.PARENT)))
        // 对方人物 oid 指回本机 af，但名字改了——可信标记失效，降为候选
        val b = SpouseMerge.Snapshot(
            persons = listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("x1", "杨建华", Gender.MALE)),
            phones = emptyList(),
            relationships = emptyList(),
            dates = emptyList(),
            origins = mapOf("x1" to "af"))
        val r = SpouseMerge.plan(a, b, "a", "b") as SpouseMerge.Result.Ok
        val c = r.plan.candidates.single { it.remoteId == "x1" }
        assertEquals("af", c.localId)
        assertTrue("x1" !in r.plan.idMap)
        // 勾选批准后才并入
        val ok = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("x1")) as SpouseMerge.Result.Ok
        assertEquals("af", ok.plan.idMap["x1"])
    }

    @Test
    fun oid指向本机我_对方录入的我直接认回() {
        // 本机：我杨明（已婚过一轮：对方机器上有我录入的“李雪”副本带 oid）
        val a = snap(
            listOf(
                p("a", "杨明", Gender.MALE, true),
                p("w", "李雪", Gender.FEMALE)),
            listOf(r("a", "w", RelationshipType.SPOUSE)))
        // 对方快照：她 + 她录入的“我”副本（oid 指回本机 a——证明该记录
        // 本来就来自本机），跳过阻塞候选直接并入
        val b = SpouseMerge.Snapshot(
            persons = listOf(
                p("b", "李雪", Gender.FEMALE, true),
                p("m", "杨明", Gender.MALE)),
            phones = emptyList(),
            relationships = listOf(
                r("b", "m", RelationshipType.SPOUSE)),
            dates = emptyList(),
            origins = mapOf("m" to "a"))
        val r = SpouseMerge.plan(a, b, "a", "b",
            approved = setOf("b")) as SpouseMerge.Result.Ok
        // “对方录入的我”凭 oid 直接并入本机我，不产生阻塞候选也不造第二个我
        assertEquals("a", r.plan.idMap["m"])
        assertTrue(r.plan.candidates.none { it.localId == "a" })
        assertTrue(r.plan.newPersons.none { it.name == "杨明" })
    }
}
