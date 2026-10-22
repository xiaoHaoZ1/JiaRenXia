package io.github.xiaohaoz1.jiarenxia.guardian.sys

import android.app.KeyguardManager
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.xiaohaoz1.jiarenxia.guardian.core.Ports

/** Ports 的 Android 实现：把引擎指令转给闹钟/响铃/通知/拨号/短信等子系统。 */
class AndroidPorts : Ports {

    private fun scheduler() = ServiceLocator.scheduler
    private fun alert() = ServiceLocator.alert

    override fun scheduleDeadline(at: Long) = scheduler().scheduleDeadline(at)

    override fun scheduleConfirmEnd(at: Long) = scheduler().scheduleConfirmEnd(at)

    override fun scheduleDailySms(at: Long) = scheduler().scheduleDailySms(at)

    override fun scheduleWatchdog(delayMillis: Long) =
        scheduler().scheduleWatchdog(delayMillis)

    override fun cancelConfirmAndAlert() = scheduler().cancelConfirmAndAlert()

    override fun cancelAll() = scheduler().cancelAll()

    override fun startConfirmRinging(confirmEndAt: Long) =
        alert().startConfirmRinging(confirmEndAt)

    override fun stopRinging() = alert().stopRinging()

    override fun showConfirmNotification(confirmEndAt: Long) =
        Notifications.showConfirm(ServiceLocator.app, confirmEndAt)

    override fun showAlertedNotification(alertedAt: Long) =
        Notifications.showAlerted(ServiceLocator.app, alertedAt)

    override fun placeCall(number: String): Boolean =
        ServiceLocator.telecom.placeCall(number)

    override fun sendDailySms() {
        ServiceLocator.sms.sendDailySms()
    }

    override fun sendEmergencySms() {
        ServiceLocator.sms.sendEmergencySms()
    }

    override fun ensureServiceRunning() {
        try {
            val ctx = ServiceLocator.app
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, GuardService::class.java)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "ensureServiceRunning failed", t)
        }
    }

    override fun stopService() {
        val ctx = ServiceLocator.app
        ctx.stopService(Intent(ctx, GuardService::class.java))
        Notifications.cancelLegacyStatus(ctx)
    }

    override fun isDeviceInUse(): Boolean = try {
        val ctx = ServiceLocator.app
        val pm = ctx.getSystemService(PowerManager::class.java)
        val kg = ctx.getSystemService(KeyguardManager::class.java)
        pm != null && pm.isInteractive && kg != null && !kg.isKeyguardLocked
    } catch (t: Throwable) {
        false
    }

    override fun reconcileMissedUnlockSince(since: Long): Long? =
        ServiceLocator.usageStats.missedUnlockSince(since)

    override fun log(tag: String, msg: String) {
        Log.d(tag, msg)
        try {
            ServiceLocator.eventLog.append(tag, msg)
        } catch (t: Throwable) {
            // 日志失败不影响业务
        }
    }

    companion object {
        const val TAG = "GuardPorts"
    }
}
