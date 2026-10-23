package io.github.xiaohaoz1.jiarenxia.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.xiaohaoz1.jiarenxia.MainActivity
import io.github.xiaohaoz1.jiarenxia.R
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * 每天按各类提醒的设置时刻检查：发生日与纪念日提醒、家庭事项提醒、联系提醒；
 * 执行后把下一次排到最近的下一个时刻。
 * 类名不可改——WorkManager 已持久化了已排队的任务名。
 * 用 APPEND_OR_REPLACE——REPLACE 会取消正在运行的自己。
 */
@HiltWorker
class BirthdayReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FamilyRepository,
    private val taskRepository: io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository,
    private val contactRepository: io.github.xiaohaoz1.jiarenxia.data
        .ContactRepository,
    private val settings: SettingsRepository,
    private val observeOverview: ObserveFamilyOverview,
    private val lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar,
    private val clock: TimeSource
) : CoroutineWorker(context, params) {

    private fun now(): LocalDateTime =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).toLocalDateTime()

    override suspend fun doWork(): Result {
        val now = now()
        val today = now.toLocalDate()
        val force = inputData.getBoolean(KEY_FORCE, false)
        // 调试强制运行：只发通知，不写运行状态、不排下次——不打断常规的每日检查链
        if (force) {
            // 调试强制运行：三类都发（忽略各自时刻与当天是否已发）
            val posted = mutableSetOf<String>()
            if (settings.remindersEnabled.first()) postNotifications(today, posted)
            if (settings.taskRemindersEnabled.first())
                postTaskNotifications(today, java.time.LocalTime.MAX, posted)
            if (settings.contactRemindersEnabled.first()) postContactNotifications(today, posted)
            return Result.success()
        }
        // 当天已通知过的提醒键：补跑（数据在时刻过后被改成今天到期等）只发新的
        val state = settings.getReminderRunState()
        val posted: MutableSet<String> =
            (if (state?.first == today.toEpochDay()) state.second else emptySet())
                .toMutableSet()
        val nowTime = now.toLocalTime()
        if (settings.remindersEnabled.first() &&
            !nowTime.isBefore(ReminderTiming.timeOf(settings.reminderMinuteOfDay.first()))
        ) {
            postNotifications(today, posted)
        }
        if (settings.taskRemindersEnabled.first()) {
            postTaskNotifications(today, nowTime, posted)
        }
        if (settings.contactRemindersEnabled.first() &&
            !nowTime.isBefore(ReminderTiming.timeOf(settings.contactReminderMinuteOfDay.first()))
        ) {
            postContactNotifications(today, posted)
        }
        settings.setReminderRunState(today.toEpochDay(), posted)
        enqueueNext(now)
        return Result.success()
    }

    /** 排下一次检查（最近的下一个已开启时刻，已过的算明天）。 */
    private suspend fun enqueueNext(now: LocalDateTime) {
        val delay = ReminderTiming.nextRunDelay(
            now, enabledCheckTimes(settings, taskRepository.getTasks())) ?: return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WorkManagerReminderScheduler.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<BirthdayReminderWorker>().setInitialDelay(delay).build()
        )
    }

    private suspend fun postNotifications(today: LocalDate, posted: MutableSet<String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val overview = observeOverview().first() ?: return
        // 称谓表：未关联 / 本人 不放进称谓
        val titles = overview.byId.mapValues { (_, o) ->
            val k = o.result.kind
            if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF) null else o.displayTitle
        }
        val dues = ReminderPlanner.dueNotifications(repository.getBirthdayEntries(), titles, today, lunar) +
            // 纪念日和忌日与生日同一开关、独立渠道（V0.8）
            ReminderPlanner.dueDateReminders(repository.getDateEntries(), titles, today, lunar, overview)
        if (dues.isEmpty()) return
        ensureChannel()
        ensureAnnivChannel()
        val nm = NotificationManagerCompat.from(context)
        dues.forEach { d ->
            val key = "date:" + d.kind.name + ":" + d.personId + ":" + d.daysBefore +
                (d.milestoneDays?.let { ":" + it } ?: "")
            if (key in posted) return@forEach
            val isSelf = overview.byId[d.personId]?.person?.isSelf == true
            runCatching { nm.notify(notificationId(d), build(d, isSelf)) }
                .onSuccess { posted += key }
        }
    }

    private fun notificationId(d: DueReminder): Int =
        (d.kind.name + ":" + d.personId + ":" + d.daysBefore +
                (d.milestoneDays?.let { ":" + it } ?: "")).hashCode()

    private fun build(d: DueReminder, isSelf: Boolean): android.app.Notification {
        val text = ReminderPlanner.text(d, isSelf)
        val intent = PendingIntent.getActivity(
            context, notificationId(d),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PERSON_ID, d.personId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channel = if (d.kind.isBirthday) CHANNEL_ID else ANNIV_CHANNEL_ID
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentText(if (channel == ANNIV_CHANNEL_ID)
                "家人侠：有纪念日或忌日提醒" else "家人侠：有家人的生日提醒")
            .build()
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("家人侠")
            .setContentText(text)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(intent)
            .build()
    }

    /**
     * 事项通知：独立渠道 family_tasks，点开该事项编辑页。
     * 各事项有效时刻 = 自定义提醒时刻或家事提醒时刻；未到点的本期不发。
     */
    private suspend fun postTaskNotifications(
        today: LocalDate,
        nowTime: java.time.LocalTime,
        posted: MutableSet<String>
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val global = ReminderTiming.timeOf(settings.taskReminderMinuteOfDay.first())
        val dues = TaskSchedule.dueNotifications(taskRepository.getTasks(), today)
            .filter { d ->
                val at = d.task.remindMinutes?.let { ReminderTiming.timeOf(it) } ?: global
                !nowTime.isBefore(at)
            }
        if (dues.isEmpty()) return
        ensureTaskChannel()
        val nm = NotificationManagerCompat.from(context)
        dues.forEach { d ->
            val key = "task:" + d.task.id + ":" + TaskSchedule.dueDate(d.task).toEpochDay() +
                ":" + d.daysBefore
            if (key in posted) return@forEach
            runCatching { nm.notify(taskNotificationId(d), buildTask(d)) }
                .onSuccess { posted += key }
        }
    }

    private fun taskNotificationId(d: DueTask): Int =
        ("task:" + d.task.id + ":" + TaskSchedule.dueDate(d.task).toEpochDay() +
            ":" + d.daysBefore).hashCode()

    private fun buildTask(d: DueTask): android.app.Notification {
        val intent = PendingIntent.getActivity(
            context, taskNotificationId(d),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TASK_ID, d.task.id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(context, TASK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentText("家人侠：有家庭事项提醒")
            .build()
        return NotificationCompat.Builder(context, TASK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("家人侠")
            .setContentText(TaskSchedule.text(d.task, d.daysBefore))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(intent)
            .build()
    }

    /**
     * 联系提醒（FAMILY_AFFAIRS）：设了周期且到期（到期日 / 每 7 天复提）的人。
     * 点开对应人物详情页；独立渠道 contact。
     */
    private suspend fun postContactNotifications(today: LocalDate, posted: MutableSet<String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val plans = contactRepository.getPlanList()
        if (plans.isEmpty()) return
        val overview = observeOverview().first() ?: return
        val logs = contactRepository.getLogs().groupBy { it.personId }
        val titles = overview.byId.mapValues { (_, o) ->
            val k = o.result.kind
            if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF) null
            else o.displayTitle
        }
        val nm = NotificationManagerCompat.from(context)
        ensureContactChannel()
        plans.forEach { plan ->
            val key = "contact:" + plan.personId
            if (key in posted) return@forEach
            val status = io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
                .status(
                    logs[plan.personId].orEmpty(), plan.intervalDays,
                    plan.createdAt, today)
            if (!io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
                    .remindToday(status, today)
            ) return@forEach
            val person = overview.byId[plan.personId]?.person
                ?: return@forEach
            if (!io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
                    .eligible(person)
            ) return@forEach
            val label = titles[plan.personId] ?: person.name
            val text = io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
                .notifyText(status, label)
            val id = ("contact:" + plan.personId).hashCode()
            val intent = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_PERSON_ID, plan.personId)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val publicVersion = NotificationCompat.Builder(
                context, CONTACT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentText("家人侠：有家人该联系了")
                .build()
            runCatching {
                nm.notify(
                    id,
                    NotificationCompat.Builder(context, CONTACT_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_notify)
                        .setContentTitle("家人侠")
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .setPublicVersion(publicVersion)
                        .setContentIntent(intent)
                        .build()
                )
            }.onSuccess { posted += key }
        }
    }

    private fun ensureContactChannel() {
        val channel = NotificationChannel(
            CONTACT_CHANNEL_ID, "联系提醒", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun ensureTaskChannel() {
        val channel = NotificationChannel(
            TASK_CHANNEL_ID, "家庭事项", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun ensureAnnivChannel() {
        val chan = NotificationChannel(
            ANNIV_CHANNEL_ID, "纪念日与忌日", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(chan)
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "生日提醒", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "birthday"
        const val ANNIV_CHANNEL_ID = "anniversary"
        const val TASK_CHANNEL_ID = "family_tasks"
        const val CONTACT_CHANNEL_ID = "contact"
        const val KEY_FORCE = "force"
    }
}
