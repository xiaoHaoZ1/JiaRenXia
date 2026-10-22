package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.AppEvents
import io.github.xiaohaoz1.jiarenxia.data.FakeContactRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
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
            IcuLunarCalendar()
        ).also(viewModels::add)
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
}
