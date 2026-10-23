package io.github.xiaohaoz1.jiarenxia.data.tap

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.data.FakeSettingsRepository
import io.github.xiaohaoz1.jiarenxia.data.PersonDraft
import io.github.xiaohaoz1.jiarenxia.data.RepoTestBase
import io.github.xiaohaoz1.jiarenxia.data.backup.BackupManager
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.clearTables
import io.github.xiaohaoz1.jiarenxia.domain.merge.SpouseMerge
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 碰一碰配对存储的 Room 实现（v9）：
 * 读写往返、整表清空级联、importMerge 同事务原子性。
 */
class RoomSpousePairingStoreTest : RepoTestBase() {

    private lateinit var ctx: Context
    private lateinit var store: RoomSpousePairingStore
    private lateinit var backup: BackupManager

    @Before
    fun setUpStore() {
        ctx = ApplicationProvider.getApplicationContext()
        store = RoomSpousePairingStore(db)
        backup = BackupManager(ctx, db, FakeSettingsRepository())
    }

    private suspend fun seedCouple(): Pair<String, String> {
        val me = createSelf("我")
        val spouse = repo.createRelative(
            PersonDraft("老伴", Gender.FEMALE), me, RelationRole.WIFE).ok()
        return me to spouse
    }

    // ---------- 基本读写 ----------

    @Test
    fun 配对记录与映射读写往返() = runTest {
        val (me, spouse) = seedCouple()
        assertNull(store.current())
        store.recordMerge(
            pairingId = "ab12", peerSelfId = "r-self",
            localSpouseId = spouse, peerName = "老伴",
            mapping = mapOf("r-self" to spouse, "r-me" to me),
            sessionId = 7, payloadSha = "ff", isReader = true)

        val rec = store.current()!!
        assertEquals("ab12", rec.pairingId)
        assertEquals("r-self", rec.peerSelfId)
        assertEquals(spouse, rec.localSpouseId)
        assertEquals("老伴", rec.peerName)
        assertEquals(7, rec.lastSessionId)
        assertEquals("ff", rec.lastPayloadSha)
        assertEquals(true, rec.lastRoleReader)
        assertEquals(
            mapOf("r-self" to spouse, "r-me" to me), store.personMap())
        // 设备级偏好随合并成功写入
        assertEquals(true, store.preferredRole())
    }

    @Test
    fun 同配对再记录_映射增量合并() = runTest {
        val (me, spouse) = seedCouple()
        store.recordMerge("p1", "r-self", spouse, "老伴",
            mapOf("r-self" to spouse), null, null, true)
        store.recordMerge("p1", "r-self", spouse, "老伴",
            mapOf("r-new" to me), null, null, false)
        // 同一配对：映射合并；角色记忆更新为本次
        assertEquals(
            mapOf("r-self" to spouse, "r-new" to me), store.personMap())
        assertEquals(false, store.current()!!.lastRoleReader)
        assertEquals(false, store.preferredRole())
    }

    @Test
    fun 换配对则旧映射整体作废() = runTest {
        val (me, spouse) = seedCouple()
        store.recordMerge("p1", "r-self", spouse, "老伴",
            mapOf("r-self" to spouse), null, null, true)
        store.recordMerge("p2", "r-self", spouse, "老伴",
            mapOf("r2-self" to spouse), null, null, true)
        assertEquals(mapOf("r2-self" to spouse), store.personMap())
        assertEquals("p2", store.current()!!.pairingId)
    }

    @Test
    fun 清空后配对映射设备偏好全没了() = runTest {
        val (_, spouse) = seedCouple()
        store.recordMerge("p1", "r-self", spouse, "老伴",
            mapOf("r-self" to spouse), null, null, true)
        store.clear()
        assertNull(store.current())
        assertTrue(store.personMap().isEmpty())
        assertNull(store.preferredRole())
        assertNull(db.tapPairingDao().getDevice())
    }

    // ---------- 与家庭资料的同事务/级联 ----------

    @Test
    fun 合并与配对写入同一事务_配对失败则家人也不落() = runTest {
        seedCouple()
        val plan = SpouseMerge.MergePlan(
            idMap = emptyMap(),
            newPersons = listOf(
                PersonEntity(
                    id = "newP", name = "新人", gender = Gender.FEMALE,
                    avatarPath = null, note = null,
                    livingStatus = LivingStatus.ALIVE, isSelf = false,
                    createdAt = 0, updatedAt = 0)),
            newRelationships = emptyList(), newPhones = emptyList(),
            newDates = emptyList(), mergedNames = emptyList(),
            mapping = emptyMap(), conflicts = emptyList())
        try {
            backup.importMerge(plan) {
                // 事务内写配对时失败：家人插入必须一起回滚
                store.recordMerge("p1", "r-self", "noSuch", "x",
                    emptyMap(), null, null, true)
            }
            fail("应当抛出")
        } catch (e: Exception) {
            // 预期：FK 约束失败回滚整个事务
        }
        assertNull(db.personDao().getById("newP"))
        assertNull(store.current())
        assertTrue(store.personMap().isEmpty())
    }

    @Test
    fun 合并与配对写入同一事务_成功则一起落() = runTest {
        val (me, spouse) = seedCouple()
        val plan = SpouseMerge.MergePlan(
            idMap = mapOf("r-self" to spouse),
            newPersons = emptyList(), newRelationships = emptyList(),
            newPhones = emptyList(), newDates = emptyList(),
            mergedNames = emptyList(),
            mapping = mapOf("r-self" to spouse, "r-me" to me),
            conflicts = emptyList())
        backup.importMerge(plan) {
            store.recordMerge("p1", "r-self", spouse, "老伴",
                plan.mapping, 9, "aa", true)
        }
        assertEquals("p1", store.current()!!.pairingId)
        assertEquals(mapOf("r-self" to spouse, "r-me" to me),
            store.personMap())
    }

    @Test
    fun 清空家庭资料级联清配对但保留设备偏好() = runTest {
        val (_, spouse) = seedCouple()
        store.recordMerge("p1", "r-self", spouse, "老伴",
            mapOf("r-self" to spouse), null, null, true)
        // 备份恢复/共享导入/清空数据的整表清空路径
        db.withTransaction { db.clearTables() }
        assertNull(store.current())
        assertTrue(store.personMap().isEmpty())
        // 设备级连接偏好是本机硬件设置，随清表保留
        assertEquals(true, store.preferredRole())
    }
}
