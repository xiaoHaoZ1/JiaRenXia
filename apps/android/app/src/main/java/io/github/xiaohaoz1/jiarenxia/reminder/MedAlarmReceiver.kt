package io.github.xiaohaoz1.jiarenxia.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MedAlarmEntryPoint {
    fun medAlarmScheduler(): MedAlarmScheduler
}

/**
 * 「按时吃」闹钟接收器（exported=false）：
 * - ACTION_FIRE：单个槽位到点，goAsync 里按当前数据重算并通知；
 * - BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED /
 *   精确闹钟权限变化：全量重排（系统杀进程、改时间后恢复）。
 */
class MedAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val scheduler = EntryPointAccessors.fromApplication(
            context.applicationContext, MedAlarmEntryPoint::class.java
        ).medAlarmScheduler()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == MedAlarmScheduler.ACTION_FIRE) {
                    scheduler.onAlarmFired(
                        personId = intent.getStringExtra(
                            MedAlarmScheduler.EXTRA_PERSON_ID).orEmpty(),
                        minute = intent.getIntExtra(
                            MedAlarmScheduler.EXTRA_MINUTE, -1),
                        scheduledAtMillis = intent.getLongExtra(
                            MedAlarmScheduler.EXTRA_SCHEDULED_AT, 0L)
                    )
                } else {
                    scheduler.rescheduleAll()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
