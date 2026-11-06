package io.github.xiaohaoz1.jiarenxia.domain

/**
 * 家庭页「X家」标题（「家书」改版 2.22）：X 取“我”的姓。
 * 名字前两字命中复姓表时取两字，否则取首字；名字为空或首字不是汉字时回退「家庭」。
 */
object FamilyTitle {

    /** 常见复姓（百家姓复姓部分的常用子集）。 */
    private val COMPOUND_SURNAMES = setOf(
        "欧阳", "太史", "端木", "上官", "司马", "东方", "独孤", "南宫",
        "万俟", "闻人", "夏侯", "诸葛", "尉迟", "公羊", "赫连", "澹台",
        "皇甫", "宗政", "濮阳", "公冶", "太叔", "申屠", "公孙", "慕容",
        "仲孙", "钟离", "长孙", "宇文", "司徒", "鲜于", "司空", "闾丘",
        "子车", "亓官", "司寇", "巫马", "公西", "颛孙", "壤驷", "公良",
        "漆雕", "乐正", "宰父", "谷梁", "拓跋", "夹谷", "轩辕", "令狐",
        "段干", "百里", "呼延", "东郭", "南门", "羊舌", "微生", "公户",
        "公玉", "公仪", "梁丘", "公仲", "公上", "公门", "公山", "公坚",
        "左丘", "公伯", "西门", "公祖", "第五", "公乘", "贯丘", "公皙",
        "南荣", "东里", "东宫", "仲长", "子书", "子桑", "即墨", "达奚",
        "褚师"
    )

    private fun isHanzi(c: Char): Boolean = c.code in 0x4E00..0x9FFF

    /** “我”的名字 → 「X家」标题。 */
    fun of(selfName: String?): String {
        val name = selfName.orEmpty()
        val first = name.firstOrNull()
        if (first == null || !isHanzi(first)) return "家庭"
        val surname = if (name.length >= 2 &&
            name.substring(0, 2) in COMPOUND_SURNAMES
        ) name.substring(0, 2) else first.toString()
        return surname + "家"
    }
}
