package io.github.xiaohaoz1.jiarenxia.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.domain.ceremony.WeddingWords
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 应用设置（DataStore Preferences “settings”）。
 */
interface SettingsRepository {
    /** 生日提醒总开关，默认开启。 */
    val remindersEnabled: Flow<Boolean>
    suspend fun setRemindersEnabled(enabled: Boolean)

    /** 家庭事项提醒总开关，默认开启（FAMILY_AFFAIRS 5.4）。 */
    val taskRemindersEnabled: Flow<Boolean>
    suspend fun setTaskRemindersEnabled(enabled: Boolean)

    /** 联系提醒总开关，默认开启；只对设了周期的人生效。 */
    val contactRemindersEnabled: Flow<Boolean>
    suspend fun setContactRemindersEnabled(enabled: Boolean)

    /**
     * 每日提醒检查的运行状态：first = 上次运行的日期（epochDay），
     * second = 当天已发出通知的提醒键。同一天再次运行的检查只补发
     * 没通知过的提醒（数据改动后能立刻提醒且不重复打扰）；null = 还没跑过。
     * 接口提供默认实现，不关心提醒的测试假类无需逐个补。
     */
    suspend fun getReminderRunState(): Pair<Long, Set<String>>? = null
    suspend fun setReminderRunState(epochDay: Long, notifiedKeys: Set<String>) {}
    /** 清除运行状态（恢复备份 / 共享导入后重建调度）。 */
    suspend fun clearReminderRunState() {}

    /** 生日与纪念日提醒的检查时间（一天内的分钟数 0..1439），默认 540（09:00）。 */
    val reminderMinuteOfDay: Flow<Int>
    suspend fun setReminderMinuteOfDay(minute: Int)

    /** 家庭事项提醒的检查时间（一天内的分钟数），默认 540（09:00）。 */
    val taskReminderMinuteOfDay: Flow<Int>
    suspend fun setTaskReminderMinuteOfDay(minute: Int)

    /** 联系提醒的检查时间（一天内的分钟数），默认 540（09:00）。 */
    val contactReminderMinuteOfDay: Flow<Int>
    suspend fun setContactReminderMinuteOfDay(minute: Int)

    /** 是否已申请过通知权限（用于“先说明用途再申请”的引导）。 */
    val notificationPermissionRequested: Flow<Boolean>
    suspend fun setNotificationPermissionRequested(requested: Boolean)

    /** 外观模式，默认浅色（温馨暖色）。 */
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    /** 应用内字体大小级别，默认标准（叠加在系统字号之上）。 */
    val fontScaleLevel: Flow<FontScaleLevel>
    suspend fun setFontScaleLevel(level: FontScaleLevel)

    /**
     * 上次成功导出备份的时刻（epoch 毫秒），从未备份过为 null。
     * 本机记录，不进备份文件；恢复备份不改它。
     */
    val lastBackupAt: Flow<Long?>
    suspend fun setLastBackupAt(atMillis: Long)

    /**
     * “测试工具”（快速存档/读档）入口开关：正式版默认隐藏，
     * 在“关于家人侠”的版本号上连点 7 次切换；debug 构建恒显示。
     * 本机偏好，不进备份。接口提供默认实现，测试假类无需逐个补。
     */
    val testToolsEnabled: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setTestToolsEnabled(enabled: Boolean) {}

    /** 关系图“关系词”开关：每条关系线旁标注父子/夫妻/兄弟等，默认关闭。 */
    val edgeLabelsEnabled: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setEdgeLabelsEnabled(enabled: Boolean) {}

    /**
     * 新手引导（首次启动的四页轮播）是否已看完：默认未看。
     * 只在新用户路径（启动页 → 无“我” → 引导）自动弹出；跳过与“开始使用”都记为已看。
     * 我的页“新手引导”可随时重看。本机偏好，不进备份。
     * 接口默认实现返回“已看”，测试假类不会意外触发引导，无需逐个补。
     */
    val introSeen: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(true)
    suspend fun setIntroSeen(seen: Boolean) {}

    /**
     * 婚礼祝词（碰一碰婚礼模式左右两词，“左|右”），默认“喜结|连理”。
     * 碰一碰时随 HELLO 带给对方，两端不一致以读卡端为准。
     * 本机偏好，不进备份；接口提供默认实现，测试假类无需逐个补。
     */
    val weddingWords: Flow<String> get() =
        kotlinx.coroutines.flow.flowOf(WeddingWords.join(WeddingWords.DEFAULT))
    suspend fun setWeddingWords(raw: String) {}

    /**
     * 「联网检查更新」开关：默认关闭，关闭时 App 完全不联网（INTERNET
     * 权限虽随包声明，但所有联网代码都受本开关门控）。开启即表示用户
     * 同意联网——仅用于查询官网版本信息与下载安装包，可随时关闭。
     * 本机偏好，不进备份。
     */
    val updateCheckEnabled: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setUpdateCheckEnabled(enabled: Boolean) {}

    /** 上次执行更新检查的时刻（毫秒），0 = 从未查过；自动检查按此节流。 */
    val updateLastCheckAt: Flow<Long> get() = kotlinx.coroutines.flow.flowOf(0L)
    suspend fun setUpdateLastCheckAt(at: Long) {}

    /** 最近一次检查发现的最新版本号（versionCode）；0 = 没有更新的版本。 */
    val updateKnownCode: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(0)
    suspend fun setUpdateKnownCode(code: Int) {}

    /** 与 [updateKnownCode] 对应的版本名与更新说明。 */
    val updateKnownName: Flow<String> get() = kotlinx.coroutines.flow.flowOf("")
    suspend fun setUpdateKnownName(name: String) {}
    val updateKnownNotes: Flow<String> get() = kotlinx.coroutines.flow.flowOf("")
    suspend fun setUpdateKnownNotes(notes: String) {}

    /**
     * 启动更新提醒里点了「不再提示」的版本（versionCode）；0 = 未跳过任何版本。
     * 只压这一个版本的提醒：之后出现更高的 versionCode 仍会再次弹窗。
     */
    val updateSkippedCode: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(0)
    suspend fun setUpdateSkippedCode(code: Int) {}

    suspend fun clear()
}

/** 外观模式：浅色 / 深色 / 跟随系统。默认浅色。 */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** 应用内字体大小级别：在系统字号之上再乘以 factor。 */
enum class FontScaleLevel(val factor: Float) {
    STANDARD(1.0f), LARGE(1.15f), EXTRA_LARGE(1.3f)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    private val keyRemindersEnabled = booleanPreferencesKey("reminders_enabled")
    private val keyTaskReminders = booleanPreferencesKey("task_reminders_enabled")
    private val keyLastRunDay = longPreferencesKey("last_reminder_run_epoch_day")
    private val keyNotifiedKeys = stringSetPreferencesKey("reminder_notified_keys")
    private val keyReminderMinute = intPreferencesKey("reminder_minute_of_day")
    private val keyTaskReminderMinute = intPreferencesKey("task_reminder_minute_of_day")
    private val keyContactReminderMinute = intPreferencesKey("contact_reminder_minute_of_day")
    private val keyNotifRequested = booleanPreferencesKey("notification_permission_requested")
    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyFontScale = stringPreferencesKey("font_scale_level")
    private val keyContactReminders = booleanPreferencesKey("contact_reminders_enabled")
    private val keyLastBackupAt = longPreferencesKey("last_backup_at")
    private val keyTestTools = booleanPreferencesKey("test_tools_enabled")
    private val keyEdgeLabels = booleanPreferencesKey("edge_labels_enabled")
    private val keyIntroSeen = booleanPreferencesKey("intro_seen")
    private val keyWeddingWords = stringPreferencesKey("wedding_words")
    private val keyUpdateCheckEnabled = booleanPreferencesKey("update_check_enabled")
    private val keyUpdateLastCheckAt = longPreferencesKey("update_last_check_at")
    private val keyUpdateKnownCode = intPreferencesKey("update_known_code")
    private val keyUpdateKnownName = stringPreferencesKey("update_known_name")
    private val keyUpdateKnownNotes = stringPreferencesKey("update_known_notes")
    private val keyUpdateSkippedCode = intPreferencesKey("update_skipped_code")

    override val remindersEnabled: Flow<Boolean> =
        dataStore.data.map { it[keyRemindersEnabled] ?: true }

    override suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[keyRemindersEnabled] = enabled }
    }

    override val taskRemindersEnabled: Flow<Boolean> =
        dataStore.data.map { it[keyTaskReminders] ?: true }

    override suspend fun setTaskRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[keyTaskReminders] = enabled }
    }

    override val contactRemindersEnabled: Flow<Boolean> =
        dataStore.data.map { it[keyContactReminders] ?: true }

    override suspend fun setContactRemindersEnabled(enabled: Boolean) {
        dataStore.edit { it[keyContactReminders] = enabled }
    }

    override suspend fun getReminderRunState(): Pair<Long, Set<String>>? {
        val p = dataStore.data.first()
        val day = p[keyLastRunDay] ?: return null
        return day to (p[keyNotifiedKeys] ?: emptySet())
    }

    override suspend fun setReminderRunState(epochDay: Long, notifiedKeys: Set<String>) {
        dataStore.edit {
            it[keyLastRunDay] = epochDay
            it[keyNotifiedKeys] = notifiedKeys
        }
    }

    override suspend fun clearReminderRunState() {
        dataStore.edit {
            it.remove(keyLastRunDay)
            it.remove(keyNotifiedKeys)
        }
    }

    override val reminderMinuteOfDay: Flow<Int> =
        dataStore.data.map {
            // 默认 09:00（与 ReminderTiming.DEFAULT_MINUTE_OF_DAY 一致）
            (it[keyReminderMinute] ?: 9 * 60).coerceIn(0, 1439)
        }
            .distinctUntilChanged()

    override suspend fun setReminderMinuteOfDay(minute: Int) {
        dataStore.edit { it[keyReminderMinute] = minute.coerceIn(0, 1439) }
    }

    override val taskReminderMinuteOfDay: Flow<Int> =
        dataStore.data.map { (it[keyTaskReminderMinute] ?: 9 * 60).coerceIn(0, 1439) }
            .distinctUntilChanged()

    override suspend fun setTaskReminderMinuteOfDay(minute: Int) {
        dataStore.edit { it[keyTaskReminderMinute] = minute.coerceIn(0, 1439) }
    }

    override val contactReminderMinuteOfDay: Flow<Int> =
        dataStore.data.map { (it[keyContactReminderMinute] ?: 9 * 60).coerceIn(0, 1439) }
            .distinctUntilChanged()

    override suspend fun setContactReminderMinuteOfDay(minute: Int) {
        dataStore.edit { it[keyContactReminderMinute] = minute.coerceIn(0, 1439) }
    }

    override val notificationPermissionRequested: Flow<Boolean> =
        dataStore.data.map { it[keyNotifRequested] ?: false }

    override suspend fun setNotificationPermissionRequested(requested: Boolean) {
        dataStore.edit { it[keyNotifRequested] = requested }
    }

    override val fontScaleLevel: Flow<FontScaleLevel> =
        dataStore.data.map {
            it[keyFontScale]?.let { v ->
                runCatching { FontScaleLevel.valueOf(v) }.getOrNull()
            } ?: FontScaleLevel.STANDARD
        }

    override suspend fun setFontScaleLevel(level: FontScaleLevel) {
        dataStore.edit { it[keyFontScale] = level.name }
    }

    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map {
            it[keyThemeMode]?.let { v -> runCatching { ThemeMode.valueOf(v) }.getOrNull() }
                ?: ThemeMode.LIGHT
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[keyThemeMode] = mode.name }
    }

    override val lastBackupAt: Flow<Long?> =
        dataStore.data.map { it[keyLastBackupAt] }.distinctUntilChanged()

    override suspend fun setLastBackupAt(atMillis: Long) {
        dataStore.edit { it[keyLastBackupAt] = atMillis }
    }

    override val testToolsEnabled: Flow<Boolean> =
        dataStore.data.map { it[keyTestTools] ?: false }.distinctUntilChanged()

    override suspend fun setTestToolsEnabled(enabled: Boolean) {
        dataStore.edit { it[keyTestTools] = enabled }
    }

    // 图谱视觉改版：关系词默认关闭（线上不堆“父子/夫妻”文字），
    // 需要时经关系图更多菜单开启
    override val edgeLabelsEnabled: Flow<Boolean> =
        dataStore.data.map { it[keyEdgeLabels] ?: false }.distinctUntilChanged()

    override suspend fun setEdgeLabelsEnabled(enabled: Boolean) {
        dataStore.edit { it[keyEdgeLabels] = enabled }
    }

    override val introSeen: Flow<Boolean> =
        dataStore.data.map { it[keyIntroSeen] ?: false }

    override suspend fun setIntroSeen(seen: Boolean) {
        dataStore.edit { it[keyIntroSeen] = seen }
    }

    override val weddingWords: Flow<String> =
        dataStore.data.map {
            it[keyWeddingWords] ?: WeddingWords.join(WeddingWords.DEFAULT)
        }.distinctUntilChanged()

    override suspend fun setWeddingWords(raw: String) {
        // 落库前归一化：非法输入回落默认，保证上屏两词非空
        dataStore.edit {
            it[keyWeddingWords] = WeddingWords.join(WeddingWords.split(raw))
        }
    }

    override val updateCheckEnabled: Flow<Boolean> =
        dataStore.data.map { it[keyUpdateCheckEnabled] ?: false }.distinctUntilChanged()

    override suspend fun setUpdateCheckEnabled(enabled: Boolean) {
        dataStore.edit { it[keyUpdateCheckEnabled] = enabled }
    }

    override val updateLastCheckAt: Flow<Long> =
        dataStore.data.map { it[keyUpdateLastCheckAt] ?: 0L }

    override suspend fun setUpdateLastCheckAt(at: Long) {
        dataStore.edit { it[keyUpdateLastCheckAt] = at }
    }

    override val updateKnownCode: Flow<Int> =
        dataStore.data.map { it[keyUpdateKnownCode] ?: 0 }

    override suspend fun setUpdateKnownCode(code: Int) {
        dataStore.edit { it[keyUpdateKnownCode] = code }
    }

    override val updateKnownName: Flow<String> =
        dataStore.data.map { it[keyUpdateKnownName] ?: "" }

    override suspend fun setUpdateKnownName(name: String) {
        dataStore.edit { it[keyUpdateKnownName] = name }
    }

    override val updateKnownNotes: Flow<String> =
        dataStore.data.map { it[keyUpdateKnownNotes] ?: "" }

    override suspend fun setUpdateKnownNotes(notes: String) {
        dataStore.edit { it[keyUpdateKnownNotes] = notes }
    }

    override val updateSkippedCode: Flow<Int> =
        dataStore.data.map { it[keyUpdateSkippedCode] ?: 0 }

    override suspend fun setUpdateSkippedCode(code: Int) {
        dataStore.edit { it[keyUpdateSkippedCode] = code }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
