package io.github.xiaohaoz1.jiarenxia.data

import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.room.withTransaction
import io.github.xiaohaoz1.jiarenxia.core.kinship.FamilyGraph
import io.github.xiaohaoz1.jiarenxia.core.model.CalendarType
import io.github.xiaohaoz1.jiarenxia.core.model.BirthdayValue
import io.github.xiaohaoz1.jiarenxia.core.model.Gender
import io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
import io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus
import io.github.xiaohaoz1.jiarenxia.core.model.PartialDate
import io.github.xiaohaoz1.jiarenxia.core.model.Person
import io.github.xiaohaoz1.jiarenxia.core.model.PhoneStatus
import io.github.xiaohaoz1.jiarenxia.core.model.RelationRole
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipSpec
import io.github.xiaohaoz1.jiarenxia.core.model.RelationshipType
import io.github.xiaohaoz1.jiarenxia.core.model.RelativeAgeOrder
import io.github.xiaohaoz1.jiarenxia.core.model.ReminderPreference
import io.github.xiaohaoz1.jiarenxia.data.avatar.AvatarStorage
import io.github.xiaohaoz1.jiarenxia.data.db.AppDatabase
import io.github.xiaohaoz1.jiarenxia.data.db.CustomTitleEntity
import io.github.xiaohaoz1.jiarenxia.data.db.clearTables
import io.github.xiaohaoz1.jiarenxia.data.db.ImportantDateEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity
import io.github.xiaohaoz1.jiarenxia.data.db.PhoneNumberEntity
import io.github.xiaohaoz1.jiarenxia.data.db.RelationshipEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderOffsetEntity
import io.github.xiaohaoz1.jiarenxia.data.db.ReminderPreferenceEntity
import io.github.xiaohaoz1.jiarenxia.data.db.toBirthdayValue
import io.github.xiaohaoz1.jiarenxia.domain.lunar.BirthdayMath
import io.github.xiaohaoz1.jiarenxia.data.db.toModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * [FamilyRepository] 的 Room 实现。
 * 校验规则见 DEVELOPMENT_PLAN 2.3 六；生日 / 提醒规则见 2.5；手机号规则见 2.8。
 */
@Singleton
class FamilyRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val avatarStorage: AvatarStorage,
    private val ids: IdGenerator,
    private val clock: TimeSource,
    private val lunar: io.github.xiaohaoz1.jiarenxia.domain.lunar.LunarCalendar
) : FamilyRepository {

    private val personDao get() = db.personDao()
    private val phoneDao get() = db.phoneNumberDao()
    private val relDao get() = db.relationshipDao()
    private val dateDao get() = db.importantDateDao()
    private val prefDao get() = db.reminderPreferenceDao()
    private val offsetDao get() = db.reminderOffsetDao()
    private val customTitleDao get() = db.customTitleDao()

    // ---------- 观察 ----------

    override fun observeSelf(): Flow<Person?> =
        personDao.observeSelf().map { it?.toModel() }

    override fun observeFamilyGraph(): Flow<FamilyGraph> = combine(
        personDao.observeAll(),
        relDao.observeAll(),
        dateDao.observeBirthdays()
    ) { persons, rels, dates ->
        val byPerson = dates.groupBy { it.personId }
        FamilyGraph(
            persons = persons.map { it.toModel() },
            relationships = rels.map { it.toModel() },
            birthdays = byPerson.mapNotNull { (pid, list) ->
                // 长幼用可确定的真实出生日：双生日冲突时判未知，不猜
                BirthdayMath.resolveBirthDate(
                    list.map { it.toBirthdayValue() }, lunar
                ).date?.let {
                    pid to PartialDate(it.year, it.monthValue, it.dayOfMonth)
                }
            }.toMap(),
            birthdayValues = byPerson.mapValues { (_, list) ->
                // 阳历在前，保持稳定的展示顺序
                list.sortedBy { it.calendarType }.map { it.toBirthdayValue() }
            }
        )
    }

    override fun observePersonDetail(personId: String): Flow<PersonDetail?> = combine(
        personDao.observeById(personId),
        phoneDao.observeForPerson(personId),
        dateDao.observeBirthdays(personId),
        prefDao.observeBirthdayPreferences(personId),
        relDao.observeForPerson(personId),
        // 提前天数是真正的响应式依赖：只改 offsets 表也要重发详情
        offsetDao.observeBirthdayForPerson(personId)
    ) { values ->
        val person = values[0] as PersonEntity? ?: return@combine null
        @Suppress("UNCHECKED_CAST")
        val phones = values[1] as List<PhoneNumberEntity>
        @Suppress("UNCHECKED_CAST")
        val birthdayRows = values[2] as List<ImportantDateEntity>
        @Suppress("UNCHECKED_CAST")
        val prefs = values[3] as List<ReminderPreferenceEntity>
        @Suppress("UNCHECKED_CAST")
        val rels = values[4] as List<RelationshipEntity>
        @Suppress("UNCHECKED_CAST")
        val allOffsets = values[5] as List<ReminderOffsetEntity>
        val prefByDate = prefs.associateBy { it.importantDateId }
        val offsetsByPref = allOffsets.groupBy { it.reminderPreferenceId }
        // 按历法分槽装配：每个生日带自己的偏好与偏移
        val birthdays = birthdayRows.mapNotNull { d ->
            val cal = d.type.birthdayCalendar ?: return@mapNotNull null
            val pref = prefByDate[d.id]
            cal to BirthdayDetail(
                dateId = d.id,
                value = d.toBirthdayValue(),
                reminder = pref?.toModel(
                    offsetsByPref[pref.id]?.map { it.daysBefore }?.toSet()
                        ?: ReminderPreference.DEFAULT_OFFSETS
                )
            )
        }.toMap()
        PersonDetail(
            person = person.toModel(),
            phones = phones.map { it.toModel() },
            birthdays = birthdays,
            relationships = rels.map { it.toModel() }
        )
    }

    override fun observeDefaultNumbers(): Flow<Map<String, String>> =
        phoneDao.observeAll().map { phones ->
            phones.filter { it.status == PhoneStatus.ACTIVE }
                .groupBy { it.personId }
                // DAO 已按 主号码优先、created_at、id 排序 → 每组取第一条
                .mapValues { (_, list) -> list.first().number }
        }

    override fun observeBirthdayEntries(): Flow<List<BirthdayEntry>> = combine(
        personDao.observeAll(),
        dateDao.observeBirthdays(),
        prefDao.observeAll(),
        offsetDao.observeAll()
    ) { persons, dates, prefs, offsets -> birthdayEntries(persons, dates, prefs, offsets) }

    override suspend fun getBirthdayEntries(): List<BirthdayEntry> =
        birthdayEntries(
            personDao.getAll(), dateDao.getBirthdays(), prefDao.getAll(), offsetDao.getAll()
        )

    private fun birthdayEntries(
        persons: List<PersonEntity>,
        dates: List<ImportantDateEntity>,
        prefs: List<ReminderPreferenceEntity>,
        offsets: List<ReminderOffsetEntity>
    ): List<BirthdayEntry> {
        val byId = persons.associateBy { it.id }
        val prefByDate = prefs.associateBy { it.importantDateId }
        val offsetsByPref = offsets.groupBy { it.reminderPreferenceId }
        // 已故人物不进入提醒页（DEVELOPMENT_PLAN 2.5 五）
        return dates.mapNotNull { d ->
            val p = byId[d.personId] ?: return@mapNotNull null
            if (p.livingStatus != LivingStatus.ALIVE) return@mapNotNull null
            val pref = prefByDate[d.id]
            BirthdayEntry(
                person = p.toModel(),
                birthday = d.toBirthdayValue(),
                reminderEnabled = pref?.enabled ?: false,
                dateId = d.id,
                reminderOffsets = pref?.let { pf ->
                    offsetsByPref[pf.id]?.map { it.daysBefore }?.toSet()
                        ?: ReminderPreference.DEFAULT_OFFSETS
                } ?: ReminderPreference.DEFAULT_OFFSETS
            )
        }
    }

    // ---------- 人物 ----------

    override suspend fun createSelf(draft: PersonDraft): FamilyResult<String> {
        validateDraft(draft)?.let { return FamilyResult.Failure(it) }
        val id = ids.newId()
        val now = clock.now()
        var error: FamilyError? = null
        return guardWrite(phoneWrite = true) {
            db.withTransaction {
                if (personDao.getSelf() != null) {
                    error = FamilyError.SelfAlreadyExists
                    return@withTransaction
                }
                insertPerson(id, draft, isSelf = true, now = now)
                insertPhones(id, draft.phones, now)
                draft.birthdays.list.forEach { createBirthdayWithPreference(id, it, now) }
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(id)
        }
    }

    override suspend fun createRelative(
        draft: PersonDraft,
        anchorId: String,
        role: RelationRole
    ): FamilyResult<String> = createRelative(
        draft,
        listOf(role.toRelationshipSpec(
            io.github.xiaohaoz1.jiarenxia.domain.NEW_PERSON, anchorId)),
        null
    )

    override suspend fun createRelative(
        draft: PersonDraft,
        specs: List<RelationshipSpec>,
        customTitle: String?
    ): FamilyResult<String> {
        validateDraft(draft)?.let { return FamilyResult.Failure(it) }
        val id = ids.newId()
        val now = clock.now()
        // 新人物与全部关系在同一事务写入：先插入人物，再逐条校验；
        // 任一校验失败整体回滚（人物也不落库）
        return guardWrite(phoneWrite = true) {
            db.withTransaction {
                insertPerson(id, draft, isSelf = false, now = now)
                insertPhones(id, draft.phones, now)
                draft.birthdays.list.forEach { createBirthdayWithPreference(id, it, now) }
                for (spec in specs) {
                    val bound = spec.bindNew(id)
                    validateRelationship(bound)?.let { throw TxAbort(it) }
                    insertRelationship(bound, now)
                }
                upsertObserverTitle(id, customTitle, now)
            }
            FamilyResult.Success(id)
        }
    }

    /** 计划规格中的 NEW_PERSON 占位替换为本事务新建的人物 id。 */
    private fun RelationshipSpec.bindNew(id: String): RelationshipSpec = copy(
        sourcePersonId = if (sourcePersonId ==
            io.github.xiaohaoz1.jiarenxia.domain.NEW_PERSON) id else sourcePersonId,
        targetPersonId = if (targetPersonId ==
            io.github.xiaohaoz1.jiarenxia.domain.NEW_PERSON) id else targetPersonId
    )

    /**
     * 事务内写观察者=“我”的自定义称呼（“只记称呼”场景）；
     * 与 setCustomTitle 同一套校验：目标须存在且非“我”、trim 后 1–12 字；
     * 已有称呼沿用原 id/createdAt。targetId/title 任一为空则不写。
     */
    private suspend fun upsertObserverTitle(
        targetId: String?, title: String?, now: Long
    ) {
        if (targetId == null) return
        val trimmed = title?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        if (trimmed.length > 12) throw TxAbort(FamilyError.InvalidCustomTitle)
        val self = personDao.getSelf() ?: throw TxAbort(FamilyError.PersonNotFound)
        val target = personDao.getById(targetId)
            ?: throw TxAbort(FamilyError.PersonNotFound)
        if (target.isSelf) throw TxAbort(FamilyError.CustomTitleOnSelf)
        // 已存在则沿用原 id/createdAt，Upsert 才能落到同一条记录
        val existing = customTitleDao.getForPair(self.id, targetId)
        customTitleDao.upsert(
            CustomTitleEntity(
                id = existing?.id ?: ids.newId(),
                observerPersonId = self.id,
                targetPersonId = targetId,
                title = trimmed,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    override suspend fun updatePerson(personId: String, draft: PersonDraft): FamilyResult<Unit> {
        validateDraft(draft)?.let { return FamilyResult.Failure(it) }
        val now = clock.now()
        var error: FamilyError? = null
        return guardWrite(phoneWrite = true) {
            db.withTransaction {
            val existing = personDao.getById(personId)
            if (existing == null) {
                error = FamilyError.PersonNotFound
                return@withTransaction
            }
            // 只更新表单字段：isSelf / avatarPath 不走表单
            personDao.update(
                existing.copy(
                    name = draft.name.trim(),
                    gender = draft.gender,
                    note = draft.note.cleanNote(),
                    livingStatus = draft.livingStatus,
                    siblingRank = draft.siblingRank,
                    updatedAt = now
                )
            )
            syncPhones(personId, draft.phones, now)
            // 阳历、农历两个槽位各自同步；互不干扰
            syncBirthday(personId, CalendarType.SOLAR, draft.birthdays.solar, now)
            syncBirthday(personId, CalendarType.LUNAR, draft.birthdays.lunar, now)
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(Unit)
        }
    }

    override suspend fun deletePerson(personId: String): FamilyResult<Unit> {
        var avatarPath: String? = null
        val result = guardWrite<Unit> {
            var r: FamilyResult<Unit> = FamilyResult.Success(Unit)
            db.withTransaction {
                val person = personDao.getById(personId)
                when {
                    person == null -> r = FamilyResult.Failure(FamilyError.PersonNotFound)
                    person.isSelf -> r = FamilyResult.Failure(FamilyError.CannotDeleteSelf)
                    else -> {
                        avatarPath = person.avatarPath
                        personDao.deleteById(personId)
                    }
                }
            }
            r
        }
        // 头像文件在事务提交后删除（数据库不感知文件系统）
        if (result is FamilyResult.Success) avatarStorage.delete(avatarPath)
        return result
    }

    // ---------- 关系 ----------

    override suspend fun addRelationship(
        personId: String,
        otherId: String,
        role: RelationRole
    ): FamilyResult<String> {
        val id = ids.newId()
        val now = clock.now()
        val spec = role.toRelationshipSpec(otherId, personId)
        var error: FamilyError? = null
        return guardWrite<String> {
            db.withTransaction {
                error = validateRelationship(spec)
                if (error == null) insertRelationship(spec, now, id)
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(id)
        }
    }

    override suspend fun addRelationships(
        specs: List<RelationshipSpec>,
        customTitleFor: String?,
        customTitle: String?
    ): FamilyResult<Unit> {
        val now = clock.now()
        return guardWrite {
            db.withTransaction {
                for (spec in specs) {
                    validateRelationship(spec)?.let { throw TxAbort(it) }
                    insertRelationship(spec, now)
                }
                upsertObserverTitle(customTitleFor, customTitle, now)
            }
            FamilyResult.Success(Unit)
        }
    }

    override suspend fun removeRelationship(relationshipId: String): FamilyResult<Unit> {
        relDao.deleteById(relationshipId)
        return FamilyResult.Success(Unit)
    }

    override suspend fun setSiblingAgeOrder(
        relationshipId: String,
        subjectId: String,
        order: RelativeAgeOrder?
    ): FamilyResult<Unit> {
        val rel = relDao.getById(relationshipId)
            ?: return FamilyResult.Failure(FamilyError.RelationshipNotFound)
        // 申报堂表亲同样携带长幼，可经同一入口修改
        if (rel.type != RelationshipType.SIBLING &&
            rel.type != RelationshipType.COUSIN
        ) {
            return FamilyResult.Failure(FamilyError.NotSibling)
        }
        if (subjectId != rel.sourcePersonId && subjectId != rel.targetPersonId) {
            return FamilyResult.Failure(FamilyError.PersonNotFound)
        }
        // 存储方向 = source 相对 target；入参方向 = subject 相对另一端
        val stored = when {
            order == null -> null
            subjectId == rel.sourcePersonId -> order
            else -> order.inverse()
        }
        return guardWrite {
            relDao.updateAgeOrder(relationshipId, stored, clock.now())
            FamilyResult.Success(Unit)
        }
    }

    // ---------- 生日提醒 ----------

    override suspend fun setReminderEnabled(
        personId: String, enabled: Boolean,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
    ): FamilyResult<Unit> {
        val now = clock.now()
        return guardWrite {
            var error: FamilyError? = null
            db.withTransaction {
                val date = dateDao.getByPersonAndType(personId, type.name)
                if (date == null) {
                    error = FamilyError.NoBirthday
                    return@withTransaction
                }
                val pref = prefDao.getForDate(date.id)
                if (pref != null) {
                    prefDao.update(pref.copy(enabled = enabled, updatedAt = now))
                } else {
                    // 偏好不存在视为未提醒；打开开关时补建默认偏好
                    insertPreference(date.id, enabled, now,
                        defaultOffsets(type))
                }
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(Unit)
        }
    }

    override suspend fun setReminderOffsets(
        personId: String, offsets: Set<Int>,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
    ): FamilyResult<Unit> {
        if (offsets.isEmpty() || !ALLOWED_OFFSETS.containsAll(offsets)) {
            return FamilyResult.Failure(FamilyError.InvalidReminderOffsets)
        }
        val now = clock.now()
        return guardWrite {
            var error: FamilyError? = null
            db.withTransaction {
                val date = dateDao.getByPersonAndType(personId, type.name)
                if (date == null) {
                    error = FamilyError.NoBirthday
                    return@withTransaction
                }
                var pref = prefDao.getForDate(date.id)
                if (pref == null) {
                    // 缺失偏好：补建为开启状态（用户已主动设置提醒时间）
                    insertPreference(date.id, enabled = true, now = now,
                        defaultOffsets(type))
                    pref = prefDao.getForDate(date.id)
                }
                pref ?: return@withTransaction
                offsetDao.deleteForPreference(pref.id)
                offsetDao.insertAll(
                    offsets.sorted().map {
                        ReminderOffsetEntity(ids.newId(), pref.id, it)
                    }
                )
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(Unit)
        }
    }

    // ---------- 重要日期：忌日 / 结婚纪念日（FAMILY_AFFAIRS 第 8 批） ----------

    /** 生日值 → 实体字段复用（syncBirthday 之外的通用写法）。 */
    private fun ImportantDateEntity.withValue(
        v: BirthdayValue, now: Long
    ) = copy(
        year = v.date.year, month = v.date.month, day = v.date.day,
        calendarType = v.calendar, isLeapMonth = v.leapMonth,
        updatedAt = now
    )

    private fun dateEntity(
        personId: String,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        v: BirthdayValue, now: Long
    ): ImportantDateEntity {
        // 生日行的历法由 type 槽位决定，保证 type↔calendar 一致
        val calendar = type.birthdayCalendar ?: v.calendar
        return ImportantDateEntity(
            id = ids.newId(), personId = personId, type = type,
            year = v.date.year, month = v.date.month, day = v.date.day,
            calendarType = calendar, isLeapMonth = v.leapMonth,
            note = null, createdAt = now, updatedAt = now
        )
    }

    /**
     * 单槽生日设置：value=null 删除该槽位（偏好随级联删除）；
     * 已有行原地更新保留 id 与提醒配置。
     */
    override suspend fun setBirthday(
        personId: String,
        calendar: io.github.xiaohaoz1.jiarenxia.core.model.CalendarType,
        value: BirthdayValue?
    ): FamilyResult<Unit> {
        if (value != null && value.calendar != calendar) {
            return FamilyResult.Failure(FamilyError.InvalidBirthday)
        }
        if (value != null) {
            val probe = io.github.xiaohaoz1.jiarenxia.core.model
                .PersonBirthdays().with(calendar, value)
            validateBirthdays(probe)?.let { return FamilyResult.Failure(it) }
        }
        val now = clock.now()
        return guardWrite {
            var error: FamilyError? = null
            db.withTransaction {
                if (personDao.getById(personId) == null) {
                    error = FamilyError.PersonNotFound
                    return@withTransaction
                }
                syncBirthday(personId, calendar, value, now)
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(Unit)
        }
    }

    override suspend fun setDeathAnniversary(
        personId: String, value: BirthdayValue?
    ): FamilyResult<Unit> {
        val now = clock.now()
        return guardWrite {
            var error: FamilyError? = null
            db.withTransaction {
                val person = personDao.getById(personId)
                if (person == null) {
                    error = FamilyError.PersonNotFound; return@withTransaction
                }
                if (value == null) {
                    dateDao.deleteByPersonAndType(
                        personId,
                        io.github.xiaohaoz1.jiarenxia.core.model
                            .ImportantDateType.DEATH_ANNIVERSARY.name)
                    return@withTransaction
                }
                if (person.livingStatus !=
                    io.github.xiaohaoz1.jiarenxia.core.model.LivingStatus.DECEASED
                ) {
                    error = FamilyError.NotDeceased; return@withTransaction
                }
                val t = io.github.xiaohaoz1.jiarenxia.core.model
                    .ImportantDateType.DEATH_ANNIVERSARY
                val ex = dateDao.getByPersonAndType(personId, t.name)
                if (ex != null) dateDao.update(ex.withValue(value, now))
                else {
                    val e = dateEntity(personId, t, value, now)
                    dateDao.insert(e)
                    insertPreference(e.id, enabled = true, now = now,
                        offsets = defaultOffsets(t))
                }
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(Unit)
        }
    }

    override suspend fun setWeddingAnniversary(
        personId: String, value: BirthdayValue?
    ) = setCoupleAnniversary(
        personId,
        io.github.xiaohaoz1.jiarenxia.core.model
            .ImportantDateType.WEDDING_ANNIVERSARY, value)

    override suspend fun setCoupleAnniversary(
        personId: String,
        type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType,
        value: BirthdayValue?
    ): FamilyResult<Unit> {
        if (!type.isCoupleShared) {
            return FamilyResult.Failure(FamilyError.InvalidDateType)
        }
        val now = clock.now()
        val t = type
        return guardWrite {
            var error: FamilyError? = null
            db.withTransaction {
                val person = personDao.getById(personId)
                if (person == null) {
                    error = FamilyError.PersonNotFound; return@withTransaction
                }
                val spouse = relDao.getSpouseRecords(personId)
                    .firstOrNull()?.let {
                        if (it.sourcePersonId == personId) it.targetPersonId
                        else it.sourcePersonId
                    }
                if (value == null) {
                    // 删除：本人或配偶名下那一条都清掉
                    dateDao.deleteByPersonAndType(personId, t.name)
                    spouse?.let { dateDao.deleteByPersonAndType(it, t.name) }
                    return@withTransaction
                }
                if (spouse == null) {
                    error = FamilyError.NoSpouse; return@withTransaction
                }
                // 一对夫妻只有一条：配偶已有就改那一条
                val onSpouse = dateDao.getByPersonAndType(spouse, t.name)
                val onSelf = dateDao.getByPersonAndType(personId, t.name)
                when {
                    onSpouse != null ->
                        dateDao.update(onSpouse.withValue(value, now))
                    onSelf != null ->
                        dateDao.update(onSelf.withValue(value, now))
                    else -> {
                        val e = dateEntity(personId, t, value, now)
                        dateDao.insert(e)
                        insertPreference(e.id, enabled = true, now = now,
                            offsets = defaultOffsets(t))
                    }
                }
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(Unit)
        }
    }

    override suspend fun coParentCandidate(
        personId: String
    ): io.github.xiaohaoz1.jiarenxia.core.model.Person? {
        val me = personDao.getById(personId) ?: return null
        if (relDao.getSpouseRecords(personId).isNotEmpty()) return null
        // 我的孩子 → 孩子的另一位父母
        val myChildIds = relDao.getAll().filter {
            it.type == RelationshipType.PARENT && it.sourcePersonId == personId
        }.map { it.targetPersonId }
        val candidates = LinkedHashSet<String>()
        for (cid in myChildIds) {
            relDao.getParentRecords(cid).forEach { pr ->
                if (pr.sourcePersonId != personId) candidates += pr.sourcePersonId
            }
        }
        val ok = candidates.mapNotNull { personDao.getById(it) }
            .filter { other ->
                other.gender != me.gender &&
                    relDao.getSpouseRecords(other.id).isEmpty()
            }
        // 不止一位候选（再婚等）时不自动推荐
        return ok.singleOrNull()?.toModel()
    }

    override suspend fun commonChildCandidates(
        a: String, b: String
    ): List<CommonChildCandidate> {
        val pa = personDao.getById(a) ?: return emptyList()
        val pb = personDao.getById(b) ?: return emptyList()
        // owner 已录的子女中，可能也是 other 子女的人
        suspend fun of(owner: PersonEntity, other: PersonEntity) =
            relDao.getChildRecords(owner.id).mapNotNull { r ->
                val parents = relDao.getParentRecords(r.targetPersonId)
                // 已录父母里有另一方 → 已是共同子女，不重复问
                if (parents.any { it.sourcePersonId == other.id }) {
                    return@mapNotNull null
                }
                // 已有与另一方同性别的父母：有父亲的孩子不再问新丈夫
                if (parents.any {
                    personDao.getById(it.sourcePersonId)?.gender == other.gender
                }) {
                    return@mapNotNull null
                }
                val child = personDao.getById(r.targetPersonId)
                    ?: return@mapNotNull null
                CommonChildCandidate(
                    child = child.toModel(),
                    existingParentId = owner.id,
                    childLabel = if (child.gender == Gender.MALE) "儿子" else "女儿"
                )
            }
        return of(pa, pb) + of(pb, pa)
    }

    override suspend fun linkSpouseAndSetWeddingAnniversary(
        personId: String, spouseId: String,
        value: BirthdayValue
    ): FamilyResult<Unit> {
        val now = clock.now()
        val t = io.github.xiaohaoz1.jiarenxia.core.model
            .ImportantDateType.WEDDING_ANNIVERSARY
        return guardWrite {
            var error: FamilyError? = null
            db.withTransaction {
                // 与 addRelationship 走同一校验/规范化插入
                val spec = io.github.xiaohaoz1.jiarenxia.core.model
                    .RelationshipSpec(
                        personId, spouseId, RelationshipType.SPOUSE, null)
                error = validateRelationship(spec)
                if (error != null) return@withTransaction
                insertRelationship(spec, now)
                // 纪念日（此时双方均无既有记录，挂在 personId 名下）
                val e = dateEntity(personId, t, value, now)
                dateDao.insert(e)
                insertPreference(e.id, enabled = true, now = now,
                    offsets = defaultOffsets(t))
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(Unit)
        }
    }

    override fun observeDateEntries(): Flow<List<DateEntry>> = combine(
        personDao.observeAll(), dateDao.observeAllDates(),
        prefDao.observeAll(), offsetDao.observeAll(), relDao.observeAll()
    ) { persons, dates, prefs, offsets, rels ->
        // 关系是真实依赖：结婚/离婚改变纪念日配偶侧展示，必须响应式读取
        dateEntries(persons, dates, prefs, offsets, rels)
    }

    override suspend fun getDateEntries(): List<DateEntry> = dateEntries(
        personDao.getAll(), dateDao.getAllDates(),
        prefDao.getAll(), offsetDao.getAll(), relDao.getAll())

    private fun dateEntries(
        personsList: List<PersonEntity>,
        datesList: List<ImportantDateEntity>,
        prefsList: List<ReminderPreferenceEntity>,
        offsetsList: List<ReminderOffsetEntity>,
        relsList: List<RelationshipEntity>
    ): List<DateEntry> {
        val persons = personsList.associateBy { it.id }
        val prefs = prefsList.associateBy { it.importantDateId }
        val offsets = offsetsList.groupBy { it.reminderPreferenceId }
        val spouses = relsList.filter {
            it.type == io.github.xiaohaoz1.jiarenxia.core.model
                .RelationshipType.SPOUSE
        }
        return datesList.mapNotNull { d ->
            val p = persons[d.personId] ?: return@mapNotNull null
            val pref = prefs[d.id]
            val offs = pref?.let { pf ->
                offsets[pf.id]?.map { it.daysBefore }?.toSet()
            }
            // 夫妻共享纪念日（结婚/领证/恋爱）：找这对夫妻的另一方
            var partner: io.github.xiaohaoz1.jiarenxia.data.db.PersonEntity? = null
            if (d.type.isCoupleShared) {
                partner = spouses.firstOrNull {
                    it.sourcePersonId == p.id || it.targetPersonId == p.id
                }?.let {
                    persons[
                        if (it.sourcePersonId == p.id) it.targetPersonId
                        else it.sourcePersonId]
                }
            }
            DateEntry(
                type = d.type, person = p.toModel(),
                date = d.toBirthdayValue(),
                reminderEnabled = pref?.enabled ?: false,
                reminderOffsets = offs ?: defaultOffsets(d.type),
                partnerId = partner?.id, partnerName = partner?.name,
                partnerIsSelf = partner?.isSelf == true
            )
        }
    }

    // ---------- 头像 ----------

    override suspend fun setAvatar(personId: String, source: Uri): FamilyResult<Unit> {
        val person = personDao.getById(personId)
            ?: return FamilyResult.Failure(FamilyError.PersonNotFound)
        val relativePath = try {
            avatarStorage.import(personId, source)
        } catch (e: Exception) {
            return FamilyResult.Failure(FamilyError.AvatarFailed)
        }
        return try {
            // 只更新 avatar_path 与 updated_at，避免并发下覆盖其它字段
            personDao.updateAvatar(personId, relativePath, clock.now())
            avatarStorage.delete(person.avatarPath)
            FamilyResult.Success(Unit)
        } catch (e: Exception) {
            avatarStorage.delete(relativePath)
            FamilyResult.Failure(FamilyError.AvatarFailed)
        }
    }

    override suspend fun removeAvatar(personId: String): FamilyResult<Unit> {
        val person = personDao.getById(personId)
            ?: return FamilyResult.Failure(FamilyError.PersonNotFound)
        val oldPath = person.avatarPath
        personDao.updateAvatar(personId, null, clock.now())
        avatarStorage.delete(oldPath)
        return FamilyResult.Success(Unit)
    }

    // ---------- 清空 ----------

    override fun observeCustomTitles(): Flow<Map<String, String>> = combine(
        personDao.observeSelf(),
        customTitleDao.observeAll()
    ) { self, rows ->
        rows.filter { it.observerPersonId == self?.id }
            .associate { it.targetPersonId to it.title }
    }

    override suspend fun setCustomTitle(targetId: String, title: String?): FamilyResult<Unit> {
        val trimmed = title?.trim().orEmpty()
        return guardWrite {
            var error: FamilyError? = null
            db.withTransaction {
                val self = personDao.getSelf()
                val target = personDao.getById(targetId)
                when {
                    self == null || target == null -> error = FamilyError.PersonNotFound
                    target.isSelf -> error = FamilyError.CustomTitleOnSelf
                    trimmed.isEmpty() ->
                        customTitleDao.delete(self.id, targetId)
                    trimmed.length > 12 -> error = FamilyError.InvalidCustomTitle
                    else -> {
                        val now = clock.now()
                        // 已存在则沿用原 id/createdAt，Upsert 才能落到同一条记录
                        val existing = customTitleDao.getForPair(self.id, targetId)
                        customTitleDao.upsert(
                            CustomTitleEntity(
                                id = existing?.id ?: ids.newId(),
                                observerPersonId = self.id,
                                targetPersonId = targetId,
                                title = trimmed,
                                createdAt = existing?.createdAt ?: now,
                                updatedAt = now
                            )
                        )
                    }
                }
            }
            error?.let { FamilyResult.Failure(it) } ?: FamilyResult.Success(Unit)
        }
    }

    override suspend fun clearAllData() {
        db.withTransaction { db.clearTables() }
        avatarStorage.deleteAll()
    }

    // ---------- 内部 ----------

    /**
     * 写操作保护：TxAbort 透出业务错误；意外的唯一约束冲突在手机号写入路径翻译为
     * DuplicatePhone，其余路径翻译为 SaveFailed，避免异常逃出仓库层。
     */
    private suspend fun <T> guardWrite(
        phoneWrite: Boolean = false,
        block: suspend () -> FamilyResult<T>
    ): FamilyResult<T> = try {
        block()
    } catch (e: TxAbort) {
        FamilyResult.Failure(e.error)
    } catch (e: SQLiteConstraintException) {
        FamilyResult.Failure(if (phoneWrite) FamilyError.DuplicatePhone else FamilyError.SaveFailed)
    } catch (e: kotlinx.coroutines.CancellationException) {
        // 协程取消必须原样抛出，不能当保存失败吞掉
        throw e
    } catch (e: Exception) {
        FamilyResult.Failure(FamilyError.SaveFailed)
    }

    /** 空白备注存为 null。 */
    private fun String?.cleanNote(): String? = this?.trim()?.ifBlank { null }

    /** 表单校验，返回第一个错误；null 表示通过。 */
    private fun validateDraft(draft: PersonDraft): FamilyError? {
        if (draft.name.isBlank()) return FamilyError.NameRequired
        draft.siblingRank?.let { if (it !in 1..50) return FamilyError.InvalidSiblingRank }
        validateBirthdays(draft.birthdays)?.let { return it }
        val keys = HashSet<String>()
        var primaryCount = 0
        for (p in draft.phones) {
            if (p.number.isBlank()) return FamilyError.EmptyPhoneNumber
            if (!keys.add(normalizePhone(p.number))) return FamilyError.DuplicatePhone
            if (p.isPrimary) primaryCount++
        }
        if (primaryCount > 1) return FamilyError.MultiplePrimary
        return null
    }

    /** 双生日槽位校验：每个已填生日按其历法校验；null 表示全部通过。 */
    private fun validateBirthdays(
        birthdays: io.github.xiaohaoz1.jiarenxia.core.model.PersonBirthdays
    ): FamilyError? {
        for (bd in birthdays.list) {
            when (bd.calendar) {
                CalendarType.SOLAR -> {
                    if (!bd.date.isValid()) return FamilyError.InvalidBirthday
                    val year = bd.date.year
                    if (year != null && year !in 1800..currentYear()) return FamilyError.InvalidBirthday
                }
                CalendarType.LUNAR -> {
                    if (bd.date.month !in 1..12 || bd.date.day !in 1..30)
                        return FamilyError.InvalidBirthday
                    val year = bd.date.year
                    if (year != null) {
                        if (year !in 1900..currentYear()) return FamilyError.InvalidBirthday
                        // 有年份时必须真实存在（闰月/三十日校验）
                        if (lunar.toSolar(year, bd.date.month, bd.date.day, bd.leapMonth) == null)
                            return FamilyError.InvalidBirthday
                    }
                }
            }
        }
        return null
    }

    private fun currentYear(): Int =
        Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault()).year

    private suspend fun insertPerson(id: String, draft: PersonDraft, isSelf: Boolean, now: Long) {
        personDao.insert(
            PersonEntity(
                id = id,
                name = draft.name.trim(),
                gender = draft.gender,
                avatarPath = null,
                note = draft.note.cleanNote(),
                livingStatus = draft.livingStatus,
                isSelf = isSelf,
                siblingRank = draft.siblingRank,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /** 号码入库：INACTIVE 号码强制非主号码，表单顺序用 createdAt 依次递增保留。 */
    private suspend fun insertPhones(personId: String, phones: List<PhoneDraft>, now: Long) {
        phones.forEachIndexed { index, d ->
            phoneDao.insert(
                PhoneNumberEntity(
                    id = ids.newId(),
                    personId = personId,
                    number = d.number.trim(),
                    label = d.label,
                    status = d.status,
                    isPrimary = d.isPrimary && d.status == PhoneStatus.ACTIVE,
                    note = d.note.cleanNote(),
                    createdAt = now + index,
                    updatedAt = now
                )
            )
        }
    }

    /** 编辑时同步手机号：带 id 的更新、缺失的删除、无 id 的按表单顺序插入。 */
    private suspend fun syncPhones(personId: String, phones: List<PhoneDraft>, now: Long) {
        val existing = phoneDao.getForPerson(personId)
        val byId = existing.associateBy { it.id }
        val keptIds = phones.mapNotNull { it.id }.toSet()
        val removed = existing.filter { it.id !in keptIds }.map { it.id }
        if (removed.isNotEmpty()) phoneDao.deleteByIds(removed)
        // 两遍写：先给号码有变化的行换上临时唯一值，避免 UNIQUE(person_id, number) 在
        // 号码对调/复用时发生瞬态冲突，随后再写回最终值
        val pending = phones.map { it to (it.id?.let { id -> byId[id] }) }
            .filter { (_, target) -> target != null }
        for ((d, target) in pending) {
            if (target!!.number != d.number.trim()) {
                phoneDao.update(target.copy(number = "__tmp__" + target.id, updatedAt = now))
            }
        }
        for ((d, target) in pending) {
            phoneDao.update(
                target!!.copy(
                    number = d.number.trim(),
                    label = d.label,
                    status = d.status,
                    isPrimary = d.isPrimary && d.status == PhoneStatus.ACTIVE,
                    note = d.note.cleanNote(),
                    updatedAt = now
                )
            )
        }
        phones.forEachIndexed { index, d ->
            if (d.id != null && byId.containsKey(d.id)) return@forEachIndexed
            phoneDao.insert(
                PhoneNumberEntity(
                    id = ids.newId(),
                    personId = personId,
                    number = d.number.trim(),
                    label = d.label,
                    status = d.status,
                    isPrimary = d.isPrimary && d.status == PhoneStatus.ACTIVE,
                    note = d.note.cleanNote(),
                    createdAt = now + index,
                    updatedAt = now
                )
            )
        }
    }

    /** 单槽生日同步：null 删除该槽位（偏好随级联删除）；已有行原地更新保留 id 与偏好。 */
    private suspend fun syncBirthday(
        personId: String,
        calendar: CalendarType,
        birthday: BirthdayValue?,
        now: Long
    ) {
        val type = ImportantDateType.birthdayOf(calendar)
        val existing = dateDao.getByPersonAndType(personId, type.name)
        if (birthday == null) {
            if (existing != null) {
                dateDao.deleteByPersonAndType(personId, type.name)
            }
        } else if (existing != null) {
            // 原地更新：行 id、提醒偏好与偏移全部保留
            dateDao.update(existing.withValue(birthday, now))
        } else {
            createBirthdayWithPreference(personId, birthday, now)
        }
    }

    /**
     * 创建生日行 + 默认提醒偏好（提前 {0,1} 天），同一事务内调用。
     * type 由历法槽位决定；新增第二生日默认不额外开启提醒——
     * 该人已有生日行时 enabled=false，首个生日沿用默认开启。
     */
    private suspend fun createBirthdayWithPreference(personId: String, birthday: BirthdayValue, now: Long) {
        val type = ImportantDateType.birthdayOf(birthday.calendar)
        val e = dateEntity(personId, type, birthday, now)
        dateDao.insert(e)
        insertPreference(
            e.id,
            enabled = dateDao.getBirthdays(personId).size == 1,
            now = now,
            offsets = defaultOffsets(type)
        )
    }

    private suspend fun insertPreference(
        dateId: String, enabled: Boolean, now: Long,
        offsets: Set<Int> = ReminderPreference.DEFAULT_OFFSETS
    ) {
        val prefId = ids.newId()
        prefDao.insert(
            ReminderPreferenceEntity(
                id = prefId,
                importantDateId = dateId,
                enabled = enabled,
                createdAt = now,
                updatedAt = now
            )
        )
        offsetDao.insertAll(
            offsets.map {
                ReminderOffsetEntity(ids.newId(), prefId, it)
            }
        )
    }

    private suspend fun insertRelationship(spec: RelationshipSpec, now: Long, id: String = ids.newId()) {
        // 对称关系规范存储（DATABASE_SCHEMA 7.13/7.14）：
        // SPOUSE/SIBLING/COUSIN 统一 source < target；SIBLING/COUSIN 交换端点时翻转长幼
        var src = spec.sourcePersonId
        var tgt = spec.targetPersonId
        var order = if (spec.type == RelationshipType.SIBLING ||
            spec.type == RelationshipType.COUSIN
        ) spec.relativeAgeOrder else null
        if ((spec.type == RelationshipType.SPOUSE ||
            spec.type == RelationshipType.SIBLING ||
            spec.type == RelationshipType.COUSIN) && src > tgt
        ) {
            src = spec.targetPersonId
            tgt = spec.sourcePersonId
            order = order?.inverse()
        }
        relDao.insert(
            RelationshipEntity(
                id = id,
                sourcePersonId = src,
                targetPersonId = tgt,
                type = spec.type,
                // relativeAgeOrder 只保留在 SIBLING / COUSIN 上
                relativeAgeOrder = order,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    /**
     * 关系写入校验（DEVELOPMENT_PLAN 2.3 六），在事务内调用：
     * 两端存在 → 非自关系 → 两点间无既有关系 → 类型特定约束。
     */
    private suspend fun validateRelationship(spec: RelationshipSpec): FamilyError? {
        val src = spec.sourcePersonId
        val tgt = spec.targetPersonId
        if (personDao.getById(src) == null || personDao.getById(tgt) == null) {
            return FamilyError.PersonNotFound
        }
        if (src == tgt) return FamilyError.SelfRelation
        if (relDao.getBetween(src, tgt).isNotEmpty()) return FamilyError.DuplicatePair
        when (spec.type) {
            RelationshipType.PARENT -> {
                // target 最多两位父母
                if (relDao.getParentRecords(tgt).size >= 2) return FamilyError.TooManyParents
                // 拒绝祖先循环：target 不能已是 source 的祖先
                if (tgt in ancestorsOf(src)) return FamilyError.AncestorCycle
            }
            RelationshipType.SPOUSE -> {
                if (relDao.getSpouseRecords(src).isNotEmpty() ||
                    relDao.getSpouseRecords(tgt).isNotEmpty()
                ) {
                    return FamilyError.SpouseAlreadyExists
                }
            }
            else -> Unit
        }
        if (io.github.xiaohaoz1.jiarenxia.core.model.GenerationRules.conflicts(
                relDao.getAll().map {
                    RelationshipSpec(it.sourcePersonId, it.targetPersonId, it.type, it.relativeAgeOrder)
                }, spec)) return FamilyError.GenerationConflict
        return null
    }

    /** 沿 PARENT 记录向上收集祖先集合（用于拒绝辈分循环）。 */
    private suspend fun ancestorsOf(personId: String): Set<String> {
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque<String>()
        queue.add(personId)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            for (r in relDao.getParentRecords(n)) {
                if (seen.add(r.sourcePersonId)) queue.add(r.sourcePersonId)
            }
        }
        return seen
    }

    // ==================== 离线协作（FAMILY_AFFAIRS §5） ====================

    override fun observeLocalOnlyIds(): Flow<Set<String>> =
        db.familyLocalOnlyDao().observeIds().map { it.toSet() }

    override fun observeLocalOnly():
        Flow<List<io.github.xiaohaoz1.jiarenxia.data.db.FamilyLocalOnlyEntity>> =
        db.familyLocalOnlyDao().observeAll()

    override suspend fun canonicalRosterSnapshot():
        io.github.xiaohaoz1.jiarenxia.data.collab.CollabRoster =
        db.withTransaction { rosterSnapshotInTx() }

    /**
     * 事务内构建本机名册规范快照 + 哈希：排除“仅本机保留”人物，
     * 人物/条目 id 经 规范→本机 映射反转换到规范空间；头像按内容哈希。
     * 必须在 withTransaction 内调用。
     */
    internal suspend fun rosterSnapshotInTx():
        io.github.xiaohaoz1.jiarenxia.data.collab.CollabRoster {
        val state = db.familyCollabDao().get()
        val idMap = parseIdMap(state?.idMapJson)
        val localToCanon = idMap.entries.associate { (c, l) -> l to c }
        val localOnly = db.familyLocalOnlyDao().getIds().toSet()
        fun canon(localId: String) = localToCanon[localId] ?: localId
        val persons = personDao.getAll().filter { it.id !in localOnly }
        val keep = persons.mapTo(HashSet()) { it.id }
        val snap = io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot
            .fromEntities(
                persons = persons.map { it.copy(id = canon(it.id)) },
                phones = phoneDao.getAll()
                    .filter { it.personId in keep }
                    .map { it.copy(personId = canon(it.personId)) },
                rels = relDao.getAll()
                    .filter { it.sourcePersonId in keep && it.targetPersonId in keep }
                    .map { it.copy(
                        sourcePersonId = canon(it.sourcePersonId),
                        targetPersonId = canon(it.targetPersonId)) },
                dates = dateDao.getAllDates()
                    .filter { it.personId in keep }
                    .map { it.copy(personId = canon(it.personId)) },
                avatarHashOf = { p ->
                    p.avatarPath?.let { path ->
                        val f = avatarStorage.resolve(path)
                        if (f.isFile) {
                            io.github.xiaohaoz1.jiarenxia.domain.merge
                                .FamilySnapshot.sha256Hex(f.readBytes())
                        } else null
                    }
                }
            )
        return io.github.xiaohaoz1.jiarenxia.data.collab.CollabRoster(
            snap = snap,
            hash = io.github.xiaohaoz1.jiarenxia.domain.merge.FamilySnapshot
                .hash(snap),
            canonicalToLocal = idMap.filterValues { local ->
                persons.any { it.id == local } },
            localOnlyIds = localOnly
        )
    }

    /**
     * 协作应用（唯一写入口）。顺序：
     * 名册哈希复核 → 草稿 PENDING 复核 → 删关系 → 增/改人物 → 电话/日期
     * upsert/删除 → 关系逐条 RelationGraph 校验插入 → 人物移除（私人外键
     * 复核转“仅本机保留”；self 阻止）→ 仅本机标记 → self 指定 →
     * 协作状态/发布索引/草稿记录写回。
     */
    override suspend fun applyFamilyRevision(
        apply: io.github.xiaohaoz1.jiarenxia.data.collab.CollabApply
    ): FamilyResult<Unit> = guardWrite {
        db.withTransaction {
            // 1. 名册复核：计划时刻与本机当前一致才允许落库
            if (rosterSnapshotInTx().hash != apply.expectedRosterHash) {
                throw TxAbort(FamilyError.CollabStale)
            }
            // 2. 草稿复核：必须是 PENDING（同包重复应用防护）
            apply.expectPendingPackage?.let { pkg ->
                val d = db.familyReceiveDraftDao().get(pkg)
                if (d == null || d.status != "PENDING") {
                    throw TxAbort(FamilyError.CollabDraftStale)
                }
            }
            val now = clock.now()
            val localOnlyDao = db.familyLocalOnlyDao()
            // 3. 关系删除先行（为换配偶这类“先删后加”腾约束空间）
            apply.deleteRelIds.forEach { relDao.deleteById(it) }
            // 4. 人物增/改
            apply.addPersons.forEach { personDao.insert(it.copy(isSelf = false)) }
            apply.updatePersons.forEach { personDao.update(it) }
            apply.setSelfId?.let { sid ->
                personDao.getAll().forEach { p ->
                    if (p.isSelf && p.id != sid) {
                        personDao.update(p.copy(isSelf = false, updatedAt = now))
                    }
                }
                personDao.getById(sid)?.let {
                    personDao.update(it.copy(isSelf = true, updatedAt = now))
                } ?: throw TxAbort(FamilyError.PersonNotFound)
            }
            // 5. 电话：按（人物，规范化号码）匹配保留行 id，否则插入
            apply.upsertPhones.forEach { e ->
                val existing = phoneDao.getForPerson(e.personId)
                    .firstOrNull { normalizePhone(it.number) == normalizePhone(e.number) }
                if (existing != null) {
                    phoneDao.update(e.copy(id = existing.id,
                        number = existing.number,
                        createdAt = existing.createdAt, updatedAt = now))
                } else {
                    phoneDao.insert(e)
                }
            }
            apply.deletePhones.forEach { (pid, num) ->
                phoneDao.getForPerson(pid)
                    .filter { normalizePhone(it.number) == num }
                    .forEach { phoneDao.deleteByIds(listOf(it.id)) }
            }
            // 6. 日期：按（人物，类型）匹配保留行 id，否则插入
            apply.upsertDates.forEach { e ->
                val existing = dateDao.getByPersonAndType(e.personId, e.type.name)
                if (existing != null) {
                    dateDao.update(e.copy(id = existing.id,
                        createdAt = existing.createdAt, updatedAt = now))
                } else {
                    dateDao.insert(e)
                }
            }
            apply.deleteDates.forEach { (pid, t) ->
                dateDao.deleteByPersonAndType(pid, t.name)
            }
            // 7. 关系逐条校验后插入（基于删后图，逐条 check+add）
            val personsNow = personDao.getAll().associateBy { it.id }
            val graph = io.github.xiaohaoz1.jiarenxia.domain.merge.RelationGraph(
                personsNow, relDao.getAll())
            for (r in apply.insertRels) {
                if (personsNow[r.sourcePersonId] == null ||
                    personsNow[r.targetPersonId] == null) {
                    throw TxAbort(FamilyError.PersonNotFound)
                }
                val v = graph.check(r)
                when {
                    v == null -> { relDao.insert(r); graph.add(r) }
                    v.identical -> {}  // 完全相同：静默跳过
                    else -> throw TxAbort(FamilyError.CollabRelConflict)
                }
            }
            // 8. 人物移除：私人外键复核——有引用转“仅本机保留”；self 阻止
            val keepSet = apply.removePersons.toMutableSet()
            apply.markLocalOnly.forEach { keepSet -= it.personId }
            for (pid in keepSet) {
                val p = personDao.getById(pid) ?: continue
                if (p.isSelf) throw TxAbort(FamilyError.CollabSelfBlocked)
                if (hasPrivateRefs(pid)) {
                    localOnlyDao.upsert(
                        io.github.xiaohaoz1.jiarenxia.data.db
                            .FamilyLocalOnlyEntity(
                                personId = pid,
                                canonicalId = parseIdMap(
                                    db.familyCollabDao().get()?.idMapJson)
                                    .entries.firstOrNull { it.value == pid }?.key,
                                note = "有私人记录，仅本机保留",
                                createdAt = now))
                } else {
                    personDao.deleteById(pid)
                }
            }
            // 9. 仅本机标记（含采用“从名册移除”的有外键人物，由服务预置）
            apply.markLocalOnly.forEach { localOnlyDao.upsert(it) }
            apply.unmarkLocalOnly.forEach { localOnlyDao.delete(it) }
            // 10. 协作状态 / 发布索引 / 草稿写回
            apply.stateWrite?.let { db.familyCollabDao().upsert(it) }
            apply.publication?.let { db.familyPublicationDao().insert(it) }
            apply.draftWrite?.let { db.familyReceiveDraftDao().upsert(it) }
        }
        FamilyResult.Success(Unit)
    }

    /** 事务内：人物是否有私人外键引用（事项/联系/自定义称呼）。 */
    private suspend fun hasPrivateRefs(pid: String): Boolean =
        db.familyTaskDao().getAll().any { it.personId == pid } ||
            db.contactLogDao().getAll().any { it.personId == pid } ||
            db.contactPlanDao().get(pid) != null ||
            customTitleDao.getAll().any {
                it.observerPersonId == pid || it.targetPersonId == pid }

    /** idMap JSON（规范→本机）解析。 */
    private fun parseIdMap(json: String?): Map<String, String> =
        if (json.isNullOrBlank()) emptyMap() else runCatching {
            val o = org.json.JSONObject(json)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())

    /** 事务内校验失败时用于回滚的信号。 */
    private class TxAbort(val error: FamilyError) : Exception()

    companion object {
        /** 可选提醒偏移：当天 / 提前 1 / 3 / 7 天。 */
        val ALLOWED_OFFSETS: Set<Int> = setOf(0, 1, 3, 7)

        /** 各日期类型的默认提前天数（实现见 ReminderPreference.defaultOffsetsFor）。 */
        fun defaultOffsets(
            type: io.github.xiaohaoz1.jiarenxia.core.model.ImportantDateType
        ) = ReminderPreference.defaultOffsetsFor(type)
    }
}
