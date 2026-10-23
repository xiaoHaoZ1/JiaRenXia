package io.github.xiaohaoz1.jiarenxia.domain.ceremony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeddingWordsTest {

    @Test
    fun joinSplit往返() {
        val p = "永结" to "同心"
        assertEquals("永结|同心", WeddingWords.join(p))
        assertEquals(p, WeddingWords.split("永结|同心"))
        assertEquals(WeddingWords.DEFAULT,
            WeddingWords.split(WeddingWords.join(WeddingWords.DEFAULT)))
    }

    @Test
    fun split非法回落默认() {
        assertEquals(WeddingWords.DEFAULT, WeddingWords.split(null))
        assertEquals(WeddingWords.DEFAULT, WeddingWords.split(""))
        // 无分隔符 / 一侧为空 / 超字数均非法
        assertEquals(WeddingWords.DEFAULT, WeddingWords.split("永结同心"))
        assertEquals(WeddingWords.DEFAULT, WeddingWords.split("永结|"))
        assertEquals(WeddingWords.DEFAULT, WeddingWords.split("|同心"))
        assertEquals(WeddingWords.DEFAULT, WeddingWords.split("百年好合久|长"))
        // 首尾空白容忍
        assertEquals("永结" to "同心", WeddingWords.split(" 永结 | 同心 "))
    }

    @Test
    fun sanitize清洗() {
        // 分隔符被剥掉、超长按 MAX_CHARS 截断
        assertEquals("永结" to "同心", WeddingWords.sanitize("永结|", "同心"))
        assertEquals("百年好合" to "长长久久",
            WeddingWords.sanitize("百年好合久", "长长久久久"))
        // 任一侧清洗后为空 → 非法
        assertNull(WeddingWords.sanitize("", "同心"))
        assertNull(WeddingWords.sanitize("  ", "同心"))
        assertNull(WeddingWords.sanitize("永结", ""))
    }

    @Test
    fun resolve读卡端为准() {
        val mine = "永结|同心"
        val peer = "白头|偕老"
        // 本机是读卡端 → 用本机祝词；是卡片端 → 用对方（读卡端）祝词
        assertEquals("永结" to "同心", WeddingWords.resolve(true, mine, peer))
        assertEquals("白头" to "偕老", WeddingWords.resolve(false, mine, peer))
        // 读卡端未带字段（旧实现空串）→ 默认
        assertEquals(WeddingWords.DEFAULT, WeddingWords.resolve(false, mine, ""))
        assertEquals(WeddingWords.DEFAULT, WeddingWords.resolve(false, mine, null))
    }

    @Test
    fun 预设均合法且默认在列() {
        assertTrue(WeddingWords.PRESETS.isNotEmpty())
        WeddingWords.PRESETS.forEach {
            assertTrue(WeddingWords.isValid(it))
        }
        assertEquals(WeddingWords.DEFAULT, WeddingWords.PRESETS.first())
    }

    @Test
    fun phrase拼读() {
        assertEquals("喜结连理", WeddingWords.phrase(WeddingWords.DEFAULT))
        assertEquals("永结同心", WeddingWords.phrase("永结" to "同心"))
    }
}
