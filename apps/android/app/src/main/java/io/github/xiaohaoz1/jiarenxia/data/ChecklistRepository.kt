package io.github.xiaohaoz1.jiarenxia.data

import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.model.Checklist
import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistItem
import io.github.xiaohaoz1.jiarenxia.core.model.ChecklistKind
import io.github.xiaohaoz1.jiarenxia.core.model.PregnancyStatus
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.ChecklistEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ChecklistItemEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ChecklistWithItems
import io.github.xiaohaoz1.jiarenxia.domain.checklist.ChecklistTemplates
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** 清单 + 勾选进度（observeForPregnancy 返回）。 */
data class ChecklistProgress(
    val checklist: Checklist,
    val checkedCount: Int,
    val totalCount: Int
)

/** 某人物名下清单条目的完成进度（observeItemsProgressForPerson 返回）。 */
data class ItemsProgress(
    val checklistId: String,
    val doneCount: Int,
    val totalCount: Int
)

/**
 * 清单仓库（FAMILY_AFFAIRS 26：待产包等）。
 * 写入统一走 db.withTransaction；条目顺序 = 模板组顺序 + 组内 sort_order。
 */
interface ChecklistRepository {
    /** 清单 + 条目（按模板组顺序、组内 sort_order 排序）。 */
    fun observe(id: String): Flow<ChecklistWithItemsModel?>
    /** 该孕期该类清单（至多一张）及勾选进度；无清单返回 null。 */
    fun observeForPregnancy(
        pregnancyId: String,
        kind: ChecklistKind
    ): Flow<ChecklistProgress?>
    /** 全部清单及勾选进度（人物详情页孕期卡/历史入口用）。 */
    fun observeAll(): Flow<List<ChecklistProgress>>
    /**
     * 该人物名下某类清单条目的完成进度（POSTNATAL 宝宝卡「出生后要办的事」行；
     * 该人物没有条目时返回 null）。有 task_id 的条目按事项已办计。
     */
    fun observeItemsProgressForPerson(
        personId: String,
        kind: ChecklistKind
    ): Flow<ItemsProgress?>
    suspend fun get(id: String): ChecklistWithItemsModel?
    /**
     * 按模板生成清单：孕期须存在且 ACTIVE；幂等——已有同类清单直接返回其 id。
     * 清单 title = “待产包”，条目 person_id = 孕妈。
     */
    suspend fun createFromTemplate(
        kind: ChecklistKind,
        pregnancyId: String
    ): FamilyResult<String>
    suspend fun setChecked(itemId: String, checked: Boolean): FamilyResult<Unit>
    /**
     * 在该组末尾追加一条；标题 trim 后 1–30 字。
     * [personId]：POSTNATAL 清单按卡（宝宝/妈妈）继承；待产包恒 null。
     */
    suspend fun addItem(
        checklistId: String,
        groupName: String,
        title: String,
        personId: String? = null
    ): FamilyResult<String>
    /**
     * 「定日期」/「再定一次日期」：同一事务新建一条 CUSTOM 事项
     * （不重复、提前 1 天 + 当天、不挂孕期）并把 task_id 写回条目；
     * 返回事项 id。旧事项保留（再定一次日期时替换引用）。
     */
    suspend fun setItemTask(
        itemId: String,
        personId: String?,
        title: String,
        anchorDate: LocalDate
    ): FamilyResult<String>
    suspend fun renameItem(itemId: String, title: String): FamilyResult<Unit>
    suspend fun deleteItem(itemId: String): FamilyResult<Unit>
    suspend fun deleteChecklist(id: String): FamilyResult<Unit>
}

/** observe() 的返回载体：清单 + 已排序条目。 */
data class ChecklistWithItemsModel(
    val checklist: Checklist,
    val items: List<ChecklistItem>
)

@Singleton
class ChecklistRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource
) : ChecklistRepository {

    private val checklistDao get() = db.checklistDao()
    private val itemDao get() = db.checklistItemDao()
    private val taskDao get() = db.familyTaskDao()
    private val taskOffsetDao get() = db.familyTaskOffsetDao()

    /** 模板组顺序索引：未知组排在模板组之后（按名称兜底）。 */
    private fun groupRank(kind: ChecklistKind, groupName: String): Int {
        val order = ChecklistTemplates.groupsOf(kind)
        val i = order.indexOf(groupName)
        return if (i >= 0) i else order.size
    }

    private fun sortItems(
        kind: ChecklistKind,
        items: List<ChecklistItemEntity>
    ): List<ChecklistItem> =
        items.sortedWith(
            compareBy(
                { groupRank(kind, it.groupName) },
                { it.sortOrder },
                { it.id }
            )
        ).map { it.toModel() }

    override fun observe(id: String): Flow<ChecklistWithItemsModel?> =
        combine(
            checklistDao.observe(id),
            itemDao.observeAll()
        ) { e, all ->
            e?.let { ce ->
                val kind = runCatching { ChecklistKind.valueOf(ce.kind) }
                    .getOrDefault(ChecklistKind.BIRTH_BAG)
                ChecklistWithItemsModel(
                    checklist = ce.toModel(),
                    items = sortItems(
                        kind,
                        all.filter { it.checklistId == ce.id }
                    )
                )
            }
        }

    /**
     * 条目的有效完成态：有 task_id 且事项还在 → 以事项是否已办为准；
     * 否则（无日期 / 事项被删）按条目自己的 checked。
     */
    private fun effectiveDone(
        item: ChecklistItemEntity,
        taskById: Map<String, io.github.xiaohaoz1.jiarenxia.data.db
            .FamilyTaskEntity>
    ): Boolean = item.taskId?.let { taskById[it]?.done } ?: item.checked

    override fun observeForPregnancy(
        pregnancyId: String,
        kind: ChecklistKind
    ): Flow<ChecklistProgress?> =
        combine(
            checklistDao.observeForPregnancy(pregnancyId, kind.name),
            itemDao.observeAll(),
            taskDao.observeAll()
        ) { e, all, tasks ->
            e?.let { ce ->
                val taskById = tasks.associateBy { it.id }
                val items = all.filter { it.checklistId == ce.id }
                ChecklistProgress(
                    checklist = ce.toModel(),
                    checkedCount = items.count { effectiveDone(it, taskById) },
                    totalCount = items.size
                )
            }
        }

    override fun observeAll(): Flow<List<ChecklistProgress>> =
        combine(
            checklistDao.observeAll(),
            itemDao.observeAll(),
            taskDao.observeAll()
        ) { rows, all, tasks ->
            val taskById = tasks.associateBy { it.id }
            rows.map { ce ->
                val items = all.filter { it.checklistId == ce.id }
                ChecklistProgress(
                    checklist = ce.toModel(),
                    checkedCount = items.count { effectiveDone(it, taskById) },
                    totalCount = items.size
                )
            }
        }

    override fun observeItemsProgressForPerson(
        personId: String,
        kind: ChecklistKind
    ): Flow<ItemsProgress?> =
        combine(
            checklistDao.observeAll(),
            itemDao.observeAll(),
            taskDao.observeAll()
        ) { rows, all, tasks ->
            val taskById = tasks.associateBy { it.id }
            // 取该人物第一张有条目的同类清单（正常只有一张 POSTNATAL）
            rows.firstNotNullOfOrNull { ce ->
                if (ce.kind != kind.name) return@firstNotNullOfOrNull null
                val items = all.filter {
                    it.checklistId == ce.id && it.personId == personId
                }
                if (items.isEmpty()) return@firstNotNullOfOrNull null
                ItemsProgress(
                    checklistId = ce.id,
                    doneCount = items.count { effectiveDone(it, taskById) },
                    totalCount = items.size
                )
            }
        }

    override suspend fun get(id: String): ChecklistWithItemsModel? {
        val e = checklistDao.get(id) ?: return null
        val kind = runCatching { ChecklistKind.valueOf(e.kind) }
            .getOrDefault(ChecklistKind.BIRTH_BAG)
        return ChecklistWithItemsModel(
            checklist = e.toModel(),
            items = sortItems(kind, itemDao.getForChecklist(id))
        )
    }

    override suspend fun createFromTemplate(
        kind: ChecklistKind,
        pregnancyId: String
    ): FamilyResult<String> {
        val now = clock.now()
        return db.withTransaction {
            val preg = db.pregnancyDao().get(pregnancyId)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.PregnancyNotFound)
            if (preg.status != PregnancyStatus.ACTIVE.name) {
                return@withTransaction FamilyResult.Failure(FamilyError.PregnancyNotFound)
            }
            // 幂等：已有同孕期同类清单直接返回
            checklistDao.getForPregnancy(pregnancyId, kind.name)?.let {
                return@withTransaction FamilyResult.Success(it.id)
            }
            val template = ChecklistTemplates.itemsOf(kind)
            val order = ChecklistTemplates.groupsOf(kind)
            // 每组内 sort_order 从 0 连续编号（跨组也连续，便于整体排序）
            var seq = 0
            val itemEntities = mutableListOf<ChecklistItemEntity>()
            order.forEach { group ->
                template[group].orEmpty().forEach { t ->
                    itemEntities += ChecklistItemEntity(
                        id = ids.newId(),
                        checklistId = "", // 下面统一回填
                        groupName = group,
                        title = t.title,
                        note = t.note,
                        checked = false,
                        sortOrder = seq++,
                        // 待产包条目不关联人物（person_id 恒 null）
                        personId = null,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            }
            val checklistId = ids.newId()
            checklistDao.upsert(
                ChecklistEntity(
                    id = checklistId,
                    kind = kind.name,
                    title = ChecklistTemplates.BIRTH_BAG_TITLE,
                    personId = preg.personId,
                    pregnancyId = pregnancyId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            itemEntities.forEach { itemDao.upsert(it.copy(checklistId = checklistId)) }
            FamilyResult.Success(checklistId)
        }
    }

    private fun validTitle(title: String): Boolean =
        title.trim().length in 1..30

    override suspend fun setChecked(
        itemId: String,
        checked: Boolean
    ): FamilyResult<Unit> = db.withTransaction {
        val e = itemDao.get(itemId)
            ?: return@withTransaction FamilyResult.Failure(FamilyError.ChecklistItemNotFound)
        // 有日期的条目以事项为准，不能直接勾选
        if (e.taskId != null && db.familyTaskDao().get(e.taskId) != null) {
            return@withTransaction FamilyResult.Failure(FamilyError.ChecklistItemLinked)
        }
        itemDao.upsert(e.copy(checked = checked, updatedAt = clock.now()))
        FamilyResult.Success(Unit)
    }

    override suspend fun addItem(
        checklistId: String,
        groupName: String,
        title: String,
        personId: String?
    ): FamilyResult<String> {
        val trimmed = title.trim()
        if (!validTitle(trimmed)) {
            return FamilyResult.Failure(FamilyError.InvalidChecklistTitle)
        }
        return db.withTransaction {
            val ce = checklistDao.get(checklistId)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.ChecklistNotFound)
            val kind = runCatching { ChecklistKind.valueOf(ce.kind) }
                .getOrDefault(ChecklistKind.BIRTH_BAG)
            // 组名必须是该清单模板里的组（未知组不允许手加，防脏数据）
            if (groupName !in ChecklistTemplates.groupsOf(kind)) {
                return@withTransaction FamilyResult.Failure(FamilyError.ChecklistGroupInvalid)
            }
            // personId 有外键：给了就必须是存在的人物（POSTNATAL 按卡继承）
            if (personId != null &&
                db.personDao().getById(personId) == null
            ) {
                return@withTransaction FamilyResult.Failure(FamilyError.PersonNotFound)
            }
            val now = clock.now()
            // 组内追加：sort_order 取全表当前最大 +1。
            // 排序先按模板组序再按 sort_order，组内相对顺序不受影响。
            val nextOrder = (itemDao.getForChecklist(checklistId)
                .maxOfOrNull { it.sortOrder } ?: -1) + 1
            val id = ids.newId()
            itemDao.upsert(
                ChecklistItemEntity(
                    id = id,
                    checklistId = checklistId,
                    groupName = groupName,
                    title = trimmed,
                    note = null,
                    checked = false,
                    sortOrder = nextOrder,
                    personId = personId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            FamilyResult.Success(id)
        }
    }

    override suspend fun setItemTask(
        itemId: String,
        personId: String?,
        title: String,
        anchorDate: LocalDate
    ): FamilyResult<String> = db.withTransaction {
        val e = itemDao.get(itemId)
            ?: return@withTransaction FamilyResult.Failure(FamilyError.ChecklistItemNotFound)
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed.length > 30) {
            return@withTransaction FamilyResult.Failure(FamilyError.InvalidTaskTitle)
        }
        if (personId != null && db.personDao().getById(personId) == null) {
            return@withTransaction FamilyResult.Failure(FamilyError.PersonNotFound)
        }
        val now = clock.now()
        val taskId = ids.newId()
        taskDao.insertAll(
            listOf(
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskEntity(
                    id = taskId,
                    personId = personId,
                    kind = io.github.xiaohaoz1.jiarenxia.core.model.TaskKind.CUSTOM.name,
                    title = trimmed,
                    anchorEpochDay = anchorDate.toEpochDay(),
                    repeat = io.github.xiaohaoz1.jiarenxia.core.model
                        .TaskRepeat.NONE.name,
                    periodIndex = 0,
                    lastDoneEpochDay = null,
                    done = false,
                    amountFen = null,
                    note = null,
                    enabled = true,
                    remindMinutes = null,
                    pregnancyId = null,
                    archived = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        // 提前 1 天 + 当天
        taskOffsetDao.insertAll(
            setOf(0, 1).map {
                io.github.xiaohaoz1.jiarenxia.data.db.FamilyTaskOffsetEntity(
                    id = ids.newId(), taskId = taskId, daysBefore = it
                )
            }
        )
        itemDao.updateTaskId(itemId, taskId, now)
        FamilyResult.Success(taskId)
    }

    override suspend fun renameItem(
        itemId: String,
        title: String
    ): FamilyResult<Unit> {
        val trimmed = title.trim()
        if (!validTitle(trimmed)) {
            return FamilyResult.Failure(FamilyError.InvalidChecklistTitle)
        }
        return db.withTransaction {
            val e = itemDao.get(itemId)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.ChecklistItemNotFound)
            itemDao.upsert(e.copy(title = trimmed, updatedAt = clock.now()))
            FamilyResult.Success(Unit)
        }
    }

    override suspend fun deleteItem(itemId: String): FamilyResult<Unit> =
        db.withTransaction {
            itemDao.delete(itemId)
            FamilyResult.Success(Unit)
        }

    override suspend fun deleteChecklist(id: String): FamilyResult<Unit> =
        db.withTransaction {
            checklistDao.delete(id) // 条目经 CASCADE 删除
            FamilyResult.Success(Unit)
        }

    private fun ChecklistEntity.toModel() = Checklist(
        id = id,
        kind = runCatching { ChecklistKind.valueOf(kind) }
            .getOrDefault(ChecklistKind.BIRTH_BAG),
        title = title,
        personId = personId,
        pregnancyId = pregnancyId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ChecklistItemEntity.toModel() = ChecklistItem(
        id = id,
        checklistId = checklistId,
        groupName = groupName,
        title = title,
        note = note,
        checked = checked,
        sortOrder = sortOrder,
        personId = personId,
        taskId = taskId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
