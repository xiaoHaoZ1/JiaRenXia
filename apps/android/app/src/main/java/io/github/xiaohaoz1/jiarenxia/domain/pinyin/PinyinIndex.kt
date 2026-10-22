package io.github.xiaohaoz1.jiarenxia.domain.pinyin

import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * 汉字拼音索引：为搜索提供"全拼"与"声母缩写"两种拉丁写法。
 *
 * 使用 `android.icu.text.Transliterator`（API 29+）：
 * - 首选 `Han-Latin/Names`（姓名读音，可正确转"单/解/仇"等多音姓）；
 * - 设备不支持时回退普通 `Han-Latin`。
 * 输出再经 `Latin-ASCII` 去声调、`Lower` 小写。
 *
 * API 26–28 没有该 API：拼音能力整体降级（中文子串匹配不受影响）。
 * 非汉字原样保留并转小写；结果按字符串缓存。
 */
object PinyinIndex {

    /** 一个字符串的拼音视图：全拼（音节相连）与声母缩写。 */
    data class Form(val full: String, val initials: String)

    /** 拼音能力是否可用（android.icu Transliterator 需 API 29+）。 */
    val available: Boolean = Build.VERSION.SDK_INT >= 29

    /** 首选的姓名读音变换器（Han-Latin/Names）是否成功创建。 */
    val namesAvailable: Boolean

    /** ICU 变换器；低版本设备为 null（此时拼音检索整体关闭）。 */
    private val transliterator: android.icu.text.Transliterator?

    init {
        var t: android.icu.text.Transliterator? = null
        var names = false
        if (Build.VERSION.SDK_INT >= 29) {
            t = createTransliterator("Han-Latin/Names; Latin-ASCII; Lower")
            if (t != null) names = true
            else t = createTransliterator("Han-Latin; Latin-ASCII; Lower")
        }
        transliterator = t
        namesAvailable = names
    }

    @androidx.annotation.RequiresApi(29)
    private fun createTransliterator(id: String): android.icu.text.Transliterator? =
        runCatching { android.icu.text.Transliterator.getInstance(id) }.getOrNull()

    @androidx.annotation.RequiresApi(29)
    private fun transliterate(text: String): String? =
        transliterator?.transliterate(text)

    private val cache = ConcurrentHashMap<String, Form>()

    /** 返回 [text] 的拼音形式；纯非汉字文本即"自身小写"；低版本只保留原文小写。 */
    fun of(text: String): Form = cache.getOrPut(text) { compute(text) }

    private fun compute(text: String): Form {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Form("", "")
        if (Build.VERSION.SDK_INT < 29) return Form(trimmed.lowercase(), "")
        val lat = transliterate(trimmed)?.trim() ?: return Form(trimmed.lowercase(), "")
        val syllables = lat.split(Regex("\\s+")).filter { it.isNotEmpty() }
        // 每个音节取首字母；非汉字部分会被转成其小写形式
        val initials = StringBuilder()
        for (sy in syllables) {
            val first = sy.firstOrNull { it.isLetterOrDigit() } ?: continue
            initials.append(first.lowercaseChar())
        }
        return Form(full = syllables.joinToString(""), initials = initials.toString())
    }
}
