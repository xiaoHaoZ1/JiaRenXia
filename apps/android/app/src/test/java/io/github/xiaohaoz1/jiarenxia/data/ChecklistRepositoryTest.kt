package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.domain.checklist.ChecklistTemplates
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清单仓库：模板生成幂等、ACTIVE 校验、勾选/增删改、标题校验、
 * 删除孕期连带删待产包、删孕妈人物级联。
 */
class ChecklistRepositoryTest : RepoTestBase() {

    private lateinit var checklists: ChecklistRepositoryImpl
    private lateinit var pregnancies: PregnancyRepositoryImpl
    private var seq = 3000

    private val today = LocalDate.of(2026, 10, 2)
    private val due = today.plusDays(54)

    @org.junit.Before
    fun setUpCl() {
        checklists = ChecklistRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "cl-" + (++seq) },
            clock = object : TimeSource { override fun now() = now }
        )
        pregnancies = PregnancyRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "pg-" + (++seq) },
            clock = object : TimeSource { override fun now() = now },
            familyRepository = repo
        )
    }

    private suspend fun mom(): String {
        val me = repo.createSelf(PersonDraft("我", Gender.MALE)).ok()
        return repo.createRelative(
            PersonDraft("李雪", Gender.FEMALE), me, RelationRole.WIFE
        ).ok()
    }

    private suspend fun activePreg(personId: String): String =
        pregnancies.create(personId, due, null, today).ok()

    @Test
    fun `按模板创建_28条且幂等`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val id1 = checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        val id2 = checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        assertEquals(id1, id2)
        val model = checklists.get(id1)!!
        assertEquals("待产包", model.checklist.title)
        assertEquals(pid, model.checklist.personId)
        assertEquals(pregId, model.checklist.pregnancyId)
        assertEquals(ChecklistKind.BIRTH_BAG, model.checklist.kind)
        assertEquals(28, model.items.size)
        // 组序：证件资料 → 妈妈用品 → 宝宝用品；组内 sort_order 连续
        assertEquals(
            listOf("证件资料", "妈妈用品", "宝宝用品"),
            model.items.map { it.groupName }.distinct()
        )
        assertEquals(
            ChecklistTemplates.BIRTH_BAG_ITEMS.map { it.title },
            model.items.map { it.title }
        )
        // 条目 person_id 本批不写（第 3 批按宝宝用）
        assertTrue(model.items.all { it.personId == null })
        // 备注落库
        assertEquals("各地名称可能不同",
            model.items.first { it.title == "母子健康手册" }.note)
    }

    @Test
    fun `非ACTIVE孕期拒绝创建`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        pregnancies.end(pregId, today).ok()
        val r = checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId)
        assertTrue(r is FamilyResult.Failure)
        // 不存在的孕期同样失败
        assertTrue(
            checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, "nope")
                is FamilyResult.Failure
        )
    }

    @Test
    fun `勾选与进度`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val id = checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        val item = checklists.get(id)!!.items.first()
        checklists.setChecked(item.id, true).ok()
        val progress = checklists.observeForPregnancy(
            pregId, ChecklistKind.BIRTH_BAG).first()!!
        assertEquals(1, progress.checkedCount)
        assertEquals(28, progress.totalCount)
        assertEquals(id, progress.checklist.id)
        checklists.setChecked(item.id, false).ok()
        assertEquals(0,
            checklists.observeForPregnancy(pregId, ChecklistKind.BIRTH_BAG)
                .first()!!.checkedCount)
    }

    @Test
    fun `添加改名删除条目`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val id = checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        // 追加到组末尾
        val newId = checklists.addItem(id, "妈妈用品", "  哺乳枕  ").ok()
        val model = checklists.get(id)!!
        val added = model.items.first { it.id == newId }
        assertEquals("哺乳枕", added.title) // trim
        val momItems = model.items.filter { it.groupName == "妈妈用品" }
        assertEquals("哺乳枕", momItems.last().title)
        // 改名
        checklists.renameItem(newId, "哺乳靠垫").ok()
        assertEquals("哺乳靠垫",
            checklists.get(id)!!.items.first { it.id == newId }.title)
        // 删除（无确认，直接删）
        checklists.deleteItem(newId).ok()
        assertNull(checklists.get(id)!!.items.firstOrNull { it.id == newId })
        assertEquals(28, checklists.get(id)!!.items.size)
    }

    @Test
    fun `标题校验_空与超长失败`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val id = checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        val item = checklists.get(id)!!.items.first()
        assertTrue(checklists.renameItem(item.id, "   ") is FamilyResult.Failure)
        assertTrue(checklists.renameItem(item.id, "x".repeat(31))
            is FamilyResult.Failure)
        assertTrue(checklists.renameItem(item.id, "") is FamilyResult.Failure)
        assertTrue(checklists.addItem(id, "妈妈用品", " ") is FamilyResult.Failure)
        assertTrue(checklists.addItem(id, "妈妈用品", "y".repeat(31))
            is FamilyResult.Failure)
        // 30 字边界可过
        checklists.renameItem(item.id, "z".repeat(30)).ok()
        // 非法分组拒绝
        assertTrue(checklists.addItem(id, "不存在的组", "x")
            is FamilyResult.Failure)
    }

    @Test
    fun `删除孕期连带删待产包_其他kind保留`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val bagId = checklists.createFromTemplate(
            ChecklistKind.BIRTH_BAG, pregId).ok()
        // 手工塞一张其他 kind 的清单（模拟第 3 批数据）
        db.checklistDao().upsert(
            io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity(
                id = "other-1", kind = "OTHER", title = "其他清单",
                personId = pid, pregnancyId = pregId, createdAt = 1, updatedAt = 1))
        pregnancies.delete(pregId).ok()
        // 待产包没了
        assertNull(checklists.get(bagId))
        // 其他 kind 的清单还在，pregnancy_id 被 SET NULL
        val other = db.checklistDao().get("other-1")!!
        assertNull(other.pregnancyId)
        assertEquals(pid, other.personId)
    }

    @Test
    fun `删除清单与整表`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val id = checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        checklists.deleteChecklist(id).ok()
        assertNull(checklists.get(id))
        assertEquals(0, db.checklistItemDao().getAll().size)
    }

    @Test
    fun `删除孕妈人物_清单级联`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        repo.deletePerson(pid).ok()
        // persons 删除 → checklists.person_id CASCADE、pregnancies 也级联
        assertEquals(0, db.checklistDao().getAll().size)
        assertEquals(0, db.checklistItemDao().getAll().size)
    }

    @Test
    fun `observe按组序排序_手加条目在组末`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val id = checklists.createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        checklists.addItem(id, "证件资料", "其他证件").ok()
        val items = checklists.get(id)!!.items
        // “其他证件”排在证件资料组末尾（住院押金之后、妈妈用品之前）
        val idx = items.indexOfFirst { it.title == "其他证件" }
        assertEquals("住院押金", items[idx - 1].title)
        assertEquals("洗漱用品", items[idx + 1].title)
    }

    // ---------- 第 3 批：条目「定日期」转家庭事项 ----------

    @Test
    fun `定日期_建事项写回taskId_再定一次替换`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val clId = checklists
            .createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        val item = checklists.get(clId)!!.items.first { it.title == "医保卡" }

        // 首次定日期：新建 CUSTOM 事项，提前1天+当天，personId=条目归属
        val taskId = checklists.setItemTask(
            item.id, pid, "李雪的医保卡", LocalDate.of(2027, 3, 5)
        ).ok()
        val t = db.familyTaskDao().get(taskId)!!
        assertEquals("李雪的医保卡", t.title)
        assertEquals(pid, t.personId)
        assertEquals(
            LocalDate.of(2027, 3, 5).toEpochDay(), t.anchorEpochDay)
        assertEquals(false, t.archived)
        assertNull(t.pregnancyId)
        // 提前1天+当天两个偏移
        val offsets = db.familyTaskOffsetDao().getForTask(taskId)
            .map { it.daysBefore }.toSet()
        assertEquals(setOf(0, 1), offsets)
        // 条目 task_id 写回
        assertEquals(taskId, checklists.get(clId)!!.items
            .first { it.id == item.id }.taskId)

        // 再定一次日期：新事项替换引用，旧事项保留
        val taskId2 = checklists.setItemTask(
            item.id, pid, "李雪的医保卡", LocalDate.of(2027, 3, 8)
        ).ok()
        assertTrue(taskId2 != taskId)
        assertNotNull(db.familyTaskDao().get(taskId))
        assertEquals(taskId2, checklists.get(clId)!!.items
            .first { it.id == item.id }.taskId)
    }

    @Test
    fun `定日期_事项删除后条目回退普通态`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        val clId = checklists
            .createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        val item = checklists.get(clId)!!.items.first { it.title == "饭盒" }
        val taskId = checklists.setItemTask(
            item.id, pid, "我的饭盒", LocalDate.of(2027, 3, 5)
        ).ok()
        // 删除事项（不删条目）→ task_id 悬空；仓库层面条目仍带 taskId，
        // UI 侧按 task 找不到回退普通条目（此处验证事项确实没了）
        db.familyTaskDao().delete(taskId)
        assertNull(db.familyTaskDao().get(taskId))
        // 条目本身仍在
        assertTrue(checklists.get(clId)!!.items.any { it.id == item.id })
    }

    @Test
    fun `定日期_校验与POSTNATAL条目`() = runTest {
        val pid = mom()
        val pregId = activePreg(pid)
        // POSTNATAL 清单：经 recordBirth 生成（复用 BirthRecordTest 路径太重，
        // 直接验证 setItemTask 对任意条目通用 + personId 校验）
        val clId = checklists
            .createFromTemplate(ChecklistKind.BIRTH_BAG, pregId).ok()
        val item = checklists.get(clId)!!.items.first()
        // 空标题失败
        assertErr(
            checklists.setItemTask(item.id, null, "  ", LocalDate.of(2027, 3, 5)),
            FamilyError.InvalidTaskTitle
        )
        // 不存在的人物失败
        assertErr(
            checklists.setItemTask(item.id, "ghost", "x", LocalDate.of(2027, 3, 5)),
            FamilyError.PersonNotFound
        )
        // 条目不存在
        assertErr(
            checklists.setItemTask("nope", null, "x", LocalDate.of(2027, 3, 5)),
            FamilyError.ChecklistItemNotFound
        )
    }
}
