package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.FactCategory
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.Pregnancy
import io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.PersonDetail
import io.github.xiaohaoz1.jiarenxia.data.FakeContactRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.PersonFactRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.TableLunarCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 人物详情 ViewModel：“共同父母推荐 → 记为夫妻”成功后，
 * 双方已录子女中可能的共同子女要继续询问。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest : RepoTestBase() {

    private val viewModels = mutableListOf<PersonDetailViewModel>()
    private var seq = 9000
    private lateinit var tasks: FamilyTaskRepositoryImpl

    @org.junit.After
    fun tearDownMain() {
        // 先结束 ViewModel 的数据库订阅，再重置 Main、由基类关闭数据库。
        runBlocking {
            viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        }
        Dispatchers.resetMain()
    }

    @org.junit.Before
    fun setUpMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        tasks = FamilyTaskRepositoryImpl(
            db,
            object : IdGenerator { override fun newId() = "t-" + (++seq) },
            object : TimeSource { override fun now() = now })
    }

    private fun vm(personId: String): PersonDetailViewModel {
        return PersonDetailViewModel(
            SavedStateHandle(mapOf("personId" to personId)),
            repo,
            FakeContactRepository(),
            AppEvents(),
            tasks,
            KinshipEngine(),
            ObserveFamilyOverview(repo, KinshipEngine()),
            TableLunarCalendar(),
            PersonFactRepositoryImpl(
                db,
                object : IdGenerator { override fun newId() = "f-" + (++seq) },
                object : TimeSource { override fun now() = now }
            ),
            io.github.xiaohaoz1.jiarenxia.data.PregnancyRepositoryImpl(
                db,
                object : IdGenerator { override fun newId() = "g-" + (++seq) },
                object : TimeSource { override fun now() = now },
                repo
            ),
            io.github.xiaohaoz1.jiarenxia.data.ChecklistRepositoryImpl(
                db,
                object : IdGenerator { override fun newId() = "c-" + (++seq) },
                object : TimeSource { override fun now() = now }
            ),
            io.github.xiaohaoz1.jiarenxia.data.BabyCelebrationRepositoryImpl(db),
            io.github.xiaohaoz1.jiarenxia.data.MedicationRepositoryImpl(
                db,
                object : IdGenerator { override fun newId() = "m-" + (++seq) },
                object : TimeSource { override fun now() = now }
            ),
            io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository(),
            io.github.xiaohaoz1.jiarenxia.data.CallQuestionRepositoryImpl(
                db,
                object : IdGenerator { override fun newId() = "q-" + (++seq) },
                object : TimeSource { override fun now() = now }
            )
        ).also(viewModels::add)
    }

    @Test
    fun 关于TA新增并替换同名预设尺码() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            draft("妈妈", Gender.FEMALE), me, RelationRole.MOTHER
        ).ok()
        val model = vm(mom)
        model.ui.first { !it.loading }

        model.addFact(FactCategory.FOOD_LIKE, null, "红烧肉")
        model.ui.first { it.facts.any { fact -> fact.content == "红烧肉" } }
        model.addFact(FactCategory.SIZE, "鞋码", "37")
        model.ui.first { it.facts.any { fact -> fact.content == "37" } }
        model.addFact(FactCategory.SIZE, "鞋码", "38")
        val state = model.ui.first {
            it.facts.count { fact -> fact.category == FactCategory.SIZE } == 1 &&
                it.facts.any { fact -> fact.content == "38" }
        }
        assertEquals("红烧肉", state.facts.first { it.category == FactCategory.FOOD_LIKE }.content)
        assertEquals("38", state.facts.single { it.category == FactCategory.SIZE }.content)
    }

    @Test
    fun 已有孩子可确认缺失父亲_取消不写_确认后入口消失() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(draft("林青", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val dad = repo.createRelative(draft("周远", Gender.MALE), mom, RelationRole.HUSBAND).ok()
        val child = repo.createRelative(draft("周安", Gender.MALE), mom, RelationRole.SON).ok()
        val model = vm(child)
        val initial = model.ui.first { it.parentCompletion != null }
        assertEquals("父亲", initial.parentCompletion!!.parentLabel)
        assertNull(initial.commonChildrenAsk)
        model.askParentCompletion()
        assertEquals(dad, model.ui.value.commonChildrenAsk!!.spouseBId)
        model.dismissCommonChildren()
        assertTrue(db.relationshipDao().getParentRecords(child).none { it.sourcePersonId == dad })
        model.askParentCompletion()
        model.confirmCommonChildren()
        model.ui.first { it.parentCompletion == null && it.commonChildrenAsk == null }
        assertTrue(db.relationshipDao().getParentRecords(child).any { it.sourcePersonId == dad })
    }

    private fun draft(name: String, gender: Gender) = PersonDraft(name, gender)

    /** 王蓓蓓与杨超已共同录有女儿安安；小宝只有王蓓蓓一个已录父母。 */
    private suspend fun coParentsFixture(): Triple<String, String, String> {
        val me = createSelf()
        val mom = repo.createRelative(
            draft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val yang = repo.createRelative(
            draft("杨超", Gender.MALE), me, RelationRole.BROTHER).ok()
        val dau = repo.createRelative(
            draft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        repo.addRelationship(dau, yang, RelationRole.FATHER)
        val son = repo.createRelative(
            draft("小宝", Gender.MALE), mom, RelationRole.SON).ok()
        return Triple(mom, yang, son)
    }

    @Test
    fun 记为夫妻后询问共同子女_确认写入() = runTest {
        val (mom, yang, son) = coParentsFixture()
        val vm = vm(mom)
        vm.ui.first { !it.loading }

        vm.linkSpouseAndWedding(
            yang, BirthdayValue(PartialDate(2020, 1, 1), CalendarType.SOLAR))
        val st = vm.ui.first { it.commonChildrenAsk != null }
        // 只问小宝：安安已是两人共同子女
        assertEquals(listOf(son),
            st.commonChildrenAsk!!.candidates.map { it.child.id })

        vm.confirmCommonChildren()
        vm.ui.first { it.commonChildrenAsk == null }
        assertTrue(db.relationshipDao().getBetween(mom, yang)
            .any { it.type == RelationshipType.SPOUSE })
        assertTrue(db.relationshipDao().getParentRecords(son)
            .any { it.sourcePersonId == yang })
    }

    @Test
    fun 共同子女选都不是不写入() = runTest {
        val (mom, yang, son) = coParentsFixture()
        val vm = vm(mom)
        vm.ui.first { !it.loading }

        vm.linkSpouseAndWedding(
            yang, BirthdayValue(PartialDate(2020, 1, 1), CalendarType.SOLAR))
        vm.ui.first { it.commonChildrenAsk != null }

        vm.dismissCommonChildren()
        vm.ui.first { it.commonChildrenAsk == null }
        // 夫妻与纪念日已建立，杨超与小宝之间没有连线
        assertTrue(db.relationshipDao().getBetween(mom, yang)
            .any { it.type == RelationshipType.SPOUSE })
        assertTrue(db.relationshipDao().getParentRecords(son)
            .none { it.sourcePersonId == yang })
    }

    /** 手工录入的宝宝：阳历生日 [daysAgo] 天前，无孕期、无产后清单。 */
    private suspend fun babyFixture(daysAgo: Long): String {
        // 新生宝宝卡按真实今天（LocalDate.now）判定，而生日年份校验用仓库时钟：
        // 把测试时钟拨到真实当前日期，保证出生年份合法且年龄窗口一致。
        now = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val me = createSelf()
        val baby = repo.createRelative(
            draft("小宝", Gender.MALE), me, RelationRole.SON).ok()
        val b = java.time.LocalDate.now().minusDays(daysAgo)
        repo.setBirthday(
            baby, CalendarType.SOLAR,
            BirthdayValue(
                PartialDate(b.year, b.monthValue, b.dayOfMonth),
                CalendarType.SOLAR)
        ).ok()
        return baby
    }

    /** 直接写一张 POSTNATAL 清单 + [count] 条未办的宝宝条目。 */
    private suspend fun insertPostnatalItems(personId: String, count: Int) {
        db.checklistDao().insertAll(listOf(
            io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity(
                id = "cl-pn-" + personId, kind = "POSTNATAL",
                title = "出生后要办的事", personId = personId,
                pregnancyId = null, createdAt = 1, updatedAt = 1
            )
        ))
        db.checklistItemDao().insertAll((0 until count).map { i ->
            io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity(
                id = "pi-" + personId + "-" + i,
                checklistId = "cl-pn-" + personId,
                groupName = "宝宝", title = "事项" + i, note = null,
                checked = false, sortOrder = i, personId = personId,
                taskId = null, createdAt = 1, updatedAt = 1
            )
        })
    }

    @Test
    fun 新生宝宝卡_出生第18天无清单_卡片显示() = runTest {
        val baby = babyFixture(17)   // 出生第 18 天
        val model = vm(baby)
        val st = model.ui.first { !it.loading }
        assertTrue(st.newbornCard != null)
    }

    @Test
    fun 新生宝宝卡_百天后31天无未办条目_卡片不显示() = runTest {
        val baby = babyFixture(130)  // 百天日 = 今天 - 31 天，已出窗口
        val model = vm(baby)
        val st = model.ui.first { !it.loading }
        assertNull(st.newbornCard)
    }

    @Test
    fun 新生宝宝卡_百天后31天有未办条目_卡片仍显示() = runTest {
        val baby = babyFixture(130)
        insertPostnatalItems(baby, 2)
        val model = vm(baby)
        val st = model.ui.first {
            !it.loading && it.newbornCard != null
        }
        assertEquals("cl-pn-" + baby, st.newbornCard!!.postnatalChecklistId)
        assertEquals(0, st.newbornCard!!.postnatalDone)
        assertEquals(2, st.newbornCard!!.postnatalTotal)
    }

    @Test
    fun 已故人物有进行中孕期_孕期卡不显示() {
        // activePregnancy 由 detail.person.livingStatus + pregnancies 推出：
        // 已故 → null（卡片隐藏、数据仍在 pregnancies 里）
        val preg = Pregnancy(
            id = "pg1", personId = "p1",
            dueDate = java.time.LocalDate.of(2027, 1, 20), lmpDate = null,
            status = PregnancyStatus.ACTIVE, endedOn = null)
        fun state(status: LivingStatus) = PersonDetailUiState(
            detail = PersonDetail(
                person = Person("p1", "王阿姨", Gender.FEMALE,
                    livingStatus = status),
                phones = emptyList(), birthdays = emptyMap(),
                relationships = emptyList()),
            pregnancies = listOf(preg))
        assertNull(state(LivingStatus.DECEASED).activePregnancy)
        assertEquals(preg, state(LivingStatus.ALIVE).activePregnancy)
    }
}
