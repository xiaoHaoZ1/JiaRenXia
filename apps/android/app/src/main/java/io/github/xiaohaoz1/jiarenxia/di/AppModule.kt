package io.github.xiaohaoz1.jiarenxia.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.IdGenerator
import io.github.xiaohaoz1.jiarenxia.data.SystemTimeSource
import io.github.xiaohaoz1.jiarenxia.data.TimeSource
import io.github.xiaohaoz1.jiarenxia.data.UuidIdGenerator
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorage
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorageImpl
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_1_2
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_2_3
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_3_4
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_4_5
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_5_6
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_6_7
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_7_8
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_8_9
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_9_10
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_10_11
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_11_12
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_12_13
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_13_14
import io.github.xiaohaoz1.jiarenxia.data.db.MIGRATION_14_15
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.settings.SettingsRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.reminder.ReminderScheduler
import io.github.xiaohaoz1.jiarenxia.reminder.WorkManagerReminderScheduler
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "jiarenxia.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
            .build()

    @Provides fun providePersonDao(db: AppDatabase) = db.personDao()
    @Provides fun providePhoneNumberDao(db: AppDatabase) = db.phoneNumberDao()
    @Provides fun provideRelationshipDao(db: AppDatabase) = db.relationshipDao()
    @Provides fun provideImportantDateDao(db: AppDatabase) = db.importantDateDao()
    @Provides fun provideReminderPreferenceDao(db: AppDatabase) = db.reminderPreferenceDao()
    @Provides fun provideReminderOffsetDao(db: AppDatabase) = db.reminderOffsetDao()
    @Provides fun provideFamilyTaskDao(db: AppDatabase) = db.familyTaskDao()
    @Provides fun provideFamilyTaskOffsetDao(db: AppDatabase) = db.familyTaskOffsetDao()

    @Provides
    @Singleton
    fun provideLunarCalendar(): io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar =
        io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") }
        )

    @Provides
    @Singleton
    fun provideKinshipEngine(): KinshipEngine = KinshipEngine()

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = UuidIdGenerator()

    @Provides
    @Singleton
    fun provideTimeSource(): TimeSource = SystemTimeSource()

    /** 应用级协程域：供需要存活整个进程期的后台工作使用。 */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** 应用级协程域限定符。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds
    @Singleton
    abstract fun bindFamilyRepository(impl: FamilyRepositoryImpl): FamilyRepository

    @Binds
    @Singleton
    abstract fun bindAvatarStorage(impl: AvatarStorageImpl): AvatarStorage

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler

    @Binds
    @Singleton
    abstract fun bindDataClearer(
        impl: io.github.xiaohaoz1.jiarenxia.domain.ClearAllDataUseCase
    ): io.github.xiaohaoz1.jiarenxia.domain.DataClearer

    @Binds
    @Singleton
    abstract fun bindFamilyTaskRepository(
        impl: io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepositoryImpl
    ): io.github.xiaohaoz1.jiarenxia.data.FamilyTaskRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(
        impl: io.github.xiaohaoz1.jiarenxia.data.ContactRepositoryImpl
    ): io.github.xiaohaoz1.jiarenxia.data.ContactRepository

    /** 碰一碰配对存储：Room（v9）实现；旧 JSON 文件记录首次访问时迁入。 */
    @Binds
    @Singleton
    abstract fun bindSpousePairingStore(
        impl: io.github.xiaohaoz1.jiarenxia.data.tap.RoomSpousePairingStore
    ): io.github.xiaohaoz1.jiarenxia.domain.merge.SpousePairingStore
}
