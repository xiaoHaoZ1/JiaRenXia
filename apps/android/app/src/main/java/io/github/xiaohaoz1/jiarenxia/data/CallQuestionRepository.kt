package io.github.xiaohaoz1.jiarenxia.data

import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.CallQuestionEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * 「打电话要问的事」仓库（FAMILY_AFFAIRS 26 第 7 批）。
 * 只存用户自己添加的内容；写入统一走 db.withTransaction，
 * 文字 trim 后 1–40 字、每人至多 [MAX_PER_PERSON] 条。
 */
interface CallQuestionRepository {
    fun observeForPerson(personId: String): Flow<List<CallQuestionEntity>>
    fun observeAll(): Flow<List<CallQuestionEntity>>
    suspend fun getForPerson(personId: String): List<CallQuestionEntity>
    /** 新增一条；成功返回新行 id。 */
    suspend fun add(
        personId: String, text: String, keep: Boolean
    ): FamilyResult<String>
    /** 改文字与/或 keep（null 表示不改该项）。 */
    suspend fun update(
        id: String, text: String?, keep: Boolean?
    ): FamilyResult<Unit>
    suspend fun delete(id: String)
    /** 批量删除（“联系上了”勾选划掉用）。 */
    suspend fun deleteIds(ids: Collection<String>)

    companion object {
        /** 每人至多记的条数。 */
        const val MAX_PER_PERSON = 10
        /** 单条文字上限（trim 后 1–40 字）。 */
        const val MAX_TEXT = 40
    }
}

@Singleton
class CallQuestionRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource
) : CallQuestionRepository {

    private val dao get() = db.callQuestionDao()

    override fun observeForPerson(personId: String) = dao.observeForPerson(personId)

    override fun observeAll() = dao.observeAll()

    override suspend fun getForPerson(personId: String) = dao.getForPerson(personId)

    override suspend fun add(
        personId: String, text: String, keep: Boolean
    ): FamilyResult<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > CallQuestionRepository.MAX_TEXT) {
            return FamilyResult.Failure(FamilyError.CallQuestionTextInvalid)
        }
        // 人物存在性、条数上限与插入同事务；并发删人/并发添加都收敛到明确错误
        return try {
            db.withTransaction {
                if (db.personDao().getById(personId) == null) {
                    FamilyResult.Failure(FamilyError.PersonNotFound)
                } else if (dao.countForPerson(personId) >=
                    CallQuestionRepository.MAX_PER_PERSON
                ) {
                    FamilyResult.Failure(FamilyError.CallQuestionLimitReached)
                } else {
                    val id = ids.newId()
                    val now = clock.now()
                    dao.insert(
                        CallQuestionEntity(
                            id = id, personId = personId, text = trimmed,
                            keep = keep, createdAt = now, updatedAt = now
                        )
                    )
                    FamilyResult.Success(id)
                }
            }
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            FamilyResult.Failure(FamilyError.PersonNotFound)
        }
    }

    override suspend fun update(
        id: String, text: String?, keep: Boolean?
    ): FamilyResult<Unit> {
        val trimmed = text?.trim()
        if (trimmed != null &&
            (trimmed.isEmpty() || trimmed.length > CallQuestionRepository.MAX_TEXT)
        ) {
            return FamilyResult.Failure(FamilyError.CallQuestionTextInvalid)
        }
        return db.withTransaction {
            val existing = dao.get(id)
            if (existing == null) {
                FamilyResult.Failure(FamilyError.CallQuestionNotFound)
            } else {
                dao.update(
                    id,
                    trimmed ?: existing.text,
                    keep ?: existing.keep,
                    clock.now()
                )
                FamilyResult.Success(Unit)
            }
        }
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun deleteIds(ids: Collection<String>) {
        if (ids.isNotEmpty()) dao.deleteIds(ids.toList())
    }
}
