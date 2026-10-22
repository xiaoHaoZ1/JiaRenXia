package io.github.xiaohaoz1.jiarenxia.ui.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AvatarPalette 的确定性取色与已故灰阶。 */
class AvatarPaletteTest {

    @Test
    fun 同名同色_索引在色板内() {
        assertEquals(AvatarPalette.hueIndex("张三"), AvatarPalette.hueIndex("张三"))
        assertTrue(AvatarPalette.hueIndex("张三") in AvatarPalette.hues.indices)
        assertTrue(AvatarPalette.hueIndex("李四") in AvatarPalette.hues.indices)
    }

    @Test
    fun 渐变端点确定且深色端更暗() {
        val (a1, b1) = AvatarPalette.gradient("张三")
        val (a2, b2) = AvatarPalette.gradient("张三")
        assertEquals(a1, a2); assertEquals(b1, b2)
        // 深色端每个通道更暗
        assertTrue(b1.red < a1.red)
        // 上端来自 8 色色相表
        assertTrue(AvatarPalette.hues.contains(a1))
    }

    @Test
    fun 已故为灰阶() {
        val (t, b) = AvatarPalette.gradient("张三", deceased = true)
        assertTrue(t.red == t.green && t.green == t.blue)
        assertTrue(b.red == b.green && b.green == b.blue)
    }
}
