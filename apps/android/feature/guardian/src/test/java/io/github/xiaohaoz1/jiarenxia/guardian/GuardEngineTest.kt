package io.github.xiaohaoz1.jiarenxia.guardian

import io.github.xiaohaoz1.jiarenxia.guardian.core.ActivityKind
import io.github.xiaohaoz1.jiarenxia.guardian.core.Event
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardConfig
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardEngine
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardState
import io.github.xiaohaoz1.jiarenxia.guardian.core.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardEngineTest {

    private val t0 = 1_700_000_000_000L
    private val num = "13800001111"

    private fun fixture(
        // 免打扰时段依赖本机时区，既有用例默认关掉；免打扰行为有专项用例覆盖
        cfg: GuardConfig = GuardConfig(contactNumber = num, quietEnabled = false),
        st: GuardState = GuardState(),
        now: Long = t0,
    ): Triple<GuardEngine, FakeClock, FakePorts> {
        val store = FakeStore(cfg, st)
        val clock = FakeClock(now)
        val ports = FakePorts()
        return Triple(GuardEngine(store, clock, ports), clock, ports)
    }

    // ---------- 开启/关闭 ----------

    @Test
    fun `enable switches to MONITORING and schedules all alarms`() {
        val (e, _, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        val s = e.snapshot()
        assertEquals(Phase.MONITORING, s.phase)
        assertEquals(t0, s.lastActivityAt)
        assertEquals(ActivityKind.ENABLE, s.lastActivityKind)
        assertEquals(t0 + 8 * 3600_000L, s.deadlineAt)
        assertEquals(s.deadlineAt, ports.deadlines.last())
        assertEquals(1, ports.ensureCount)
        assertEquals(1, ports.dailySmsSchedules.size)
        assertTrue(s.nextDailySmsAt > t0)
        assertEquals(GuardEngine.WATCHDOG_INTERVAL_MS, ports.watchdogDelays.last())
    }

    @Test
    fun `enable without contact is refused and logs`() {
        val (e, _, ports) = fixture(cfg = GuardConfig(contactNumber = ""))
        e.handle(Event.EnabledChanged(true))
        assertFalse(e.snapshotConfig().enabled)
        assertTrue(ports.deadlines.isEmpty())
        assertEquals(0, ports.ensureCount)
        assertTrue(ports.logs.any { it.second.contains("未设置紧急联系人") })
        // 空白号码同样拒绝
        val (e2, _, _) = fixture(cfg = GuardConfig(contactNumber = "   "))
        e2.handle(Event.EnabledChanged(true))
        assertFalse(e2.snapshotConfig().enabled)
    }

    @Test
    fun `disable stops ringing and cancels all alarms`() {
        val (e, _, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        e.handle(Event.EnabledChanged(false))
        val s = e.snapshot()
        assertFalse(e.snapshotConfig().enabled)
        assertEquals(0L, s.deadlineAt)
        assertEquals(0L, s.confirmEndAt)
        assertTrue(ports.cancelAllCount >= 1)
        assertTrue(ports.ringStopCount >= 1)
    }

    // ---------- 活动 ----------

    @Test
    fun `activity resets deadline`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(3600_000L)
        e.handle(Event.Activity(ActivityKind.UNLOCK))
        val s = e.snapshot()
        assertEquals(t0 + 3600_000L, s.lastActivityAt)
        assertEquals(t0 + 3600_000L + 8 * 3600_000L, s.deadlineAt)
        assertEquals(s.deadlineAt, ports.deadlines.last())
    }

    @Test
    fun `activity ignored when disabled`() {
        val (e, _, ports) = fixture()
        e.handle(Event.Activity(ActivityKind.UNLOCK))
        assertEquals(0L, e.snapshot().lastActivityAt)
        assertTrue(ports.deadlines.isEmpty())
    }

    // ---------- 到期 → 确认 ----------

    @Test
    fun `deadline alarm enters CONFIRMING when overdue`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        val s = e.snapshot()
        assertEquals(Phase.CONFIRMING, s.phase)
        assertEquals(1, ports.ringingStarts.size)
        assertEquals(1, ports.confirmNotifs.size)
        assertEquals(t0 + 8 * 3600_000L + 2 * 60_000L, s.confirmEndAt)
        assertEquals(s.confirmEndAt, ports.confirmEnds.last())
    }

    @Test
    fun `deadline alarm fired early reschedules and stays MONITORING`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(3600_000L)
        e.handle(Event.DeadlineAlarm)
        assertEquals(Phase.MONITORING, e.snapshot().phase)
        assertTrue(ports.ringingStarts.isEmpty())
        assertEquals(e.snapshot().deadlineAt, ports.deadlines.last())
    }

    @Test
    fun `quiet window defers deadline into morning`() {
        // 20:00 开启、阈值 8 小时：deadline 原落凌晨 04:00，免打扰（默认 23–07）顺延到 07:00
        val zone = java.time.ZoneId.systemDefault()
        val d = java.time.LocalDate.now(zone)
        val t = d.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        val (e, clock, ports) = fixture(
            cfg = GuardConfig(contactNumber = num),
            now = t
        )
        e.handle(Event.EnabledChanged(true))
        val expect = d.plusDays(1).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expect, e.snapshot().deadlineAt)
        // 到原该响铃的 04:00，提前到的闹钟被重挂，仍 MONITORING
        clock.t = d.plusDays(1).atTime(4, 0).atZone(zone).toInstant().toEpochMilli()
        e.handle(Event.DeadlineAlarm)
        assertEquals(Phase.MONITORING, e.snapshot().phase)
        assertTrue(ports.ringingStarts.isEmpty())
    }

    @Test
    fun `stale deadline inside quiet window is deferred not alerted`() {
        // 旧 deadline 已存在且落在免打扰时段（如改配置前算的）：闹钟到时顺延而非报警
        val zone = java.time.ZoneId.systemDefault()
        val d = java.time.LocalDate.now(zone)
        val t = d.atTime(3, 0).atZone(zone).toInstant().toEpochMilli()
        val (e, _, ports) = fixture(
            cfg = GuardConfig(contactNumber = num, enabled = true),
            st = GuardState(
                phase = Phase.MONITORING,
                lastActivityAt = t - 8 * 3600_000L,
                deadlineAt = t,
                inUse = false
            ),
            now = t
        )
        e.handle(Event.DeadlineAlarm)
        val expect = d.atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(Phase.MONITORING, e.snapshot().phase)
        assertEquals(expect, e.snapshot().deadlineAt)
        assertEquals(expect, ports.deadlines.last())
        assertTrue(ports.ringingStarts.isEmpty())
    }

    @Test
    fun `deadline alarm reconciles missed unlock from usage stats`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        val missed = t0 + 4 * 3600_000L
        ports.reconcileResult = missed
        e.handle(Event.DeadlineAlarm)
        val s = e.snapshot()
        assertEquals(Phase.MONITORING, s.phase)
        assertEquals(missed, s.lastActivityAt)
        assertEquals(ActivityKind.USAGE_STATS, s.lastActivityKind)
        assertEquals(missed + 8 * 3600_000L, s.deadlineAt)
        assertTrue(ports.ringingStarts.isEmpty())
    }

    @Test
    fun `force deadline enters CONFIRMING immediately`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(60_000L)
        e.handle(Event.ForceDeadline)
        assertEquals(Phase.CONFIRMING, e.snapshot().phase)
        assertEquals(1, ports.ringingStarts.size)
    }

    @Test
    fun `activity during CONFIRMING cancels alarm and resets`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        clock.advance(30_000L)
        e.handle(Event.Activity(ActivityKind.UNLOCK))
        val s = e.snapshot()
        assertEquals(Phase.MONITORING, s.phase)
        assertEquals(1, ports.ringStopCount)
        assertEquals(1, ports.cancelConfirmCount)
        assertEquals(t0 + 8 * 3600_000L + 30_000L, s.lastActivityAt)
        assertEquals(s.lastActivityAt + 8 * 3600_000L, s.deadlineAt)
    }

    // ---------- 确认超时 → 拨号 ----------

    @Test
    fun `confirm end expired places emergency call`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        clock.advance(2 * 60_000L)
        e.handle(Event.ConfirmEndAlarm)
        val s = e.snapshot()
        assertEquals(listOf(num), ports.calls)
        assertEquals(Phase.ALERTED, s.phase)
        assertTrue(s.alertedAt > 0)
        assertFalse(s.selfDialing)
        assertEquals(1, ports.alertedNotifs.size)
        assertTrue(ports.ringStopCount >= 1)
    }

    @Test
    fun `confirm end alarm fired early reschedules`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        clock.advance(30_000L)
        e.handle(Event.ConfirmEndAlarm)
        assertEquals(Phase.CONFIRMING, e.snapshot().phase)
        assertTrue(ports.calls.isEmpty())
        assertEquals(e.snapshot().confirmEndAt, ports.confirmEnds.last())
    }

    @Test
    fun `confirm end alarm ignored when not confirming`() {
        val (e, _, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        e.handle(Event.ConfirmEndAlarm)
        assertTrue(ports.calls.isEmpty())
        assertEquals(Phase.MONITORING, e.snapshot().phase)
    }

    @Test
    fun `im ok cancels CONFIRMING`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        e.handle(Event.ImOk)
        val s = e.snapshot()
        assertEquals(Phase.MONITORING, s.phase)
        assertEquals(ActivityKind.MANUAL_IM_OK, s.lastActivityKind)
        assertTrue(ports.calls.isEmpty())
        assertEquals(1, ports.cancelConfirmCount)
    }

    @Test
    fun `security exception on dial still goes ALERTED and sends emergency sms`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        ports.throwOnPlaceCall = SecurityException("no perm")
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        clock.advance(2 * 60_000L)
        e.handle(Event.ConfirmEndAlarm)
        assertEquals(Phase.ALERTED, e.snapshot().phase)
        assertEquals(1, ports.alertedNotifs.size)
        assertEquals(1, ports.emergencySmsCount)
    }

    @Test
    fun `placeCall returning false also sends emergency sms`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        ports.placeCallResult = false
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        clock.advance(2 * 60_000L)
        e.handle(Event.ConfirmEndAlarm)
        assertEquals(Phase.ALERTED, e.snapshot().phase)
        assertEquals(listOf(num), ports.calls)
        assertEquals(1, ports.emergencySmsCount)
    }

    // ---------- 通话 / 自拨号 ----------

    @Test
    fun `call offhook counts as activity`() {
        val (e, clock, _) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(3600_000L)
        e.handle(Event.CallOffhook)
        val s = e.snapshot()
        assertEquals(t0 + 3600_000L, s.lastActivityAt)
        assertEquals(ActivityKind.CALL, s.lastActivityKind)
    }

    @Test
    fun `call offhook ignored while self dialing`() {
        val (e, _, _) = fixture(
            cfg = GuardConfig(enabled = true),
            st = GuardState(phase = Phase.ALERTING, lastActivityAt = t0),
        )
        e.handle(Event.CallOffhook)
        assertEquals(t0, e.snapshot().lastActivityAt)
        assertEquals(Phase.ALERTING, e.snapshot().phase)
    }

    @Test
    fun `own alert call offhook within guard window is ignored`() {
        val (e, clock, _) = fixture(
            cfg = GuardConfig(enabled = true),
            st = GuardState(phase = Phase.ALERTED, lastActivityAt = t0, alertedAt = t0),
        )
        clock.advance(5_000L) // 我们刚拨出的报警电话的 OFFHOOK 到了
        e.handle(Event.CallOffhook)
        assertEquals(Phase.ALERTED, e.snapshot().phase)
        assertEquals(t0, e.snapshot().lastActivityAt)
    }

    @Test
    fun `offhook after alert window counts as activity`() {
        val (e, clock, _) = fixture(
            cfg = GuardConfig(enabled = true),
            st = GuardState(phase = Phase.ALERTED, lastActivityAt = t0, alertedAt = t0),
        )
        clock.advance(GuardEngine.ALERT_CALL_GUARD_MS + 1000)
        e.handle(Event.CallOffhook)
        assertEquals(Phase.MONITORING, e.snapshot().phase)
    }

    @Test
    fun `answered incoming call right after alert counts as activity`() {
        val (e, clock, _) = fixture(
            cfg = GuardConfig(enabled = true),
            st = GuardState(phase = Phase.ALERTED, lastActivityAt = t0, alertedAt = t0),
        )
        clock.advance(30_000L)
        e.handle(Event.CallRinging) // 有新的来电响铃
        clock.advance(5_000L)
        e.handle(Event.CallOffhook) // 被守护的人接听了 → 真实活动
        assertEquals(Phase.MONITORING, e.snapshot().phase)
        assertEquals(ActivityKind.CALL, e.snapshot().lastActivityKind)
    }

    @Test
    fun `ringing alone is not activity`() {
        val (e, clock, _) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(60_000L)
        e.handle(Event.CallRinging)
        assertEquals(t0, e.snapshot().lastActivityAt) // 未接来电不重置计时
        assertEquals(Phase.MONITORING, e.snapshot().phase)
    }

    // ---------- AlertNow ----------

    @Test
    fun `alert now from MONITORING dials immediately`() {
        val (e, _, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        e.handle(Event.AlertNow)
        assertEquals(listOf(num), ports.calls)
        assertEquals(Phase.ALERTED, e.snapshot().phase)
    }

    @Test
    fun `alert now from CONFIRMING dials immediately`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        e.handle(Event.AlertNow)
        assertEquals(listOf(num), ports.calls)
        assertEquals(Phase.ALERTED, e.snapshot().phase)
    }

    @Test
    fun `alert now ignored when disabled`() {
        val (e, _, ports) = fixture()
        e.handle(Event.AlertNow)
        assertTrue(ports.calls.isEmpty())
    }

    // ---------- 看门狗 ----------

    @Test
    fun `watchdog heals missed deadline`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L) // DeadlineAlarm 被系统吞了
        e.handle(Event.WatchdogAlarm)
        assertEquals(Phase.CONFIRMING, e.snapshot().phase)
        assertEquals(1, ports.ringingStarts.size)
        assertEquals(2, ports.watchdogDelays.size) // 自愈后仍重挂
    }

    @Test
    fun `watchdog heals missed confirm end`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        clock.advance(2 * 60_000L) // ConfirmEndAlarm 被吞
        e.handle(Event.WatchdogAlarm)
        assertEquals(Phase.ALERTED, e.snapshot().phase)
        assertEquals(listOf(num), ports.calls)
    }

    @Test
    fun `watchdog ensures service and reschedules`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(60_000L)
        e.handle(Event.WatchdogAlarm)
        assertTrue(ports.ensureCount >= 2)
        assertEquals(2, ports.watchdogDelays.size)
        assertEquals(Phase.MONITORING, e.snapshot().phase)
    }

    // ---------- 每日短信 ----------

    @Test
    fun `daily sms alarm sends and reschedules`() {
        val (e, _, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        e.handle(Event.DailySmsAlarm)
        assertEquals(1, ports.smsCount)
        assertEquals(2, ports.dailySmsSchedules.size)
        assertTrue(e.snapshot().nextDailySmsAt > t0)
    }

    @Test
    fun `daily sms skipped when flag off but still reschedules`() {
        val (e, _, ports) = fixture(
            cfg = GuardConfig(enabled = true, dailySmsEnabled = false)
        )
        e.handle(Event.DailySmsAlarm)
        assertEquals(0, ports.smsCount)
        assertEquals(1, ports.dailySmsSchedules.size)
    }

    // ---------- SystemReset ----------

    @Test
    fun `system reset with overdue deadline goes CONFIRMING not dial`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.SystemReset)
        assertEquals(Phase.CONFIRMING, e.snapshot().phase)
        assertTrue(ports.calls.isEmpty())
        assertEquals(1, ports.ringingStarts.size)
    }

    @Test
    fun `system reset not overdue reschedules deadline`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(3600_000L)
        ports.deadlines.clear()
        e.handle(Event.SystemReset)
        assertEquals(Phase.MONITORING, e.snapshot().phase)
        assertEquals(e.snapshot().deadlineAt, ports.deadlines.last())
        assertTrue(ports.ringingStarts.isEmpty())
    }

    @Test
    fun `system reset clamps future lastActivity and recomputes deadline`() {
        val future = t0 + 999_000L
        val (e, _, _) = fixture(
            cfg = GuardConfig(enabled = true),
            st = GuardState(
                phase = Phase.MONITORING,
                lastActivityAt = future,
                deadlineAt = future + 8 * 3600_000L,
            ),
        )
        e.handle(Event.SystemReset)
        val s = e.snapshot()
        assertEquals(t0, s.lastActivityAt)
        assertEquals(t0 + 8 * 3600_000L, s.deadlineAt)
    }

    @Test
    fun `system reset restores CONFIRMING ringing and alarms`() {
        val confirmEnd = t0 + 60_000L
        val (e, _, ports) = fixture(
            cfg = GuardConfig(enabled = true),
            st = GuardState(
                phase = Phase.CONFIRMING,
                lastActivityAt = t0,
                deadlineAt = t0,
                confirmEndAt = confirmEnd,
            ),
        )
        e.handle(Event.SystemReset)
        assertEquals(Phase.CONFIRMING, e.snapshot().phase)
        assertEquals(listOf(confirmEnd), ports.ringingStarts)
        assertEquals(listOf(confirmEnd), ports.confirmEnds)
        assertEquals(1, ports.confirmNotifs.size)
        // 日报/看门狗补齐
        assertEquals(1, ports.dailySmsSchedules.size)
        assertEquals(1, ports.watchdogDelays.size)
    }

    @Test
    fun `system reset ignored when disabled`() {
        val (e, _, ports) = fixture()
        e.handle(Event.SystemReset)
        assertEquals(0, ports.ensureCount)
        assertTrue(ports.ringingStarts.isEmpty())
    }

    // ---------- 测试模式 / 配置变更 ----------

    @Test
    fun `test mode uses 30s threshold and 15s confirm window`() {
        val (e, clock, ports) = fixture(
            cfg = GuardConfig(enabled = true, contactNumber = num, testMode = true)
        )
        // 设备不在使用中：锁屏后倒计时才被测试模式缩短到 30 秒
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        assertEquals(t0 + 30_000L, e.snapshot().deadlineAt)
        clock.advance(30_000L)
        e.handle(Event.DeadlineAlarm)
        assertEquals(Phase.CONFIRMING, e.snapshot().phase)
        assertEquals(t0 + 45_000L, e.snapshot().confirmEndAt)
        clock.advance(15_000L)
        e.handle(Event.ConfirmEndAlarm)
        assertEquals(listOf(num), ports.calls)
    }

    @Test
    fun `config changed recomputes deadline with new threshold`() {
        val store = FakeStore(GuardConfig(contactNumber = num), GuardState())
        val clock = FakeClock(t0)
        val ports = FakePorts()
        val e = GuardEngine(store, clock, ports)
        e.handle(Event.EnabledChanged(true))
        clock.advance(60_000L)
        store.config = store.config.copy(thresholdMinutes = 120)
        e.handle(Event.ConfigChanged)
        assertEquals(t0 + 120 * 60_000L, e.snapshot().deadlineAt)
        assertEquals(e.snapshot().deadlineAt, ports.deadlines.last())
    }

    @Test
    fun `config changed moves daily sms schedule`() {
        val store = FakeStore(GuardConfig(contactNumber = num), GuardState())
        val clock = FakeClock(t0)
        val ports = FakePorts()
        val e = GuardEngine(store, clock, ports)
        e.handle(Event.EnabledChanged(true))
        val before = ports.dailySmsSchedules.last()
        store.config = store.config.copy(dailySmsMinuteOfDay = 20 * 60)
        e.handle(Event.ConfigChanged)
        assertTrue(ports.dailySmsSchedules.last() != before || ports.dailySmsSchedules.size >= 2)
    }

    // ---------- 使用会话（inUse） ----------

    @Test
    fun `enable while device in use marks inUse and uses cap not test threshold`() {
        val (e, _, _) = fixture(
            cfg = GuardConfig(contactNumber = num, testMode = true)
        )
        // FakePorts.deviceInUse 默认 true
        e.handle(Event.EnabledChanged(true))
        val s = e.snapshot()
        assertTrue(s.inUse)
        // 使用中上限不受测试模式影响：仍是 8 小时，不是 30 秒
        assertEquals(t0 + 480 * 60_000L, s.deadlineAt)
    }

    @Test
    fun `enable while device locked starts countdown immediately`() {
        val (e, _, ports) = fixture(
            cfg = GuardConfig(contactNumber = num, testMode = true)
        )
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        val s = e.snapshot()
        assertFalse(s.inUse)
        assertEquals(t0 + 30_000L, s.deadlineAt)
    }

    @Test
    fun `screen off after use ends session and starts after-lock countdown`() {
        val (e, clock, _) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(3600_000L)
        e.handle(Event.ScreenOff)
        val s = e.snapshot()
        assertFalse(s.inUse)
        assertEquals(ActivityKind.LOCK_AFTER_USE, s.lastActivityKind)
        assertEquals(t0 + 3600_000L, s.lastActivityAt)
        assertEquals(t0 + 3600_000L, s.lastLockAt)
        assertEquals(t0 + 3600_000L + 8 * 3600_000L, s.deadlineAt)
    }

    @Test
    fun `screen off after use in test mode counts down 30s`() {
        val (e, clock, ports) = fixture(
            cfg = GuardConfig(contactNumber = num, testMode = true)
        )
        // 先锁屏态开启，再解锁进入会话
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        clock.advance(10_000L)
        e.handle(Event.UserPresent)
        clock.advance(5_000L)
        e.handle(Event.ScreenOff)
        val s = e.snapshot()
        assertFalse(s.inUse)
        assertEquals(t0 + 15_000L, s.lastLockAt)
        assertEquals(t0 + 15_000L + 30_000L, s.deadlineAt)
    }

    @Test
    fun `screen off without session is a no-op`() {
        val (e, _, ports) = fixture()
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        val before = e.snapshot()
        val deadlinesBefore = ports.deadlines.size
        e.handle(Event.ScreenOff)
        assertEquals(before, e.snapshot())
        assertEquals(deadlinesBefore, ports.deadlines.size)
    }

    @Test
    fun `screen off ignored when disabled`() {
        val (e, _, ports) = fixture()
        e.handle(Event.ScreenOff)
        assertFalse(e.snapshot().inUse)
        assertTrue(ports.deadlines.isEmpty())
    }

    @Test
    fun `user present starts session and deadline uses in-use cap`() {
        val (e, clock, ports) = fixture(
            cfg = GuardConfig(contactNumber = num, testMode = true)
        )
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        clock.advance(10_000L)
        e.handle(Event.UserPresent)
        val s = e.snapshot()
        assertTrue(s.inUse)
        assertEquals(ActivityKind.UNLOCK, s.lastActivityKind)
        assertEquals(t0 + 10_000L, s.lastActivityAt)
        // 使用中：走正常阈值上限（测试模式不缩短）
        assertEquals(t0 + 10_000L + 480 * 60_000L, s.deadlineAt)
    }

    @Test
    fun `screen off during CONFIRMING only ends session and keeps ringing`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        e.handle(Event.ScreenOff)
        val s = e.snapshot()
        assertEquals(Phase.CONFIRMING, s.phase)
        assertFalse(s.inUse)
        // 熄屏不算平安：不停止响铃、不取消确认闹钟、不重置确认截止
        assertEquals(0, ports.ringStopCount)
        assertEquals(0, ports.cancelConfirmCount)
        assertEquals(t0 + 8 * 3600_000L + 2 * 60_000L, s.confirmEndAt)
    }

    @Test
    fun `sync session records missed unlock`() {
        val (e, _, ports) = fixture()
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        ports.deviceInUse = true
        e.handle(Event.SyncSession)
        val s = e.snapshot()
        assertTrue(s.inUse)
        assertEquals(ActivityKind.UNLOCK, s.lastActivityKind)
        assertEquals(t0, s.lastActivityAt)
        assertEquals(t0 + 480 * 60_000L, s.deadlineAt)
    }

    @Test
    fun `sync session ends session without screen off broadcast`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(3600_000L)
        ports.deviceInUse = false
        e.handle(Event.SyncSession)
        val s = e.snapshot()
        assertFalse(s.inUse)
        // 保守：仍按上次活动起算锁屏后阈值
        assertEquals(t0, s.lastActivityAt)
        assertEquals(t0 + 8 * 3600_000L, s.deadlineAt)
        assertEquals(s.deadlineAt, ports.deadlines.last())
    }

    @Test
    fun `sync session with overdue recomputed deadline enters CONFIRMING`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(9 * 3600_000L)
        ports.deviceInUse = false
        e.handle(Event.SyncSession)
        assertEquals(Phase.CONFIRMING, e.snapshot().phase)
        assertEquals(1, ports.ringingStarts.size)
    }

    @Test
    fun `sync session during CONFIRMING does nothing`() {
        val (e, clock, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        val before = e.snapshot()
        e.handle(Event.SyncSession)
        val after = e.snapshot()
        // 只补挂闹钟/更新 nextDailySmsAt，不推进报警流程
        assertEquals(before.copy(nextDailySmsAt = after.nextDailySmsAt), after)
        assertTrue(ports.calls.isEmpty())
    }

    @Test
    fun `sync session re-arms lost alarms when monitoring`() {
        // 进程被强停后闹钟全丢：MONITORING 且截止未到 → 补挂截止/平安短信/看门狗
        val (e, _, ports) = fixture()
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        ports.deadlines.clear()
        ports.dailySmsSchedules.clear()
        ports.watchdogDelays.clear()
        e.handle(Event.SyncSession)
        val s = e.snapshot()
        assertEquals(Phase.MONITORING, s.phase)
        assertEquals(s.deadlineAt, ports.deadlines.last())
        assertEquals(s.nextDailySmsAt, ports.dailySmsSchedules.last())
        assertEquals(GuardEngine.WATCHDOG_INTERVAL_MS, ports.watchdogDelays.last())
        assertTrue(ports.calls.isEmpty())
    }

    @Test
    fun `sync session schedules nothing when disabled`() {
        val (e, _, ports) = fixture()
        e.handle(Event.SyncSession)
        assertTrue(ports.deadlines.isEmpty())
        assertTrue(ports.confirmEnds.isEmpty())
        assertTrue(ports.dailySmsSchedules.isEmpty())
        assertTrue(ports.watchdogDelays.isEmpty())
        assertTrue(ports.calls.isEmpty())
    }

    @Test
    fun `sync session in CONFIRMING with overdue window never dials`() {
        // 过期确认窗口交给看门狗：SyncSession 不得拨号
        val (e, clock, ports) = fixture()
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        val before = e.snapshot()
        clock.advance(3 * 60_000L)   // 超过 2 分钟确认窗口
        e.handle(Event.SyncSession)
        val s = e.snapshot()
        assertEquals(Phase.CONFIRMING, s.phase)
        assertEquals(before.confirmEndAt, s.confirmEndAt)
        assertTrue(ports.calls.isEmpty())
        // 闹钟仍补挂：过期窗口不重复挂确认结束闹钟，平安短信/看门狗照旧
        assertEquals(1, ports.confirmEnds.count())
        assertEquals(GuardEngine.WATCHDOG_INTERVAL_MS, ports.watchdogDelays.last())
    }

    @Test
    fun `sync session in CONFIRMING re-arms pending confirm end`() {
        val (e, clock, ports) = fixture()
        ports.deviceInUse = false
        e.handle(Event.EnabledChanged(true))
        clock.advance(8 * 3600_000L)
        e.handle(Event.DeadlineAlarm)
        clock.advance(30_000L)       // 窗口未过期
        e.handle(Event.SyncSession)
        val s = e.snapshot()
        assertEquals(Phase.CONFIRMING, s.phase)
        assertEquals(s.confirmEndAt, ports.confirmEnds.last())
        assertTrue(ports.calls.isEmpty())
    }

    @Test
    fun `watchdog syncs session but never starts service when disabled`() {
        val (e, _, ports) = fixture()
        e.handle(Event.WatchdogAlarm)
        assertEquals(0, ports.ensureCount)
        assertTrue(ports.watchdogDelays.isEmpty())

        val (e2, _, ports2) = fixture()
        ports2.deviceInUse = false
        e2.handle(Event.EnabledChanged(true))
        ports2.deviceInUse = true
        e2.handle(Event.WatchdogAlarm)
        // 看门狗顺带找回漏掉的解锁会话
        assertTrue(e2.snapshot().inUse)
        assertEquals(ActivityKind.UNLOCK, e2.snapshot().lastActivityKind)
        assertEquals(2, ports2.watchdogDelays.size)
    }

    @Test
    fun `disable stops foreground service and clears inUse`() {
        val (e, _, ports) = fixture()
        e.handle(Event.EnabledChanged(true))
        assertTrue(e.snapshot().inUse)
        e.handle(Event.EnabledChanged(false))
        assertEquals(1, ports.stopServiceCount)
        assertFalse(e.snapshot().inUse)
    }

    @Test
    fun `in-use cap deadline still enters CONFIRMING`() {
        val (e, clock, _) = fixture(
            cfg = GuardConfig(contactNumber = num, testMode = true)
        )
        // 持续使用中：30 秒闹钟到点不算数，8 小时上限到点仍响铃
        e.handle(Event.EnabledChanged(true))
        clock.advance(30_000L)
        e.handle(Event.DeadlineAlarm)
        assertEquals(Phase.MONITORING, e.snapshot().phase)
        clock.advance(8 * 3600_000L - 30_000L)
        e.handle(Event.DeadlineAlarm)
        assertEquals(Phase.CONFIRMING, e.snapshot().phase)
    }

    @Test
    fun `call keeps inUse unchanged`() {
        val (e, clock, _) = fixture()
        e.handle(Event.EnabledChanged(true))
        clock.advance(60_000L)
        e.handle(Event.CallOffhook)
        assertTrue(e.snapshot().inUse)
        assertEquals(ActivityKind.CALL, e.snapshot().lastActivityKind)

        val (e2, clock2, ports2) = fixture()
        ports2.deviceInUse = false
        e2.handle(Event.EnabledChanged(true))
        clock2.advance(60_000L)
        e2.handle(Event.CallOffhook)
        assertFalse(e2.snapshot().inUse)
        assertEquals(ActivityKind.CALL, e2.snapshot().lastActivityKind)
    }
}
