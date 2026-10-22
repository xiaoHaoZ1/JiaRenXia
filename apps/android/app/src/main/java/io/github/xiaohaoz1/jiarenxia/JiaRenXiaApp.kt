package io.github.xiaohaoz1.jiarenxia

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.xiaohaoz1.jiarenxia.data.db.PersonDao
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.update.UpdateChecker
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口。
 *
 * 实现 [Configuration.Provider] 并注入 [HiltWorkerFactory]，
 * 使 WorkManager 创建的 Worker 支持 Hilt 依赖注入；
 * Manifest 中已移除默认的 WorkManagerInitializer。
 *
 * 生日提醒调度经 [StartupGate] 门控，等用户解锁后启动（Direct Boot 下
 * credential-protected 的 Room/DataStore/WorkManager 尚不可用）。
 * 守护功能已拆为独立应用「守护侠」（C3），本应用不再初始化守护模块。
 */
@HiltAndroidApp
class JiaRenXiaApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderSync: ReminderSync

    @Inject
    lateinit var updateChecker: UpdateChecker

    @Inject
    lateinit var settings: SettingsRepository

    @Inject
    lateinit var personDao: PersonDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // 生日提醒调度、插件表监听与更新检查：解锁后再启动
        StartupGate.start(this) {
            reminderSync.start()
            io.github.xiaohaoz1.jiarenxia.widget.WidgetRefresh.install(this)
            // 「联网检查更新」默认关：未开启时内部直接返回，不发任何请求
            appScope.launch { updateChecker.autoCheckIfDue() }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
