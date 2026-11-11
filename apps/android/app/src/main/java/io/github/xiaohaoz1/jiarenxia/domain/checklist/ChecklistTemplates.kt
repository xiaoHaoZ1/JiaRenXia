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

    // ---------- 出生后要办的事（POSTNATAL，第 3 批） ----------
    //
    // 出处（最后核验 2026-11-11）：
    // - 《户口登记条例》第七条（国家卫生健康委员会法规页
    //   https://www.nhc.gov.cn/wjw/flfg/202505/4ff7613b24f542d48f98fa5b540fd7f2.shtml ）
    // - 《疫苗管理法》第四十七条（中国人大网
    //   http://www.npc.gov.cn/npc/c2/c30834/201907/t20190702_299244.html ）
    // - 国发〔2024〕3 号把新生儿出生列为第一批“高效办成一件事”
    //   （https://www.gov.cn/zhengce/zhengceku/202401/content_6926256.htm ）
    // - 国家政务服务平台“高效办成一件事”重点事项服务专区
    //   https://gjzwfw.www.gov.cn/col/col1460/index.html

    /** 出生后要办的事清单标题（recordBirth 写入 checklists.title）。 */
    const val POSTNATAL_TITLE = "出生后要办的事"

    /** 宝宝条目的组名（每个宝宝一份，person_id = 宝宝）。 */
    const val POSTNATAL_GROUP_BABY = "宝宝"

    /** 孕妈条目的组名（每张清单一份，person_id = 孕妈）。 */
    const val POSTNATAL_GROUP_MOM = "妈妈"

    /** 「打开国家政务服务平台」打开的专区链接。 */
    const val POSTNATAL_PLATFORM_URL =
        "https://gjzwfw.www.gov.cn/col/col1460/index.html"

    /** 每个宝宝一份的条目（5 条，组 "宝宝"）。 */
    val POSTNATAL_BABY_ITEMS: List<TemplateItem> = listOf(
        TemplateItem(POSTNATAL_GROUP_BABY, "出生医学证明", "向分娩的医院或助产机构申领"),
        TemplateItem(
            POSTNATAL_GROUP_BABY, "申报出生登记（落户）",
            "《户口登记条例》第七条：婴儿出生后一个月以内申报出生登记"),
        TemplateItem(
            POSTNATAL_GROUP_BABY, "预防接种证",
            "《疫苗管理法》第四十七条：出生后一个月内到接种单位或出生医院办理"),
        TemplateItem(
            POSTNATAL_GROUP_BABY, "新生儿医保参保",
            "参保时间和缴费以当地医保部门通知为准"),
        TemplateItem(
            POSTNATAL_GROUP_BABY, "疫苗接种",
            "按接种单位的安排给这一条定下次接种日期")
    )

    /** 孕妈一份的条目（2 条，组 "妈妈"；仅新建清单时生成一次）。 */
    val POSTNATAL_MOM_ITEMS: List<TemplateItem> = listOf(
        TemplateItem(POSTNATAL_GROUP_MOM, "产后复查", "按医院安排的时间复查"),
        TemplateItem(
            POSTNATAL_GROUP_MOM, "生育津贴申领",
            "申领条件和时间以当地医保部门规定为准")
    )

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

    /** 模板中的组名（addItem 的合法分组集合；POSTNATAL 按“宝宝/妈妈”两组）。 */
    fun groupsOf(kind: io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind): List<String> =
        when (kind) {
            io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.BIRTH_BAG -> GROUP_ORDER
            io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.POSTNATAL ->
                listOf(POSTNATAL_GROUP_BABY, POSTNATAL_GROUP_MOM)
        }

    /** 按组分组的模板条目（组内保持模板顺序；POSTNATAL 为单份模板）。 */
    fun itemsOf(kind: io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind):
            Map<String, List<TemplateItem>> =
        when (kind) {
            io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.BIRTH_BAG ->
                BIRTH_BAG_ITEMS.groupBy { it.groupName }
            io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.POSTNATAL ->
                (POSTNATAL_BABY_ITEMS + POSTNATAL_MOM_ITEMS).groupBy { it.groupName }
        }
}
