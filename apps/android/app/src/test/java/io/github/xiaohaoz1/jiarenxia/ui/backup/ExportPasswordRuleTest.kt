package io.github.xiaohaoz1.jiarenxia.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 导出密码规则提示（exportPasswordProblem）：纯函数，无需 Robolectric。 */
class ExportPasswordRuleTest {

    @Test
    fun 未输入_不提示() {
        assertNull(exportPasswordProblem("", ""))
    }

    @Test
    fun 不足6位_提示还差几位() {
        assertEquals("还差 5 位", exportPasswordProblem("a", ""))
        assertEquals("还差 1 位", exportPasswordProblem("12345", ""))
    }

    @Test
    fun 满6位且第二次是前缀_不提示() {
        assertNull(exportPasswordProblem("123456", ""))
        assertNull(exportPasswordProblem("123456", "123"))
        assertNull(exportPasswordProblem("123456", "123456"))
    }

    @Test
    fun 第二次输入偏离_提示不一致() {
        assertEquals("两次输入不一致", exportPasswordProblem("123456", "124"))
        assertEquals("两次输入不一致", exportPasswordProblem("123456", "1234567"))
    }

    @Test
    fun 位数不足优先于不一致() {
        assertEquals("还差 3 位", exportPasswordProblem("abc", "x"))
    }
}
