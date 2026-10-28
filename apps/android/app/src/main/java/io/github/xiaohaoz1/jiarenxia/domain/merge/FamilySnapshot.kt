package io.github.xiaohaoz1.jiarenxia.domain.merge

import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.data.normalizePhone
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * 规范化共享名册快照（FAMILY_AFFAIRS 协作 §5.1，纯 JVM）。
 *
 * 快照在「名册规范 id」空间表达：同一人物在两台设备上的本机 id 可以不同，
 * 经协作状态里保存的 规范 id↔本机 id 映射换算后进入同一空间再做比较。
 *
 * 覆盖的共享字段：人物姓名/性别/在世状态/备注/头像内容哈希、
 * 电话（号码/标签/状态/主号码/备注）、关系、重要日期（含历法/闰月/未知年份/备注）。
 * 明确排除：isSelf、自定义称谓、提醒偏好、createdAt/updatedAt、本机头像路径、
 * 联系与家事（事项/人情/保单）资料。
 *
 * 条目对应规则：
 * - 电话条目按（规范人物 id，normalizePhone 后的号码）对应——库里
 *   UNIQUE(person_id, number) 保证同人同号只有一条；换号=删旧+加新两条差异，
 *   绝不猜“不同号码是同一条的更正”。
 * - 日期条目按（规范人物 id，类型）对应——UNIQUE(person_id, type) 保证唯一槽位。
 * - 夫妻共享纪念日（结婚/领证/恋爱）归一挂到规范 id 较小的一方，避免两台
 *   设备各挂一方造成的假差异。
 * - 关系规范化方向：PARENT 保持 source=父母、target=子女；
 *   SPOUSE/SIBLING/COUSIN 端点按 id 排序，relativeAgeOrder 相应翻转，
 *   始终表示“a 相对 b 的长幼”。
 */
object FamilySnapshot {

    /** 规范化电话条目。number 为 normalizePhone 后的号码。 */
    data class Phone(
        val number: String,
        val label: String,
        val status: String,
        val primary: Boolean,
        val note: String?
    )

    /** 规范化日期条目（year=null 表示只知月日）。 */
    data class DateEntry(
        val type: String,
        val year: Int?,
        val month: Int,
        val day: Int,
        val calendar: String,
        val leap: Boolean,
        val note: String?
    )

    /** 规范人物：id 为名册规范 id。phones/dates 已排序。 */
    data class Person(
        val id: String,
        val name: String,
        val gender: String,
        val living: String,
        val note: String?,
        /** 头像内容 sha256 十六进制；无头像为 null。 */
        val avatarHash: String?,
        /** 兄弟姐妹排行（1=老大），未记录为 null。 */
        val siblingRank: Int?,
        val phones: List<Phone>,
        val dates: List<DateEntry>
    )

    /** 规范化关系：PARENT 有向（a=父母端），其余无序 a<b，order 表示 a 相对 b。 */
    data class Rel(
        val type: String,
        val a: String,
        val b: String,
        val order: String?
    ) {
        /** 端点无序对（约束检查用）。 */
        val pair: Pair<String, String> get() = if (a < b) a to b else b to a
    }

    data class Snap(
        /** 按 id 排序的人物列表。 */
        val persons: List<Person>,
        /** 排序后的关系列表。 */
        val rels: List<Rel>
    ) {
        val personMap: Map<String, Person> get() = persons.associateBy { it.id }
    }

    // ---------- 构建 ----------

    /**
     * 从本机库表构建快照。[persons]/[phones]/[rels]/[dates] 的 id 已是规范 id
     * （调用方先做本机→规范映射）；[avatarHashOf] 返回人物头像内容哈希
     * （无头像返回 null）；[excludeIds] 内的人物（如“仅本机保留”）整体排除。
     */
    fun fromEntities(
        persons: List<PersonEntity>,
        phones: List<PhoneNumberEntity>,
        rels: List<RelationshipEntity>,
        dates: List<ImportantDateEntity>,
        avatarHashOf: (PersonEntity) -> String?,
        excludeIds: Set<String> = emptySet()
    ): Snap {
        val keep = persons.filter { it.id !in excludeIds }
        val keepIds = keep.mapTo(HashSet()) { it.id }
        // 夫妻共享日期归一：挂到规范 id 较小的一方
        val spouseOf = HashMap<String, String>()
        rels.forEach { r ->
            if (r.type == RelationshipType.SPOUSE &&
                r.sourcePersonId in keepIds && r.targetPersonId in keepIds
            ) {
                spouseOf[r.sourcePersonId] = r.targetPersonId
                spouseOf[r.targetPersonId] = r.sourcePersonId
            }
        }
        val datesByPerson = HashMap<String, MutableList<ImportantDateEntity>>()
        dates.forEach { d ->
            if (d.personId !in keepIds) return@forEach
            val owner = if (d.type.isCoupleShared) {
                minOf(d.personId, spouseOf[d.personId] ?: d.personId)
            } else d.personId
            datesByPerson.getOrPut(owner) { ArrayList() } += d
        }
        val phonesByPerson = phones.filter { it.personId in keepIds }
            .groupBy { it.personId }
        val snapPersons = keep.map { p ->
            Person(
                id = p.id,
                name = p.name.trim(),
                gender = p.gender.name,
                living = p.livingStatus.name,
                note = p.note?.trim()?.ifBlank { null },
                avatarHash = avatarHashOf(p),
                siblingRank = p.siblingRank,
                phones = phonesByPerson[p.id].orEmpty()
                    // 同一规范化号码只留一条（库 UNIQUE 按原始串，极端脏数据防御）
                    .groupBy { normalizePhone(it.number) }
                    .map { (_, same) -> same.sortedBy { it.id }.first() }
                    .map { ph ->
                        Phone(
                            number = normalizePhone(ph.number),
                            label = ph.label.name,
                            status = ph.status.name,
                            primary = ph.isPrimary,
                            note = ph.note?.trim()?.ifBlank { null }
                        )
                    }
                    .sortedBy { it.number },
                dates = datesByPerson[p.id].orEmpty().map { d ->
                    DateEntry(
                        type = d.type.name,
                        year = d.year,
                        month = d.month,
                        day = d.day,
                        calendar = d.calendarType.name,
                        leap = d.isLeapMonth,
                        note = d.note?.trim()?.ifBlank { null }
                    )
                }.sortedBy { it.type }
            )
        }.sortedBy { it.id }
        val snapRels = rels.mapNotNull { r ->
            if (r.sourcePersonId !in keepIds || r.targetPersonId !in keepIds) {
                return@mapNotNull null
            }
            normalizeRel(r)
        }.distinct().sortedWith(compareBy({ it.type }, { it.a }, { it.b }))
        return Snap(snapPersons, snapRels)
    }

    /** 关系规范化：对称类型端点按 id 排序、长幼方向随交换翻转。 */
    fun normalizeRel(r: RelationshipEntity): Rel = when (r.type) {
        RelationshipType.PARENT, RelationshipType.CHILD ->
            Rel(r.type.name, r.sourcePersonId, r.targetPersonId, null)
        else -> {
            if (r.sourcePersonId <= r.targetPersonId) {
                Rel(r.type.name, r.sourcePersonId, r.targetPersonId,
                    r.relativeAgeOrder?.name)
            } else {
                Rel(r.type.name, r.targetPersonId, r.sourcePersonId,
                    r.relativeAgeOrder?.inverse()?.name)
            }
        }
    }

    /** 规范 id 重映射：贡献包发送方本机 id → 规范 id；未映射的 id 原样保留。 */
    fun remap(snap: Snap, map: Map<String, String>): Snap {
        fun m(id: String) = map[id] ?: id
        return Snap(
            persons = snap.persons.map { p ->
                p.copy(id = m(p.id))
            }.sortedBy { it.id },
            rels = snap.rels.map { Rel(it.type, m(it.a), m(it.b), it.order) }
                .sortedWith(compareBy({ it.type }, { it.a }, { it.b }))
        )
    }

    // ---------- 规范化序列化与哈希 ----------

    /**
     * 规范化 JSON 字符串：键序与数组序全部固定，不依赖 Map 迭代顺序；
     * 同一逻辑快照在任何设备上得到同一字符串（即同一哈希）。
     */
    fun canonicalJson(snap: Snap): String = buildString {
        append("{\"persons\":[")
        snap.persons.forEachIndexed { i, p ->
            if (i > 0) append(',')
            append("{\"id\":").append(j(p.id))
            append(",\"name\":").append(j(p.name))
            append(",\"gender\":").append(j(p.gender))
            append(",\"living\":").append(j(p.living))
            append(",\"note\":").append(j(p.note))
            append(",\"avatarHash\":").append(j(p.avatarHash))
            // siblingRank 只在非 null 时输出：全员未记录时规范串与
            // 旧版一致，跨版本协作的基准快照哈希保持兼容。
            p.siblingRank?.let { append(",\"siblingRank\":").append(it) }
            append(",\"phones\":[")
            p.phones.forEachIndexed { k, ph ->
                if (k > 0) append(',')
                append("{\"number\":").append(j(ph.number))
                append(",\"label\":").append(j(ph.label))
                append(",\"status\":").append(j(ph.status))
                append(",\"primary\":").append(ph.primary)
                append(",\"note\":").append(j(ph.note))
                append('}')
            }
            append("],\"dates\":[")
            p.dates.forEachIndexed { k, d ->
                if (k > 0) append(',')
                append("{\"type\":").append(j(d.type))
                append(",\"year\":").append(d.year?.toString() ?: "null")
                append(",\"month\":").append(d.month)
                append(",\"day\":").append(d.day)
                append(",\"calendar\":").append(j(d.calendar))
                append(",\"leap\":").append(d.leap)
                append(",\"note\":").append(j(d.note))
                append('}')
            }
            append("]}")
        }
        append("],\"rels\":[")
        snap.rels.forEachIndexed { i, r ->
            if (i > 0) append(',')
            append("{\"type\":").append(j(r.type))
            append(",\"a\":").append(j(r.a))
            append(",\"b\":").append(j(r.b))
            append(",\"order\":").append(j(r.order))
            append('}')
        }
        append("]}")
    }

    /** 快照内容哈希（sha256，十六进制小写）。 */
    fun hash(snap: Snap): String =
        sha256Hex(canonicalJson(snap).toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ---------- JSON 读写（包内嵌基准快照用） ----------

    /** 与 [canonicalJson] 同结构的 JSONObject（键序无关紧要，解析端按字段读）。 */
    fun toJson(snap: Snap): JSONObject {
        val root = JSONObject()
        root.put("persons", JSONArray().apply {
            snap.persons.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("gender", p.gender)
                    put("living", p.living)
                    put("note", p.note ?: JSONObject.NULL)
                    put("avatarHash", p.avatarHash ?: JSONObject.NULL)
                    p.siblingRank?.let { put("siblingRank", it) }
                    put("phones", JSONArray().apply {
                        p.phones.forEach { ph ->
                            put(JSONObject().apply {
                                put("number", ph.number); put("label", ph.label)
                                put("status", ph.status); put("primary", ph.primary)
                                put("note", ph.note ?: JSONObject.NULL)
                            })
                        }
                    })
                    put("dates", JSONArray().apply {
                        p.dates.forEach { d ->
                            put(JSONObject().apply {
                                put("type", d.type)
                                put("year", d.year ?: JSONObject.NULL)
                                put("month", d.month); put("day", d.day)
                                put("calendar", d.calendar); put("leap", d.leap)
                                put("note", d.note ?: JSONObject.NULL)
                            })
                        }
                    })
                })
            }
        })
        root.put("rels", JSONArray().apply {
            snap.rels.forEach { r ->
                put(JSONObject().apply {
                    put("type", r.type); put("a", r.a); put("b", r.b)
                    put("order", r.order ?: JSONObject.NULL)
                })
            }
        })
        return root
    }

    /** 解析嵌入包内的基准快照；非法内容抛 [IllegalArgumentException]。 */
    fun parse(json: JSONObject): Snap {
        val persons = json.getJSONArray("persons").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Person(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    gender = o.getString("gender"),
                    living = o.getString("living"),
                    note = if (o.isNull("note")) null else o.getString("note"),
                    avatarHash = if (o.isNull("avatarHash")) null
                        else o.getString("avatarHash"),
                    // 旧版快照没有该键：缺省/非法值视为未记录
                    siblingRank = o.optInt("siblingRank", 0)
                        .takeIf { it >= 1 },
                    phones = o.getJSONArray("phones").let { pa ->
                        (0 until pa.length()).map { j ->
                            val ph = pa.getJSONObject(j)
                            Phone(
                                number = ph.getString("number"),
                                label = ph.getString("label"),
                                status = ph.getString("status"),
                                primary = ph.getBoolean("primary"),
                                note = if (ph.isNull("note")) null
                                    else ph.getString("note")
                            )
                        }
                    },
                    dates = o.getJSONArray("dates").let { da ->
                        (0 until da.length()).map { j ->
                            val d = da.getJSONObject(j)
                            DateEntry(
                                type = d.getString("type"),
                                year = if (d.isNull("year")) null else d.getInt("year"),
                                month = d.getInt("month"),
                                day = d.getInt("day"),
                                calendar = d.getString("calendar"),
                                leap = d.getBoolean("leap"),
                                note = if (d.isNull("note")) null
                                    else d.getString("note")
                            )
                        }
                    }
                )
            }
        }
        val rels = json.getJSONArray("rels").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Rel(
                    type = o.getString("type"),
                    a = o.getString("a"),
                    b = o.getString("b"),
                    order = if (o.isNull("order")) null else o.getString("order")
                )
            }
        }
        return Snap(persons.sortedBy { it.id },
            rels.sortedWith(compareBy({ it.type }, { it.a }, { it.b })))
    }

    /** JSON 字符串转义（引号/反斜杠/控制字符）。 */
    private fun j(s: String?): String {
        if (s == null) return "null"
        return buildString(s.length + 2) {
            append('"')
            for (c in s) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u%04x".format(c.code))
                        else append(c)
                }
            }
            append('"')
        }
    }
}
