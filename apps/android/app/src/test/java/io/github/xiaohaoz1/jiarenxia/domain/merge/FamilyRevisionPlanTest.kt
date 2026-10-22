package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyRevisionPlan.Choice
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyRevisionPlan.Ctx
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyRevisionPlan.IdentChoice
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyRevisionPlan.Op
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilyRevisionPlan.State
import io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot.Snap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 协作三方比较计划（FAMILY_AFFAIRS §5.3）：快照构建/哈希/规范化、
 * 三值分类、身份裁决、再现防护、阻止项、待汇入保护、决议→写操作。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilyRevisionPlanTest {

    @Test fun 普通合并与协作接收都拒绝配偶路径上的逆向亲子关系() {
        val people = listOf(p("mother", "陈静怡", Gender.FEMALE),
            p("father", "许文成", Gender.MALE), p("girl", "许思宁", Gender.FEMALE))
        val relationships = listOf(rel("mother", "girl", RelationshipType.PARENT),
            rel("father", "mother", RelationshipType.SPOUSE))
        val bad = rel("girl", "father", RelationshipType.PARENT)
        val good = rel("father", "girl", RelationshipType.PARENT)
        val normal = RelationGraph(people.associateBy { it.id }, relationships)
        assertTrue(normal.check(bad)?.reason?.contains("辈分冲突") == true)
        assertNull(normal.check(good))
        val s = snap(persons = people, rels = relationships)
        assertTrue(FamilyRevisionPlan.checkRel(s.rels, s.personMap,
            FamilySnapshot.normalizeRel(bad), emptySet()).first?.contains("辈分冲突") == true)
        assertNull(FamilyRevisionPlan.checkRel(s.rels, s.personMap,
            FamilySnapshot.normalizeRel(good), emptySet()).first)
    }

    // ---------- 造数 ----------

    private fun p(
        id: String, name: String, g: Gender,
        living: LivingStatus = LivingStatus.ALIVE, note: String? = null
    ) = PersonEntity(
        id = id, name = name, gender = g, avatarPath = null,
        note = note, livingStatus = living,
        isSelf = false, createdAt = 0, updatedAt = 0)

    private fun rel(
        src: String, tgt: String, t: RelationshipType,
        order: RelativeAgeOrder? = null,
        id: String = "r-$src-$tgt-${t.name}"
    ) = RelationshipEntity(
        id = id, sourcePersonId = src, targetPersonId = tgt,
        type = t, relativeAgeOrder = order, createdAt = 0, updatedAt = 0)

    private fun phone(
        pid: String, number: String, primary: Boolean = false,
        label: PhoneLabel = PhoneLabel.MOBILE, note: String? = null
    ) = PhoneNumberEntity(
        id = "ph-$pid-$number", personId = pid, number = number,
        label = label, status = PhoneStatus.ACTIVE, isPrimary = primary,
        note = note, createdAt = 0, updatedAt = 0)

    private fun date(
        pid: String, type: ImportantDateType, year: Int?,
        month: Int, day: Int, cal: CalendarType = CalendarType.SOLAR
    ) = ImportantDateEntity(
        id = "dt-$pid-${type.name}", personId = pid, type = type,
        year = year, month = month, day = day, calendarType = cal,
        isLeapMonth = false, note = null, createdAt = 0, updatedAt = 0)

    private fun snap(
        persons: List<PersonEntity>,
        phones: List<PhoneNumberEntity> = emptyList(),
        rels: List<RelationshipEntity> = emptyList(),
        dates: List<ImportantDateEntity> = emptyList(),
        avatarHash: (String) -> String? = { null }
    ): Snap = FamilySnapshot.fromEntities(
        persons = persons, phones = phones, rels = rels, dates = dates,
        avatarHashOf = { avatarHash(it.id) })

    private val ctx = Ctx(selfCanonicalId = "me")

    private fun itemKeys(plan: FamilyRevisionPlan.Plan, s: State) =
        plan.items.filter { it.state == s }.map { it.key }.toSet()

    // ---------- 快照构建 ----------

    @Test
    fun 快照规范化_号码归一与排序() {
        val s = snap(
            persons = listOf(p("a", "杨明", Gender.MALE)),
            phones = listOf(
                phone("a", "138 0013 8000"),
                phone("a", "010-12345678"))
        )
        val ph = s.persons.single().phones
        assertEquals(listOf("01012345678", "13800138000"),
            ph.map { it.number })
    }

    @Test
    fun 快照规范化_夫妻共享日期挂小id方() {
        val s = snap(
            persons = listOf(p("b", "张芳", Gender.FEMALE),
                p("a", "杨明", Gender.MALE)),
            rels = listOf(rel("a", "b", RelationshipType.SPOUSE)),
            dates = listOf(
                date("b", ImportantDateType.WEDDING_ANNIVERSARY, 2000, 5, 1))
        )
        // 挂在 id 较小的 "a" 上
        assertEquals(1, s.personMap["a"]!!.dates.size)
        assertTrue(s.personMap["b"]!!.dates.isEmpty())
    }

    @Test
    fun 快照哈希_键序无关同构同值() {
        val s1 = snap(
            persons = listOf(p("a", "杨明", Gender.MALE), p("b", "张芳", Gender.FEMALE)),
            rels = listOf(rel("a", "b", RelationshipType.SPOUSE)))
        // 关系端点反过来写（存储方向不同）规范化后哈希应一致
        val s2 = snap(
            persons = listOf(p("b", "张芳", Gender.FEMALE), p("a", "杨明", Gender.MALE)),
            rels = listOf(rel("b", "a", RelationshipType.SPOUSE)))
        assertEquals(FamilySnapshot.hash(s1), FamilySnapshot.hash(s2))
    }

    @Test
    fun 快照JSON往返() {
        val s = snap(
            persons = listOf(p("a", "杨明", Gender.MALE, note = "爸爸")),
            phones = listOf(phone("a", "13800138000", primary = true)),
            dates = listOf(
                date("a", ImportantDateType.BIRTHDAY_SOLAR, 1970, 3, 5),
                date("a", ImportantDateType.BIRTHDAY_LUNAR, null, 2, 1,
                    CalendarType.LUNAR)))
        val parsed = FamilySnapshot.parse(FamilySnapshot.toJson(s))
        assertEquals(FamilySnapshot.hash(s), FamilySnapshot.hash(parsed))
    }

    @Test
    fun 快照排除仅本机() {
        val s = snap(
            persons = listOf(p("a", "杨明", Gender.MALE), p("x", "私密", Gender.MALE)),
            rels = listOf(rel("a", "x", RelationshipType.SIBLING)),
            avatarHash = { null }
        )
        val filtered = FamilySnapshot.fromEntities(
            persons = listOf(p("a", "杨明", Gender.MALE), p("x", "私密", Gender.MALE)),
            phones = emptyList(),
            rels = listOf(rel("a", "x", RelationshipType.SIBLING)),
            dates = emptyList(),
            avatarHashOf = { null },
            excludeIds = setOf("x"))
        assertEquals(1, filtered.persons.size)
        assertTrue(filtered.rels.isEmpty())
    }

    // ---------- 三值分类 ----------

    @Test
    fun 无基准_远端新增自动采用() {
        val remote = snap(listOf(p("m", "妈妈", Gender.FEMALE)))
        val plan = FamilyRevisionPlan.plan(null, remote, snap(emptyList()), ctx)
        assertEquals(setOf("add:m"), itemKeys(plan, State.APPLY))
        val r = FamilyRevisionPlan.resolve(null, remote, snap(emptyList()),
            ctx, plan, emptyMap())
        assertEquals(listOf("m"), r.ops.filterIsInstance<Op.AddPerson>()
            .map { it.person.id })
    }

    @Test
    fun 无基准_远端与本机同名可疑_需身份裁决() {
        val remote = snap(listOf(p("r1", "爸爸", Gender.MALE)))
        val local = snap(listOf(p("l1", "爸爸", Gender.MALE)))
        val plan = FamilyRevisionPlan.plan(null, remote, local, ctx)
        val add = plan.items.single() as FamilyRevisionPlan.PersonAdd
        assertEquals(State.IDENTITY, add.state)
        assertEquals(listOf("l1"), add.candidates.map { it.localId })
        // 裁决为同一人 → 不产生新增
        val d = mapOf(add.key to IdentChoice.wire(IdentChoice.Same("l1")))
        val c2 = ctx2(identities = mapOf("r1" to IdentChoice.Same("l1")))
        val plan2 = FamilyRevisionPlan.plan(null, remote, local, c2)
        assertTrue(plan2.decisions.isEmpty())
        val r = FamilyRevisionPlan.resolve(null, remote, local, c2, plan2, d)
        assertTrue(r.ops.isEmpty())
        assertEquals(mapOf("r1" to "l1"), r.identityAdds)
    }

    @Test
    fun 无基准_缺项不推断删除() {
        // 远端比本机少一个人：无基准时不产生删除项
        val remote = snap(listOf(p("a", "杨明", Gender.MALE)))
        val local = snap(listOf(
            p("a", "杨明", Gender.MALE), p("x", "张叔", Gender.MALE)))
        val plan = FamilyRevisionPlan.plan(null, remote, local, ctx)
        assertTrue(plan.items.isEmpty())
        // 本机多出的记为待汇入
        assertEquals(1, plan.pendingLocal.size)
        assertTrue(plan.pendingLocal[0] is FamilyRevisionPlan.PersonAdd)
    }

    @Test
    fun 三方_远端改本机未改_应用() {
        val base = snap(listOf(p("m", "妈妈", Gender.FEMALE)))
        val remote = snap(listOf(p("m", "妈妈王", Gender.FEMALE)))
        val local = snap(listOf(p("m", "妈妈", Gender.FEMALE)))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertEquals(setOf("f:m:name"), itemKeys(plan, State.APPLY))
        val r = FamilyRevisionPlan.resolve(base, remote, local, ctx,
            plan, emptyMap())
        val set = r.ops.single() as Op.SetPerson
        assertEquals("妈妈王", set.name)
    }

    @Test
    fun 三方_本机改远端未动_待汇入() {
        val base = snap(listOf(p("m", "妈妈", Gender.FEMALE)))
        val remote = base
        val local = snap(listOf(p("m", "妈妈李", Gender.FEMALE)))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertTrue(plan.items.isEmpty())
        assertEquals(setOf("f:m:name"),
            plan.pendingLocal.map { it.key }.toSet())
    }

    @Test
    fun 三方_双改不一致_分歧() {
        val base = snap(listOf(p("m", "妈妈", Gender.FEMALE)))
        val remote = snap(listOf(p("m", "王芳", Gender.FEMALE)))
        val local = snap(listOf(p("m", "李芳", Gender.FEMALE)))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertEquals(setOf("f:m:name"), itemKeys(plan, State.CONFLICT))
        // 决议取远端
        val r = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf("f:m:name" to Choice.TAKE_REMOTE.wire))
        assertEquals("王芳",
            (r.ops.single() as Op.SetPerson).name)
        // 决议留本机
        val r2 = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf("f:m:name" to Choice.KEEP_LOCAL.wire))
        assertTrue(r2.ops.isEmpty())
    }

    @Test
    fun 三方_远端删除条目_应用() {
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000")))
        val remote = snap(persons = listOf(p("m", "妈妈", Gender.FEMALE)))
        val local = base
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertEquals(setOf("ph:m:13800000000"), itemKeys(plan, State.APPLY))
        val r = FamilyRevisionPlan.resolve(base, remote, local, ctx,
            plan, emptyMap())
        assertEquals(listOf("m" to "13800000000"),
            r.ops.filterIsInstance<Op.DelPhone>().map { it.personId to it.number })
    }

    // ---------- 删除×修改三方语义（审查 R2） ----------

    @Test
    fun 三方_远端删条目本机未改_自动删除() {
        // 基准电话、本机原样、远端删 → L==B 才可自动删
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000")))
        val remote = snap(persons = listOf(p("m", "妈妈", Gender.FEMALE)))
        val plan = FamilyRevisionPlan.plan(base, remote, base, ctx)
        assertEquals(setOf("ph:m:13800000000"), itemKeys(plan, State.APPLY))
    }

    @Test
    fun 三方_远端删电话本机已改_须确认分歧() {
        // 本机改过标签 → 远端删除不能直接落
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000", label = PhoneLabel.MOBILE)))
        val remote = snap(persons = listOf(p("m", "妈妈", Gender.FEMALE)))
        val local = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000", label = PhoneLabel.HOME)))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertEquals(setOf("ph:m:13800000000"),
            itemKeys(plan, State.CONFLICT))
        // 采用远端 → 删除；保留本机 → 不删
        val take = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf("ph:m:13800000000" to Choice.TAKE_REMOTE.wire))
        assertEquals(1, take.ops.filterIsInstance<Op.DelPhone>().size)
        val keep = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf("ph:m:13800000000" to Choice.KEEP_LOCAL.wire))
        assertTrue(keep.ops.isEmpty())
    }

    @Test
    fun 三方_远端删日期本机已改_须确认分歧() {
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            dates = listOf(date("m", ImportantDateType.BIRTHDAY_SOLAR,
                1970, 3, 2)))
        val remote = snap(persons = listOf(p("m", "妈妈", Gender.FEMALE)))
        val local = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            dates = listOf(date("m", ImportantDateType.BIRTHDAY_SOLAR,
                1970, 3, 3)))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertEquals(setOf("dt:m:BIRTHDAY_SOLAR"),
            itemKeys(plan, State.CONFLICT))
        assertTrue(itemKeys(plan, State.APPLY).isEmpty())
    }

    @Test
    fun 三方_本机删远端已改_须确认分歧() {
        // 基准生日 3/2，远端改 3/3，本机删 → 不是普通待汇入
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            dates = listOf(date("m", ImportantDateType.BIRTHDAY_SOLAR,
                1970, 3, 2)))
        val remote = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            dates = listOf(date("m", ImportantDateType.BIRTHDAY_SOLAR,
                1970, 3, 3)))
        val local = snap(persons = listOf(p("m", "妈妈", Gender.FEMALE)))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertEquals(setOf("dt:m:BIRTHDAY_SOLAR"),
            itemKeys(plan, State.CONFLICT))
        // 采用远端 → 恢复远端的新值
        val take = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf("dt:m:BIRTHDAY_SOLAR" to Choice.TAKE_REMOTE.wire))
        val put = take.ops.filterIsInstance<Op.PutDate>().single()
        assertEquals(3, put.date.day)
        // 保留本机 → 维持删除
        val keep = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf("dt:m:BIRTHDAY_SOLAR" to Choice.KEEP_LOCAL.wire))
        assertTrue(keep.ops.isEmpty())
    }

    @Test
    fun 三方_本机删远端未改_待汇入() {
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000")))
        val local = snap(persons = listOf(p("m", "妈妈", Gender.FEMALE)))
        val plan = FamilyRevisionPlan.plan(base, base, local, ctx)
        assertTrue(plan.items.isEmpty())
        assertEquals(setOf("ph:m:13800000000"),
            plan.pendingLocal.map { it.key }.toSet())
    }

    @Test
    fun 三方_双端同删基准号各自记新号_换号分歧() {
        // 基准号码 A：远端删 A 记 B，本机删 A 记 C —— 是替换分歧，
        // 不能静默并存 B、C
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000")))
        val remote = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13900000000")))
        val local = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13700000000")))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        // 两端新号都列为待确认，未裁决不能接收
        assertEquals(setOf("ph:m:13900000000", "ph:m:13700000000"),
            itemKeys(plan, State.CONFLICT))
        assertFalse(plan.ready(emptyMap()))
        // 采用远端 → 写入 B、删除本机 C
        val take = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf("ph:m:13900000000" to Choice.TAKE_REMOTE.wire,
                "ph:m:13700000000" to Choice.TAKE_REMOTE.wire))
        assertEquals(listOf("13900000000"),
            take.ops.filterIsInstance<Op.PutPhone>().map { it.phone.number })
        assertEquals(listOf("13700000000"),
            take.ops.filterIsInstance<Op.DelPhone>().map { it.number })
        // 保留本机 → 不加 B、不删 C
        val keep = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf("ph:m:13900000000" to Choice.KEEP_LOCAL.wire,
                "ph:m:13700000000" to Choice.KEEP_LOCAL.wire))
        assertTrue(keep.ops.isEmpty())
    }

    @Test
    fun 三方_双端各自新增不同号_仍自动合并() {
        // 基准号码 A 双方都保留，各自新加了不同号码 —— 非替换，照常合并
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000")))
        val remote = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000"),
                phone("m", "13900000000")))
        val local = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE)),
            phones = listOf(phone("m", "13800000000"),
                phone("m", "13700000000")))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertEquals(setOf("ph:m:13900000000"), itemKeys(plan, State.APPLY))
        assertTrue(itemKeys(plan, State.CONFLICT).isEmpty())
        assertEquals(setOf("ph:m:13700000000"),
            plan.pendingLocal.map { it.key }.toSet())
    }

    @Test
    fun 远端整人移除_一个去留决策覆盖全部字段() {
        val base = snap(
            persons = listOf(p("m", "妈妈", Gender.FEMALE),
                p("f", "爸爸", Gender.MALE)),
            phones = listOf(phone("m", "138")),
            rels = listOf(rel("f", "m", RelationshipType.SPOUSE)))
        val remote = snap(persons = listOf(p("f", "爸爸", Gender.MALE)))
        val local = base
        val plan = FamilyRevisionPlan.plan(base, remote, local,
            Ctx(selfCanonicalId = "f"))
        // 只产生一个 PersonRemove 决策项；电话/关系差异不单独列项
        val rm = plan.items.filterIsInstance<FamilyRevisionPlan.PersonRemove>()
            .single()
        assertEquals("m", rm.personId)
        assertEquals(State.CONFLICT, rm.state)
        assertTrue(plan.items.none { it is FamilyRevisionPlan.PhoneItem })
        assertTrue(plan.items.none { it is FamilyRevisionPlan.RelItem })
        // 采用移除
        val r = FamilyRevisionPlan.resolve(base, remote, local,
            Ctx(selfCanonicalId = "f"), plan,
            mapOf(rm.key to Choice.TAKE_REMOTE.wire))
        assertEquals(listOf("m"),
            r.ops.filterIsInstance<Op.RemovePerson>().map { it.personId })
    }

    @Test
    fun 移除我为阻止项() {
        val base = snap(listOf(p("me", "我", Gender.MALE)))
        val remote = snap(emptyList())
        val local = base
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        val rm = plan.items.single() as FamilyRevisionPlan.PersonRemove
        assertEquals(State.BLOCKED, rm.state)
        assertTrue(rm.blocked)
    }

    @Test
    fun 把我标已故为阻止项() {
        val base = snap(listOf(p("me", "我", Gender.MALE)))
        val remote = snap(listOf(
            p("me", "我", Gender.MALE, living = LivingStatus.DECEASED)))
        val local = base
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        val f = plan.items.single() as FamilyRevisionPlan.FieldItem
        assertEquals(State.BLOCKED, f.state)
        // 决议强制保留后不落 SetPerson
        val r = FamilyRevisionPlan.resolve(base, remote, local, ctx, plan,
            mapOf(f.key to Choice.KEEP_LOCAL.wire))
        assertTrue(r.ops.isEmpty())
    }

    @Test
    fun 曾移除者再现_需显式裁决() {
        val remote = snap(listOf(p("x", "张叔", Gender.MALE)))
        val local = snap(emptyList())
        val plan = FamilyRevisionPlan.plan(null, remote, local,
            Ctx(selfCanonicalId = "me", removedIds = setOf("x")))
        val add = plan.items.single() as FamilyRevisionPlan.PersonAdd
        assertEquals(State.REAPPEAR, add.state)
        // 选择不重新加入 → 无操作
        val r = FamilyRevisionPlan.resolve(null, remote, local,
            Ctx(selfCanonicalId = "me", removedIds = setOf("x")),
            plan, mapOf(add.key to Choice.KEEP_LOCAL.wire))
        assertTrue(r.ops.isEmpty())
        // 选择重新加入
        val r2 = FamilyRevisionPlan.resolve(null, remote, local,
            Ctx(selfCanonicalId = "me", removedIds = setOf("x")),
            plan, mapOf(add.key to Choice.TAKE_REMOTE.wire))
        assertEquals(1, r2.ops.filterIsInstance<Op.AddPerson>().size)
    }

    @Test
    fun 关系_远端换型_删除加新增自动采用() {
        // 远端把 a-b 从配偶改成兄弟姐妹：删旧+加新都是 L=B → 自动应用
        val base = snap(
            persons = listOf(p("a", "杨明", Gender.MALE), p("b", "张芳", Gender.FEMALE)),
            rels = listOf(rel("a", "b", RelationshipType.SPOUSE)))
        val remote = snap(
            persons = listOf(p("a", "杨明", Gender.MALE), p("b", "张芳", Gender.FEMALE)),
            rels = listOf(rel("a", "b", RelationshipType.SIBLING)))
        val local = base
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        val relItems = plan.items.filterIsInstance<FamilyRevisionPlan.RelItem>()
        assertEquals(2, relItems.size)
        assertTrue(relItems.all { it.state == State.APPLY })
    }

    @Test
    fun 关系_远端新增与本机独有矛盾_转显式决策() {
        // 远端新增 a-b 兄弟姐妹；本机独有（远端不知道）a-b 是配偶 → 冲突
        val base = snap(persons = listOf(
            p("a", "杨明", Gender.MALE), p("b", "张芳", Gender.FEMALE)))
        val remote = snap(
            persons = listOf(p("a", "杨明", Gender.MALE), p("b", "张芳", Gender.FEMALE)),
            rels = listOf(rel("a", "b", RelationshipType.SIBLING)))
        val local = snap(
            persons = listOf(p("a", "杨明", Gender.MALE), p("b", "张芳", Gender.FEMALE)),
            rels = listOf(rel("a", "b", RelationshipType.SPOUSE)))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        val sib = plan.items.filterIsInstance<FamilyRevisionPlan.RelItem>()
            .first { it.rel.type == "SIBLING" }
        assertEquals(State.CONFLICT, sib.state)
        assertEquals(1, sib.alsoRemoves.size)
        assertEquals("SPOUSE", sib.alsoRemoves[0].type)
    }

    @Test
    fun 关系_未裁决新人关系暂列() {
        // 远端带来新人物及其关系，人物身份未决时关系项不列出
        val remote = snap(
            persons = listOf(
                p("a", "杨明", Gender.MALE),
                p("nb", "新弟", Gender.MALE)),
            rels = listOf(rel("a", "nb", RelationshipType.SIBLING)))
        val base = snap(persons = listOf(p("a", "杨明", Gender.MALE)))
        // 本机有同名同性别候选 → nb 是 IDENTITY
        val local = snap(
            persons = listOf(
                p("a", "杨明", Gender.MALE),
                p("lb", "新弟", Gender.MALE)),
            rels = emptyList())
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        val idItem = plan.items.filterIsInstance<FamilyRevisionPlan.PersonAdd>()
            .single()
        assertEquals(State.IDENTITY, idItem.state)
        // 涉及未决新人的关系项被跳过
        assertTrue(plan.items.none {
            it is FamilyRevisionPlan.RelItem &&
                (it.rel.a == "nb" || it.rel.b == "nb") })
    }

    @Test
    fun ready_决策未齐不可接收() {
        val base = snap(listOf(p("m", "妈妈", Gender.FEMALE)))
        val remote = snap(listOf(p("m", "王芳", Gender.FEMALE)))
        val local = snap(listOf(p("m", "李芳", Gender.FEMALE)))
        val plan = FamilyRevisionPlan.plan(base, remote, local, ctx)
        assertFalse(plan.ready(emptyMap()))
        assertTrue(plan.ready(mapOf("f:m:name" to "keep")))
    }

    private fun ctx2(
        identities: Map<String, IdentChoice> = emptyMap()
    ) = Ctx(selfCanonicalId = "me", identities = identities)
}
