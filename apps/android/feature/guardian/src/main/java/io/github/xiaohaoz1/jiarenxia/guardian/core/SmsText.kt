package io.github.xiaohaoz1.jiarenxia.guardian.core

/**
 * 短信文案（纯 Kotlin，可单测）。品牌前缀“【家人侠·守护】”。
 */
object SmsText {

    /**
     * 每日平安短信："【家人侠·守护】一切正常。最后一次使用手机：MM-dd HH:mm（X小时前）。电量NN%…"
     * inUse（正在使用中）时最后使用时间写作“正在使用”。
     */
    fun daily(
        lastActivityAt: Long,
        now: Long,
        batteryPct: Int,
        charging: Boolean,
        inUse: Boolean,
    ): String {
        val lastPart = if (inUse) {
            "最后一次使用手机：正在使用。"
        } else {
            val lastStr =
                if (lastActivityAt > 0) TimeRules.formatDateTime(lastActivityAt) else "无记录"
            val ago = if (lastActivityAt > 0)
                TimeRules.formatDuration(now - lastActivityAt) + "前" else "未知"
            "最后一次使用手机：" + lastStr + "（" + ago + "）。"
        }
        val chg = if (charging) "，充电中" else ""
        return "【家人侠·守护】一切正常。" + lastPart +
            "电量" + batteryPct + "%" + chg +
            "。若长时间未收到本短信，请检查手机是否关机或APP被清理。"
    }

    /** 拨号失败兜底紧急短信。 */
    fun emergency(lastActivityAt: Long): String {
        val last = if (lastActivityAt > 0) TimeRules.formatDateTime(lastActivityAt) else "无记录"
        return "【家人侠·守护】紧急：长时间未检测到使用手机，且自动拨打电话失败" +
            "（可能权限被收回或SIM卡异常），请尽快电话联系确认！最后使用时间：" + last + "。"
    }
}
