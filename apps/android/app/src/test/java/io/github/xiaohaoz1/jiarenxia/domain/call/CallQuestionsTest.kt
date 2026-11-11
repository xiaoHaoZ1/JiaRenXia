package io.github.xiaohaoz1.jiarenxia.domain.call

import io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「打电话要问的事」纯逻辑：拨号前是否弹框、对话框条目、打完电话删除集合。 */
class CallQuestionsTest {

    private fun q(id: String, text: String = "问" + id, keep: Boolean = false) =
        CallQuestionEntity(
            id = id, personId = "p1", text = text, keep = keep,
            createdAt = 1, updatedAt = 1
        )

    @Test
    fun 无要问的事不弹框_有才弹() {
        assertFalse(CallQuestions.shouldPrompt(emptyList()))
        assertTrue(CallQuestions.shouldPrompt(listOf(q("a"))))
        // 全是「每次都问」也弹（条目要提醒）
        assertTrue(CallQuestions.shouldPrompt(listOf(q("a", keep = true))))
    }

    @Test
    fun 对话框条目_全部保序() {
        val qs = listOf(q("a", "吃药了吗"), q("b", "体检做了吗", keep = true))
        assertEquals(listOf("吃药了吗", "体检做了吗"),
            CallQuestions.dialogItems(qs))
    }

    @Test
    fun 只问一次条目_每次都问不进划掉区() {
        val qs = listOf(
            q("once1", "问一"),
            q("keep1", "问二", keep = true),
            q("once2", "问三")
        )
        val once = CallQuestions.onceQuestions(qs)
        assertEquals(listOf("once1", "once2"), once.map { it.id })
        assertEquals(listOf("问一", "问三"), once.map { it.text })
    }

    @Test
    fun 删除集合_只问一次交集勾选() {
        val once = listOf(
            CallQuestionPrompt("a", "问一"),
            CallQuestionPrompt("b", "问二"),
            CallQuestionPrompt("c", "问三")
        )
        // 全勾 → 全删
        assertEquals(setOf("a", "b", "c"),
            CallQuestions.deletionIds(once, setOf("a", "b", "c")))
        // 取消一条 → 只删勾着的
        assertEquals(setOf("a", "c"),
            CallQuestions.deletionIds(once, setOf("a", "c")))
        // 勾选集里混入非只问一次 id（如 keep 条目）→ 不影响结果
        assertEquals(setOf("a"),
            CallQuestions.deletionIds(once, setOf("a", "keep-x")))
        // 全不勾 → 不删
        assertTrue(CallQuestions.deletionIds(once, emptySet()).isEmpty())
    }

    @Test
    fun 每次都问条目永不出现在删除集合() {
        // onceQuestions 已过滤 keep=true；deletionIds 只看传入的 once 集合，
        // 因此 keep 条目即使被误传入勾选集也不会删除——由 onceQuestions 保证。
        val qs = listOf(q("k", "每次", keep = true), q("o", "一次"))
        val once = CallQuestions.onceQuestions(qs)
        assertEquals(listOf("o"), once.map { it.id })
        assertEquals(setOf("o"),
            CallQuestions.deletionIds(once, setOf("o", "k")))
    }
}
