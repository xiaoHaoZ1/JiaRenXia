package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「宝宝出生了」recordBirth：新建/已有宝宝、关系与生日补写、
 * BORN 与 ended、产前归档、POSTNATAL 清单、append、整体回滚。
 */
class BirthRecordTest : RepoTestBase() {

    private lateinit var pregnancies: PregnancyRepositoryImpl
    private lateinit var checklists: ChecklistRepositoryImpl
    private lateinit var tasks: FamilyTaskRepositoryImpl
    private var seq = 4000

    // 测试时钟 now≈2023-11-14：出生日期必须早于它才不会 InvalidBirthday
    private val today = LocalDate.of(2023, 12, 1)
    private val birthDate = today.minusDays(20) // 出生第 21 天
    private val due = today.plusDays(30)

    @org.junit.Before
    fun setUpBirth() {
        pregnancies = PregnancyRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "pg-" + (++seq) },
            clock = object : TimeSource { override fun now() = now },
            familyRepository = repo
        )
        checklists = ChecklistRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "cl-" + (++seq) },
            clock = object : TimeSource { override fun now() = now }
        )
        tasks = FamilyTaskRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "tid-" + (++seq) },
            clock = object : TimeSource { override fun now() = now }
        )
    }

    /** 我 + 妻子（孕妈）+ 丈夫（另一位父母候选）。 */
    private suspend fun family(): Triple<String, String, String> {
        val me = repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        val mom = repo.createRelative(
            PersonDraft("李雪", Gender.FEMALE), me, RelationRole.WIFE
        ).ok()
        return Triple(me, mom, me)
    }

    private suspend fun activePreg(momId: String): String =
        pregnancies.create(momId, due, null, today).ok()

    private suspend fun prenatalTask(momId: String, pregId: String): String =
        tasks.save(
            TaskDraft(
                personId = momId, kind = TaskKind.CHECKUP, title = "产检",
                anchorDate = today, repeat = TaskRepeat.NONE,
                offsets = setOf(0), pregnancyId = pregId
            )
        ).ok()

    private suspend fun postnatalItems(clId: String) =
        checklists.observe(clId).first()!!.items


    @Test
    fun `勾另一位父母_双亲关系都建`() = runTest {
        val (_, mom, me) = family()
        val pregId = activePreg(mom)
        val r = pregnancies.recordBirth(
            pregId,
            listOf(BirthBaby.New("豆豆", Gender.FEMALE, birthDate)),
            otherParentId = me, append = false, today = today
        ).ok()
        val parents = db.relationshipDao()
            .getParentRecords(r.babyIds[0]).map { it.sourcePersonId }.toSet()
        assertEquals(setOf(mom, me), parents)
        // 非配偶不能当另一位父母
        val stranger = repo.createRelative(
            PersonDraft("路人", Gender.MALE), me, RelationRole.BROTHER
        ).ok()
        val mom2 = repo.createRelative(
            PersonDraft("阿姨", Gender.FEMALE), me, RelationRole.SISTER
        ).ok()
        val preg2 = activePreg(mom2)
        assertErr(
            pregnancies.recordBirth(
                preg2,
                listOf(BirthBaby.New("豆豆", Gender.FEMALE, birthDate)),
                otherParentId = stranger, append = false, today = today
            ),
            FamilyError.BirthOtherParentInvalid
        )
    }

    @Test
    fun `已有子女_补关系不重复_已有生日不改`() = runTest {
        val (_, mom, _) = family()
        val pregId = activePreg(mom)
        // 孕妈已有一个女儿（无阳历生日）
        val girl = repo.createRelative(
            PersonDraft(
                "囡囡", Gender.FEMALE,
                birthdays = PersonBirthdays()
            ),
            mom, RelationRole.DAUGHTER
        ).ok()
        val r = pregnancies.recordBirth(
            pregId,
            listOf(BirthBaby.Existing(girl, birthDate)),
            null, false, today
        ).ok()
        assertEquals(listOf(girl), r.babyIds)
        // 关系已在 → 仍只有一条孕妈→囡囡
        val parents = db.relationshipDao()
            .getParentRecords(girl).map { it.sourcePersonId }
        assertEquals(1, parents.count { it == mom })
        // 无阳历生日 → 按传入日期补写
        val solar = db.importantDateDao().getByPersonAndType(
            girl, ImportantDateType.BIRTHDAY_SOLAR.name)
        assertNotNull(solar)
        assertEquals(birthDate.dayOfMonth, solar!!.day)
    }

    @Test
    fun `未连上孕妈的家人_补关系并可补生日`() = runTest {
        val (_, mom, me) = family()
        val pregId = activePreg(mom)
        // 儿子：已录在“我”名下、尚未连到孕妈 → 补孕妈→儿子关系
        val son = repo.createRelative(
            PersonDraft("豆豆", Gender.MALE), me, RelationRole.SON
        ).ok()
        val r = pregnancies.recordBirth(
            pregId,
            listOf(BirthBaby.Existing(son, birthDate, 480)),
            null, false, today
        ).ok()
        assertEquals(listOf(son), r.babyIds)
        val parents = db.relationshipDao()
            .getParentRecords(son).map { it.sourcePersonId }
        assertTrue(parents.contains(mom))
        assertTrue(parents.contains(me))
        assertNotNull(db.importantDateDao().getByPersonAndType(
            son, ImportantDateType.BIRTHDAY_SOLAR.name))
        // 清单条目挂在这个人身上
        val items = postnatalItems(r.checklistId)
        assertEquals(5, items.count { it.personId == son })
    }

    @Test
    fun `双胞胎_清单5x2加2条_ended取最早`() = runTest {
        val (_, mom, _) = family()
        val pregId = activePreg(mom)
        val r = pregnancies.recordBirth(
            pregId,
            listOf(
                BirthBaby.New("大宝", Gender.MALE, birthDate),
                BirthBaby.New("二宝", Gender.FEMALE, birthDate.minusDays(1), 60)
            ),
            null, false, today
        ).ok()
        assertEquals(2, r.babyIds.size)
        val p = pregnancies.get(pregId)!!
        assertEquals(PregnancyStatus.BORN, p.status)
        assertEquals(birthDate.minusDays(1), p.endedOn)
        val items = postnatalItems(r.checklistId)
        assertEquals(12, items.size) // 5×2 宝宝 + 2 妈妈
        assertEquals(10, items.count { it.groupName == "宝宝" })
        assertEquals(2, items.count { it.groupName == "妈妈" })
    }

    @Test
    fun `失败整体回滚_不留宝宝孕期仍ACTIVE`() = runTest {
        val (_, mom, _) = family()
        val pregId = activePreg(mom)
        val before = db.personDao().getAll().size
        // 第二个宝宝日期越界（366 天前）→ 整笔失败
        val r = pregnancies.recordBirth(
            pregId,
            listOf(
                BirthBaby.New("好宝宝", Gender.MALE, birthDate),
                BirthBaby.New("坏宝宝", Gender.FEMALE, today.minusDays(366))
            ),
            null, false, today
        )
        assertErr(r, FamilyError.PregnancyInvalidDate)
        // 无新增人物、孕期仍 ACTIVE、无 pregnancy_babies、无 POSTNATAL 清单
        assertEquals(before, db.personDao().getAll().size)
        val p = pregnancies.get(pregId)!!
        assertEquals(PregnancyStatus.ACTIVE, p.status)
        assertTrue(p.babyIds.isEmpty())
        assertTrue(db.checklistDao()
            .getForPregnancy(pregId, ChecklistKind.POSTNATAL.name) == null)
    }

    @Test
    fun `同一人不能关联两次孕期`() = runTest {
        val (_, mom, _) = family()
        val preg1 = activePreg(mom)
        val baby = repo.createRelative(
            PersonDraft("囡囡", Gender.FEMALE), mom, RelationRole.DAUGHTER
        ).ok()
        pregnancies.recordBirth(
            preg1, listOf(BirthBaby.Existing(baby, birthDate)),
            null, false, today
        ).ok()
        // 第二条孕期：preg1 已 BORN 不挡新 ACTIVE，直接再建一条
        val preg2 = pregnancies.create(mom, due, null, today).ok()
        assertErr(
            pregnancies.recordBirth(
                preg2, listOf(BirthBaby.Existing(baby)),
                null, false, today
            ),
            FamilyError.BirthBabyIneligible
        )
    }

    @Test
    fun `append补记_只补宝宝条目不动状态`() = runTest {
        val (me, mom, _) = family()
        val pregId = activePreg(mom)
        pregnancies.recordBirth(
            pregId,
            listOf(BirthBaby.New("大宝", Gender.MALE, birthDate)),
            null, false, today
        ).ok()
        val clId = db.checklistDao()
            .getForPregnancy(pregId, ChecklistKind.POSTNATAL.name)!!.id
        val countBefore = postnatalItems(clId).size

        // append：再补一个宝宝
        val r = pregnancies.recordBirth(
            pregId,
            listOf(BirthBaby.New("二宝", Gender.FEMALE, birthDate.plusDays(1))),
            null, append = true, today = today
        ).ok()
        assertEquals(1, r.babyIds.size)
        // 状态仍 BORN、ended 不变、清单只多 5 条宝宝条目
        val p = pregnancies.get(pregId)!!
        assertEquals(PregnancyStatus.BORN, p.status)
        assertEquals(birthDate, p.endedOn)
        val items = postnatalItems(clId)
        assertEquals(countBefore + 5, items.size)
        assertEquals(10, items.count { it.groupName == "宝宝" })
        assertEquals(2, items.count { it.groupName == "妈妈" })
        // append 时孕期须 BORN：对 ACTIVE 孕期 append 被拒
        val mom2 = repo.createRelative(
            PersonDraft("阿姨", Gender.FEMALE), me, RelationRole.SISTER
        ).ok()
        val active2 = activePreg(mom2)
        assertErr(
            pregnancies.recordBirth(
                active2,
                listOf(BirthBaby.New("三宝", Gender.MALE, birthDate)),
                null, append = true, today = today
            ),
            FamilyError.PregnancyNotFound
        )
        // 非 append 对 BORN 被拒
        assertErr(
            pregnancies.recordBirth(
                pregId,
                listOf(BirthBaby.New("四宝", Gender.MALE, birthDate)),
                null, append = false, today = today
            ),
            FamilyError.PregnancyNotFound
        )
    }

    @Test
    fun `校验_空列表超三人时刻越界`() = runTest {
        val (me, mom, _) = family()
        val pregId = activePreg(mom)
        assertErr(
            pregnancies.recordBirth(pregId, emptyList(), null, false, today),
            FamilyError.BirthNoBabies
        )
        assertErr(
            pregnancies.recordBirth(
                pregId,
                (1..4).map {
                    BirthBaby.New("宝$it", Gender.MALE, birthDate)
                },
                null, false, today
            ),
            FamilyError.BirthTooManyBabies
        )
        assertErr(
            pregnancies.recordBirth(
                pregId,
                listOf(BirthBaby.New("豆豆", Gender.MALE, birthDate, 1440)),
                null, false, today
            ),
            FamilyError.PregnancyInvalidDate
        )
        // “我”不能当已有宝宝
        assertErr(
            pregnancies.recordBirth(
                pregId,
                listOf(BirthBaby.Existing(me, birthDate)),
                null, false, today
            ),
            FamilyError.BirthBabyIneligible
        )
    }

    @Test
    fun `observeLinkedBabyIds_排除已关联者`() = runTest {
        val (_, mom, _) = family()
        val pregId = activePreg(mom)
        assertTrue(pregnancies.observeLinkedBabyIds().first().isEmpty())
        val r = pregnancies.recordBirth(
            pregId,
            listOf(BirthBaby.New("豆豆", Gender.MALE, birthDate)),
            null, false, today
        ).ok()
        assertEquals(
            setOf(r.babyIds[0]), pregnancies.observeLinkedBabyIds().first())
    }
}
