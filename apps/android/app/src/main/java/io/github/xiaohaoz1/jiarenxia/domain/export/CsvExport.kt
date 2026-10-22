package io.github.xiaohaoz1.jiarenxia.domain.export


/**
 * 导出表格：家事提醒 CSV——UTF-8 带 BOM（Excel 中文不乱码）、
 * 字段按 RFC 4180 转义（引号、逗号、换行）。
 * （A6：人情/保单 CSV 随功能退出删除。）
 */
object CsvExport {

        const val BOM = "\uFEFF"
    private const val EOL = "\r\n"

    /**
     * RFC 4180：含逗号/引号/换行/回车的字段用双引号包起来，内部引号翻倍。
     * 以 = + - @ 开头的字段会被 Excel/WPS 当公式解析（CSV 公式注入），
     * 引号转义挡不住，这里在内容前加单引号前缀强制按文本处理。
     */
    fun escape(v: String?): String {
        if (v == null) return ""
        val safe = if (v.isNotEmpty() && v[0] in "=+-@") "'" + v else v
        val need = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (need) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun row(vararg cols: String?) =
        cols.joinToString(",") { escape(it) } + EOL

    private fun fen(f: Long?): String =
        if (f == null) "" else "%.2f".format(f / 100.0).trimEnd('0').trimEnd('.')

    /**
     * 家事提醒 CSV（行由 [ReminderExport.build] 生成，此处只管序列化）。
     * 空列表也输出表头，便于空范围导出。
     */
    fun remindersCsv(rows: List<ReminderExport.Row>): String = buildString {
        append(BOM)
        append(row("日期", "关联家人", "类型", "标题", "状态", "历法说明"))
        rows.forEach { r ->
            append(row(r.date.toString(), r.person, r.type, r.title, r.status,
                r.calendarNote))
        }
    }
}
