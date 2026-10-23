package io.github.xiaohaoz1.jiarenxia.domain.ceremony

/**
 * 碰一碰婚礼祝词（FAMILY_AFFAIRS 第 10 节）：合心动画左右两机各显示
 * 一个词，拼起来是一句四字祝词（默认“喜结|连理”）。
 *
 * 祝词是本机设置，随 HELLO 带给对方；两端设置不一致时**以读卡端为准**
 * ——两机各持有双方 HELLO 与自己的角色，各自算出同一结果。
 */
object WeddingWords {

    /** 每个词最多字数（超出在设置页输入时截断）。 */
    const val MAX_CHARS = 4

    /** 存储/线上表示用的分隔符：左词|右词。 */
    const val SEPARATOR = '|'

    val DEFAULT: Pair<String, String> = "喜结" to "连理"

    /** 预设方案（第一项即默认），“我的”页选择弹窗按此顺序展示。 */
    val PRESETS: List<Pair<String, String>> = listOf(
        DEFAULT,
        "永结" to "同心",
        "百年" to "好合",
        "佳偶" to "天成",
        "白头" to "偕老"
    )

    /** Pair → “左|右”存储串。 */
    fun join(words: Pair<String, String>): String =
        words.first + SEPARATOR + words.second

    /**
     * “左|右”串 → Pair；缺失/非法一律回落默认，保证动画上屏的
     * 两个词永远非空且等长可拼读。
     */
    fun split(raw: String?): Pair<String, String> {
        if (raw == null) return DEFAULT
        val pair = raw.substringBefore(SEPARATOR).trim() to
            raw.substringAfter(SEPARATOR, "").trim()
        return if (isValid(pair)) pair else DEFAULT
    }

    /**
     * 自定义输入清洗：去掉分隔符与首尾空白、截到 [MAX_CHARS]；
     * 任一侧为空返回 null（设置页据此禁用“确定”）。
     */
    fun sanitize(left: String, right: String): Pair<String, String>? {
        val pair = left.replace(SEPARATOR, ' ').trim().take(MAX_CHARS) to
            right.replace(SEPARATOR, ' ').trim().take(MAX_CHARS)
        return if (isValid(pair)) pair else null
    }

    fun isValid(words: Pair<String, String>): Boolean =
        words.first.isNotEmpty() && words.second.isNotEmpty() &&
            words.first.length <= MAX_CHARS &&
            words.second.length <= MAX_CHARS

    /**
     * 两端各取哪份祝词：读卡端为准。[isReader] 是本机角色，
     * [own]/[peer] 为双方 HELLO 里的原始串。
     */
    fun resolve(
        isReader: Boolean,
        own: String?,
        peer: String?
    ): Pair<String, String> = split(if (isReader) own else peer)

    /** 拼读展示（无障碍描述、设置页摘要）：喜结连理。 */
    fun phrase(words: Pair<String, String>): String =
        words.first + words.second
}
