package io.github.xiaohaoz1.jiarenxia.data.db

import android.content.Context
import android.os.Build
import androidx.room.Room
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
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 数据库 v1 → v2 迁移：important_dates 增加 is_leap_month，行数据与偏好必须保留。
 *
 * 说明：room-testing 的 MigrationTestHelper 在 Robolectric 下因内置
 * SupportSQLiteDriver 的“库名 vs 路径”校验而无法使用（Room 2.8），
 * 这里改为按 1.json 的 createSql 手工建 v1 库，再由 Room 以 v2 打开：
 * Room 自动执行 [MIGRATION_1_2] 并对所有表做 schema 校验，最后经 DAO 校验数据。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class MigrationTest {

    /** 按 v1 schema（1.json 的 createSql）建库文件并插入一行带偏好的公历生日。 */
    private fun createV1Database(context: Context, dbFile: File) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `persons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `gender` TEXT NOT NULL, `avatar_path` TEXT, `note` TEXT, `living_status` TEXT NOT NULL, `is_self` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_persons_name` ON `persons` (`name`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_persons_is_self` ON `persons` (`is_self`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `phone_numbers` (`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `number` TEXT NOT NULL, `label` TEXT NOT NULL, `status` TEXT NOT NULL, `is_primary` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_phone_numbers_person_id_number` ON `phone_numbers` (`person_id`, `number`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_phone_numbers_number` ON `phone_numbers` (`number`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_phone_numbers_person_id_status` ON `phone_numbers` (`person_id`, `status`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `relationships` (`id` TEXT NOT NULL, `source_person_id` TEXT NOT NULL, `target_person_id` TEXT NOT NULL, `type` TEXT NOT NULL, `relative_age_order` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`source_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_relationships_source_person_id_target_person_id_type` ON `relationships` (`source_person_id`, `target_person_id`, `type`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_relationships_target_person_id` ON `relationships` (`target_person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_relationships_type` ON `relationships` (`type`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `important_dates` (`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `type` TEXT NOT NULL, `year` INTEGER, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `calendar_type` TEXT NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_important_dates_person_id_type` ON `important_dates` (`person_id`, `type`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_important_dates_month_day` ON `important_dates` (`month`, `day`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `reminder_preferences` (`id` TEXT NOT NULL, `important_date_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`important_date_id`) REFERENCES `important_dates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_preferences_important_date_id` ON `reminder_preferences` (`important_date_id`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `reminder_offsets` (`id` TEXT NOT NULL, `reminder_preference_id` TEXT NOT NULL, `days_before` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`reminder_preference_id`) REFERENCES `reminder_preferences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_offsets_reminder_preference_id_days_before` ON `reminder_offsets` (`reminder_preference_id`, `days_before`)""")
                        // v1 测试数据：一条公历生日 + 默认偏好 + 偏移
                        db.execSQL("""INSERT INTO persons VALUES
                            ('p1','爸爸','MALE',NULL,NULL,'ALIVE',0,1000,1000)""")
                        db.execSQL("""INSERT INTO important_dates VALUES
                            ('d1','p1','BIRTHDAY',1960,3,15,'SOLAR',NULL,1000,1000)""")
                        db.execSQL("""INSERT INTO reminder_preferences VALUES
                            ('rp1','d1',1,1000,1000)""")
                        db.execSQL("""INSERT INTO reminder_offsets VALUES ('o1','rp1',0)""")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                    ) {}
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    /** 按 v3 schema（3.json 的 createSql）建库：v2 七表 + custom_titles + 一条数据。 */
    private fun createV3Database(context: Context, dbFile: File) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `persons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `gender` TEXT NOT NULL, `avatar_path` TEXT, `note` TEXT, `living_status` TEXT NOT NULL, `is_self` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_persons_name` ON `persons` (`name`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_persons_is_self` ON `persons` (`is_self`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `phone_numbers` (`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `number` TEXT NOT NULL, `label` TEXT NOT NULL, `status` TEXT NOT NULL, `is_primary` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_phone_numbers_person_id_number` ON `phone_numbers` (`person_id`, `number`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_phone_numbers_number` ON `phone_numbers` (`number`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_phone_numbers_person_id_status` ON `phone_numbers` (`person_id`, `status`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `relationships` (`id` TEXT NOT NULL, `source_person_id` TEXT NOT NULL, `target_person_id` TEXT NOT NULL, `type` TEXT NOT NULL, `relative_age_order` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`source_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_relationships_source_person_id_target_person_id_type` ON `relationships` (`source_person_id`, `target_person_id`, `type`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_relationships_target_person_id` ON `relationships` (`target_person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_relationships_type` ON `relationships` (`type`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `important_dates` (`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `type` TEXT NOT NULL, `year` INTEGER, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `calendar_type` TEXT NOT NULL, `is_leap_month` INTEGER NOT NULL DEFAULT 0, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_important_dates_person_id_type` ON `important_dates` (`person_id`, `type`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_important_dates_month_day` ON `important_dates` (`month`, `day`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `reminder_preferences` (`id` TEXT NOT NULL, `important_date_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`important_date_id`) REFERENCES `important_dates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_preferences_important_date_id` ON `reminder_preferences` (`important_date_id`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `reminder_offsets` (`id` TEXT NOT NULL, `reminder_preference_id` TEXT NOT NULL, `days_before` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`reminder_preference_id`) REFERENCES `reminder_preferences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_offsets_reminder_preference_id_days_before` ON `reminder_offsets` (`reminder_preference_id`, `days_before`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `custom_titles` (`id` TEXT NOT NULL, `observer_person_id` TEXT NOT NULL, `target_person_id` TEXT NOT NULL, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`observer_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_titles_observer_person_id_target_person_id` ON `custom_titles` (`observer_person_id`, `target_person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_custom_titles_target_person_id` ON `custom_titles` (`target_person_id`)""")
                        db.execSQL("""INSERT INTO persons VALUES ('me','我','MALE',NULL,NULL,'ALIVE',1,1000,1000)""")
                        db.execSQL("""INSERT INTO persons VALUES ('p1','爸爸','MALE',NULL,NULL,'ALIVE',0,1000,1000)""")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                    ) {}
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    /** 退出表是否已从库中消失（v13 断言助手）。 */
    private fun assertRetiredTablesGone(db: AppDatabase) {
        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table'"
        ).use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names += c.getString(0)
            assertTrue("gift_events 应已删除", "gift_events" !in names)
            assertTrue("gift_records 应已删除", "gift_records" !in names)
            assertTrue("insurance_policies 应已删除", "insurance_policies" !in names)
        }
        // family_tasks 不再有 policy_id 列
        db.openHelper.writableDatabase.query(
            "PRAGMA table_info(family_tasks)"
        ).use { c ->
            val cols = mutableListOf<String>()
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) cols += c.getString(idx)
            assertTrue("policy_id 列应已删除", "policy_id" !in cols)
            assertTrue("person_id 保留", "person_id" in cols)
        }
    }

    @Test
    fun v4库升级到v13_家人事项保留且退出表消失() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-413.db")
        dbFile.delete()
        createV4Database(context, dbFile)

        // Room 以 v13 打开：整条迁移链 4→13 必须走通
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        assertEquals(2, db.personDao().getAll().size)
        assertRetiredTablesGone(db)
        db.close()
        dbFile.delete()
    }

    @Test
    fun v5库升级到v13_链走通且退出表消失() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-513.db")
        dbFile.delete()
        createV5Database(context, dbFile)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
            )
            .allowMainThreadQueries()
            .build()

        assertEquals(2, db.personDao().getAll().size)
        assertRetiredTablesGone(db)
        db.close()
        dbFile.delete()
    }

    private fun createV4Database(context: Context, dbFile: File) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `persons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `gender` TEXT NOT NULL, `avatar_path` TEXT, `note` TEXT, `living_status` TEXT NOT NULL, `is_self` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_persons_name` ON `persons` (`name`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_persons_is_self` ON `persons` (`is_self`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `phone_numbers` (`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `number` TEXT NOT NULL, `label` TEXT NOT NULL, `status` TEXT NOT NULL, `is_primary` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_phone_numbers_person_id_number` ON `phone_numbers` (`person_id`, `number`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_phone_numbers_number` ON `phone_numbers` (`number`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_phone_numbers_person_id_status` ON `phone_numbers` (`person_id`, `status`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `relationships` (`id` TEXT NOT NULL, `source_person_id` TEXT NOT NULL, `target_person_id` TEXT NOT NULL, `type` TEXT NOT NULL, `relative_age_order` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`source_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_relationships_source_person_id_target_person_id_type` ON `relationships` (`source_person_id`, `target_person_id`, `type`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_relationships_target_person_id` ON `relationships` (`target_person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_relationships_type` ON `relationships` (`type`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `important_dates` (`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `type` TEXT NOT NULL, `year` INTEGER, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `calendar_type` TEXT NOT NULL, `is_leap_month` INTEGER NOT NULL DEFAULT 0, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_important_dates_person_id_type` ON `important_dates` (`person_id`, `type`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_important_dates_month_day` ON `important_dates` (`month`, `day`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `reminder_preferences` (`id` TEXT NOT NULL, `important_date_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`important_date_id`) REFERENCES `important_dates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_preferences_important_date_id` ON `reminder_preferences` (`important_date_id`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `reminder_offsets` (`id` TEXT NOT NULL, `reminder_preference_id` TEXT NOT NULL, `days_before` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`reminder_preference_id`) REFERENCES `reminder_preferences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_offsets_reminder_preference_id_days_before` ON `reminder_offsets` (`reminder_preference_id`, `days_before`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `custom_titles` (`id` TEXT NOT NULL, `observer_person_id` TEXT NOT NULL, `target_person_id` TEXT NOT NULL, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`observer_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_titles_observer_person_id_target_person_id` ON `custom_titles` (`observer_person_id`, `target_person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_custom_titles_target_person_id` ON `custom_titles` (`target_person_id`)""")
                        db.execSQL("""INSERT INTO persons VALUES ('me','我','MALE',NULL,NULL,'ALIVE',1,1000,1000)""")
                        db.execSQL("""INSERT INTO persons VALUES ('p1','爸爸','MALE',NULL,NULL,'ALIVE',0,1000,1000)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `family_tasks` (`id` TEXT NOT NULL, `person_id` TEXT, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `anchor_epoch_day` INTEGER NOT NULL, `repeat` TEXT NOT NULL, `period_index` INTEGER NOT NULL, `last_done_epoch_day` INTEGER, `done` INTEGER NOT NULL, `amount_fen` INTEGER, `note` TEXT, `enabled` INTEGER NOT NULL, `policy_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_family_tasks_person_id` ON `family_tasks` (`person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_family_tasks_policy_id` ON `family_tasks` (`policy_id`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `family_task_offsets` (`id` TEXT NOT NULL, `task_id` TEXT NOT NULL, `days_before` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`task_id`) REFERENCES `family_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_family_task_offsets_task_id_days_before` ON `family_task_offsets` (`task_id`, `days_before`)""")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                    ) {}
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    private fun createV5Database(context: Context, dbFile: File) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `persons` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `gender` TEXT NOT NULL, `avatar_path` TEXT, `note` TEXT, `living_status` TEXT NOT NULL, `is_self` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_persons_name` ON `persons` (`name`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_persons_is_self` ON `persons` (`is_self`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `phone_numbers` (`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `number` TEXT NOT NULL, `label` TEXT NOT NULL, `status` TEXT NOT NULL, `is_primary` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_phone_numbers_person_id_number` ON `phone_numbers` (`person_id`, `number`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_phone_numbers_number` ON `phone_numbers` (`number`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_phone_numbers_person_id_status` ON `phone_numbers` (`person_id`, `status`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `relationships` (`id` TEXT NOT NULL, `source_person_id` TEXT NOT NULL, `target_person_id` TEXT NOT NULL, `type` TEXT NOT NULL, `relative_age_order` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`source_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_relationships_source_person_id_target_person_id_type` ON `relationships` (`source_person_id`, `target_person_id`, `type`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_relationships_target_person_id` ON `relationships` (`target_person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_relationships_type` ON `relationships` (`type`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `important_dates` (`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `type` TEXT NOT NULL, `year` INTEGER, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `calendar_type` TEXT NOT NULL, `is_leap_month` INTEGER NOT NULL DEFAULT 0, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_important_dates_person_id_type` ON `important_dates` (`person_id`, `type`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_important_dates_month_day` ON `important_dates` (`month`, `day`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `reminder_preferences` (`id` TEXT NOT NULL, `important_date_id` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`important_date_id`) REFERENCES `important_dates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_preferences_important_date_id` ON `reminder_preferences` (`important_date_id`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `reminder_offsets` (`id` TEXT NOT NULL, `reminder_preference_id` TEXT NOT NULL, `days_before` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`reminder_preference_id`) REFERENCES `reminder_preferences`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_reminder_offsets_reminder_preference_id_days_before` ON `reminder_offsets` (`reminder_preference_id`, `days_before`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `custom_titles` (`id` TEXT NOT NULL, `observer_person_id` TEXT NOT NULL, `target_person_id` TEXT NOT NULL, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`observer_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_titles_observer_person_id_target_person_id` ON `custom_titles` (`observer_person_id`, `target_person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_custom_titles_target_person_id` ON `custom_titles` (`target_person_id`)""")
                        db.execSQL("""INSERT INTO persons VALUES ('me','我','MALE',NULL,NULL,'ALIVE',1,1000,1000)""")
                        db.execSQL("""INSERT INTO persons VALUES ('p1','爸爸','MALE',NULL,NULL,'ALIVE',0,1000,1000)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `family_tasks` (`id` TEXT NOT NULL, `person_id` TEXT, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `anchor_epoch_day` INTEGER NOT NULL, `repeat` TEXT NOT NULL, `period_index` INTEGER NOT NULL, `last_done_epoch_day` INTEGER, `done` INTEGER NOT NULL, `amount_fen` INTEGER, `note` TEXT, `enabled` INTEGER NOT NULL, `policy_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_family_tasks_person_id` ON `family_tasks` (`person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_family_tasks_policy_id` ON `family_tasks` (`policy_id`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `family_task_offsets` (`id` TEXT NOT NULL, `task_id` TEXT NOT NULL, `days_before` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`task_id`) REFERENCES `family_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
                        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_family_task_offsets_task_id_days_before` ON `family_task_offsets` (`task_id`, `days_before`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `gift_events` (`id` TEXT NOT NULL, `occasion` TEXT NOT NULL, `title` TEXT NOT NULL, `date_epoch_day` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_gift_events_date_epoch_day` ON `gift_events` (`date_epoch_day`)""")
                        db.execSQL("""CREATE TABLE IF NOT EXISTS `gift_records` (`id` TEXT NOT NULL, `event_id` TEXT, `direction` TEXT NOT NULL, `person_id` TEXT, `counterpart_name` TEXT NOT NULL, `amount_fen` INTEGER, `gift_desc` TEXT, `date_epoch_day` INTEGER NOT NULL, `occasion` TEXT NOT NULL, `note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`event_id`) REFERENCES `gift_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_gift_records_person_id` ON `gift_records` (`person_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_gift_records_event_id` ON `gift_records` (`event_id`)""")
                        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_gift_records_date_epoch_day` ON `gift_records` (`date_epoch_day`)""")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                    ) {}
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    private fun createV6Database(context: Context, dbFile: File) {
        createV5Database(context, dbFile)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                    ) {
                        MIGRATION_5_6.migrate(db)
                    }
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    @Test
    fun `v6到v7新增联系两表`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-67.db")
        dbFile.delete()
        createV6Database(context, dbFile)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // 计划 upsert + 日志级联
        db.contactPlanDao().insertAll(
            listOf(ContactPlanEntity("p1", 14, 1, 1))
        )
        assertEquals(14, db.contactPlanDao().get("p1")!!.intervalDays)
        db.contactLogDao().insertAll(
            listOf(
                ContactLogEntity(
                    id = "c1", personId = "p1", atMillis = 100,
                    method = "CALL", note = null, createdAt = 1
                )
            )
        )
        assertEquals(1, db.contactLogDao().getAll().size)
        db.personDao().deleteById("p1")
        assertNull(db.contactPlanDao().get("p1"))
        assertEquals(0, db.contactLogDao().getAll().size)
        db.close()
        dbFile.delete()
    }

    @Test
    fun `v1到v9完整迁移链`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-17.db")
        dbFile.delete()
        createV1Database(context, dbFile)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14
            )
            .allowMainThreadQueries()
            .build()

        // v1 塞入的生日仍在；contact 两表可写；v9 三表已建且为空
        assertEquals(1, db.personDao().getAll().size)
        assertEquals(1, db.importantDateDao().getAllDates().size)
        db.contactPlanDao().insertAll(
            listOf(ContactPlanEntity("p1", 7, 1, 1))
        )
        assertEquals(7, db.contactPlanDao().get("p1")!!.intervalDays)
        db.close()
        dbFile.delete()
    }

    /**
     * v7 库：在 v6 之上执行 MIGRATION_6_7，再写入旧式 'BIRTHDAY' 行
     *（p1 阳历 + p2 农历含提醒偏好/偏移），供 v7→v8 拆分迁移校验。
     */
    private fun createV7Database(context: Context, dbFile: File) {
        createV6Database(context, dbFile)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int
                    ) {
                        MIGRATION_6_7.migrate(db)
                        // 旧库只能一人一“生日”：阳历、农历各放在不同人名下
                        db.execSQL("""INSERT INTO important_dates VALUES
                            ('db1','p1','BIRTHDAY',1960,3,15,'SOLAR',0,NULL,1000,1000)""")
                        db.execSQL("""INSERT INTO reminder_preferences VALUES
                            ('rpb1','db1',1,1000,1000)""")
                        db.execSQL("""INSERT INTO reminder_offsets VALUES ('ob1','rpb1',7)""")
                        db.execSQL("""INSERT INTO persons VALUES
                            ('p2','奶奶','FEMALE',NULL,NULL,'ALIVE',0,1000,1000)""")
                        db.execSQL("""INSERT INTO important_dates VALUES
                            ('db2','p2','BIRTHDAY',NULL,8,15,'LUNAR',1,NULL,1000,1000)""")
                        db.execSQL("""INSERT INTO reminder_preferences VALUES
                            ('rpb2','db2',0,1000,1000)""")
                    }
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    @Test
    fun `v7到v8生日类型拆分且偏好保留`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-78.db")
        dbFile.delete()
        createV7Database(context, dbFile)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // 阳历行 → BIRTHDAY_SOLAR，行 id 与字段不变
        val solar = db.importantDateDao().getBirthdays("p1").single()
        assertEquals("db1", solar.id)
        assertEquals(ImportantDateType.BIRTHDAY_SOLAR, solar.type)
        assertEquals(CalendarType.SOLAR, solar.calendarType)
        assertEquals(1960, solar.year)
        // 偏好与偏移原样挂在同一行上
        assertTrue(db.reminderPreferenceDao().getForDate("db1")!!.enabled)
        assertEquals(
            setOf(7),
            db.reminderOffsetDao().getForPreference("rpb1").map { it.daysBefore }.toSet()
        )
        // 农历行 → BIRTHDAY_LUNAR，闰月标记保留
        val lunar = db.importantDateDao().getBirthdays("p2").single()
        assertEquals(ImportantDateType.BIRTHDAY_LUNAR, lunar.type)
        assertEquals(CalendarType.LUNAR, lunar.calendarType)
        assertTrue(lunar.isLeapMonth)
        assertNull(lunar.year)
        assertFalse(db.reminderPreferenceDao().getForDate("db2")!!.enabled)
        // 非生日行不受影响：库里没有遗留 'BIRTHDAY' 类型
        assertTrue(
            db.importantDateDao().getAllDates()
                .none { it.type !in setOf(
                    ImportantDateType.BIRTHDAY_SOLAR, ImportantDateType.BIRTHDAY_LUNAR,
                    ImportantDateType.DEATH_ANNIVERSARY,
                    ImportantDateType.WEDDING_ANNIVERSARY) }
        )
        db.close()
        dbFile.delete()
    }

    /** v8 库：v7 之上执行 MIGRATION_7_8（生日拆分），供 v8→v9 校验。 */
    private fun createV8Database(context: Context, dbFile: File) {
        createV7Database(context, dbFile)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int,
                        newVersion: Int
                    ) {
                        MIGRATION_7_8.migrate(db)
                    }
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    @Test
    fun `v8到v9新增配对三表且随人物级联`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-89.db")
        dbFile.delete()
        createV8Database(context, dbFile)

        // Room 以 v9 打开：执行 MIGRATION_8_9 并对全部表做 schema 校验
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // v8 数据原样保留
        assertEquals(3, db.personDao().getAll().size)
        db.close()
        dbFile.delete()
    }

    /** v9 库：v8 之上执行 MIGRATION_8_9（配对三表），供 v9→v10 校验。 */
    private fun createV9Database(context: Context, dbFile: File) {
        createV8Database(context, dbFile)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int,
                        newVersion: Int
                    ) {
                        MIGRATION_8_9.migrate(db)
                    }
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    @Test
    fun `v9到v10新增person_alias且随人物级联`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-910.db")
        dbFile.delete()
        createV9Database(context, dbFile)

        // Room 以 v10 打开：执行 MIGRATION_9_10 并对全部表做 schema 校验
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // v9 数据原样保留；person_alias 已建且为空
        assertEquals(3, db.personDao().getAll().size)
        assertTrue(db.personAliasDao().getAll().isEmpty())

        // 对应关系可写可读；删除本机人物 → 该对应行级联作废
        db.personAliasDao().upsertAll(
            listOf(
                PersonAliasEntity("r-a", "p1", 1),
                PersonAliasEntity("r-b", "p2", 1)
            )
        )
        assertEquals(
            mapOf("r-a" to "p1", "r-b" to "p2"),
            db.personAliasDao().getAll()
                .associate { it.remotePersonId to it.localPersonId })
        db.personDao().deleteById("p2")
        assertEquals(
            mapOf("r-a" to "p1"),
            db.personAliasDao().getAll()
                .associate { it.remotePersonId to it.localPersonId })
        db.close()
        dbFile.delete()
    }

    /** v10 库：v9 之上执行 MIGRATION_9_10（person_alias），供 v10→v11 校验。 */
    private fun createV10Database(context: Context, dbFile: File) {
        createV9Database(context, dbFile)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int,
                        newVersion: Int
                    ) {
                        MIGRATION_9_10.migrate(db)
                    }
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    @Test
    fun `v10到v11新增事项提醒时刻列`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-1011.db")
        dbFile.delete()
        createV10Database(context, dbFile)

        // Room 以 v11 打开：执行 MIGRATION_10_11 并对全部表做 schema 校验
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // 旧数据保留；remind_minutes 默认为 NULL（跟随家事提醒时刻）
        assertEquals(3, db.personDao().getAll().size)
        db.familyTaskDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = "t1", personId = "p1", kind = "CUSTOM",
                    title = "吃板面", anchorEpochDay = 20500,
                    repeat = "NONE", periodIndex = 0, lastDoneEpochDay = null,
                    done = false, amountFen = null, note = null, enabled = true,
                    remindMinutes = 12 * 60,
                    createdAt = 1, updatedAt = 1
                )
            )
        )
        assertEquals(12 * 60, db.familyTaskDao().get("t1")!!.remindMinutes)
        db.close()
        dbFile.delete()
    }

    /** v11 库：v10 之上执行 MIGRATION_10_11（事项提醒时刻列）。 */
    private fun createV11Database(context: Context, dbFile: File) {
        createV10Database(context, dbFile)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int,
                        newVersion: Int
                    ) {
                        MIGRATION_10_11.migrate(db)
                    }
                })
                .build()
        )
        helper.writableDatabase.close()
    }

    @Test
    fun `v11到v12新增协作四表`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-1112.db")
        dbFile.delete()
        createV11Database(context, dbFile)

        // Room 以 v13 打开：执行 MIGRATION_11_12 + 12_13 并校验全部表
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // 旧数据原样保留；协作四表已建且为空
        assertEquals(3, db.personDao().getAll().size)
        assertNull(db.familyCollabDao().get())
        assertTrue(db.familyPublicationDao().getAll().isEmpty())
        assertTrue(db.familyReceiveDraftDao().getAll().isEmpty())
        assertTrue(db.familyLocalOnlyDao().getAll().isEmpty())

        // 协作状态可写可读
        db.familyCollabDao().upsert(
            FamilyCollabStateEntity(
                id = 1, seriesId = "s1", role = "coordinator",
                coordinatorPersonId = "me", acceptedRevision = 1,
                acceptedHash = "h1", baseSnapshotJson = "{}",
                idMapJson = "{}", removedIdsJson = "[]", updatedAt = 1))
        assertEquals("s1", db.familyCollabDao().get()!!.seriesId)
        // 仅本机标记随人物删除级联
        db.familyLocalOnlyDao().upsert(
            FamilyLocalOnlyEntity("p1", "c1", "有保单", 1))
        assertEquals(1, db.familyLocalOnlyDao().getIds().size)
        db.personDao().deleteById("p1")
        assertTrue(db.familyLocalOnlyDao().getIds().isEmpty())
        // 草稿 PENDING → APPLIED
        db.familyReceiveDraftDao().upsert(
            FamilyReceiveDraftEntity(
                packageId = "pkg1", contentHash = "ch1", mode = "confirmed",
                seriesId = "s1", revision = 1, rosterHash = "rh",
                baseRevision = null, baseHash = null,
                decisionsJson = "{}", filePath = "/tmp/x",
                status = "PENDING", planCacheJson = null,
                createdAt = 1, updatedAt = 1))
        db.familyReceiveDraftDao().upsert(
            db.familyReceiveDraftDao().get("pkg1")!!.copy(status = "APPLIED"))
        assertEquals("APPLIED", db.familyReceiveDraftDao().get("pkg1")!!.status)
        db.close()
        dbFile.delete()
    }

    /** v12 库：v11 之上执行 MIGRATION_11_12（协作四表），含退出表的种子行。 */
    private fun createV12Database(context: Context, dbFile: File) {
        createV11Database(context, dbFile)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int,
                        newVersion: Int
                    ) {
                        MIGRATION_11_12.migrate(db)
                    }
                })
                .build()
        )
        // v12 里的退出数据：一条带 policy_id 的缴费事项 + 礼簿/记录/保单各一；
        // 另加一条普通事项（非空 remind_minutes）与两条事项的提前提醒偏移，
        // 覆盖“升级必须原样保留子表行”的回归（主审实测补充）。
        helper.writableDatabase.let { d ->
            d.execSQL(
                "INSERT INTO `family_tasks` (`id`,`person_id`,`kind`,`title`," +
                    "`anchor_epoch_day`,`repeat`,`period_index`," +
                    "`last_done_epoch_day`,`done`,`amount_fen`,`note`," +
                    "`enabled`,`remind_minutes`,`policy_id`,`created_at`," +
                    "`updated_at`) VALUES ('ptask','p1','INSURANCE_PREMIUM'," +
                    "'缴费',20000,'YEARLY',2,19900,0,68000,'备注',1,NULL," +
                    "'pol1',1,2)"
            )
            d.execSQL(
                "INSERT INTO `family_tasks` (`id`,`person_id`,`kind`,`title`," +
                    "`anchor_epoch_day`,`repeat`,`period_index`," +
                    "`last_done_epoch_day`,`done`,`amount_fen`,`note`," +
                    "`enabled`,`remind_minutes`,`policy_id`,`created_at`," +
                    "`updated_at`) VALUES ('ord1',NULL,'CUSTOM','宽带缴费'," +
                    "20100,'NONE',0,20090,1,9900,'n',0,720,NULL,3,4)"
            )
            d.execSQL(
                "INSERT INTO `family_task_offsets` VALUES " +
                    "('ptask-o15','ptask',15),('ptask-o3','ptask',3)," +
                    "('ptask-o0','ptask',0),('ord1-o7','ord1',7)," +
                    "('ord1-o0','ord1',0)"
            )
            d.execSQL(
                "INSERT INTO `gift_events` (`id`,`occasion`,`title`," +
                    "`date_epoch_day`,`note`,`created_at`,`updated_at`) " +
                    "VALUES ('ge1','BIRTH','满月',20000,NULL,1,1)"
            )
            d.execSQL(
                "INSERT INTO `gift_records` (`id`,`event_id`,`direction`," +
                    "`person_id`,`counterpart_name`,`amount_fen`,`gift_desc`," +
                    "`date_epoch_day`,`occasion`,`note`,`created_at`," +
                    "`updated_at`) VALUES ('gr1','ge1','RECEIVED','p1','妈'," +
                    "60000,NULL,20000,'BIRTH',NULL,1,1)"
            )
            d.execSQL(
                "INSERT INTO `insurance_policies` (`id`,`insured_person_id`," +
                    "`holder_person_id`,`beneficiary`,`insurer`," +
                    "`product_name`,`category`,`sum_insured_fen`," +
                    "`premium_fen`,`pay_frequency`,`pay_years`," +
                    "`start_epoch_day`,`end_epoch_day`,`lifelong`," +
                    "`policy_no`,`agent_name`,`agent_phone`,`insurer_phone`," +
                    "`note`,`created_at`,`updated_at`) VALUES ('pol1','p1'," +
                    "'p1',NULL,'平安','百万医疗','MEDICAL',100,200,'YEARLY'," +
                    "1,20000,NULL,0,'PA1',NULL,NULL,NULL,NULL,1,1)"
            )
        }
        helper.writableDatabase.close()
    }

    @Test
    fun v12到v13删退出表且事项字段保留() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-1213.db")
        dbFile.delete()
        createV12Database(context, dbFile)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // 三张退出表消失、policy_id 列消失；事项其余字段全部保留
        assertRetiredTablesGone(db)
        val t = db.familyTaskDao().get("ptask")!!
        assertEquals("p1", t.personId)
        assertEquals("INSURANCE_PREMIUM", t.kind)
        assertEquals("缴费", t.title)
        assertEquals(20000L, t.anchorEpochDay)
        assertEquals("YEARLY", t.repeat)
        assertEquals(2, t.periodIndex)
        assertEquals(19900L, t.lastDoneEpochDay)
        assertEquals(false, t.done)
        assertEquals(68000L, t.amountFen)
        assertEquals("备注", t.note)
        assertEquals(true, t.enabled)
        assertEquals(1L, t.createdAt)
        assertEquals(2L, t.updatedAt)
        // 普通事项同样保留（含非空 remind_minutes）
        val o = db.familyTaskDao().get("ord1")!!
        assertNull(o.personId)
        assertEquals("CUSTOM", o.kind)
        assertEquals("宽带缴费", o.title)
        assertEquals(20100L, o.anchorEpochDay)
        assertEquals("NONE", o.repeat)
        assertEquals(0, o.periodIndex)
        assertEquals(20090L, o.lastDoneEpochDay)
        assertEquals(true, o.done)
        assertEquals(9900L, o.amountFen)
        assertEquals("n", o.note)
        assertEquals(false, o.enabled)
        assertEquals(720, o.remindMinutes)
        assertEquals(3L, o.createdAt)
        assertEquals(4L, o.updatedAt)
        // 提前提醒偏移：两行事项的 5 条记录（id / task_id / days_before）逐项保留
        assertEquals(
            setOf("ptask-o15:ptask:15", "ptask-o3:ptask:3", "ptask-o0:ptask:0"),
            db.familyTaskOffsetDao().getForTask("ptask")
                .map { it.id + ":" + it.taskId + ":" + it.daysBefore }.toSet()
        )
        assertEquals(
            setOf("ord1-o7:ord1:7", "ord1-o0:ord1:0"),
            db.familyTaskOffsetDao().getForTask("ord1")
                .map { it.id + ":" + it.taskId + ":" + it.daysBefore }.toSet()
        )
        // 家人等其他资料不受影响；外键完整性（foreign_key_check 零违例）
        assertEquals(3, db.personDao().getAll().size)
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { c ->
            assertEquals(0, c.count)
        }
        // 升级后级联约束照常生效：删人物 → 其事项与偏移一并级联删除
        db.personDao().deleteById("p1")
        assertNull(db.familyTaskDao().get("ptask"))
        assertTrue(db.familyTaskOffsetDao().getForTask("ptask").isEmpty())
        assertEquals("ord1", db.familyTaskDao().get("ord1")!!.id)
        db.close()
        dbFile.delete()
    }

    /** v13 库：v12 之上执行 MIGRATION_12_13（含碰一碰三表），供 v13→v14 校验。 */
    private fun createV13Database(context: Context, dbFile: File) {
        createV12Database(context, dbFile)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {}
                    override fun onCreate(db: SupportSQLiteDatabase) {}
                    override fun onUpgrade(
                        db: SupportSQLiteDatabase, oldVersion: Int,
                        newVersion: Int
                    ) {
                        MIGRATION_12_13.migrate(db)
                    }
                })
                .build()
        )
        // v13 里的碰一碰数据：配对/映射/设备各一行 + 一名普通人物，
        // 验证升级只删这三张表、其余资料原样保留
        helper.writableDatabase.let { d ->
            d.execSQL(
                "INSERT INTO `persons` (`id`,`name`,`gender`,`avatar_path`,`note`," +
                    "`living_status`,`is_self`,`created_at`,`updated_at`) " +
                    "VALUES ('px','甲','MALE',NULL,NULL,'ALIVE',0,1,1)"
            )
            d.execSQL(
                "INSERT INTO `spouse_pairing` (`id`,`pairing_id`,`peer_self_id`," +
                    "`local_spouse_id`,`peer_name`,`last_session_id`,`last_payload_sha`," +
                    "`last_role_reader`,`updated_at`) VALUES (1,'ab12','r-self'," +
                    "'px','老伴',7,'ff',1,9)"
            )
            d.execSQL(
                "INSERT INTO `tap_person_map` (`remote_person_id`,`local_person_id`)" +
                    " VALUES ('r-self','px')"
            )
            d.execSQL(
                "INSERT INTO `tap_device` (`id`,`role_reader`) VALUES (1,1)"
            )
        }
        helper.writableDatabase.close()
    }

    @Test
    fun v13到v14删碰一碰表且其余资料保留() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.getDatabasePath("x").parentFile, "migration-1314.db")
        dbFile.delete()
        createV13Database(context, dbFile)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
            .addMigrations(MIGRATION_13_14)
            .allowMainThreadQueries()
            .build()

        // 三张碰一碰表消失：sqlite_master 里查不到
        val tables = mutableSetOf<String>()
        db.openHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        ).use { c -> while (c.moveToNext()) tables.add(c.getString(0)) }
        assertTrue("spouse_pairing" !in tables)
        assertTrue("tap_person_map" !in tables)
        assertTrue("tap_device" !in tables)
        // 其余资料原样保留：v13 里建的人物与事项都在
        assertEquals(4, db.personDao().getAll().size)
        assertNotNull(db.personDao().getAll().firstOrNull { it.id == "px" })
        assertEquals("ptask", db.familyTaskDao().get("ptask")!!.id)
        // 外键完整性零违例
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { c ->
            assertEquals(0, c.count)
        }
        db.close()
        dbFile.delete()
    }
}
