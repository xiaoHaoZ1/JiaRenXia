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
    /** 在该组末尾追加一条；标题 trim 后 1–30 字。 */
    suspend fun addItem(
        checklistId: String,
        groupName: String,
        title: String
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

    override fun observeForPregnancy(
        pregnancyId: String,
        kind: ChecklistKind
    ): Flow<ChecklistProgress?> =
        combine(
            checklistDao.observeForPregnancy(pregnancyId, kind.name),
            itemDao.observeAll()
        ) { e, all ->
            e?.let { ce ->
                val items = all.filter { it.checklistId == ce.id }
                ChecklistProgress(
                    checklist = ce.toModel(),
                    checkedCount = items.count { it.checked },
                    totalCount = items.size
                )
            }
        }

    override fun observeAll(): Flow<List<ChecklistProgress>> =
        combine(checklistDao.observeAll(), itemDao.observeAll()) { rows, all ->
            rows.map { ce ->
                val items = all.filter { it.checklistId == ce.id }
                ChecklistProgress(
                    checklist = ce.toModel(),
                    checkedCount = items.count { it.checked },
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
                        // 条目 person_id 本批不写（第 3 批按宝宝用）
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
        itemDao.upsert(e.copy(checked = checked, updatedAt = clock.now()))
        FamilyResult.Success(Unit)
    }

    override suspend fun addItem(
        checklistId: String,
        groupName: String,
        title: String
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
                    // 条目 person_id 本批不写（第 3 批按宝宝用）
                    personId = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
            FamilyResult.Success(id)
        }
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
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
