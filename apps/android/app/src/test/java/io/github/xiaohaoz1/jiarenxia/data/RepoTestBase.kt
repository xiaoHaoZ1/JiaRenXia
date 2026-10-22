package io.github.xiaohaoz1.jiarenxia.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorageImpl
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据层测试基类：真实内存数据库 + 真实 Repository 实现 + 确定性 ID/时间。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class RepoTestBase {

    protected lateinit var db: AppDatabase
    protected lateinit var repo: FamilyRepositoryImpl
    protected lateinit var storage: AvatarStorageImpl
    protected var now: Long = 1_700_000_000_000L
    private var seq = 0

    @Before
    fun baseSetUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        storage = AvatarStorageImpl(context)
        repo = FamilyRepositoryImpl(
            db = db,
            avatarStorage = storage,
            ids = object : IdGenerator { override fun newId() = "id-" + (++seq) },
            clock = object : TimeSource { override fun now() = now },
            lunar = io.github.xiaohaoz1.jiarenxia.domain.lunar.IcuLunarCalendar()
        )
    }

    @After
    fun baseTearDown() {
        db.close()
    }

    protected fun <T> FamilyResult<T>.ok(): T =
        (this as? FamilyResult.Success)?.value
            ?: error("expected Success but was " + (this as? FamilyResult.Failure)?.error)

    protected fun FamilyResult<*>.err(): FamilyError =
        (this as? FamilyResult.Failure)?.error ?: error("expected Failure but was Success")

    protected fun assertErr(result: FamilyResult<*>, expected: FamilyError) {
        assertTrue("expected " + expected + " got " + result, result is FamilyResult.Failure)
        assertEquals(expected, result.err())
        assertEquals(expected.message, (result as FamilyResult.Failure).error.message)
    }

    /** 建立“我”，返回其 id。 */
    protected suspend fun createSelf(name: String = "我"): String =
        repo.createSelf(
            PersonDraft(name = name, gender = io.github.xiaohaoz1.jiarenxia.core.model.Gender.MALE)
        ).ok()

    protected fun rel(src: String, tgt: String, type: io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType) =
        RelationshipEntity("r-" + src + "-" + tgt + "-" + type, src, tgt, type, null, 0, 0)
}
