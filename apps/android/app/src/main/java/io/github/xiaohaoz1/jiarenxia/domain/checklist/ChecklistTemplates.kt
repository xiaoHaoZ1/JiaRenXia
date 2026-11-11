package io.github.xiaohaoz1.jiarenxia.domain.checklist

/**
 * 待产包清单模板（FAMILY_AFFAIRS 26）。纯 JVM，不依赖 Android。
 * 组顺序与组内顺序即 sort_order；note 为条目备注（可空），不进标题。
 *
 * 出处（最后核验 2026-11-11）：
 * - 主出处：北京市卫生健康委员会《【妇产科普】临产要准备好哪些？详细攻略来了》，
 *   2024-07-01，https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202407/t20240701_3733742.html
 * - 「手机和充电器」出自同一机构《【妇产科普】预产期在寒冬？这份超实用待产包清单
 *   让你“暖”迎宝宝！》，2026-01-12，
 *   https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202601/t20260112_4422213.html
 */
object ChecklistTemplates {

    /** 发布机构名（UI 出处说明用）。 */
    const val BIRTH_BAG_PUBLISHER = "北京市卫生健康委员会"

    /** 主出处链接（UI「查看出处」打开）。 */
    const val BIRTH_BAG_SOURCE_URL =
        "https://wjw.beijing.gov.cn/bmfw_20143/jkzs/jksh/202407/t20240701_3733742.html"

    /** 待产包清单标题（createFromTemplate 写入 checklists.title）。 */
    const val BIRTH_BAG_TITLE = "待产包"

    /** 模板条目：组名 + 标题 + 备注。 */
    data class TemplateItem(
        val groupName: String,
        val title: String,
        val note: String? = null
    )

    /** 待产包模板，按组顺序、组内顺序排列（28 条：5 + 16 + 7）。 */
    val BIRTH_BAG_ITEMS: List<TemplateItem> = listOf(
        // —— 证件资料（5）——
        TemplateItem("证件资料", "夫妻双方身份证"),
        TemplateItem("证件资料", "医保卡"),
        TemplateItem("证件资料", "母子健康手册", "各地名称可能不同"),
        TemplateItem("证件资料", "产科病历和孕期检查资料"),
        TemplateItem("证件资料", "住院押金", "现金、银行卡或手机支付"),
        // —— 妈妈用品（16）——
        TemplateItem("妈妈用品", "洗漱用品"),
        TemplateItem("妈妈用品", "防滑拖鞋"),
        TemplateItem("妈妈用品", "带刻度水杯、吸管杯", "发动时和分娩后方便喝水"),
        TemplateItem("妈妈用品", "手纸、纸巾"),
        TemplateItem("妈妈用品", "饭盒"),
        TemplateItem("妈妈用品", "前扣款纯棉睡衣", "方便穿脱和哺乳"),
        TemplateItem("妈妈用品", "哺乳胸罩"),
        TemplateItem("妈妈用品", "纯棉内裤"),
        TemplateItem("妈妈用品", "防溢乳垫"),
        TemplateItem("妈妈用品", "腹带", "可自备，也可在医院购买"),
        TemplateItem("妈妈用品", "产妇卫生巾"),
        TemplateItem("妈妈用品", "一次性护理垫"),
        TemplateItem("妈妈用品", "便盆"),
        TemplateItem("妈妈用品", "一次性马桶垫"),
        TemplateItem("妈妈用品", "补充体力的食物", "如鸡蛋、巧克力、牛奶"),
        TemplateItem("妈妈用品", "手机和充电器"),
        // —— 宝宝用品（7）——
        TemplateItem("宝宝用品", "包被、包单、帽子", "不少医院可现场购买，先问分娩医院"),
        TemplateItem("宝宝用品", "婴儿衣服", "一般不能带进产房和手术室，回病房后再穿"),
        TemplateItem("宝宝用品", "新生儿纸尿裤"),
        TemplateItem("宝宝用品", "婴儿湿纸巾、棉柔巾"),
        TemplateItem("宝宝用品", "澡盆"),
        TemplateItem("宝宝用品", "婴儿小梳子"),
        TemplateItem("宝宝用品", "婴儿沐浴露、润肤露")
    )

    /** 组顺序（UI 分组渲染与排序用）。 */
    val GROUP_ORDER: List<String> = listOf("证件资料", "妈妈用品", "宝宝用品")

    /** 模板中的组名（addItem 的合法分组集合）。 */
    fun groupsOf(kind: io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind): List<String> =
        when (kind) {
            io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.BIRTH_BAG -> GROUP_ORDER
        }

    /** 按组分组的模板条目（组内保持模板顺序）。 */
    fun itemsOf(kind: io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind):
            Map<String, List<TemplateItem>> =
        when (kind) {
            io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.BIRTH_BAG ->
                BIRTH_BAG_ITEMS.groupBy { it.groupName }
        }
}
