package io.github.xiaohaoz1.jiarenxia.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.ConscryptMode

/** 按双边祖辈旁支模板生成四代合成家庭，并验证完整加密备份往返。 */
@ConscryptMode(ConscryptMode.Mode.OFF)
class FourGenerationFamilyBackupTest : RepoTestBase() {
    private val generations = mutableMapOf<String, Int>()

    /** 创建带有合成来源及代际标记的人物，不包含任何真实个人资料。 */
    private fun draft(name: String, male: Boolean, generation: Int) = PersonDraft(
        name = name,
        gender = if (male) Gender.MALE else Gender.FEMALE,
        note = "合成测试人物；第${generation}代；不对应真人。",
        birthdays = PersonBirthdays(solar = BirthdayValue(
            PartialDate(listOf(1940, 1965, 1990, 2018)[generation - 1], 5, 12)
        ))
    )

    /** 新建亲属并记录代际，所有写入均经过真实仓库校验。 */
    private suspend fun relative(
        name: String, male: Boolean, generation: Int, anchor: String, role: RelationRole
    ): String = repo.createRelative(draft(name, male, generation), anchor, role).ok()
        .also { generations[it] = generation }

    /** 补齐配偶，返回男性、女性顺序的夫妻。 */
    private suspend fun couple(
        person: String, male: Boolean, spouseName: String, generation: Int
    ): Pair<String, String> {
        val spouse = relative(spouseName, !male, generation, person,
            if (male) RelationRole.WIFE else RelationRole.HUSBAND)
        return if (male) person to spouse else spouse to person
    }

    /** 同时连接双亲，避免把母亲或父亲留成仅有婚姻关系的孤支。 */
    private suspend fun child(
        parents: Pair<String, String>, name: String, male: Boolean, generation: Int
    ): String {
        val role = if (male) RelationRole.SON else RelationRole.DAUGHTER
        val id = relative(name, male, generation, parents.first, role)
        repo.addRelationship(parents.second, id, role).ok()
        return id
    }

    @Test
    fun `四代双边旁支家庭可导出校验并恢复`() = runTest {
        val self = repo.createSelf(draft("沈明远", true, 3)).ok()
        generations[self] = 3
        val us = couple(self, true, "林知夏", 3)
        val father = relative("沈建安", true, 2, self, RelationRole.FATHER)
        val parents = couple(father, true, "许文静", 2)
        repo.addRelationship(self, parents.second, RelationRole.MOTHER).ok()
        val grandpa = relative("沈怀山", true, 1, father, RelationRole.FATHER)
        val grandparents = couple(grandpa, true, "周淑兰", 1)
        repo.addRelationship(father, grandparents.second, RelationRole.MOTHER).ok()
        val maternalGrandpa = relative("许松年", true, 1, parents.second, RelationRole.FATHER)
        val maternal = couple(maternalGrandpa, true, "方桂芳", 1)
        repo.addRelationship(parents.second, maternal.second, RelationRole.MOTHER).ok()
        val aunt = child(grandparents, "沈雅琴", false, 2)
        val auntCouple = couple(aunt, false, "郑志成", 2)
        val cousin = child(auntCouple, "郑书宁", false, 3)
        child(couple(cousin, false, "顾云舟", 3), "顾小禾", false, 4)
        couple(child(maternal, "许文博", true, 2), true, "陶静宜", 2)
        child(us, "沈星河", true, 4)
        child(us, "沈星月", false, 4)

        // 爷爷的四个亲兄弟：每支补设两个儿子，每个儿子有一儿一女；第三代成家各育两孩。
        for (branch in 1..4) {
            val elder = relative("沈怀${"松柏竹梅"[branch - 1]}", true, 1,
                grandpa, RelationRole.BROTHER)
            val elders = couple(elder, true, "梁淑${"琴棋书画"[branch - 1]}", 1)
            for (son in 1..2) {
                val stem = "${"东南西北"[branch - 1]}${"安宁"[son - 1]}"
                val second = couple(child(elders, "沈建$stem", true, 2), true, "苏美$stem", 2)
                for (index in 1..2) {
                    val male = index == 1
                    val third = child(second, "沈${stem}${if (male) "轩" else "萱"}", male, 3)
                    val family = couple(third, male,
                        "${if (male) "唐" else "陆"}${stem}${if (male) "晴" else "朗"}", 3)
                    val surname = if (male) "沈" else "陆"
                    val tag = "$stem${if (male) "嘉" else "佳"}"
                    child(family, "$surname${tag}乐", true, 4)
                    child(family, "$surname${tag}悦", false, 4)
                }
            }
        }

        val wifeFather = relative("林志诚", true, 2, us.second, RelationRole.FATHER)
        val wifeParents = couple(wifeFather, true, "叶舒云", 2)
        repo.addRelationship(us.second, wifeParents.second, RelationRole.MOTHER).ok()
        val wifeGrandpa = relative("林敬山", true, 1, wifeFather, RelationRole.FATHER)
        val wifeGrandparents = couple(wifeGrandpa, true, "何秀珍", 1)
        repo.addRelationship(wifeFather, wifeGrandparents.second, RelationRole.MOTHER).ok()
        // 配偶的亲姑姑、亲叔叔：均挂在同一对祖父母下，补设配偶、孩子及第四代。
        val wifeAunt = child(wifeGrandparents, "林雅芝", false, 2)
        val wifeUncle = child(wifeGrandparents, "林志谦", true, 2)
        val wifeCousin = child(couple(wifeAunt, false, "程文清", 2), "程晓露", false, 3)
        child(couple(wifeCousin, false, "江亦晨", 3), "江小雨", false, 4)
        val wifeCousin2 = child(couple(wifeUncle, true, "宋婉宁", 2), "林晓峰", true, 3)
        child(couple(wifeCousin2, true, "杜清妍", 3), "林小川", true, 4)

        // 配偶爷爷的三个亲兄弟：严格一儿一女，儿女均成家，向下延伸至第四代。
        for (branch in 1..3) {
            val stem = "海川泉"[branch - 1]
            val elder = relative("林敬$stem", true, 1, wifeGrandpa, RelationRole.BROTHER)
            val elders = couple(elder, true, "吴秀$stem", 1)
            for (index in 1..2) {
                val male = index == 1
                val second = child(elders, "林${stem}${if (male) "文" else "兰"}", male, 2)
                val family = couple(second, male, "${if (male) "蒋" else "秦"}${stem}清", 2)
                val surname = if (male) "林" else "秦"
                val third = child(family, "$surname${stem}晓${if (male) "阳" else "月"}", male, 3)
                val thirdCouple = couple(third, male,
                    "${if (male) "白" else "贺"}${stem}思宁", 3)
                child(thirdCouple, "${if (male) "林" else "贺"}${stem}小满", !male, 4)
            }
        }

        assertEquals(mapOf(1 to 20, 2 to 40, 3 to 52, 4 to 43),
            generations.values.groupingBy { it }.eachCount())
        val people = db.personDao().getAll()
        val relationships = db.relationshipDao().getAll()
        assertEquals(155, people.size)
        assertEquals(155, people.map { it.name }.toSet().size)
        assertEquals(1, people.count { it.isSelf })
        val siblings = relationships.filter { it.type == RelationshipType.SIBLING }
        assertEquals(7, siblings.size)
        assertEquals(4, siblings.count { it.sourcePersonId == grandpa || it.targetPersonId == grandpa })
        assertEquals(3, siblings.count { it.sourcePersonId == wifeGrandpa || it.targetPersonId == wifeGrandpa })
        val reached = mutableSetOf(self)
        do {
            val before = reached.size
            relationships.forEach { edge ->
                if (edge.sourcePersonId in reached || edge.targetPersonId in reached) {
                    reached += edge.sourcePersonId
                    reached += edge.targetPersonId
                }
            }
        } while (reached.size > before)
        assertEquals(people.map { it.id }.toSet(), reached)
        val parentLinks = relationships.filter { it.type == RelationshipType.PARENT }
        parentLinks.forEach { edge ->
            assertEquals(generations.getValue(edge.sourcePersonId) + 1,
                generations.getValue(edge.targetPersonId))
        }
        parentLinks.groupBy { it.targetPersonId }.values.forEach { assertEquals(2, it.size) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val backup = BackupManager(context, db, FakeSettingsRepository())
        val file = File(context.cacheDir, "jrx_four_generation_family.jrxb")
        file.outputStream().use { backup.export(it, TestFamily.PASSWORD.toCharArray()) }
        val plan = file.inputStream().use {
            backup.inspect(it, TestFamily.PASSWORD.toCharArray()).getOrThrow()
        }
        assertEquals(155, plan.summary.persons)
        assertEquals(relationships.size, plan.summary.relationships)
        backup.restore(plan)
        assertEquals(people.toSet(), db.personDao().getAll().toSet())
        assertEquals(relationships.toSet(), db.relationshipDao().getAll().toSet())
        if (System.getProperty("jrx.testFamily") == "true") {
            val out = File(requireNotNull(File(requireNotNull(System.getProperty("user.dir"))).parentFile),
                "testdata/jrx_four_generation_family.jrxb")
            requireNotNull(out.parentFile).mkdirs()
            file.copyTo(out, overwrite = true)
            assertTrue(out.length() > 0)
        }
    }
}
