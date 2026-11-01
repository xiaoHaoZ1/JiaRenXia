package io.github.xiaohaoz1.jiarenxia.ui.person

import io.github.xiaohaoz1.jiarenxia.core.model.FactCategory
import io.github.xiaohaoz1.jiarenxia.core.model.PersonFact
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 「关于TA」分组与 chip 文案纯函数。 */
class AboutGroupsTest {
    private fun fact(
        id: String,
        category: FactCategory,
        label: String? = null,
        content: String = id,
        createdAt: Long = 1,
        day: LocalDate = LocalDate.of(2026, 9, 1)
    ) = PersonFact(id, "p", category, label, content, day, createdAt, createdAt)

    @Test
    fun 分组按固定顺序_尺码预设顺序_自定义按最早时间() {
        val groups = aboutGroups(
            listOf(
                fact("custom-b", FactCategory.CUSTOM, "常去的店", createdAt = 20),
                fact("shirt", FactCategory.SIZE, "上衣", createdAt = 1),
                fact("custom-a", FactCategory.CUSTOM, "喜欢的花", createdAt = 10),
                fact("shoe", FactCategory.SIZE, "鞋码", createdAt = 30),
                fact("food", FactCategory.FOOD_LIKE, content = "红烧肉"),
                fact("hat", FactCategory.SIZE, "帽子", createdAt = 0)
            )
        )
        assertEquals(listOf("尺码", "爱吃", "喜欢的花", "常去的店"), groups.map { it.first })
        assertEquals(listOf("shoe", "shirt", "hat"), groups.first().second.map { it.id })
    }

    @Test
    fun 空分组不出现() {
        assertEquals(listOf("爱好"), aboutGroups(listOf(fact("h", FactCategory.HOBBY))).map { it.first })
    }

    @Test
    fun chip文案与一年边界() {
        val today = LocalDate.of(2026, 10, 2)
        assertEquals(
            "鞋码 37",
            factChipText(fact("s", FactCategory.SIZE, "鞋码", "37", day = today.minusDays(365)), today)
        )
        assertEquals(
            "按摩椅 · 2025年",
            factChipText(fact("w", FactCategory.WISH, content = "按摩椅", day = today.minusDays(366)), today)
        )
    }
}
