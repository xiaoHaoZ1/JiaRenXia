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
 * 明成祖朱棣为“我”，父系、母系、配偶系、兄弟姐妹、子孙五条支线都有人——
 * 上溯父母与祖辈，旁及兄弟姐妹、堂表亲、妻舅妻妹，下含子女儿媳女婿与
 * 孙辈曾孙。日期一律公历（农历历法范围装不下明代），生卒用通行史载，
 * 个别阙考日期为近似值。演示定位：祖辈与早亡者记忌日，儿孙及以下辈
 * 一律按在世处理（不写卒年），让生日与忌日在时间线上都有展示。
 * 生成/校验与 TestFamily 同一套机制：本规格同时驱动纯图断言与真库导出。
 */
internal object MingFamily {

    const val PASSWORD = "test1234"
    const val SELF_NAME = "朱棣"

    private data class Spec(
        val name: String,
        val gender: Gender,
        val birthday: PartialDate? = null,          // 公历
        val death: PartialDate? = null,             // 忌日（公历）；null = 演示中按在世处理
        val note: String? = null,
        val anchor: String,                          // 已创建人物名（必须先出现）
        val role: RelationRole                       // 相对 anchor 的角色
    )

    // 注意：anchor 必须先于本人出现（创建顺序即依赖顺序）
    private val specs = listOf(
        // ---------- 父母与祖辈 ----------
        Spec("朱元璋", Gender.MALE, PartialDate(1328, 10, 21), PartialDate(1398, 6, 24),
            "明太祖，年号洪武", anchor = "朱棣", role = RelationRole.FATHER),
        Spec("马皇后", Gender.FEMALE, PartialDate(1332, 9, 5), PartialDate(1382, 9, 18),
            "孝慈高皇后", anchor = "朱棣", role = RelationRole.MOTHER),
        // 母系：马皇后幼孤，由郭子兴夫妇抚养（养父母一支）
        Spec("郭子兴", Gender.MALE, PartialDate(1330, 6, 15), PartialDate(1355, 3, 1),
            "濠州红巾军元帅，马皇后养父", anchor = "马皇后", role = RelationRole.FATHER),
        Spec("张夫人", Gender.FEMALE, PartialDate(1335, 2, 20), PartialDate(1355, 9, 10),
            "郭子兴妻，马皇后养母", anchor = "郭子兴", role = RelationRole.WIFE),
        Spec("郭天叙", Gender.MALE, PartialDate(1350, 8, 12), PartialDate(1355, 10, 15),
            "郭子兴之子，马皇后养兄弟，攻集庆战死", anchor = "郭子兴", role = RelationRole.SON),
        // 父系祖辈旁支：朱元璋的兄姐及其后人
        Spec("朱兴隆", Gender.MALE, PartialDate(1315, 4, 8), PartialDate(1344, 5, 20),
            "南昌王（追封），太祖长兄，淮北大饥中亡", anchor = "朱元璋", role = RelationRole.ELDER_BROTHER),
        Spec("王氏", Gender.FEMALE, PartialDate(1318, 9, 3), PartialDate(1344, 5, 25),
            "南昌王妃", anchor = "朱兴隆", role = RelationRole.WIFE),
        Spec("朱文正", Gender.MALE, PartialDate(1336, 10, 10), PartialDate(1365, 8, 20),
            "大都督，洪都保卫战主将", anchor = "朱兴隆", role = RelationRole.SON),
        Spec("朱守谦", Gender.MALE, PartialDate(1361, 4, 18), PartialDate(1392, 2, 28),
            "靖江王，藩于桂林", anchor = "朱文正", role = RelationRole.SON),
        Spec("曹国长公主", Gender.FEMALE, PartialDate(1320, 5, 15), PartialDate(1351, 10, 20),
            "太祖之姐（追封），李文忠之母", anchor = "朱元璋", role = RelationRole.ELDER_SISTER),
        Spec("李贞", Gender.MALE, PartialDate(1303, 6, 20), PartialDate(1379, 10, 16),
            "曹国公，驸马都尉", anchor = "曹国长公主", role = RelationRole.HUSBAND),
        Spec("李文忠", Gender.MALE, PartialDate(1339, 3, 25), PartialDate(1384, 3, 1),
            "曹国公，开国名将，太祖外甥", anchor = "曹国长公主", role = RelationRole.SON),
        // ---------- 妻族（配偶系） ----------
        Spec("徐皇后", Gender.FEMALE, PartialDate(1362, 3, 5), PartialDate(1407, 8, 6),
            "仁孝文皇后，徐达长女", anchor = "朱棣", role = RelationRole.WIFE),
        Spec("徐达", Gender.MALE, PartialDate(1332, 2, 14), PartialDate(1385, 4, 7),
            "中山王，开国第一功臣", anchor = "徐皇后", role = RelationRole.FATHER),
        Spec("谢氏", Gender.FEMALE, PartialDate(1335, 6, 1), PartialDate(1399, 1, 12),
            "徐达继室", anchor = "徐皇后", role = RelationRole.MOTHER),
        Spec("徐辉祖", Gender.MALE, PartialDate(1368, 7, 20), PartialDate(1407, 11, 1),
            "袭魏国公，靖难时率兵抗燕", anchor = "徐皇后", role = RelationRole.YOUNGER_BROTHER),
        Spec("徐增寿", Gender.MALE, PartialDate(1372, 11, 8), PartialDate(1402, 8, 1),
            "暗中通燕，为建文帝所诛，追封定国公", anchor = "徐皇后", role = RelationRole.YOUNGER_BROTHER),
        Spec("徐氏", Gender.FEMALE, PartialDate(1370, 4, 16), null,
            "徐达次女，代王妃——既是小姨子又是弟媳", anchor = "徐皇后", role = RelationRole.YOUNGER_SISTER),
        // ---------- 兄嫂侄辈：朱标一房 ----------
        Spec("朱标", Gender.MALE, PartialDate(1355, 10, 10), PartialDate(1392, 5, 17),
            "懿文太子", anchor = "朱棣", role = RelationRole.ELDER_BROTHER),
        Spec("常氏", Gender.FEMALE, PartialDate(1355, 11, 1), PartialDate(1378, 12, 11),
            "懿文太子妃，常遇春之女", anchor = "朱标", role = RelationRole.WIFE),
        Spec("朱允炆", Gender.MALE, PartialDate(1377, 12, 5), PartialDate(1402, 7, 13),
            "建文帝", anchor = "朱标", role = RelationRole.SON),
        // 建文生母：一人一位配偶的约束下不占朱标的配偶位，按朱允炆之母挂接
        Spec("吕氏", Gender.FEMALE, PartialDate(1358, 6, 30), null,
            "朱标继妃，建文帝生母", anchor = "朱允炆", role = RelationRole.MOTHER),
        Spec("朱允熥", Gender.MALE, PartialDate(1378, 11, 29), null,
            "吴王（降封广泽王）", anchor = "朱标", role = RelationRole.SON),
        Spec("江都公主", Gender.FEMALE, PartialDate(1378, 3, 10), null,
            "朱标长女，下嫁耿璿", anchor = "朱标", role = RelationRole.DAUGHTER),
        Spec("朱文奎", Gender.MALE, PartialDate(1396, 10, 30), PartialDate(1402, 7, 13),
            "建文太子，与父同没于宫中火", anchor = "朱允炆", role = RelationRole.SON),
        Spec("朱文圭", Gender.MALE, PartialDate(1401, 3, 10), null,
            "建庶人，幽禁五十余载", anchor = "朱允炆", role = RelationRole.SON),
        // ---------- 兄嫂侄辈：朱樉一房 ----------
        Spec("朱樉", Gender.MALE, PartialDate(1356, 12, 3), PartialDate(1395, 4, 9),
            "秦王", anchor = "朱棣", role = RelationRole.ELDER_BROTHER),
        Spec("观音奴", Gender.FEMALE, PartialDate(1356, 2, 14), PartialDate(1395, 4, 9),
            "秦王正妃，扩廓帖木儿（王保保）之妹，殉葬", anchor = "朱樉", role = RelationRole.WIFE),
        Spec("朱尚炳", Gender.MALE, PartialDate(1380, 12, 15), null,
            "袭秦王", anchor = "朱樉", role = RelationRole.SON),
        // ---------- 其余兄弟与姐妹 ----------
        Spec("朱棡", Gender.MALE, PartialDate(1358, 12, 18), PartialDate(1398, 3, 30),
            "晋王", anchor = "朱棣", role = RelationRole.ELDER_BROTHER),
        Spec("朱橚", Gender.MALE, PartialDate(1361, 10, 8), null,
            "周王，著《救荒本草》", anchor = "朱棣", role = RelationRole.YOUNGER_BROTHER),
        Spec("朱有炖", Gender.MALE, PartialDate(1379, 2, 6), null,
            "朱橚长子，明代杂剧名家", anchor = "朱橚", role = RelationRole.SON),
        Spec("朱桢", Gender.MALE, PartialDate(1364, 4, 5), PartialDate(1424, 4, 20),
            "楚王", anchor = "朱棣", role = RelationRole.YOUNGER_BROTHER),
        Spec("朱柏", Gender.MALE, PartialDate(1371, 9, 12), PartialDate(1399, 5, 18),
            "湘王，建文削藩，阖宫自焚", anchor = "朱棣", role = RelationRole.YOUNGER_BROTHER),
        Spec("朱权", Gender.MALE, PartialDate(1378, 5, 27), null,
            "宁王，靖难时以朵颜三卫相借", anchor = "朱棣", role = RelationRole.YOUNGER_BROTHER),
        Spec("朱桂", Gender.MALE, PartialDate(1374, 8, 25), null,
            "代王，娶徐达次女", anchor = "朱棣", role = RelationRole.YOUNGER_BROTHER),
        Spec("临安公主", Gender.FEMALE, PartialDate(1359, 3, 12), PartialDate(1421, 8, 17),
            "太祖长女，下嫁李祺", anchor = "朱棣", role = RelationRole.ELDER_SISTER),
        Spec("李祺", Gender.MALE, PartialDate(1356, 7, 22), PartialDate(1403, 2, 15),
            "韩国公李善长之子，驸马都尉", anchor = "临安公主", role = RelationRole.HUSBAND),
        Spec("宁国公主", Gender.FEMALE, PartialDate(1364, 7, 1), null,
            "太祖次女", anchor = "朱棣", role = RelationRole.YOUNGER_SISTER),
        Spec("梅殷", Gender.MALE, PartialDate(1360, 1, 15), PartialDate(1405, 11, 4),
            "汝南侯，辅政淮安", anchor = "宁国公主", role = RelationRole.HUSBAND),
        Spec("梅顺昌", Gender.MALE, PartialDate(1382, 5, 1), null,
            "宁国公主长子", anchor = "宁国公主", role = RelationRole.SON),
        // ---------- 儿女与孙辈 ----------
        Spec("朱高炽", Gender.MALE, PartialDate(1378, 8, 16), null,
            "明仁宗，年号洪熙", anchor = "朱棣", role = RelationRole.SON),
        Spec("张氏", Gender.FEMALE, PartialDate(1379, 11, 18), null,
            "诚孝昭皇后", anchor = "朱高炽", role = RelationRole.WIFE),
        Spec("朱瞻基", Gender.MALE, PartialDate(1399, 3, 16), null,
            "明宣宗，年号宣德", anchor = "朱高炽", role = RelationRole.SON),
        Spec("孙氏", Gender.FEMALE, PartialDate(1401, 5, 11), null,
            "孝恭章皇后", anchor = "朱瞻基", role = RelationRole.WIFE),
        Spec("朱祁镇", Gender.MALE, PartialDate(1427, 11, 29), null,
            "明英宗，年号正统、天顺", anchor = "朱瞻基", role = RelationRole.SON),
        Spec("朱祁钰", Gender.MALE, PartialDate(1428, 9, 11), null,
            "明代宗，年号景泰", anchor = "朱瞻基", role = RelationRole.SON),
        Spec("朱瞻墉", Gender.MALE, PartialDate(1405, 11, 8), null,
            "越王", anchor = "朱高炽", role = RelationRole.SON),
        Spec("朱瞻墡", Gender.MALE, PartialDate(1406, 4, 4), null,
            "襄王", anchor = "朱高炽", role = RelationRole.SON),
        Spec("嘉兴公主", Gender.FEMALE, PartialDate(1398, 9, 20), null,
            "朱高炽长女", anchor = "朱高炽", role = RelationRole.DAUGHTER),
        Spec("朱高煦", Gender.MALE, PartialDate(1380, 12, 30), null,
            "汉王", anchor = "朱棣", role = RelationRole.SON),
        Spec("韦氏", Gender.FEMALE, PartialDate(1382, 10, 12), null,
            "汉王妃", anchor = "朱高煦", role = RelationRole.WIFE),
        Spec("朱瞻壑", Gender.MALE, PartialDate(1402, 11, 25), null,
            "汉王世子", anchor = "朱高煦", role = RelationRole.SON),
        Spec("朱高燧", Gender.MALE, PartialDate(1383, 1, 19), null,
            "赵王", anchor = "朱棣", role = RelationRole.SON),
        Spec("永安公主", Gender.FEMALE, PartialDate(1377, 7, 1), PartialDate(1417, 1, 28),
            "下嫁袁容", anchor = "朱棣", role = RelationRole.DAUGHTER),
        Spec("袁容", Gender.MALE, PartialDate(1371, 5, 20), null,
            "广平侯，驸马都尉", anchor = "永安公主", role = RelationRole.HUSBAND),
        Spec("永平公主", Gender.FEMALE, PartialDate(1379, 11, 11), null,
            "下嫁李让", anchor = "朱棣", role = RelationRole.DAUGHTER),
        Spec("李让", Gender.MALE, PartialDate(1375, 11, 3), null,
            "富阳侯，驸马都尉", anchor = "永平公主", role = RelationRole.HUSBAND),
        Spec("安成公主", Gender.FEMALE, PartialDate(1384, 3, 10), null,
            "下嫁宋琥", anchor = "朱棣", role = RelationRole.DAUGHTER),
        Spec("宋琥", Gender.MALE, PartialDate(1380, 6, 18), null,
            "西宁侯宋晟之子，驸马都尉", anchor = "安成公主", role = RelationRole.HUSBAND),
        Spec("咸宁公主", Gender.FEMALE, PartialDate(1385, 9, 2), null,
            "下嫁宋瑛", anchor = "朱棣", role = RelationRole.DAUGHTER),
        Spec("宋瑛", Gender.MALE, PartialDate(1382, 2, 27), null,
            "宋琥之弟，驸马都尉", anchor = "咸宁公主", role = RelationRole.HUSBAND),
        Spec("常宁公主", Gender.FEMALE, PartialDate(1386, 12, 15), PartialDate(1408, 4, 1),
            "下嫁沐昕，早逝", anchor = "朱棣", role = RelationRole.DAUGHTER),
        Spec("沐昕", Gender.MALE, PartialDate(1386, 8, 9), null,
            "黔宁王沐英之子，驸马都尉", anchor = "常宁公主", role = RelationRole.HUSBAND)
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
        Triple("徐皇后", "安成公主", RelationRole.DAUGHTER),
        Triple("徐皇后", "咸宁公主", RelationRole.DAUGHTER),
        Triple("徐皇后", "常宁公主", RelationRole.DAUGHTER),
        // 谢氏为徐皇后弟妹之母
        Triple("谢氏", "徐辉祖", RelationRole.SON),
        Triple("谢氏", "徐增寿", RelationRole.SON),
        Triple("谢氏", "徐氏", RelationRole.DAUGHTER),
        // 徐氏兼代王妃：相对朱棣既是小姨子又是弟媳（双路径演示）
        Triple("朱桂", "徐氏", RelationRole.WIFE),
        // 嫂子为侄辈之母：朱允熥是常氏所生，江都公主归吕氏名下
        Triple("吕氏", "江都公主", RelationRole.DAUGHTER),
        Triple("常氏", "朱允熥", RelationRole.SON),
        // 祖辈旁支的母系边（李文忠已挂曹国长公主名下，无需再补）
        Triple("王氏", "朱文正", RelationRole.SON),
        Triple("张夫人", "郭天叙", RelationRole.SON),
        Triple("张夫人", "马皇后", RelationRole.DAUGHTER),
        // 儿媳为孙辈之母
        Triple("张氏", "朱瞻基", RelationRole.SON),
        Triple("张氏", "朱瞻墉", RelationRole.SON),
        Triple("张氏", "朱瞻墡", RelationRole.SON),
        Triple("张氏", "嘉兴公主", RelationRole.DAUGHTER),
        Triple("韦氏", "朱瞻壑", RelationRole.SON),
        Triple("孙氏", "朱祁镇", RelationRole.SON),
        // 梅殷为外甥之父
        Triple("梅殷", "梅顺昌", RelationRole.SON)
    )

    // ---------- 称谓期望表（generator/table 测试共用断言） ----------
    // name -> (standardTitle, structuralDescription 或 null=不校验)
    val expectedTitles: List<Triple<String, String?, String?>> = listOf(
        Triple("朱元璋", "父亲", null),
        Triple("马皇后", "母亲", null),
        Triple("郭子兴", "姥爷", "母亲的父亲"),
        Triple("张夫人", "姥姥", "母亲的母亲"),
        Triple("郭天叙", "舅舅", "母亲的兄弟"),
        Triple("朱兴隆", "伯父", "父亲的哥哥"),
        Triple("王氏", "伯母", "父亲的哥哥的妻子"),
        Triple("朱文正", "堂哥", null),
        Triple("朱守谦", "堂侄", null),
        Triple("曹国长公主", "姑姑", "父亲的姐妹"),
        Triple("李贞", "姑父", "父亲的姐姐的丈夫"),
        Triple("李文忠", "表哥", null),
        Triple("徐皇后", "妻子", "妻子"),
        Triple("徐达", "岳父", "妻子的父亲"),
        Triple("谢氏", "岳母", "妻子的母亲"),
        Triple("徐辉祖", "小舅子", "妻子的弟弟"),
        Triple("徐增寿", "小舅子", "妻子的弟弟"),
        // 徐氏是小姨子也是弟媳：两条等长最短路径给出不同称谓，引擎不静默择一、置 null
        Triple("徐氏", null, "妻子的妹妹"),
        Triple("朱标", "哥哥", null),
        Triple("常氏", "嫂子", "哥哥的妻子"),
        Triple("朱允炆", "侄子", "哥哥的儿子"),
        Triple("朱允熥", "侄子", "哥哥的儿子"),
        Triple("江都公主", "侄女", "哥哥的女儿"),
        Triple("朱文奎", "侄孙", "哥哥的儿子的儿子"),
        Triple("朱文圭", "侄孙", "哥哥的儿子的儿子"),
        Triple("吕氏", null, "哥哥的儿子的母亲"),
        Triple("朱樉", "哥哥", null),
        Triple("观音奴", "嫂子", "哥哥的妻子"),
        Triple("朱尚炳", "侄子", "哥哥的儿子"),
        Triple("朱棡", "哥哥", null),
        Triple("朱橚", "弟弟", null),
        Triple("朱有炖", "侄子", "弟弟的儿子"),
        Triple("朱桢", "弟弟", null),
        Triple("朱柏", "弟弟", null),
        Triple("朱权", "弟弟", null),
        Triple("朱桂", "弟弟", null),
        Triple("临安公主", "姐姐", null),
        Triple("李祺", "姐夫", "姐姐的丈夫"),
        Triple("宁国公主", "妹妹", null),
        Triple("梅殷", "妹夫", "妹妹的丈夫"),
        Triple("梅顺昌", "外甥", "妹妹的儿子"),
        Triple("朱高炽", "儿子", null),
        Triple("张氏", "儿媳", "儿子的妻子"),
        Triple("朱瞻基", "孙子", "儿子的儿子"),
        Triple("孙氏", "孙媳", "儿子的儿子的妻子"),
        Triple("朱祁镇", "曾孙", "儿子的儿子的儿子"),
        Triple("朱祁钰", "曾孙", "儿子的儿子的儿子"),
        Triple("朱瞻墉", "孙子", "儿子的儿子"),
        Triple("朱瞻墡", "孙子", "儿子的儿子"),
        Triple("嘉兴公主", "孙女", "儿子的女儿"),
        Triple("朱高煦", "儿子", null),
        Triple("韦氏", "儿媳", "儿子的妻子"),
        Triple("朱瞻壑", "孙子", "儿子的儿子"),
        Triple("朱高燧", "儿子", null),
        Triple("永安公主", "女儿", null),
        Triple("袁容", "女婿", "女儿的丈夫"),
        Triple("永平公主", "女儿", null),
        Triple("李让", "女婿", "女儿的丈夫"),
        Triple("安成公主", "女儿", null),
        Triple("宋琥", "女婿", "女儿的丈夫"),
        Triple("咸宁公主", "女儿", null),
        Triple("宋瑛", "女婿", "女儿的丈夫"),
        Triple("常宁公主", "女儿", null),
        Triple("沐昕", "女婿", "女儿的丈夫")
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
                // 有卒年者为已故；无卒年者演示中按在世处理
                livingStatus = if (sp.death != null)
                    LivingStatus.DECEASED else LivingStatus.ALIVE
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
                livingStatus = if (sp.death != null)
                    LivingStatus.DECEASED else LivingStatus.ALIVE
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
