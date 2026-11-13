package io.github.xiaohaoz1.jiarenxia.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import dagger.hilt.android.EntryPointAccessors
import io.github.xiaohaoz1.jiarenxia.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「打完电话记一笔」通知的动作与划掉处理（显式 PendingIntent，不导出）：
 * - 联系上了：不开 App 直接按拨号时刻记一条，Toast 确认；
 * - 没联系上 / 划掉：收掉待提示与通知。
 */
class PostCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifier = EntryPointAccessors.fromApplication(
            context.applicationContext, PostCallEntryPoint::class.java
        ).postCallNotifier()
        when (intent.action) {
            PostCallNotifier.ACTION_CONNECTED -> {
                val personId = intent.getStringExtra(MainActivity.EXTRA_PERSON_ID)
                    ?: return
                val at = intent.getLongExtra(MainActivity.EXTRA_POSTCALL_AT, 0L)
                if (at <= 0L) return
                val label = intent.getStringExtra(
                    MainActivity.EXTRA_POSTCALL_LABEL) ?: ""
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val logged = notifier.logConnectedFromNotification(
                            personId, at)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (logged) "已记下和" + label + "的联系"
                                else "这条之前已经记过了",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            PostCallNotifier.ACTION_NOT_CONNECTED,
            PostCallNotifier.ACTION_DISMISSED -> notifier.answered()
        }
    }
}
