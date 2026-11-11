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
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.HomeVisitSchedule
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombCalendar
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay
import io.github.xiaohaoz1.jiarenxia.domain.tomb.TombPrefs
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
    private val homeVisitRepository: io.github.xiaohaoz1.jiarenxia.data
        .HomeVisitRepository,
    private val pregnancyRepository: io.github.xiaohaoz1.jiarenxia.data
        .PregnancyRepository,
    private val checklistRepository: io.github.xiaohaoz1.jiarenxia.data
        .ChecklistRepository,
    private val celebrationRepository: io.github.xiaohaoz1.jiarenxia.data
        .BabyCelebrationRepository,
    private val medicationRepository: io.github.xiaohaoz1.jiarenxia.data
        .MedicationRepository,
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
            // 调试强制运行：四类都发（忽略各自时刻与当天是否已发）
            val posted = mutableSetOf<String>()
            if (settings.remindersEnabled.first()) postNotifications(today, posted)
            if (settings.taskRemindersEnabled.first())
                postTaskNotifications(today, java.time.LocalTime.MAX, posted)
            if (settings.contactRemindersEnabled.first()) {
                postContactNotifications(today, posted)
                postHomeVisitNotifications(today, posted)
            }
            if (settings.tombRemindersEnabled.first()) postTombNotifications(today, posted)
            if (settings.medRemindersEnabled.first())
                postMedicationNotifications(today, posted)
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
            postHomeVisitNotifications(today, posted)
        }
        if (settings.tombRemindersEnabled.first() &&
            !nowTime.isBefore(ReminderTiming.timeOf(settings.tombReminderMinuteOfDay.first()))
        ) {
            postTombNotifications(today, posted)
        }
        if (settings.medRemindersEnabled.first() &&
            !nowTime.isBefore(ReminderTiming.timeOf(settings.medReminderMinuteOfDay.first()))
        ) {
            postMedicationNotifications(today, posted)
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
        val birthdayEntries = repository.getBirthdayEntries()
        val dues = ReminderPlanner.dueNotifications(birthdayEntries, titles, today, lunar) +
            // 纪念日与生日同一开关、独立渠道（V0.8）；忌日已挪入上坟提醒（FAMILY_AFFAIRS 19）
            ReminderPlanner.dueDateReminders(repository.getDateEntries(), titles, today, lunar, overview)
                .filter { it.kind != ImportantDateType.DEATH_ANNIVERSARY }
        ensureChannel()
        ensureAnnivChannel()
        // 满月/百天与生日 dues 相互独立：当天没有生日类事项也要照发
        postBabyCelebrationNotifications(today, posted, overview, birthdayEntries)
        if (dues.isEmpty()) return
        // 「发祝福」动作要默认号码：仅当存在生日当天提醒时才查
        val numbers = if (dues.any { it.kind.isBirthday && it.daysBefore == 0 })
            repository.observeDefaultNumbers().first() else emptyMap()
        val nm = NotificationManagerCompat.from(context)
        dues.forEach { d ->
            val key = "date:" + d.kind.name + ":" + d.personId + ":" + d.daysBefore +
                (d.milestoneDays?.let { ":" + it } ?: "")
            if (key in posted) return@forEach
            val isSelf = overview.byId[d.personId]?.person?.isSelf == true
            val blessing = blessingFor(d, isSelf, numbers, overview)
            runCatching { nm.notify(notificationId(d), build(d, isSelf, blessing = blessing)) }
                .onSuccess { posted += key }
        }
    }

    /**
     * 宝宝满月 / 百天提醒（FAMILY_AFFAIRS 26 第 3 批）：与生日同开关、
     * 同 birthday 渠道、同补跑与去重机制；只在事件日 −7 天与当天各发一次。
     * 事件日 = 另设庆祝日期（baby_celebrations），没有则按出生日算。
     */
    private suspend fun postBabyCelebrationNotifications(
        today: LocalDate,
        posted: MutableSet<String>,
        overview: io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview,
        birthdayEntries: List<io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry>
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        // 至少一条生日提醒开着的人
        val reminderOn = birthdayEntries
            .filter { it.reminderEnabled }
            .map { it.person.id }.toSet()
        if (reminderOn.isEmpty()) return
        // 另设庆祝日期覆盖：personId → kind → 事件日 epochDay
        val overrides = celebrationRepository.getAll()
            .groupBy { it.personId }
            .mapValues { (_, l) -> l.associate { it.kind to it.epochDay } }
        val nm = NotificationManagerCompat.from(context)
        reminderOn.forEach { personId ->
            val o = overview.byId[personId] ?: return@forEach
            if (o.person.livingStatus !=
                io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE
            ) return@forEach
            val birth = o.solarBirthday?.takeIf { it.isComplete }
                ?.toLocalDateOrNull() ?: return@forEach
            val kinds = overrides[personId].orEmpty()
            listOf(
                io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebrationKind
                    .FULL_MONTH to "满月",
                io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebrationKind
                    .HUNDRED_DAYS to "百天"
            ).forEach { (kind, label) ->
                val computed = when (kind) {
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .BabyCelebrationKind.FULL_MONTH ->
                        io.github.xiaohaoz1.jiarenxia.domain.baby.BabyMath
                            .fullMonthDate(birth)
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .BabyCelebrationKind.HUNDRED_DAYS ->
                        io.github.xiaohaoz1.jiarenxia.domain.baby.BabyMath
                            .hundredDaysDate(birth)
                }
                // 事件日 = 另设庆祝日期，没有则按出生日算
                val event = kinds[kind]?.let { LocalDate.ofEpochDay(it) } ?: computed
                val daysBefore = when {
                    today == event.minusDays(7) -> 7
                    today == event -> 0
                    else -> return@forEach
                }
                val key = "baby:" + personId + ":" + kind.name + ":" +
                    event.toEpochDay() + ":" + daysBefore
                if (key in posted) return@forEach
                val text = if (daysBefore == 7)
                    "7 天后（" + event.monthValue + "月" + event.dayOfMonth +
                        "日）是" + o.person.name + "的" + label
                else "今天是" + o.person.name + "的" + label
                runCatching {
                    nm.notify(key.hashCode(), buildBaby(personId, text))
                }.onSuccess { posted += key }
            }
        }
    }

    /** 宝宝满月/百天通知：birthday 渠道 + 同款锁屏公开文案 + 人物页深链。 */
    private fun buildBaby(personId: String, text: String): android.app.Notification {
        val intent = PendingIntent.getActivity(
            context, ("baby:" + personId + ":" + text).hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PERSON_ID, personId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentText("家人侠：有家人的生日提醒")
            .build()
        return NotificationCompat.Builder(context, CHANNEL_ID)
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
     * 生日当天「发祝福」动作（FAMILY_AFFAIRS 22）：仅生日当天（daysBefore == 0）、
     * 非本人、有默认号码时附带；返回 (号码, 预填正文)。提前提醒与纪念日不加。
     */
    private fun blessingFor(
        d: DueReminder, isSelf: Boolean,
        numbers: Map<String, String>,
        overview: io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
    ): Pair<String, String>? {
        if (!d.kind.isBirthday || d.daysBefore != 0 || isSelf) return null
        val number = numbers[d.personId] ?: return null
        val o = overview.byId[d.personId] ?: return null
        val label = d.title ?: o.person.name
        val elder = io.github.xiaohaoz1.jiarenxia.domain.BirthdayBlessing
            .isElder(o.result)
        return number to io.github.xiaohaoz1.jiarenxia.domain.BirthdayBlessing
            .defaultText(label, elder)
    }

    private fun notificationId(d: DueReminder): Int =
        (d.kind.name + ":" + d.personId + ":" + d.daysBefore +
                (d.milestoneDays?.let { ":" + it } ?: "")).hashCode()

    private fun build(
        d: DueReminder, isSelf: Boolean, channel: String? = null,
        blessing: Pair<String, String>? = null
    ): android.app.Notification {
        val text = ReminderPlanner.text(d, isSelf)
        val resolved = channel ?: if (d.kind.isBirthday) CHANNEL_ID else ANNIV_CHANNEL_ID
        val intent = PendingIntent.getActivity(
            context, notificationId(d),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PERSON_ID, d.personId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(context, resolved)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentText(when (resolved) {
                TOMB_CHANNEL_ID -> "家人侠：有上坟提醒"
                ANNIV_CHANNEL_ID -> "家人侠：有纪念日提醒"
                else -> "家人侠：有家人的生日提醒"
            })
            .build()
        val builder = NotificationCompat.Builder(context, resolved)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("家人侠")
            .setContentText(text)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(intent)
        if (blessing != null) {
            // 「发祝福」：打开短信应用并预填正文，由用户自己点发送
            builder.addAction(
                0, "发祝福",
                PendingIntent.getActivity(
                    context, ("bless:" + d.personId).hashCode(),
                    smsIntent(blessing.first, blessing.second),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }
        return builder.build()
    }

    /**
     * 上坟提醒（FAMILY_AFFAIRS 19）：忌日（挪自「生日与纪念日」段）、
     * 清明/中元/寒衣/除夕/冬至节日与做七/百日，独立渠道 tomb。
     * 节日点开只打开 App；做七点开该人详情；忌日点开人物详情。
     */
    private suspend fun postTombNotifications(today: LocalDate, posted: MutableSet<String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val overview = observeOverview().first() ?: return
        val titles = overview.byId.mapValues { (_, o) ->
            val k = o.result.kind
            if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF) null else o.displayTitle
        }
        val dateEntries = repository.getDateEntries()
        ensureTombChannel()
        val nm = NotificationManagerCompat.from(context)
        val days = settings.tombDays.first()

        // 忌日：沿用 dueDateReminders 的文案与各自提醒偏好；
        // 仅在「忌日」日子开启时发，走 tomb 渠道、点开人物详情
        if (TombDay.DEATH_DAY in days) {
            ReminderPlanner.dueDateReminders(dateEntries, titles, today, lunar)
                .filter { it.kind == ImportantDateType.DEATH_ANNIVERSARY }
                .forEach { d ->
                    val key = "date:" + d.kind.name + ":" + d.personId + ":" + d.daysBefore +
                        (d.milestoneDays?.let { ":" + it } ?: "")
                    if (key in posted) return@forEach
                    val isSelf = overview.byId[d.personId]?.person?.isSelf == true
                    runCatching {
                        nm.notify(notificationId(d), build(d, isSelf, TOMB_CHANNEL_ID))
                    }.onSuccess { posted += key }
                }
        }

        // 节日与做七/百日：固定提前天数命中；做七还要求该忌日条目提醒开着
        val prefs = TombPrefs(
            days = days,
            zhongyuanDay = settings.tombZhongyuanDay.first(),
            excludedIds = settings.tombExcludedPersonIds.first(),
            includedIds = settings.tombIncludedPersonIds.first()
        )
        val reminderOnDeathIds = dateEntries
            .filter {
                it.type == ImportantDateType.DEATH_ANNIVERSARY && it.reminderEnabled
            }
            .mapTo(HashSet()) { it.person.id }
        TombCalendar.events(
            today = today,
            windowDays = TombCalendar.NOTIFY_WINDOW_DAYS,
            lunar = lunar,
            mourners = TombCalendar.mourners(overview),
            deathEntries = dateEntries,
            prefs = prefs
        ).forEach { ev ->
            val daysBefore = TombCalendar.dueDaysBefore(ev, today) ?: return@forEach
            val isMourning = ev.day == TombDay.MOURNING
            if (isMourning && ev.personIds.firstOrNull() !in reminderOnDeathIds)
                return@forEach
            val key = if (isMourning)
                "tomb7:" + ev.personIds.first() + ":" + ev.mourningDayNumber +
                    ":" + daysBefore
            else "tomb:" + ev.day.name + ":" + ev.date.toEpochDay() + ":" + daysBefore
            if (key in posted) return@forEach
            val text = if (isMourning) TombCalendar.mourningText(ev, daysBefore)
            else TombCalendar.festivalText(ev, daysBefore)
            val personId = if (isMourning) ev.personIds.firstOrNull() else null
            runCatching {
                nm.notify(key.hashCode(), buildTomb(key, text, personId))
            }.onSuccess { posted += key }
        }
    }

    /** 上坟节日/做七通知：[personId] 为 null 时点开只进 App（节日）。 */
    private fun buildTomb(
        key: String, text: String, personId: String?
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (personId != null) intent.putExtra(MainActivity.EXTRA_PERSON_ID, personId)
        val pending = PendingIntent.getActivity(
            context, key.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(context, TOMB_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentText("家人侠：有上坟提醒").build()
        return NotificationCompat.Builder(context, TOMB_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("家人侠")
            .setContentText(text)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(pending)
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
        if (dues.isNotEmpty()) {
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
        // 36 周待产包提醒（FAMILY_AFFAIRS 26）：与事项同渠道同每日时刻
        postBirthBagNotifications(today, nowTime, global, posted)
    }

    /**
     * 36 周待产包提醒：预产期前 28 天当天，孕期 ACTIVE、孕妈在世、
     * 待产包未备齐（或还没有清单）时发一次；改预产期自然按新日期算，
     * 不存额外状态。点开孕妈的人物详情页。
     */
    private suspend fun postBirthBagNotifications(
        today: LocalDate,
        nowTime: java.time.LocalTime,
        global: java.time.LocalTime,
        posted: MutableSet<String>
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        // 家庭事项每日时刻未到不发（与事项段同一口径，含当天补跑）
        if (nowTime.isBefore(global)) return
        val active = pregnancyRepository.observeActive().first()
        if (active.isEmpty()) return
        val overview = observeOverview().first() ?: return
        val nm = NotificationManagerCompat.from(context)
        active.forEach { preg ->
            // 今天 == 预产期 − 28 天（孕 36 周）
            if (today != preg.dueDate.minusDays(28)) return@forEach
            val entry = overview.byId[preg.personId] ?: return@forEach
            val person = entry.person
            if (person.livingStatus !=
                io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE
            ) return@forEach
            // 已有待产包且全部勾完 → 不提醒；没有清单或未备齐 → 提醒
            val bag = checklistRepository.observeForPregnancy(
                preg.id,
                io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind.BIRTH_BAG
            ).first()
            if (bag != null && bag.totalCount > 0 &&
                bag.checkedCount == bag.totalCount
            ) return@forEach
            val key = "birthbag:" + preg.id + ":" + today.toEpochDay()
            if (key in posted) return@forEach
            val text = if (person.isSelf) "孕 36 周了，待产包备齐了吗？"
            else {
                val title = entry.displayTitle.ifBlank { person.name }
                title + "孕 36 周了，待产包备齐了吗？"
            }
            runCatching {
                nm.notify(key.hashCode(), buildBirthBag(key, text, person.id))
            }.onSuccess { posted += key }
        }
    }

    /** 36 周提醒通知：家庭事项渠道，点开孕妈人物详情页。 */
    private fun buildBirthBag(
        key: String, text: String, personId: String
    ): android.app.Notification {
        ensureTaskChannel()
        val intent = PendingIntent.getActivity(
            context, key.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PERSON_ID, personId)
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
            .setContentText(text)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(intent)
            .build()
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

    /**
     * 回家看看提醒（FAMILY_AFFAIRS 20）：跟随「联系家人」的开关与时刻，
     * 不新增检查时刻。到期提醒（到期当天 + 每 7 天复提）与节前提醒
     * （春节/中秋/国庆/成员生日前 7 天）共用 home_visit 渠道；
     * 点开只打开 App（无 extra）。
     */
    private suspend fun postHomeVisitNotifications(today: LocalDate, posted: MutableSet<String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val plans = homeVisitRepository.getHomes()
        if (plans.isEmpty()) return
        val overview = observeOverview().first() ?: return
        val states = HomeVisitSchedule.homes(
            plans, overview, contactRepository.getLogs(), today)
        if (states.isEmpty()) return
        ensureHomeChannel()
        val nm = NotificationManagerCompat.from(context)

        // 到期：与联系提醒同一 remindToday 口径（到期当天一次，之后每 7 天）
        states.forEach { home ->
            val key = "home:" + home.planId
            if (key in posted) return@forEach
            if (!io.github.xiaohaoz1.jiarenxia.domain.ContactSchedule
                    .remindToday(home.status, today)
            ) return@forEach
            runCatching {
                nm.notify(key.hashCode(), buildHome(key, HomeVisitSchedule.dueText(home)))
            }.onSuccess { posted += key }
        }

        // 节前提醒：独立开关 homeVisitHolidayNudge
        if (settings.homeVisitHolidayNudge.first()) {
            HomeVisitSchedule.preHoliday(states, overview, lunar, today)
                .forEach { ev ->
                    if (ev.dedupKey in posted) return@forEach
                    runCatching {
                        nm.notify(ev.dedupKey.hashCode(), buildHome(ev.dedupKey, ev.text))
                    }.onSuccess { posted += ev.dedupKey }
                }
        }
    }

    /** 回家看看通知：点开只打开 App。 */
    private fun buildHome(key: String, text: String): android.app.Notification {
        val intent = PendingIntent.getActivity(
            context, key.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(context, HOME_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentText("家人侠：该回家看看了")
            .build()
        return NotificationCompat.Builder(context, HOME_CHANNEL_ID)
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
     * 用药提醒（FAMILY_AFFAIRS 23）：疗程到期（记录的最后一天当天）与
     * 续药（下次拿药日前 3 天与当天）。独立渠道 medication；
     * 仅当该人物「疗程到期与续药提醒」开关为真（无记录按默认开）才发；
     * 点开该人物详情页。锁屏公开文案不带药名。
     */
    private suspend fun postMedicationNotifications(
        today: LocalDate,
        posted: MutableSet<String>
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val meds = medicationRepository.getAll()
        if (meds.isEmpty()) return
        val overview = observeOverview().first() ?: return
        // 按人开关：无记录的行按默认开
        val prefs = medicationRepository.observePrefs().first()
            .associate { it.personId to it.courseRefillEnabled }
        ensureMedChannel()
        val nm = NotificationManagerCompat.from(context)
        meds.forEach { med ->
            val entry = overview.byId[med.personId] ?: return@forEach
            val person = entry.person
            if (person.livingStatus !=
                io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.ALIVE
            ) return@forEach
            if (prefs[med.personId] == false) return@forEach
            val isSelf = person.isSelf
            // 称谓无（SELF/UNRELATED/空）则用姓名
            val label = entry.displayTitle.ifBlank { person.name }
            io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule.duesOn(med, today)
                .forEach { due ->
                    val key = when (due.kind) {
                        io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
                            .DueKind.COURSE_END ->
                            "medcourse:" + med.id + ":" + due.epochDay
                        io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
                            .DueKind.REFILL ->
                            "medrefill:" + med.id + ":" + due.epochDay +
                                ":" + due.daysBefore
                    }
                    if (key in posted) return@forEach
                    val text = when (due.kind) {
                        io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
                            .DueKind.COURSE_END ->
                            if (isSelf)
                                "今天是" + med.name +
                                    "记录疗程的最后一天，之后怎么吃请按医嘱确认"
                            else
                                "今天是" + label + "的" + med.name +
                                    "记录疗程的最后一天，之后怎么吃请按医嘱确认"
                        io.github.xiaohaoz1.jiarenxia.domain.med.MedSchedule
                            .DueKind.REFILL -> {
                            val day = LocalDate.ofEpochDay(due.epochDay)
                            val date = day.monthValue.toString() + "月" +
                                day.dayOfMonth + "日"
                            if (due.daysBefore == 3) {
                                if (isSelf) "3 天后（" + date + "）该去拿" +
                                    med.name + "了"
                                else "3 天后（" + date + "）该给" + label +
                                    "拿" + med.name + "了"
                            } else {
                                if (isSelf) "今天该去拿" + med.name + "了"
                                else "今天该给" + label + "拿" + med.name + "了"
                            }
                        }
                    }
                    runCatching {
                        nm.notify(key.hashCode(), buildMed(key, text, med.personId))
                    }.onSuccess { posted += key }
                }
        }
    }

    /** 用药提醒通知：独立渠道，点开人物详情页；锁屏公开版不带药名。 */
    private fun buildMed(
        key: String, text: String, personId: String
    ): android.app.Notification {
        val intent = PendingIntent.getActivity(
            context, key.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PERSON_ID, personId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(context, MED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentText("家人侠：有用药提醒")
            .build()
        return NotificationCompat.Builder(context, MED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("家人侠")
            .setContentText(text)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(intent)
            .build()
    }

    private fun ensureMedChannel() {
        val channel = NotificationChannel(
            MED_CHANNEL_ID, "用药提醒", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun ensureHomeChannel() {
        val channel = NotificationChannel(
            HOME_CHANNEL_ID, "回家看看", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
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
            ANNIV_CHANNEL_ID, "纪念日", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(chan)
    }

    private fun ensureTombChannel() {
        val channel = NotificationChannel(
            TOMB_CHANNEL_ID, "上坟提醒", NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
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
        const val TOMB_CHANNEL_ID = "tomb"
        const val HOME_CHANNEL_ID = "home_visit"
        const val MED_CHANNEL_ID = "medication"
        const val KEY_FORCE = "force"
    }
}
