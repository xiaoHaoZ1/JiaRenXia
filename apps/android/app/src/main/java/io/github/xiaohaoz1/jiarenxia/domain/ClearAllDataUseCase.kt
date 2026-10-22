package io.github.xiaohaoz1.jiarenxia.domain

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.backup.QuickSnapshotStore
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

/** “清空所有数据”入口抽象：调用方按接口注入，测试可替换成假实现。 */
interface DataClearer {
    suspend fun run()
}

/**
 * 清空所有数据（DEVELOPMENT_PLAN 2.6）：数据库、头像目录、设置、
 * 快速存档、提醒任务与通知。
 * 测试工具里的“清空数据但保留存档”走 [run] 的 keepSnapshots 变体。
 */
@Singleton
class ClearAllDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FamilyRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val snapshots: QuickSnapshotStore
) : DataClearer {
    override suspend fun run() = run(keepSnapshots = false)

    /** [keepSnapshots] 为 true 时保留快速存档（“清空数据但保留存档”）。 */
    suspend fun run(keepSnapshots: Boolean) {
        repository.clearAllData()
        settings.clear()
        scheduler.cancelAll()
        // 协作接收暂存的加密文件随资料一起清（与 BackupManager.restore 一致）
        java.io.File(context.filesDir, "collab_incoming").deleteRecursively()
        if (!keepSnapshots) snapshots.clear()
        NotificationManagerCompat.from(context).cancelAll()
    }
}
