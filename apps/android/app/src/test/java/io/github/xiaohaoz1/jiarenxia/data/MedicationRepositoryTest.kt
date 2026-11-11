package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.MedMeal
import io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用药记录仓库（FAMILY_AFFAIRS 23）：校验、usage 字段归一、停用、按人开关。 */
class MedicationRepositoryTest : RepoTestBase() {

    private lateinit var meds: MedicationRepositoryImpl
    private var medSeq = 0

    @org.junit.Before
    fun setUpMeds() {
        meds = MedicationRepositoryImpl(
            db,
            object : IdGenerator { override fun newId() = "med-" + (++medSeq) },
            object : TimeSource { override fun now() = now }
        )
    }

    private fun draft(
        personId: String,
        name: String = "阿莫西林",
        usage: MedUsage = MedUsage.SHORT_COURSE,
        courseDays: Int? = 7,
        refill: LocalDate? = null,
        times: List<Int> = emptyList(),
        meal: MedMeal? = null,
        start: LocalDate = LocalDate.of(2026, 9, 30),
        id: String? = null
    ) = MedicationDraft(
        id = id, personId = personId, name = name,
        dose = null, spec = null, meal = meal, usage = usage,
        courseDays = courseDays, startEpochDay = start.toEpochDay(),
        purpose = null, note = null,
        refillEpochDay = refill?.toEpochDay(), times = times
    )

    // ---------- 新建与读取 ----------

    @Test
    fun 新建短期药_字段与时刻落库() = runTest {
        val me = createSelf()
        val id = meds.save(
            draft(me, times = listOf(1200, 480), meal = MedMeal.AFTER)
        ).ok()
        val m = meds.get(id)!!
        assertEquals("阿莫西林", m.name)
        assertEquals(MedUsage.SHORT_COURSE, m.usage)
        assertEquals(7, m.courseDays)
        assertEquals(listOf(480, 1200), m.times)
        assertEquals(MedMeal.AFTER, m.meal)
        assertNull(m.stoppedEpochDay)
    }

    @Test
    fun usage归一化_非短期清天数_非长期清拿药日_需要时吃清时刻() = runTest {
        val me = createSelf()
        // LONG_TERM：courseDays 即使传了也清空；refill 保留
        val longId = meds.save(draft(
            me, usage = MedUsage.LONG_TERM, courseDays = 7,
            refill = LocalDate.of(2026, 10, 20),
            times = listOf(480)
        )).ok()
        val long = meds.get(longId)!!
        assertNull(long.courseDays)
        assertEquals(LocalDate.of(2026, 10, 20).toEpochDay(), long.refillEpochDay)
        assertEquals(listOf(480), long.times)
        // AS_NEEDED：时刻清空
        val asId = meds.save(draft(
            me, usage = MedUsage.AS_NEEDED, courseDays = null,
            times = listOf(480, 720)
        )).ok()
        assertTrue(meds.get(asId)!!.times.isEmpty())
        // SHORT_COURSE：refill 清空
        val shortId = meds.save(draft(
            me, usage = MedUsage.SHORT_COURSE, courseDays = 5,
            refill = LocalDate.of(2026, 10, 20)
        )).ok()
        assertNull(meds.get(shortId)!!.refillEpochDay)
    }

    // ---------- 校验 ----------

    @Test
    fun 药名与字段长度校验() = runTest {
        val me = createSelf()
        assertErr(meds.save(draft(me, name = "  ")), FamilyError.InvalidMedName)
        assertErr(meds.save(draft(me, name = "x".repeat(31))),
            FamilyError.InvalidMedName)
        // 可选字段超长
        val base = draft(me)
        assertErr(meds.save(base.copy(dose = "x".repeat(21))),
            FamilyError.InvalidMedDose)
        assertErr(meds.save(base.copy(spec = "x".repeat(21))),
            FamilyError.InvalidMedSpec)
        assertErr(meds.save(base.copy(purpose = "x".repeat(31))),
            FamilyError.InvalidMedPurpose)
        assertErr(meds.save(base.copy(note = "x".repeat(201))),
            FamilyError.InvalidMedNote)
        // 前后空白裁剪后为空串视为未填（保存成功）
        val id = meds.save(base.copy(dose = "  ", spec = " ")).ok()
        val m = meds.get(id)!!
        assertNull(m.dose)
        assertNull(m.spec)
    }

    @Test
    fun 疗程天数与时刻校验() = runTest {
        val me = createSelf()
        assertErr(meds.save(draft(me, courseDays = 0)),
            FamilyError.InvalidMedCourseDays)
        assertErr(meds.save(draft(me, courseDays = 366)),
            FamilyError.InvalidMedCourseDays)
        assertErr(meds.save(draft(me, times = listOf(480, 600, 720, 840, 960))),
            FamilyError.InvalidMedTimes)
        assertErr(meds.save(draft(me, times = listOf(1500))),
            FamilyError.InvalidMedTimes)
        // 边界合法
        meds.save(draft(me, courseDays = 1)).ok()
        meds.save(draft(me, courseDays = 365)).ok()
        meds.save(draft(me, times = listOf(0, 1439))).ok()
    }

    @Test
    fun 人物须存在且在世() = runTest {
        val me = createSelf()
        assertErr(meds.save(draft("no-such-person")), FamilyError.PersonNotFound)
        // 已故人物不能记药
        val dead = repo.createRelative(
            PersonDraft(
                name = "奶奶", gender = Gender.FEMALE,
                livingStatus = LivingStatus.DECEASED
            ),
            me, io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.MOTHER
        ).ok()
        assertErr(meds.save(draft(dead)), FamilyError.MedPersonDeceased)
    }

    // ---------- 编辑 / 停用 / 删除 ----------

    @Test
    fun 编辑保留停用标记与创建时间_时刻整组替换() = runTest {
        val me = createSelf()
        val id = meds.save(draft(me, times = listOf(480))).ok()
        meds.markStopped(id, LocalDate.of(2026, 10, 1)).ok()
        val first = meds.get(id)!!
        // 编辑：换用法与时刻，stopped/createdAt 不动
        meds.save(draft(
            me, id = id, usage = MedUsage.LONG_TERM, courseDays = null,
            refill = LocalDate.of(2026, 11, 1), times = listOf(600, 1080),
            start = LocalDate.of(2026, 9, 1)
        )).ok()
        val m = meds.get(id)!!
        assertEquals(first.createdAt, m.createdAt)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), m.stoppedEpochDay)
        assertEquals(listOf(600, 1080), m.times)
        assertEquals(MedUsage.LONG_TERM, m.usage)
    }

    @Test
    fun 编辑不存在的用药报错_跨人物编辑报错() = runTest {
        val me = createSelf()
        val other = repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            me, io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.BROTHER
        ).ok()
        assertErr(meds.save(draft(me, id = "ghost")), FamilyError.MedicationNotFound)
        val id = meds.save(draft(me)).ok()
        // 把 personId 换成别人 → 拒绝
        assertErr(meds.save(draft(other, id = id)), FamilyError.PersonNotFound)
    }

    @Test
    fun 停用与重新开始() = runTest {
        val me = createSelf()
        val id = meds.save(draft(me)).ok()
        assertErr(meds.markStopped("ghost", LocalDate.now()),
            FamilyError.MedicationNotFound)
        meds.markStopped(id, LocalDate.of(2026, 10, 1)).ok()
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(),
            meds.get(id)!!.stoppedEpochDay)
        assertErr(meds.clearStopped("ghost"), FamilyError.MedicationNotFound)
        meds.clearStopped(id).ok()
        assertNull(meds.get(id)!!.stoppedEpochDay)
        // 开始日期不受影响
        assertEquals(LocalDate.of(2026, 9, 30).toEpochDay(),
            meds.get(id)!!.startEpochDay)
    }

    @Test
    fun 删除连带时刻() = runTest {
        val me = createSelf()
        val id = meds.save(draft(me, times = listOf(480, 720))).ok()
        meds.delete(id).ok()
        assertNull(meds.get(id))
        assertTrue(meds.getAll().isEmpty())
    }

    // ---------- 按人开关 ----------

    @Test
    fun 疗程续药开关_无行按默认开() = runTest {
        val me = createSelf()
        // 无 prefs 行 → 默认开（observePrefs 不含该人，调用方按 true 处理）
        assertTrue(meds.observePrefs().first().none { it.personId == me })
        meds.setCourseRefillEnabled(me, false).ok()
        val prefs = meds.observePrefs().first().first { it.personId == me }
        assertEquals(false, prefs.courseRefillEnabled)
        // 再开回
        meds.setCourseRefillEnabled(me, true).ok()
        assertEquals(true, meds.observePrefs().first()
            .first { it.personId == me }.courseRefillEnabled)
    }

    @Test
    fun observeForPerson只回该人() = runTest {
        val me = createSelf()
        val other = repo.createRelative(
            PersonDraft(name = "弟弟", gender = Gender.MALE),
            me, io.github.xiaohaoz1.jiarenxia.core.model.RelationRole.BROTHER
        ).ok()
        meds.save(draft(me, name = "药A")).ok()
        meds.save(draft(other, name = "药B")).ok()
        val mine = meds.observeForPerson(me).first()
        assertEquals(listOf("药A"), mine.map { it.name })
    }
}
