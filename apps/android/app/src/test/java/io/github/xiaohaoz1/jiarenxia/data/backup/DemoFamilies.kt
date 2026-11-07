package io.github.xiaohaoz1.jiarenxia.data.backup

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity

/**
 * 「演示版」两份内置家庭的规格（demo_groom_zhudi.jrxb / demo_bride_xu.jrxb，
 * 密码 test1234）：用于两台真机实拍「碰一碰结为夫妻」。
 * 新郎版 = MingFamily 全量裁掉「妻族」与「儿女与孙辈」两支（朱棣单身视角，
 * 妻子徐皇后在新娘手机上，碰一碰时才合并）；新娘版 = 徐皇后视角的小家庭。
 * 两份人物姓名不重叠，合并时不会出现「可能是同一人」待确认项。
 */
internal object DemoFamilies {

    const val GROOM_ASSET = "demo_groom_zhudi.jrxb"
    const val BRIDE_ASSET = "demo_bride_xu.jrxb"
    const val PASSWORD = "test1234"

    /** 新郎版裁掉的人物（妻族 6 人 + 儿女与孙辈 22 人）。 */
    val GROOM_DELETED = setOf(
        // 妻族（配偶系）：碰一碰的对象在新娘手机上
        "徐皇后", "徐达", "谢氏", "徐辉祖", "徐增寿", "徐氏",
        // 儿女与孙辈
        "朱高炽", "张氏", "朱瞻基", "孙氏", "朱祁镇", "朱祁钰",
        "朱瞻墉", "朱瞻墡", "嘉兴公主",
        "朱高煦", "韦氏", "朱瞻壑", "朱高燧",
        "永安公主", "袁容", "永平公主", "李让",
        "安成公主", "宋琥", "咸宁公主", "宋瑛",
        "常宁公主", "沐昕"
    )

    /** 新郎版保留名单 = MingFamily 全部规格人名 − 被删名单。 */
    val GROOM_KEEP: Set<String> =
        MingFamily.specs.map { it.name }.toSet() - GROOM_DELETED

    /** 新娘版「我」。 */
    const val BRIDE_SELF = "徐皇后"

    /** 新娘版补边：谢氏 是 徐达 的妻子（父母互为夫妻需显式 SPOUSE 边）。 */
    val BRIDE_EXTRA_EDGES = listOf(
        Triple("徐达", "谢氏", RelationRole.WIFE)
    )

    /**
     * 新娘版规格（与 MingFamily.Spec 同构；anchor 必须先于本人出现）。
     * 徐达/谢氏的生卒与 MingFamily 中同名人物一致，合并后日期不冲突；
     * 徐皇后在新娘视角为在世（新郎版中她已故——合并取舍由碰一碰逻辑定）。
     */
    val BRIDE_SPECS = listOf(
        MingFamily.Spec("徐达", Gender.MALE, PartialDate(1332, 2, 14),
            PartialDate(1385, 4, 7), "中山王，开国第一功臣",
            anchor = BRIDE_SELF, role = RelationRole.FATHER),
        MingFamily.Spec("谢氏", Gender.FEMALE, PartialDate(1335, 6, 1),
            PartialDate(1399, 1, 12), "徐达继室",
            anchor = BRIDE_SELF, role = RelationRole.MOTHER),
        MingFamily.Spec("徐辉祖", Gender.MALE, PartialDate(1368, 7, 20),
            PartialDate(1407, 11, 1), "袭魏国公",
            anchor = BRIDE_SELF, role = RelationRole.YOUNGER_BROTHER),
        MingFamily.Spec("徐增寿", Gender.MALE, PartialDate(1372, 11, 8),
            PartialDate(1402, 8, 1), "左都督",
            anchor = BRIDE_SELF, role = RelationRole.YOUNGER_BROTHER),
        MingFamily.Spec("徐氏", Gender.FEMALE, PartialDate(1370, 4, 16), null,
            "徐达次女",
            anchor = BRIDE_SELF, role = RelationRole.YOUNGER_SISTER),
        MingFamily.Spec("徐妙锦", Gender.FEMALE, PartialDate(1375, 5, 20), null,
            "徐达幼女",
            anchor = BRIDE_SELF, role = RelationRole.YOUNGER_SISTER),
        MingFamily.Spec("徐钦", Gender.MALE, PartialDate(1392, 3, 10), null,
            "徐辉祖之子",
            anchor = "徐辉祖", role = RelationRole.SON),
        MingFamily.Spec("徐景昌", Gender.MALE, PartialDate(1394, 8, 8), null,
            "徐增寿之子",
            anchor = "徐增寿", role = RelationRole.SON)
    )

    /** 新娘版称谓期望（standardTitle；结构描述不校验）。 */
    val BRIDE_TITLES = listOf(
        "徐达" to "父亲",
        "谢氏" to "母亲",
        "徐辉祖" to "弟弟",
        "徐增寿" to "弟弟",
        "徐氏" to "妹妹",
        "徐妙锦" to "妹妹",
        "徐钦" to "侄子",
        "徐景昌" to "侄子"
    )

    /** 新娘版纯图（姓名即 id），与真库导出一致。 */
    fun brideGraph(): FamilyGraph {
        val persons = mutableListOf(
            Person(
                id = BRIDE_SELF, name = BRIDE_SELF, gender = Gender.FEMALE,
                livingStatus = LivingStatus.ALIVE, isSelf = true
            )
        )
        val rels = mutableListOf<Relationship>()
        val birthdays = mutableMapOf<String, PartialDate>()
        birthdays[BRIDE_SELF] = PartialDate(1362, 3, 5)
        for (sp in BRIDE_SPECS) {
            persons += Person(
                id = sp.name, name = sp.name, gender = sp.gender,
                livingStatus = if (sp.death != null)
                    LivingStatus.DECEASED else LivingStatus.ALIVE
            )
            sp.birthday?.let { birthdays[sp.name] = it }
            rels += MingFamily.relOf(sp.name, sp.anchor, sp.role)
        }
        BRIDE_EXTRA_EDGES.forEach { (anchor, person, role) ->
            rels += MingFamily.relOf(person, anchor, role)
        }
        return FamilyGraph(persons, rels, birthdays)
    }

    private fun <T> FamilyResult<T>.ok(): T =
        (this as? FamilyResult.Success)?.value
            ?: error((this as FamilyResult.Failure).error.message)

    /**
     * 新娘版真库播种：与 MingFamily.seed 同法——明代年份绕过表单校验
     * 直写 important_dates；徐皇后本人生日同样直写。
     */
    suspend fun seedBride(repo: FamilyRepositoryImpl, db: AppDatabase): Map<String, String> {
        val specs = BRIDE_SPECS
        val ids = mutableMapOf<String, String>()
        ids[BRIDE_SELF] = repo.createSelf(
            PersonDraft(
                name = BRIDE_SELF, gender = Gender.FEMALE,
                note = "徐达长女，燕王妃"
            )
        ).ok()
        for (sp in specs) {
            require(ids.containsKey(sp.anchor)) {
                "anchor ${sp.anchor} 不在保留名单内（${sp.name}）"
            }
            val draft = PersonDraft(
                name = sp.name, gender = sp.gender,
                note = sp.note,
                livingStatus = if (sp.death != null)
                    LivingStatus.DECEASED else LivingStatus.ALIVE
            )
            ids[sp.name] = repo.createRelative(draft, ids[sp.anchor]!!, sp.role).ok()
        }
        for ((anchor, person, role) in BRIDE_EXTRA_EDGES) {
            repo.addRelationship(ids[anchor]!!, ids[person]!!, role).ok()
        }

        fun dateEntity(
            id: String, person: String, type: ImportantDateType, d: PartialDate
        ) = ImportantDateEntity(
            id = id, personId = ids[person]!!, type = type,
            year = d.year, month = d.month, day = d.day,
            calendarType = CalendarType.SOLAR, isLeapMonth = false,
            note = null, createdAt = 1, updatedAt = 1
        )
        val dateDao = db.importantDateDao()
        dateDao.insert(
            dateEntity("d-xh-b", BRIDE_SELF, ImportantDateType.BIRTHDAY_SOLAR,
                PartialDate(1362, 3, 5))
        )
        specs.forEachIndexed { i, sp ->
            sp.birthday?.let {
                dateDao.insert(
                    dateEntity("d-br-$i-b", sp.name, ImportantDateType.BIRTHDAY_SOLAR, it)
                )
            }
            sp.death?.let {
                dateDao.insert(
                    dateEntity("d-br-$i-x", sp.name,
                        ImportantDateType.DEATH_ANNIVERSARY, it)
                )
            }
        }
        return ids
    }
}
