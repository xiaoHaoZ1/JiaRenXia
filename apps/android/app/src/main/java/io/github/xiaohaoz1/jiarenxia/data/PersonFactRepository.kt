package io.github.xiaohaoz1.jiarenxia.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.model.FACT_CONTENT_MAX
import io.github.xiaohaoz1.jiarenxia.core.model.FACT_LABEL_MAX
import io.github.xiaohaoz1.jiarenxia.core.model.FactCategory
import io.github.xiaohaoz1.jiarenxia.core.model.PersonFact
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.PersonFactEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 人物「关于TA」私密记录仓库。 */
interface PersonFactRepository {
    fun observeForPerson(personId: String): Flow<List<PersonFact>>
    suspend fun add(
        personId: String,
        category: FactCategory,
        label: String?,
        content: String
    ): FamilyResult<Unit>
    suspend fun update(id: String, label: String?, content: String): FamilyResult<Unit>
    suspend fun delete(id: String)
}

/** Room 实现；读写独立于家人共享与合并。 */
@Singleton
class PersonFactRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val ids: IdGenerator,
    private val clock: TimeSource
) : PersonFactRepository {
    private val dao get() = db.personFactDao()

    override fun observeForPerson(personId: String): Flow<List<PersonFact>> =
        dao.observeForPerson(personId).map { rows -> rows.map { it.toModel() } }

    override suspend fun add(
        personId: String,
        category: FactCategory,
        label: String?,
        content: String
    ): FamilyResult<Unit> {
        val normalizedContent = normalizeContent(content)
            ?: return FamilyResult.Failure(FamilyError.FactContentRequired)
        val normalizedLabel = normalizeLabel(category, label)
            ?: if (category.needsLabel) {
                return FamilyResult.Failure(FamilyError.FactLabelRequired)
            } else null
        return try {
            db.withTransaction {
                if (db.personDao().getById(personId) == null) {
                    return@withTransaction FamilyResult.Failure(FamilyError.PersonNotFound)
                }
                val now = clock.now()
                dao.insert(
                    PersonFactEntity(
                        id = ids.newId(),
                        personId = personId,
                        category = category.name,
                        label = normalizedLabel,
                        content = normalizedContent,
                        recordedEpochDay = today(now).toEpochDay(),
                        createdAt = now,
                        updatedAt = now
                    )
                )
                FamilyResult.Success(Unit)
            }
        } catch (_: SQLiteConstraintException) {
            FamilyResult.Failure(FamilyError.PersonNotFound)
        }
    }

    override suspend fun update(
        id: String,
        label: String?,
        content: String
    ): FamilyResult<Unit> {
        val normalizedContent = normalizeContent(content)
            ?: return FamilyResult.Failure(FamilyError.FactContentRequired)
        return db.withTransaction {
            val old = dao.getById(id)
                ?: return@withTransaction FamilyResult.Failure(FamilyError.FactNotFound)
            val category = runCatching { FactCategory.valueOf(old.category) }
                .getOrDefault(FactCategory.CUSTOM)
            val normalizedLabel = normalizeLabel(category, label)
                ?: if (category.needsLabel) {
                    return@withTransaction FamilyResult.Failure(FamilyError.FactLabelRequired)
                } else null
            if (normalizedLabel == old.label && normalizedContent == old.content) {
                return@withTransaction FamilyResult.Success(Unit)
            }
            val now = clock.now()
            dao.update(
                old.copy(
                    label = normalizedLabel,
                    content = normalizedContent,
                    recordedEpochDay = today(now).toEpochDay(),
                    updatedAt = now
                )
            )
            FamilyResult.Success(Unit)
        }
    }

    override suspend fun delete(id: String) = dao.delete(id)

    private fun normalizeContent(content: String): String? =
        content.trim().take(FACT_CONTENT_MAX).takeIf { it.isNotEmpty() }

    private fun normalizeLabel(category: FactCategory, label: String?): String? =
        if (category.needsLabel) {
            label?.trim()?.take(FACT_LABEL_MAX)?.takeIf { it.isNotEmpty() }
        } else null

    private fun today(now: Long): LocalDate =
        Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun PersonFactEntity.toModel(): PersonFact {
    val modelCategory = runCatching { FactCategory.valueOf(category) }
        .getOrDefault(FactCategory.CUSTOM)
    return PersonFact(
        id = id,
        personId = personId,
        category = modelCategory,
        label = if (modelCategory == FactCategory.CUSTOM && label.isNullOrBlank()) "其他" else label,
        content = content,
        recordedOn = LocalDate.ofEpochDay(recordedEpochDay),
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
