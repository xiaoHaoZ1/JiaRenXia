package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「打电话要问的事」仓库：增删改、10 条上限、文字校验、deleteIds、删人物级联。 */
class CallQuestionRepositoryTest : RepoTestBase() {

    private lateinit var questions: CallQuestionRepositoryImpl
    private var seq = 2000

    @org.junit.Before
    fun setUpQuestions() {
        questions = CallQuestionRepositoryImpl(
            db = db,
            ids = object : IdGenerator { override fun newId() = "q-" + (++seq) },
            clock = object : TimeSource { override fun now() = now }
        )
    }

    private var meId: String? = null

    private suspend fun me(): String =
        meId ?: createSelf().also { meId = it }

    private suspend fun person(
        name: String = "妈妈",
        role: RelationRole = RelationRole.MOTHER
    ): String {
        return repo.createRelative(
            PersonDraft(name = name, gender = Gender.FEMALE),
            me(), role
        ).ok()
    }

    @Test
    fun 增删改与观察() = runTest {
        val pid = person()
        val id = questions.add(pid, "  有没有按时吃药  ", keep = false).ok()
        // 文字 trim 后入库
        val list = questions.observeForPerson(pid).first()
        assertEquals(1, list.size)
        assertEquals("有没有按时吃药", list[0].text)
        assertEquals(false, list[0].keep)

        // 改文字与 keep（null 不改该项）
        questions.update(id, "改问的事", null).ok()
        questions.update(id, null, true).ok()
        val after = questions.getForPerson(pid).single()
        assertEquals("改问的事", after.text)
        assertEquals(true, after.keep)

        questions.delete(id)
        assertTrue(questions.getForPerson(pid).isEmpty())
    }

    @Test
    fun 文字校验_空与超长() = runTest {
        val pid = person()
        assertErr(questions.add(pid, "", false), FamilyError.CallQuestionTextInvalid)
        assertErr(questions.add(pid, "   ", false),
            FamilyError.CallQuestionTextInvalid)
        assertErr(questions.add(pid, "字".repeat(41), false),
            FamilyError.CallQuestionTextInvalid)
        // 40 字整可以
        questions.add(pid, "字".repeat(40), false).ok()
        // update 同样校验
        val id = questions.add(pid, "ok", false).ok()
        assertErr(questions.update(id, "", null),
            FamilyError.CallQuestionTextInvalid)
        assertErr(questions.update(id, "字".repeat(41), null),
            FamilyError.CallQuestionTextInvalid)
        assertErr(questions.update("不存在", "x", null),
            FamilyError.CallQuestionNotFound)
    }

    @Test
    fun 每人至多十条() = runTest {
        val pid = person()
        repeat(CallQuestionRepository.MAX_PER_PERSON) { i ->
            questions.add(pid, "第" + i + "条", false).ok()
        }
        assertErr(questions.add(pid, "第11条", false),
            FamilyError.CallQuestionLimitReached)
        // 另一人不受影响
        val other = person("爸爸", RelationRole.FATHER)
        questions.add(other, "爸爸的事", true).ok()
        assertEquals(CallQuestionRepository.MAX_PER_PERSON,
            questions.getForPerson(pid).size)
        assertEquals(1, questions.getForPerson(other).size)
    }

    @Test
    fun deleteIds批量删除() = runTest {
        val pid = person()
        val a = questions.add(pid, "问一", false).ok()
        val b = questions.add(pid, "问二", true).ok()
        val c = questions.add(pid, "问三", false).ok()
        questions.deleteIds(listOf(a, c))
        val left = questions.getForPerson(pid)
        assertEquals(listOf(b), left.map { it.id })
        // 空集不报错
        questions.deleteIds(emptyList())
    }

    @Test
    fun 删除人物级联删问() = runTest {
        val pid = person()
        questions.add(pid, "问一", false).ok()
        questions.add(pid, "问二", true).ok()
        repo.deletePerson(pid).ok()
        assertTrue(questions.getForPerson(pid).isEmpty())
        assertTrue(questions.observeAll().first().isEmpty())
    }

    @Test
    fun 人物不存在不能添加() = runTest {
        assertErr(questions.add("没人", "问", false), FamilyError.PersonNotFound)
    }
}
