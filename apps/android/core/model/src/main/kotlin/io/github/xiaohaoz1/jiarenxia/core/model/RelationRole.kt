package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 新增人物时可选的关系选项（DEVELOPMENT_PLAN 2.3 的选项表）。
 *
 * label 为界面显示文案；gender 为该选项预填的性别（用户仍可修改）。
 */
enum class RelationRole(val label: String, val gender: Gender) {
    FATHER("父亲", Gender.MALE),
    MOTHER("母亲", Gender.FEMALE),
    SON("儿子", Gender.MALE),
    DAUGHTER("女儿", Gender.FEMALE),
    HUSBAND("丈夫", Gender.MALE),
    WIFE("妻子", Gender.FEMALE),
    ELDER_BROTHER("哥哥", Gender.MALE),
    ELDER_SISTER("姐姐", Gender.FEMALE),
    YOUNGER_BROTHER("弟弟", Gender.MALE),
    YOUNGER_SISTER("妹妹", Gender.FEMALE),
    BROTHER("兄弟（长幼未知）", Gender.MALE),
    SISTER("姐妹（长幼未知）", Gender.FEMALE);

    /**
     * 生成“person 是 anchor 的 [this]”对应的存储关系。
     * 父母子女事实统一存 PARENT；长幼信息仅出现在 SIBLING 上。
     */
    fun toRelationshipSpec(personId: String, anchorId: String): RelationshipSpec = when (this) {
        FATHER, MOTHER ->
            RelationshipSpec(personId, anchorId, RelationshipType.PARENT, null)
        SON, DAUGHTER ->
            RelationshipSpec(anchorId, personId, RelationshipType.PARENT, null)
        HUSBAND, WIFE ->
            RelationshipSpec(personId, anchorId, RelationshipType.SPOUSE, null)
        ELDER_BROTHER, ELDER_SISTER ->
            RelationshipSpec(personId, anchorId, RelationshipType.SIBLING, RelativeAgeOrder.OLDER)
        YOUNGER_BROTHER, YOUNGER_SISTER ->
            RelationshipSpec(personId, anchorId, RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER)
        BROTHER, SISTER ->
            RelationshipSpec(personId, anchorId, RelationshipType.SIBLING, null)
    }
}

/**
 * 待写入的关系描述（不含 id 与时间戳），由数据层转换为 [Relationship]。
 */
data class RelationshipSpec(
    val sourcePersonId: String,
    val targetPersonId: String,
    val type: RelationshipType,
    val relativeAgeOrder: RelativeAgeOrder?
)
