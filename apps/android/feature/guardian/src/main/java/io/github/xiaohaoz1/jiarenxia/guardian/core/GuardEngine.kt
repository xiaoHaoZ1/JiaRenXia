package io.github.xiaohaoz1.jiarenxia.guardian.core

/**
 * 状态机核心（纯 Kotlin，不碰 Android API）。
 * 一切状态流转走 handle(Event)，所有处理 @Synchronized，幂等可重入。
 *
 * 持久化通过 Store SPI（data/GuardStore 实现；单测用内存 Fake）。
 */
class GuardEngine(
    private val store: Store,
    private val clock: Clock,
    private val ports: Ports,
) {
    /** 引擎与持久层之间的存储接口。 */
    interface Store {
        fun loadConfig(): GuardConfig
        fun saveConfig(c: GuardConfig)
        fun loadState(): GuardState
        fun saveState(s: GuardState)
    }

    private var config: GuardConfig = store.loadConfig()
    private var state: GuardState = store.loadState()

    /** UI/测试读取快照用（只读拷贝）。 */
    @Synchronized
    fun snapshot(): GuardState = state

    @Synchronized
    fun snapshotConfig(): GuardConfig = config

    @Synchronized
    fun handle(e: Event) {
        when (e) {
            is Event.EnabledChanged -> onEnabledChanged(e.enabled)
            is Event.Activity -> onActivity(e.kind, e.at)
            Event.UserPresent -> onActivity(ActivityKind.UNLOCK, null)
            Event.ScreenOff -> onScreenOff()
            Event.SyncSession -> onSyncSession()
            Event.DeadlineAlarm -> onDeadline(force = false)
            Event.ForceDeadline -> onDeadline(force = true)
            Event.ConfirmEndAlarm -> onConfirmEnd()
            Event.CallRinging -> onCallRinging()
            Event.CallOffhook -> onCallOffhook()
            Event.ImOk -> onActivity(ActivityKind.MANUAL_IM_OK, null)
            Event.AlertNow -> onAlertNow()
            Event.WatchdogAlarm -> onWatchdog()
            Event.DailySmsAlarm -> onDailySms()
            Event.SystemReset -> onSystemReset()
            Event.ConfigChanged -> onConfigChanged()
        }
    }

    private fun now(): Long = clock.now()

    private fun saveState() = store.saveState(state)

    private fun saveConfig() = store.saveConfig(config)

    // ---------- 开关 ----------

    private fun onEnabledChanged(enabled: Boolean) {
        if (enabled && config.contactNumber.isBlank()) {
            ports.log(TAG, "未设置紧急联系人，无法开启守护")
            return
        }
        config = config.copy(enabled = enabled)
        saveConfig()
        if (enabled) {
            val now = now()
            // 开启瞬间手机若正在使用，先记为使用中会话（走使用中上限，不立刻倒计时）
            val inUse = ports.isDeviceInUse()
            state = state.copy(
                phase = Phase.MONITORING,
                lastActivityAt = now,
                lastActivityKind = ActivityKind.ENABLE,
                inUse = inUse,
                deadlineAt = TimeRules.computeDeadline(now, config, inUse),
                confirmEndAt = 0,
                selfDialing = false,
            )
            val next = TimeRules.nextDailySmsAt(now, config.dailySmsMinuteOfDay)
            state = state.copy(nextDailySmsAt = next)
            saveState()
            ports.scheduleDeadline(state.deadlineAt)
            ports.ensureServiceRunning()
            ports.scheduleDailySms(next)
            ports.scheduleWatchdog(WATCHDOG_INTERVAL_MS)
            ports.log(TAG, "守护已开启 deadline=${state.deadlineAt} nextSms=$next")
        } else {
            ports.stopRinging()
            ports.cancelAll()
            // 停掉前台服务与常驻通知（旧版本 tag 状态通知一并清掉）
            ports.stopService()
            // 保留 phase/lastActivity 等字段便于 UI 显示，仅清掉未到期的闹钟锚点。
            state = state.copy(
                deadlineAt = 0, confirmEndAt = 0, selfDialing = false, inUse = false
            )
            saveState()
            ports.log(TAG, "守护已关闭")
        }
    }

    // ---------- 活动 ----------

    private fun onActivity(kind: ActivityKind, at: Long?) {
        if (!config.enabled) {
            ports.log(TAG, "忽略活动（未开启）kind=$kind")
            return
        }
        // 时钟防呆：外部给的未来时间戳一律钳到 now，避免 deadline 被推到遥远未来
        val ts = minOf(at ?: now(), now())
        val wasAlerting = state.phase != Phase.MONITORING
        // 解锁开始使用会话，使用后熄屏结束会话；其他活动不改变会话标记
        val inUse = when (kind) {
            ActivityKind.UNLOCK -> true
            ActivityKind.LOCK_AFTER_USE -> false
            else -> state.inUse
        }
        state = state.copy(
            phase = Phase.MONITORING,
            lastActivityAt = ts,
            lastActivityKind = kind,
            inUse = inUse,
            lastLockAt =
                if (kind == ActivityKind.LOCK_AFTER_USE) ts else state.lastLockAt,
            deadlineAt = TimeRules.computeDeadline(ts, config, inUse),
            confirmEndAt = 0,
            selfDialing = false,
        )
        saveState()
        if (wasAlerting) {
            ports.stopRinging()
            ports.cancelConfirmAndAlert()
        }
        ports.scheduleDeadline(state.deadlineAt)
        ports.log(TAG, "活动 kind=${kind.label} ts=$ts deadline=${state.deadlineAt}")
    }

    /** 熄屏：使用会话中 → 记 LOCK_AFTER_USE 重新计时；报警流程中 → 仅结束会话，不算平安。 */
    private fun onScreenOff() {
        if (!config.enabled) {
            ports.log(TAG, "忽略熄屏（未开启）")
            return
        }
        if (!state.inUse) return
        if (state.phase == Phase.MONITORING) {
            onActivity(ActivityKind.LOCK_AFTER_USE, null)
        } else {
            // 响铃/拨号流程里的熄屏不能当“人没事”，只把会话标记收回
            state = state.copy(inUse = false)
            saveState()
            ports.log(TAG, "报警流程中熄屏：仅结束使用会话，不算平安")
        }
    }

    /**
     * 会话对账：只在 MONITORING 下用 isDeviceInUse 实况校对 inUse。
     * CONFIRMING/ALERTING/ALERTED 一律不动——屏幕状态永远不能取消报警。
     */
    private fun syncSession() {
        if (!config.enabled || state.phase != Phase.MONITORING) return
        val live = ports.isDeviceInUse()
        if (live && !state.inUse) {
            // 漏掉的解锁广播（服务被杀等）：补记一次使用
            onActivity(ActivityKind.UNLOCK, null)
        } else if (!live && state.inUse) {
            // 会话结束但没收到熄屏广播：保守地从上次活动按锁屏后阈值重算
            state = state.copy(
                inUse = false,
                deadlineAt = TimeRules.computeDeadline(state.lastActivityAt, config, false),
            )
            saveState()
            ports.scheduleDeadline(state.deadlineAt)
            ports.log(TAG, "会话已结束（未收到熄屏广播），按上次活动重新计时")
        }
    }

    /**
     * 会话对账 + 闹钟自愈：进程被强停会带走全部闹钟，服务启动/守护页可见时补挂。
     * CONFIRMING 只补挂确认结束闹钟——过期窗口交给看门狗，绝不在此拨号。
     */
    private fun onSyncSession() {
        syncSession()
        if (!config.enabled) return
        val now = now()
        when (state.phase) {
            Phase.MONITORING -> {
                // 持久 deadline 若落进免打扰时段（改配置/跨时区后），先顺延再判断
                val dl = TimeRules.deferQuiet(state.deadlineAt, config)
                if (dl != state.deadlineAt) {
                    state = state.copy(deadlineAt = dl)
                    saveState()
                }
                if (state.deadlineAt in 1..now) {
                    onDeadline(force = false)
                } else if (state.deadlineAt > now) {
                    ports.scheduleDeadline(state.deadlineAt)
                }
            }
            Phase.CONFIRMING ->
                if (state.confirmEndAt > now) {
                    ports.scheduleConfirmEnd(state.confirmEndAt)
                }
            Phase.ALERTING, Phase.ALERTED -> Unit
        }
        // 平安短信与看门狗闹钟始终补挂
        val next = if (state.nextDailySmsAt > now) {
            state.nextDailySmsAt
        } else {
            TimeRules.nextDailySmsAt(now, config.dailySmsMinuteOfDay)
        }
        if (next != state.nextDailySmsAt) {
            state = state.copy(nextDailySmsAt = next)
            saveState()
        }
        ports.scheduleDailySms(next)
        ports.scheduleWatchdog(WATCHDOG_INTERVAL_MS)
    }

    // ---------- 到期 / 确认 ----------

    private fun onDeadline(force: Boolean) {
        if (!config.enabled || state.phase != Phase.MONITORING) return
        // UsageStats 兜底：服务被杀期间可能漏掉解锁事件
        val missed = ports.reconcileMissedUnlockSince(state.lastActivityAt)
        if (missed != null && missed > state.lastActivityAt) {
            ports.log(TAG, "UsageStats 找回解锁 ts=$missed")
            onActivity(ActivityKind.USAGE_STATS, missed)
            return
        }
        val now = now()
        if (!force) {
            // 持久 deadline 可能落在新设的免打扰时段内（改配置/跨时区后）：顺延，不报警
            val dl = TimeRules.deferQuiet(state.deadlineAt, config)
            if (dl != state.deadlineAt) {
                state = state.copy(deadlineAt = dl)
                saveState()
                ports.scheduleDeadline(dl)
                ports.log(TAG, "deadline 落在免打扰时段，顺延到 $dl")
                return
            }
            if (now < state.deadlineAt) {
                // 闹钟提前响了（系统对齐等），按持久 deadline 重挂
                ports.scheduleDeadline(state.deadlineAt)
                return
            }
        }
        enterConfirming(now)
    }

    private fun enterConfirming(now: Long) {
        state = state.copy(
            phase = Phase.CONFIRMING,
            confirmEndAt = now + config.effectiveConfirmWindowMillis(),
        )
        saveState()
        ports.startConfirmRinging(state.confirmEndAt)
        ports.showConfirmNotification(state.confirmEndAt)
        ports.scheduleConfirmEnd(state.confirmEndAt)
        ports.log(TAG, "进入确认 phase=CONFIRMING confirmEnd=${state.confirmEndAt}")
    }

    private fun onConfirmEnd() {
        if (state.phase != Phase.CONFIRMING) return
        if (!config.enabled) {
            ports.log(TAG, "确认结束闹钟到时但已关闭守护，忽略")
            return
        }
        val now = now()
        if (now < state.confirmEndAt) {
            ports.scheduleConfirmEnd(state.confirmEndAt)
            return
        }
        dialOut("确认超时无人响应")
    }

    /** ALERTING → 拨号 → ALERTED。只拨一次，不重拨；拨号失败补发紧急短信。 */
    private fun dialOut(reason: String) {
        state = state.copy(phase = Phase.ALERTING, selfDialing = true)
        saveState()
        ports.stopRinging()
        val ok = try {
            ports.placeCall(config.contactNumber)
        } catch (se: SecurityException) {
            ports.log(TAG, "拨号权限不足 ${se.message}")
            false
        } catch (t: Throwable) {
            ports.log(TAG, "拨号异常 $t")
            false
        }
        if (!ok) {
            ports.log(TAG, "拨号失败，补发紧急短信")
            try {
                ports.sendEmergencySms()
            } catch (t: Throwable) {
                ports.log(TAG, "紧急短信发送异常 $t")
            }
        }
        val now = now()
        state = state.copy(phase = Phase.ALERTED, selfDialing = false, alertedAt = now)
        saveState()
        ports.showAlertedNotification(now)
        ports.log(TAG, "已拨打紧急电话 " + maskNumber(config.contactNumber) +
            " ok=" + ok + " reason=" + reason + " alertedAt=" + now)
    }

    // ---------- 其他事件 ----------

    private fun onCallRinging() {
        state = state.copy(lastRingAt = now())
        saveState()
        ports.log(TAG, "来电响铃 lastRingAt=${state.lastRingAt}")
    }

    private fun onCallOffhook() {
        val now = now()
        // 三种情况不算活动：
        // 1) placeCall 正在执行（selfDialing/ALERTING）；
        // 2) 报警电话拨出后 OFFHOOK 异步到达——ALERTED 后 3 分钟内、
        //    且报警之后没有新的来电响铃的摘机，认定为“我们自己那通电话”。
        if (state.selfDialing || state.phase == Phase.ALERTING) {
            ports.log(TAG, "忽略自拨号摘机")
            return
        }
        if (state.phase == Phase.ALERTED && state.alertedAt > 0 &&
            now - state.alertedAt <= ALERT_CALL_GUARD_MS &&
            state.lastRingAt <= state.alertedAt
        ) {
            ports.log(TAG, "忽略报警电话自身的摘机")
            return
        }
        onActivity(ActivityKind.CALL, null)
    }

    private fun onAlertNow() {
        if (!config.enabled) return
        when (state.phase) {
            Phase.CONFIRMING, Phase.MONITORING -> dialOut("手动立即拨打")
            else -> ports.log(TAG, "AlertNow 在 ${state.phase} 下忽略")
        }
    }

    private fun onWatchdog() {
        // 关闭状态下闹钟残留不应拉起服务
        if (!config.enabled) return
        ports.ensureServiceRunning()
        syncSession()
        val now = now()
        when (state.phase) {
            Phase.MONITORING -> {
                val dl = TimeRules.deferQuiet(state.deadlineAt, config)
                if (dl != state.deadlineAt) {
                    state = state.copy(deadlineAt = dl)
                    saveState()
                    ports.scheduleDeadline(dl)
                } else if (dl in 1..now) {
                    onDeadline(force = false)
                }
            }
            Phase.CONFIRMING ->
                if (state.confirmEndAt in 1..now) {
                    onConfirmEnd()
                } else if (state.confirmEndAt > 0) {
                    // 进程被杀会带走响铃：确认窗口内自愈重启响铃与全屏通知
                    ports.startConfirmRinging(state.confirmEndAt)
                    ports.showConfirmNotification(state.confirmEndAt)
                }
            else -> Unit
        }
        ports.scheduleWatchdog(WATCHDOG_INTERVAL_MS)
        ports.log(TAG, "看门狗自查 phase=${state.phase} now=$now")
    }

    private fun onDailySms() {
        if (!config.enabled) return
        if (config.dailySmsEnabled) {
            ports.sendDailySms()
            ports.log(TAG, "已触发每日平安短信")
        }
        val next = TimeRules.nextDailySmsAt(now(), config.dailySmsMinuteOfDay)
        state = state.copy(nextDailySmsAt = next)
        saveState()
        ports.scheduleDailySms(next)
    }

    private fun onSystemReset() {
        if (!config.enabled) return
        ports.ensureServiceRunning()
        var now = now()
        // 时钟回拨防护：lastActivity 不得在未来
        var clamped = false
        if (state.lastActivityAt > now) {
            state = state.copy(lastActivityAt = now)
            saveState()
            clamped = true
            ports.log(TAG, "时钟回拨，lastActivity 钳到 $now")
        }
        syncSession()
        when (state.phase) {
            Phase.MONITORING -> {
                var dl = state.deadlineAt
                if (dl <= 0 || now >= dl || clamped ||
                    TimeRules.deferQuiet(dl, config) != dl
                ) {
                    dl = TimeRules.computeDeadline(state.lastActivityAt, config, state.inUse)
                }
                if (now >= dl) {
                    // 已过：先响铃给缓冲，不直接拨号
                    state = state.copy(deadlineAt = dl)
                    saveState()
                    ports.log(TAG, "SystemReset: deadline 已过，进入确认流程")
                    onDeadline(force = true)
                } else {
                    state = state.copy(deadlineAt = dl)
                    saveState()
                    ports.scheduleDeadline(dl)
                }
            }
            Phase.CONFIRMING -> {
                if (state.confirmEndAt <= 0) {
                    state = state.copy(confirmEndAt = now + config.effectiveConfirmWindowMillis())
                    saveState()
                }
                ports.startConfirmRinging(state.confirmEndAt)
                ports.showConfirmNotification(state.confirmEndAt)
                ports.scheduleConfirmEnd(state.confirmEndAt)
            }
            Phase.ALERTING -> {
                // 拨号途中进程死/重启：按已报警收尾，避免永远卡在 ALERTING
                state = state.copy(phase = Phase.ALERTED, selfDialing = false)
                saveState()
                ports.log(TAG, "SystemReset: ALERTING 收敛为 ALERTED")
            }
            Phase.ALERTED ->
                ports.log(TAG, "SystemReset: 已报警状态，仅记录")
        }
        // 保证日报/看门狗闹钟都在
        now = now()
        val next = if (state.nextDailySmsAt > now) {
            state.nextDailySmsAt
        } else {
            TimeRules.nextDailySmsAt(now, config.dailySmsMinuteOfDay)
        }
        if (next != state.nextDailySmsAt) {
            state = state.copy(nextDailySmsAt = next)
            saveState()
        }
        ports.scheduleDailySms(next)
        ports.scheduleWatchdog(WATCHDOG_INTERVAL_MS)
        ports.log(TAG, "SystemReset 完成 phase=${state.phase} deadline=${state.deadlineAt}")
    }

    private fun onConfigChanged() {
        config = store.loadConfig()
        ports.log(TAG, "配置更新 $config")
        if (!config.enabled) return
        val now = now()
        if (state.phase == Phase.MONITORING) {
            val dl = TimeRules.computeDeadline(state.lastActivityAt, config, state.inUse)
            if (dl != state.deadlineAt) {
                state = state.copy(deadlineAt = dl)
                saveState()
                ports.scheduleDeadline(dl)
            }
        }
        val next = TimeRules.nextDailySmsAt(now, config.dailySmsMinuteOfDay)
        if (next != state.nextDailySmsAt) {
            state = state.copy(nextDailySmsAt = next)
            saveState()
            ports.scheduleDailySms(next)
        }
        ports.scheduleWatchdog(WATCHDOG_INTERVAL_MS)
    }

    companion object {
        const val TAG = "GuardEngine"
        const val WATCHDOG_INTERVAL_MS = 30 * 60_000L

        /** 报警电话拨出后的自摘机保护窗（区分被守护的人真实接打电话）。 */
        const val ALERT_CALL_GUARD_MS = 3 * 60_000L
    }
}
