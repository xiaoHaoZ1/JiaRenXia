package io.github.xiaohaoz1.jiarenxia.guardian.core

/**
 * 引擎对平台（Android 层）的全部依赖。实现位于 sys/，单测用 FakePorts。
 */
interface Ports {

    /** 用户可见定时（setAlarmClock）：无活动到期。 */
    fun scheduleDeadline(at: Long)

    /** 用户可见定时：响铃确认窗口结束。 */
    fun scheduleConfirmEnd(at: Long)

    /** 用户可见定时：每日平安短信。 */
    fun scheduleDailySms(at: Long)

    /** 看门狗自查（setExactAndAllowWhileIdle）。 */
    fun scheduleWatchdog(delayMillis: Long)

    /** 取消确认阶段相关闹钟与确认通知。 */
    fun cancelConfirmAndAlert()

    /** 取消所有闹钟（关守护时用）。 */
    fun cancelAll()

    /** 开始响铃+震动+唤醒锁+全屏通知，confirmEndAt 用于界面倒计时。 */
    fun startConfirmRinging(confirmEndAt: Long)

    /** 停止响铃/震动并释放资源。 */
    fun stopRinging()

    /** 展示“需要确认”通知（fullScreenIntent → PreAlarmActivity）。 */
    fun showConfirmNotification(confirmEndAt: Long)

    /** 展示“已拨打紧急电话”结果通知。 */
    fun showAlertedNotification(alertedAt: Long)

    /** 拨打紧急电话（免提 extra），返回是否成功发起。 */
    fun placeCall(number: String): Boolean

    /** 发送每日平安短信（文案在 sys 层拼装，含电量等系统信息）。 */
    fun sendDailySms()

    /** 拨号失败时的兜底紧急短信。 */
    fun sendEmergencySms()

    /** 确保前台守护服务在跑。 */
    fun ensureServiceRunning()

    /** 停止前台守护服务及其常驻通知（关闭守护时调用）。 */
    fun stopService()

    /** 设备此刻是否正在使用：屏幕亮着且未处于锁屏。异常时按 false 处理。 */
    fun isDeviceInUse(): Boolean

    /**
     * UsageStats 兜底：返回 since 之后最后一次 KEYGUARD_HIDDEN 的时间戳；
     * 无权限/无事件/低版本返回 null。
     */
    fun reconcileMissedUnlockSince(since: Long): Long?

    /** 统一日志入口（Logcat + EventLog 落盘）。 */
    fun log(tag: String, msg: String)
}
