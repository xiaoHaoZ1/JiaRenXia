package io.github.xiaohaoz1.jiarenxia.ui.person

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 人物编辑 ViewModel：血缘细分状态与“保存并继续添加”
 * （清空个人信息、保留关联人/称谓上下文，DEVELOPMENT_PLAN 2.14）。
 * 称呼不推断血缘：细分与共同父母都必须用户显式确认。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonEditViewModelTest : RepoTestBase() {

    private val viewModels = mutableListOf<PersonEditViewModel>()

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
        anchorId: String,
        roles: String,
        anchors: String = ""
    ): PersonEditViewModel =
        PersonEditViewModel(
            SavedStateHandle(
                mapOf(
                    "personId" to "", "anchorId" to anchorId,
                    "roles" to roles, "anchors" to anchors
                )
            ),
            repo,
            ObserveFamilyOverview(repo, KinshipEngine()),
            IcuLunarCalendar()
        ).also(viewModels::add)

    @Test
    fun 保存并继续添加_清空个人字段保留分支() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        vm.onName("王芳")
        // 未显式选择血缘细分前不能保存
        assertFalse(vm.canSave)
        vm.onLineage(SiblingLineage.REAL)
        // 共同父母也必须显式确认（不默认同父同母）
        assertFalse(vm.canSave)
        vm.onSharedParents(SharedParents.UNKNOWN)
        assertTrue(vm.canSave)

        vm.onSolarBirthday(BirthdayFormState(on = true, yearUnknown = true, month = 3, day = 15))
        vm.onLunarBirthday(BirthdayFormState(on = true, yearUnknown = true, month = 8, day = 15))
        vm.save(continueAdd = true)
        // 等保存完成（saving 回落且 savedNotice 出现）
        val st = vm.ui.first { it.savedNotice != null }
        assertEquals("已保存「王芳」", st.savedNotice)
        // 个人字段清空；关联人与称谓保留
        assertEquals("", st.name)
        assertTrue(st.phones.isEmpty())
        assertFalse(st.solarBirthday.on)
        assertFalse(st.lunarBirthday.on)
        assertFalse(st.birthdayConflict)
        assertFalse(st.conflictAcked)
        assertFalse(st.done)
        assertEquals(me, st.anchorId)
        assertEquals(RelationRole.SISTER, st.role)
        // 已确认且仍适用于同一关联人的血缘上下文保留
        assertEquals(SiblingLineage.REAL, st.lineage)
        assertEquals(SharedParents.UNKNOWN, st.sharedParents)
        // 称谓预填的性别也保留
        assertEquals(Gender.FEMALE, st.gender)
        // 第一人已落库
        assertEquals(2, db.personDao().getAll().size)

        // 连续录第二位：血缘上下文仍就绪，普通保存走原返回路径
        vm.onName("王兰")
        assertTrue(vm.canSave)
        vm.save()
        vm.ui.first { it.done }
        assertEquals(3, db.personDao().getAll().size)
        assertEquals(2, db.relationshipDao().getAll().size)
        val first = db.personDao().getAll().first { it.name == "王芳" }
        val second = db.personDao().getAll().first { it.name == "王兰" }
        assertEquals(2, db.importantDateDao().getBirthdays(first.id).size)
        assertTrue(db.importantDateDao().getBirthdays(second.id).isEmpty())
    }

    @Test
    fun 只记称呼不落关系_自定义称呼生效() = runTest {
        val me = createSelf()
        val dad = PersonDraft("爸", Gender.MALE)
            .let { repo.createRelative(it, me, RelationRole.FATHER) }
        val dadId = (dad as FamilyResult.Success).value
        val vm = vm(dadId, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        // 等血缘上下文加载完（anchorAsParent 由 refreshLineage 写入）
        vm.ui.first { it.anchorAsParent == Gender.MALE }

        // 关联人是父亲：角色标签应为“姑姑”系列
        assertEquals(Gender.MALE, vm.ui.value.anchorAsParent)

        vm.onName("杨秀")
        // 称谓不代表血缘：未选细分时不能保存
        assertFalse(vm.canSave)
        vm.onLineage(SiblingLineage.TITLE_ONLY)
        // 未手改时默认称呼为“姑姑”
        assertEquals("姑姑", vm.ui.value.titleOnlyText)
        assertTrue(vm.canSave)
        vm.save()
        vm.ui.first { it.done }

        val aunt = db.personDao().getAll().first { it.name == "杨秀" }
        assertTrue(db.relationshipDao().getForPerson(aunt.id).isEmpty())
        val titles = repo.observeCustomTitles().first()
        assertEquals("姑姑", titles[aunt.id])
    }

    @Test
    fun 多候选共同父母必须显式选择否则不可保存() = runTest {
        val me = createSelf()
        // 两个“父亲”候选（校验只限人数，不限性别）
        repo.createRelative(PersonDraft("父甲", Gender.MALE), me, RelationRole.FATHER)
        repo.createRelative(PersonDraft("父乙", Gender.MALE), me, RelationRole.FATHER)
        val vm = vm(me, RelationRole.BROTHER.name)
        vm.init(PersonEditMode.NEW)
        // 等血缘上下文加载完：两位父亲候选
        vm.ui.first { it.fatherCandidates.size == 2 }

        vm.onName("弟弟")
        // 未确认细分与共同父母，不能保存
        assertFalse(vm.canSave)
        vm.onLineage(SiblingLineage.REAL)
        vm.onSharedParents(SharedParents.BOTH)
        // 两位父亲候选 → 未选且未点“不确定”时不能保存
        assertEquals(2, vm.ui.value.fatherCandidates.size)
        assertFalse(vm.canSave)
        // 显式选“不确定”后可保存（不补共同父母）
        vm.onFatherUnsure()
        // 母亲侧无候选，天然就绪
        assertTrue(vm.canSave)
        vm.save()
        vm.ui.first { it.done }
        val bro = db.personDao().getAll().first { it.name == "弟弟" }
        // 只写兄妹一条：没有补任何共同父母
        assertEquals(1, db.relationshipDao().getForPerson(bro.id)
            .count { it.type == RelationshipType.SIBLING })
        assertTrue(db.relationshipDao().getForPerson(bro.id)
            .none { it.type == RelationshipType.PARENT })
    }

    @Test
    fun 唯一祖辈候选默认不补建_显式勾选才补() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER)
        val dadId = (dad as FamilyResult.Success).value
        val vm = vm(me, RelationRole.ELDER_BROTHER.name)
        vm.init(PersonEditMode.NEW)
        // 等血缘上下文加载完：唯一父亲候选被自动选中
        vm.ui.first { it.pickedFatherId != null }

        vm.onName("哥哥")
        vm.onLineage(SiblingLineage.REAL)
        vm.onSharedParents(SharedParents.BOTH)
        // 候选存在不等于用户确认：默认不勾选，不补建祖辈
        assertFalse(vm.ui.value.linkFather)
        assertTrue(vm.canSave)
        vm.save()
        vm.ui.first { it.done }
        var bro = db.personDao().getAll().first { it.name == "哥哥" }
        var rels = db.relationshipDao().getForPerson(bro.id)
        assertEquals(1, rels.size)
        assertTrue(rels.none { it.type == RelationshipType.PARENT })

        // 显式勾选“同时记为父亲”后，兄妹 + 共同父亲两条一次写入
        val vm2 = vm(me, RelationRole.ELDER_BROTHER.name)
        vm2.init(PersonEditMode.NEW)
        vm2.ui.first { it.pickedFatherId != null }
        vm2.onName("二哥")
        vm2.onLineage(SiblingLineage.REAL)
        vm2.onSharedParents(SharedParents.BOTH)
        vm2.onLinkFather(true)
        vm2.save()
        vm2.ui.first { it.done }
        bro = db.personDao().getAll().first { it.name == "二哥" }
        rels = db.relationshipDao().getForPerson(bro.id)
        assertEquals(2, rels.size)
        assertTrue(rels.any {
            it.type == RelationshipType.PARENT && it.sourcePersonId == dadId })
    }

    @Test
    fun 显式同父只补父亲_不补母亲() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER)
        val dadId = (dad as FamilyResult.Success).value
        val mom = repo.createRelative(
            PersonDraft("妈", Gender.FEMALE), me, RelationRole.MOTHER)
        (mom as FamilyResult.Success)
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.motherCandidates.isNotEmpty() }

        vm.onName("同父妹")
        vm.onLineage(SiblingLineage.REAL)
        vm.onSharedParents(SharedParents.FATHER)
        vm.onLinkFather(true)
        vm.save()
        vm.ui.first { it.done }
        val sis = db.personDao().getAll().first { it.name == "同父妹" }
        val parents = db.relationshipDao().getForPerson(sis.id)
            .filter { it.type == RelationshipType.PARENT }
        // 同父：只补父亲一侧，母亲候选存在也不自动补
        assertEquals(listOf(dadId), parents.map { it.sourcePersonId })
    }

    @Test
    fun 显式同母只补母亲_不补父亲() = runTest {
        val me = createSelf()
        repo.createRelative(PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER)
        val mom = repo.createRelative(
            PersonDraft("妈", Gender.FEMALE), me, RelationRole.MOTHER)
        val momId = (mom as FamilyResult.Success).value
        val vm = vm(me, RelationRole.BROTHER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.fatherCandidates.isNotEmpty() }

        vm.onName("同母弟")
        vm.onLineage(SiblingLineage.REAL)
        vm.onSharedParents(SharedParents.MOTHER)
        vm.onLinkMother(true)
        vm.save()
        vm.ui.first { it.done }
        val bro = db.personDao().getAll().first { it.name == "同母弟" }
        val parents = db.relationshipDao().getForPerson(bro.id)
            .filter { it.type == RelationshipType.PARENT }
        assertEquals(listOf(momId), parents.map { it.sourcePersonId })
    }

    @Test
    fun 切换关联人后血缘确认全部失效() = runTest {
        val me = createSelf()
        // 第二个可选关联人：我的兄弟（与我有直接关系但在候选列表中）
        val uncle = repo.createRelative(
            PersonDraft("大哥", Gender.MALE), me, RelationRole.ELDER_BROTHER)
        val uncleId = (uncle as FamilyResult.Success).value
        val vm = vm("", RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded && it.anchorCandidates.size == 2 }

        vm.onAnchor(me)
        vm.onName("甲")
        vm.onLineage(SiblingLineage.REAL)
        vm.onSharedParents(SharedParents.BOTH)
        assertTrue(vm.canSave)

        // 换关联人：旧确认作废，不能沿用
        vm.onAnchor(uncleId)
        assertNull(vm.ui.value.lineage)
        assertNull(vm.ui.value.sharedParents)
        assertNull(vm.ui.value.pickedFatherId)
        assertNull(vm.ui.value.middleId)
        assertFalse(vm.ui.value.linkFather)
        assertFalse(vm.canSave)
    }

    @Test
    fun 切换称谓后血缘确认全部失效() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name + "," + RelationRole.BROTHER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        vm.onName("某人")
        vm.onRole(RelationRole.SISTER)
        vm.onLineage(SiblingLineage.REAL)
        vm.onSharedParents(SharedParents.UNKNOWN)
        assertTrue(vm.canSave)

        // 换称谓：旧确认作废，需要重新选择
        vm.onRole(RelationRole.BROTHER)
        assertNull(vm.ui.value.lineage)
        assertNull(vm.ui.value.sharedParents)
        assertFalse(vm.canSave)
    }

    @Test
    fun 添加丈夫后询问共同子女_勾选确认写入() = runTest {
        val me = createSelf()
        // 王蓓蓓是我的妹妹、无配偶，已录女儿安安和儿子小宝
        val mom = repo.createRelative(
            PersonDraft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val dau = repo.createRelative(
            PersonDraft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val son = repo.createRelative(
            PersonDraft("小宝", Gender.MALE), mom, RelationRole.SON).ok()
        val vm = vm(mom, RelationRole.HUSBAND.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        vm.onName("杨超")
        assertTrue(vm.canSave)
        vm.save()
        // 配偶写入成功 → 共同子女询问（与记为夫妻询问不同时出现）
        val st = vm.ui.first { it.commonChildrenAsk != null }
        assertNull(st.spouseAsk)
        val ask = st.commonChildrenAsk!!
        assertEquals(setOf(dau, son), ask.candidates.map { it.child.id }.toSet())
        // 默认都不勾
        assertTrue(ask.checkedIds.isEmpty())

        // 只勾选安安：杨超记为安安的父亲，小宝不写，随后返回上页
        vm.toggleCommonChild(dau)
        vm.confirmCommonChildren()
        vm.ui.first { it.done }
        val yang = db.personDao().getAll().first { it.name == "杨超" }
        assertTrue(db.relationshipDao().getParentRecords(dau)
            .any { it.sourcePersonId == yang.id })
        assertTrue(db.relationshipDao().getParentRecords(son)
            .none { it.sourcePersonId == yang.id })
    }

    @Test
    fun 共同子女选都不是不写入关系() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            PersonDraft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val dau = repo.createRelative(
            PersonDraft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val vm = vm(mom, RelationRole.HUSBAND.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        vm.onName("杨超")
        vm.save()
        vm.ui.first { it.commonChildrenAsk != null }

        vm.dismissCommonChildren()
        vm.ui.first { it.done }
        val yang = db.personDao().getAll().first { it.name == "杨超" }
        // 夫妻已建立，但杨超与安安之间没有连线
        assertTrue(db.relationshipDao().getParentRecords(dau)
            .none { it.sourcePersonId == yang.id })
        assertTrue(db.relationshipDao().getBetween(mom, yang.id)
            .any { it.type == RelationshipType.SPOUSE })
    }

    @Test
    fun 记为夫妻确认后追问共同子女() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            PersonDraft("妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        val anan = repo.createRelative(
            PersonDraft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val vm = vm(me, RelationRole.FATHER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        vm.onName("爸")
        vm.save()
        // 妈无配偶 → 先问“是否记为夫妻”
        vm.ui.first { it.spouseAsk != null }
        vm.confirmSpouse()
        // 随后追问共同子女：安安已录父母只有妈 → 候选；两个对话框不同时出现
        val st = vm.ui.first { it.commonChildrenAsk != null }
        assertNull(st.spouseAsk)
        assertEquals(listOf(anan),
            st.commonChildrenAsk!!.candidates.map { it.child.id })
        // 单候选：确定即写入（无勾选步骤）
        vm.confirmCommonChildren()
        vm.ui.first { it.done }
        val dad = db.personDao().getAll().first { it.name == "爸" }
        assertTrue(db.relationshipDao().getParentRecords(anan)
            .any { it.sourcePersonId == dad.id })
    }

    @Test
    fun 保存并继续添加_共同子女询问关闭后进入下一位() = runTest {
        val me = createSelf()
        val mom = repo.createRelative(
            PersonDraft("王蓓蓓", Gender.FEMALE), me, RelationRole.SISTER).ok()
        repo.createRelative(
            PersonDraft("安安", Gender.FEMALE), mom, RelationRole.DAUGHTER).ok()
        val vm = vm(mom, RelationRole.HUSBAND.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        vm.onName("杨超")
        vm.save(continueAdd = true)
        // 询问未关闭前不进入下一位
        val st = vm.ui.first { it.commonChildrenAsk != null }
        assertFalse(st.done)

        vm.dismissCommonChildren()
        // 询问关闭 → 清空个人字段进入下一位，锚点/角色保留
        val next = vm.ui.first { it.savedNotice != null }
        assertEquals("已保存「杨超」", next.savedNotice)
        assertEquals("", next.name)
        assertNull(next.commonChildrenAsk)
        assertFalse(next.done)
        assertEquals(mom, next.anchorId)
        assertEquals(RelationRole.HUSBAND, next.role)
    }

    @Test
    fun 先有夫妻再新增子女_确认后补父亲_连续添加仍保留上下文() = runTest {
        val me = createSelf()
        val mother = repo.createRelative(PersonDraft("小姨", Gender.FEMALE), me, RelationRole.SISTER).ok()
        val father = repo.createRelative(PersonDraft("姨夫", Gender.MALE), mother, RelationRole.HUSBAND).ok()
        val vm = vm(mother, RelationRole.SON.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        vm.onName("小安")
        vm.save(continueAdd = true)
        val ask = vm.ui.first { it.commonChildrenAsk != null }.commonChildrenAsk!!
        val child = ask.candidates.single().child.id
        assertEquals("父亲", ask.parentLabel)
        assertFalse(vm.ui.value.done)
        assertTrue(db.relationshipDao().getParentRecords(child).none { it.sourcePersonId == father })
        vm.confirmCommonChildren()
        val next = vm.ui.first { it.savedNotice != null }
        assertEquals("已保存「小安」", next.savedNotice)
        assertEquals(mother, next.anchorId)
        assertEquals(RelationRole.SON, next.role)
        assertEquals("", next.name)
        assertTrue(db.relationshipDao().getParentRecords(child).any { it.sourcePersonId == father })

        vm.onName("小乐")
        vm.save(continueAdd = true)
        val second = vm.ui.first { it.commonChildrenAsk != null }.commonChildrenAsk!!.candidates.single().child.id
        vm.dismissCommonChildren()
        vm.ui.first { it.savedNotice == "已保存「小乐」" }
        assertTrue(db.relationshipDao().getParentRecords(second).none { it.sourcePersonId == father })
    }

    @Test
    fun 堂表亲经姨妈中间人新增也询问另一位父母_取消不写() = runTest {
        val me = createSelf()
        val mother = repo.createRelative(PersonDraft("妈妈", Gender.FEMALE), me, RelationRole.MOTHER).ok()
        val aunt = repo.createRelative(PersonDraft("姨妈", Gender.FEMALE), mother, RelationRole.SISTER).ok()
        val uncle = repo.createRelative(PersonDraft("姨夫", Gender.MALE), aunt, RelationRole.HUSBAND).ok()
        val vm = vm(me, RelationRole.BROTHER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        vm.onName("兵兵")
        vm.onLineage(SiblingLineage.COUSIN)
        vm.ui.first { it.middleCandidates.any { c -> c.person.id == aunt } }
        vm.onMiddle(aunt)
        assertTrue(vm.canSave)
        vm.save()
        val ask = vm.ui.first { it.commonChildrenAsk != null }.commonChildrenAsk!!
        assertEquals(uncle, ask.spouseBId)
        val child = ask.candidates.single().child.id
        assertTrue(db.relationshipDao().getParentRecords(child).any { it.sourcePersonId == aunt })
        vm.dismissCommonChildren()
        vm.ui.first { it.done }
        assertTrue(db.relationshipDao().getParentRecords(child).none { it.sourcePersonId == uncle })
    }

    // ---------- 电话录入改版（2026-10-13 交接文档 3.2） ----------

    private fun vmEdit(personId: String): PersonEditViewModel =
        PersonEditViewModel(
            SavedStateHandle(
                mapOf(
                    "personId" to personId, "anchorId" to "",
                    "roles" to "", "anchors" to ""
                )
            ),
            repo,
            ObserveFamilyOverview(repo, KinshipEngine()),
            IcuLunarCalendar()
        ).also(viewModels::add)

    @Test
    fun 手动填写优先复用空行_不再连击加行() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        vm.addPhone()
        vm.addPhone()
        // 空行被复用：不追加第二条，只定位第一条空行
        assertEquals(1, vm.ui.value.phones.size)
        assertTrue(vm.ui.value.focusPhoneKeyboard)
        val key = vm.ui.value.phones.single().rowKey
        assertEquals(key, vm.ui.value.focusPhoneKey)

        // 空行填了内容后再点才追加新行
        vm.updatePhone(key, vm.ui.value.phones.single().copy(number = "13800000001"))
        vm.addPhone()
        assertEquals(2, vm.ui.value.phones.size)
    }

    @Test
    fun 卡级联系人选择先填空行再追加() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        vm.addPhone()   // 一行空白新行
        vm.addPhoneFromContact("王芳", "138 0000 0002")
        // 空白行被填入而不是追加
        assertEquals(1, vm.ui.value.phones.size)
        assertEquals("138 0000 0002", vm.ui.value.phones.single().number)
        // NEW 模式姓名顺带带出（原规则）
        assertEquals("王芳", vm.ui.value.name)

        vm.addPhoneFromContact("李四", "13900000003")
        assertEquals(2, vm.ui.value.phones.size)
    }

    @Test
    fun 规范化重复的卡级选择不追加_就地提示并定位已有行() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        vm.addPhoneFromContact(null, "138 0000-0002")
        assertEquals(1, vm.ui.value.phones.size)
        // 同一号码换个写法再选：normalizePhone 后相同 → 不追加
        vm.addPhoneFromContact(null, "(138)00000002")
        val st = vm.ui.value
        assertEquals(1, st.phones.size)
        val row = st.phones.single()
        assertEquals("这个号码已经添加了", row.notice)
        assertEquals(row.rowKey, st.focusPhoneKey)
        // 定位行不强制弹键盘
        assertFalse(st.focusPhoneKeyboard)
    }

    @Test
    fun 行级联系人选择撞号_保留本行输入不改其它行() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        vm.addPhoneFromContact(null, "13800000001")
        vm.addPhoneFromContact(null, "13900000002")
        val st0 = vm.ui.value
        assertEquals(2, st0.phones.size)
        val second = st0.phones[1]
        // 第二行选到与第一行相同的号码：本行输入保留，就地提示指向第 1 条
        vm.pickPhoneForRow(second.rowKey, null, "138-0000-0001")
        val st = vm.ui.value
        assertEquals("13900000002", st.phones[1].number)
        assertEquals("这个号码已经在第 1 条了", st.phones[1].notice)
        assertEquals(second.rowKey, st.focusPhoneKey)
        // 第一行不受影响
        assertNull(st.phones[0].error)
        assertNull(st.phones[0].notice)
    }

    @Test
    fun 行级联系人选择正常写入本行() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        vm.addPhoneFromContact(null, "13800000001")
        vm.addPhone()
        val blank = vm.ui.value.phones.last()
        vm.pickPhoneForRow(blank.rowKey, null, "13700000003")
        val st = vm.ui.value
        assertEquals("13700000003", st.phones[1].number)
        assertEquals("13800000001", st.phones[0].number)
    }

    @Test
    fun 清空已有号码_保存就地报错并定位该行() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft(
                "爸", Gender.MALE,
                phones = listOf(io.github.xiaohaoz1.jiarenxia.data.PhoneDraft(
                    number = "13900001111"))
            ), me, RelationRole.FATHER).ok()
        val vm = vmEdit(dad)
        vm.init(PersonEditMode.EDIT)
        val st = vm.ui.first { it.loaded && it.phones.isNotEmpty() }
        val rowKey = st.phones.single().rowKey
        assertTrue(rowKey.startsWith("db:"))

        // 清空已有号码 → 保存被拒，行内错误 + 定位
        vm.updatePhone(rowKey, st.phones.single().copy(number = ""))
        vm.save()
        val after = vm.ui.value
        assertEquals("请填写号码，或删除这条电话", after.phones.single().error)
        assertEquals(rowKey, after.focusPhoneKey)
        assertFalse(after.done)
        // 数据库里的号码原样保留
        assertEquals("13900001111",
            db.phoneNumberDao().getForPerson(dad).single().number)
    }

    @Test
    fun 空白新行保存时忽略_带附加内容的新空行必须报错() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val vm = vmEdit(dad)
        vm.init(PersonEditMode.EDIT)
        vm.ui.first { it.loaded && it.name == "爸" }

        // 完全空白的新行：保存直接跳过，不拦也不写入
        vm.addPhone()
        vm.save()
        vm.ui.first { it.done }
        assertTrue(db.phoneNumberDao().getForPerson(dad).isEmpty())

        // 有附加内容的新空行（比如改过标签）：必须报错，不能静默丢
        val vm2 = vmEdit(dad)
        vm2.init(PersonEditMode.EDIT)
        vm2.ui.first { it.loaded }
        vm2.addPhone()
        val key = vm2.ui.value.phones.single().rowKey
        vm2.updatePhone(key, vm2.ui.value.phones.single().copy(
            label = io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel.WORK))
        vm2.save()
        val st = vm2.ui.value
        assertEquals("请填写号码，或删除这条电话", st.phones.single().error)
        assertEquals(key, st.focusPhoneKey)
        assertFalse(st.done)
    }

    @Test
    fun 两行同号保存报错_指出重复行并定位第一处() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val vm = vmEdit(dad)
        vm.init(PersonEditMode.EDIT)
        vm.ui.first { it.loaded }

        vm.addPhoneFromContact(null, "13800000001")
        vm.addPhoneFromContact(null, "13900000002")
        // 联系人路径有去重拦截，重复行由手动输入造成；保存时统一标出
        val second = vm.ui.value.phones[1]
        vm.updatePhone(second.rowKey, second.copy(number = "138-0000-0001"))
        vm.save()
        val st = vm.ui.value
        assertFalse(st.done)
        assertEquals("与第 2 条电话重复，请修改或删除一条", st.phones[0].error)
        assertEquals("与第 1 条电话重复，请修改或删除一条", st.phones[1].error)
        assertEquals(st.phones[0].rowKey, st.focusPhoneKey)

        // 把第一行改成不冲突的号码：两行错误自动清除（错误不借位）
        vm.updatePhone(st.phones[0].rowKey,
            st.phones[0].copy(number = "13500000005"))
        assertNull(vm.ui.value.phones[0].error)
        assertNull(vm.ui.value.phones[1].error)
    }

    @Test
    fun 删除错误行后行号重算不借位() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val vm = vmEdit(dad)
        vm.init(PersonEditMode.EDIT)
        vm.ui.first { it.loaded }

        vm.addPhoneFromContact(null, "13800000001")
        vm.addPhoneFromContact(null, "13900000002")
        vm.addPhoneFromContact(null, "13700000003")
        // 把第三行手动改成与第一行重复（手动输入不做行级去重，留给保存统一标）
        val third = vm.ui.value.phones[2]
        vm.updatePhone(third.rowKey, third.copy(number = "13800000001"))
        vm.save()
        val st = vm.ui.value
        assertEquals("与第 1 条电话重复，请修改或删除一条", st.phones[2].error)

        // 删掉第一行：原第三行的错误重算为“与第 1 条重复”→ 已无重复则清除
        vm.removePhone(st.phones[0].rowKey)
        val st2 = vm.ui.value
        assertEquals(2, st2.phones.size)
        assertNull(st2.phones[0].error)
        assertNull(st2.phones[1].error)
    }

    @Test
    fun 主号码互斥() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        vm.addPhoneFromContact(null, "13800000001")
        vm.addPhoneFromContact(null, "13900000002")
        val keys = vm.ui.value.phones.map { it.rowKey }

        vm.setPrimary(keys[0])
        assertTrue(vm.ui.value.phones[0].isPrimary)
        vm.setPrimary(keys[1])
        val st = vm.ui.value
        assertFalse(st.phones[0].isPrimary)
        assertTrue(st.phones[1].isPrimary)
    }

    @Test
    fun 电话附加字段保存完整保留() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val vm = vmEdit(dad)
        vm.init(PersonEditMode.EDIT)
        vm.ui.first { it.loaded }

        vm.addPhone()
        val key = vm.ui.value.phones.single().rowKey
        vm.updatePhone(key, vm.ui.value.phones.single().copy(
            number = "13800000001",
            label = io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel.WORK,
            status = io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus.INACTIVE,
            note = "工作日打"))
        vm.save()
        vm.ui.first { it.done }
        val row = db.phoneNumberDao().getForPerson(dad).single()
        assertEquals("13800000001", row.number)
        assertEquals(io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel.WORK, row.label)
        assertEquals(io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus.INACTIVE,
            row.status)
        assertEquals("工作日打", row.note)
        // 停用号码不落主号码标记
        assertFalse(row.isPrimary)
    }

    @Test
    fun 保存失败保留全部输入() = runTest {
        val me = createSelf()
        val dad = repo.createRelative(
            PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER).ok()
        val vm = vmEdit(dad)
        vm.init(PersonEditMode.EDIT)
        vm.ui.first { it.loaded && it.name == "爸" }
        vm.addPhoneFromContact(null, "13800000001")

        // 人物在编辑期间被删（比如另一入口删掉了）：保存失败，输入不能丢
        repo.deletePerson(dad)
        vm.save()
        val st = vm.ui.first { it.error != null }
        assertFalse(st.saving)
        assertFalse(st.done)
        assertEquals("爸", st.name)
        assertEquals("13800000001", st.phones.single().number)
        assertNull(st.phones.single().error)
    }

    @Test
    fun 保存中重复点击不再写一次() = runTest {
        val me = createSelf()
        // 门闩 repo：createRelative 挂起直到放行，让“保存中”窗口可控可见；
        // 不切 Main 调度器——viewModelScope 常驻协程若睡在虚拟调度器上，
        // tearDown 的 cancelAndJoin 会永远等不到派发
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowRepo = object : io.github.xiaohaoz1.jiarenxia.data.FamilyRepository by repo {
            override suspend fun createRelative(
                draft: PersonDraft,
                specs: List<io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec>,
                customTitle: String?
            ) = gate.await().let { repo.createRelative(draft, specs, customTitle) }
        }
        val vm = PersonEditViewModel(
            SavedStateHandle(
                mapOf(
                    "personId" to "", "anchorId" to me,
                    "roles" to RelationRole.FATHER.name, "anchors" to ""
                )
            ),
            slowRepo,
            ObserveFamilyOverview(repo, KinshipEngine()),
            IcuLunarCalendar()
        ).also(viewModels::add)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        vm.onName("爸")
        assertTrue(vm.canSave)

        vm.save()
        // 协程停在门闩上：saving 可见
        assertTrue(vm.ui.value.saving)
        // 保存中再点：直接忽略，不重复写
        vm.save()
        gate.complete(Unit)
        vm.ui.first { it.done }
        assertEquals(2, db.personDao().getAll().size)
        // 完成后再点同样被忽略
        vm.save()
        assertEquals(2, db.personDao().getAll().size)
    }

    @Test
    fun 缺项提示与定位() = runTest {
        val me = createSelf()
        // 两个可选称谓：性别不预填、称谓必选，缺项最完整
        val vm = vm(me, RelationRole.SISTER.name + "," + RelationRole.BROTHER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        // 姓名/性别/称谓都缺：列出全部缺项
        val missing = vm.missingRequirements()
        assertTrue(missing.contains("姓名"))
        assertTrue(missing.contains("性别"))
        assertTrue(missing.contains("称谓"))

        vm.onMissingClick()
        val st = vm.ui.value
        // 第一个缺项是姓名：定位姓名框（界面据此聚焦并唤起键盘）
        assertEquals(EditFocusTarget.NAME, st.focusTarget)
        assertTrue(PersonEditField.NAME in st.fieldErrors)
        assertTrue(PersonEditField.GENDER in st.fieldErrors)
        assertTrue(PersonEditField.ROLE in st.fieldErrors)
        vm.onFocusHandled()
        assertNull(vm.ui.value.focusTarget)

        // 姓名性别补齐后缺项只剩称谓，定位到称谓选项
        vm.onName("王芳")
        vm.onGender(Gender.FEMALE)
        vm.onMissingClick()
        assertEquals(EditFocusTarget.ROLE, vm.ui.value.focusTarget)
    }

    // ---------- 保存标错与自动定位（父亲试用反馈：缺项就地提示 + 第一个错误定位） ----------

    @Test
    fun 保存缺项_就地标错并定位第一个错误() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name + "," + RelationRole.BROTHER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        // 首次打开不提前报错
        assertFalse(vm.ui.value.saveAttempted)
        assertTrue(vm.ui.value.fieldErrors.isEmpty())

        // 姓名、性别、称谓都缺：保存被拒，三处全部标出，定位姓名
        vm.save()
        val st = vm.ui.value
        assertTrue(st.saveAttempted)
        assertEquals(
            setOf(PersonEditField.NAME, PersonEditField.GENDER, PersonEditField.ROLE),
            st.fieldErrors
        )
        assertEquals(EditFocusTarget.NAME, st.focusTarget)
        assertFalse(st.done)
    }

    @Test
    fun 修正后错误随有效输入消失_其余字段继续标错() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name + "," + RelationRole.BROTHER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        vm.save()

        // 填姓名：姓名错消失，性别/称谓仍标
        vm.onName("王芳")
        assertEquals(
            setOf(PersonEditField.GENDER, PersonEditField.ROLE),
            vm.ui.value.fieldErrors
        )
        // 性别由称谓带入：选称谓后性别与称谓同时补齐
        vm.onRole(RelationRole.SISTER)
        // 兄妹称谓还差血缘细分
        assertEquals(setOf(PersonEditField.LINEAGE), vm.ui.value.fieldErrors)

        // 再保存：定位到血缘区块，而不是已修好的姓名
        vm.save()
        assertEquals(EditFocusTarget.LINEAGE, vm.ui.value.focusTarget)
        // 补齐血缘即可保存（等默认称呼按所选称谓异步预填）
        vm.ui.first { it.titleOnlyText.isNotBlank() }
        vm.onLineage(SiblingLineage.TITLE_ONLY)
        assertTrue(vm.ui.value.fieldErrors.isEmpty())
        assertTrue(vm.canSave)
    }

    @Test
    fun 保存被拒_电话错误与字段错误同时标出() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.FATHER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }
        // 留一行改过标签的空电话行（必须报错的非空白行）
        vm.addPhone()
        val key = vm.ui.value.phones.single().rowKey
        vm.updatePhone(key, vm.ui.value.phones.single().copy(
            label = io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel.WORK))
        vm.onFocusHandled()

        // 姓名缺 + 电话行错：一次保存全部标出，定位仍按页面顺序到姓名
        vm.save()
        val st = vm.ui.value
        assertEquals(setOf(PersonEditField.NAME), st.fieldErrors)
        assertEquals("请填写号码，或删除这条电话", st.phones.single().error)
        assertEquals(EditFocusTarget.NAME, st.focusTarget)
    }

    @Test
    fun 关联人未选时定位关联卡() = runTest {
        val me = createSelf()
        // “其他亲属”入口：关联人可选、全部称谓可选；
        // 候选多于一人时锚点不自动选中
        repo.createRelative(
            PersonDraft("爸", Gender.MALE), me, RelationRole.FATHER)
        val vm = vm("", "")
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded && it.anchorCandidates.size == 2 }

        vm.onName("某人")
        vm.onGender(Gender.MALE)
        vm.save()
        val st = vm.ui.value
        assertEquals(setOf(PersonEditField.ANCHOR), st.fieldErrors)
        assertEquals(EditFocusTarget.ANCHOR, st.focusTarget)
        // 选定关联人后锚点错消失，称谓接着标出
        vm.onAnchor(me)
        assertEquals(setOf(PersonEditField.ROLE), vm.ui.value.fieldErrors)
    }

    @Test
    fun 保存并继续添加_错误状态重置() = runTest {
        val me = createSelf()
        val vm = vm(me, RelationRole.SISTER.name)
        vm.init(PersonEditMode.NEW)
        vm.ui.first { it.loaded }

        // 先被拒一次（姓名缺）
        vm.save()
        assertTrue(vm.ui.value.saveAttempted)
        // 填好后连续添加：下一位是全新表单，不带上一位的错误标记
        vm.onName("王芳")
        vm.onLineage(SiblingLineage.REAL)
        vm.onSharedParents(SharedParents.UNKNOWN)
        vm.save(continueAdd = true)
        val st = vm.ui.first { it.savedNotice != null }
        assertFalse(st.saveAttempted)
        assertTrue(st.fieldErrors.isEmpty())
        assertNull(st.focusTarget)
    }
}
