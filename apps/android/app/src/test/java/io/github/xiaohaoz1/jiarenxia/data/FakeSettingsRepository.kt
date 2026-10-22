package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** 共享内存设置实现（新测试用；各测试文件里已有的私有假实现保持不动）。 */
class FakeSettingsRepository : SettingsRepository {
    var runState: Pair<Long, Set<String>>? = null
    override val remindersEnabled: Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setRemindersEnabled(enabled: Boolean) {}
        override val taskRemindersEnabled = MutableStateFlow(true)
    override val contactRemindersEnabled = MutableStateFlow(true)
        override suspend fun setTaskRemindersEnabled(enabled: Boolean) {
            taskRemindersEnabled.value = enabled
        }
    override suspend fun setContactRemindersEnabled(enabled: Boolean) {
        contactRemindersEnabled.value = enabled
    }


    override suspend fun getReminderRunState() = runState
    override suspend fun setReminderRunState(epochDay: Long, notifiedKeys: Set<String>) {
        runState = epochDay to notifiedKeys
    }
    override suspend fun clearReminderRunState() { runState = null }
    override val reminderMinuteOfDay = MutableStateFlow(540)
    override suspend fun setReminderMinuteOfDay(minute: Int) {
        reminderMinuteOfDay.value = minute
    }
    override val taskReminderMinuteOfDay = MutableStateFlow(540)
    override suspend fun setTaskReminderMinuteOfDay(minute: Int) {
        taskReminderMinuteOfDay.value = minute
    }
    override val contactReminderMinuteOfDay = MutableStateFlow(540)
    override suspend fun setContactReminderMinuteOfDay(minute: Int) {
        contactReminderMinuteOfDay.value = minute
    }
    override val notificationPermissionRequested: Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setNotificationPermissionRequested(requested: Boolean) {}
    override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: ThemeMode) {}
    override val fontScaleLevel = MutableStateFlow(FontScaleLevel.STANDARD)
    override suspend fun setFontScaleLevel(level: FontScaleLevel) { fontScaleLevel.value = level }
    override val lastBackupAt = MutableStateFlow<Long?>(null)
    override suspend fun setLastBackupAt(atMillis: Long) { lastBackupAt.value = atMillis }
    override val testToolsEnabled = MutableStateFlow(false)
    override suspend fun setTestToolsEnabled(enabled: Boolean) {
        testToolsEnabled.value = enabled
    }
    override val updateCheckEnabled = MutableStateFlow(false)
    override suspend fun setUpdateCheckEnabled(enabled: Boolean) {
        updateCheckEnabled.value = enabled
    }
    override val updateLastCheckAt = MutableStateFlow(0L)
    override suspend fun setUpdateLastCheckAt(at: Long) { updateLastCheckAt.value = at }
    override val updateKnownCode = MutableStateFlow(0)
    override suspend fun setUpdateKnownCode(code: Int) { updateKnownCode.value = code }
    override val updateKnownName = MutableStateFlow("")
    override suspend fun setUpdateKnownName(name: String) { updateKnownName.value = name }
    override val updateKnownNotes = MutableStateFlow("")
    override suspend fun setUpdateKnownNotes(notes: String) { updateKnownNotes.value = notes }
    override val updateSkippedCode = MutableStateFlow(0)
    override suspend fun setUpdateSkippedCode(code: Int) { updateSkippedCode.value = code }
    override suspend fun clear() {}
}
