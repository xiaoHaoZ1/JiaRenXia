package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderOffsetEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderPreferenceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 通用家庭合并引擎（FAMILY_AFFAIRS 16.5/16.6，FamilyMerge）。 */
class FamilyMergeTest {

    private var seq = 0
    private fun nid() = "new-" + (++seq)

    private fun req(
        local: FamilyMerge.Side, remote: FamilyMerge.Side,
        self: String = "me",
        anchors: Map<String, String> = emptyMap(),
        knownMap: Map<String, String> = emptyMap(),
        approved: Set<String> = emptySet(),
        takeRemote: Set<FamilyMerge.ConflictKey> = emptySet(),
        bridge: FamilyMerge.Bridge? = null,
        remoteAnchor: FamilyMerge.RemoteAnchor? = null
    ) = FamilyMerge.Request(
        local = local, remote = remote, localSelfId = self,
        anchors = anchors, knownMap = knownMap, approved = approved,
        takeRemote = takeRemote, bridge = bridge, remoteAnchor = remoteAnchor,
        now = 1000L, newId = { nid() })

    private fun p(
        id: String, name: String, g: Gender, self: Boolean = false,
        alive: LivingStatus = LivingStatus.ALIVE, avatar: String? = null
    ) = PersonEntity(
        id = id, name = name, gender = g, avatarPath = avatar,
        note = null, livingStatus = alive,
        isSelf = self, createdAt = 0, updatedAt = 0)

    private fun r(
        src: String, tgt: String, t: RelationshipType,
        id: String = "r-$src-$tgt-${t.name}"
    ) = RelationshipEntity(
        id = id, sourcePersonId = src, targetPersonId = tgt,
        type = t, relativeAgeOrder = null, createdAt = 0, updatedAt = 0)

    private fun ph(
        id: String, pid: String, num: String, primary: Boolean = true
    ) = PhoneNumberEntity(
        id = id, personId = pid, number = num, label = PhoneLabel.MOBILE,
        status = PhoneStatus.ACTIVE, isPrimary = primary,
        note = null, createdAt = 0, updatedAt = 0)

    private fun d(
        id: String, pid: String, t: ImportantDateType,
        y: Int? = null, m: Int = 5, dd: Int = 12,
        cal: CalendarType = CalendarType.SOLAR, leap: Boolean = false
    ) = ImportantDateEntity(
        id = id, personId = pid, type = t, year = y, month = m, day = dd,
        calendarType = cal, isLeapMonth = leap,
        note = null, createdAt = 0, updatedAt = 0)

    private fun pref(id: String, dateId: String, enabled: Boolean = true) =
        ReminderPreferenceEntity(
            id = id, importantDateId = dateId, enabled = enabled,
            createdAt = 0, updatedAt = 0)

    private fun off(id: String, prefId: String, days: Int) =
        ReminderOffsetEntity(
            id = id, reminderPreferenceId = prefId, daysBefore = days)

    private fun side(
        persons: List<PersonEntity>,
        phones: List<PhoneNumberEntity> = emptyList(),
        rels: List<RelationshipEntity> = emptyList(),
        dates: List<ImportantDateEntity> = emptyList(),
        prefs: List<ReminderPreferenceEntity> = emptyList(),
        offsets: List<ReminderOffsetEntity> = emptyList()
    ) = FamilyMerge.Side(persons, phones, rels, dates, prefs, offsets)

    private fun ok(r: FamilyMerge.Result) =
        (r as FamilyMerge.Result.Ok).plan

    // ---------- ① 同源更新：按 id 认回 + 应用后幂等 ----------

    @Test
    fun 同源更新_按id认回只新增_应用后没有新内容() {
        seq = 0
        // 本机是对方旧版：我 + 父亲；对方新增母亲与其关系/生日/手机号
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE)),
            rels = listOf(r("dad", "me", RelationshipType.PARENT)))
        val remote = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE),
                p("mom", "王秀兰", Gender.FEMALE)),
            rels = listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE)),
            phones = listOf(ph("rp1", "mom", "13800001111")),
            dates = listOf(
                d("rd1", "mom", ImportantDateType.BIRTHDAY_SOLAR,
                    1965, 3, 8)))
        val plan = ok(FamilyMerge.plan(req(local, remote)))
        assertEquals(setOf("me", "dad"), plan.idMap.keys)
        assertEquals(listOf("mom"), plan.newPersons.map { it.id })
        assertFalse(plan.newPersons[0].isSelf)
        assertEquals(2, plan.newRelationships.size)
        assertEquals(1, plan.newPhones.size)
        assertEquals(listOf("mom"), plan.newDates.map { it.personId })
        assertTrue(plan.candidates.isEmpty())
        assertTrue(plan.conflicts.isEmpty())

        // 把计划手动应用到本机快照后再算一次 → 没有新内容
        val merged = local.copy(
            persons = local.persons + plan.newPersons,
            phones = local.phones + plan.newPhones,
            relationships = local.relationships + plan.newRelationships,
            dates = local.dates + plan.newDates)
        val again = ok(FamilyMerge.plan(
            req(merged, remote, knownMap = plan.mapping)))
        assertTrue(again.nothingToShow())
    }

    // ---------- ② 同 id 改名冲突 + takeRemote + 性别不符换新 ----------

    @Test
    fun 同id改名_冲突与takeRemote_性别不符换新() {
        seq = 0
        val local = side(listOf(p("me", "杨明", Gender.MALE, true),
            p("dad", "杨国", Gender.MALE)))
        val remote = side(listOf(p("me", "杨明", Gender.MALE, true),
            p("dad", "杨建国", Gender.MALE)))
        val plan = ok(FamilyMerge.plan(req(local, remote)))
        val c = plan.conflicts.single()
        assertEquals(FamilyMerge.ConflictKey("dad", "name"), c.key)
        assertEquals("姓名", c.fieldLabel)
        assertEquals("杨国", c.localValue)
        assertEquals("杨建国", c.remoteValue)
        assertTrue(plan.personUpdates.isEmpty())
        // takeRemote → 改写进 personUpdates（updatedAt=now）
        val plan2 = ok(FamilyMerge.plan(req(local, remote,
            takeRemote = setOf(c.key))))
        val u = plan2.personUpdates.single()
        assertEquals("dad", u.id)
        assertEquals("杨建国", u.name)
        assertEquals(1000L, u.updatedAt)

        // 同 id 但性别不同 → 不认为同一人，对方按新人物换新 id
        seq = 0
        val local2 = side(listOf(
            p("me", "杨明", Gender.MALE, true),
            p("x", "小芳", Gender.FEMALE)))
        val remote2 = side(listOf(
            p("me", "杨明", Gender.MALE, true),
            p("x", "小军", Gender.MALE)))
        val plan3 = ok(FamilyMerge.plan(req(local2, remote2)))
        assertFalse("x" in plan3.idMap)
        val np = plan3.newPersons.single()
        assertEquals("new-1", np.id)
        assertEquals("小军", np.name)
        assertEquals("new-1", plan3.mapping["x"])
    }

    // ---------- ③ anchors + 结构候选（同名母亲/异名外婆）+ approved ----------

    @Test
    fun anchors发现两层候选_确认后认回() {
        seq = 0
        // 本机：我 + 母王秀兰 + 外婆王老太；对方：我 + 母王秀兰 + 外婆李秀兰
        // （对方 id 全不同 → 全靠 anchor + 结构候选认人）
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("am", "王秀兰", Gender.FEMALE),
                p("agm", "王老太", Gender.FEMALE)),
            rels = listOf(
                r("am", "me", RelationshipType.PARENT),
                r("agm", "am", RelationshipType.PARENT)))
        val remote = side(
            listOf(p("rs", "杨明", Gender.MALE, true),
                p("rm", "王秀兰", Gender.FEMALE),
                p("rgm", "李秀兰", Gender.FEMALE)),
            rels = listOf(
                r("rm", "rs", RelationshipType.PARENT),
                r("rgm", "rm", RelationshipType.PARENT)))
        val plan = ok(FamilyMerge.plan(
            req(local, remote, anchors = mapOf("rs" to "me"))))
        assertEquals("me", plan.idMap["rs"])
        val c1 = plan.candidates.single { it.remoteId == "rm" }
        assertEquals("am", c1.localId)
        assertEquals("杨明的母亲", c1.desc)
        assertFalse(c1.nameDiffers)
        // 外婆经候选再扩展，异名标 nameDiffers
        val c2 = plan.candidates.single { it.remoteId == "rgm" }
        assertEquals("agm", c2.localId)
        assertTrue(c2.nameDiffers)
        // 确认后全部认回，不新建人物与关系；异名认回 → 姓名冲突
        val plan2 = ok(FamilyMerge.plan(
            req(local, remote, anchors = mapOf("rs" to "me"),
                approved = setOf("rm", "rgm"))))
        assertEquals("am", plan2.idMap["rm"])
        assertEquals("agm", plan2.idMap["rgm"])
        assertTrue(plan2.newPersons.isEmpty())
        assertTrue(plan2.newRelationships.isEmpty())
        assertTrue(plan2.conflicts.any {
            it.key == FamilyMerge.ConflictKey("agm", "name") })
    }

    // ---------- ④ 不确认外婆 → 关系矛盾 + conflictIfDeclined ----------

    @Test
    fun 不确认外婆_关系矛盾与候选提示() {
        seq = 0
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("am", "王秀兰", Gender.FEMALE),
                p("agm", "王老太", Gender.FEMALE)),
            rels = listOf(
                r("am", "me", RelationshipType.PARENT),
                r("agm", "am", RelationshipType.PARENT)))
        val remote = side(
            listOf(p("rs", "杨明", Gender.MALE, true),
                p("rm", "王秀兰", Gender.FEMALE),
                p("rgm", "李秀兰", Gender.FEMALE)),
            rels = listOf(
                r("rm", "rs", RelationshipType.PARENT),
                r("rgm", "rm", RelationshipType.PARENT)))
        // 只确认妈妈，不确认外婆 → 外婆按新人物，妈妈多一位母亲
        val plan = ok(FamilyMerge.plan(
            req(local, remote, anchors = mapOf("rs" to "me"),
                approved = setOf("rm"))))
        assertEquals("am", plan.idMap["rm"])
        assertFalse("rgm" in plan.idMap)
        val rc = plan.relationConflicts.single()
        assertEquals("rgm", rc.aId)           // 外婆（按新人物，id 不撞则保留）→ 王秀兰
        assertEquals("am", rc.bId)
        assertEquals("王秀兰已经记了母亲", rc.reason)
        val cand = plan.candidates.single { it.remoteId == "rgm" }
        assertTrue(cand.conflictIfDeclined)
    }

    // ---------- ⑤ 关系约束（表驱动） ----------

    @Test
    fun 关系约束_各类进冲突_相同跳过() {
        data class Row(
            val desc: String,
            val localRels: List<RelationshipEntity>,
            val remoteRel: RelationshipEntity,
            /** null = 完全相同 → 静默跳过；否则 = 应出现的冲突原因。 */
            val reason: String?
        )
        val base = listOf(
            p("me", "杨明", Gender.MALE, true),
            p("dad", "杨建国", Gender.MALE),
            p("mom", "王秀兰", Gender.FEMALE),
            p("w", "李雪", Gender.FEMALE),
            p("gm", "杨福生", Gender.MALE),
            p("x", "钱多多", Gender.MALE),
            p("xf", "小芳", Gender.FEMALE))
        val rows = listOf(
            Row("完全相同跳过",
                listOf(r("dad", "me", RelationshipType.PARENT)),
                r("dad", "me", RelationshipType.PARENT), null),
            Row("两位父母已满",
                listOf(
                    r("dad", "me", RelationshipType.PARENT),
                    r("mom", "me", RelationshipType.PARENT)),
                r("x", "me", RelationshipType.PARENT),
                "已经记了两位父母"),
            Row("同性别父母",
                listOf(r("dad", "me", RelationshipType.PARENT)),
                r("x", "me", RelationshipType.PARENT),
                "已经记了父亲"),
            Row("配偶已有",
                listOf(r("me", "w", RelationshipType.SPOUSE)),
                r("me", "xf", RelationshipType.SPOUSE),
                "已经有配偶了"),
            Row("辈分循环（me 记成 gm 的父母）",
                listOf(
                    r("gm", "dad", RelationshipType.PARENT),
                    r("dad", "me", RelationshipType.PARENT)),
                r("me", "gm", RelationshipType.PARENT),
                "会形成辈分循环"),
            Row("同一对人换类型",
                listOf(r("dad", "me", RelationshipType.PARENT)),
                r("dad", "me", RelationshipType.SIBLING),
                "本机已记为："))
        for (row in rows) {
            seq = 0
            val local = side(base, rels = row.localRels)
            val remote = side(base, rels = listOf(row.remoteRel))
            val plan = ok(FamilyMerge.plan(req(local, remote)))
            if (row.reason == null) {
                assertTrue(row.desc, plan.newRelationships.isEmpty())
                assertTrue(row.desc, plan.relationConflicts.isEmpty())
            } else {
                assertTrue(row.desc + " -> " +
                    plan.relationConflicts.map { it.reason },
                    plan.relationConflicts.any {
                        it.reason.contains(row.reason) })
                // 违规关系不写入
                assertTrue(row.desc, plan.newRelationships.isEmpty())
            }
        }
    }

    // ---------- ⑥ 手机号 + 日期（补缺/偏好/冲突/忌日/纪念日） ----------

    @Test
    fun 手机号与日期规则() {
        seq = 0
        // --- 手机号：规范化去重 + 主号码降级 + 新人物保留 ---
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE)),
            phones = listOf(ph("lp1", "dad", "13800001111")),
            rels = listOf(r("dad", "me", RelationshipType.PARENT)))
        val remote = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE),
                p("mom", "王秀兰", Gender.FEMALE)),
            rels = listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE)),
            phones = listOf(
                ph("rp1", "dad", "138-0000-1111"),            // 同号换写法 → 去重
                ph("rp2", "dad", "139-0000-2222"),            // 新号 → 降级
                ph("rp3", "mom", "137-0000-3333")),           // 新人物 → 保留
            dates = listOf(
                // 同值 → 跳过（dad 的阳历在本机补一条同值）
                d("rd1", "dad", ImportantDateType.BIRTHDAY_SOLAR,
                    1965, 3, 8),
                // 缺农历 → 补，带文件偏好（关 + {7}）
                d("rd2", "dad", ImportantDateType.BIRTHDAY_LUNAR,
                    1965, 2, 9, CalendarType.LUNAR),
                // 缺结婚纪念日 → 补（dad-mom 是夫妻），文件无偏好 → 默认 {1,0}
                d("rd3", "dad", ImportantDateType.WEDDING_ANNIVERSARY,
                    1988, 6, 6),
                // dad 在世 → 忌日不加
                d("rd4", "dad", ImportantDateType.DEATH_ANNIVERSARY,
                    2020, 4, 4),
                // 新人物生日：文件无偏好 → 默认 {0,1}
                d("rd5", "mom", ImportantDateType.BIRTHDAY_SOLAR,
                    1965, 3, 8)),
            prefs = listOf(pref("rpf1", "rd2", enabled = false)),
            offsets = listOf(off("rof1", "rpf1", 7)))
        val localWithDate = local.copy(
            dates = listOf(
                d("ld1", "dad", ImportantDateType.BIRTHDAY_SOLAR,
                    1965, 3, 8)))
        val plan = ok(FamilyMerge.plan(req(localWithDate, remote)))
        // 手机号
        assertEquals(2, plan.newPhones.size)
        val addedPh = plan.newPhones.single { it.personId == "dad" }
        assertFalse("本机已有主号码 → 补上的不设为主", addedPh.isPrimary)
        assertTrue("新人物号码原样",
            plan.newPhones.single { it.personId == "mom" }.isPrimary)
        // 日期：阳历同值跳过；忌日在世跳过；农历+纪念日+mom 生日补上
        val types = plan.newDates.map { it.personId to it.type }
        assertEquals(3, plan.newDates.size)
        assertTrue(("dad" to ImportantDateType.BIRTHDAY_LUNAR) in types)
        assertTrue(("dad" to ImportantDateType.WEDDING_ANNIVERSARY) in types)
        assertTrue(("mom" to ImportantDateType.BIRTHDAY_SOLAR) in types)
        // 偏好：农历复制文件偏好（关、{7}）；其余补默认
        val lunarDate = plan.newDates.single {
            it.type == ImportantDateType.BIRTHDAY_LUNAR }
        val lunarPref = plan.newPrefs.single {
            it.importantDateId == lunarDate.id }
        assertFalse(lunarPref.enabled)
        assertEquals(listOf(7), plan.newOffsets
            .filter { it.reminderPreferenceId == lunarPref.id }
            .map { it.daysBefore })
        val wedDate = plan.newDates.single {
            it.type == ImportantDateType.WEDDING_ANNIVERSARY }
        val wedPref = plan.newPrefs.single {
            it.importantDateId == wedDate.id }
        assertEquals(setOf(1, 0), plan.newOffsets
            .filter { it.reminderPreferenceId == wedPref.id }
            .map { it.daysBefore }.toSet())
        assertTrue(plan.newPrefs.single {
            it.importantDateId ==
                plan.newDates.single { it.personId == "mom" }.id
        }.enabled)

        // --- 日期冲突 + takeRemote（沿用本机行 id）；忌日随在世改写才加 ---
        seq = 0
        val local2 = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE)),
            rels = listOf(r("dad", "me", RelationshipType.PARENT)),
            dates = listOf(
                d("ld1", "dad", ImportantDateType.BIRTHDAY_SOLAR,
                    1965, 3, 8),
                d("lw", "dad", ImportantDateType.WEDDING_ANNIVERSARY,
                    1988, 6, 6)))
        val remote2 = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE,
                    alive = LivingStatus.DECEASED),
                p("mom", "王秀兰", Gender.FEMALE)),
            rels = listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE)),
            dates = listOf(
                // 不同值 → 冲突
                d("rd1", "dad", ImportantDateType.BIRTHDAY_SOLAR,
                    1966, 3, 8),
                // 结婚纪念日：dad 本机已有 → 只比较不新增（不同值 → 冲突）
                d("rd2", "dad", ImportantDateType.WEDDING_ANNIVERSARY,
                    1989, 6, 6),
                d("rd3", "mom", ImportantDateType.WEDDING_ANNIVERSARY,
                    1989, 6, 6),
                // 忌日：dad 本机在世 → 不加
                d("rd4", "dad", ImportantDateType.DEATH_ANNIVERSARY,
                    2020, 4, 4)))
        val plan2 = ok(FamilyMerge.plan(req(local2, remote2)))
        val nameKeys = plan2.conflicts.map { it.key.field }
        assertTrue(ImportantDateType.BIRTHDAY_SOLAR.name in nameKeys)
        assertTrue(ImportantDateType.WEDDING_ANNIVERSARY.name in nameKeys)
        assertTrue(plan2.newDates.none {
            it.type == ImportantDateType.WEDDING_ANNIVERSARY })
        assertTrue(plan2.newDates.none {
            it.type == ImportantDateType.DEATH_ANNIVERSARY })
        // takeRemote：生日用对方的（保留本机行 id）；dad 改已故 → 忌日进入
        val plan3 = ok(FamilyMerge.plan(req(local2, remote2,
            takeRemote = setOf(
                FamilyMerge.ConflictKey("dad",
                    ImportantDateType.BIRTHDAY_SOLAR.name),
                FamilyMerge.ConflictKey("dad", "livingStatus")))))
        val du = plan3.dateUpdates.single()
        assertEquals("ld1", du.id)
        assertEquals(1966, du.year)
        assertTrue(plan3.newDates.any {
            it.type == ImportantDateType.DEATH_ANNIVERSARY })

        // --- 对方记的别段婚姻在本机被判矛盾：纪念日不挂到本机这对夫妻上 ---
        seq = 0
        val local3 = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("gm", "杨福生", Gender.MALE),
                p("gf", "王老太", Gender.FEMALE)),
            rels = listOf(r("gf", "gm", RelationshipType.SPOUSE)),
            dates = listOf(d("lw2", "gm",
                ImportantDateType.WEDDING_ANNIVERSARY, 1960, 1, 1)))
        val remote3 = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("gm", "杨福生", Gender.MALE),
                p("gf", "王老太", Gender.FEMALE),
                p("rx", "某某", Gender.FEMALE)),
            // 对方记 gm 娶了 rx（本机 gm 已有配偶 gf → 关系矛盾）
            rels = listOf(r("gm", "rx", RelationshipType.SPOUSE)),
            dates = listOf(d("rw9", "gm",
                ImportantDateType.WEDDING_ANNIVERSARY, 1990, 1, 1)))
        val plan4 = ok(FamilyMerge.plan(req(local3, remote3)))
        assertTrue(plan4.relationConflicts.isNotEmpty())
        // 那段婚姻的纪念日：不比较、不新增
        assertTrue(plan4.conflicts.none {
            it.key.field == ImportantDateType.WEDDING_ANNIVERSARY.name })
        assertTrue(plan4.newDates.isEmpty())
    }

    // ---------- ⑥b 恋爱/领证纪念日：同一对夫妻同类型只算一条 ----------

    @Test
    fun 恋爱领证纪念日_按夫妻和类型去重() {
        seq = 0
        // 本机：爸妈是夫妻；爸名下已有领证纪念日
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE),
                p("mom", "王秀兰", Gender.FEMALE)),
            rels = listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE)),
            dates = listOf(
                d("ld1", "dad", ImportantDateType.LICENSE_ANNIVERSARY,
                    1988, 6, 6)))
        val remote = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE),
                p("mom", "王秀兰", Gender.FEMALE)),
            rels = listOf(
                r("dad", "me", RelationshipType.PARENT),
                r("mom", "me", RelationshipType.PARENT),
                r("dad", "mom", RelationshipType.SPOUSE)),
            dates = listOf(
                // 领记录在妈名下且同值 → 与本机爸名下那条视为同一条
                d("rd1", "mom", ImportantDateType.LICENSE_ANNIVERSARY,
                    1988, 6, 6),
                // 恋爱纪念日：爸、妈各记一条同值 → 只进一条
                d("rd2", "dad", ImportantDateType.DATING_ANNIVERSARY,
                    1980, 1, 1),
                d("rd3", "mom", ImportantDateType.DATING_ANNIVERSARY,
                    1980, 1, 1)))
        val plan = ok(FamilyMerge.plan(req(local, remote)))
        val datings = plan.newDates.filter {
            it.type == ImportantDateType.DATING_ANNIVERSARY }
        assertEquals(1, datings.size)
        assertTrue(plan.newDates.none {
            it.type == ImportantDateType.LICENSE_ANNIVERSARY })
        assertTrue(plan.conflicts.isEmpty())

        // 对方领记录与本机不同值（记在妈名下）→ 冲突记在本机持有人爸身上
        seq = 0
        val remote2 = remote.copy(
            dates = listOf(d("rd9", "mom",
                ImportantDateType.LICENSE_ANNIVERSARY, 1989, 9, 9)))
        val plan2 = ok(FamilyMerge.plan(req(local, remote2)))
        val c = plan2.conflicts.single {
            it.key.field == ImportantDateType.LICENSE_ANNIVERSARY.name }
        assertEquals("dad", c.key.localPersonId)
        assertEquals("领证纪念日", c.fieldLabel)
        // 不同类型互不去重：对方再带一条结婚纪念日 → 正常新增
        seq = 0
        val remote3 = remote.copy(
            dates = remote.dates + d("rd8", "dad",
                ImportantDateType.WEDDING_ANNIVERSARY, 1988, 10, 1))
        val plan3 = ok(FamilyMerge.plan(req(local, remote3)))
        assertEquals(setOf(
            ImportantDateType.DATING_ANNIVERSARY,
            ImportantDateType.WEDDING_ANNIVERSARY),
            plan3.newDates.map { it.type }.toSet())
    }

    // ---------- ⑦ 头像 ----------

    @Test
    fun 头像_三种情况() {
        seq = 0
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE),           // 本机无头像
                p("mom", "王秀兰", Gender.FEMALE,
                    avatar = "avatars/local.jpg")))       // 本机已有
        val remote = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE,
                    avatar = "avatars/r-dad.jpg"),
                p("mom", "王秀兰", Gender.FEMALE,
                    avatar = "avatars/r-mom.jpg"),
                p("gm", "杨福生", Gender.MALE,
                    avatar = "avatars/r-gm.jpg")))
        val plan = ok(FamilyMerge.plan(req(local, remote)))
        // 新人物 → AvatarAdd(新 id, true)；已认出且无头像 → (本机 id, false)；
        // 本机已有头像 → 不加
        assertEquals(2, plan.avatarAdds.size)
        val gm = plan.avatarAdds.single { it.isNewPerson }
        assertEquals("gm", gm.localPersonId)  // 新人物 id 不撞 → 保留对方 id
        assertEquals("r-gm.jpg", gm.remoteFileName)
        val dad = plan.avatarAdds.single { !it.isNewPerson }
        assertEquals("dad", dad.localPersonId)
        assertEquals("r-dad.jpg", dad.remoteFileName)
        // 新人物的 avatarPath 由数据层写文件后补
        assertNull(plan.newPersons.single().avatarPath)
    }

    // ---------- ⑧ anchor / bridge 拒绝与放行 ----------

    @Test
    fun anchor与bridge_拒绝与放行() {
        seq = 0
        val local = side(listOf(p("me", "杨明", Gender.MALE, true),
            p("dad", "杨建国", Gender.MALE)))
        val remote = side(listOf(
            p("rs", "杨明", Gender.MALE, true),
            p("rm", "王秀兰", Gender.FEMALE)))
        // anchor 性别不符 → Reject
        val r1 = FamilyMerge.plan(req(local, remote,
            anchors = mapOf("rm" to "dad")))
        assertTrue(r1 is FamilyMerge.Result.Reject)
        assertEquals("「王秀兰」和「杨建国」性别不同，不能是同一个人",
            (r1 as FamilyMerge.Result.Reject).reason)
        // 同一本机人被两个 anchor 指认 → Reject
        val r2 = FamilyMerge.plan(req(local, remote,
            anchors = mapOf("rs" to "me", "rm" to "me")))
        assertTrue(r2 is FamilyMerge.Result.Reject)

        // bridge 正常：我是对方 rf 的儿子 → PARENT(rf→me)
        seq = 0
        val local3 = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("mom", "王秀兰", Gender.FEMALE)),
            rels = listOf(r("mom", "me", RelationshipType.PARENT)))
        val remote3 = side(
            listOf(p("rs", "王秀兰", Gender.FEMALE, true),
                p("rf", "杨建国", Gender.MALE)))
        val plan = ok(FamilyMerge.plan(req(local3, remote3,
            anchors = mapOf("rs" to "mom"),
            bridge = FamilyMerge.Bridge("rf", RelationRole.SON))))
        assertTrue(plan.newRelationships.any {
            it.type == RelationshipType.PARENT &&
                it.sourcePersonId == "rf" && it.targetPersonId == "me" })
        // bridge 违规（me 已有两位父母）→ Reject
        val local4 = local3.copy(relationships = local3.relationships +
            r("dad2", "me", RelationshipType.PARENT))
        val r3 = FamilyMerge.plan(req(local4, remote3,
            anchors = mapOf("rs" to "mom"),
            bridge = FamilyMerge.Bridge("rf", RelationRole.SON)))
        assertTrue(r3 is FamilyMerge.Result.Reject)
    }

    // ---------- ⑨ remoteAnchor（互加亲人“他不在我家”） ----------

    @Test
    fun remoteAnchor新建对方本人并挂入声明关系() {
        seq = 0
        // 本机：我 + 大姨；对方带来：他本人 + 他妈妈 + 他弟弟
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("aunt", "王秀兰", Gender.FEMALE)),
            rels = listOf(r("aunt", "me", RelationshipType.PARENT)))
        val remote = side(
            listOf(p("rs", "李雷", Gender.MALE, true),
                p("rm", "王秀兰", Gender.FEMALE),
                p("rb", "李波", Gender.MALE)),
            rels = listOf(
                r("rm", "rs", RelationshipType.PARENT),
                r("rm", "rb", RelationshipType.PARENT),
                r("rs", "rb", RelationshipType.SIBLING)))
        // 声明“他是我家大姨的儿子”：对方本人新建并挂 PARENT(aunt→新人物)
        val plan = ok(FamilyMerge.plan(req(local, remote,
            remoteAnchor = FamilyMerge.RemoteAnchor(
                "rs", "aunt", RelationRole.SON))))
        val self = plan.newPersons.single { it.name == "李雷" }
        assertTrue(plan.newRelationships.any {
            it.type == RelationshipType.PARENT &&
                it.sourcePersonId == "aunt" && it.targetPersonId == self.id })
        // 对方的妈妈经增广图被认成本机大姨（结构候选）
        assertTrue(plan.candidates.any {
            it.remoteId == "rm" && it.localId == "aunt" })
    }

    @Test
    fun remoteAnchor声明的关系与本机矛盾则拒绝() {
        seq = 0
        // 本机“我”已记了父亲；声明“对方是我爸的儿子”不矛盾，
        // 但声明“对方是我的父亲”矛盾
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("dad", "杨建国", Gender.MALE)),
            rels = listOf(r("dad", "me", RelationshipType.PARENT)))
        val remote = side(listOf(
            p("rs", "李雷", Gender.MALE, true)))
        val r1 = FamilyMerge.plan(req(local, remote,
            remoteAnchor = FamilyMerge.RemoteAnchor(
                "rs", "me", RelationRole.FATHER)))
        assertTrue(r1 is FamilyMerge.Result.Reject)
        // 合法声明：对方是我爸的儿子（新建 + PARENT(dad→新人物)）
        val plan = ok(FamilyMerge.plan(req(local, remote,
            remoteAnchor = FamilyMerge.RemoteAnchor(
                "rs", "dad", RelationRole.SON))))
        val self = plan.newPersons.single()
        assertTrue(plan.newRelationships.any {
            it.sourcePersonId == "dad" && it.targetPersonId == self.id })
    }

    @Test
    fun remoteAnchor已被认出时不生效() {
        seq = 0
        // anchors 已把对方本人指到本机人物 → remoteAnchor 被忽略
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("cousin", "李雷", Gender.MALE)))
        val remote = side(listOf(p("rs", "李雷", Gender.MALE, true)))
        val plan = ok(FamilyMerge.plan(req(local, remote,
            anchors = mapOf("rs" to "cousin"),
            remoteAnchor = FamilyMerge.RemoteAnchor(
                "rs", "me", RelationRole.SON))))
        assertTrue(plan.newPersons.isEmpty())
        assertEquals("cousin", plan.idMap["rs"])
    }

    @Test
    fun knownMap沿用保存的映射不再追问() {
        seq = 0
        // 上次互加留下的映射：对方 rs → 本机 cousin
        val local = side(
            listOf(p("me", "杨明", Gender.MALE, true),
                p("cousin", "李雷", Gender.MALE)))
        val remote = side(
            listOf(p("rs", "李雷", Gender.MALE, true),
                p("rn", "新成员", Gender.MALE)),
            rels = listOf(r("rn", "rs", RelationshipType.PARENT)))
        val plan = ok(FamilyMerge.plan(req(local, remote,
            knownMap = mapOf("rs" to "cousin"))))
        assertEquals("cousin", plan.idMap["rs"])
        // 新成员照旧新增并挂到已认出的 cousin 上
        val np = plan.newPersons.single { it.name == "新成员" }
        assertTrue(plan.newRelationships.any {
            it.sourcePersonId == np.id && it.targetPersonId == "cousin" })
    }
}
