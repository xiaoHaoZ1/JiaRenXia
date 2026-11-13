package io.github.xiaohaoz1.jiarenxia.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.xiaohaoz1.jiarenxia.MainActivity
import io.github.xiaohaoz1.jiarenxia.R
import io.github.xiaohaoz1.jiarenxia.core.model.ContactMethod
import io.github.xiaohaoz1.jiarenxia.data.CallQuestionRepository
import io.github.xiaohaoz1.jiarenxia.data.ContactPrompt
import io.github.xiaohaoz1.jiarenxia.data.ContactRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.domain.call.CallQuestions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PostCallEntryPoint {
    fun postCallNotifier(): PostCallNotifier
}

/**
 * 「打完电话记一笔」：从 App 拨号即记一条待提示并发一条静音通知——
 * App 无通话权限感知不到“挂断”，通知由系统托管，锁屏后、进程被回收都在；
 * 页面回到前台 10 分钟内照旧弹「联系上了吗」对话框，除此之外：
 * - 通知「联系上了」：不进 App 直接按拨号时刻记一条电话联系并划掉要问的事；
 * - 通知「没联系上」/划掉：收掉待提示与通知；
 * - 点通知正文：打开 App 到该人详情页并弹同一个对话框（不受 10 分钟窗口限制）。
 * 通知只有一条（同 id 覆盖），随最新一次拨号更新。
 */
@Singleton
class PostCallNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
    private val callQuestionRepository: CallQuestionRepository
) {

    private val _pending = MutableStateFlow<PostCallPending?>(null)

    /** 拨号后待提示（页面弹窗与通知共用同一份负载）。 */
    val pending: StateFlow<PostCallPending?> = _pending

    /** 拨给家人：记待提示 + 发通知（无通知权限时只留待提示，弹窗照旧）。 */
    fun dialMade(prompt: ContactPrompt) {
        _pending.value = PostCallPending(prompt)
        post(prompt)
    }

    /** 对话框已展示：待提示一次性消费掉；通知保留（仍可动作记一笔或再点进来）。 */
    fun consumeForDisplay() {
        _pending.value = null
    }

    /** 已答复（对话框两键 / 通知「没联系上」/ 划掉通知）：待提示与通知一起收掉。 */
    fun answered() {
        _pending.value = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * 「联系上了」归口：按拨号时刻记一条电话联系，
     * 删勾选的「只问一次」要问的事（“每次都问”永不在列），收掉通知。
     * 同一拨号时刻已有记录则不再插入（弹窗与通知动作可能并发答复）。
     */
    suspend fun logConnected(
        prompt: ContactPrompt,
        checkedQuestionIds: Set<String>
    ) {
        val dup = contactRepository.getLogs()
            .any { it.personId == prompt.personId && it.atMillis == prompt.atMillis }
        if (!dup) {
            contactRepository.logContact(
                prompt.personId, ContactMethod.CALL, null, prompt.atMillis)
        }
        val ids = CallQuestions.deletionIds(
            prompt.oneTimeQuestions, checkedQuestionIds)
        if (ids.isNotEmpty()) callQuestionRepository.deleteIds(ids)
        answered()
    }

    /**
     * 通知「联系上了」动作：不开 App 直接记。
     * 同一拨号时刻已有联系记录则跳过（页面/通知重复答复不重复记）；
     * 要删的「只问一次」优先用待提示快照，快照没了（进程被回收等）
     * 退化为该人当前「只问一次」全删——它们拨号前都弹过，语义一致。
     * @return true 新记一条；false 已记过或人物已不存在
     */
    suspend fun logConnectedFromNotification(
        personId: String,
        callAtMillis: Long
    ): Boolean {
        val dup = contactRepository.getLogs()
            .any { it.personId == personId && it.atMillis == callAtMillis }
        if (dup) {
            answered()
            return false
        }
        val once = _pending.value?.prompt
            ?.takeIf { it.personId == personId && it.atMillis == callAtMillis }
            ?.oneTimeQuestions
            ?: CallQuestions.onceQuestions(
                callQuestionRepository.getForPerson(personId))
        val r = contactRepository.logContact(
            personId, ContactMethod.CALL, null, callAtMillis)
        if (r is FamilyResult.Success) {
            callQuestionRepository.deleteIds(once.map { it.id })
            answered()
            return true
        }
        return false
    }

    /**
     * 通知正文点开：待提示恢复为强制态（force=true，不受 10 分钟窗口限制）。
     * 内存里的待提示对得上就沿用（保住拨号时的「只问一次」快照），
     * 否则按该人当前「只问一次」重建。
     */
    suspend fun rearm(prompt: ContactPrompt) {
        val cur = _pending.value?.prompt
        val merged = if (cur != null && cur.personId == prompt.personId &&
            cur.atMillis == prompt.atMillis
        ) {
            cur
        } else {
            prompt.copy(
                oneTimeQuestions = CallQuestions.onceQuestions(
                    callQuestionRepository.getForPerson(prompt.personId)))
        }
        _pending.value = PostCallPending(merged, force = true)
    }

    private fun post(prompt: ContactPrompt) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        runCatching {
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID, build(prompt))
        }
    }

    private fun build(prompt: ContactPrompt): android.app.Notification {
        // 三处 PendingIntent 共用同一组 extra：人物 + 拨号时刻 + 称呼
        val extras = Intent()
            .putExtra(MainActivity.EXTRA_PERSON_ID, prompt.personId)
            .putExtra(MainActivity.EXTRA_POSTCALL_AT, prompt.atMillis)
            .putExtra(MainActivity.EXTRA_POSTCALL_LABEL, prompt.label)
        // 正文：打开 App 到该人详情页并弹「联系上了吗」
        val open = PendingIntent.getActivity(
            context, REQ_OPEN,
            Intent(context, MainActivity::class.java).putExtras(extras)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(req: Int, act: String) = PendingIntent.getBroadcast(
            context, req,
            Intent(context, PostCallReceiver::class.java).setAction(act)
                .putExtras(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentText("家人侠：有联系待记录")
            .build()
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("家人侠")
            .setContentText("刚才给" + prompt.label + "打了电话，联系上了吗")
            .setWhen(prompt.atMillis)
            .setShowWhen(true)
            // 点开只算“开始处理”：不 autoCancel，答复后才由 answered() 收掉
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(open)
            .addAction(0, "联系上了", action(REQ_CONNECTED, ACTION_CONNECTED))
            .addAction(0, "没联系上", action(REQ_NOT_CONNECTED, ACTION_NOT_CONNECTED))
            .setDeleteIntent(action(REQ_DISMISSED, ACTION_DISMISSED))
            .build()
    }

    private fun ensureChannel() {
        // 静音渠道：通知发出时人通常正在通话，不作响、只在通知栏等
        val channel = NotificationChannel(
            CHANNEL_ID, "打完电话记一笔", NotificationManager.IMPORTANCE_LOW
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "postcall"
        val NOTIFICATION_ID = "postcall".hashCode()
        const val ACTION_CONNECTED =
            "io.github.xiaohaoz1.jiarenxia.POSTCALL_CONNECTED"
        const val ACTION_NOT_CONNECTED =
            "io.github.xiaohaoz1.jiarenxia.POSTCALL_NOT_CONNECTED"
        const val ACTION_DISMISSED =
            "io.github.xiaohaoz1.jiarenxia.POSTCALL_DISMISSED"
        private const val REQ_OPEN = 0
        private const val REQ_CONNECTED = 1
        private const val REQ_NOT_CONNECTED = 2
        private const val REQ_DISMISSED = 3
    }
}

/**
 * 待提示负载：拨号留下的 [ContactPrompt] + 是否越过 10 分钟窗口。
 * force=true 仅由通知点进产生——用户明确要求记这次，不再限窗口。
 */
data class PostCallPending(
    val prompt: ContactPrompt,
    val force: Boolean = false
)
