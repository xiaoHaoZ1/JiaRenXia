package io.github.xiaohaoz1.jiarenxia.guardian.sys

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.xiaohaoz1.jiarenxia.guardian.core.GuardState
import io.github.xiaohaoz1.jiarenxia.guardian.core.TimeRules

/** 每日平安短信：文案在本层拼装（含电量，需要 Android API）。 */
class SmsReporter(private val context: Context) {

    fun sendDailySms(): Boolean {
        val cfg = ServiceLocator.store.configFlow.value
        val state = ServiceLocator.store.stateFlow.value
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "SEND_SMS 未授权，无法发送平安短信")
            return false
        }
        val body = buildBody(state)
        val sm: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        if (sm == null) {
            Log.e(TAG, "SmsManager 不可用")
            return false
        }
        return try {
            sm.sendMultipartTextMessage(
                cfg.contactNumber, null, sm.divideMessage(body), null, null
            )
            Log.d(TAG, "平安短信已发送至 " +
                io.github.xiaohaoz1.jiarenxia.guardian.core.maskNumber(cfg.contactNumber))
            true
        } catch (t: Throwable) {
            Log.e(TAG, "平安短信发送失败", t)
            false
        }
    }

    /** 平安短信正文（文案逻辑在 core/SmsText，单测覆盖）。 */
    fun buildBody(state: GuardState): String {
        val now = System.currentTimeMillis()
        val (pct, charging) = batteryInfo()
        return io.github.xiaohaoz1.jiarenxia.guardian.core.SmsText.daily(
            state.lastActivityAt, now, pct, charging, state.inUse
        )
    }

    /** 拨号失败的兜底紧急短信。 */
    fun sendEmergencySms(): Boolean {
        val cfg = ServiceLocator.store.configFlow.value
        val state = ServiceLocator.store.stateFlow.value
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "SEND_SMS 未授权，无法发送紧急短信")
            return false
        }
        val body = io.github.xiaohaoz1.jiarenxia.guardian.core.SmsText.emergency(
            state.lastActivityAt
        )
        val sm: SmsManager = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            ) ?: return false
        return try {
            sm.sendMultipartTextMessage(
                cfg.contactNumber, null, sm.divideMessage(body), null, null
            )
            Log.d(TAG, "紧急短信已发送至 " +
                io.github.xiaohaoz1.jiarenxia.guardian.core.maskNumber(cfg.contactNumber))
            true
        } catch (t: Throwable) {
            Log.e(TAG, "紧急短信发送失败", t)
            false
        }
    }

    private fun batteryInfo(): Pair<Int, Boolean> {
        return try {
            val bat: Intent? = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            pct to charging
        } catch (t: Throwable) {
            -1 to false
        }
    }

    companion object {
        const val TAG = "GuardSms"
    }
}
