package io.github.xiaohaoz1.jiarenxia.data.backup

import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.db.CustomTitleEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderOffsetEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderPreferenceEntity
import io.github.xiaohaoz1.jiarenxia.data.settings.ThemeMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

/** 备份/恢复摘要（数量与元信息）。 */
data class BackupSummary(
    val createdAt: Long?,
    val appVersion: String?,
    val persons: Int,
    val relationships: Int,
    val birthdays: Int,
    val avatars: Int,
    /** 家庭事项条数（schemaVersion 4 新增）。 */
    val tasks: Int = 0,
    /** 纪念日和忌日合计（schemaVersion 7）。 */
    val anniversaries: Int = 0,
    /** 备份中的“我”的姓名（未设时为 null）。 */
    val selfName: String?
)

/** 备份内的设置（不含每日检查运行状态与通知权限标记）。 */
data class BackupSettings(
    val themeMode: ThemeMode,
    val remindersEnabled: Boolean,
    /** 应用内字体大小级别名（可选；缺失/未知 → 标准，不算校验错误）。 */
    val fontScale: String? = null,
    /** 旧版称谓叫法（该设置已移除）：仅为兼容旧备份保留字段，恢复时忽略。 */
    val titleStyle: String? = null,
    /** 生日与纪念日提醒时刻（一天内分钟数，可选；缺失/越界 → 保留当前设置）。 */
    val reminderMinuteOfDay: Int? = null,
    /** 家庭事项提醒时刻（schemaVersion 11；缺失/越界 → 保留当前设置）。 */
    val taskReminderMinuteOfDay: Int? = null,
    /** 联系提醒时刻（schemaVersion 11；缺失/越界 → 保留当前设置）。 */
    val contactReminderMinuteOfDay: Int? = null,
    /** 家庭事项提醒开关（schemaVersion 4；缺失 → 保留当前设置）。 */
    val taskRemindersEnabled: Boolean? = null,
    /** 上坟提醒开关（可选；缺失 → 保留当前设置）。 */
    val tombRemindersEnabled: Boolean? = null,
    /** 上坟提醒时刻（可选；缺失/越界 → 保留当前设置）。 */
    val tombReminderMinuteOfDay: Int? = null,
    /** 上坟日子（TombDay 枚举名集合；可选；缺失 → 保留当前设置）。 */
    val tombDays: Set<String>? = null,
    /** 中元节按农历十四/十五（可选；缺失/其他 → 保留当前设置）。 */
    val tombZhongyuanDay: Int? = null,
    /** “给谁上坟”手动关掉的人（可选；缺失 → 保留当前设置）。 */
    val tombExcludedPersonIds: Set<String>? = null,
    /** “给谁上坟”手动打开的人（可选；缺失 → 保留当前设置）。 */
    val tombIncludedPersonIds: Set<String>? = null,
    /** 回家看看·节前提醒开关（可选；缺失 → 保留当前设置）。 */
    val homeVisitHolidayNudge: Boolean? = null,
    /** 用药提醒开关（可选；缺失 → 保留当前设置）。 */
    val medRemindersEnabled: Boolean? = null,
    /** 用药提醒时刻（可选；缺失/越界 → 保留当前设置）。 */
    val medReminderMinuteOfDay: Int? = null,
    /** 「按时吃提醒」子开关（可选；缺失 → 保留当前设置）。 */
    val medOnTimeEnabled: Boolean? = null,
    /** 旧版离线激活码（schemaVersion 6，会员功能已下线）：不再写入，
     *  仅为兼容旧备份保留字段，恢复时忽略。 */
    val licenseCode: String? = null
)

/** 备份内的一条回家看看计划（成员 id 列表随计划走）。 */
data class HomeVisitBackup(
    val id: String,
    val intervalDays: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val memberIds: List<String>
)

/**
 * 孕期 ↔ 宝宝关联行（备份内 babyIds 的载体；恢复时展开为
 * pregnancy_babies 行）。本批无写入入口，仅随备份往返。
 */
typealias PregnancyBabyLink = io.github.xiaohaoz1.jiarenxia.data.db
    .PregnancyBabyEntity

/** 解密并校验后的恢复计划：可直接 apply。 */
data class RestorePlan(
    val summary: BackupSummary,
    val persons: List<PersonEntity>,
    val phones: List<PhoneNumberEntity>,
    val relationships: List<RelationshipEntity>,
    val importantDates: List<ImportantDateEntity>,
    /** prefs 与其偏移（写入时 prefs 在前、offsets 各自展开）。 */
    val prefs: List<Pair<ReminderPreferenceEntity, List<ReminderOffsetEntity>>>,
    /** 自定义称呼（schemaVersion 3 新增；2 导入时为空表）。 */
    val customTitles: List<CustomTitleEntity> = emptyList(),
    /** 家庭事项与其提前天数（schemaVersion 4 新增；低版本导入时为空表）。 */
    val tasks: List<Pair<io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity,
            List<io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity>>> = emptyList(),
    // A6（2026-10-20）：人情/保单退出，新备份不再写 giftEvents/giftRecords/
    // policies；读旧备份时这些字段被忽略，不再解析恢复。
    /** 联系记录与联系周期（schemaVersion 8；低版本导入时为空表）。 */
    val contactLogs: List<io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity> = emptyList(),
    val contactPlans: List<io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity> = emptyList(),
    /** 「关于TA」（可选键，不升 schemaVersion；旧备份缺失为空）。 */
    val personFacts: List<io.github.xiaohaoz1.jiarenxia.data.db.PersonFactEntity> = emptyList(),
    /** 回家看看计划（可选键，不升 schemaVersion；旧备份缺失为空）。 */
    val homeVisits: List<HomeVisitBackup> = emptyList(),
    /**
     * 孕期记录（可选键，不升 schemaVersion；旧备份缺失为空）。
     * 每条 = 孕期实体 + 宝宝关联行（babyIds 已过滤为备份内人物）。
     */
    val pregnancies: List<Pair<io.github.xiaohaoz1.jiarenxia.data.db
        .PregnancyEntity, List<PregnancyBabyLink>>> = emptyList(),
    /**
     * 清单（可选键，不升 schemaVersion；旧备份缺失为空）。
     * 每条 = 清单实体 + 其条目。
     */
    val checklists: List<Pair<io.github.xiaohaoz1.jiarenxia.data.db
        .ChecklistEntity, List<io.github.xiaohaoz1.jiarenxia.data.db
        .ChecklistItemEntity>>> = emptyList(),
    /**
     * 宝宝满月/百天另设日期（可选键，不升 schemaVersion；旧备份缺失为空）。
     * kind 不认识的跳过；personId 不在备份人物中的丢弃。
     */
    val babyCelebrations: List<io.github.xiaohaoz1.jiarenxia.data.db
        .BabyCelebrationEntity> = emptyList(),
    /**
     * 用药记录（可选键，不升 schemaVersion；旧备份缺失为空）。
     * 每条 = 用药实体 + 其服用时刻。
     */
    val medications: List<Pair<io.github.xiaohaoz1.jiarenxia.data.db
        .MedicationEntity, List<io.github.xiaohaoz1.jiarenxia.data.db
        .MedicationTimeEntity>>> = emptyList(),
    /** 用药按人开关（可选键；personId 不在备份人物中的丢弃）。 */
    val medicationPrefs: List<io.github.xiaohaoz1.jiarenxia.data.db
        .MedicationPersonPrefsEntity> = emptyList(),
    /**
     * 「打电话要问的事」（可选键，不升 schemaVersion；旧备份缺失为空）。
     * personId 不在备份人物中的丢弃；文字越界 fail。
     */
    val callQuestions: List<io.github.xiaohaoz1.jiarenxia.data.db
        .CallQuestionEntity> = emptyList(),
    val settings: BackupSettings?,
    /** 头像文件名 → 字节。 */
    val avatars: Map<String, ByteArray>,
    /** 文件用途："backup"（默认，旧文件没有该字段）或 "share"（家人共享）。 */
    val purpose: String = "backup",
    /** 家人共享：分享者“我”的 personId。 */
    val sharedBy: String? = null,
    /**
     * 碰一碰资料包人物来源标注：personId → 该机当初收到此人物时的来源 id
     * （仅碰一碰资料包携带）。对方据此识别“这个人本来就是从你这儿来的”，
     * 回环资料不会产生重复人物，也不需再次确认候选。
     */
    val personOrigins: Map<String, String> = emptyMap(),
)

/**
 * 备份负载：ZIP[backup.json + avatars 目录下头像]（java.util.zip + org.json，纯 JVM）。
 * 解析侧先做全部校验，不触碰任何持久数据；失败一律抛 [BackupCrypto.BackupFailure]。
 */
object BackupJson {

    const val FORMAT = "jiarenxia-backup"
    const val FORMAT_VERSION = 1
    // v9：生日类型拆分为 BIRTHDAY_SOLAR / BIRTHDAY_LUNAR（双生日）
    // v10：重要日期新增 LICENSE_ANNIVERSARY / DATING_ANNIVERSARY（领证/恋爱纪念日）
    // v11：提醒时间拆分为生日 / 事项 / 联系各自独立（新增两个时刻字段）
    // v12：曾为协作元数据段（家庭协作已下线）；仍接受该版本文件
    //   按普通分享/备份解析，collaboration/collabState 字段读取时忽略。
    const val SCHEMA_VERSION = 11
    const val COLLAB_SCHEMA_VERSION = 12
    /** 解析上限：比本地写出版本高的文件拒绝（更高版本的旧 App 兼容策略）。 */
    const val MAX_SUPPORTED_VERSION = COLLAB_SCHEMA_VERSION
    private const val MAX_UNCOMPRESSED = 200L * 1024 * 1024
    private val AVATAR_NAME = Regex("[A-Za-z0-9_.-]+")

    // ---------- 写出 ----------

    fun writeZip(
        persons: List<PersonEntity>,
        phones: List<PhoneNumberEntity>,
        relationships: List<RelationshipEntity>,
        dates: List<ImportantDateEntity>,
        prefs: List<Pair<ReminderPreferenceEntity, List<ReminderOffsetEntity>>>,
        customTitles: List<CustomTitleEntity>,
        tasks: List<Pair<io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity,
                List<io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity>>> = emptyList(),
        contactLogs: List<io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity> = emptyList(),
        contactPlans: List<io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity> = emptyList(),
        personFacts: List<io.github.xiaohaoz1.jiarenxia.data.db.PersonFactEntity> = emptyList(),
        homeVisits: List<HomeVisitBackup> = emptyList(),
        pregnancies: List<Pair<io.github.xiaohaoz1.jiarenxia.data.db
            .PregnancyEntity, List<PregnancyBabyLink>>> = emptyList(),
        checklists: List<Pair<io.github.xiaohaoz1.jiarenxia.data.db
            .ChecklistEntity, List<io.github.xiaohaoz1.jiarenxia.data.db
            .ChecklistItemEntity>>> = emptyList(),
        /** 宝宝满月/百天另设日期（可选键；分享/碰一碰不传）。 */
        babyCelebrations: List<io.github.xiaohaoz1.jiarenxia.data.db
            .BabyCelebrationEntity> = emptyList(),
        /** 用药记录（可选键；分享/碰一碰不传）。 */
        medications: List<Pair<io.github.xiaohaoz1.jiarenxia.data.db
            .MedicationEntity, List<io.github.xiaohaoz1.jiarenxia.data.db
            .MedicationTimeEntity>>> = emptyList(),
        /** 用药按人开关（可选键；分享/碰一碰不传）。 */
        medicationPrefs: List<io.github.xiaohaoz1.jiarenxia.data.db
            .MedicationPersonPrefsEntity> = emptyList(),
        /** 「打电话要问的事」（可选键；分享/碰一碰不传）。 */
        callQuestions: List<io.github.xiaohaoz1.jiarenxia.data.db
            .CallQuestionEntity> = emptyList(),
        settings: BackupSettings?,
        avatars: Map<String, ByteArray>,
        createdAt: Long,
        appVersion: String,
        /** "backup"（默认）或 "share"（家人共享文件）。 */
        purpose: String = "backup",
        /** 家人共享：分享者“我”的 personId。 */
        sharedBy: String? = null,
        /** 人物来源标注（仅碰一碰资料包）：本机人物 id → 当初收到它时的来源 id。 */
        personOrigins: Map<String, String> = emptyMap(),
    ): ByteArray {
        val schemaVersion = SCHEMA_VERSION
        val json = JSONObject()
        json.put("format", FORMAT)
        json.put("formatVersion", FORMAT_VERSION)
        json.put("schemaVersion", schemaVersion)
        json.put("createdAt", createdAt)
        json.put("appVersion", appVersion)
        json.put("purpose", purpose)
        sharedBy?.let { json.put("sharedBy", it) }
        json.put("persons", JSONArray().apply {
            persons.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("gender", p.gender.name)
                    personOrigins[p.id]?.let { put("oid", it) }
                    p.avatarPath?.let { put("avatarFile", it.removePrefix("avatars/")) }
                    p.note?.let { put("note", it) }
                    put("livingStatus", p.livingStatus.name)
                    p.siblingRank?.let { put("siblingRank", it) }
                    put("isSelf", p.isSelf)
                    put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
                })
            }
        })
        json.put("phones", JSONArray().apply {
            phones.forEach { ph ->
                put(JSONObject().apply {
                    put("id", ph.id); put("personId", ph.personId); put("number", ph.number)
                    put("label", ph.label.name); put("status", ph.status.name)
                    put("isPrimary", ph.isPrimary)
                    ph.note?.let { put("note", it) }
                    put("createdAt", ph.createdAt); put("updatedAt", ph.updatedAt)
                })
            }
        })
        json.put("relationships", JSONArray().apply {
            relationships.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("sourcePersonId", r.sourcePersonId)
                    put("targetPersonId", r.targetPersonId); put("type", r.type.name)
                    r.relativeAgeOrder?.let { put("relativeAgeOrder", it.name) }
                    put("createdAt", r.createdAt); put("updatedAt", r.updatedAt)
                })
            }
        })
        json.put("importantDates", JSONArray().apply {
            dates.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("personId", d.personId); put("type", d.type.name)
                    d.year?.let { put("year", it) }
                    put("month", d.month); put("day", d.day)
                    put("calendarType", d.calendarType.name)
                    put("isLeapMonth", d.isLeapMonth)
                    d.note?.let { put("note", it) }
                    put("createdAt", d.createdAt); put("updatedAt", d.updatedAt)
                })
            }
        })
        json.put("reminderPreferences", JSONArray().apply {
            prefs.forEach { (pref, offsets) ->
                put(JSONObject().apply {
                    put("id", pref.id); put("importantDateId", pref.importantDateId)
                    put("enabled", pref.enabled)
                    put("offsets", JSONArray().apply { offsets.forEach { put(it.daysBefore) } })
                    put("createdAt", pref.createdAt); put("updatedAt", pref.updatedAt)
                })
            }
        })
        json.put("customTitles", JSONArray().apply {
            customTitles.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("observerPersonId", c.observerPersonId)
                    put("targetPersonId", c.targetPersonId); put("title", c.title)
                    put("createdAt", c.createdAt); put("updatedAt", c.updatedAt)
                })
            }
        })
        json.put("familyTasks", JSONArray().apply {
            tasks.forEach { (task, offsets) ->
                put(JSONObject().apply {
                    put("id", task.id); put("personId", task.personId ?: JSONObject.NULL)
                    put("kind", task.kind); put("title", task.title)
                    put("anchorEpochDay", task.anchorEpochDay)
                    put("repeat", task.repeat); put("periodIndex", task.periodIndex)
                    task.lastDoneEpochDay?.let { put("lastDoneEpochDay", it) }
                    put("done", task.done)
                    task.amountFen?.let { put("amountFen", it) }
                    task.note?.let { put("note", it) }
                    put("enabled", task.enabled)
                    task.remindMinutes?.let { put("remindMinutes", it) }
                    task.pregnancyId?.let { put("pregnancyId", it) }
                    if (task.archived) put("archived", true)
                    put("offsets", JSONArray().apply {
                        offsets.sortedBy { it.daysBefore }.forEach { put(it.daysBefore) }
                    })
                    put("createdAt", task.createdAt); put("updatedAt", task.updatedAt)
                })
            }
        })
        json.put("contactLogs", JSONArray().apply {
            contactLogs.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("personId", e.personId)
                    put("atMillis", e.atMillis); put("method", e.method)
                    e.note?.let { put("note", it) }
                    put("createdAt", e.createdAt)
                })
            }
        })
        json.put("contactPlans", JSONArray().apply {
            contactPlans.forEach { e ->
                put(JSONObject().apply {
                    put("personId", e.personId)
                    put("intervalDays", e.intervalDays)
                    put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                })
            }
        })
        json.put("personFacts", JSONArray().apply {
            personFacts.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("personId", e.personId)
                    put("category", e.category)
                    e.label?.let { put("label", it) }
                    put("content", e.content)
                    put("recordedEpochDay", e.recordedEpochDay)
                    put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                })
            }
        })
        json.put("homeVisits", JSONArray().apply {
            homeVisits.forEach { h ->
                put(JSONObject().apply {
                    put("id", h.id)
                    put("intervalDays", h.intervalDays)
                    put("createdAt", h.createdAt); put("updatedAt", h.updatedAt)
                    put("memberIds", JSONArray().apply {
                        h.memberIds.forEach { put(it) }
                    })
                })
            }
        })
        // 孕期记录（可选键）：babyIds 只写真正关联的宝宝人物
        json.put("pregnancies", JSONArray().apply {
            pregnancies.forEach { (preg, babies) ->
                put(JSONObject().apply {
                    put("id", preg.id); put("personId", preg.personId)
                    put("dueEpochDay", preg.dueEpochDay)
                    preg.lmpEpochDay?.let { put("lmpEpochDay", it) }
                    put("status", preg.status)
                    preg.endedEpochDay?.let { put("endedEpochDay", it) }
                    put("createdAt", preg.createdAt); put("updatedAt", preg.updatedAt)
                    // 出生时刻（第 3 批）：非空才写 {babyPersonId: 分钟}
                    val minutes = babies.mapNotNull { b ->
                        b.birthMinute?.let { b.babyPersonId to it }
                    }.toMap()
                    if (minutes.isNotEmpty()) {
                        put("birthMinutes", JSONObject().apply {
                            minutes.forEach { (pid, m) -> put(pid, m) }
                        })
                    }
                    put("babyIds", JSONArray().apply {
                        babies.forEach { put(it.babyPersonId) }
                    })
                })
            }
        })
        // 清单（可选键）：非空才写——分享/碰一碰文件不携带此键
        if (checklists.isNotEmpty()) {
            json.put("checklists", JSONArray().apply {
                checklists.forEach { (cl, items) ->
                    put(JSONObject().apply {
                        put("id", cl.id); put("kind", cl.kind)
                        put("title", cl.title)
                        cl.personId?.let { put("personId", it) }
                        cl.pregnancyId?.let { put("pregnancyId", it) }
                        put("createdAt", cl.createdAt); put("updatedAt", cl.updatedAt)
                        put("items", JSONArray().apply {
                            items.forEach { it ->
                                put(JSONObject().apply {
                                    put("id", it.id)
                                    put("groupName", it.groupName)
                                    put("title", it.title)
                                    it.note?.let { n -> put("note", n) }
                                    put("checked", it.checked)
                                    put("sortOrder", it.sortOrder)
                                    it.personId?.let { p -> put("personId", p) }
                                    it.taskId?.let { t -> put("taskId", t) }
                                    put("createdAt", it.createdAt)
                                    put("updatedAt", it.updatedAt)
                                })
                            }
                        })
                    })
                }
            })
        }
        // 宝宝满月/百天另设日期（可选键）：非空才写——分享/碰一碰文件不携带
        if (babyCelebrations.isNotEmpty()) {
            json.put("babyCelebrations", JSONArray().apply {
                babyCelebrations.forEach { c ->
                    put(JSONObject().apply {
                        put("personId", c.personId); put("kind", c.kind)
                        put("epochDay", c.epochDay)
                    })
                }
            })
        }
        // 用药记录（可选键）：非空才写——分享/碰一碰文件不携带
        if (medications.isNotEmpty()) {
            json.put("medications", JSONArray().apply {
                medications.forEach { (m, times) ->
                    put(JSONObject().apply {
                        put("id", m.id); put("personId", m.personId)
                        put("name", m.name)
                        m.dose?.let { put("dose", it) }
                        m.spec?.let { put("spec", it) }
                        m.meal?.let { put("meal", it) }
                        put("usage", m.usage)
                        m.courseDays?.let { put("courseDays", it) }
                        put("startEpochDay", m.startEpochDay)
                        m.purpose?.let { put("purpose", it) }
                        m.note?.let { put("note", it) }
                        m.refillEpochDay?.let { put("refillEpochDay", it) }
                        m.stoppedEpochDay?.let { put("stoppedEpochDay", it) }
                        put("times", JSONArray().apply {
                            times.map { it.minuteOfDay }.sorted().forEach { put(it) }
                        })
                        put("createdAt", m.createdAt); put("updatedAt", m.updatedAt)
                    })
                }
            })
        }
        if (medicationPrefs.isNotEmpty()) {
            json.put("medicationPrefs", JSONArray().apply {
                medicationPrefs.forEach { p ->
                    put(JSONObject().apply {
                        put("personId", p.personId)
                        put("courseRefillEnabled", p.courseRefillEnabled)
                        p.onTimeEnabled?.let { put("onTimeEnabled", it) }
                    })
                }
            })
        }
        // 「打电话要问的事」（可选键）：非空才写——分享/碰一碰文件不携带
        if (callQuestions.isNotEmpty()) {
            json.put("callQuestions", JSONArray().apply {
                callQuestions.forEach { q ->
                    put(JSONObject().apply {
                        put("id", q.id); put("personId", q.personId)
                        put("text", q.text); put("keep", q.keep)
                        put("createdAt", q.createdAt); put("updatedAt", q.updatedAt)
                    })
                }
            })
        }
        settings?.let {
            json.put("settings", JSONObject()
                .put("themeMode", it.themeMode.name)
                .put("remindersEnabled", it.remindersEnabled)
                .put("fontScale", it.fontScale ?: JSONObject.NULL)
                .put("titleStyle", it.titleStyle ?: JSONObject.NULL)
                .put("reminderMinuteOfDay", it.reminderMinuteOfDay ?: JSONObject.NULL)
                .put("taskReminderMinuteOfDay", it.taskReminderMinuteOfDay ?: JSONObject.NULL)
                .put("contactReminderMinuteOfDay", it.contactReminderMinuteOfDay ?: JSONObject.NULL)
                .put("taskRemindersEnabled", it.taskRemindersEnabled ?: JSONObject.NULL)
                .put("tombRemindersEnabled", it.tombRemindersEnabled ?: JSONObject.NULL)
                .put("tombReminderMinuteOfDay",
                    it.tombReminderMinuteOfDay ?: JSONObject.NULL)
                .put("tombDays", it.tombDays?.let { days ->
                    JSONArray().apply { days.sorted().forEach { put(it) } }
                } ?: JSONObject.NULL)
                .put("tombZhongyuanDay", it.tombZhongyuanDay ?: JSONObject.NULL)
                .put("tombExcludedPersonIds", it.tombExcludedPersonIds?.let { ids ->
                    JSONArray().apply { ids.sorted().forEach { put(it) } }
                } ?: JSONObject.NULL)
                .put("tombIncludedPersonIds", it.tombIncludedPersonIds?.let { ids ->
                    JSONArray().apply { ids.sorted().forEach { put(it) } }
                } ?: JSONObject.NULL)
                .put("homeVisitHolidayNudge", it.homeVisitHolidayNudge ?: JSONObject.NULL)
                .put("medRemindersEnabled", it.medRemindersEnabled ?: JSONObject.NULL)
                .put("medReminderMinuteOfDay",
                    it.medReminderMinuteOfDay ?: JSONObject.NULL)
                .put("medOnTimeEnabled", it.medOnTimeEnabled ?: JSONObject.NULL)
                .put("licenseCode", it.licenseCode ?: JSONObject.NULL))
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("backup.json"))
            z.write(json.toString().toByteArray(Charsets.UTF_8))
            z.closeEntry()
            avatars.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry("avatars/$name"))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    // ---------- 解析 ----------

    /** 解压 ZIP：防护路径穿越与 zip-bomb，忽略多余条目。 */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val name = e.name
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\"))
                    throw BackupCrypto.BackupFailure(
                        BackupError.InvalidContent("bad entry name: $name"))
                if (!e.isDirectory) {
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var n = z.read(chunk)
                    while (n >= 0) {
                        total += n
                        if (total > MAX_UNCOMPRESSED)
                            throw BackupCrypto.BackupFailure(
                                BackupError.InvalidContent("zip too large"))
                        buf.write(chunk, 0, n)
                        n = z.read(chunk)
                    }
                    files[name] = buf.toByteArray()
                }
                e = z.nextEntry
            }
        }
        return files
    }

    /** 解密后的字节 → 校验后的 RestorePlan。 */
    fun parsePlan(zipBytes: ByteArray): RestorePlan {
        val files = try {
            unzip(zipBytes)
        } catch (e: BackupCrypto.BackupFailure) {
            throw e
        } catch (e: Exception) {
            throw BackupCrypto.BackupFailure(BackupError.NotABackup)
        }
        val jsonBytes = files["backup.json"]
            ?: throw BackupCrypto.BackupFailure(BackupError.NotABackup)

        val root = try {
            JSONObject(String(jsonBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw BackupCrypto.BackupFailure(BackupError.InvalidContent("bad json"))
        }
        fun fail(reason: String): Nothing =
            throw BackupCrypto.BackupFailure(BackupError.InvalidContent(reason))

        if (root.optString("format") != FORMAT) fail("format")
        if (root.optInt("schemaVersion", SCHEMA_VERSION) > MAX_SUPPORTED_VERSION)
            throw BackupCrypto.BackupFailure(BackupError.UnsupportedVersion)

        // ---------- 枚举解析辅助 ----------
        fun <T : Enum<T>> enumOf(
            cls: Class<T>, v: String?, what: String
        ): T = try {
            java.lang.Enum.valueOf(cls, v ?: "")
        } catch (e: Exception) {
            fail("enum $what")
        }

        // oid 是碰一碰资料包专有的来源标记；备份/共享文件中的 oid 一律忽略，
        // 防止伪造 oid 绕过“同一人候选”确认而直接并入已有人物
        val isTap = root.optString("purpose") == "tap"
        val personOrigins = LinkedHashMap<String, String>()
        val persons = root.optJSONArray("persons")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                if (isTap) {
                    o.optString("oid", "").takeIf { it.isNotEmpty() }
                        ?.let { personOrigins[o.getString("id")] = it }
                }
                PersonEntity(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    gender = enumOf(Gender::class.java, o.optString("gender", null), "gender"),
                    avatarPath = o.optString("avatarFile", "").takeIf { it.isNotEmpty() }
                        ?.let { "avatars/$it" },
                    note = if (o.has("note")) o.getString("note") else null,
                    livingStatus = enumOf(
                        LivingStatus::class.java,
                        o.optString("livingStatus", null), "livingStatus"
                    ),
                    isSelf = o.optBoolean("isSelf", false),
                    // 旧备份无该键；非法值一律视为未记录
                    siblingRank = o.optInt("siblingRank", 0)
                        .takeIf { it >= 1 },
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        } ?: emptyList()

        val phones = root.optJSONArray("phones")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PhoneNumberEntity(
                    id = o.getString("id"),
                    personId = o.getString("personId"),
                    number = o.getString("number"),
                    label = enumOf(PhoneLabel::class.java, o.optString("label", null), "label"),
                    status = enumOf(
                        PhoneStatus::class.java, o.optString("status", null), "status"
                    ),
                    isPrimary = o.optBoolean("isPrimary", false),
                    note = if (o.has("note")) o.getString("note") else null,
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        } ?: emptyList()

        val relationships = root.optJSONArray("relationships")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RelationshipEntity(
                    id = o.getString("id"),
                    sourcePersonId = o.getString("sourcePersonId"),
                    targetPersonId = o.getString("targetPersonId"),
                    type = enumOf(
                        RelationshipType::class.java, o.optString("type", null), "type"
                    ),
                    relativeAgeOrder = if (o.has("relativeAgeOrder"))
                        enumOf(RelativeAgeOrder::class.java,
                            o.getString("relativeAgeOrder"), "relativeAgeOrder")
                    else null,
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        } ?: emptyList()

        val dates = root.optJSONArray("importantDates")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val cal = enumOf(
                    CalendarType::class.java, o.optString("calendarType", null), "calendarType"
                )
                val rawType = o.optString("type", null)
                // 旧版（schemaVersion ≤8）生日统一写作 "BIRTHDAY"：按历法归入新槽位
                val type = if (rawType == "BIRTHDAY") {
                    ImportantDateType.birthdayOf(cal)
                } else {
                    enumOf(ImportantDateType::class.java, rawType, "type")
                }
                // 生日槽位与历法必须一致（防止 BIRTHDAY_SOLAR + LUNAR 这类错配）
                type.birthdayCalendar?.let { if (it != cal) fail("birthday type/calendar mismatch") }
                ImportantDateEntity(
                    id = o.getString("id"),
                    personId = o.getString("personId"),
                    type = type,
                    year = if (o.has("year")) o.getInt("year") else null,
                    month = o.getInt("month"),
                    day = o.getInt("day"),
                    calendarType = cal,
                    isLeapMonth = o.optBoolean("isLeapMonth", false),
                    note = if (o.has("note")) o.getString("note") else null,
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        } ?: emptyList()

        val prefs = root.optJSONArray("reminderPreferences")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val offsets = o.optJSONArray("offsets")?.let { off ->
                    (0 until off.length()).map { j ->
                        val d = off.getInt(j)
                        if (d !in 0..30) fail("offset out of range")
                        ReminderOffsetEntity(
                            id = o.getString("id") + "-off-" + j,
                            reminderPreferenceId = o.getString("id"),
                            daysBefore = d
                        )
                    }
                } ?: emptyList()
                ReminderPreferenceEntity(
                    id = o.getString("id"),
                    importantDateId = o.getString("importantDateId"),
                    enabled = o.optBoolean("enabled", true),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                ) to offsets
            }
        } ?: emptyList()

        // 自定义称呼（schemaVersion 3；低版本或缺失时为空）
        val customTitles = root.optJSONArray("customTitles")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CustomTitleEntity(
                    id = o.getString("id"),
                    observerPersonId = o.getString("observerPersonId"),
                    targetPersonId = o.getString("targetPersonId"),
                    title = o.getString("title"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        } ?: emptyList()

        // 家庭事项（schemaVersion 4；低版本或缺失时为空）
        val tasks = root.optJSONArray("familyTasks")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val offsets = o.optJSONArray("offsets")?.let { off ->
                    (0 until off.length()).map { j ->
                        val d = off.getInt(j)
                        if (d !in io.github.xiaohaoz1.jiarenxia.core.model.TASK_OFFSETS)
                            fail("task offset out of range")
                        io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                            id = o.getString("id") + "-off-" + j,
                            taskId = o.getString("id"),
                            daysBefore = d
                        )
                    }
                } ?: fail("task offsets required")
                if (offsets.isEmpty()) fail("task offsets empty")
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = o.getString("id"),
                    // optString 对 JSONObject.NULL 返回 "null" 字符串，必须用 isNull 判空
                    personId = if (o.isNull("personId")) null else o.getString("personId"),
                    kind = enumOf(
                        io.github.xiaohaoz1.jiarenxia.core.model.TaskKind::class.java,
                        o.optString("kind", null), "task kind"
                    ).name,
                    title = o.getString("title"),
                    anchorEpochDay = o.getLong("anchorEpochDay"),
                    repeat = enumOf(
                        io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat::class.java,
                        o.optString("repeat", null), "task repeat"
                    ).name,
                    periodIndex = o.optInt("periodIndex", 0).also {
                        if (it < 0 || it > 10000) fail("periodIndex range")
                    },
                    lastDoneEpochDay = if (o.has("lastDoneEpochDay"))
                        o.getLong("lastDoneEpochDay") else null,
                    done = o.optBoolean("done", false),
                    amountFen = if (o.has("amountFen"))
                        o.getLong("amountFen").also {
                            if (it < 0 || it > 99_999_999_999L) fail("amountFen range")
                        }
                    else null,
                    note = if (o.has("note")) o.getString("note") else null,
                    enabled = o.optBoolean("enabled", true),
                    remindMinutes = if (o.has("remindMinutes"))
                        o.getInt("remindMinutes").also {
                            if (it !in 0..1439) fail("task remindMinutes range")
                        }
                    else null,
                    // 可选键：缺失按 null/false；pregnancyId 悬空引用在校验段置 null
                    pregnancyId = if (o.isNull("pregnancyId")) null
                        else o.optString("pregnancyId", null),
                    archived = o.optBoolean("archived", false),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                ) to offsets
            }
        } ?: emptyList()

        // ---------- 校验 ----------
        fun uniqueIds(ids: List<String>, what: String) {
            if (ids.toSet().size != ids.size) fail("duplicate id in $what")
        }
        uniqueIds(persons.map { it.id }, "persons")
        uniqueIds(phones.map { it.id }, "phones")
        uniqueIds(relationships.map { it.id }, "relationships")
        uniqueIds(dates.map { it.id }, "importantDates")
        uniqueIds(prefs.map { it.first.id }, "reminderPreferences")

        val personIds = persons.map { it.id }.toSet()
        val dateIds = dates.map { it.id }.toSet()
        phones.forEach { if (it.personId !in personIds) fail("phone->person") }
        relationships.forEach {
            if (it.sourcePersonId !in personIds || it.targetPersonId !in personIds)
                fail("relationship->person")
        }
        dates.forEach { if (it.personId !in personIds) fail("date->person") }
        // (personId, type) 与数据库唯一索引一致：每人每类日期最多一条
        if (dates.map { it.personId to it.type }.toSet().size != dates.size)
            fail("duplicate date slot")
        prefs.forEach { if (it.first.importantDateId !in dateIds) fail("pref->date") }
        if (persons.count { it.isSelf } > 1) fail("multiple self")
        // 自定义称呼：引用可解析、(observer,target) 唯一、称谓 trim 后 1–12 字符
        uniqueIds(customTitles.map { it.id }, "customTitles")
        if (customTitles.map { it.observerPersonId to it.targetPersonId }.toSet().size
            != customTitles.size) fail("duplicate custom title pair")
        customTitles.forEach {
            if (it.observerPersonId !in personIds || it.targetPersonId !in personIds)
                fail("customTitle->person")
            if (it.title.trim().length !in 1..12) fail("customTitle length")
        }
        // 家庭事项：id 唯一、关联人物存在、标题非空
        uniqueIds(tasks.map { it.first.id }, "familyTasks")
        tasks.forEach { (task, offsets) ->
            task.personId?.let { if (it !in personIds) fail("task->person") }
            if (task.title.trim().isEmpty() || task.title.length > 30) fail("task title")
            if (offsets.map { it.taskId }.toSet() != setOf(task.id)) fail("task offset link")
        }
        // 联系记录与计划（v8）：personId 可解析、方法枚举、备注 ≤100 字
        val contactLogs = root.optJSONArray("contactLogs")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity(
                    id = o.getString("id"),
                    personId = o.getString("personId"),
                    atMillis = o.getLong("atMillis"),
                    method = enumOf(
                        io.github.xiaohaoz1.jiarenxia.core.model
                            .ContactMethod::class.java,
                        o.optString("method", null), "contact method"
                    ).name,
                    note = if (o.has("note")) o.getString("note") else null,
                    createdAt = o.getLong("createdAt")
                )
            }
        } ?: emptyList()
        val contactPlans = root.optJSONArray("contactPlans")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity(
                    personId = o.getString("personId"),
                    intervalDays = o.getInt("intervalDays"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        } ?: emptyList()
        uniqueIds(contactLogs.map { it.id }, "contactLogs")
        uniqueIds(contactPlans.map { it.personId }, "contactPlans")
        contactLogs.forEach {
            if (it.personId !in personIds) fail("contactLog->person")
            if (it.note != null && it.note.length > 100) fail("contact note")
            if (it.atMillis < 0) fail("contact atMillis")
        }
        contactPlans.forEach {
            if (it.personId !in personIds) fail("contactPlan->person")
            if (it.intervalDays !in 1..366) fail("contact interval")
        }
        val personFacts = root.optJSONArray("personFacts")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                io.github.xiaohaoz1.jiarenxia.data.db.PersonFactEntity(
                    id = o.getString("id"),
                    personId = o.getString("personId"),
                    category = o.getString("category"),
                    label = if (o.has("label")) o.getString("label") else null,
                    content = o.getString("content"),
                    recordedEpochDay = o.getLong("recordedEpochDay"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        } ?: emptyList()
        uniqueIds(personFacts.map { it.id }, "personFacts")
        personFacts.forEach {
            if (it.personId !in personIds) fail("personFact->person")
            if (it.category.isEmpty() || it.category.length > 32) fail("fact category")
            if (it.content.trim().isEmpty() || it.content.length > 100) fail("fact content")
            if (it.label != null && it.label.length > 20) fail("fact label")
            if (it.recordedEpochDay < 0) fail("fact day")
        }
        // 回家看看（可选键，不升 schemaVersion）：成员 id 不在此校验，
        // 恢复时丢弃指向不存在人物的成员（规格口径）
        val homeVisits = root.optJSONArray("homeVisits")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HomeVisitBackup(
                    id = o.getString("id"),
                    intervalDays = o.getInt("intervalDays"),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt"),
                    memberIds = o.optJSONArray("memberIds")?.let { m ->
                        (0 until m.length()).map { m.getString(it) }
                    } ?: emptyList()
                )
            }
        } ?: emptyList()
        uniqueIds(homeVisits.map { it.id }, "homeVisits")
        homeVisits.forEach {
            if (it.intervalDays !in 1..366) fail("homeVisit interval")
            if (it.createdAt < 0 || it.updatedAt < 0) fail("homeVisit time")
        }

        // 孕期记录（可选键，不升 schemaVersion）：id 唯一、人物存在、
        // status 合法枚举（与 task kind 一致，非法即 fail）；
        // babyIds 指向不存在人物的丢弃（不 fail）；
        // birthMinutes 可选 {babyPersonId: 0..1439}，非法范围即 fail。
        val pregnancies = root.optJSONArray("pregnancies")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val pregId = o.getString("id")
                val birthMinutes = o.optJSONObject("birthMinutes")?.let { bm ->
                    bm.keys().asSequence().associateWith { pid ->
                        val m = bm.getInt(pid)
                        if (m !in 0..1439) fail("birthMinutes range")
                        m
                    }
                } ?: emptyMap()
                val babyIds = o.optJSONArray("babyIds")?.let { b ->
                    (0 until b.length()).map { b.getString(it) }
                }?.distinct() ?: emptyList()
                io.github.xiaohaoz1.jiarenxia.data.db.PregnancyEntity(
                    id = pregId,
                    personId = o.getString("personId"),
                    dueEpochDay = o.getLong("dueEpochDay"),
                    lmpEpochDay = if (o.has("lmpEpochDay"))
                        o.getLong("lmpEpochDay") else null,
                    status = enumOf(
                        io.github.xiaohaoz1.jiarenxia.core.model
                            .PregnancyStatus::class.java,
                        o.optString("status", null), "pregnancy status"
                    ).name,
                    endedEpochDay = if (o.has("endedEpochDay"))
                        o.getLong("endedEpochDay") else null,
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                ) to babyIds.filter { it in personIds }
                    .map {
                        PregnancyBabyLink(pregId, it, birthMinutes[it])
                    }
            }
        } ?: emptyList()
        uniqueIds(pregnancies.map { it.first.id }, "pregnancies")
        pregnancies.forEach { (preg, _) ->
            if (preg.personId !in personIds) fail("pregnancy->person")
        }
        // 事项的 pregnancyId 指向不存在的孕期时置 null（不 fail）
        val pregnancyIds = pregnancies.map { it.first.id }.toSet()
        val tasksFixed = tasks.map { (task, offsets) ->
            if (task.pregnancyId != null && task.pregnancyId !in pregnancyIds) {
                task.copy(pregnancyId = null) to offsets
            } else task to offsets
        }

        // 清单（可选键，不升 schemaVersion）：kind 不认识的整张跳过
        // （连同条目）——旧版本恢复新备份时不丢整份；
        // personId/pregnancyId（含条目 personId）悬空引用置 null（不 fail）；
        // taskId 指向不存在事项的置 null；
        // 标题 trim 后 1–30 字，超长/为空 fail。
        val taskIds = tasks.mapTo(HashSet()) { it.first.id }
        val checklistsRaw = root.optJSONArray("checklists")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val clId = o.getString("id")
                // kind 不认识（含缺失/空）→ 跳过整张清单
                val kindName = runCatching {
                    io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind
                        .valueOf(o.optString("kind", ""))
                }.getOrNull()?.name ?: return@mapNotNull null
                val items = o.optJSONArray("items")?.let { it2 ->
                    (0 until it2.length()).map { j ->
                        val io2 = it2.getJSONObject(j)
                        io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity(
                            id = io2.getString("id"),
                            checklistId = clId,
                            groupName = io2.getString("groupName"),
                            title = io2.getString("title"),
                            note = if (io2.has("note")) io2.getString("note") else null,
                            checked = io2.optBoolean("checked", false),
                            sortOrder = io2.optInt("sortOrder", 0),
                            personId = if (io2.isNull("personId")) null
                                else io2.optString("personId", null),
                            taskId = if (io2.isNull("taskId")) null
                                else io2.optString("taskId", null),
                            createdAt = io2.getLong("createdAt"),
                            updatedAt = io2.getLong("updatedAt")
                        )
                    }
                } ?: emptyList()
                io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity(
                    id = clId,
                    kind = kindName,
                    title = o.getString("title"),
                    personId = if (o.isNull("personId")) null
                        else o.optString("personId", null),
                    pregnancyId = if (o.isNull("pregnancyId")) null
                        else o.optString("pregnancyId", null),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                ) to items
            }
        } ?: emptyList()
        uniqueIds(checklistsRaw.map { it.first.id }, "checklists")
        uniqueIds(checklistsRaw.flatMap { it.second }.map { it.id }, "checklistItems")
        val checklists = checklistsRaw.map { (cl, items) ->
            if (cl.title.trim().isEmpty() || cl.title.length > 30) fail("checklist title")
            items.forEach {
                if (it.title.trim().isEmpty() || it.title.length > 30)
                    fail("checklist item title")
            }
            cl.copy(
                personId = cl.personId?.takeIf { it in personIds },
                pregnancyId = cl.pregnancyId?.takeIf { it in pregnancyIds }
            ) to items.map {
                it.copy(
                    personId = it.personId?.takeIf { p -> p in personIds },
                    taskId = it.taskId?.takeIf { t -> t in taskIds }
                )
            }
        }

        // 宝宝满月/百天另设日期（可选键，不升 schemaVersion）：
        // kind 不认识的跳过；personId 不在备份人物中的丢弃（不 fail）。
        val babyCelebrations = root.optJSONArray("babyCelebrations")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val kind = runCatching {
                    io.github.xiaohaoz1.jiarenxia.core.model
                        .BabyCelebrationKind.valueOf(o.optString("kind", ""))
                }.getOrNull() ?: return@mapNotNull null
                val pid = o.getString("personId")
                if (pid !in personIds) return@mapNotNull null
                io.github.xiaohaoz1.jiarenxia.data.db.BabyCelebrationEntity(
                    personId = pid, kind = kind.name,
                    epochDay = o.getLong("epochDay")
                )
            }
        } ?: emptyList()

        // 用药记录（可选键，不升 schemaVersion）：
        // usage / meal 不认识的跳过该条（连同时刻）；personId 不在备份
        // 人物中的丢弃（不 fail）；文本长度越界 fail；
        // 时刻去重排序、至多 4 个、0..1439；usage 与字段不匹配的按
        // 写入口径归一化（非 SHORT 清 course_days、非 LONG 清 refill、
        // AS_NEEDED 清时刻）。
        val medications = root.optJSONArray("medications")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val usage = runCatching {
                    io.github.xiaohaoz1.jiarenxia.core.model.MedUsage
                        .valueOf(o.optString("usage", ""))
                }.getOrNull() ?: return@mapNotNull null
                val meal = if (o.isNull("meal") || !o.has("meal")) null
                else runCatching {
                    io.github.xiaohaoz1.jiarenxia.core.model.MedMeal
                        .valueOf(o.getString("meal"))
                }.getOrNull() ?: return@mapNotNull null
                val pid = o.getString("personId")
                if (pid !in personIds) return@mapNotNull null
                val name = o.getString("name")
                if (name.trim().isEmpty() || name.length > 30) fail("med name")
                val dose = if (o.has("dose")) o.getString("dose") else null
                if (dose != null && dose.length > 20) fail("med dose")
                val spec = if (o.has("spec")) o.getString("spec") else null
                if (spec != null && spec.length > 20) fail("med spec")
                val purpose = if (o.has("purpose")) o.getString("purpose") else null
                if (purpose != null && purpose.length > 30) fail("med purpose")
                val note = if (o.has("note")) o.getString("note") else null
                if (note != null && note.length > 200) fail("med note")
                val courseDays = if (o.has("courseDays")) o.getInt("courseDays") else null
                if (courseDays != null && courseDays !in 1..365) fail("med courseDays")
                val times = o.optJSONArray("times")?.let { t ->
                    (0 until t.length()).map { t.getInt(it) }
                }?.distinct()?.sorted() ?: emptyList()
                if (times.size > 4 || times.any { it !in 0..1439 }) fail("med times")
                val refill = if (o.has("refillEpochDay"))
                    o.getLong("refillEpochDay") else null
                val stopped = if (o.has("stoppedEpochDay"))
                    o.getLong("stoppedEpochDay") else null
                io.github.xiaohaoz1.jiarenxia.data.db.MedicationEntity(
                    id = o.getString("id"),
                    personId = pid,
                    name = name,
                    dose = dose, spec = spec, meal = meal?.name,
                    usage = usage.name,
                    courseDays = if (usage ==
                        io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.SHORT_COURSE)
                        courseDays else null,
                    startEpochDay = o.getLong("startEpochDay"),
                    purpose = purpose, note = note,
                    refillEpochDay = if (usage ==
                        io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.LONG_TERM)
                        refill else null,
                    stoppedEpochDay = stopped,
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                ) to if (usage ==
                    io.github.xiaohaoz1.jiarenxia.core.model.MedUsage.AS_NEEDED)
                    emptyList()
                else times.map {
                    io.github.xiaohaoz1.jiarenxia.data.db.MedicationTimeEntity(
                        medicationId = "", minuteOfDay = it)
                }
            }
        }?.map { (m, times) ->
            m to times.map { it.copy(medicationId = m.id) }
        } ?: emptyList()
        uniqueIds(medications.map { it.first.id }, "medications")

        // 用药按人开关（可选键）：personId 不在备份人物中的丢弃；
        // on_time_enabled 缺失为 null（第 5 批默认口径）。
        val medicationPrefs = root.optJSONArray("medicationPrefs")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val pid = o.getString("personId")
                if (pid !in personIds) return@mapNotNull null
                io.github.xiaohaoz1.jiarenxia.data.db.MedicationPersonPrefsEntity(
                    personId = pid,
                    courseRefillEnabled = o.optBoolean("courseRefillEnabled", true),
                    onTimeEnabled = if (o.isNull("onTimeEnabled") ||
                        !o.has("onTimeEnabled")) null
                    else o.getBoolean("onTimeEnabled")
                )
            }
        } ?: emptyList()

        // 「打电话要问的事」（可选键，不升 schemaVersion）：id 唯一、
        // personId 不在备份人物中的丢弃（不 fail）、文字越界 fail、
        // 每人至多 10 条（超出即 fail，与仓库口径一致）。
        val callQuestions = root.optJSONArray("callQuestions")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val pid = o.getString("personId")
                if (pid !in personIds) return@mapNotNull null
                val text = o.getString("text")
                if (text.trim().isEmpty() || text.length >
                    io.github.xiaohaoz1.jiarenxia.data.CallQuestionRepository
                        .MAX_TEXT) fail("callQuestion text")
                io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity(
                    id = o.getString("id"),
                    personId = pid,
                    text = text,
                    keep = o.optBoolean("keep", false),
                    createdAt = o.getLong("createdAt"),
                    updatedAt = o.getLong("updatedAt")
                )
            }
        } ?: emptyList()
        uniqueIds(callQuestions.map { it.id }, "callQuestions")
        callQuestions.groupBy { it.personId }.forEach { (_, list) ->
            if (list.size > io.github.xiaohaoz1.jiarenxia.data
                    .CallQuestionRepository.MAX_PER_PERSON) fail("callQuestion limit")
        }

        // 头像文件名校验 + 收集
        val avatarFiles = persons.mapNotNull { p ->
            p.avatarPath?.removePrefix("avatars/")
        }
        avatarFiles.forEach {
            if (!AVATAR_NAME.matches(it) || it == "." || it == "..") fail("avatar name")
        }
        val avatars = avatarFiles.mapNotNull { name ->
            files["avatars/$name"]?.let { name to it }
        }.toMap()

        val settings = root.optJSONObject("settings")?.let { o ->
            BackupSettings(
                themeMode = enumOf(
                    ThemeMode::class.java, o.optString("themeMode", null), "themeMode"
                ),
                remindersEnabled = o.optBoolean("remindersEnabled", true),
                fontScale = o.optString("fontScale").takeIf { it.isNotBlank() },
                titleStyle = o.optString("titleStyle").takeIf { it.isNotBlank() },
                reminderMinuteOfDay = if (o.isNull("reminderMinuteOfDay")) null
                else o.optInt("reminderMinuteOfDay", -1).takeIf { it in 0..1439 },
                taskReminderMinuteOfDay = if (o.isNull("taskReminderMinuteOfDay")) null
                else o.optInt("taskReminderMinuteOfDay", -1).takeIf { it in 0..1439 },
                contactReminderMinuteOfDay = if (o.isNull("contactReminderMinuteOfDay")) null
                else o.optInt("contactReminderMinuteOfDay", -1).takeIf { it in 0..1439 },
                taskRemindersEnabled = if (o.isNull("taskRemindersEnabled")) null
                else o.optBoolean("taskRemindersEnabled", true),
                // 上坟提醒（可选键，不升 schemaVersion）：缺失/非法一律保留当前设置
                tombRemindersEnabled = if (o.isNull("tombRemindersEnabled")) null
                else o.optBoolean("tombRemindersEnabled", true),
                tombReminderMinuteOfDay = if (o.isNull("tombReminderMinuteOfDay")) null
                else o.optInt("tombReminderMinuteOfDay", -1).takeIf { it in 0..1439 },
                tombDays = if (o.isNull("tombDays")) null
                else o.optJSONArray("tombDays")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                },
                tombZhongyuanDay = if (o.isNull("tombZhongyuanDay")) null
                else o.optInt("tombZhongyuanDay", -1).takeIf { it == 14 || it == 15 },
                tombExcludedPersonIds = if (o.isNull("tombExcludedPersonIds")) null
                else o.optJSONArray("tombExcludedPersonIds")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                },
                tombIncludedPersonIds = if (o.isNull("tombIncludedPersonIds")) null
                else o.optJSONArray("tombIncludedPersonIds")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }.toSet()
                },
                homeVisitHolidayNudge = if (o.isNull("homeVisitHolidayNudge")) null
                else o.optBoolean("homeVisitHolidayNudge", true),
                // 用药提醒（可选键，不升 schemaVersion）：缺失/非法保留当前设置
                medRemindersEnabled = if (o.isNull("medRemindersEnabled")) null
                else o.optBoolean("medRemindersEnabled", true),
                medReminderMinuteOfDay = if (o.isNull("medReminderMinuteOfDay")) null
                else o.optInt("medReminderMinuteOfDay", -1).takeIf { it in 0..1439 },
                medOnTimeEnabled = if (o.isNull("medOnTimeEnabled")) null
                else o.optBoolean("medOnTimeEnabled", true),
                // A6 后忽略：旧备份里的 giftModuleEnabled / insuranceModuleEnabled
                licenseCode = if (o.isNull("licenseCode")) null
                else o.optString("licenseCode", "").ifEmpty { null }
            )
        }

        val summary = BackupSummary(
            createdAt = if (root.has("createdAt")) root.getLong("createdAt") else null,
            appVersion = root.optString("appVersion", null)?.takeIf { it.isNotEmpty() },
            persons = persons.size,
            relationships = relationships.size,
            birthdays = dates.count { it.type.isBirthday },
            anniversaries = dates.count { !it.type.isBirthday },
            avatars = avatars.size,
            tasks = tasks.size,
            selfName = persons.firstOrNull { it.isSelf }?.name
        )

        return RestorePlan(
            summary = summary,
            persons = persons,
            phones = phones,
            relationships = relationships,
            importantDates = dates,
            prefs = prefs,
            customTitles = customTitles,
            tasks = tasksFixed,
            contactLogs = contactLogs,
            contactPlans = contactPlans,
            personFacts = personFacts,
            homeVisits = homeVisits,
            pregnancies = pregnancies,
            checklists = checklists,
            babyCelebrations = babyCelebrations,
            medications = medications,
            medicationPrefs = medicationPrefs,
            callQuestions = callQuestions,
            settings = settings,
            avatars = avatars,
            purpose = root.optString("purpose", "backup"),
            sharedBy = if (root.isNull("sharedBy")) null
                else root.optString("sharedBy", null),
            personOrigins = personOrigins
        )
    }

    /** ISO 日期字符串 → epochDay；非法一律按内容损坏处理。 */
    private fun parseIsoDay(iso: String, what: String): Long =
        try {
            java.time.LocalDate.parse(iso).toEpochDay()
        } catch (e: Exception) {
            throw BackupCrypto.BackupFailure(
                BackupError.InvalidContent(what))
        }
}
