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
 * 对外演示用测试家庭（testdata/ming_yongle_family.jrxb，密码 test1234）：
 * 明成祖朱棣为“我”，上溯父母、旁及兄弟姐妹与妻族、下含子女儿媳与孙辈，
 * 外加建文帝这个最有戏剧性的侄子。日期一律公历（农历历法范围装不下明代），
 * 生卒用通行史载，个别阙考日期为近似值；所有人除“我”外均为已故。
 * 生成/校验与 TestFamily 同一套机制：本规格同时驱动纯图断言与真库导出。
 */
internal object MingFamily {

    const val PASSWORD = "test1234"
    const val SELF_NAME = "朱棣"

    private data class Spec(
        val name: String,
        val gender: Gender,
        val birthday: PartialDate? = null,          // 公历
        val death: PartialDate? = null,             // 忌日（公历，阙考者略）
        val note: String? = null,
        val anchor: String,                          // 已创建人物名（必须先出现）
        val role: RelationRole                       // 相对 anchor 的角色
    )

    // 注意：anchor 必须先于本人出现（创建顺序即依赖顺序）
    private val specs = listOf(
        Spec("朱元璋", Gender.MALE, PartialDate(1328, 10, 21), PartialDate(1398, 6, 24),
            "明太祖，年号洪武", anchor = "朱棣", role = RelationRole.FATHER),
        Spec("马皇后", Gender.FEMALE, PartialDate(1332, 9, 5), PartialDate(1382, 9, 18),
            "孝慈高皇后", anchor = "朱棣", role = RelationRole.MOTHER),
        Spec("徐皇后", Gender.FEMALE, PartialDate(1362, 3, 5), PartialDate(1407, 8, 6),
            "仁孝文皇后，徐达长女", anchor = "朱棣", role = RelationRole.WIFE),
        Spec("朱标", Gender.MALE, PartialDate(1355, 10, 10), PartialDate(1392, 5, 17),
            "懿文太子", anchor = "朱棣", role = RelationRole.ELDER_BROTHER),
        Spec("朱樉", Gender.MALE, PartialDate(1356, 12, 3), PartialDate(1395, 4, 9),
            "秦王", anchor = "朱棣", role = RelationRole.ELDER_BROTHER),
        Spec("朱棡", Gender.MALE, PartialDate(1358, 12, 18), PartialDate(1398, 3, 30),
            "晋王", anchor = "朱棣", role = RelationRole.ELDER_BROTHER),
        Spec("朱橚", Gender.MALE, PartialDate(1361, 10, 8), PartialDate(1425, 11, 14),
            "周王", anchor = "朱棣", role = RelationRole.YOUNGER_BROTHER),
        Spec("朱桢", Gender.MALE, PartialDate(1364, 4, 5), PartialDate(1424, 4, 20),
            "楚王", anchor = "朱棣", role = RelationRole.YOUNGER_BROTHER),
        Spec("宁国公主", Gender.FEMALE, PartialDate(1364, 7, 1), PartialDate(1434, 9, 7),
            "太祖次女", anchor = "朱棣", role = RelationRole.YOUNGER_SISTER),
        Spec("徐达", Gender.MALE, PartialDate(1332, 2, 14), PartialDate(1385, 4, 7),
            "中山王，开国第一功臣", anchor = "徐皇后", role = RelationRole.FATHER),
        Spec("谢氏", Gender.FEMALE, PartialDate(1335, 6, 1), PartialDate(1399, 1, 12),
            "徐达继室", anchor = "徐皇后", role = RelationRole.MOTHER),
        Spec("常氏", Gender.FEMALE, PartialDate(1355, 11, 1), PartialDate(1378, 12, 11),
            "懿文太子妃，常遇春之女", anchor = "朱标", role = RelationRole.WIFE),
        Spec("朱允炆", Gender.MALE, PartialDate(1377, 12, 5), PartialDate(1402, 7, 13),
            "建文帝", anchor = "朱标", role = RelationRole.SON),
        Spec("梅殷", Gender.MALE, PartialDate(1360, 1, 15), PartialDate(1405, 11, 4),
            "汝南侯，辅政淮安", anchor = "宁国公主", role = RelationRole.HUSBAND),
        Spec("梅顺昌", Gender.MALE, PartialDate(1382, 5, 1), PartialDate(1432, 4, 11),
            "宁国公主长子", anchor = "宁国公主", role = RelationRole.SON),
        Spec("朱高炽", Gender.MALE, PartialDate(1378, 8, 16), PartialDate(1425, 5, 29),
            "明仁宗，年号洪熙", anchor = "朱棣", role = RelationRole.SON),
        Spec("朱高煦", Gender.MALE, PartialDate(1380, 12, 30), PartialDate(1426, 10, 6),
            "汉王", anchor = "朱棣", role = RelationRole.SON),
        Spec("朱高燧", Gender.MALE, PartialDate(1383, 1, 19), PartialDate(1431, 10, 5),
            "赵王", anchor = "朱棣", role = RelationRole.SON),
        Spec("永安公主", Gender.FEMALE, PartialDate(1377, 7, 1), PartialDate(1417, 1, 28),
            "下嫁袁容", anchor = "朱棣", role = RelationRole.DAUGHTER),
        Spec("永平公主", Gender.FEMALE, PartialDate(1379, 11, 11), PartialDate(1444, 11, 19),
            "下嫁李让", anchor = "朱棣", role = RelationRole.DAUGHTER),
        Spec("张氏", Gender.FEMALE, PartialDate(1379, 11, 18), PartialDate(1442, 11, 20),
            "诚孝昭皇后", anchor = "朱高炽", role = RelationRole.WIFE),
        Spec("朱瞻基", Gender.MALE, PartialDate(1399, 3, 16), PartialDate(1435, 1, 31),
            "明宣宗，年号宣德", anchor = "朱高炽", role = RelationRole.SON),
        Spec("朱瞻墉", Gender.MALE, PartialDate(1405, 11, 8), PartialDate(1439, 8, 5),
            "越王", anchor = "朱高炽", role = RelationRole.SON),
        Spec("朱瞻壑", Gender.MALE, PartialDate(1402, 11, 25), PartialDate(1426, 10, 6),
            "汉王世子", anchor = "朱高煦", role = RelationRole.SON)
    )

    /** 补边的三元组：addRelationship(anchor, person, role) 语义为“person 是 anchor 的 role”。 */
    private val extraEdges = listOf(
        // 父母、岳父母互为夫妻（PARENT 边只指向孩子，夫妻需显式 SPOUSE）
        Triple("马皇后", "朱元璋", RelationRole.HUSBAND),
        Triple("谢氏", "徐达", RelationRole.HUSBAND),
        // 徐皇后为诸子诸女之母
        Triple("徐皇后", "朱高炽", RelationRole.SON),
        Triple("徐皇后", "朱高煦", RelationRole.SON),
        Triple("徐皇后", "朱高燧", RelationRole.SON),
        Triple("徐皇后", "永安公主", RelationRole.DAUGHTER),
        Triple("徐皇后", "永平公主", RelationRole.DAUGHTER),
        // 张氏为二孙之母
        Triple("张氏", "朱瞻基", RelationRole.SON),
        Triple("张氏", "朱瞻墉", RelationRole.SON),
        // 梅殷为外甥之父
        Triple("梅殷", "梅顺昌", RelationRole.SON)
    )

    // ---------- 称谓期望表（generator/table 测试共用断言） ----------
    // name -> (standardTitle, structuralDescription 或 null=不校验)
    val expectedTitles: List<Triple<String, String?, String?>> = listOf(
        Triple("朱元璋", "父亲", null),
        Triple("马皇后", "母亲", null),
        Triple("徐皇后", "妻子", "妻子"),
        Triple("朱标", "哥哥", null),
        Triple("朱樉", "哥哥", null),
        Triple("朱棡", "哥哥", null),
        Triple("朱橚", "弟弟", null),
        Triple("朱桢", "弟弟", null),
        Triple("宁国公主", "妹妹", null),
        Triple("徐达", "岳父", "妻子的父亲"),
        Triple("谢氏", "岳母", "妻子的母亲"),
        Triple("常氏", "嫂子", "哥哥的妻子"),
        Triple("朱允炆", "侄子", "哥哥的儿子"),
        Triple("梅殷", "妹夫", "妹妹的丈夫"),
        Triple("梅顺昌", "外甥", "妹妹的儿子"),
        Triple("朱高炽", "儿子", null),
        Triple("朱高煦", "儿子", null),
        Triple("朱高燧", "儿子", null),
        Triple("永安公主", "女儿", null),
        Triple("永平公主", "女儿", null),
        Triple("张氏", "儿媳", "儿子的妻子"),
        Triple("朱瞻基", "孙子", "儿子的儿子"),
        Triple("朱瞻墉", "孙子", "儿子的儿子"),
        Triple("朱瞻壑", "孙子", "儿子的儿子")
    )

    // ---------- 纯图构建 ----------

    private fun relOf(person: String, anchor: String, role: RelationRole): Relationship {
        val spec = role.toRelationshipSpec(person, anchor)
        return Relationship(
            id = "r-$person-$anchor-${spec.type}",
            sourcePersonId = spec.sourcePersonId,
            targetPersonId = spec.targetPersonId,
            type = spec.type,
            relativeAgeOrder = spec.relativeAgeOrder
        )
    }

    /** 与真库一致的纯 FamilyGraph（姓名即 id）。 */
    fun graph(): FamilyGraph {
        val persons = mutableListOf(
            Person(
                id = SELF_NAME, name = SELF_NAME, gender = Gender.MALE,
                livingStatus = LivingStatus.ALIVE, isSelf = true
            )
        )
        val rels = mutableListOf<Relationship>()
        val birthdays = mutableMapOf<String, PartialDate>()
        birthdays[SELF_NAME] = PartialDate(1360, 5, 2)
        for (sp in specs) {
            persons += Person(
                id = sp.name, name = sp.name, gender = sp.gender,
                livingStatus = LivingStatus.DECEASED
            )
            sp.birthday?.let { birthdays[sp.name] = it }
            rels += relOf(sp.name, sp.anchor, sp.role)
        }
        extraEdges.forEach { (anchor, person, role) -> rels += relOf(person, anchor, role) }
        return FamilyGraph(persons, rels, birthdays)
    }

    private fun <T> FamilyResult<T>.ok(): T =
        (this as? FamilyResult.Success)?.value
            ?: error((this as FamilyResult.Failure).error.message)

    // ---------- 真库播种 ----------

    /**
     * 通过真实 repository 建人建关系，返回 姓名→personId。
     * 明代年份超出表单的 1800..今年 校验，故生卒/结婚日期一律直写 important_dates
     * （与下方家事任务同样绕过 UI 层校验——本文件就是测试夹具，不走表单）。
     */
    suspend fun seed(repo: FamilyRepositoryImpl, db: AppDatabase): Map<String, String> {
        val ids = mutableMapOf<String, String>()
        ids[SELF_NAME] = repo.createSelf(
            PersonDraft(
                name = SELF_NAME, gender = Gender.MALE,
                note = "明成祖，年号永乐"
            )
        ).ok()
        for (sp in specs) {
            val draft = PersonDraft(
                name = sp.name, gender = sp.gender,
                note = sp.note,
                livingStatus = LivingStatus.DECEASED
            )
            ids[sp.name] = repo.createRelative(draft, ids[sp.anchor]!!, sp.role).ok()
        }
        for ((anchor, person, role) in extraEdges) {
            repo.addRelationship(ids[anchor]!!, ids[person]!!, role).ok()
        }

        // 全部日期直写表：生日 BIRTHDAY_SOLAR、忌日 DEATH_ANNIVERSARY、
        // 朱棣×徐皇后成婚纪念日（洪武九年正月册为燕王妃，取近似日，挂徐皇后名下）
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
            dateEntity("d-zd-b", SELF_NAME, ImportantDateType.BIRTHDAY_SOLAR,
                PartialDate(1360, 5, 2))
        )
        specs.forEachIndexed { i, sp ->
            sp.birthday?.let {
                dateDao.insert(
                    dateEntity("d-$i-b", sp.name, ImportantDateType.BIRTHDAY_SOLAR, it)
                )
            }
            sp.death?.let {
                dateDao.insert(
                    dateEntity("d-$i-x", sp.name, ImportantDateType.DEATH_ANNIVERSARY, it)
                )
            }
        }
        dateDao.insert(
            dateEntity("d-xu-wed", "徐皇后", ImportantDateType.WEDDING_ANNIVERSARY,
                PartialDate(1376, 3, 1))
        )

        // 一条演示家事：给父皇备祭
        val today = java.time.LocalDate.now()
        val taskId = "task-ming-xiaoling"
        db.familyTaskDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = taskId, personId = ids["朱元璋"],
                    kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CUSTOM.name,
                    title = "筹备明孝陵祭礼",
                    anchorEpochDay = today.plusDays(12).toEpochDay(),
                    repeat = io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.NONE.name,
                    periodIndex = 0, lastDoneEpochDay = null,
                    done = false, amountFen = null,
                    note = "供果仪程已拟",
                    enabled = true, createdAt = 1, updatedAt = 1
                )
            )
        )
        db.familyTaskOffsetDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    taskId + "-o0", taskId, 0
                )
            )
        )

        return ids
    }
}
