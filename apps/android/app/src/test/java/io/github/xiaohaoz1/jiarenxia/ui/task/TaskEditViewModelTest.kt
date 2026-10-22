package io.github.xiaohaoz1.jiarenxia.ui.task

import androidx.lifecycle.SavedStateHandle
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 事项编辑：防重复提交的进行中/终态守卫与失败重试。 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskEditViewModelTest : RepoTestBase() {

    private lateinit var tasks: FamilyTaskRepositoryImpl
    private var seq = 0

    @org.junit.After
    fun tearDownMain() = Dispatchers.resetMain()

    @org.junit.Before
    fun setUpTaskVm() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ids = object : IdGenerator { override fun newId() = "id-" + (++seq) }
        val clock = object : TimeSource { override fun now() = now }
        tasks = FamilyTaskRepositoryImpl(db, ids, clock)
    }

    private fun vm(args: Map<String, Any> = emptyMap()) = TaskEditViewModel(
        SavedStateHandle(args),
        tasks,
        repo,
        ObserveFamilyOverview(repo, KinshipEngine())
    )

    @Test
    fun 保存进行中重复调用只写一次() = runTest {
        createSelf()
        val vm = vm()
        vm.ui.first { it.persons.isNotEmpty() }
        vm.setTitle("给妈妈体检")
        vm.save()
        // 第一次写入未完成（saving=true）时再次提交必须被挡住
        vm.save()
        vm.ui.first { it.saved }
        assertEquals(1, db.familyTaskDao().count())
    }

    @Test
    fun 保存成功后终态拒绝再次保存() = runTest {
        createSelf()
        val vm = vm()
        vm.ui.first { it.persons.isNotEmpty() }
        vm.setTitle("给妈妈体检")
        vm.save()
        vm.ui.first { it.saved }
        // 导航完成前再次触发：saving 已复位，但 saved 终态挡下第二笔
        vm.save()
        assertEquals(1, db.familyTaskDao().count())
    }

    @Test
    fun 分享文本预填标题与日期并可保存() = runTest {
        createSelf()
        val vm = vm(mapOf("shared" to "明天记得去幼儿园接小宝"))
        val st = vm.ui.first { it.persons.isNotEmpty() }
        assertEquals("去幼儿园接小宝", st.title)
        assertEquals(java.time.LocalDate.now().plusDays(1), st.anchorDate)
        assertNull(st.remindMinutes)
        // 与手工新建同一条保存路径
        vm.save()
        vm.ui.first { it.saved }
        assertEquals(1, db.familyTaskDao().count())
    }

    @Test
    fun 分享文本预填提醒时刻() = runTest {
        createSelf()
        val vm = vm(mapOf("shared" to "明天中午提醒我吃板面"))
        val st = vm.ui.first { it.persons.isNotEmpty() }
        assertEquals("吃板面", st.title)
        assertEquals(java.time.LocalDate.now().plusDays(1), st.anchorDate)
        assertEquals(12 * 60, st.remindMinutes)
        // 时刻随保存落库
        vm.save()
        vm.ui.first { it.saved }
        assertEquals(12 * 60, db.familyTaskDao().getAll().first().remindMinutes)
    }

    @Test
    fun 标题非法失败后重试成功() = runTest {
        createSelf()
        val vm = vm()
        vm.ui.first { it.persons.isNotEmpty() }
        vm.setTitle(" ")
        vm.save()
        vm.ui.first { it.error != null && !it.saving }
        // 失败保留输入语义：改正标题后重试成功
        vm.setTitle("给妈妈体检")
        vm.save()
        vm.ui.first { it.saved }
        assertEquals(1, db.familyTaskDao().count())
    }
}
