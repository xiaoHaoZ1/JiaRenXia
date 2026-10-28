package io.github.xiaohaoz1.jiarenxia.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 应用级一次性事件（不带状态、可丢）：供“清空所有数据”这类跨界面跳转使用。
 * 锁屏覆盖层期间 AppNavHost 仍在组合中，能正常收到事件并导航到引导页；
 * 不用 Activity.recreate()——它会保留 ViewModel，事件状态残留会循环重建。
 */
@Singleton
class AppEvents @Inject constructor() {
    private val _dataCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 全部数据已清空（我的页清空 / 锁屏忘记密码清空都会发）。 */
    val dataCleared: SharedFlow<Unit> = _dataCleared

    suspend fun emitDataCleared() {
        _dataCleared.emit(Unit)
    }

    /** 待导入的家人共享（从恢复流程识别到 purpose=share 时塞入）。 */
    var pendingSharePlan: io.github.xiaohaoz1.jiarenxia.data.backup.RestorePlan? = null

    /** 拨打家人电话后待提示的“联系上了吗”（10 分钟窗口，联系提醒）。 */
    var pendingContactPrompt: ContactPrompt? = null
}

/** “刚才联系上了吗”提示的负载。 */
data class ContactPrompt(
    val personId: String,
    /** 称谓或姓名（拨号入口当时显示的称呼；无称谓回退姓名）。 */
    val label: String,
    /** 拨号时刻；“联系上了”按这个时刻记一条电话联系。 */
    val atMillis: Long
)
