package io.github.xiaohaoz1.jiarenxia.guardian

import io.github.xiaohaoz1.jiarenxia.guardian.core.Clock
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardConfig
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardEngine
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardState
import io.github.xiaohaoz1.jiarenxia.guardian.core.Ports

class FakeClock(var t: Long) : Clock {
    override fun now(): Long = t
    fun advance(ms: Long) {
        t += ms
    }
}

class FakeStore(
    var config: GuardConfig = GuardConfig(),
    var state: GuardState = GuardState(),
) : GuardEngine.Store {
    override fun loadConfig(): GuardConfig = config
    override fun saveConfig(c: GuardConfig) {
        config = c
    }

    override fun loadState(): GuardState = state
    override fun saveState(s: GuardState) {
        state = s
    }
}

class FakePorts : Ports {
    val deadlines = mutableListOf<Long>()
    val confirmEnds = mutableListOf<Long>()
    val dailySmsSchedules = mutableListOf<Long>()
    val watchdogDelays = mutableListOf<Long>()
    var cancelConfirmCount = 0
    var cancelAllCount = 0
    val ringingStarts = mutableListOf<Long>()
    var ringStopCount = 0
    val confirmNotifs = mutableListOf<Long>()
    val alertedNotifs = mutableListOf<Long>()
    val calls = mutableListOf<String>()
    var smsCount = 0
    var emergencySmsCount = 0
    var ensureCount = 0
    var stopServiceCount = 0
    var deviceInUse = true
    var reconcileResult: Long? = null
    var throwOnPlaceCall: Throwable? = null
    var placeCallResult = true
    val logs = mutableListOf<Pair<String, String>>()

    override fun scheduleDeadline(at: Long) {
        deadlines += at
    }

    override fun scheduleConfirmEnd(at: Long) {
        confirmEnds += at
    }

    override fun scheduleDailySms(at: Long) {
        dailySmsSchedules += at
    }

    override fun scheduleWatchdog(delayMillis: Long) {
        watchdogDelays += delayMillis
    }

    override fun cancelConfirmAndAlert() {
        cancelConfirmCount++
    }

    override fun cancelAll() {
        cancelAllCount++
    }

    override fun startConfirmRinging(confirmEndAt: Long) {
        ringingStarts += confirmEndAt
    }

    override fun stopRinging() {
        ringStopCount++
    }

    override fun showConfirmNotification(confirmEndAt: Long) {
        confirmNotifs += confirmEndAt
    }

    override fun showAlertedNotification(alertedAt: Long) {
        alertedNotifs += alertedAt
    }

    override fun placeCall(number: String): Boolean {
        throwOnPlaceCall?.let { throw it }
        calls += number
        return placeCallResult
    }

    override fun sendDailySms() {
        smsCount++
    }

    override fun sendEmergencySms() {
        emergencySmsCount++
    }

    override fun ensureServiceRunning() {
        ensureCount++
    }

    override fun stopService() {
        stopServiceCount++
    }

    override fun isDeviceInUse(): Boolean = deviceInUse

    override fun reconcileMissedUnlockSince(since: Long): Long? = reconcileResult

    override fun log(tag: String, msg: String) {
        logs += tag to msg
    }
}
