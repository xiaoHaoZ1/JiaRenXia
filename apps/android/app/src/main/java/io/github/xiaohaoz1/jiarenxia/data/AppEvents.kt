package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.domain.call.CallQuestionPrompt
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
}

/** “刚才联系上了吗”提示的负载。 */
data class ContactPrompt(
    val personId: String,
    /** 称谓或姓名（拨号入口当时显示的称呼；无称谓回退姓名）。 */
    val label: String,
    /** 拨号时刻；“联系上了”按这个时刻记一条电话联系。 */
    val atMillis: Long,
    /**
     * 这次拨号前弹过的「只问一次」要问的事（id + 文字；
     * 只在内存里随提示走，不持久化）。「每次都问」的条目不在这里，
     * “联系上了”时按勾选删除这里列出的条目。
     */
    val oneTimeQuestions: List<CallQuestionPrompt> = emptyList()
)
