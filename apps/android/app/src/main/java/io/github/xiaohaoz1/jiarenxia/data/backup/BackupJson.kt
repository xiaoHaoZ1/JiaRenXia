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
    /** 旧版离线激活码（schemaVersion 6，会员功能已下线）：不再写入，
     *  仅为兼容旧备份保留字段，恢复时忽略。 */
    val licenseCode: String? = null
)

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
            tasks = tasks,
            contactLogs = contactLogs,
            contactPlans = contactPlans,
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
