package io.github.xiaohaoz1.jiarenxia.core.model

/** 性别。 */
enum class Gender { MALE, FEMALE }

/** 在世状态。 */
enum class LivingStatus { ALIVE, DECEASED }

/**
 * 家庭成员。
 *
 * @param id 人物唯一 ID
 * @param name 姓名
 * @param gender 性别
 * @param avatarPath 头像在 App 私有目录中的相对路径，可为空
 * @param note 备注
 * @param livingStatus 在世状态
 * @param isSelf 是否为“我”本人
 * @param createdAt 创建时间戳
 * @param updatedAt 更新时间戳
 */
data class Person(
    val id: String,
    val name: String,
    val gender: Gender,
    val avatarPath: String? = null,
    val note: String? = null,
    val livingStatus: LivingStatus = LivingStatus.ALIVE,
    val isSelf: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
