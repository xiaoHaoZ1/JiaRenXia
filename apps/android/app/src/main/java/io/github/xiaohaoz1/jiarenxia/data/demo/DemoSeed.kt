package io.github.xiaohaoz1.jiarenxia.data.demo

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.xiaohaoz1.jiarenxia.BuildConfig
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.domain.ClearAllDataUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 「演示版」内置家庭：首启空库自动导入、我的页一键重置。
 * 仅当 BuildConfig.DEMO_ROLE 非空（demo 变体，-PdemoRole=groom|bride）时生效；
 * 正式版与测试版 DEMO_ROLE 为空，两个入口都直接返回 false，行为不变。
 * 接口供启动/我的页注入，测试可替换假实现（同 DataClearer 先例）。
 */
interface DemoSeed {
    /** 角色 → 内置资产文件名；非演示角色返回 null。 */
    fun assetFor(role: String): String?

    /** 角色 → 「我」的展示名（重置确认框文案用）。 */
    fun displayNameFor(role: String): String?

    /** 当前构建的演示展示名；非演示版返回 null。 */
    fun displayName(): String?

    /**
     * 首启预装：仅演示版、库里还没有任何人物时导入对应资产，
     * 并把引导标志置为已看（配合 StartViewModel 使首屏直达主界面）。
     * 导入失败只记日志返回 false，走正常欢迎流程，不崩。
     */
    suspend fun seedIfEmpty(role: String = BuildConfig.DEMO_ROLE): Boolean

    /**
     * 重置演示数据：清空所有数据（含碰一碰配对/映射/别名/快速存档）
     * → 重新导入资产 → 写回外观设置（清空与备份恢复都会改写主题/字号）。
     */
    suspend fun resetDemo(role: String = BuildConfig.DEMO_ROLE): Boolean
}

/** [DemoSeed] 的 Room/备份实现。 */
@Singleton
class DemoSeedImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FamilyRepository,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val clearAll: ClearAllDataUseCase,
    private val db: AppDatabase
) : DemoSeed {

    override fun assetFor(role: String): String? = when (role) {
        ROLE_GROOM -> "demo_groom_zhudi.jrxb"
        ROLE_BRIDE -> "demo_bride_xu.jrxb"
        else -> null
    }

    override fun displayNameFor(role: String): String? = when (role) {
        ROLE_GROOM -> "朱棣"
        ROLE_BRIDE -> "徐皇后"
        else -> null
    }

    override fun displayName(): String? = displayNameFor(BuildConfig.DEMO_ROLE)

    override suspend fun seedIfEmpty(role: String): Boolean {
        val asset = assetFor(role) ?: return false
        if (repository.observeFamilyGraph().first().persons.isNotEmpty()) return false
        return import(asset).also { ok ->
            if (ok) settings.setIntroSeen(true)
        }
    }

    override suspend fun resetDemo(role: String): Boolean {
        val asset = assetFor(role) ?: return false
        // 外观设置保留：清空与备份恢复都会改写主题/字号/插件配色/长辈模式
        val mode = settings.themeMode.first()
        val style = settings.themeStyle.first()
        val factor = settings.fontScaleFactor.first()
        val widgetMode = settings.widgetThemeMode.first()
        val elder = settings.elderMode.first()
        clearAll.run()
        // tap_device 是设备级连接偏好，通用清空刻意保留；演示重置要回到出厂态
        db.tapPairingDao().deleteDevice()
        val ok = import(asset)
        settings.setThemeMode(mode)
        settings.setThemeStyle(style)
        settings.setFontScaleFactor(factor)
        settings.setWidgetThemeMode(widgetMode)
        settings.setElderMode(elder)
        settings.setIntroSeen(true)
        return ok
    }

    /** 走现有备份管线解密 + 恢复（inspect → restore），失败记日志返回 false。 */
    private suspend fun import(asset: String): Boolean = runCatching {
        context.assets.open(asset).use { input ->
            val plan = backup.inspect(input, PASSWORD).getOrThrow()
            backup.restore(plan)
        }
    }.onFailure {
        Log.e(TAG, "演示数据导入失败：$asset", it)
    }.isSuccess

    private companion object {
        const val TAG = "DemoSeed"
        const val ROLE_GROOM = "groom"
        const val ROLE_BRIDE = "bride"
        val PASSWORD = "test1234".toCharArray()
    }
}
