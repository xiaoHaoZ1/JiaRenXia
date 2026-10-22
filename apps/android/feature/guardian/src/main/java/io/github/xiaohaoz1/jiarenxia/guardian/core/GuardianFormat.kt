package io.github.xiaohaoz1.jiarenxia.guardian.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 守护 UI 用纯函数（可单测）。
 */
object GuardianFormat {

    /** "今天 19:05" / "昨天 08:12" / "10月1日 08:12"。 */
    fun formatRelativeDayTime(at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = Instant.ofEpochMilli(at).atZone(zone).toLocalDateTime()
        val n = Instant.ofEpochMilli(now).atZone(zone).toLocalDateTime()
        val hm = "%02d:%02d".format(t.hour, t.minute)
        return when (t.toLocalDate()) {
            n.toLocalDate() -> "今天 $hm"
            n.toLocalDate().minusDays(1) -> "昨天 $hm"
            else -> "${t.monthValue}月${t.dayOfMonth}日 $hm"
        }
    }

    /** 相对时长："3 小时前" / "25 分钟前" / "刚刚"。 */
    fun formatAgo(at: Long, now: Long): String {
        val d = (now - at).coerceAtLeast(0)
        return when {
            d < 60_000 -> "刚刚"
            d < 3_600_000 -> "${d / 60_000} 分钟前"
            d < 86_400_000 -> "${d / 3_600_000} 小时前"
            else -> "${d / 86_400_000} 天前"
        }
    }

    /** 倒计时："4小时55分" / "3分20秒" / "40秒"。 */
    fun formatCountdown(millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> "${h}小时${m}分"
            m > 0 -> "${m}分${sec}秒"
            else -> "${sec}秒"
        }
    }

    /**
     * 手动输入号码校验：只允许数字/+/-/空格，去掉分隔符后至少 5 位数字。
     */
    fun isValidContactNumber(raw: String): Boolean {
        val t = raw.trim()
        if (t.isEmpty()) return false
        if (!t.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) return false
        return t.count { it.isDigit() } >= 5
    }

    /** 守护状态通知文案：前台服务通知与刷新共用。 */
    fun statusText(state: GuardState): String = when (state.phase) {
        Phase.MONITORING -> when {
            state.inUse -> "守护中｜正在使用"
            state.lastActivityAt > 0 ->
                "守护中｜上次使用 " + TimeRules.formatClock(state.lastActivityAt)
            else -> "守护中"
        }
        Phase.CONFIRMING -> "正在等待确认"
        Phase.ALERTING, Phase.ALERTED ->
            if (state.alertedAt > 0)
                "已于 " + TimeRules.formatClock(state.alertedAt) + " 拨打紧急电话"
            else "已拨打紧急电话"
    }

    /** 权限进度：返回 (必做已完成数, 必做总数)。 */
    fun requiredProgress(
        items: List<Pair<Boolean, Boolean>>  // (granted, optional)
    ): Pair<Int, Int> {
        val required = items.filter { !it.second }
        return required.count { it.first } to required.size
    }
}
