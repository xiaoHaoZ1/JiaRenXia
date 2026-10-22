package io.github.xiaohaoz1.jiarenxia.guardian.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.xiaohaoz1.jiarenxia.guardian.core.Event
import kotlin.concurrent.thread

/**
 * 开机 / 锁定开机（Direct Boot）/ 包升级 / 时间时区变化 → 统一 SystemReset。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val pending = goAsync()
        thread(name = "GuardBoot") {
            try {
                ServiceLocator.init(context)
                ServiceLocator.engine.handle(Event.SystemReset)
            } catch (t: Throwable) {
                Log.e(TAG, "SystemReset failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "GuardBoot"
    }
}
