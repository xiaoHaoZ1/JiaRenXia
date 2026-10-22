package io.github.xiaohaoz1.jiarenxia.reminder

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 提醒检查的运行时机（DEVELOPMENT_PLAN 2.5）：生日与纪念日、家庭事项、联系
 * 三类提醒各有自己的每日时刻（默认 09:00），在「我的 → 提醒」中分别设置。
 * 一天只按这些时刻排队运行；某类数据在该类时刻过后被改成“今天到期”时，
 * 立即补跑一次（只发该类且当天尚未通知过的提醒，见 BirthdayReminderWorker）。
 */
object ReminderTiming {

    /** 默认检查时刻（09:00）对应的一天内分钟数。 */
    const val DEFAULT_MINUTE_OF_DAY = 9 * 60

    /** 一天内的分钟数（0..1439，越界按边界处理）→ 检查时刻。 */
    fun timeOf(minuteOfDay: Int): LocalTime {
        val m = minuteOfDay.coerceIn(0, 1439)
        return LocalTime.of(m / 60, m % 60)
    }

    /** 一天内的分钟数 → “HH:mm”（界面显示用）。 */
    fun format(minuteOfDay: Int): String {
        val t = timeOf(minuteOfDay)
        return "%02d:%02d".format(t.hour, t.minute)
    }

    /**
     * reconcile 用的延迟：[times] 为所有已开启提醒的检查时刻。
     * 有任一时刻今天已过 → 立即补跑一次（worker 按当天已通知键去重，只发
     * 没通知过的）；否则排到最近的一个未来时刻。空集 → null（无需排）。
     */
    fun nextDelay(now: LocalDateTime, times: Set<LocalTime>): Duration? {
        if (times.isEmpty()) return null
        val passed = times.any { !now.toLocalTime().isBefore(it) }
        if (passed) return Duration.ZERO
        return Duration.between(now, now.toLocalDate().atTime(times.min()))
    }

    /**
     * 任务跑完后排下一次：每个时刻的下次发生（已过的算明天）取最早者，
     * 恒指向未来；空集 → null。
     */
    fun nextRunDelay(now: LocalDateTime, times: Set<LocalTime>): Duration? {
        val today = now.toLocalDate()
        return times.map { t ->
            today.atTime(t).let { if (now.isBefore(it)) it else today.plusDays(1).atTime(t) }
        }.minOrNull()?.let { Duration.between(now, it) }
    }
}
