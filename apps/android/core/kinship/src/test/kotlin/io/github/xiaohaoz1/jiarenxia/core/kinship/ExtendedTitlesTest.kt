package io.github.xiaohaoz1.jiarenxia.core.kinship

import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V0.2 扩展称谓（KINSHIP_ENGINE 37.2）：姻亲、孙辈、伯母婶婶、堂表亲、连襟妯娌。 */
class ExtendedTitlesTest {

    private val engine = KinshipEngine()

    private fun check(
        graph: FamilyGraph, observer: String, target: String,
        structural: String, title: String?,
        confidence: Confidence = Confidence.EXACT,
        missing: Set<MissingInformation> = emptySet()
    ) {
        val r = engine.resolve(graph, observer, target)
        assertEquals("$target kind", KinshipKind.DERIVED, r.kind)
        assertEquals("$target structural", structural, r.structuralDescription)
        assertEquals("$target title", title, r.standardTitle)
        assertEquals("$target confidence", confidence, r.confidence)
        assertEquals("$target missing", missing, r.missingInformation)
        assertEquals("$target displayTitle", title ?: structural, r.displayTitle)
    }

    // ---------- 兄弟姐妹的配偶 ----------

    /** 我 + 哥哥/弟弟/姐姐/妹妹 及各自配偶。 */
    private fun siblingSpouseGraph(order: RelativeAgeOrder?, reverse: Boolean = false): FamilyGraph {
        val persons = listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("bro", Gender.MALE), T.p("sis", Gender.FEMALE),
            T.p("broWife", Gender.FEMALE), T.p("sisHusb", Gender.MALE)
        )
        val rels = mutableListOf(
            T.rel("bro", "broWife", RelationshipType.SPOUSE),
            T.rel("sis", "sisHusb", RelationshipType.SPOUSE)
        )
        // SIBLING 记录方向正反均可，order 语义在 source 相对 target
        rels += if (reverse) {
            T.rel("me", "bro", RelationshipType.SIBLING, order?.inverse())
            // 姐妹
        } else {
            T.rel("bro", "me", RelationshipType.SIBLING, order)
        }
        rels += T.rel("sis", "me", RelationshipType.SIBLING, order)
        return T.graph(persons, rels)
    }

    @Test
    fun `哥哥的妻子-嫂子`() =
        check(siblingSpouseGraph(RelativeAgeOrder.OLDER), "me", "broWife", "哥哥的妻子", "嫂子")

    @Test
    fun `弟弟的妻子-弟媳`() =
        check(siblingSpouseGraph(RelativeAgeOrder.YOUNGER), "me", "broWife", "弟弟的妻子", "弟媳")

    @Test
    fun `兄弟长幼未知-兄弟的妻子 PARTIAL`() =
        check(siblingSpouseGraph(null), "me", "broWife", "兄弟的妻子", null,
            Confidence.PARTIAL, setOf(MissingInformation.SIBLING_AGE_ORDER))

    @Test
    fun `兄弟长幼相同-无称谓 EXACT`() =
        check(siblingSpouseGraph(RelativeAgeOrder.SAME), "me", "broWife", "兄弟的妻子", null)

    @Test
    fun `姐姐的丈夫-姐夫`() =
        check(siblingSpouseGraph(RelativeAgeOrder.OLDER), "me", "sisHusb", "姐姐的丈夫", "姐夫")

    @Test
    fun `妹妹的丈夫-妹夫`() =
        check(siblingSpouseGraph(RelativeAgeOrder.YOUNGER), "me", "sisHusb", "妹妹的丈夫", "妹夫")

    @Test
    fun `SIBLING 反方向存储同样生效`() =
        check(siblingSpouseGraph(RelativeAgeOrder.OLDER, reverse = true),
            "me", "broWife", "哥哥的妻子", "嫂子")

    @Test
    fun `兄弟姐妹长幼用生日判断`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("bro", Gender.MALE), T.p("broWife", Gender.FEMALE)
            ),
            listOf(
                T.rel("bro", "me", RelationshipType.SIBLING),
                T.rel("bro", "broWife", RelationshipType.SPOUSE)
            ),
            mapOf("bro" to PartialDate(1985, 1, 1), "me" to PartialDate(1988, 1, 1))
        )
        check(g, "me", "broWife", "哥哥的妻子", "嫂子")
    }

    // ---------- 配偶的兄弟姐妹 ----------

    /** 我(observer) + 配偶 + 配偶的兄弟/姐妹（order = 配偶兄弟姐妹相对配偶）。 */
    private fun spouseSiblingGraph(
        observerMale: Boolean,
        order: RelativeAgeOrder?
    ): FamilyGraph {
        val persons = listOf(
            T.p("me", if (observerMale) Gender.MALE else Gender.FEMALE, self = true),
            T.p("sp", if (observerMale) Gender.FEMALE else Gender.MALE),
            T.p("spBro", Gender.MALE), T.p("spSis", Gender.FEMALE)
        )
        val rels = listOf(
            T.rel("me", "sp", RelationshipType.SPOUSE),
            T.rel("spBro", "sp", RelationshipType.SIBLING, order),
            T.rel("spSis", "sp", RelationshipType.SIBLING, order)
        )
        return T.graph(persons, rels)
    }

    @Test
    fun `妻子的兄弟-大舅子小舅子`() {
        check(spouseSiblingGraph(true, RelativeAgeOrder.OLDER), "me", "spBro",
            "妻子的哥哥", "大舅子")
        check(spouseSiblingGraph(true, RelativeAgeOrder.YOUNGER), "me", "spBro",
            "妻子的弟弟", "小舅子")
    }

    @Test
    fun `妻子的姐妹-大姨子小姨子`() {
        check(spouseSiblingGraph(true, RelativeAgeOrder.OLDER), "me", "spSis",
            "妻子的姐姐", "大姨子")
        check(spouseSiblingGraph(true, RelativeAgeOrder.YOUNGER), "me", "spSis",
            "妻子的妹妹", "小姨子")
    }

    @Test
    fun `丈夫的兄弟-大伯子小叔子`() {
        check(spouseSiblingGraph(false, RelativeAgeOrder.OLDER), "me", "spBro",
            "丈夫的哥哥", "大伯子")
        check(spouseSiblingGraph(false, RelativeAgeOrder.YOUNGER), "me", "spBro",
            "丈夫的弟弟", "小叔子")
    }

    @Test
    fun `丈夫的姐妹-大姑子小姑子`() {
        check(spouseSiblingGraph(false, RelativeAgeOrder.OLDER), "me", "spSis",
            "丈夫的姐姐", "大姑子")
        check(spouseSiblingGraph(false, RelativeAgeOrder.YOUNGER), "me", "spSis",
            "丈夫的妹妹", "小姑子")
    }

    @Test
    fun `配偶兄弟姐妹长幼未知-PARTIAL`() {
        check(spouseSiblingGraph(true, null), "me", "spBro",
            "妻子的兄弟", null, Confidence.PARTIAL,
            setOf(MissingInformation.SIBLING_AGE_ORDER))
    }

    @Test
    fun `配偶兄弟姐妹长幼相同-无称谓 EXACT`() {
        check(spouseSiblingGraph(true, RelativeAgeOrder.SAME), "me", "spBro",
            "妻子的兄弟", null)
    }

    // ---------- 子女的下一代 ----------

    private fun childKinGraph(): FamilyGraph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("son", Gender.MALE), T.p("daughter", Gender.FEMALE),
            T.p("sonWife", Gender.FEMALE), T.p("dauHusb", Gender.MALE),
            T.p("gs", Gender.MALE), T.p("gd", Gender.FEMALE),
            T.p("wgs", Gender.MALE), T.p("wgd", Gender.FEMALE)
        ),
        listOf(
            T.rel("me", "son", RelationshipType.PARENT),
            T.rel("me", "daughter", RelationshipType.PARENT),
            T.rel("son", "sonWife", RelationshipType.SPOUSE),
            T.rel("daughter", "dauHusb", RelationshipType.SPOUSE),
            T.rel("son", "gs", RelationshipType.PARENT),
            T.rel("son", "gd", RelationshipType.PARENT),
            T.rel("daughter", "wgs", RelationshipType.PARENT),
            T.rel("daughter", "wgd", RelationshipType.PARENT)
        )
    )

    @Test
    fun `儿媳女婿与孙辈`() {
        val g = childKinGraph()
        check(g, "me", "sonWife", "儿子的妻子", "儿媳")
        check(g, "me", "dauHusb", "女儿的丈夫", "女婿")
        check(g, "me", "gs", "儿子的儿子", "孙子")
        check(g, "me", "gd", "儿子的女儿", "孙女")
        check(g, "me", "wgs", "女儿的儿子", "外孙")
        check(g, "me", "wgd", "女儿的女儿", "外孙女")
    }

    @Test
    fun `侄甥`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("bro", Gender.MALE), T.p("sis", Gender.FEMALE),
                T.p("zhizi", Gender.MALE), T.p("zhinv", Gender.FEMALE),
                T.p("waisheng", Gender.MALE), T.p("waishengnv", Gender.FEMALE)
            ),
            listOf(
                T.rel("bro", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
                T.rel("sis", "me", RelationshipType.SIBLING, RelativeAgeOrder.OLDER),
                T.rel("bro", "zhizi", RelationshipType.PARENT),
                T.rel("bro", "zhinv", RelationshipType.PARENT),
                T.rel("sis", "waisheng", RelationshipType.PARENT),
                T.rel("sis", "waishengnv", RelationshipType.PARENT)
            )
        )
        check(g, "me", "zhizi", "哥哥的儿子", "侄子")
        check(g, "me", "zhinv", "哥哥的女儿", "侄女")
        check(g, "me", "waisheng", "姐姐的儿子", "外甥")
        check(g, "me", "waishengnv", "姐姐的女儿", "外甥女")
    }

    // ---------- 父母的兄弟姐妹的配偶 ----------

    private fun uncleAuntInLawGraph(order: RelativeAgeOrder?): FamilyGraph = T.graph(
        listOf(
            T.p("me", Gender.MALE, self = true),
            T.p("dad", Gender.MALE), T.p("mom", Gender.FEMALE),
            T.p("pUncle", Gender.MALE), T.p("pAunt", Gender.FEMALE),
            T.p("mUncle", Gender.MALE), T.p("mAunt", Gender.FEMALE),
            T.p("bomu", Gender.FEMALE),  // 伯父/叔父的妻子
            T.p("gufu", Gender.MALE),    // 姑父
            T.p("jiuma", Gender.FEMALE), // 舅妈
            T.p("yifu", Gender.MALE)     // 姨夫
        ),
        listOf(
            T.rel("dad", "me", RelationshipType.PARENT),
            T.rel("mom", "me", RelationshipType.PARENT),
            T.rel("pUncle", "dad", RelationshipType.SIBLING, order),
            T.rel("pAunt", "dad", RelationshipType.SIBLING, order),
            T.rel("mUncle", "mom", RelationshipType.SIBLING, order),
            T.rel("mAunt", "mom", RelationshipType.SIBLING, order),
            T.rel("pUncle", "bomu", RelationshipType.SPOUSE),
            T.rel("pAunt", "gufu", RelationshipType.SPOUSE),
            T.rel("mUncle", "jiuma", RelationshipType.SPOUSE),
            T.rel("mAunt", "yifu", RelationshipType.SPOUSE)
        )
    )

    @Test
    fun `伯母婶婶`() {
        check(uncleAuntInLawGraph(RelativeAgeOrder.OLDER), "me", "bomu",
            "父亲的哥哥的妻子", "伯母")
        check(uncleAuntInLawGraph(RelativeAgeOrder.YOUNGER), "me", "bomu",
            "父亲的弟弟的妻子", "婶婶")
    }

    @Test
    fun `伯母长幼未知 PARTIAL`() {
        check(uncleAuntInLawGraph(null), "me", "bomu",
            "父亲的兄弟的妻子", null, Confidence.PARTIAL,
            setOf(MissingInformation.SIBLING_AGE_ORDER))
    }

    @Test
    fun `姑父舅妈姨夫`() {
        val g = uncleAuntInLawGraph(RelativeAgeOrder.OLDER)
        check(g, "me", "gufu", "父亲的姐姐的丈夫", "姑父")
        check(g, "me", "jiuma", "母亲的哥哥的妻子", "舅妈")
        check(g, "me", "yifu", "母亲的姐姐的丈夫", "姨夫")
    }

    // ---------- 堂表兄弟姐妹 ----------

    /** 祖父 → 父亲 + 叔叔；叔叔 → 堂兄弟。隐式 SIBLING（无显式记录）。 */
    private fun cousinGraph(
        cousinMale: Boolean,
        myBirth: PartialDate?, cousinBirth: PartialDate?,
        viaMom: Boolean = false
    ): FamilyGraph {
        val persons = mutableListOf(
            T.p("me", Gender.MALE, self = true),
            T.p("cousin", if (cousinMale) Gender.MALE else Gender.FEMALE)
        )
        val rels = mutableListOf<io.github.xiaohaoz1.jiarenxia.core.model.Relationship>()
        if (viaMom) {
            persons += T.p("mom", Gender.FEMALE)
            persons += T.p("mAunt", Gender.FEMALE)
            rels += T.rel("mom", "me", RelationshipType.PARENT)
            rels += T.rel("mAunt", "mom", RelationshipType.SIBLING)
            rels += T.rel("mAunt", "cousin", RelationshipType.PARENT)
        } else {
            persons += T.p("gf", Gender.MALE)
            persons += T.p("dad", Gender.MALE)
            persons += T.p("uncle", Gender.MALE)
            rels += T.rel("gf", "dad", RelationshipType.PARENT)
            rels += T.rel("gf", "uncle", RelationshipType.PARENT)
            rels += T.rel("dad", "me", RelationshipType.PARENT)
            rels += T.rel("uncle", "cousin", RelationshipType.PARENT)
        }
        val birthdays = buildMap {
            myBirth?.let { put("me", it) }
            cousinBirth?.let { put("cousin", it) }
        }
        return T.graph(persons, rels, birthdays)
    }

    @Test
    fun `堂哥堂弟-隐式兄弟路径`() {
        val me1990 = PartialDate(1990, 5, 1)
        check(cousinGraph(true, me1990, PartialDate(1986, 4, 10)),
            "me", "cousin", "父亲的兄弟的儿子", "堂哥")
        check(cousinGraph(true, me1990, PartialDate(1995, 1, 1)),
            "me", "cousin", "父亲的兄弟的儿子", "堂弟")
    }

    @Test
    fun `堂姐堂妹`() {
        val me1990 = PartialDate(1990, 5, 1)
        check(cousinGraph(false, me1990, PartialDate(1986, 4, 10)),
            "me", "cousin", "父亲的兄弟的女儿", "堂姐")
        check(cousinGraph(false, me1990, PartialDate(1995, 1, 1)),
            "me", "cousin", "父亲的兄弟的女儿", "堂妹")
    }

    @Test
    fun `表妹-经母亲的姐妹`() {
        check(cousinGraph(false, PartialDate(1990, 5, 1), PartialDate(1995, 7, 7),
            viaMom = true), "me", "cousin", "母亲的姐妹的女儿", "表妹")
        check(cousinGraph(true, PartialDate(1990, 5, 1), PartialDate(1985, 7, 7),
            viaMom = true), "me", "cousin", "母亲的姐妹的儿子", "表哥")
    }

    @Test
    fun `堂亲生日相同-通用称谓 EXACT`() {
        val same = PartialDate(1990, 5, 1)
        check(cousinGraph(true, same, same), "me", "cousin",
            "父亲的兄弟的儿子", "堂兄弟")
    }

    @Test
    fun `堂亲生日缺失-通用称谓 PARTIAL`() {
        check(cousinGraph(true, PartialDate(1990, 5, 1), null), "me", "cousin",
            "父亲的兄弟的儿子", "堂兄弟",
            Confidence.PARTIAL, setOf(MissingInformation.BIRTH_DATES))
        // 不完整生日同样 PARTIAL
        check(cousinGraph(true, PartialDate(1990, 5, 1),
            PartialDate(null, 4, 10)), "me", "cousin",
            "父亲的兄弟的儿子", "堂兄弟",
            Confidence.PARTIAL, setOf(MissingInformation.BIRTH_DATES))
    }

    // ---------- 连襟 / 妯娌 ----------

    @Test
    fun `连襟`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.MALE, self = true),
                T.p("wife", Gender.FEMALE),
                T.p("wifeSis", Gender.FEMALE),
                T.p("lianjin", Gender.MALE)
            ),
            listOf(
                T.rel("me", "wife", RelationshipType.SPOUSE),
                T.rel("wifeSis", "wife", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
                T.rel("wifeSis", "lianjin", RelationshipType.SPOUSE)
            )
        )
        check(g, "me", "lianjin", "妻子的妹妹的丈夫", "连襟")
    }

    @Test
    fun `妯娌`() {
        val g = T.graph(
            listOf(
                T.p("me", Gender.FEMALE, self = true),
                T.p("husb", Gender.MALE),
                T.p("husbBro", Gender.MALE),
                T.p("jieli", Gender.FEMALE)
            ),
            listOf(
                T.rel("me", "husb", RelationshipType.SPOUSE),
                T.rel("husbBro", "husb", RelationshipType.SIBLING, RelativeAgeOrder.YOUNGER),
                T.rel("husbBro", "jieli", RelationshipType.SPOUSE)
            )
        )
        check(g, "me", "jieli", "丈夫的弟弟的妻子", "妯娌")
    }
}
