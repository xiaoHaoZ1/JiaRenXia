package io.github.xiaohaoz1.jiarenxia.data

import io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebration
import io.github.xiaohaoz1.jiarenxia.core.model.BabyCelebrationKind
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.BabyCelebrationEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 宝宝满月 / 百天庆祝日期（baby_celebrations）：用户另设的庆祝日，
 * 为空时按出生日计算（domain/baby/BabyMath）。
 */
interface BabyCelebrationRepository {
    /** 全部覆盖（提醒同步/worker 用；变化触发 reconcile）。 */
    fun observeAll(): Flow<List<BabyCelebration>>
    /** 某人的庆祝日期覆盖：kind → 事件日（无覆盖的 kind 不在表内）。 */
    fun observeOverrides(personId: String): Flow<Map<BabyCelebrationKind, LocalDate>>
    /** 全部覆盖（提醒 worker 用）。 */
    suspend fun getAll(): List<BabyCelebration>
    /** 写覆盖；date 为 null 时清除该 kind 的覆盖。 */
    suspend fun set(personId: String, kind: BabyCelebrationKind, date: LocalDate?)
}

@Singleton
class BabyCelebrationRepositoryImpl @Inject constructor(
    private val db: AppDatabase
) : BabyCelebrationRepository {

    override fun observeAll(): Flow<List<BabyCelebration>> =
        db.babyCelebrationDao().observeAll().map { list ->
            list.mapNotNull { e ->
                runCatching { BabyCelebrationKind.valueOf(e.kind) }.getOrNull()
                    ?.let { BabyCelebration(e.personId, it, e.epochDay) }
            }
        }

    override fun observeOverrides(personId: String) =
        db.babyCelebrationDao().observeForPerson(personId).map { list ->
            list.mapNotNull { e ->
                runCatching { BabyCelebrationKind.valueOf(e.kind) }.getOrNull()
                    ?.let { it to LocalDate.ofEpochDay(e.epochDay) }
            }.toMap()
        }

    override suspend fun getAll(): List<BabyCelebration> =
        db.babyCelebrationDao().getAll().mapNotNull { e ->
            runCatching { BabyCelebrationKind.valueOf(e.kind) }.getOrNull()
                ?.let { BabyCelebration(e.personId, it, e.epochDay) }
        }

    override suspend fun set(
        personId: String,
        kind: BabyCelebrationKind,
        date: LocalDate?
    ) {
        if (date == null) {
            db.babyCelebrationDao().delete(personId, kind.name)
        } else {
            db.babyCelebrationDao().upsert(
                BabyCelebrationEntity(personId, kind.name, date.toEpochDay())
            )
        }
    }
}
