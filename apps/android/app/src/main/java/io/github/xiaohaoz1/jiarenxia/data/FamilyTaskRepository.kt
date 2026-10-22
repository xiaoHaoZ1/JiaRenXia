package io.github.xiaohaoz1.jiarenxia.data

import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.model.FamilyTask
import io.github.xiaohaoz1.jiarenxia.core.model.TASK_OFFSETS
import io.github.xiaohaoz1.jiarenxia.core.model.TaskKind
import io.github.xiaohaoz1.jiarenxia.core.model.TaskRepeat
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity
import io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity
import io.github.xiaohaoz1.jiarenxia.reminder.TaskSchedule
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 家庭事项草稿（新建 id 为 null；offsets 保存时整组替换）。 */
data class TaskDraft(
    val id: String? = null,
    val personId: String? = null,
    val kind: TaskKind,
    val title: String,
    val anchorDate: LocalDate,
    val repeat: TaskRepeat,
    val amountFen: Long? = null,
    val note: String? = null,
    val enabled: Boolean = true,
    val offsets: Set<Int> = setOf(0),
    /** 一天内提醒时刻（分钟数）；null = 跟随家事提醒时刻设置。 */
    val remindMinutes: Int? = null
)

/**
 * 家庭事项仓库（DATABASE_SCHEMA v4，FAMILY_AFFAIRS 5.5）。
 * 写入统一走 db.withTransaction；offsets 以子表整组替换。
 */
interface FamilyTaskRepository {
    fun observeTasks(): Flow<List<FamilyTask>>
    fun observeTasksForPerson(personId: String): Flow<List<FamilyTask>>
    suspend fun getTasks(): List<FamilyTask>
    suspend fun getTask(id: String): FamilyTask?
    /** 新建（draft.id == null）或更新；返回事项 id。 */
    suspend fun save(draft: TaskDraft): FamilyResult<String>
    /** “办完”：重复事项推进到 today 之后最近一期；不重复置 done。 */
    suspend fun markDone(id: String, today: LocalDate): FamilyResult<Unit>
    /**
     * 撤销“已办”：只回滚办完推进的字段（periodIndex/lastDone/done）。
     * 期间对标题、金额、提醒时间等其他字段的修改不被快照覆盖；
     * 事项已被删除时返回 TaskNotFound，不会重建。
     */
    suspend fun restore(task: FamilyTask): FamilyResult<Unit>
    suspend fun delete(id: String)
}

@Singleton
class FamilyTaskRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource
) : FamilyTaskRepository {

    private val taskDao get() = db.familyTaskDao()
    private val offsetDao get() = db.familyTaskOffsetDao()

    // 观察与读取都走 @Transaction 的两表快照：
    // 不暴露“新事项配旧 offsets”的中间态，拼装也只有一处
    override fun observeTasks(): Flow<List<FamilyTask>> =
        taskDao.observeAllWithOffsets().map { l -> l.map { it.toModel() } }

    override fun observeTasksForPerson(personId: String): Flow<List<FamilyTask>> =
        // 只读该人物名下事项及其 offsets
        taskDao.observeForPersonWithOffsets(personId).map { l -> l.map { it.toModel() } }

    override suspend fun getTasks(): List<FamilyTask> =
        taskDao.getAllWithOffsets().map { it.toModel() }

    override suspend fun getTask(id: String): FamilyTask? =
        taskDao.getWithOffsets(id)?.toModel()

    private fun io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskWithOffsets.toModel() =
        task.toModel(offsets.map { it.daysBefore }.toSet())

    override suspend fun save(draft: TaskDraft): FamilyResult<String> {
        // 纯输入校验不依赖数据库状态，留在事务外
        val title = draft.title.trim()
        if (title.isEmpty() || title.length > 30) return FamilyResult.Failure(FamilyError.InvalidTaskTitle)
        if (draft.offsets.isEmpty() || !TASK_OFFSETS.containsAll(draft.offsets)) {
            return FamilyResult.Failure(FamilyError.InvalidReminderOffsets)
        }
        if (draft.anchorDate.year !in 1900..2100) {
            return FamilyResult.Failure(FamilyError.InvalidTaskDate)
        }
        if (draft.amountFen != null && draft.amountFen !in 0..99_999_999_999L) {
            return FamilyResult.Failure(FamilyError.InvalidTaskAmount)
        }
        if (draft.note != null && draft.note.length > 200) {
            return FamilyResult.Failure(FamilyError.InvalidTaskNote)
        }
        if (draft.remindMinutes != null && draft.remindMinutes !in 0..1439) {
            return FamilyResult.Failure(FamilyError.InvalidTaskTime)
        }
        val now = clock.now()
        // 旧记录读取、关联校验与写入必须在同一事务：
        // 否则事务外读到的 periodIndex/personId 可能是已被并发修改/删除的旧值
        return db.withTransaction {
            val old = draft.id?.let { taskDao.get(it) }
            // 编辑时记录已被删除：不允许 upsert 静默重建
            if (draft.id != null && old == null) {
                return@withTransaction FamilyResult.Failure(FamilyError.TaskNotFound)
            }
            if (draft.personId != null && db.personDao().getById(draft.personId) == null) {
                return@withTransaction FamilyResult.Failure(FamilyError.PersonNotFound)
            }
            val id = draft.id ?: ids.newId()
            taskDao.upsert(
                FamilyTaskEntity(
                    id = id,
                    personId = draft.personId,
                    kind = draft.kind.name,
                    title = title,
                    anchorEpochDay = draft.anchorDate.toEpochDay(),
                    repeat = draft.repeat.name,
                    periodIndex = old?.periodIndex ?: 0,
                    lastDoneEpochDay = old?.lastDoneEpochDay,
                    done = old?.done ?: false,
                    amountFen = draft.amountFen,
                    note = draft.note?.trim()?.ifEmpty { null },
                    enabled = draft.enabled,
                    remindMinutes = draft.remindMinutes,
                    createdAt = old?.createdAt ?: now,
                    updatedAt = now
                )
            )
            offsetDao.deleteForTask(id)
            offsetDao.insertAll(
                draft.offsets.sorted().map {
                    FamilyTaskOffsetEntity(ids.newId(), id, it)
                }
            )
            FamilyResult.Success(id)
        }
    }

    override suspend fun markDone(id: String, today: LocalDate): FamilyResult<Unit> =
        db.withTransaction {
            // 读当前行与写回同事务：与编辑交错时不会用旧 periodIndex 覆盖新值
            val e = taskDao.get(id)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.TaskNotFound)
            val next = TaskSchedule.nextPeriodIndex(e.toModel(emptySet()), today)
            taskDao.upsert(
                e.copy(
                    periodIndex = next ?: e.periodIndex,
                    done = next == null,
                    lastDoneEpochDay = today.toEpochDay(),
                    updatedAt = clock.now()
                )
            )
            FamilyResult.Success(Unit)
        }

    override suspend fun restore(task: FamilyTask): FamilyResult<Unit> =
        db.withTransaction {
            // 以事务内当前行为准：目标已删除不重建；
            // 只把“办完”推进的字段写回，期间的其他编辑保留
            val cur = taskDao.get(task.id)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.TaskNotFound)
            taskDao.upsert(
                cur.copy(
                    periodIndex = task.periodIndex,
                    lastDoneEpochDay = task.lastDoneDate?.toEpochDay(),
                    done = task.done,
                    updatedAt = clock.now()
                )
            )
            FamilyResult.Success(Unit)
        }

    override suspend fun delete(id: String) {
        taskDao.delete(id)
    }

    private fun FamilyTaskEntity.toModel(offsets: Set<Int>) = FamilyTask(
        id = id,
        personId = personId,
        kind = TaskKind.valueOf(kind),
        title = title,
        anchorDate = LocalDate.ofEpochDay(anchorEpochDay),
        repeat = TaskRepeat.valueOf(repeat),
        periodIndex = periodIndex,
        lastDoneDate = lastDoneEpochDay?.let(LocalDate::ofEpochDay),
        done = done,
        amountFen = amountFen,
        note = note,
        enabled = enabled,
        offsets = offsets,
        remindMinutes = remindMinutes
    )
}
