package io.github.xiaohaoz1.jiarenxia.guardian.sys

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import io.github.xiaohaoz1.jiarenxia.guardian.R

/** 权限清单页数据源：逐项检查 + 对应跳转 Intent。 */
object Permissions {

    data class PermissionItem(
        val key: String,
        val title: String,
        val desc: String,
        val granted: Boolean,
        /** 可选权限（缺失只提示不算红灯）。 */
        val optional: Boolean = false,
        /** 点击“去开启”要启动的 Intent。 */
        val settingsIntent: Intent? = null,
    )

    /**
     * 开启守护时需要一起申请的运行时权限。
     * 发送短信是可选权限，不在开启时申请；用到平安短信时单独申请。
     */
    val runtimePerms: List<String>
        get() = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }

    fun snapshot(context: Context): List<PermissionItem> {
        val items = mutableListOf<PermissionItem>()

        fun runtime(
            key: String, titleRes: Int, descRes: Int, perm: String,
            optional: Boolean = false
        ) {
            items += PermissionItem(
                key = key,
                title = context.getString(titleRes),
                desc = context.getString(descRes),
                granted = ContextCompat.checkSelfPermission(context, perm) ==
                    PackageManager.PERMISSION_GRANTED,
                optional = optional,
                settingsIntent = appDetails(context),
            )
        }

        runtime("call_phone", R.string.perm_call_phone, R.string.perm_call_phone_desc,
            Manifest.permission.CALL_PHONE)
        runtime("phone_state", R.string.perm_phone_state, R.string.perm_phone_state_desc,
            Manifest.permission.READ_PHONE_STATE)
        // 发送短信为可选权限：缺失只影响平安短信与紧急短信，不影响开启守护
        runtime("send_sms", R.string.perm_send_sms, R.string.perm_send_sms_desc,
            Manifest.permission.SEND_SMS, optional = true)
        if (Build.VERSION.SDK_INT >= 33) {
            runtime("notifications", R.string.perm_notifications,
                R.string.perm_notifications_desc, Manifest.permission.POST_NOTIFICATIONS)
        }

        // 忽略电池优化
        val pm = context.getSystemService(PowerManager::class.java)
        val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        items += PermissionItem(
            key = "battery",
            title = context.getString(R.string.perm_battery),
            desc = context.getString(R.string.perm_battery_desc),
            granted = ignoring,
            settingsIntent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
        )

        // 使用情况访问（可选兜底）
        items += PermissionItem(
            key = "usage_stats",
            title = context.getString(R.string.perm_usage_stats),
            desc = context.getString(R.string.perm_usage_stats_desc),
            granted = UsageStatsReconcile(context).hasAccess(),
            optional = true,
            settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )

        // 精确闹钟：API31/32 需要 SCHEDULE_EXACT_ALARM；33+ USE_EXACT_ALARM 自动授予
        val exactGranted = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> true
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> true
            else -> context.getSystemService(AlarmManager::class.java)
                ?.canScheduleExactAlarms() == true
        }
        items += PermissionItem(
            key = "exact_alarm",
            title = context.getString(R.string.perm_exact_alarm),
            desc = context.getString(R.string.perm_exact_alarm_desc),
            granted = exactGranted,
            settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                )
            } else {
                appDetails(context)
            },
        )

        // 全屏通知：API34+ 需 canUseFullScreenIntent
        val fsiGranted = if (Build.VERSION.SDK_INT >= 34) {
            context.getSystemService(NotificationManager::class.java)
                ?.canUseFullScreenIntent() == true
        } else {
            true
        }
        items += PermissionItem(
            key = "fullscreen",
            title = context.getString(R.string.perm_fullscreen),
            desc = context.getString(R.string.perm_fullscreen_desc),
            granted = fsiGranted,
            settingsIntent = if (Build.VERSION.SDK_INT >= 34) {
                Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:${context.packageName}"),
                )
            } else {
                appDetails(context)
            },
        )

        // 小米系额外一道闸：appops 10021「后台弹出界面」。
        // 未开时 fullScreenIntent 被降级成普通通知，确认页不弹出——只显示在小米设备上。
        if (isMiuiDevice()) {
            items += PermissionItem(
                key = "miui_bg_popup",
                title = context.getString(R.string.perm_miui_bg_popup),
                desc = context.getString(R.string.perm_miui_bg_popup_desc),
                granted = checkMiuiOp(context, MIUI_OP_BACKGROUND_POPUP) ==
                    AppOpsManager.MODE_ALLOWED,
                settingsIntent = appDetails(context),
            )
        }

        return items
    }

    /** 小米/红米/POCO（含澎湃OS）：厂商名或 ro.miui.ui.version.name 任一命中。 */
    private fun isMiuiDevice(): Boolean {
        if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return true
        return runCatching {
            @Suppress("PrivateApi")
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, "ro.miui.ui.version.name") as? String
        }.getOrNull()?.isNotEmpty() == true
    }

    /** 读 MIUI 私有 appops 值：int op 的重载不在公开 SDK，走反射调用。 */
    private fun checkMiuiOp(context: Context, op: Int): Int {
        val ops = context.getSystemService(AppOpsManager::class.java)
            ?: return AppOpsManager.MODE_ERRORED
        return runCatching {
            @Suppress("DiscouragedPrivateApi")
            AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java,
            ).invoke(ops, op, Process.myUid(), context.packageName) as Int
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
    }

    /** MIUI 私有 op：后台弹出界面。 */
    private const val MIUI_OP_BACKGROUND_POPUP = 10021

    private fun appDetails(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
}
