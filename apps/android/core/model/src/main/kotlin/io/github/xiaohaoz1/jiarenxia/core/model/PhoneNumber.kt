package io.github.xiaohaoz1.jiarenxia.core.model

/** 手机号标签。 */
enum class PhoneLabel { MOBILE, WORK, HOME, BACKUP, OTHER }

/** 手机号状态。停用的号码不再进入拨号入口，但保留记录。 */
enum class PhoneStatus { ACTIVE, INACTIVE }

/**
 * 人物手机号。
 *
 * @param isPrimary 是否为主号码（详情展示与拨号快捷入口优先使用）
 */
data class PhoneNumber(
    val id: String,
    val personId: String,
    val number: String,
    val label: PhoneLabel = PhoneLabel.MOBILE,
    val status: PhoneStatus = PhoneStatus.ACTIVE,
    val isPrimary: Boolean = false,
    val note: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
