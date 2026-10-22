package io.github.xiaohaoz1.jiarenxia.domain

import io.github.xiaohaoz1.jiarenxia.core.kinship.Confidence
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipKind
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipResult
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.domain.pinyin.PinyinIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PersonSearch 的匹配规则（含拼音；android.icu 需 Robolectric）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonSearchTest {

    private fun ov(name: String, title: String, structural: String) = PersonOverview(
        person = Person(id = name, name = name, gender = Gender.MALE),
        result = KinshipResult(
            "me", name, emptyList(), structural, title,
            confidence = Confidence.EXACT, missingInformation = emptySet(), kind = KinshipKind.DERIVED
        ),
        birthdays = emptyList()
    )

    private val items = listOf(
        ov("我", "我", "我"),
        ov("杨建国", "父亲", "父亲"),
        ov("杨建春", "伯父", "父亲的哥哥"),
        ov("李雪", "配偶", "配偶")
    )

    @Test
    fun 空白查询返回空() {
        assertEquals(emptyList<PersonOverview>(), PersonSearch.filter(items, ""))
        assertEquals(emptyList<PersonOverview>(), PersonSearch.filter(items, "   "))
    }

    @Test
    fun 按姓名匹配() {
        assertEquals(listOf("杨建国", "杨建春"), PersonSearch.filter(items, "杨建").map { it.person.name })
    }

    @Test
    fun 按称谓与结构关系匹配() {
        // “父亲”命中杨建国（称谓）与杨建春（结构关系“父亲的哥哥”）
        assertEquals(listOf("杨建国", "杨建春"), PersonSearch.filter(items, "父亲").map { it.person.name })
        assertEquals(listOf("杨建春"), PersonSearch.filter(items, "哥哥").map { it.person.name })
        assertEquals(listOf("李雪"), PersonSearch.filter(items, "配偶").map { it.person.name })
    }

    @Test
    fun 不排除自己() {
        assertEquals(listOf("我"), PersonSearch.filter(items, "我").map { it.person.name })
    }

    // ---------- 拼音 ----------

    @Test
    fun 拼音全拼与声母() {
        assertEquals(PinyinIndex.Form("yangjianguo", "yjg"), PinyinIndex.of("杨建国"))
        assertEquals(PinyinIndex.Form("wangxiulan", "wxl"), PinyinIndex.of("王秀兰"))
    }

    @Test
    fun 拼音_姓氏读音() {
        // Han-Latin/Names 可用时，“单”作为姓氏读 shan；普通 Han-Latin 读 dan
        if (PinyinIndex.namesAvailable) {
            assertTrue(
                "单 应为 shan 或 dan，实际 " + PinyinIndex.of("单").full,
                PinyinIndex.of("单").full == "shan"
            )
        }
    }

    @Test
    fun 拼音_非汉字原样保留() {
        assertEquals("mama", PinyinIndex.of("Mama").full)
    }

    @Test
    fun 拼音查询_姓名() {
        assertEquals(listOf("杨建国"), PersonSearch.filter(items, "yjg").map { it.person.name })
        assertEquals(listOf("杨建国"), PersonSearch.filter(items, "YJG").map { it.person.name })
        assertEquals(listOf("杨建国", "杨建春"),
            PersonSearch.filter(items, "yangjian").map { it.person.name })
    }

    @Test
    fun 拼音查询_称谓() {
        // “伯父”：全拼 bofu / 声母 bf 都命中杨建春
        assertEquals(listOf("杨建春"), PersonSearch.filter(items, "bofu").map { it.person.name })
        assertEquals(listOf("杨建春"), PersonSearch.filter(items, "bf").map { it.person.name })
    }

    @Test
    fun 中文与无命中保持原语义() {
        assertEquals(listOf("杨建国", "杨建春"), PersonSearch.filter(items, "杨").map { it.person.name })
        assertEquals(emptyList<PersonOverview>(), PersonSearch.filter(items, "xyz"))
    }
}
