package io.github.xiaohaoz1.jiarenxia.guardian.sys

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 所有 PendingIntent/闹钟集中在此。
 * 用户可见定时（到期/确认/日报）用 setAlarmClock——不受 Doze 影响；
 * 看门狗用 setExactAndAllowWhileIdle。
 */
class AlarmScheduler(private val context: Context) {

    private val am: AlarmManager = context.getSystemService(AlarmManager::class.java)

    private fun op(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 闹钟旁边点了跳转主界面。 */
    private fun showIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * 用户可见定时优先 setAlarmClock（不受 Doze 影响）；
     * API31/32 未授权 SCHEDULE_EXACT_ALARM 时会抛 SecurityException，逐级降级。
     */
    private fun setUserVisibleAlarm(at: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent()), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (se: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun scheduleDeadline(at: Long) {
        setUserVisibleAlarm(at, op(ACTION_DEADLINE, REQ_DEADLINE))
    }

    fun scheduleConfirmEnd(at: Long) {
        setUserVisibleAlarm(at, op(ACTION_CONFIRM_END, REQ_CONFIRM_END))
    }

    fun scheduleDailySms(at: Long) {
        setUserVisibleAlarm(at, op(ACTION_DAILY_SMS, REQ_DAILY_SMS))
    }

    fun scheduleWatchdog(delayMillis: Long) {
        val triggerAt = System.currentTimeMillis() + delayMillis
        val pi = op(ACTION_WATCHDOG, REQ_WATCHDOG)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } catch (se: SecurityException) {
            // API31/32 未授权精确闹钟时降级
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelConfirmAndAlert() {
        am.cancel(op(ACTION_CONFIRM_END, REQ_CONFIRM_END))
        Notifications.cancelConfirm(context)
    }

    fun cancelAll() {
        am.cancel(op(ACTION_DEADLINE, REQ_DEADLINE))
        am.cancel(op(ACTION_CONFIRM_END, REQ_CONFIRM_END))
        am.cancel(op(ACTION_DAILY_SMS, REQ_DAILY_SMS))
        am.cancel(op(ACTION_WATCHDOG, REQ_WATCHDOG))
        Notifications.cancelConfirm(context)
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    companion object {
        const val ACTION_DEADLINE = "io.github.xiaohaoz1.jiarenxia.guardian.alarm.DEADLINE"
        const val ACTION_CONFIRM_END = "io.github.xiaohaoz1.jiarenxia.guardian.alarm.CONFIRM_END"
        const val ACTION_DAILY_SMS = "io.github.xiaohaoz1.jiarenxia.guardian.alarm.DAILY_SMS"
        const val ACTION_WATCHDOG = "io.github.xiaohaoz1.jiarenxia.guardian.alarm.WATCHDOG"

        private const val REQ_DEADLINE = 1
        private const val REQ_CONFIRM_END = 2
        private const val REQ_DAILY_SMS = 3
        private const val REQ_WATCHDOG = 4
    }
}
