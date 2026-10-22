package io.github.xiaohaoz1.jiarenxia

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserManager
import androidx.core.content.ContextCompat

/**
 * Direct Boot 启动门控（DEVELOPMENT_PLAN 6.5）：
 * 用户解锁前 device-protected 之外的存储（Room/DataStore/WorkManager）不可用，
 * 因此生日提醒调度延后到 [UserManager.isUserUnlocked] 为真时启动；
 * 未解锁时注册 ACTION_USER_UNLOCKED 监听，解锁后只启动一次并自注销。
 */
object StartupGate {

    /**
     * @param isUnlocked 用户提供解锁判定（可注入，便于单测）
     * @param onUnlocked 解锁后要执行的启动动作（只调用一次）
     */
    fun start(
        context: Context,
        isUnlocked: (Context) -> Boolean = {
            it.getSystemService(UserManager::class.java)?.isUserUnlocked == true
        },
        onUnlocked: () -> Unit
    ) {
        if (isUnlocked(context)) {
            onUnlocked()
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                    onUnlocked()
                    ctx.unregisterReceiver(this)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_USER_UNLOCKED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
}
