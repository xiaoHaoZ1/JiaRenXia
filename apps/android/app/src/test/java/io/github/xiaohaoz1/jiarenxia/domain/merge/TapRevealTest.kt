package io.github.xiaohaoz1.jiarenxia.domain.merge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.kinship.KinshipEngine
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.FamilyResult
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar
import io.github.xiaohaoz1.jiarenxia.ui.tap.buildTapPhone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 合心名单：从“我”的视角，对方家人带着新称谓飘进半颗心
 * （IcuLunarCalendar 用 android.icu → 需要 Robolectric）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TapRevealTest {

    private val engine = KinshipEngine()
    private val lunar = IcuLunarCalendar()
    private val phones = mutableListOf<io.github.xiaohaoz1.jiarenxia.ui.tap.TapPhone>()

    @org.junit.After
    fun closeDbs() = phones.forEach { it.db.close() }

    private suspend fun phone(
        name: String, gender: Gender, father: String, mother: String
    ) = buildTapPhone(name, gender, father, mother).also { phones.add(it) }

    private fun <T> FamilyResult<T>.okOrFail(): T =
        (this as? FamilyResult.Success)?.value ?: error("seed failed: $this")

    @Test
    fun 男方视角妻子一家带新称谓() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val maleSnap = BackupManager(ctx, male.db, FakeSettingsRepository())
            .localMergeSnapshot()
        val femaleSnap = BackupManager(ctx, female.db, FakeSettingsRepository())
            .localMergeSnapshot()
        val myId = male.db.personDao().getSelf()!!.id
        val peerSelf = female.db.personDao().getSelf()!!.id
        val plan = (SpouseMerge.plan(
            maleSnap, femaleSnap, myId, peerSelf)
                as SpouseMerge.Result.Ok).plan

        val labels = TapReveal.labels(
            maleSnap, plan, myId, engine, lunar)
        assertEquals(3, labels.size)
        // 配偶在最前
        assertEquals("妻子", labels[0].title)
        assertEquals("李雪", labels[0].name)
        assertEquals(
            setOf("岳父" to "李国强", "岳母" to "周淑芬"),
            labels.drop(1).map { it.title to it.name }.toSet())
    }

    @Test
    fun 女方视角丈夫一家带新称谓() = runTest {
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val maleSnap = BackupManager(ctx, male.db, FakeSettingsRepository())
            .localMergeSnapshot()
        val femaleSnap = BackupManager(ctx, female.db, FakeSettingsRepository())
            .localMergeSnapshot()
        val myId = female.db.personDao().getSelf()!!.id
        val peerSelf = male.db.personDao().getSelf()!!.id
        val plan = (SpouseMerge.plan(
            femaleSnap, maleSnap, myId, peerSelf)
                as SpouseMerge.Result.Ok).plan

        val labels = TapReveal.labels(
            femaleSnap, plan, myId, engine, lunar)
        assertEquals(3, labels.size)
        assertEquals("丈夫", labels[0].title)
        assertEquals("杨明", labels[0].name)
        assertEquals(
            setOf("公公" to "杨建国", "婆婆" to "王秀兰"),
            labels.drop(1).map { it.title to it.name }.toSet())
    }

    @Test
    fun 祖辈也带标准称谓进合心名单() = runTest {
        // 妻子家再添一位母亲的父亲：从男方看是“姥爷”
        val male = phone("杨明", Gender.MALE, "杨建国", "王秀兰")
        val female = phone("李雪", Gender.FEMALE, "李国强", "周淑芬")
        val grandmaDad = female.db.personDao().getAll()
            .first { it.name == "周淑芬" }.id
        female.repo.createRelative(
            PersonDraft("刘福生", Gender.MALE), grandmaDad,
            RelationRole.FATHER).okOrFail()

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val maleSnap = BackupManager(ctx, male.db, FakeSettingsRepository())
            .localMergeSnapshot()
        val femaleSnap = BackupManager(ctx, female.db, FakeSettingsRepository())
            .localMergeSnapshot()
        val myId = male.db.personDao().getSelf()!!.id
        val peerSelf = female.db.personDao().getSelf()!!.id
        val plan = (SpouseMerge.plan(
            maleSnap, femaleSnap, myId, peerSelf)
                as SpouseMerge.Result.Ok).plan

        val labels = TapReveal.labels(
            maleSnap, plan, myId, engine, lunar)
        val grandpa = labels.firstOrNull { it.name == "刘福生" }
        assertTrue("姥爷应出现在合心名单", grandpa != null)
        assertTrue(grandpa!!.title.isNotBlank())
    }
}
