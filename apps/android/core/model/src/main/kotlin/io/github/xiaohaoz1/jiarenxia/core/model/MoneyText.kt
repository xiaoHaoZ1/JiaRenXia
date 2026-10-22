package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 金额输入的解析与回显（金额统一以“分”存储）。
 *
 * 全程十进制整数运算，不经过 Double，也不依赖系统 Locale：
 * 输入“4.10”精确得到 410 分，回显的文本也能被再次解析。
 * 与数据层共用同一套规则：非负、最多两位小数、合计不超过 999,999,999.99 元。
 */
object MoneyText {

    /** 金额上限（分）：999,999,999.99 元。 */
    const val MAX_FEN: Long = 99_999_999_999L

    /** 解析结果：区分“没填”“填错了”和“合法金额”。 */
    sealed interface Result {
        /** 空白输入：视为未填写，由业务决定可空与否。 */
        data object Empty : Result

        /** 非空但非法：必须提示用户，不能静默按未填或零处理。 */
        data object Invalid : Result

        /** 合法金额（分）。 */
        data class Fen(val value: Long) : Result
    }

    /**
     * 解析“元”文本为“分”：接受 "680"、"680.5"、"680.50"、".5"、"5."；
     * 拒绝负号、多余字符、超过两位小数与超过上限的值。
     */
    fun parse(text: String): Result {
        if (text.isBlank()) return Result.Empty
        val (intPart, fracPart) = splitDecimal(text) ?: return Result.Invalid
        if (fracPart.length > 2) return Result.Invalid
        val yuan = if (intPart.isEmpty()) 0L
        else intPart.toLongOrNull() ?: return Result.Invalid
        if (yuan > 999_999_999L) return Result.Invalid
        val cents = if (fracPart.isEmpty()) 0L else fracPart.padEnd(2, '0').toLong()
        val fen = yuan * 100 + cents
        return if (fen <= MAX_FEN) Result.Fen(fen) else Result.Invalid
    }

    /**
     * 解析“万元”文本为“分”（保单保额输入单位为万元）。
     * 1 万元 = 10,000 元 = 1,000,000 分；小数最多 6 位（1 分 = 0.000001 万元），
     * 因此任意合法分值都能被回显文本精确还原。
     */
    fun parseWan(text: String): Result {
        if (text.isBlank()) return Result.Empty
        val (intPart, fracPart) = splitDecimal(text) ?: return Result.Invalid
        if (fracPart.length > 6) return Result.Invalid
        val wan = if (intPart.isEmpty()) 0L
        else intPart.toLongOrNull() ?: return Result.Invalid
        if (wan > 99_999L) return Result.Invalid
        val micro = if (fracPart.isEmpty()) 0L else fracPart.padEnd(6, '0').toLong()
        val fen = wan * 1_000_000L + micro
        return if (fen <= MAX_FEN) Result.Fen(fen) else Result.Invalid
    }

    /** “分”→“元”输入框文本：整元不带小数，如 "680"、"4.10"、"0.05"。 */
    fun formatYuanInput(fen: Long): String {
        val yuan = fen / 100
        val cents = fen % 100
        return if (cents == 0L) yuan.toString()
        else "$yuan.${cents / 10}${cents % 10}"
    }

    /** “分”→“万元”输入框文本（保额）：按需保留小数，不静默截断。 */
    fun formatWanInput(fen: Long): String {
        val wan = fen / 1_000_000L
        val micro = fen % 1_000_000L
        if (micro == 0L) return wan.toString()
        // 补零成 6 位小数再去尾零：500000 分 → "0.5"，505000 分 → "0.505"
        val digits = micro.toString().padStart(6, '0').trimEnd('0')
        return "$wan.$digits"
    }

    /** 拆分“整数.小数”两段；空白、非数字内容或多余小数点返回 null。 */
    private fun splitDecimal(text: String): Pair<String, String>? {
        val t = text.trim()
        if (t.isEmpty()) return null
        if (t.count { it == '.' } > 1) return null
        val intPart = t.substringBefore('.')
        val fracPart = t.substringAfter('.', "")
        if (intPart.isEmpty() && fracPart.isEmpty()) return null
        if (intPart.any { !it.isDigit() } || fracPart.any { !it.isDigit() }) return null
        return intPart to fracPart
    }
}
