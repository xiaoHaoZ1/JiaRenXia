package io.github.xiaohaoz1.jiarenxia.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：important_dates 增加 is_leap_month（农历闰月标记）。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE important_dates ADD COLUMN is_leap_month INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/**
 * v2 → v3：新增 custom_titles（自定义称呼；DATABASE_SCHEMA 25.2）。
 * SQL 与 Room 导出的 3.json createSql/index 保持一致。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `custom_titles` (`id` TEXT NOT NULL, `observer_person_id` TEXT NOT NULL, `target_person_id` TEXT NOT NULL, `title` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`observer_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_titles_observer_person_id_target_person_id` ON `custom_titles` (`observer_person_id`, `target_person_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_custom_titles_target_person_id` ON `custom_titles` (`target_person_id`)"
        )
    }
}

/**
 * v3 → v4：新增 family_tasks / family_task_offsets（家庭事项，FAMILY_AFFAIRS 5.5）。
 * SQL 与 Room 导出的 4.json createSql/index 保持一致。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_tasks` (`id` TEXT NOT NULL, `person_id` TEXT, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `anchor_epoch_day` INTEGER NOT NULL, `repeat` TEXT NOT NULL, `period_index` INTEGER NOT NULL, `last_done_epoch_day` INTEGER, `done` INTEGER NOT NULL, `amount_fen` INTEGER, `note` TEXT, `enabled` INTEGER NOT NULL, `policy_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_family_tasks_person_id` ON `family_tasks` (`person_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_family_tasks_policy_id` ON `family_tasks` (`policy_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_task_offsets` (`id` TEXT NOT NULL, `task_id` TEXT NOT NULL, `days_before` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`task_id`) REFERENCES `family_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_family_task_offsets_task_id_days_before` ON `family_task_offsets` (`task_id`, `days_before`)"
        )
    }
}

/** v4 -> v5：新增人情往来两表（FAMILY_AFFAIRS 6），列名与 Room 导出的 5.json 一致。 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gift_events` (" +
                "`id` TEXT NOT NULL, `occasion` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`date_epoch_day` INTEGER NOT NULL, `note` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_gift_events_date_epoch_day` " +
                "ON `gift_events` (`date_epoch_day`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `gift_records` (" +
                "`id` TEXT NOT NULL, `event_id` TEXT, `direction` TEXT NOT NULL, " +
                "`person_id` TEXT, `counterpart_name` TEXT NOT NULL, `amount_fen` INTEGER, " +
                "`gift_desc` TEXT, `date_epoch_day` INTEGER NOT NULL, `occasion` TEXT NOT NULL, " +
                "`note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`event_id`) REFERENCES `gift_events`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_gift_records_person_id` " +
                "ON `gift_records` (`person_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_gift_records_event_id` " +
                "ON `gift_records` (`event_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_gift_records_date_epoch_day` " +
                "ON `gift_records` (`date_epoch_day`)"
        )
    }
}

/** v5 -> v6：新增 insurance_policies 表（FAMILY_AFFAIRS 7），列名与 6.json 一致。 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `insurance_policies` (" +
                "`id` TEXT NOT NULL, `insured_person_id` TEXT NOT NULL, " +
                "`holder_person_id` TEXT, `beneficiary` TEXT, `insurer` TEXT NOT NULL, " +
                "`product_name` TEXT, `category` TEXT NOT NULL, `sum_insured_fen` INTEGER, " +
                "`premium_fen` INTEGER, `pay_frequency` TEXT NOT NULL, `pay_years` INTEGER, " +
                "`start_epoch_day` INTEGER, `end_epoch_day` INTEGER, " +
                "`lifelong` INTEGER NOT NULL, `policy_no` TEXT, `agent_name` TEXT, " +
                "`agent_phone` TEXT, `insurer_phone` TEXT, `note` TEXT, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`insured_person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`holder_person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_insurance_policies_insured_person_id` " +
                "ON `insurance_policies` (`insured_person_id`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_insurance_policies_holder_person_id` " +
                "ON `insurance_policies` (`holder_person_id`)"
        )
    }
}

/**
 * v6 → v7：新增 contact_logs / contact_plans（联系提醒），列名与 7.json 一致。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `contact_logs` (" +
                "`id` TEXT NOT NULL, `person_id` TEXT NOT NULL, " +
                "`at_millis` INTEGER NOT NULL, `method` TEXT NOT NULL, " +
                "`note` TEXT, `created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_contact_logs_person_id_at_millis` " +
                "ON `contact_logs` (`person_id`, `at_millis`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `contact_plans` (" +
                "`person_id` TEXT NOT NULL, `interval_days` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`person_id`), " +
                "FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }
}

/**
 * v7 → v8：生日按历法拆类型——`BIRTHDAY` 改写为
 * `BIRTHDAY_SOLAR` / `BIRTHDAY_LUNAR`（按原 calendar_type）。
 * 表结构与索引不变：UNIQUE(person_id, type) 自然保证每人最多
 * 一条阳历 + 一条农历；忌日/结婚纪念日约束原样保留。
 * 行 id、提醒偏好与偏移全部原样保留（纯 UPDATE，非破坏性迁移）。
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE important_dates SET type = 'BIRTHDAY_LUNAR' " +
                "WHERE type = 'BIRTHDAY' AND calendar_type = 'LUNAR'"
        )
        db.execSQL(
            "UPDATE important_dates SET type = 'BIRTHDAY_SOLAR' " +
                "WHERE type = 'BIRTHDAY'"
        )
    }
}

/**
 * v8 → v9：碰一碰配对持久化进 Room（FAMILY_AFFAIRS 第 10 节）——
 * `spouse_pairing`（单行配对记录）、`tap_person_map`（来源人物→本机人物
 * 映射）、`tap_device`（设备级连接方式偏好，单行）。配对与映射的外键
 * 指向 persons 并级联删除：备份恢复、共享导入、清空数据都会整表清
 * persons，此时配对自动失效，恢复出来的资料不会被误当成已配对设备。
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `spouse_pairing` (" +
                "`id` INTEGER NOT NULL, `pairing_id` TEXT NOT NULL, " +
                "`peer_self_id` TEXT NOT NULL, " +
                "`local_spouse_id` TEXT NOT NULL, `peer_name` TEXT NOT NULL, " +
                "`last_session_id` INTEGER, `last_payload_sha` TEXT, " +
                "`last_role_reader` INTEGER, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`local_spouse_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_spouse_pairing_local_spouse_id` " +
                "ON `spouse_pairing` (`local_spouse_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tap_person_map` (" +
                "`remote_person_id` TEXT NOT NULL, " +
                "`local_person_id` TEXT NOT NULL, " +
                "PRIMARY KEY(`remote_person_id`), " +
                "FOREIGN KEY(`local_person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_tap_person_map_local_person_id` " +
                "ON `tap_person_map` (`local_person_id`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tap_device` (" +
                "`id` INTEGER NOT NULL, `role_reader` INTEGER, " +
                "PRIMARY KEY(`id`))"
        )
    }
}

/**
 * v9 → v10：家人共享合并的对应关系持久化（FAMILY_AFFAIRS 16.5）——
 * `person_alias`（对方人物 id → 本机人物 id）。本机人物删除时级联作废；
 * 只存本机，不进备份、不进共享文件。
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `person_alias` (" +
                "`remote_person_id` TEXT NOT NULL, " +
                "`local_person_id` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`remote_person_id`), " +
                "FOREIGN KEY(`local_person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_person_alias_local_person_id` " +
                "ON `person_alias` (`local_person_id`)"
        )
    }
}

/**
 * v10 → v11：family_tasks 加 `remind_minutes`——事项级一天内提醒时刻
 * （分钟数；NULL = 跟随家事提醒时刻设置），供「中午提醒我」这类按时刻提醒。
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `family_tasks` ADD COLUMN `remind_minutes` INTEGER")
    }
}

/**
 * v11 → v12：离线协作四表（FAMILY_AFFAIRS §5）——
 * family_collaboration_state（名册绑定单行）、family_publications（发布索引）、
 * family_receive_drafts（接收草稿/已处理记录）、family_local_only（仅本机保留）。
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_collaboration_state` (" +
                "`id` INTEGER NOT NULL, " +
                "`series_id` TEXT NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "`coordinator_person_id` TEXT NOT NULL, " +
                "`accepted_revision` INTEGER NOT NULL, " +
                "`accepted_hash` TEXT NOT NULL, " +
                "`base_snapshot_json` TEXT NOT NULL, " +
                "`id_map_json` TEXT NOT NULL, " +
                "`removed_ids_json` TEXT NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_publications` (" +
                "`revision` INTEGER NOT NULL, " +
                "`package_id` TEXT NOT NULL, " +
                "`snapshot_hash` TEXT NOT NULL, " +
                "`parent_revision` INTEGER NOT NULL, " +
                "`parent_hash` TEXT, " +
                "`created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`revision`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_receive_drafts` (" +
                "`package_id` TEXT NOT NULL, " +
                "`content_hash` TEXT NOT NULL, " +
                "`mode` TEXT NOT NULL, " +
                "`series_id` TEXT NOT NULL, " +
                "`revision` INTEGER NOT NULL, " +
                "`roster_hash` TEXT NOT NULL, " +
                "`base_revision` INTEGER, " +
                "`base_hash` TEXT, " +
                "`decisions_json` TEXT NOT NULL, " +
                "`file_path` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`plan_cache_json` TEXT, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`package_id`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_local_only` (" +
                "`person_id` TEXT NOT NULL, " +
                "`canonical_id` TEXT, " +
                "`note` TEXT, " +
                "`created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`person_id`), " +
                "FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }
}
/**
 * v12 → v13（A6 退出人情与保障）：
 * 删 gift_events / gift_records / insurance_policies 表；
 * family_tasks 重建去掉 policy_id 列与索引，其余字段（id、kind、
 * title、周期 anchor/repeat、期数 period_index、办理记录
 * last_done_epoch_day/done、金额、备注、提醒开关、提醒时刻、
 * 时间戳）与外键、person_id 索引原样保留。
 * family_task_offsets 以 task_id 外键挂 family_tasks：Room 执行
 * 迁移时外键检查处于关闭状态（foreign_keys=0），DROP 父表不会
 * 级联删除子行，offsets 原样保留；升级完成后外键约束照常生效。
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `family_tasks_new` (" +
                "`id` TEXT NOT NULL, " +
                "`person_id` TEXT, " +
                "`kind` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`anchor_epoch_day` INTEGER NOT NULL, " +
                "`repeat` TEXT NOT NULL, " +
                "`period_index` INTEGER NOT NULL, " +
                "`last_done_epoch_day` INTEGER, " +
                "`done` INTEGER NOT NULL, " +
                "`amount_fen` INTEGER, " +
                "`note` TEXT, " +
                "`enabled` INTEGER NOT NULL, " +
                "`remind_minutes` INTEGER, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT INTO `family_tasks_new` (`id`, `person_id`, `kind`, " +
                "`title`, `anchor_epoch_day`, `repeat`, `period_index`, " +
                "`last_done_epoch_day`, `done`, `amount_fen`, `note`, " +
                "`enabled`, `remind_minutes`, `created_at`, `updated_at`) " +
                "SELECT `id`, `person_id`, `kind`, `title`, " +
                "`anchor_epoch_day`, `repeat`, `period_index`, " +
                "`last_done_epoch_day`, `done`, `amount_fen`, `note`, " +
                "`enabled`, `remind_minutes`, `created_at`, `updated_at` " +
                "FROM `family_tasks`"
        )
        db.execSQL("DROP TABLE `family_tasks`")
        db.execSQL("ALTER TABLE `family_tasks_new` RENAME TO `family_tasks`")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_family_tasks_person_id` " +
                "ON `family_tasks` (`person_id`)"
        )
        db.execSQL("DROP TABLE `gift_records`")
        db.execSQL("DROP TABLE `gift_events`")
        db.execSQL("DROP TABLE `insurance_policies`")
    }
}

/**
 * v13 → v14（2026-10-21 碰一碰整体删除）：
 * 删 spouse_pairing / tap_person_map / tap_device 三张碰一碰专用表。
 * 两表的外键本都指向 persons，迁移期间 foreign_keys 关闭，直接 DROP 即可；
 * 其余表不受影响。
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE `spouse_pairing`")
        db.execSQL("DROP TABLE `tap_person_map`")
        db.execSQL("DROP TABLE `tap_device`")
    }
}
