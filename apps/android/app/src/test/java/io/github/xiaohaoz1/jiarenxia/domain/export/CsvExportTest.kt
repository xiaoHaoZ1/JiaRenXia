package io.github.xiaohaoz1.jiarenxia.domain.export

import org.junit.Assert.assertEquals
import org.junit.Test

/** 导出 CSV 公共规则（FAMILY_AFFAIRS 8.6）：BOM、转义、公式前缀。 */
class CsvExportTest {

    @Test
    fun 转义规则() {
        assertEquals("plain", CsvExport.escape("plain"))
        assertEquals("\"a,b\"", CsvExport.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvExport.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvExport.escape("line1\nline2"))
        assertEquals("", CsvExport.escape(null))
    }

    @Test
    fun 公式前缀防护() {
        // Excel/WPS 会把 = + - @ 开头的单元格当公式执行 → 前置单引号强制文本
        assertEquals("'=cmd", CsvExport.escape("=cmd"))
        assertEquals("'+SUM(A1:A2)", CsvExport.escape("+SUM(A1:A2)"))
        assertEquals("'-2+3", CsvExport.escape("-2+3"))
        assertEquals("'@x", CsvExport.escape("@x"))
        // 前缀与转义叠加：含逗号时整字段再包引号
        assertEquals("\"'=1,2\"", CsvExport.escape("=1,2"))
        // 空串与正常中文不受影响
        assertEquals("", CsvExport.escape(""))
        assertEquals("标题", CsvExport.escape("标题"))
        // 前导空格已天然失效，不再加前缀
        assertEquals(" =1", CsvExport.escape(" =1"))
    }
}
