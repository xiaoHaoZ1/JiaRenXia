package io.github.xiaohaoz1.jiarenxia.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.room.InvalidationTracker
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * 数据变更 → 刷新桌面插件：监听时间线相关表的失效事件（防抖 400ms，
 * 一次事务写多张表只刷一次）。Room 属凭据保护存储，只能由
 * StartupGate 在用户解锁后调用安装。
 */
object WidgetRefresh {

    private val installed = AtomicBoolean(false)
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** 安装失效监听并立即刷新一次（覆盖未解锁时画出的占位）。 */
    fun install(context: Context) {
        val app = context.applicationContext
        if (!installed.compareAndSet(false, true)) return
        val ep = WidgetEntryPoint.of(app)
        val db = ep.database()
        db.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(WATCHED_TABLES) {
                override fun onInvalidated(tables: Set<String>) {
                    ticks.tryEmit(Unit)
                }
            }
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // 插件配色（DataStore，不在表里）变化也要即时重绘；
        // drop(1) 跳过安装时初值——下面本来就先刷一次
        scope.launch {
            ep.settings().widgetThemeMode.drop(1).collect { ticks.tryEmit(Unit) }
        }
        scope.launch {
            runCatching { JrxAppWidget().updateAll(app) }
            ticks.collectLatest {
                delay(400)
                runCatching { JrxAppWidget().updateAll(app) }
            }
        }
    }

    /** 影响时间线展示的全部表。 */
    private val WATCHED_TABLES = arrayOf(
        "persons", "phone_numbers", "relationships", "custom_titles",
        "person_alias", "important_dates", "reminder_preferences",
        "reminder_offsets", "family_tasks", "family_task_offsets",
        "contact_plans", "contact_logs",
    )
}
