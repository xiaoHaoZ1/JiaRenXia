package io.github.xiaohaoz1.jiarenxia.reminder

import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 提醒同步器（DEVELOPMENT_PLAN 2.5）：应用启动时 reconcile 一次，
 * 随后每当生日/纪念日数据、事项数据、联系计划与记录、用药记录与按人开关、
 * 五类开关、各自的提醒时刻、用药开关与时刻或上坟日子/名单设置变化
 * （防抖 500ms）重新调度。
 * WorkManager 会跨重启保持任务，无需 BOOT 广播。
 */
@Singleton
class ReminderSync @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val repository: FamilyRepository,
    private val taskRepository: io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository,
    private val contactRepository: io.github.xiaohaoz1.jiarenxia.data.ContactRepository,
    private val homeVisitRepository: io.github.xiaohaoz1.jiarenxia.data
        .HomeVisitRepository,
    private val celebrationRepository: io.github.xiaohaoz1.jiarenxia.data
        .BabyCelebrationRepository,
    private val medicationRepository: io.github.xiaohaoz1.jiarenxia.data
        .MedicationRepository,
    private val settings: SettingsRepository,
    private val medAlarmScheduler: MedAlarmScheduler,
    @ApplicationScope private val scope: CoroutineScope
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            scheduler.reconcile()
            medAlarmScheduler.rescheduleAll()
        }
        scope.launch {
            // 数据侧：生日、纪念日/忌日、家庭事项、联系计划与联系记录、回家看看计划
            val dataWatch = combine<Any?, DataWatch>(
                repository.observeBirthdayEntries(),
                repository.observeDateEntries(),
                taskRepository.observeTasks(),
                contactRepository.observePlanList(),
                contactRepository.observeAllLogs(),
                homeVisitRepository.observeHomes(),
                celebrationRepository.observeAll(),
                medicationRepository.observeAll(),
                medicationRepository.observePrefs()
            ) { arr ->
                @Suppress("UNCHECKED_CAST")
                DataWatch(
                    arr[0] as List<io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry>,
                    arr[1] as List<io.github.xiaohaoz1.jiarenxia.data.DateEntry>,
                    arr[2] as List<io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask>,
                    arr[3] as List<io.github.xiaohaoz1.jiarenxia.data.ContactPlan>,
                    arr[4] as List<io.github.xiaohaoz1.jiarenxia.core.model.ContactLog>,
                    arr[5] as List<io.github.xiaohaoz1.jiarenxia.data.HomePlan>,
                    arr[6] as List<io.github.xiaohaoz1.jiarenxia.core.model
                        .BabyCelebration>,
                    arr[7] as List<io.github.xiaohaoz1.jiarenxia.core.model.Medication>,
                    arr[8] as List<io.github.xiaohaoz1.jiarenxia.core.model
                        .MedicationPersonPrefs>
                )
            }
            // 设置侧：四个总开关、四个提醒时刻、上坟日子/名单与回家节前提醒开关
            val settingsWatch = combine<Any?, SettingsWatch>(
                settings.remindersEnabled,
                settings.taskRemindersEnabled,
                settings.contactRemindersEnabled,
                settings.tombRemindersEnabled,
                settings.reminderMinuteOfDay,
                settings.taskReminderMinuteOfDay,
                settings.contactReminderMinuteOfDay,
                settings.tombReminderMinuteOfDay,
                settings.tombDays,
                settings.tombZhongyuanDay,
                settings.tombExcludedPersonIds,
                settings.tombIncludedPersonIds,
                settings.homeVisitHolidayNudge,
                settings.medRemindersEnabled,
                settings.medReminderMinuteOfDay,
                settings.medOnTimeEnabled
            ) { v ->
                SettingsWatch(
                    v[0] as Boolean, v[1] as Boolean, v[2] as Boolean,
                    v[3] as Boolean,
                    v[4] as Int, v[5] as Int, v[6] as Int, v[7] as Int,
                    (v[8] as Set<*>).filterIsInstance<io.github.xiaohaoz1.jiarenxia
                        .domain.tomb.TombDay>().toSet(),
                    v[9] as Int,
                    (v[10] as Set<*>).filterIsInstance<String>().toSet(),
                    (v[11] as Set<*>).filterIsInstance<String>().toSet(),
                    v[12] as Boolean,
                    v[13] as Boolean, v[14] as Int, v[15] as Boolean
                )
            }
            combine(dataWatch, settingsWatch) { d, s -> ReminderWatch(d, s) }
                .distinctUntilChanged()
                .debounce(500)
                .collectLatest {
                    scheduler.reconcile()
                    // 「按时吃」闹钟：用药数据/每人开关/三个用药设置变化、
                    // 备份恢复与清空数据（数据流触发）都经这里全量重排
                    medAlarmScheduler.rescheduleAll()
                }
        }
    }

    private data class DataWatch(
        val birthdays: List<io.github.xiaohaoz1.jiarenxia.data.BirthdayEntry>,
        val dates: List<io.github.xiaohaoz1.jiarenxia.data.DateEntry>,
        val tasks: List<io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask>,
        val contactPlans: List<io.github.xiaohaoz1.jiarenxia.data.ContactPlan>,
        val contactLogs: List<io.github.xiaohaoz1.jiarenxia.core.model.ContactLog>,
        val homeVisits: List<io.github.xiaohaoz1.jiarenxia.data.HomePlan>,
        val babyCelebrations: List<io.github.xiaohaoz1.jiarenxia.core.model
            .BabyCelebration>,
        val medications: List<io.github.xiaohaoz1.jiarenxia.core.model.Medication>,
        val medicationPrefs: List<io.github.xiaohaoz1.jiarenxia.core.model
            .MedicationPersonPrefs>
    )

    private data class SettingsWatch(
        val remindersEnabled: Boolean,
        val taskRemindersEnabled: Boolean,
        val contactRemindersEnabled: Boolean,
        val tombRemindersEnabled: Boolean,
        val birthdayMinute: Int,
        val taskMinute: Int,
        val contactMinute: Int,
        val tombMinute: Int,
        val tombDays: Set<io.github.xiaohaoz1.jiarenxia.domain.tomb.TombDay>,
        val tombZhongyuanDay: Int,
        val tombExcludedIds: Set<String>,
        val tombIncludedIds: Set<String>,
        val homeVisitHolidayNudge: Boolean,
        val medRemindersEnabled: Boolean,
        val medMinute: Int,
        val medOnTimeEnabled: Boolean
    )

    private data class ReminderWatch(val data: DataWatch, val settings: SettingsWatch)
}
