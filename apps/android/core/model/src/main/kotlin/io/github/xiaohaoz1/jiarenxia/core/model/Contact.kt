package io.github.xiaohaoz1.jiarenxia.core.model

/** 联系方式（联系提醒，FAMILY_AFFAIRS）。 */
enum class ContactMethod(val label: String) {
    /** 电话。 */
    CALL("电话"),
    /** 见面。 */
    VISIT("见面"),
    /** 微信 / 消息。 */
    MESSAGE("微信/消息"),
    /** 视频。 */
    VIDEO("视频"),
    /** 其他。 */
    OTHER("其他")
}

/** 一条联系记录。 */
data class ContactLog(
    val id: String,
    val personId: String,
    /** 联系发生时间（毫秒）。 */
    val atMillis: Long,
    val method: ContactMethod,
    val note: String?
)
