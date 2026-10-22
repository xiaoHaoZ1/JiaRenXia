package io.github.xiaohaoz1.jiarenxia.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.xiaohaoz1.jiarenxia.data.ContactRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.domain.ObserveFamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar

/** 桌面插件取依赖的入口（GlanceAppWidget/Receiver 不走 Hilt 字段注入）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    fun familyRepository(): FamilyRepository
    fun taskRepository(): FamilyTaskRepository
    fun contactRepository(): ContactRepository
    fun observeOverview(): ObserveFamilyOverview
    fun lunarCalendar(): LunarCalendar
    fun database(): AppDatabase

    companion object {
        fun of(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, WidgetEntryPoint::class.java)
    }
}
