package io.github.xiaohaoz1.jiarenxia.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 长辈模式有效字号系数：长辈模式下不低于 1.3。 */
class FontFactorTest {

    @Test
    fun 普通模式用用户系数() {
        assertEquals(1.0f, effectiveFontFactor(1.0f, elder = false), 0.0001f)
        assertEquals(0.85f, effectiveFontFactor(0.85f, elder = false), 0.0001f)
    }

    @Test
    fun 长辈模式下限1点3() {
        assertEquals(1.3f, effectiveFontFactor(1.0f, elder = true), 0.0001f)
        assertEquals(1.3f, effectiveFontFactor(0.85f, elder = true), 0.0001f)
    }

    @Test
    fun 长辈模式用户系数更大时取用户系数() {
        assertEquals(1.5f, effectiveFontFactor(1.5f, elder = true), 0.0001f)
    }
}
