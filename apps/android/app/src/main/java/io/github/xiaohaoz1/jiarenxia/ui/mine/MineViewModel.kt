package io.github.xiaohaoz1.jiarenxia.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.FontScaleLevel
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import io.github.xiaohaoz1.jiarenxia.data.settings.WidgetThemeMode
import io.github.xiaohaoz1.jiarenxia.domain.ClearAllDataUseCase
import io.github.xiaohaoz1.jiarenxia.domain.ceremony.WeddingWords
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MineUiState(
    val self: Person? = null,
    val remindersEnabled: Boolean = true,
    val taskRemindersEnabled: Boolean = true,
    val contactRemindersEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    /** 桌面插件配色（跟随系统/浅色/深色），默认跟随系统。 */
    val widgetThemeMode: WidgetThemeMode = WidgetThemeMode.SYSTEM,
    val fontScaleLevel: FontScaleLevel = FontScaleLevel.STANDARD,
    val cleared: Boolean = false,
    /** 生日与纪念日提醒时刻（一天内分钟数，默认 09:00）。 */
    val reminderMinuteOfDay: Int = io.github.xiaohaoz1.jiarenxia.reminder.ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    /** 家庭事项提醒时刻（一天内分钟数，默认 09:00）。 */
    val taskReminderMinuteOfDay: Int = io.github.xiaohaoz1.jiarenxia.reminder.ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    /** 联系提醒时刻（一天内分钟数，默认 09:00）。 */
    val contactReminderMinuteOfDay: Int = io.github.xiaohaoz1.jiarenxia.reminder.ReminderTiming.DEFAULT_MINUTE_OF_DAY,
    /** 上次成功导出备份的时刻（毫秒），从未备份过为 null。 */
    val lastBackupAt: Long? = null,
    /** Snackbar 提示（备份/恢复结果等），一次性事件。 */
    val message: String? = null,
    /** 婚礼祝词原始串（“左|右”），展示经 WeddingWords.split。 */
    val weddingWords: String = WeddingWords.join(WeddingWords.DEFAULT)
)

@HiltViewModel
class MineViewModel @Inject constructor(
    private val repository: FamilyRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val clearAll: ClearAllDataUseCase,
    private val backup: io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager,
    private val appEvents: io.github.xiaohaoz1.jiarenxia.data.AppEvents
) : ViewModel() {

    // ---------- 备份 / 恢复 ----------

    private val message = MutableStateFlow<String?>(null)

    /** 备份导出流程（与关系图共用 ui/backup/BackupExportFlow）。 */
    val backupExport = io.github.xiaohaoz1.jiarenxia.ui.backup.BackupExportFlow(
        scope = viewModelScope,
        backup = backup,
        settings = settings,
        onMessage = { message.value = it }
    )

    /**
     * 恢复流程（与欢迎页共用 ui/restore/RestoreFlow）。
     * “我的”页能打开即本机已有数据 → hasLocalData 恒 true（确认页永远带覆盖警告）。
     */
    val restore = io.github.xiaohaoz1.jiarenxia.ui.restore.RestoreFlow(
        scope = viewModelScope,
        backup = backup,
        appEvents = appEvents,
        hasLocalData = { true },
        onMessage = { message.value = it }
    )

    // 「分享给家人」直接进选人页（ui/share/ShareScopeScreen）——
    // 共享文件只含家人档案，这里不再有分享状态机。

    fun onMessageShown() { message.value = null }

    private val cleared = MutableStateFlow(false)

    val ui: StateFlow<MineUiState> = combine(
        repository.observeSelf(), settings.remindersEnabled, settings.themeMode,
        settings.fontScaleLevel, cleared
    ) { self, enabled, mode, level, c ->
        MineUiState(self, enabled, themeMode = mode, fontScaleLevel = level, cleared = c)
    }
        .combine(settings.taskRemindersEnabled) { st, t -> st.copy(taskRemindersEnabled = t) }
        .combine(settings.contactRemindersEnabled) { st, c ->
            st.copy(contactRemindersEnabled = c) }
        .combine(settings.reminderMinuteOfDay) { st, m -> st.copy(reminderMinuteOfDay = m) }
        .combine(settings.taskReminderMinuteOfDay) { st, m ->
            st.copy(taskReminderMinuteOfDay = m) }
        .combine(settings.contactReminderMinuteOfDay) { st, m ->
            st.copy(contactReminderMinuteOfDay = m) }
        .combine(settings.widgetThemeMode) { st, m -> st.copy(widgetThemeMode = m) }
        .combine(settings.lastBackupAt) { st, t -> st.copy(lastBackupAt = t) }
        .combine(message) { st, m -> st.copy(message = m) }
        .combine(settings.weddingWords) { st, w -> st.copy(weddingWords = w) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MineUiState())

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setRemindersEnabled(enabled) }
    }

    /** 家庭事项提醒总开关（与生日提醒互不影响）。 */
    fun setTaskRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setTaskRemindersEnabled(enabled) }
    }

    /** 联系提醒总开关。 */
    fun setContactRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setContactRemindersEnabled(enabled) }
    }

    /** 婚礼祝词（左词/右词）；落库前由 settings 归一化。 */
    fun setWeddingWords(words: Pair<String, String>) {
        viewModelScope.launch { settings.setWeddingWords(WeddingWords.join(words)) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** 桌面插件配色；保存后由 WidgetRefresh 监听到设置变化即时重绘。 */
    fun setWidgetThemeMode(mode: WidgetThemeMode) {
        viewModelScope.launch { settings.setWidgetThemeMode(mode) }
    }

    /** 设置应用内字体大小级别（我的 → 外观）。 */
    fun setFontScaleLevel(level: FontScaleLevel) {
        viewModelScope.launch { settings.setFontScaleLevel(level) }
    }

    /** 设置生日与纪念日提醒时间；调度由 ReminderSync 随设置变化重排。 */
    fun setReminderMinuteOfDay(minute: Int) {
        viewModelScope.launch { settings.setReminderMinuteOfDay(minute) }
    }

    /** 设置家庭事项提醒时间；调度由 ReminderSync 随设置变化重排。 */
    fun setTaskReminderMinuteOfDay(minute: Int) {
        viewModelScope.launch { settings.setTaskReminderMinuteOfDay(minute) }
    }

    /** 设置联系提醒时间；调度由 ReminderSync 随设置变化重排。 */
    fun setContactReminderMinuteOfDay(minute: Int) {
        viewModelScope.launch { settings.setContactReminderMinuteOfDay(minute) }
    }

    /** 调试：立即执行一次提醒检查（忽略各类时刻与当天是否已发）。 */
    fun debugCheckReminders() {
        scheduler.enqueueDebugRun()
    }

    fun clearAllData() {
        viewModelScope.launch {
            clearAll.run()
            cleared.value = true
        }
    }
}
