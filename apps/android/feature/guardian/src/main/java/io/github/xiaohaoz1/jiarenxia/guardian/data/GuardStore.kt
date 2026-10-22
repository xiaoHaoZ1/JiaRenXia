package io.github.xiaohaoz1.jiarenxia.guardian.data

import android.content.Context
import android.content.SharedPreferences
import io.github.xiaohaoz1.jiarenxia.guardian.core.ActivityKind
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardConfig
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardEngine
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardState
import io.github.xiaohaoz1.jiarenxia.guardian.core.Phase
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 持久化：device-protected 存储（重启未解锁也能读写）。
 * 写操作全部 @Synchronized，save 后刷新 Flow 给 UI。
 */
class GuardStore(context: Context) : GuardEngine.Store {

    private val prefs: SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences("guardian", Context.MODE_PRIVATE)

    val configFlow = MutableStateFlow(loadConfig())
    val stateFlow = MutableStateFlow(loadState())

    // ---------- config ----------

    @Synchronized
    override fun loadConfig(): GuardConfig = GuardConfig(
        enabled = prefs.getBoolean(K_ENABLED, false),
        contactName = prefs.getString(K_NAME, "") ?: "",
        contactNumber = prefs.getString(K_NUMBER, "") ?: "",
        // 已存的超标值收敛到上限内（旧版本曾允许设到 72 小时）
        thresholdMinutes = prefs.getInt(K_THRESHOLD_MIN, 480)
            .coerceIn(GuardConfig.MIN_THRESHOLD_MINUTES,
                GuardConfig.MAX_THRESHOLD_MINUTES),
        confirmWindowMinutes = prefs.getInt(K_CONFIRM_MIN, 2),
        dailySmsEnabled = prefs.getBoolean(K_DAILY_ENABLED, true),
        dailySmsMinuteOfDay = prefs.getInt(K_DAILY_MINUTE, 9 * 60),
        quietEnabled = prefs.getBoolean(K_QUIET_ENABLED, true),
        quietStartMinuteOfDay = prefs.getInt(K_QUIET_START, 23 * 60),
        quietEndMinuteOfDay = prefs.getInt(K_QUIET_END, 7 * 60),
        testMode = prefs.getBoolean(K_TEST_MODE, false),
    )

    @Synchronized
    override fun saveConfig(c: GuardConfig) {
        prefs.edit()
            .putBoolean(K_ENABLED, c.enabled)
            .putString(K_NAME, c.contactName)
            .putString(K_NUMBER, c.contactNumber)
            .putInt(K_THRESHOLD_MIN, c.thresholdMinutes)
            .putInt(K_CONFIRM_MIN, c.confirmWindowMinutes)
            .putBoolean(K_DAILY_ENABLED, c.dailySmsEnabled)
            .putInt(K_DAILY_MINUTE, c.dailySmsMinuteOfDay)
            .putBoolean(K_QUIET_ENABLED, c.quietEnabled)
            .putInt(K_QUIET_START, c.quietStartMinuteOfDay)
            .putInt(K_QUIET_END, c.quietEndMinuteOfDay)
            .putBoolean(K_TEST_MODE, c.testMode)
            .apply()
        configFlow.value = c
    }

    /** UI 设置页用：读-改-写，然后由调用方发 Event.ConfigChanged。 */
    @Synchronized
    fun updateConfig(transform: (GuardConfig) -> GuardConfig) {
        saveConfig(transform(loadConfig()))
    }

    // ---------- state ----------

    @Synchronized
    override fun loadState(): GuardState = GuardState(
        phase = runCatching {
            Phase.valueOf(prefs.getString(K_PHASE, Phase.MONITORING.name) ?: Phase.MONITORING.name)
        }.getOrDefault(Phase.MONITORING),
        lastActivityAt = prefs.getLong(K_LAST_AT, 0),
        lastActivityKind = prefs.getString(K_LAST_KIND, null)
            ?.let { runCatching { ActivityKind.valueOf(it) }.getOrNull() },
        deadlineAt = prefs.getLong(K_DEADLINE, 0),
        confirmEndAt = prefs.getLong(K_CONFIRM_END, 0),
        nextDailySmsAt = prefs.getLong(K_NEXT_SMS, 0),
        alertedAt = prefs.getLong(K_ALERTED_AT, 0),
        selfDialing = prefs.getBoolean(K_SELF_DIALING, false),
        lastRingAt = prefs.getLong(K_LAST_RING, 0),
        inUse = prefs.getBoolean(K_IN_USE, false),
        lastLockAt = prefs.getLong(K_LAST_LOCK, 0),
    )

    @Synchronized
    override fun saveState(s: GuardState) {
        prefs.edit()
            .putString(K_PHASE, s.phase.name)
            .putLong(K_LAST_AT, s.lastActivityAt)
            .putString(K_LAST_KIND, s.lastActivityKind?.name)
            .putLong(K_DEADLINE, s.deadlineAt)
            .putLong(K_CONFIRM_END, s.confirmEndAt)
            .putLong(K_NEXT_SMS, s.nextDailySmsAt)
            .putLong(K_ALERTED_AT, s.alertedAt)
            .putBoolean(K_SELF_DIALING, s.selfDialing)
            .putLong(K_LAST_RING, s.lastRingAt)
            .putBoolean(K_IN_USE, s.inUse)
            .putLong(K_LAST_LOCK, s.lastLockAt)
            .apply()
        stateFlow.value = s
    }

    /** 是否已申请过运行时权限（MainActivity 首次启动用）。 */
    var permsRequested: Boolean
        get() = prefs.getBoolean(K_PERMS_REQUESTED, false)
        set(v) = prefs.edit().putBoolean(K_PERMS_REQUESTED, v).apply()

    companion object {
        private const val K_ENABLED = "enabled"
        private const val K_NAME = "contact_name"
        private const val K_NUMBER = "contact_number"
        private const val K_THRESHOLD_MIN = "threshold_minutes"
        private const val K_CONFIRM_MIN = "confirm_window_minutes"
        private const val K_DAILY_ENABLED = "daily_sms_enabled"
        private const val K_DAILY_MINUTE = "daily_sms_minute"
        private const val K_QUIET_ENABLED = "quiet_enabled"
        private const val K_QUIET_START = "quiet_start_minute"
        private const val K_QUIET_END = "quiet_end_minute"
        private const val K_TEST_MODE = "test_mode"
        private const val K_PHASE = "phase"
        private const val K_LAST_AT = "last_activity_at"
        private const val K_LAST_KIND = "last_activity_kind"
        private const val K_DEADLINE = "deadline_at"
        private const val K_CONFIRM_END = "confirm_end_at"
        private const val K_NEXT_SMS = "next_daily_sms_at"
        private const val K_ALERTED_AT = "alerted_at"
        private const val K_SELF_DIALING = "self_dialing"
        private const val K_LAST_RING = "last_ring_at"
        private const val K_IN_USE = "in_use"
        private const val K_LAST_LOCK = "last_lock_at"
        private const val K_PERMS_REQUESTED = "perms_requested"
    }
}
