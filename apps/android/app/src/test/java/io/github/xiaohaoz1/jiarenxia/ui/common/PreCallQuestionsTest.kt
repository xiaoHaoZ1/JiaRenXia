package io.github.xiaohaoz1.jiarenxia.ui.common

import io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 拨号前三页共用的要问的事检查：有才弹框，确认时才带「只问一次」条目拨号。 */
class PreCallQuestionsTest {

    private fun q(id: String, text: String = "问" + id, keep: Boolean = false) =
        CallQuestionEntity(
            id = id, personId = "p1", text = text, keep = keep,
            createdAt = 1, updatedAt = 1
        )

    @Test
    fun 无要问的事_直接拨号不弹框() = runTest {
        var dialed: List<io.github.xiaohaoz1.jiarenxia.domain.call
            .CallQuestionPrompt>? = null
        val req = preCallRequest(
            questionsFor = { emptyList() },
            personId = "p1", label = "妈妈"
        ) { once -> dialed = once }
        assertNull(req)
        // 没问题也走 onDial（once 为空）——各页据此写待提示
        assertEquals(emptyList<io.github.xiaohaoz1.jiarenxia.domain.call
            .CallQuestionPrompt>(), dialed)
    }

    @Test
    fun 有要问的事_先弹框确认才拨号() = runTest {
        val qs = listOf(
            q("once1", "有没有按时吃药"),
            q("keep1", "血压量了吗", keep = true),
            q("once2", "复查去了吗")
        )
        var dialed: List<io.github.xiaohaoz1.jiarenxia.domain.call
            .CallQuestionPrompt>? = null
        val req = preCallRequest(
            questionsFor = { qs },
            personId = "p1", label = "妈妈"
        ) { once -> dialed = once }
        // 弹框请求：label 与全量快照（keep 也在列表里，供对话框展示）
        assertEquals("妈妈", req?.label)
        assertEquals(listOf("有没有按时吃药", "血压量了吗", "复查去了吗"),
            req?.questions?.map { it.text })
        // 「去拨号」之前不动 onDial
        assertNull(dialed)

        req!!.onConfirm()
        // 只把「只问一次」条目带给待提示；「每次都问」不进划掉区
        val got = checkNotNull(dialed)
        assertEquals(listOf("once1", "once2"), got.map { it.id })
        assertEquals(listOf("有没有按时吃药", "复查去了吗"),
            got.map { it.text })
    }

    @Test
    fun 全是每次都问_弹框但划掉区为空() = runTest {
        val qs = listOf(q("k1", "问一", keep = true))
        var dialed: List<io.github.xiaohaoz1.jiarenxia.domain.call
            .CallQuestionPrompt>? = null
        val req = preCallRequest(
            questionsFor = { qs },
            personId = "p1", label = "妈妈"
        ) { once -> dialed = once }
        assertTrue(req != null)   // 有条目仍提醒
        req!!.onConfirm()
        assertTrue(dialed!!.isEmpty())
    }
}
