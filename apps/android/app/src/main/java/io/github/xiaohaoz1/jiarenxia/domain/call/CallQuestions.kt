package io.github.xiaohaoz1.jiarenxia.domain.call

import io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity

/** 「打电话要问的事」一条（拨号前提醒 / 打完电话划掉用的最小负载）。 */
data class CallQuestionPrompt(val id: String, val text: String)

/**
 * 「打电话要问的事」的纯逻辑（FAMILY_AFFAIRS 26 第 7 批）：
 * 拨号前是否弹提醒、提醒列出哪些、打完电话删哪些。
 */
object CallQuestions {

    /** 该人有要问的事时拨号前先弹「打给…之前」对话框。 */
    fun shouldPrompt(questions: List<CallQuestionEntity>): Boolean =
        questions.isNotEmpty()

    /** 拨号前对话框逐条列出的文字（全部条目，保序）。 */
    fun dialogItems(questions: List<CallQuestionEntity>): List<String> =
        questions.map { it.text }

    /**
     * 「联系上了吗」划掉区与待删负载：只取「只问一次」（keep=false）的条目；
     * 「每次都问」的条目永远不出现在这里、不会被删。
     */
    fun onceQuestions(
        questions: List<CallQuestionEntity>
    ): List<CallQuestionPrompt> =
        questions.filter { !it.keep }.map { CallQuestionPrompt(it.id, it.text) }

    /**
     * 点「联系上了」时实际删除的 id 集合 = 只问一次条目 ∩ 勾选项；
     * 「没联系上」与关闭对话框时调用方不调用本函数（什么都不删）。
     */
    fun deletionIds(
        onceQuestions: List<CallQuestionPrompt>,
        checkedIds: Set<String>
    ): Set<String> =
        onceQuestions.mapTo(HashSet()) { it.id }.intersect(checkedIds)
}
