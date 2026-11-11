package io.github.xiaohaoz1.jiarenxia.core.model

/**
 * 用药「怎么吃」三选一（FAMILY_AFFAIRS 23）：
 * 短期疗程（记录疗程天数，可待确认）/ 长期吃 / 需要时吃。
 * 存 TEXT；备份遇到不认识的 usage 跳过该条药（不整份失败）。
 */
enum class MedUsage { SHORT_COURSE, LONG_TERM, AS_NEEDED }

/** 饭前 / 饭后 / 随餐；null = 不填。 */
enum class MedMeal { BEFORE, AFTER, WITH }

/**
 * 一条用药记录（medications + medication_times；FAMILY_AFFAIRS 23）。
 * App 只知道「记录」，不知道「实际服用」：天数一律按 [startEpochDay]
 * 推算，界面文案不得写「已经吃了 N 天」「已停药」这类服药事实。
 *
 * [courseDays] 仅 SHORT_COURSE 有意义（1..365；null = 疗程待确认）；
 * [refillEpochDay] 仅 LONG_TERM（下次拿药日）；[stoppedEpochDay]
 * 非空 = 用户点过「记录已停用」。
 * [times] 为每天服用时刻（一天内分钟数 0..1439，至多 4 个，
 * 已排序去重；AS_NEEDED 恒为空）。
 */
data class Medication(
    val id: String,
    val personId: String,
    val name: String,
    val dose: String?,
    val spec: String?,
    val meal: MedMeal?,
    val usage: MedUsage,
    val courseDays: Int?,
    /** 记录的开始日期（epochDay）。 */
    val startEpochDay: Long,
    val purpose: String?,
    val note: String?,
    /** 长期吃的「下次拿药日」（epochDay）；其他用法恒 null。 */
    val refillEpochDay: Long?,
    /** 记录停用日期（epochDay）；null = 未停用。 */
    val stoppedEpochDay: Long?,
    val times: List<Int> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

/**
 * 人物用药区的按人开关（medication_person_prefs，每人一行）。
 * [courseRefillEnabled]：疗程到期与续药提醒（默认开）。
 * [onTimeEnabled]：按时吃提醒（第 5 批用；null = 按默认：
 * “我”开、他人关）。
 */
data class MedicationPersonPrefs(
    val personId: String,
    val courseRefillEnabled: Boolean = true,
    val onTimeEnabled: Boolean? = null
)
