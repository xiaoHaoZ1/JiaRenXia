package io.github.xiaohaoz1.jiarenxia.guardian.core

/**
 * 状态机相位。DISABLED 不是相位——是否开启由 GuardConfig.enabled 表达，
 * 关闭时保留相位便于 UI 展示与恢复。
 */
enum class Phase {
    MONITORING,
    CONFIRMING,
    ALERTING,
    ALERTED,
}

/**
 * 持久化状态（墙钟毫秒）。selfDialing 是“自己拨出的报警电话不算活动”的双保险之一。
 */
data class GuardState(
    val phase: Phase = Phase.MONITORING,
    val lastActivityAt: Long = 0L,
    val lastActivityKind: ActivityKind? = null,
    val deadlineAt: Long = 0L,
    val confirmEndAt: Long = 0L,
    val nextDailySmsAt: Long = 0L,
    val alertedAt: Long = 0L,
    val selfDialing: Boolean = false,
    /** 最近一次来电响铃时刻，用于区分“接听来电”和“我们自己拨出的报警电话”。 */
    val lastRingAt: Long = 0L,
    /** 引擎认为手机正处于解锁使用会话中：此时不计倒计时，走使用中上限。 */
    val inUse: Boolean = false,
    /** 最近一次“使用后熄屏”的时刻（仅展示用）。 */
    val lastLockAt: Long = 0L,
)
