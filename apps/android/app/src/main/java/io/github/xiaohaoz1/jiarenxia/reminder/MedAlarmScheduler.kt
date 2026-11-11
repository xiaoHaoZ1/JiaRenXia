package io.github.xiaohaoz1.jiarenxia.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.MainActivity
import io.github.xiaohaoz1.jiarenxia.R
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.data.MedicationRepository
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.med.MedOnTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AlarmManager 的薄封装：把 exact / inexact / cancel 收敛成三个动词，
 * 生产实现走系统 AlarmManager，测试可替换断言。
 */
interface MedAlarmGateway {
    /** Android 12+ 无精确闹钟权限时返回 false；12 以下恒 true。 */
    fun canScheduleExact(): Boolean
    fun setExact(requestCode: Int, triggerAtMillis: Long, pi: PendingIntent)
    fun setInexact(requestCode: Int, triggerAtMillis: Long, pi: PendingIntent)
    fun cancel(requestCode: Int, pi: PendingIntent)
}

@Singleton
class AlarmManagerGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : MedAlarmGateway {
    private val am get() = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (am?.canScheduleExactAlarms() ?: false)

    override fun setExact(requestCode: Int, triggerAtMillis: Long, pi: PendingIntent) {
        am?.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    override fun setInexact(requestCode: Int, triggerAtMillis: Long, pi: PendingIntent) {
        am?.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
    }

    override fun cancel(requestCode: Int, pi: PendingIntent) {
        am?.cancel(pi)
    }
}

/**
 * 「按时吃」闹钟调度（FAMILY_AFFAIRS 23）：
 * 数据库是唯一事实来源——每次重排先取消上次排过的全部槽位
 * （槽位清单持久化在 SharedPreferences），再按 [MedOnTime.slots]
 * 与 [MedOnTime.nextTrigger] 重排。有精确闹钟权限走
 * setExactAndAllowWhileIdle，否则降级 setAndAllowWhileIdle；
 * 通知权限关闭或「按时吃药」渠道被关时不排。
 * 不用常驻服务；系统杀进程后由开机/时间变更广播与下次启动恢复。
 */
@Singleton
class MedAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val medicationRepository: MedicationRepository,
    private val observeOverview: ObserveFamilyOverview,
    private val settings: SettingsRepository,
    private val gateway: MedAlarmGateway,
    private val clock: TimeSource
) {

    private val mutex = Mutex()

    private data class Snapshot(
        val slots: List<MedOnTime.Slot>,
        val labels: Map<String, String>,
        val selfId: String?
    )

    /** 当前可排槽位；任一前置条件不满足返回 null（调用方据此清空闹钟）。 */
    private suspend fun snapshot(): Snapshot? {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return null
        val channel = nm.getNotificationChannel(MED_ON_TIME_CHANNEL)
        if (channel != null &&
            channel.importance == NotificationManager.IMPORTANCE_NONE
        ) return null
        val remindersOn = settings.medRemindersEnabled.first()
        val onTimeOn = settings.medOnTimeEnabled.first()
        if (!remindersOn || !onTimeOn) return null
        val now = nowZoned()
        val today = now.toLocalDate()
        val overview = observeOverview().first()
        val selfId = overview?.self?.person?.id
        val livingIds = overview?.byId.orEmpty().values
            .filter { it.person.livingStatus == LivingStatus.ALIVE }
            .map { it.person.id }
            .toSet()
        val meds = medicationRepository.getAll()
        val prefs = medicationRepository.observePrefs().first()
            .associateBy { it.personId }
        val slots = MedOnTime.slots(
            meds, prefs, selfId, livingIds,
            MedOnTime.Settings(remindersOn, onTimeOn), today)
        val titles = overview?.byId.orEmpty().mapValues { (_, o) ->
            val k = o.result.kind
            if (k == KinshipKind.UNRELATED || k == KinshipKind.SELF)
                o.person.name else o.displayTitle
        }
        return Snapshot(slots, titles, selfId)
    }

    /** 全量重排：先取消已排槽位，再按当前数据逐槽重排并持久化清单。 */
    suspend fun rescheduleAll() = mutex.withLock {
        val snap = snapshot()
        storedKeys().forEach { cancelStored(it) }
        if (snap == null) {
            storeKeys(emptySet())
            return@withLock
        }
        val now = nowZoned()
        val next = mutableSetOf<String>()
        snap.slots.forEach { slot ->
            val trigger = MedOnTime.nextTrigger(slot, now) ?: return@forEach
            val key = slotKey(slot.personId, slot.minute)
            val rc = requestCode(slot.personId, slot.minute)
            val pi = alarmPendingIntent(
                slot.personId, slot.minute,
                trigger.toInstant().toEpochMilli())
            if (gateway.canScheduleExact()) {
                gateway.setExact(rc, trigger.toInstant().toEpochMilli(), pi)
            } else {
                gateway.setInexact(rc, trigger.toInstant().toEpochMilli(), pi)
            }
            next += key
        }
        storeKeys(next)
    }

    /**
     * 闹钟触发：按当前数据重算该槽，deliveryText 为 null 不发；
     * 发完（或本就不发）按当前数据全量重排，保证该槽排上下一次。
     */
    suspend fun onAlarmFired(
        personId: String,
        minute: Int,
        scheduledAtMillis: Long
    ) {
        val snap = snapshot()
        val slot = snap?.slots?.firstOrNull {
            it.personId == personId && it.minute == minute
        }
        if (slot != null) {
            val fireDate = Instant.ofEpochMilli(scheduledAtMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val meds = slot.meds.filter {
                MedOnTime.scheduledOn(it, fireDate)
            }
            if (meds.isNotEmpty()) {
                val scheduledAt = Instant.ofEpochMilli(scheduledAtMillis)
                val now = Instant.ofEpochMilli(clock.now())
                val isSelf = snap.selfId == personId
                val text = MedOnTime.deliveryText(
                    personLabel = if (isSelf) null else snap.labels[personId],
                    isSelf = isSelf,
                    minute = minute,
                    meds = meds,
                    scheduledAt = scheduledAt,
                    now = now
                )
                if (text != null) postNotification(personId, minute, text)
            }
        }
        rescheduleAll()
    }

    // ---------- 通知 ----------

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
            ?: return
        if (nm.getNotificationChannel(MED_ON_TIME_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    MED_ON_TIME_CHANNEL, "按时吃药",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun postNotification(personId: String, minute: Int, text: String) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val nm = NotificationManagerCompat.from(context)
        ensureChannel()
        val notifId = ("medtime:" + personId + ":" + minute).hashCode()
        val intent = PendingIntent.getActivity(
            context, notifId,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PERSON_ID, personId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val publicVersion = NotificationCompat.Builder(context, MED_ON_TIME_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("家人侠：有吃药提醒")
            .build()
        runCatching {
            nm.notify(
                notifId,
                NotificationCompat.Builder(context, MED_ON_TIME_CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat_notify)
                    .setContentTitle(text)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion)
                    .setContentIntent(intent)
                    .build()
            )
        }
    }

    // ---------- 闹钟与持久化 ----------

    private fun nowZoned(): ZonedDateTime =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault())

    private fun slotKey(personId: String, minute: Int) = "$personId:$minute"

    private fun requestCode(personId: String, minute: Int) =
        ("medalarm:" + personId + ":" + minute).hashCode()

    private fun alarmPendingIntent(
        personId: String,
        minute: Int,
        scheduledAtMillis: Long
    ): PendingIntent {
        val intent = Intent(context, MedAlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_PERSON_ID, personId)
            .putExtra(EXTRA_MINUTE, minute)
            .putExtra(EXTRA_SCHEDULED_AT, scheduledAtMillis)
        return PendingIntent.getBroadcast(
            context, requestCode(personId, minute), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelStored(key: String) {
        val i = key.indexOf(':')
        if (i <= 0) return
        val personId = key.substring(0, i)
        val minute = key.substring(i + 1).toIntOrNull() ?: return
        val rc = requestCode(personId, minute)
        gateway.cancel(rc, alarmPendingIntent(personId, minute, 0L))
    }

    private fun prefs() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun storedKeys(): Set<String> =
        prefs().getStringSet(KEY_SLOTS, emptySet()).orEmpty()

    private fun storeKeys(keys: Set<String>) {
        prefs().edit().putStringSet(KEY_SLOTS, keys).apply()
    }

    companion object {
        const val MED_ON_TIME_CHANNEL = "med_on_time"
        const val ACTION_FIRE =
            "io.github.xiaohaoz1.jiarenxia.action.MED_ON_TIME"
        const val EXTRA_PERSON_ID = "medOnTimePersonId"
        const val EXTRA_MINUTE = "medOnTimeMinute"
        const val EXTRA_SCHEDULED_AT = "medOnTimeScheduledAt"
        private const val PREFS_NAME = "med_alarms"
        private const val KEY_SLOTS = "scheduled_slots"
    }
}
