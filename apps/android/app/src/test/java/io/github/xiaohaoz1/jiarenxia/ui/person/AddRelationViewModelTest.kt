package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyError
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.SharedParents
import io.github.xiaohaoz1.jiarenxia.domain.SiblingLineage
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 添加关系 ViewModel：与新增人物同一套血缘细分规则；
 * 称呼不推断血缘（细分/共同父母都需显式确认），
 * 写入失败要显示错误而不是“完成”。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddRelationViewModelTest : RepoTestBase() {

    @Test
    fun 已有中间人亲子关系也能补录堂表亲长幼_不重复写亲子关系() = runTest {
        val me = createSelf()
        val (_, uncle) = uncleOf(me)
        val cousin = repo.createRelative(draft("周亦辰", Gender.MALE), uncle, RelationRole.SON).ok()
        val vm = vm(me)
        vm.ui.first { !it.loading && it.candidates.any { c -> c.person.id == cousin } }
        vm.onPick(vm.ui.value.candidates.first { it.person.id == cousin })
        vm.onRole(RelationRole.ELDER_BROTHER)
        vm.onLineage(SiblingLineage.COUSIN)
        vm.onMiddle(uncle)
        vm.save()
        vm.ui.first { it.done || it.error != null }
        assertNull(vm.ui.value.error)
        assertTrue(vm.ui.value.done)
        assertEquals(1, db.relationshipDao().getBetween(uncle, cousin).size)
        val declared = db.relationshipDao().getBetween(cousin, me).single()
        assertEquals(RelationshipType.COUSIN, declared.type)
    }

    private val viewModels = mutableListOf<AddRelationViewModel>()

    @org.junit.After
    fun tearDownMain() {
        // 先结束 ViewModel 的数据库订阅与刷新，再重置 Main、由基类关闭数据库。
        runBlocking {
            viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        }
        Dispatchers.resetMain()
    }

    @org.junit.Before
    fun setUpMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private fun vm(
        personId: String,
        r: FamilyRepository = repo
    ): AddRelationViewModel =
        AddRelationViewModel(
            SavedStateHandle(mapOf("personId" to personId)),
            r,
            ObserveFamilyOverview(r, KinshipEngine())
        ).also(viewModels::add)

    /** 可注入失败的关系写包装：读全部走真实仓库。 */
    private class WriteFailRepo(
        private val delegate: FamilyRepository,
        var failAddRelationships: Boolean = false,
        var failAddRelationship: Boolean = false
    ) : FamilyRepository by delegate {
        override suspend fun addRelationships(
            specs: List<RelationshipSpec>,
            customTitleFor: String?,
            customTitle: String?
        ): FamilyResult<Unit> =
            if (failAddRelationships) FamilyResult.Failure(FamilyError.SaveFailed)
            else delegate.addRelationships(specs, customTitleFor, customTitle)

        override suspend fun addRelationship(
            personId: String, otherId: String, role: RelationRole
        ): FamilyResult<String> =
            if (failAddRelationship) FamilyResult.Failure(FamilyError.SaveFailed)
            else delegate.addRelationship(personId, otherId, role)
    }

    private fun draft(name: String, gender: Gender) = PersonDraft(name, gender)

    /** 妈的兄弟：存在但和“我”无直接关系，因此出现在候选列表。 */
    private suspend fun uncleOf(me: String): Pair<String, String> {
        val mom = repo.createRelative(draft("妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        val x = repo.createRelative(draft("大舅", Gender.MALE), mom, RelationRole.ELDER_BROTHER).ok()
        return mom to x
    }

    @Test
    fun 兄妹角色未选细分不可保存_确认后只写确认的关系() = runTest {
        val me = createSelf()
        val x = repo.createRelative(draft("待关联家人", Gender.MALE), emptyList()).ok()
        val vm = vm(me)
        vm.ui.first { !it.loading && it.candidates.isNotEmpty() }

        vm.onPick(vm.ui.value.candidates.first { it.person.id == x })
        vm.onRole(RelationRole.BROTHER)
        // 未选血缘细分：称谓不代表血缘，不能保存
        assertFalse(vm.canSave)
        vm.onLineage(SiblingLineage.REAL)
        // 未确认共同父母仍不能保存（不默认同父同母）
        assertFalse(vm.canSave)
        vm.onSharedParents(SharedParents.UNKNOWN)
        assertTrue(vm.canSave)
        vm.save()
        vm.ui.first { it.done }

        val rels = db.relationshipDao().getBetween(x, me)
        // 暂不确定：只有兄妹一条，不补任何父母边
        assertEquals(1, rels.count { it.type == RelationshipType.SIBLING })
        assertTrue(db.relationshipDao().getForPerson(x)
            .none { it.type == RelationshipType.PARENT && it.targetPersonId == x })
    }

    @Test
    fun 只记称呼与关系同事务写入() = runTest {
        val me = createSelf()
        val (_, x) = uncleOf(me)
        val vm = vm(me)
        vm.ui.first { !it.loading && it.candidates.isNotEmpty() }

        vm.onPick(vm.ui.value.candidates.first { it.person.id == x })
        vm.onRole(RelationRole.BROTHER)
        vm.onLineage(SiblingLineage.TITLE_ONLY)
        vm.onTitleOnlyText("大表哥")
        assertTrue(vm.canSave)
        vm.save()
        vm.ui.first { it.done }

        // 没有新增关系，只落了观察者=“我”的自定义称呼
        assertTrue(db.relationshipDao().getBetween(x, me).isEmpty())
        assertEquals("大表哥", repo.observeCustomTitles().first()[x])
    }

    @Test
    fun 写入失败显示错误不显示完成() = runTest {
        val me = createSelf()
        val (_, x) = uncleOf(me)
        val failing = WriteFailRepo(repo, failAddRelationships = true)
        val vm = vm(me, failing)
        vm.ui.first { !it.loading && it.candidates.isNotEmpty() }

        vm.onPick(vm.ui.value.candidates.first { it.person.id == x })
        vm.onRole(RelationRole.BROTHER)
        vm.onLineage(SiblingLineage.TITLE_ONLY)
        vm.onTitleOnlyText("大表哥")
        assertTrue(vm.canSave)
        vm.save()
        // 失败：显示错误、不显示完成、不写称呼
        vm.ui.first { it.error != null }
        assertFalse(vm.ui.value.done)
        assertFalse(vm.ui.value.saving)
        assertTrue(repo.observeCustomTitles().first().isEmpty())
    }

    @Test
    fun 切换对方后血缘确认全部失效() = runTest {
        val me = createSelf()
        val (mom, x) = uncleOf(me)
        val y = repo.createRelative(
            draft("外公", Gender.MALE), mom, RelationRole.FATHER).ok()
        val vm = vm(me)
        vm.ui.first { !it.loading && it.candidates.isNotEmpty() }

        vm.onPick(vm.ui.value.candidates.first { it.person.id == x })
        vm.onRole(RelationRole.BROTHER)
        vm.onLineage(SiblingLineage.REAL)
        vm.onSharedParents(SharedParents.UNKNOWN)
        assertTrue(vm.canSave)

        // 换对方：旧确认作废
        vm.onPick(vm.ui.value.candidates.first { it.person.id == y })
        assertNull(vm.ui.value.role)
        assertNull(vm.ui.value.lineage)
        assertNull(vm.ui.value.sharedParents)
        assertFalse(vm.ui.value.linkFather)
        assertFalse(vm.canSave)
    }

    @Test
    fun confirmSpouse失败显示错误保留询问() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            draft("妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        // 待关联人物无既定辈分，既可被选中，又能合法成为“我”的父亲及妈的配偶。
        val x = repo.createRelative(draft("甲", Gender.MALE), emptyList()).ok()
        val wrapped = WriteFailRepo(repo)
        val vm = vm(me, wrapped)
        vm.ui.first { !it.loading && it.candidates.isNotEmpty() }

        // 把 x 记为“我”的父亲：x 无配偶、我另一位父母“妈”无配偶 → 询问记为夫妻
        vm.onPick(vm.ui.value.candidates.first { it.person.id == x })
        vm.onRole(RelationRole.FATHER)
        vm.save()
        vm.ui.first { it.spouseAsk != null }
        val ask = vm.ui.value.spouseAsk!!
        assertEquals(x, ask.newPersonId)
        assertEquals(mom, ask.candidateId)

        // 记夫妻失败：显示错误、保留询问、不显示完成
        wrapped.failAddRelationship = true
        vm.confirmSpouse()
        vm.ui.first { it.error != null }
        assertNotNull(vm.ui.value.spouseAsk)
        assertFalse(vm.ui.value.done)

        // 恢复后重试成功：询问关闭并返回
        wrapped.failAddRelationship = false
        vm.confirmSpouse()
        vm.ui.first { it.done }
        assertNull(vm.ui.value.spouseAsk)
        assertTrue(db.relationshipDao().getBetween(x, mom)
            .any { it.type == RelationshipType.SPOUSE })
    }

    @Test
    fun 记丈夫后出现共同子女询问_勾选写入() = runTest {
        val me = createSelf()
        // 妹妹王蓓蓓（无配偶）有女儿安安和儿子小宝；
        // 杨超与她无直接关系，可被选中为丈夫
        val mom = repo.createRelative(
            draft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val dau = repo.createRelative(
            draft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val son = repo.createRelative(
            draft("小宝", Gender.MALE), mom, RelationRole.SON).ok()
        val yang = repo.createRelative(
            draft("杨超", Gender.MALE), me, RelationRole.BROTHER).ok()
        val vm = vm(mom)
        vm.ui.first { !it.loading && it.candidates.any { it.person.id == yang } }

        vm.onPick(vm.ui.value.candidates.first { it.person.id == yang })
        vm.onRole(RelationRole.HUSBAND)
        assertTrue(vm.canSave)
        vm.save()
        val st = vm.ui.first { it.commonChildrenAsk != null }
        assertNull(st.spouseAsk)
        val ask = st.commonChildrenAsk!!
        assertEquals(setOf(dau, son), ask.candidates.map { it.child.id }.toSet())
        assertTrue(ask.checkedIds.isEmpty())

        // 只勾选安安：杨超记为安安的父亲，小宝不写
        vm.toggleCommonChild(dau)
        vm.confirmCommonChildren()
        vm.ui.first { it.done }
        assertTrue(db.relationshipDao().getParentRecords(dau)
            .any { it.sourcePersonId == yang })
        assertTrue(db.relationshipDao().getParentRecords(son)
            .none { it.sourcePersonId == yang })
    }

    @Test
    fun 共同子女选都不是不写入() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            draft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val dau = repo.createRelative(
            draft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val yang = repo.createRelative(
            draft("杨超", Gender.MALE), me, RelationRole.BROTHER).ok()
        val vm = vm(mom)
        vm.ui.first { !it.loading && it.candidates.any { it.person.id == yang } }
        vm.onPick(vm.ui.value.candidates.first { it.person.id == yang })
        vm.onRole(RelationRole.HUSBAND)
        vm.save()
        vm.ui.first { it.commonChildrenAsk != null }

        vm.dismissCommonChildren()
        vm.ui.first { it.done }
        // 夫妻已建立，杨超与安安之间没有连线
        assertTrue(db.relationshipDao().getParentRecords(dau)
            .none { it.sourcePersonId == yang })
        assertTrue(db.relationshipDao().getBetween(mom, yang)
            .any { it.type == RelationshipType.SPOUSE })
    }

    @Test
    fun 记为夫妻确认后追问共同子女() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            draft("妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        val anan = repo.createRelative(
            draft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val son = repo.createRelative(
            draft("小宝", Gender.MALE), mom, RelationRole.SON).ok()
        // 杨超与安安无直接关系：记为安安的父亲 → 询问“妈是否记为夫妻”
        val yang = repo.createRelative(
            draft("杨超", Gender.MALE), emptyList()).ok()
        val vm = vm(anan)
        vm.ui.first { !it.loading && it.candidates.any { it.person.id == yang } }
        vm.onPick(vm.ui.value.candidates.first { it.person.id == yang })
        vm.onRole(RelationRole.FATHER)
        vm.save()
        vm.ui.first { it.spouseAsk != null }
        vm.confirmSpouse()
        // 追问共同子女：安安已认杨超为父不再问；我、小宝已录父母只有妈 → 候选
        val st = vm.ui.first { it.commonChildrenAsk != null }
        assertNull(st.spouseAsk)
        assertEquals(listOf(me, son),
            st.commonChildrenAsk!!.candidates.map { it.child.id })
        // 只勾选小宝：确定写入杨超为小宝的父亲；我不写
        vm.toggleCommonChild(son)
        vm.confirmCommonChildren()
        vm.ui.first { it.done }
        assertTrue(db.relationshipDao().getParentRecords(son)
            .any { it.sourcePersonId == yang })
        assertTrue(db.relationshipDao().getParentRecords(me)
            .none { it.sourcePersonId == yang })
    }

    @Test
    fun 先有夫妻再添加已有孩子_确认补父亲_写入失败可重试() = runTest {
        val me = createSelf()
        // 场景人物与“我”无既定关系：与“我”同辈的人不能给“我”的同胞当父母，
        // 会被辈分冲突校验拒绝，到不了要测的询问环节
        val mother = repo.createRelative(draft("姨妈", Gender.FEMALE), emptyList()).ok()
        val father = repo.createRelative(draft("姨夫", Gender.MALE), mother, RelationRole.HUSBAND).ok()
        val child = repo.createRelative(draft("小安", Gender.MALE), emptyList()).ok()
        // 已有的另一个单亲孩子不可被这次关系保存顺带询问。
        repo.createRelative(draft("小乐", Gender.FEMALE), mother, RelationRole.DAUGHTER).ok()
        val wrapped = WriteFailRepo(repo)
        val vm = vm(mother, wrapped)
        vm.ui.first { !it.loading && it.candidates.any { c -> c.person.id == child } }
        vm.onPick(vm.ui.value.candidates.first { it.person.id == child })
        vm.onRole(RelationRole.SON)
        vm.save()
        val ask = vm.ui.first { it.commonChildrenAsk != null }.commonChildrenAsk!!
        assertEquals(listOf(child), ask.candidates.map { it.child.id })
        assertEquals(father, ask.spouseBId)
        assertTrue(db.relationshipDao().getParentRecords(child).none { it.sourcePersonId == father })
        wrapped.failAddRelationships = true
        vm.confirmCommonChildren()
        vm.ui.first { it.error != null }
        assertNotNull(vm.ui.value.commonChildrenAsk)
        assertFalse(vm.ui.value.done)
        wrapped.failAddRelationships = false
        vm.confirmCommonChildren()
        vm.ui.first { it.done }
        assertTrue(db.relationshipDao().getParentRecords(child).any { it.sourcePersonId == father })
    }

    @Test
    fun 给已有孩子添加母亲_推荐其丈夫_取消不补父亲() = runTest {
        val me = createSelf()
        // 场景人物与“我”无既定关系，避免同辈路径触发辈分冲突校验
        val mother = repo.createRelative(draft("姨妈", Gender.FEMALE), emptyList()).ok()
        val father = repo.createRelative(draft("姨夫", Gender.MALE), mother, RelationRole.HUSBAND).ok()
        val child = repo.createRelative(draft("小安", Gender.MALE), emptyList()).ok()
        val vm = vm(child)
        vm.ui.first { !it.loading && it.candidates.any { c -> c.person.id == mother } }
        vm.onPick(vm.ui.value.candidates.first { it.person.id == mother })
        vm.onRole(RelationRole.MOTHER)
        vm.save()
        val ask = vm.ui.first { it.commonChildrenAsk != null }.commonChildrenAsk!!
        assertEquals(father, ask.spouseBId)
        assertNull(vm.ui.value.spouseAsk)
        vm.dismissCommonChildren()
        vm.ui.first { it.done }
        assertTrue(db.relationshipDao().getParentRecords(child).none { it.sourcePersonId == father })
    }

    @Test
    fun 孩子已有父亲_添加母亲后不问其丈夫() = runTest {
        val me = createSelf()
        // 场景人物与“我”无既定关系，避免同辈路径触发辈分冲突校验
        val mother = repo.createRelative(draft("姨妈", Gender.FEMALE), emptyList()).ok()
        repo.createRelative(draft("姨夫", Gender.MALE), mother, RelationRole.HUSBAND).ok()
        val child = repo.createRelative(draft("小安", Gender.MALE), emptyList()).ok()
        repo.createRelative(draft("爸爸", Gender.MALE), child, RelationRole.FATHER).ok()
        val vm = vm(child)
        vm.ui.first { !it.loading && it.candidates.any { c -> c.person.id == mother } }
        vm.onPick(vm.ui.value.candidates.first { it.person.id == mother })
        vm.onRole(RelationRole.MOTHER)
        vm.save()
        vm.ui.first { it.done }
        assertNull(vm.ui.value.commonChildrenAsk)
    }

    @Test
    fun 共同子女确认失败显示错误保留询问() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            draft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val dau = repo.createRelative(
            draft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val yang = repo.createRelative(
            draft("杨超", Gender.MALE), me, RelationRole.BROTHER).ok()
        val wrapped = WriteFailRepo(repo)
        val vm = vm(mom, wrapped)
        vm.ui.first { !it.loading && it.candidates.any { it.person.id == yang } }
        vm.onPick(vm.ui.value.candidates.first { it.person.id == yang })
        vm.onRole(RelationRole.HUSBAND)
        vm.save()
        vm.ui.first { it.commonChildrenAsk != null }

        // “确定”写入失败：显示错误、保留询问、不显示完成
        wrapped.failAddRelationships = true
        vm.confirmCommonChildren()
        vm.ui.first { it.error != null }
        assertNotNull(vm.ui.value.commonChildrenAsk)
        assertFalse(vm.ui.value.done)

        // 恢复后重试成功：询问关闭并返回
        wrapped.failAddRelationships = false
        vm.confirmCommonChildren()
        vm.ui.first { it.done }
        assertNull(vm.ui.value.commonChildrenAsk)
        assertTrue(db.relationshipDao().getParentRecords(dau)
            .any { it.sourcePersonId == yang })
    }
}
