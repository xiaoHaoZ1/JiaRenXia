package io.github.xiaohaoz1.jiarenxia.screenshots

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneLabel
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneNumber
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.core.model.ReminderPreference
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOverview
import io.github.xiaohaoz1.jiarenxia.domain.PersonOverview
import io.github.xiaohaoz1.jiarenxia.ui.family.FamilyUiState
import io.github.xiaohaoz1.jiarenxia.data.PersonDetail
import io.github.xiaohaoz1.jiarenxia.domain.FamilyOrdering
import io.github.xiaohaoz1.jiarenxia.ui.person.BirthdayFormState
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonDetailUiState
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditMode
import io.github.xiaohaoz1.jiarenxia.ui.person.PersonEditUiState
import io.github.xiaohaoz1.jiarenxia.ui.person.RelationRow
import java.time.LocalDate

/** 截图/演示用的中文演示家庭（ScreenShotsTest 与 ReadmeShotsTest 共用）。 */
internal object Demo {

// ---------- 演示数据 ----------

val today = LocalDate.of(2026, 10, 2)
val lunar = io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()

fun p(
    id: String, name: String, gender: Gender,
    status: LivingStatus = LivingStatus.ALIVE, self: Boolean = false
) = Person(
    id = id, name = name, gender = gender, livingStatus = status, isSelf = self
)

fun rel(s: String, t: String, type: RelationshipType, order: RelativeAgeOrder? = null) =
    Relationship("r-$s-$t-$type", s, t, type, order)

/** 演示家庭：我 杨明 + 三代亲属 + 一位未关联。 */
fun demoGraph(publicSample: Boolean = false): FamilyGraph {
    val persons = listOf(
        p("me", "杨明", Gender.MALE, self = true),
        p("dad", "杨建国", Gender.MALE),
        p("mom", "王秀兰", Gender.FEMALE),
        p("gfd", "杨福生", Gender.MALE, status = LivingStatus.DECEASED),
        p("gmd", "李桂英", Gender.FEMALE),
        p("wife", "李雪", Gender.FEMALE),
        p("fil", "李国强", Gender.MALE),
        p("mil", "陈美华", Gender.FEMALE),
        p("dau", "杨小雨", Gender.FEMALE),
        p("bro", "杨帆", Gender.MALE),
        p("uncle", "杨建春", Gender.MALE),
        p("aunt", "杨建英", Gender.FEMALE),
        p("uncle_m", "王志刚", Gender.MALE),
        p("other", "赵老师", Gender.MALE),
        // V0.2 称谓扩展演示数据（KINSHIP_ENGINE 37.2）
        p("bomu", "周丽", Gender.FEMALE),       // 伯父的妻子 → 伯母
        p("cousin", "杨磊", Gender.MALE),        // 伯父的儿子，长于我 → 堂哥
        p("cousin_m", "王婷", Gender.FEMALE),    // 舅家的女儿 → 表妹
        p("sao", "刘静", Gender.FEMALE),         // 哥哥的妻子 → 嫂子
        p("jiuzi", "李强", Gender.MALE)          // 妻子的哥哥 → 大舅子
    )
    val rels = listOf(
        rel("dad", "me", RelationshipType.PARENT),
        rel("mom", "me", RelationshipType.PARENT),
        rel("gfd", "dad", RelationshipType.PARENT),
        rel("gmd", "dad", RelationshipType.PARENT),
        rel("me", "wife", RelationshipType.SPOUSE),
        rel("fil", "wife", RelationshipType.PARENT),
        rel("mil", "wife", RelationshipType.PARENT),
        rel("me", "dau", RelationshipType.PARENT),
        rel("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
        rel("uncle", "dad", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
        rel("aunt", "dad", RelationshipType.SIBLING),
        rel("uncle_m", "mom", RelationshipType.SIBLING),
        rel("uncle", "bomu", RelationshipType.SPOUSE),
        rel("uncle", "cousin", RelationshipType.PARENT),
        rel("uncle_m", "cousin_m", RelationshipType.PARENT),
        rel("bro", "sao", RelationshipType.SPOUSE),
        rel("jiuzi", "wife", RelationshipType.SIBLING)
    )
    val birthdayValues = mapOf(
        "me" to BirthdayValue(PartialDate(1990, 5, 12)),
        "dad" to BirthdayValue(PartialDate(1962, 3, 1)),
        "mom" to BirthdayValue(PartialDate(1964, 10, 2)),      // 今天
        // 奶奶：农历八月十五，未填年份
        "gmd" to BirthdayValue(
            PartialDate(null, 8, 15), io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR
        ),
        "wife" to BirthdayValue(PartialDate(1992, 8, 20)),
        "mil" to BirthdayValue(PartialDate(null, 10, 20)),
        "dau" to BirthdayValue(PartialDate(2018, 6, 1)),
        "bro" to BirthdayValue(PartialDate(1987, 1, 15)),
        "uncle" to BirthdayValue(PartialDate(1958, 11, 3)),
        "cousin" to BirthdayValue(PartialDate(1986, 4, 10)),   // 长于我 → 堂哥
        "cousin_m" to BirthdayValue(PartialDate(1995, 7, 7)),  // 幼于我 → 表妹
        "jiuzi" to BirthdayValue(PartialDate(1989, 2, 2))      // 长于妻子 → 大舅子
    )
    // birthdays 供长幼比较（农历先换算公历，无年份者不参与）
    val solar = birthdayValues.mapNotNull { (id, b) ->
        io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath.solarBirthDate(b, lunar)
            ?.let { id to PartialDate(it.year, it.monthValue, it.dayOfMonth) }
    }.toMap()
    // 公开宣传图使用英文合成姓名与健在人物，避免中文重名造成误解。
    val publicNames = mapOf(
        "me" to "Alex", "dad" to "David", "mom" to "Emma",
        "gfd" to "George", "gmd" to "Grace", "wife" to "Lily",
        "fil" to "Henry", "mil" to "Helen", "dau" to "Lucy",
        "bro" to "Ben", "uncle" to "James", "aunt" to "Alice",
        "uncle_m" to "Jack", "other" to "Oliver", "bomu" to "Rose",
        "cousin" to "Leo", "cousin_m" to "Sophie", "sao" to "Anna",
        "jiuzi" to "Ryan"
    )
    val samplePersons = if (publicSample) persons.map {
        it.copy(name = publicNames.getValue(it.id), livingStatus = LivingStatus.ALIVE)
    } else persons
    return FamilyGraph(samplePersons, rels, solar, birthdayValues.mapValues { listOf(it.value) })
}

/** 与 ObserveFamilyOverview.build 相同的组装方式。 */
/** 演示用自定义称呼（DATABASE_SCHEMA 25.2）：杨建春 → “大伯”。 */
val customTitles: Map<String, String> = mapOf("uncle" to "大伯")

fun demoOverview(publicSample: Boolean = false): FamilyOverview {
    val graph = demoGraph(publicSample)
    val engine = KinshipEngine()
    val self = graph.persons.first { it.isSelf }
    val results = engine.resolveAll(graph, self.id)
    val byId = graph.persons.associate { person ->
        person.id to PersonOverview(
            person, results.getValue(person.id),
            graph.birthdayValues[person.id] ?: emptyList(),
            solarBirthday = graph.birthdays[person.id],
            customTitle = customTitles[person.id]
        )
    }
    val rest = byId.values.filter { it.person.id != self.id }
    return FamilyOverview(
        self = byId.getValue(self.id),
        graph = graph,
        byId = byId,
        core = FamilyOrdering.core(rest.filter { it.result.coreGroup != null }, byId),
        others = FamilyOrdering.others(rest.filter { it.result.coreGroup == null })
    )
}

fun familyState(publicSample: Boolean = false) = FamilyUiState(
    overview = demoOverview(publicSample),
    defaultNumbers = mapOf(
        "me" to "13800138000",
        "mom" to "13912345678"
    ),
    today = today
)

fun detailOf(id: String, publicSample: Boolean = false): PersonDetailUiState {
    val ov = demoOverview(publicSample)
    val o = ov.byId.getValue(id)
    val birthdays = (ov.graph.birthdayValues[id] ?: emptyList()).associate { v ->
        val dateId = "d-$id-" + v.calendar.name
        v.calendar to io.github.xiaohaoz1.jiarenxia.data.BirthdayDetail(
            dateId = dateId, value = v,
            reminder = ReminderPreference("rp-$dateId", dateId, enabled = true)
        )
    }
    val rels = ov.graph.relationships.filter {
        it.sourcePersonId == id || it.targetPersonId == id
    }
    val engine = KinshipEngine()
    val rows = rels.map { r ->
        val otherId = if (r.sourcePersonId == id) r.targetPersonId else r.sourcePersonId
        RelationRow(
            r, ov.graph.persons.first { it.id == otherId },
            engine.directRelationLabel(ov.graph, id, r)
        )
    }
    return PersonDetailUiState(
        loading = false,
        detail = PersonDetail(
            person = o.person,
            phones = when (id) {
                "me" -> listOf(
                    PhoneNumber("ph1", id, "13800138000", PhoneLabel.MOBILE, isPrimary = true)
                )
                "mom" -> listOf(
                    PhoneNumber("ph2", id, "13912345678", PhoneLabel.MOBILE, isPrimary = true)
                )
                else -> emptyList()
            },
            birthdays = birthdays,
            relationships = rels
        ),
        overview = o,
        familyById = ov.byId,
        relationRows = rows,
        relatedIds = rels.flatMap { listOf(it.sourcePersonId, it.targetPersonId) }.toSet()
    )
}

/**
 * “我”的详情：领证/结婚两条已填纪念日走 DateRowDetail 面板，
 * 恋爱纪念日留空走 AddDateRow 添加入口，展示新的提醒面板样式。
 */
fun selfDetail(publicSample: Boolean = false): PersonDetailUiState {
    val base = detailOf("me", publicSample)
    val ov = demoOverview(publicSample)
    val me = ov.byId.getValue("me").person
    val wife = ov.byId.getValue("wife").person
    fun entry(t: ImportantDateType, y: Int, m: Int, d: Int) =
        io.github.xiaohaoz1.jiarenxia.data.DateEntry(
            type = t, person = me,
            date = BirthdayValue(PartialDate(y, m, d)),
            reminderEnabled = true,
            partnerId = wife.id, partnerName = wife.name
        )
    return base.copy(
        coupleDates = mapOf(
            ImportantDateType.LICENSE_ANNIVERSARY to
                entry(ImportantDateType.LICENSE_ANNIVERSARY, 2020, 10, 10),
            ImportantDateType.WEDDING_ANNIVERSARY to
                entry(ImportantDateType.WEDDING_ANNIVERSARY, 2021, 5, 20)
        )
    )
}

/**
 * 双生日演示：妈妈阳历 1964-10-02 + 经换算与之一致的农历生日；
 * 农历槽位演示“第二生日默认关提醒”（enabled = false）。
 */
fun dualBirthdayDetail(): PersonDetailUiState {
    val base = detailOf("mom")
    val ld = lunar.fromSolar(LocalDate.of(1964, 10, 2))
    val lunarB = BirthdayValue(
        PartialDate(ld.year, ld.month, ld.day),
        io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR, ld.leap
    )
    val extra = io.github.xiaohaoz1.jiarenxia.data.BirthdayDetail(
        "d-mom-LUNAR", lunarB,
        ReminderPreference("rp-mom-lunar", "d-mom-LUNAR", enabled = false)
    )
    val d = base.detail!!.copy(
        birthdays = base.detail.birthdays + (lunarB.calendar to extra)
    )
    return base.copy(detail = d)
}

/** 与 [dualBirthdayDetail] 同一份生日的编辑页表单态（两份完整且一致）。 */
fun dualBirthdayEditState(): PersonEditUiState {
    val ld = lunar.fromSolar(LocalDate.of(1964, 10, 2))
    return PersonEditUiState(
        loaded = true, mode = PersonEditMode.EDIT,
        name = "王秀兰", gender = Gender.FEMALE,
        solarBirthday = BirthdayFormState(
            on = true, yearText = "1964", month = 10, day = 2
        ),
        lunarBirthday = BirthdayFormState(
            on = true, yearText = ld.year.toString(),
            month = ld.month, day = ld.day, leapMonth = ld.leap
        ),
        anchorTitle = "母亲", isSelf = false
    )
}
}
