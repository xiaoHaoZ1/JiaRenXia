package io.github.xiaohaoz1.jiarenxia.data.backup

import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.Relationship
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.data.FamilyRepositoryImpl
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.PhoneDraft
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase

/**
 * 可复用测试家庭（testdata/jrx_test_family.jrxb 的内容，密码 test1234）。
 * 杨明为“我”：父系/母系/妻系各一支 + 子女四代 + 一位未关联人物。
 * 同一份规格同时驱动：纯图断言（TestFamilyTitlesTest）与真库导出（TestFamilyBackupTest）。
 */
internal object TestFamily {

    const val PASSWORD = "test1234"
    const val SELF_NAME = "杨明"

    private data class Spec(
        val name: String,
        val gender: Gender,
        val birthday: PartialDate? = null,          // 公历
        val deceased: Boolean = false,
        val phone: String? = null,                  // 主号码
        val anchor: String? = null,                 // 已创建人物名
        val role: RelationRole? = null              // 相对 anchor 的角色
    )

    // 注意：anchor 必须先于本人出现（创建顺序即依赖顺序）
    private val specs = listOf(
        Spec("李雪", Gender.FEMALE, PartialDate(1992, 8, 20), phone = "00000000000",
            anchor = "杨明", role = RelationRole.WIFE),
        Spec("杨建国", Gender.MALE, PartialDate(1962, 3, 1), anchor = "杨明",
            role = RelationRole.FATHER),
        Spec("王秀兰", Gender.FEMALE, PartialDate(1964, 10, 2), phone = "00000000001",
            anchor = "杨明", role = RelationRole.MOTHER),
        Spec("杨福生", Gender.MALE, deceased = true, anchor = "杨建国",
            role = RelationRole.FATHER),
        Spec("李桂英", Gender.FEMALE, anchor = "杨建国", role = RelationRole.MOTHER),
        Spec("杨老太爷", Gender.MALE, anchor = "杨福生", role = RelationRole.FATHER),
        Spec("杨福来", Gender.MALE, anchor = "杨福生", role = RelationRole.ELDER_BROTHER),
        Spec("杨福英", Gender.FEMALE, anchor = "杨福生", role = RelationRole.SISTER),
        Spec("李桂林", Gender.MALE, anchor = "李桂英", role = RelationRole.BROTHER),
        Spec("杨建春", Gender.MALE, anchor = "杨建国", role = RelationRole.ELDER_BROTHER),
        Spec("杨建英", Gender.FEMALE, anchor = "杨建国", role = RelationRole.SISTER),
        Spec("周丽", Gender.FEMALE, anchor = "杨建春", role = RelationRole.WIFE),
        Spec("杨磊", Gender.MALE, PartialDate(1986, 4, 10), anchor = "杨建春",
            role = RelationRole.SON),
        Spec("刘静", Gender.FEMALE, anchor = "杨磊", role = RelationRole.WIFE),
        Spec("杨朵朵", Gender.FEMALE, anchor = "杨磊", role = RelationRole.DAUGHTER),
        Spec("杨建华", Gender.MALE, PartialDate(1958, 6, 1), anchor = "杨福来",
            role = RelationRole.SON),
        Spec("杨建业", Gender.MALE, PartialDate(1965, 1, 1), anchor = "杨福来",
            role = RelationRole.SON),
        Spec("赵红", Gender.FEMALE, anchor = "杨建业", role = RelationRole.WIFE),
        Spec("张建明", Gender.MALE, PartialDate(1966, 2, 2), anchor = "杨福英",
            role = RelationRole.SON),
        Spec("李秀梅", Gender.FEMALE, anchor = "李桂林", role = RelationRole.DAUGHTER),
        Spec("王德福", Gender.MALE, anchor = "王秀兰", role = RelationRole.FATHER),
        Spec("赵淑珍", Gender.FEMALE, anchor = "王秀兰", role = RelationRole.MOTHER),
        Spec("王志刚", Gender.MALE, anchor = "王秀兰", role = RelationRole.BROTHER),
        Spec("孙丽", Gender.FEMALE, anchor = "王志刚", role = RelationRole.WIFE),
        Spec("王磊", Gender.MALE, PartialDate(1988, 3, 3), anchor = "王志刚",
            role = RelationRole.SON),
        Spec("陈晨", Gender.FEMALE, anchor = "王磊", role = RelationRole.WIFE),
        Spec("王小宝", Gender.MALE, anchor = "王磊", role = RelationRole.SON),
        Spec("杨帆", Gender.MALE, PartialDate(1987, 1, 15), anchor = "杨明",
            role = RelationRole.ELDER_BROTHER),
        Spec("赵敏", Gender.FEMALE, anchor = "杨帆", role = RelationRole.WIFE),
        Spec("杨小帆", Gender.MALE, anchor = "杨帆", role = RelationRole.SON),
        Spec("钱多多", Gender.FEMALE, anchor = "杨小帆", role = RelationRole.WIFE),
        Spec("杨豆豆", Gender.MALE, anchor = "杨小帆", role = RelationRole.SON),
        Spec("杨小明", Gender.MALE, anchor = "杨明", role = RelationRole.SON),
        Spec("吴佳", Gender.FEMALE, anchor = "杨小明", role = RelationRole.WIFE),
        Spec("吴建军", Gender.MALE, anchor = "吴佳", role = RelationRole.FATHER),
        Spec("郑红", Gender.FEMALE, anchor = "吴佳", role = RelationRole.MOTHER),
        Spec("杨乐乐", Gender.MALE, anchor = "杨小明", role = RelationRole.SON),
        Spec("孙悦", Gender.FEMALE, anchor = "杨乐乐", role = RelationRole.WIFE),
        Spec("杨小小", Gender.MALE, anchor = "杨乐乐", role = RelationRole.SON),
        Spec("李国强", Gender.MALE, PartialDate(1968, 1, 1), anchor = "李雪",
            role = RelationRole.FATHER),
        Spec("陈美华", Gender.FEMALE, anchor = "李雪", role = RelationRole.MOTHER),
        Spec("李老爷子", Gender.MALE, anchor = "李国强", role = RelationRole.FATHER),
        Spec("刘奶奶", Gender.FEMALE, anchor = "李国强", role = RelationRole.MOTHER),
        Spec("李国华", Gender.MALE, anchor = "李国强", role = RelationRole.YOUNGER_BROTHER),
        Spec("马丽", Gender.FEMALE, anchor = "李国华", role = RelationRole.WIFE),
        Spec("李国栋", Gender.MALE, anchor = "李国强", role = RelationRole.ELDER_BROTHER),
        Spec("陈美玲", Gender.FEMALE, anchor = "陈美华", role = RelationRole.SISTER),
        Spec("李伟", Gender.MALE, PartialDate(1990, 1, 1), anchor = "李国华",
            role = RelationRole.SON),
        Spec("李强", Gender.MALE, PartialDate(1989, 2, 2), anchor = "李雪",
            role = RelationRole.ELDER_BROTHER),
        Spec("何静", Gender.FEMALE, anchor = "李强", role = RelationRole.WIFE),
        Spec("李小龙", Gender.MALE, anchor = "李强", role = RelationRole.SON),
        Spec("李老二", Gender.MALE, anchor = "李老爷子", role = RelationRole.YOUNGER_BROTHER),
        Spec("李国平", Gender.MALE, PartialDate(1970, 1, 1), anchor = "李老二",
            role = RelationRole.SON),
        Spec("赵老师", Gender.MALE)   // 未关联到我
    )

    /** 杨小明的第二父母：李雪 也是其母。
     *  addRelationship(anchor, other, role) 语义为“other 是 anchor 的 role”。 */
    /**
     * 家庭事项样例（FAMILY_AFFAIRS 5）：日期相对播种当天，走查时必有今天/逾期/未来项。
     * (taskId, 关联人名或 null=全家, kind, 标题, 距今天数 anchor, repeat, 金额分, 备注, offsets)
     */
    private data class TaskSpec(
        val id: String, val person: String?,
        val kind: io.github.xiaohaoz1.jiarenxia.core.model.TaskKind,
        val title: String, val daysOffset: Int,
        val repeat: io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat,
        val amountFen: Long?, val note: String?,
        val offsets: Set<Int>
    )

    private val taskSpecs = listOf(
        TaskSpec(
            "task-mom-yibao", "赵淑珍",
            io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.MEDICAL_INSURANCE,
            "妈妈的居民医保缴费", 0,
            io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.YEARLY, 38000,
            "社区统一缴费", setOf(7, 0)
        ),
        TaskSpec(
            "task-mom-premium", "王秀兰",
            io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.INSURANCE_PREMIUM,
            "妈妈的百万医疗险缴费", 20,
            io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.YEARLY, 68000,
            null, setOf(15, 3, 0)
        ),
        TaskSpec(
            "task-dad-baoxian", "杨建国",
            io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.INSURANCE_PREMIUM,
            "爸爸的保险缴费", 9,
            io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.YEARLY, 120000,
            null, setOf(15, 3, 0)
        ),
        TaskSpec(
            "task-yeye-renzheng", "杨建国",
            io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.PENSION_CERT,
            "爸爸的养老金资格认证", -2,
            io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.YEARLY, null,
            "手机上刷脸认证", setOf(30, 7)
        ),
        TaskSpec(
            "task-self-tijian", SELF_NAME,
            io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CHECKUP,
            "我的体检", 25,
            io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.YEARLY, null,
            null, setOf(7, 1)
        ),
        TaskSpec(
            "task-all-huafei", null,
            io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CUSTOM,
            "家里宽带缴费", 40,
            io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat.NONE, 12000,
            null, setOf(1, 0)
        )
    )

    private val extraEdges = listOf(
        Triple("李雪", "杨小明", RelationRole.SON)
    )

    val customTitles = mapOf("杨建春" to "大伯")

    // ---------- 称谓期望表（generator/table 测试共用断言） ----------
    // name -> (standardTitle, structuralDescription 或 null=不校验)
    val expectedTitles: List<Triple<String, String?, String?>> = listOf(
        Triple("李雪", "妻子", "妻子"),
        Triple("杨建国", "父亲", null),
        Triple("王秀兰", "母亲", null),
        Triple("杨福生", "爷爷", null),
        Triple("李桂英", "奶奶", null),
        Triple("杨老太爷", "太爷爷", "父亲的父亲的父亲"),
        Triple("杨福来", "伯祖父", "父亲的父亲的哥哥"),
        Triple("杨福英", "姑奶奶", null),
        Triple("李桂林", "舅爷爷", null),
        Triple("杨建春", "伯父", "父亲的哥哥"),
        Triple("杨建英", "姑姑", null),
        Triple("周丽", "伯母", null),
        Triple("杨磊", "堂哥", null),
        Triple("刘静", "堂嫂", "堂哥的妻子"),
        Triple("杨朵朵", "堂侄女", "堂哥的女儿"),
        Triple("杨建华", "堂伯", "父亲的堂哥"),
        Triple("杨建业", "堂叔", "父亲的堂弟"),
        Triple("赵红", "堂婶", "父亲的堂弟的妻子"),
        Triple("张建明", "表叔", "父亲的表弟"),
        Triple("李秀梅", "表姑", "父亲的表姐妹"),
        Triple("王德福", "姥爷", null),
        Triple("赵淑珍", "姥姥", null),
        Triple("王志刚", "舅舅", null),
        Triple("孙丽", "舅妈", null),
        Triple("王磊", "表哥", null),
        Triple("陈晨", "表嫂", "表哥的妻子"),
        Triple("王小宝", "表侄", "表哥的儿子"),
        Triple("杨帆", "哥哥", null),
        Triple("赵敏", "嫂子", null),
        Triple("杨小帆", "侄子", null),
        Triple("钱多多", "侄媳", "哥哥的儿子的妻子"),
        Triple("杨豆豆", "侄孙", "哥哥的儿子的儿子"),
        Triple("杨小明", "儿子", null),
        Triple("吴佳", "儿媳", null),
        Triple("吴建军", "亲家公", "儿子的妻子的父亲"),
        Triple("郑红", "亲家母", "儿子的妻子的母亲"),
        Triple("杨乐乐", "孙子", null),
        Triple("孙悦", "孙媳", "儿子的儿子的妻子"),
        Triple("杨小小", "曾孙", "儿子的儿子的儿子"),
        Triple("李国强", "岳父", "妻子的父亲"),
        Triple("陈美华", "岳母", "妻子的母亲"),
        Triple("李老爷子", "爷爷", "妻子的父亲的父亲"),
        Triple("刘奶奶", "奶奶", "妻子的父亲的母亲"),
        Triple("李国华", "叔叔", "妻子的父亲的弟弟"),
        Triple("马丽", "婶婶", "妻子的父亲的弟弟的妻子"),
        Triple("李国栋", "伯父", "妻子的父亲的哥哥"),
        Triple("陈美玲", "姨妈", "妻子的母亲的姐妹"),
        Triple("李伟", "堂哥", "妻子的父亲的弟弟的儿子"),
        Triple("李强", "大舅子", "妻子的哥哥"),
        Triple("何静", "嫂子", "妻子的哥哥的妻子"),
        Triple("李小龙", "侄子", "妻子的哥哥的儿子"),
        Triple("李老二", "叔祖父", "妻子的父亲的父亲的弟弟"),
        Triple("李国平", "堂叔", "妻子的父亲的堂弟"),
        Triple("赵老师", null, "未关联到我")
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
        birthdays[SELF_NAME] = PartialDate(1990, 5, 12)   // 长幼比较需要“我”的生日
        for (sp in specs) {
            persons += Person(
                id = sp.name, name = sp.name, gender = sp.gender,
                livingStatus = if (sp.deceased) LivingStatus.DECEASED else LivingStatus.ALIVE
            )
            sp.birthday?.let { birthdays[sp.name] = it }
            if (sp.anchor != null && sp.role != null) {
                rels += relOf(sp.name, sp.anchor, sp.role)
            }
        }
        extraEdges.forEach { (anchor, person, role) -> rels += relOf(person, anchor, role) }
        return FamilyGraph(persons, rels, birthdays)
    }

    private fun <T> FamilyResult<T>.ok(): T =
        (this as? FamilyResult.Success)?.value
            ?: error((this as FamilyResult.Failure).error.message)

    // ---------- 真库播种 ----------

    /** 通过真实 repository 建人建关系，返回 姓名→personId。 */
    suspend fun seed(repo: FamilyRepositoryImpl, db: AppDatabase): Map<String, String> {
        val ids = mutableMapOf<String, String>()
        ids[SELF_NAME] = repo.createSelf(
            PersonDraft(
                name = SELF_NAME, gender = Gender.MALE,
                birthdays = PersonBirthdays(solar = BirthdayValue(PartialDate(1990, 5, 12)))
            )
        ).ok()
        for (sp in specs) {
            val draft = PersonDraft(
                name = sp.name, gender = sp.gender,
                livingStatus = if (sp.deceased) LivingStatus.DECEASED else LivingStatus.ALIVE,
                birthdays = sp.birthday
                    ?.let { PersonBirthdays(solar = BirthdayValue(it)) }
                    ?: PersonBirthdays(),
                phones = sp.phone?.let {
                    listOf(PhoneDraft(number = it, isPrimary = true))
                } ?: emptyList()
            )
            val id = if (sp.anchor != null && sp.role != null) {
                repo.createRelative(draft, ids[sp.anchor]!!, sp.role).ok()
            } else {
                // 未关联人物：先挂靠再删边
                val pid = repo.createRelative(draft, ids[SELF_NAME]!!, RelationRole.BROTHER).ok()
                val rid = db.relationshipDao().getBetween(pid, ids[SELF_NAME]!!).single().id
                repo.removeRelationship(rid).ok()
                pid
            }
            ids[sp.name] = id
        }
        for ((anchor, person, role) in extraEdges) {
            repo.addRelationship(ids[anchor]!!, ids[person]!!, role).ok()
        }
        customTitles.forEach { (name, title) -> repo.setCustomTitle(ids[name]!!, title) }
        // 家庭事项：直接写表（相对今天排期）
        val today = java.time.LocalDate.now()
        taskSpecs.forEach { ts ->
            db.familyTaskDao().insertAll(
                listOf(
                    io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                        id = ts.id, personId = ts.person?.let { ids[it] },
                        kind = ts.kind.name, title = ts.title,
                        anchorEpochDay = today.plusDays(ts.daysOffset.toLong()).toEpochDay(),
                        repeat = ts.repeat.name, periodIndex = 0, lastDoneEpochDay = null,
                        done = false, amountFen = ts.amountFen, note = ts.note,
                        enabled = true,
                        createdAt = 1, updatedAt = 1
                    )
                )
            )
            db.familyTaskOffsetDao().insertAll(
                ts.offsets.sorted().mapIndexed { i, d ->
                    io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                        ts.id + "-o" + i, ts.id, d
                    )
                }
            )
        }
        // 重要日期样例（V0.8）：爸妈先记为夫妻（PARENT 边只指向孩子，
        // 两人之间需要显式 SPOUSE），再写纪念日（挂爸名下）；
        // 爷爷农历忌日（带去世年份，算“享年/N 周年”）
        repo.addRelationship(
            ids["王秀兰"]!!, ids["杨建国"]!!,
            RelationRole.HUSBAND
        ).ok()
        repo.setWeddingAnniversary(
            ids["杨建国"]!!,
            BirthdayValue(PartialDate(1986, 10, 18))
        ).ok()
        repo.setDeathAnniversary(
            ids["杨福生"]!!,
            BirthdayValue(
                PartialDate(2016, 8, 25),
                io.github.xiaohaoz1.jiarenxia.core.model.CalendarType.LUNAR)
        ).ok()
        // 联系提醒（V0.9）：妈妈每两周提醒 + 20 天前一次电话 → 逾期状态
        db.contactPlanDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ContactPlanEntity(
                    personId = ids["王秀兰"]!!, intervalDays = 14,
                    createdAt = 1, updatedAt = 1
                )
            )
        )
        db.contactLogDao().insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.ContactLogEntity(
                    id = "cl-mom-1", personId = ids["王秀兰"]!!,
                    atMillis = today.minusDays(20).atTime(9, 0)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli(),
                    method = "CALL", note = "聊家常", createdAt = 1
                )
            )
        )

        return ids
    }
}
