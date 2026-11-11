package io.github.xiaohaoz1.jiarenxia.domain.checklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 待产包模板：条目数、分组、组内顺序、文字与备注逐字校验。 */
class ChecklistTemplatesTest {

    @Test
    fun `共28条_三组5_16_7`() {
        val items = ChecklistTemplates.BIRTH_BAG_ITEMS
        assertEquals(28, items.size)
        val byGroup = items.groupBy { it.groupName }
        assertEquals(listOf("证件资料", "妈妈用品", "宝宝用品"), byGroup.keys.toList())
        assertEquals(5, byGroup["证件资料"]!!.size)
        assertEquals(16, byGroup["妈妈用品"]!!.size)
        assertEquals(7, byGroup["宝宝用品"]!!.size)
    }

    @Test
    fun `组内顺序与文字逐字`() {
        val items = ChecklistTemplates.BIRTH_BAG_ITEMS
        // 证件资料
        assertEquals(
            listOf("夫妻双方身份证", "医保卡", "母子健康手册", "产科病历和孕期检查资料", "住院押金"),
            items.filter { it.groupName == "证件资料" }.map { it.title }
        )
        // 妈妈用品
        assertEquals(
            listOf(
                "洗漱用品", "防滑拖鞋", "带刻度水杯、吸管杯", "手纸、纸巾", "饭盒",
                "前扣款纯棉睡衣", "哺乳胸罩", "纯棉内裤", "防溢乳垫", "腹带",
                "产妇卫生巾", "一次性护理垫", "便盆", "一次性马桶垫",
                "补充体力的食物", "手机和充电器"
            ),
            items.filter { it.groupName == "妈妈用品" }.map { it.title }
        )
        // 宝宝用品
        assertEquals(
            listOf(
                "包被、包单、帽子", "婴儿衣服", "新生儿纸尿裤", "婴儿湿纸巾、棉柔巾",
                "澡盆", "婴儿小梳子", "婴儿沐浴露、润肤露"
            ),
            items.filter { it.groupName == "宝宝用品" }.map { it.title }
        )
    }

    @Test
    fun `备注逐字且只在指定条目`() {
        val items = ChecklistTemplates.BIRTH_BAG_ITEMS
        val notes = items.filter { it.note != null }
            .associate { it.title to it.note }
        assertEquals(
            mapOf(
                "母子健康手册" to "各地名称可能不同",
                "住院押金" to "现金、银行卡或手机支付",
                "带刻度水杯、吸管杯" to "发动时和分娩后方便喝水",
                "前扣款纯棉睡衣" to "方便穿脱和哺乳",
                "腹带" to "可自备，也可在医院购买",
                "补充体力的食物" to "如鸡蛋、巧克力、牛奶",
                "包被、包单、帽子" to "不少医院可现场购买，先问分娩医院",
                "婴儿衣服" to "一般不能带进产房和手术室，回病房后再穿"
            ),
            notes
        )
    }

    @Test
    fun `组内标题不重复且都不超30字`() {
        ChecklistTemplates.BIRTH_BAG_ITEMS.groupBy { it.groupName }.forEach { (_, g) ->
            assertEquals(g.size, g.map { it.title }.toSet().size)
            g.forEach { assertTrue(it.title.length <= 30) }
        }
    }
}
