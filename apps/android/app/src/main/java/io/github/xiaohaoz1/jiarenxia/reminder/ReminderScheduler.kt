package io.github.xiaohaoz1.jiarenxia.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 提醒调度：三类提醒（生日与纪念日 / 家庭事项 / 联系）各有每日时刻，
 * 每天在各自的设置时刻检查一次（DEVELOPMENT_PLAN 2.5、FAMILY_AFFAIRS 5.4）。
 * reconcile() 可被重复调用——无论何时都保证“下一次检查”按当前状态重算；
 * 有已开启的时刻今天已过则立即补跑一次（补跑只发当天尚未通知过的提醒）。
 */
interface ReminderScheduler {
    suspend fun reconcile()
    fun cancelAll()
    /** 调试：立即执行一次检查（忽略各类时刻与当天是否已发）。 */
    fun enqueueDebugRun()
}

/**
 * 当前已开启提醒的检查时刻集合（调度与 worker 排下次时共用同一口径）。
 * 事项开了「按时刻提醒」时把各自的时刻也排进去（仅看未办完且开启提醒的事项）。
 */
internal suspend fun enabledCheckTimes(
    settings: SettingsRepository,
    tasks: List<io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask>
): Set<LocalTime> =
    buildSet {
        if (settings.remindersEnabled.first())
            add(ReminderTiming.timeOf(settings.reminderMinuteOfDay.first()))
        if (settings.taskRemindersEnabled.first()) {
            add(ReminderTiming.timeOf(settings.taskReminderMinuteOfDay.first()))
            tasks.forEach { t ->
                if (t.enabled && !t.done) t.remindMinutes?.let { add(ReminderTiming.timeOf(it)) }
            }
        }
        if (settings.contactRemindersEnabled.first())
            add(ReminderTiming.timeOf(settings.contactReminderMinuteOfDay.first()))
    }

@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val taskRepository: io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository
) : ReminderScheduler {

    override suspend fun reconcile() {
        val delay = ReminderTiming.nextDelay(
            LocalDateTime.now(), enabledCheckTimes(settings, taskRepository.getTasks()))
        if (delay == null) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BirthdayReminderWorker>()
                .setInitialDelay(delay)
                .build()
        )
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    override fun enqueueDebugRun() {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<BirthdayReminderWorker>()
                .setInputData(Data.Builder().putBoolean(BirthdayReminderWorker.KEY_FORCE, true).build())
                .build()
        )
    }

    companion object {
        const val WORK_NAME = "birthday-reminder"
    }
}
