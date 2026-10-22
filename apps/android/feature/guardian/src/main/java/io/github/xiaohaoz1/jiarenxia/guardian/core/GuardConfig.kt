package io.github.xiaohaoz1.jiarenxia.guardian.core

/**
 * 守护配置。测试模式把阈值/确认窗口缩到秒级，方便真机回归。
 */
data class GuardConfig(
    val enabled: Boolean = false,
    /** 紧急联系人姓名（仅展示用，可为空）。 */
    val contactName: String = "",
    val contactNumber: String = "",
    val thresholdMinutes: Int = 480,
    val confirmWindowMinutes: Int = 2,
    val dailySmsEnabled: Boolean = true,
    val dailySmsMinuteOfDay: Int = 9 * 60,
    /**
     * 夜间免打扰：到点的报警若落在 [quietStartMinuteOfDay, quietEndMinuteOfDay)
     * 时段内则顺延到时段结束，期间不响铃不拨号——睡得久的人不会每天凌晨被误报。
     * 代价是时段内真出事要等结束才被发现，所以可关可改。
     */
    val quietEnabled: Boolean = true,
    val quietStartMinuteOfDay: Int = 23 * 60,
    val quietEndMinuteOfDay: Int = 7 * 60,
    val testMode: Boolean = false,
) {
    /** 无活动阈值：测试模式固定 30 秒。 */
    fun effectiveThresholdMillis(): Long =
        if (testMode) 30_000L else thresholdMinutes.toLong() * 60_000L

    /**
     * 使用中上限：一次解锁会话的最长时长，到点同样响铃确认。
     * 故意不受测试模式影响——测试模式只缩短“锁屏后”的倒计时，
     * 防止回归时误以为使用中也会 30 秒就响。
     */
    fun inUseCapMillis(): Long = thresholdMinutes.toLong() * 60_000L

    /** 响铃确认窗口：测试模式固定 15 秒。 */
    fun effectiveConfirmWindowMillis(): Long =
        if (testMode) 15_000L else confirmWindowMinutes.toLong() * 60_000L

    companion object {
        /** 无活动阈值下限：1 小时。 */
        const val MIN_THRESHOLD_MINUTES = 60
        /** 无活动阈值上限：24 小时——手机一整天没人用就该报警，更久失去守护意义。 */
        const val MAX_THRESHOLD_MINUTES = 24 * 60
    }

    /** 号码脱敏，防止 toString 把联系人号码写进日志。 */
    override fun toString(): String =
        "GuardConfig(enabled=" + enabled +
            ", contactName=" + (if (contactName.isEmpty()) "" else "***") +
            ", contactNumber=" + maskNumber(contactNumber) +
            ", thresholdMinutes=" + thresholdMinutes +
            ", confirmWindowMinutes=" + confirmWindowMinutes +
            ", dailySmsEnabled=" + dailySmsEnabled +
            ", dailySmsMinuteOfDay=" + dailySmsMinuteOfDay +
            ", quietEnabled=" + quietEnabled +
            ", quietStartMinuteOfDay=" + quietStartMinuteOfDay +
            ", quietEndMinuteOfDay=" + quietEndMinuteOfDay +
            ", testMode=" + testMode + ")"
}

/**
 * 电话号码脱敏：保留前 3 位与后 4 位，中间以 **** 代替；
 * 7 位及以下只显示末 2 位。用于日志与 EventLog（ARCHITECTURE §43）。
 */
fun maskNumber(n: String): String {
    val digits = n.trim()
    return when {
        digits.length <= 2 -> digits
        digits.length <= 7 -> "*".repeat(digits.length - 2) + digits.takeLast(2)
        else -> digits.take(3) + "****" + digits.takeLast(4)
    }
}
