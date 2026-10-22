package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.domain.pinyin.PinyinIndex

/**
 * 家庭内搜索：对姓名、显示称谓、结构关系做去空白、不分大小写的子串匹配，
 * 并在纯拉丁字母查询时匹配拼音（姓名全拼包含 / 声母前缀；称谓全拼或声母包含）。
 * 不排除任何人（包括“我”）；空白查询返回空列表。顺序保持传入顺序。
 */
object PersonSearch {

    fun filter(items: List<PersonOverview>, query: String): List<PersonOverview> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        // 纯 ASCII 字母查询（忽略空格）→ 同时匹配拼音
        val latin = q.replace(" ", "")
        val usePinyin = latin.isNotEmpty() && latin.all { it in 'a'..'z' }
        return items.filter {
            it.person.name.lowercase().contains(q) ||
                it.displayTitle.lowercase().contains(q) ||
                // 自定义称呼覆盖后，标准称谓仍应可搜索
                it.result.standardTitle.orEmpty().lowercase().contains(q) ||
                it.result.structuralDescription.lowercase().contains(q) ||
                (usePinyin && pinyinMatch(it, latin))
        }
    }

    /** 拼音匹配：姓名全拼包含 / 声母前缀 / 称谓全拼或声母包含。 */
    private fun pinyinMatch(item: PersonOverview, q: String): Boolean {
        val name = PinyinIndex.of(item.person.name)
        if (name.full.contains(q) || name.initials.startsWith(q)) return true
        val title = PinyinIndex.of(item.displayTitle)
        if (title.full.contains(q) || title.initials.contains(q)) return true
        // 标准称谓拼音（自定义称呼覆盖后兜底）
        val std = PinyinIndex.of(item.result.standardTitle.orEmpty())
        if (std.full.contains(q) || std.initials.contains(q)) return true
        val structural = PinyinIndex.of(item.result.structuralDescription)
        return structural.full.contains(q) || structural.initials.contains(q)
    }
}
